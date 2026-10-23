package cn.maple.retry.util;

import cn.maple.retry.config.GXRetryOptions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXRetryUtilConcurrencyTest {

    private static final int THREADS = 32;

    private static final int KEYS_PER_THREAD = 10;

    @Test
    void cacheKeyIncludesTimeoutAndJitter() throws Exception {
        GXRetryUtil.retryOperation(context -> "ok", null,
                new GXRetryOptions(2, 1000L, 1.5, 5000L, 0L, 0L, Map.of(Exception.class, true)));
        GXRetryUtil.retryOperation(context -> "ok", null,
                new GXRetryOptions(2, 1000L, 1.5, 5000L, 500L, 0L, Map.of(Exception.class, true)));
        GXRetryUtil.retryOperation(context -> "ok", null,
                new GXRetryOptions(2, 1000L, 1.5, 5000L, 0L, 50L, Map.of(Exception.class, true)));

        assertTrue(hasCacheKeyWith(0L, 0L), "base strategy key must exist");
        assertTrue(hasCacheKeyWith(500L, 0L), "timeout must be part of the cache key");
        assertTrue(hasCacheKeyWith(0L, 50L), "jitter must be part of the cache key");
    }

    @Test
    void cacheKeyIncludesMultiplierMaxIntervalAndExceptions() throws Exception {
        GXRetryUtil.retryOperation(context -> "ok", null,
                new GXRetryOptions(3, 800L, 2.0, 8000L, 0L, 0L, Map.of(Exception.class, true)));
        assertEquals(1, countKeysWith(3, 800L, 2.0, 8000L, 0L, 0L, Exception.class),
                "baseline key must be created once");

        GXRetryUtil.retryOperation(context -> "ok", null,
                new GXRetryOptions(3, 800L, 1.5, 8000L, 0L, 0L, Map.of(Exception.class, true)));
        assertEquals(1, countKeysWith(3, 800L, 1.5, 8000L, 0L, 0L, Exception.class),
                "multiplier must be part of the cache key");

        GXRetryUtil.retryOperation(context -> "ok", null,
                new GXRetryOptions(3, 800L, 1.5, 9000L, 0L, 0L, Map.of(Exception.class, true)));
        assertEquals(1, countKeysWith(3, 800L, 1.5, 9000L, 0L, 0L, Exception.class),
                "maxInterval must be part of the cache key");

        GXRetryUtil.retryOperation(context -> "ok", null,
                new GXRetryOptions(3, 800L, 1.5, 9000L, 0L, 0L, Map.of(IOException.class, true)));
        assertEquals(1, countKeysWith(3, 800L, 1.5, 9000L, 0L, 0L, IOException.class),
                "retryExceptions must be part of the cache key");
    }

    @Test
    void cacheTrimKeepsSizeWithinConfiguredBounds() throws Exception {
        int max = intConstant("MAX_RETRY_TEMPLATE_CACHE_SIZE");
        int target = intConstant("TARGET_RETRY_TEMPLATE_CACHE_SIZE");
        assertEquals(256, max);
        assertEquals(192, target);

        for (int index = 0; index < 300; index++) {
            int initialInterval = 2000 + index;
            GXRetryUtil.retryOperation(context -> "ok", 1, initialInterval);
        }

        int size = cacheSize();
        assertTrue(size >= target, "trim must never go below the target, actual: " + size);
        assertTrue(size <= max, "size must stay within the maximum, actual: " + size);
    }

    @Test
    void concurrentCallsWithSameStrategyCreateSingleCacheEntry() throws Exception {
        GXRetryOptions options = new GXRetryOptions(6, 700L, 1.0, 7000L, 0L, 0L, Map.of(IOException.class, true));
        int concurrency = 16;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < concurrency; index++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return GXRetryUtil.retryOperation(context -> "ok", null, options);
                }));
            }
            start.countDown();
            for (Future<String> future : futures) {
                assertEquals("ok", future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, countKeysWith(6, 700L, 1.0, 7000L, 0L, 0L, IOException.class),
                "concurrent calls with the same strategy must share one template");
    }

    @Test
    void concurrentCallsKeepResultsCorrectAndCacheBounded() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        AtomicInteger failures = new AtomicInteger();
        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int threadIndex = 0; threadIndex < THREADS; threadIndex++) {
                final int owner = threadIndex;
                futures.add(pool.submit(() -> {
                    int completed = 0;
                    // 300 distinct cache keys (initialInterval 100..399), no retry so no sleeping.
                    for (int slot = 0; slot < KEYS_PER_THREAD; slot++) {
                        int initialInterval = 100 + ((owner * KEYS_PER_THREAD + slot) % 300);
                        AtomicInteger calls = new AtomicInteger();
                        String result = GXRetryUtil.retryOperation(context -> {
                            calls.incrementAndGet();
                            return "ok";
                        }, 1, initialInterval);
                        if (!"ok".equals(result) || calls.get() != 1) {
                            failures.incrementAndGet();
                        }
                        completed++;
                    }
                    // One retrying call per thread: 4 attempts, fixed 100ms backoff.
                    AtomicInteger retryCalls = new AtomicInteger();
                    String retryResult = GXRetryUtil.retryOperation(context -> {
                        if (retryCalls.incrementAndGet() < 4) {
                            throw new IllegalStateException("temporary");
                        }
                        return "done";
                    }, 4, 100L, 1.0, 1000L, Map.of(Exception.class, true));
                    if (!"done".equals(retryResult) || retryCalls.get() != 4) {
                        failures.incrementAndGet();
                    }
                    return completed;
                }));
            }

            for (Future<Integer> future : futures) {
                assertEquals(KEYS_PER_THREAD, future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, failures.get(), "every call must observe its own strategy");
        assertTrue(cacheSize() <= 256, "template cache must stay bounded, actual: " + cacheSize());
    }

    private int countKeysWith(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            long timeout,
            long jitter,
            @Nullable Class<? extends Throwable> markerException) throws Exception {
        int count = 0;
        for (Object key : cache().keySet()) {
            if ((int) invoke(key, "maxAttempts") == maxAttempts
                    && (long) invoke(key, "initialInterval") == initialInterval
                    && (double) invoke(key, "multiplier") == multiplier
                    && (long) invoke(key, "maxInterval") == maxInterval
                    && (long) invoke(key, "timeout") == timeout
                    && (long) invoke(key, "jitter") == jitter
                    && (markerException == null || ((Map<?, ?>) invoke(key, "retryExceptions")).containsKey(markerException))) {
                count++;
            }
        }
        return count;
    }

    private boolean hasCacheKeyWith(long timeout, long jitter) throws Exception {
        for (Object key : cache().keySet()) {
            if ((long) invoke(key, "timeout") == timeout && (long) invoke(key, "jitter") == jitter) {
                return true;
            }
        }
        return false;
    }

    private Object invoke(Object target, String accessor) throws Exception {
        Method method = target.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private int intConstant(String fieldName) throws Exception {
        Field field = GXRetryUtil.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (int) field.get(null);
    }

    private int cacheSize() throws Exception {
        return cache().size();
    }

    private Map<?, ?> cache() throws Exception {
        Field field = GXRetryUtil.class.getDeclaredField("RETRY_TEMPLATE_CACHE");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(null);
    }
}
