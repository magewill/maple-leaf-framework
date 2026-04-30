package cn.maple.core.framework.util;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBeanValidateException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.Set;

public class GXValidatorUtils {
    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private GXValidatorUtils() {
    }

    public static void validateEntity(Object object, Class<?>... groups) {
        if (object == null) {
            throw new IllegalArgumentException("待校验对象不能为空");
        }
        Set<ConstraintViolation<Object>> constraintViolations = validator.validate(object, groups);
        if (!constraintViolations.isEmpty()) {
            final Dict dict = Dict.create();
            for (ConstraintViolation<Object> constraint : constraintViolations) {
                final String rootBeanName = CharSequenceUtil.lowerFirst(constraint.getRootBean().getClass().getSimpleName());
                final String currentFormName =/* rootBeanName + "." +*/ constraint.getPropertyPath().toString();
                dict.set(currentFormName, CharSequenceUtil.format("{} , value = {}", constraint.getMessage(), constraint.getInvalidValue()));
            }
            throw new GXBeanValidateException("数据验证错误", HttpStatus.HTTP_INTERNAL_ERROR, dict);
        }
    }

    public static void validateEntity(Object object, String jsonName, Class<?>... groups) {
        Set<ConstraintViolation<Object>> constraintViolations = validator.validate(object, groups);
        if (!constraintViolations.isEmpty()) {
            final Dict dict = Dict.create();
            for (ConstraintViolation<Object> constraint : constraintViolations) {
                String currentFormName = constraint.getPropertyPath().toString();
                if (CharSequenceUtil.isNotBlank(jsonName)) {
                    currentFormName = jsonName + "." + currentFormName;
                }
                String message = constraint.getMessage();
                //currentFormName = CharSequenceUtil.toSymbolCase(currentFormName, '_');
                if (constraint.getMessageTemplate().contains("{fieldName}")) {
                    final Dict param = Dict.create().set("fieldName", currentFormName);
                    message = StrUtil.format(constraint.getMessageTemplate(), param);
                }
                dict.putIfAbsent(currentFormName, message);
            }
            throw new GXBeanValidateException("数据验证错误", HttpStatus.HTTP_INTERNAL_ERROR, dict);
        }
    }
}
