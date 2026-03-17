package cn.maple.core.datasource.cache;

import cn.hutool.core.annotation.AnnotationUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.redisson.services.GXRedissonCacheService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.cache.Cache;
import org.redisson.api.RedissonClient;
import org.springframework.util.DigestUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * MyBatis Plus 使用Redisson作为二级缓存实现
 * <p>
 * 该类实现了MyBatis的Cache接口，使用Redisson作为缓存存储介质，
 * 提供了线程安全的缓存操作，支持缓存过期时间设置，并使用MD5对缓存键进行处理以节省内存。
 * </p>
 *
 * @author 塵渊 britton@126.com
 */
@Slf4j
public class GXMybatisPlusRedissonCache implements Cache {
    /**
     * Redisson 客户端
     * <p>
     * 用于获取分布式锁，确保缓存操作的线程安全性
     * </p>
     */
    private static final RedissonClient redissonClient;

    /**
     * Redisson 缓存服务
     * <p>
     * 提供缓存的基本操作，如获取、设置、删除等
     * </p>
     */
    private static final GXRedissonCacheService redissonCacheService;

    static {
        redissonClient = GXSpringContextUtils.getBean(RedissonClient.class);
        redissonCacheService = GXSpringContextUtils.getBean(GXRedissonCacheService.class);

        // 安全检查：确保必要的服务已正确初始化
        if (redissonClient == null) {
            log.error("初始化MyBatis二级缓存失败：RedissonClient未找到，请检查Redisson配置");
        }
        if (redissonCacheService == null) {
            log.error("初始化MyBatis二级缓存失败：GXRedissonCacheService未找到，请检查相关服务配置");
        }
    }

    /**
     * 缓存实例ID
     * <p>
     * 通常是Mapper接口的全限定名，用于在Redis中唯一标识一个缓存空间
     * </p>
     */
    private final String id;

    /**
     * 缓存刷新间隔，单位为毫秒
     * <p>
     * 从Mapper接口上的@CacheNamespace注解中获取，用于设置缓存的过期时间
     * 默认值为0，表示不过期
     * </p>
     * <p>
     * 注意：自定义cache实现类无法使用MyBatis默认的flushInterval机制，
     * 需要在putObject方法中手动处理过期时间
     * </p>
     */
    private Integer flushInterval = 0;

    /**
     * 构造函数
     * <p>
     * 初始化缓存实例，设置缓存ID和刷新间隔
     * </p>
     *
     * @param id 缓存实例ID，通常是Mapper接口的全限定名
     * @throws IllegalArgumentException 如果ID为null
     * @throws GXBusinessException      如果找不到对应的类或获取注解失败
     */
    public GXMybatisPlusRedissonCache(final String id) {
        if (id == null) {
            throw new IllegalArgumentException("Cache instances require an ID");
        }
        log.info("初始化MyBatis二级缓存，缓存ID: {}", id);
        try {
            // 获取Mapper接口上的@CacheNamespace注解中的flushInterval值
            CacheNamespace cacheNamespace = AnnotationUtil.getAnnotation(Class.forName(id), CacheNamespace.class);
            if (cacheNamespace != null) {
                flushInterval = Math.toIntExact(cacheNamespace.flushInterval());
            }
            this.id = id;
        } catch (ClassNotFoundException e) {
            log.error("初始化MyBatis二级缓存失败：找不到类 {}", id, e);
            throw new GXBusinessException("初始化缓存失败：" + e.getMessage(), e);
        }
    }

    /**
     * 获取缓存实例ID
     *
     * @return 缓存实例ID
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * 将查询结果存入Redis缓存
     * <p>
     * 根据flushInterval设置缓存的过期时间
     * 使用MD5处理缓存键以节省内存空间
     * </p>
     *
     * @param key   缓存键，通常是SQL语句
     * @param value 缓存值，通常是查询结果
     * @throws AssertionError 如果redissonCacheService为null
     */
    @Override
    public void putObject(@NotNull Object key, @NotNull Object value) {
        if (redissonCacheService == null) {
            log.warn("缓存服务不可用，无法存储缓存数据");
            return;
        }

        if (Objects.nonNull(value)) {
            String storeKey = generateCacheKey(key);
            log.debug("MyBatis缓存：向[{}]缓存桶中存入缓存，key: {}", id, storeKey);

            try {
                if (flushInterval > 0) {
                    redissonCacheService.setCache(getId(), storeKey, value, flushInterval, TimeUnit.MILLISECONDS);
                } else {
                    redissonCacheService.setCache(getId(), storeKey, value);
                }
            } catch (Exception e) {
                log.error("MyBatis缓存：存储缓存数据失败", e);
                // 不抛出异常，避免影响正常业务流程
            }
        }
    }

