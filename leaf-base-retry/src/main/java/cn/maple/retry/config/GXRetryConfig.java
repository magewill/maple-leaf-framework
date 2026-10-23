package cn.maple.retry.config;

import cn.hutool.core.map.MapUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.retry.listener.GXRetryListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
 *
 * <p>The default exception set retries {@link GXBusinessException} as well; applications that use
 * business exceptions for deterministic failures should exclude them explicitly, see
 * {@code docs/leaf-base-retry开发手册.md} section "default retry exceptions and risks".
 *
 * <p>Note: {@link cn.maple.retry.util.GXRetryUtil} builds its own templates and does not read the
 * {@link RetryTemplate} bean; a custom bean only affects injection by type.
 */
@Configuration
@EnableResilientMethods
public class GXRetryConfig {
    /**
     * Default maximum attempts (including the initial attempt). Single source of truth for this module.
     */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /**
     * Default initial backoff interval in milliseconds. Single source of truth for this module.
     */
    public static final long DEFAULT_INITIAL_INTERVAL = 1000L;

    /**
     * Default backoff multiplier. Single source of truth for this module.
     */
    public static final double DEFAULT_MULTIPLIER = 2.0;

    /**
     * Default maximum backoff interval in milliseconds. Single source of truth for this module.
     */
    public static final long DEFAULT_MAX_INTERVAL = 10000L;

    private final GXRetryListener retryListener;

    public GXRetryConfig() {
        this.retryListener = new GXRetryListener();
    }

    /**
     * Spring path: use the container {@link GXRetryListener} bean when present, otherwise fall back to
     * a local instance so that importing this configuration alone still starts.
     */
    @Autowired
    public GXRetryConfig(ObjectProvider<GXRetryListener> retryListenerProvider) {
        this.retryListener = retryListenerProvider.getIfAvailable(GXRetryListener::new);
    }

    @Bean
    @ConditionalOnMissingBean(RetryTemplate.class)
    public RetryTemplate retryTemplate() {
        return createCustomRetryTemplate(
                DEFAULT_MAX_ATTEMPTS,
                DEFAULT_INITIAL_INTERVAL,
                DEFAULT_MULTIPLIER,
                DEFAULT_MAX_INTERVAL);
    }

    public RetryTemplate createCustomRetryTemplate(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval) {
        return createCustomRetryTemplate(new GXRetryOptions(
                maxAttempts,
                initialInterval,
                multiplier,
                maxInterval,
                0L,
                0L,
                createDefaultRetryExceptionMap()));
    }

    public RetryTemplate createCustomRetryTemplate(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        return createCustomRetryTemplate(new GXRetryOptions(
                maxAttempts,
                initialInterval,
                multiplier,
                maxInterval,
                0L,
                0L,
                retryExceptions));
    }

    /**
     * Create a template with an optional total timeout and optional jitter.
     *
     * @param timeout total elapsed time budget in milliseconds, {@code 0} means unlimited
     * @param jitter  backoff jitter in milliseconds, {@code 0} means no jitter
     */
    public RetryTemplate createCustomRetryTemplate(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            long timeout,
            long jitter,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        return createCustomRetryTemplate(new GXRetryOptions(
                maxAttempts,
                initialInterval,
                multiplier,
                maxInterval,
                timeout,
                jitter,
                retryExceptions));
    }

    public RetryTemplate createCustomRetryTemplate(GXRetryOptions options) {
        Map<Class<? extends Throwable>, Boolean> exceptionMap =
                MapUtil.isNotEmpty(options.retryExceptions()) ? copyRetryExceptionMap(options.retryExceptions()) : createDefaultRetryExceptionMap();
        RetryPolicy retryPolicy = createRetryPolicy(options, exceptionMap);
        RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
        retryTemplate.setRetryListener(retryListener);
        return retryTemplate;
    }

    private Map<Class<? extends Throwable>, Boolean> createDefaultRetryExceptionMap() {
        Map<Class<? extends Throwable>, Boolean> retryExceptions = new ConcurrentHashMap<>();
        retryExceptions.put(GXBusinessException.class, true);
        retryExceptions.put(IOException.class, true);
        retryExceptions.put(TimeoutException.class, true);
        retryExceptions.put(SocketTimeoutException.class, true);
        return retryExceptions;
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

    private RetryPolicy createRetryPolicy(
            GXRetryOptions options,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        RetryPolicy.Builder builder = RetryPolicy.builder()
                .maxRetries(options.maxAttempts() - 1L)
                .delay(Duration.ofMillis(options.initialInterval()))
                .multiplier(options.multiplier())
                .maxDelay(Duration.ofMillis(options.maxInterval()));
        if (options.timeout() > 0) {
            builder.timeout(Duration.ofMillis(options.timeout()));
        }
        if (options.jitter() > 0) {
            builder.jitter(Duration.ofMillis(options.jitter()));
        }

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
}
