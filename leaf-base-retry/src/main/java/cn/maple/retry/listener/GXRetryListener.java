package cn.maple.retry.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.Retryable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Retry listener based on Spring Framework 7 core retry support.
 *
 * <p>Log level is controlled by the system property {@code maple.framework.retry.log.level}
 * (threshold semantics: {@code OFF < ERROR < WARN < INFO < DEBUG}, default {@code INFO}) and read on
 * every event, so it can be changed at runtime. {@code maple.framework.retry.log.verbose=true}
 * additionally writes failure stacks at DEBUG level. These are system properties only; they cannot be
 * set through {@code application.yml}.
 */
@Slf4j
@Component
public class GXRetryListener implements RetryListener {
    private static final String LOG_LEVEL_PROPERTY = "maple.framework.retry.log.level";

    private static final String VERBOSE_PROPERTY = "maple.framework.retry.log.verbose";

    private static final String DEFAULT_LOG_LEVEL = "INFO";

    private static final int LEVEL_OFF = 0;
    private static final int LEVEL_ERROR = 1;
    private static final int LEVEL_WARN = 2;
    private static final int LEVEL_INFO = 3;
    private static final int LEVEL_DEBUG = 4;

    @Override
    public void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
        if (retryState.getRetryCount() == 1 && isLoggable(LEVEL_INFO) && log.isInfoEnabled()) {
            log.info("GXRetryListener: retry started. retryable: {}, retryCount: {}",
                    getRetryableTypeDescription(retryable), retryState.getRetryCount());
        }
    }

    @Override
    public void onRetryableExecution(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
        if (retryState.isSuccessful() || retryState.getExceptions().isEmpty()) {
            return;
        }

        Throwable throwable = retryState.getLastException();
        if (isLoggable(LEVEL_WARN) && log.isWarnEnabled()) {
            String message = "GXRetryListener: retryable execution failed. retryable: {}, retryCount: {}, exception: {}: {}";
            if (isVerbose()) {
                message += ", exceptionCount: {}";
                log.warn(message, getRetryableTypeDescription(retryable), retryState.getRetryCount(),
                        getThrowableType(throwable), getThrowableMessage(throwable),
                        retryState.getExceptions().size());
            } else {
                log.warn(message, getRetryableTypeDescription(retryable), retryState.getRetryCount(),
                        getThrowableType(throwable), getThrowableMessage(throwable));
            }
        }

        if (isLoggable(LEVEL_DEBUG) && log.isDebugEnabled()) {
            log.debug("GXRetryListener: retry failure stack. retryable: {}, retryCount: {}",
                    getRetryableTypeDescription(retryable), retryState.getRetryCount(), throwable);
        }
    }

    @Override
    public void onRetrySuccess(RetryPolicy retryPolicy, Retryable<?> retryable, Object result) {
        if (isLoggable(LEVEL_INFO) && log.isInfoEnabled()) {
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
        if (isLoggable(LEVEL_WARN) && log.isWarnEnabled()) {
            Throwable cause = exception.getCause();
            log.warn("GXRetryListener: {}. retryable: {}, retryCount: {}, lastException: {}: {}",
                    reason, getRetryableTypeDescription(retryable), exception.getRetryCount(),
                    getThrowableType(cause), getThrowableMessage(cause));
        }
        if (isLoggable(LEVEL_DEBUG) && log.isDebugEnabled()) {
            log.debug("GXRetryListener: retry termination stack. retryable: {}", getRetryableTypeDescription(retryable), exception);
        }
    }

    private String getRetryableTypeDescription(Retryable<?> retryable) {
        String retryableName = retryable.getName();
        if (retryableName != null && !retryableName.isBlank()) {
            return retryableName;
        }
        return retryable.getClass().getSimpleName();
    }

    private String getThrowableType(Throwable throwable) {
        return throwable == null ? "<null>" : throwable.getClass().getSimpleName();
    }

    private String getThrowableMessage(Throwable throwable) {
        return throwable == null ? "" : Optional.ofNullable(throwable.getMessage()).orElse("");
    }

    private boolean isLoggable(int messageLevel) {
        return messageLevel <= configuredLevel();
    }

    private int configuredLevel() {
        String level = System.getProperty(LOG_LEVEL_PROPERTY, DEFAULT_LOG_LEVEL);
        return switch (level.trim().toUpperCase()) {
            case "OFF" -> LEVEL_OFF;
            case "ERROR" -> LEVEL_ERROR;
            case "WARN" -> LEVEL_WARN;
            case "INFO" -> LEVEL_INFO;
            case "DEBUG", "TRACE" -> LEVEL_DEBUG;
            default -> LEVEL_INFO;
        };
    }

    private boolean isVerbose() {
        return Boolean.parseBoolean(System.getProperty(VERBOSE_PROPERTY, "false"));
    }

    public static Supplier<String> getConfigInfo() {
        return () -> String.format("logLevel=%s, verboseLogging=%s",
                System.getProperty(LOG_LEVEL_PROPERTY, DEFAULT_LOG_LEVEL),
                System.getProperty(VERBOSE_PROPERTY, "false"));
    }
}
