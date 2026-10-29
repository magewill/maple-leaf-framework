package cn.maple.core.datasource.aspect.mybatis.support;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.enums.GXMyBatisListenerDegradeReasonEnums;
import cn.maple.core.datasource.event.GXMyBatisModelDeleteEvent;
import cn.maple.core.datasource.event.GXMyBatisModelDeleteSoftEvent;
import cn.maple.core.datasource.event.GXMyBatisModelSaveBatchEntitySnapshotEvent;
import cn.maple.core.datasource.event.GXMyBatisModelSaveEntityEvent;
import cn.maple.core.datasource.event.GXMyBatisModelUpdateEntityEvent;
import cn.maple.core.datasource.event.GXMyBatisModelUpdateFieldEvent;
import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.RejectedExecutionException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central delivery path for MyBatis write-operation events.
 *
 * <p>Responsibilities: honour the feature switches, resolve declarations once, de-duplicate, pick
 * sync/async delivery and record metrics. Listener failures are isolated from the business caller
 * by default; with {@code propagate-exception=true} synchronous failures surface to the caller
 * as-is and stop subsequent listeners, while asynchronous callbacks and submissions (including
 * rejected ones) are always isolated so they can never terminate the executor thread or change the
 * business result (contracts/listener-callback.md).</p>
 */
@Component
@Slf4j
public class GXMyBatisListenerEventPublisher {
    public static final String ENABLED_KEY = "maple.framework.enable.mybatis-listener";
    public static final String ASYNC_ENABLED_KEY = "maple.framework.enable.mybatis-listener-async";
    private static final String DEGRADE_REASONS = "degradeReasons";
    private static final String SNAPSHOT_DEGRADED = "snapshotDegraded";
    private static final String SNAPSHOT_SCAN_TRUNCATED = "snapshotScanTruncated";
    private static final String CONDITION_LOSSLESS = "conditionLossless";
    private static final AtomicLong OPERATION_SEQUENCE = new AtomicLong();
    private static final int MAX_SNAPSHOT_SCAN_DEPTH = 8;
    private static final int MAX_SNAPSHOT_SCAN_NODES = 4096;

    /**
     * Hard limit on nested listener callback chains: a callback that writes through an annotated
     * mapper is delivered again, and this bound stops the chain (spec edge case "递归触发").
     */
    public static final int MAX_DELIVERY_DEPTH = 5;

    private final GXMyBatisListenerConfigResolver resolver;
    private final GXMyBatisListenerProperties properties;
    private final Environment environment;

