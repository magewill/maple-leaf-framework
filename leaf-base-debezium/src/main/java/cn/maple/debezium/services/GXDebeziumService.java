package cn.maple.debezium.services;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.redisson.services.GXRedissonCacheService;
import org.redisson.api.RMapCache;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Business extension point for Debezium CDC events.
 */
public interface GXDebeziumService {
    /**
     * Redis cache bucket for Debezium engine ownership.
     */
    String BUCKET_NAME = "maple-framework-debezium-engine";

    /**
     * Redis lock name format.
     */
    String LOCK_NAME_FORMAT = "initial-engine-lock:{}:{}";

    long LOCK_TTL_MINUTES = 5L;

    String LOCK_VALUE = UUID.randomUUID().toString();

    /**
     * Handles a Debezium payload. The payload keeps Debezium field names such as
     * {@code op}, {@code before}, {@code after}, {@code source}, and {@code ts_ms}.
     *
     * @param data Debezium payload data
     */
    void processCaptureDataChange(Dict data);

    /**
     * Tries to claim the distributed engine owner slot.
     *
     * @param lockKey lock key
     * @return true if this instance owns the slot
     */
    default boolean tryInitialEngineLock(String lockKey) {
        GXRedissonCacheService redissonCacheService = getRequiredRedissonCacheService();
        RMapCache<Object, Object> mapCache = redissonCacheService.getRedissonClient().getMapCache(BUCKET_NAME);
        return mapCache.fastPutIfAbsent(lockKey, LOCK_VALUE, LOCK_TTL_MINUTES, TimeUnit.MINUTES);
    }

    default void initialEngineLock(String lockKey) {
        tryInitialEngineLock(lockKey);
    }

    /**
     * Renews the distributed engine owner slot while the engine is running.
     *
     * @param lockKey lock key
     * @return true if the slot still exists and was renewed
     */
    default boolean renewInitialEngineLock(String lockKey) {
        GXRedissonCacheService redissonCacheService = getRequiredRedissonCacheService();
        RMapCache<Object, Object> mapCache = redissonCacheService.getRedissonClient().getMapCache(BUCKET_NAME);
        return LOCK_VALUE.equals(mapCache.get(lockKey))
                && mapCache.expireEntry(lockKey, Duration.ofMinutes(LOCK_TTL_MINUTES), Duration.ZERO);
    }

    /**
     * Releases the distributed engine owner slot.
     *
     * @param lockKey lock key
     */
    default void initialEngineUnLock(String lockKey) {
        GXRedissonCacheService redissonCacheService = getRequiredRedissonCacheService();
        RMapCache<Object, Object> mapCache = redissonCacheService.getRedissonClient().getMapCache(BUCKET_NAME);
        mapCache.remove(lockKey, LOCK_VALUE);
    }

    /**
     * Checks whether an engine owner slot exists.
     *
     * @param lockKey lock key
     * @return true if the slot exists
     */
    default boolean isEngineInitialized(String lockKey) {
        GXRedissonCacheService redissonCacheService = getRequiredRedissonCacheService();
        return redissonCacheService.exists(BUCKET_NAME, lockKey);
    }

    private static GXRedissonCacheService getRequiredRedissonCacheService() {
        GXRedissonCacheService redissonCacheService = GXSpringContextUtils.getBean(GXRedissonCacheService.class);
        if (redissonCacheService == null) {
            throw new IllegalStateException("GXRedissonCacheService bean is required for Debezium engine lock");
        }
        return redissonCacheService;
    }
}
