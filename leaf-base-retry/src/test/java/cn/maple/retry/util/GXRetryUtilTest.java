package cn.maple.retry.util;

import cn.maple.retry.callback.GXRetryCallback;
import cn.maple.retry.context.GXRetryContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GXRetryUtilTest {

    @Test
    void testRetryOperation_DefaultParams_SuccessOnFirstAttempt() {
        String expectedResult = "success";

        String result = GXRetryUtil.retryOperation(context -> expectedResult);

        assertEquals(expectedResult, result);
    }

    @Test
    void testRetryOperation_DefaultParams_SuccessAfterRetries() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicReference<GXRetryContext> thirdAttemptContext = new AtomicReference<>();

        String result = GXRetryUtil.retryOperation(context -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new IllegalStateException("temporary failure " + attempt);
            }
            thirdAttemptContext.set(context);
            return "success";
        });

        assertEquals("success", result);
        assertEquals(3, attempts.get());
        assertEquals(2, thirdAttemptContext.get().getRetryCount());
        assertInstanceOf(IllegalStateException.class, thirdAttemptContext.get().getLastThrowable());
        assertEquals("temporary failure 2", thirdAttemptContext.get().getLastThrowable().getMessage());
    }

    @Test
    void testRetryOperation_DefaultParams_AllAttemptsFail() {
        AtomicInteger attempts = new AtomicInteger(0);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                GXRetryUtil.retryOperation(context -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("always fail");
                }));

        assertTrue(exception.getMessage().contains("always fail"));
        assertEquals(3, attempts.get());
    }

    @Test
    void testRetryOperation_CustomParams_SuccessAfterRetries() throws Throwable {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = GXRetryUtil.retryOperation((GXRetryCallback<String, Throwable>) context -> {
            if (attempts.incrementAndGet() < 4) {
                throw new RuntimeException("temporary failure");
            }
            return "success";
        }, 5, 100L);

        assertEquals("success", result);
        assertEquals(4, attempts.get());
    }

    @Test
    void testRetryOperation_FullCustomParams_RetryOnSpecificExceptions() throws Throwable {
        AtomicInteger attempts = new AtomicInteger(0);
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(
                IOException.class, true,
                SocketTimeoutException.class, true);

        String result = GXRetryUtil.retryOperation(new GXRetryCallback<String, Throwable>() {
            @Override
            public String doWithRetry(GXRetryContext context) throws Throwable {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    throw new IOException("io");
                }
                if (attempt == 2) {
                    throw new SocketTimeoutException("timeout");
                }
                return "success";
            }
        }, 4, 100L, 1.5, 1000L, retryableExceptions);

        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void testRetryOperation_SpecificExceptions_NonRetryableException() {
        AtomicInteger attempts = new AtomicInteger(0);
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(IOException.class, true);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                GXRetryUtil.retryOperation(new GXRetryCallback<String, Throwable>() {
                    @Override
                    public String doWithRetry(GXRetryContext context) {
                        attempts.incrementAndGet();
                        throw new IllegalArgumentException("bad argument");
                    }
                }, 3, 100L, 1.5, 1000L, retryableExceptions));

        assertInstanceOf(IllegalArgumentException.class, exception);
        assertEquals(1, attempts.get());
    }

    @Test
    void testRetryOperation_CheckedExceptionTypeIsPreserved() {
        AtomicInteger attempts = new AtomicInteger(0);

        IOException exception = assertThrows(IOException.class, () ->
                GXRetryUtil.retryOperation((GXRetryCallback<String, IOException>) context -> {
                    attempts.incrementAndGet();
                    throw new IOException("checked failure");
                }, 2, 100L));

        assertEquals("checked failure", exception.getMessage());
        assertEquals(2, attempts.get());
    }

    @Test
    void testRetryOperation_DefaultExceptionPolicyDoesNotRetryErrors() {
        AtomicInteger attempts = new AtomicInteger(0);

        AssertionError error = assertThrows(AssertionError.class, () ->
                GXRetryUtil.retryOperation(context -> {
                    attempts.incrementAndGet();
                    throw new AssertionError("fatal");
                }));

        assertEquals("fatal", error.getMessage());
        assertEquals(1, attempts.get());
    }

    @Test
    void testRetryOperation_FalseExceptionMappingExcludesRetry() {
        AtomicInteger attempts = new AtomicInteger(0);
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(
                Exception.class, true,
                IllegalArgumentException.class, false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                GXRetryUtil.retryOperation(context -> {
                    attempts.incrementAndGet();
                    throw new IllegalArgumentException("excluded");
                }, 3, 100L, 1.5, 1000L, retryableExceptions));

        assertEquals("excluded", exception.getMessage());
        assertEquals(1, attempts.get());
    }

    @Test
    void testRetryOperation_RecoveryRunsForNonRetryableExceptionWithoutRetrying() {
        AtomicInteger attempts = new AtomicInteger(0);
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(IOException.class, true);

        String result = GXRetryUtil.retryOperation(
                context -> {
                    attempts.incrementAndGet();
                    throw new IllegalArgumentException("not retryable");
                },
                context -> {
                    assertEquals(1, context.getRetryCount());
                    assertInstanceOf(IllegalArgumentException.class, context.getLastThrowable());
                    return "recovered";
                },
                3,
                100L,
                1.5,
                1000L,
                retryableExceptions);

        assertEquals("recovered", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void testRetryOperation_RecoveryCallbackReceivesLastFailureAndTotalAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = GXRetryUtil.retryOperation(
                context -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("always fail");
                },
                context -> {
                    assertEquals(3, context.getRetryCount());
                    assertInstanceOf(RuntimeException.class, context.getLastThrowable());
                    assertEquals("always fail", context.getLastThrowable().getMessage());
                    return "recovered";
                });

        assertEquals("recovered", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void testRetryOperation_RecoveryCallbackExceptionIsPropagated() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                GXRetryUtil.retryOperation(
                        context -> {
                            throw new RuntimeException("operation failed");
                        },
                        context -> {
                            throw new IllegalStateException("recovery failed");
                        },
                        2));

        assertEquals("recovery failed", exception.getMessage());
    }

    @Test
    void testRetryOperation_NullCallbackRejected() {
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                GXRetryUtil.retryOperation(null));

        assertTrue(exception.getMessage().contains("retryCallback"));
    }

    @Test
    void testRetryOperation_InvalidRetryParametersRejectedBeforeExecution() {
        AtomicInteger attempts = new AtomicInteger(0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                GXRetryUtil.retryOperation(context -> {
                    attempts.incrementAndGet();
                    return "never";
                }, 0));

        assertTrue(exception.getMessage().contains("maxAttempts"));
        assertEquals(0, attempts.get());
    }

    @Test
    void testRetrySupplier_RetriesSupplier() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = GXRetryUtil.retrySupplier(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("supplier failed");
            }
            return "success";
        }, 2, 100L);

        assertEquals("success", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void testRetryOperationAsync_SuccessAfterRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(context -> {
                if (attempts.incrementAndGet() < 2) {
                    throw new RuntimeException("async failed");
                }
                return "success";
            }, executor);

            assertEquals("success", future.get());
            assertEquals(2, attempts.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testRetryOperationAsync_FailureWrappedInBusinessException() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(context -> {
                throw new RuntimeException("async failed");
            }, executor);

            ExecutionException exception = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(cn.maple.core.framework.exception.GXBusinessException.class, exception.getCause());
            assertEquals("async failed", exception.getCause().getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testRetryOperationAsync_CustomRecoveryCallback() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(
                    context -> {
                        attempts.incrementAndGet();
                        throw new SQLException("database unavailable");
                    },
                    context -> {
                        assertEquals(2, context.getRetryCount());
                        assertInstanceOf(SQLException.class, context.getLastThrowable());
                        return "async recovered";
                    },
                    2,
                    100L,
                    1.5,
                    1000L,
                    Map.of(SQLException.class, true),
                    executor);

            assertEquals("async recovered", future.get());
            assertEquals(2, attempts.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testRetryOperation_PerformanceAndDelay() {
        AtomicInteger attempts = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                GXRetryUtil.retryOperation(context -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("always fail");
                }, 4, 100L, 2.0, 1000L, null));

        long duration = System.currentTimeMillis() - startTime;

        assertTrue(exception.getMessage().contains("always fail"));
        assertEquals(4, attempts.get());
        assertTrue(duration >= 700L, "retry delay should use exponential backoff");
    }
}
