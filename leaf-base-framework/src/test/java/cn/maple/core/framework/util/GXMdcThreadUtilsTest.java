package cn.maple.core.framework.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GXMdcThreadUtils 单元测试与性能测试
 * 包含MDC上下文传递的正确性、线程安全性及性能验证
 */
@ExtendWith(MockitoExtension.class)
class GXMdcThreadUtilsTest {
    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_VALUE = "test-trace-id";

    @BeforeEach
    void setUp() {
        MDC.clear();
        MDC.put(TRACE_ID_KEY, TRACE_ID_VALUE);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testWrapRunnableMdcPropagation() throws Exception {
        Runnable runnable = () -> assertEquals(TRACE_ID_VALUE, MDC.get(TRACE_ID_KEY));
        Runnable wrapped = GXMdcThreadUtils.wrap(runnable, MDC.getCopyOfContextMap());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(wrapped);
        future.get();
        executor.shutdown();
    }

    @Test
    void testWrapCallableMdcPropagation() throws Exception {
        Callable<String> callable = () -> MDC.get(TRACE_ID_KEY);
        Callable<String> wrapped = GXMdcThreadUtils.wrap(callable, MDC.getCopyOfContextMap());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(wrapped);
        assertEquals(TRACE_ID_VALUE, future.get());
        executor.shutdown();
    }

    @Test
    void testWrapSupplierMdcPropagation() {
        Supplier<String> supplier = () -> MDC.get(TRACE_ID_KEY);
        Supplier<String> wrapped = GXMdcThreadUtils.wrapSupplier(supplier, MDC.getCopyOfContextMap());
        assertEquals(TRACE_ID_VALUE, wrapped.get());
    }

    @Test
    void testSupplyAsyncMdcPropagation() throws Exception {
        CompletableFuture<String> future = GXMdcThreadUtils.supplyAsync(() -> MDC.get(TRACE_ID_KEY));
        assertEquals(TRACE_ID_VALUE, future.get());
    }

    @Test
    void testSupplyAsyncUsesVirtualThreadByDefault() throws Exception {
        CompletableFuture<Boolean> future = GXMdcThreadUtils.supplyAsync(() ->
                Thread.currentThread().isVirtual() && TRACE_ID_VALUE.equals(MDC.get(TRACE_ID_KEY))
        );

        assertTrue(future.get());
    }

    @Test
    void testCompletableFutureThenApplyExplicitWrapperMdcPropagation() throws Exception {
        CompletableFuture<String> future = GXMdcThreadUtils.supplyAsync(() -> "value")
                .thenApply(GXMdcThreadUtils.contextWrapper(value -> value + ":" + MDC.get(TRACE_ID_KEY)));

        assertEquals("value:" + TRACE_ID_VALUE, future.get());
    }

    @Test
    void testCompletableFutureThenComposeExplicitWrapperMdcPropagation() throws Exception {
        CompletableFuture<String> future = GXMdcThreadUtils.supplyAsync(() -> "value")
                .thenCompose(GXMdcThreadUtils.contextWrapper(value -> CompletableFuture.completedFuture(value + ":" + MDC.get(TRACE_ID_KEY))));

        assertEquals("value:" + TRACE_ID_VALUE, future.get());
    }

    @Test
    void testCompletableFutureWhenCompleteExplicitWrapperMdcPropagation() throws Exception {
        CompletableFuture<String> future = GXMdcThreadUtils.supplyAsync(() -> "value")
                .whenComplete(GXMdcThreadUtils.biConsumerWrapper((value, throwable) -> assertEquals(TRACE_ID_VALUE, MDC.get(TRACE_ID_KEY)), MDC.getCopyOfContextMap()));

        assertEquals("value", future.get());
    }

    @Test
    void testRunAsyncMdcPropagation() throws Exception {
        CompletableFuture<Void> future = GXMdcThreadUtils.runAsync(() -> assertEquals(TRACE_ID_VALUE, MDC.get(TRACE_ID_KEY)));
        future.get();
    }

    @Test
    void testRunAsyncUsesVirtualThreadByDefault() throws Exception {
        CompletableFuture<Void> future = GXMdcThreadUtils.runAsync(() -> assertTrue(Thread.currentThread().isVirtual()));

        future.get();
    }

    @Test
    void testMdcContextCleanup() throws Exception {
        Runnable runnable = () -> {
        };
        Runnable wrapped = GXMdcThreadUtils.wrap(runnable, MDC.getCopyOfContextMap());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(wrapped).get();
        assertNull(executor.submit(() -> MDC.get(TRACE_ID_KEY)).get());
        executor.shutdown();
    }

    @Test
    void testPerformance() throws Exception {
        int taskCount = 10000;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        Runnable wrapped = GXMdcThreadUtils.wrap(() -> {
        }, MDC.getCopyOfContextMap());
        long start = System.nanoTime();
        for (int i = 0; i < taskCount; i++) {
            executor.submit(wrapped);
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("MDC传递性能测试: " + taskCount + "次任务耗时 " + durationMs + " ms");
        assertTrue(durationMs < 2000, "性能测试未通过");
    }
}
