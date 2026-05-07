package cn.maple.redisson.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RScript;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Common Redisson utility methods.
 */
public final class GXRedissonUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXRedissonUtils.class);
    private static final long DEFAULT_LOCK_WAIT_TIME = 10L;
    private static final long DEFAULT_LOCK_LEASE_TIME = -1L;

    private GXRedissonUtils() {
        throw new UnsupportedOperationException("Utility class must not be instantiated");
    }

    public static void set(String key, Object value, int expire, TimeUnit timeUnit) {
        validateKey(key);
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (expire > 0 && timeUnit == null) {
            throw new IllegalArgumentException("timeUnit must not be null when expire is positive");
        }
        validateExpireMillis(expire, timeUnit);

        try {
            RBucket<Object> bucket = getRedissonClient().getBucket(key);
            if (expire > 0) {
                bucket.set(value, expire, timeUnit);
            } else {
                bucket.set(value);
            }
        } catch (Exception e) {
            LOG.error("Failed to set Redis key [{}]", key, e);
            throw new GXBusinessException("Failed to set Redis key: " + e.getMessage(), e);
        }
    }

    public static void set(String key, Object value) {
        set(key, value, 0, null);
    }

    public static <R> R get(String key, Class<R> clazz) {
        validateKey(key);
        if (clazz == null) {
            throw new IllegalArgumentException("clazz must not be null");
        }
        Object value = getRedissonClient().getBucket(key).get();
        return value == null ? null : Convert.convert(clazz, value);
    }

    public static String get(String key) {
        return get(key, String.class);
    }

    public static boolean exists(String key) {
        validateKey(key);
        try {
            return getRedissonClient().getBucket(key).isExists();
        } catch (Exception e) {
            LOG.error("Failed to check Redis key [{}]", key, e);
            return false;
        }
    }

    public static boolean delete(String key) {
        validateKey(key);
        try {
            return getRedissonClient().getBucket(key).delete();
        } catch (Exception e) {
            LOG.error("Failed to delete Redis key [{}]", key, e);
            return false;
        }
    }

    public static long incrementAndGet(String key, long expire, TimeUnit timeUnit) {
        return changeCounterByScript(key, expire, timeUnit, "incr");
    }

    public static long incrementAndGet(String key) {
        return incrementAndGet(key, 0, null);
    }

    public static long decrementAndGet(String key, long expire, TimeUnit timeUnit) {
        return changeCounterByScript(key, expire, timeUnit, "decr");
    }

    public static long decrementAndGet(String key) {
        return decrementAndGet(key, 0, null);
    }

    public static long getCounterValue(String key) {
        validateKey(key);
        try {
            return getRedissonClient().getAtomicLong(key).get();
        } catch (Exception e) {
            LOG.error("Failed to get counter [{}]", key, e);
            return 0L;
        }
    }

    public static boolean counterExists(String key) {
        validateKey(key);
        try {
            return getRedissonClient().getAtomicLong(key).isExists();
        } catch (Exception e) {
            LOG.error("Failed to check counter [{}]", key, e);
            return false;
        }
    }

    public static void setCounterValue(String key, long value, long expire, TimeUnit timeUnit) {
        validateKey(key);
        if (expire > 0 && timeUnit == null) {
            throw new IllegalArgumentException("timeUnit must not be null when expire is positive");
        }
        validateExpireMillis(expire, timeUnit);
        try {
            RAtomicLong atomicLong = getRedissonClient().getAtomicLong(key);
            atomicLong.set(value);
            if (expire > 0) {
                atomicLong.expire(expire, timeUnit);
            }
        } catch (Exception e) {
            LOG.error("Failed to set counter [{}]", key, e);
            throw new GXBusinessException("Failed to set counter: " + e.getMessage(), e);
        }
    }

    public static boolean resetCounter(String key) {
        validateKey(key);
        try {
            return getRedissonClient().getAtomicLong(key).delete();
        } catch (Exception e) {
            LOG.error("Failed to reset counter [{}]", key, e);
            return false;
        }
    }

    public static RLock getLock(String lockName) {
        validateKey(lockName);
        return getLock("lock", lockName);
    }

    public static RLock getLock(String lockPrefix, String lockName) {
        if (CharSequenceUtil.isBlank(lockPrefix) || CharSequenceUtil.isBlank(lockName)) {
            throw new IllegalArgumentException("lockPrefix and lockName must not be blank");
        }
        return getRedissonClient().getLock(lockPrefix + ":" + lockName);
    }

    public static <T> T executeWithLock(String lockName, Supplier<T> operation) {
        return executeWithLock(lockName, DEFAULT_LOCK_WAIT_TIME, DEFAULT_LOCK_LEASE_TIME, TimeUnit.SECONDS, operation);
    }

    public static <T> T executeWithLock(String lockName, long waitTime, long leaseTime, TimeUnit timeUnit, Supplier<T> operation) {
        validateLockArguments(lockName, timeUnit, operation);
        RLock lock = getLock(lockName);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (!acquired) {
                LOG.warn("Timed out while acquiring lock [{}]", lockName);
                return null;
            }
            return operation.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GXBusinessException("Interrupted while acquiring lock", e);
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new GXBusinessException("Failed to execute operation with lock: " + e.getMessage(), e);
        } finally {
            if (acquired) {
                safeUnlock(lock);
            }
        }
    }

    public static <T> T tryExecuteWithLock(String lockName, Supplier<T> operation) {
        return tryExecuteWithLock(lockName, 0, DEFAULT_LOCK_LEASE_TIME, TimeUnit.SECONDS, operation);
    }

    public static <T> T tryExecuteWithLock(String lockName, long waitTime, long leaseTime, TimeUnit timeUnit, Supplier<T> operation) {
        validateLockArguments(lockName, timeUnit, operation);
        RLock lock = getLock(lockName);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, timeUnit);
            return acquired ? operation.get() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GXBusinessException("Interrupted while acquiring lock", e);
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new GXBusinessException("Failed to execute operation with lock: " + e.getMessage(), e);
        } finally {
            if (acquired) {
                safeUnlock(lock);
            }
        }
    }

    public static boolean throttling(String name, int rate, int rateInterval, TimeUnit timeUnit) {
        validateRateLimiterArguments(name, rate, rateInterval, timeUnit);
        try {
            return getRedissonClient()
                    .getRateLimiter(name)
                    .trySetRate(RateType.OVERALL, rate, toDuration(rateInterval, timeUnit));
        } catch (Exception e) {
            LOG.error("Failed to configure rate limiter [{}]", name, e);
            return false;
        }
    }

    public static boolean tryAcquire(String name) {
        return tryAcquire(name, 1);
    }

    public static boolean tryAcquire(String name, int permits) {
        validatePermits(name, permits);
        try {
            return getRedissonClient().getRateLimiter(name).tryAcquire(permits);
        } catch (Exception e) {
            LOG.error("Failed to acquire rate limiter permit, name={}, permits={}", name, permits, e);
            return false;
        }
    }

    public static void acquire(String name) {
        acquire(name, 1);
    }

    public static void acquire(String name, int permits) {
        validatePermits(name, permits);
        try {
            getRedissonClient().getRateLimiter(name).acquire(permits);
        } catch (Exception e) {
            LOG.error("Failed to acquire rate limiter permit, name={}, permits={}", name, permits, e);
            throw new GXBusinessException("Failed to acquire rate limiter permit: " + e.getMessage(), e);
        }
    }

    public static RedissonClient getRedissonClient() {
        RedissonClient redissonClient = GXSpringContextUtils.getBean("redissonClient", RedissonClient.class);
        if (redissonClient == null) {
            redissonClient = GXSpringContextUtils.getBean(RedissonClient.class);
        }
        if (redissonClient == null) {
            throw new IllegalStateException("Unable to get RedissonClient bean");
        }
        return redissonClient;
    }

    private static long changeCounterByScript(String key, long expire, TimeUnit timeUnit, String command) {
        validateKey(key);
        if (expire > 0 && timeUnit == null) {
            throw new IllegalArgumentException("timeUnit must not be null when expire is positive");
        }
        long expireMillis = validateExpireMillis(expire, timeUnit);
        try {
            if (expire <= 0) {
                RAtomicLong atomicLong = getRedissonClient().getAtomicLong(key);
                return "incr".equals(command) ? atomicLong.incrementAndGet() : atomicLong.decrementAndGet();
            }

            String luaScript = "local current = redis.call('" + command + "', KEYS[1]);"
                    + "redis.call('pexpire', KEYS[1], ARGV[1]);"
                    + "return current";
            RScript script = getRedissonClient().getScript(StringCodec.INSTANCE);
            Long result = script.eval(
                    RScript.Mode.READ_WRITE,
                    luaScript,
                    RScript.ReturnType.LONG,
                    Collections.singletonList(key),
                    expireMillis
            );
            return result == null ? 0L : result;
        } catch (Exception e) {
            LOG.error("Failed to change counter [{}]", key, e);
            throw new GXBusinessException("Failed to change counter: " + e.getMessage(), e);
        }
    }

    private static void validateKey(String key) {
        if (CharSequenceUtil.isBlank(key)) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }

    private static long validateExpireMillis(long expire, TimeUnit timeUnit) {
        if (expire <= 0) {
            return 0L;
        }
        long expireMillis = timeUnit.toMillis(expire);
        if (expireMillis < 1L) {
            throw new IllegalArgumentException("expire must be at least 1 millisecond");
        }
        return expireMillis;
    }

    private static void validateLockArguments(String lockName, TimeUnit timeUnit, Supplier<?> operation) {
        validateKey(lockName);
        if (timeUnit == null) {
            throw new IllegalArgumentException("timeUnit must not be null");
        }
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
    }

    private static void validateRateLimiterArguments(String name, int rate, int rateInterval, TimeUnit timeUnit) {
        validateKey(name);
        if (rate <= 0 || rateInterval <= 0) {
            throw new IllegalArgumentException("rate and rateInterval must be greater than 0");
        }
        if (timeUnit == null) {
            throw new IllegalArgumentException("timeUnit must not be null");
        }
    }

    private static void validatePermits(String name, int permits) {
        validateKey(name);
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be greater than 0");
        }
    }

    private static Duration toDuration(long interval, TimeUnit timeUnit) {
        return switch (timeUnit) {
            case NANOSECONDS -> Duration.ofNanos(interval);
            case MICROSECONDS -> Duration.ofNanos(TimeUnit.MICROSECONDS.toNanos(interval));
            case MILLISECONDS -> Duration.ofMillis(interval);
            case SECONDS -> Duration.ofSeconds(interval);
            case MINUTES -> Duration.ofMinutes(interval);
            case HOURS -> Duration.ofHours(interval);
            case DAYS -> Duration.ofDays(interval);
        };
    }

    private static void safeUnlock(RLock lock) {
        try {
            if (lock != null && lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            LOG.warn("Failed to unlock safely", e);
        }
    }
}
