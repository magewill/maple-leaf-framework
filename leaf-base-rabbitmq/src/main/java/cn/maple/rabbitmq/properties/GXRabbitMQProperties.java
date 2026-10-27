package cn.maple.rabbitmq.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;

/**
 * RabbitMQ 配置基类，业务代码只依赖本类型，不依赖 local/nacos 具体变体。
 *
 * <p>前缀固定为 {@code spring.rabbitmq}，因此 {@code spring.rabbitmq.default-queue-name}
 * 与 {@code spring.rabbitmq.defaultQueueName} 两种写法都会通过 relaxed binding 绑定到
 * {@link #defaultQueueName}；默认监听器的队列名占位符同样支持这两种写法。</p>
 */
@Setter
@Getter
public class GXRabbitMQProperties extends RabbitProperties {
    /**
     * 默认监听队列名，仅在未配置 {@code maple.framework.mq.rabbitmq.default-queue-name} 时生效。
     */
    private String defaultQueueName = "maple.default.queue";

    /**
     * 连接数上限，仅对 {@link CachingConnectionFactory} 生效。
     */
    private Integer connectionLimit;

    /**
     * channel 缓存数量，仅对 {@link CachingConnectionFactory} 生效。
     */
    private Integer channelCacheSize;

    /**
     * 连接缓存模式，仅对 {@link CachingConnectionFactory} 生效。
     */
    private CachingConnectionFactory.CacheMode cacheMode;

    /**
     * 获取 channel 的最长等待时间（毫秒），需配合 {@link #connectionLimit} 使用。
     */
    private Long channelCheckoutTimeout;
}
