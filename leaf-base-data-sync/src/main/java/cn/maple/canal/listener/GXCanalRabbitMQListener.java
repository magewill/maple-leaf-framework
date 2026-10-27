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
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Canal message consumer for RabbitMQ.
 *
 * <p>The bean must stay eager: a lazy listener bean is never instantiated, so its
 * {@code @RabbitListener} endpoint would never be registered.</p>
 *
 * <p>Every parsed event is classified before the message is acknowledged. Handler failures are
 * retried a bounded number of times through {@link GXCanalFailureHandler}; permanent failures are
 * rejected so the dead letter exchange routes them to the dead letter queue.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "canal", name = "mq-type", havingValue = "rabbitmq", matchIfMissing = true)
@ConditionalOnBooleanProperty(prefix = "canal", name = "consumer-enabled", havingValue = true, matchIfMissing = true)
public class GXCanalRabbitMQListener {
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

    @RabbitListener(bindings = {@QueueBinding(
            value = @Queue(value = "${canal.canal-queue-name:canalQueue}", durable = "true"),
            exchange = @Exchange(value = "${canal.exchange-name:exchange.fanout.canal}", type = ExchangeTypes.FANOUT),
            key = "${canal.routing-key:canal.example.exchange.routingkey}")},
            concurrency = "${canal.concurrency-count:10}")
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
}
