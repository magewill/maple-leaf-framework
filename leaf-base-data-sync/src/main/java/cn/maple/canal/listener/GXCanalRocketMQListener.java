package cn.maple.canal.listener;

import cn.maple.canal.constant.GXCanalConstant;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.service.GXCanalMessageParseService;
import cn.maple.canal.service.support.GXCanalFailureHandler;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Canal message consumer for RocketMQ.
 *
 * <p>The consumer container is provided by {@code rocketmq-spring} through {@code leaf-base-rocketmq};
 * this class only adapts the payload to the shared parsing pipeline. Retryable failures are rethrown
 * so the container returns {@code RECONSUME_LATER}; permanent failures are forwarded by
 * {@link GXCanalFailureHandler} and then acknowledged.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "canal", name = "mq-type", havingValue = "rocketmq")
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQListener")
@ConditionalOnBooleanProperty(prefix = "canal", name = "consumer-enabled", havingValue = true, matchIfMissing = true)
@RocketMQMessageListener(consumerGroup = "${canal.rocketmq.consumer-group}",
        topic = "${canal.rocketmq.topic}",
        selectorExpression = "${canal.rocketmq.tag:*}",
        maxReconsumeTimes = GXCanalConstant.DEFAULT_RETRY_MAX_ATTEMPTS,
        consumeThreadMax = GXCanalConstant.ROCKETMQ_CONSUME_THREAD_MAX)
public class GXCanalRocketMQListener implements RocketMQListener<String> {
    private final GXCanalMessageParseService canalMessageParseService;

    private final GXCanalFailureHandler canalFailureHandler;

    private final GXCanalConsumeStatistics statistics;

    private final GXCanalLogThrottle logThrottle;

    public GXCanalRocketMQListener(GXCanalMessageParseService canalMessageParseService,
                                   GXCanalFailureHandler canalFailureHandler,
                                   GXCanalConsumeStatistics statistics,
                                   GXCanalLogThrottle logThrottle) {
        this.canalMessageParseService = canalMessageParseService;
        this.canalFailureHandler = canalFailureHandler;
        this.statistics = statistics;
        this.logThrottle = logThrottle;
    }

    @Override
    public void onMessage(String payload) {
        String traceId = GXTraceIdContextUtils.generateTraceId();
        GXTraceIdContextUtils.setTraceId(traceId);
        try {
            consume(payload, traceId);
        } finally {
            GXTraceIdContextUtils.removeTraceId();
        }
    }

    private void consume(String payload, String traceId) {
        List<GXCanalParseResultInnerDto> results;
        try {
            results = canalMessageParseService.parse(payload);
        } catch (RuntimeException e) {
            logThrottle.error("rocketmq-parse-failed",
                    "Canal message processing failed, retry requested: traceId={}, error={}",
                    traceId, e.getMessage(), e);
            canalFailureHandler.handle(GXCanalParseResultInnerDto.retryableFailure(describe(e), 0),
                    payload, 0, traceId);
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
            canalFailureHandler.handle(firstFailure, payload, 0, traceId);
        }
    }

    private void logFailure(String traceId, GXCanalParseResultInnerDto result) {
        logThrottle.warn("rocketmq-failed-events:" + result.outcome(),
                "Canal payload contains a failed event: traceId={}, batchIndex={}, outcome={}, reason={}",
                traceId, result.batchIndex(), result.outcome(), result.reason());
    }

    private String describe(RuntimeException exception) {
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }
}
