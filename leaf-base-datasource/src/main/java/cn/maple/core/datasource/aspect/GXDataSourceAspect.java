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

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 动态数据源切换处理类
 */
@Aspect
@Component
@Order(-1000) // 显式设定较高的优先级，保证在@Transactional(默认最低优先级)前执行，但避免使用HIGHEST_PRECEDENCE造成的隐式冲突
@Slf4j
public class GXDataSourceAspect {
    /**
     * 类注解缓存，用于存储类及其父类/接口上的@GXDataSource注解信息
     * 使用 ConcurrentReferenceHashMap 避免 ClassLoader 内存泄漏
     */
    private static final Map<Class<?>, DataSourceCacheEntry> CLASS_ANNOTATION_CACHE = new ConcurrentReferenceHashMap<>();

    /**
     * 方法注解缓存用的键
     */
    private record MethodCacheKey(Class<?> targetClass, Method method) {}

    /**
     * 方法注解缓存，用于存储方法上的@GXDataSource注解信息
     * 避免频繁反射调用带来的性能损耗
     */
    private static final Map<MethodCacheKey, DataSourceCacheEntry> METHOD_ANNOTATION_CACHE = new ConcurrentReferenceHashMap<>();

    /**
     * 定义数据源切换的切点
     * 拦截以下情况：
     * 1. 方法上标注了@GXDataSource注解
     * 2. 类上标注了@GXDataSource注解
     * 3. 实现/继承了自定义基础特定类（增强适用性，解决接口代理时@within有时不生效的问题）
     */
    @Pointcut("@annotation(cn.maple.core.datasource.annotation.GXDataSource) || " +
            "@within(cn.maple.core.datasource.annotation.GXDataSource) || " +
            "target(cn.maple.core.datasource.repository.GXMyBatisRepository+) || " +
            "target(cn.maple.core.datasource.service.GXMyBatisBaseService+)")
    public void dataSourcePointCut() {
        // 这是切点标记，用于拦截需要进行数据源切换的方法调用
    }

