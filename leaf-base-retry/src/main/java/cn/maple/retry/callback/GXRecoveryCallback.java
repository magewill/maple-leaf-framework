package cn.maple.retry.callback;

import cn.maple.retry.context.GXRetryContext;

/**
 * Callback invoked after all retry attempts have failed.
 *
 * @param <T> recovery result type
 */
@FunctionalInterface
public interface GXRecoveryCallback<T> {

    /**
     * Recover from retry exhaustion.
     *
     * @param context retry context containing the last failure
     * @return recovery result
     * @throws Throwable if recovery fails
     */
    T recover(GXRetryContext context) throws Throwable;
}