    public GXMyBatisListenerEventPublisher(GXMyBatisListenerConfigResolver resolver,
                                           GXMyBatisListenerProperties properties,
                                           Environment environment) {
        this.resolver = resolver;
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * Cheap pre-check used by aspects to avoid building a payload when no listener is declared.
     *
     * <p>Since the aspects short-circuit here, this is where a write without a matching declaration
     * is counted in {@code skipped} (FR-020); {@code publishWithCandidates} keeps a defensive
     * counterpart for callers that publish without this pre-check.</p>
     */
    public boolean hasListener(@Nullable Object target, @Nullable Method invokedMethod) {
        if (!isListenerEnabled() || Objects.isNull(target) || Objects.isNull(invokedMethod)) {
            return false;
        }
        boolean declared = !resolver.resolve(resolver.mapperCandidateTypes(target), invokedMethod).isEmpty();
        if (!declared) {
            // The aspects short-circuit here, so this is the only place where a write without any
            // listener declaration can be counted (FR-020, data-model §6).
            GXMyBatisListenerMetrics.recordSkipped();
        }
        return declared;
    }

    /**
     * Cheap pre-check for the batch path, which also considers the mapper type. Like the simple
     * overload it counts a short-circuited write in {@code skipped}.
     */
    public boolean hasListener(@Nullable Object target, @Nullable Class<?> extraType, @Nullable Method invokedMethod) {
        if (!isListenerEnabled() || Objects.isNull(target) || Objects.isNull(invokedMethod)) {
            return false;
        }
        boolean declared = !resolver.resolve(resolver.serviceCandidateTypes(target, extraType), invokedMethod).isEmpty();
        if (!declared) {
            GXMyBatisListenerMetrics.recordSkipped();
        }
        return declared;
    }

    /**
     * Publishes an event for the given proxy target.
     */
    public void publish(@Nullable Object target, @Nullable Method invokedMethod,
                        GXModelEventNamingEnums syncEvent, GXModelEventNamingEnums asyncEvent,
                        @Nullable Dict source) {
        if (!isListenerEnabled() || Objects.isNull(target)) {
            return;
        }
        publishWithCandidates(resolver.mapperCandidateTypes(target), invokedMethod, syncEvent, asyncEvent, source);
    }

    /**
     * Publishes an event for the given proxy target plus one extra declaring type.
     */
    public void publish(@Nullable Object target, @Nullable Class<?> extraType, @Nullable Method invokedMethod,
                        GXModelEventNamingEnums syncEvent, GXModelEventNamingEnums asyncEvent,
                        @Nullable Dict source) {
        if (!isListenerEnabled() || Objects.isNull(target)) {
            return;
        }
        List<Class<?>> candidateTypes = resolver.serviceCandidateTypes(target, extraType);
        publishWithCandidates(candidateTypes, invokedMethod, syncEvent, asyncEvent, source);
    }

    /**
     * Publishes an event for an explicit candidate type list.
     */
    public void publishWithCandidates(@Nullable List<Class<?>> candidateTypes, @Nullable Method invokedMethod,
                                      GXModelEventNamingEnums syncEvent, GXModelEventNamingEnums asyncEvent,
                                      @Nullable Dict source) {
        if (!isListenerEnabled() || Objects.isNull(source) || source.isEmpty()) {
            return;
        }
        int deliveryDepth = GXMyBatisListenerTaskContext.currentDeliveryDepth();
        if (deliveryDepth >= MAX_DELIVERY_DEPTH) {
            // The write was issued by a listener callback that is already MAX_DELIVERY_DEPTH levels
            // deep (spec edge case "递归触发"). Stop the chain instead of recursing into a
            // StackOverflowError on the business thread; the suppressed delivery is counted and
            // logged so the runaway declaration stays visible.
            GXMyBatisListenerMetrics.recordFailed();
            log.warn("MyBatis listener recursion suppressed: operationId={}, depth={}, maxDepth={}, eventType={}, reason=recursion-limit",
                    buildOperationId(invokedMethod), deliveryDepth, MAX_DELIVERY_DEPTH, syncEvent.getEventType());
            return;
        }
        List<GXMyBatisListenerConfigResolver.ResolvedListener> listeners =
                resolver.resolve(candidateTypes, invokedMethod);
        if (listeners.isEmpty()) {
            GXMyBatisListenerMetrics.recordSkipped();
            if (log.isDebugEnabled()) {
                log.debug("No MyBatis listener declared, event skipped: method={}",
                        Objects.isNull(invokedMethod) ? null : invokedMethod.getName());
            }
            return;
        }
        boolean asyncModeEnabled = isAsyncModeEnabled();
        String operationId = buildOperationId(invokedMethod);
        // The scan annotates the payload; counting happens once afterwards so a payload that is both
        // marked by its aspect and truncated by the scan is still one degraded payload (data-model §6).
        markSnapshotDegradedIfNeeded(source, invokedMethod, operationId);
        recordDegradedIfMarked(source);
        // 批量事件只构建一次载荷；多监听方时改为只读快照共享，避免按监听方复制（SC-005），
        // 同时任何修改尝试都会失败，不会影响其它监听方（FR-008）。
        boolean batchEvent = syncEvent == GXModelEventNamingEnums.SYNC_SAVE_BATCH_ENTITY
                || syncEvent == GXModelEventNamingEnums.SYNC_BATCH_CHANGE;
        Dict payload = batchEvent && listeners.size() > 1 ? GXReadOnlyPayload.wrap(source) : source;
        // The operation id must stay visible to every asynchronous delivery of this write: the
        // executor's task decorator reads it when the task is submitted at after-commit time
        // (FR-020). It is published once around the whole loop and released once afterwards, so no
        // listener's cleanup can clear it before a later listener's after-commit delivery runs.
        boolean asyncDeclared = asyncModeEnabled && hasAsyncListener(listeners);
        String enclosingOperationId = GXMyBatisListenerTaskContext.currentOperationId();
        if (asyncDeclared) {
            GXMyBatisListenerTaskContext.setOperationId(operationId);
        }
        try {
            for (GXMyBatisListenerConfigResolver.ResolvedListener listener : listeners) {
                deliver(listener, syncEvent, asyncEvent, payload, asyncModeEnabled, operationId);
            }
        } finally {
            if (asyncDeclared) {
                // A listener callback may write through an annotated mapper again, so this write can
                // be nested inside an enclosing one. Restoring the enclosing id keeps the outer
                // after-commit deliveries correlated instead of clearing it (spec 边缘用例：递归触发).
                releaseOperationId(enclosingOperationId);
            }
        }
    }

    private boolean hasAsyncListener(List<GXMyBatisListenerConfigResolver.ResolvedListener> listeners) {
        for (GXMyBatisListenerConfigResolver.ResolvedListener listener : listeners) {
            if (listener.async()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hands the operation-id restoration over to the surrounding transaction so the after-commit
     * deliveries still observe this write's id, or restores it right away when nothing can defer the
     * delivery. Restoring the enclosing id (instead of clearing unconditionally) keeps a nested
     * write issued from a listener callback from erasing the outer write's correlation.
     */
    private void releaseOperationId(@Nullable String enclosingOperationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    GXMyBatisListenerTaskContext.setOperationId(enclosingOperationId);
                }
            });
            return;
        }
        GXMyBatisListenerTaskContext.setOperationId(enclosingOperationId);
    }

