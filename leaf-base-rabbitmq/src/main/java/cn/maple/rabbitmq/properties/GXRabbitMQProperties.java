package cn.maple.rabbitmq.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;

/**
 * RabbitMQ配置属性基类。
 * <p>
 * 继承Spring Boot的RabbitProperties以复用标准spring.rabbitmq配置绑定，同时保留框架历史配置项。
 * </p>
 */
@Setter
@Getter
public class GXRabbitMQProperties extends RabbitProperties {
    private String defaultQueueName = "maple.default.queue";

    private Integer connectionLimit;

    private Integer channelCacheSize;

    private CachingConnectionFactory.CacheMode cacheMode;

    private Long channelCheckoutTimeout;

}
