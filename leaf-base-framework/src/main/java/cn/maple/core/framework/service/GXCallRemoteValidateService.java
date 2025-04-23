package cn.maple.core.framework.service;

import cn.hutool.core.lang.Dict;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 调用指定服务进行验证
 */
public interface GXCallRemoteValidateService {
    /**
     * Checks whether or not a given value exists for a given field
     *
     * @param value                      The value to check for
     * @param constraintValidatorContext validate constraint Object
     * @param param                      extract param
     * @return True if the value exists for the field; false otherwise
     * @throws UnsupportedOperationException
     */
    boolean callRemoteValidateService(Object value, ConstraintValidatorContext constraintValidatorContext, Dict param) throws UnsupportedOperationException;
}
