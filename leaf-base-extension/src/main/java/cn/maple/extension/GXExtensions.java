package cn.maple.extension;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * GXExtensions注解是{@link GXExtension}注解的补充，用于支持多个业务场景坐标
 * <p>
 * 该注解有两种使用方式：
 * 1. 通过value属性直接指定多个{@link GXExtension}注解
 * 2. 通过bizId、useCase和scenario属性指定多个值，框架会自动生成这些值的笛卡尔积组合
 * <p>
 * 线程安全性：该注解本身不涉及线程安全问题，但在使用扩展点框架时，需要注意扩展点实现类的线程安全性
 * <p>
 * 使用示例：
 * <pre>
 * // 方式一：直接指定多个GXExtension
 * @GXExtensions(value = {
 *     @GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay"),
 *     @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
 * })
 * public class PaymentExtImpl implements PaymentExtPoint {
 *     // 实现方法
 * }
 * 
 * // 方式二：使用笛卡尔积组合
 * @GXExtensions(
 *     bizId = {"mall", "b2b"},
 *     useCase = {"payment"},
 *     scenario = {"alipay", "wechat"}
 * )
 * public class MultiScenarioPaymentExtImpl implements PaymentExtPoint {
 *     // 这将注册4个扩展点实现：
 *     // mall.payment.alipay
 *     // mall.payment.wechat
 *     // b2b.payment.alipay
 *     // b2b.payment.wechat
 * }
 * </pre>
 *
 * @author wangguoqiang wrote on 2022/10/10 12:19
 * @version 1.0
 * @see GXExtension 扩展点注解
 * @see GXExtensionPoint 扩展点接口
 * @see GXBizScenario 业务场景
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Component
public @interface GXExtensions {
    /**
     * 业务ID数组，用于标识业务领域
     * <p>
     * 与useCase和scenario数组组合形成笛卡尔积，生成多个业务场景
     *
     * @return 业务ID数组
     */
    String[] bizId() default GXBizScenario.DEFAULT_BIZ_ID;

    /**
     * 用例数组，用于标识业务领域下的具体用例
     * <p>
     * 与bizId和scenario数组组合形成笛卡尔积，生成多个业务场景
     *
     * @return 用例数组
     */
    String[] useCase() default GXBizScenario.DEFAULT_USE_CASE;

    /**
     * 场景数组，用于标识用例下的具体场景
     * <p>
     * 与bizId和useCase数组组合形成笛卡尔积，生成多个业务场景
     *
     * @return 场景数组
     */
    String[] scenario() default GXBizScenario.DEFAULT_SCENARIO;

    /**
     * 扩展点注解数组，直接指定多个扩展点注解
     * <p>
     * 当需要精确控制每个扩展点的业务场景时，可以使用此属性
     *
     * @return 扩展点注解数组
     */
    GXExtension[] value() default {};
}
