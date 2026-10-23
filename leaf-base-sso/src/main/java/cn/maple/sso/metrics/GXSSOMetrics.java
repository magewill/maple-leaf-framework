package cn.maple.sso.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Process wide counters for the SSO authentication path.
 *
 * <p>The class is a static singleton on purpose: the SSO entry points are static utilities
 * ({@code GXSSOHelperUtil}) and interceptors that must not perform a bean lookup per
 * request. Reading is always done through {@link #snapshot()}, which returns an immutable
 * record.</p>
 */
public final class GXSSOMetrics {
    private static final GXSSOMetrics INSTANCE = new GXSSOMetrics();

    private final LongAdder parseSuccess = new LongAdder();

    private final LongAdder parseFailure = new LongAdder();

    private final LongAdder cacheHit = new LongAdder();

    private final LongAdder cacheMiss = new LongAdder();

    private final LongAdder denied = new LongAdder();

    private final LongAdder loginFailure = new LongAdder();

    private final LongAdder cacheFallback = new LongAdder();

    private final LongAdder invalidToken = new LongAdder();

    private final AtomicLong activeTokens = new AtomicLong();

    private GXSSOMetrics() {
    }

    public static GXSSOMetrics getInstance() {
        return INSTANCE;
    }

    public static Snapshot snapshot() {
        return INSTANCE.snapshotOf();
    }

    public static void reset() {
        INSTANCE.resetCounters();
    }

    public void incrementParseSuccess() {
        parseSuccess.increment();
    }

    public void incrementParseFailure() {
        parseFailure.increment();
    }

    public void incrementCacheHit() {
        cacheHit.increment();
    }

    public void incrementCacheMiss() {
        cacheMiss.increment();
    }

    public void incrementDenied() {
        denied.increment();
    }

    public void incrementLoginFailure() {
        loginFailure.increment();
    }

    public void incrementCacheFallback() {
        cacheFallback.increment();
    }

    public void incrementInvalidToken() {
        invalidToken.increment();
    }

    public void updateActiveTokens(long size) {
        activeTokens.set(Math.max(size, 0L));
    }

    public Snapshot snapshotOf() {
        return new Snapshot(parseSuccess.sum(), parseFailure.sum(), cacheHit.sum(), cacheMiss.sum(),
                denied.sum(), loginFailure.sum(), cacheFallback.sum(), invalidToken.sum(), activeTokens.get());
    }

    private void resetCounters() {
        parseSuccess.reset();
        parseFailure.reset();
        cacheHit.reset();
        cacheMiss.reset();
        denied.reset();
        loginFailure.reset();
        cacheFallback.reset();
        invalidToken.reset();
        activeTokens.set(0L);
    }

    /**
     * Immutable view of all counters.
     */
    public record Snapshot(long parseSuccess, long parseFailure, long cacheHit, long cacheMiss,
                           long denied, long loginFailure, long cacheFallback,
                           long invalidToken, long activeTokens) {
    }
}
