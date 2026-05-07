package cn.maple.rabbitmq.listener;

import org.springframework.amqp.core.Message;

public interface GXRabbitMQQueueListener {
    void process(Message data);
}
