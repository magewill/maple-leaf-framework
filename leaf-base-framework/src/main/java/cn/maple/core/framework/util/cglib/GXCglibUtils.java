package cn.maple.core.framework.util.cglib;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.framework.convert.GXCGLibDataConvert;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.cglib.core.Converter;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 基于 CGLIB {@link BeanCopier} / {@link BeanMap} 的对象拷贝与 Map-Bean 互转工具。
 *
 * <p>使用约定：</p>
 * <ul>
 *     <li>拷贝只按属性名匹配。不传 {@code converter} 时，<b>类型不兼容的字段会被静默跳过</b>（不是转换失败，而是根本不会调用 setter），
 *     需要类型转换请显式传入 {@code GXCGLibDataConvert.getConverter(targetClass)}。</li>
 *     <li>{@link BeanCopier} 依赖 getter 取值，因此 <b>Map 作为 source 时不会拷贝任何字段</b>；Map 转 Bean 请使用 {@link #toBean(Map, Class)}。</li>
 *     <li>{@link #copyList} 会跳过 {@code null} 元素，返回的 {@link List} 始终是可变的 {@link ArrayList}。</li>
 * </ul>
 */
public class GXCglibUtils {
    private GXCglibUtils() {
    }

    /**
     * 创建目标类型实例并拷贝属性。
     *
     * @param source      源对象，不可为 null
     * @param targetClass 目标类型，必须可实例化（非接口、非抽象类），不可为 null
     * @return 新的目标实例，不会为 null
     */
    public static <T> T copy(final Object source, final Class<T> targetClass) {
        Assert.notNull(source, "Source object must not be null");
        Assert.notNull(targetClass, "Target class must not be null");
        return copy(source, targetClass, null);
    }

    /**
     * 创建目标类型实例并按指定转换器拷贝属性。
     *
     * @param source      源对象，不可为 null
     * @param targetClass 目标类型，必须可实例化（非接口、非抽象类），不可为 null
     * @param converter   字段转换器，可为 null
     * @return 新的目标实例，不会为 null
     */
    public static <T> T copy(final Object source, final Class<T> targetClass, final Converter converter) {
        Assert.notNull(source, "Source object must not be null");
        Assert.notNull(targetClass, "Target class must not be null");
        final T target = newInstance(targetClass);
        copy(source, target, converter);
        return target;
    }

    /**
     * 把源对象属性拷贝到已存在的目标对象。
     *
     * @param source 源对象，不可为 null
     * @param target 目标对象，不可为 null
     */
    public static void copy(final Object source, final Object target) {
        Assert.notNull(source, "Source object must not be null");
        Assert.notNull(target, "Target object must not be null");
        copy(source, target, null);
    }

    /**
     * 把源对象属性拷贝到已存在的目标对象，并按指定转换器处理字段。
     *
     * @param source    源对象，不可为 null
     * @param target    目标对象，不可为 null
     * @param converter 字段转换器，可为 null
     */
    public static void copy(final Object source, final Object target, final Converter converter) {
        Assert.notNull(source, "Source object must not be null");
        Assert.notNull(target, "Target object must not be null");

        final Class<?> sourceClass = source.getClass();
        final Class<?> targetClass = target.getClass();
        final BeanCopier beanCopier = GXBeanCopierCache.INSTANCE.get(sourceClass, targetClass, converter);

        beanCopier.copy(source, target, converter);
    }

    /**
     * 批量拷贝，目标实例由 supplier 提供。
     *
     * @param source 源集合，不可为 null
     * @param target 目标实例工厂，每次调用必须返回非 null 的新实例
     * @return 可变 {@link ArrayList}；源集合为空时返回空的可变列表
     */
    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target) {
        Assert.notNull(source, "Source collection must not be null");
        Assert.notNull(target, "Target supplier must not be null");
        return copyList(source, target, null, null);
    }

    /**
     * 批量拷贝，目标实例由 supplier 提供，并按指定转换器处理字段。
     *
     * @param source    源集合，不可为 null
     * @param target    目标实例工厂，每次调用必须返回非 null 的新实例
     * @param converter 字段转换器，可为 null
     * @return 可变 {@link ArrayList}；源集合为空时返回空的可变列表
     */
    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target, final Converter converter) {
        Assert.notNull(source, "Source collection must not be null");
        Assert.notNull(target, "Target supplier must not be null");
        return copyList(source, target, converter, null);
    }

    /**
     * 批量拷贝，目标实例由 supplier 提供，并在每个元素拷贝完成后回调。
     *
     * @param source   源集合，不可为 null
     * @param target   目标实例工厂，每次调用必须返回非 null 的新实例
     * @param callback 每个成功拷贝的元素回调一次（{@code null} 源元素不触发），可为 null
     * @return 可变 {@link ArrayList}；源集合为空时返回空的可变列表
     */
    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target, final BiConsumer<S, T> callback) {
        Assert.notNull(source, "Source collection must not be null");
        Assert.notNull(target, "Target supplier must not be null");
        return copyList(source, target, null, callback);
    }

    /**
     * 批量拷贝到指定目标类型。
     *
     * @param source      源集合，不可为 null
     * @param targetClass 目标类型，必须可实例化（非接口、非抽象类），不可为 null
     * @return 可变 {@link ArrayList}；源集合为空时返回空的可变列表
     */
    public static <S, T> List<T> copyList(final Collection<S> source, final Class<T> targetClass) {
        return copyList(source, targetClass, null, null);
    }

    /**
     * 批量拷贝到指定目标类型，并按指定转换器处理字段。
     *
     * @param source      源集合，不可为 null
     * @param targetClass 目标类型，必须可实例化（非接口、非抽象类），不可为 null
     * @param converter   字段转换器，可为 null
     * @return 可变 {@link ArrayList}；源集合为空时返回空的可变列表
     */
    public static <S, T> List<T> copyList(final Collection<S> source, final Class<T> targetClass, final Converter converter) {
        return copyList(source, targetClass, converter, null);
    }

    /**
     * 批量拷贝到指定目标类型，并按指定转换器处理字段，每个元素拷贝完成后回调。
     *
     * @param source      源集合，不可为 null
     * @param targetClass 目标类型，必须可实例化（非接口、非抽象类），不可为 null
     * @param converter   字段转换器，可为 null
     * @param callback    每个成功拷贝的元素回调一次（{@code null} 源元素不触发），可为 null
     * @return 可变 {@link ArrayList}；源集合为空时返回空的可变列表
     */
    public static <S, T> List<T> copyList(final Collection<S> source, final Class<T> targetClass, final Converter converter, final BiConsumer<S, T> callback) {
        Assert.notNull(source, "Source collection must not be null");
        Assert.notNull(targetClass, "Target class must not be null");
        return copyList(source, () -> newInstance(targetClass), converter, callback);
    }

    /**
     * 批量拷贝的核心实现。
     *
     * <p>{@code null} 源元素会被跳过（不产生目标实例、不触发回调）；元素的实际类型不一致时会按
     * 「源类型 + 目标类型」重新获取 {@link BeanCopier}，因此支持多态集合，但返回列表中可能混有不同类型的目标对象。</p>
     *
     * @param source    源集合，不可为 null
     * @param target    目标实例工厂，每次调用必须返回非 null 的新实例，返回 null 会抛 {@link IllegalStateException}
     * @param converter 字段转换器，可为 null
     * @param callback  每个成功拷贝的元素回调一次，可为 null
     * @return 可变 {@link ArrayList}；源集合为空时返回空的可变列表
     */
    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target, final Converter converter, final BiConsumer<S, T> callback) {
        Assert.notNull(source, "Source collection must not be null");
        Assert.notNull(target, "Target supplier must not be null");

        if (source.isEmpty()) {
            return new ArrayList<>(0);
        }
        final List<T> result = new ArrayList<>(source.size());
        // 单类型集合是主流场景：用"上一个 copier"做零分配快路径，多态集合再回落到全局缓存查找
        Class<?> lastSourceClass = null;
        Class<?> lastTargetClass = null;
        BeanCopier lastBeanCopier = null;

        for (S s : source) {
            if (Objects.isNull(s)) {
                continue;
            }
            final T t = target.get();
            if (Objects.isNull(t)) {
                throw new IllegalStateException("Target supplier returned null for source class: " + s.getClass().getName());
            }

            final Class<?> currentSourceClass = s.getClass();
            final Class<?> currentTargetClass = t.getClass();
            if (lastBeanCopier == null || currentSourceClass != lastSourceClass || currentTargetClass != lastTargetClass) {
                lastBeanCopier = GXBeanCopierCache.INSTANCE.get(currentSourceClass, currentTargetClass, converter);
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

    /**
     * 把 Bean 转为 {@link BeanMap}。
     *
     * <p><b>注意</b>：返回的是与 {@code bean} 双向绑定的实时视图（修改 map 会写回 bean），且 {@link BeanMap} 非线程安全。
     * 需要快照请使用 {@code new HashMap<>(GXCglibUtils.toMap(bean))}。</p>
     *
     * @param bean 目标 bean，不可为 null
     * @return 与 bean 绑定的 {@link BeanMap}，不会为 null
     */
    public static BeanMap toMap(final Object bean) {
        Assert.notNull(bean, "Bean object must not be null");
        return BeanMap.create(bean);
    }

    /**
     * 用 Map 中的值填充已存在的 bean。
     *
     * <p>仅处理 Map 中存在的键：键统一转为属性名字符串（支持非 String 键），bean 上不存在的属性忽略；
     * 值按 {@link GXCGLibDataConvert} 的规则转换到属性类型；只有 getter 没有 setter 的只读属性会被跳过而不是抛异常。</p>
     *
     * @param map  Map 数据源，不可为 null
     * @param bean 目标 bean，不可为 null
     * @return 填充后的同一个 bean 实例，不会为 null
     */
    public static <T> T fillBean(final Map<?, ?> map, final T bean) {
        Assert.notNull(map, "Map must not be null");
        Assert.notNull(bean, "Bean object must not be null");
        final BeanMap beanMap = BeanMap.create(bean);
        final GXCGLibDataConvert converter = GXCGLibDataConvert.getConverter(bean.getClass());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            final Object rawKey = entry.getKey();
            if (rawKey == null) {
                continue;
            }
            final String propertyName = String.valueOf(rawKey);
            if (!beanMap.containsKey(propertyName)) {
                continue;
            }
            final Class<?> propertyType = beanMap.getPropertyType(propertyName);
            final Object convertedValue = converter.convert(entry.getValue(), propertyType, propertyName);
            if (convertedValue == null && propertyType.isPrimitive()) {
                continue;
            }
            // 只读属性（只有 getter 没有 setter）在 BeanMap 上写入是空操作，不会抛异常，无需特殊处理；
            // 写入失败（如 setter 内部校验拒绝）必须向上传播，不能被吞掉
            beanMap.put(propertyName, convertedValue);
        }
        return bean;
    }

    /**
     * 把 Map 转换为指定类型的 bean。
     *
     * @param map       Map 数据源，不可为 null
     * @param beanClass 目标类型，必须可实例化（非接口、非抽象类），不可为 null
     * @return 填充后的新 bean 实例，不会为 null
     */
    public static <T> T toBean(final Map<?, ?> map, final Class<T> beanClass) {
        Assert.notNull(map, "Map must not be null");
        Assert.notNull(beanClass, "Bean class must not be null");
        return fillBean(map, newInstance(beanClass));
    }

    /**
     * 实例化目标类型，失败时快速报错而不是返回 null 导致后续 NPE。
     */
    private static <T> T newInstance(final Class<T> targetClass) {
        final T instance = ReflectUtil.newInstanceIfPossible(targetClass);
        if (instance == null) {
            throw new RuntimeException("Cannot instantiate target class: " + targetClass.getName());
        }
        return instance;
    }
}
