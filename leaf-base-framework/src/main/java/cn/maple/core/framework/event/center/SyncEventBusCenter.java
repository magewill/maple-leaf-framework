package cn.maple.core.framework.event.center;

import cn.maple.core.framework.util.GXCommonUtils;
import com.google.common.eventbus.EventBus;

/**
 * 同步事件总线中心
 * <p>
 * 提供基于Guava EventBus的同步事件发布-订阅功能，事件发布后会在当前线程中同步执行所有订阅者的处理方法。
 * 该类是线程安全的，可在多线程环境下使用。同步事件处理适用于需要立即响应的场景，如业务流程中的关键步骤、
 * 需要事务一致性的操作等。
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 定义事件类
 * public class OrderCompletedEvent {
 *     private final Order order;
 *
 *     public OrderCompletedEvent(Order order) {
 *         this.order = order;
 *     }
 *
 *     public Order getOrder() {
 *         return order;
 *     }
 * }
 *
 * // 2. 定义事件订阅者
 * public class OrderEventListener {
 *
 *     // 构造函数中注册到事件总线
 *     public OrderEventListener() {
 *         SyncEventBusCenter.register(this);
 *     }
 *
 *     // 使用@Subscribe注解标记事件处理方法
 *     @Subscribe
 *     public void handleOrderCompleted(OrderCompletedEvent event) {
 *         Order order = event.getOrder();
 *         // 同步处理订单完成事件，如更新库存、生成发票等
 *         System.out.println("同步处理订单完成事件: " + order.getOrderNo());
 *     }
 *
 *     // 在对象销毁时取消注册
 *     public void destroy() {
 *         SyncEventBusCenter.unregister(this);
 *     }
 * }
 *
 * // 3. 发布事件
 * Order order = orderService.getOrder("ORD20230101001");
 * SyncEventBusCenter.post(new OrderCompletedEvent(order));
 * // 事件处理完成后才会继续执行后续代码
 * System.out.println("事件处理已完成");
 * </pre>
 * </p>
 *
 * <p>
 * 性能与安全说明：
 * 1. 同步事件处理会阻塞当前线程，直到所有订阅者处理完成
 * 2. 适用于需要立即响应的场景，如业务流程中的关键步骤
 * 3. 事件处理方法应尽量简短，避免长时间阻塞
 * 4. 如果处理逻辑复杂或耗时，建议使用AsyncEventBusCenter
 * 5. 事件处理方法中的异常会直接传播到事件发布者，需要妥善处理
 * </p>
 *
 * @author maple
 * @see com.google.common.eventbus.Subscribe 用于标记事件处理方法的注解
 * @see AsyncEventBusCenter 异步事件总线中心，用于不需要立即响应的场景
 */
@SuppressWarnings("unused")
public class SyncEventBusCenter {
    /**
     * 应用名称，用于标识事件总线
     */
    private static final String APPLICATION_NAME = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);

    /**
     * CPU核心数，保留字段，可用于后续扩展
     */
    private static final Integer CPU_CORE_NUMBER = Runtime.getRuntime().availableProcessors();

    /**
     * 同步事件总线实例
     * 事件发布后会同步执行所有订阅者的处理方法
     * 静态初始化确保线程安全
     */
    private static final EventBus SYNC_EVENT_BUS = new EventBus("sync-" + APPLICATION_NAME);

    /**
     * 私有构造函数，防止实例化
     * 该类采用静态工厂方法模式提供功能
     */
    private SyncEventBusCenter() {
        // 防止通过反射方式实例化
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 获取同步事件总线实例
     *
     * @return EventBus实例
     */
    public static EventBus getInstance() {
        return SYNC_EVENT_BUS;
    }

    /**
     * 注册事件订阅者
     * 将包含@Subscribe注解方法的对象注册到事件总线
     *
     * @param obj 事件订阅者对象
     * @throws NullPointerException 如果obj为null
     */
    public static void register(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event subscriber cannot be null");
        }
        SYNC_EVENT_BUS.register(obj);
    }

    /**
     * 注销事件订阅者
     * 将对象从事件总线中移除，不再接收事件
     *
     * @param obj 事件订阅者对象
     * @throws NullPointerException 如果obj为null
     */
    public static void unregister(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event subscriber cannot be null");
        }
        SYNC_EVENT_BUS.unregister(obj);
    }

    /**
     * 发布事件
     * 将事件发送给所有注册的订阅者
     * 事件处理在当前线程同步执行
     *
     * @param obj 事件对象
     * @throws NullPointerException 如果obj为null
     */
    public static void post(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event cannot be null");
        }
        SYNC_EVENT_BUS.post(obj);
    }
}
