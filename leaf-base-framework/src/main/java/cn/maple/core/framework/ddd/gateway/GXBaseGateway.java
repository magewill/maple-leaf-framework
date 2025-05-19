package cn.maple.core.framework.ddd.gateway;

/**
 * 领域驱动设计(DDD)中的网关(Gateway)基础接口
 * <p>
 * 在DDD中，Gateway是领域层与外部系统交互的抽象接口。Gateway封装了与外部系统通信的细节，
 * 使领域层可以专注于业务逻辑而不必关心外部系统的实现细节。Gateway通常用于以下场景：
 * 1. 与外部微服务或API的通信
 * 2. 与第三方系统的集成
 * 3. 访问不同的数据存储（如缓存、消息队列等）
 * </p>
 *
 * <p>使用示例:</p>
 * <pre>
 * {@code
 * // 1. 定义支付网关接口
 * public interface PaymentGateway extends GXBaseGateway {
 *     /**
 *      * 处理支付请求
 *      * @param orderId 订单ID
 *      * @param amount 支付金额
 *      * @param paymentMethod 支付方式
 *      * @return 支付结果
 *      *\/
 *     PaymentResult processPayment(String orderId, BigDecimal amount, String paymentMethod);
 *
 *     /**
 *      * 查询支付状态
 *      * @param paymentId 支付ID
 *      * @return 支付状态
 *      *\/
 *     PaymentStatus queryPaymentStatus(String paymentId);
 * }
 *
 * // 2. 实现支付宝支付网关
 * @Component("alipayGateway")
 * public class AlipayGateway implements PaymentGateway {
 *     private final AlipayClient alipayClient; // 支付宝SDK客户端
 *
 *     public AlipayGateway(AlipayClient alipayClient) {
 *         this.alipayClient = alipayClient;
 *     }
 *
 *     @Override
 *     public PaymentResult processPayment(String orderId, BigDecimal amount, String paymentMethod) {
 *         try {
 *             // 构建支付宝支付请求
 *             AlipayTradePayRequest request = new AlipayTradePayRequest();
 *             request.setBizContent("{\"out_trade_no\":\"" + orderId + "\",\"total_amount\":\"" + amount + "\",\"subject\":\"订单支付\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");
 *
 *             // 调用支付宝API
 *             AlipayTradePayResponse response = alipayClient.execute(request);
 *
 *             // 转换并返回结果
 *             return new PaymentResult(response.isSuccess(), response.getTradeNo(), response.getMsg());
 *         } catch (Exception e) {
 *             // 异常处理
 *             return new PaymentResult(false, null, "支付处理失败: " + e.getMessage());
 *         }
 *     }
 *
 *     @Override
 *     public PaymentStatus queryPaymentStatus(String paymentId) {
 *         try {
 *             // 构建支付宝查询请求
 *             AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
 *             request.setBizContent("{\"out_trade_no\":\"" + paymentId + "\"}");
 *
 *             // 调用支付宝API
 *             AlipayTradeQueryResponse response = alipayClient.execute(request);
 *
 *             // 转换支付状态
 *             if (!response.isSuccess()) {
 *                 return PaymentStatus.UNKNOWN;
 *             }
 *
 *             switch (response.getTradeStatus()) {
 *                 case "TRADE_SUCCESS":
 *                     return PaymentStatus.SUCCESS;
 *                 case "TRADE_CLOSED":
 *                     return PaymentStatus.CANCELLED;
 *                 case "WAIT_BUYER_PAY":
 *                     return PaymentStatus.PENDING;
 *                 default:
 *                     return PaymentStatus.UNKNOWN;
 *             }
 *         } catch (Exception e) {
 *             // 异常处理
 *             return PaymentStatus.UNKNOWN;
 *         }
 *     }
 * }
 *
 * // 3. 在领域服务中使用
 * @Service
 * public class OrderService {
 *     private final PaymentGateway paymentGateway;
 *
 *     public OrderService(@Qualifier("alipayGateway") PaymentGateway paymentGateway) {
 *         this.paymentGateway = paymentGateway;
 *     }
 *
 *     public void processOrderPayment(Order order) {
 *         // 调用支付网关处理支付
 *         PaymentResult result = paymentGateway.processPayment(
 *             order.getOrderId(),
 *             order.getTotalAmount(),
 *             order.getPaymentMethod()
 *         );
 *
 *         // 根据支付结果更新订单状态
 *         if (result.isSuccess()) {
 *             order.markAsPaid(result.getTransactionId());
 *         } else {
 *             order.markPaymentFailed(result.getErrorMessage());
 *         }
 *     }
 * }
 * }
 * </pre>
 *
 * <p>
 * 实现此接口时应注意：
 * 1. Gateway应该是无状态的，确保线程安全
 * 2. 应处理与外部系统通信可能出现的异常，并转换为领域异常
 * 3. 应该提供合适的超时和重试机制
 * 4. 考虑使用断路器模式处理外部系统故障
 * 5. 实现应该关注性能和安全性
 * </p>
 *
 * @author britton
 * @since 2021-11-08
 */
public interface GXBaseGateway {
    // 作为标记接口，不定义具体方法
    // 具体的Gateway实现类应根据需要定义自己的方法
}
