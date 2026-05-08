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
