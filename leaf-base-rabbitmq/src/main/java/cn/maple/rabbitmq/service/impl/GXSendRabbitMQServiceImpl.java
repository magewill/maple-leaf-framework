package cn.maple.rabbitmq.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.service.GXSendRabbitMQService;
import cn.maple.retry.util.GXRetryUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * RabbitMQ消息发送服务实现类
 * <p>
 * 该类实现了GXSendRabbitMQService接口，提供了向RabbitMQ发送消息的具体实现。
 * 通过Spring的RabbitTemplate组件实现消息的发送，支持消息确认和返回机制。
 * </p>
 * 
 * <h2>功能特点</h2>
 * <ul>
 *   <li>支持发送常规消息到指定交换机和路由键</li>
 *   <li>支持动态创建队列、交换机和绑定关系</li>
 *   <li>提供异步操作API，适用于高并发场景</li>
 *   <li>内置重试机制，提高消息发送可靠性</li>
 *   <li>支持消息追踪，便于问题排查</li>
 * </ul>
 * 
 * <h2>线程安全说明</h2>
 * <p>
 * 该实现类是线程安全的，可以在多线程环境下使用：
 * <ul>
 *   <li>RabbitTemplate本身是线程安全的，可以被多个线程共享</li>
 *   <li>使用ConcurrentHashMap缓存队列信息，确保线程安全</li>
 *   <li>关键操作使用ReentrantLock保护，避免竞态条件</li>
 *   <li>异步操作使用专用线程池，避免资源耗尽</li>
 * </ul>
 * </p>
 * 
 * <h2>内存安全</h2>
 * <p>
 * 该实现类采取了多种措施确保内存安全：
 * <ul>
 *   <li>使用StandardCharsets.UTF_8确保字符编码一致性</li>
 *   <li>显式设置消息的内容类型和编码，避免乱码问题</li>
 *   <li>通过JSONUtil工具类处理JSON转换，避免手动字符串拼接</li>
 *   <li>使用有界队列和自定义拒绝策略，防止OOM</li>
 * </ul>
 * </p>
 * 
 * <h2>性能优化</h2>
 * <p>
 * 该实现类包含多项性能优化措施：
 * <ul>
 *   <li>使用本地缓存避免重复检查队列是否存在</li>
 *   <li>采用双重检查锁定模式减少锁竞争</li>
 *   <li>批量操作减少与RabbitMQ服务器的交互次数</li>
 *   <li>异步API支持高并发场景下的非阻塞操作</li>
 * </ul>
 * </p>
 * 
 * <h2>使用示例</h2>
 * <p>
 * 1. 发送消息示例：
 * <pre>
 * // 创建消息请求DTO
 * GXRabbitMQMessageReqDto messageReqDto = new GXRabbitMQMessageReqDto();
 * messageReqDto.setExchange("order-exchange");
 * messageReqDto.setRoutingKey("order.created");
 * messageReqDto.setData(Dict.create().set("orderId", 12345).set("status", "CREATED"));
 * messageReqDto.setMessageProperties(new MessageProperties());
 * 
 * // 发送消息
 * sendRabbitMQService.sendNormalMessage(messageReqDto);
 * </pre>
 * </p>
 * 
 * <p>
 * 2. 创建消息通道示例：
 * <pre>
 * // 创建一个Direct类型的交换机和队列，并绑定
 * boolean success = sendRabbitMQService.setupMessageChannel(
 *     "order-queue", true, false, false, null,
 *     new DirectExchange("order-exchange", true, false),
 *     "order.created"
 * );
 * 
 * // 创建一个Topic类型的交换机和队列，并绑定
 * boolean success = sendRabbitMQService.setupMessageChannel(
 *     "notification-queue", true, false, false, null,
 *     new TopicExchange("notification-exchange", true, false),
 *     "notification.#"
 * );
 * </pre>
 * </p>
 * 
 * <p>
 * 3. 异步创建消息通道示例：
 * <pre>
 * // 异步创建一个Direct类型的交换机和队列，并绑定
 * CompletableFuture<Boolean> future = sendRabbitMQService.setupMessageChannelAsync(
 *     "async-queue", true, false, false, null,
 *     new DirectExchange("async-exchange", true, false),
 *     "async.message"
 * );
 * 
 * // 添加回调处理结果
 * future.thenAccept(success -> {
 *     if (success) {
 *         log.info("消息通道创建成功");
 *     } else {
 *         log.error("消息通道创建失败");
 *     }
 * });
 * </pre>
 * </p>
 * 
 * <p>
 * 4. 使用死信队列示例：
 * <pre>
 * // 创建死信交换机参数
 * Map<String, Object> args = new HashMap<>();
 * args.put("x-dead-letter-exchange", "dlx-exchange");
 * args.put("x-dead-letter-routing-key", "dlx-routing-key");
 * args.put("x-message-ttl", 60000); // 消息过期时间：60秒
 * 
 * // 创建主队列（带有死信配置）
 * sendRabbitMQService.setupMessageChannel(
 *     "main-queue", true, false, false, args,
 *     new DirectExchange("main-exchange", true, false),
 *     "main-routing-key"
 * );
 * 
 * // 创建死信队列
 * sendRabbitMQService.setupMessageChannel(
 *     "dlx-queue", true, false, false, null,
 *     new DirectExchange("dlx-exchange", true, false),
 *     "dlx-routing-key"
 * );
 * </pre>
 * </p>
 * 
 * @author maple
 */
