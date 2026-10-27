package cn.maple.canal.properties;

import cn.maple.canal.constant.GXCanalConstant;
import lombok.Data;

/**
 * RocketMQ consumer settings. Connection level settings stay in {@code rocketmq.*} and are owned by
 * {@code leaf-base-rocketmq}.
 *
 * <p>Fields are {@code volatile} so consumer threads observe a rebind. The consume thread count is not
 * configurable: it is an annotation attribute of {@code @RocketMQMessageListener} and therefore fixed
 * at {@link GXCanalConstant#ROCKETMQ_CONSUME_THREAD_MAX}; the effective value is logged at startup.</p>
 */
@Data
public class GXCanalRocketMQConsumerProperties {
    /**
     * Topic the Canal producer writes to. Required when {@code canal.mq-type=rocketmq}.
     */
    private volatile String topic = "";

    /**
     * Consumer group. Required when {@code canal.mq-type=rocketmq}.
     */
    private volatile String consumerGroup = "";

    /**
     * Tag expression used as the selector.
     */
    private volatile String tag = GXCanalConstant.DEFAULT_ROCKETMQ_TAG;

    /**
     * Optional dead letter topic for non retryable payloads. When blank, such payloads are only logged
     * and counted.
     */
    private volatile String deadLetterTopic = "";
}