    /**
     * Mirrors the condition used by {@code GXEventPublisherUtils#publishEventAfterCommit}: only an
     * actual transaction with an active synchronization really defers the callback. Anything else is
     * delivered inline on the business thread, so it must be treated as an immediate synchronous
     * delivery (T118).
     */
    private boolean isAfterCommitDeferralActive() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    private void deliver(GXMyBatisListenerConfigResolver.ResolvedListener listener,
                         GXModelEventNamingEnums syncEvent, GXModelEventNamingEnums asyncEvent,
                         Dict source, boolean asyncModeEnabled, String operationId) {
        boolean async = listener.async() && asyncModeEnabled;
        // sync-after-commit only defers a callback when a real transaction can defer it: without one
        // GXEventPublisherUtils#publishEventAfterCommit publishes inline, so the legacy
        // propagate-exception semantics and the slow-callback measurement must still apply
        // (contracts/listener-callback.md 异常语义).
        boolean deferred = async || (properties.isSyncAfterCommit() && isAfterCommitDeferralActive());
        GXModelEventNamingEnums naming = async ? asyncEvent : syncEvent;
        Dict eventParam = Dict.create()
                .set(GXMyBatisEventConstant.LISTENER_CLAZZ_NAME, listener.listenerClazz().getSimpleName())
                .set(GXMyBatisEventConstant.LISTENER_CLAZZ, listener.listenerClazz())
                .set(GXMyBatisEventConstant.PROPAGATE_EXCEPTION, properties.isPropagateException())
                .set(GXMyBatisEventConstant.ASYNC_DELIVERY, async)
                .set(GXMyBatisEventConstant.DEFERRED_DELIVERY, deferred)
                .set(GXMyBatisEventConstant.OPERATION_ID, operationId);
        GXBaseEvent<Dict> event;
        try {
            event = createEvent(naming, source, eventParam);
        } catch (RuntimeException exception) {
            // Building the event includes the defensive snapshot, which can refuse an oversized or
            // too deeply nested payload. That is a delivery concern, so it must never surface on the
            // business caller and fail a write that already succeeded (FR-006).
            GXMyBatisListenerMetrics.recordFailed();
            log.error("MyBatis listener payload construction failed: operationId={}, eventType={}, mode={}, listener={}, declaredFrom={}, reason={}",
                    operationId, naming.getEventType(), async ? "async" : "sync",
                    listener.listenerClazz().getName(), listener.declaredFrom(), exception.getMessage(), exception);
            return;
        }
        logDegradedPayloadIfNeeded(source, naming, async, operationId);
        long startNanos = System.nanoTime();
        try {
            if (deferred) {
                // After-commit delivery: the operation id is already published for this write and is
                // released once the whole listener loop is done (see publishWithCandidates).
                GXEventPublisherUtils.publishEventAfterCommit(event);
            } else {
                GXEventPublisherUtils.publishEvent(event);
            }
        } catch (RuntimeException exception) {
            // Delivery-layer failures are counted and logged exactly once here in both switch modes.
            // Callback failures never arrive as thrown exceptions: the dispatch layer already counted
            // and logged them and hands pending ones over below (T077). A rejection is already
            // counted and logged by the executor's rejection handler, so it must not inflate
            // failed as well (FR-020).
            if (!(async && exception instanceof RejectedExecutionException)) {
                GXMyBatisListenerMetrics.recordFailed();
                log.error("MyBatis listener delivery failed: operationId={}, eventType={}, mode={}, listener={}, declaredFrom={}, reason={}",
                        operationId, naming.getEventType(), async ? "async" : "sync",
                        listener.listenerClazz().getName(), listener.declaredFrom(), exception.getMessage(), exception);
            }
            // Deferred deliveries (asynchronous submissions, including rejected ones, and the
            // transactional after-commit path) must never change the business result: the write is
            // already committed and the caller cannot act on the failure
            // (FR-016, contracts/listener-callback.md).
            if (!deferred && properties.isPropagateException()) {
                throw exception;
            }
            return;
        }
        RuntimeException pendingCallbackFailure = consumePendingCallbackFailure(event);
        if (pendingCallbackFailure != null) {
            // Legacy behaviour (contracts/listener-callback.md): surface the original callback
            // exception as-is so the publish loop stops and the business caller observes it. The
            // dispatch layer already counted and logged it. Deferred deliveries never stash a
            // pending failure, so this branch can only be reached for inline synchronous callbacks.
            throw pendingCallbackFailure;
        }
        GXMyBatisListenerMetrics.recordDelivered();
        if (!deferred) {
            // Only inline synchronous deliveries measure the real callback duration; asynchronous
            // and after-commit deliveries only measure submission/registration here.
            emitSlowCallbackWarning(naming, listener, startNanos, operationId);
        }
    }

