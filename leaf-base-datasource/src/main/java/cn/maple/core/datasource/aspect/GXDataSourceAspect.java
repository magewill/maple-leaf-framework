package cn.maple.core.datasource.aspect;

import cn.maple.core.datasource.annotation.GXDataSource;
import cn.maple.core.datasource.config.GXDynamicContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Dynamic data source switching aspect.
 */
@Aspect
@Component
@Order(-1000)
@Slf4j
public class GXDataSourceAspect {

    /**
     * Cache class-level @GXDataSource resolution.
     */
    private static final Map<Class<?>, DataSourceCacheEntry> CLASS_ANNOTATION_CACHE = new ConcurrentReferenceHashMap<>();

    /**
     * Method annotation cache key.
     */
    private record MethodCacheKey(Class<?> targetClass, Method method) {
    }

    /**
     * Cache method-level @GXDataSource resolution.
     */
    private static final Map<MethodCacheKey, DataSourceCacheEntry> METHOD_ANNOTATION_CACHE = new ConcurrentReferenceHashMap<>();

    @Pointcut("@annotation(cn.maple.core.datasource.annotation.GXDataSource) || " +
            "@within(cn.maple.core.datasource.annotation.GXDataSource) || " +
            "target(cn.maple.core.datasource.repository.GXMyBatisRepository+) || " +
            "target(cn.maple.core.datasource.service.GXMyBatisBaseService+)")
    public void dataSourcePointCut() {
        // Pointcut marker.
    }

    private DataSourceCacheEntry getDataSourceAnnotationFromClass(Class<?> targetClass) {
        if (targetClass == null) {
            return new DataSourceCacheEntry(false, "");
        }

        return CLASS_ANNOTATION_CACHE.computeIfAbsent(targetClass, clazz -> {
            GXDataSource annotation = AnnotatedElementUtils.findMergedAnnotation(clazz, GXDataSource.class);
            if (annotation != null) {
                String dataSourceValue = normalizeDataSourceValue(annotation.value());
                if (StringUtils.hasText(dataSourceValue)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found class-level @GXDataSource on {}, value={}", clazz.getName(), dataSourceValue);
                    }
                    return new DataSourceCacheEntry(true, dataSourceValue);
                }
                if (log.isWarnEnabled()) {
                    log.warn("Class {} has @GXDataSource but value is blank, fallback to default datasource", clazz.getName());
                }
            }

            // For proxy classes, also scan implemented interfaces.
            for (Class<?> ifc : clazz.getInterfaces()) {
                annotation = AnnotatedElementUtils.findMergedAnnotation(ifc, GXDataSource.class);
                if (annotation != null) {
                    String dataSourceValue = normalizeDataSourceValue(annotation.value());
                    if (StringUtils.hasText(dataSourceValue)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Found interface-level @GXDataSource on {} -> {}, value={}",
                                    clazz.getName(), ifc.getName(), dataSourceValue);
                        }
                        return new DataSourceCacheEntry(true, dataSourceValue);
                    }
                    if (log.isWarnEnabled()) {
                        log.warn("Interface {} on class {} has blank @GXDataSource value, continue searching",
                                ifc.getName(), clazz.getName());
                    }
                }
            }

