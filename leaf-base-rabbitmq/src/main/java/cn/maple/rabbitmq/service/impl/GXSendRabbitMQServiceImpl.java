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
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class GXSendRabbitMQServiceImpl extends GXBusinessServiceImpl implements GXSendRabbitMQService {
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    private final Map<String, Boolean> queueCache = new ConcurrentHashMap<>();

    private final ReentrantLock queueOperationLock = new ReentrantLock();

    @Value("${maple.framework.rabbitmq.thread-pool.core-size:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}")
    private int threadPoolCoreSize;

    @Value("${maple.framework.rabbitmq.thread-pool.max-size:#{T(java.lang.Runtime).getRuntime().availableProcessors() * 2}}")
    private int threadPoolMaxSize;

    @Value("${maple.framework.rabbitmq.thread-pool.queue-capacity:1000}")
    private int threadPoolQueueCapacity;

    private volatile ExecutorService rabbitMqAsyncExecutor;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RabbitAdmin rabbitAdmin;

    private ExecutorService getRabbitMqAsyncExecutor() {
        if (rabbitMqAsyncExecutor == null) {
            synchronized (this) {
                if (rabbitMqAsyncExecutor == null) {
                    rabbitMqAsyncExecutor = new ThreadPoolExecutor(
                            threadPoolCoreSize,
                            threadPoolMaxSize,
                            60L,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(threadPoolQueueCapacity),
                            r -> {
                                Thread thread = new Thread(r, "rabbitmq-async-worker-" + THREAD_COUNTER.getAndIncrement());
                                thread.setDaemon(true);
                                return thread;
                            },
                            new ThreadPoolExecutor.CallerRunsPolicy()
                    );
                    log.info("RabbitMQ异步操作线程池已初始化，核心线程数: {}, 最大线程数: {}, 队列容量: {}",
                            threadPoolCoreSize, threadPoolMaxSize, threadPoolQueueCapacity);
                }
            }
        }
        return rabbitMqAsyncExecutor;
    }

    @Override
    public Object sendNormalMessage(GXRabbitMQMessageReqDto messageReqDto) {
        Objects.requireNonNull(messageReqDto, "消息请求DTO不能为null");
        Objects.requireNonNull(messageReqDto.getExchange(), "交换机名称不能为null");
        Objects.requireNonNull(messageReqDto.getRoutingKey(), "路由键不能为null");
        Objects.requireNonNull(messageReqDto.getData(), "消息内容不能为null");
        Objects.requireNonNull(messageReqDto.getMessageProperties(), "消息属性不能为null");

        try {
            Dict data = messageReqDto.getData();
            String exchange = messageReqDto.getExchange();
            String routingKey = messageReqDto.getRoutingKey();
            CorrelationData correlationData = messageReqDto.getCorrelationData();
            MessageProperties messageProperties = messageReqDto.getMessageProperties();

            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            if (CharSequenceUtil.isNotBlank(messageReqDto.getTag())) {
                messageProperties.setConsumerTag(messageReqDto.getTag());
            }

            Message message = new Message(JSONUtil.toJsonStr(data).getBytes(StandardCharsets.UTF_8), messageProperties);

            enhanceMessageWithTracing(message, messageReqDto);

            rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);

            log.debug("消息发送成功 - 交换机: {}, 路由键: {}", exchange, routingKey);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (AmqpConnectException e) {
            log.error("RabbitMQ连接异常，将进行重试: {}", e.getMessage());
            throw e;
        } catch (AmqpException e) {
            log.error("消息发送失败 - 原因: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("消息发送失败 - 异常类型: {}, 原因: {}", e.getClass().getName(), e.getMessage(), e);
            throw new AmqpException("消息发送过程中发生未预期的异常", e);
        }
        return null;
    }

    @Override
    public Queue createQueue(String queueName, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments) {
        if (queueCache.containsKey(queueName)) {
            log.debug("队列已存在于缓存中: {}", queueName);
            return new Queue(queueName, durable, exclusive, autoDelete, arguments);
        }

        try {
            queueOperationLock.lock();
            if (queueCache.containsKey(queueName)) {
                return new Queue(queueName, durable, exclusive, autoDelete, arguments);
            }
            Queue queue = new Queue(queueName, durable, exclusive, autoDelete, arguments);
            rabbitAdmin.declareQueue(queue);
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

    @Override
    public Queue createDurableQueue(String queueName) {
        return createQueue(queueName, true, false, false, null);
    }

    @Override
    public Queue createTemporaryQueue(String queueName) {
        return createQueue(queueName, false, true, true, null);
    }

    @Override
    public boolean deleteQueue(String queueName) {
        try {
            queueOperationLock.lock();
            queueCache.remove(queueName);
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

    private void enhanceMessageWithTracing(Message message, GXRabbitMQMessageReqDto messageReqDto) {
        Objects.requireNonNull(message, "消息对象不能为null");

        MessageProperties props = message.getMessageProperties();

        String traceId = GXTraceIdContextUtils.generateTraceId();
        if (traceId != null) {
            props.setHeader("X-Trace-Id", traceId);
        } else {
            traceId = UUID.randomUUID().toString();
            props.setHeader("X-Trace-Id", traceId);
        }

        props.setTimestamp(DateUtil.date());
        props.setHeader("X-Timestamp", System.currentTimeMillis());

        String applicationName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        if (applicationName == null || applicationName.trim().isEmpty()) {
            applicationName = "unknown";
        }

        props.setHeader("X-Source-Service", applicationName);

        if (props.getMessageId() == null) {
            props.setMessageId(traceId);
        }

        log.trace("消息追踪信息已添加 - TraceId: {}, Service: {}", traceId, applicationName);
    }

    @Override
    public CompletableFuture<Boolean> setupMessageChannelAsync(String queueName, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments, AbstractExchange exchange, String routingKey) {
        Objects.requireNonNull(queueName, "队列名称不能为null");
        if (queueName.trim().isEmpty()) {
            throw new IllegalArgumentException("队列名称不能为空");
        }
        Objects.requireNonNull(exchange, "交换机不能为null");
        Objects.requireNonNull(routingKey, "路由键不能为null");
        if (routingKey.trim().isEmpty()) {
            throw new IllegalArgumentException("路由键不能为空");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return setupMessageChannel(queueName, durable, exclusive, autoDelete, arguments, exchange, routingKey);
            } catch (Exception e) {
                log.error("异步设置消息通道失败 - 队列: {}, 交换机: {}, 路由键: {}, 异常: {}",
                        queueName, exchange.getName(), routingKey, e.getMessage(), e);
                return false;
            }
        }, getRabbitMqAsyncExecutor());
    }

    public boolean queueExists(String queueName) {
        if (queueCache.containsKey(queueName)) {
            return true;
        }

        try {
            Properties properties = rabbitAdmin.getQueueProperties(queueName);
            boolean exists = properties != null && !properties.isEmpty();

            if (exists) {
                queueCache.put(queueName, true);
            }

            return exists;
        } catch (Exception e) {
            log.error("检查队列是否存在时发生异常: {}, 原因: {}", queueName, e.getMessage(), e);
            return false;
        }
    }

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

    @Override
    public boolean setupMessageChannel(String queueName, boolean durable, boolean exclusive, boolean autoDelete,
                                       Map<String, Object> queueArgs, AbstractExchange exchange, String routingKey) {
        if (CharSequenceUtil.isBlank(queueName) || exchange == null || CharSequenceUtil.isBlank(routingKey)) {
            log.error("创建消息通道失败: 参数不完整 queueName={}, exchange={}, routingKey={}",
                    queueName, exchange != null ? exchange.getName() : "null", routingKey);
            return false;
        }
        try {
            queueOperationLock.lock();
            try {
                boolean exchangeCreated = createExchange(exchange);
                if (!exchangeCreated) {
                    log.error("消息通道创建失败: 无法创建交换机 {}", exchange.getName());
                    return false;
                }

                Queue queue = createQueue(queueName, durable, exclusive, autoDelete, queueArgs);
                if (queue == null) {
                    log.error("消息通道创建失败: 无法创建队列 {}", queueName);
                    return false;
                }

                boolean bindingCreated = bindQueueToExchange(queueName, exchange.getName(), routingKey);
                if (!bindingCreated) {
                    log.error("消息通道创建失败: 无法建立绑定关系 队列[{}] -> 交换机[{}], 路由键[{}]",
                            queueName, exchange.getName(), routingKey);
                    return false;
                }

                log.info("成功创建完整消息通道: 队列[{}] -> 交换机[{}](类型:{}), 路由键[{}]",
                        queueName, exchange.getName(), exchange.getType(), routingKey);
            } finally {
                queueOperationLock.unlock();
            }
            return true;
        } catch (Exception e) {
            log.error("创建消息通道时发生异常: 队列[{}], 交换机[{}], 路由键[{}], 原因: {}",
                    queueName, exchange.getName(), routingKey, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean setupDurableMessageChannel(String queueName, AbstractExchange exchange, String routingKey) {
        return setupMessageChannel(queueName, true, false, false, null, exchange, routingKey);
    }
}