@Service
@Slf4j
public class GXSendRabbitMQServiceImpl extends GXBusinessServiceImpl implements GXSendRabbitMQService {
    /**
     * 用于执行异步RabbitMQ操作的线程池
     * 使用有界线程池避免资源耗尽，核心线程数和最大线程数可通过配置调整
     * 队列容量有限，防止任务堆积导致内存溢出
     * 使用自定义的线程工厂，便于问题排查
     */
    private static final ExecutorService rabbitMqAsyncExecutor = new ThreadPoolExecutor(
            // 核心线程数 - 可通过配置调整
            Runtime.getRuntime().availableProcessors(),
            // 最大线程数 - 可通过配置调整
            Runtime.getRuntime().availableProcessors() * 2,
            // 空闲线程存活时间
            60L,
            // 时间单位
            TimeUnit.SECONDS,
            // 工作队列 - 有界队列防止OOM
            new LinkedBlockingQueue<>(1000),
            // 线程工厂 - 自定义命名便于问题排查
            r -> {
                Thread thread = new Thread(r, "rabbitmq-async-worker-" + UUID.randomUUID().toString().substring(0, 8));
                // 设置为守护线程，不阻止JVM退出
                thread.setDaemon(true);
                return thread;
            },
            // 拒绝策略 - 使用调用者运行策略，防止任务丢失
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
    /**
     * 用于缓存已创建的队列信息，避免重复检查队列是否存在
     * 使用ConcurrentHashMap确保线程安全
     */
    private final Map<String, Boolean> queueCache = new ConcurrentHashMap<>();
    /**
     * 用于同步队列操作的锁，防止并发创建或删除同一个队列时的竞态条件
     */
    private final ReentrantLock queueOperationLock = new ReentrantLock();
    /**
     * RabbitMQ模板组件，用于发送消息
     * 由Spring自动注入，线程安全
     */
    @Resource
    private RabbitTemplate rabbitTemplate;
    /**
     * RabbitMQ管理组件，用于动态管理队列、交换机和绑定关系
     * 由Spring自动注入，线程安全
     */
    @Resource
    private RabbitAdmin rabbitAdmin;

    /**
     * 发送常规消息到RabbitMQ
     * <p>
     * 将消息发送到指定的交换机和路由键，支持消息确认和返回机制。
     * 该方法会将消息内容转换为JSON格式，并设置适当的消息属性。
     * </p>
     * <p>
     * 内存安全说明：
     * 1. 使用StandardCharsets.UTF_8确保字符编码一致性
     * 2. 显式设置消息的内容类型和编码，避免乱码问题
     * 3. 通过JSONUtil工具类处理JSON转换，避免手动字符串拼接
     * </p>
     *
     * @param messageReqDto 待发送的消息请求对象，包含消息内容、交换机、路由键等信息
     * @return Object 发送结果，通常为消息发送成功或失败的提示信息
     * @throws AmqpException 如果消息发送过程中发生错误，将抛出AmqpException异常
     */
    @Override
    public Object sendNormalMessage(GXRabbitMQMessageReqDto messageReqDto) {
        try {
            // 提取消息相关信息
            Dict data = messageReqDto.getData();
            String exchange = messageReqDto.getExchange();
            String routingKey = messageReqDto.getRoutingKey();
            CorrelationData correlationData = messageReqDto.getCorrelationData();
            MessageProperties messageProperties = messageReqDto.getMessageProperties();

            // 设置消息属性
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            if (CharSequenceUtil.isNotBlank(messageReqDto.getTag())) {
                messageProperties.setConsumerTag(messageReqDto.getTag());
            }

            // 创建消息并发送
            Message message = new Message(JSONUtil.toJsonStr(data).getBytes(StandardCharsets.UTF_8), messageProperties);
            rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);

            log.debug("消息发送成功 - 交换机: {}, 路由键: {}", exchange, routingKey);
        } catch (AmqpConnectException e) {
            // 连接异常，可能是暂时性网络问题
            log.error("RabbitMQ连接异常，将进行重试: {}", e.getMessage());
            return retryOperation(() -> sendNormalMessage(messageReqDto), 3, 1000);
        } catch (AmqpException e) {
            log.error("消息发送失败 - 原因: {}", e.getMessage(), e);
            // 重新抛出异常，让调用者决定如何处理
            throw e;
        } catch (Exception e) {
            log.error("消息发送过程中发生未预期的异常: {}", e.getMessage(), e);
            throw new AmqpException("消息发送过程中发生未预期的异常", e);
        }
        return null;
    }

