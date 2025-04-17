package cn.maple.redisson.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.redisson.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Redisson工具类，提供基于Redisson的Redis操作
 * <p>
 * 该工具类提供了常用的Redis操作，包括：
 * - 数据的存取和删除
 * - 分布式计数器
 * - 分布式锁
 * - API限流
 * </p>
 * <p>
 * 所有方法都是线程安全的，适合在多线程环境下使用
 * </p>
 *
 * @author maple
 */
public class GXRedissonUtils {
    /**
     * Logger对象，用于记录工具类操作日志
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXRedissonUtils.class);

    /**
     * 计数器缓存的名字，所有计数器相关操作都使用此缓存
     */
    private static final String COUNTER_MAP_CACHE_NAME = "counter_map_cache_name";

    /**
     * 私有构造函数，防止实例化
     * 工具类应使用静态方法，不应被实例化
     */
    private GXRedissonUtils() {
        // 防止通过反射调用构造函数
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 设置数据到Redis
     * <p>
     * 使用RMap存储键值对，支持设置过期时间
     * </p>
     *
     * @param key      键名，不能为null或空
     * @param value    数据值，不能为null
     * @param expire   过期时间，大于0时生效
     * @param timeUnit 时间单位，不能为null
     * @return 之前存储在key位置的值，如果没有则返回null
     * @throws IllegalArgumentException 如果key或timeUnit为null
     */
    public static Object set(String key, String value, int expire, TimeUnit timeUnit) {
        if (CharSequenceUtil.isBlank(key) || timeUnit == null) {
            throw new IllegalArgumentException("Key和TimeUnit不能为空");
        }
        final RMap<Object, Object> rMap = getRedissonClient().getMap(key);
        if (expire > 0) {
            rMap.expire(expire, timeUnit);
        }
        return rMap.put(key, value);
    }

    /**
     * 获取Redis中存储的数据
     * <p>
     * 从RMap中获取数据并转换为指定类型
     * </p>
     *
     * @param key   数据key，不能为null或空
     * @param clazz 返回数据的类型，不能为null
     * @return 转换为指定类型的数据，如果不存在则返回null
     * @throws IllegalArgumentException 如果key或clazz为null
     */
    public static <R> R get(String key, Class<R> clazz) {
        if (CharSequenceUtil.isBlank(key) || clazz == null) {
            throw new IllegalArgumentException("Key和Class类型不能为空");
        }
        final RMap<Object, Object> rMap = getRedissonClient().getMap(key);
        return Convert.convert(clazz, rMap.get(key));
    }

    /**
     * 删除Redis中的数据
     * <p>
     * 从RMap中删除指定key的数据
     * </p>
     *
     * @param key 数据key，不能为null或空
     * @return 如果数据被成功删除返回true，否则返回false
     * @throws IllegalArgumentException 如果key为null
     */
    public static boolean delete(String key) {
        if (CharSequenceUtil.isBlank(key)) {
            throw new IllegalArgumentException("Key不能为空");
        }
        final RMap<Object, Object> rMap = getRedissonClient().getMap(key);
        return null != rMap.remove(key);
    }

    /**
     * 获取并递增计数器的值
     * <p>
     * 该方法是线程安全的，使用分布式锁确保计数器的原子性操作
     * 如果计数器不存在，则创建并设置初始值为1，同时设置过期时间
     * 如果计数器已存在，则将其值加1并返回
     * </p>
     *
     * @param key      计数器的key，不能为null或空
     * @param expire   过期时间，大于0时生效
     * @param timeUnit 时间单位，不能为null
     * @return 递增后的计数器值
     * @throws IllegalArgumentException 如果key或timeUnit为null
     */
    public static long getCounter(String key, int expire, TimeUnit timeUnit) {
        if (CharSequenceUtil.isBlank(key) || timeUnit == null) {
            throw new IllegalArgumentException("Key和TimeUnit不能为空");
        }
        final RLock rLock = getLock(key);
        RMapCache<Object, Object> rMapCache = getRedissonClient().getMapCache(COUNTER_MAP_CACHE_NAME);
        try {
            rLock.lock();
            Object oldCount = rMapCache.get(key);
            if (null == oldCount) {
                long counter = 1;
                rMapCache.put(key, counter, expire, timeUnit);
                return counter;
            }
            long counter = (long) oldCount + 1L;
            rMapCache.put(key, counter);
            return counter;
        } finally {
            rLock.unlock();
        }
    }

    /**
     * 获取当前计数器的值，不会修改计数器
     * <p>
     * 该方法仅查询计数器的当前值，不会对计数器进行任何修改
     * 如果计数器不存在，则返回-1
     * </p>
     *
     * @param key 计数器的key，不能为null或空
     * @return 计数器的当前值，如果不存在则返回-1
     * @throws IllegalArgumentException 如果key为null
     */
    public static long getCounter(String key) {
        if (CharSequenceUtil.isBlank(key)) {
            throw new IllegalArgumentException("Key不能为空");
        }
        RMapCache<Object, Object> rMapCache = getRedissonClient().getMapCache(COUNTER_MAP_CACHE_NAME);
        final Object o = rMapCache.get(key);
        if (null == o) {
            return -1;
        }
        return Convert.convert(Long.class, o);
    }

    /**
     * 获取Redis分布式锁
     * <p>
     * 使用默认前缀"lock"创建分布式锁
     * 返回的锁对象需要手动加锁和解锁
     * </p>
     *
     * @param lockName 锁的名字，不能为null或空
     * @return Redisson分布式锁对象
     * @throws IllegalArgumentException 如果lockName为null
     */
    public static RLock getLock(String lockName) {
        if (CharSequenceUtil.isBlank(lockName)) {
            throw new IllegalArgumentException("锁名称不能为空");
        }
        return getLock("lock", lockName);
    }

    /**
     * 获取Redis分布式锁，支持自定义前缀
     * <p>
     * 使用指定前缀创建分布式锁，格式为"前缀:锁名"
     * 返回的锁对象需要手动加锁和解锁
     * </p>
     *
     * @param lockPrefix 锁前缀，不能为null或空
     * @param lockName   锁的名字，不能为null或空
     * @return Redisson分布式锁对象
     * @throws IllegalArgumentException 如果lockPrefix或lockName为null
     */
    public static RLock getLock(String lockPrefix, String lockName) {
        if (CharSequenceUtil.isBlank(lockPrefix) || CharSequenceUtil.isBlank(lockName)) {
            throw new IllegalArgumentException("锁前缀和锁名称不能为空");
        }
        return getRedissonClient().getLock(CharSequenceUtil.format("{}:{}", lockPrefix, lockName));
    }

    /**
     * API请求限流，在单位时间内限制请求次数
     * <p>
     * 使用Redisson的RateLimiter实现分布式限流功能
     * 可以限制在指定时间单位内的最大请求次数
     * </p>
     *
     * @param name             限流器的名字，不能为null或空
     * @param rate             频率，每单位时间内允许的请求数量，必须大于0
     * @param rateInterval     时间间隔值，必须大于0
     * @param rateIntervalUnit 时间间隔单位，不能为null
     * @return 设置成功返回true，否则返回false
     * @throws IllegalArgumentException 如果参数不合法
     */
    public static boolean throttling(String name, int rate, int rateInterval, RateIntervalUnit rateIntervalUnit) {
        if (CharSequenceUtil.isBlank(name) || rate <= 0 || rateInterval <= 0 || rateIntervalUnit == null) {
            throw new IllegalArgumentException("限流参数不合法");
        }
        final RRateLimiter rateLimiter = getRedissonClient().getRateLimiter(name);
        return rateLimiter.trySetRate(RateType.OVERALL, rate, rateInterval, rateIntervalUnit);
    }

    /**
     * 获取RedissonClient对象
     * <p>
     * 首先尝试通过名称获取Bean，如果失败则尝试通过类型获取
     * 该方法用于所有Redis操作的底层支持
     * </p>
     *
     * @return RedissonClient实例，不会为null
     * @throws IllegalStateException 如果无法获取RedissonClient实例
     */
    public static RedissonClient getRedissonClient() {
        RedissonClient redissonClient = GXSpringContextUtils.getBean("redissonClient", RedissonClient.class);
        if (ObjectUtil.isNotNull(redissonClient)) {
            return redissonClient;
        }
        return GXSpringContextUtils.getBean(RedissonClient.class);
    }
}