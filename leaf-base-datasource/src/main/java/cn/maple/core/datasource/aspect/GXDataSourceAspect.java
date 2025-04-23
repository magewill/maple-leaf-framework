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
 * 3. 目标对象是框架内置的特定类型（如GXMyBatisDao的实现类）
 * 4. 目标对象是GXMyBatisBaseService的实现类
 * <p>
 * 数据源切换优先级：
 * 1. 方法级注解 - 最高优先级
 * 2. 类级注解 - 次优先级
 * 3. 父类注解 - 最低优先级
 * <p>
 * 使用方式：
 * 1. 在Dao类上添加注解：
 * <pre>
 * @GXDataSource("slave")
 * public class UserDao extends GXMyBatisDao<UserEntity> {
 *     // 所有方法都会使用slave数据源
 * }
 * </pre>
 * 
 * 2. 在Service类上添加注解：
 * <pre>
 * @GXDataSource("master")
 * public class UserServiceImpl implements UserService {
 *     @Autowired
 *     private UserDao userDao;
 *     
 *     // 所有方法都会使用master数据源
 *     public void createUser(UserEntity user) {
 *         userDao.insert(user);
 *     }
 *     
 *     // 方法级注解会覆盖类级注解
 *     @GXDataSource("slave")
 *     public List<UserEntity> getUserList() {
 *         return userDao.selectList(null);
 *     }
 * }
 * </pre>
 * 
 * 3. 在具体方法上添加注解：
 * <pre>
 * public class OrderService {
 *     @GXDataSource("order_db")
 *     public void createOrder(OrderEntity order) {
 *         // 该方法使用order_db数据源
 *     }
 * }
 * </pre>
 * 
 * 4. 嵌套调用场景：
 * <pre>
 * @GXDataSource("outer")
 * public class OuterService {
 *     @Autowired
 *     private InnerService innerService;
 *     
 *     public void outerMethod() {
 *         // 使用outer数据源
 *         innerService.innerMethod(); // 内部方法使用inner数据源
 *         // 返回后继续使用outer数据源
 *     }
 * }
 * 
 * @GXDataSource("inner")
 * public class InnerService {
 *     public void innerMethod() {
 *         // 使用inner数据源
 *     }
 * }
 * </pre>
 * 
 * <p>
 * 内存安全优化：
 * 1. 使用基于软引用(SoftReference)的缓存机制，在内存不足时允许JVM回收缓存对象
 * 2. 实现缓存大小限制和定期清理机制，防止缓存无限增长
 * 3. 只在必要时切换数据源，避免不必要的ThreadLocal操作
 * 4. 确保在所有执行路径上正确清理ThreadLocal资源，防止内存泄漏
 * 5. 缓存条目使用不可变对象，保证线程安全
 * 6. 使用基于时间的缓存淘汰策略，优先清理最早创建的缓存条目
 * 7. 定期记录缓存命中率统计信息，便于性能监控和调优
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GXDataSourceAspect {
    /**
     * 类注解缓存，用于存储类及其父类上的@GXDataSource注解信息
     * <p>
     * 缓存结构：
     * - 键为类对象(Class<?>)，用于快速查找特定类的注解信息
     * - 值为包含数据源信息的软引用对象(SoftReference<DataSourceCacheEntry>)
     * <p>
     * 设计考虑：
     * - 使用ConcurrentHashMap确保线程安全，适合高并发环境
     * - 使用软引用(SoftReference)机制，在内存不足时允许JVM回收缓存对象
     * - 缓存查找比反射操作更高效，显著提升性能
     * - 支持自动清理机制，防止内存泄漏
     */
    private static final Map<Class<?>, SoftReference<DataSourceCacheEntry>> CLASS_ANNOTATION_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 缓存大小限制，防止缓存无限增长
     * <p>
     * 当缓存条目数量超过此值时，将触发清理操作：
     * 1. 首先清理所有无效的软引用（已被GC回收的对象）
     * 2. 如果清理后仍超过限制，则按创建时间排序，删除最旧的25%缓存条目
     * <p>
     * 调优建议：
     * - 对于小型应用，可以适当减小此值，如200-300
     * - 对于大型应用或微服务架构，可以适当增大此值，如800-1000
     * - 如果应用中的类数量较多，建议增大此值以提高缓存命中率
     */
    private static final int MAX_CACHE_SIZE = 500;
    
    /**
     * 当前缓存条目数量计数器
     * <p>
     * 用于跟踪缓存中的条目数量，触发缓存清理机制
     * 使用AtomicInteger确保线程安全，避免并发更新问题
     */
    private static final AtomicInteger CACHE_COUNT = new AtomicInteger(0);
    
    /**
     * 缓存命中计数器，用于统计缓存效率
     * <p>
     * 记录缓存命中的次数，用于计算缓存命中率
     * 缓存命中率 = CACHE_HIT_COUNT / CACHE_ACCESS_COUNT * 100%
     * 高命中率表示缓存策略有效，低命中率可能需要调整缓存大小或策略
     */
    private static final AtomicInteger CACHE_HIT_COUNT = new AtomicInteger(0);
    
    /**
     * 缓存访问总次数计数器
     * <p>
     * 记录缓存被访问的总次数，包括命中和未命中的情况
     * 用于计算缓存命中率和评估缓存效率
     * 定期清理后会重置此计数器，便于分段统计缓存效率
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
    @Pointcut("@annotation(cn.maple.core.datasource.annotation.GXDataSource) || " +
            "@within(cn.maple.core.datasource.annotation.GXDataSource) || " +
            "execution(public * cn.maple.core.datasource.dao.GXMyBatisDao+.*(..)) || " +
            "execution(public * cn.maple.core.datasource.service.GXMyBatisBaseService.*(..)) ")
    public void dataSourcePointCut() {
        // 这是切点标记，用于拦截需要进行数据源切换的方法调用
    }

    /**
     * 从类的继承层次结构中查找@GXDataSource注解
     * 使用基于软引用的缓存机制避免重复查找，提高性能
     * 同时实现缓存大小控制和自动清理，防止内存泄漏
     * <p>
     * 查找策略：
     * 1. 首先检查缓存中是否已存在目标类的注解信息
     * 2. 如果缓存命中且软引用未被回收，直接返回缓存结果
     * 3. 如果缓存未命中或软引用已被回收，检查目标类上是否有注解
     * 4. 如果目标类上没有注解，递归检查其父类
     * 5. 将查找结果缓存，避免重复查找
     * <p>
     * 性能优化：
     * 1. 使用缓存减少反射操作，显著提高性能
     * 2. 采用软引用机制，在内存压力大时允许JVM回收缓存对象
     * 3. 统计缓存命中率，便于监控和调优
     * 4. 递归查找时优先使用缓存结果，减少递归深度
     *
     * @param targetClass 目标类
     * @return 数据源缓存条目，包含是否需要切换数据源和数据源值
     */
    private DataSourceCacheEntry findDataSourceAnnotationInHierarchy(Class<?> targetClass) {
        // 更新缓存访问计数
        CACHE_ACCESS_COUNT.incrementAndGet();
        
        if (targetClass == null) {
            log.warn("尝试在null类上查找@GXDataSource注解，返回空结果");
            return new DataSourceCacheEntry(false, "");
        }
        
        String className = targetClass.getName();
        log.trace("在类{}上查找@GXDataSource注解", className);

        // 首先检查缓存中是否已存在且软引用未被回收
        SoftReference<DataSourceCacheEntry> cachedEntryRef = CLASS_ANNOTATION_CACHE.get(targetClass);
        if (cachedEntryRef != null) {
            DataSourceCacheEntry cachedEntry = cachedEntryRef.get();
            if (cachedEntry != null) {
                // 缓存命中，更新命中计数
                CACHE_HIT_COUNT.incrementAndGet();
                log.trace("类{}的@GXDataSource注解信息缓存命中", className);
                return cachedEntry;
            } else {
                // 软引用已被回收，从缓存中移除
                CLASS_ANNOTATION_CACHE.remove(targetClass);
                CACHE_COUNT.decrementAndGet();
                log.debug("类{}的@GXDataSource注解信息软引用已被回收，从缓存中移除", className);
            }
        }

        // 检查缓存大小，如果超过限制则清理部分缓存
        checkAndCleanCache();

        // 检查目标类上是否有注解
        GXDataSource annotation = targetClass.getAnnotation(GXDataSource.class);
        if (annotation != null) {
            String dataSourceValue = annotation.value();
            log.debug("在类{}上直接找到@GXDataSource注解，数据源值为{}", className, dataSourceValue);
            DataSourceCacheEntry entry = new DataSourceCacheEntry(true, dataSourceValue);
            cacheEntry(targetClass, entry);
            return entry;
        }

        // 递归检查父类
        Class<?> superClass = targetClass.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            log.trace("在类{}上未找到@GXDataSource注解，检查父类{}", className, superClass.getName());
            DataSourceCacheEntry superEntry = findDataSourceAnnotationInHierarchy(superClass);
            if (superEntry.needSwitch) {
                // 缓存结果以避免将来重复查找
                log.debug("在类{}的父类中找到@GXDataSource注解，数据源值为{}", className, superEntry.dataSourceValue);
                cacheEntry(targetClass, superEntry);
                return superEntry;
            }
        }

        // 没有找到注解，缓存空结果
        log.trace("在类{}及其父类中均未找到@GXDataSource注解", className);
        DataSourceCacheEntry emptyEntry = new DataSourceCacheEntry(false, "");
        cacheEntry(targetClass, emptyEntry);
        return emptyEntry;
    }

    /**
     * 将数据源缓存条目添加到缓存中
     * 使用软引用包装缓存条目，允许在内存不足时被GC回收
     * <p>
     * 缓存管理策略：
     * 1. 使用软引用包装缓存条目，在内存不足时允许JVM回收
     * 2. 更新缓存计数器，用于触发缓存清理机制
     * 3. 记录详细日志，便于问题排查和性能监控
     *
     * @param targetClass 目标类
     * @param entry       数据源缓存条目
     */
    private void cacheEntry(Class<?> targetClass, DataSourceCacheEntry entry) {
        if (targetClass == null) {
            log.warn("尝试缓存null类的数据源注解信息，操作被忽略");
            return;
        }
        
        if (entry == null) {
            log.warn("尝试缓存null数据源条目，操作被忽略");
            return;
        }
        
        try {
            // 记录缓存操作的详细信息
            String className = targetClass.getName();
            boolean hasDataSource = entry.needSwitch;
            String dataSourceValue = entry.dataSourceValue;
            
            // 添加到缓存并更新计数
            CLASS_ANNOTATION_CACHE.put(targetClass, new SoftReference<>(entry));
            int currentCount = CACHE_COUNT.incrementAndGet();
            
            if (hasDataSource) {
                log.trace("缓存类{}的数据源注解信息，数据源值为{}，当前缓存大小: {}", 
                        className, dataSourceValue, currentCount);
            } else {
                log.trace("缓存类{}的空数据源注解信息，当前缓存大小: {}", className, currentCount);
            }
            
            // 如果缓存大小接近限制，记录警告日志
            if (currentCount > MAX_CACHE_SIZE * 0.9) {
                log.warn("数据源注解缓存大小({})接近限制({}), 即将触发清理", currentCount, MAX_CACHE_SIZE);
            }
        } catch (Exception e) {
            // 捕获并记录异常，但不影响主流程
            log.error("缓存数据源注解信息时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查缓存大小并在必要时清理部分缓存
     * 当缓存大小超过限制时，清理无效的软引用和最早创建的缓存条目
     * <p>
     * 清理策略：
     * 1. 首先清理所有无效的软引用（已被GC回收的对象）
     * 2. 如果清理后仍超过限制，则按创建时间排序，删除最旧的25%缓存条目
     * <p>
     * 性能优化考虑：
     * 1. 只在缓存大小达到阈值时才执行清理，避免频繁清理影响性能
     * 2. 使用removeIf方法进行原子操作，减少并发冲突
     * 3. 清理后重置计数器，便于下一轮统计
     * 4. 记录缓存命中率，便于监控和调优
     */
    private void checkAndCleanCache() {
        try {
            // 如果缓存大小未超过限制，则不进行清理
            if (CACHE_COUNT.get() < MAX_CACHE_SIZE) {
                return;
            }
    
            log.debug("开始清理数据源注解缓存，当前缓存大小: {}, 限制大小: {}", CACHE_COUNT.get(), MAX_CACHE_SIZE);
            
            // 记录清理前的缓存大小
            int beforeSize = CACHE_COUNT.get();
            AtomicInteger removedNullRefs = new AtomicInteger();
    
            // 清理无效的软引用
            CLASS_ANNOTATION_CACHE.entrySet().removeIf(entry -> {
                boolean shouldRemove = entry.getValue().get() == null;
                if (shouldRemove) {
                    CACHE_COUNT.decrementAndGet();
                    removedNullRefs.getAndIncrement();
                }
                return shouldRemove;
            });
            
            if (removedNullRefs.get() > 0) {
                log.debug("已清理{}个无效软引用，清理后缓存大小: {}", removedNullRefs, CACHE_COUNT.get());
            }
    
            // 如果清理无效引用后仍然超过限制，则清理最早创建的25%的缓存条目
            if (CACHE_COUNT.get() >= MAX_CACHE_SIZE) {
                int targetSize = (int) (MAX_CACHE_SIZE * 0.75);
                int toRemove = CACHE_COUNT.get() - targetSize;
    
                if (toRemove > 0) {
                    // 记录缓存效率统计信息
                    if (CACHE_ACCESS_COUNT.get() > 0) {
                        double hitRate = (double) CACHE_HIT_COUNT.get() / CACHE_ACCESS_COUNT.get() * 100;
                        log.info("数据源缓存统计 - 命中率: {}%, 访问次数: {}, 命中次数: {}, 当前大小: {}, 清理前大小: {}",
                                String.format("%.2f", hitRate), 
                                CACHE_ACCESS_COUNT.get(),
                                CACHE_HIT_COUNT.get(),
                                CACHE_COUNT.get(), 
                                beforeSize);
                    }
    
                    // 收集所有有效的缓存条目及其创建时间
                    Map<Class<?>, Long> entryTimes = new HashMap<>();
                    CLASS_ANNOTATION_CACHE.forEach((clazz, ref) -> {
                        DataSourceCacheEntry entry = ref.get();
                        if (entry != null) {
                            entryTimes.put(clazz, entry.creationTime);
                        }
                    });
    
                    if (entryTimes.isEmpty()) {
                        log.warn("缓存清理异常：未找到有效的缓存条目，但缓存计数为{}", CACHE_COUNT.get());
                        // 重置计数器，修正可能的计数错误
                        CACHE_COUNT.set(0);
                        return;
                    }
    
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
                        log.trace("从缓存中移除类: {}", clazz.getName());
                    }
    
                    // 重置计数器
                    CACHE_HIT_COUNT.set(0);
                    CACHE_ACCESS_COUNT.set(0);
    
                    log.info("数据源缓存清理完成 - 超过限制 ({}), 已清理 {} 个最旧条目, 当前缓存大小: {}", 
                            MAX_CACHE_SIZE, oldestEntries.size(), CACHE_COUNT.get());
                }
            }
        } catch (Exception e) {
            // 捕获并记录异常，但不影响主流程
            log.error("数据源缓存清理过程中发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 环绕通知，在方法执行前后进行数据源切换和恢复
     * 数据源切换的优先级：方法注解 > 类注解 > 父类注解
     * <p>
     * 执行流程：
     * 1. 获取目标方法和类信息
     * 2. 检查方法上是否有@GXDataSource注解
     * 3. 如果方法上没有注解，则检查类及其父类
     * 4. 根据注解信息决定是否切换数据源
     * 5. 执行目标方法
     * 6. 在finally块中恢复原数据源，确保资源正确释放
     * <p>
     * 线程安全保证：
     * 1. 使用ThreadLocal存储数据源上下文，确保线程隔离
     * 2. 采用栈结构管理数据源切换，支持嵌套调用场景
     * 3. 在所有执行路径上正确清理ThreadLocal资源，防止内存泄漏
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
        
        if (!(point.getSignature() instanceof MethodSignature)) {
            log.error("切点签名类型不是MethodSignature，无法执行数据源切换");
            return point.proceed();
        }
        
        MethodSignature signature = (MethodSignature) point.getSignature();
        Class<?> targetClass = point.getTarget().getClass();
        Method method = signature.getMethod();
        String methodName = method.getName();
        String className = targetClass.getName();
        
        log.trace("开始处理数据源切换，类: {}, 方法: {}", className, methodName);

        // 检查是否需要切换数据源
        boolean needSwitchDataSource = false;
        String dataSourceValue = "";

        // 优先检查方法上的注解
        GXDataSource methodDataSourceAnnotation = method.getAnnotation(GXDataSource.class);
        if (methodDataSourceAnnotation != null) {
            needSwitchDataSource = true;
            dataSourceValue = methodDataSourceAnnotation.value();
            log.debug("方法{}上找到@GXDataSource注解，数据源值为{}", methodName, dataSourceValue);
        } else {
            // 如果方法上没有注解，则检查类及其父类
            DataSourceCacheEntry entry = findDataSourceAnnotationInHierarchy(targetClass);
            needSwitchDataSource = entry.needSwitch;
            dataSourceValue = entry.dataSourceValue;
            
            if (needSwitchDataSource) {
                log.debug("类{}上找到@GXDataSource注解，数据源值为{}", className, dataSourceValue);
            } else {
                log.trace("类{}及其父类上均未找到@GXDataSource注解，使用默认数据源", className);
            }
        }

        // 如果需要切换数据源，则进行切换
        String threadName = Thread.currentThread().getName();
        if (needSwitchDataSource) {
            // 获取当前数据源，用于日志记录
            String previousDataSource = GXDynamicContextHolder.peek();
            GXDynamicContextHolder.push(dataSourceValue);
            log.debug("{}线程数据源从[{}]切换为[{}]", threadName, 
                    (previousDataSource != null ? previousDataSource : "默认"), dataSourceValue);
        }

        try {
            log.trace("{}线程执行方法: {}.{}", threadName, className, methodName);
            Object result = point.proceed();
            log.trace("{}线程成功执行方法: {}.{}", threadName, className, methodName);
            return result;
        } catch (Throwable e) {
            log.error("{}线程执行方法{}时发生异常: {}", threadName, methodName, e.getMessage(), e);
            throw e;
        } finally {
            // 只有在当前切面实际进行了数据源切换时才清除，避免清除上层设置的数据源
            if (needSwitchDataSource) {
                GXDynamicContextHolder.poll();
                // 获取恢复后的数据源，用于日志记录
                String restoredDataSource = GXDynamicContextHolder.peek();
                log.debug("{}线程清除数据源[{}]，恢复为[{}]", threadName, dataSourceValue, 
                        (restoredDataSource != null ? restoredDataSource : "默认"));
            }
        }
    }

    /**
     * 数据源缓存条目，存储数据源值和是否需要切换的标志
     * 不可变对象，线程安全
     * 包含创建时间戳，用于实现基于时间的缓存清理策略
     * <p>
     * 设计考虑：
     * 1. 使用final字段确保对象不可变，提高线程安全性
     * 2. 记录创建时间，支持基于时间的缓存淘汰策略
     * 3. 结构简单高效，减少内存占用
     * 4. 无状态设计，避免并发修改问题
     * <p>
     * 字段说明：
     * - needSwitch: 是否需要切换数据源的标志
     * - dataSourceValue: 数据源名称，如果needSwitch为false则为空字符串
     * - creationTime: 缓存条目创建时间，用于实现LRU缓存清理策略
     */
    private static class DataSourceCacheEntry {
        /**
         * 是否需要切换数据源的标志
         * true表示需要切换数据源，false表示不需要切换
         */
        final boolean needSwitch;
        
        /**
         * 数据源名称
         * 如果needSwitch为true，则为注解指定的数据源名称
         * 如果needSwitch为false，则为空字符串
         */
        final String dataSourceValue;
        
        /**
         * 缓存条目创建时间
         * 用于实现基于时间的缓存淘汰策略
         * 创建时间越早的条目，在缓存清理时优先被移除
         */
        final long creationTime;

        /**
         * 创建数据源缓存条目
         * 
         * @param needSwitch 是否需要切换数据源
         * @param dataSourceValue 数据源名称
         */
        DataSourceCacheEntry(boolean needSwitch, String dataSourceValue) {
            this.needSwitch = needSwitch;
            this.dataSourceValue = dataSourceValue;
            this.creationTime = System.currentTimeMillis();
        }
    }
}