package cn.maple.redisson.audit;

import cn.maple.redisson.properties.GXRedissonStreamMQProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Shared helpers for audit tests.
 * <p>
 * The audit tests describe CURRENT BEHAVIOR of the module. When a finding is
 * fixed, the corresponding assertion must be flipped, which turns these tests
 * into regression guards for the fix.
 */
public final class GXRedissonAuditTestSupport {

    public static final String CURRENT_BEHAVIOR = "CURRENT BEHAVIOR";

    private GXRedissonAuditTestSupport() {
        throw new UnsupportedOperationException("Utility class must not be instantiated");
    }

    public static GXRedissonStreamMQProperties streamProperties() {
        GXRedissonStreamMQProperties props = new GXRedissonStreamMQProperties();
        props.setConsumerThreads(1);
        props.setBatchSize(10);
        props.setBlockingTimeoutMillis(50L);
        props.setPendingRecoveryInterval(3_600_000L);
        props.setDelayScanInterval(3_600_000L);
        props.setDelayTransferRecoveryMillis(3_600_000L);
        return props;
    }

    public static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class not found: " + className, e);
        }
    }

    public static Object newRecord(String className, Class<?>[] parameterTypes, Object... args) {
        try {
            Constructor<?> constructor = loadClass(className).getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Unable to create " + className, e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create " + className, e);
        }
    }

    public static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Invocation failed: " + methodName, cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to invoke " + methodName, e);
        }
    }

    public static Object field(Object target, String fieldName) {
        return ReflectionTestUtils.getField(target, fieldName);
    }

    public static void setField(Object target, String fieldName, Object value) {
        ReflectionTestUtils.setField(target, fieldName, value);
    }
}
