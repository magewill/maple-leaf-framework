package cn.maple.core.framework.annotation;

import java.lang.annotation.*;

/**
 * 敏感信息类标记注解
 * <p>
 * 该注解用于标记包含敏感信息字段的类，配合{@link GXSensitiveField}注解一起使用。
 * 被此注解标记的类中的敏感字段将根据配置进行自动加密和解密处理。
 * </p>
 * 
 * <p>
 * 安全特性：
 * - 提供统一的敏感数据识别机制
 * - 支持AOP方式拦截和处理敏感数据
 * - 与{@link GXSensitiveField}配合实现字段级别的精细化控制
 * - 便于安全审计和敏感数据追踪
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 标记包含敏感信息的用户类
 * {@literal @}GXSensitiveData
 * public class UserDTO {
 *     private Long id;
 *     private String username;
 *     
 *     // 标记手机号为敏感字段，使用默认加密服务
 *     {@literal @}GXSensitiveField
 *     private String mobile;
 *     
 *     // 标记身份证号为敏感字段，使用自定义加密服务和密钥
 *     {@literal @}GXSensitiveField(serviceClazz = CustomEncryptService.class, deEncryptKey = "custom_key")
 *     private String idCard;
 *     
 *     // getter和setter方法
 * }
 * </pre>
 * </p>
 *
 * @author britton <britton@126.com>
 * @see GXSensitiveField 敏感字段注解，用于标记类中的具体敏感字段
 */
@Inherited
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface GXSensitiveData {
}