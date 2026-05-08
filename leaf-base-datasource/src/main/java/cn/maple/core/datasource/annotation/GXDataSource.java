package cn.maple.core.datasource.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Switches the dynamic datasource for a class or method.
 * Method-level annotations take precedence over class-level annotations.
 *
 * <pre>
 * @GXDataSource("slave")
 * class UserService {
 *     @GXDataSource("master")
 *     void save(User user) { ... }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface GXDataSource {
    /**
     * Datasource name configured in datasource properties.
     */
    String value() default "framework";
}
