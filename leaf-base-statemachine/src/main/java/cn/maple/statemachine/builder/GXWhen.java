package cn.maple.statemachine.builder;

import cn.maple.statemachine.GXAction;

/**
 * 状态转换的条件构建器接口
 * <p>
 * 在状态机的构建过程中，此接口用于为状态转换添加执行动作。
 * 它是构建状态转换链中的最后一步，在指定条件(when)之后或直接在指定事件(on)之后使用。
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
 *     .when(context -> context.isValid())
 *     .perform((from, to, event, ctx) -> {
 *         // 执行状态转换时的动作
 *         System.out.println("状态从 " + from + " 转换到 " + to);
 *         ctx.setApprovedTime(new Date());
 *         ctx.sendNotification("您的申请已被批准");
 *     });
 *
 * // 也可以不添加条件，直接添加动作
 * builder.externalTransition()
 *     .from(States.APPROVED)
 *     .to(States.COMPLETED)
 *     .on(Events.COMPLETE)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("处理完成");
 *     });
 * </pre>
 */
public interface GXWhen<S, E, C> {
    /**
     * 定义状态转换过程中要执行的动作
     * <p>
     * 此方法用于添加一个在状态转换发生时要执行的动作。
     * 动作可以用于记录日志、发送通知、更新数据等操作。
     * 如果不添加动作，状态转换将只改变状态而不执行任何附加操作。
     *
     * @param action 要执行的动作，一个实现了GXAction接口的对象
     *               该动作将在状态转换时被调用，并接收源状态、目标状态、事件和上下文作为参数
     */
    void perform(GXAction<S, E, C> action);
}