    /**
     * Removes and returns the callback failure stashed by the dispatch layer for
     * {@code propagate-exception=true} synchronous deliveries, or {@code null} when none is pending.
     */
    private RuntimeException consumePendingCallbackFailure(GXBaseEvent<Dict> event) {
        Dict param = event.getParam();
        if (Objects.isNull(param)) {
            return null;
        }
        Object pending = param.remove(GXMyBatisEventConstant.PENDING_CALLBACK_FAILURE);
        return pending instanceof RuntimeException runtimeException ? runtimeException : null;
    }

    private void emitSlowCallbackWarning(GXModelEventNamingEnums naming,
                                         GXMyBatisListenerConfigResolver.ResolvedListener listener,
                                         long startNanos, String operationId) {
        long threshold = properties.getSlowCallbackThresholdMs();
        if (threshold <= 0L) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        if (elapsedMillis > threshold) {
            log.warn("Slow MyBatis listener callback: operationId={}, eventType={}, mode=sync, reason=slow-callback, listener={}, elapsed={} ms",
                    operationId, naming.getEventType(), listener.listenerClazz().getName(), elapsedMillis);
        }
    }

    private String buildOperationId(@Nullable Method invokedMethod) {
        String methodName = Objects.isNull(invokedMethod) ? "unknown" : invokedMethod.getName();
        return methodName + "#" + OPERATION_SEQUENCE.incrementAndGet();
    }

    /**
     * Counts a degraded payload exactly once, whatever marked it: the aspect (lossy condition,
     * retained references) or the strict scan (retained references, exhausted budget). Every marker is
     * inspected here instead of being counted where it is set, so a payload carrying several markers is
     * still one degraded payload in the metrics (data-model §6).
     */
    private void recordDegradedIfMarked(Dict source) {
        boolean conditionLossless = !Boolean.FALSE.equals(source.get(CONDITION_LOSSLESS));
        boolean snapshotDegraded = Boolean.TRUE.equals(source.getBool(SNAPSHOT_DEGRADED));
        boolean scanTruncated = Boolean.TRUE.equals(source.getBool(SNAPSHOT_SCAN_TRUNCATED));
        Object reasons = source.get(DEGRADE_REASONS);
        boolean hasReasons = reasons instanceof Collection<?> collection && !collection.isEmpty();
        if (!conditionLossless || snapshotDegraded || scanTruncated || hasReasons) {
            GXMyBatisListenerMetrics.recordDegraded();
        }
    }

