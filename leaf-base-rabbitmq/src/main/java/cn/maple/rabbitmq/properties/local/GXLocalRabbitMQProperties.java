package cn.maple.rabbitmq.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * 本地配置文件来源，仅在 {@code maple.framework.rabbitmq.config-source=local} 时装配。
 *
 * <p>同时尝试两个位置：类路径 {@code classpath:/<profile>/rabbit.yml} 与外置目录
 * {@code file:./config/<profile>/rabbit.yml}；两处都存在时后者覆盖前者。</p>
 */
@Slf4j
@Primary
@Component
@EqualsAndHashCode(callSuper = true)
@ConditionalOnProperty(name = "maple.framework.rabbitmq.config-source", havingValue = "local")
@PropertySource(value = {
        "classpath:/${spring.profiles.active:dev}/rabbit.yml",
        "file:./config/${spring.profiles.active:dev}/rabbit.yml"
}, factory = GXYamlPropertySourceFactory.class, ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "spring.rabbitmq")
public class GXLocalRabbitMQProperties extends GXRabbitMQProperties {
    public GXLocalRabbitMQProperties() {
        log.info("RabbitMQ config source: local, resources=[classpath:/<profile>/rabbit.yml, file:./config/<profile>/rabbit.yml]");
    }
}
