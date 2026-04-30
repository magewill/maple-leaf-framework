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

@Service
@Slf4j
public class GXDynamicCallMethodServiceImpl implements GXDynamicCallMethodService {
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>(128, 0.75f);

    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>(256, 0.75f);

    private static final ReentrantReadWriteLock CLASS_LOAD_LOCK = new ReentrantReadWriteLock(true);

    private static final int MAX_CACHE_SIZE = 1000;

    @Override
    public Object call(String serviceClassName, String methodName, Object... parameters) {
        if (serviceClassName == null || methodName == null) {
            log.error("动态调用方法失败：类名或方法名不能为空");
            return null;
        }

        try {
            Class<?> targetClass = getClassFromCache(serviceClassName);

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

    @Override
    public Object call(Object target, String methodName, Object... parameters) {
        if (target == null || methodName == null) {
            log.error("动态调用方法失败：目标对象或方法名不能为空");
            return null;
        }

        try {
            return callMethodWithCache(target, methodName, parameters);
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("动态调用方法失败：调用{}对象的{}方法时发生异常，异常信息：{}",
                    target.getClass().getName(), methodName, e.getMessage(), e);
            return null;
        }
    }

    private Class<?> getClassFromCache(String className) throws ClassNotFoundException {
        Objects.requireNonNull(className, "类名不能为null");

        CLASS_LOAD_LOCK.readLock().lock();
        try {
            Class<?> cachedClass = CLASS_CACHE.get(className);
            if (cachedClass != null) {
                return cachedClass;
            }
        } finally {
            CLASS_LOAD_LOCK.readLock().unlock();
        }

        CLASS_LOAD_LOCK.writeLock().lock();
        try {
            Class<?> cachedClass = CLASS_CACHE.get(className);
            if (cachedClass != null) {
                return cachedClass;
            }

            if (CLASS_CACHE.size() >= MAX_CACHE_SIZE) {
                log.warn("类加载缓存达到最大容量{}，执行缓存清理", MAX_CACHE_SIZE);
                CLASS_CACHE.clear();
            }

            Class<?> loadedClass = Class.forName(className);
            CLASS_CACHE.put(className, loadedClass);
            return loadedClass;
        } finally {
            CLASS_LOAD_LOCK.writeLock().unlock();
        }
    }

    private Object callMethodWithCache(Object target, String methodName, Object... parameters) throws Exception {
        Objects.requireNonNull(target, "目标对象不能为null");
        Objects.requireNonNull(methodName, "方法名不能为null");

        parameters = parameters == null ? new Object[0] : parameters;

        String cacheKey = buildMethodCacheKey(target.getClass(), methodName, parameters);

        Method method = METHOD_CACHE.get(cacheKey);
        if (method == null) {
            Object result = GXCommonUtils.reflectCallObjectMethod(target, methodName, parameters);

            try {
                if (METHOD_CACHE.size() < MAX_CACHE_SIZE) {
                    Class<?>[] paramTypes = Arrays.stream(parameters)
                            .map(p -> p != null ? p.getClass() : Object.class)
                            .toArray(Class<?>[]::new);

                    method = target.getClass().getMethod(methodName, paramTypes);
                    METHOD_CACHE.put(cacheKey, method);
                }
            } catch (NoSuchMethodException e) {
                log.debug("无法缓存方法{}，原因：{}", cacheKey, e.getMessage());
            }

            return result;
        }

        try {
            if (!method.canAccess(target)) {
                method.setAccessible(true);
            }
            return method.invoke(target, parameters);
        } catch (Exception e) {
            METHOD_CACHE.remove(cacheKey);
            return GXCommonUtils.reflectCallObjectMethod(target, methodName, parameters);
        }
    }

    private String buildMethodCacheKey(Class<?> targetClass, String methodName, Object... parameters) {
        Objects.requireNonNull(targetClass, "目标类不能为null");
        Objects.requireNonNull(methodName, "方法名不能为null");

        StringBuilder keyBuilder = new StringBuilder(256)
                .append(targetClass.getName())
                .append('#')
                .append(methodName);

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
