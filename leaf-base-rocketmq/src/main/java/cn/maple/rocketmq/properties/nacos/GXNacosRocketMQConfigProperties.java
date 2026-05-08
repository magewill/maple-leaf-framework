package cn.maple.rocketmq.properties.nacos;

import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Slf4j
@Component
@SuppressWarnings("all")
@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@ConditionalOnProperty(name = "rocketmq.config-source", havingValue = "nacos", matchIfMissing = true)
@NacosConfigurationProperties(dataId = "rocket-mq.yml",
        groupId = "${spring.cloud.nacos.config.group:${nacos.config.group:}}",
        properties = @NacosProperties(
                serverAddr = "${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:}}",
                namespace = "${spring.cloud.nacos.config.namespace:${nacos.config.namespace:}}",
                username = "${spring.cloud.nacos.username:${nacos.config.username:}}",
                password = "${spring.cloud.nacos.password:${nacos.config.password:}}"))
@ConfigurationProperties(prefix = "rocketmq")
public class GXNacosRocketMQConfigProperties {
    public GXNacosRocketMQConfigProperties() {
        log.info("RocketMQ config source: nacos.");
    }
}