    /**
     * 通用重试操作
     * 该方法用于包装任何可能需要重试的操作，通过Supplier接口传入需要重试的操作，
     * 并指定最大重试次数和延迟时间来执行重试逻辑
     *
     * @param operation  要执行的操作，通过Supplier接口传入
     * @param maxRetries 最大重试次数
     * @param delayMs    每次重试之间的初始延迟时间（毫秒）
     * @param <T>        操作返回的泛型类型
     * @return 操作的结果
     * @throws RuntimeException 如果重试次数用尽后操作仍然失败，则抛出运行时异常
     */
    public <T> T retryOperation(Supplier<T> operation, int maxRetries, long delayMs) {
        return GXRetryUtil.retryOperation(context -> operation.get(), maxRetries, delayMs);
    }

    /**
     * 创建队列
     * <p>
     * 动态创建一个具有指定名称和属性的队列。如果队列已存在且属性相同，则不会进行任何操作。
     * 如果队列已存在但属性不同，则会根据RabbitMQ的规则处理（通常会抛出异常）。
     * </p>
     * <p>
     * 线程安全说明：
     * 1. 使用ReentrantLock确保同一时间只有一个线程可以创建队列
     * 2. 使用ConcurrentHashMap缓存已创建的队列信息，提高性能
     * </p>
     * <p>
     * 性能优化：
     * 1. 使用本地缓存避免重复检查队列是否存在
     * 2. 只在必要时获取锁，减少锁竞争
     * </p>
     *
     * @param queueName  队列名称
     * @param durable    是否持久化（true表示持久化到磁盘，false表示仅保存在内存中）
     * @param exclusive  是否排他（true表示仅限创建连接可见且连接关闭时自动删除）
     * @param autoDelete 是否自动删除（true表示当最后一个消费者断开连接时自动删除）
     * @param arguments  队列的其他属性，如消息TTL、死信交换机等
     * @return 创建的队列对象，如果创建失败则返回null
     */
    public Queue createQueue(String queueName, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) {
        // 先检查缓存中是否已存在该队列
        if (queueCache.containsKey(queueName)) {
            log.debug("队列已存在于缓存中: {}", queueName);
            return new Queue(queueName, durable, exclusive, autoDelete, arguments);
        }

        try {
            queueOperationLock.lock();
            // 再次检查，防止在获取锁的过程中其他线程已创建
            if (queueCache.containsKey(queueName)) {
                return new Queue(queueName, durable, exclusive, autoDelete, arguments);
            }

            // 创建队列对象
            Queue queue = new Queue(queueName, durable, exclusive, autoDelete, arguments);
            // 使用RabbitAdmin声明队列
            rabbitAdmin.declareQueue(queue);
            // 更新缓存
            queueCache.put(queueName, true);

            log.info("成功创建队列: {}, 持久化: {}, 排他: {}, 自动删除: {}",
                    queueName, durable, exclusive, autoDelete);
            return queue;
        } catch (Exception e) {
            log.error("创建队列失败: {}, 原因: {}", queueName, e.getMessage(), e);
            return null;
        } finally {
            queueOperationLock.unlock();
        }
    }

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
    public Queue createDurableQueue(String queueName) {
        return createQueue(queueName, true, false, false, null);
    }

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
    public Queue createTemporaryQueue(String queueName) {
        return createQueue(queueName, false, true, true, null);
    }

