package cn.maple.redisson.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Forwards expired Redisson delayed-queue messages to a Redisson reliable topic.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXRedissonDelayMQToTopic {
    /**
     * Name of the blocking queue that receives expired delayed messages.
     */
    String delayQueueName();

    /**
     * Target reliable topic name.
     */
    String topicName();

    /**
     * Poll timeout in seconds when waiting for expired messages.
     */
    int timeout() default 1800;
}
