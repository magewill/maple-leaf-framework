package cn.maple.rabbitmq.properties.nacos;

import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@SuppressWarnings("all")
@EqualsAndHashCode(callSuper = true)
@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@NacosConfigurationProperties(dataId = "rabbit.yml",
        prefix = "spring.rabbitmq",
        groupId = "${spring.cloud.nacos.config.group:${nacos.config.group:}}",
        type = ConfigType.YAML,
        autoRefreshed = true,
        properties = @NacosProperties(
                serverAddr = "${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:}}",
                namespace = "${spring.cloud.nacos.config.namespace:${nacos.config.namespace:}}",
                username = "${spring.cloud.nacos.username:${nacos.config.username:}}",
                password = "${spring.cloud.nacos.password:${nacos.config.password:}}"))
@ConfigurationProperties(prefix = "spring.rabbitmq")
public class GXNacosRabbitMQProperties extends GXRabbitMQProperties {
    public GXNacosRabbitMQProperties() {
        log.info("RabbitMQ数据源配置使用NACOS配置");
    }
}
