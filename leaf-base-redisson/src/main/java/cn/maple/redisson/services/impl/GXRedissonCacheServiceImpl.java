package cn.maple.redisson.services.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.NumberUtil;
import cn.maple.redisson.services.GXRedissonCacheService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redisson缓存服务实现类
 * <p>
 * 基于Redisson的RMapCache实现的分布式缓存服务，提供了缓存的设置、获取、删除等功能。
 * 该实现类是线程安全的，可以在多线程环境下使用。
 * 所有操作都通过Redisson客户端进行，确保了分布式环境下的数据一致性。
 * </p>
 *
 * @author maple
 * @since 1.0.0
 */
@Service
@Slf4j
public class GXRedissonCacheServiceImpl implements GXRedissonCacheService {
    @Resource
    private RedissonClient redissonClient;

    /**
     * 设置缓存，带有过期时间
     * <p>
     * 将指定的键值对存储到指定的缓存桶中，并设置过期时间。
     * 该方法会验证bucketName和key的长度，如果超过64个字符会记录错误日志，但仍会执行缓存操作。
     * </p>
     *
     * @param bucketName 数据桶的名字，来源于CacheConstant，不应为null且长度不应超过64
     * @param key        缓存key，不应为null且长度不应超过64
     * @param value      缓存值，可以是任意对象，但应确保可序列化
     * @param expired    过期时间，0表示永不过期
     * @param timeUnit   时间单位，不应为null
     * @return 之前与key关联的值，如果key不存在则返回null
     */
    @Override
    public Object setCache(String bucketName, String key, Object value, int expired, TimeUnit timeUnit) {
        // 参数验证
        if (CharSequenceUtil.isEmpty(bucketName)) {
            log.error("Redis的BucketName不能为空!");
            return null;
        }
        if (CharSequenceUtil.isEmpty(key)) {
            log.error("Redis的key不能为空!");
            return null;
        }
        if (CharSequenceUtil.length(bucketName) > 64) {
            log.error("Redis的BucketName:{}太长,建议长度不超过64,请修改!", bucketName);
        }
        if (CharSequenceUtil.length(key) > 64) {
            log.error("Redis的key:{}太长,建议长度不超过64,请修改!", key);
        }
        if (timeUnit == null) {
            log.error("TimeUnit不能为空，使用默认值SECONDS");
            timeUnit = TimeUnit.SECONDS;
        }
        try {
            return redissonClient.getMapCache(bucketName).put(key, value, expired, timeUnit);
        } catch (Exception e) {
            log.error("设置缓存异常: bucketName={}, key={}, error={}", bucketName, key, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 设置缓存，无过期时间
     * <p>
     * 将指定的键值对存储到指定的缓存桶中，不设置过期时间（永久有效）。
     * 内部调用带过期时间的setCache方法，过期时间设为0表示永不过期。
     * </p>
     *
     * @param bucketName 数据桶的名字，来源于CacheConstant，不应为null
     * @param key        缓存key，不应为null
     * @param value      缓存值，可以是任意对象，但应确保可序列化
     * @return 之前与key关联的值，如果key不存在则返回null
     */
    @Override
    public Object setCache(String bucketName, String key, Object value) {
        return setCache(bucketName, key, value, 0, TimeUnit.MICROSECONDS);
    }

    /**
     * 设置字符串缓存，带有过期时间
     * <p>
     * 将指定的字符串键值对存储到指定的缓存桶中，并设置过期时间。
     * 此方法专门用于字符串类型的值，是对通用setCache方法的类型特化。
     * </p>
     *
     * @param bucketName 数据桶的名字，来源于CacheConstant，不应为null
     * @param key        缓存key，不应为null
     * @param value      缓存字符串值，不应为null
     * @param expired    过期时间，0表示永不过期
     * @param timeUnit   时间单位，不应为null
     * @return 之前与key关联的值，如果key不存在则返回null
     */
    @Override
    public Object setCache(String bucketName, String key, String value, int expired, TimeUnit timeUnit) {
        return redissonClient.getMapCache(bucketName).put(key, value, expired, timeUnit);
    }

    /**
     * 设置字符串缓存，无过期时间
     * <p>
     * 将指定的字符串键值对存储到指定的缓存桶中，不设置过期时间（永久有效）。
     * 内部调用带过期时间的setCache方法，过期时间设为0表示永不过期。
     * </p>
     *
     * @param bucketName 数据桶的名字，来源于CacheConstant，不应为null
     * @param key        缓存key，不应为null
     * @param value      缓存字符串值，不应为null
     * @return 之前与key关联的值，如果key不存在则返回null
     */
    @Override
    public Object setCache(String bucketName, String key, String value) {
        return setCache(bucketName, key, value, 0, TimeUnit.MICROSECONDS);
    }

    /**
     * 获取缓存数据
     * <p>
     * 从指定的缓存桶中获取指定key的缓存值。
     * 如果key不存在或已过期，则返回null。
     * </p>
     *
     * @param bucketName 数据桶的名字，来源于CacheConstant，不应为null
     * @param key        缓存key，不应为null
     * @return 缓存值，如果key不存在则返回null
     */
    @Override
    public Object getCache(String bucketName, String key) {
        return redissonClient.getMapCache(bucketName).get(key);
    }

    /**
     * 删除指定的缓存数据
     * <p>
     * 从指定的缓存桶中删除指定key的缓存数据。
     * 该方法会先检查key是否存在，如果不存在则记录信息日志并返回null。
     * </p>
     *
     * @param bucketName 数据桶的名字，来源于CacheConstant，不应为null
     * @param key        缓存key，不应为null
     * @return 已经删除的数据，如果key不存在则返回null
     */
    @Override
    public Object deleteCache(String bucketName, String key) {
        RMapCache<Object, Object> mapCache = redissonClient.getMapCache(bucketName);
        if (Objects.nonNull(mapCache.get(key))) {
            return mapCache.remove(key);
        }
        log.info("缓存key【{}】不存在", key);
        return null;
    }

    /**
     * 获取缓存剩余有效时长
     * <p>
     * 获取指定缓存桶中指定key的缓存数据剩余有效时长（毫秒）。
     * 如果key不存在或已过期，则返回-2。
     * 如果key存在但没有设置过期时间，则返回-1。
     * </p>
     *
     * @param bucketName 缓存桶名字，不应为null
     * @param keyName    缓存key，不应为null
     * @return 剩余有效时长（毫秒），特殊值：-1（永不过期），-2（不存在或已过期）
     */
    @Override
    public Long getCacheRemainTimeToLive(String bucketName, String keyName) {
        return redissonClient.getMapCache(bucketName).remainTimeToLive(keyName);
    }

    /**
     * 更新缓存有效时长
     * <p>
     * 当缓存的剩余有效时长小于指定阈值时，更新缓存的过期时间。
     * 该方法会先检查key是否存在，如果不存在则记录错误日志并返回false。
     * 如果剩余有效时长大于阈值，则不更新过期时间并返回true。
     * </p>
     *
     * @param bucketName       缓存桶名字，不应为null
     * @param keyName          缓存key，不应为null
     * @param expired          需要设置的新过期时间，单位:秒，不应为null
     * @param refreshThreshold 刷新缓存时间的阈值（毫秒），如果剩余时间小于该值则刷新过期时间，不应为null
     * @return 是否成功：true-更新成功或无需更新，false-缓存不存在或更新失败
     */
    @Override
    public boolean updateCacheExpiredTime(String bucketName, String keyName, Integer expired, Integer refreshThreshold) {
        Object o = redissonClient.getMapCache(bucketName).get(keyName);
        if (Objects.isNull(o)) {
            log.error("{}-{}缓存不存在", bucketName, keyName);
            return false;
        }
        long ttl = redissonClient.getMapCache(bucketName).remainTimeToLive(keyName);
        if (ttl <= refreshThreshold) {
            // <code>true</code> if key is a new key in the hash and value was set.
            // <code>false</code> if key already exists in the hash and the value was updated.
            // fastPut方法在此处会返回false 所以需要取反
            return !redissonClient.getMapCache(bucketName).fastPut(keyName, o, expired, TimeUnit.SECONDS);
        }
        return true;
    }

    /**
     * 清除指定桶中的所有数据
     * <p>
     * 清除指定缓存桶中的所有数据，但不删除桶本身。
     * 该操作是原子的，在分布式环境中是安全的。
     * </p>
     *
     * @param bucketName 缓存桶名字，不应为null
     */
    @Override
    public void clear(String bucketName) {
        redissonClient.getMapCache(bucketName).clear();
    }

    /**
     * 查询桶中有多少的key
     * <p>
     * 获取指定缓存桶中的key数量。
     * 该方法会返回当前有效（未过期）的key数量。
     * </p>
     *
     * @param bucketName 缓存桶名字，不应为null
     * @return key的数量，如果桶不存在则返回0
     */
    @Override
    public Integer size(String bucketName) {
        return redissonClient.getMapCache(bucketName).size();
    }

    /**
     * 检查指定的桶中是否有指定的key
     * <p>
     * 检查指定缓存桶中是否存在指定的key。
     * 该方法通过尝试获取key的值并检查是否为null来判断key是否存在。
     * </p>
     *
     * @param bucketName 桶名字，不应为null
     * @param key        指定的key，不应为null
     * @return 是否存在：true-存在，false-不存在或已过期
     */
    @Override
    public boolean exists(String bucketName, String key) {
        return Objects.nonNull(redissonClient.getMapCache(bucketName).get(key));
    }

    /**
     * 获取指定桶中的所有数据
     * <p>
     * 从指定缓存桶中获取指定数量的数据，可以通过pattern进行过滤。
     * 该方法会验证count参数的有效范围（1-1000），并确保返回的数据量不超过指定的count。
     * 如果桶中没有数据或没有匹配pattern的数据，则返回空Map。
     * </p>
     *
     * @param bucketName 桶名字，不应为null
     * @param count      获取数量，有效范围：1-1000
     * @param pattern    redis key pattern，可以为null，表示不进行pattern过滤
     * @return 桶中的数据，key为缓存key，value为缓存值，如果没有数据则返回空Map
     * @throws IllegalArgumentException 如果count参数不在有效范围内
     */
    @Override
    public Map<Object, Object> getBucketAllData(String bucketName, int count, String pattern) {
        Assert.checkBetween(count, 1, 1000, "count必须在{}到{}之间.", 1, 1000);
        count = NumberUtil.min(count, 1000);
        RMapCache<Object, Object> rMapCache = redissonClient.getMapCache(bucketName);
        Set<Object> keys;
        if (CharSequenceUtil.isNotEmpty(pattern)) {
            keys = rMapCache.keySet(pattern, count);
        } else {
            keys = rMapCache.keySet(count);
        }
        if (CollUtil.isEmpty(keys)) {
            return Collections.emptyMap();
        }
        if (keys.size() > count) {
            List<Object> sub = CollUtil.sub(keys, 0, count);
            keys = CollUtil.newHashSet(sub);
        }
        return rMapCache.getAll(keys);
    }

    /**
     * 批量设置缓存数据
     * <p>
     * 将指定的数据批量存储到指定的缓存桶中，使用默认的批处理大小（1000）。
     * 内部调用带批处理大小的setBucketAllData方法。
     * </p>
     *
     * @param bucketName 存储桶的名字，不应为null
     * @param data       存储的数据，不应为null，但可以为空Map
     */
    public void setBucketAllData(String bucketName, Map<Object, Object> data) {
        setBucketAllData(bucketName, data, 1000);
    }

    /**
     * 批量设置缓存数据
     * <p>
     * 将指定的数据批量存储到指定的缓存桶中，可以指定批处理大小。
     * 该方法会验证batchSize参数的有效范围（1-2000），并确保批处理大小不超过2000。
     * 注意：此方法设置的缓存数据没有过期时间，将永久有效，除非手动删除。
     * </p>
     *
     * @param bucketName 存储桶的名字，不应为null
     * @param data       存储的数据，不应为null，但可以为空Map
     * @param batchSize  批处理大小，有效范围：1-2000
     * @throws IllegalArgumentException 如果batchSize参数不在有效范围内
     */
    @Override
    public void setBucketAllData(String bucketName, Map<Object, Object> data, int batchSize) {
        Assert.checkBetween(batchSize, 1, 2000, "count必须在{}到{}之间.", 1, 2000);
        batchSize = NumberUtil.min(batchSize, 2000);
        redissonClient.getMapCache(bucketName).putAll(data, batchSize);
    }

    /**
     * 删除指定Bucket中的所有数据
     * <p>
     * 删除指定缓存桶及其中的所有数据。
     * 该操作会完全删除缓存桶，而不仅仅是清空其中的数据。
     * 该操作是原子的，在分布式环境中是安全的。
     * </p>
     *
     * @param bucketName 存储桶的名字，不应为null
     * @return 是否成功删除：true-删除成功，false-桶不存在或删除失败
     */
    @Override
    public boolean deleteBucketAllData(String bucketName) {
        return redissonClient.getMapCache(bucketName).delete();
    }

    /**
     * 获取RedissonClient客户端实例
     * <p>
     * 获取当前服务使用的RedissonClient实例，可用于执行本服务未提供的Redisson操作。
     * 注意：直接使用RedissonClient时，应确保了解Redisson的API和使用方式，以避免不当操作。
     * </p>
     *
     * @return RedissonClient实例，不会为null
     */
    @Override
    public RedissonClient getRedissonClient() {
        return redissonClient;
    }

    /**
     * 获取指定桶中的所有数据
     * <p>
     * 从指定缓存桶中获取所有数据，使用默认的获取数量限制（1000）。
     * 内部调用带数量限制的getBucketAllData方法。
     * </p>
     *
     * @param bucketName 桶名字，不应为null
     * @return 桶中的数据，key为缓存key，value为缓存值，如果没有数据则返回空Map
     */
    @Override
    public Map<Object, Object> getBucketAllData(String bucketName) {
        return getBucketAllData(bucketName, 1000);
    }

    /**
     * 获取指定桶中的所有数据
     * <p>
     * 从指定缓存桶中获取指定数量的数据，不使用pattern过滤。
     * 内部调用带pattern的getBucketAllData方法，pattern参数为null。
     * </p>
     *
     * @param bucketName 桶名字，不应为null
     * @param count      获取数量，有效范围：1-1000
     * @return 桶中的数据，key为缓存key，value为缓存值，如果没有数据则返回空Map
     * @throws IllegalArgumentException 如果count参数不在有效范围内
     */
    @Override
    public Map<Object, Object> getBucketAllData(String bucketName, int count) {
        return getBucketAllData(bucketName, count, null);
    }
}
