package cn.maple.core.framework.annotation;

import cn.maple.core.framework.service.GXSensitiveFieldDeEncryptService;

import java.lang.annotation.*;

@Inherited
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface GXSensitiveField {
    Class<?> serviceClazz() default GXSensitiveFieldDeEncryptService.class;

    String encryptAlgorithm() default "encryptAlgorithm";

    String decryAlgorithm() default "decryAlgorithm";

    String deEncryptKey() default "";

    String[] params() default {};
}