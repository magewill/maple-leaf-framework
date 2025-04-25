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
 * 线程安全性考虑：
 * <ul>
 *   <li>状态转换对象在构建完成后应视为不可变对象，避免在运行时修改其属性</li>
 *   <li>转换条件和动作应设计为线程安全的，特别是在高并发环境下</li>
 *   <li>transit方法在执行时不依赖共享状态，确保在多线程环境下的安全性</li>
 *   <li>equals和toString方法不修改对象状态，可以安全地在多线程环境中调用</li>
 * </ul>
 * </p>
 * <p>
 * 性能优化：
 * <ul>
 *   <li>转换条件应尽量简单高效，避免复杂的计算和IO操作</li>
 *   <li>动作执行应考虑异步处理，避免阻塞状态转换流程</li>
 *   <li>equals方法优化比较顺序，先比较最可能不同的属性，提高比较效率</li>
 *   <li>verify方法在转换执行前进行验证，避免无效转换的执行</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建订单状态和事件枚举
 * public enum OrderStatus { WAIT_PAYMENT, PAID, SHIPPED, COMPLETED }
 * public enum OrderEvent { PAY, SHIP, RECEIVE }
 * 
 * // 2. 创建订单上下文类
 * public class OrderContext {
 *     private String orderId;
 *     private double amount;
 *     // getter和setter方法
 * }
 * 
 * // 3. 创建状态对象
 * GXStateImpl<OrderStatus, OrderEvent, OrderContext> waitPaymentState = 
 *     new GXStateImpl<>(OrderStatus.WAIT_PAYMENT);
 * GXStateImpl<OrderStatus, OrderEvent, OrderContext> paidState = 
 *     new GXStateImpl<>(OrderStatus.PAID);
 * 
 * // 4. 创建并配置转换对象
 * GXTransitionImpl<OrderStatus, OrderEvent, OrderContext> transition = 
 *     new GXTransitionImpl<>();
 * transition.setSource(waitPaymentState);
 * transition.setTarget(paidState);
 * transition.setEvent(OrderEvent.PAY);
 * transition.setType(GXTransitionType.EXTERNAL);
 * 
 * // 5. 设置转换条件（可选）
 * transition.setCondition(ctx -> ctx.getAmount() > 0);
 * 
 * // 6. 设置转换动作（可选）
 * transition.setAction((from, to, event, ctx) -> {
 *     System.out.println("订单 " + ctx.getOrderId() + " 已支付，金额：" + ctx.getAmount());
 *     // 执行支付后的业务逻辑，如发送通知、更新数据库等
 * });
 * 
 * // 7. 执行状态转换
 * OrderContext context = new OrderContext();
 * context.setOrderId("ORD123456");
 * context.setAmount(100.0);
 * GXState<OrderStatus, OrderEvent, OrderContext> newState = 
 *     transition.transit(context, true);
 * System.out.println("新状态：" + newState.getId()); // 输出：新状态：PAID
 * </pre>
 * </p>
 */
public class GXTransitionImpl<S, E, C> implements GXTransition<S, E, C> {

    /**
     * 源状态
     * <p>
     * 转换的起始状态，转换发生时将从此状态开始。
     * 在状态机中，只有当前状态与源状态匹配时，才会考虑此转换。
     * </p>
     * <p>
     * 注意：此字段在转换配置完成后不应再被修改，以保证线程安全性。
     * </p>
     */
    private GXState<S, E, C> source;

    /**
     * 目标状态
     * <p>
     * 转换的目标状态，转换成功后将进入此状态。
     * 对于内部转换(INTERNAL)，目标状态必须与源状态相同。
     * </p>
     * <p>
     * 注意：此字段在转换配置完成后不应再被修改，以保证线程安全性。
     * </p>
     */
    private GXState<S, E, C> target;

