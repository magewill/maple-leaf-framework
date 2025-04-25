package cn.maple.core.framework.util.cglib;

import cn.hutool.core.map.WeakConcurrentMap;
import cn.hutool.core.util.StrUtil;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.core.Converter;

/**
 * BeanCopier属性缓存工具类
 * <p>
 * 该类使用枚举单例模式实现，提供了BeanCopier实例的缓存功能，有效防止多次反射创建BeanCopier实例造成的性能问题。
 * 内部使用WeakConcurrentMap作为缓存容器，具有以下特点：
 * 1. 线程安全：支持并发环境下的安全访问和更新
 * 2. 内存优化：使用弱引用机制，当内存不足时允许JVM回收缓存的BeanCopier对象
 * 3. 高性能：通过缓存复用已创建的BeanCopier实例，显著提升对象属性拷贝的性能
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 直接获取BeanCopier实例
 * BeanCopier copier = GXBeanCopierCache.INSTANCE.get(SourceClass.class, TargetClass.class, false);
 * 
 * // 使用获取的BeanCopier进行对象拷贝
 * SourceClass source = new SourceClass();
 * TargetClass target = new TargetClass();
 * copier.copy(source, target, null);
 * 
 * // 使用自定义转换器
 * Converter converter = new CustomConverter();
 * BeanCopier copierWithConverter = GXBeanCopierCache.INSTANCE.get(SourceClass.class, TargetClass.class, converter);
 * copierWithConverter.copy(source, target, converter);
 * </pre>
 * </p>
 * 
 * @author magleton
 * @since 1.0.0
 */
public enum GXBeanCopierCache {
    /**
     * BeanCopier属性缓存单例
     * 枚举单例确保线程安全和唯一实例
     */
    INSTANCE;

    /**
     * BeanCopier缓存容器
     * 使用WeakConcurrentMap实现线程安全的弱引用缓存，在内存压力大时允许GC回收不常用的BeanCopier实例
     */
    private final WeakConcurrentMap<String, BeanCopier> cache = new WeakConcurrentMap<>();

    /**
     * 获取BeanCopier实例，根据源类、目标类和转换器
     * <p>
     * 该方法会根据提供的源类、目标类和转换器获取对应的BeanCopier实例。
     * 如果缓存中已存在匹配的实例，则直接返回；否则创建新实例并缓存。
     * 方法内部会自动判断转换器是否为null，并调用相应的重载方法。
     * </p>
     *
     * @param srcClass    源Bean的类，不能为null
     * @param targetClass 目标Bean的类，不能为null
     * @param converter   类型转换器，可以为null
     * @return 对应的BeanCopier实例
     * @throws NullPointerException 如果srcClass或targetClass为null
     */
    public BeanCopier get(final Class<?> srcClass, final Class<?> targetClass, final Converter converter) {
        if (srcClass == null || targetClass == null) {
            throw new NullPointerException("源类或目标类不能为null");
        }
        return get(srcClass, targetClass, null != converter);
    }

    /**
     * 获取BeanCopier实例，根据源类、目标类和是否使用转换器
     * <p>
     * 该方法是{@link #get(Class, Class, Converter)}的重载版本，
     * 允许直接指定是否使用转换器而不需要提供具体的转换器实例。
     * 方法内部使用缓存机制，相同参数的多次调用会返回同一个BeanCopier实例，提高性能。
     * </p>
     *
     * @param srcClass     源Bean的类，不能为null
     * @param targetClass  目标Bean的类，不能为null
     * @param useConverter 是否使用转换器
     * @return 对应的BeanCopier实例
     * @throws NullPointerException 如果srcClass或targetClass为null
     */
    public BeanCopier get(final Class<?> srcClass, final Class<?> targetClass, final boolean useConverter) {
        if (srcClass == null || targetClass == null) {
            throw new NullPointerException("源类或目标类不能为null");
        }
        final String key = genKey(srcClass, targetClass, useConverter);
        return cache.computeIfAbsent(key, (k) -> BeanCopier.create(srcClass, targetClass, useConverter));
    }

    /**
     * 生成缓存键
     * <p>
     * 根据源类、目标类和是否使用转换器生成唯一的缓存键。
     * 生成的键格式为：srcClassName#targetClassName#1（使用转换器）或 srcClassName#targetClassName#0（不使用转换器）
     * 该方法为私有方法，仅供内部使用。
     * </p>
     *
     * @param srcClass     源Bean的类，不能为null
     * @param targetClass  目标Bean的类，不能为null
     * @param useConverter 是否使用转换器
     * @return 生成的缓存键字符串
     */
    private String genKey(Class<?> srcClass, Class<?> targetClass, boolean useConverter) {
        final StringBuilder key = StrUtil.builder()
                .append(srcClass.getName())
                .append('#').append(targetClass.getName())
                .append('#').append(useConverter ? 1 : 0);
        return key.toString();
    }
}
