package cn.maple.statemachine.builder;

import cn.maple.statemachine.GXStateMachine;

/**
 * 状态机构建器接口
 * <p>
 * 此接口定义了构建状态机的核心方法，包括创建外部转换、多重外部转换和内部转换。
 * 通过链式调用的方式，可以流畅地定义状态机的结构和行为。
 * <p>
 * 泛型参数说明：
 * <ul>
 *     <li>S - 状态(State)类型，通常使用枚举或字符串</li>
 *     <li>E - 事件(Event)类型，通常使用枚举或字符串</li>
 *     <li>C - 上下文(Context)类型，用于在转换过程中传递数据</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * // 定义状态和事件枚举
 * enum States { STATE1, STATE2, STATE3 }
 * enum Events { EVENT1, EVENT2 }
 *
 * // 创建状态机构建器
 * GXStateMachineBuilder<States, Events, Context> builder = GXStateMachineBuilderFactory.create();
 *
 * // 定义状态转换
 * builder.externalTransition()
 *     .from(States.STATE1)
 *     .to(States.STATE2)
 *     .on(Events.EVENT1)
 *     .when(context -> context.getData() != null)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("状态从 " + from + " 转换到 " + to);
 *     });
 *
 * // 构建状态机
 * GXStateMachine<States, Events, Context> stateMachine = builder.build("示例状态机");
 *
 * // 使用状态机
 * Context context = new Context();
 * context.setData("测试数据");
 * States newState = stateMachine.fireEvent(States.STATE1, Events.EVENT1, context);
 * </pre>
 */
public interface GXStateMachineBuilder<S, E, C> {
    /**
     * 创建单个外部转换的构建器
     * <p>
     * 外部转换是指源状态和目标状态不同的转换。
     * 当事件触发时，如果满足条件，状态将从源状态变为目标状态。
     *
     * @return 外部转换构建器
     */
    GXExternalTransitionBuilder<S, E, C> externalTransition();

    /**
     * 创建多重外部转换的构建器
     * <p>
     * 多重外部转换允许定义多个源状态到一个目标状态的转换，
     * 简化了多个源状态共享同一个目标状态的转换定义。
     *
     * @return 多重外部转换构建器
     */
    GXExternalTransitionsBuilder<S, E, C> externalTransitions();

    /**
     * 创建内部转换的构建器
     * <p>
     * 内部转换是指源状态和目标状态相同的转换。
     * 当事件触发时，如果满足条件，将执行动作但不改变状态。
     *
     * @return 内部转换构建器
     */
    GXInternalTransitionBuilder<S, E, C> internalTransition();

    /**
     * 构建状态机
     * <p>
     * 完成所有转换定义后，调用此方法构建并返回可用的状态机实例。
     * 构建过程会注册状态机到工厂中，以便后续可以通过ID获取。
     *
     * @param machineId 状态机唯一标识符
     * @return 构建好的状态机实例
     */
    GXStateMachine<S, E, C> build(String machineId);
}
