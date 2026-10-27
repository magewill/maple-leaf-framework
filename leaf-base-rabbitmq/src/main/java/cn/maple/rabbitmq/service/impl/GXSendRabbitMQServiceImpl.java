package cn.maple.rabbitmq.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.core.framework.wrapper.mdc.GXMdcWrapperThreadPoolExecutor;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.exception.GXRabbitMQOperationException;
import cn.maple.rabbitmq.exception.GXRabbitMQOperationException.Operation;
import cn.maple.rabbitmq.service.GXSendRabbitMQService;
import cn.maple.rabbitmq.support.GXRabbitMQJsonSupport;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 发送与资源管理实现。
 *
 * <p>关键语义：</p>
 * <ul>
 *   <li>每次创建队列都触达 Broker，同队列并发声明通过单飞（single-flight）收敛为一次；</li>
 *   <li>发送前对调用方 {@link MessageProperties} 做深拷贝，发送链路不再改写调用方对象；</li>
 *   <li>参数非法抛 {@link GXBusinessException}，Broker 操作失败抛 {@link GXRabbitMQOperationException}，
 *       不再用 {@code null}/{@code false} 掩盖失败。</li>
 * </ul>
 */
@Service
@Slf4j
public class GXSendRabbitMQServiceImpl extends GXBusinessServiceImpl implements GXSendRabbitMQService {
    private static final int MAX_RESOURCE_NAME_LENGTH = 255;

    private static final long ASYNC_SHUTDOWN_TIMEOUT_SECONDS = 30L;

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final String TIMESTAMP_HEADER = "X-Timestamp";

    private static final String SOURCE_SERVICE_HEADER = "X-Source-Service";

    private static final String STEP_DECLARE_EXCHANGE = "DECLARE_EXCHANGE";

    private static final String STEP_DECLARE_QUEUE = "DECLARE_QUEUE";

    private static final String STEP_BIND = "BIND";

    private final RabbitTemplate rabbitTemplate;

    private final RabbitAdmin rabbitAdmin;

    private final Map<String, CompletableFuture<Queue>> inFlightDeclarations = new ConcurrentHashMap<>();

    @Value("${maple.framework.rabbitmq.thread-pool.core-size:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}")
    private int threadPoolCoreSize;

    @Value("${maple.framework.rabbitmq.thread-pool.max-size:#{T(java.lang.Runtime).getRuntime().availableProcessors() * 2}}")
    private int threadPoolMaxSize;

    @Value("${maple.framework.rabbitmq.thread-pool.queue-capacity:1000}")
    private int threadPoolQueueCapacity;

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    private volatile ExecutorService rabbitMqAsyncExecutor;

    public GXSendRabbitMQServiceImpl(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
    }

