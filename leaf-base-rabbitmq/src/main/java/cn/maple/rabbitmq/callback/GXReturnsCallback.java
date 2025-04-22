package cn.maple.rabbitmq.callback;

import org.springframework.amqp.core.ReturnedMessage;

/**
 * RabbitMQ消息返回回调接口
 * <p>
 * 该接口用于处理无法路由的消息返回情况。当消息成功发送到交换机，但无法路由到队列时，
 * 如果设置了mandatory标志或发布者返回功能，RabbitMQ会将消息返回给生产者，并通过该回调接口通知。
 * </p>
 * <p>
 * 实现该接口可以自定义无法路由消息的处理逻辑，例如记录日志、重新发送到其他交换机或通知管理员等。
 * </p>
 * 
 * @author maple
 */
public interface GXReturnsCallback {
    /**
     * 消息返回回调方法
     * <p>
     * 当消息无法路由到队列时，RabbitMQ会调用该方法返回消息。
     * 通过ReturnedMessage对象可以获取到原始消息内容、交换机名称、路由键等信息。
     * </p>
     *
     * @param returned 返回的消息对象，包含消息内容、交换机、路由键和返回原因等信息
     */
    void returnedMessage(ReturnedMessage returned);
}
