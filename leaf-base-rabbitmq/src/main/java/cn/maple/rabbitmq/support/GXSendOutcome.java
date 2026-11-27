package cn.maple.rabbitmq.support;

/**
 * AMQP 1.0 发布结算结果。
 *
 * <p>与 RabbitMQ 0-9-1 帧的对应关系：</p>
 * <ul>
 *   <li>{@link #ACCEPTED} ↔ {@code basic.ack}；</li>
 *   <li>{@link #REJECTED} ↔ {@code basic.nack}；</li>
 *   <li>{@link #RELEASED} ↔ {@code basic.return}（无队列可路由）；</li>
 *   <li>{@link #MODIFIED} 当前 RabbitMQ 不用于结算发布；</li>
 *   <li>{@link #UNKNOWN} 结算超时或链路提前关闭。</li>
 * </ul>
 */
public enum GXSendOutcome {
    ACCEPTED,
    REJECTED,
    RELEASED,
    MODIFIED,
    UNKNOWN
}
