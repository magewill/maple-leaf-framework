package cn.maple.core.framework.util.cglib;

import org.jspecify.annotations.NonNull;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.core.Converter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link BeanCopier} 实例缓存。
 *
 * <p>以「源类型」作为 {@link ClassValue} 槽位、以「目标类型 + 是否使用转换器」作为槽内键：</p>
 * <ul>
 *     <li>键是强引用（{@link Class} 与 {@code record} 组合），同一个类型对只会生成一次字节码，缓存可以真正命中；</li>
 *     <li>条目挂在<b>源类型</b>的 {@link Class} 上，源类型被卸载时整槽释放，不需要弱引用键；</li>
 *     <li>但槽内以目标类型为键，属于强引用：若「目标类型先于源类型卸载」（跨 ClassLoader 场景），
 *     目标类型会被钉住直到源类型卸载。框架内 DTO 与实体通常由同一 ClassLoader 加载，不会触发该场景。</li>
 * </ul>
 */
public enum GXBeanCopierCache {
    INSTANCE;

    private final ClassValue<ConcurrentMap<CopierKey, BeanCopier>> cache = new ClassValue<>() {
        @Override
        protected ConcurrentMap<CopierKey, BeanCopier> computeValue(final @NonNull Class<?> type) {
            return new ConcurrentHashMap<>(8);
        }
    };

    /**
     * 获取（必要时创建）指定类型对的 {@link BeanCopier}。
     *
     * <p>注意：这里<b>只用 converter 是否为空来决定 copier 的类型</b>，converter 实例本身不会被缓存。
     * 调用 {@link BeanCopier#copy} 时必须把同一个 converter 再传一次，否则生成的字节码会在运行期
     * 因 converter 为 null 而抛 {@link NullPointerException}。</p>
     *
     * @param srcClass    源类型，不可为 null
     * @param targetClass 目标类型，不可为 null
     * @param converter   字段转换器，为 null 时创建不使用转换器的 copier；非 null 时创建使用转换器的 copier
     * @return 该类型对对应的 {@link BeanCopier}，不会为 null
     */
    public BeanCopier get(final Class<?> srcClass, final Class<?> targetClass, final Converter converter) {
        return get(srcClass, targetClass, null != converter);
    }

    /**
     * 获取（必要时创建）指定类型对的 {@link BeanCopier}。
     *
     * @param srcClass     源类型，不可为 null
     * @param targetClass  目标类型，不可为 null
     * @param useConverter 是否创建启用转换器的 copier
     * @return 该类型对对应的 {@link BeanCopier}，不会为 null
     */
    public BeanCopier get(final Class<?> srcClass, final Class<?> targetClass, final boolean useConverter) {
        if (srcClass == null || targetClass == null) {
            throw new NullPointerException("Source class and target class must not be null");
        }
        return cache.get(srcClass).computeIfAbsent(new CopierKey(targetClass, useConverter),
                key -> BeanCopier.create(srcClass, key.targetClass(), key.useConverter()));
    }

    /**
     * 指定源类型下已缓存的 copier 数量，仅用于测试与诊断。
     *
     * @param srcClass 源类型，不可为 null
     * @return 该源类型下已缓存的 copier 数量
     */
    public int size(final Class<?> srcClass) {
        if (srcClass == null) {
            throw new NullPointerException("Source class must not be null");
        }
        return cache.get(srcClass).size();
    }

    private record CopierKey(Class<?> targetClass, boolean useConverter) {
    }
}
