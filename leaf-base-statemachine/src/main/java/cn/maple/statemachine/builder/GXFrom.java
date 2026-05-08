package cn.maple.statemachine.builder;

/**
 * 状态转换的源状态构建器接口
 * <p>
 * 在状态机的构建过程中，此接口用于指定状态转换的目标状态。
 * 它是构建状态转换链中的第二步，在指定源状态(from)之后使用。
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
 * // 定义状态转换，从PENDING到APPROVED
 * builder.externalTransition()
 *     .from(States.PENDING)    // 指定源状态
 *     .to(States.APPROVED)     // 指定目标状态
 *     .on(Events.APPROVE)      // 指定触发事件
 *     .perform((from, to, event, ctx) -> {
 *         // 执行转换动作
 *     });
 * </pre>
 */
public interface GXFrom<S, E, C> {
    /**
     * 构建转换的目标状态
     * <p>
     * 此方法指定状态转换的目标状态，是构建状态转换的第二步
     *
     * @param stateId 目标状态的标识符，类型为S（由使用者定义的状态类型）
     * @return 返回To子句构建器，用于继续构建转换链
     */
    GXTo<S, E, C> to(S stateId);
}
