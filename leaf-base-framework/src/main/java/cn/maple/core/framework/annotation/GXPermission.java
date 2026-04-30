package cn.maple.core.framework.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.METHOD)
public @interface GXPermission {
    String permissionName();

    String permissionCode();

    String moduleCode() default "";

    String moduleName() default "";
}