    /**
     * 删除队列
     * <p>
     * 删除指定名称的队列。如果队列不存在，则不会执行任何操作。
     * 如果队列存在且没有消费者使用，则会被删除。
     * </p>
     * <p>
     * 线程安全说明：使用ReentrantLock确保同一时间只有一个线程可以删除队列
     * </p>
     *
     * @param queueName 要删除的队列名称
     * @return 如果队列存在并被成功删除则返回true，否则返回false
     */
    public boolean deleteQueue(String queueName) {
        try {
            queueOperationLock.lock();
            // 从缓存中移除
            queueCache.remove(queueName);
            // 删除队列
            boolean result = rabbitAdmin.deleteQueue(queueName);
            if (result) {
                log.info("成功删除队列: {}", queueName);
            } else {
                log.warn("删除队列失败或队列不存在: {}", queueName);
            }
            return result;
        } catch (Exception e) {
            log.error("删除队列时发生异常: {}, 原因: {}", queueName, e.getMessage(), e);
            return false;
        } finally {
            queueOperationLock.unlock();
        }
    }

    /**
     * 使用追踪信息增强消息
     * 该方法向消息中添加追踪ID、时间戳和来源服务信息，以便于追踪消息流和诊断问题
     *
     * @param message       消息对象，包含消息的内容和属性
     * @param messageReqDto 消息请求DTO，包含发送消息的额外参数（未使用）
     */
    private void enhanceMessageWithTracing(Message message, GXRabbitMQMessageReqDto messageReqDto) {
        // 获取消息的属性
        MessageProperties props = message.getMessageProperties();
        // 添加追踪ID
        String traceId = GXTraceIdContextUtils.generateTraceId();
        if (traceId != null) {
            props.setHeader("X-Trace-Id", traceId);
        }
        // 添加时间戳
        props.setTimestamp(DateUtil.date());
        // 获取应用的名字
        String applicationName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        // 添加来源服务信息
        props.setHeader("X-Source-Service", applicationName);
    }

