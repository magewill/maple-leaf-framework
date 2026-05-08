package cn.maple.statemachine.builder;

/**
 * 外部多状态转换构建器接口
 * <p>
 * 此构建器用于创建多个状态转换，当前仅支持多个源状态到一个目标状态的转换模式（多对一）。
 * 使用此接口可以简化多个源状态共享同一个目标状态的转换定义，避免重复代码。
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
 * // 定义多个源状态到一个目标状态的转换
 * builder.externalTransitions()
 *     .fromAmong(States.STATE1, States.STATE2, States.STATE3) // 指定多个源状态
 *     .to(States.STATE4)                                     // 指定目标状态
 *     .on(Events.EVENT1)                                     // 指定触发事件
 *     .when(context -> context.getData() != null)            // 可选：添加转换条件
 *     .perform((from, to, event, ctx) -> {                   // 可选：添加转换动作
 *         System.out.println("从状态 " + from + " 转换到状态 " + to);
 *     });
 * </pre>
 */
public interface GXExternalTransitionsBuilder<S, E, C> {
    /**
     * 指定多个源状态
     *
     * @param stateIds 源状态ID集合，可以是多个状态
     * @return 源状态构建器接口，用于继续构建状态转换
     */
    GXFrom<S, E, C> fromAmong(S... stateIds);
}
