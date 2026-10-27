package cn.maple.canal.enums;

/**
 * Message middleware bound to {@code canal.mq-type}. Exactly one type is active at a time.
 */
public enum GXCanalMqTypeEnums {
    /**
     * AMQP based consumption with exchange, queue, binding and dead letter queue.
     */
    RABBITMQ,
    /**
     * RocketMQ based consumption; no AMQP infrastructure is declared.
     */
    ROCKETMQ
}
