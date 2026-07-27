package cn.maple.core.framework.util;

import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.event.center.AsyncEventBusCenter;
import cn.maple.core.framework.event.center.SyncEventBusCenter;
import cn.maple.core.framework.exception.GXBusinessException;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("all")
public class GXEventPublisherUtils {
    private static final int REGISTER_LOCK_STRIPE_SIZE = 64;
    private static final int EVENT_METADATA_LOG_MAX_LENGTH = 128;
    private static final Logger LOG = LoggerFactory.getLogger(GXEventPublisherUtils.class);
    private static final ConcurrentHashMap<String, Boolean> EVENT_BUS_REGISTER_CACHE = new ConcurrentHashMap<>(1024);
    private static final Object[] EVENT_BUS_REGISTER_LOCK_STRIPES = initRegisterLockStripes();

    private GXEventPublisherUtils() {
    }

    public static <T> void publishEvent(GXBaseEvent<T> event) {
        GXSpringContextUtils.getApplicationContext().publishEvent(event);
    }

    /**
     * Publishes an event after a successfully committed transaction, or immediately when no actual transaction exists.
     * <p>
     * A publishing failure in {@link TransactionSynchronization#afterCommit()} is logged and contained because the
     * database transaction has already committed at that point. Without a transaction, publishing remains immediate
     * and preserves the caller-visible exception behavior.
     * <p>
     * This method is not a durable outbox: a process failure between commit and callback can still lose the event.
     * Synchronous listeners that write to the database should use a new transaction propagation.
     */
    public static <T> void publishEventAfterCommit(GXBaseEvent<T> event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        publishEvent(event);
                    } catch (RuntimeException exception) {
                        logAfterCommitPublishFailure(event, exception);
                    }
                }
            });
            return;
        }
        publishEvent(event);
    }

    public static <T> void publishGuavaAsyncEvent(GXBaseEvent<T> event, Class<?> listenerClazz) {
        AsyncEventBus asyncEventBus = getAsyncEventBus();
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            throw new GXBusinessException("Event listener bean does not exist");
        }
        registerAndPostEvent(asyncEventBus, listener, event);
    }

    public static <T> void publishGuavaSyncEvent(GXBaseEvent<T> event, Class<?> listenerClazz) {
        EventBus eventBus = getSyncEventBus();
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            throw new GXBusinessException("Event listener bean does not exist");
        }
        registerAndPostEvent(eventBus, listener, event);
    }

    public static <T> void publishGuavaAsyncEvent(GXBaseEvent<T> event, Object listener) {
        registerAndPostEvent(getAsyncEventBus(), listener, event);
    }

    public static <T> void publishGuavaSyncEvent(GXBaseEvent<T> event, Object listener) {
        registerAndPostEvent(getSyncEventBus(), listener, event);
    }

    public static void unregisterGuavaAsyncEventObserver(Object listener) {
        unregisterEventObserver(getAsyncEventBus(), listener);
    }

    public static void unregisterGuavaSyncEventObserver(Object listener) {
        unregisterEventObserver(getSyncEventBus(), listener);
    }

    public static void unregisterGuavaAsyncEventObserver(Class<?> listenerClazz) {
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            return;
        }
        unregisterEventObserver(getAsyncEventBus(), listener);
    }

    public static void unregisterGuavaSyncEventObserver(Class<?> listenerClazz) {
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            return;
        }
        unregisterEventObserver(getSyncEventBus(), listener);
    }

    private static EventBus getSyncEventBus() {
        EventBus eventBus = GXSpringContextUtils.getBean("eventBus", EventBus.class);
        return eventBus == null ? SyncEventBusCenter.getInstance() : eventBus;
    }

    private static AsyncEventBus getAsyncEventBus() {
        AsyncEventBus asyncEventBus = GXSpringContextUtils.getBean("asyncEventBus", AsyncEventBus.class);
        return asyncEventBus == null ? AsyncEventBusCenter.getInstance() : asyncEventBus;
    }

    private static <T> void registerAndPostEvent(EventBus eventBus, Object listener, GXBaseEvent<T> event) {
        if (listener == null) {
            throw new GXBusinessException("Event listener does not exist");
        }
        String key = buildRegisterCacheKey(eventBus, listener);
        if (Boolean.TRUE.equals(EVENT_BUS_REGISTER_CACHE.get(key))) {
            eventBus.post(event);
            return;
        }
        synchronized (resolveRegisterLock(key)) {
            if (!Boolean.TRUE.equals(EVENT_BUS_REGISTER_CACHE.get(key))) {
                eventBus.register(listener);
                EVENT_BUS_REGISTER_CACHE.put(key, Boolean.TRUE);
            }
        }
        eventBus.post(event);
    }

    private static void unregisterEventObserver(EventBus eventBus, Object listener) {
        if (listener == null) {
            return;
        }
        String key = buildRegisterCacheKey(eventBus, listener);
        if (!Boolean.TRUE.equals(EVENT_BUS_REGISTER_CACHE.get(key))) {
            return;
        }
        synchronized (resolveRegisterLock(key)) {
            if (Boolean.TRUE.equals(EVENT_BUS_REGISTER_CACHE.get(key))) {
                eventBus.unregister(listener);
                EVENT_BUS_REGISTER_CACHE.remove(key);
            }
        }
    }

    private static String buildRegisterCacheKey(EventBus eventBus, Object listener) {
        return System.identityHashCode(eventBus) + ":" + System.identityHashCode(listener) + ":" + listener.getClass().getName();
    }

    private static Object[] initRegisterLockStripes() {
        Object[] stripes = new Object[REGISTER_LOCK_STRIPE_SIZE];
        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new Object();
        }
        return stripes;
    }

    private static Object resolveRegisterLock(String key) {
        int lockIndex = (key.hashCode() & Integer.MAX_VALUE) % REGISTER_LOCK_STRIPE_SIZE;
        return EVENT_BUS_REGISTER_LOCK_STRIPES[lockIndex];
    }

    private static void logAfterCommitPublishFailure(GXBaseEvent<?> event, RuntimeException exception) {
        LOG.error(
                "Failed to publish event after transaction commit: eventClass={}, eventType={}, eventName={}",
                event == null ? null : event.getClass().getName(),
                event == null ? null : sanitizeEventMetadata(event.getEventType()),
                event == null || event.getEventName() == null ? null : sanitizeEventMetadata(event.getEventName().toString()),
                exception
        );
    }

    private static String sanitizeEventMetadata(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        if (sanitized.length() <= EVENT_METADATA_LOG_MAX_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, EVENT_METADATA_LOG_MAX_LENGTH) + "...";
    }
}
