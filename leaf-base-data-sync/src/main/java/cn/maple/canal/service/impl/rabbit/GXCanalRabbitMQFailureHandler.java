package cn.maple.canal.service.impl.rabbit;

import cn.maple.canal.constant.GXCanalConstant;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.exception.GXCanalMessageRejectedException;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.canal.service.support.GXCanalFailureHandler;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import cn.maple.rabbitmq.dto.inner.GXCorrelationData;
import cn.maple.rabbitmq.service.GXSendRabbitMQService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * RabbitMQ failure action: bounded re-publish, then reject so the dead letter exchange routes the
 * message to the dead letter queue.
 *
 * <p>AMQP 1.0 has no delivery counter for rejected messages, so the retry count travels in the
 * {@code x-canal-retry-count} application property of the re-published message. The raw payload is
 * re-published as-is through {@link GXSendRabbitMQService#sendRawMessage}.</p>
 *
 * <p>重投必须等到结算结果：只有 Broker 返回 accepted 才确认原消息；重投失败（同步异常、
 * rejected/released 或结算超时）时原消息改为拒绝，进入死信队列等待回放，避免静默丢事件。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "canal", name = "mq-type", havingValue = "rabbitmq", matchIfMissing = true)
public class GXCanalRabbitMQFailureHandler implements GXCanalFailureHandler {
    private static final Duration REPUBLISH_SETTLEMENT_TIMEOUT = Duration.ofSeconds(5);

    private final GXCanalProperties canalProperties;

    private final GXCanalConsumeStatistics statistics;

    private final ObjectProvider<GXSendRabbitMQService> sendServiceProvider;

    private final GXCanalLogThrottle logThrottle;

    public GXCanalRabbitMQFailureHandler(GXCanalProperties canalProperties, GXCanalConsumeStatistics statistics,
                                         ObjectProvider<GXSendRabbitMQService> sendServiceProvider,
                                         GXCanalLogThrottle logThrottle) {
        this.canalProperties = canalProperties;
        this.statistics = statistics;
        this.sendServiceProvider = sendServiceProvider;
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
        GXSendRabbitMQService sendRabbitMQService = sendServiceProvider.getIfAvailable();
        if (sendRabbitMQService == null) {
            statistics.recordDeadLetter();
            throw new GXCanalMessageRejectedException(
                    "RabbitMQ send service is not available, cannot retry Canal message: " + result.reason());
        }
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
        messageProperties.setHeader(GXCanalConstant.HEADER_RETRY_COUNT, nextRetryCount);
        messageProperties.setHeader(GXCanalConstant.HEADER_TRACE_ID, traceId);
        GXCorrelationData correlationData = new GXCorrelationData(traceId + "#retry-" + nextRetryCount);
        try {
            sendRabbitMQService.sendRawMessage(canalProperties.getExchangeName(), canalProperties.getRoutingKey(),
                    rawPayload, messageProperties, correlationData);
        } catch (RuntimeException e) {
            statistics.recordDeadLetter();
            throw new GXCanalMessageRejectedException(
                    "Canal message republish failed, rejected to dead letter queue: " + result.reason(), e);
        }
        if (!correlationData.await(REPUBLISH_SETTLEMENT_TIMEOUT)) {
            // 重投未被 Broker 接受：原消息此时还在手上，拒绝它才能进死信而不是静默丢失。
            statistics.recordDeadLetter();
            throw new GXCanalMessageRejectedException(
                    "Canal message republish not accepted, rejected to dead letter queue: " + result.reason());
        }
        logThrottle.warn("rabbit-retry", "Canal message re-published for retry: traceId={}, retryCount={}, reason={}",
                traceId, nextRetryCount, result.reason());
    }
}
