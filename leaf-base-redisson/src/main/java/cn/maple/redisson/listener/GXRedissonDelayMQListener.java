package cn.maple.redisson.listener;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.redisson.util.GXRedissonMQUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Listener used by the delayed-message forwarder.
 */
public interface GXRedissonDelayMQListener {
    Logger log = LoggerFactory.getLogger(GXRedissonDelayMQListener.class);

    /**
     * Handles an expired delayed message. The default implementation forwards it
     * to the configured reliable topic.
     *
     * @param topicName target topic name
     * @param message   expired delayed message
     * @return true when the message was published successfully
     */
    default CompletableFuture<Boolean> execute(String topicName, String message) {
        if (CharSequenceUtil.isBlank(topicName)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("topicName must not be blank"));
        }
        if (message == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("message must not be null"));
        }

        return GXRedissonMQUtils.publishAsync(topicName, message)
                .thenApply(subscriberCount -> {
                    log.debug("Published delayed message to topic [{}], subscriberCount={}", topicName, subscriberCount);
                    return true;
                })
                .exceptionally(ex -> {
                    log.error("Failed to publish delayed message to topic [{}]", topicName, ex);
                    return false;
                });
    }

    /**
     * Synchronous wrapper for {@link #execute(String, String)}.
     */
    default boolean executeSync(String topicName, String message, long timeout, TimeUnit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("unit must not be null");
        }
        try {
            return execute(topicName, message).get(timeout, unit);
        } catch (Exception e) {
            log.error("Failed to execute delayed message synchronously, topic={}, message={}", topicName, message, e);
            return false;
        }
    }

    /**
     * Synchronous wrapper with a 30 second timeout.
     */
    default boolean executeSync(String topicName, String message) {
        return executeSync(topicName, message, 30, TimeUnit.SECONDS);
    }
}
