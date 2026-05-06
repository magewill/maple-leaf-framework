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

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = GXValidateCallRemoteServiceValidator.class)
@Documented
public @interface GXValidateCRS {
    Class<? extends GXCallRemoteValidateService> service();

    String message() default "验证失败";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
