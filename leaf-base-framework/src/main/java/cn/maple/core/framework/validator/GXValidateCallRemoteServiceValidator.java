package cn.maple.core.framework.validator;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.annotation.GXValidateCRS;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXCallRemoteValidateService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;

/**
 * 远程服务调用验证器
 * <p>
 * 该验证器用于实现通过调用远程服务进行数据验证的功能。
 * 配合@GXValidateCRS注解使用，可以在实体类字段上添加自定义的远程验证逻辑。
 * </p>
 * 
 * <p>
 * 主要功能：
 * - 支持调用任意实现了GXCallRemoteValidateService接口的服务进行验证
 * - 自动从Spring容器中获取验证服务实例
 * - 提供友好的错误提示信息
 * - 支持传递额外参数到验证服务
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建一个实现GXCallRemoteValidateService接口的验证服务
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
 * // 2. 在实体类中使用@GXValidateCRS注解
 * public class LoginRequest {
 *     @GXValidateCRS(service = UserExistsValidateService.class, message = "用户名验证失败")
 *     private String username;
 *     
 *     private String password;
 *     
 *     // getter和setter方法
 * }
 * 
 * // 3. 在控制器中使用验证
 * @RestController
 * public class AuthController {
 *     @PostMapping("/login")
 *     public Result login(@Validated LoginRequest request) {
 *         // 验证通过后的业务逻辑
 *         return Result.ok();
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author magleton
 */
@Slf4j
public class GXValidateCallRemoteServiceValidator implements ConstraintValidator<GXValidateCRS, Object> {
    /**
     * 远程验证服务实例
     * 通过Spring容器获取，在initialize方法中初始化
     */
    private GXCallRemoteValidateService service;

    /**
     * 初始化验证器
     * <p>
     * 从注解中获取验证服务类型，并从Spring容器中获取对应的服务实例
     * </p>
     *
     * @param annotation GXValidateCRS注解实例
     */
    @Override
    public void initialize(GXValidateCRS annotation) {
        Class<? extends GXCallRemoteValidateService> clazz = annotation.service();
        service = GXSpringContextUtils.getBean(clazz);
    }

    /**
     * 执行验证逻辑
     * <p>
     * 调用远程服务进行验证，并处理异常情况
     * </p>
     *
     * @param o                        待验证的值
     * @param constraintValidatorContext 验证上下文，用于自定义错误消息
     * @return 验证是否通过
     * @throws GXBusinessException 当值为null或未指定验证服务时抛出
     */
    @Override
    public boolean isValid(Object o, ConstraintValidatorContext constraintValidatorContext) {
        // 检查待验证的值是否为null
        if (Objects.isNull(o)) {
            throw new GXBusinessException(CharSequenceUtil.format("验证出错 , 值为<{}>", o));
        }
        // 检查验证服务是否已正确初始化
        if (null == service) {
            throw new GXBusinessException(CharSequenceUtil.format("需要指定相应的Service进行验证...", o));
        }
        // 创建参数字典，可用于向验证服务传递额外参数
        Dict param = Dict.create();
        // 调用远程验证服务执行实际的验证逻辑
        return service.callRemoteValidateService(o, constraintValidatorContext, param);
    }
}