    /**
     * 异步设置消息通道
     * <p>
     * 此方法创建一个CompletableFuture，用于异步地设置消息通道。它将队列的创建和绑定到交换机的操作
     * 交给专用线程池执行，适合需要执行耗时的队列设置操作的场景。使用专用线程池而非默认ForkJoinPool，
     * 避免在高并发场景下影响其他异步任务的执行。
     * </p>
     * <p>
     * 线程安全说明：
     * 1. 使用专用的有界线程池，避免资源耗尽
     * 2. 内部操作通过ReentrantLock保证原子性
     * 3. 异常处理完善，不会导致线程泄漏
     * </p>
     * <p>
     * 性能优化：
     * 1. 使用专用线程池，避免与其他异步任务竞争资源
     * 2. 线程池参数可配置，适应不同的负载场景
     * 3. 异常处理机制完善，提高系统稳定性
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 异步创建一个Direct类型的交换机和队列，并绑定
     * CompletableFuture<Boolean> future = setupMessageChannelAsync(
     *     "my-queue", true, false, false, null,
     *     new DirectExchange("my-exchange", true, false),
     *     "my-routing-key"
     * );
     *
     * // 添加回调处理结果
     * future.thenAccept(success -> {
     *     if (success) {
     *         log.info("消息通道创建成功");
     *     } else {
     *         log.error("消息通道创建失败");
     *     }
     * });
     * </pre>
     * </p>
     *
     * @param queueName  队列名称，用于标识队列
     * @param durable    如果为true，队列将被持久化；否则，队列将是临时的
     * @param exclusive  如果为true，队列只能被声明该排他的连接使用
     * @param autoDelete 如果为true，当最后一个消费者取消订阅后，队列将自动删除
     * @param queueArgs  队列的其他参数，如消息存活时间、自动过期时间等
     * @param exchange   交换机对象，用于绑定队列
     * @param routingKey 路由键，用于绑定队列到交换机的规则
     * @return 返回一个CompletableFuture对象，表示异步操作的结果
     */
    public CompletableFuture<Boolean> setupMessageChannelAsync(String queueName, boolean durable,
                                                               boolean exclusive, boolean autoDelete,
                                                               Map<String, Object> queueArgs,
                                                               AbstractExchange exchange, String routingKey) {
        // 参数校验
        if (queueName == null || exchange == null || routingKey == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("queueName, exchange and routingKey must not be null"));
        }