    /**
     * 事件
     * <p>
     * 触发此转换的事件。当状态机接收到此事件时，
     * 如果当前状态匹配源状态且满足转换条件，将执行此转换。
     * </p>
     * <p>
     * 事件通常使用枚举类型定义，以确保类型安全和代码可读性。
     * </p>
     * <p>
     * 注意：此字段在转换配置完成后不应再被修改，以保证线程安全性。
     * </p>
     */
    private E event;

    /**
     * 条件
     * <p>
     * 转换执行的前提条件，可选。
     * 只有当条件满足（返回true）时，转换才会执行。
     * 如果未设置条件，则视为无条件转换。
     * </p>
     * <p>
     * 条件应设计为无副作用的纯函数，避免修改上下文对象或其他共享状态。
     * 这样可以确保在多线程环境下的安全性，并允许条件被多次评估而不产生副作用。
     * </p>
     * <p>
     * 性能考虑：条件应尽量简单高效，避免复杂的计算和IO操作，以免影响状态机的响应性能。
     * </p>
     */
    private GXCondition<C> condition;

    /**
     * 动作
     * <p>
     * 转换过程中执行的操作，可选。
     * 当转换发生时，将执行此动作，可用于实现业务逻辑。
     * </p>
     * <p>
     * 动作可以包含任何业务逻辑，但应注意以下几点：
     * <ul>
     *   <li>动作应处理好异常，避免异常传播到状态机框架</li>
     *   <li>对于耗时操作，考虑异步执行，避免阻塞状态机</li>
     *   <li>动作应避免修改状态机的内部状态，只关注业务逻辑</li>
     * </ul>
     * </p>
     * <p>
     * 在高并发环境下，动作的实现应考虑线程安全性，特别是当访问共享资源时。
     * </p>
     */
    private GXAction<S, E, C> action;

    /**
     * 转换类型
     * <p>
     * 定义转换的行为方式，默认为外部转换(EXTERNAL)。
     * 可选值：
     * <ul>
     *   <li>INTERNAL：内部转换，不退出源状态，源状态和目标状态必须相同</li>
     *   <li>LOCAL：本地转换，不退出复合源状态但会退出子状态</li>
     *   <li>EXTERNAL：外部转换，完全退出源状态，适用于源状态和目标状态不同的情况</li>
     * </ul>
     * </p>
     * <p>
     * 选择合适的转换类型对于状态机的行为至关重要：
     * <ul>
     *   <li>当需要在同一状态内处理事件而不改变状态时，使用INTERNAL</li>
     *   <li>当需要在复合状态的子状态间转换时，使用LOCAL</li>
     *   <li>当需要完全改变状态时，使用EXTERNAL</li>
     * </ul>
     * </p>
     */
    private GXTransitionType type = GXTransitionType.EXTERNAL;

    /**
     * 获取源状态
     * <p>
     * 返回此转换的源状态（起始状态）。
     * </p>
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
     * 此方法应只在转换配置阶段调用，配置完成后不应再修改源状态。
     * </p>
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
     * </p>
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
     * 此方法应只在转换配置阶段调用，配置完成后不应再修改事件。
     * </p>
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
     * 此方法应只在转换配置阶段调用，配置完成后不应再修改类型。
     * </p>
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
     * </p>
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
     * 此方法应只在转换配置阶段调用，配置完成后不应再修改目标状态。
     * </p>
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
     * </p>
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
     * 此方法应只在转换配置阶段调用，配置完成后不应再修改条件。
     * </p>
     * <p>
     * 条件实现应注意：
     * <ul>
     *   <li>应为纯函数，不修改上下文对象或其他共享状态</li>
     *   <li>应高效执行，避免复杂计算和IO操作</li>
     *   <li>应处理好异常，避免异常传播到状态机框架</li>
     * </ul>
     * </p>
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
     * </p>
     *
     * @return 动作对象，如果没有则返回null
     */
    @Override
    public GXAction<S, E, C> getAction() {
        return this.action;
    }

