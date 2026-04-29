package cn.maple.core.framework.util.cglib;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReflectUtil;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.cglib.core.Converter;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class GXCglibUtils {
    private GXCglibUtils() {
    }

    public static <T> T copy(final Object source, final Class<T> targetClass) {
        Assert.notNull(source, "源对象不能为null");
        Assert.notNull(targetClass, "目标类不能为null");
        return copy(source, targetClass, null);
    }

    public static <T> T copy(final Object source, final Class<T> targetClass, final Converter converter) {
        Assert.notNull(source, "源对象不能为null");
        Assert.notNull(targetClass, "目标类不能为null");
        final T target = ReflectUtil.newInstanceIfPossible(targetClass);
        if (target == null) {
            throw new RuntimeException("无法实例化目标类: " + targetClass.getName());
        }
        copy(source, target, converter);
        return target;
    }

    public static void copy(final Object source, final Object target) {
        Assert.notNull(source, "源对象不能为null");
        Assert.notNull(target, "目标对象不能为null");
        copy(source, target, null);
    }

    public static void copy(final Object source, final Object target, final Converter converter) {
        Assert.notNull(source, "源对象不能为null");
        Assert.notNull(target, "目标对象不能为null");

        final Class<?> sourceClass = source.getClass();
        final Class<?> targetClass = target.getClass();
        final BeanCopier beanCopier = GXBeanCopierCache.INSTANCE.get(sourceClass, targetClass, converter);

        beanCopier.copy(source, target, converter);
    }

    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target) {
        Assert.notNull(source, "源集合不能为null");
        Assert.notNull(target, "目标对象供应商不能为null");
        return copyList(source, target, null, null);
    }

    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target, final Converter converter) {
        Assert.notNull(source, "源集合不能为null");
        Assert.notNull(target, "目标对象供应商不能为null");
        return copyList(source, target, converter, null);
    }

    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target, final BiConsumer<S, T> callback) {
        Assert.notNull(source, "源集合不能为null");
        Assert.notNull(target, "目标对象供应商不能为null");
        return copyList(source, target, null, callback);
    }

    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target, final Converter converter, final BiConsumer<S, T> callback) {
        Assert.notNull(source, "源集合不能为null");
        Assert.notNull(target, "目标对象供应商不能为null");

        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        final List<T> result = new ArrayList<>(source.size());
        Map<CopierCacheKey, BeanCopier> localBeanCopierCache = null;
        Class<?> lastSourceClass = null;
        Class<?> lastTargetClass = null;
        BeanCopier lastBeanCopier = null;

        for (S s : source) {
            if (Objects.isNull(s)) {
                continue;
            }
            final T t = target.get();
            if (Objects.isNull(t)) {
                continue;
            }

            final Class<?> currentSourceClass = s.getClass();
            final Class<?> currentTargetClass = t.getClass();
            if (lastBeanCopier == null) {
                lastBeanCopier = GXBeanCopierCache.INSTANCE.get(currentSourceClass, currentTargetClass, converter);
                lastSourceClass = currentSourceClass;
                lastTargetClass = currentTargetClass;
            } else if (currentSourceClass != lastSourceClass || currentTargetClass != lastTargetClass) {
                final CopierCacheKey cacheKey = new CopierCacheKey(currentSourceClass, currentTargetClass);
                if (localBeanCopierCache == null) {
                    localBeanCopierCache = new HashMap<>(4);
                    localBeanCopierCache.put(new CopierCacheKey(lastSourceClass, lastTargetClass), lastBeanCopier);
                }
                lastBeanCopier = localBeanCopierCache.computeIfAbsent(cacheKey,
                        key -> GXBeanCopierCache.INSTANCE.get(key.sourceClass(), key.targetClass(), converter));
                lastSourceClass = currentSourceClass;
                lastTargetClass = currentTargetClass;
            }
            lastBeanCopier.copy(s, t, converter);
            if (callback != null) {
                callback.accept(s, t);
            }
            result.add(t);
        }
        return result;
    }

    public static BeanMap toMap(final Object bean) {
        Assert.notNull(bean, "Bean对象不能为null");
        return BeanMap.create(bean);
    }

    @SuppressWarnings("rawtypes")
    public static <T> T fillBean(final Map map, final T bean) {
        Assert.notNull(map, "Map不能为null");
        Assert.notNull(bean, "Bean对象不能为null");
        BeanMap.create(bean).putAll(map);
        return bean;
    }

    @SuppressWarnings("rawtypes")
    public static <T> T toBean(final Map map, final Class<T> beanClass) {
        Assert.notNull(map, "Map不能为null");
        Assert.notNull(beanClass, "Bean类不能为null");
        T bean = ReflectUtil.newInstanceIfPossible(beanClass);
        if (bean == null) {
            throw new RuntimeException("无法实例化Bean类: " + beanClass.getName());
        }
        return fillBean(map, bean);
    }

    private record CopierCacheKey(Class<?> sourceClass, Class<?> targetClass) {
    }
}
