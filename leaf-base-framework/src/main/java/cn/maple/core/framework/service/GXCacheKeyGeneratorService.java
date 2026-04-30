package cn.maple.core.framework.service;

public interface GXCacheKeyGeneratorService {
    default String cacheKey(Object... param) {
        return "";
    }

    default Boolean cacheCondition(Object... param) {
        return Boolean.TRUE;
    }
}
