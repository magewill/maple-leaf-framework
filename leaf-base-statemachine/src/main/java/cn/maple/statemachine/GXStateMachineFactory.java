package cn.maple.statemachine;

import cn.maple.statemachine.impl.GXStateMachineException;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 状态机工厂类
 * <p>
 * 该类是状态机框架的核心组件之一，负责管理所有创建的状态机实例。
 * 它采用单例模式设计，提供了状态机的注册和获取功能，使得状态机可以在应用的不同部分共享使用。
 * </p>
 * <p>
 * 工厂类的主要功能：
 * <ul>
 *   <li>注册状态机：将构建好的状态机实例注册到工厂中</li>
 *   <li>获取状态机：根据状态机ID获取已注册的状态机实例</li>
 *   <li>确保状态机ID的唯一性：防止重复注册相同ID的状态机</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 创建状态机构建器
 * GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = 
 *     GXStateMachineBuilderFactory.create();
 * 
 * // 配置状态转换
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)
 *     .to(OrderStatus.PAID)
 *     .on(OrderEvent.PAY)
 *     .when(ctx -> ctx.getAmount() > 0)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单已支付：" + ctx.getOrderId());
 *     });
 * 
 * // 构建并注册状态机（注册过程在build方法内部自动完成）
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> orderStateMachine = 
 *     builder.build("orderStateMachine");
 * 
 * // 在应用的其他部分获取状态机
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> machine = 
 *     GXStateMachineFactory.get("orderStateMachine");
 * 
 * // 使用获取的状态机处理事件
 * OrderContext context = new OrderContext();
 * context.setOrderId("ORDER_123456");
 * context.setAmount(100);
 * OrderStatus newStatus = machine.fireEvent(OrderStatus.WAIT_PAYMENT, OrderEvent.PAY, context);
 * }
 * </pre>
 * </p>
 * 
 * @see GXStateMachine 状态机接口
 * @see cn.maple.statemachine.builder.GXStateMachineBuilder 状态机构建器接口
 */
@Slf4j
public class GXStateMachineFactory {
    /**
     * 状态机映射表
     * <p>
     * 使用线程安全的ConcurrentHashMap存储所有注册的状态机实例。
     * 键为状态机ID，值为状态机实例。
     * </p>
     */
    static Map<String /* machineId */, GXStateMachine<?, ?, ?>> stateMachineMap = new ConcurrentHashMap<>();

    /**
     * 私有构造函数
     * <p>
     * 防止外部直接实例化工厂类，确保单例模式。
     * </p>
     */
    private GXStateMachineFactory() {
        // 私有构造函数，防止实例化
    }

    /**
     * 注册状态机
     * <p>
     * 将构建好的状态机实例注册到工厂中，以便后续通过ID获取。
     * 如果尝试注册已存在ID的状态机，将抛出异常。
     * </p>
     * <p>
     * 注意：此方法通常由状态机构建器在build方法中自动调用，
     * 用户一般不需要直接调用此方法。
     * </p>
     *
     * @param stateMachine 要注册的状态机实例
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 上下文类型
     * @throws GXStateMachineException 如果尝试注册已存在ID的状态机
     */
    public static <S, E, C> void register(GXStateMachine<S, E, C> stateMachine) {
        String machineId = stateMachine.getMachineId();
        if (stateMachineMap.get(machineId) != null) {
            throw new GXStateMachineException("The state machine with id [" + machineId + "] is already built, no need to build again");
        }
        stateMachineMap.put(stateMachine.getMachineId(), stateMachine);
        log.debug("状态机 [{}] 已成功注册", machineId);
    }

    /**
     * 获取状态机
     * <p>
     * 根据状态机ID获取已注册的状态机实例。
     * 如果指定ID的状态机不存在，将抛出异常。
     * </p>
     * <p>
     * 使用此方法可以在应用的不同部分共享同一个状态机实例，
     * 避免重复创建相同的状态机。
     * </p>
     *
     * @param machineId 状态机ID
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 上下文类型
     * @return 状态机实例
     * @throws GXStateMachineException 如果指定ID的状态机不存在
     */
    public static <S, E, C> GXStateMachine<S, E, C> get(String machineId) {
        GXStateMachine<S, E, C> stateMachine = (GXStateMachine<S, E, C>) stateMachineMap.get(machineId);
        if (stateMachine == null) {
            throw new GXStateMachineException("There is no stateMachine instance for " + machineId + ", please build it first");
        }
        return stateMachine;
    }
}
