package cn.maple.core.framework.service;

public interface GXDynamicCallMethodService {
    Object call(String serviceClassName, String methodName, Object... parameters);

    Object call(Object target, String methodName, Object... parameters);
}