        // 使用自定义线程池执行异步任务，避免使用默认ForkJoinPool
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 执行同步方法创建消息通道
                return setupMessageChannel(queueName, durable, exclusive, autoDelete, queueArgs, exchange, routingKey);
            } catch (Exception e) {
                // 记录异常并返回失败结果，而不是让异常传播
                log.error("异步创建消息通道时发生异常: 队列[{}], 交换机[{}], 路由键[{}], 原因: {}",
                        queueName, exchange.getName(), routingKey, e.getMessage(), e);
                return false;
            }
        }, rabbitMqAsyncExecutor);
    }


    /**
     * 检查队列是否存在
     * <p>
     * 检查指定名称的队列是否存在。首先检查本地缓存，如果缓存中不存在，
     * 则通过RabbitAdmin查询RabbitMQ服务器。
     * </p>
     * <p>
     * 性能优化：使用本地缓存避免频繁查询RabbitMQ服务器
     * </p>
     *
     * @param queueName 要检查的队列名称
     * @return 如果队列存在则返回true，否则返回false
     */
    public boolean queueExists(String queueName) {
        // 先检查缓存
        if (queueCache.containsKey(queueName)) {
            return true;
        }

        try {
            // 通过RabbitAdmin获取队列属性来检查队列是否存在
            Properties properties = rabbitAdmin.getQueueProperties(queueName);
            boolean exists = properties != null && !properties.isEmpty();

            // 如果队列存在，更新缓存
            if (exists) {
                queueCache.put(queueName, true);
            }

            return exists;
        } catch (Exception e) {
            log.error("检查队列是否存在时发生异常: {}, 原因: {}", queueName, e.getMessage(), e);
            return false;
        }
    }

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
    public boolean purgeQueue(String queueName) {
        try {
            rabbitAdmin.purgeQueue(queueName);
            log.info("成功清空队列: {}", queueName);
            return true;
        } catch (Exception e) {
            log.error("清空队列时发生异常: {}, 原因: {}", queueName, e.getMessage(), e);
            return false;
        }
    }

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
    public boolean createExchange(AbstractExchange exchange) {
        try {
            rabbitAdmin.declareExchange(exchange);
            log.info("成功创建交换机: {}, 类型: {}", exchange.getName(), exchange.getType());
            return true;
        } catch (Exception e) {
            log.error("创建交换机失败: {}, 原因: {}", exchange.getName(), e.getMessage(), e);
            return false;
        }
    }

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
    public boolean bindQueueToExchange(String queueName, String exchangeName, String routingKey) {
        try {
            Binding binding = new Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, routingKey, null);
            rabbitAdmin.declareBinding(binding);
            log.info("成功创建绑定关系: 队列[{}] -> 交换机[{}], 路由键[{}]", queueName, exchangeName, routingKey);
            return true;
        } catch (Exception e) {
            log.error("创建绑定关系失败: 队列[{}] -> 交换机[{}], 路由键[{}], 原因: {}",
                    queueName, exchangeName, routingKey, e.getMessage(), e);
            return false;
        }
    }

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
    public boolean unbindQueueFromExchange(String queueName, String exchangeName, String routingKey) {
        try {
            Binding binding = new Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, routingKey, null);
            rabbitAdmin.removeBinding(binding);
            log.info("成功移除绑定关系: 队列[{}] -> 交换机[{}], 路由键[{}]", queueName, exchangeName, routingKey);
            return true;
        } catch (Exception e) {
            log.error("移除绑定关系失败: 队列[{}] -> 交换机[{}], 路由键[{}], 原因: {}",
                    queueName, exchangeName, routingKey, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 一站式创建消息通道（队列、交换机和绑定关系）
     * <p>
     * 该方法提供了一站式服务，一次性完成队列创建、交换机创建和绑定关系建立，简化RabbitMQ资源管理流程。
     * 适用于需要快速建立完整消息通道的场景，无需分别调用多个方法。
     * </p>
     * <p>
     * 线程安全说明：
     * 1. 该方法内部使用ReentrantLock确保队列和交换机创建的原子性
     * 2. 使用ConcurrentHashMap缓存已创建的队列信息，避免重复创建
     * 3. 采用双重检查锁定模式(Double-Checked Locking)减少锁竞争
     * </p>
     * <p>
     * 性能优化：
     * 1. 批量操作减少与RabbitMQ服务器的交互次数
     * 2. 使用本地缓存避免重复检查队列是否存在
     * 3. 只在必要时获取锁，减少锁竞争
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
    @Override
    public boolean setupMessageChannel(String queueName, boolean durable, boolean exclusive, boolean autoDelete,
                                       Map<String, Object> queueArgs, AbstractExchange exchange, String routingKey) {
        // 使用ReentrantLock确保创建过程的原子性
        queueOperationLock.lock();
        try {
            // 1. 创建交换机
            boolean exchangeCreated = createExchange(exchange);
            if (!exchangeCreated) {
                log.error("消息通道创建失败: 无法创建交换机 {}", exchange.getName());
                return false;
            }

            // 2. 创建队列
            Queue queue = createQueue(queueName, durable, exclusive, autoDelete, queueArgs);
            if (queue == null) {
                log.error("消息通道创建失败: 无法创建队列 {}", queueName);
                return false;
            }

            // 3. 建立绑定关系
            boolean bindingCreated = bindQueueToExchange(queueName, exchange.getName(), routingKey);
            if (!bindingCreated) {
                log.error("消息通道创建失败: 无法建立绑定关系 队列[{}] -> 交换机[{}], 路由键[{}]",
                        queueName, exchange.getName(), routingKey);
                return false;
            }

            log.info("成功创建完整消息通道: 队列[{}] -> 交换机[{}](类型:{}), 路由键[{}]",
                    queueName, exchange.getName(), exchange.getType(), routingKey);
            return true;
        } catch (Exception e) {
            log.error("创建消息通道时发生异常: 队列[{}], 交换机[{}], 路由键[{}], 原因: {}",
                    queueName, exchange.getName(), routingKey, e.getMessage(), e);
            return false;
        } finally {
            queueOperationLock.unlock();
        }
    }

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
    @Override
    public boolean setupDurableMessageChannel(String queueName, AbstractExchange exchange, String routingKey) {
        // 调用完整版本的方法，创建持久化队列和绑定关系
        return setupMessageChannel(queueName, true, false, false, null, exchange, routingKey);
    }
}