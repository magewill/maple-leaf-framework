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

@Configuration
@Slf4j
@ConditionalOnClass(name = {"org.springframework.amqp.rabbit.connection.ConnectionFactory"})
@EnableRabbit
public class GXRabbitMQConfig {
    @Resource
    private ConnectionFactory connectionFactory;

    @Resource
    private GXRabbitMQProperties rabbitProperties;

    private static ThreadPoolTaskScheduler getVirtualThreadPoolTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler() {
            // 重写schedule方法，以支持使用虚拟线程池执行任务
            @Override
            public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
                return super.schedule(() -> {
                    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        try {
                            executor.submit(task).get();
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, trigger);
            }
        };
        scheduler.initialize();
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

    @Bean
    public RabbitTemplate rabbitTemplate() {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate();
        rabbitTemplate.setConnectionFactory(connectionFactory);
        applyTemplateProperties(rabbitTemplate);

        DefaultClassMapper defaultClassMapper = new DefaultClassMapper();
        defaultClassMapper.setTrustedPackages("cn.hutool.core", "cn.maple");

        JsonMapper jsonMapper = new JsonMapper();
        jsonMapper.registeredModules().add(new JavaTimeModule());

        JacksonJsonMessageConverter jacksonJsonMessageConverter = new JacksonJsonMessageConverter(jsonMapper);
        jacksonJsonMessageConverter.setClassMapper(defaultClassMapper);
        rabbitTemplate.setMessageConverter(jacksonJsonMessageConverter);

        rabbitTemplate.setReturnsCallback(returned -> {
            try {
                GXReturnsCallback returnsCallback = GXSpringContextUtils.getBean(GXReturnsCallback.class);
                if (ObjectUtil.isNotNull(returnsCallback)) {
                    returnsCallback.returnedMessage(returned);
                } else {
                    log.warn("消息路由失败: exchange={}, routingKey={}, replyCode={}, replyText={}, message={}",
                            returned.getExchange(), returned.getRoutingKey(),
                            returned.getReplyCode(), returned.getReplyText(),
                            new String(returned.getMessage().getBody(), StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                log.error("处理消息返回回调时发生异常", e);
            }
        });

        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            try {
                GXConfirmCallback confirmCallback = GXSpringContextUtils.getBean(GXConfirmCallback.class);
                if (ObjectUtil.isNotNull(confirmCallback)) {
                    confirmCallback.confirm(correlationData, ack, cause);
                } else if (!ack) {
                    log.warn("消息未能发送到交换机: correlationData={}, cause={}", correlationData, cause);
                }
            } catch (Exception e) {
                log.error("处理消息确认回调时发生异常", e);
            }
        });

        rabbitTemplate.setRecoveryCallback(throwable -> {
            try {
                GXRecoveryCallback recoveryCallback = GXSpringContextUtils.getBean(GXRecoveryCallback.class);
                if (ObjectUtil.isNotNull(recoveryCallback)) {
                    return recoveryCallback.recover(throwable);
                } else {
                    log.error("RabbitMQ消息发送重试失败，实现信息: {}", throwable.getMessage());
                }
            } catch (Exception e) {
                log.error("处理消息重试恢复回调时发生异常", e);
            }
            return null;
        });

        return rabbitTemplate;
    }

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

    @Bean
    public AsyncRabbitTemplate asyncRabbitTemplate(RabbitTemplate rabbitTemplate) {
        AsyncRabbitTemplate asyncTemplate = new AsyncRabbitTemplate(rabbitTemplate);
        asyncTemplate.setTaskScheduler(rabbitTaskScheduler());
        return asyncTemplate;
    }

    @Bean
    public TaskScheduler rabbitTaskScheduler() {
        if (JavaVersion.getJavaVersion().isEqualOrNewerThan(JavaVersion.SEVENTEEN)) {
            return getVirtualThreadPoolTaskScheduler();
        }

        int processors = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

        taskScheduler.setPoolSize(processors * 2);

        taskScheduler.setThreadGroupName("maple-framework-rabbit-async-group");

        taskScheduler.setThreadFactory(new RabbitThreadFactory("maple-framework-rabbit-async-"));

        taskScheduler.setThreadPriority(Thread.NORM_PRIORITY);

        taskScheduler.setErrorHandler(throwable -> {
            log.error("RabbitMQ异步任务执行异常", throwable);
        });

        taskScheduler.setDaemon(false);

        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);

        taskScheduler.setAwaitTerminationSeconds(180);

        taskScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);

        taskScheduler.setRemoveOnCancelPolicy(true);

        taskScheduler.initialize();

        log.info("RabbitMQ异步任务线程池已初始化，线程池大小：{}", processors * 2);

        return taskScheduler;
    }

    @Bean
    public RabbitMessagingTemplate rabbitMessagingTemplate(RabbitTemplate rabbitTemplate) {
        RabbitMessagingTemplate messagingTemplate = new RabbitMessagingTemplate();
        messagingTemplate.setRabbitTemplate(rabbitTemplate);
        messagingTemplate.setMessageConverter(new GenericMessageConverter(new DefaultConversionService()));
        return messagingTemplate;
    }

    private static class RabbitThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        public RabbitThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setUncaughtExceptionHandler((t, e) ->
                    log.error("线程 {} 发生未捕获异常", t.getName(), e));
            return thread;
        }
    }

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
