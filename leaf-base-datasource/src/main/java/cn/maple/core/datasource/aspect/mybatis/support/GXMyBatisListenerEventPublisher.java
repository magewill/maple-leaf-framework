package cn.maple.core.datasource.aspect.mybatis.support;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.event.GXMyBatisModelDeleteEvent;
import cn.maple.core.datasource.event.GXMyBatisModelDeleteSoftEvent;
import cn.maple.core.datasource.event.GXMyBatisModelSaveBatchEntityEvent;
import cn.maple.core.datasource.event.GXMyBatisModelSaveEntityEvent;
import cn.maple.core.datasource.event.GXMyBatisModelUpdateEntityEvent;
import cn.maple.core.datasource.event.GXMyBatisModelUpdateFieldEvent;
import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

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
        for (GXMyBatisListenerConfigResolver.ResolvedListener listener : listeners) {
            deliver(listener, syncEvent, asyncEvent, source, asyncModeEnabled);
        }
    }

    private void deliver(GXMyBatisListenerConfigResolver.ResolvedListener listener,
                         GXModelEventNamingEnums syncEvent, GXModelEventNamingEnums asyncEvent,
                         Dict source, boolean asyncModeEnabled) {
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
            emitSlowCallbackWarning(naming, listener, startNanos);
        } catch (RuntimeException exception) {
            GXMyBatisListenerMetrics.recordFailed();
            log.error("MyBatis listener delivery failed: eventType={}, mode={}, listener={}, declaredFrom={}, reason={}",
                    naming.getEventType(), async ? "async" : "sync",
                    listener.listenerClazz().getName(), listener.declaredFrom(), exception.getMessage(), exception);
            if (properties.isPropagateException()) {
                throw exception;
            }
        }
    }

    private void emitSlowCallbackWarning(GXModelEventNamingEnums naming,
                                         GXMyBatisListenerConfigResolver.ResolvedListener listener,
                                         long startNanos) {
        long threshold = properties.getSlowCallbackThresholdMs();
        if (threshold <= 0L) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        if (elapsedMillis > threshold) {
            log.warn("Slow MyBatis listener callback: eventType={}, listener={}, elapsed={} ms",
                    naming.getEventType(), listener.listenerClazz().getName(), elapsedMillis);
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
