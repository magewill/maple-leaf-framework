package cn.maple.debezium.services;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.redisson.services.GXRedissonCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.context.support.GenericApplicationContext;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXDebeziumServiceTest {
    private static final String LOCK_KEY = "initial-engine-lock:test:default";

    @AfterEach
    void tearDown() {
        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
    }

    @Test
    void tryInitialEngineLockUsesTtlAndOwnerToken() {
        GXDebeziumService service = testService();
        RMapCache<Object, Object> mapCache = registerRedissonBean();
        when(mapCache.fastPutIfAbsent(LOCK_KEY, GXDebeziumService.LOCK_VALUE,
                GXDebeziumService.LOCK_TTL_MINUTES, TimeUnit.MINUTES)).thenReturn(true);

        assertTrue(service.tryInitialEngineLock(LOCK_KEY));
        verify(mapCache).fastPutIfAbsent(LOCK_KEY, GXDebeziumService.LOCK_VALUE,
                GXDebeziumService.LOCK_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Test
    void renewInitialEngineLockOnlyRenewsOwnedLock() {
        GXDebeziumService service = testService();
        RMapCache<Object, Object> mapCache = registerRedissonBean();
        when(mapCache.get(LOCK_KEY)).thenReturn(GXDebeziumService.LOCK_VALUE);
        when(mapCache.expireEntry(LOCK_KEY, Duration.ofMinutes(GXDebeziumService.LOCK_TTL_MINUTES), Duration.ZERO))
                .thenReturn(true);

        assertTrue(service.renewInitialEngineLock(LOCK_KEY));
        verify(mapCache).expireEntry(LOCK_KEY, Duration.ofMinutes(GXDebeziumService.LOCK_TTL_MINUTES), Duration.ZERO);
    }

    @Test
    void renewInitialEngineLockRejectsForeignOwner() {
        GXDebeziumService service = testService();
        RMapCache<Object, Object> mapCache = registerRedissonBean();
        when(mapCache.get(LOCK_KEY)).thenReturn("other-owner");

        assertFalse(service.renewInitialEngineLock(LOCK_KEY));
        verify(mapCache, never()).expireEntry(LOCK_KEY, Duration.ofMinutes(GXDebeziumService.LOCK_TTL_MINUTES), Duration.ZERO);
    }

    @Test
    void initialEngineUnLockRemovesOnlyOwnedLock() {
        GXDebeziumService service = testService();
        RMapCache<Object, Object> mapCache = registerRedissonBean();

        service.initialEngineUnLock(LOCK_KEY);
        verify(mapCache).remove(LOCK_KEY, GXDebeziumService.LOCK_VALUE);
    }

    @Test
    void lockMethodsFailFastWhenRedissonBeanIsMissing() {
        GXDebeziumService service = testService();

        assertThrows(IllegalStateException.class, () -> service.tryInitialEngineLock(LOCK_KEY));
    }

    private GXDebeziumService testService() {
        return data -> {
        };
    }

    private RMapCache<Object, Object> registerRedissonBean() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RMapCache<Object, Object> mapCache = mock(RMapCache.class);
        when(redissonClient.getMapCache(GXDebeziumService.BUCKET_NAME)).thenReturn(mapCache);

        GXRedissonCacheService redissonCacheService = mock(GXRedissonCacheService.class);
        when(redissonCacheService.getRedissonClient()).thenReturn(redissonClient);

        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(GXRedissonCacheService.class, () -> redissonCacheService);
        context.refresh();
        GXApplicationContextSingleton.INSTANCE.setApplicationContext(context);
        return mapCache;
    }
}
