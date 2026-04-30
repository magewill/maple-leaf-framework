package cn.maple.core.framework.aspect;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBeanValidateException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Set;

@Aspect
@Component
@Slf4j
public class GXValidateRequestParamAspect implements Ordered {
    private static final Validator validator;

    static {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.GetMapping)")
    public void requestParamValidate() {
    }

    @Around("requestParamValidate()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        try {
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();

            Object targetBean = GXSpringContextUtils.getBean(method.getDeclaringClass());
            if (targetBean == null) {
                log.error("无法获取目标类的Bean实例: {}", method.getDeclaringClass().getName());
                return point.proceed(point.getArgs());
            }

            log.debug("验证请求参数: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
            Set<? extends ConstraintViolation<Object>> constraintViolations =
                    validator.forExecutables().validateParameters(targetBean, method, point.getArgs());

            if (!constraintViolations.isEmpty()) {
                final Dict dict = Dict.create();
                for (ConstraintViolation<Object> constraint : constraintViolations) {
                    final String currentFormName = constraint.getPropertyPath().toString();
                    dict.set(currentFormName, constraint.getMessage());
                    log.debug("参数验证失败: {} - {}", currentFormName, constraint.getMessage());
                }
                throw new GXBeanValidateException("请求参数验证失败", HttpStatus.HTTP_INTERNAL_ERROR, dict);
            }

            return point.proceed(point.getArgs());
        } catch (GXBeanValidateException e) {
            throw e;
        } catch (Throwable e) {
            //log.error("参数验证过程中发生异常: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100; // 确保在事务切面之前执行
    }
}
