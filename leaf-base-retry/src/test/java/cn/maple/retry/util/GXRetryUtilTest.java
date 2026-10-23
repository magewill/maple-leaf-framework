package cn.maple.retry.util;

import cn.maple.retry.callback.GXRetryCallback;
import cn.maple.retry.config.GXRetryOptions;
import cn.maple.retry.context.GXRetryContext;
import cn.maple.retry.exception.GXRetryCancelledException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXRetryUtilTest {

    @Test
    void testRetryOperation_DefaultParams_SuccessOnFirstAttempt() {
        String expectedResult = "success";

        String result = GXRetryUtil.retryOperation(context -> expectedResult);

        assertEquals(expectedResult, result);
    }

    /**
     * Keeps exactly one case that waits for the real default backoff (1s + 2s) so that the default
     * parameters stay covered end-to-end; the rest use the 100ms lower bound.
     */
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
        assertEquals(2, thirdAttemptContext.get().getAttemptIndex());
        assertInstanceOf(IllegalStateException.class, thirdAttemptContext.get().getLastThrowable());
        assertEquals("temporary failure 2", thirdAttemptContext.get().getLastThrowable().getMessage());
    }

    @Test
    void testRetryOperation_ExhaustsConfiguredAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                GXRetryUtil.retryOperation(context -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("always fail");
                }, 3, 100L));

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
                    assertEquals(0, context.getRetryCount());
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
    void testRetryOperation_RecoveryCallbackReceivesLastFailureAndRetryCount() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicReference<GXRetryContext> lastAttemptContext = new AtomicReference<>();

        String result = GXRetryUtil.retryOperation(
                context -> {
                    lastAttemptContext.set(context);
                    attempts.incrementAndGet();
                    throw new RuntimeException("always fail");
                },
                context -> {
                    assertEquals(2, context.getRetryCount());
                    assertInstanceOf(RuntimeException.class, context.getLastThrowable());
                    assertEquals("always fail", context.getLastThrowable().getMessage());
                    return "recovered";
                },
                3,
                100L);

        assertEquals("recovered", result);
        assertEquals(3, attempts.get());
        assertEquals(2, lastAttemptContext.get().getRetryCount());
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
                        2,
                        100L));

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
    void testRetryOperation_AttributesAreCarriedAcrossAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicReference<GXRetryContext> secondAttempt = new AtomicReference<>();

        String result = GXRetryUtil.retryOperation(context -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                context.setAttribute("endpoint", "node-b");
                throw new IllegalStateException("try another node");
            }
            secondAttempt.set(context);
            return String.valueOf(context.getAttribute("endpoint"));
        }, 2, 100L);

        assertEquals("node-b", result);
        assertEquals(2, attempts.get());
        assertNotNull(secondAttempt.get().getParent());
        assertSame("node-b", secondAttempt.get().getAttribute("endpoint"));
        assertSame("node-b", secondAttempt.get().getParent().getAttribute("endpoint"));
    }

    @Test
    void testRetryOperation_PropagatesNullFromCallbackAndRecovery() {
        assertNull(GXRetryUtil.retryOperation(context -> null));

        String recoveredToNull = GXRetryUtil.retryOperation(
                (GXRetryCallback<String, RuntimeException>) context -> {
                    throw new IllegalStateException("boom");
                },
                context -> null,
                2,
                100L);

        assertNull(recoveredToNull);
    }

    @Test
    void testRetryOperation_DefaultPolicyRetriesInterruptedException() {
        AtomicInteger attempts = new AtomicInteger(0);

        InterruptedException exception = assertThrows(InterruptedException.class, () ->
                GXRetryUtil.retryOperation((GXRetryCallback<String, InterruptedException>) context -> {
                    attempts.incrementAndGet();
                    throw new InterruptedException("cancelled by upstream");
                }, 3, 100L));

        assertEquals("cancelled by upstream", exception.getMessage());
        assertEquals(3, attempts.get(), "default policy retries every Exception, including InterruptedException");
    }

    @Test
    void testRetryOperation_ExceptionMapIsSnapshottedForInFlightRetry() {
        Map<Class<? extends Throwable>, Boolean> mutableExceptions = new HashMap<>();
        mutableExceptions.put(IOException.class, true);
        AtomicInteger attempts = new AtomicInteger(0);

        IOException exception = assertThrows(IOException.class, () ->
                GXRetryUtil.retryOperation((GXRetryCallback<String, IOException>) context -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt == 1) {
                        mutableExceptions.clear();
                        mutableExceptions.put(IllegalArgumentException.class, true);
                    }
                    throw new IOException("io");
                }, 2, 100L, 1.0, 1000L, mutableExceptions));

        assertEquals("io", exception.getMessage());
        assertEquals(2, attempts.get(), "in-flight retry must follow the mapping snapshot taken at call time");
    }

    @Test
    void testRetryOperation_CacheKeyUsesImmutableExceptionMapSnapshot() throws Exception {
        Map<Class<? extends Throwable>, Boolean> mutableExceptions = new HashMap<>();
        mutableExceptions.put(IOException.class, true);

        GXRetryUtil.retryOperation(context -> "ok", 2, 200L, 1.0, 2000L, mutableExceptions);
        int sizeAfterFirstCall = templateCache().size();

        mutableExceptions.clear();
        mutableExceptions.put(IllegalArgumentException.class, true);
        Map<Class<? extends Throwable>, Boolean> sameAsFirstSnapshot = new LinkedHashMap<>();
        sameAsFirstSnapshot.put(IOException.class, true);
        GXRetryUtil.retryOperation(context -> "ok", 2, 200L, 1.0, 2000L, sameAsFirstSnapshot);

        assertEquals(sizeAfterFirstCall, templateCache().size(),
                "mutating the caller's exception map must not corrupt the cached key");
    }

    @Test
    void testRetryOperation_TotalTimeoutStopsSyncChainEarly() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger recoveryRetryCount = new AtomicInteger(-1);
        GXRetryOptions options = new GXRetryOptions(5, 100L, 1.0, 1000L, 300L, 0L, Map.of(Exception.class, true));

        String result = GXRetryUtil.retryOperation(
                context -> {
                    attempts.incrementAndGet();
                    sleep(200L);
                    throw new IllegalStateException("slow failure");
                },
                context -> {
                    recoveryRetryCount.set(context.getRetryCount());
                    return "recovered";
                },
                options);

        assertEquals("recovered", result);
        assertTrue(attempts.get() < 5, "timeout must stop before maxAttempts, actual: " + attempts.get());
        assertEquals(0, recoveryRetryCount.get(), "timeout after the first attempt means zero retries");
    }

    @Test
    void testRetryOperation_ExceptionMapOverloadRetriesConfiguredType() throws Throwable {
        AtomicInteger attempts = new AtomicInteger(0);
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(IOException.class, true);

        String result = GXRetryUtil.retryOperation((GXRetryCallback<String, Throwable>) context -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IOException("io");
            }
            return "ok";
        }, retryableExceptions);

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void testRetryOperation_RecoveryOverloadRunsRecoveryWithoutRetryingFatalError() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = GXRetryUtil.retryOperation(
                (GXRetryCallback<String, RuntimeException>) context -> {
                    attempts.incrementAndGet();
                    throw new AssertionError("fatal");
                },
                context -> {
                    assertInstanceOf(AssertionError.class, context.getLastThrowable());
                    assertEquals(0, context.getRetryCount());
                    return "recovered";
                });

        assertEquals("recovered", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void testRetryOperation_AlreadyInterruptedThreadStopsBeforeCallback() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicBoolean recovered = new AtomicBoolean(false);

        Thread.currentThread().interrupt();
        try {
            GXRetryCancelledException exception = assertThrows(GXRetryCancelledException.class, () ->
                    GXRetryUtil.retryOperation(
                            (GXRetryCallback<String, RuntimeException>) context -> {
                                attempts.incrementAndGet();
                                return "unexpected";
                            },
                            context -> {
                                recovered.set(true);
                                return "recovered";
                            }));
            assertTrue(exception.getMessage().contains("cancelled"), exception.getMessage());
        } finally {
            Thread.interrupted();
        }

        assertEquals(0, attempts.get(), "callback must not run when the calling thread is already interrupted");
        assertFalse(recovered.get(), "recovery callback must not run for cancellation");
    }

    @Test
    void testRetrySupplier_DefaultOverload() {
        assertEquals("value", GXRetryUtil.retrySupplier(() -> "value"));
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
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(
                    context -> {
                        throw new RuntimeException("async failed");
                    },
                    null,
                    2,
                    100L,
                    1.5,
                    1000L,
                    Map.of(Exception.class, true),
                    executor);

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
                        assertEquals(1, context.getRetryCount());
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
    void testRetryOperationAsync_CustomFailureWrappedInBusinessException() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(
                    context -> {
                        throw new SQLException("database unavailable");
                    },
                    null,
                    2,
                    100L,
                    1.5,
                    1000L,
                    Map.of(SQLException.class, true),
                    executor);

            ExecutionException exception = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(cn.maple.core.framework.exception.GXBusinessException.class, exception.getCause());
            assertEquals("database unavailable", exception.getCause().getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testRetryOperationAsync_ErrorIsNotWrappedInBusinessException() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(context -> {
                throw new AssertionError("fatal");
            }, executor);

            ExecutionException exception = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(AssertionError.class, exception.getCause());
            assertEquals("fatal", exception.getCause().getMessage());
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

    private Map<?, ?> templateCache() throws Exception {
        Field field = GXRetryUtil.class.getDeclaredField("RETRY_TEMPLATE_CACHE");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(null);
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
