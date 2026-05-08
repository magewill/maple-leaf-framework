package cn.maple.statemachine.builder;

import cn.maple.statemachine.GXCondition;

/**
 * 状态转换的事件构建器接口
 * <p>
 * 在状态机的构建过程中，此接口用于为状态转换添加条件。
 * 它是构建状态转换链中的第四步，在指定事件(on)之后使用。
 * 此接口继承自GXWhen接口，因此也可以直接调用perform方法添加转换动作。
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
 * // 定义状态转换
 * builder.externalTransition()
 *     .from(States.PENDING)
 *     .to(States.APPROVED)
 *     .on(Events.APPROVE)
 *     .when(context -> {
 *         // 只有当上下文满足特定条件时才允许转换
 *         return context.isValid() && context.hasPermission();
 *     })
 *     .perform((from, to, event, ctx) -> {
 *         // 执行转换动作
 *         System.out.println("状态从 " + from + " 转换到 " + to);
 *     });
 * </pre>
 */
public interface GXOn<S, E, C> extends GXWhen<S, E, C> {
    /**
     * 为状态转换添加条件
     * <p>
     * 此方法用于添加一个条件，只有当条件满足时，状态转换才会执行。
     * 如果不添加条件，状态转换将无条件执行。
     *
     * @param condition 转换条件，一个实现了GXCondition接口的对象，用于判断是否满足转换条件
     * @return 返回When子句构建器，用于继续构建转换链，可以添加转换动作
     */
    GXWhen<S, E, C> when(GXCondition<C> condition);
}
