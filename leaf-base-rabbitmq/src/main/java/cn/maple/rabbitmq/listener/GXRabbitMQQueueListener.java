package cn.maple.rabbitmq.listener;

import org.springframework.amqp.core.Message;

/**
 * 队列消息监听契约。
 *
 * <p>实现方不得"捕获异常后正常返回"——那会让容器 ack 掉消息并造成静默丢失。
 * 不可重试的失败请抛 {@code AmqpRejectAndDontRequeueException}，需要重试的失败原样抛出。</p>
 */
public interface GXRabbitMQQueueListener {
    void process(Message data);
}
