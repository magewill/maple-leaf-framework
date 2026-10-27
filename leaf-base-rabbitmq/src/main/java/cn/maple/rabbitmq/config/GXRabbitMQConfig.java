package cn.maple.rabbitmq.config;

import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.rabbitmq.callback.GXConfirmCallback;
import cn.maple.rabbitmq.callback.GXRecoveryCallback;
import cn.maple.rabbitmq.callback.GXReturnsCallback;
import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import cn.maple.rabbitmq.support.GXRabbitMQJsonSupport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.Environment;
import org.springframework.messaging.converter.GenericMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ 核心装配。
 *
 * <p>调度器统一由 {@link #rabbitTaskScheduler()} 提供：默认以 {@code TaskScheduler} Bean 暴露（保持历史行为），
 * 可通过 {@code maple.framework.rabbitmq.scheduler.expose=false} 关闭暴露，改由本类内部持有并随上下文关闭。</p>
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
@ConditionalOnClass(name = {"org.springframework.amqp.rabbit.connection.ConnectionFactory"})
@EnableRabbit
public class GXRabbitMQConfig {
    private static final int SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final ConnectionFactory connectionFactory;

    private final GXRabbitMQProperties rabbitProperties;

    private final Environment environment;

    @Value("${maple.framework.rabbitmq.scheduler.expose:true}")
    private boolean exposeScheduler;

    private volatile ThreadPoolTaskScheduler internalScheduler;

    public GXRabbitMQConfig(ConnectionFactory connectionFactory, GXRabbitMQProperties rabbitProperties,
                            Environment environment) {
        this.connectionFactory = connectionFactory;
        this.rabbitProperties = rabbitProperties;
        this.environment = environment;
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

        log.info("RabbitMQ connection factory tuned: propertiesBean={}, configSource={}, profile={}, cacheMode={}, channelCacheSize={}, connectionLimit={}, channelCheckoutTimeout={}",
                rabbitProperties.getClass().getSimpleName(),
                environment.getProperty("maple.framework.rabbitmq.config-source", "nacos"),
                environment.getProperty("spring.profiles.active", "default"),
                cachingConnectionFactory.getCacheMode(),
                rabbitProperties.getChannelCacheSize(),
                rabbitProperties.getConnectionLimit(),
                rabbitProperties.getChannelCheckoutTimeout());
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate();
        rabbitTemplate.setConnectionFactory(connectionFactory);
        applyTemplateProperties(rabbitTemplate);

        DefaultClassMapper defaultClassMapper = new DefaultClassMapper();
        defaultClassMapper.setTrustedPackages("cn.hutool.core.lang", "cn.hutool.core", "cn.maple");

        JsonMapper jsonMapper = GXRabbitMQJsonSupport.jsonMapper();
        JacksonJsonMessageConverter jacksonJsonMessageConverter = new JacksonJsonMessageConverter(jsonMapper);
        jacksonJsonMessageConverter.setClassMapper(defaultClassMapper);
        rabbitTemplate.setMessageConverter(jacksonJsonMessageConverter);

        rabbitTemplate.setReturnsCallback(returned -> {
            try {
                GXReturnsCallback returnsCallback = GXSpringContextUtils.getBean(GXReturnsCallback.class);
                if (ObjectUtil.isNotNull(returnsCallback)) {
                    returnsCallback.returnedMessage(returned);
                } else {
                    log.warn("RabbitMQ message returned: exchange={}, routingKey={}, replyCode={}, replyText={}, bodyLength={}",
                            returned.getExchange(), returned.getRoutingKey(),
                            returned.getReplyCode(), returned.getReplyText(),
                            returned.getMessage().getBody().length);
                }
            } catch (Exception e) {
                log.error("RabbitMQ returns callback failed: {}", e.getClass().getName(), e);
            }
        });

        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            try {
                GXConfirmCallback confirmCallback = GXSpringContextUtils.getBean(GXConfirmCallback.class);
                if (ObjectUtil.isNotNull(confirmCallback)) {
                    confirmCallback.confirm(correlationData, ack, cause);
                } else if (!ack) {
                    log.warn("RabbitMQ message not acknowledged by exchange: correlationData={}, cause={}",
                            correlationData, cause);
                }
            } catch (Exception e) {
                log.error("RabbitMQ confirm callback failed: {}", e.getClass().getName(), e);
            }
        });

        rabbitTemplate.setRecoveryCallback(throwable -> {
            try {
                GXRecoveryCallback recoveryCallback = GXSpringContextUtils.getBean(GXRecoveryCallback.class);
                if (ObjectUtil.isNotNull(recoveryCallback)) {
                    return recoveryCallback.recover(throwable);
                } else {
                    log.error("RabbitMQ send retry exhausted: errorType={}, error={}",
                            throwable.getClass().getName(), throwable.getMessage());
                }
            } catch (Exception e) {
                log.error("RabbitMQ recovery callback failed: {}", e.getClass().getName(), e);
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
    @ConditionalOnProperty(name = "maple.framework.rabbitmq.scheduler.expose", havingValue = "true", matchIfMissing = true)
    public ThreadPoolTaskScheduler rabbitTaskScheduler() {
        return createScheduler();
    }

    @Bean
    public AsyncRabbitTemplate asyncRabbitTemplate(RabbitTemplate rabbitTemplate,
                                                   @Qualifier("rabbitTaskScheduler") ObjectProvider<ThreadPoolTaskScheduler> schedulerProvider) {
        AsyncRabbitTemplate asyncTemplate = new AsyncRabbitTemplate(rabbitTemplate);
        asyncTemplate.setTaskScheduler(schedulerProvider.getIfAvailable(this::getOrCreateInternalScheduler));
        return asyncTemplate;
    }

    @Bean
    public RabbitMessagingTemplate rabbitMessagingTemplate(RabbitTemplate rabbitTemplate) {
        RabbitMessagingTemplate messagingTemplate = new RabbitMessagingTemplate();
        messagingTemplate.setRabbitTemplate(rabbitTemplate);
        messagingTemplate.setMessageConverter(new GenericMessageConverter(new DefaultConversionService()));
        return messagingTemplate;
    }

    private ThreadPoolTaskScheduler getOrCreateInternalScheduler() {
        ThreadPoolTaskScheduler scheduler = this.internalScheduler;
        if (scheduler == null) {
            synchronized (this) {
                scheduler = this.internalScheduler;
                if (scheduler == null) {
                    scheduler = createScheduler();
                    this.internalScheduler = scheduler;
                }
            }
        }
        return scheduler;
    }

    private ThreadPoolTaskScheduler createScheduler() {
        int processors = Runtime.getRuntime().availableProcessors();
        int poolSize = Math.max(2, processors);
        boolean virtualThreads = Runtime.version().feature() >= 21;

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("maple-rabbit-scheduler-");
        scheduler.setVirtualThreads(virtualThreads);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setErrorHandler(throwable ->
                log.error("RabbitMQ scheduler task failed: {}", throwable.getClass().getName(), throwable));
        scheduler.setDaemon(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(SCHEDULER_SHUTDOWN_TIMEOUT_SECONDS);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);
        scheduler.initialize();

        log.info("RabbitMQ task scheduler initialized: poolSize={}, virtualThreads={}", poolSize, virtualThreads);
        return scheduler;
    }

    @PreDestroy
    public void shutdownInternalScheduler() {
        ThreadPoolTaskScheduler scheduler = this.internalScheduler;
        if (scheduler == null) {
            return;
        }
        scheduler.shutdown();
        this.internalScheduler = null;
        log.info("RabbitMQ internal task scheduler stopped: exposed={}", exposeScheduler);
    }
}
