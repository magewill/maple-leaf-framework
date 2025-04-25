package cn.maple.statemachine.builder;

/**
 * 内部状态转换构建器接口
 * <p>
 * 用于构建状态机中的内部状态转换。内部转换是指状态不发生改变，但会执行相应的动作。
 * 与外部转换不同，内部转换不会触发状态的退出和进入动作，只会执行转换本身定义的动作。
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
 * // 使用内部转换构建器定义一个在PROCESSING状态内的转换
 * builder.internalTransition()
 *     .within(States.PROCESSING)
 *     .on(Events.UPDATE)
 *     .when(context -> context.needsUpdate())
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("更新处理中的数据");
 *         ctx.updateProgress(10);
 *     });
 * 
 * // 构建状态机
 * GXStateMachine<States, Events, Context> stateMachine = builder.build("processStateMachine");
 * </pre>
 */
public interface GXInternalTransitionBuilder<S, E, C> {
    /**
     * 构建内部转换的状态
     * <p>
     * 此方法指定内部转换发生的状态，内部转换不会改变状态，只会在同一状态内执行动作
     *
     * @param stateId 状态标识符，类型为S（由使用者定义的状态类型）
     * @return 返回To子句构建器，用于继续构建转换链
     */
    GXTo<S, E, C> within(S stateId);
}
