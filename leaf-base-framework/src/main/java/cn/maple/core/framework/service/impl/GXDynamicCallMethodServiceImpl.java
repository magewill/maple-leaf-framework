package cn.maple.core.framework.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXDynamicCallMethodService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class GXDynamicCallMethodServiceImpl implements GXDynamicCallMethodService {
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>(128, 0.75f);

    private static final int MAX_CACHE_SIZE = 1000;

    private static final Object CLASS_CACHE_CLEAR_LOCK = new Object();

    @Override
    public Object call(String serviceClassName, String methodName, Object... parameters) {
        if (CharSequenceUtil.isBlank(serviceClassName) || CharSequenceUtil.isBlank(methodName)) {
            log.error("Dynamic method call failed: className and methodName must not be blank");
            return null;
        }

        try {
            Class<?> targetClass = getClassFromCache(serviceClassName);
            Object bean = GXSpringContextUtils.getBean(targetClass);
            if (bean == null) {
                log.error("Dynamic method call failed: bean not found, className={}", serviceClassName);
                return null;
            }
            return call(bean, methodName, parameters);
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Dynamic method call failed: className={}, methodName={}, error={}",
                    serviceClassName, methodName, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Object call(Object target, String methodName, Object... parameters) {
        if (target == null || CharSequenceUtil.isBlank(methodName)) {
            log.error("Dynamic method call failed: target and methodName must not be blank");
            return null;
        }

        try {
            return GXCommonUtils.reflectCallObjectMethod(target, methodName, parameters);
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Dynamic method call failed: targetType={}, methodName={}, error={}",
                    target.getClass().getName(), methodName, e.getMessage(), e);
            return null;
        }
    }

    private Class<?> getClassFromCache(String className) {
        Objects.requireNonNull(className, "Class name must not be null");
        if (CLASS_CACHE.size() >= MAX_CACHE_SIZE && !CLASS_CACHE.containsKey(className)) {
            synchronized (CLASS_CACHE_CLEAR_LOCK) {
                if (CLASS_CACHE.size() >= MAX_CACHE_SIZE && !CLASS_CACHE.containsKey(className)) {
                    CLASS_CACHE.clear();
                    log.warn("Dynamic method class cache cleared: maxSize={}", MAX_CACHE_SIZE);
                }
            }
        }
        return CLASS_CACHE.computeIfAbsent(className, this::loadClass);
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found: " + className, e);
        }
    }
}
