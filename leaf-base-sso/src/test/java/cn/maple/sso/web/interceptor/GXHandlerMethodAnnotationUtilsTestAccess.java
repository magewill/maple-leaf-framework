package cn.maple.sso.web.interceptor;

import cn.maple.core.framework.annotation.GXIgnoreLoginIntercept;
import org.springframework.web.method.HandlerMethod;

/**
 * Test accessor for the package private annotation cache.
 *
 * <p>{@code GXHandlerMethodAnnotationUtils} is package private by design; concurrency
 * tests live in other packages and go through this thin accessor instead of reflection
 * (FR-023).</p>
 */
public final class GXHandlerMethodAnnotationUtilsTestAccess {
    private GXHandlerMethodAnnotationUtilsTestAccess() {
    }

    public static boolean hasIgnoreAnnotation(HandlerMethod handlerMethod) {
        return GXHandlerMethodAnnotationUtils.hasMergedAnnotation(handlerMethod, GXIgnoreLoginIntercept.class);
    }

    public static int cacheSize() {
        return GXHandlerMethodAnnotationUtils.cacheSize();
    }
}
