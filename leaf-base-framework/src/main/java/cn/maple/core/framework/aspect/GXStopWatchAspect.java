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
        Object target = point.getTarget();
        Class<?> targetClass = target == null ? methodDeclaringClass(signature) : target.getClass();
        String simpleName = targetClass.getSimpleName();
        Method method = signature.getMethod();
        String name = method.getName();
        String callInfo = CharSequenceUtil.format("{}.{}", simpleName, name);

        if (log.isInfoEnabled()) {
            log.info("{} {} {} : call {} request parameters ---- > {}",
                    traceIdKey, traceId, threadName, callInfo, toJson(buildParameters(method, point.getArgs())));
        }

        long start = System.currentTimeMillis();

        Object result;
        try {
            result = point.proceed();
        } catch (Throwable ex) {
            log.error("{} {} {} : call {} failed ---- > {}",
                    traceIdKey, traceId, threadName, callInfo, ex.getMessage(), ex);
            throw ex;
        } finally {
            long executionTimeMs = System.currentTimeMillis() - start;
            long executionTimeSec = executionTimeMs / 1000;

            if (log.isDebugEnabled()) {
                log.debug("{} {} {} : call {} completed in {} ms ({} s)",
                        traceIdKey, traceId, threadName, callInfo, executionTimeMs, executionTimeSec);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("{} {} {} : call {} response ---- > {}",
                    traceIdKey, traceId, threadName, callInfo, toJson(result));
        }

        return result;
    }

    private Class<?> methodDeclaringClass(MethodSignature signature) {
        Method method = signature.getMethod();
        return method == null ? Object.class : method.getDeclaringClass();
    }

    private Dict buildParameters(Method method, Object[] args) {
        Dict parametersDict = Dict.create();
        Parameter[] parameters = method.getParameters();
        Object[] safeArgs = args == null ? new Object[0] : args;
        int length = Math.min(parameters.length, safeArgs.length);
        for (int i = 0; i < length; i++) {
            parametersDict.set(parameters[i].getName(), safeArgs[i]);
        }
        if (safeArgs.length > parameters.length) {
            for (int i = parameters.length; i < safeArgs.length; i++) {
                parametersDict.set("arg" + i, safeArgs[i]);
            }
        }
        return parametersDict;
    }

    private String toJson(Object value) {
        try {
            return JSONUtil.toJsonStr(value);
        } catch (Exception e) {
            log.warn("Failed to serialize stopwatch value: {}", e.getMessage());
            return CharSequenceUtil.format("<json-serialize-error:{}>", e.getClass().getSimpleName());
        }
    }
}
