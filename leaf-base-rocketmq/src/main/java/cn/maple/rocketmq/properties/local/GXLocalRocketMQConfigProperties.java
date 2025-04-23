package cn.maple.rocketmq.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Data
@Slf4j
@Component
@SuppressWarnings("all")
@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@PropertySource(value = {"classpath:/${spring.profiles.active}/rocket-mq.yml"}, factory = GXYamlPropertySourceFactory.class, encoding = "utf-8", ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "rocketmq")
public class GXLocalRocketMQConfigProperties {
    public GXLocalRocketMQConfigProperties() {
        log.info("RocketMQ数据源的配置使用的是本地配置");
    }
}
