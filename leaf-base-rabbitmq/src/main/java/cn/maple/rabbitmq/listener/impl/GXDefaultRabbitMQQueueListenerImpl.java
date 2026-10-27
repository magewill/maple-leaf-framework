package cn.maple.rabbitmq.listener.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.rabbitmq.listener.GXRabbitMQQueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 默认队列监听实现，仅负责把消息解析为 {@code Dict} 并记录必要日志。
 *
 * <p>失败处置：空消息与无法解析的 JSON 抛出 {@link AmqpRejectAndDontRequeueException}（不重入队列，
 * 由部署方决定是否配置死信）；其余运行时异常原样抛出，交由容器重试策略处理。
 * 任何情况下都不会"只记日志后正常返回"。</p>
 */
@Component
@Slf4j
@Lazy
@ConditionalOnProperty(prefix = "maple.framework.mq.rabbitmq", name = "enable", havingValue = "true")
@ConditionalOnClass(name = {"org.springframework.amqp.rabbit.connection.ConnectionFactory"})
public class GXDefaultRabbitMQQueueListenerImpl implements GXRabbitMQQueueListener {
    private static final String REASON_EMPTY = "empty";

    private static final String REASON_UNPARSEABLE = "unparseable";

    private final GXRabbitMQListenerStatistics statistics;

    public GXDefaultRabbitMQQueueListenerImpl(GXRabbitMQListenerStatistics statistics) {
        this.statistics = statistics;
    }

    @Override
    @RabbitListener(queues = "${maple.framework.mq.rabbitmq.default-queue-name:${spring.rabbitmq.default-queue-name:${spring.rabbitmq.defaultQueueName:maple.default.queue}}}")
    public void process(Message data) {
        try {
            if (data == null) {
                throw rejectEmpty(0);
            }
            byte[] body = data.getBody();
            if (body == null || body.length == 0) {
                throw rejectEmpty(0);
            }

            String messageContent = new String(body, StandardCharsets.UTF_8);
            if (!CharSequenceUtil.isNotBlank(messageContent)) {
                throw rejectEmpty(body.length);
            }

            if (JSONUtil.isTypeJSON(messageContent)) {
                parseJsonMessage(messageContent);
            } else {
                log.debug("RabbitMQ listener received text message: length={}", messageContent.length());
            }
            statistics.processed();
        } catch (AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (RuntimeException e) {
            statistics.failed(e);
            log.error("RABBITMQ_LISTENER_FAILED errorType={}, error={}", e.getClass().getName(), e.getMessage());
            throw e;
        }
    }

    private void parseJsonMessage(String messageContent) {
        try {
            Dict param = JSONUtil.toBean(messageContent, Dict.class);
            log.debug("RabbitMQ listener received json message: length={}, keys={}", messageContent.length(), param.size());
        } catch (RuntimeException e) {
            statistics.rejected(REASON_UNPARSEABLE);
            log.warn("RABBITMQ_LISTENER_REJECTED reason={}, length={}, errorType={}",
                    REASON_UNPARSEABLE, messageContent.length(), e.getClass().getName());
            throw new AmqpRejectAndDontRequeueException("Unparseable JSON message", e);
        }
    }

    private AmqpRejectAndDontRequeueException rejectEmpty(int length) {
        statistics.rejected(REASON_EMPTY);
        log.warn("RABBITMQ_LISTENER_REJECTED reason={}, length={}", REASON_EMPTY, length);
        return new AmqpRejectAndDontRequeueException("Empty message body");
    }
}
