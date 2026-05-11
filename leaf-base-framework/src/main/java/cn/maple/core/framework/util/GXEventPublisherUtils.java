package cn.maple.core.framework.util;

import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.event.center.AsyncEventBusCenter;
import cn.maple.core.framework.event.center.SyncEventBusCenter;
import cn.maple.core.framework.exception.GXBusinessException;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("all")
public class GXEventPublisherUtils {
    private static final ConcurrentHashMap<String, Boolean> EVENT_BUS_REGISTER_CACHE = new ConcurrentHashMap<>(1024);

    private GXEventPublisherUtils() {
    }

    public static <T> void publishEvent(GXBaseEvent<T> event) {
        GXSpringContextUtils.getApplicationContext().publishEvent(event);
    }

    public static <T> void publishEventAfterCommit(GXBaseEvent<T> event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishEvent(event);
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
        if (!Boolean.TRUE.equals(EVENT_BUS_REGISTER_CACHE.get(key))) {
            eventBus.register(listener);
            EVENT_BUS_REGISTER_CACHE.put(key, Boolean.TRUE);
        }
        eventBus.post(event);
    }

    private static void unregisterEventObserver(EventBus eventBus, Object listener) {
        if (listener == null) {
            return;
        }
        String key = buildRegisterCacheKey(eventBus, listener);
        if (Boolean.TRUE.equals(EVENT_BUS_REGISTER_CACHE.get(key))) {
            eventBus.unregister(listener);
            EVENT_BUS_REGISTER_CACHE.remove(key);
        }
    }

    private static String buildRegisterCacheKey(EventBus eventBus, Object listener) {
        return System.identityHashCode(eventBus) + ":" + System.identityHashCode(listener) + ":" + listener.getClass().getName();
    }
}
