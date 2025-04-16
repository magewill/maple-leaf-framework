package cn.maple.core.datasource.aspect;

import cn.maple.core.datasource.annotation.GXDataSource;
import cn.maple.core.datasource.config.GXDynamicContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 动态数据源切换处理类
 * <p>
 * 该切面负责在方法执行前后动态切换数据源，支持多种切换场景：
 * 1. 方法上标注了@GXDataSource注解
 * 2. 类上标注了@GXDataSource注解（包括父类继承的注解）
 * 3. 目标对象是框架内置的特定类型
 * 4. 调用MyBatis Plus的特定方法
 * <p>
 * 使用方式：
 * 1. 如果调用的是项目内的功能，需要在XXXRepository上添加@GXDataSource("other")
 * 2. 如果需要调用MyBatis Plus封装的功能，需要在XXXService上添加@GXDataSource("other")
 * <p>
 * 内存安全优化：
 * 1. 使用基于软引用(SoftReference)的缓存机制，在内存不足时允许JVM回收缓存对象
 * 2. 实现缓存大小限制和定期清理机制，防止缓存无限增长
 * 3. 只在必要时切换数据源，避免不必要的ThreadLocal操作
 * 4. 确保在所有执行路径上正确清理ThreadLocal资源，防止内存泄漏
 * 5. 缓存条目使用不可变对象，保证线程安全
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GXDataSourceAspect {
    /**
     * 类注解缓存，用于存储类及其父类上的@GXDataSource注解信息
     * 键为类对象，值为包含数据源信息的软引用对象
     * 使用ConcurrentHashMap确保线程安全，适合高并发环境
     * 使用软引用(SoftReference)机制，在内存不足时允许JVM回收缓存对象
     */
    private static final Map<Class<?>, SoftReference<DataSourceCacheEntry>> CLASS_ANNOTATION_CACHE = new ConcurrentHashMap<>();
    /**
     * 缓存大小限制，防止缓存无限增长
     * 当缓存条目数量超过此值时，将触发清理操作
     */
    private static final int MAX_CACHE_SIZE = 500;
    /**
     * 当前缓存条目数量计数器
     */
    private static final AtomicInteger CACHE_COUNT = new AtomicInteger(0);
    /**
     * 缓存命中计数器，用于统计缓存效率
     */
    private static final AtomicInteger CACHE_HIT_COUNT = new AtomicInteger(0);
    /**
     * 缓存访问总次数计数器
     */
    private static final AtomicInteger CACHE_ACCESS_COUNT = new AtomicInteger(0);

    /**
     * 定义数据源切换的切点
     * 拦截以下情况：
     * 1. 方法上标注了@GXDataSource注解
     * 2. 类上标注了@GXDataSource注解
     * 3. 目标对象是GXMyBatisBaseService的实现类
     * 4. 目标对象是GXMyBatisRepository的实现类
     * 5. 调用MyBatis Plus的ServiceImpl类的任何方法
     * 6. 调用MyBatis Plus的BaseMapper接口的任何方法
     */
    @Pointcut("@annotation(cn.maple.core.datasource.annotation.GXDataSource) " +
            "|| @within(cn.maple.core.datasource.annotation.GXDataSource) " +
            "|| target(cn.maple.core.datasource.service.GXMyBatisBaseService) " +
            "|| target(cn.maple.core.datasource.repository.GXMyBatisRepository) " +
            "|| execution(* com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.*(..)) " +
            "|| execution(* com.baomidou.mybatisplus.core.mapper.BaseMapper.*(..))")

    public void dataSourcePointCut() {
        // 这是切点标记，用于拦截需要进行数据源切换的方法调用
    }

    /**
     * 从类的继承层次结构中查找@GXDataSource注解
     * 使用基于软引用的缓存机制避免重复查找，提高性能
     * 同时实现缓存大小控制和自动清理，防止内存泄漏
     *
     * @param targetClass 目标类
     * @return 数据源缓存条目，包含是否需要切换数据源和数据源值
     */
    private DataSourceCacheEntry findDataSourceAnnotationInHierarchy(Class<?> targetClass) {
        // 更新缓存访问计数
        CACHE_ACCESS_COUNT.incrementAndGet();

        // 首先检查缓存中是否已存在且软引用未被回收
        SoftReference<DataSourceCacheEntry> cachedEntryRef = CLASS_ANNOTATION_CACHE.get(targetClass);
        if (cachedEntryRef != null) {
            DataSourceCacheEntry cachedEntry = cachedEntryRef.get();
            if (cachedEntry != null) {
                // 缓存命中，更新命中计数
                CACHE_HIT_COUNT.incrementAndGet();
                return cachedEntry;
            } else {
                // 软引用已被回收，从缓存中移除
                CLASS_ANNOTATION_CACHE.remove(targetClass);
                CACHE_COUNT.decrementAndGet();
            }
        }

        // 检查缓存大小，如果超过限制则清理部分缓存
        checkAndCleanCache();

        // 检查目标类上是否有注解
        GXDataSource annotation = targetClass.getAnnotation(GXDataSource.class);
        if (annotation != null) {
            DataSourceCacheEntry entry = new DataSourceCacheEntry(true, annotation.value());
            cacheEntry(targetClass, entry);
            return entry;
        }

        // 递归检查父类
        Class<?> superClass = targetClass.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            DataSourceCacheEntry superEntry = findDataSourceAnnotationInHierarchy(superClass);
            if (superEntry.needSwitch) {
                // 缓存结果以避免将来重复查找
                cacheEntry(targetClass, superEntry);
                return superEntry;
            }
        }

        // 没有找到注解，缓存空结果
        DataSourceCacheEntry emptyEntry = new DataSourceCacheEntry(false, "");
        cacheEntry(targetClass, emptyEntry);
        return emptyEntry;
    }

    /**
     * 将数据源缓存条目添加到缓存中
     * 使用软引用包装缓存条目，允许在内存不足时被GC回收
     *
     * @param targetClass 目标类
     * @param entry       数据源缓存条目
     */
    private void cacheEntry(Class<?> targetClass, DataSourceCacheEntry entry) {
        CLASS_ANNOTATION_CACHE.put(targetClass, new SoftReference<>(entry));
        CACHE_COUNT.incrementAndGet();
    }

    /**
     * 检查缓存大小并在必要时清理部分缓存
     * 当缓存大小超过限制时，清理无效的软引用和最早创建的缓存条目
     * 清理策略：
     * 1. 首先清理所有无效的软引用（已被GC回收的对象）
     * 2. 如果清理后仍超过限制，则按创建时间排序，删除最旧的25%缓存条目
     */
    private void checkAndCleanCache() {
        // 如果缓存大小未超过限制，则不进行清理
        if (CACHE_COUNT.get() < MAX_CACHE_SIZE) {
            return;
        }

        // 记录清理前的缓存大小
        int beforeSize = CACHE_COUNT.get();

        // 清理无效的软引用
        CLASS_ANNOTATION_CACHE.entrySet().removeIf(entry -> {
            boolean shouldRemove = entry.getValue().get() == null;
            if (shouldRemove) {
                CACHE_COUNT.decrementAndGet();
            }
            return shouldRemove;
        });

        // 如果清理无效引用后仍然超过限制，则清理最早创建的25%的缓存条目
        if (CACHE_COUNT.get() >= MAX_CACHE_SIZE) {
            int targetSize = (int) (MAX_CACHE_SIZE * 0.75);
            int toRemove = CACHE_COUNT.get() - targetSize;

            if (toRemove > 0) {
                // 记录缓存效率统计信息
                if (CACHE_ACCESS_COUNT.get() > 0) {
                    double hitRate = (double) CACHE_HIT_COUNT.get() / CACHE_ACCESS_COUNT.get() * 100;
                    log.debug("数据源缓存命中率: {}%, 当前大小: {}, 清理前大小: {}",
                            String.format("%.2f", hitRate), CACHE_COUNT.get(), beforeSize);
                }

                // 收集所有有效的缓存条目及其创建时间
                Map<Class<?>, Long> entryTimes = new HashMap<>();
                CLASS_ANNOTATION_CACHE.forEach((clazz, ref) -> {
                    DataSourceCacheEntry entry = ref.get();
                    if (entry != null) {
                        entryTimes.put(clazz, entry.creationTime);
                    }
                });

                // 按创建时间排序，获取最早创建的条目
                List<Class<?>> oldestEntries = entryTimes.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .limit(toRemove)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

                // 删除最早创建的条目
                for (Class<?> clazz : oldestEntries) {
                    CLASS_ANNOTATION_CACHE.remove(clazz);
                    CACHE_COUNT.decrementAndGet();
                }

                // 重置计数器
                CACHE_HIT_COUNT.set(0);
                CACHE_ACCESS_COUNT.set(0);

                log.debug("数据源缓存超过限制 ({}), 已清理 {} 个最旧条目", MAX_CACHE_SIZE, oldestEntries.size());
            }
        }
    }

    /**
     * 环绕通知，在方法执行前后进行数据源切换和恢复
     * 数据源切换的优先级：方法注解 > 类注解 > 父类注解
     *
     * @param point 切点对象，包含目标方法的相关信息
     * @return 目标方法的执行结果
     * @throws Throwable 目标方法执行过程中可能抛出的异常
     */
    @Around("dataSourcePointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Class<?> targetClass = point.getTarget().getClass();
        Method method = signature.getMethod();

        // 检查是否需要切换数据源
        boolean needSwitchDataSource = false;
        String dataSourceValue = "";

        // 优先检查方法上的注解
        GXDataSource methodDataSourceAnnotation = method.getAnnotation(GXDataSource.class);
        if (methodDataSourceAnnotation != null) {
            needSwitchDataSource = true;
            dataSourceValue = methodDataSourceAnnotation.value();
        } else {
            // 如果方法上没有注解，则检查类及其父类
            DataSourceCacheEntry entry = findDataSourceAnnotationInHierarchy(targetClass);
            needSwitchDataSource = entry.needSwitch;
            dataSourceValue = entry.dataSourceValue;
        }

        // 如果需要切换数据源，则进行切换
        if (needSwitchDataSource) {
            GXDynamicContextHolder.push(dataSourceValue);
            log.debug("{}线程设置的数据源是{}", Thread.currentThread().getName(), dataSourceValue);
        }

        try {
            return point.proceed();
        } finally {
            // 只有在当前切面实际进行了数据源切换时才清除，避免清除上层设置的数据源
            if (needSwitchDataSource) {
                GXDynamicContextHolder.poll();
                log.debug("{}线程清除数据源", Thread.currentThread().getName());
            }
        }
    }

    /**
     * 数据源缓存条目，存储数据源值和是否需要切换的标志
     * 不可变对象，线程安全
     * 包含创建时间戳，用于实现基于时间的缓存清理策略
     */
    private static class DataSourceCacheEntry {
        final boolean needSwitch;
        final String dataSourceValue;
        final long creationTime;

        DataSourceCacheEntry(boolean needSwitch, String dataSourceValue) {
            this.needSwitch = needSwitch;
            this.dataSourceValue = dataSourceValue;
            this.creationTime = System.currentTimeMillis();
        }
    }
}