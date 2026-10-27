package cn.maple.rabbitmq.listener.impl;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 默认监听器的失败与处理计数。
 *
 * <p>存在 {@link MeterRegistry} 时注册低基数 Counter；不存在时降级为 debug 日志，
 * 不因为缺少监控组件而影响消息处理。</p>
 */
@Component
@Slf4j
public class GXRabbitMQListenerStatistics {
    private static final String METRIC_PROCESSED = "rabbitmq.listener.messages.processed";

    private static final String METRIC_REJECTED = "rabbitmq.listener.messages.rejected";

    private static final String METRIC_FAILED = "rabbitmq.listener.messages.failed";

    private static final String TAG_REASON = "reason";

    private static final String TAG_TYPE = "type";

    private final MeterRegistry registry;

    public GXRabbitMQListenerStatistics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    public void processed() {
        increment(METRIC_PROCESSED);
    }

    public void rejected(String reason) {
        increment(METRIC_REJECTED, TAG_REASON, reason);
    }

    public void failed(Throwable throwable) {
        increment(METRIC_FAILED, TAG_TYPE, typeOf(throwable));
    }

    private void increment(String name, String... tags) {
        if (registry == null) {
            log.debug("RABBITMQ_LISTENER_COUNTER name={}", name);
            return;
        }
        registry.counter(name, tags).increment();
    }

    private static String typeOf(Throwable throwable) {
        if (throwable == null) {
            return "other";
        }
        String simpleName = throwable.getClass().getSimpleName();
        return switch (simpleName) {
            case "AmqpException", "AmqpRejectAndDontRequeueException", "IllegalArgumentException", "IllegalStateException" ->
                    simpleName;
            default -> "other";
        };
    }
}