    @Override
    public Object sendNormalMessage(GXRabbitMQMessageReqDto messageReqDto) {
        if (messageReqDto == null) {
            throw new GXBusinessException("消息请求DTO不能为null");
        }
        if (messageReqDto.getExchange() == null) {
            throw new GXBusinessException("交换机名称不能为null（默认交换机请传空字符串）");
        }
        requireText(messageReqDto.getRoutingKey(), "路由键不能为空");
        if (messageReqDto.getData() == null) {
            throw new GXBusinessException("消息内容不能为null");
        }
        if (messageReqDto.getMessageProperties() == null) {
            throw new GXBusinessException("消息属性不能为null");
        }

        Dict data = messageReqDto.getData();
        String exchange = messageReqDto.getExchange();
        String routingKey = messageReqDto.getRoutingKey();
        String traceId = resolveTraceId();

        MessageProperties snapshot = snapshotProperties(messageReqDto.getMessageProperties(), traceId);
        Message message = new Message(JSONUtil.toJsonStr(data).getBytes(StandardCharsets.UTF_8), snapshot);

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, messageReqDto.getCorrelationData());
            log.debug("RabbitMQ message dispatched: exchange={}, routingKey={}, traceId={}, bodyLength={}",
                    exchange, routingKey, traceId, message.getBody().length);
        } catch (AmqpException e) {
            log.error("RabbitMQ send failed: exchange={}, routingKey={}, errorType={}, error={}",
                    exchange, routingKey, e.getClass().getName(), e.getMessage());
            throw e;
        }
        return null;
    }

    @Override
    public Queue createQueue(String queueName, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) {
        validateResourceName(queueName, "队列名称");
        Map<String, Object> immutableArguments = copyArguments(arguments);
        Queue queue = new Queue(queueName, durable, exclusive, autoDelete, immutableArguments);
        String key = queueName + "#" + fingerprint(durable, exclusive, autoDelete, immutableArguments);
        return declareOnce(key, queue);
    }

    @Override
    public Queue createDurableQueue(String queueName) {
        return createQueue(queueName, true, false, false, null);
    }

    /**
     * 声明非持久化、自动删除队列。
     *
     * <p>注意：排他队列绑定声明连接，共享 {@code RabbitAdmin} 声明后其他连接无法消费，
     * 因此本方法不声明排他队列，调用方若需要严格排他语义应自行持有连接声明。</p>
     */
    @Override
    public Queue createTemporaryQueue(String queueName) {
        return createQueue(queueName, false, false, true, null);
    }

    @Override
    public boolean deleteQueue(String queueName) {
        validateResourceName(queueName, "队列名称");
        try {
            boolean deleted = rabbitAdmin.deleteQueue(queueName);
            if (deleted) {
                log.debug("RabbitMQ queue deleted: name={}", queueName);
            } else {
                log.warn("RabbitMQ queue not found while deleting: name={}", queueName);
            }
            return deleted;
        } catch (AmqpException e) {
            log.error("RabbitMQ queue delete failed: name={}, errorType={}, error={}",
                    queueName, e.getClass().getName(), e.getMessage());
            throw new GXRabbitMQOperationException(Operation.DELETE_QUEUE, queueName, "删除队列失败", e);
        }
    }

    @Override
    public boolean queueExists(String queueName) {
        validateResourceName(queueName, "队列名称");
        try {
            Properties properties = rabbitAdmin.getQueueProperties(queueName);
            return properties != null && !properties.isEmpty();
        } catch (AmqpException e) {
            log.error("RabbitMQ queue property query failed: name={}, errorType={}, error={}",
                    queueName, e.getClass().getName(), e.getMessage());
            throw new GXRabbitMQOperationException(Operation.QUERY_QUEUE, queueName, "查询队列状态失败", e);
        }
    }

    @Override
    public boolean purgeQueue(String queueName) {
        validateResourceName(queueName, "队列名称");
        try {
            int purged = rabbitAdmin.purgeQueue(queueName);
            log.debug("RabbitMQ queue purged: name={}, purged={}", queueName, purged);
            return true;
        } catch (AmqpException e) {
            log.error("RabbitMQ queue purge failed: name={}, errorType={}, error={}",
                    queueName, e.getClass().getName(), e.getMessage());
            throw new GXRabbitMQOperationException(Operation.PURGE_QUEUE, queueName, "清空队列失败", e);
        }
    }

    @Override
    public boolean createExchange(AbstractExchange exchange) {
        validateExchange(exchange);
        try {
            rabbitAdmin.declareExchange(exchange);
            log.debug("RabbitMQ exchange declared: name={}, type={}", exchange.getName(), exchange.getType());
            return true;
        } catch (AmqpException e) {
            log.error("RabbitMQ exchange declaration failed: name={}, type={}, errorType={}, error={}",
                    exchange.getName(), exchange.getType(), e.getClass().getName(), e.getMessage());
            throw new GXRabbitMQOperationException(Operation.DECLARE_EXCHANGE, exchange.getName(), "交换机声明失败", e);
        }
    }

    @Override
    public boolean bindQueueToExchange(String queueName, String exchangeName, String routingKey) {
        validateResourceName(queueName, "队列名称");
        validateResourceName(exchangeName, "交换机名称");
        requireText(routingKey, "路由键不能为空");
        try {
            Binding binding = new Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, routingKey, null);
            rabbitAdmin.declareBinding(binding);
            log.debug("RabbitMQ binding declared: queue={}, exchange={}, routingKey={}",
                    queueName, exchangeName, routingKey);
            return true;
        } catch (AmqpException e) {
            log.error("RabbitMQ binding failed: queue={}, exchange={}, routingKey={}, errorType={}, error={}",
                    queueName, exchangeName, routingKey, e.getClass().getName(), e.getMessage());
            throw new GXRabbitMQOperationException(Operation.BIND, queueName, "绑定队列失败", e);
        }
    }

    @Override
    public boolean unbindQueueFromExchange(String queueName, String exchangeName, String routingKey) {
        validateResourceName(queueName, "队列名称");
        validateResourceName(exchangeName, "交换机名称");
        requireText(routingKey, "路由键不能为空");
        try {
            Binding binding = new Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, routingKey, null);
            rabbitAdmin.removeBinding(binding);
            log.debug("RabbitMQ binding removed: queue={}, exchange={}, routingKey={}",
                    queueName, exchangeName, routingKey);
            return true;
        } catch (AmqpException e) {
            log.error("RabbitMQ unbind failed: queue={}, exchange={}, routingKey={}, errorType={}, error={}",
                    queueName, exchangeName, routingKey, e.getClass().getName(), e.getMessage());
            throw new GXRabbitMQOperationException(Operation.UNBIND, queueName, "解除绑定失败", e);
        }
    }

    @Override
    public boolean setupMessageChannel(String queueName, boolean durable, boolean exclusive, boolean autoDelete,
                                       Map<String, Object> queueArgs, AbstractExchange exchange, String routingKey) {
        validateResourceName(queueName, "队列名称");
        validateExchange(exchange);
        requireText(routingKey, "路由键不能为空");

        List<String> completedSteps = new ArrayList<>(3);
        try {
            createExchange(exchange);
            completedSteps.add(STEP_DECLARE_EXCHANGE);

            createQueue(queueName, durable, exclusive, autoDelete, queueArgs);
            completedSteps.add(STEP_DECLARE_QUEUE);

            bindQueueToExchange(queueName, exchange.getName(), routingKey);
            completedSteps.add(STEP_BIND);

            log.debug("RabbitMQ channel ready: queue={}, exchange={}, type={}, routingKey={}",
                    queueName, exchange.getName(), exchange.getType(), routingKey);
            return true;
        } catch (GXRabbitMQOperationException e) {
            throw e.withProgress(completedSteps, e.getOperation().name());
        }
    }

    @Override
    public boolean setupDurableMessageChannel(String queueName, AbstractExchange exchange, String routingKey) {
        return setupMessageChannel(queueName, true, false, false, null, exchange, routingKey);
    }

    @Override
    public CompletableFuture<Boolean> setupMessageChannelAsync(String queueName, boolean durable, boolean exclusive,
                                                               boolean autoDelete, Map<String, Object> arguments,
                                                               AbstractExchange exchange, String routingKey) {
        validateResourceName(queueName, "队列名称");
        validateExchange(exchange);
        requireText(routingKey, "路由键不能为空");

        try {
            return CompletableFuture.supplyAsync(
                    () -> setupMessageChannel(queueName, durable, exclusive, autoDelete, arguments, exchange, routingKey),
                    getRabbitMqAsyncExecutor());
        } catch (RejectedExecutionException e) {
            log.warn("RabbitMQ async channel setup rejected: queue={}, error={}", queueName, e.getMessage());
            return CompletableFuture.failedFuture(new GXRabbitMQOperationException(Operation.DECLARE_QUEUE,
                    queueName, "异步创建消息通道被拒绝", e));
        }
    }

    @PreDestroy
    public void shutdownAsyncExecutor() {
        ExecutorService executor = this.rabbitMqAsyncExecutor;
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(ASYNC_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("RabbitMQ async executor forced shutdown: pendingTasksDiscarded=true");
            } else {
                log.info("RabbitMQ async executor stopped");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            log.warn("RabbitMQ async executor shutdown interrupted");
        } finally {
            this.rabbitMqAsyncExecutor = null;
        }
    }

    private MessageProperties snapshotProperties(MessageProperties source, String traceId) {
        MessageProperties snapshot;
        try {
            snapshot = MessagePropertiesBuilder.fromClonedProperties(source).build();
        } catch (RuntimeException e) {
            throw new GXBusinessException("消息属性复制失败", e);
        }
        snapshot.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        snapshot.setContentEncoding(StandardCharsets.UTF_8.name());
        snapshot.setHeader(TRACE_ID_HEADER, traceId);
        snapshot.setTimestamp(DateUtil.date());
        snapshot.setHeader(TIMESTAMP_HEADER, System.currentTimeMillis());
        snapshot.setHeader(SOURCE_SERVICE_HEADER, StringUtils.hasText(applicationName) ? applicationName : "unknown");
        snapshot.setHeader(GXRabbitMQJsonSupport.TYPE_ID_HEADER, GXRabbitMQJsonSupport.DICT_TYPE_ID);
        if (snapshot.getMessageId() == null) {
            snapshot.setMessageId(traceId);
        }
        return snapshot;
    }

    private String resolveTraceId() {
        String traceId = GXTraceIdContextUtils.getNullableTraceId();
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return GXTraceIdContextUtils.generateTraceId();
    }

    private Queue declareOnce(String key, Queue queue) {
        CompletableFuture<Queue> mine = new CompletableFuture<>();
        CompletableFuture<Queue> existing = inFlightDeclarations.putIfAbsent(key, mine);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                throw unwrap(e);
            }
        }
        try {
            Queue declared = declareQueueInternal(queue);
            mine.complete(declared);
            return declared;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlightDeclarations.remove(key, mine);
        }
    }

    private Queue declareQueueInternal(Queue queue) {
        try {
            rabbitAdmin.declareQueue(queue);
            log.debug("RabbitMQ queue declared: name={}, durable={}, exclusive={}, autoDelete={}",
                    queue.getName(), queue.isDurable(), queue.isExclusive(), queue.isAutoDelete());
            return queue;
        } catch (AmqpException e) {
            log.error("RabbitMQ queue declaration failed: name={}, durable={}, exclusive={}, errorType={}, error={}",
                    queue.getName(), queue.isDurable(), queue.isExclusive(), e.getClass().getName(), e.getMessage());
            throw new GXRabbitMQOperationException(Operation.DECLARE_QUEUE, queue.getName(), "队列声明失败", e);
        }
    }

    private ExecutorService getRabbitMqAsyncExecutor() {
        ExecutorService executor = this.rabbitMqAsyncExecutor;
        if (executor == null) {
            synchronized (this) {
                executor = this.rabbitMqAsyncExecutor;
                if (executor == null) {
                    executor = createAsyncExecutor();
                    this.rabbitMqAsyncExecutor = executor;
                }
            }
        }
        return executor;
    }

    private ExecutorService createAsyncExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        int configuredCore = threadPoolCoreSize > 0 ? threadPoolCoreSize : processors;
        int configuredMax = threadPoolMaxSize > 0 ? threadPoolMaxSize : Math.max(configuredCore, processors * 2);
        int core = Math.max(1, Math.min(configuredCore, configuredMax));
        int max = Math.max(core, configuredMax);

        ThreadPoolExecutor executor = new GXMdcWrapperThreadPoolExecutor(
                core,
                max,
                60L,
                TimeUnit.SECONDS,
                threadPoolQueueCapacity > 0
                        ? new LinkedBlockingQueue<>(threadPoolQueueCapacity)
                        : new SynchronousQueue<>(),
                Thread.ofPlatform().name("rabbitmq-async-worker-", 1).daemon(true).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);

        log.info("RabbitMQ async executor initialized: core={}, max={}, queueCapacity={}",
                core, max, threadPoolQueueCapacity);
        return executor;
    }

    private static RuntimeException unwrap(CompletionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return e;
    }

    private static Map<String, Object> copyArguments(Map<String, Object> arguments) {
        if (arguments == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(arguments.size());
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!StringUtils.hasText(entry.getKey())) {
                throw new GXBusinessException("队列参数名不能为空");
            }
            if (entry.getValue() == null) {
                throw new GXBusinessException("队列参数值不能为null: " + entry.getKey());
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String fingerprint(boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) {
        StringBuilder builder = new StringBuilder(64)
                .append(durable).append('|').append(exclusive).append('|').append(autoDelete);
        if (arguments != null) {
            for (Map.Entry<String, Object> entry : new TreeMap<>(arguments).entrySet()) {
                builder.append('|').append(entry.getKey()).append('=').append(entry.getValue());
            }
        }
        return builder.toString();
    }

    private static void validateExchange(AbstractExchange exchange) {
        if (exchange == null) {
            throw new GXBusinessException("交换机不能为null");
        }
        validateResourceName(exchange.getName(), "交换机名称");
    }

    private static void validateResourceName(String name, String field) {
        requireText(name, field + "不能为空");
        if (name.length() > MAX_RESOURCE_NAME_LENGTH) {
            throw new GXBusinessException(field + "长度不能超过" + MAX_RESOURCE_NAME_LENGTH);
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) {
                throw new GXBusinessException(field + "不能包含控制字符");
            }
        }
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new GXBusinessException(message);
        }
    }

}
