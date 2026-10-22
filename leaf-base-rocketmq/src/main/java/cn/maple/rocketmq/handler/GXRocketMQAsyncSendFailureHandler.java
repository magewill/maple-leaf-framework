package cn.maple.rocketmq.handler;

/**
 * Optional extension point invoked when an asynchronous send finally fails.
 *
 * <p>Implementations are discovered through {@code ObjectProvider}; when none is
 * registered the failure is only logged. Implementations run on the producer callback
 * thread and must not block. Exceptions thrown by an implementation are logged and do
 * not affect other implementations or the producer callback thread.</p>
 */
@FunctionalInterface
public interface GXRocketMQAsyncSendFailureHandler {
    /**
     * @param context immutable failure snapshot, never null
     */
    void onFailure(GXRocketMQAsyncSendFailureContext context);
}
