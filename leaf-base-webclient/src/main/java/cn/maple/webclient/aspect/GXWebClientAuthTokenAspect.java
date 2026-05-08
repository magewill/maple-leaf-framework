package cn.maple.webclient.aspect;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.annotation.GXHttpInvokerAuthToken;
import cn.maple.core.framework.constant.GXHttpInvokerConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXWebClientAuthTokenException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.webclient.service.GXWebClientService;
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
 * Validates WebClient invocation tokens for methods or classes annotated with
 * {@link GXHttpInvokerAuthToken} and value {@code webClient}.
 *
 * @author britton gapleaf@63.com
 * @since 1.0.0
 */
@Aspect
@Component
@Slf4j
@Order(100)
public class GXWebClientAuthTokenAspect {
    private static final ConcurrentHashMap<String, String> METHOD_SIGNATURE_CACHE = new ConcurrentHashMap<>();

    /**
     * Matches method-level and class-level WebClient auth token annotations.
     */
    @Pointcut("@annotation(cn.maple.core.framework.annotation.GXHttpInvokerAuthToken) || " +
            "@within(cn.maple.core.framework.annotation.GXHttpInvokerAuthToken) ")
    public void webClientAuthTokenPointCut() {
    }

    /**
     * Validates token before the matched method executes.
     *
     * @param point join point
     */
    @Before("webClientAuthTokenPointCut()")
    public void before(JoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        GXHttpInvokerAuthToken httpInvokerAuthToken = findAuthTokenAnnotation(point, method);
        if (Objects.isNull(httpInvokerAuthToken)) {
            return;
        }
        String value = httpInvokerAuthToken.value();
        if (!CharSequenceUtil.equalsIgnoreCase(value, GXHttpInvokerConstant.WEB_CLIENT_INVOKER)) {
            return;
        }
        GXWebClientService webClientService = GXSpringContextUtils.getBean(GXWebClientService.class);
        if (Objects.isNull(webClientService)) {
            String errorMsg = CharSequenceUtil.format("Please implement {} interface", GXWebClientService.class.getName());
            log.error(errorMsg);
            throw new GXBusinessException(errorMsg);
        }

        String methodName = getMethodSignature(point);
        log.debug("Validate WebClient auth token: method={}", methodName);

        if (!webClientService.checkTokenValidity()) {
            String errorMsg = CharSequenceUtil.format("WebClient auth token validation failed: method={}", methodName);
            log.error(errorMsg);
            throw new GXWebClientAuthTokenException(errorMsg);
        }

        log.debug("WebClient auth token validation succeeded: method={}", methodName);
    }

    private GXHttpInvokerAuthToken findAuthTokenAnnotation(JoinPoint point, Method method) {
        GXHttpInvokerAuthToken annotation = AnnotationUtils.findAnnotation(method, GXHttpInvokerAuthToken.class);
        if (Objects.nonNull(annotation)) {
            return annotation;
        }

        Class<?> targetClass = Objects.nonNull(point.getTarget())
                ? AopUtils.getTargetClass(point.getTarget())
                : method.getDeclaringClass();
        annotation = AnnotationUtils.findAnnotation(targetClass, GXHttpInvokerAuthToken.class);
        if (Objects.nonNull(annotation)) {
            return annotation;
        }

        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), GXHttpInvokerAuthToken.class);
    }

    private String getMethodSignature(JoinPoint point) {
        String signatureKey = point.getSignature().toString();
        return METHOD_SIGNATURE_CACHE.computeIfAbsent(signatureKey, key -> {
            MethodSignature signature = (MethodSignature) point.getSignature();
            return signature.getDeclaringTypeName() + "." + signature.getName();
        });
    }
}
