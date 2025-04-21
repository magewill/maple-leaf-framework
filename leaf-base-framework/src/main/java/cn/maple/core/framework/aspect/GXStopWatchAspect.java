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

/**
 * 方法执行时间监控切面
 * <p>
 * 该切面用于监控被@GXStopWatch注解标记的方法或类中所有方法的执行时间。
 * 它记录方法的调用参数、返回结果和执行耗时，便于性能分析和问题排查。
 * 通过集成分布式追踪ID，可以在微服务环境中追踪完整的调用链。
 * </p>
 * 
 * @author britton@126.com
 * @since 2021-10-19 15:23
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 确保该切面最先执行
@Slf4j
public class GXStopWatchAspect {
    /**
     * 定义切点，匹配被@GXStopWatch注解标记的方法或类中的所有方法
     */
    @Pointcut("@annotation(cn.maple.core.framework.annotation.GXStopWatch) || @within(cn.maple.core.framework.annotation.GXStopWatch)")
    public void stopWatchPointCut() {
        // 这只是切点标记，不需要实现
    }

    /**
     * 环绕通知，在目标方法执行前后记录执行时间和相关信息
     * <p>
     * 该方法在目标方法执行前记录开始时间和参数信息，
     * 在目标方法执行后记录结束时间、返回结果和执行耗时。
     * 所有信息都会带有分布式追踪ID，便于在分布式系统中追踪完整调用链。
     * </p>
     *
     * @param point 连接点，包含目标方法的相关信息
     * @return 目标方法的返回值
     * @throws Throwable 如果目标方法执行过程中抛出异常
     */
    @Around("stopWatchPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        // 获取追踪ID相关信息
        String traceIdKey = GXTraceIdContextUtils.TRACE_ID_KEY;
        String traceId = GXTraceIdContextUtils.getTraceId();
        String threadName = Thread.currentThread().getName();
        
        // 获取目标方法信息
        MethodSignature signature = (MethodSignature) point.getSignature();
        Class<?> targetClass = point.getTarget().getClass();
        String simpleName = targetClass.getSimpleName();
        Method method = signature.getMethod();
        String name = method.getName();
        String callInfo = CharSequenceUtil.format("{}.{}", simpleName, name);
        
        // 记录方法参数
        Dict parametersDict = Dict.create();
        Parameter[] parameters = method.getParameters();
        Object[] args = point.getArgs();
        int length = parameters.length;
        for (int i = 0; i < length; i++) {
            String key = parameters[i].getName();
            Object realParam = args[i];
            parametersDict.set(key, realParam);
        }
        
        // 记录方法调用开始信息
        if (log.isInfoEnabled()) {
            log.info("{} {} {} : 调用{}方法的请求参数 ---- > {}", 
                    traceIdKey, traceId, threadName, callInfo, JSONUtil.toJsonStr(parametersDict));
        }
        
        // 记录开始时间
        long start = System.currentTimeMillis();
        
        // 执行目标方法
        Object result;
        try {
            result = point.proceed();
        } catch (Throwable ex) {
            // 记录异常信息
            log.error("{} {} {} : 调用{}方法发生异常 ---- > {}", 
                    traceIdKey, traceId, threadName, callInfo, ex.getMessage(), ex);
            throw ex;
        } finally {
            // 计算执行时间
            long end = System.currentTimeMillis();
            long executionTimeMs = end - start;
            long executionTimeSec = executionTimeMs / 1000;
            
            // 记录执行时间
            if (log.isDebugEnabled()) {
                log.debug("{} {} {} : 调用{}方法总共运行{}毫秒({}秒)", 
                        traceIdKey, traceId, threadName, callInfo, executionTimeMs, executionTimeSec);
            }
        }
        
        // 记录返回结果
        if (log.isDebugEnabled()) {
            log.debug("{} {} {} : 调用{}方法的响应数据 ---- > {}", 
                    traceIdKey, traceId, threadName, callInfo, JSONUtil.toJsonStr(result));
        }
        
        return result;
    }
}
