package cn.maple.retry.exception;

import cn.maple.core.framework.exception.GXBusinessException;

/**
 * Thrown when a retrying operation is cancelled through {@link cn.maple.retry.context.GXCancellationToken}.
 *
 * <p>Cancellation is terminal: the recovery callback is not invoked.
 */
public class GXRetryCancelledException extends GXBusinessException {
    public GXRetryCancelledException(String message) {
        super(message);
    }

    public GXRetryCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
