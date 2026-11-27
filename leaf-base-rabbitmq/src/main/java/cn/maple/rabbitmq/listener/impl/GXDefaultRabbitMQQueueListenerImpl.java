package cn.maple.rabbitmq.listener.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.rabbitmq.listener.GXRabbitMQQueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.client.annotation.AmqpListener;
import org.springframework.amqp.core.AmqpAcknowledgment;
import org.springframework.amqp.core.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 默认队列监听实现，仅负责把消息解析为 {@code Dict} 并记录必要日志。
 *
 * <p>AMQP 1.0 下容器以 {@code autoAccept=false} 运行，消息由本类显式结算：</p>
 * <ul>
 *   <li>处理成功 → {@code ACCEPT}；</li>
 *   <li>空消息、无法解析的 JSON → {@code REJECT}（不重入队列，由部署方决定死信路由）；</li>
 *   <li>其他运行时异常 → {@code REQUEUE}，交回 Broker 重新投递。</li>
 * </ul>
 *
 * <p>Bean 必须保持饿汉初始化：{@code @AmqpListener} 端点只在 Bean 被创建后才注册，
 * 懒加载会让监听器永远不生效。</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "maple.framework.mq.rabbitmq", name = "enable", havingValue = "true")
@ConditionalOnClass(name = {"org.springframework.amqp.client.annotation.AmqpListener"})
public class GXDefaultRabbitMQQueueListenerImpl implements GXRabbitMQQueueListener {
    private static final String REASON_EMPTY = "empty";

    private static final String REASON_UNPARSEABLE = "unparseable";

    /**
     * RabbitMQ 只接受 AMQP 1.0 v2 地址，消费源地址必须写成 {@code /queues/<queue>}，
     * 裸队列名会以 {@code amqp_address_v1_not_permitted} 被拒绝。
     */
    private static final String QUEUE_NAME_PLACEHOLDER =
            "/queues/${maple.framework.mq.rabbitmq.default-queue-name:"
                    + "${spring.rabbitmq.default-queue-name:"
                    + "${spring.rabbitmq.defaultQueueName:maple.default.queue}}}";

    private final GXRabbitMQListenerStatistics statistics;

    public GXDefaultRabbitMQQueueListenerImpl(GXRabbitMQListenerStatistics statistics) {
        this.statistics = statistics;
    }

    @AmqpListener(addresses = QUEUE_NAME_PLACEHOLDER)
    public void onMessage(Message data, AmqpAcknowledgment acknowledgment) {
        AmqpAcknowledgment.Status status;
        try {
            process(data);
            status = AmqpAcknowledgment.Status.ACCEPT;
        } catch (AmqpRejectAndDontRequeueException e) {
            status = AmqpAcknowledgment.Status.REJECT;
        } catch (RuntimeException e) {
            statistics.failed(e);
            log.error("RABBITMQ_LISTENER_REQUEUE errorType={}, error={}", e.getClass().getName(), e.getMessage());
            status = AmqpAcknowledgment.Status.REQUEUE;
        }
        // 结算放在 try 之外：结算失败属于传输问题，不能被当成业务失败再触发一次 REQUEUE。
        acknowledgment.acknowledge(status);
    }

    @Override
    public void process(Message data) {
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
