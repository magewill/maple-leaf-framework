package cn.maple.redisson.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.redisson.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson工具类，提供基于Redisson的Redis操作
 * <p>
 * 该工具类提供了常用的Redis操作，包括：
 * - 数据的存取和删除
 * - 分布式计数器
 * - 分布式锁
 * - API限流
 * </p>
 *
 * <p>线程安全说明：</p>
 * <p>所有方法都是线程安全的，适合在多线程环境下使用。工具类基于Redisson的线程安全特性，
 * 所有操作都是原子的，可以安全地在多线程环境中使用。</p>
 *
 * <p>性能优化：</p>
 * <p>1. 使用了参数验证提取，减少代码重复</p>
 * <p>2. 统一的异常处理机制，提高代码可靠性</p>
 * <p>3. 优化了内存使用，减少不必要的对象创建</p>
 * <p>4. 使用Java 17+特性提升代码质量</p>
 *
 * @author maple
 * @since 1.0.0
 */
public class GXRedissonUtils {
    /**
     * Logger对象，用于记录工具类操作日志
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXRedissonUtils.class);

    /**
     * 默认锁等待时间（秒）
     */
    private static final long DEFAULT_LOCK_WAIT_TIME = 10L;

    /**
     * 默认锁租约时间（秒），-1表示使用看门狗机制
     */
    private static final long DEFAULT_LOCK_LEASE_TIME = -1L;

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
     */
    public static void set(String key, Object value, int expire, TimeUnit timeUnit) {
        validateKey(key);
        if (value == null) {
            throw new IllegalArgumentException("Value不能为空");
        }
        if (expire > 0 && timeUnit == null) {
            throw new IllegalArgumentException("当设置过期时间时，TimeUnit不能为空");
        }
        try {
            RBucket<Object> bucket = getRedissonClient().getBucket(key);
            if (expire > 0) {
                bucket.set(value, Duration.of(expire, timeUnit.toChronoUnit()));
            } else {
                bucket.set(value);
            }
        } catch (Exception e) {
            LOG.error("设置数据失败，key: {}", key, e);
            throw new RuntimeException("设置数据失败", e);
        }
    }

