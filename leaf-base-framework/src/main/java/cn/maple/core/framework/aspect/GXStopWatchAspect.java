package cn.maple.core.framework.aspect;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GXStopWatchAspect {
    @Pointcut("@annotation(cn.maple.core.framework.annotation.GXStopWatch) || @within(cn.maple.core.framework.annotation.GXStopWatch)")
    public void stopWatchPointCut() {
    }

    @Around("stopWatchPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        String traceIdKey = GXTraceIdContextUtils.TRACE_ID_KEY;
        String traceId = GXTraceIdContextUtils.getTraceId();
        String threadName = Thread.currentThread().getName();

        MethodSignature signature = (MethodSignature) point.getSignature();
        Class<?> targetClass = point.getTarget().getClass();
        String simpleName = targetClass.getSimpleName();
        Method method = signature.getMethod();
        String name = method.getName();
        String callInfo = CharSequenceUtil.format("{}.{}", simpleName, name);

        Dict parametersDict = Dict.create();
        Parameter[] parameters = method.getParameters();
        Object[] args = point.getArgs();
        int length = parameters.length;
        for (int i = 0; i < length; i++) {
            String key = parameters[i].getName();
            Object realParam = args[i];
            parametersDict.set(key, realParam);
        }

        if (log.isInfoEnabled()) {
            log.info("{} {} {} : 调用{}方法的请求参数 ---- > {}",
                    traceIdKey, traceId, threadName, callInfo, JSONUtil.toJsonStr(parametersDict));
        }

        long start = System.currentTimeMillis();

        Object result;
        try {
            result = point.proceed();
        } catch (Throwable ex) {
            log.error("{} {} {} : 调用{}方法发生异常 ---- > {}",
                    traceIdKey, traceId, threadName, callInfo, ex.getMessage(), ex);
            throw ex;
        } finally {
            long end = System.currentTimeMillis();
            long executionTimeMs = end - start;
            long executionTimeSec = executionTimeMs / 1000;

            if (log.isDebugEnabled()) {
                log.debug("{} {} {} : 调用{}方法总共运行{}毫秒({}秒)",
                        traceIdKey, traceId, threadName, callInfo, executionTimeMs, executionTimeSec);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("{} {} {} : 调用{}方法的响应数据 ---- > {}",
                    traceIdKey, traceId, threadName, callInfo, JSONUtil.toJsonStr(result));
        }

        return result;
    }
}
