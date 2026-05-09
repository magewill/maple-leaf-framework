package cn.maple.redisson.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.redisson.properties.GXRedissonConnectProperties;
import cn.maple.redisson.properties.GXRedissonProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Slf4j
@Component
@EqualsAndHashCode(callSuper = true)
@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@PropertySource(value = {"classpath:/${spring.profiles.active}/redisson.yml"}, factory = GXYamlPropertySourceFactory.class, encoding = "utf-8", ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "redisson")
public class GXLocalRedissonProperties extends GXRedissonProperties {
    private Map<String, GXRedissonConnectProperties> config = new LinkedHashMap<>();

    public GXLocalRedissonProperties() {
        log.info("Redisson datasource config uses local configuration");
    }

    @Override
    public Map<String, GXRedissonConnectProperties> getConfig() {
        return config;
    }
}
