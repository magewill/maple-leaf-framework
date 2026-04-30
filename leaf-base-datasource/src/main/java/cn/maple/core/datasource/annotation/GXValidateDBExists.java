package cn.maple.core.datasource.annotation;

import cn.maple.core.datasource.service.GXValidateDBExistsService;
import cn.maple.core.datasource.service.impl.GXValidateDBExistsValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = GXValidateDBExistsValidator.class)
@Documented
public @interface GXValidateDBExists {
    String message() default "{fieldName} does not match required database existence rule";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    Class<? extends GXValidateDBExistsService> service();

    String fieldName();

    String tableName();

    String condition() default "";

    String spEL() default "";

    String[] dependOnFields() default {};

    boolean enableCache() default false;
    
    int cacheExpireSeconds() default 300;
}
