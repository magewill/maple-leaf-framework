package cn.maple.rabbitmq.properties.nacos;

import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Nacos 配置来源变体，{@code maple.framework.rabbitmq.config-source} 未配置或等于 {@code nacos} 时装配。
 *
 * <p>真正的 {@code rabbit.yml} 拉取由 {@link cn.maple.rabbitmq.properties.GXRabbitMQEnvironmentPostProcessor}
 * 在环境准备阶段完成；本类只负责把最终 Environment 绑定为类型安全配置，并保持历史 Bean 身份。</p>
 */
@Slf4j
@Primary
@Component
@EqualsAndHashCode(callSuper = true)
@ConditionalOnProperty(name = "maple.framework.rabbitmq.config-source", havingValue = "nacos", matchIfMissing = true)
@ConfigurationProperties(prefix = "spring.rabbitmq")
public class GXNacosRabbitMQProperties extends GXRabbitMQProperties {
    public GXNacosRabbitMQProperties() {
        log.info("RabbitMQ config source: nacos, dataId=rabbit.yml");
    }
}
