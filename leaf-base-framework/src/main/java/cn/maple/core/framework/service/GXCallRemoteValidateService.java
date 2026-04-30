package cn.maple.core.framework.service;

import cn.hutool.core.lang.Dict;
import jakarta.validation.ConstraintValidatorContext;

public interface GXCallRemoteValidateService {
    boolean callRemoteValidateService(Object value, ConstraintValidatorContext constraintValidatorContext, Dict param) throws UnsupportedOperationException;
}
