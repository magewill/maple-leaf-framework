package cn.maple.rabbitmq.callback;

import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@FunctionalInterface
public interface GXReturnsCallback extends RabbitTemplate.ReturnsCallback {
    @Override
    void returnedMessage(ReturnedMessage returned);
}
