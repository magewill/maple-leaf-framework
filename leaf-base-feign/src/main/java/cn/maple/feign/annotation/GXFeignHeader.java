package cn.maple.feign.annotation;

import java.lang.annotation.*;

/**
 * Declares request headers that should be propagated from the current servlet
 * request to a Feign request.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXFeignHeader {
    /**
     * Header names to propagate; missing source headers are ignored.
     */
    String[] names() default {};
}
