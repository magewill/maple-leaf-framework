package cn.maple.statemachine.impl;

/**
 * 状态转换类型枚举
 * <p>
 * 定义了状态机中可能的转换类型，每种类型对应不同的状态转换行为。
 * 状态转换类型决定了在触发转换时，源状态和目标状态的进入/退出行为。
 * </p>
 *
 * <p>
 * 状态转换类型的选择对状态机的行为有重要影响：
 * <ul>
 *   <li>内部转换(INTERNAL)：在同一状态内处理事件，不触发状态变化</li>
 *   <li>本地转换(LOCAL)：在复合状态内部进行子状态转换，不退出父状态</li>
 *   <li>外部转换(EXTERNAL)：完全退出源状态，进入目标状态</li>
 * </ul>
 * </p>
 *
 * <p>
 * 在业务系统中的应用场景：
 * <ul>
 *   <li>订单系统：使用外部转换处理订单状态流转（如未支付→已支付→已发货）</li>
 *   <li>工作流引擎：使用内部转换处理状态内的数据更新，使用外部转换处理流程推进</li>
 *   <li>游戏状态：使用本地转换处理角色在某个场景内的状态变化</li>
 * </ul>
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 创建状态机构建器
 * GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = GXStateMachineBuilderFactory.create();
 *
 * // 1. 创建一个内部转换（不改变状态）
 * builder.internalTransition()
 *     .within(OrderStatus.PAID)  // 在已支付状态内
 *     .on(OrderEvent.UPDATE_INFO)  // 当收到更新信息事件时
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("更新订单信息：" + ctx.getOrderId());
 *         ctx.setUpdateTime(new Date());
 *     });
 *
 * // 2. 创建一个本地转换（在复合状态内转换）
 * // 注：此示例假设有复合状态的场景
 * builder.localTransition()
 *     .from(OrderStatus.SHIPPING.PACKING)  // 从打包子状态
 *     .to(OrderStatus.SHIPPING.DELIVERING)  // 到配送中子状态
 *     .on(OrderEvent.DELIVER)  // 当收到发货事件时
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单开始配送：" + ctx.getOrderId());
 *     });
 *
 * // 3. 创建一个外部转换（完全退出源状态）
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)  // 从等待支付状态
 *     .to(OrderStatus.PAID)  // 到已支付状态
 *     .on(OrderEvent.PAY)  // 当收到支付事件时
 *     .when(ctx -> ctx.getAmount() > 0)  // 当支付金额大于0时
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单已支付：" + ctx.getOrderId() + ", 金额：" + ctx.getAmount());
 *     });
 *
 * // 构建状态机
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine = builder.build("订单状态机");
 * }
 * </pre>
 * </p>
 */
public enum GXTransitionType {
    /**
     * Implies that the Transition, if triggered, occurs without exiting or entering the source State
     * (i.e., it does not cause a state change). This means that the entry or exit condition of the source
     * State will not be invoked. An internal Transition can be taken even if the SateMachine is in one or
     * more Regions nested within the associated State.
     * <p>
     * 内部转换：如果触发，转换发生时不会退出或进入源状态（即不会导致状态变化）。
     * 这意味着源状态的进入或退出条件不会被调用。即使状态机处于与关联状态嵌套的一个或多个区域中，
     * 内部转换也可以被执行。
     * <p>
     * 内部转换通常用于处理不需要改变当前状态的事件，例如更新状态内部的数据或执行某些操作。
     */
    INTERNAL,
    /**
     * Implies that the Transition, if triggered, will not exit the composite (source) State, but it
     * will exit and re-enter any state within the composite State that is in the current state configuration.
     * <p>
     * 本地转换：如果触发，转换不会退出复合（源）状态，但会退出并重新进入当前状态配置中复合状态内的任何状态。
     * <p>
     * 本地转换适用于复合状态（包含子状态的状态）场景，它允许在不完全退出父状态的情况下改变子状态。
     * 这种转换类型在需要保持父状态上下文的同时改变内部状态时非常有用。
     */
    LOCAL,
    /**
     * Implies that the Transition, if triggered, will exit the composite (source) State.
     * <p>
     * 外部转换：如果触发，转换将退出复合（源）状态。
     * <p>
     * 外部转换是最常见的转换类型，它会完全退出源状态并进入目标状态。在这个过程中，
     * 源状态的退出动作和目标状态的进入动作都会被执行。当需要完全改变系统状态时，应使用外部转换。
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 从等待支付状态转换到已支付状态
     * builder.externalTransition()
     *     .from(OrderStatus.WAIT_PAYMENT)
     *     .to(OrderStatus.PAID)
     *     .on(OrderEvent.PAY)
     *     .perform((from, to, event, ctx) -> {
     *         System.out.println("订单已支付：" + ctx.getOrderId());
     *     });
     * }
     * </pre>
     */
    EXTERNAL
}
