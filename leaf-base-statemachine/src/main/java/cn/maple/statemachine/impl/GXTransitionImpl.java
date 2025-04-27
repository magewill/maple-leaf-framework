package cn.maple.statemachine.impl;

import cn.maple.statemachine.GXAction;
import cn.maple.statemachine.GXCondition;
import cn.maple.statemachine.GXState;
import cn.maple.statemachine.GXTransition;

/**
 * 状态转换实现类
 * <p>
 * 此类实现了{@link GXTransition}接口，代表状态机中的一个转换。
 * 转换定义了从一个状态到另一个状态的规则，包括源状态、目标状态、触发事件、条件和动作。
 * </p>
 * <p>
 * 设计为不可变对象，以避免线程安全风险。一旦转换被创建和配置完成，
 * 其属性不应被修改，这样可以安全地在多线程环境中共享使用。
 * </p>
 * <p>
 * 主要组成部分：
 * <ul>
 *   <li>源状态(Source)：转换的起始状态</li>
 *   <li>目标状态(Target)：转换的目标状态</li>
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
 * // 创建转换
 * GXTransitionImpl<OrderStatus, OrderEvent, OrderContext> transition = new GXTransitionImpl<>();
 *
 * // 设置源状态和目标状态
 * transition.setSource(waitPaymentState);
 * transition.setTarget(paidState);
 *
 * // 设置触发事件
 * transition.setEvent(OrderEvent.PAY);
 *
 * // 设置转换类型
 * transition.setType(GXTransitionType.EXTERNAL);
 *
 * // 设置转换条件
 * transition.setCondition(ctx -> ctx.getAmount() > 0);
 *
 * // 设置转换动作
 * transition.setAction((from, to, event, ctx) -> {
 *     System.out.println("订单已支付：" + ctx.getOrderId());
 *     // 执行支付后的业务逻辑
 * });
 * }
 * </pre>
 */
public class GXTransitionImpl<S, E, C> implements GXTransition<S, E, C> {

    /**
     * 源状态
     * <p>
     * 转换的起始状态，转换发生时将从此状态开始。
     */
    private GXState<S, E, C> source;

    /**
     * 目标状态
     * <p>
     * 转换的目标状态，转换成功后将进入此状态。
     * 对于内部转换(INTERNAL)，目标状态必须与源状态相同。
     */
    private GXState<S, E, C> target;

    /**
     * 事件
     * <p>
     * 触发此转换的事件。当状态机接收到此事件时，
     * 如果当前状态匹配源状态且满足转换条件，将执行此转换。
     */
    private E event;

    /**
     * 条件
     * <p>
     * 转换执行的前提条件，可选。
     * 只有当条件满足（返回true）时，转换才会执行。
     * 如果未设置条件，则视为无条件转换。
     */
    private GXCondition<C> condition;

    /**
     * 动作
     * <p>
     * 转换过程中执行的操作，可选。
     * 当转换发生时，将执行此动作，可用于实现业务逻辑。
     */
    private GXAction<S, E, C> action;

    /**
     * 转换类型
     * <p>
     * 定义转换的行为方式，默认为外部转换(EXTERNAL)。
     * 可选值：
     * <ul>
     *   <li>INTERNAL：内部转换，不退出源状态</li>
     *   <li>LOCAL：本地转换，不退出复合源状态但会退出子状态</li>
     *   <li>EXTERNAL：外部转换，完全退出源状态</li>
     * </ul>
     */
    private GXTransitionType type = GXTransitionType.EXTERNAL;

    /**
     * 获取源状态
     * <p>
     * 返回此转换的源状态（起始状态）。
     *
     * @return 源状态对象
     */
    @Override
    public GXState<S, E, C> getSource() {
        return source;
    }

    /**
     * 设置源状态
     * <p>
     * 设置此转换的源状态（起始状态）。
     *
     * @param state 源状态对象
     */
    @Override
    public void setSource(GXState<S, E, C> state) {
        this.source = state;
    }

    /**
     * 获取事件
     * <p>
     * 返回触发此转换的事件。
     *
     * @return 事件对象
     */
    @Override
    public E getEvent() {
        return this.event;
    }

    /**
     * 设置事件
     * <p>
     * 设置触发此转换的事件。
     *
     * @param event 事件对象
     */
    @Override
    public void setEvent(E event) {
        this.event = event;
    }

    /**
     * 设置转换类型
     * <p>
     * 设置此转换的类型，如INTERNAL、LOCAL或EXTERNAL。
     * 转换类型决定了状态转换的行为方式。
     *
     * @param type 转换类型
     */
    @Override
    public void setType(GXTransitionType type) {
        this.type = type;
    }

    /**
     * 获取目标状态
     * <p>
     * 返回此转换的目标状态。
     *
     * @return 目标状态对象
     */
    @Override
    public GXState<S, E, C> getTarget() {
        return this.target;
    }

    /**
     * 设置目标状态
     * <p>
     * 设置此转换的目标状态。
     * 对于内部转换(INTERNAL)，目标状态必须与源状态相同。
     *
     * @param target 目标状态对象
     */
    @Override
    public void setTarget(GXState<S, E, C> target) {
        this.target = target;
    }

    /**
     * 获取条件
     * <p>
     * 返回此转换的执行条件。
     * 如果未设置条件，返回null。
     *
     * @return 条件对象，如果没有则返回null
     */
    @Override
    public GXCondition<C> getCondition() {
        return this.condition;
    }

    /**
     * 设置条件
     * <p>
     * 设置此转换的执行条件。
     * 只有当条件满足（返回true）时，转换才会执行。
     *
     * @param condition 条件对象
     */
    @Override
    public void setCondition(GXCondition<C> condition) {
        this.condition = condition;
    }

    /**
     * 获取动作
     * <p>
     * 返回此转换的执行动作。
     * 如果未设置动作，返回null。
     *
     * @return 动作对象，如果没有则返回null
     */
    @Override
    public GXAction<S, E, C> getAction() {
        return this.action;
    }

    @Override
    public void setAction(GXAction<S, E, C> action) {
        this.action = action;
    }

    @Override
    public GXState<S, E, C> transit(C ctx, boolean checkCondition) {
        GXDebugger.debug("Do transition: " + this);
        this.verify();
        if (!checkCondition || condition == null || condition.isSatisfied(ctx)) {
            if (action != null) {
                action.execute(source.getId(), target.getId(), event, ctx);
            }
            return target;
        }

        GXDebugger.debug("Condition is not satisfied, stay at the " + source + " state ");
        return source;
    }

    @Override
    public final String toString() {
        return source + "-[" + event.toString() + ", " + type + "]->" + target;
    }

    @Override
    public boolean equals(Object anObject) {
        if (anObject instanceof GXTransition<?, ?, ?> other) {
            return this.event.equals(other.getEvent())
                    && this.source.equals(other.getSource())
                    && this.target.equals(other.getTarget());
        }
        return false;
    }

    @Override
    public void verify() {
        if (type == GXTransitionType.INTERNAL && source != target) {
            throw new GXStateMachineException(String.format("Internal transition source state '%s' " +
                    "and target state '%s' must be same.", source, target));
        }
    }
}
