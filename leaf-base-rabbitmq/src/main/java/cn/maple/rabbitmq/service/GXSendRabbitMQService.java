package cn.maple.rabbitmq.service;

import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.Queue;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface GXSendRabbitMQService extends GXBusinessService {
    Object sendNormalMessage(GXRabbitMQMessageReqDto messageReqDto);

    Queue createQueue(String queueName, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments);

    Queue createDurableQueue(String queueName);

    Queue createTemporaryQueue(String queueName);

    boolean deleteQueue(String queueName);

    boolean queueExists(String queueName);

    boolean purgeQueue(String queueName);

    boolean createExchange(AbstractExchange exchange);

    boolean bindQueueToExchange(String queueName, String exchangeName, String routingKey);

    boolean unbindQueueFromExchange(String queueName, String exchangeName, String routingKey);

    boolean setupMessageChannel(String queueName, boolean durable, boolean exclusive, boolean autoDelete,
                                Map<String, Object> queueArgs, AbstractExchange exchange, String routingKey);


    boolean setupDurableMessageChannel(String queueName, AbstractExchange exchange, String routingKey);

    CompletableFuture<Boolean> setupMessageChannelAsync(String queueName, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments, AbstractExchange exchange, String routingKey);
}