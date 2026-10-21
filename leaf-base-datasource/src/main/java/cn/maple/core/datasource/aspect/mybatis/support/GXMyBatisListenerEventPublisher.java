package cn.maple.core.datasource.aspect.mybatis.support;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.enums.GXMyBatisListenerDegradeReasonEnums;
import cn.maple.core.datasource.event.GXMyBatisModelDeleteEvent;
import cn.maple.core.datasource.event.GXMyBatisModelDeleteSoftEvent;
import cn.maple.core.datasource.event.GXMyBatisModelSaveBatchEntityEvent;
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

import java.lang.reflect.Method;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central delivery path for MyBatis write-operation events.
 *
 * <p>Responsibilities: honour the feature switches, resolve declarations once, de-duplicate, pick
 * sync/async delivery, record metrics and isolate listener failures from the business caller.</p>
 */
@Component
@Slf4j
public class GXMyBatisListenerEventPublisher {
    public static final String ENABLED_KEY = "maple.framework.enable.mybatis-listener";
    public static final String ASYNC_ENABLED_KEY = "maple.framework.enable.mybatis-listener-async";
    private static final String DEGRADE_REASONS = "degradeReasons";
    private static final String SNAPSHOT_DEGRADED = "snapshotDegraded";
    private static final AtomicLong OPERATION_SEQUENCE = new AtomicLong();

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
     */
    public boolean hasListener(@Nullable Object target, @Nullable Method invokedMethod) {
        if (!isListenerEnabled() || Objects.isNull(target) || Objects.isNull(invokedMethod)) {
            return false;
        }
        return !resolver.resolve(resolver.mapperCandidateTypes(target), invokedMethod).isEmpty();
    }

    /**
     * Cheap pre-check for the batch path, which also considers the mapper type.
     */
    public boolean hasListener(@Nullable Object target, @Nullable Class<?> extraType, @Nullable Method invokedMethod) {
        if (!isListenerEnabled() || Objects.isNull(target) || Objects.isNull(invokedMethod)) {
            return false;
        }
        return !resolver.resolve(resolver.serviceCandidateTypes(target, extraType), invokedMethod).isEmpty();
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
        List<GXMyBatisListenerConfigResolver.ResolvedListener> listeners =
                resolver.resolve(candidateTypes, invokedMethod);
        if (listeners.isEmpty()) {
            if (log.isDebugEnabled()) {
                GXMyBatisListenerMetrics.recordSkipped();
                log.debug("No MyBatis listener declared, event skipped: method={}",
                        Objects.isNull(invokedMethod) ? null : invokedMethod.getName());
            }
            return;
        }
        boolean asyncModeEnabled = isAsyncModeEnabled();
        String operationId = buildOperationId(invokedMethod);
        markSnapshotDegradedIfNeeded(source, invokedMethod, operationId);
        logDegradedPayloadIfNeeded(source, invokedMethod, operationId);
        for (GXMyBatisListenerConfigResolver.ResolvedListener listener : listeners) {
            deliver(listener, syncEvent, asyncEvent, source, asyncModeEnabled, operationId);
        }
    }

    private void deliver(GXMyBatisListenerConfigResolver.ResolvedListener listener,
                         GXModelEventNamingEnums syncEvent, GXModelEventNamingEnums asyncEvent,
                         Dict source, boolean asyncModeEnabled, String operationId) {
        boolean async = listener.async() && asyncModeEnabled;
        GXModelEventNamingEnums naming = async ? asyncEvent : syncEvent;
        Dict eventParam = Dict.create()
                .set("listenerClazzName", listener.listenerClazz().getSimpleName())
                .set("listenerClazz", listener.listenerClazz());
        GXBaseEvent<Dict> event = createEvent(naming, source, eventParam);
        long startNanos = System.nanoTime();
        try {
            if (async || properties.isSyncAfterCommit()) {
                GXEventPublisherUtils.publishEventAfterCommit(event);
            } else {
                GXEventPublisherUtils.publishEvent(event);
            }
            GXMyBatisListenerMetrics.recordDelivered();
            emitSlowCallbackWarning(naming, listener, startNanos, async, operationId);
        } catch (RuntimeException exception) {
            GXMyBatisListenerMetrics.recordFailed();
            log.error("MyBatis listener delivery failed: operationId={}, eventType={}, mode={}, listener={}, declaredFrom={}, reason={}",
                    operationId, naming.getEventType(), async ? "async" : "sync",
                    listener.listenerClazz().getName(), listener.declaredFrom(), exception.getMessage(), exception);
            if (properties.isPropagateException()) {
                throw exception;
            }
        }
    }

