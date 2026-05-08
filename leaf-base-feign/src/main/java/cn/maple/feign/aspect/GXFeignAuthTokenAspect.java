package cn.maple.feign.aspect;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.annotation.GXHttpInvokerAuthToken;
import cn.maple.core.framework.constant.GXHttpInvokerConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXFeignAuthTokenException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.feign.service.GXFeignService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates Feign invocation tokens for methods or classes annotated with
 * {@link GXHttpInvokerAuthToken}.
 *
 * <p>Method-level annotations take precedence over class-level annotations.</p>
 */
@Aspect
@Component
@Slf4j
@Order(100)
public class GXFeignAuthTokenAspect {
    /**
     * Cache resolved method names for stable application method sets.
     */
    private static final ConcurrentHashMap<String, String> METHOD_SIGNATURE_CACHE = new ConcurrentHashMap<>();

    @Pointcut("@annotation(cn.maple.core.framework.annotation.GXHttpInvokerAuthToken) || " +
            "@within(cn.maple.core.framework.annotation.GXHttpInvokerAuthToken) ")
    public void feignAuthTokenPointCut() {
    }

    /**
     * Validates the current request token before executing matched methods.
     *
     * @param point 连接点，包含被拦截方法的信息
     * @throws GXBusinessException       当未找到GXFeignService实现类时抛出
     * @throws GXFeignAuthTokenException 当Token验证失败时抛出
     */
    @Before("feignAuthTokenPointCut()")
    public void before(JoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        GXHttpInvokerAuthToken httpInvokerAuthToken = getAuthTokenAnnotation(point, signature);
        if (Objects.isNull(httpInvokerAuthToken)) {
            return;
        }
        String value = httpInvokerAuthToken.value();
        if (!CharSequenceUtil.equalsIgnoreCase(value, GXHttpInvokerConstant.FEIGN_INVOKER)) {
            return;
        }
        GXFeignService feignService = GXSpringContextUtils.getBean(GXFeignService.class);
        if (Objects.isNull(feignService)) {
            String errorMsg = CharSequenceUtil.format("请实现{}接口", GXFeignService.class.getName());
            log.error("Missing GXFeignService implementation: {}", GXFeignService.class.getName());
            throw new GXBusinessException(errorMsg);
        }

        String methodName = getMethodSignature(point);
        log.debug("Validating Feign auth token");

        if (!feignService.checkTokenValidity()) {
            String errorMsg = CharSequenceUtil.format("Feign调用token校验失败，方法: {}", methodName);
            log.error("Feign auth token validation failed");
            throw new GXFeignAuthTokenException(errorMsg);
        }

        log.debug("Feign auth token validation succeeded");
    }

    /**
     * Resolve method-level annotations first, then class-level annotations.
     */
    private GXHttpInvokerAuthToken getAuthTokenAnnotation(JoinPoint point, MethodSignature signature) {
        Method method = signature.getMethod();
        Class<?> targetClass = Objects.nonNull(point.getTarget())
                ? AopUtils.getTargetClass(point.getTarget())
                : signature.getDeclaringType();
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

        GXHttpInvokerAuthToken annotation = AnnotationUtils.findAnnotation(specificMethod, GXHttpInvokerAuthToken.class);
        if (Objects.nonNull(annotation)) {
            return annotation;
        }

        annotation = AnnotationUtils.findAnnotation(method, GXHttpInvokerAuthToken.class);
        if (Objects.nonNull(annotation)) {
            return annotation;
        }

        return AnnotationUtils.findAnnotation(targetClass, GXHttpInvokerAuthToken.class);
    }

    /**
     * Returns a stable method name for diagnostics and exception messages.
     */
    private String getMethodSignature(JoinPoint point) {
        String signatureKey = point.getSignature().toString();
        return METHOD_SIGNATURE_CACHE.computeIfAbsent(signatureKey, key -> {
            MethodSignature signature = (MethodSignature) point.getSignature();
            return signature.getDeclaringTypeName() + "." + signature.getName();
        });
    }
}
