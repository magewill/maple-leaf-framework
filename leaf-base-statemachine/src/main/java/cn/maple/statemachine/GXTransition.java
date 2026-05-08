package cn.maple.statemachine;

import cn.maple.statemachine.impl.GXTransitionType;

/**
 * 状态转换接口
 * <p>
 * 状态转换是状态机的核心组件，定义了从一个状态到另一个状态的转换规则。
 * 每个转换包含源状态、目标状态、触发事件、转换条件和转换动作等元素。
 * 当状态机接收到事件时，会根据当前状态和事件查找匹配的转换，并在满足条件的情况下执行状态转换。
 * </p>
 * <p>
 * 状态转换的主要组成部分：
 * <ul>
 *   <li>源状态(Source State)：转换的起始状态</li>
 *   <li>目标状态(Target State)：转换的目标状态</li>
 *   <li>事件(Event)：触发转换的事件</li>
 *   <li>条件(Condition)：转换执行的前提条件，可选</li>
 *   <li>动作(Action)：转换过程中执行的操作，可选</li>
 *   <li>类型(Type)：转换类型，如INTERNAL、LOCAL或EXTERNAL</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 在状态机构建过程中创建和配置转换
 * GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = GXStateMachineBuilderFactory.create();
 *
 * // 创建一个外部转换
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)  // 设置源状态
 *     .to(OrderStatus.PAID)           // 设置目标状态
 *     .on(OrderEvent.PAY)             // 设置触发事件
 *     .when(ctx -> ctx.getAmount() > 0) // 设置转换条件
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单已支付：" + ctx.getOrderId());
 *         // 执行支付后的业务逻辑
 *     });                             // 设置转换动作
 *
 * // 创建一个内部转换（不改变状态）
 * builder.internalTransition()
 *     .within(OrderStatus.PAID)        // 设置状态
 *     .on(OrderEvent.UPDATE_INFO)      // 设置触发事件
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("更新订单信息：" + ctx.getOrderId());
 *         // 执行更新订单信息的业务逻辑
 *     });                             // 设置转换动作
 * }
 * </pre>
 * </p>
 *
 * @param <S> 状态类型，通常使用枚举或字符串
 * @param <E> 事件类型，通常使用枚举或字符串
 * @param <C> 用户自定义上下文类型，用于在状态转换过程中传递数据
 * @see GXState 状态接口
 * @see GXCondition 条件接口
 * @see GXAction 动作接口
 * @see cn.maple.statemachine.impl.GXTransitionType 转换类型枚举
 * @see cn.maple.statemachine.impl.GXTransitionImpl 转换实现类
 */
public interface GXTransition<S, E, C> {
    /**
     * 获取转换的源状态
     * <p>
     * 源状态是转换的起始状态，只有当系统处于该状态时，才可能触发此转换。
     * </p>
     *
     * @return 源状态对象
     */
    GXState<S, E, C> getSource();

    /**
     * 设置转换的源状态
     * <p>
     * 用于在构建转换时设置源状态。
     * </p>
     *
     * @param state 源状态对象
     */
    void setSource(GXState<S, E, C> state);

    /**
     * 获取触发转换的事件
     * <p>
     * 事件是触发状态转换的信号，当状态机接收到该事件时，会查找匹配的转换。
     * </p>
     *
     * @return 事件对象
     */
    E getEvent();

    /**
     * 设置触发转换的事件
     * <p>
     * 用于在构建转换时设置触发事件。
     * </p>
     *
     * @param event 事件对象
     */
    void setEvent(E event);

    /**
     * 设置转换类型
     * <p>
     * 转换类型决定了状态转换的行为方式，包括：
     * <ul>
     *   <li>INTERNAL：内部转换，不退出当前状态</li>
     *   <li>LOCAL：本地转换，退出并重新进入当前状态的子状态</li>
     *   <li>EXTERNAL：外部转换，完全退出当前状态并进入新状态</li>
     * </ul>
     * </p>
     *
     * @param type 转换类型
     * @see cn.maple.statemachine.impl.GXTransitionType 转换类型枚举
     */
    void setType(GXTransitionType type);

    /**
     * 获取转换的目标状态
     * <p>
     * 目标状态是转换完成后系统将进入的新状态。对于内部转换(INTERNAL)，
     * 目标状态通常与源状态相同。
     * </p>
     *
     * @return 目标状态对象
     */
    GXState<S, E, C> getTarget();

    /**
     * 设置转换的目标状态
     * <p>
     * 用于在构建转换时设置目标状态。
     * </p>
     *
     * @param state 目标状态对象
     */
    void setTarget(GXState<S, E, C> state);

    /**
     * 获取转换条件
     * <p>
     * 转换条件决定了在接收到匹配事件时，是否执行状态转换。
     * 只有当条件满足时，才会执行转换。如果没有设置条件，则默认执行转换。
     * </p>
     *
     * @return 条件对象，如果没有设置条件则可能返回null
     * @see GXCondition 条件接口
     */
    GXCondition<C> getCondition();

    /**
     * 设置转换条件
     * <p>
     * 用于在构建转换时设置条件。
     * </p>
     *
     * @param condition 条件对象
     */
    void setCondition(GXCondition<C> condition);

    /**
     * 获取转换动作
     * <p>
     * 转换动作是在执行状态转换过程中要执行的操作。
     * 动作通常用于执行与状态变化相关的业务逻辑。
     * </p>
     *
     * @return 动作对象，如果没有设置动作则可能返回null
     * @see GXAction 动作接口
     */
    GXAction<S, E, C> getAction();

    /**
     * 设置转换动作
     * <p>
     * 用于在构建转换时设置动作。
     * </p>
     *
     * @param action 动作对象
     */
    void setAction(GXAction<S, E, C> action);

    /**
     * 执行状态转换
     * <p>
     * 根据上下文和是否检查条件参数，执行从源状态到目标状态的转换。
     * 如果设置了条件且需要检查条件，则只有在条件满足时才会执行转换。
     * 如果设置了动作，则在转换过程中会执行该动作。
     * </p>
     *
     * @param ctx            上下文对象，包含转换可能需要的数据
     * @param checkCondition 是否检查条件，如果为true则会检查条件是否满足
     * @return 转换后的目标状态，如果条件不满足则返回源状态
     */
    GXState<S, E, C> transit(C ctx, boolean checkCondition);

    /**
     * 验证转换的正确性
     * <p>
     * 检查转换配置是否正确，例如对于内部转换(INTERNAL)，
     * 源状态和目标状态必须相同。
     * </p>
     *
     * @throws cn.maple.statemachine.impl.GXStateMachineException 如果转换配置不正确
     */
    void verify();
}
