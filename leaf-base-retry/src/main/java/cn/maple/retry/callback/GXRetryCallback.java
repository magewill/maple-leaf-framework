package cn.maple.retry.callback;

import cn.maple.retry.context.GXRetryContext;

/**
 * Callback for an operation that can be retried.
 *
 * @param <T> operation result type
 * @param <E> exception type that may be thrown by the operation
 */
@FunctionalInterface
public interface GXRetryCallback<T, E extends Throwable> {
    /**
     * Execute the operation for the current retry attempt.
     *
     * @param context retry context for the current operation
     * @return operation result
     * @throws E if the operation fails
     */
    T doWithRetry(GXRetryContext context) throws E;
}
