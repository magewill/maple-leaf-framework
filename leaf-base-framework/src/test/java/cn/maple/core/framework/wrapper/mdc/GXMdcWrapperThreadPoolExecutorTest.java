package cn.maple.core.framework.wrapper.mdc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GXMdcWrapperThreadPoolExecutor 单元测试与性能测试
 * 验证MDC上下文在线程池中的传递、清理及性能
 */
class GXMdcWrapperThreadPoolExecutorTest {
    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_VALUE = "pool-trace-id";
    private GXMdcWrapperThreadPoolExecutor executor;

    @BeforeEach
    void setUp() {
        MDC.clear();
        MDC.put(TRACE_ID_KEY, TRACE_ID_VALUE);
        executor = new GXMdcWrapperThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
        MDC.clear();
    }

    @Test
    void testExecuteMdcPropagation() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> {
            assertEquals(TRACE_ID_VALUE, MDC.get(TRACE_ID_KEY));
            latch.countDown();
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void testSubmitMdcPropagation() throws Exception {
        Future<String> future = executor.submit(() -> MDC.get(TRACE_ID_KEY));
        String result = future.get();
        System.out.println(result);
        assertEquals(TRACE_ID_VALUE, result);
    }

    @Test
    void testMdcContextCleanup() throws Exception {
        Future<?> future = executor.submit(() -> {
        });
        future.get();
        MDC.clear();
        assertNull(executor.submit(() -> MDC.get(TRACE_ID_KEY)).get());
    }

    @Test
    void testPerformance() throws Exception {
        int taskCount = 10000;
        long start = System.nanoTime();
        for (int i = 0; i < taskCount; i++) {
            executor.execute(() -> {
            });
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("线程池MDC传递性能测试: " + taskCount + "次任务耗时 " + durationMs + " ms");
        assertTrue(durationMs < 2000, "性能测试未通过");
    }
}
