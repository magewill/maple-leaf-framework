package cn.maple.seata.properties;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Loads local Seata configuration from the active profile directory.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingClass("com.alibaba.cloud.nacos.NacosConfigManager")
@PropertySource(
        value = "classpath:/${spring.profiles.active}/seata.yml",
        factory = GXYamlPropertySourceFactory.class,
        encoding = "utf-8",
        ignoreResourceNotFound = false
)
@ConfigurationProperties(prefix = "")
public class GXLocalSeataProperties {
}
