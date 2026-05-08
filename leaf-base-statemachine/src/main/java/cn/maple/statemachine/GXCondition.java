package cn.maple.statemachine;

/**
 * 条件接口
 * <p>
 * 该接口是状态机中的核心组件之一，用于定义状态转换的前置条件。
 * 通过实现该接口，可以在状态转换过程中添加自定义的条件判断逻辑，
 * 只有当条件满足时，才会执行状态转换。
 * </p>
 * <p>
 * 条件在状态机中的应用：
 * <ul>
 *   <li>控制状态转换的执行，增加业务逻辑的灵活性</li>
 *   <li>允许同一事件在不同条件下触发不同的状态转换</li>
 *   <li>提高状态机的可扩展性，便于添加新的业务规则</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 定义订单上下文类
 * public class OrderContext {
 *     private String orderId;
 *     private double amount;
 *     private boolean isPremiumUser;
 *     // getter and setter
 * }
 *
 * // 实现支付金额检查条件
 * GXCondition<OrderContext> paymentAmountCondition = ctx -> ctx.getAmount() > 0;
 *
 * // 实现VIP用户特殊条件
 * GXCondition<OrderContext> premiumUserCondition = new GXCondition<OrderContext>() {
 *     @Override
 *     public boolean isSatisfied(OrderContext context) {
 *         return context.isPremiumUser();
 *     }
 *
 *     @Override
 *     public String name() {
 *         return "PremiumUserCondition";
 *     }
 * };
 *
 * // 在状态机构建器中使用条件
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)
 *     .to(OrderStatus.PAID)
 *     .on(OrderEvent.PAY)
 *     .when(paymentAmountCondition)
 *     .perform(paymentAction);
 * }
 * </pre>
 * </p>
 *
 * @param <C> 上下文类型，用于在状态转换过程中传递数据和进行条件判断
 * @see GXAction 动作接口，定义状态转换时执行的操作
 * @see GXTransition 转换接口，表示状态之间的转换规则
 */
public interface GXCondition<C> {
    /**
     * 判断条件是否满足
     * <p>
     * 当状态机接收到事件并找到匹配的转换时，会调用此方法判断条件是否满足。
     * 只有当此方法返回true时，才会执行状态转换。
     * </p>
     * <p>
     * 实现此方法时应注意：
     * <ul>
     *   <li>方法应保持高性能，避免复杂的计算或IO操作</li>
     *   <li>方法应是幂等的，多次调用结果应一致</li>
     *   <li>避免在方法中修改上下文对象的状态</li>
     *   <li>避免抛出异常，应通过返回false表示条件不满足</li>
     * </ul>
     * </p>
     *
     * @param context 上下文对象，包含条件判断可能需要的数据
     * @return 如果条件满足返回true，否则返回false
     */
    boolean isSatisfied(C context);

    /**
     * 获取条件名称
     * <p>
     * 返回此条件的名称，用于日志记录和调试。
     * 默认实现返回条件类的简单名称。
     * </p>
     * <p>
     * 子类可以覆盖此方法提供更有意义的名称，特别是对于lambda表达式实现的条件，
     * 可以通过自定义名称提高可读性。
     * </p>
     *
     * @return 条件名称
     */
    default String name() {
        return this.getClass().getSimpleName();
    }
}