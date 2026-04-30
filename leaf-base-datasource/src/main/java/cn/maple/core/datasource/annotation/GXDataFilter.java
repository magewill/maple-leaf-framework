package cn.maple.core.datasource.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXDataFilter {
    String tableAlias() default "";

    String[] userIdFieldNames() default {"user_id"};

    String[] deptIdFieldNames() default {"dept_id"};
}
