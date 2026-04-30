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
import java.util.concurrent.atomic.AtomicReference;

@Component
@Aspect
@Slf4j
public class GXBusinessLogAspect implements Ordered {
    @Pointcut(value = "@annotation(cn.maple.core.framework.annotation.GXBusinessLog)")
    public void pointcut() {
        // 切点定义，无需实现
    }

    @Around("pointcut()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) {
        long beginAt = System.currentTimeMillis();
        log.info("----GXBusinessLogAspect 环绕通知 START----");
        AtomicReference<Object> resultRef = new AtomicReference<>();
        try {
            Object result = proceedingJoinPoint.proceed();
            resultRef.set(result);
        } catch (Throwable throwable) {
            log.error("业务方法执行异常: {}", throwable.getMessage(), throwable);
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            } else {
                throw new RuntimeException("业务方法执行异常", throwable);
            }
        } finally {
            long executionTime = System.currentTimeMillis() - beginAt;
            try {
                saveBusinessLog(proceedingJoinPoint, executionTime);
            } catch (Exception e) {
                log.error("保存业务日志时发生异常: {}", e.getMessage(), e);
            }
            log.info("----GXBusinessLogAspect 环绕通知 END----");
        }
        return resultRef.get();
    }

    private void saveBusinessLog(ProceedingJoinPoint proceedingJoinPoint, long executionTime) {
        try {
            Object target = proceedingJoinPoint.getTarget();
            MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
            GXBusinessLog clazzAnnotation = target.getClass().getAnnotation(GXBusinessLog.class);
            GXBusinessLog methodAnnotation = signature.getMethod().getAnnotation(GXBusinessLog.class);

            GXBusinessLogDto businessLogDto = new GXBusinessLogDto();

            String name = methodAnnotation.name();
            if (CharSequenceUtil.isBlank(name) && Objects.nonNull(clazzAnnotation)) {
                name = clazzAnnotation.name();
            }
            String description = methodAnnotation.description();
            businessLogDto.setBusinessName(name);
            businessLogDto.setBusinessDescription(description);

            String className = target.getClass().getName();
            String methodName = signature.getName();
            businessLogDto.setMethodName(className + "." + methodName + "()");

            Object[] args = proceedingJoinPoint.getArgs();
            String params = JSONUtil.toJsonStr(args);
            businessLogDto.setParams(params);

            businessLogDto.setIp(GXCurrentRequestContextUtils.getClientIP());

            businessLogDto.setExecutionTime(executionTime);

            businessLogDto.setRequestAt(DateUtil.currentSeconds());

            GXBusinessLogService businessLogService = GXSpringContextUtils.getBean(GXBusinessLogService.class);
            if (Objects.nonNull(businessLogService)) {
                String username = businessLogService.getUserName();
                businessLogDto.setUserName(username);

                businessLogService.saveBusinessLog(businessLogDto);
                log.debug("业务日志已保存: {}", businessLogDto.getBusinessName());
            } else {
                log.warn("未找到GXBusinessLogService的实现类，业务日志无法保存");
            }
        } catch (Exception e) {
            log.error("保存业务日志时发生异常: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
