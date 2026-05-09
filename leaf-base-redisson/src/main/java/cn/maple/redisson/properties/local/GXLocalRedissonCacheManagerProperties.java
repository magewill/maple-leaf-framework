package cn.maple.redisson.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.redisson.properties.GXRedissonCacheManagerProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Data
@Slf4j
@Component
@EqualsAndHashCode(callSuper = true)
@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@PropertySource(value = {"classpath:/${spring.profiles.active}/redisson-cache-config.yml"}, factory = GXYamlPropertySourceFactory.class, encoding = "utf-8", ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "redisson")
public class GXLocalRedissonCacheManagerProperties extends GXRedissonCacheManagerProperties {
    public GXLocalRedissonCacheManagerProperties() {
        log.info("Redisson cache manager config uses local configuration");
    }
}
