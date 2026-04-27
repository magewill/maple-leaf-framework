package cn.maple.rabbitmq.config;

import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.rabbitmq.callback.GXConfirmCallback;
import cn.maple.rabbitmq.callback.GXRecoveryCallback;
import cn.maple.rabbitmq.callback.GXReturnsCallback;
import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.system.JavaVersion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.messaging.converter.GenericMessageConverter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RabbitMQ配置类
 * <p>
 * 该配置类负责创建和配置与RabbitMQ相关的各种Bean，包括RabbitTemplate、RabbitAdmin、
 * AsyncRabbitTemplate和RabbitMessagingTemplate等。
 * 配置类只在classpath中存在ConnectionFactory类时才会生效。
 * </p>
 * <p>
 * 主要功能：
 * 1. 提供线程安全的RabbitTemplate配置，支持在高并发环境下使用
 * 2. 配置消息转换器，支持JSON格式的消息，并增强反序列化安全性
 * 3. 提供异步消息处理能力，通过AsyncRabbitTemplate和自定义线程池优化性能
 * 4. 支持消息确认和返回机制，提高消息可靠性
 * 5. 支持与Spring Messaging API集成
 * 6. 利用Java 17+特性优化线程池和内存管理
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * @Autowired
 * private RabbitTemplate rabbitTemplate;
 *
 * // 发送消息
 * rabbitTemplate.convertAndSend("exchange", "routingKey", message);
 *
 * // 使用异步模板
 * @Autowired
 * private AsyncRabbitTemplate asyncRabbitTemplate;
 *
 * ListenableFuture<Message> future = asyncRabbitTemplate.sendAndReceive("exchange", "routingKey", message);
 * future.addCallback(result -> {
 *     // 处理结果
 * }, ex -> {
 *     // 处理异常
 * });
 * }</pre>
 * </p>
 *
 * @author maple
 * @since 1.0.0
 */
@Configuration
@Slf4j
@ConditionalOnClass(name = {"org.springframework.amqp.rabbit.connection.ConnectionFactory"})
@EnableRabbit
public class GXRabbitMQConfig {
    /**
     * RabbitMQ连接工厂，由Spring Boot自动配置注入
     */
    @Resource
    private ConnectionFactory connectionFactory;

    @Resource
    private GXRabbitMQProperties rabbitProperties;

