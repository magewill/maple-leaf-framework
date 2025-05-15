package cn.maple.core.framework.util;

import java.util.Arrays;
import java.util.Objects;

/**
 * 方法缓存键工具类
 * <p>
 * 该工具类用于生成用于缓存方法的唯一键，主要用于反射调用方法时的缓存优化。
 * 通过组合类、方法名和参数类型，创建一个唯一的缓存键，用于在多线程环境下
 * 安全地缓存和检索方法对象，从而提高反射性能。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 获取方法缓存键
 * Class<?> targetClass = MyService.class;
 * String methodName = "processData";
 * Class<?>[] paramTypes = new Class<?>[] {String.class, Integer.class};
 * MethodCacheKey key = GXMethodCacheKeyUtils.getMethodCacheKey(targetClass, methodName, paramTypes);
 *
 * // 使用缓存键从缓存中获取或存储Method对象
 * Method method = methodCache.computeIfAbsent(key, k -> {
 *     try {
 *         return k.clazz().getDeclaredMethod(k.methodName(), k.paramTypes());
 *     } catch (NoSuchMethodException e) {
 *         throw new RuntimeException(e);
 *     }
 * });
 * </pre>
 *
 * <p>线程安全性：</p>
 * <p>该工具类是线程安全的。MethodCacheKey记录类是不可变的，可以安全地在多线程环境中使用。</p>
 *
 * @author maple-leaf-framework
 * @since 1.0.0
 */
public final class GXMethodCacheKeyUtils {
    /**
     * 私有构造函数，防止实例化
     *
     * @throws UnsupportedOperationException 当尝试实例化此工具类时抛出
     */
    private GXMethodCacheKeyUtils() {
        throw new UnsupportedOperationException("工具类不能被实例化");
    }

    /**
     * 创建方法缓存键
     * <p>
     * 根据提供的类、方法名和参数类型创建一个唯一的方法缓存键，用于在缓存中存储和检索方法对象。
     * 该方法是线程安全的，可以在多线程环境中使用。
     * </p>
     *
     * @param clazz      目标类
     * @param methodName 方法名
     * @param paramTypes 方法参数类型数组
     * @return 方法缓存键对象
     */
    public static MethodCacheKey getMethodCacheKey(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        return new MethodCacheKey(clazz, methodName, paramTypes);
    }

    /**
     * 方法缓存键记录类
     * <p>
     * 使用Java 17+的Record特性，创建一个不可变的方法缓存键类型。
     * 该记录类自动生成构造函数、访问器方法、equals和hashCode方法。
     * 由于是不可变对象，因此在多线程环境中使用是安全的。
     * </p>
     *
     * @param clazz      目标类
     * @param methodName 方法名
     * @param paramTypes 方法参数类型数组
     */
    public record MethodCacheKey(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        /**
         * 重写equals方法，确保缓存键的正确比较
         * <p>
         * 比较两个MethodCacheKey对象是否相等。当且仅当类、方法名和参数类型数组都相等时，
         * 两个MethodCacheKey对象才被认为是相等的。
         * </p>
         *
         * @param o 要比较的对象
         * @return 如果对象相等则返回true，否则返回false
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MethodCacheKey that = (MethodCacheKey) o;
            return Objects.equals(clazz, that.clazz) &&
                    Objects.equals(methodName, that.methodName) &&
                    Arrays.equals(paramTypes, that.paramTypes);
        }

        /**
         * 重写hashCode方法，确保缓存键在哈希表中的正确分布
         * <p>
         * 生成MethodCacheKey对象的哈希码。哈希码基于类、方法名和参数类型数组的哈希值计算。
         * 这确保了在使用HashMap或ConcurrentHashMap等基于哈希的集合时，能够正确地存储和检索方法缓存键。
         * </p>
         *
         * @return 哈希码
         */
        @Override
        public int hashCode() {
            return Objects.hash(clazz, methodName, Arrays.hashCode(paramTypes));
        }
    }
}
