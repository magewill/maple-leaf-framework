package cn.maple.redisson.services;

import cn.maple.core.framework.service.GXBaseCacheService;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;

import java.util.Map;

public interface GXRedissonCacheService extends GXBaseCacheService {
    /**
     * 获取RedissonClient客户端实例
     *
     * @return RedissonClient
     */
    RedissonClient getRedissonClient();

    RLock getLock(String lockName);

    RReadWriteLock getReadWriteLock(String lockName);

    RLock getReadLock(String lockName);

    RLock getWriteLock(String lockName);

    /**
     * 获取指定桶中的所有数据
     *
     * @param bucketName 桶名字
     * @return 桶中所有的数据
     */
    Map<Object, Object> getBucketAllData(String bucketName);

    /**
     * 获取指定桶中的所有数据
     *
     * @param bucketName 桶名字
     * @param count      获取数量
     * @return 桶中所有的数据
     */
    Map<Object, Object> getBucketAllData(String bucketName, int count);

    /**
     * 获取指定桶中的所有数据
     *
     * @param bucketName 桶名字
     * @param count      获取数量
     * @param pattern    redis key pattern
     * @return 桶中所有的数据
     */
    Map<Object, Object> getBucketAllData(String bucketName, int count, String pattern);

    /**
     * 批量设置缓存数据
     *
     * @param bucketName 存储桶的名字
     * @param data       存储的数据
     */
    void setBucketAllData(String bucketName, Map<Object, Object> data);

    /**
     * 批量设置缓存数据
     *
     * @param bucketName 存储桶的名字
     * @param data       存储的数据
     * @param batchSize  允许插入的数量
     */
    void setBucketAllData(String bucketName, Map<Object, Object> data, int batchSize);

    /**
     * 删除指定Bucket中的数据
     *
     * @param bucketName 存储桶的名字
     */
    boolean deleteBucketAllData(String bucketName);
}
