package cn.maple.rocketmq.handler;

import org.apache.rocketmq.client.producer.SendStatus;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot describing one failed asynchronous message send.
 *
 * <p>{@code topic}, {@code tag} and {@code messageKey} are captured before the send is
 * submitted, so later mutations of the request object do not affect the snapshot.
 * Exactly one of {@code sendStatus} and {@code cause} is present.</p>
 *
 * @param topic      destination topic, never null
 * @param tag        destination tag, empty string when not set
 * @param messageKey message key, empty string when not set
 * @param msgId      broker message id, null when the broker did not return one
 * @param sendStatus non-OK send status, null when the send failed with an exception
 * @param cause      failure cause, null when the broker returned a non-OK status
 */
public record GXRocketMQAsyncSendFailureContext(String topic, String tag, String messageKey,
                                                @Nullable String msgId,
                                                @Nullable SendStatus sendStatus,
                                                @Nullable Throwable cause) {
    public GXRocketMQAsyncSendFailureContext {
        topic = topic == null ? "" : topic;
        tag = tag == null ? "" : tag;
        messageKey = messageKey == null ? "" : messageKey;
        if (sendStatus == null && cause == null) {
            throw new IllegalArgumentException("Either sendStatus or cause must be present");
        }
    }
}
