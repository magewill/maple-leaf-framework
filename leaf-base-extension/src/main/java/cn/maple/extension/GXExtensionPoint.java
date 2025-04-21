package cn.maple.extension;

/**
 * 扩展点接口，所有扩展点接口的父接口
 * <p>
 * 扩展点表示一块逻辑在不同的业务场景下有不同的实现。使用扩展点做接口声明，然后用{@link GXExtension}注解标记的实现类去实现扩展点。
 * 扩展点框架会根据业务场景自动查找并执行对应的扩展点实现。
 * <p>
 * 线程安全性：扩展点接口本身不涉及线程安全问题，但实现类需要保证线程安全。
 * <p>
 * 使用示例：
 * <pre>
 * // 定义扩展点接口
 * public interface PaymentExtPoint extends GXExtensionPoint {
 *     // 支付方法
 *     PayResult pay(Order order);
 *     
 *     // 退款方法
 *     RefundResult refund(Order order);
 * }
 * 
 * // 实现扩展点接口
 * @GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
 * public class AlipayPaymentExt implements PaymentExtPoint {
 *     @Override
 *     public PayResult pay(Order order) {
 *         // 支付宝支付实现
 *         return new PayResult();
 *     }
 *     
 *     @Override
 *     public RefundResult refund(Order order) {
 *         // 支付宝退款实现
 *         return new RefundResult();
 *     }
 * }
 * </pre>
 *
 * @author britton
 * @see GXExtension 扩展点注解
 * @see GXExtensions 扩展点集合注解
 * @see GXExtensionExecutor 扩展执行器
 * @see GXBizScenario 业务场景
 */
public interface GXExtensionPoint {
}
