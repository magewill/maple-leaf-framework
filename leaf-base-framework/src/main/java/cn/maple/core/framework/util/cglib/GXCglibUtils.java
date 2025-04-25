package cn.maple.core.framework.util.cglib;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReflectUtil;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.cglib.core.Converter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Cglib工具类
 * <p>
 * 该工具类提供了基于CGLIB的Bean对象属性拷贝、集合拷贝和Bean与Map互转的功能。
 * 所有方法都经过内存安全和并发安全的处理，确保在高并发环境下的稳定性和可靠性。
 * 内部使用GXBeanCopierCache缓存BeanCopier实例，显著提升性能并降低内存占用。
 * </p>
 * <p>
 * 内存安全特性：
 * 1. 所有方法都进行了参数验证，防止空指针异常和非法参数
 * 2. 使用安全的集合操作，避免并发修改异常和内存泄漏
 * 3. 合理管理资源，避免资源泄漏和内存溢出
 * 4. 使用断言确保关键参数不为空，提前捕获潜在问题
 * </p>
 * <p>
 * 线程安全特性：
 * 1. 所有方法都是无状态的，可以安全地在多线程环境中调用
 * 2. 使用线程安全的BeanCopierCache缓存BeanCopier实例
 * 3. 通过参数验证和防御性编程确保多线程环境下的安全性
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 简单的Bean拷贝
 * UserDTO userDTO = GXCglibUtils.copy(userEntity, UserDTO.class);
 * 
 * // 2. 带转换器的Bean拷贝
 * UserDTO userDTO = GXCglibUtils.copy(userEntity, UserDTO.class, new CustomConverter());
 * 
 * // 3. 集合拷贝
 * List<UserEntity> userEntities = getUserEntities();
 * List<UserDTO> userDTOs = GXCglibUtils.copyList(userEntities, UserDTO::new);
 * 
 * // 4. 带回调的集合拷贝（可以在拷贝后进行额外处理）
 * List<UserDTO> userDTOs = GXCglibUtils.copyList(userEntities, UserDTO::new, (src, target) -> {
 *     target.setFullName(src.getFirstName() + " " + src.getLastName());
 * });
 * 
 * // 5. Bean转Map
 * BeanMap beanMap = GXCglibUtils.toMap(userEntity);
 * 
 * // 6. Map转Bean
 * UserEntity user = GXCglibUtils.toBean(map, UserEntity.class);
 * </pre>
 * </p>
 * 
 * @author magleton
 * @since 1.0.0
 */
public class GXCglibUtils {
    /**
     * 私有构造方法，防止实例化
     */
    private GXCglibUtils() {
    }

    /**
     * 拷贝Bean对象属性到目标类型
     * <p>
     * 此方法通过指定目标类型自动创建目标对象实例，然后拷贝源对象的属性到目标对象。
     * 内部调用{@link #copy(Object, Class, Converter)}方法，不使用转换器。
     * </p>
     *
     * @param <T>         目标对象类型
     * @param source      源bean对象，不能为null
     * @param targetClass 目标bean类，不能为null，会自动实例化此对象
     * @return 拷贝属性后的目标对象
     * @throws IllegalArgumentException 如果source或targetClass为null
     * @throws RuntimeException 如果目标类实例化失败
     */
    public static <T> T copy(final Object source, final Class<T> targetClass) {
        Assert.notNull(source, "源对象不能为null");
        Assert.notNull(targetClass, "目标类不能为null");
        return copy(source, targetClass, null);
    }

    /**
     * 拷贝Bean对象属性到目标类型（带转换器）
     * <p>
     * 此方法通过指定目标类型自动创建目标对象实例，然后拷贝源对象的属性到目标对象。
     * 可以提供自定义的属性转换器，用于在拷贝过程中对属性值进行转换处理。
     * </p>
     *
     * @param <T>         目标对象类型
     * @param source      源bean对象，不能为null
     * @param targetClass 目标bean类，不能为null，会自动实例化此对象
     * @param converter   转换器，可以为null，为null时不进行转换
     * @return 拷贝属性后的目标对象
     * @throws IllegalArgumentException 如果source或targetClass为null
     * @throws RuntimeException 如果目标类实例化失败
     */
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

