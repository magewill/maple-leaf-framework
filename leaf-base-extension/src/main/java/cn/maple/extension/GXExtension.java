package cn.maple.extension;

import java.lang.annotation.*;

/**
 * GXExtension注解用于标记一个类是扩展点的实现类。
 * <p>
 * 该注解可以标记在任何实现了{@link GXExtensionPoint}接口的类上，用于指定该实现类适用的业务场景。
 * 通过bizId、useCase和scenario三个属性，可以精确定位扩展点实现类适用的业务场景。
 * <p>
 * 该注解支持可重复注解，可以在同一个类上多次使用该注解，以支持一个实现类适用于多个业务场景。
 * 也可以使用{@link GXExtensions}注解来一次性指定多个业务场景。
 * <p>
 * 线程安全性：该注解本身不涉及线程安全问题，但在使用扩展点框架时，需要注意扩展点实现类的线程安全性。
 * <p>
 * 使用示例：
 * <pre>
 * // 实现一个扩展点接口
 * public interface PaymentExtPoint extends GXExtensionPoint {
 *     void pay(Order order);
 * }
 * 
 * // 为特定业务场景提供实现
 * @GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
 * public class AlipayPaymentExt implements PaymentExtPoint {
 *     @Override
 *     public void pay(Order order) {
 *         // 支付宝支付实现
 *     }
 * }
 * 
 * // 为多个业务场景提供实现
 * @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
 * @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini")
 * public class WechatPaymentExt implements PaymentExtPoint {
 *     @Override
 *     public void pay(Order order) {
 *         // 微信支付实现
 *     }
 * }
 * </pre>
 *
 * @author britton
 * @see GXExtensionPoint 扩展点接口
 * @see GXExtensions 扩展点集合注解
 * @see GXBizScenario 业务场景
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(GXExtensions.class)
public @interface GXExtension {
    /**
     * 业务ID，用于标识业务领域
     * <p>
     * 例如：订单、用户、商品等
     *
     * @return 业务ID
     */
    String bizId() default GXBizScenario.DEFAULT_BIZ_ID;

    /**
     * 用例，用于标识业务领域下的具体用例
     * <p>
     * 例如：下单、支付、退款等
     *
     * @return 用例
     */
    String useCase() default GXBizScenario.DEFAULT_USE_CASE;

    /**
     * 场景，用于标识用例下的具体场景
     * <p>
     * 例如：普通下单、促销下单、秒杀下单等
     *
     * @return 场景
     */
    String scenario() default GXBizScenario.DEFAULT_SCENARIO;
}
