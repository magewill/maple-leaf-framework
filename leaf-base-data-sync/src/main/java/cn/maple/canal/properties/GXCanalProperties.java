package cn.maple.canal.properties;

import cn.maple.canal.constant.GXCanalConstant;
import cn.maple.canal.enums.GXCanalMqTypeEnums;
import lombok.Data;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Shared Canal consumer properties. Bound from the {@code canal} prefix by
 * {@link cn.maple.canal.config.GXCanalPropertiesConfig}.
 *
 * <p>Values are read by consumer threads while Spring may rebind them (for example after a Nacos
 * configuration refresh triggered by {@code ConfigurationPropertiesRebinder}). Every mutable field is
 * therefore {@code volatile} so that a rebind is visible to consumer threads without an external
 * happens-before edge. Keys that influence assembly (queue/exchange/routing key/concurrency/mq type,
 * RocketMQ topic and consumer group) still only take effect on restart, which is logged at startup.</p>
 */
@Data
public class GXCanalProperties {
    /**
     * Rabbit listener concurrency.
     */
    protected volatile String concurrencyCount = GXCanalConstant.RABBITMQ_CANAL_CONCURRENCY_COUNT;
    /**
     * Queue name consumed by application.
     */
    protected volatile String canalQueueName = GXCanalConstant.RABBITMQ_CANAL_QUEUE_NAME;
    /**
     * Exchange name used by Canal producer.
     */
    protected volatile String exchangeName = GXCanalConstant.RABBITMQ_CANAL_EXCHANGE_NAME;
    /**
     * Routing key used by Canal producer.
     */
    protected volatile String routingKey = GXCanalConstant.RABBITMQ_CANAL_ROUTING_KEY;

    /**
     * Active message middleware. Exactly one type is assembled.
     */
    private volatile GXCanalMqTypeEnums mqType = GXCanalMqTypeEnums.RABBITMQ;

    /**
     * Whether the consumer side is assembled at all.
     */
    private volatile boolean consumerEnabled = true;

    /**
     * Explicit configuration source: {@code local}, {@code nacos} or blank for class path detection.
     */
    private volatile String configSource = "";

    /**
     * Upper bound of the resolved handler cache.
     */
    private volatile int handlerCacheSize = GXCanalConstant.DEFAULT_HANDLER_CACHE_SIZE;

    /**
     * Log level used when no table level handler exists: off, debug, warn-once or warn.
     */
    private volatile String handlerMissLogLevel = GXCanalConstant.DEFAULT_HANDLER_MISS_LOG_LEVEL;

    @NestedConfigurationProperty
    private final GXCanalRetryProperties retry = new GXCanalRetryProperties();

    @NestedConfigurationProperty
    private final GXCanalDeadLetterProperties deadLetter = new GXCanalDeadLetterProperties();

    @NestedConfigurationProperty
    private final GXCanalRocketMQConsumerProperties rocketmq = new GXCanalRocketMQConsumerProperties();
}