    /**
     * Honours {@code strictSnapshot}: payloads that keep references to values that cannot be defensively
     * copied are marked with {@code snapshotDegraded} and an {@code UNSNAPSHOTABLE_VALUE} degrade reason.
     *
     * <p>The scan is bounded, so payloads larger than the budget cannot be verified. Those are marked
     * with {@code snapshotScanTruncated} instead of being reported as clean, otherwise a large batch
     * would silently look like a verified snapshot (spec 边缘用例：严格快照扫描预算).</p>
     *
     * <p>This method only annotates and logs; counting belongs to {@link #recordDegradedIfMarked(Dict)},
     * which runs after it so one payload cannot be counted twice.</p>
     */
    private void markSnapshotDegradedIfNeeded(Dict source, @Nullable Method invokedMethod, String operationId) {
        if (!properties.isStrictSnapshot()) {
            return;
        }
        SnapshotScan scan = scanPayload(source);
        if (scan == SnapshotScan.CLEAN) {
            return;
        }
        String methodName = Objects.isNull(invokedMethod) ? null : invokedMethod.getName();
        if (scan == SnapshotScan.TRUNCATED) {
            if (Boolean.TRUE.equals(source.getBool(SNAPSHOT_SCAN_TRUNCATED))) {
                return;
            }
            source.set(SNAPSHOT_SCAN_TRUNCATED, Boolean.TRUE);
            log.warn("MyBatis listener payload scan truncated: operationId={}, method={}, maxDepth={}, maxNodes={}, reason=scan-truncated",
                    operationId, methodName, MAX_SNAPSHOT_SCAN_DEPTH, MAX_SNAPSHOT_SCAN_NODES);
            return;
        }
        if (Boolean.TRUE.equals(source.getBool(SNAPSHOT_DEGRADED))) {
            return;
        }
        source.set(SNAPSHOT_DEGRADED, Boolean.TRUE);
        source.set(DEGRADE_REASONS, mergeDegradeReasons(source.get(DEGRADE_REASONS)));
        log.warn("MyBatis listener payload keeps references to unsnapshotable values: operationId={}, method={}",
                operationId, methodName);
    }

    private void logDegradedPayloadIfNeeded(Dict source, GXModelEventNamingEnums naming, boolean async, String operationId) {
        Object reasons = source.get(DEGRADE_REASONS);
        if (!(reasons instanceof Collection<?> collection) || collection.isEmpty()) {
            return;
        }
        log.warn("MyBatis listener payload degraded: operationId={}, eventType={}, mode={}, reasons={}",
                operationId, naming.getEventType(), async ? "async" : "sync", collection);
    }

    private SnapshotScan scanPayload(Dict source) {
        return scanPayload(source, 0, new int[]{0});
    }

