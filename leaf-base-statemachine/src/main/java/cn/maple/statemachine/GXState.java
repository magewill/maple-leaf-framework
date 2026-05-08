package cn.maple.statemachine;

import cn.maple.statemachine.impl.GXTransitionType;

import java.util.Collection;
import java.util.List;

/**
 * 状态接口
 * <p>
 * 状态是状态机的基本组成元素，代表系统在特定时刻的状态。
 * 每个状态可以有多个出站转换，当接收到特定事件时，系统会根据当前状态和事件查找匹配的转换，
 * 并可能转换到新的状态。
 * </p>
 * <p>
 * 状态的主要职责：
 * <ul>
 *   <li>维护状态标识符</li>
 *   <li>管理从该状态出发的所有转换</li>
 *   <li>提供查询转换的方法</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 在状态机构建过程中使用状态
 * GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = GXStateMachineBuilderFactory.create();
 *
 * // 添加从WAIT_PAYMENT到PAID的转换
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT) // 源状态
 *     .to(OrderStatus.PAID)          // 目标状态
 *     .on(OrderEvent.PAY);           // 触发事件
 *
 * // 构建状态机后，可以通过状态机获取状态对象
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine = builder.build("订单状态机");
 *
 * // 在访问者模式中使用状态
 * stateMachine.accept(new GXVisitor() {
 *     @Override
 *     public String visitOnEntry(GXState<?, ?, ?> state) {
 *         System.out.println("当前状态: " + state.getId());
 *         // 获取该状态的所有转换
 *         Collection<GXTransition<?, ?, ?>> transitions = state.getAllTransitions();
 *         for (GXTransition<?, ?, ?> transition : transitions) {
 *             System.out.println("  转换: " + transition);
 *         }
 *         return "";
 *     }
 *     // 其他访问者方法实现...
 * });
 * }
 * </pre>
 * </p>
 *
 * @param <S> 状态类型，通常使用枚举或字符串
 * @param <E> 事件类型，通常使用枚举或字符串
 * @param <C> 上下文类型，用于在状态转换过程中传递数据
 * @see GXStateMachine 状态机接口
 * @see GXTransition 转换接口
 * @see cn.maple.statemachine.impl.GXStateImpl 状态实现类
 */
public interface GXState<S, E, C> extends GXVisitable {
    /**
     * 获取状态标识符
     * <p>
     * 状态标识符用于唯一标识一个状态，通常使用枚举值或字符串。
     * </p>
     *
     * @return 状态标识符
     */
    S getId();

    /**
     * 添加状态转换
     * <p>
     * 为当前状态添加一个新的转换，指定触发事件、目标状态和转换类型。
     * 转换类型可以是内部转换(INTERNAL)、本地转换(LOCAL)或外部转换(EXTERNAL)。
     * </p>
     *
     * @param event          触发转换的事件
     * @param target         转换的目标状态
     * @param transitionType 转换类型，如INTERNAL、LOCAL或EXTERNAL
     * @return 新创建的转换对象
     * @see cn.maple.statemachine.impl.GXTransitionType 转换类型枚举
     */
    GXTransition<S, E, C> addTransition(E event, GXState<S, E, C> target, GXTransitionType transitionType);

    /**
     * 获取指定事件的所有转换
     * <p>
     * 返回当前状态下，由指定事件触发的所有可能转换。
     * 一个事件可能触发多个转换，具体使用哪个转换取决于条件(Condition)的满足情况。
     * </p>
     *
     * @param event 事件类型
     * @return 转换列表，如果没有匹配的转换则可能返回空列表或null
     */
    List<GXTransition<S, E, C>> getEventTransitions(E event);

    /**
     * 获取所有转换
     * <p>
     * 返回当前状态下的所有可能转换，包括由不同事件触发的所有转换。
     * </p>
     *
     * @return 包含所有转换的集合
     */
    Collection<GXTransition<S, E, C>> getAllTransitions();
}
