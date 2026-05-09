package cn.maple.retry.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.Retryable;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Retry listener based on Spring Framework 7 core retry support.
 */
@Slf4j
@Component
public class GXRetryListener implements RetryListener {
    private static final int MAX_CACHE_SIZE = 1000;

    private static final Map<Class<?>, String> RETRYABLE_TYPE_CACHE = new ConcurrentHashMap<>();

    private static final Function<Class<?>, String> DEFAULT_TYPE_RESOLVER = Class::getSimpleName;

    private static final String LOG_LEVEL = System.getProperty("maple.framework.retry.log.level", "INFO");

    private static final boolean ENABLE_VERBOSE_LOGGING = Boolean.parseBoolean(
            System.getProperty("maple.framework.retry.log.verbose", "false"));

    @Override
    public void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
        if (retryState.getRetryCount() == 0 && shouldLogAtLevel("INFO")) {
            log.info("GXRetryListener: retry started. retryable: {}, retryCount: {}",
                    getRetryableTypeDescription(retryable), retryState.getRetryCount());
        }
    }

    @Override
    public void onRetryableExecution(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
        if (retryState.isSuccessful()) {
            return;
        }

        Throwable throwable = retryState.getLastException();
        if (shouldLogAtLevel("WARN")) {
            String message = "GXRetryListener: retry execution failed. retryable: {}, retryCount: {}, exception: {}: {}";
            if (ENABLE_VERBOSE_LOGGING) {
                message += ", exceptionCount: {}";
                log.warn(message, getRetryableTypeDescription(retryable), retryState.getRetryCount(),
                        getThrowableType(throwable), getThrowableMessage(throwable),
                        retryState.getExceptions().size());
            } else {
                log.warn(message, getRetryableTypeDescription(retryable), retryState.getRetryCount(),
                        getThrowableType(throwable), getThrowableMessage(throwable));
            }
        }

        if (shouldLogAtLevel("DEBUG") && log.isDebugEnabled()) {
            log.debug("GXRetryListener: retry failure stack. retryable: {}, retryCount: {}",
                    getRetryableTypeDescription(retryable), retryState.getRetryCount(), throwable);
        }
    }

    @Override
    public void onRetrySuccess(RetryPolicy retryPolicy, Retryable<?> retryable, Object result) {
        if (shouldLogAtLevel("INFO")) {
            log.info("GXRetryListener: retry completed successfully. retryable: {}", getRetryableTypeDescription(retryable));
        }
    }

    @Override
    public void onRetryPolicyExhaustion(RetryPolicy retryPolicy, Retryable<?> retryable, RetryException exception) {
        logRetryException("retry policy exhausted", retryable, exception);
    }

    @Override
    public void onRetryPolicyInterruption(RetryPolicy retryPolicy, Retryable<?> retryable, RetryException exception) {
        logRetryException("retry policy interrupted", retryable, exception);
    }

    @Override
    public void onRetryPolicyTimeout(RetryPolicy retryPolicy, Retryable<?> retryable, RetryException exception) {
        logRetryException("retry policy timed out", retryable, exception);
    }

    private void logRetryException(String reason, Retryable<?> retryable, RetryException exception) {
        if (shouldLogAtLevel("WARN")) {
            Throwable cause = exception.getCause();
            log.warn("GXRetryListener: {}. retryable: {}, retryCount: {}, lastException: {}: {}",
                    reason, getRetryableTypeDescription(retryable), exception.getRetryCount(),
                    getThrowableType(cause), getThrowableMessage(cause));
        }
        if (shouldLogAtLevel("DEBUG") && log.isDebugEnabled()) {
            log.debug("GXRetryListener: retry termination stack. retryable: {}", getRetryableTypeDescription(retryable), exception);
        }
    }

    private String getRetryableTypeDescription(Retryable<?> retryable) {
        if (retryable == null) {
            return "<null>";
        }

        Class<?> retryableClass = retryable.getClass();
        String retryableName = retryable.getName();
        if (retryableName != null && !retryableName.isBlank()) {
            return retryableName;
        }
        if (retryableClass.getName().contains("$$Lambda")) {
            return "Lambda@" + Integer.toHexString(System.identityHashCode(retryable));
        }

        if (RETRYABLE_TYPE_CACHE.size() >= MAX_CACHE_SIZE) {
            var iterator = RETRYABLE_TYPE_CACHE.entrySet().iterator();
            for (int count = 0; iterator.hasNext() && count < MAX_CACHE_SIZE / 2; count++) {
                iterator.next();
                iterator.remove();
            }
        }

        return RETRYABLE_TYPE_CACHE.computeIfAbsent(retryableClass, DEFAULT_TYPE_RESOLVER);
    }

    private String getThrowableType(Throwable throwable) {
        return throwable == null ? "<null>" : throwable.getClass().getSimpleName();
    }

    private String getThrowableMessage(Throwable throwable) {
        return throwable == null ? "" : Optional.ofNullable(throwable.getMessage()).orElse("");
    }

    private boolean shouldLogAtLevel(String level) {
        return switch (LOG_LEVEL.toUpperCase()) {
            case "DEBUG" -> true;
            case "INFO" -> !"DEBUG".equalsIgnoreCase(level);
            case "WARN" -> "WARN".equalsIgnoreCase(level) || "ERROR".equalsIgnoreCase(level);
            case "ERROR" -> "ERROR".equalsIgnoreCase(level);
            default -> true;
        };
    }

    public static Supplier<String> getConfigInfo() {
        return () -> String.format("logLevel=%s, verboseLogging=%s, cacheSize=%d/%d",
                LOG_LEVEL, ENABLE_VERBOSE_LOGGING, RETRYABLE_TYPE_CACHE.size(), MAX_CACHE_SIZE);
    }

    public static void clearCache() {
        RETRYABLE_TYPE_CACHE.clear();
        if (log.isDebugEnabled()) {
            log.debug("GXRetryListener: retryable type cache cleared");
        }
    }
}
