package cn.maple.rabbitmq.callback;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@FunctionalInterface
public interface GXConfirmCallback extends RabbitTemplate.ConfirmCallback {
    @Override
    void confirm(CorrelationData correlationData, boolean ack, String cause);
}
