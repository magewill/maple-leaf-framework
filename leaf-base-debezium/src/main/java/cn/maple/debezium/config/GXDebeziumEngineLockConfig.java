package cn.maple.debezium.config;

import cn.maple.redisson.services.GXRedissonCacheService;
import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Owns the Debezium engine lease in Redis.
 */
@Component
public class GXDebeziumEngineLockConfig {
    public static final String BUCKET_NAME = "maple-framework-debezium-engine";
    public static final long LOCK_TTL_MINUTES = 5L;

    static final String LOCK_RENEW_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """;

    static final String LOCK_RELEASE_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;

    private final String ownerToken = UUID.randomUUID().toString();

    @Resource
    private GXRedissonCacheService redissonCacheService;

    public boolean tryLock(String lockKey) {
        RBucket<String> lockBucket = getRedissonClient().getBucket(toRedisLockKey(lockKey), StringCodec.INSTANCE);
        return lockBucket.trySet(ownerToken, LOCK_TTL_MINUTES, TimeUnit.MINUTES);
    }

    public void lock(String lockKey) {
        if (!tryLock(lockKey)) {
            throw new IllegalStateException("Debezium engine lock is already owned by another instance: " + lockKey);
        }
    }

    public boolean renew(String lockKey) {
        Number renewed = getRedissonClient().getScript(StringCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE,
                        LOCK_RENEW_SCRIPT,
                        RScript.ReturnType.LONG,
                        Collections.singletonList(toRedisLockKey(lockKey)),
                        ownerToken,
                        String.valueOf(TimeUnit.MINUTES.toMillis(LOCK_TTL_MINUTES)));
        return renewed != null && renewed.longValue() == 1L;
    }

    public void unlock(String lockKey) {
        getRedissonClient().getScript(StringCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE,
                        LOCK_RELEASE_SCRIPT,
                        RScript.ReturnType.LONG,
                        Collections.singletonList(toRedisLockKey(lockKey)),
                        ownerToken);
    }

    public boolean isLocked(String lockKey) {
        return getRedissonClient().getBucket(toRedisLockKey(lockKey), StringCodec.INSTANCE).isExists();
    }

    public String getOwnerToken() {
        return ownerToken;
    }

    static String toRedisLockKey(String lockKey) {
        return BUCKET_NAME + ":" + lockKey;
    }

    private RedissonClient getRedissonClient() {
        if (redissonCacheService == null) {
            throw new IllegalStateException("GXRedissonCacheService bean is required for Debezium engine lock");
        }
        return redissonCacheService.getRedissonClient();
    }
}
