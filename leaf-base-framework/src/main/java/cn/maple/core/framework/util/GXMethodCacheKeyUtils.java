package cn.maple.core.framework.util;

import java.util.Arrays;
import java.util.Objects;

public final class GXMethodCacheKeyUtils {
    private GXMethodCacheKeyUtils() {
        throw new UnsupportedOperationException("Utility class must not be instantiated");
    }

    public static MethodCacheKey getMethodCacheKey(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        return new MethodCacheKey(clazz, methodName, paramTypes);
    }

    public record MethodCacheKey(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        private static final Class<?>[] EMPTY_PARAM_TYPES = new Class<?>[0];

        /**
         * 参数类型数组会被当作缓存键的一部分参与 {@code hashCode}，必须由本对象独占，
         * 否则调用方事后修改数组会让条目落到错误的哈希桶里、永远取不回来。
         */
        public MethodCacheKey {
            paramTypes = (paramTypes == null || paramTypes.length == 0)
                    ? EMPTY_PARAM_TYPES : paramTypes.clone();
        }

        @Override
        public Class<?>[] paramTypes() {
            return paramTypes.clone();
        }

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

        @Override
        public int hashCode() {
            return Objects.hash(clazz, methodName, Arrays.hashCode(paramTypes));
        }
    }
}
