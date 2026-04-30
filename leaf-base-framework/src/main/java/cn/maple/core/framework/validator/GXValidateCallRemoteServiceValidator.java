package cn.maple.core.framework.validator;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.annotation.GXValidateCRS;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXCallRemoteValidateService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
public class GXValidateCallRemoteServiceValidator implements ConstraintValidator<GXValidateCRS, Object> {
    private GXCallRemoteValidateService service;

    @Override
    public void initialize(GXValidateCRS annotation) {
        Class<? extends GXCallRemoteValidateService> clazz = annotation.service();
        service = GXSpringContextUtils.getBean(clazz);
    }

    @Override
    public boolean isValid(Object o, ConstraintValidatorContext constraintValidatorContext) {
        if (Objects.isNull(o)) {
            throw new GXBusinessException(CharSequenceUtil.format("验证出错 , 值为<{}>", o));
        }
        if (null == service) {
            throw new GXBusinessException(CharSequenceUtil.format("需要指定相应的Service进行验证...", o));
        }
        Dict param = Dict.create();
        return service.callRemoteValidateService(o, constraintValidatorContext, param);
    }
}
