package cn.maple.statemachine.builder;

import cn.maple.statemachine.GXState;
import cn.maple.statemachine.GXStateMachine;
import cn.maple.statemachine.GXStateMachineFactory;
import cn.maple.statemachine.impl.GXStateMachineImpl;
import cn.maple.statemachine.impl.GXTransitionType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 状态机构建器实现类
 * <p>
 * 此类实现了GXStateMachineBuilder接口，提供了构建状态机的核心功能。
 * 它维护了一个状态映射表，用于存储状态机中的所有状态，并提供创建各种类型转换的方法。
 * 该实现采用了流式API设计，使状态机的构建过程更加直观和易于理解。
 * <p>
 * 泛型参数说明：
 * <ul>
 *     <li>S - 状态(State)类型，通常使用枚举或字符串</li>
 *     <li>E - 事件(Event)类型，通常使用枚举或字符串</li>
 *     <li>C - 上下文(Context)类型，用于在转换过程中传递数据</li>
 * </ul>
 * <p>
 * 线程安全性：
 * <p>
 * 此实现使用ConcurrentHashMap存储状态映射，确保在多线程环境下的安全性。
 * 状态机构建过程通常在应用启动时完成，而状态转换可能在运行时被多个线程并发调用，
 * 因此线程安全是必要的。
 * <p>
 * 性能考虑：
 * <p>
 * 状态机的构建是一次性操作，而状态转换会被频繁调用。本实现在设计上注重转换执行的性能，
 * 通过预先构建状态转换关系，使运行时的状态转换操作高效执行。
 * <p>
 * 完整使用示例：
 * <pre>
 * // 定义订单状态和事件
 * enum OrderStatus {
 *     WAIT_PAYMENT,    // 等待支付
 *     PAID,            // 已支付
 *     WAIT_DELIVER,    // 等待发货
 *     DELIVERING,      // 配送中
 *     DELIVERED,       // 已送达
 *     COMPLETED        // 已完成
 * }
 *
 * enum OrderEvent {
 *     PAY,                    // 支付事件
 *     DELIVER,                // 发货事件
 *     RECEIVE,                // 收货事件
 *     CONFIRM,                // 确认收货事件
 *     UPDATE_DELIVERY_ADDRESS // 更新配送地址事件
 * }
 *
 * // 订单上下文，包含订单相关信息
 * class OrderContext {
 *     private String orderId;
 *     private BigDecimal amount;
 *     private String deliveryAddress;
 *
 *     // getter和setter方法
 * }
 *
 * // 创建状态机构建器
 * GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder =
 *     GXStateMachineBuilderFactory.create();
 *
 * // 定义从等待支付到已支付的外部转换
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)
 *     .to(OrderStatus.PAID)
 *     .on(OrderEvent.PAY)
 *     .when(orderContext -> orderContext.getAmount().compareTo(BigDecimal.ZERO) > 0)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单[" + ctx.getOrderId() + "]支付完成，金额：" + ctx.getAmount());
 *         // 这里可以添加支付成功后的业务逻辑
 *     });
 *
 * // 定义从已支付到等待发货的外部转换
 * builder.externalTransition()
 *     .from(OrderStatus.PAID)
 *     .to(OrderStatus.WAIT_DELIVER)
 *     .on(OrderEvent.DELIVER)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单[" + ctx.getOrderId() + "]已准备发货");
 *         // 这里可以添加准备发货的业务逻辑
 *     });
 *
 * // 定义从等待发货到配送中的外部转换
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_DELIVER)
 *     .to(OrderStatus.DELIVERING)
 *     .on(OrderEvent.DELIVER)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单[" + ctx.getOrderId() + "]开始配送，配送地址：" + ctx.getDeliveryAddress());
 *         // 这里可以添加开始配送的业务逻辑
 *     });
 *
 * // 定义更新配送地址的内部转换（状态不变）
 * builder.internalTransition()
 *     .within(OrderStatus.WAIT_DELIVER)
 *     .on(OrderEvent.UPDATE_DELIVERY_ADDRESS)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单[" + ctx.getOrderId() + "]更新配送地址为：" + ctx.getDeliveryAddress());
 *         // 这里可以添加更新地址的业务逻辑
 *     });
 *
 * // 定义多个源状态到同一目标状态的转换
 * builder.externalTransitions()
 *     .fromAmong(OrderStatus.DELIVERING, OrderStatus.DELIVERED)
 *     .to(OrderStatus.COMPLETED)
 *     .on(OrderEvent.CONFIRM)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单[" + ctx.getOrderId() + "]已确认完成，原状态：" + from);
 *         // 这里可以添加订单完成的业务逻辑
 *     });
 *
 * // 构建状态机
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> orderStateMachine =
 *     builder.build("订单状态机");
 *
 * // 使用状态机处理订单状态转换
 * OrderContext context = new OrderContext();
 * context.setOrderId("ORD20230001");
 * context.setAmount(new BigDecimal("100.00"));
 * context.setDeliveryAddress("北京市海淀区中关村大街1号");
 *
 * // 触发支付事件，状态从WAIT_PAYMENT变为PAID
 * OrderStatus newStatus = orderStateMachine.fireEvent(OrderStatus.WAIT_PAYMENT, OrderEvent.PAY, context);
 * // 输出: 订单[ORD20230001]支付完成，金额：100.00
 *
 * // 触发发货事件，状态从PAID变为WAIT_DELIVER
 * newStatus = orderStateMachine.fireEvent(newStatus, OrderEvent.DELIVER, context);
 * // 输出: 订单[ORD20230001]已准备发货
 * </pre>
 */
