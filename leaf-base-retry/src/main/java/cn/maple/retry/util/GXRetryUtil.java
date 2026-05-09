package cn.maple.retry.util;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.retry.callback.GXRecoveryCallback;
import cn.maple.retry.callback.GXRetryCallback;
import cn.maple.retry.config.GXRetryConfig;
import cn.maple.retry.context.GXRetryContext;
import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
public final class GXRetryUtil {
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_INITIAL_INTERVAL = 1000L;
    private static final double DEFAULT_MULTIPLIER = 2.0;
    private static final long DEFAULT_MAX_INTERVAL = 10000L;

    private static final int MAX_RETRY_TEMPLATE_CACHE_SIZE = 256;

    private static final GXRetryConfig STANDALONE_RETRY_CONFIG = new GXRetryConfig();

    private static final Map<RetryTemplateCacheKey, RetryTemplate> RETRY_TEMPLATE_CACHE = new ConcurrentHashMap<>();

    private GXRetryUtil() {
        throw new UnsupportedOperationException("GXRetryUtil is a utility class and cannot be instantiated.");
    }

    public static <T, E extends Throwable> T retryOperation(GXRetryCallback<T, E> retryCallback) throws E {
        return retryOperation(retryCallback, null, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_INTERVAL,
                DEFAULT_MULTIPLIER, DEFAULT_MAX_INTERVAL, Collections.emptyMap());
    }

    public static <T, E extends Throwable> T retryOperation(GXRetryCallback<T, E> retryCallback, int maxAttempts) throws E {
        return retryOperation(retryCallback, null, maxAttempts, DEFAULT_INITIAL_INTERVAL);
    }

    public static <T, E extends Throwable> T retryOperation(GXRetryCallback<T, E> retryCallback, int maxAttempts, long initialInterval) throws E {
        return retryOperation(retryCallback, null, maxAttempts, initialInterval);
    }

