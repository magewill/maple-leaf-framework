package cn.maple.redisson;

import cn.maple.redisson.services.impl.GXRedissonCacheServiceImpl;
import cn.maple.redisson.util.GXRedissonDelayMQUtils;
import cn.maple.redisson.util.GXRedissonMQUtils;
import cn.maple.redisson.util.GXRedissonUtils;
import org.junit.jupiter.api.Test;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RMapCache;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonUtilityRegressionTest {

    @Test
    void updateCacheExpiredTimeRefreshesTtlWithoutRewritingValue() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RMapCache<Object, Object> mapCache = mock(RMapCache.class);
        GXRedissonCacheServiceImpl service = new GXRedissonCacheServiceImpl();

        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        when(redissonClient.getMapCache("bucket")).thenReturn(mapCache);
        when(mapCache.remainTimeToLive("key")).thenReturn(TimeUnit.SECONDS.toMillis(1));
        when(mapCache.expireEntry("key", Duration.ofSeconds(30), Duration.ZERO)).thenReturn(true);

        assertTrue(service.updateCacheExpiredTime("bucket", "key", 30, 10));
        verify(mapCache).expireEntry("key", Duration.ofSeconds(30), Duration.ZERO);
        verify(mapCache, never()).get("key");
        verify(mapCache, never()).fastPut(eq("key"), any(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void updateCacheExpiredTimeReturnsFalseWhenEntryIsMissing() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RMapCache<Object, Object> mapCache = mock(RMapCache.class);
        GXRedissonCacheServiceImpl service = new GXRedissonCacheServiceImpl();

        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        when(redissonClient.getMapCache("bucket")).thenReturn(mapCache);
        when(mapCache.remainTimeToLive("key")).thenReturn(-2L);

        assertEquals(false, service.updateCacheExpiredTime("bucket", "key", 30, 10));
        verify(mapCache, never()).expireEntry(any(), any(), any());
    }

    @Test
    void updateCacheExpiredTimeKeepsExistingNoExpiryEntry() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RMapCache<Object, Object> mapCache = mock(RMapCache.class);
        GXRedissonCacheServiceImpl service = new GXRedissonCacheServiceImpl();

        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        when(redissonClient.getMapCache("bucket")).thenReturn(mapCache);
        when(mapCache.remainTimeToLive("key")).thenReturn(-1L);

        assertTrue(service.updateCacheExpiredTime("bucket", "key", 30, 10));
        verify(mapCache, never()).expireEntry(any(), any(), any());
    }

    @Test
    void getBucketAllDataUsesCountAsScanBatchSizeWithoutTruncatingResults() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RMapCache<Object, Object> mapCache = mock(RMapCache.class);
        GXRedissonCacheServiceImpl service = new GXRedissonCacheServiceImpl();

        Set<Object> scannedKeys = new LinkedHashSet<>();
        scannedKeys.add("k1");
        scannedKeys.add("k2");
        scannedKeys.add("k3");
        Map<Object, Object> expected = new LinkedHashMap<>();
        expected.put("k1", "v1");
        expected.put("k2", "v2");
        expected.put("k3", "v3");

        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        when(redissonClient.getMapCache("bucket")).thenReturn(mapCache);
        when(mapCache.keySet(2)).thenReturn(scannedKeys);
        when(mapCache.getAll(scannedKeys)).thenReturn(expected);

        Map<Object, Object> actual = service.getBucketAllData("bucket", 2);

        assertEquals(expected, actual);
        verify(mapCache).getAll(scannedKeys);
    }

    @Test
    void clearDelayedQueueCacheDestroysCachedQueues() throws Exception {
        ConcurrentHashMap<String, RDelayedQueue<String>> cache = delayedQueueCache();
        cache.clear();
        RDelayedQueue<String> delayedQueue = mock(RDelayedQueue.class);
        cache.put("queue", delayedQueue);

        GXRedissonDelayMQUtils.clearDelayedQueueCache();

        verify(delayedQueue).destroy();
        assertTrue(cache.isEmpty());
    }

    @Test
    void clearReliableTopicLocalCacheRemovesCachedState() throws Exception {
        ConcurrentHashMap<String, RReliableTopic> topicCache = topicCache();
        ConcurrentHashMap<String, Object> listenerCache = listenerRegistrationCache();
        topicCache.clear();
        listenerCache.clear();
        topicCache.put("topic", mock(RReliableTopic.class));
        listenerCache.put("listener", new Object());

        GXRedissonMQUtils.clearLocalCache();

        assertTrue(topicCache.isEmpty());
        assertTrue(listenerCache.isEmpty());
    }

    @Test
    void counterTtlMustNotRoundDownToZeroMilliseconds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GXRedissonUtils.incrementAndGet("counter", 1, TimeUnit.MICROSECONDS)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GXRedissonUtils.set("key", "value", 1, TimeUnit.MICROSECONDS)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GXRedissonUtils.setCounterValue("counter", 1, 1, TimeUnit.MICROSECONDS)
        );
    }

    @Test
    void cacheEntryTtlMustNotRoundDownToZeroMilliseconds() {
        GXRedissonCacheServiceImpl service = new GXRedissonCacheServiceImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.setCache("bucket", "key", "value", 1, TimeUnit.MICROSECONDS)
        );
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, RDelayedQueue<String>> delayedQueueCache() throws Exception {
        Field field = GXRedissonDelayMQUtils.class.getDeclaredField("DELAYED_QUEUE_CACHE");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, RDelayedQueue<String>>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, RReliableTopic> topicCache() throws Exception {
        Field field = GXRedissonMQUtils.class.getDeclaredField("TOPIC_CACHE");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, RReliableTopic>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> listenerRegistrationCache() throws Exception {
        Field field = GXRedissonMQUtils.class.getDeclaredField("LISTENER_REGISTRATION_CACHE");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, Object>) field.get(null);
    }
}
