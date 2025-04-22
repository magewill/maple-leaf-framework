package cn.maple.rabbitmq.config;

import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.rabbitmq.callback.GXConfirmCallback;
import cn.maple.rabbitmq.callback.GXRecoveryCallback;
import cn.maple.rabbitmq.callback.GXReturnsCallback;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.GenericMessageConverter;

/**
 * RabbitMQ配置类
 * <p>
 * 该配置类负责创建和配置与RabbitMQ相关的各种Bean，包括RabbitTemplate、RabbitAdmin、
 * AsyncRabbitTemplate和RabbitMessagingTemplate等。
 * 配置类只在classpath中存在ConnectionFactory类时才会生效。
 * </p>
 * 
 * @author maple
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
     * 线程安全说明：RabbitTemplate是线程安全的，可以在多线程环境下共享使用
     * </p>
     *
     * @return 配置好的RabbitTemplate实例
     */
    @Bean
    public RabbitTemplate rabbitTemplate() {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate();
        rabbitTemplate.setConnectionFactory(connectionFactory);
        
        // 配置消息转换器，提高安全性
        DefaultClassMapper defaultClassMapper = new DefaultClassMapper();
        defaultClassMapper.setTrustedPackages("cn.hutool.core");
        Jackson2JsonMessageConverter jackson2JsonMessageConverter = new Jackson2JsonMessageConverter();
        jackson2JsonMessageConverter.setClassMapper(defaultClassMapper);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter);
        
        // 设置消息发送失败返回回调
        rabbitTemplate.setReturnsCallback(returned -> {
            GXReturnsCallback returnsCallback = GXSpringContextUtils.getBean(GXReturnsCallback.class);
            if (ObjectUtil.isNotNull(returnsCallback)) {
                returnsCallback.returnedMessage(returned);
            }
        });
        
        // 设置消息发送确认回调
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            GXConfirmCallback confirmCallback = GXSpringContextUtils.getBean(GXConfirmCallback.class);
            if (ObjectUtil.isNotNull(confirmCallback)) {
                confirmCallback.confirm(correlationData, ack, cause);
            }
        });
        
        // 设置重试恢复回调
        rabbitTemplate.setRecoveryCallback(retryContext -> {
            GXRecoveryCallback recoveryCallback = GXSpringContextUtils.getBean(GXRecoveryCallback.class);
            if (ObjectUtil.isNotNull(recoveryCallback)) {
                return recoveryCallback.recover(retryContext);
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
     *
     * @return 配置好的RabbitAdmin实例
     */
    @Bean
    public RabbitAdmin rabbitAdmin() {
        return new RabbitAdmin(connectionFactory);
    }

    /**
     * 创建AsyncRabbitTemplate实例
     * <p>
     * AsyncRabbitTemplate提供了异步发送消息的能力，适用于需要异步处理的场景。
     * 它基于RabbitTemplate，但提供了异步API，可以使用Future或回调来处理结果。
     * </p>
     *
     * @param rabbitTemplate 已配置的RabbitTemplate实例
     * @return 配置好的AsyncRabbitTemplate实例
     */
    @Bean
    public AsyncRabbitTemplate asyncRabbitTemplate(RabbitTemplate rabbitTemplate) {
        return new AsyncRabbitTemplate(rabbitTemplate);
    }

    /**
     * 初始化 RabbitMessagingTemplate
     * <p>
     * RabbitMessagingTemplate是对RabbitTemplate的封装，提供了与Spring Messaging API集成的能力。
     * 它允许使用统一的消息模型发送消息，适用于需要与Spring Integration或其他Spring Messaging组件集成的场景。
     * 该Bean使用GenericMessageConverter作为消息转换器，支持通用的消息格式转换。
     * </p>
     * <p>
     * 注意：这里创建了一个新的RabbitTemplate实例，而不是复用已有的Bean，这样可以使用不同的配置。
     * </p>
     *
     * @return 配置好的RabbitMessagingTemplate实例
     */
    @Bean
    public RabbitMessagingTemplate simpleMessageTemplate() {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        RabbitMessagingTemplate rabbitMessagingTemplate = new RabbitMessagingTemplate();
        rabbitMessagingTemplate.setMessageConverter(new GenericMessageConverter());
        rabbitMessagingTemplate.setRabbitTemplate(template);
        return rabbitMessagingTemplate;
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
