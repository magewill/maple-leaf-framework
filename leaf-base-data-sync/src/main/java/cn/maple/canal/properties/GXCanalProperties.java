package cn.maple.canal.properties;

import cn.maple.canal.constant.CanalConstant;
import lombok.Data;

/**
 * Shared Canal RabbitMQ properties for local and Nacos sources.
 */
@Data
public class GXCanalProperties {
    /**
     * Rabbit listener concurrency.
     */
    protected String concurrencyCount = CanalConstant.RABBITMQ_CANAL_CONCURRENCY_COUNT;
    /**
     * Queue name consumed by application.
     */
    protected String canalQueueName = CanalConstant.RABBITMQ_CANAL_QUEUE_NAME;
    /**
     * Exchange name used by Canal producer.
     */
    protected String exchangeName = CanalConstant.RABBITMQ_CANAL_EXCHANGE_NAME;
    /**
     * Routing key used by Canal producer.
     */
    protected String routingKey = CanalConstant.RABBITMQ_CANAL_ROUTING_KEY;
}
