package cn.maple.rabbitmq.service;

import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.Queue;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * RabbitMQ 发送与资源管理契约。
 *
 * <p>失败表达统一为：参数非法抛 {@code GXBusinessException}，Broker 操作失败抛
 * {@code GXRabbitMQOperationException}（保留原始 {@code AmqpException} 作为 cause）。
 * 返回 {@code boolean} 的方法只在"目标不存在/未产生副作用"这类正常结果下返回 {@code false}，
 * 传输或权限失败一律抛异常。</p>
 *
 * <p>声明类方法每次都会触达 Broker（同队列并发调用由内部单飞合并），不依赖长期缓存。</p>
 */
public interface GXSendRabbitMQService extends GXBusinessService {
    /**
     * 发送普通消息。返回 {@code null} 表示已提交给 {@code RabbitTemplate}，不代表 Broker 已确认。
     *
     * <p>调用方不得在发送期间并发修改 {@code data} 载荷；模块在调用时同步完成序列化。</p>
     */
    @Nullable
    Object sendNormalMessage(GXRabbitMQMessageReqDto messageReqDto);

    /**
     * 声明队列并返回实际声明的队列描述。
     *
     * @param arguments 队列参数，可为 {@code null}
     */
    Queue createQueue(String queueName, boolean durable, boolean exclusive, boolean autoDelete,
                      @Nullable Map<String, Object> arguments);

    Queue createDurableQueue(String queueName);

    /**
     * 声明非持久化、自动删除队列；不声明排他队列（排他队列绑定声明连接，跨连接不可消费）。
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
