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

/**
 * 事件发布工具类
 * 封装了Spring事件和Guava事件的发布功能，支持同步和异步事件发布
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 发布Spring事件
 * GXEventPublisherUtils.publishEvent(new GXBaseEvent<>(data));
 *
 * // 发布Guava异步事件
 * GXEventPublisherUtils.publishGuavaAsyncEvent(new GXBaseEvent<>(data), MyListener.class);
 *
 * // 发布Guava同步事件
 * GXEventPublisherUtils.publishGuavaSyncEvent(new GXBaseEvent<>(data), MyListener.class);
 * }
 * </pre>
 * </p>
 *
 * @author gapleaf@163.com
 */
@SuppressWarnings("all")
public class GXEventPublisherUtils {
    /**
     * 缓存已注册的Guava事件监听器
     * key: 监听器类的全限定名
     * value: 监听器类的简单名称
     */
    private static final ConcurrentHashMap<String, String> EVENT_BUS_REGISTER_CACHE = new ConcurrentHashMap<>(1024);

    /**
     * 私有构造函数，防止实例化
     */
    private GXEventPublisherUtils() {
    }

    /**
     * 发布Spring事件
     * 异步事件可以通过在监听器上添加@Async注解实现，但需要开启SpringBoot的异步功能
     * {@code @EnableAsync}
     *
     * @param event 事件对象
     * @param <T>   事件数据类型
     */
    public static <T> void publishEvent(GXBaseEvent<T> event) {
        GXSpringContextUtils.getApplicationContext().publishEvent(event);
    }

    /**
     * Publish Spring event after current transaction commits.
     * Falls back to immediate publishing when no transaction is active.
     *
     * @param event event object
     * @param <T>   event payload type
     */
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

    /**
     * 发布Guava异步事件
     * 使用指定的监听器类型发布事件，监听器会自动从Spring容器中获取
     *
     * @param event         事件对象
     * @param listenerClazz 监听器的类型
     * @param <T>           事件数据类型
     * @throws GXBusinessException 当指定的监听器类型不存在时抛出
     */
    public static <T> void publishGuavaAsyncEvent(GXBaseEvent<T> event, Class<?> listenerClazz) {
        AsyncEventBus asyncEventBus = AsyncEventBusCenter.getInstance();
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            throw new GXBusinessException("指定的监听类型不存在");
        }
        registerAndPostEvent(asyncEventBus, listener, listenerClazz.getName(), event);
    }

    /**
     * 发布Guava同步事件
     * 使用指定的监听器类型发布事件，监听器会自动从Spring容器中获取
     *
     * @param event         事件对象
     * @param listenerClazz 监听器的类型
     * @param <T>           事件数据类型
     * @throws GXBusinessException 当指定的监听器类型不存在时抛出
     */
    public static <T> void publishGuavaSyncEvent(GXBaseEvent<T> event, Class<?> listenerClazz) {
        EventBus eventBus = SyncEventBusCenter.getInstance();
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            throw new GXBusinessException("指定的监听类型不存在");
        }
        registerAndPostEvent(eventBus, listener, listenerClazz.getName(), event);
    }

    /**
     * 发布Guava异步事件
     * 使用指定的监听器对象发布事件
     *
     * @param event    事件对象
     * @param listener 监听器对象
     * @param <T>      事件数据类型
     */
    public static <T> void publishGuavaAsyncEvent(GXBaseEvent<T> event, Object listener) {
        AsyncEventBus asyncEventBus = AsyncEventBusCenter.getInstance();
        registerAndPostEvent(asyncEventBus, listener, listener.getClass().getName(), event);
    }

    /**
     * 发布Guava同步事件
     * 使用指定的监听器对象发布事件
     *
     * @param event    事件对象
     * @param listener 监听器对象
     * @param <T>      事件数据类型
     */
    public static <T> void publishGuavaSyncEvent(GXBaseEvent<T> event, Object listener) {
        EventBus eventBus = SyncEventBusCenter.getInstance();
        registerAndPostEvent(eventBus, listener, listener.getClass().getName(), event);
    }

    /**
     * 注销Guava异步事件监听器
     *
     * @param listener 监听器对象
     */
    public static void unregisterGuavaAsyncEventObserver(Object listener) {
        unregisterEventObserver(AsyncEventBusCenter.getInstance(), listener);
    }

    /**
     * 注销Guava同步事件监听器
     *
     * @param listener 监听器对象
     */
    public static void unregisterGuavaSyncEventObserver(Object listener) {
        unregisterEventObserver(SyncEventBusCenter.getInstance(), listener);
    }

    /**
     * 注销Guava异步事件监听器
     *
     * @param listenerClazz 监听器类型
     */
    public static void unregisterGuavaAsyncEventObserver(Class<?> listenerClazz) {
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            return;
        }
        unregisterEventObserver(AsyncEventBusCenter.getInstance(), listener);
    }

    /**
     * 注销Guava同步事件监听器
     *
     * @param listenerClazz 监听器类型
     */
    public static void unregisterGuavaSyncEventObserver(Class<?> listenerClazz) {
        Object listener = GXSpringContextUtils.getBean(listenerClazz);
        if (Objects.isNull(listener)) {
            return;
        }
        unregisterEventObserver(SyncEventBusCenter.getInstance(), listener);
    }

    /**
     * 注册监听器并发布事件
     *
     * @param eventBus 事件总线
     * @param listener 监听器对象
     * @param key      缓存键
     * @param event    事件对象
     * @param <T>      事件数据类型
     */
    private static <T> void registerAndPostEvent(EventBus eventBus, Object listener, String key, GXBaseEvent<T> event) {
        String s = EVENT_BUS_REGISTER_CACHE.get(key);
        if (CharSequenceUtil.isEmpty(s)) {
            eventBus.register(listener);
            EVENT_BUS_REGISTER_CACHE.put(key, listener.getClass().getSimpleName());
        }
        eventBus.post(event);
    }

    /**
     * 注销事件监听器
     *
     * @param eventBus 事件总线
     * @param listener 监听器对象
     */
    private static void unregisterEventObserver(EventBus eventBus, Object listener) {
        String key = listener.getClass().getName();
        String s = EVENT_BUS_REGISTER_CACHE.get(key);
        if (CharSequenceUtil.isNotEmpty(s)) {
            eventBus.unregister(listener);
            EVENT_BUS_REGISTER_CACHE.remove(key);
        }
    }
}