    /**
     * 设置动作
     * <p>
     * 设置此转换执行时的动作。
     * 当转换发生时，将执行此动作，可用于实现业务逻辑。
     * 此方法应只在转换配置阶段调用，配置完成后不应再修改动作。
     * </p>
     * <p>
     * 动作实现应注意：
     * <ul>
     *   <li>应处理好异常，避免异常传播到状态机框架</li>
     *   <li>对于耗时操作，考虑异步执行，避免阻塞状态机</li>
     *   <li>在高并发环境下，应考虑线程安全性</li>
     * </ul>
     * </p>
     *
     * @param action 动作对象
     */
    @Override
    public void setAction(GXAction<S, E, C> action) {
        this.action = action;
    }

    /**
     * 执行状态转换
     * <p>
     * 根据上下文和条件，执行状态转换并返回新的状态。
     * 执行流程：
     * <ol>
     *   <li>记录调试信息</li>
     *   <li>验证转换配置的有效性</li>
     *   <li>检查条件是否满足（如果需要）</li>
     *   <li>如果条件满足或不需要检查条件，执行动作（如果有）</li>
     *   <li>返回目标状态</li>
     *   <li>如果条件不满足，返回源状态</li>
     * </ol>
     * </p>
     * <p>
     * 性能优化：
     * <ul>
     *   <li>条件检查使用短路逻辑，避免不必要的条件评估</li>
     *   <li>只有在条件满足时才执行动作，避免不必要的操作</li>
     *   <li>调试信息的记录不影响主要逻辑的执行</li>
     * </ul>
     * </p>
     *
     * @param ctx 上下文对象，包含转换所需的数据
     * @param checkCondition 是否检查条件，如果为false则无条件执行转换
     * @return 转换后的状态，如果条件不满足则返回源状态
     */
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

    /**
     * 转换为字符串表示
     * <p>
     * 返回此转换的字符串表示，格式为：源状态-[事件, 类型]->目标状态。
     * 此方法用于调试和日志记录。
     * </p>
     * <p>
     * 使用final修饰，防止子类重写，确保一致的字符串表示。
     * </p>
     *
     * @return 转换的字符串表示
     */
    @Override
    public final String toString() {
        return source + "-[" + event.toString() + ", " + type + "]->"
                + target;
    }

    /**
     * 比较两个转换是否相等
     * <p>
     * 两个转换相等的条件是：事件、源状态和目标状态都相等。
     * 此方法用于在集合中查找和比较转换。
     * </p>
     * <p>
     * 性能优化：先检查类型，再比较最可能不同的属性（事件），最后比较状态。
     * </p>
     *
     * @param anObject 要比较的对象
     * @return 如果相等返回true，否则返回false
     */
    @Override
    public boolean equals(Object anObject) {
        if (anObject instanceof GXTransition) {
            GXTransition<?, ?, ?> other = (GXTransition<?, ?, ?>) anObject;
            return this.event.equals(other.getEvent())
                    && this.source.equals(other.getSource())
                    && this.target.equals(other.getTarget());
        }
        return false;
    }

    /**
     * 验证转换配置
     * <p>
     * 检查转换的配置是否有效。主要验证内部转换(INTERNAL)的源状态和目标状态是否相同，
     * 因为内部转换要求源状态和目标状态必须是同一个状态。
     * </p>
     * <p>
     * 如果验证失败，将抛出异常，防止无效的转换配置被使用。
     * 此方法在每次执行转换前调用，确保转换的有效性。
     * </p>
     * <p>
     * 安全性考虑：在执行转换前验证配置，可以及早发现配置错误，避免在运行时出现不一致的状态。
     * </p>
     *
     * @throws GXStateMachineException 如果内部转换的源状态和目标状态不同
     */
    @Override
    public void verify() {
        if (type == GXTransitionType.INTERNAL && source != target) {
            throw new GXStateMachineException(String.format("Internal transition source state '%s' "
                    + "and target state '%s' must be same.", source, target));
        }
    }
}
