package cn.maple.core.framework.service;

import java.util.concurrent.TimeUnit;

public interface GXBaseCacheService {
    Object setCache(String bucketName, String key, Object value, int expired, TimeUnit timeUnit);

    Object setCache(String bucketName, String key, Object value);

    Object setCache(String bucketName, String key, String value, int expired, TimeUnit timeUnit);

    Object setCache(String bucketName, String key, String value);

    Object getCache(String bucketName, String key);

    Object deleteCache(String bucketName, String key);

    Long getCacheRemainTimeToLive(String bucketName, String keyName);

    boolean updateCacheExpiredTime(String bucketName, String keyName, Integer expired, Integer refreshThreshold);

    void clear(String bucketName);

    Integer size(String bucketName);

    boolean exists(String bucketName, String key);
}