            if (log.isTraceEnabled()) {
                log.trace("No @GXDataSource found on class {} or its interfaces", clazz.getName());
            }
            return new DataSourceCacheEntry(false, "");
        });
    }

    private DataSourceCacheEntry getDataSourceAnnotationFromMethod(Class<?> targetClass, Method method) {
        if (targetClass == null || method == null) {
            return new DataSourceCacheEntry(false, "");
        }

        MethodCacheKey cacheKey = new MethodCacheKey(targetClass, method);
        return METHOD_ANNOTATION_CACHE.computeIfAbsent(cacheKey, k -> {
            Method targetMethod = AopUtils.getMostSpecificMethod(k.method(), k.targetClass());
            GXDataSource annotation = AnnotatedElementUtils.findMergedAnnotation(targetMethod, GXDataSource.class);
            if (annotation != null) {
                String dataSourceValue = normalizeDataSourceValue(annotation.value());
                if (StringUtils.hasText(dataSourceValue)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found method-level @GXDataSource on {}.{}, value={}",
                                targetClass.getName(), targetMethod.getName(), dataSourceValue);
                    }
                    return new DataSourceCacheEntry(true, dataSourceValue);
                }
                if (log.isWarnEnabled()) {
                    log.warn("Method {}.{} has @GXDataSource but value is blank, fallback to class/default datasource",
                            targetClass.getName(), targetMethod.getName());
                }
            }
            return new DataSourceCacheEntry(false, "");
        });
    }

    /**
     * Normalize datasource value to avoid invalid switch caused by blanks.
     */
    private String normalizeDataSourceValue(String dataSourceValue) {
        return dataSourceValue == null ? "" : dataSourceValue.trim();
    }

    @Around("dataSourcePointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        if (point == null) {
            log.error("ProceedingJoinPoint is null, cannot switch datasource");
            throw new IllegalArgumentException("ProceedingJoinPoint cannot be null");
        }

        if (!(point.getSignature() instanceof MethodSignature signature)) {
            if (log.isErrorEnabled()) {
                log.error("JoinPoint signature is not MethodSignature, skip datasource switch");
            }
            return point.proceed();
        }

        Object target = point.getTarget();
        if (target == null) {
            if (log.isTraceEnabled()) {
                log.trace("Target object is null, skip datasource switch");
            }
            return point.proceed();
        }

        Class<?> targetClass = target.getClass();
        Method method = signature.getMethod();

        boolean isTraceEnabled = log.isTraceEnabled();
        boolean isDebugEnabled = log.isDebugEnabled();
        String threadName = null;
        String methodName = null;
        String className = null;

        if (isTraceEnabled || isDebugEnabled) {
            threadName = Thread.currentThread().getName();
            methodName = method.getName();
            className = targetClass.getName();
            if (isTraceEnabled) {
                log.trace("Start datasource switch handling, thread={}, class={}, method={}", threadName, className, methodName);
            }
        }

        boolean needSwitchDataSource;
        String dataSourceValue;

        DataSourceCacheEntry methodEntry = getDataSourceAnnotationFromMethod(targetClass, method);
        if (methodEntry.needSwitch()) {
            needSwitchDataSource = true;
            dataSourceValue = methodEntry.dataSourceValue();
        } else {
            DataSourceCacheEntry classEntry = getDataSourceAnnotationFromClass(targetClass);
            needSwitchDataSource = classEntry.needSwitch();
            dataSourceValue = classEntry.dataSourceValue();

            if (!needSwitchDataSource && isTraceEnabled) {
                log.trace("No @GXDataSource found on {} or hierarchy, use default datasource", className);
            }
        }

        boolean pushed = false;
        try {
            if (needSwitchDataSource) {
                String previousDataSource = GXDynamicContextHolder.peek();
                GXDynamicContextHolder.push(dataSourceValue);
                pushed = true;

                if (isDebugEnabled) {
                    log.debug("Thread {} datasource switched from [{}] to [{}]", threadName,
                            (previousDataSource != null ? previousDataSource : "default"), dataSourceValue);
                }
            }

            if (isTraceEnabled) {
                log.trace("Thread {} executing method: {}.{}", threadName, className, methodName);
            }
            Object result = point.proceed();
            if (isTraceEnabled) {
                log.trace("Thread {} method executed successfully: {}.{}", threadName, className, methodName);
            }
            return result;
        } finally {
            if (pushed) {
                GXDynamicContextHolder.poll();
                if (isDebugEnabled) {
                    String restoredDataSource = GXDynamicContextHolder.peek();
                    log.debug("Thread {} restore datasource from [{}] to [{}]", threadName, dataSourceValue,
                            (restoredDataSource != null ? restoredDataSource : "default"));
                }
            }
        }
    }

    private record DataSourceCacheEntry(boolean needSwitch, String dataSourceValue) {
    }
}
