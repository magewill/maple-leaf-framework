package cn.maple.statemachine;

/**
 * 状态上下文接口
 * <p>
 * 该接口定义了状态转换过程中的上下文环境，提供了访问当前转换和状态机的方法。
 * 状态上下文在状态转换过程中起到桥梁作用，连接了状态机、状态、转换和用户自定义上下文。
 * </p>
 * <p>
 * 状态上下文在状态机中的应用：
 * <ul>
 *   <li>提供对当前转换的访问，包括源状态、目标状态、事件等信息</li>
 *   <li>提供对状态机的访问，便于在动作中执行其他状态转换</li>
 *   <li>作为状态转换过程中的环境容器，传递必要的信息</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 定义一个使用状态上下文的动作
 * GXAction<OrderStatus, OrderEvent, OrderContext> complexAction =
 *     (from, to, event, ctx) -> {
 *         // 在实际应用中，可以通过状态上下文获取更多信息
 *         // 例如，如果有一个状态上下文实现类：
 *         // GXStateContext<OrderStatus, OrderEvent, OrderContext> stateContext = ...;
 *
 *         // 获取当前转换信息
 *         GXTransition<OrderStatus, OrderEvent, OrderContext> transition =
 *             stateContext.getTransition();
 *         System.out.println("当前转换：" + transition);
 *
 *         // 获取状态机实例，可用于触发其他事件
 *         GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine =
 *             stateContext.getStateMachine();
 *
 *         // 执行业务逻辑
 *         System.out.println("订单状态从 " + from + " 变更为 " + to);
 *     };
 * }
 * </pre>
 * </p>
 *
 * @param <S> 状态类型，通常使用枚举或字符串
 * @param <E> 事件类型，通常使用枚举或字符串
 * @param <C> 用户自定义上下文类型，用于在状态转换过程中传递数据
 * @see GXTransition 转换接口，表示状态之间的转换规则
 * @see GXStateMachine 状态机接口，定义状态机的核心功能
 */
public interface GXStateContext<S, E, C> {
    /**
     * 获取当前转换
     * <p>
     * 返回触发当前状态上下文的转换对象，包含源状态、目标状态、事件等信息。
     * 通过此方法可以访问转换的详细信息，如条件、动作等。
     * </p>
     *
     * @return 当前转换对象
     */
    GXTransition<S, E, C> getTransition();

    /**
     * 获取状态机
     * <p>
     * 返回当前状态上下文所属的状态机实例。
     * 通过此方法可以访问状态机的功能，如触发其他事件、查询状态机ID等。
     * </p>
     *
     * @return 状态机实例
     */
    GXStateMachine<S, E, C> getStateMachine();
}
