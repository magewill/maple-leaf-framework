package cn.maple.core.framework.audit;

import cn.maple.core.framework.util.GXConcurrentToolsUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CURRENT BEHAVIOR - flip when fixed.
 *
 * <p>Characterizes the shared thread pool rejection policy and the static rate-limit
 * registry used by the framework.</p>
 */
class GXConcurrencySafetyAuditTest {
    @Test
    void sharedPoolUsesCallerRunsPolicy() throws Exception {
        ThreadPoolExecutor pool = sharedPool();

        assertInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class, pool.getRejectedExecutionHandler());
        assertTrue(pool.getQueue().remainingCapacity() > 0);
    }

    @Test
    void sharedPoolTasksRunOnFrameworkOwnedThreads() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        ConcurrentHashMap<String, Object> results = new ConcurrentHashMap<>();

        GXConcurrentToolsUtils.composerFuture(() -> {
            threadName.set(Thread.currentThread().getName());
            completed.countDown();
            return "ok";
        }, results, "audit");

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertTrue(threadName.get().startsWith("maple-concurrent-thread-pool-"));
    }

    @Test
    void rateLimiterKeepsOneEntryPerDistinctClientId() throws Exception {
        Map<String, ?> timestamps = rateLimitRegistry();

        for (int index = 0; index < 1000; index++) {
            GXCurrentRequestContextUtils.isRateLimited("audit-client-" + index);
        }

        assertTrue(timestamps.size() >= 1000);
        assertTrue(timestamps.containsKey("audit-client-999"));
    }

    private ThreadPoolExecutor sharedPool() throws Exception {
        Field field = GXConcurrentToolsUtils.class.getDeclaredField("EXECUTOR_SERVICE");
        field.setAccessible(true);
        return (ThreadPoolExecutor) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> rateLimitRegistry() throws Exception {
        Field field = GXCurrentRequestContextUtils.class.getDeclaredField("requestTimestamps");
        field.setAccessible(true);
        return (Map<String, ?>) field.get(null);
    }
}
