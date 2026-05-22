package cn.maple.debezium.config;

import cn.maple.redisson.services.GXRedissonCacheService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXDebeziumEngineLockConfigTest {
    private static final String LOCK_KEY = "initial-engine-lock:test:default";
    private static final String REDIS_LOCK_KEY = GXDebeziumEngineLockConfig.BUCKET_NAME + ":" + LOCK_KEY;

    @Test
    void tryLockUsesTtlAndOwnerToken() throws Exception {
        GXDebeziumEngineLockConfig engineLockConfig = engineLockConfigWithRedisson();
        RedissonClient redissonClient = redissonClient(engineLockConfig);
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(REDIS_LOCK_KEY, StringCodec.INSTANCE)).thenReturn(bucket);
        when(bucket.trySet(engineLockConfig.getOwnerToken(),
                GXDebeziumEngineLockConfig.LOCK_TTL_MINUTES,
                TimeUnit.MINUTES)).thenReturn(true);

        assertTrue(engineLockConfig.tryLock(LOCK_KEY));
        verify(bucket).trySet(engineLockConfig.getOwnerToken(),
                GXDebeziumEngineLockConfig.LOCK_TTL_MINUTES,
                TimeUnit.MINUTES);
    }

    @Test
    void lockFailsWhenSlotIsOwnedByAnotherInstance() throws Exception {
        GXDebeziumEngineLockConfig engineLockConfig = engineLockConfigWithRedisson();
        RedissonClient redissonClient = redissonClient(engineLockConfig);
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(REDIS_LOCK_KEY, StringCodec.INSTANCE)).thenReturn(bucket);
        when(bucket.trySet(engineLockConfig.getOwnerToken(),
                GXDebeziumEngineLockConfig.LOCK_TTL_MINUTES,
                TimeUnit.MINUTES)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> engineLockConfig.lock(LOCK_KEY));
    }

    @Test
    void renewAtomicallyRenewsOwnedLock() throws Exception {
        GXDebeziumEngineLockConfig engineLockConfig = engineLockConfigWithRedisson();
        RedissonClient redissonClient = redissonClient(engineLockConfig);
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(eq(RScript.Mode.READ_WRITE), eq(GXDebeziumEngineLockConfig.LOCK_RENEW_SCRIPT),
                eq(RScript.ReturnType.LONG), anyList(), any(), any())).thenReturn(1L);

        assertTrue(engineLockConfig.renew(LOCK_KEY));
        verify(script).eval(RScript.Mode.READ_WRITE,
                GXDebeziumEngineLockConfig.LOCK_RENEW_SCRIPT,
                RScript.ReturnType.LONG,
                Collections.singletonList(REDIS_LOCK_KEY),
                engineLockConfig.getOwnerToken(),
                String.valueOf(TimeUnit.MINUTES.toMillis(GXDebeziumEngineLockConfig.LOCK_TTL_MINUTES)));
    }

    @Test
    void renewRejectsForeignOwner() throws Exception {
        GXDebeziumEngineLockConfig engineLockConfig = engineLockConfigWithRedisson();
        RedissonClient redissonClient = redissonClient(engineLockConfig);
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(),
                eq(RScript.ReturnType.LONG), anyList(), any(), any())).thenReturn(0L);

        assertFalse(engineLockConfig.renew(LOCK_KEY));
    }

    @Test
    void unlockRemovesOnlyOwnedLock() throws Exception {
        GXDebeziumEngineLockConfig engineLockConfig = engineLockConfigWithRedisson();
        RedissonClient redissonClient = redissonClient(engineLockConfig);
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);

        engineLockConfig.unlock(LOCK_KEY);
        verify(script).eval(RScript.Mode.READ_WRITE,
                GXDebeziumEngineLockConfig.LOCK_RELEASE_SCRIPT,
                RScript.ReturnType.LONG,
                Collections.singletonList(REDIS_LOCK_KEY),
                engineLockConfig.getOwnerToken());
    }

    @Test
    void ownerTokenIsUniquePerLockServiceInstance() {
        assertNotEquals(new GXDebeziumEngineLockConfig().getOwnerToken(),
                new GXDebeziumEngineLockConfig().getOwnerToken());
    }

    @Test
    void lockMethodsFailFastWhenRedissonBeanIsMissing() {
        GXDebeziumEngineLockConfig engineLockConfig = new GXDebeziumEngineLockConfig();

        assertThrows(IllegalStateException.class, () -> engineLockConfig.tryLock(LOCK_KEY));
    }

    private GXDebeziumEngineLockConfig engineLockConfigWithRedisson() throws Exception {
        GXDebeziumEngineLockConfig engineLockConfig = new GXDebeziumEngineLockConfig();
        RedissonClient redissonClient = mock(RedissonClient.class);
        GXRedissonCacheService redissonCacheService = mock(GXRedissonCacheService.class);
        when(redissonCacheService.getRedissonClient()).thenReturn(redissonClient);

        Field field = GXDebeziumEngineLockConfig.class.getDeclaredField("redissonCacheService");
        field.setAccessible(true);
        field.set(engineLockConfig, redissonCacheService);
        return engineLockConfig;
    }

    private RedissonClient redissonClient(GXDebeziumEngineLockConfig engineLockConfig) throws Exception {
        Field field = GXDebeziumEngineLockConfig.class.getDeclaredField("redissonCacheService");
        field.setAccessible(true);
        GXRedissonCacheService redissonCacheService = (GXRedissonCacheService) field.get(engineLockConfig);
        return redissonCacheService.getRedissonClient();
    }
}
