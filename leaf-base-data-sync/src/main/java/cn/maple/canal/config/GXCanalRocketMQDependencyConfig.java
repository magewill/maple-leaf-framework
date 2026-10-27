package cn.maple.canal.config;

import cn.maple.core.framework.exception.GXBusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Fails fast when RocketMQ consumption is requested but the dependency that provides it is missing.
 *
 * <p>{@code leaf-base-rocketmq} is an optional dependency, so applications that only use RabbitMQ are
 * not forced to carry the RocketMQ client. Selecting {@code rocketmq} without the dependency must not
 * silently fall back.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "canal", name = "mq-type", havingValue = "rocketmq")
@ConditionalOnMissingClass("org.apache.rocketmq.spring.core.RocketMQListener")
@ConditionalOnBooleanProperty(prefix = "canal", name = "consumer-enabled", havingValue = true, matchIfMissing = true)
public class GXCanalRocketMQDependencyConfig {
    public GXCanalRocketMQDependencyConfig() {
        throw new GXBusinessException("canal.mq-type=rocketmq requires leaf-base-rocketmq on the class path; "
                + "add leaf-base-rocketmq as a dependency or set canal.mq-type=rabbitmq");
    }
}
