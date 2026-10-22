package cn.maple.feign.annotation;

import java.lang.annotation.*;

/**
 * Declares request headers that should be propagated from the current servlet
 * request to a Feign request.
 *
 * <p>Scope and boundaries:</p>
 * <ul>
 *   <li>Only method level declarations are supported; type level declaration has no
 *   effect.</li>
 *   <li>Headers are read from the current {@code HttpServletRequest}. Without a servlet
 *   request context the declared headers are silently skipped and the Feign call
 *   continues.</li>
 *   <li>Sensitive and hop-by-hop headers (for example {@code Authorization},
 *   {@code Cookie}, {@code X-Auth-Token}) are always blocked, even when declared.</li>
 * </ul>
 *
 * @see cn.maple.feign.interceptor.GXFeignRequestInterceptor
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
