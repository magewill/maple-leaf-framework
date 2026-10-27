package cn.maple.canal.enums;

/**
 * Outcome of one consumed Canal event.
 *
 * <p>RabbitMQ maps {@link #RETRYABLE_FAILURE} to a bounded re-publish and {@link #NON_RETRYABLE_FAILURE}
 * to a reject that routes the message to the configured dead letter queue. RocketMQ maps
 * {@link #RETRYABLE_FAILURE} to {@code RECONSUME_LATER} and forwards {@link #NON_RETRYABLE_FAILURE}
 * to the configured dead letter topic.</p>
 */
public enum GXCanalOutcomeEnums {
    /**
     * The table handler processed the event successfully.
     */
    SUCCESS,
    /**
     * The event does not need processing, for example a DDL event.
     */
    SKIPPED,
    /**
     * Processing failed with a transient error and may be retried.
     */
    RETRYABLE_FAILURE,
    /**
     * Processing failed with a permanent error and must not be retried.
     */
    NON_RETRYABLE_FAILURE;

    /**
     * Whether this outcome may be retried.
     *
     * @return true only for {@link #RETRYABLE_FAILURE}
     */
    public boolean isRetryable() {
        return this == RETRYABLE_FAILURE;
    }
}