public class GXStateMachineBuilderImpl<S, E, C> implements GXStateMachineBuilder<S, E, C> {
    /**
     * 状态映射表
     * <p>
     * 此映射表是状态机的核心，存储了所有状态及其关联的转换。
     * 使用线程安全的ConcurrentHashMap确保在多线程环境下的安全性。
     * <p>
     * 线程安全性：
     * <ul>
     *     <li>ConcurrentHashMap提供了线程安全的并发读写操作，适合高并发环境</li>
     *     <li>状态机构建阶段（添加状态和转换）通常在单线程中完成</li>
     *     <li>状态机运行阶段（触发事件和状态转换）可能在多线程环境中执行</li>
     * </ul>
     * <p>
     * 性能考虑：
     * <ul>
     *     <li>ConcurrentHashMap在读操作上有较高性能，适合状态机运行时的频繁查询</li>
     *     <li>状态映射表的大小通常与业务状态数量相关，一般不会很大</li>
     *     <li>预先构建所有状态和转换关系，避免运行时的动态构建开销</li>
     * </ul>
     */
    private final Map<S, GXState<S, E, C>> stateMap = new ConcurrentHashMap<>();

    /**
     * 状态机实例
     * <p>
     * 在构建器初始化时创建，并在build方法调用时完成配置。
     * 状态机实例持有对状态映射表的引用，用于在事件触发时查找和执行相应的状态转换。
     * <p>
     * 设计考虑：
     * <ul>
     *     <li>状态机实例在构建器初始化时就创建，避免了重复创建的开销</li>
     *     <li>状态机实例是不可变的，一旦构建完成就不应再修改其结构</li>
     *     <li>通过工厂模式注册和获取状态机，支持在应用不同部分共享同一状态机实例</li>
     * </ul>
     * <p>
     * 使用建议：
     * <ul>
     *     <li>一个业务流程通常对应一个状态机实例，如订单流程、支付流程等</li>
     *     <li>状态机ID应具有业务含义，便于识别和管理</li>
     *     <li>状态机实例可以在应用启动时构建并注册，然后在需要时通过ID获取</li>
     * </ul>
     */
    private final GXStateMachineImpl<S, E, C> stateMachine = new GXStateMachineImpl<>(stateMap);

