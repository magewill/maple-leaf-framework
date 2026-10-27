package cn.maple.canal.service.impl.rabbit;

import cn.maple.canal.constant.GXCanalConstant;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.exception.GXCanalMessageRejectedException;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.canal.service.support.GXCanalFailureHandler;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * RabbitMQ failure action: bounded re-publish, then reject so the dead letter exchange routes the
 * message to the dead letter queue.
 *
 * <p>AMQP has no delivery counter for rejected messages, so the retry count travels in the
 * {@code x-canal-retry-count} header of the re-published message.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "canal", name = "mq-type", havingValue = "rabbitmq", matchIfMissing = true)
public class GXCanalRabbitMQFailureHandler implements GXCanalFailureHandler {
    private final GXCanalProperties canalProperties;

    private final GXCanalConsumeStatistics statistics;

    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    private final GXCanalLogThrottle logThrottle;

    public GXCanalRabbitMQFailureHandler(GXCanalProperties canalProperties, GXCanalConsumeStatistics statistics,
                                         ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
                                         GXCanalLogThrottle logThrottle) {
        this.canalProperties = canalProperties;
        this.statistics = statistics;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
        this.logThrottle = logThrottle;
    }

    @Override
    public void handle(GXCanalParseResultInnerDto result, String rawPayload, int currentRetryCount, String traceId) {
        int maxAttempts = Math.max(0, canalProperties.getRetry().getMaxAttempts());
        if (result.retryable() && currentRetryCount < maxAttempts) {
            republish(result, rawPayload, currentRetryCount + 1, traceId);
            return;
        }
        statistics.recordDeadLetter();
        logThrottle.error("rabbit-dead-letter",
                "Canal message rejected to dead letter queue: traceId={}, outcome={}, reason={}, retryCount={}, "
                        + "maxAttempts={}",
                traceId, result.outcome(), result.reason(), currentRetryCount, maxAttempts);
        throw new GXCanalMessageRejectedException("Canal message rejected: " + result.reason());
    }

    private void republish(GXCanalParseResultInnerDto result, String rawPayload, int nextRetryCount, String traceId) {
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate == null) {
            statistics.recordDeadLetter();
            throw new GXCanalMessageRejectedException(
                    "RabbitTemplate is not available, cannot retry Canal message: " + result.reason());
        }
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
        messageProperties.setHeader(GXCanalConstant.HEADER_RETRY_COUNT, nextRetryCount);
        messageProperties.setHeader(GXCanalConstant.HEADER_TRACE_ID, traceId);
        Message message = new Message(rawPayload.getBytes(StandardCharsets.UTF_8), messageProperties);
        rabbitTemplate.send(canalProperties.getExchangeName(), canalProperties.getRoutingKey(), message);
        logThrottle.warn("rabbit-retry", "Canal message re-published for retry: traceId={}, retryCount={}, reason={}",
                traceId, nextRetryCount, result.reason());
    }
}