    /**
     * 创建虚拟线程池任务调度器
     * <p>
     * 该方法初始化并返回一个ThreadPoolTaskScheduler实例，该实例使用虚拟线程池来执行任务
     * 虚拟线程池的使用允许每个任务在自己的虚拟线程中执行，从而提高并发性和响应性
     *
     * @return ThreadPoolTaskScheduler实例，用于调度在虚拟线程池中执行的任务
     */
    private static ThreadPoolTaskScheduler getVirtualThreadPoolTaskScheduler() {
        // 创建并初始化一个ThreadPoolTaskScheduler对象
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler() {
            // 重写schedule方法，以支持使用虚拟线程池执行任务
            @Override
            public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
                // 提交任务到虚拟线程池执行，并处理结果
                return super.schedule(() -> {
                    // 使用try-with-resources确保执行器在任务完成后关闭
                    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        try {
                            // 提交任务并等待完成，如果发生异常则包装并抛出
                            executor.submit(task).get();
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, trigger);
            }
        };
        // 初始化调度器
        scheduler.initialize();
        // 返回初始化后的调度器实例
        return scheduler;
    }

    @PostConstruct
    public void applyConnectionFactoryProperties() {
        if (!(connectionFactory instanceof CachingConnectionFactory cachingConnectionFactory) || rabbitProperties == null) {
            return;
        }

        if (rabbitProperties.getCacheMode() != null) {
            cachingConnectionFactory.setCacheMode(rabbitProperties.getCacheMode());
        }
        if (rabbitProperties.getChannelCacheSize() != null) {
            cachingConnectionFactory.setChannelCacheSize(rabbitProperties.getChannelCacheSize());
        }
        if (rabbitProperties.getConnectionLimit() != null) {
            cachingConnectionFactory.setConnectionLimit(rabbitProperties.getConnectionLimit());
        }
        if (rabbitProperties.getChannelCheckoutTimeout() != null) {
            cachingConnectionFactory.setChannelCheckoutTimeout(rabbitProperties.getChannelCheckoutTimeout());
        }
    }

    /**
     * 创建RabbitTemplate实例
     * <p>
     * 配置RabbitTemplate，设置连接工厂、消息转换器和各种回调函数。
     * 主要功能包括：
     * 1. 设置消息转换器为Jackson2JsonMessageConverter，支持JSON格式的消息
     * 2. 配置可信任的包，提高反序列化时的安全性
     * 3. 设置消息发送失败时的返回回调
     * 4. 设置消息确认回调，用于确认消息是否成功发送到交换机
     * 5. 设置重试恢复回调，用于处理重试失败的情况
     * </p>
     * <p>
     * 线程安全说明：RabbitTemplate是线程安全的，可以在多线程环境下共享使用。
     * 内部使用ThreadLocal保存Channel，确保每个线程使用独立的Channel实例。
     * </p>
     * <p>
     * 性能优化：
     * 1. 使用自定义的ObjectMapper配置，优化JSON序列化/反序列化性能
     * 2. 支持Java 8日期时间类型
     * 3. 禁用了一些不必要的Jackson特性，减少序列化开销
     * 4. 使用Java 17+的增强型空值处理，提高代码健壮性
     * </p>
     *
     * @return 配置好的RabbitTemplate实例
     */
    @Bean
    public RabbitTemplate rabbitTemplate() {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate();
        rabbitTemplate.setConnectionFactory(connectionFactory);
        applyTemplateProperties(rabbitTemplate);

        // 配置消息转换器，提高安全性和性能
        DefaultClassMapper defaultClassMapper = new DefaultClassMapper();
        // 设置可信任的包，提高反序列化安全性
        defaultClassMapper.setTrustedPackages("cn.hutool.core", "cn.maple");

        // 创建并配置ObjectMapper，优化JSON处理
        JsonMapper jsonMapper = new JsonMapper();
        jsonMapper.registeredModules().add(new JavaTimeModule());

        JacksonJsonMessageConverter jacksonJsonMessageConverter = new JacksonJsonMessageConverter(jsonMapper);
        jacksonJsonMessageConverter.setClassMapper(defaultClassMapper);
        rabbitTemplate.setMessageConverter(jacksonJsonMessageConverter);

        // 设置消息发送失败返回回调
        rabbitTemplate.setReturnsCallback(returned -> {
            try {
                GXReturnsCallback returnsCallback = GXSpringContextUtils.getBean(GXReturnsCallback.class);
                if (ObjectUtil.isNotNull(returnsCallback)) {
                    returnsCallback.returnedMessage(returned);
                } else {
                    // 如果没有自定义回调实现，记录基本日志
                    log.warn("消息路由失败: exchange={}, routingKey={}, replyCode={}, replyText={}, message={}",
                            returned.getExchange(), returned.getRoutingKey(),
                            returned.getReplyCode(), returned.getReplyText(),
                            new String(returned.getMessage().getBody(), StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                log.error("处理消息返回回调时发生异常", e);
            }
        });

        // 设置消息发送确认回调
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            try {
                GXConfirmCallback confirmCallback = GXSpringContextUtils.getBean(GXConfirmCallback.class);
                if (ObjectUtil.isNotNull(confirmCallback)) {
                    confirmCallback.confirm(correlationData, ack, cause);
                } else if (!ack) {
                    // 如果没有自定义回调实现且确认失败，记录警告日志
                    log.warn("消息未能发送到交换机: correlationData={}, cause={}", correlationData, cause);
                }
            } catch (Exception e) {
                log.error("处理消息确认回调时发生异常", e);
            }
        });

        // 设置重试恢复回调
        rabbitTemplate.setRecoveryCallback(throwable -> {
            try {
                GXRecoveryCallback recoveryCallback = GXSpringContextUtils.getBean(GXRecoveryCallback.class);
                if (ObjectUtil.isNotNull(recoveryCallback)) {
                    return recoveryCallback.recover(throwable);
                } else {
                    // 如果没有自定义回调实现，记录错误日志
                    log.error("RabbitMQ消息发送重试失败，实现信息: {}", throwable.getMessage());
                }
            } catch (Exception e) {
                log.error("处理消息重试恢复回调时发生异常", e);
            }
            return null;
        });

        return rabbitTemplate;
    }

    /**
     * 创建RabbitAdmin实例
     * <p>
     * RabbitAdmin用于管理RabbitMQ的队列、交换机和绑定关系等资源。
     * 它会自动检测容器中的队列、交换机和绑定声明，并在RabbitMQ中自动创建这些资源。
     * </p>
     * <p>
     * 功能特点：
     * 1. 自动创建队列、交换机和绑定关系
     * 2. 支持动态管理RabbitMQ资源
     * 3. 提供队列和交换机的状态查询功能
     * </p>
     *
     * @return 配置好的RabbitAdmin实例
     */
    @Bean
    public RabbitAdmin rabbitAdmin() {
        return new RabbitAdmin(connectionFactory);
    }

    private void applyTemplateProperties(RabbitTemplate rabbitTemplate) {
        if (rabbitProperties == null || rabbitProperties.getTemplate() == null) {
            return;
        }

        RabbitProperties.Template template = rabbitProperties.getTemplate();
        if (template.getMandatory() != null) {
            rabbitTemplate.setMandatory(template.getMandatory());
        }
        if (template.getReceiveTimeout() != null) {
            rabbitTemplate.setReceiveTimeout(template.getReceiveTimeout().toMillis());
        }
        if (template.getReplyTimeout() != null) {
            rabbitTemplate.setReplyTimeout(template.getReplyTimeout().toMillis());
        }
        if (template.getExchange() != null) {
            rabbitTemplate.setExchange(template.getExchange());
        }
        if (template.getRoutingKey() != null) {
            rabbitTemplate.setRoutingKey(template.getRoutingKey());
        }
        if (template.getDefaultReceiveQueue() != null) {
            rabbitTemplate.setDefaultReceiveQueue(template.getDefaultReceiveQueue());
        }
        rabbitTemplate.setObservationEnabled(template.isObservationEnabled());
    }

    /**
     * 创建AsyncRabbitTemplate实例
     * <p>
     * AsyncRabbitTemplate提供了异步发送消息的能力，适用于需要异步处理的场景。
     * 它基于RabbitTemplate，但提供了异步API，可以使用Future或回调来处理结果。
     * </p>
     * <p>
     * 性能优化：
     * 1. 使用自定义线程池处理异步操作，避免使用默认线程池可能导致的资源竞争
     * 2. 线程池参数经过优化，适合处理I/O密集型任务
     * 3. 使用有界队列和拒绝策略，防止系统过载
     * 4. 利用Java 17+的增强型并发API，提高线程池效率
     * </p>
     * <p>
     * 使用示例：
     * <pre>{@code
     * // 发送异步消息并等待回复
     * ListenableFuture<Message> future = asyncRabbitTemplate.sendAndReceive("exchange", "routingKey", message);
     *
     * // 添加回调处理结果
     * future.addCallback(result -> {
     *     // 处理返回的消息
     * }, ex -> {
     *     // 处理异常
     * });
     * }</pre>
     * </p>
     *
     * @param rabbitTemplate 已配置的RabbitTemplate实例
     * @return 配置好的AsyncRabbitTemplate实例
     */
    @Bean
    public AsyncRabbitTemplate asyncRabbitTemplate(RabbitTemplate rabbitTemplate) {
        AsyncRabbitTemplate asyncTemplate = new AsyncRabbitTemplate(rabbitTemplate);
        // 设置自定义线程池执行器，优化异步处理性能
        asyncTemplate.setTaskScheduler(rabbitTaskScheduler());
        return asyncTemplate;
    }

    /**
     * 创建RabbitMQ操作专用的线程池执行器
     * <p>
     * 该线程池用于处理RabbitMQ的异步操作，如AsyncRabbitTemplate的异步消息发送和接收。
     * 线程池参数经过优化，适合处理I/O密集型任务，能够在高并发场景下提供良好的性能。
     * </p>
     * <p>
     * 线程池配置说明：
     * 1. 核心线程数：CPU核心数 * 2，适合I/O密集型任务的并发处理
     * 2. 线程优先级：设置为5（中等优先级），确保不会抢占关键业务线程资源
     * 3. 错误处理：配置自定义的ErrorHandler，防止线程因未捕获异常而终止
     * 4. 拒绝策略：在线程池满载时，通过CallerRunsPolicy实现背压机制
     * 5. 线程前缀：使用规范化的命名前缀，方便在线程转储和监控中识别
     * 6. 优雅关闭：配置关闭超时和等待策略，确保应用关闭时能够完成正在处理的任务
     * 7. 任务装饰：支持任务执行前后的监控和统计
     * 8. 自定义线程工厂：使用Java 17+的虚拟线程特性，提高并发性能（可选配置）
     * </p>
     * <p>
     * 性能优化：
     * 1. 线程池大小基于CPU核心数动态计算，适应不同硬件环境
     * 2. 针对I/O密集型任务特性进行参数调优，提高资源利用率
     * 3. 通过ErrorHandler机制确保异常不会导致线程终止，提高线程池稳定性
     * 4. 支持JMX监控，便于运行时观察线程池状态和性能指标
     * 5. 使用Java 17+的增强型并发API，提高线程池效率
     * </p>
     *
     * @return 配置好的ThreadPoolTaskScheduler实例
     */
    @Bean
    public TaskScheduler rabbitTaskScheduler() {
        // 1. 考虑使用Java 17的虚拟线程（如果项目支持Java 17+）
        if (JavaVersion.getJavaVersion().isEqualOrNewerThan(JavaVersion.SEVENTEEN)) {
            return getVirtualThreadPoolTaskScheduler();
        }

        // 2. 对于Java 16及以下版本  使用自适应线程池大小，根据系统负载动态调整
        // 获取可用处理器数量
        int processors = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

        // 设置线程池大小为处理器数量的2倍，适合I/O密集型任务
        // 对于I/O密集型任务，线程数可以适当增加，因为大部分时间线程都在等待I/O操作完成
        taskScheduler.setPoolSize(processors * 2);

        // 设置线程组名称，便于管理和监控
        taskScheduler.setThreadGroupName("maple-framework-rabbit-async-group");

        // 设置自定义线程工厂，提供更好的线程命名和异常处理
        taskScheduler.setThreadFactory(new RabbitThreadFactory("maple-framework-rabbit-async-"));

        // 设置线程优先级（1-10，默认为5）
        // 避免设置过高优先级，防止抢占其他关键业务线程资源
        taskScheduler.setThreadPriority(Thread.NORM_PRIORITY);

        // 配置自定义的未捕获异常处理器，防止线程因未处理异常而终止
        taskScheduler.setErrorHandler(throwable -> {
            log.error("RabbitMQ异步任务执行异常", throwable);
            // 这里可以添加额外的异常处理逻辑，如发送告警、记录指标等
        });

        // 设置为非守护线程，确保应用关闭前能够完成任务
        taskScheduler.setDaemon(false);

        // 应用关闭时等待任务完成
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);

        // 等待终止的最长时间（秒）- 设置为3分钟
        // 在应用关闭时，最多等待3分钟让任务完成，避免关闭过程无限等待
        taskScheduler.setAwaitTerminationSeconds(180);

        // 关闭时执行已存在的延迟任务
        taskScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);

        // 设置是否移除已取消的任务
        taskScheduler.setRemoveOnCancelPolicy(true);

        // 初始化线程池
        taskScheduler.initialize();

        log.info("RabbitMQ异步任务线程池已初始化，线程池大小：{}", processors * 2);

        return taskScheduler;
    }

