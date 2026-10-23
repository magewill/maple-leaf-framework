package cn.maple.retry.util;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXMdcThreadUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.retry.callback.GXRecoveryCallback;
import cn.maple.retry.callback.GXRetryCallback;
import cn.maple.retry.config.GXRetryConfig;
import cn.maple.retry.config.GXRetryOptions;
import cn.maple.retry.context.GXCancellationToken;
import cn.maple.retry.context.GXRetryContext;
import cn.maple.retry.exception.GXRetryCancelledException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Retry utility backed by Spring Framework 7 core retry support.
 *
 * <p>Default behaviour: up to {@value cn.maple.retry.config.GXRetryConfig#DEFAULT_MAX_ATTEMPTS}
 * attempts, 1000ms initial interval, x2.0 multiplier, 10000ms max interval, no total timeout, no
 * jitter, and <strong>every {@link Exception} is retried</strong> (including
 * {@link GXBusinessException} and {@link InterruptedException}). Exclude deterministic failures
 * explicitly when the operation is not idempotent:
 *
 * <pre>{@code
 * Map<Class<? extends Throwable>, Boolean> retryExceptions = Map.of(
 *         Exception.class, true,
 *         GXBusinessException.class, false,
 *         InterruptedException.class, false);
 * GXRetryUtil.retryOperation(callback, null, retryExceptions);
 * }</pre>
 *
 * <p>Templates are cached per strategy parameters. This utility never reads the {@code RetryTemplate}
 * bean from the container, so a custom bean only affects injection by type.
 *
 * <p>Exceptions are rethrown as-is on the synchronous path (checked types preserved) and wrapped in
 * {@link GXBusinessException} on the asynchronous path. Cancellation (see
 * {@link GXCancellationToken}) throws {@link GXRetryCancelledException} and skips the recovery callback.
 *
 * <p>All synchronous entry points are <em>nullable</em>: when the callback or the recovery callback
 * returns {@code null}, the {@code null} value is returned as-is.
 */
@Slf4j
public final class GXRetryUtil {
    private static final int MAX_RETRY_TEMPLATE_CACHE_SIZE = 256;

    private static final int TARGET_RETRY_TEMPLATE_CACHE_SIZE = 192;

    private static final Object EVICTION_LOCK = new Object();

    private static final GXRetryConfig STANDALONE_RETRY_CONFIG = new GXRetryConfig();

    private static final Map<RetryTemplateCacheKey, RetryTemplate> RETRY_TEMPLATE_CACHE = new ConcurrentHashMap<>();

    private GXRetryUtil() {
        throw new UnsupportedOperationException("GXRetryUtil is a utility class and cannot be instantiated.");
    }

    public static <T, E extends Throwable> @Nullable T retryOperation(GXRetryCallback<T, E> retryCallback) throws E {
        return retryOperation(retryCallback, null, GXRetryOptions.defaults());
    }

    public static <T, E extends Throwable> @Nullable T retryOperation(GXRetryCallback<T, E> retryCallback, int maxAttempts) throws E {
        return retryOperation(retryCallback, null, maxAttempts, GXRetryConfig.DEFAULT_INITIAL_INTERVAL);
    }

    public static <T, E extends Throwable> @Nullable T retryOperation(GXRetryCallback<T, E> retryCallback, int maxAttempts, long initialInterval) throws E {
        return retryOperation(retryCallback, null, maxAttempts, initialInterval);
    }

    public static <T, E extends Throwable> @Nullable T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            @Nullable GXRecoveryCallback<T> recoveryCallback,
            int maxAttempts) throws E {
        return retryOperation(retryCallback, recoveryCallback, maxAttempts, GXRetryConfig.DEFAULT_INITIAL_INTERVAL);
    }

    public static <T, E extends Throwable> @Nullable T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            @Nullable GXRecoveryCallback<T> recoveryCallback,
            int maxAttempts,
            long initialInterval) throws E {
        return retryOperation(retryCallback, recoveryCallback, new GXRetryOptions(
                maxAttempts,
                initialInterval,
                GXRetryConfig.DEFAULT_MULTIPLIER,
                GXRetryConfig.DEFAULT_MAX_INTERVAL,
                0L,
                0L,
                Collections.emptyMap()));
    }

    public static <T, E extends Throwable> @Nullable T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            Map<Class<? extends Throwable>, Boolean> retryableExceptions) throws E {
        return retryOperation(retryCallback, null, GXRetryOptions.defaults().withRetryExceptions(retryableExceptions));
    }

    public static <T, E extends Throwable> @Nullable T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            @Nullable GXRecoveryCallback<T> recoveryCallback) throws E {
        return retryOperation(retryCallback, recoveryCallback, GXRetryOptions.defaults());
    }

    public static <T, E extends Throwable> @Nullable T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryableExceptions) throws E {
        return retryOperation(retryCallback, null, new GXRetryOptions(
                maxAttempts, initialInterval, multiplier, maxInterval, 0L, 0L, retryableExceptions));
    }

    public static <T, E extends Throwable> @Nullable T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            @Nullable GXRecoveryCallback<T> recoveryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval) throws E {
        return retryOperation(retryCallback, recoveryCallback, new GXRetryOptions(
                maxAttempts, initialInterval, multiplier, maxInterval, 0L, 0L, Collections.emptyMap()));
    }

    public static <T, E extends Throwable> @Nullable T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            @Nullable GXRecoveryCallback<T> recoveryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) throws E {
        return retryOperation(retryCallback, recoveryCallback, new GXRetryOptions(
                maxAttempts, initialInterval, multiplier, maxInterval, 0L, 0L, retryExceptions));
    }

    /**
     * Core entry point. {@code options.timeout()} limits the total elapsed time of the whole retry
     * chain, {@code options.jitter()} adds randomness to the backoff; both default to {@code 0}
     * (disabled).
     */
    public static <T, E extends Throwable> @Nullable T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            @Nullable GXRecoveryCallback<T> recoveryCallback,
            GXRetryOptions options) throws E {
        return doRetry(retryCallback, recoveryCallback, options, null);
    }

    private static <T, E extends Throwable> @Nullable T doRetry(
            GXRetryCallback<T, E> retryCallback,
            @Nullable GXRecoveryCallback<T> recoveryCallback,
            GXRetryOptions options,
            @Nullable GXCancellationToken token) throws E {
        Objects.requireNonNull(retryCallback, "retryCallback must not be null");
        Objects.requireNonNull(options, "options must not be null");

        GXRetryOptions effectiveOptions = normalizeOptions(options);
        if (token != null) {
            effectiveOptions = excludeCancellation(effectiveOptions);
        }

        RetryTemplate retryTemplate = createRetryTemplate(effectiveOptions);
        AtomicInteger retryCount = new AtomicInteger(0);
        AtomicReference<Throwable> lastThrowable = new AtomicReference<>();
        AtomicReference<GXRetryContext> previousContext = new AtomicReference<>();

        try {
            return retryTemplate.execute(new org.springframework.core.retry.Retryable<>() {
                @Override
                public T execute() throws Throwable {
                    if (isCancelled(token)) {
                        throw new GXRetryCancelledException("retry operation cancelled");
                    }
                    GXRetryContext context = new GXRetryContext(
                            retryCount.getAndIncrement(), lastThrowable.get(), previousContext.get());
                    previousContext.set(context);
                    try {
                        return retryCallback.doWithRetry(context);
                    } catch (Throwable throwable) {
                        lastThrowable.set(throwable);
                        throw throwable;
                    }
                }

                @Override
                public String getName() {
                    return "GXRetryUtil";
                }
            });
        } catch (RetryException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof GXRetryCancelledException cancelledException) {
                throw cancelledException;
            }
            if (recoveryCallback != null) {
                return recover(recoveryCallback, new GXRetryContext(
                        exception.getRetryCount(), cause, previousContext.get()));
            }
            throwAs(cause);
            return null;
        }
    }

    public static <T, E extends Throwable> CompletableFuture<T> retryOperationAsync(
            GXRetryCallback<T, E> retryCallback, Executor executor) {
        return retryOperationAsync(retryCallback, null, GXRetryOptions.defaults(), executor, null);
    }

    public static <T, E extends Throwable> CompletableFuture<T> retryOperationAsync(
            GXRetryCallback<T, E> retryCallback,
            @Nullable GXRecoveryCallback<T> recoveryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions,
            Executor executor) {
        return retryOperationAsync(retryCallback, recoveryCallback, new GXRetryOptions(
                maxAttempts, initialInterval, multiplier, maxInterval, 0L, 0L, retryExceptions), executor, null);
    }

    public static <T, E extends Throwable> CompletableFuture<T> retryOperationAsync(
            GXRetryCallback<T, E> retryCallback,
            @Nullable GXRecoveryCallback<T> recoveryCallback,
            GXRetryOptions options,
            Executor executor) {
        return retryOperationAsync(retryCallback, recoveryCallback, options, executor, null);
    }

    /**
     * Asynchronous retry with cooperative cancellation.
     *
     * <p>The MDC context of the calling thread is propagated to the worker thread. Cancelling the
     * supplied token stops the loop before the next attempt and completes the future exceptionally
     * with {@link GXRetryCancelledException} without running the recovery callback. Note that
     * {@link CompletableFuture#cancel(boolean)} does not interrupt an in-flight retry, so use the
     * token when cancellation must be honoured.
     */
    public static <T, E extends Throwable> CompletableFuture<T> retryOperationAsync(
            GXRetryCallback<T, E> retryCallback,
            @Nullable GXRecoveryCallback<T> recoveryCallback,
            GXRetryOptions options,
            Executor executor,
            @Nullable GXCancellationToken token) {
        Objects.requireNonNull(retryCallback, "retryCallback must not be null");
        Objects.requireNonNull(options, "options must not be null");
        Objects.requireNonNull(executor, "executor must not be null");

        return GXMdcThreadUtils.supplyAsync(() -> {
            try {
                return doRetry(retryCallback, recoveryCallback, options, token);
            } catch (Error e) {
                throw e;
            } catch (GXRetryCancelledException e) {
                throw e;
            } catch (Throwable e) {
                throw new GXBusinessException(e.getMessage(), e);
            }
        }, executor);
    }

    public static <T> @Nullable T retrySupplier(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return retryOperation(context -> supplier.get());
    }

    public static <T> @Nullable T retrySupplier(Supplier<T> supplier, int maxAttempts, long initialInterval) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return retryOperation(context -> supplier.get(), maxAttempts, initialInterval);
    }

    private static RetryTemplate createRetryTemplate(GXRetryOptions options) {
        GXRetryConfig retryConfigBean = GXSpringContextUtils.getBean(GXRetryConfig.class);
        GXRetryConfig retryConfig = retryConfigBean == null ? STANDALONE_RETRY_CONFIG : retryConfigBean;
        RetryTemplateCacheKey cacheKey = new RetryTemplateCacheKey(
                options.maxAttempts(),
                options.initialInterval(),
                options.multiplier(),
                options.maxInterval(),
                options.timeout(),
                options.jitter(),
                copyRetryExceptions(options.retryExceptions()));
        evictRetryTemplateCacheIfNecessary();
        return RETRY_TEMPLATE_CACHE.computeIfAbsent(cacheKey, key -> retryConfig.createCustomRetryTemplate(options));
    }

    private static GXRetryOptions normalizeOptions(GXRetryOptions options) {
        if (!options.retryExceptions().isEmpty()) {
            return options;
        }
        return options.withRetryExceptions(Map.of(Exception.class, true));
    }

    private static GXRetryOptions excludeCancellation(GXRetryOptions options) {
        Map<Class<? extends Throwable>, Boolean> exceptions = new LinkedHashMap<>(options.retryExceptions());
        exceptions.put(GXRetryCancelledException.class, false);
        return options.withRetryExceptions(Collections.unmodifiableMap(exceptions));
    }

    private static boolean isCancelled(@Nullable GXCancellationToken token) {
        return (token != null && token.isCancelled()) || Thread.currentThread().isInterrupted();
    }

    private static Map<Class<? extends Throwable>, Boolean> copyRetryExceptions(
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(retryExceptions));
    }

    private static void evictRetryTemplateCacheIfNecessary() {
        if (RETRY_TEMPLATE_CACHE.size() <= MAX_RETRY_TEMPLATE_CACHE_SIZE) {
            return;
        }
        synchronized (EVICTION_LOCK) {
            if (RETRY_TEMPLATE_CACHE.size() <= MAX_RETRY_TEMPLATE_CACHE_SIZE) {
                return;
            }
            int removeCount = RETRY_TEMPLATE_CACHE.size() - TARGET_RETRY_TEMPLATE_CACHE_SIZE;
            var iterator = RETRY_TEMPLATE_CACHE.keySet().iterator();
            for (int count = 0; iterator.hasNext() && count < removeCount; count++) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private static <T> @Nullable T recover(GXRecoveryCallback<T> recoveryCallback, GXRetryContext context) {
        try {
            return recoveryCallback.recover(context);
        } catch (Throwable throwable) {
            throwAs(throwable);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwAs(Throwable throwable) throws E {
        throw (E) throwable;
    }

    /**
     * Cache key: all strategy inputs that affect the built template. Instance identity is deliberately
     * excluded; if a configurable strategy property is ever introduced, it must be added here.
     */
    private record RetryTemplateCacheKey(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            long timeout,
            long jitter,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
    }
}