    public static <T, E extends Throwable> T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            GXRecoveryCallback<T> recoveryCallback,
            int maxAttempts) throws E {
        return retryOperation(retryCallback, recoveryCallback, maxAttempts, DEFAULT_INITIAL_INTERVAL);
    }

    public static <T, E extends Throwable> T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            GXRecoveryCallback<T> recoveryCallback,
            int maxAttempts,
            long initialInterval) throws E {
        return retryOperation(retryCallback, recoveryCallback, maxAttempts, initialInterval,
                DEFAULT_MULTIPLIER, DEFAULT_MAX_INTERVAL, Collections.emptyMap());
    }

    public static <T, E extends Throwable> T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            Map<Class<? extends Throwable>, Boolean> retryableExceptions) throws E {
        return retryOperation(retryCallback, null, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_INTERVAL,
                DEFAULT_MULTIPLIER, DEFAULT_MAX_INTERVAL, retryableExceptions);
    }

    public static <T, E extends Throwable> T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            GXRecoveryCallback<T> recoveryCallback) throws E {
        return retryOperation(retryCallback, recoveryCallback, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_INTERVAL,
                DEFAULT_MULTIPLIER, DEFAULT_MAX_INTERVAL, Collections.emptyMap());
    }

    public static <T, E extends Throwable> T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryableExceptions) throws E {
        return retryOperation(retryCallback, null, maxAttempts, initialInterval, multiplier, maxInterval, retryableExceptions);
    }

    public static <T, E extends Throwable> T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            GXRecoveryCallback<T> recoveryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval) throws E {
        return retryOperation(retryCallback, recoveryCallback, maxAttempts, initialInterval,
                multiplier, maxInterval, Collections.emptyMap());
    }

    public static <T, E extends Throwable> T retryOperation(
            GXRetryCallback<T, E> retryCallback,
            GXRecoveryCallback<T> recoveryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) throws E {
        Objects.requireNonNull(retryCallback, "retryCallback must not be null");

        RetryTemplate retryTemplate = createRetryTemplate(maxAttempts, initialInterval, multiplier, maxInterval, retryExceptions);
        AtomicInteger retryCount = new AtomicInteger(0);
        AtomicReference<Throwable> lastThrowable = new AtomicReference<>();

        try {
            return retryTemplate.execute(new org.springframework.core.retry.Retryable<>() {
                @Override
                public T execute() throws Throwable {
                    GXRetryContext context = new GXRetryContext(retryCount.getAndIncrement(), lastThrowable.get());
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
            if (recoveryCallback != null) {
                return recover(recoveryCallback, new GXRetryContext(exception.getExceptions().size(), cause));
            }
            throwAs(cause);
            return null;
        }
    }

    public static <T, E extends Throwable> CompletableFuture<T> retryOperationAsync(
            GXRetryCallback<T, E> retryCallback, Executor executor) {
        Objects.requireNonNull(retryCallback, "retryCallback must not be null");
        Objects.requireNonNull(executor, "executor must not be null");

        return CompletableFuture.supplyAsync(() -> {
            try {
                return retryOperation(retryCallback);
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                throw new GXBusinessException(e.getMessage(), e);
            }
        }, executor);
    }

    public static <T, E extends Throwable> CompletableFuture<T> retryOperationAsync(
            GXRetryCallback<T, E> retryCallback,
            GXRecoveryCallback<T> recoveryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions,
            Executor executor) {
        Objects.requireNonNull(retryCallback, "retryCallback must not be null");
        Objects.requireNonNull(executor, "executor must not be null");

        return CompletableFuture.supplyAsync(() -> {
            try {
                return retryOperation(retryCallback, recoveryCallback, maxAttempts,
                        initialInterval, multiplier, maxInterval, retryExceptions);
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                throw new GXBusinessException(e.getMessage(), e);
            }
        }, executor);
    }

    public static <T> T retrySupplier(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return retryOperation(context -> supplier.get());
    }

    public static <T> T retrySupplier(Supplier<T> supplier, int maxAttempts, long initialInterval) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return retryOperation(context -> supplier.get(), maxAttempts, initialInterval);
    }

    private static RetryTemplate createRetryTemplate(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        GXRetryConfig retryConfigBean = GXSpringContextUtils.getBean(GXRetryConfig.class);
        GXRetryConfig retryConfig = retryConfigBean == null ? STANDALONE_RETRY_CONFIG : retryConfigBean;
        Map<Class<? extends Throwable>, Boolean> effectiveRetryExceptions = getRetryableExceptionMap(retryExceptions);
        RetryTemplateCacheKey cacheKey = new RetryTemplateCacheKey(maxAttempts, initialInterval, multiplier,
                maxInterval, copyRetryExceptions(effectiveRetryExceptions), retryConfig.getClass().getName());
        evictRetryTemplateCacheIfNecessary();
        return RETRY_TEMPLATE_CACHE.computeIfAbsent(cacheKey, key ->
                retryConfig.createCustomRetryTemplate(key.maxAttempts(), key.initialInterval(),
                        key.multiplier(), key.maxInterval(), key.retryExceptions()));
    }

    private static Map<Class<? extends Throwable>, Boolean> copyRetryExceptions(
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(retryExceptions));
    }

    private static void evictRetryTemplateCacheIfNecessary() {
        if (RETRY_TEMPLATE_CACHE.size() < MAX_RETRY_TEMPLATE_CACHE_SIZE) {
            return;
        }
        var iterator = RETRY_TEMPLATE_CACHE.keySet().iterator();
        for (int count = 0; iterator.hasNext() && count < MAX_RETRY_TEMPLATE_CACHE_SIZE / 2; count++) {
            iterator.next();
            iterator.remove();
        }
    }

    private static Map<Class<? extends Throwable>, Boolean> getRetryableExceptionMap(
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        if (retryExceptions == null || retryExceptions.isEmpty()) {
            return Map.of(Exception.class, true);
        }
        return retryExceptions;
    }

    private static <T> T recover(GXRecoveryCallback<T> recoveryCallback, GXRetryContext context) {
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

    private record RetryTemplateCacheKey(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions,
            String retryConfigType) {
    }
}