    /**
     * 获取类级别的数据源注解信息（支持继承和接口）
     *
     * @param targetClass 目标类
     * @return 数据源缓存条目，包含是否需要切换数据源和数据源值
     */
    private DataSourceCacheEntry getDataSourceAnnotationFromClass(Class<?> targetClass) {
        if (targetClass == null) {
            return new DataSourceCacheEntry(false, "");
        }

        return CLASS_ANNOTATION_CACHE.computeIfAbsent(targetClass, clazz -> {
            GXDataSource annotation = AnnotatedElementUtils.findMergedAnnotation(clazz, GXDataSource.class);
            if (annotation != null) {
                if (log.isDebugEnabled()) {
                    log.debug("在类{}上找到@GXDataSource注解，数据源值为{}", clazz.getName(), annotation.value());
                }
                return new DataSourceCacheEntry(true, annotation.value());
            }
            // 如果是代理类，主动扫描其实现的接口
            for (Class<?> ifc : clazz.getInterfaces()) {
                annotation = AnnotatedElementUtils.findMergedAnnotation(ifc, GXDataSource.class);
                if (annotation != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("在类{}的接口{}上找到@GXDataSource注解，数据源值为{}", clazz.getName(), ifc.getName(), annotation.value());
                    }
                    return new DataSourceCacheEntry(true, annotation.value());
                }
            }
            if (log.isTraceEnabled()) {
                log.trace("类{}及其接口上未找到@GXDataSource注解", clazz.getName());
            }
            return new DataSourceCacheEntry(false, "");
        });
    }

    /**
     * 获取方法级别的数据源注解信息
     *
     * @param targetClass 目标类
     * @param method 原始方法
     * @return 数据源缓存条目
     */
    private DataSourceCacheEntry getDataSourceAnnotationFromMethod(Class<?> targetClass, Method method) {
        if (targetClass == null || method == null) {
            return new DataSourceCacheEntry(false, "");
        }

        MethodCacheKey cacheKey = new MethodCacheKey(targetClass, method);
        return METHOD_ANNOTATION_CACHE.computeIfAbsent(cacheKey, k -> {
            Method targetMethod = AopUtils.getMostSpecificMethod(k.method(), k.targetClass());
            GXDataSource annotation = AnnotatedElementUtils.findMergedAnnotation(targetMethod, GXDataSource.class);
            if (annotation != null) {
                if (log.isDebugEnabled()) {
                    log.debug("在方法{}上找到@GXDataSource注解，数据源值为{}", targetMethod.getName(), annotation.value());
                }
                return new DataSourceCacheEntry(true, annotation.value());
            }
            return new DataSourceCacheEntry(false, "");
        });
    }

    /**
     * 环绕通知，在方法执行前后进行数据源切换和恢复
     *
     * @param point 切点对象，包含目标方法的相关信息
     * @return 目标方法的执行结果
     * @throws Throwable 目标方法执行过程中可能抛出的异常
     */
    @Around("dataSourcePointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        if (point == null) {
            log.error("切点对象为null，无法执行数据源切换");
            throw new IllegalArgumentException("切点对象不能为null");
        }

        // 验证切点签名类型
        if (!(point.getSignature() instanceof MethodSignature signature)) {
            if (log.isErrorEnabled()) {
                log.error("切点签名类型不是MethodSignature，无法执行数据源切换");
            }
            return point.proceed();
        }

        Object target = point.getTarget();
        if (target == null) {
            if (log.isTraceEnabled()) {
                log.trace("目标对象为空，跳过数据源切换");
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
                log.trace("开始处理数据源切换，线程: {}, 类: {}, 方法: {}", threadName, className, methodName);
            }
        }

        // 检查是否需要切换数据源
        boolean needSwitchDataSource = false;
        String dataSourceValue = "";

        // 优先检查方法上的注解（支持元注解和组合注解），使用缓存避免频繁反射
        DataSourceCacheEntry methodEntry = getDataSourceAnnotationFromMethod(targetClass, method);
        if (methodEntry.needSwitch()) {
            needSwitchDataSource = true;
            dataSourceValue = methodEntry.dataSourceValue();
        } else {
            // 如果方法上没有注解，则检查类及其父类/接口
            DataSourceCacheEntry classEntry = getDataSourceAnnotationFromClass(targetClass);
            needSwitchDataSource = classEntry.needSwitch();
            dataSourceValue = classEntry.dataSourceValue();

            if (!needSwitchDataSource && isTraceEnabled) {
                log.trace("类{}及其父类/接口上均未找到@GXDataSource注解，使用默认数据源", className);
            }
        }

        boolean pushed = false;
        try {
            if (needSwitchDataSource) {
                String previousDataSource = GXDynamicContextHolder.peek();
                GXDynamicContextHolder.push(dataSourceValue);
                pushed = true;
                
                if (isDebugEnabled) {
                    log.debug("{}线程数据源从[{}]切换为[{}]", threadName,
                            (previousDataSource != null ? previousDataSource : "默认"), dataSourceValue);
                }
            }

            if (isTraceEnabled) {
                log.trace("{}线程执行方法: {}.{}", threadName, className, methodName);
            }
            Object result = point.proceed();
            if (isTraceEnabled) {
                log.trace("{}线程成功执行方法: {}.{}", threadName, className, methodName);
            }
            return result;
        } finally {
            // 只有在当前切面实际进行了数据源切换时才清除，避免清除上层设置的数据源
            if (pushed) {
                GXDynamicContextHolder.poll();
                if (isDebugEnabled) {
                    // 获取恢复后的数据源，用于日志记录
                    String restoredDataSource = GXDynamicContextHolder.peek();
                    log.debug("{}线程清除数据源[{}]，恢复为[{}]", threadName, dataSourceValue,
                            (restoredDataSource != null ? restoredDataSource : "默认"));
                }
            }
        }
    }

    /**
     * 数据源缓存条目，存储数据源值和是否需要切换的标志
     */
    private record DataSourceCacheEntry(boolean needSwitch, String dataSourceValue) {
    }
}