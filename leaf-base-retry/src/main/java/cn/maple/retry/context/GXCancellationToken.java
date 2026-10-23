package cn.maple.retry.context;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cancellation handle for a retrying operation.
 *
 * <p>Once cancelled, the retry loop stops before the next attempt and throws
 * {@link cn.maple.retry.exception.GXRetryCancelledException} without running the recovery callback.
 * Cancellation is cooperative: it does not interrupt a callback that is already running.
 */
public final class GXCancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Mark this token as cancelled. Idempotent.
     */
    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
