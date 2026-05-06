package cn.maple.core.framework.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GXConcurrentToolsUtilsTest {
    @AfterEach
    void tearDown() {
        MDC.clear();
        Thread.interrupted();
    }

    @Test
    void composerFutureCompletesSuccessfullyAndPropagatesMdc() throws Exception {
        ConcurrentMap<String, Object> results = new ConcurrentHashMap<>();
        MDC.put("requestId", "req-1");

        CompletableFuture<Map<String, String>> future = GXConcurrentToolsUtils.composerFuture(
                () -> MDC.getCopyOfContextMap(),
                results,
                "success"
        );

        Map<String, String> taskContext = future.get(2, TimeUnit.SECONDS);
        assertEquals("req-1", taskContext.get("requestId"));
        assertNotNull(taskContext.get(GXTraceIdContextUtils.TRACE_ID_KEY));

        waitForResult(results, "success");
        assertTrue(results.get("success") instanceof Map);
    }

    @Test
    void composerFutureRejectsNullArguments() {
        ConcurrentMap<String, Object> results = new ConcurrentHashMap<>();

        assertThrows(NullPointerException.class, () -> GXConcurrentToolsUtils.composerFuture(null, results, "key"));
        assertThrows(NullPointerException.class, () -> GXConcurrentToolsUtils.composerFuture(() -> "value", null, "key"));
        assertThrows(NullPointerException.class, () -> GXConcurrentToolsUtils.composerFuture(() -> "value", results, null));
    }

    @Test
    void composerFutureStoresSpecialValueWhenTaskReturnsNull() throws Exception {
        ConcurrentMap<String, Object> results = new ConcurrentHashMap<>();

        CompletableFuture<String> future = GXConcurrentToolsUtils.composerFuture(() -> null, results, "nullValue");

        assertNull(future.get(2, TimeUnit.SECONDS));
        waitForResult(results, "nullValue");
        assertEquals(GXConcurrentToolsUtils.FLAG_SPECIAL_VALUE, results.get("nullValue"));
    }

    @Test
    void composerFutureStoresSpecialValueWhenTaskFails() throws Exception {
        ConcurrentMap<String, Object> results = new ConcurrentHashMap<>();

        CompletableFuture<String> future = GXConcurrentToolsUtils.composerFuture(
                () -> {
                    throw new IllegalStateException("boom");
                },
                results,
                "failed"
        );

        assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        waitForResult(results, "failed");
        assertEquals(GXConcurrentToolsUtils.FLAG_SPECIAL_VALUE, results.get("failed"));
    }

    @Test
    void composerFutureCancelInterruptsRunningTask() throws Exception {
        ConcurrentMap<String, Object> results = new ConcurrentHashMap<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);

        CompletableFuture<String> future = GXConcurrentToolsUtils.composerFuture(() -> {
            started.countDown();
            try {
                while (true) {
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                return "interrupted";
            }
        }, results, "cancelled");

        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertTrue(future.cancel(true));
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        assertTrue(future.isCancelled());
        waitForResult(results, "cancelled");
        assertEquals(GXConcurrentToolsUtils.FLAG_SPECIAL_VALUE, results.get("cancelled"));
    }

    @Test
    void allOfCompletesNormally() {
        List<CompletableFuture<?>> futures = List.of(
                CompletableFuture.completedFuture("a"),
                CompletableFuture.completedFuture("b")
        );

        assertDoesNotThrow(() -> GXConcurrentToolsUtils.allOf(futures, 2, TimeUnit.SECONDS));
    }

    @Test
    void allOfDefaultTimeoutOverloadCompletesNormally() {
        assertDoesNotThrow(() -> GXConcurrentToolsUtils.allOf(List.of(CompletableFuture.completedFuture("done"))));
    }

    @Test
    void allOfCancelsPendingTasksOnTimeout() throws Exception {
        CompletableFuture<Void> pending = new CompletableFuture<>();
        CompletableFuture<Void> done = CompletableFuture.completedFuture(null);
        List<CompletableFuture<?>> futures = List.of(done, pending);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> GXConcurrentToolsUtils.allOf(futures, 50, TimeUnit.MILLISECONDS));

        assertTrue(ex.getMessage().contains("timed out"));
        waitForCompletion(pending);
        assertTrue(pending.isCancelled());
    }

    @Test
    void allOfCancelsPendingTasksOnFailure() throws Exception {
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("boom"));
        CompletableFuture<Void> pending = new CompletableFuture<>();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> GXConcurrentToolsUtils.allOf(List.of(failed, pending), 2, TimeUnit.SECONDS));

        assertTrue(ex.getMessage().contains("Task execution failed"));
        waitForCompletion(pending);
        assertTrue(pending.isCancelled());
    }

    @Test
    void allOfRestoresInterruptStatus() throws Exception {
        CompletableFuture<Void> pending = new CompletableFuture<>();
        Thread.currentThread().interrupt();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> GXConcurrentToolsUtils.allOf(List.of(pending), 2, TimeUnit.SECONDS));

        assertTrue(ex.getMessage().contains("Thread interrupted"));
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void allOfRejectsNullOrEmptyInput() {
        assertThrows(NullPointerException.class, () -> GXConcurrentToolsUtils.allOf(null, 1, TimeUnit.SECONDS));
        assertThrows(IllegalArgumentException.class, () -> GXConcurrentToolsUtils.allOf(List.of(), 1, TimeUnit.SECONDS));
        assertThrows(IllegalArgumentException.class, () -> GXConcurrentToolsUtils.allOf(Collections.singletonList(null), 1, TimeUnit.SECONDS));
        assertThrows(IllegalArgumentException.class, () -> GXConcurrentToolsUtils.allOf(List.of(CompletableFuture.completedFuture(null)), -1, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () -> GXConcurrentToolsUtils.allOf(List.of(CompletableFuture.completedFuture(null)), 1, null));
    }

    @Test
    void statisticsAndStatusExposeReadableValues() {
        String statistics = GXConcurrentToolsUtils.getTaskStatistics();
        String status = GXConcurrentToolsUtils.getThreadPoolStatus();

        assertTrue(statistics.startsWith("Task Statistics:"));
        assertTrue(status.startsWith("Thread Pool Status:"));
    }

    @Test
    void completedResultsDoNotLeakTaskLocalMdc() throws Exception {
        ConcurrentMap<String, Object> results = new ConcurrentHashMap<>();
        MDC.put("requestId", "req-cleanup");

        CompletableFuture<String> first = GXConcurrentToolsUtils.composerFuture(() -> {
            MDC.put("taskLocal", "dirty");
            return "ok";
        }, results, "cleanup-1");
        assertEquals("ok", first.get(2, TimeUnit.SECONDS));
        waitForResult(results, "cleanup-1");

        MDC.clear();
        List<CompletableFuture<String>> probes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            probes.add(GXConcurrentToolsUtils.composerFuture(() -> MDC.get("taskLocal"), results, "probe-" + i));
        }

        for (CompletableFuture<String> probe : probes) {
            assertNull(probe.get(2, TimeUnit.SECONDS));
        }
    }

    private static void waitForResult(ConcurrentMap<String, Object> results, String key) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!results.containsKey(key) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(results.containsKey(key), "Timed out waiting for result key: " + key);
    }

    private static void waitForCompletion(CompletableFuture<?> future) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!future.isDone() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(future.isDone(), "Timed out waiting for future completion");
    }
}
