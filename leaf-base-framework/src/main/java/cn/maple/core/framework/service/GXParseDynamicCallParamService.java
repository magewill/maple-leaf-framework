package cn.maple.core.framework.service;

import java.util.Map;

public interface GXParseDynamicCallParamService {
    Object getDynamicCallMethodParamValue(String jsonStr);

    default Map<String, Object> getDynamicCallMethodParamMap(String jsonStr) {
        throw new UnsupportedOperationException("Please provide an implementation");
    }

    default <T> T getDynamicCallMethodParamValue(String jsonStr, Class<T> clazz) {
        throw new UnsupportedOperationException("Please provide an implementation");
    }
}
