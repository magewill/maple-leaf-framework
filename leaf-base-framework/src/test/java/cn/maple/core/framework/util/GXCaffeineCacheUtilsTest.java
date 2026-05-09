package cn.maple.core.framework.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GXCaffeineCacheUtilsTest {

    @Test
    void getCaffeineCacheCreatesSingleInstanceConcurrently() throws Exception {
        String configKey = uniqueKey();
        Set<Cache<String, String>> caches = ConcurrentHashMap.newKeySet();
        int threadCount = 16;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    await(start);
                    caches.add(GXCaffeineCacheUtils.getCaffeineCache(configKey));
                });
            }

            start.countDown();
            executorService.shutdown();

            assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(1, caches.size());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void loadingCacheRejectsDifferentLoaderForSameName() {
        String configKey = uniqueKey();
        CacheLoader<String, String> firstLoader = key -> "first-" + key;
        CacheLoader<String, String> secondLoader = key -> "second-" + key;

        LoadingCache<String, String> cache = GXCaffeineCacheUtils.getCaffeineCache(configKey, firstLoader);

        assertEquals("first-a", cache.get("a"));
        assertSame(cache, GXCaffeineCacheUtils.getCaffeineCache(configKey, firstLoader));
        assertThrows(IllegalStateException.class, () -> GXCaffeineCacheUtils.getCaffeineCache(configKey, secondLoader));
    }

    @Test
    void failedLoadingCacheBuildDoesNotReserveLoader() {
        String configKey = uniqueKey();
        CacheLoader<String, String> failedLoader = key -> "failed-" + key;
        CacheLoader<String, String> retryLoader = key -> "retry-" + key;

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class)) {
            commonUtils.when(() -> GXCommonUtils.getEnvironmentValue(configKey, String.class))
                    .thenReturn("not-a-caffeine-spec");

            assertThrows(IllegalArgumentException.class,
                    () -> GXCaffeineCacheUtils.getCaffeineCache(configKey, failedLoader));
        }

        LoadingCache<String, String> cache = GXCaffeineCacheUtils.getCaffeineCache(configKey, retryLoader);

        assertEquals("retry-a", cache.get("a"));
    }

    @Test
    void clearCacheInvalidatesNamedCaches() {
        String configKey = uniqueKey();
        Cache<String, String> cache = GXCaffeineCacheUtils.getCaffeineCache(configKey);
        cache.put("key", "value");

        GXCaffeineCacheUtils.clearCache(configKey);

        assertNull(cache.getIfPresent("key"));
    }

    @Test
    void removeCacheDropsLoaderBindingAndAllowsRebuild() {
        String configKey = uniqueKey();
        CacheLoader<String, String> firstLoader = key -> "first-" + key;
        CacheLoader<String, String> secondLoader = key -> "second-" + key;
        LoadingCache<String, String> firstCache = GXCaffeineCacheUtils.getCaffeineCache(configKey, firstLoader);

        GXCaffeineCacheUtils.removeCache(configKey);
        LoadingCache<String, String> secondCache = GXCaffeineCacheUtils.getCaffeineCache(configKey, secondLoader);

        assertNotSame(firstCache, secondCache);
        assertEquals("second-a", secondCache.get("a"));
    }

    @Test
    void rejectsBlankCacheConfigKey() {
        assertThrows(IllegalArgumentException.class, () -> GXCaffeineCacheUtils.getCaffeineCache(" "));
    }

    private static String uniqueKey() {
        return "test.caffeine." + UUID.randomUUID();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(e);
        }
    }
}
