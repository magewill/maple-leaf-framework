package cn.maple.core.framework.service;

import java.util.Map;

public interface GXParseDynamicCallParamService {
    Object getDynamicCallMethodParamValue(String jsonStr);

    default Map<String, Object> getDynamicCallMethodParamMap(String jsonStr) {
        throw new UnsupportedOperationException("请在实现类中提供此方法的具体实现");
    }

    default <T> T getDynamicCallMethodParamValue(String jsonStr, Class<T> clazz) {
        throw new UnsupportedOperationException("请在实现类中提供此方法的具体实现");
    }
}
