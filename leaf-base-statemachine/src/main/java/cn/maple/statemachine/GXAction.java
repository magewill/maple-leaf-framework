package cn.maple.statemachine;

/**
 * 动作接口
 * <p>
 * 该接口是策略模式的核心组件之一，定义了状态机响应事件时执行的动作。
 * 通过实现该接口，可以在状态转换过程中执行自定义的业务逻辑，如数据更新、消息发送、日志记录等。
 * </p>
 * <p>
 * 动作在状态机中的应用：
 * <ul>
 *   <li>封装了状态转换时需要执行的业务逻辑</li>
 *   <li>使得状态转换和业务逻辑解耦，便于维护和扩展</li>
 *   <li>支持条件执行，可以根据上下文决定是否执行特定动作</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 定义状态、事件和上下文类型
 * public enum OrderStatus { WAIT_PAYMENT, PAID, DELIVERING, RECEIVED }
 * public enum OrderEvent { PAY, DELIVER, RECEIVE }
 * public class OrderContext { 
 *     private String orderId;
 *     private double amount;
 *     // getter and setter
 * }
 * 
 * // 实现支付成功动作
 * GXAction<OrderStatus, OrderEvent, OrderContext> paymentAction = 
 *     (from, to, event, ctx) -> {
 *         System.out.println("订单已支付：" + ctx.getOrderId());
 *         // 执行支付成功后的业务逻辑，如更新订单状态、发送通知等
 *     };
 * 
 * // 在状态机构建器中使用该动作
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)
 *     .to(OrderStatus.PAID)
 *     .on(OrderEvent.PAY)
 *     .when(ctx -> ctx.getAmount() > 0)
 *     .perform(paymentAction);
 * }
 * </pre>
 * </p>
 * 
 * @param <S> 状态类型，通常使用枚举或字符串
 * @param <E> 事件类型，通常使用枚举或字符串
 * @param <C> 上下文类型，用于在状态转换过程中传递数据
 * 
 * @see GXCondition 条件接口，用于决定是否执行状态转换
 * @see GXTransition 转换接口，表示状态之间的转换规则
 */
public interface GXAction<S, E, C> {
    /**
     * 执行动作
     * <p>
     * 当状态转换发生时，如果满足转换条件，将调用此方法执行自定义业务逻辑。
     * 方法参数提供了完整的转换上下文信息，包括源状态、目标状态、触发事件和用户自定义上下文。
     * </p>
     * <p>
     * 实现此方法时应注意：
     * <ul>
     *   <li>方法应尽量保持幂等性，避免重复执行导致数据不一致</li>
     *   <li>避免在方法中抛出未检查异常，以免中断状态机的正常流程</li>
     *   <li>对于耗时操作，考虑使用异步方式执行，避免阻塞状态机</li>
     * </ul>
     * </p>
     *
     * @param from    源状态，转换开始前的状态
     * @param to      目标状态，转换完成后的状态
     * @param event   触发当前转换的事件
     * @param context 用户自定义上下文，包含状态转换可能需要的数据
     */
    void execute(S from, S to, E event, C context);
}
