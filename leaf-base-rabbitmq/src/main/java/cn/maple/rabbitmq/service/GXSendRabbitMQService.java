package cn.maple.rabbitmq.service;

import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.Queue;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * RabbitMQ消息发送和队列管理服务接口
 * <p>
 * 该接口定义了向RabbitMQ发送消息和管理队列的相关方法，提供了统一的消息发送和队列管理入口。
 * 通过该服务可以将消息发送到指定的交换机和路由键，支持消息确认和返回机制。
 * 同时，提供了运行时动态创建、删除和管理队列、交换机及绑定关系的能力。
 * </p>
 * <p>
 * 使用该服务可以简化RabbitMQ消息发送和队列管理的流程，统一消息格式和操作逻辑，
 * 便于后续的扩展和维护。
 * </p>
 * <p>
 * 线程安全说明：该接口的所有方法实现都是线程安全的，可以在多线程环境下调用。
 * </p>
 * <p>
 * 主要功能：
 * 1. 消息发送：支持JSON格式消息发送，自动处理消息属性和编码
 * 2. 队列管理：动态创建、删除、检查和清空队列
 * 3. 交换机管理：动态创建各类型交换机
 * 4. 绑定关系管理：创建和移除队列与交换机的绑定关系
 * 5. 一站式消息通道创建：一次性完成队列、交换机和绑定关系的创建
 * </p>
 *
 * @author maple
 */
public interface GXSendRabbitMQService extends GXBusinessService {
    /**
     * 发送常规消息到RabbitMQ
     * <p>
     * 将消息发送到指定的交换机和路由键，支持消息确认和返回机制。
     * 消息内容、交换机、路由键等信息通过GXRabbitMQMessageReqDto对象传入。
     * </p>
     * <p>
     * 该方法是线程安全的，可以在多线程环境下调用。
     * </p>
     *
     * @param messageReqDto 待发送的消息请求对象，包含消息内容、交换机、路由键等信息
     * @return Object  发送结果，通常为消息发送成功或失败的提示信息
     */
    Object sendNormalMessage(GXRabbitMQMessageReqDto messageReqDto);

    /**
     * 创建队列
     * <p>
     * 动态创建一个具有指定名称和属性的队列。如果队列已存在且属性相同，则不会进行任何操作。
     * 如果队列已存在但属性不同，则会根据RabbitMQ的规则处理（通常会抛出异常）。
     * </p>
     *
     * @param queueName  队列名称
     * @param durable    是否持久化（true表示持久化到磁盘，false表示仅保存在内存中）
     * @param exclusive  是否排他（true表示仅限创建连接可见且连接关闭时自动删除）
     * @param autoDelete 是否自动删除（true表示当最后一个消费者断开连接时自动删除）
     * @param arguments  队列的其他属性，如消息TTL、死信交换机等
     * @return 创建的队列对象，如果创建失败则返回null
     */
    Queue createQueue(String queueName, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments);

    /**
     * 创建持久化非排他非自动删除队列（最常用的队列类型）
     * <p>
     * 创建一个持久化的、非排他的、非自动删除的队列。这是最常用的队列类型，
     * 适合大多数生产环境场景。
     * </p>
     *
     * @param queueName 队列名称
     * @return 创建的队列对象，如果创建失败则返回null
     */
    Queue createDurableQueue(String queueName);

    /**
     * 创建临时队列（非持久化、排他、自动删除）
     * <p>
     * 创建一个临时队列，适用于临时连接或测试场景。
     * 该队列在连接关闭时会自动删除。
     * </p>
     *
     * @param queueName 队列名称
     * @return 创建的队列对象，如果创建失败则返回null
     */
    Queue createTemporaryQueue(String queueName);

    /**
     * 删除队列
     * <p>
     * 删除指定名称的队列。如果队列不存在，则不会执行任何操作。
     * 如果队列存在且没有消费者使用，则会被删除。
     * </p>
     *
     * @param queueName 要删除的队列名称
     * @return 如果队列存在并被成功删除则返回true，否则返回false
     */
    boolean deleteQueue(String queueName);

    /**
     * 检查队列是否存在
     * <p>
     * 检查指定名称的队列是否存在。
     * </p>
     *
     * @param queueName 要检查的队列名称
     * @return 如果队列存在则返回true，否则返回false
     */
    boolean queueExists(String queueName);

    /**
     * 清空队列中的消息
     * <p>
     * 清空指定队列中的所有消息，但保留队列结构。
     * 这个操作是不可逆的，请谨慎使用。
     * </p>
     *
     * @param queueName 要清空的队列名称
     * @return 如果成功清空队列则返回true，否则返回false
     */
    boolean purgeQueue(String queueName);

