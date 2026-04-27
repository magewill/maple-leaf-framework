package cn.maple.sso.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.sso.properties.GXSSOConfigProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * 多数据源属性
 */
@Data
@Slf4j
@Component
@SuppressWarnings("all")
@EqualsAndHashCode(callSuper = true)
@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@PropertySource(value = {"classpath:/${spring.profiles.active}/sso.yml"}, factory = GXYamlPropertySourceFactory.class, ignoreResourceNotFound = false)
@ConfigurationProperties(prefix = "sso")
public class GXLocalSSOConfigProperties extends GXSSOConfigProperties {
    public GXLocalSSOConfigProperties() {
        log.info("SSO的配置使用的是LOCAL配置");
    }
}
