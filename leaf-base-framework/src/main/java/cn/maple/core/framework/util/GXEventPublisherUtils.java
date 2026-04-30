package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
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
    private static final ConcurrentHashMap<String, String> EVENT_BUS_REGISTER_CACHE = new ConcurrentHashMap<>(1024);

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
        AsyncEventBus asyncEventBus = AsyncEventBusCenter.getInstance();
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            throw new GXBusinessException("指定的监听类型不存在");
        }
        registerAndPostEvent(asyncEventBus, listener, listenerClazz.getName(), event);
    }

    public static <T> void publishGuavaSyncEvent(GXBaseEvent<T> event, Class<?> listenerClazz) {
        EventBus eventBus = SyncEventBusCenter.getInstance();
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            throw new GXBusinessException("指定的监听类型不存在");
        }
        registerAndPostEvent(eventBus, listener, listenerClazz.getName(), event);
    }

    public static <T> void publishGuavaAsyncEvent(GXBaseEvent<T> event, Object listener) {
        AsyncEventBus asyncEventBus = AsyncEventBusCenter.getInstance();
        registerAndPostEvent(asyncEventBus, listener, listener.getClass().getName(), event);
    }

    public static <T> void publishGuavaSyncEvent(GXBaseEvent<T> event, Object listener) {
        EventBus eventBus = SyncEventBusCenter.getInstance();
        registerAndPostEvent(eventBus, listener, listener.getClass().getName(), event);
    }

    public static void unregisterGuavaAsyncEventObserver(Object listener) {
        unregisterEventObserver(AsyncEventBusCenter.getInstance(), listener);
    }

    public static void unregisterGuavaSyncEventObserver(Object listener) {
        unregisterEventObserver(SyncEventBusCenter.getInstance(), listener);
    }

    public static void unregisterGuavaAsyncEventObserver(Class<?> listenerClazz) {
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            return;
        }
        unregisterEventObserver(AsyncEventBusCenter.getInstance(), listener);
    }

    public static void unregisterGuavaSyncEventObserver(Class<?> listenerClazz) {
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            return;
        }
        unregisterEventObserver(SyncEventBusCenter.getInstance(), listener);
    }

    private static <T> void registerAndPostEvent(EventBus eventBus, Object listener, String key, GXBaseEvent<T> event) {
        String s = EVENT_BUS_REGISTER_CACHE.get(key);
        if (CharSequenceUtil.isEmpty(s)) {
            eventBus.register(listener);
            EVENT_BUS_REGISTER_CACHE.put(key, listener.getClass().getSimpleName());
        }
        eventBus.post(event);
    }

    private static void unregisterEventObserver(EventBus eventBus, Object listener) {
        String key = listener.getClass().getName();
        String s = EVENT_BUS_REGISTER_CACHE.get(key);
        if (CharSequenceUtil.isNotEmpty(s)) {
            eventBus.unregister(listener);
            EVENT_BUS_REGISTER_CACHE.remove(key);
        }
    }
}
