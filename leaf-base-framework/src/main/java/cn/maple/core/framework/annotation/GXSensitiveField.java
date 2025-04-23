package cn.maple.core.framework.annotation;

import cn.maple.core.framework.service.GXSensitiveFieldDeEncryptService;

import java.lang.annotation.*;

/**
 * 敏感字段标记注解
 * <p>
 * 该注解用于标记类中的敏感信息字段，需要与{@link GXSensitiveData}注解配合使用。
 * 被此注解标记的字段将根据配置的加密服务和算法进行自动加密和解密处理。
 * </p>
 * 
 * <p>
 * 安全特性：
 * - 支持字段级别的精细化加密控制
 * - 可配置不同的加密算法和密钥
 * - 支持自定义加密/解密服务实现
 * - 透明处理敏感数据，减少代码侵入性
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * {@literal @}GXSensitiveData
 * public class UserDTO {
 *     // 使用默认加密服务和算法
 *     {@literal @}GXSensitiveField
 *     private String mobile;
 *     
 *     // 使用自定义加密服务和密钥
 *     {@literal @}GXSensitiveField(
 *         serviceClazz = CustomEncryptService.class,
 *         encryptAlgorithm = "AES",
 *         decryAlgorithm = "AES",
 *         deEncryptKey = "custom_secret_key"
 *     )
 *     private String idCard;
 *     
 *     // 使用默认服务但传递额外参数
 *     {@literal @}GXSensitiveField(
 *         params = {"HIDE_MIDDLE", "4"}
 *     )
 *     private String bankCardNo;
 * }
 * </pre>
 * </p>
 *
 * @author britton <britton@126.com>
 * @see GXSensitiveData 敏感信息类注解，用于标记包含敏感字段的类
 * @see GXSensitiveFieldDeEncryptService 默认的敏感字段加解密服务接口
 */
@Inherited
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface GXSensitiveField {
    /**
     * 指定处理敏感字段的服务类
     * <p>
     * 该服务类需要实现加密和解密方法，用于处理字段值的转换
     * </p>
     * 
     * @return 加解密服务类，默认使用框架提供的GXSensitiveFieldDeEncryptService
     */
    Class<?> serviceClazz() default GXSensitiveFieldDeEncryptService.class;

    /**
     * 指定加密算法名称
     * <p>
     * 该值将传递给加密服务，用于选择具体的加密算法实现
     * </p>
     * 
     * @return 加密算法名称，默认为"encryptAlgorithm"
     */
    String encryptAlgorithm() default "encryptAlgorithm";

    /**
     * 指定解密算法名称
     * <p>
     * 该值将传递给解密服务，用于选择具体的解密算法实现
     * </p>
     * 
     * @return 解密算法名称，默认为"decryAlgorithm"
     */
    String decryAlgorithm() default "decryAlgorithm";

    /**
     * 指定加解密密钥
     * <p>
     * 用于加密和解密操作的密钥，应当妥善保管，避免泄露
     * </p>
     * 
     * @return 加解密密钥，默认为空字符串
     */
    String deEncryptKey() default "";

    /**
     * 指定额外参数
     * <p>
     * 可以传递给加解密服务的额外参数，例如掩码处理的规则、格式化选项等
     * </p>
     * 
     * @return 额外参数数组，默认为空数组
     */
    String[] params() default {};
}