package cn.maple.canal.constant;

/**
 * Default constants for Canal + RabbitMQ integration.
 */
public class CanalConstant {
    public static final String RABBITMQ_CANAL_CONCURRENCY_COUNT = "10";
    public static final String RABBITMQ_CANAL_QUEUE_NAME = "canalQueue";
    public static final String RABBITMQ_CANAL_EXCHANGE_NAME = "exchange.fanout.canal";
    public static final String RABBITMQ_CANAL_ROUTING_KEY = "canal.example.exchange.routingkey";

    private CanalConstant() {
        // Utility class.
    }
}
