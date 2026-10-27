package cn.maple.canal.exception;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * Signals the RabbitMQ container to reject a message without requeueing it, so the queue dead letter
 * arguments route it to the dead letter queue.
 */
public class GXCanalMessageRejectedException extends AmqpRejectAndDontRequeueException {
    public GXCanalMessageRejectedException(String message) {
        super(message);
    }

    public GXCanalMessageRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
