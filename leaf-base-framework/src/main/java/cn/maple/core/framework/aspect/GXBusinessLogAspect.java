package cn.maple.core.framework.aspect;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.annotation.GXBusinessLog;
import cn.maple.core.framework.dto.inner.GXBusinessLogDto;
import cn.maple.core.framework.service.GXBusinessLogService;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Aspect
@Slf4j
public class GXBusinessLogAspect implements Ordered {
    @Pointcut(value = "@annotation(cn.maple.core.framework.annotation.GXBusinessLog) || @within(cn.maple.core.framework.annotation.GXBusinessLog)")
    public void pointcut() {
    }

    @Around("pointcut()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        long beginAt = System.currentTimeMillis();
        try {
            return proceedingJoinPoint.proceed();
        } catch (Throwable throwable) {
            log.error("Business method failed: {}", throwable.getMessage(), throwable);
            throw throwable;
        } finally {
            long executionTime = System.currentTimeMillis() - beginAt;
            try {
                saveBusinessLog(proceedingJoinPoint, executionTime);
            } catch (Exception e) {
                log.error("Failed to save business log: {}", e.getMessage(), e);
            }
        }
    }

    private void saveBusinessLog(ProceedingJoinPoint proceedingJoinPoint, long executionTime) {
        try {
            Object target = proceedingJoinPoint.getTarget();
            MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
            GXBusinessLog clazzAnnotation = target.getClass().getAnnotation(GXBusinessLog.class);
            GXBusinessLog methodAnnotation = signature.getMethod().getAnnotation(GXBusinessLog.class);

            GXBusinessLogDto businessLogDto = new GXBusinessLogDto();

            String name = methodAnnotation == null ? "" : methodAnnotation.name();
            if (CharSequenceUtil.isBlank(name) && Objects.nonNull(clazzAnnotation)) {
                name = clazzAnnotation.name();
            }
            String description = methodAnnotation == null ? "" : methodAnnotation.description();
            if (CharSequenceUtil.isBlank(description) && Objects.nonNull(clazzAnnotation)) {
                description = clazzAnnotation.description();
            }
            businessLogDto.setBusinessName(name);
            businessLogDto.setBusinessDescription(description);

            String className = target.getClass().getName();
            String methodName = signature.getName();
            businessLogDto.setMethodName(className + "." + methodName + "()");

            Object[] args = proceedingJoinPoint.getArgs();
            businessLogDto.setParams(toJson(args));

            businessLogDto.setIp(GXCurrentRequestContextUtils.getClientIP());

            businessLogDto.setExecutionTime(executionTime);

            businessLogDto.setRequestAt(DateUtil.currentSeconds());

            GXBusinessLogService businessLogService = GXSpringContextUtils.getBean(GXBusinessLogService.class);
            if (Objects.nonNull(businessLogService)) {
                String username = businessLogService.getUserName();
                businessLogDto.setUserName(username);

                businessLogService.saveBusinessLog(businessLogDto);
                log.debug("Business log saved: {}", businessLogDto.getBusinessName());
            } else {
                log.warn("GXBusinessLogService bean is unavailable, business log skipped");
            }
        } catch (Exception e) {
            log.error("Failed to save business log: {}", e.getMessage(), e);
        }
    }

    private String toJson(Object value) {
        try {
            return JSONUtil.toJsonStr(value);
        } catch (Exception e) {
            log.warn("Failed to serialize business log value: {}", e.getMessage());
            return CharSequenceUtil.format("<json-serialize-error:{}>", e.getClass().getSimpleName());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
