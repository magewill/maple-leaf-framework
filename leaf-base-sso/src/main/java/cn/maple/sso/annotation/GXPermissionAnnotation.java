package cn.maple.sso.annotation;

import cn.maple.sso.enums.GXAction;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXPermissionAnnotation {
    String value() default "";
    
    GXAction action() default GXAction.Normal;
}