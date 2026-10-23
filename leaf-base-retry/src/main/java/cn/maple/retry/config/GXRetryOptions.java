package cn.maple.retry.config;

import java.util.Map;

/**
 * Immutable retry strategy options.
 *
 * <p>{@code timeout} and {@code jitter} use {@code 0} to mean "not configured", which keeps the
 * previous default behaviour (no total timeout, fixed exponential backoff without jitter).
 *
 * <p>An empty {@code retryExceptions} map means "no explicit mapping": {@link cn.maple.retry.util.GXRetryUtil}
 * falls back to retrying every {@link Exception}, while {@link GXRetryConfig} falls back to its own
 * default set. Keep this asymmetry in mind when building options by hand.
 *
 * @param maxAttempts      maximum number of attempts, including the initial one (1 ~ 100)
 * @param initialInterval  initial backoff interval in milliseconds (100 ~ 300000)
 * @param multiplier       backoff multiplier (1.0 ~ 10.0)
 * @param maxInterval      maximum backoff interval in milliseconds (1000 ~ 3600000)
 * @param timeout          total elapsed time budget in milliseconds, {@code 0} means unlimited
 * @param jitter           backoff jitter in milliseconds, {@code 0} means no jitter
 * @param retryExceptions  exception type mapping, {@code true} means retryable, {@code false} means excluded
 */
public record GXRetryOptions(
        int maxAttempts,
        long initialInterval,
        double multiplier,
        long maxInterval,
        long timeout,
        long jitter,
        Map<Class<? extends Throwable>, Boolean> retryExceptions) {

    private static final int MIN_MAX_ATTEMPTS = 1;
    private static final int MAX_MAX_ATTEMPTS = 100;

    private static final long MIN_INITIAL_INTERVAL = 100L;
    private static final long MAX_INITIAL_INTERVAL = 300000L;

    private static final double MIN_MULTIPLIER = 1.0;
    private static final double MAX_MULTIPLIER = 10.0;

    private static final long MIN_MAX_INTERVAL = 1000L;
    private static final long MAX_MAX_INTERVAL = 3600000L;

    public GXRetryOptions {
        validateRetryParameters(maxAttempts, initialInterval, multiplier, maxInterval, timeout, jitter);
        retryExceptions = retryExceptions == null ? Map.of() : retryExceptions;
    }

    /**
     * Default options: 3 attempts, 1000ms initial interval, x2.0 multiplier, 10000ms max interval,
     * no total timeout, no jitter, no explicit exception mapping.
     */
    public static GXRetryOptions defaults() {
        return new GXRetryOptions(
                GXRetryConfig.DEFAULT_MAX_ATTEMPTS,
                GXRetryConfig.DEFAULT_INITIAL_INTERVAL,
                GXRetryConfig.DEFAULT_MULTIPLIER,
                GXRetryConfig.DEFAULT_MAX_INTERVAL,
                0L,
                0L,
                Map.of());
    }

    /**
     * Return a copy of these options with the supplied retry exception mapping.
     */
    public GXRetryOptions withRetryExceptions(Map<Class<? extends Throwable>, Boolean> exceptions) {
        return new GXRetryOptions(maxAttempts, initialInterval, multiplier, maxInterval, timeout, jitter, exceptions);
    }

    private static void validateRetryParameters(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            long timeout,
            long jitter) {
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

        if (!Double.isFinite(multiplier) || multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
            throw new IllegalArgumentException(
                    String.format("multiplier must be a finite number between %.1f and %.1f, current value: %s",
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

        if (timeout < 0) {
            throw new IllegalArgumentException(
                    String.format("timeout must not be negative, current value: %d", timeout));
        }

        if (jitter < 0) {
            throw new IllegalArgumentException(
                    String.format("jitter must not be negative, current value: %d", jitter));
        }
    }
}
