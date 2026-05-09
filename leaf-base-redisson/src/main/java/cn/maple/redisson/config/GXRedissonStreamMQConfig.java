package cn.maple.redisson.config;

import cn.maple.redisson.stream.GXRedissonStreamMQManager;
import cn.maple.redisson.properties.GXRedissonStreamMQProperties;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Redis Streams message queue components.
 */
@Configuration
@ConditionalOnExpression("${maple.framework.mq.redisson.enable:false} && ${maple.framework.mq.stream.redisson.enable:false}")
@ConditionalOnBean(name = "redissonMQClient")
public class GXRedissonStreamMQConfig {
    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean(GXRedissonStreamMQManager.class)
    public GXRedissonStreamMQManager redissonStreamMessageQueueManager(@Qualifier("redissonMQClient") RedissonClient redissonMQClient,
                                                                       GXRedissonStreamMQProperties props) {
        return new GXRedissonStreamMQManager(redissonMQClient, props);
    }
}