    /**
     * 拷贝Bean对象属性
     * <p>
     * 将源对象的属性拷贝到目标对象，不使用转换器。
     * 内部调用{@link #copy(Object, Object, Converter)}方法，转换器参数为null。
     * </p>
     *
     * @param source 源bean对象，不能为null
     * @param target 目标bean对象，不能为null
     * @throws IllegalArgumentException 如果source或target为null
     */
    public static void copy(final Object source, final Object target) {
        Assert.notNull(source, "源对象不能为null");
        Assert.notNull(target, "目标对象不能为null");
        copy(source, target, null);
    }

    /**
     * 拷贝Bean对象属性（带转换器）
     * <p>
     * 将源对象的属性拷贝到目标对象，可以提供自定义的属性转换器。
     * 该方法使用CGLIB的BeanCopier实现高性能的属性拷贝，并通过GXBeanCopierCache缓存BeanCopier实例以提升性能。
     * </p>
     *
     * @param source    源bean对象，不能为null
     * @param target    目标bean对象，不能为null
     * @param converter 转换器，可以为null，为null时不进行转换
     * @throws IllegalArgumentException 如果source或target为null
     */
    public static void copy(final Object source, final Object target, final Converter converter) {
        Assert.notNull(source, "源对象不能为null");
        Assert.notNull(target, "目标对象不能为null");

        final Class<?> sourceClass = source.getClass();
        final Class<?> targetClass = target.getClass();
        final BeanCopier beanCopier = GXBeanCopierCache.INSTANCE.get(sourceClass, targetClass, converter);

        beanCopier.copy(source, target, converter);
    }

