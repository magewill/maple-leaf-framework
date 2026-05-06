package cn.maple.core.framework.util;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBeanValidateException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;

public class GXValidatorUtils {
    private GXValidatorUtils() {
    }

    public static void validateEntity(Object object, Class<?>... groups) {
        if (object == null) {
            throw new IllegalArgumentException("Validation target must not be null");
        }
        Set<ConstraintViolation<Object>> constraintViolations = getValidator().validate(object, groups);
        if (!constraintViolations.isEmpty()) {
            final Dict dict = Dict.create();
            for (ConstraintViolation<Object> constraint : constraintViolations) {
                final String currentFormName = constraint.getPropertyPath().toString();
                dict.set(currentFormName, CharSequenceUtil.format("{} , value = {}", constraint.getMessage(), constraint.getInvalidValue()));
            }
            throw new GXBeanValidateException("Data validation failed", HttpStatus.HTTP_INTERNAL_ERROR, dict);
        }
    }

    public static void validateEntity(Object object, String jsonName, Class<?>... groups) {
        if (object == null) {
            throw new IllegalArgumentException("Validation target must not be null");
        }
        Set<ConstraintViolation<Object>> constraintViolations = getValidator().validate(object, groups);
        if (!constraintViolations.isEmpty()) {
            final Dict dict = Dict.create();
            for (ConstraintViolation<Object> constraint : constraintViolations) {
                String currentFormName = constraint.getPropertyPath().toString();
                if (CharSequenceUtil.isNotBlank(jsonName)) {
                    currentFormName = jsonName + "." + currentFormName;
                }
                String message = constraint.getMessage();
                if (constraint.getMessageTemplate().contains("{fieldName}")) {
                    final Dict param = Dict.create().set("fieldName", currentFormName);
                    message = StrUtil.format(constraint.getMessageTemplate(), param);
                }
                dict.putIfAbsent(currentFormName, message);
            }
            throw new GXBeanValidateException("Data validation failed", HttpStatus.HTTP_INTERNAL_ERROR, dict);
        }
    }

    private static Validator getValidator() {
        Validator springValidator = GXSpringContextUtils.getBean(Validator.class);
        return springValidator == null ? DefaultValidatorHolder.VALIDATOR : springValidator;
    }

    @SuppressWarnings("resource")
    private static final class DefaultValidatorHolder {
        private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
        private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

        static {
            Runtime.getRuntime().addShutdownHook(new Thread(VALIDATOR_FACTORY::close, "maple-validator-factory-shutdown"));
        }

        private DefaultValidatorHolder() {
        }
    }
}
