package cn.maple.core.framework.validator;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.annotation.GXValidateCRS;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXCallRemoteValidateService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GXValidateCallRemoteServiceValidator implements ConstraintValidator<GXValidateCRS, Object> {
    private volatile GXCallRemoteValidateService service;

    @Override
    public void initialize(GXValidateCRS annotation) {
        if (annotation == null) {
            throw new GXBusinessException("GXValidateCRS annotation must not be null");
        }
        Class<? extends GXCallRemoteValidateService> clazz = annotation.service();
        service = GXSpringContextUtils.getBean(clazz);
        if (service == null) {
            throw new GXBusinessException("Remote validate service bean not found: " + clazz.getName());
        }
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext constraintValidatorContext) {
        if (value == null) {
            return true;
        }
        GXCallRemoteValidateService validateService = service;
        if (validateService == null) {
            throw new GXBusinessException("Remote validate service is not initialized");
        }
        try {
            return validateService.callRemoteValidateService(value, constraintValidatorContext, Dict.create());
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Remote validate service failed: valueType={}, error={}",
                    value.getClass().getName(), e.getMessage(), e);
            throw new GXBusinessException("Remote validate service failed: " + e.getMessage(), e);
        }
    }
}
