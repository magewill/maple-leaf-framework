package cn.maple.rabbitmq.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;

@Setter
@Getter
public class GXRabbitMQProperties extends RabbitProperties {
    private String defaultQueueName = "maple.default.queue";

    private Integer connectionLimit;

    private Integer channelCacheSize;

    private CachingConnectionFactory.CacheMode cacheMode;

    private Long channelCheckoutTimeout;

}
