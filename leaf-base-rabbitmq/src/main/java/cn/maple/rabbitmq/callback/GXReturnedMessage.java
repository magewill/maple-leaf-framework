package cn.maple.rabbitmq.callback;

import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.Message;

/**
 * 不可路由消息的退回描述，替代经典协议中的 {@code ReturnedMessage}。
 *
 * <p>AMQP 1.0 用 {@code released} outcome 表达"消息未被任何队列接收"，
 * 等价于 0-9-1 的 {@code basic.return}；因此不再有 replyCode/replyText。</p>
 *
 * @param exchange   目标交换机，默认交换机为空字符串
 * @param routingKey 发送使用的路由键
 * @param address    实际发送使用的 AMQP 1.0 地址
 * @param message    原始消息
 */
public record GXReturnedMessage(String exchange, String routingKey, String address, @Nullable Message message) {
}
