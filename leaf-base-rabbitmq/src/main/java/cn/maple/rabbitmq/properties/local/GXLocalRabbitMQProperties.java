package cn.maple.rabbitmq.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@SuppressWarnings("all")
@EqualsAndHashCode(callSuper = true)
@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@PropertySource(value = {"classpath:/${spring.profiles.active:dev}/rabbit.yml"}, factory = GXYamlPropertySourceFactory.class, ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "spring.rabbitmq")
public class GXLocalRabbitMQProperties extends GXRabbitMQProperties {
    public GXLocalRabbitMQProperties() {
        log.info("RabbitMQ数据源配置使用LOCAL配置");
    }
}
