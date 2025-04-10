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
 * 远程服务验证器
 * <p>
 * 该验证器用于通过远程服务进行数据验证。
 * 通过注解 {@link GXValidateCRS} 指定需要调用的远程验证服务，
 * 在验证过程中会调用指定的服务进行数据验证。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * @GXValidateCRS(service = MyRemoteValidateService.class)
 * private String data;
 * }
 * </pre>
 * </p>
 */
@Slf4j
public class GXValidateCallRemoteServiceValidator implements ConstraintValidator<GXValidateCRS, Object> {
    /**
     * 远程验证服务实例
     */
    private GXCallRemoteValidateService service;

    /**
     * 初始化验证器
     * <p>
     * 从Spring容器中获取指定的远程验证服务实例。
     * </p>
     *
     * @param annotation 验证注解
     * @throws GXBusinessException 当无法获取指定的服务实例时抛出
     */
    @Override
    public void initialize(GXValidateCRS annotation) {
        if (annotation == null) {
            throw new GXBusinessException("验证注解不能为空");
        }
        Class<? extends GXCallRemoteValidateService> clazz = annotation.service();
        service = GXSpringContextUtils.getBean(clazz);
        if (service == null) {
            throw new GXBusinessException(CharSequenceUtil.format("无法获取指定的远程验证服务: {}", clazz.getName()));
        }
        log.debug("初始化远程验证服务: {}", clazz.getName());
    }

    /**
     * 执行验证
     * <p>
     * 调用远程验证服务对指定的对象进行验证。
     * </p>
     *
     * @param o                         待验证的对象
     * @param constraintValidatorContext 验证上下文
     * @return true 验证通过，false 验证失败
     * @throws GXBusinessException 当验证过程中发生错误时抛出
     */
    @Override
    public boolean isValid(Object o, ConstraintValidatorContext constraintValidatorContext) {
        if (Objects.isNull(o)) {
            throw new GXBusinessException(CharSequenceUtil.format("验证出错, 值为<{}>", o));
        }
        if (null == service) {
            throw new GXBusinessException("需要指定相应的Service进行验证");
        }
        Dict param = Dict.create();
        try {
            return service.callRemoteValidateService(o, constraintValidatorContext, param);
        } catch (Exception e) {
            log.error("远程验证服务调用失败: {}", e.getMessage(), e);
            throw new GXBusinessException(CharSequenceUtil.format("远程验证服务调用失败: {}", e.getMessage()));
        }
    }
}