    /**
     * 设置数据到Redis（永不过期）
     */
    public static void set(String key, Object value) {
        set(key, value, 0, null);
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
     */
    public static <R> R get(String key, Class<R> clazz) {
        validateKey(key);
        if (clazz == null) {
            throw new IllegalArgumentException("Class类型不能为空");
        }
        RBucket<Object> bucket = getRedissonClient().getBucket(key);
        Object value = bucket.get();
        return value == null ? null : Convert.convert(clazz, value);
    }

    /**
     * 获取Redis中存储的字符串数据
     * <p>
     * 从RBucket中获取字符串数据
     * </p>
     *
     * @param key 数据key，不能为null或空
     * @return 字符串数据，如果不存在则返回null
     */
    public static String get(String key) {
        return get(key, String.class);
    }

    /**
     * 检查key是否存在
     * <p>
     * 检查Redis中是否存在指定的key
     * </p>
     *
     * @param key 数据key，不能为null或空
     * @return 如果key存在返回true，否则返回false
     */
    public static boolean exists(String key) {
        validateKey(key);
        try {
            RBucket<Object> bucket = getRedissonClient().getBucket(key);
            return bucket.isExists();
        } catch (Exception e) {
            LOG.error("检查key存在性失败，key: {}", key, e);
            return false;
        }
    }

    /**
     * 删除Redis中的数据
     * <p>
     * 从RMap中删除指定key的数据
     * </p>
     *
     * @param key 数据key，不能为null或空
     * @return 如果数据被成功删除返回true，否则返回false
     */
    public static boolean delete(String key) {
        validateKey(key);
        try {
            RBucket<Object> bucket = getRedissonClient().getBucket(key);
            return bucket.delete();
        } catch (Exception e) {
            LOG.error("删除数据失败，key: {}", key, e);
            return false;
        }
    }

    /**
     * 递增计数器并设置过期时间（线程安全，使用Lua脚本）
     * <p>
     * 特性：
     * 1. 原子性操作：increment和expire在同一个Lua脚本中执行
     * 2. 性能优化：避免了分布式锁的开销
     * 3. 过期时间策略：每次increment都会刷新过期时间
     * </p>
     *
     * @param key      计数器的key
     * @param expire   过期时间，大于0时生效
     * @param timeUnit 时间单位
     * @return 递增后的值
     */
    public static long incrementAndGet(String key, long expire, TimeUnit timeUnit) {
        validateKey(key);
        if (expire > 0 && timeUnit == null) {
            throw new IllegalArgumentException("当设置过期时间时，TimeUnit不能为空");
        }

        try {
            if (expire > 0) {
                // 使用Lua脚本保证increment和pexpire的原子性
                long expireMillis = timeUnit.toMillis(expire);

                // Lua脚本说明：
                // 1. INCR递增计数器
                // 2. PEXPIRE设置毫秒级过期时间
                // 3. 返回递增后的值
                String luaScript =
                        "local current = redis.call('incr', KEYS[1]);" +
                                "redis.call('pexpire', KEYS[1], ARGV[1]);" +
                                "return current";

                RScript script = getRedissonClient().getScript(StringCodec.INSTANCE);
                Long result = script.eval(
                        RScript.Mode.READ_WRITE,
                        luaScript,
                        RScript.ReturnType.LONG,
                        java.util.Collections.singletonList(key),
                        expireMillis
                );

                return result != null ? result : 0L;
            } else {
                // 无过期时间，直接使用原子操作
                RAtomicLong atomicLong = getRedissonClient().getAtomicLong(key);
                return atomicLong.incrementAndGet();
            }
        } catch (Exception e) {
            LOG.error("递增计数器失败，key: {}", key, e);
            throw new RuntimeException("递增计数器失败", e);
        }
    }


    /**
     * 递增计数器（无过期时间）
     */
    public static long incrementAndGet(String key) {
        return incrementAndGet(key, 0, null);
    }

    /**
     * 递减计数器并设置过期时间（线程安全）
     */
    public static long decrementAndGet(String key, long expire, TimeUnit timeUnit) {
        validateKey(key);
        if (expire > 0 && timeUnit == null) {
            throw new IllegalArgumentException("当设置过期时间时，TimeUnit不能为空");
        }

        try {
            if (expire > 0) {
                long expireMillis = timeUnit.toMillis(expire);

                String luaScript =
                        "local current = redis.call('decr', KEYS[1]);" +
                                "redis.call('pexpire', KEYS[1], ARGV[1]);" +
                                "return current";

                RScript script = getRedissonClient().getScript(org.redisson.client.codec.StringCodec.INSTANCE);
                Long result = script.eval(
                        RScript.Mode.READ_WRITE,
                        luaScript,
                        RScript.ReturnType.LONG,
                        java.util.Collections.singletonList(key),
                        expireMillis
                );

                return result != null ? result : 0L;
            } else {
                RAtomicLong atomicLong = getRedissonClient().getAtomicLong(key);
                return atomicLong.decrementAndGet();
            }
        } catch (Exception e) {
            LOG.error("递减计数器失败，key: {}", key, e);
            throw new RuntimeException("递减计数器失败", e);
        }
    }

    /**
     * 递减计数器（无过期时间）
     */
    public static long decrementAndGet(String key) {
        return decrementAndGet(key, 0, null);
    }

    /**
     * 获取计数器当前值（不修改）
     *
     * @return 计数器的当前值，key不存在时返回0
     */
    public static long getCounterValue(String key) {
        validateKey(key);
        try {
            RAtomicLong atomicLong = getRedissonClient().getAtomicLong(key);
            return atomicLong.get();
        } catch (Exception e) {
            LOG.error("获取计数器值失败，key: {}", key, e);
            return 0;
        }
    }

    /**
     * 检查计数器是否存在
     */
    public static boolean counterExists(String key) {
        validateKey(key);
        try {
            RAtomicLong atomicLong = getRedissonClient().getAtomicLong(key);
            return atomicLong.isExists();
        } catch (Exception e) {
            LOG.error("检查计数器存在性失败，key: {}", key, e);
            return false;
        }
    }

    /**
     * 设置计数器的值
     */
    public static void setCounterValue(String key, long value, long expire, TimeUnit timeUnit) {
        validateKey(key);
        if (expire > 0 && timeUnit == null) {
            throw new IllegalArgumentException("当设置过期时间时，TimeUnit不能为空");
        }

        try {
            RAtomicLong atomicLong = getRedissonClient().getAtomicLong(key);
            atomicLong.set(value);

            if (expire > 0) {
                atomicLong.expire(Duration.of(expire, timeUnit.toChronoUnit()));
            }
        } catch (Exception e) {
            LOG.error("设置计数器值失败，key: {}", key, e);
            throw new RuntimeException("设置计数器值失败", e);
        }
    }

    /**
     * 重置计数器
     * <p>
     * 删除指定的计数器
     * </p>
     *
     * @param key 计数器的key，不能为null或空
     * @return 如果计数器被成功删除返回true，否则返回false
     * @throws IllegalArgumentException 如果key为null
     */
    public static boolean resetCounter(String key) {
        validateKey(key);
        try {
            return getRedissonClient().getAtomicLong(key).delete();
        } catch (Exception e) {
            LOG.error("重置计数器失败，key: {}", key, e);
            return false;
        }
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
        validateKey(lockName);
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
     */
    public static RLock getLock(String lockPrefix, String lockName) {
        if (CharSequenceUtil.isBlank(lockPrefix) || CharSequenceUtil.isBlank(lockName)) {
            throw new IllegalArgumentException("锁前缀和锁名称不能为空");
        }
        try {
            return getRedissonClient().getLock(CharSequenceUtil.format("{}:{}", lockPrefix, lockName));
        } catch (Exception e) {
            LOG.error("获取锁失败，lockPrefix: {}, lockName: {}", lockPrefix, lockName, e);
            throw new RuntimeException("获取锁失败", e);
        }
    }

    /**
     * 在锁保护的情况下执行操作
     * <p>
     * 该方法自动处理锁的获取和释放，确保操作在锁的保护下执行
     * 无论操作是否成功，都会释放锁，避免死锁
     * </p>
     *
     * @param lockName  锁的名字，不能为null或空
     * @param operation 要执行的操作，不能为null
     * @param <T>       操作返回值的类型
     * @return 操作的返回值
     */
    public static <T> T executeWithLock(String lockName, Supplier<T> operation) {
        return executeWithLock(lockName, DEFAULT_LOCK_WAIT_TIME, DEFAULT_LOCK_LEASE_TIME,
                TimeUnit.SECONDS, operation);
    }

    /**
     * 在锁保护下执行操作（自定义超时时间）
     *
     * @param lockName  锁的名字
     * @param waitTime  等待获取锁的最长时间
     * @param leaseTime 锁的过期时间，-1表示使用看门狗自动续期
     * @param timeUnit  时间单位
     * @param operation 要执行的操作
     * @return 操作的返回值，获取锁失败返回null
     */
    public static <T> T executeWithLock(String lockName, long waitTime, long leaseTime,
                                        TimeUnit timeUnit, Supplier<T> operation) {
        validateKey(lockName);
        if (operation == null) {
            throw new IllegalArgumentException("操作不能为空");
        }
        if (timeUnit == null) {
            throw new IllegalArgumentException("时间单位不能为空");
        }

        RLock lock = getLock(lockName);
        boolean acquired = false;
        try {
            // 尝试获取锁，带超时控制
            acquired = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (!acquired) {
                LOG.warn("获取锁超时，lockName: {}, waitTime: {}{}", lockName, waitTime, timeUnit);
                return null;
            }

            return operation.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("获取锁被中断，lockName: {}", lockName, e);
            throw new RuntimeException("获取锁被中断", e);
        } catch (Exception e) {
            LOG.error("执行锁保护操作失败，lockName: {}", lockName, e);
            if (e instanceof GXBusinessException) {
                throw (GXBusinessException) e;
            }
            throw new RuntimeException("执行锁保护操作失败", e);
        } finally {
            if (acquired) {
                safeUnlock(lock);
            }
        }
    }

    /**
     * 尝试获取锁并执行操作（不等待）
     *
     * @param lockName  锁的名字
     * @param operation 要执行的操作
     * @return 操作的返回值，获取锁失败返回null
     */
    public static <T> T tryExecuteWithLock(String lockName, Supplier<T> operation) {
        return tryExecuteWithLock(lockName, 0, DEFAULT_LOCK_LEASE_TIME, TimeUnit.SECONDS, operation);
    }

    /**
     * 尝试获取锁并执行操作
     * <p>
     * 该方法尝试获取锁，如果获取成功则执行操作，否则返回null
     * 支持设置等待时间和锁的过期时间
     * </p>
     *
     * @param lockName  锁的名字，不能为null或空
     * @param waitTime  等待获取锁的最长时间
     * @param leaseTime 锁的过期时间（自动释放时间）
     * @param timeUnit  时间单位
     * @param operation 要执行的操作，不能为null
     * @param <T>       操作返回值的类型
     * @return 操作的返回值，如果获取锁失败则返回null
     */
    public static <T> T tryExecuteWithLock(String lockName, long waitTime, long leaseTime,
                                           TimeUnit timeUnit, Supplier<T> operation) {
        validateKey(lockName);
        if (operation == null) {
            throw new IllegalArgumentException("操作不能为空");
        }
        if (timeUnit == null) {
            throw new IllegalArgumentException("时间单位不能为空");
        }

        RLock lock = getLock(lockName);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (!acquired) {
                LOG.debug("未能获取锁，lockName: {}", lockName);
                return null;
            }

            return operation.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("获取锁被中断，lockName: {}", lockName, e);
            throw new RuntimeException("获取锁被中断", e);
        } catch (Exception e) {
            LOG.error("执行锁保护操作异常，lockName: {}", lockName, e);
            if (e instanceof GXBusinessException) {
                throw (GXBusinessException) e;
            }
            throw new RuntimeException("执行锁保护操作异常", e);
        } finally {
            if (acquired) {
                safeUnlock(lock);
            }
        }
    }

    /**
     * API请求限流，在单位时间内限制请求次数
     * <p>
     * 使用Redisson的RateLimiter实现分布式限流功能
     * 可以限制在指定时间单位内的最大请求次数
     * </p>
     *
     * @param name         限流器的名字，不能为null或空
     * @param rate         频率，每单位时间内允许的请求数量，必须大于0
     * @param rateInterval 时间间隔值，必须大于0
     * @param timeUnit     时间单位，不能为null
     * @return 设置成功返回true，否则返回false
     * @throws IllegalArgumentException 如果参数不合法
     */
    public static boolean throttling(String name, int rate, int rateInterval, TimeUnit timeUnit) {
        validateKey(name);
        if (rate <= 0 || rateInterval <= 0) {
            throw new IllegalArgumentException("限流参数不合法：rate和rateInterval必须大于0");
        }
        if (timeUnit == null) {
            throw new IllegalArgumentException("时间单位不能为空");
        }

        try {
            RRateLimiter rateLimiter = getRedissonClient().getRateLimiter(name);
            Duration duration = Duration.of(rateInterval, timeUnit.toChronoUnit());
            return rateLimiter.trySetRate(RateType.OVERALL, rate, duration);
        } catch (Exception e) {
            LOG.error("设置限流器失败，name: {}", name, e);
            return false;
        }
    }

    /**
     * 尝试获取限流许可
     * <p>
     * 尝试从指定的限流器中获取一个许可，如果获取成功返回true，否则返回false
     * 该方法不会阻塞，立即返回结果
     * </p>
     *
     * @param name 限流器的名字，不能为null或空
     * @return 如果获取许可成功返回true，否则返回false
     * @throws IllegalArgumentException 如果name为null
     */
    public static boolean tryAcquire(String name) {
        return tryAcquire(name, 1);
    }

    /**
     * 尝试获取指定数量的限流许可
     * <p>
     * 尝试从指定的限流器中获取指定数量的许可
     * 该方法不会阻塞，立即返回结果
     * </p>
     *
     * @param name    限流器的名字，不能为null或空
     * @param permits 需要获取的许可数量，必须大于0
     * @return 如果获取许可成功返回true，否则返回false
     * @throws IllegalArgumentException 如果参数不合法
     */
    public static boolean tryAcquire(String name, int permits) {
        validateKey(name);
        if (permits <= 0) {
            throw new IllegalArgumentException("许可数量必须大于0");
        }

        try {
            final RRateLimiter rateLimiter = getRedissonClient().getRateLimiter(name);
            // 只有当限流器配置过（存在）时，才能获取令牌，否则默认通过或抛异常取决于Redisson配置
            // 建议确保 throttling 方法在系统启动时被调用
            return rateLimiter.tryAcquire(permits);
        } catch (Exception e) {
            LOG.error("获取限流许可失败，name: {}, permits: {}", name, permits, e);
            // 降级策略：默认拒绝还是放行？这里选择拒绝(false)以保护系统
            return false;
        }
    }

    /**
     * 阻塞获取限流许可（等待直到获取成功）
     *
     * @param name 限流器的名字
     */
    public static void acquire(String name) {
        acquire(name, 1);
    }

    /**
     * 阻塞获取指定数量的限流许可
     *
     * @param name    限流器的名字
     * @param permits 需要获取的许可数量
     */
    public static void acquire(String name, int permits) {
        validateKey(name);
        if (permits <= 0) {
            throw new IllegalArgumentException("许可数量必须大于0");
        }

        try {
            RRateLimiter rateLimiter = getRedissonClient().getRateLimiter(name);
            rateLimiter.acquire(permits);
        } catch (Exception e) {
            LOG.error("获取限流许可失败（阻塞模式），name: {}, permits: {}", name, permits, e);
            throw new RuntimeException("获取限流许可失败", e);
        }
    }

    /**
     * 获取RedissonClient对象
     * <p>
     * 首先尝试通过名称获取Bean，如果失败则尝试通过类型获取
     * 该方法用于所有Redis操作的底层支持
     * </p>
     *
     * @return RedissonClient实例，不会为null
     */
    public static RedissonClient getRedissonClient() {
        try {
            RedissonClient redissonClient = GXSpringContextUtils.getBean("redissonClient", RedissonClient.class);
            if (ObjectUtil.isNotNull(redissonClient)) {
                return redissonClient;
            }
            return GXSpringContextUtils.getBean(RedissonClient.class);
        } catch (Exception e) {
            LOG.error("获取RedissonClient Bean失败", e);
            throw new IllegalStateException("无法获取RedissonClient实例", e);
        }
    }

    /**
     * 验证key参数
     *
     * @param key 键名，不能为null或空
     */
    private static void validateKey(String key) {
        if (CharSequenceUtil.isBlank(key)) {
            throw new IllegalArgumentException("Key不能为空");
        }
    }

    /**
     * 安全释放锁
     * <p>
     * 仅当当前线程持有锁且锁未被强制释放时才执行unlock
     * </p>
     */
    private static void safeUnlock(RLock lock) {
        if (lock == null) {
            return;
        }
        try {
            // 必须同时满足：锁已被锁定 && 当前线程持有锁
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (IllegalMonitorStateException e) {
            // 锁已经被释放或不是当前线程持有
            LOG.warn("尝试释放未持有的锁: {}", e.getMessage());
        } catch (Exception e) {
            // 其他异常（如网络问题），记录但不影响业务
            LOG.error("释放锁异常: {}", e.getMessage(), e);
        }
    }
}