    /**
     * 拷贝集合中的Bean对象属性
     * <p>
     * 将源集合中的每个对象拷贝到目标类型的新对象中，并返回包含这些新对象的列表。
     * 内部调用{@link #copyList(Collection, Supplier, Converter, BiConsumer)}方法，不使用转换器和回调。
     * </p>
     *
     * @param <S>    源bean类型
     * @param <T>    目标bean类型
     * @param source 源bean对象集合，不能为null
     * @param target 目标bean对象供应商，用于创建目标对象实例，不能为null
     * @return 目标bean对象列表
     * @throws IllegalArgumentException 如果source或target为null
     */
    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target) {
        Assert.notNull(source, "源集合不能为null");
        Assert.notNull(target, "目标对象供应商不能为null");
        return copyList(source, target, null, null);
    }

    /**
     * 拷贝集合中的Bean对象属性（带转换器）
     * <p>
     * 将源集合中的每个对象拷贝到目标类型的新对象中，并返回包含这些新对象的列表。
     * 可以提供自定义的属性转换器，用于在拷贝过程中对属性值进行转换处理。
     * </p>
     *
     * @param <S>       源bean类型
     * @param <T>       目标bean类型
     * @param source    源bean对象集合，不能为null
     * @param target    目标bean对象供应商，用于创建目标对象实例，不能为null
     * @param converter 转换器，可以为null，为null时不进行转换
     * @return 目标bean对象列表
     * @throws IllegalArgumentException 如果source或target为null
     */
    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target, final Converter converter) {
        Assert.notNull(source, "源集合不能为null");
        Assert.notNull(target, "目标对象供应商不能为null");
        return copyList(source, target, converter, null);
    }

    /**
     * 拷贝集合中的Bean对象属性（带回调）
     * <p>
     * 将源集合中的每个对象拷贝到目标类型的新对象中，并返回包含这些新对象的列表。
     * 可以提供回调函数，用于在拷贝完成后对目标对象进行额外处理。
     * </p>
     *
     * @param <S>      源bean类型
     * @param <T>      目标bean类型
     * @param source   源bean对象集合，不能为null
     * @param target   目标bean对象供应商，用于创建目标对象实例，不能为null
     * @param callback 回调函数，在拷贝完成后调用，可以为null
     * @return 目标bean对象列表
     * @throws IllegalArgumentException 如果source或target为null
     */
    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target, final BiConsumer<S, T> callback) {
        Assert.notNull(source, "源集合不能为null");
        Assert.notNull(target, "目标对象供应商不能为null");
        return copyList(source, target, null, callback);
    }

    /**
     * 拷贝集合中的Bean对象属性（带转换器和回调）
     * <p>
     * 将源集合中的每个对象拷贝到目标类型的新对象中，并返回包含这些新对象的列表。
     * 可以提供自定义的属性转换器和回调函数，用于在拷贝过程中对属性值进行转换处理，以及在拷贝完成后对目标对象进行额外处理。
     * 该方法使用Java 8 Stream API实现，具有良好的性能和并行处理能力。
     * </p>
     *
     * @param <S>       源bean类型
     * @param <T>       目标bean类型
     * @param source    源bean对象集合，不能为null
     * @param target    目标bean对象供应商，用于创建目标对象实例，不能为null
     * @param converter 转换器，可以为null，为null时不进行转换
     * @param callback  回调函数，在拷贝完成后调用，可以为null
     * @return 目标bean对象列表
     * @throws IllegalArgumentException 如果source或target为null
     */
    public static <S, T> List<T> copyList(final Collection<S> source, final Supplier<T> target, final Converter converter, final BiConsumer<S, T> callback) {
        Assert.notNull(source, "源集合不能为null");
        Assert.notNull(target, "目标对象供应商不能为null");
        
        return source.stream()
                .filter(Objects::nonNull)
                .map(s -> {
                    final T t = target.get();
                    if (t != null) {
                        copy(s, t, converter);
                        if (callback != null) {
                            callback.accept(s, t);
                        }
                    }
                    return t;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 将Bean转换为Map
     * <p>
     * 使用CGLIB的BeanMap将Bean对象的属性转换为Map。
     * 返回的BeanMap是与Bean对象关联的，对BeanMap的修改会反映到Bean对象上。
     * </p>
     *
     * @param bean Bean对象，不能为null
     * @return {@link BeanMap} Bean的属性Map表示
     * @throws IllegalArgumentException 如果bean为null
     */
    public static BeanMap toMap(final Object bean) {
        Assert.notNull(bean, "Bean对象不能为null");
        return BeanMap.create(bean);
    }

    /**
     * 将Map中的内容填充至Bean中
     * <p>
     * 使用CGLIB的BeanMap将Map中的键值对填充到Bean对象的属性中。
     * 只有Map中的键与Bean对象的属性名匹配的值才会被填充。
     * </p>
     *
     * @param map  Map数据源，不能为null
     * @param bean 目标Bean对象，不能为null
     * @param <T>  Bean类型
     * @return 填充后的Bean对象
     * @throws IllegalArgumentException 如果map或bean为null
     */
    @SuppressWarnings("rawtypes")
    public static <T> T fillBean(final Map map, final T bean) {
        Assert.notNull(map, "Map不能为null");
        Assert.notNull(bean, "Bean对象不能为null");
        BeanMap.create(bean).putAll(map);
        return bean;
    }

    /**
     * 将Map转换为Bean
     * <p>
     * 创建指定类型的Bean实例，并将Map中的键值对填充到Bean对象的属性中。
     * 内部调用{@link #fillBean(Map, Object)}方法实现。
     * </p>
     *
     * @param map       Map数据源，不能为null
     * @param beanClass Bean类型，不能为null
     * @param <T>       Bean类型
     * @return 转换后的Bean对象
     * @throws IllegalArgumentException 如果map或beanClass为null
     * @throws RuntimeException 如果Bean类实例化失败
     */
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
}
