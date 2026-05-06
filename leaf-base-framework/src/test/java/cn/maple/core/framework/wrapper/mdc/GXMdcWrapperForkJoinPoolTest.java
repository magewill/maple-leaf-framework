package cn.maple.core.framework.wrapper.mdc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GXMdcWrapperForkJoinPool 单元测试与性能测试
 * 验证MDC上下文在ForkJoinPool中的传递、清理及性能
 */
class GXMdcWrapperForkJoinPoolTest {
    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_VALUE = "forkjoin-trace-id";
    private GXMdcWrapperForkJoinPool pool;

    @BeforeEach
    void setUp() {
        MDC.clear();
        MDC.put(TRACE_ID_KEY, TRACE_ID_VALUE);
        pool = new GXMdcWrapperForkJoinPool(4);
    }

    @AfterEach
    void tearDown() {
        pool.shutdown();
        MDC.clear();
    }

    @Test
    void testExecuteMdcPropagation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        pool.execute(() -> {
            assertEquals(TRACE_ID_VALUE, MDC.get(TRACE_ID_KEY));
            latch.countDown();
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void testSubmitMdcPropagation() throws Exception {
        Future<String> future = pool.submit(() -> MDC.get(TRACE_ID_KEY));
        assertEquals(TRACE_ID_VALUE, future.get());
    }

    @Test
    void testInvokeAllMdcPropagation() throws Exception {
        List<Callable<String>> tasks = List.of(
                () -> MDC.get(TRACE_ID_KEY),
                () -> MDC.get(TRACE_ID_KEY)
        );

        List<Future<String>> futures = pool.invokeAll(tasks);

        for (Future<String> future : futures) {
            assertEquals(TRACE_ID_VALUE, future.get());
        }
    }

    @Test
    void testMdcContextCleanup() throws Exception {
        Future<?> future = pool.submit(() -> {
        });
        future.get();
        MDC.clear();
        assertNull(pool.submit(() -> MDC.get(TRACE_ID_KEY)).get());
    }

    @Test
    void testPerformance() throws Exception {
        int taskCount = 10000;
        long start = System.nanoTime();
        for (int i = 0; i < taskCount; i++) {
            pool.execute(() -> {
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("ForkJoinPool MDC传递性能测试: " + taskCount + "次任务耗时 " + durationMs + " ms");
        assertTrue(durationMs < 2000, "性能测试未通过");
    }
}
