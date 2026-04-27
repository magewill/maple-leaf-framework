package cn.maple.redisson.services.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.NumberUtil;
import cn.maple.redisson.services.GXRedissonCacheService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redisson-backed cache service.
 */
@Service
@Slf4j
public class GXRedissonCacheServiceImpl implements GXRedissonCacheService {
    private static final int DEFAULT_SCAN_COUNT = 1000;
    private static final int MAX_SCAN_COUNT = 1000;
    private static final int MAX_BATCH_SIZE = 2000;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public Object setCache(String bucketName, String key, Object value, int expired, TimeUnit timeUnit) {
        validateBucketAndKey(bucketName, key);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (expired > 0 && timeUnit == null) {
            throw new IllegalArgumentException("timeUnit must not be null when expired is positive");
        }

        RMapCache<Object, Object> mapCache = redissonClient.getMapCache(bucketName);
        return expired > 0 ? mapCache.put(key, value, expired, timeUnit) : mapCache.put(key, value);
    }

    @Override
    public Object setCache(String bucketName, String key, Object value) {
        return setCache(bucketName, key, value, 0, null);
    }

    @Override
    public Object setCache(String bucketName, String key, String value, int expired, TimeUnit timeUnit) {
        return setCache(bucketName, key, (Object) value, expired, timeUnit);
    }

    @Override
    public Object setCache(String bucketName, String key, String value) {
        return setCache(bucketName, key, (Object) value, 0, null);
    }

    @Override
    public Object getCache(String bucketName, String key) {
        validateBucketAndKey(bucketName, key);
        return redissonClient.getMapCache(bucketName).get(key);
    }

    @Override
    public Object deleteCache(String bucketName, String key) {
        validateBucketAndKey(bucketName, key);
        return redissonClient.getMapCache(bucketName).remove(key);
    }

    @Override
    public Long getCacheRemainTimeToLive(String bucketName, String keyName) {
        validateBucketAndKey(bucketName, keyName);
        return redissonClient.getMapCache(bucketName).remainTimeToLive(keyName);
    }

    @Override
    public boolean updateCacheExpiredTime(String bucketName, String keyName, Integer expired, Integer refreshThreshold) {
        validateBucketAndKey(bucketName, keyName);
        if (expired == null || expired <= 0) {
            throw new IllegalArgumentException("expired must be greater than 0");
        }
        if (refreshThreshold == null || refreshThreshold < 0) {
            throw new IllegalArgumentException("refreshThreshold must not be negative");
        }

        RMapCache<Object, Object> mapCache = redissonClient.getMapCache(bucketName);
        Object value = mapCache.get(keyName);
        if (Objects.isNull(value)) {
            log.warn("Cache entry does not exist, bucketName={}, keyName={}", bucketName, keyName);
            return false;
        }

        long ttlMillis = mapCache.remainTimeToLive(keyName);
        long refreshThresholdMillis = TimeUnit.SECONDS.toMillis(refreshThreshold);
        if (ttlMillis == -1L || ttlMillis > refreshThresholdMillis) {
            return true;
        }
        mapCache.fastPut(keyName, value, expired, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public void clear(String bucketName) {
        validateBucket(bucketName);
        redissonClient.getMapCache(bucketName).clear();
    }

    @Override
    public Integer size(String bucketName) {
        validateBucket(bucketName);
        return redissonClient.getMapCache(bucketName).size();
    }

    @Override
    public boolean exists(String bucketName, String key) {
        validateBucketAndKey(bucketName, key);
        return redissonClient.getMapCache(bucketName).containsKey(key);
    }

    @Override
    public RedissonClient getRedissonClient() {
        return redissonClient;
    }

    @Override
    public Map<Object, Object> getBucketAllData(String bucketName) {
        return getBucketAllData(bucketName, DEFAULT_SCAN_COUNT);
    }

    @Override
    public Map<Object, Object> getBucketAllData(String bucketName, int count) {
        return getBucketAllData(bucketName, count, null);
    }

    @Override
    public Map<Object, Object> getBucketAllData(String bucketName, int count, String pattern) {
        validateBucket(bucketName);
        int actualCount = NumberUtil.min(validateCount(count), MAX_SCAN_COUNT);
        RMapCache<Object, Object> mapCache = redissonClient.getMapCache(bucketName);
        Set<Object> keys = CharSequenceUtil.isBlank(pattern)
                ? mapCache.keySet(actualCount)
                : mapCache.keySet(pattern, actualCount);
        if (CollUtil.isEmpty(keys)) {
            return Collections.emptyMap();
        }
        return mapCache.getAll(keys);
    }

    @Override
    public void setBucketAllData(String bucketName, Map<Object, Object> data) {
        setBucketAllData(bucketName, data, DEFAULT_SCAN_COUNT);
    }

    @Override
    public void setBucketAllData(String bucketName, Map<Object, Object> data, int batchSize) {
        validateBucket(bucketName);
        if (data == null || data.isEmpty()) {
            return;
        }
        validateBatchSize(batchSize);
        redissonClient.getMapCache(bucketName).putAll(data);
    }

    @Override
    public boolean deleteBucketAllData(String bucketName) {
        validateBucket(bucketName);
        return redissonClient.getMapCache(bucketName).delete();
    }

    private static void validateBucketAndKey(String bucketName, String key) {
        validateBucket(bucketName);
        if (CharSequenceUtil.isBlank(key)) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }

    private static void validateBucket(String bucketName) {
        if (CharSequenceUtil.isBlank(bucketName)) {
            throw new IllegalArgumentException("bucketName must not be blank");
        }
        if (bucketName.length() > 64) {
            log.warn("bucketName [{}] is longer than 64 characters", bucketName);
        }
    }

    private static int validateCount(int count) {
        if (count < 1 || count > MAX_SCAN_COUNT) {
            throw new IllegalArgumentException("count must be between 1 and " + MAX_SCAN_COUNT);
        }
        return count;
    }

    private static void validateBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
        }
    }
}