    private SnapshotScan scanPayload(@Nullable Object value, int depth, int[] visitedNodes) {
        if (ObjectUtil.isNull(value)) {
            return SnapshotScan.CLEAN;
        }
        if (depth > MAX_SNAPSHOT_SCAN_DEPTH || ++visitedNodes[0] > MAX_SNAPSHOT_SCAN_NODES) {
            return SnapshotScan.TRUNCATED;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object nested : map.values()) {
                SnapshotScan result = scanPayload(nested, depth + 1, visitedNodes);
                if (result != SnapshotScan.CLEAN) {
                    return result;
                }
            }
            return SnapshotScan.CLEAN;
        }
        if (value instanceof Collection<?> collection) {
            for (Object nested : collection) {
                SnapshotScan result = scanPayload(nested, depth + 1, visitedNodes);
                if (result != SnapshotScan.CLEAN) {
                    return result;
                }
            }
            return SnapshotScan.CLEAN;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                SnapshotScan result = scanPayload(Array.get(value, index), depth + 1, visitedNodes);
                if (result != SnapshotScan.CLEAN) {
                    return result;
                }
            }
            return SnapshotScan.CLEAN;
        }
        return isUnsnapshotableLeaf(value) ? SnapshotScan.UNSNAPSHOTABLE : SnapshotScan.CLEAN;
    }

    /**
     * Outcome of the bounded strict-snapshot scan: clean, reference-keeping value found, or budget
     * exhausted before the whole payload could be checked.
     */
    private enum SnapshotScan {
        CLEAN,
        UNSNAPSHOTABLE,
        TRUNCATED
    }

    private boolean isUnsnapshotableLeaf(Object value) {
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof UUID
                || value instanceof Temporal || value instanceof Date || value instanceof Calendar) {
            return false;
        }
        try {
            return ObjectUtil.cloneIfPossible(value) == value;
        } catch (RuntimeException exception) {
            log.debug("Snapshot probe failed for value type {}; treating it as unsnapshotable",
                    value.getClass().getName(), exception);
            return true;
        }
    }

    private List<GXMyBatisListenerDegradeReasonEnums> mergeDegradeReasons(@Nullable Object existingReasons) {
        List<GXMyBatisListenerDegradeReasonEnums> merged = new ArrayList<>();
        if (existingReasons instanceof Collection<?> collection) {
            for (Object reason : collection) {
                if (reason instanceof GXMyBatisListenerDegradeReasonEnums degradeReason && !merged.contains(degradeReason)) {
                    merged.add(degradeReason);
                }
            }
        }
        if (!merged.contains(GXMyBatisListenerDegradeReasonEnums.UNSNAPSHOTABLE_VALUE)) {
            merged.add(GXMyBatisListenerDegradeReasonEnums.UNSNAPSHOTABLE_VALUE);
        }
        return List.copyOf(merged);
    }

    @PostConstruct
    void logSwitchState() {
        if (!isListenerEnabled()) {
            log.warn("MyBatis listener is disabled ({}); no write events will be published", ENABLED_KEY);
            return;
        }
        if (!isAsyncModeEnabled()) {
            log.warn("MyBatis listener async mode is disabled ({}); asynchronous declarations execute synchronously",
                    ASYNC_ENABLED_KEY);
        }
    }

    private GXBaseEvent<Dict> createEvent(GXModelEventNamingEnums naming, Dict source, Dict eventParam) {
        String eventType = naming.getEventType();
        String eventName = naming.getEventName();
        return switch (naming) {
            case SYNC_SAVE_ENTITY, ASYNC_SAVE_ENTITY ->
                    new GXMyBatisModelSaveEntityEvent<>(source, eventType, eventParam, eventName);
            case SYNC_UPDATE_ENTITY, ASYNC_UPDATE_ENTITY ->
                    new GXMyBatisModelUpdateEntityEvent<>(source, eventType, eventParam, eventName);
            case SYNC_UPDATE_FIELD, ASYNC_UPDATE_FIELD ->
                    new GXMyBatisModelUpdateFieldEvent<>(source, eventType, eventParam, eventName);
            case SYNC_DELETE_SOFT, ASYNC_DELETE_SOFT ->
                    new GXMyBatisModelDeleteSoftEvent<>(source, eventType, eventParam, eventName);
            case SYNC_DELETE, ASYNC_DELETE ->
                    new GXMyBatisModelDeleteEvent<>(source, eventType, eventParam, eventName);
            // 批量载荷由切面一次性构建，所有批量投递都复用该载体，不再由 GXBaseEvent 二次快照
            // （SC-005/FR-011）：单监听方直接使用，多监听方使用的是共享只读快照。
            case SYNC_SAVE_BATCH_ENTITY, ASYNC_SAVE_BATCH_ENTITY, SYNC_BATCH_CHANGE, ASYNC_BATCH_CHANGE ->
                    new GXMyBatisModelSaveBatchEntitySnapshotEvent<>(source, eventType, eventParam, eventName);
        };
    }

    public boolean isListenerEnabled() {
        return environment.getProperty(ENABLED_KEY, Boolean.class, Boolean.TRUE);
    }

    public boolean isAsyncModeEnabled() {
        return environment.getProperty(ASYNC_ENABLED_KEY, Boolean.class, Boolean.TRUE);
    }
}
