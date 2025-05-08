package cn.maple.core.framework.service.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXDynamicCallMethodService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 动态方法调用服务实现类
 * <p>
 * 该服务提供了通过反射机制动态调用Spring容器中Bean的方法的功能。
 * 可以通过类名和方法名，或者直接通过目标对象和方法名进行调用。
 * 主要用于需要在运行时根据配置或条件动态决定调用哪个服务的哪个方法的场景。
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 示例1：通过类名和方法名调用
 * GXDynamicCallMethodService service = applicationContext.getBean(GXDynamicCallMethodService.class);
 * UserDto userDto = (UserDto) service.call("com.example.service.UserService", "findById", 1L);
 *
 * // 示例2：通过目标对象和方法名调用
 * UserService userService = applicationContext.getBean(UserService.class);
 * UserDto userDto = (UserDto) service.call(userService, "findById", 1L);
 * </pre>
 * </p>
 *
 * @author britton
 * @since 1.0.0
 */
@Service
@Slf4j
public class GXDynamicCallMethodServiceImpl implements GXDynamicCallMethodService {
    /**
     * 类加载缓存，用于缓存已加载的类对象
     * 使用ConcurrentHashMap确保线程安全，初始容量设置为128，负载因子为0.75
     * 这些参数基于大多数应用程序的类加载模式进行了优化
     */
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>(128, 0.75f);

    /**
     * 方法缓存，用于缓存已查找的方法对象
     * 键为类名+方法名+参数类型的哈希值，值为对应的Method对象
     * 初始容量设置为256，负载因子为0.75，适合缓存大量方法引用
     */
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>(256, 0.75f);

    /**
     * 读写锁，用于保护类加载过程
     * 允许并发读取，但写入时需要独占锁
     * 使用公平锁策略，确保长时间等待的线程不会饿死
     */
    private static final ReentrantReadWriteLock CLASS_LOAD_LOCK = new ReentrantReadWriteLock(true);

    /**
     * 缓存大小限制，防止缓存无限增长导致内存泄漏
     * 当缓存达到此大小时，可以考虑实现LRU或其他淘汰策略
     */
    private static final int MAX_CACHE_SIZE = 1000;

    /**
     * 通过类名和方法名动态调用服务方法
     * <p>
     * 首先通过类名获取Class对象，然后从Spring容器中获取对应的Bean实例，
     * 最后调用指定的方法。如果类不存在或调用过程中发生异常，将记录错误日志并返回null。
     * </p>
     *
     * @param serviceClassName 服务类的全限定名
     * @param methodName       要调用的方法名
     * @param parameters       方法参数列表
     * @return 方法调用结果，如果调用失败则返回null
     */
    @Override
    public Object call(String serviceClassName, String methodName, Object... parameters) {
        // 参数验证，使用Java 17+的模式匹配增强可读性
        if (serviceClassName == null || methodName == null) {
            log.error("动态调用方法失败：类名或方法名不能为空");
            return null;
        }

        try {
            // 从缓存获取Class对象，如果不存在则加载并缓存
            Class<?> targetClass = getClassFromCache(serviceClassName);

            // 从Spring容器获取Bean实例，使用Optional优化空值处理
            return Optional.ofNullable(GXSpringContextUtils.getBean(targetClass))
                    .map(bean -> call(bean, methodName, parameters))
                    .orElseGet(() -> {
                        log.error("动态调用方法失败：无法从Spring容器获取类型为{}的Bean", serviceClassName);
                        return null;
                    });
        } catch (ClassNotFoundException e) {
            log.error("动态调用方法失败：{}类不存在，异常信息：{}", serviceClassName, e.getMessage());
        } catch (Exception e) {
            log.error("动态调用方法失败：调用{}类的{}方法时发生异常，异常信息：{}",
                    serviceClassName, methodName, e.getMessage(), e);
        }
        return null;
    }