    /**
     * 从Redis缓存中获取查询结果
     * <p>
     * 使用MD5处理缓存键以保持一致性
     * </p>
     *
     * @param key 缓存键，通常是SQL语句
     * @return 缓存值，如果不存在则返回null
     * @throws AssertionError 如果redissonCacheService为null
     */
    @Override
    public Object getObject(@NotNull Object key) {
        if (redissonCacheService == null) {
            log.warn("缓存服务不可用，无法获取缓存数据");
            return null;
        }

        String storeKey = generateCacheKey(key);
        log.debug("MyBatis缓存：从[{}]缓存桶中获取缓存，key: {}", id, storeKey);

        try {
            Object cache = redissonCacheService.getCache(getId(), storeKey);
            if (Objects.isNull(cache)) {
                log.debug("MyBatis缓存：缓存桶[{}]中key: {}不存在", id, storeKey);
            }
            return cache;
        } catch (Exception e) {
            log.error("MyBatis缓存：获取缓存数据失败", e);
            return null; // 发生异常时返回null，让MyBatis重新查询数据库
        }
    }

    /**
     * 从Redis缓存中删除指定键的缓存
     * <p>
     * 使用MD5处理缓存键以保持一致性
     * </p>
     *
     * @param key 要删除的缓存键
     * @return 被删除的缓存值，如果不存在则返回null
     * @throws AssertionError 如果redissonCacheService为null
     */
    @Override
    public Object removeObject(@NotNull Object key) {
        if (redissonCacheService == null) {
            log.warn("缓存服务不可用，无法删除缓存数据");
            return null;
        }

        String storeKey = generateCacheKey(key);
        log.debug("MyBatis缓存：从[{}]缓存桶中删除缓存，key: {}", id, storeKey);

        try {
            return redissonCacheService.deleteCache(getId(), storeKey);
        } catch (Exception e) {
            log.error("MyBatis缓存：删除缓存数据失败", e);
            return null;
        }
    }

    /**
     * 清空当前缓存实例的所有缓存
     * <p>
     * 删除指定缓存ID下的所有缓存数据
     * </p>
     *
     * @throws AssertionError 如果redissonCacheService为null
     */
    @Override
    public void clear() {
        if (redissonCacheService == null) {
            log.warn("缓存服务不可用，无法清空缓存数据");
            return;
        }

        log.debug("MyBatis缓存：清空[{}]缓存桶中的所有数据", id);

        try {
            redissonCacheService.clear(getId());
        } catch (Exception e) {
            log.error("MyBatis缓存：清空缓存数据失败", e);
            // 不抛出异常，避免影响正常业务流程
        }
    }

    /**
     * 获取当前缓存实例中的缓存数量
     * <p>
     * 注意：此方法在MyBatis中并不常用，主要用于统计目的
     * </p>
     *
     * @return 缓存数量
     * @throws AssertionError 如果redissonCacheService为null
     */
    @Override
    public int getSize() {
        if (redissonCacheService == null) {
            log.warn("缓存服务不可用，无法获取缓存数量");
            return 0;
        }

        log.debug("MyBatis缓存：获取[{}]缓存桶中数据的数量", id);

        try {
            return redissonCacheService.size(getId());
        } catch (Exception e) {
            log.error("MyBatis缓存：获取缓存数量失败", e);
            return 0;
        }
    }

    /**
     * 获取读写锁
     * <p>
     * 使用Redisson的分布式读写锁，确保在分布式环境下的缓存操作线程安全
     * 锁的名称格式为：MP:LOCK:{缓存ID}
     * </p>
     *
     * @return Redisson分布式读写锁
     * @throws AssertionError 如果redissonClient为null
     */
    @Override
    public ReadWriteLock getReadWriteLock() {
        if (redissonClient == null) {
            log.warn("Redisson客户端不可用，无法获取分布式锁");
            // 返回一个空的读写锁实现，避免NPE
            return new ReadWriteLock() {
                @Override
                public java.util.concurrent.locks.Lock readLock() {
                    return new java.util.concurrent.locks.ReentrantLock();
                }

                @Override
                public java.util.concurrent.locks.Lock writeLock() {
                    return new java.util.concurrent.locks.ReentrantLock();
                }
            };
        }

        return redissonClient.getReadWriteLock("MP:LOCK:" + getId());
    }

    /**
     * 生成缓存键
     * <p>
     * 对原始键进行MD5处理，减少内存占用并提高查找效率
     * 添加前缀标识，避免与其他系统的缓存键冲突
     * </p>
     *
     * @param key 原始缓存键
     * @return 处理后的缓存键
     */
    private String generateCacheKey(Object key) {
        if (key == null) {
            return "null";
        }

        String keyString = key.toString();
        // 对较长的键进行MD5处理，减少内存占用
        if (keyString.length() > 64) {
            return DigestUtils.md5DigestAsHex(keyString.getBytes());
        }

        // 对于较短的键，直接使用，提高可读性
        return keyString;
    }
}