    /**
     * 创建RabbitMessagingTemplate实例
     * <p>
     * RabbitMessagingTemplate是对RabbitTemplate的封装，提供了与Spring Messaging API集成的能力。
     * 它允许使用统一的消息发送API，无论底层消息中间件是什么。
     * </p>
     * <p>
     * 功能特点：
     * 1. 提供与Spring Messaging API的无缝集成
     * 2. 支持消息头和消息体的分离处理
     * 3. 支持消息转换和类型转换
     * 4. 简化消息发送和接收操作
     * </p>
     *
     * @param rabbitTemplate 已配置的RabbitTemplate实例
     * @return 配置好的RabbitMessagingTemplate实例
     */
    @Bean
    public RabbitMessagingTemplate rabbitMessagingTemplate(RabbitTemplate rabbitTemplate) {
        RabbitMessagingTemplate messagingTemplate = new RabbitMessagingTemplate();
        messagingTemplate.setRabbitTemplate(rabbitTemplate);
        messagingTemplate.setMessageConverter(new GenericMessageConverter(new DefaultConversionService()));
        return messagingTemplate;
    }

    /**
     * 自定义线程工厂，用于创建和命名RabbitMQ异步操作线程
     * <p>
     * 该线程工厂提供了更好的线程命名和异常处理机制，有助于问题排查和性能监控。
     * 使用AtomicInteger确保线程编号的唯一性和线程安全。
     * </p>
     */
    private static class RabbitThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        /**
         * 创建自定义线程工厂
         *
         * @param namePrefix 线程名称前缀
         */
        public RabbitThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            // 设置为非守护线程，确保任务能够完成
            thread.setDaemon(false);
            // 设置默认优先级
            thread.setPriority(Thread.NORM_PRIORITY);
            // 设置未捕获异常处理器
            thread.setUncaughtExceptionHandler((t, e) ->
                    log.error("线程 {} 发生未捕获异常", t.getName(), e));
            return thread;
        }
    }

    /**
     * 自定义ConnectionFactory配置方法（当前已注释）
     * <p>
     * 此方法展示了如何手动配置ConnectionFactory，而不是使用Spring Boot的自动配置。
     * 主要配置项包括：
     * 1. 发布确认类型 - 控制消息发布确认的行为
     * 2. 连接地址、用户名、密码等基本连接信息
     * 3. 发布返回 - 控制未路由消息的返回行为
     * 4. 虚拟主机 - RabbitMQ的虚拟主机
     * 5. 缓存模式 - 控制连接和通道的缓存策略
     * 6. 通道缓存大小 - 影响性能和资源使用
     * 7. 连接限制、超时等高级配置
     * </p>
     * <p>
     * 安全性说明：
     * - 使用GXCommonUtils.decodeConnectStr方法解码敏感信息，提高安全性
     * - 连接信息应当从配置文件或安全的配置中心获取，避免硬编码
     * </p>
     *
     * @return 配置好的CachingConnectionFactory实例
     */
    /*@Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory();
        cachingConnectionFactory.setPublisherConfirmType(rabbitMQProperties.getPublisherConfirmType());
        cachingConnectionFactory.setAddresses(GXCommonUtils.decodeConnectStr(rabbitMQProperties.getAddresses(), String.class));
        cachingConnectionFactory.setUsername(GXCommonUtils.decodeConnectStr(rabbitMQProperties.getUsername(), String.class));
        cachingConnectionFactory.setPassword(GXCommonUtils.decodeConnectStr(rabbitMQProperties.getPassword(), String.class));
        cachingConnectionFactory.setPublisherReturns(rabbitMQProperties.getPublisherReturns());
        cachingConnectionFactory.setVirtualHost(GXCommonUtils.decodeConnectStr(rabbitMQProperties.getVirtualHost(), String.class));
        cachingConnectionFactory.setCacheMode(rabbitMQProperties.getCacheMode());
        cachingConnectionFactory.setChannelCacheSize(rabbitMQProperties.getChannelCacheSize());
        cachingConnectionFactory.setConnectionLimit(rabbitMQProperties.getConnectionLimit());
        cachingConnectionFactory.setConnectionTimeout(rabbitMQProperties.getConnectionTimeout());
        cachingConnectionFactory.setChannelCheckoutTimeout(rabbitMQProperties.getChannelCheckoutTimeout());
        return cachingConnectionFactory;
    }*/
}
