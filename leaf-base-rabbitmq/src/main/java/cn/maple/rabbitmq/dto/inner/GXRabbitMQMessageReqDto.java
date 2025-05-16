package cn.maple.rabbitmq.dto.inner;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import lombok.*;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;

/**
 * RabbitMQ消息请求数据传输对象
 * <p>
 * 该类封装了向RabbitMQ发送消息时所需的所有信息，包括交换机名称、路由键、消息内容等。
 * 通过使用此DTO，可以统一管理消息发送的参数，简化消息发送的接口调用。
 * </p>
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>封装消息发送所需的所有参数</li>
 *   <li>提供Builder模式支持，简化对象创建</li>
 *   <li>支持消息确认机制(publisher-confirms)</li>
 *   <li>支持自定义消息属性设置</li>
 *   <li>确保线程安全的消息属性访问</li>
 * </ul>
 *
 * <h2>线程安全说明</h2>
 * <p>
 * 该类的实例在多线程环境下使用时，通过以下机制确保线程安全：
 * <ul>
 *   <li>correlationData和messageProperties字段的延迟初始化采用同步方法</li>
 *   <li>使用不可变对象作为默认值</li>
 *   <li>Builder模式创建完整对象，减少后续修改</li>
 * </ul>
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>
 * {@code
 * // 创建消息请求DTO（推荐使用Builder模式）
 * GXRabbitMQMessageReqDto messageReqDto = GXRabbitMQMessageReqDto.builder()
 *     .exchange("my-exchange")
 *     .routingKey("my-routing-key")
 *     .data(Dict.create().set("key", "value"))
 *     .build();
 *
 * // 设置消息属性（可选，默认已设置为持久化）
 * messageReqDto.getMessageProperties().setExpiration("60000"); // 60秒过期
 *
 * // 发送消息
 * sendRabbitMQService.sendNormalMessage(messageReqDto);
 * }
 * </pre>
 *
 * @author 子墨
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class GXRabbitMQMessageReqDto extends GXBaseReqDto {
    /**
     * 交换机名称
     * <p>
     * 指定消息发送到的目标交换机。交换机负责接收消息并根据路由规则将其转发到队列。
     * </p>
     * <p>
     * 示例值："user-exchange", "order-exchange", "notification-exchange"
     * </p>
     */
    private String exchange;

    /**
     * 路由键
     * <p>
     * 交换机根据路由键和绑定规则将消息路由到相应的队列。
     * 路由键的格式和作用取决于交换机类型：
     * </p>
     * <ul>
     *   <li><b>Direct交换机</b>：完全匹配路由键，如 "user.create"</li>
     *   <li><b>Topic交换机</b>：支持通配符匹配，如 "*.user.*" 或 "user.#"</li>
     *   <li><b>Fanout交换机</b>：忽略路由键，广播到所有绑定的队列</li>
     *   <li><b>Headers交换机</b>：忽略路由键，根据消息头部信息路由</li>
     * </ul>
     * <p>
     * 示例值："user.create", "order.payment.success", "notification.email"
     * </p>
     */
    private String routingKey;

    /**
     * 消息数据
     * <p>
     * 实际的消息内容，使用Dict类型提供灵活的键值对形式。
     * 在发送时会被序列化为JSON字符串进行传输。
     * </p>
     * <p>
     * 示例：
     * {@code Dict.create().set("userId", 12345).set("action", "create")}
     * </p>
     */
    private Dict data;

    /**
     * 消费者标签
     * <p>
     * 可选字段，通常由消费者在订阅队列时指定，用于标识消费者。
     * 在特定业务场景下可能需要设置此字段。
     * </p>
     * <p>
     * 示例值："user-service-consumer", "order-processor"
     * </p>
     */
    private String tag;

    /**
     * 消息关联数据
     * <p>
     * 用于实现消息发送的确认机制(publisher-confirms)。
     * 当RabbitMQ Broker确认收到消息后，会在回调(如GXConfirmCallback)中返回这个CorrelationData，
     * 从而使发送方能够将确认信息与原始消息关联起来。
     * </p>
     * <p>
     * 如果未设置，{@link #getCorrelationData()}方法会自动创建一个新的CorrelationData实例，
     * 并将其id设置为一个随机生成的UUID。
     * </p>
     */
    private transient CorrelationData correlationData;

    /**
     * 消息属性
     * <p>
     * AMQP消息的属性，可以设置消息的多种元数据，例如：
     * </p>
     * <ul>
     *   <li>投递模式：持久化({@link MessageDeliveryMode#PERSISTENT})或非持久化({@link MessageDeliveryMode#NON_PERSISTENT})</li>
     *   <li>消息优先级(Priority)</li>
     *   <li>消息过期时间(Expiration)</li>
     *   <li>消息头部信息(Headers)：自定义的键值对，可用于传递额外的应用级元数据</li>
     *   <li>内容类型(Content Type)：如application/json</li>
     *   <li>内容编码(Content Encoding)：如UTF-8</li>
     * </ul>
     * <p>
     * 如果未设置，{@link #getMessageProperties()}方法会自动创建一个新的MessageProperties实例，
     * 并设置默认的投递模式为持久化。
     * </p>
     */
    private MessageProperties messageProperties;
}