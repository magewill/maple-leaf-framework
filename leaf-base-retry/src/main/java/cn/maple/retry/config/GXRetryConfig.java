package cn.maple.retry.config;

import cn.hutool.core.map.MapUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.retry.listener.GXRetryListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.resilience.annotation.EnableResilientMethods;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Retry configuration based on Spring Framework 7 built-in retry support.
 */
@Configuration
@EnableResilientMethods
public class GXRetryConfig {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_INITIAL_INTERVAL = 1000L;
    private static final double DEFAULT_MULTIPLIER = 2.0;
    private static final long DEFAULT_MAX_INTERVAL = 10000L;

    private static final int MIN_MAX_ATTEMPTS = 1;
    private static final int MAX_MAX_ATTEMPTS = 100;

    private static final long MIN_INITIAL_INTERVAL = 100L;
    private static final long MAX_INITIAL_INTERVAL = 300000L;

    private static final double MIN_MULTIPLIER = 1.0;
    private static final double MAX_MULTIPLIER = 10.0;

    private static final long MIN_MAX_INTERVAL = 1000L;
    private static final long MAX_MAX_INTERVAL = 3600000L;

    private static final GXRetryListener RETRY_LISTENER = new GXRetryListener();

    @Bean
    @ConditionalOnMissingBean(RetryTemplate.class)
    public RetryTemplate retryTemplate() {
        return createCustomRetryTemplate(
                DEFAULT_MAX_ATTEMPTS,
                DEFAULT_INITIAL_INTERVAL,
                DEFAULT_MULTIPLIER,
                DEFAULT_MAX_INTERVAL,
                createDefaultRetryExceptionMap());
    }

    private Map<Class<? extends Throwable>, Boolean> createDefaultRetryExceptionMap() {
        Map<Class<? extends Throwable>, Boolean> retryExceptions = new ConcurrentHashMap<>();
        retryExceptions.put(GXBusinessException.class, true);
        retryExceptions.put(IOException.class, true);
        retryExceptions.put(TimeoutException.class, true);
        retryExceptions.put(SocketTimeoutException.class, true);
        return retryExceptions;
    }

    public RetryTemplate createCustomRetryTemplate(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        validateRetryParameters(maxAttempts, initialInterval, multiplier, maxInterval);

        Map<Class<? extends Throwable>, Boolean> exceptionMap =
                MapUtil.isNotEmpty(retryExceptions) ? copyRetryExceptionMap(retryExceptions) : createDefaultRetryExceptionMap();

        RetryPolicy retryPolicy = createRetryPolicy(
                maxAttempts, initialInterval, multiplier, maxInterval, exceptionMap);
        RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
        retryTemplate.setRetryListener(RETRY_LISTENER);
        return retryTemplate;
    }

    private Map<Class<? extends Throwable>, Boolean> copyRetryExceptionMap(
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        Map<Class<? extends Throwable>, Boolean> exceptionMap = new LinkedHashMap<>();
        retryExceptions.forEach((exceptionType, retryable) -> {
            if (exceptionType == null) {
                throw new IllegalArgumentException("retry exception type must not be null");
            }
            if (retryable == null) {
                throw new IllegalArgumentException("retry exception mapping must not be null");
            }
            exceptionMap.put(exceptionType, retryable);
        });
        return exceptionMap;
    }

    public RetryTemplate createCustomRetryTemplate(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval) {
        return createCustomRetryTemplate(
                maxAttempts,
                initialInterval,
                multiplier,
                maxInterval,
                createDefaultRetryExceptionMap());
    }

    private RetryPolicy createRetryPolicy(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        RetryPolicy.Builder builder = RetryPolicy.builder()
                .maxRetries(maxAttempts - 1L)
                .delay(Duration.ofMillis(initialInterval))
                .multiplier(multiplier)
                .maxDelay(Duration.ofMillis(maxInterval));

        List<Class<? extends Throwable>> includes = new ArrayList<>();
        List<Class<? extends Throwable>> excludes = new ArrayList<>();
        retryExceptions.forEach((exceptionType, retryable) -> {
            if (Boolean.TRUE.equals(retryable)) {
                includes.add(exceptionType);
            } else {
                excludes.add(exceptionType);
            }
        });
        if (!includes.isEmpty()) {
            builder.includes(includes);
        }
        if (!excludes.isEmpty()) {
            builder.excludes(excludes);
        }
        return builder.build();
    }

    private void validateRetryParameters(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval) {
        if (maxAttempts < MIN_MAX_ATTEMPTS || maxAttempts > MAX_MAX_ATTEMPTS) {
            throw new IllegalArgumentException(
                    String.format("maxAttempts must be between %d and %d, current value: %d",
                            MIN_MAX_ATTEMPTS, MAX_MAX_ATTEMPTS, maxAttempts));
        }

        if (initialInterval < MIN_INITIAL_INTERVAL || initialInterval > MAX_INITIAL_INTERVAL) {
            throw new IllegalArgumentException(
                    String.format("initialInterval must be between %d and %d milliseconds, current value: %d",
                            MIN_INITIAL_INTERVAL, MAX_INITIAL_INTERVAL, initialInterval));
        }

        if (multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
            throw new IllegalArgumentException(
                    String.format("multiplier must be between %.1f and %.1f, current value: %.2f",
                            MIN_MULTIPLIER, MAX_MULTIPLIER, multiplier));
        }

        if (maxInterval < MIN_MAX_INTERVAL || maxInterval > MAX_MAX_INTERVAL) {
            throw new IllegalArgumentException(
                    String.format("maxInterval must be between %d and %d milliseconds, current value: %d",
                            MIN_MAX_INTERVAL, MAX_MAX_INTERVAL, maxInterval));
        }

        if (initialInterval > maxInterval) {
            throw new IllegalArgumentException(
                    String.format("initialInterval (%d) must not be greater than maxInterval (%d)",
                            initialInterval, maxInterval));
        }
    }
}