    /**
     * 创建交换机
     * <p>
     * 动态创建一个交换机。如果交换机已存在且属性相同，则不会进行任何操作。
     * 如果交换机已存在但属性不同，则会根据RabbitMQ的规则处理（通常会抛出异常）。
     * </p>
     *
     * @param exchange 要创建的交换机对象
     * @return 如果成功创建交换机则返回true，否则返回false
     */
    boolean createExchange(AbstractExchange exchange);

    /**
     * 创建绑定关系
     * <p>
     * 将队列绑定到交换机，使用指定的路由键。
     * </p>
     *
     * @param queueName    队列名称
     * @param exchangeName 交换机名称
     * @param routingKey   路由键
     * @return 如果成功创建绑定关系则返回true，否则返回false
     */
    boolean bindQueueToExchange(String queueName, String exchangeName, String routingKey);

    /**
     * 移除绑定关系
     * <p>
     * 解除队列与交换机之间的绑定关系。
     * </p>
     *
     * @param queueName    队列名称
     * @param exchangeName 交换机名称
     * @param routingKey   路由键
     * @return 如果成功移除绑定关系则返回true，否则返回false
     */
    boolean unbindQueueFromExchange(String queueName, String exchangeName, String routingKey);

    /**
     * 一站式创建消息通道（队列、交换机和绑定关系）
     * <p>
     * 该方法提供了一站式服务，一次性完成队列创建、交换机创建和绑定关系建立，简化RabbitMQ资源管理流程。
     * 适用于需要快速建立完整消息通道的场景，无需分别调用多个方法。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 创建一个Direct类型的交换机和队列，并绑定
     * boolean success = setupMessageChannel(
     *     "my-queue", true, false, false, null,
     *     new DirectExchange("my-exchange", true, false),
     *     "my-routing-key"
     * );
     *
     * // 创建一个Topic类型的交换机和队列，并绑定
     * boolean success = setupMessageChannel(
     *     "my-topic-queue", true, false, false, null,
     *     new TopicExchange("my-topic-exchange", true, false),
     *     "my.routing.#"
     * );
     * </pre>
     * </p>
     *
     * @param queueName  队列名称
     * @param durable    队列是否持久化（true表示持久化到磁盘，false表示仅保存在内存中）
     * @param exclusive  队列是否排他（true表示仅限创建连接可见且连接关闭时自动删除）
     * @param autoDelete 队列是否自动删除（true表示当最后一个消费者断开连接时自动删除）
     * @param queueArgs  队列的其他属性，如消息TTL、死信交换机等
     * @param exchange   要创建的交换机对象，可以是DirectExchange、TopicExchange、FanoutExchange等
     * @param routingKey 绑定队列和交换机的路由键
     * @return 如果成功创建队列、交换机并建立绑定关系则返回true，否则返回false
     */
    boolean setupMessageChannel(String queueName, boolean durable, boolean exclusive, boolean autoDelete,
                                Map<String, Object> queueArgs, AbstractExchange exchange, String routingKey);

    /**
     * 一站式创建持久化消息通道（持久化队列、持久化交换机和绑定关系）
     * <p>
     * 该方法是{@link #setupMessageChannel}的便捷版本，创建持久化的队列和交换机，并建立绑定关系。
     * 适用于生产环境中需要消息可靠性保证的场景。
     * </p>
     *
     * @param queueName  队列名称
     * @param exchange   要创建的交换机对象，可以是DirectExchange、TopicExchange、FanoutExchange等
     * @param routingKey 绑定队列和交换机的路由键
     * @return 如果成功创建队列、交换机并建立绑定关系则返回true，否则返回false
     */
    boolean setupDurableMessageChannel(String queueName, AbstractExchange exchange, String routingKey);

    /**
     * 异步设置消息通道
     * <p>
     * 异步创建队列、交换机并绑定，适用于高并发场景。
     * 该方法会在专用线程池中执行，不会阻塞调用线程。
     * 线程池参数可通过配置文件调整，适应不同的负载场景。
     * </p>
     *
     * @param queueName  队列名称，不能为null或空
     * @param durable    是否持久化
     * @param exclusive  是否排他
     * @param autoDelete 是否自动删除
     * @param arguments  队列参数，可以为null
     * @param exchange   交换机，不能为null
     * @param routingKey 路由键，不能为null或空
     * @return CompletableFuture<Boolean> 异步操作结果，true表示成功，false表示失败
     * @throws IllegalArgumentException 如果必要参数为null或空
     */
    CompletableFuture<Boolean> setupMessageChannelAsync(String queueName, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments, AbstractExchange exchange, String routingKey);
}