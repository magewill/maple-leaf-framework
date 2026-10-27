package cn.maple.canal.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.canal.constant.GXCanalConstant;
import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.core.framework.exception.GXBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Validates the RocketMQ consumer configuration at startup.
 *
 * <p>The consumer container itself is created by {@code rocketmq-spring} from the annotation on
 * {@link cn.maple.canal.listener.GXCanalRocketMQListener}; this class only fails fast on missing
 * mandatory values and reports the effective capacity.</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "canal", name = "mq-type", havingValue = "rocketmq")
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQListener")
@ConditionalOnBooleanProperty(prefix = "canal", name = "consumer-enabled", havingValue = true, matchIfMissing = true)
public class GXCanalRocketMQConfig {
    public GXCanalRocketMQConfig(GXCanalProperties canalProperties) {
        String topic = canalProperties.getRocketmq().getTopic();
        String consumerGroup = canalProperties.getRocketmq().getConsumerGroup();
        if (CharSequenceUtil.isBlank(topic)) {
            throw new GXBusinessException(
                    "canal.rocketmq.topic must be configured when canal.mq-type=rocketmq");
        }
        if (CharSequenceUtil.isBlank(consumerGroup)) {
            throw new GXBusinessException(
                    "canal.rocketmq.consumer-group must be configured when canal.mq-type=rocketmq");
        }
        log.info("Canal RocketMQ consumer configured: topic={}, consumerGroup={}, tag={}, "
                        + "consumeThreadMax={} (fixed by annotation), maxReconsumeTimes={} (fixed by annotation), "
                        + "deadLetterTopic={}",
                topic, consumerGroup, canalProperties.getRocketmq().getTag(),
                GXCanalConstant.ROCKETMQ_CONSUME_THREAD_MAX,
                GXCanalConstant.DEFAULT_RETRY_MAX_ATTEMPTS,
                CharSequenceUtil.blankToDefault(canalProperties.getRocketmq().getDeadLetterTopic(), "none"));
    }
}
