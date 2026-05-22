package cn.maple.canal.properties;

import cn.maple.canal.constant.GXCanalConstant;
import lombok.Data;

/**
 * Shared Canal RabbitMQ properties for local and Nacos sources.
 */
@Data
public class GXCanalProperties {
    /**
     * Rabbit listener concurrency.
     */
    protected String concurrencyCount = GXCanalConstant.RABBITMQ_CANAL_CONCURRENCY_COUNT;
    /**
     * Queue name consumed by application.
     */
    protected String canalQueueName = GXCanalConstant.RABBITMQ_CANAL_QUEUE_NAME;
    /**
     * Exchange name used by Canal producer.
     */
    protected String exchangeName = GXCanalConstant.RABBITMQ_CANAL_EXCHANGE_NAME;
    /**
     * Routing key used by Canal producer.
     */
    protected String routingKey = GXCanalConstant.RABBITMQ_CANAL_ROUTING_KEY;
}
