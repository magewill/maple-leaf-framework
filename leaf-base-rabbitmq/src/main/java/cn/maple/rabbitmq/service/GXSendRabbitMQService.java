package cn.maple.rabbitmq.service;

import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.rabbitmq.dto.inner.GXCorrelationData;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * RabbitMQ 发送与资源管理契约（AMQP 1.0）。
 *
 * <p>失败表达统一为：参数非法抛 {@code GXBusinessException}，Broker 操作失败抛
 * {@code GXRabbitMQOperationException}（保留原始 {@code AmqpException} 作为 cause）。
 * 返回 {@code boolean} 的方法只在"目标不存在/未产生副作用"这类正常结果下返回 {@code false}，
 * 传输或权限失败一律抛异常。</p>
 *
 * <p>声明类方法在 AMQP 1.0 下通过 RabbitMQ Management HTTP API 触达 Broker
 * （协议本身没有 queue.declare），同队列并发调用仍由内部单飞合并。</p>
 */
public interface GXSendRabbitMQService extends GXBusinessService {
    /**
     * 发送普通消息。返回 {@code null} 表示消息已写出，结算结果通过
     * {@code GXConfirmCallback}/{@code GXReturnsCallback} 异步回调，不代表 Broker 已确认。
     *
     * <p>调用方不得在发送期间并发修改 {@code data} 载荷；模块在调用时同步完成序列化。</p>
     */
    @Nullable
    Object sendNormalMessage(GXRabbitMQMessageReqDto messageReqDto);

    /**
     * 发送消息并等待 Broker 结算。
     *
     * @param timeout 最长等待时间
     * @return {@code true} 表示 Broker 返回 accepted
     */
    boolean sendNormalMessageAwait(GXRabbitMQMessageReqDto messageReqDto, Duration timeout);

    /**
     * 发送原始载荷（不经过 Hutool 序列化），用于原样重投已收到的报文。
     *
     * <p>除消息体外的处理与 {@link #sendNormalMessage} 一致：属性快照、追踪头、`__TypeId__`、
     * 缺省 `messageId`。</p>
     *
     * @param payload           原始报文，按 UTF-8 编码写出
     * @param messageProperties 调用方消息属性，发送时会做深拷贝
     * @param correlationData   关联数据，可为 {@code null}
     */
    @Nullable
    Object sendRawMessage(String exchange, String routingKey, String payload,
                          MessageProperties messageProperties, @Nullable GXCorrelationData correlationData);

    /**
     * 声明队列并返回实际声明的队列描述。
     *
     * @param arguments 队列参数，可为 {@code null}
     */
    Queue createQueue(String queueName, boolean durable, boolean exclusive, boolean autoDelete,
                      @Nullable Map<String, Object> arguments);

    Queue createDurableQueue(String queueName);

    /**
     * 声明自动删除队列（{@code durable=true, autoDelete=true}）；不声明排他队列。
     *
     * <p>RabbitMQ 4.x 默认禁止"非持久化 + 非排他"队列，因此这里声明为持久化；
     * 队列在最后一个消费者断开后由 Broker 删除。</p>
     */
    Queue createTemporaryQueue(String queueName);

    /**
     * 删除队列；返回 {@code false} 表示队列不存在。
     */
    boolean deleteQueue(String queueName);

    boolean queueExists(String queueName);

    boolean purgeQueue(String queueName);

    boolean createExchange(AbstractExchange exchange);

    boolean bindQueueToExchange(String queueName, String exchangeName, String routingKey);

    boolean unbindQueueFromExchange(String queueName, String exchangeName, String routingKey);

    /**
     * 一站式创建交换机、队列与绑定；失败异常中携带已完成步骤，便于判断是否需要补偿。
     */
    boolean setupMessageChannel(String queueName, boolean durable, boolean exclusive, boolean autoDelete,
                                @Nullable Map<String, Object> queueArgs, AbstractExchange exchange, String routingKey);

    boolean setupDurableMessageChannel(String queueName, AbstractExchange exchange, String routingKey);

    /**
     * 异步创建消息通道；失败以 {@code failedFuture} 返回，线程池拒绝同样以失败 Future 表达。
     */
    CompletableFuture<Boolean> setupMessageChannelAsync(String queueName, boolean durable, boolean exclusive, boolean autoDelete, @Nullable Map<String, Object> arguments, AbstractExchange exchange, String routingKey);
}
