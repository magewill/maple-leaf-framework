package cn.maple.canal.listener;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.canal.constant.GXCanalConstant;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.service.GXCanalMessageParseService;
import cn.maple.canal.service.support.GXCanalFailureHandler;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.client.annotation.AmqpListener;
import org.springframework.amqp.core.AmqpAcknowledgment;
import org.springframework.amqp.core.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Canal message consumer for RabbitMQ (AMQP 1.0).
 *
 * <p>The bean must stay eager: a lazy listener bean is never instantiated, so its
 * {@code @AmqpListener} endpoint would never be registered.</p>
 *
 * <p>The container runs with {@code autoAccept=false}, so this listener settles every delivery:
 * success → {@code ACCEPT}; a rejected payload ({@code GXCanalMessageRejectedException} or
 * {@link AmqpRejectAndDontRequeueException}) → {@code REJECT} so the dead letter exchange routes it
 * to the dead letter queue; any other failure → {@code REQUEUE}.</p>
 *
 * <p>Every parsed event is classified before the message is acknowledged. Handler failures are
 * retried a bounded number of times through {@link GXCanalFailureHandler}; permanent failures are
 * rejected.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "canal", name = "mq-type", havingValue = "rabbitmq", matchIfMissing = true)
@ConditionalOnBooleanProperty(prefix = "canal", name = "consumer-enabled", havingValue = true, matchIfMissing = true)
public class GXCanalRabbitMQListener {
    private static final String ADDRESS_PLACEHOLDER = "/queues/${canal.canal-queue-name:canalQueue}";

    private static final String CONCURRENCY_PLACEHOLDER = "${canal.concurrency-count:10}";

    private final GXCanalMessageParseService canalMessageParseService;

    private final GXCanalFailureHandler canalFailureHandler;

    private final GXCanalConsumeStatistics statistics;

    private final GXCanalLogThrottle logThrottle;

    public GXCanalRabbitMQListener(GXCanalMessageParseService canalMessageParseService,
                                   GXCanalFailureHandler canalFailureHandler,
                                   GXCanalConsumeStatistics statistics,
                                   GXCanalLogThrottle logThrottle) {
        this.canalMessageParseService = canalMessageParseService;
        this.canalFailureHandler = canalFailureHandler;
        this.statistics = statistics;
        this.logThrottle = logThrottle;
    }

    @AmqpListener(addresses = ADDRESS_PLACEHOLDER, concurrency = CONCURRENCY_PLACEHOLDER)
    public void onMessage(Message message, AmqpAcknowledgment acknowledgment) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String traceId = header(message, GXCanalConstant.HEADER_TRACE_ID);
        Integer retryCount = intHeader(message, GXCanalConstant.HEADER_RETRY_COUNT);
        AmqpAcknowledgment.Status status;
        try {
            listener(payload, traceId, retryCount);
            status = AmqpAcknowledgment.Status.ACCEPT;
        } catch (AmqpRejectAndDontRequeueException e) {
            logThrottle.warn("rabbit-reject", "Canal message rejected: error={}", e.getMessage());
            status = AmqpAcknowledgment.Status.REJECT;
        } catch (RuntimeException e) {
            logThrottle.error("rabbit-requeue", "Canal message requeued: error={}", e.getMessage());
            status = AmqpAcknowledgment.Status.REQUEUE;
        }
        // 结算放在 try 之外：结算失败属于传输问题，不能被当成业务失败再触发一次 REQUEUE。
        acknowledgment.acknowledge(status);
    }

    public void listener(String payload,
                         @Header(name = GXCanalConstant.HEADER_TRACE_ID, required = false) String traceId,
                         @Header(name = GXCanalConstant.HEADER_RETRY_COUNT, required = false) Integer retryCount) {
        String effectiveTraceId = CharSequenceUtil.isBlank(traceId)
                ? GXTraceIdContextUtils.generateTraceId()
                : traceId;
        GXTraceIdContextUtils.setTraceId(effectiveTraceId);
        int currentRetryCount = retryCount == null ? 0 : retryCount;
        try {
            consume(payload, effectiveTraceId, currentRetryCount);
        } finally {
            GXTraceIdContextUtils.removeTraceId();
        }
    }

    private void consume(String payload, String traceId, int currentRetryCount) {
        List<GXCanalParseResultInnerDto> results;
        try {
            results = canalMessageParseService.parse(payload);
        } catch (RuntimeException e) {
            logThrottle.error("rabbit-parse-failed",
                    "Canal message processing failed, retry requested: traceId={}, retryCount={}, error={}",
                    traceId, currentRetryCount, e.getMessage(), e);
            canalFailureHandler.handle(GXCanalParseResultInnerDto.retryableFailure(describe(e), 0),
                    payload, currentRetryCount, traceId);
            return;
        }

        statistics.recordBatchEvents(results.size());
        GXCanalParseResultInnerDto firstRetryable = null;
        GXCanalParseResultInnerDto firstPermanent = null;
        for (GXCanalParseResultInnerDto result : results) {
            switch (result.outcome()) {
                case SUCCESS -> statistics.recordSuccess();
                case SKIPPED -> statistics.recordSkipped();
                case RETRYABLE_FAILURE -> {
                    statistics.recordRetryableFailure();
                    logFailure(traceId, result);
                    if (firstRetryable == null) {
                        firstRetryable = result;
                    }
                }
                case NON_RETRYABLE_FAILURE -> {
                    statistics.recordNonRetryableFailure();
                    logFailure(traceId, result);
                    if (firstPermanent == null) {
                        firstPermanent = result;
                    }
                }
            }
        }
        GXCanalParseResultInnerDto firstFailure = firstRetryable == null ? firstPermanent : firstRetryable;
        if (firstFailure != null) {
            canalFailureHandler.handle(firstFailure, payload, currentRetryCount, traceId);
        }
    }

    private void logFailure(String traceId, GXCanalParseResultInnerDto result) {
        logThrottle.warn("rabbit-failed-events:" + result.outcome(),
                "Canal payload contains a failed event: traceId={}, batchIndex={}, outcome={}, reason={}",
                traceId, result.batchIndex(), result.outcome(), result.reason());
    }

    private String describe(RuntimeException exception) {
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    private static String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeader(name);
        return value == null ? null : value.toString();
    }

    private static Integer intHeader(Message message, String name) {
        Object value = message.getMessageProperties().getHeader(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && CharSequenceUtil.isNotBlank(text)) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