    private void emitSlowCallbackWarning(GXModelEventNamingEnums naming,
                                         GXMyBatisListenerConfigResolver.ResolvedListener listener,
                                         long startNanos, boolean async, String operationId) {
        long threshold = properties.getSlowCallbackThresholdMs();
        if (threshold <= 0L) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        if (elapsedMillis > threshold) {
            log.warn("Slow MyBatis listener callback: operationId={}, eventType={}, mode={}, listener={}, elapsed={} ms",
                    operationId, naming.getEventType(), async ? "async" : "sync",
                    listener.listenerClazz().getName(), elapsedMillis);
        }
    }

    private String buildOperationId(@Nullable Method invokedMethod) {
        String methodName = Objects.isNull(invokedMethod) ? "unknown" : invokedMethod.getName();
        return methodName + "#" + OPERATION_SEQUENCE.incrementAndGet();
    }

    /**
     * Honours {@code strictSnapshot}: payloads that keep references to values that cannot be defensively
     * copied are marked with {@code snapshotDegraded} and an {@code UNSNAPSHOTABLE_VALUE} degrade reason.
     */
    private void markSnapshotDegradedIfNeeded(Dict source, @Nullable Method invokedMethod, String operationId) {
        if (!properties.isStrictSnapshot() || Boolean.TRUE.equals(source.getBool(SNAPSHOT_DEGRADED))) {
            return;
        }
        if (!containsUnsnapshotableValue(source)) {
            return;
        }
        source.set(SNAPSHOT_DEGRADED, Boolean.TRUE);
        source.set(DEGRADE_REASONS, mergeDegradeReasons(source.get(DEGRADE_REASONS)));
        GXMyBatisListenerMetrics.recordDegraded();
        log.warn("MyBatis listener payload keeps references to unsnapshotable values: operationId={}, method={}",
                operationId, Objects.isNull(invokedMethod) ? null : invokedMethod.getName());
    }

    private void logDegradedPayloadIfNeeded(Dict source, @Nullable Method invokedMethod, String operationId) {
        Object reasons = source.get(DEGRADE_REASONS);
        if (!(reasons instanceof Collection<?> collection) || collection.isEmpty()) {
            return;
        }
        log.warn("MyBatis listener payload degraded: operationId={}, method={}, reasons={}",
                operationId, Objects.isNull(invokedMethod) ? null : invokedMethod.getName(), collection);
    }

    private boolean containsUnsnapshotableValue(Dict source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (isUnsnapshotableValue(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnsnapshotableValue(@Nullable Object value) {
        if (ObjectUtil.isNull(value)
                || value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof UUID
                || value instanceof Temporal || value instanceof Date || value instanceof Calendar
                || value instanceof Collection<?> || value instanceof Map<?, ?> || value.getClass().isArray()) {
            return false;
        }
        try {
            return ObjectUtil.cloneIfPossible(value) == value;
        } catch (RuntimeException exception) {
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
            case SYNC_SAVE_BATCH_ENTITY, ASYNC_SAVE_BATCH_ENTITY, SYNC_BATCH_CHANGE, ASYNC_BATCH_CHANGE ->
                    new GXMyBatisModelSaveBatchEntityEvent<>(source, eventType, eventParam, eventName);
        };
    }

    public boolean isListenerEnabled() {
        return environment.getProperty(ENABLED_KEY, Boolean.class, Boolean.TRUE);
    }

    public boolean isAsyncModeEnabled() {
        return environment.getProperty(ASYNC_ENABLED_KEY, Boolean.class, Boolean.TRUE);
    }
}