    /**
     * 创建外部转换构建器
     * <p>
     * 外部转换是最常用的转换类型，当事件触发时，会完全退出源状态并进入目标状态。
     * 在这个过程中，源状态的退出动作和目标状态的进入动作都会被执行。
     * <p>
     * 使用场景：
     * <ul>
     *     <li>需要完全改变状态的场景，如订单从待支付变为已支付</li>
     *     <li>状态转换需要执行完整的退出和进入逻辑</li>
     *     <li>状态变化需要被明确感知和处理</li>
     * </ul>
     * <p>
     * 线程安全性：
     * <p>
     * 此方法返回的构建器实例不是线程安全的，应在单线程环境中使用。
     * 通常在应用初始化阶段调用，用于构建状态机。
     *
     * @return 外部转换构建器实例
     */
    @Override
    public GXExternalTransitionBuilder<S, E, C> externalTransition() {
        return new GXTransitionBuilderImpl<>(stateMap, GXTransitionType.EXTERNAL);
    }

    /**
     * 创建多重外部转换构建器
     * <p>
     * 多重外部转换允许定义多个源状态到同一个目标状态的转换规则，简化了状态机的配置。
     * 当多个不同状态需要在同一事件下转换到相同目标状态时，使用此方法可以减少重复代码。
     * <p>
     * 使用场景：
     * <ul>
     *     <li>多个状态共享相同的转换目标，如多个处理中状态都可以转为失败状态</li>
     *     <li>需要简化状态机配置，减少重复定义类似转换</li>
     *     <li>状态合并点的定义，如多个分支流程最终都归并到完成状态</li>
     * </ul>
     * <p>
     * 线程安全性：
     * <p>
     * 此方法返回的构建器实例不是线程安全的，应在单线程环境中使用。
     * 通常在应用初始化阶段调用，用于构建状态机。
     *
     * @return 多重外部转换构建器实例
     */
    @Override
    public GXExternalTransitionsBuilder<S, E, C> externalTransitions() {
        return new GXTransitionsBuilderImpl<>(stateMap, GXTransitionType.EXTERNAL);
    }

    /**
     * 创建内部转换构建器
     * <p>
     * 内部转换是指在同一状态内响应事件但不改变状态的转换。
     * 当事件触发时，不会执行状态的退出和进入动作，只执行转换定义的动作。
     * <p>
     * 使用场景：
     * <ul>
     *     <li>需要在不改变状态的情况下响应事件，如更新状态内部数据</li>
     *     <li>状态内部的自我调整，如更新订单配送地址但不改变订单状态</li>
     *     <li>需要避免状态退出和进入动作的执行开销</li>
     * </ul>
     * <p>
     * 线程安全性：
     * <p>
     * 此方法返回的构建器实例不是线程安全的，应在单线程环境中使用。
     * 通常在应用初始化阶段调用，用于构建状态机。
     *
     * @return 内部转换构建器实例
     */
    @Override
    public GXInternalTransitionBuilder<S, E, C> internalTransition() {
        return new GXTransitionBuilderImpl<>(stateMap, GXTransitionType.INTERNAL);
    }

    /**
     * 构建并返回状态机实例
     * <p>
     * 此方法完成状态机的构建过程，设置状态机ID，标记状态机为就绪状态，
     * 并将状态机注册到工厂中以便后续通过ID获取。
     * <p>
     * 构建过程是状态机生命周期中的最后一步，调用此方法后，状态机即可用于处理状态转换。
     * 注册到工厂的目的是支持在应用的不同部分通过ID获取同一个状态机实例。
     * <p>
     * 性能与安全考虑：
     * <ul>
     *     <li>构建是一次性操作，通常在应用启动时执行</li>
     *     <li>构建完成后，状态机实例是线程安全的，可以被多个线程并发使用</li>
     *     <li>状态机一旦构建完成，其结构（状态和转换定义）不应再被修改</li>
     * </ul>
     *
     * @param machineId 状态机的唯一标识符，用于在工厂中注册和后续获取
     * @return 构建完成的状态机实例，可立即使用
     */
    @Override
    public GXStateMachine<S, E, C> build(String machineId) {
        // 设置状态机ID
        stateMachine.setMachineId(machineId);
        // 标记状态机已准备就绪
        stateMachine.setReady(true);
        // 注册状态机到工厂，便于后续通过ID获取
        GXStateMachineFactory.register(stateMachine);
        return stateMachine;
    }
}
