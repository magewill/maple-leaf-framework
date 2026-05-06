package cn.maple.core.framework.aspect;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBeanValidateException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.annotation.PreDestroy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Set;

@Aspect
@Component
@Slf4j
public class GXValidateRequestParamAspect implements Ordered {
    private final ValidatorFactory validatorFactory;

    private final Validator validator;

    public GXValidateRequestParamAspect() {
        this.validatorFactory = Validation.buildDefaultValidatorFactory();
        this.validator = validatorFactory.getValidator();
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.GetMapping)")
    public void requestParamValidate() {
    }

    @Around("requestParamValidate()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        try {
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();

            Object targetBean = resolveTargetBean(point, method);
            if (targetBean == null) {
                log.warn("Target bean is unavailable for request parameter validation: {}", method.getDeclaringClass().getName());
                return point.proceed(point.getArgs());
            }
            Method validateMethod = AopUtils.getMostSpecificMethod(method, targetBean.getClass());

            log.debug("Validating request parameters: {}.{}", validateMethod.getDeclaringClass().getSimpleName(), validateMethod.getName());
            Set<? extends ConstraintViolation<Object>> constraintViolations =
                    validator.forExecutables().validateParameters(targetBean, validateMethod, point.getArgs());

            if (!constraintViolations.isEmpty()) {
                final Dict dict = Dict.create();
                for (ConstraintViolation<Object> constraint : constraintViolations) {
                    final String currentFormName = constraint.getPropertyPath().toString();
                    dict.set(currentFormName, constraint.getMessage());
                    log.debug("Request parameter validation failed: {} - {}", currentFormName, constraint.getMessage());
                }
                throw new GXBeanValidateException("Request parameter validation failed", HttpStatus.HTTP_INTERNAL_ERROR, dict);
            }

            return point.proceed(point.getArgs());
        } catch (GXBeanValidateException e) {
            throw e;
        } catch (Throwable e) {
            throw e;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @PreDestroy
    public void closeValidatorFactory() {
        validatorFactory.close();
    }

    private Object resolveTargetBean(ProceedingJoinPoint point, Method method) {
        Object targetBean = GXSpringContextUtils.getBean(method.getDeclaringClass());
        if (targetBean != null) {
            return targetBean;
        }
        return point.getTarget();
    }
}
