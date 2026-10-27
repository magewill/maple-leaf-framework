package cn.maple.canal.service.impl.rocketmq;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.exception.GXCanalMessageRetryRequestedException;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.canal.service.support.GXCanalFailureHandler;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;
import cn.maple.rocketmq.service.GXSendRocketMQService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ failure action.
 *
 * <p>Retryable failures are rethrown so the container returns {@code RECONSUME_LATER} and the broker
 * applies its bounded reconsume policy. Permanent failures are forwarded to the configured dead letter
 * topic through {@code leaf-base-rocketmq} and then acknowledged.</p>
 *
 * <p>{@code RocketMQListener<String>} does not expose message properties, so the trace id is always
 * generated locally for RocketMQ deliveries and cannot be carried across reconsume.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "canal", name = "mq-type", havingValue = "rocketmq")
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQListener")
public class GXCanalRocketMQFailureHandler implements GXCanalFailureHandler {
    private final GXCanalProperties canalProperties;

    private final GXCanalConsumeStatistics statistics;

    private final ObjectProvider<GXSendRocketMQService> rocketMQSendServiceProvider;

    private final GXCanalLogThrottle logThrottle;

    public GXCanalRocketMQFailureHandler(GXCanalProperties canalProperties, GXCanalConsumeStatistics statistics,
                                         ObjectProvider<GXSendRocketMQService> rocketMQSendServiceProvider,
                                         GXCanalLogThrottle logThrottle) {
        this.canalProperties = canalProperties;
        this.statistics = statistics;
        this.rocketMQSendServiceProvider = rocketMQSendServiceProvider;
        this.logThrottle = logThrottle;
    }

    @Override
    public void handle(GXCanalParseResultInnerDto result, String rawPayload, int currentRetryCount, String traceId) {
        if (result.retryable()) {
            logThrottle.warn("rocketmq-retry", "Canal message requires retry: traceId={}, outcome={}, reason={}",
                    traceId, result.outcome(), result.reason());
            throw new GXCanalMessageRetryRequestedException("Canal message retry requested: " + result.reason());
        }
        forwardToDeadLetterTopic(result, rawPayload, traceId);
    }

    private void forwardToDeadLetterTopic(GXCanalParseResultInnerDto result, String rawPayload, String traceId) {
        String deadLetterTopic = canalProperties.getRocketmq().getDeadLetterTopic();
        if (CharSequenceUtil.isBlank(deadLetterTopic)) {
            logThrottle.error("rocketmq-drop",
                    "Canal message dropped without retry: traceId={}, outcome={}, reason={}, "
                            + "deadLetterTopic is not configured",
                    traceId, result.outcome(), result.reason());
            return;
        }
        GXSendRocketMQService sendService = rocketMQSendServiceProvider.getIfAvailable();
        if (sendService == null) {
            logThrottle.error("rocketmq-drop",
                    "Canal message dropped without retry: traceId={}, reason={}, no GXSendRocketMQService available",
                    traceId, result.reason());
            return;
        }
        GXRocketMQMessageReqDto message = new GXRocketMQMessageReqDto();
        message.setTopic(deadLetterTopic);
        message.setTag("");
        message.setBody(rawPayload);
        sendService.sendOneway(message);
        statistics.recordDeadLetter();
        logThrottle.warn("rocketmq-dead-letter",
                "Canal message forwarded to dead letter topic: traceId={}, topic={}, reason={}",
                traceId, deadLetterTopic, result.reason());
    }
}