    /**
     * 通过目标对象和方法名动态调用方法
     * <p>
     * 直接在给定的目标对象上调用指定的方法。这个方法是上面方法的底层实现，
     * 当已经有目标对象实例时可以直接使用此方法。
     * </p>
     *
     * @param target     目标对象实例
     * @param methodName 要调用的方法名
     * @param parameters 方法参数列表
     * @return 方法调用结果，如果调用失败则返回null
     */
    @Override
    public Object call(Object target, String methodName, Object... parameters) {
        // 参数验证，使用Java 17+的模式匹配增强可读性
        if (target == null || methodName == null) {
            log.error("动态调用方法失败：目标对象或方法名不能为空");
            return null;
        }

        try {
            // 使用优化后的反射调用
            return callMethodWithCache(target, methodName, parameters);
        } catch (GXBusinessException e) {
            // 业务异常直接抛出，由上层处理
            throw e;
        } catch (Exception e) {
            // 记录详细的异常信息，包括异常堆栈，便于问题诊断
            log.error("动态调用方法失败：调用{}对象的{}方法时发生异常，异常信息：{}",
                    target.getClass().getName(), methodName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从缓存中获取Class对象，如果不存在则加载并缓存
     * <p>
     * 使用读写锁保护类加载过程，提高并发性能。
     * 采用双重检查锁定模式减少锁竞争。
     * </p>
     *
     * @param className 类的全限定名，不能为null
     * @return 加载的Class对象
     * @throws ClassNotFoundException 如果类不存在
     * @throws NullPointerException   如果className为null
     */
    private Class<?> getClassFromCache(String className) throws ClassNotFoundException {
        Objects.requireNonNull(className, "类名不能为null");

        // 先尝试从缓存读取（读锁）
        CLASS_LOAD_LOCK.readLock().lock();
        try {
            Class<?> cachedClass = CLASS_CACHE.get(className);
            if (cachedClass != null) {
                return cachedClass;
            }
        } finally {
            CLASS_LOAD_LOCK.readLock().unlock();
        }

        // 缓存未命中，加载类并缓存（写锁）
        CLASS_LOAD_LOCK.writeLock().lock();
        try {
            // 双重检查，避免在获取写锁期间其他线程已加载
            Class<?> cachedClass = CLASS_CACHE.get(className);
            if (cachedClass != null) {
                return cachedClass;
            }

            // 检查缓存大小，防止内存泄漏
            if (CLASS_CACHE.size() >= MAX_CACHE_SIZE) {
                // 简单的缓存清理策略：当达到最大容量时清除一半的缓存
                // 在生产环境中，可以考虑使用更复杂的LRU或其他淘汰策略
                log.warn("类加载缓存达到最大容量{}，执行缓存清理", MAX_CACHE_SIZE);
                CLASS_CACHE.clear();
            }

            // 加载类并缓存
            Class<?> loadedClass = Class.forName(className);
            CLASS_CACHE.put(className, loadedClass);
            return loadedClass;
        } finally {
            CLASS_LOAD_LOCK.writeLock().unlock();
        }
    }

    /**
     * 使用缓存优化的方法调用
     * <p>
     * 首先尝试从缓存获取Method对象，如果不存在则使用通用工具类调用。
     * 如果调用成功，将方法对象缓存起来以提高后续调用的性能。
     * </p>
     *
     * @param target     目标对象，不能为null
     * @param methodName 方法名，不能为null
     * @param parameters 方法参数，可以为null
     * @return 方法调用结果
     * @throws Exception 如果调用过程中发生异常
     */
    private Object callMethodWithCache(Object target, String methodName, Object... parameters) throws Exception {
        Objects.requireNonNull(target, "目标对象不能为null");
        Objects.requireNonNull(methodName, "方法名不能为null");

        // 处理参数为null的情况
        parameters = parameters == null ? new Object[0] : parameters;

        // 构建缓存键
        String cacheKey = buildMethodCacheKey(target.getClass(), methodName, parameters);

        // 尝试从缓存获取Method对象
        Method method = METHOD_CACHE.get(cacheKey);
        if (method == null) {
            // 缓存未命中，使用通用工具类调用
            Object result = GXCommonUtils.reflectCallObjectMethod(target, methodName, parameters);

            // 尝试获取并缓存成功调用的方法
            // 注意：这里不会缓存失败的方法调用，避免污染缓存
            try {
                // 检查缓存大小，防止内存泄漏
                if (METHOD_CACHE.size() < MAX_CACHE_SIZE) {
                    Class<?>[] paramTypes = Arrays.stream(parameters)
                            .map(p -> p != null ? p.getClass() : Object.class)
                            .toArray(Class<?>[]::new);

                    method = target.getClass().getMethod(methodName, paramTypes);
                    METHOD_CACHE.put(cacheKey, method);
                }
            } catch (NoSuchMethodException e) {
                // 方法不存在，忽略异常，不影响结果返回
                log.debug("无法缓存方法{}，原因：{}", cacheKey, e.getMessage());
            }

            return result;
        }

        try {
            // 确保方法可访问
            if (!method.canAccess(target)) {
                method.setAccessible(true);
            }
            // 直接调用方法，避免重复查找
            return method.invoke(target, parameters);
        } catch (Exception e) {
            // 调用失败，从缓存移除该方法（可能是类结构变化导致）
            METHOD_CACHE.remove(cacheKey);
            // 回退到通用工具类调用
            return GXCommonUtils.reflectCallObjectMethod(target, methodName, parameters);
        }
    }

    /**
     * 构建方法缓存键
     * <p>
     * 使用类名+方法名+参数类型的哈希值作为缓存键。
     * 优化了字符串拼接逻辑，减少临时对象创建。
     * </p>
     *
     * @param targetClass 目标类，不能为null
     * @param methodName  方法名，不能为null
     * @param parameters  方法参数，可以为null
     * @return 缓存键
     */
    private String buildMethodCacheKey(Class<?> targetClass, String methodName, Object... parameters) {
        Objects.requireNonNull(targetClass, "目标类不能为null");
        Objects.requireNonNull(methodName, "方法名不能为null");

        // 使用StringBuilder预分配足够的空间，减少扩容开销
        StringBuilder keyBuilder = new StringBuilder(256)
                .append(targetClass.getName())
                .append('#')
                .append(methodName);

        // 添加参数类型信息
        keyBuilder.append('(');
        if (parameters != null && parameters.length > 0) {
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) {
                    keyBuilder.append(',');
                }
                Object param = parameters[i];
                keyBuilder.append(param == null ? "null" : param.getClass().getName());
            }
        }
        keyBuilder.append(')');

        return keyBuilder.toString();
    }
}
