package cn.maple.core.framework.service;

public interface GXDynamicCallMethodService {
    /**
     * Invokes a Spring bean method by class name.
     *
     * @return method result, or {@code null} when the class, bean, or method cannot be resolved
     */
    Object call(String serviceClassName, String methodName, Object... parameters);

    /**
     * Invokes a method on the given target object.
     *
     * @return method result, or {@code null} when the target or method cannot be resolved
     */
    Object call(Object target, String methodName, Object... parameters);
}
