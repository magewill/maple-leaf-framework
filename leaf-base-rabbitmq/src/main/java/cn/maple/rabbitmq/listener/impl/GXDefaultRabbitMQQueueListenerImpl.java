package cn.maple.rabbitmq.listener.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import cn.maple.rabbitmq.listener.GXRabbitMQQueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@Lazy
@ConditionalOnExpression("${maple.framework.mq.rabbitmq.enable:false}")
@ConditionalOnClass(name = {"org.springframework.amqp.rabbit.connection.ConnectionFactory"})
public class GXDefaultRabbitMQQueueListenerImpl implements GXRabbitMQQueueListener {
    @Override
    @RabbitHandler
    @RabbitListener(queues = "${maple.framework.mq.rabbitmq.default-queue-name:${spring.rabbitmq.default-queue-name:${spring.rabbitmq.defaultQueueName:maple.default.queue}}}")
    public void process(Message data) {
        try {
            final String messageContent = new String(data.getBody(), StandardCharsets.UTF_8);

            if (CharSequenceUtil.isNotBlank(messageContent)) {
                processMessageContent(messageContent, data);
            } else {
                log.warn("接收到空消息");
            }
        } catch (AmqpException e) {
            log.error("处理RabbitMQ消息时发生AMQP异常: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("处理RabbitMQ消息时发生未预期的异常: {}", e.getMessage(), e);
        }
    }

    private void processMessageContent(String messageContent, Message originalMessage) {
        if (JSONUtil.isTypeJSON(messageContent)) {
            processJsonMessage(messageContent);
        } else {
            processNonJsonMessage(messageContent);
        }
    }

    private void processJsonMessage(String messageContent) {
        try {
            final Dict param = JSONUtil.toBean(messageContent, Dict.class);
            log.debug("接收到JSON消息: {}", param);
            // CompletableFuture.runAsync(() -> handleComplexJsonMessage(param));
        } catch (JSONException e) {
            log.warn("JSON解析异常: {}", e.getMessage());
        }
    }

    private void processNonJsonMessage(String messageContent) {
        log.debug("接收到非JSON消息: {}", messageContent);
    }

    private CompletableFuture<Void> handleComplexJsonMessage(Dict param) {
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100);
                log.debug("异步处理JSON消息完成: {}", param);
            } catch (Exception e) {
                log.error("异步处理JSON消息时发生异常: {}", e.getMessage(), e);
            }
        });
    }
}
