package cn.maple.rabbitmq.callback;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ消息发送确认回调接口
 * <p>
 * 该接口用于处理消息发送到交换机后的确认回调。当消息成功发送到交换机或发送失败时，
 * RabbitMQ会通过该回调接口通知生产者消息的发送状态。
 * </p>
 * <p>
 * 实现该接口可以自定义消息确认的处理逻辑，例如记录日志、重试发送或通知相关服务等。
 * </p>
 * 
 * @author maple
 */
@FunctionalInterface
public interface GXConfirmCallback extends RabbitTemplate.ConfirmCallback {
    /**
     * 消息发送确认回调方法
     * <p>
     * 当消息发送到交换机后，RabbitMQ会调用该方法通知消息的发送状态。
     * </p>
     *
     * @param correlationData 消息的相关数据，可用于关联消息和回调，可能为null
     * @param ack             是否成功发送到交换机，true表示成功，false表示失败
     * @param cause           失败原因，当ack为false时提供，表示消息发送失败的原因
     */
    @Override
    void confirm(CorrelationData correlationData, boolean ack, String cause);
}
