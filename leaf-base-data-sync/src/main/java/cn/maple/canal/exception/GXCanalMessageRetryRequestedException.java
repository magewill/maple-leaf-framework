package cn.maple.canal.exception;

/**
 * Signals that a message should be delivered again by the broker.
 *
 * <p>RocketMQ consumers turn any thrown exception into {@code RECONSUME_LATER}; this type makes the
 * intent explicit so logs can distinguish a retry request from an unexpected defect.</p>
 */
public class GXCanalMessageRetryRequestedException extends RuntimeException {
    public GXCanalMessageRetryRequestedException(String message) {
        super(message);
    }
}
