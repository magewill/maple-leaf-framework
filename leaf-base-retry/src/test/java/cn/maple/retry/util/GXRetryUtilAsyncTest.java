package cn.maple.retry.util;

import cn.maple.retry.config.GXRetryOptions;
import cn.maple.retry.context.GXCancellationToken;
import cn.maple.retry.exception.GXRetryCancelledException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXRetryUtilAsyncTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void totalTimeoutStopsTheRetryChainEarly() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger recoveryRetryCount = new AtomicInteger(-1);

        try {
            GXRetryOptions options = new GXRetryOptions(5, 100L, 1.0, 1000L, 300L, 0L, Map.of(Exception.class, true));
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(
                    context -> {
                        attempts.incrementAndGet();
                        sleep(200L);
                        throw new IllegalStateException("slow failure");
                    },
                    context -> {
                        recoveryRetryCount.set(context.getRetryCount());
                        return "recovered";
                    },
                    options,
                    executor);

            assertEquals("recovered", future.join());
            assertTrue(attempts.get() < 5, "timeout must stop before exhausting maxAttempts, actual: " + attempts.get());
            assertEquals(0, recoveryRetryCount.get(), "timeout after the first attempt means zero retries");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void jitterDoesNotChangeAttemptCount() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger attempts = new AtomicInteger();

        try {
            GXRetryOptions options = new GXRetryOptions(3, 100L, 1.0, 1000L, 0L, 50L, Map.of(Exception.class, true));
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(context -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("temporary");
                }
                return "ok";
            }, null, options, executor);

            assertEquals("ok", future.join());
            assertEquals(3, attempts.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cancelledTokenSkipsRemainingAttemptsAndRecovery() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        GXCancellationToken token = new GXCancellationToken();
        AtomicInteger attempts = new AtomicInteger();
        AtomicBoolean recovered = new AtomicBoolean(false);
        token.cancel();

        try {
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(
                    context -> {
                        attempts.incrementAndGet();
                        return "ok";
                    },
                    context -> {
                        recovered.set(true);
                        return "recovered";
                    },
                    GXRetryOptions.defaults(),
                    executor,
                    token);

            ExecutionException exception = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(GXRetryCancelledException.class, exception.getCause());
            assertEquals(0, attempts.get());
            assertFalse(recovered.get(), "recovery callback must not run when the operation is cancelled");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cancellationDuringBackoffStopsTheChain() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        GXCancellationToken token = new GXCancellationToken();
        AtomicInteger attempts = new AtomicInteger();

        try {
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(
                    context -> {
                        if (attempts.incrementAndGet() == 1) {
                            throw new IllegalStateException("fail once");
                        }
                        return "unexpected";
                    },
                    null,
                    new GXRetryOptions(4, 100L, 1.0, 1000L, 0L, 0L, Map.of(Exception.class, true)),
                    executor,
                    token);

            sleep(30L);
            token.cancel();

            ExecutionException exception = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(GXRetryCancelledException.class, exception.getCause());
            assertTrue(attempts.get() < 4, "no further attempts after cancellation, actual: " + attempts.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void mdcIsPropagatedToWorkerThread() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<String> traceIdInWorker = new AtomicReference<>();
        MDC.put("traceId", "trace-123");

        try {
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(context -> {
                traceIdInWorker.set(MDC.get("traceId"));
                return "ok";
            }, executor);

            assertEquals("ok", future.join());
            assertEquals("trace-123", traceIdInWorker.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectedExecutionPropagatesToCaller() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("pool full");
        };

        assertThrows(RejectedExecutionException.class, () ->
                GXRetryUtil.retryOperationAsync(context -> "ok", rejectingExecutor));
    }

    @Test
    void activeTokenDoesNotAffectSuccessfulRetry() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        GXCancellationToken token = new GXCancellationToken();
        AtomicInteger attempts = new AtomicInteger();
        AtomicBoolean recovered = new AtomicBoolean(false);

        try {
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(
                    context -> {
                        if (attempts.incrementAndGet() < 2) {
                            throw new IllegalStateException("temporary");
                        }
                        return "ok";
                    },
                    context -> {
                        recovered.set(true);
                        return "recovered";
                    },
                    new GXRetryOptions(3, 100L, 1.0, 1000L, 0L, 0L, Map.of(Exception.class, true)),
                    executor,
                    token);

            assertEquals("ok", future.get());
            assertEquals(2, attempts.get(), "a never-cancelled token must not change retry behaviour");
            assertFalse(recovered.get(), "recovery callback must not run when the retry succeeds");
            assertFalse(token.isCancelled());
        } finally {
            executor.shutdownNow();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
