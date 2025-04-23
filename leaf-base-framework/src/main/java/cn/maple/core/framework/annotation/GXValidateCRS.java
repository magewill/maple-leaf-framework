package cn.maple.core.framework.annotation;

import cn.maple.core.framework.service.GXCallRemoteValidateService;
import cn.maple.core.framework.validator.GXValidateCallRemoteServiceValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 调用指定服务进行数据验证
 * <p>
 * 该注解用于在实体类字段上标注，通过调用远程服务来验证字段值的有效性。
 * 验证逻辑由指定的服务实现，使系统具有更高的灵活性和可扩展性。
 * </p>
 * 
 * <p>
 * 主要特点：
 * - 支持自定义验证逻辑，只需实现GXCallRemoteValidateService接口
 * - 可以在不修改原有代码的情况下扩展验证规则
 * - 支持跨服务调用进行数据验证
 * - 可与其他验证注解组合使用
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建验证服务实现类
 * @Service
 * public class UserExistsValidateService implements GXCallRemoteValidateService {
 *     @Autowired
 *     private UserMapper userMapper;
 *     
 *     @Override
 *     public boolean callRemoteValidateService(Object value, ConstraintValidatorContext context, Dict param) {
 *         // 验证用户是否存在
 *         String username = (String) value;
 *         boolean exists = userMapper.checkUsernameExists(username);
 *         if (!exists) {
 *             context.disableDefaultConstraintViolation();
 *             context.buildConstraintViolationWithTemplate("用户名不存在").addConstraintViolation();
 *         }
 *         return exists;
 *     }
 * }
 * 
 * // 2. 在实体类中使用注解
 * public class UserLoginDTO {
 *     @NotBlank(message = "用户名不能为空")
 *     @GXValidateCRS(service = UserExistsValidateService.class, message = "用户名不存在")
 *     private String username;
 *     
 *     @NotBlank(message = "密码不能为空")
 *     private String password;
 *     
 *     // getter和setter方法
 * }
 * </pre>
 * </p>
 *
 * @author britton
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = GXValidateCallRemoteServiceValidator.class)
@Documented
public @interface GXValidateCRS {
    /**
     * 目标服务
     * <p>
     * 指定用于执行验证逻辑的服务类，该服务必须实现GXCallRemoteValidateService接口
     * </p>
     *
     * @return 验证服务类
     */
    Class<? extends GXCallRemoteValidateService> service();
    
    /**
     * 验证失败时的错误消息
     *
     * @return 错误消息
     */
    String message() default "验证失败";
    
    /**
     * 验证分组
     *
     * @return 分组类数组
     */
    Class<?>[] groups() default {};
    
    /**
     * 负载
     *
     * @return 负载数组
     */
    Class<? extends Payload>[] payload() default {};
}
