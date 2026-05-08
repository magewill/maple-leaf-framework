package cn.maple.statemachine.builder;

/**
 * 外部状态转换构建器接口
 * <p>
 * 用于构建状态机中的外部状态转换。外部转换是指从一个状态转换到另一个不同的状态。
 * 外部转换会触发源状态的退出动作和目标状态的进入动作。
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
 * // 创建状态机构建器
 * GXStateMachineBuilder<States, Events, Context> builder = GXStateMachineBuilderFactory.create();
 *
 * // 使用外部转换构建器定义一个从PENDING状态到APPROVED状态的转换
 * builder.externalTransition()
 *     .from(States.PENDING)
 *     .to(States.APPROVED)
 *     .on(Events.APPROVE)
 *     .when(context -> context.isValid())
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("从 " + from + " 转换到 " + to);
 *         ctx.recordTransition();
 *     });
 *
 * // 构建状态机
 * GXStateMachine<States, Events, Context> stateMachine = builder.build("orderStateMachine");
 * </pre>
 */
public interface GXExternalTransitionBuilder<S, E, C> {
    /**
     * 构建转换的源状态
     * <p>
     * 此方法指定状态转换的起始状态，是构建状态转换的第一步
     *
     * @param stateId 状态标识符，类型为S（由使用者定义的状态类型）
     * @return 返回From子句构建器，用于继续构建转换链
     */
    GXFrom<S, E, C> from(S stateId);
}
