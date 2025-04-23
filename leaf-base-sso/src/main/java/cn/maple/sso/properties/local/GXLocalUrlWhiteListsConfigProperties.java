package cn.maple.sso.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.sso.properties.GXUrlWhiteListsConfigProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 多数据源属性
 */
@Data
@Slf4j
@Component
@SuppressWarnings("all")
@EqualsAndHashCode(callSuper = true)
@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@PropertySource(value = {"classpath:/${spring.profiles.active}/url-white-lists.yml"}, factory = GXYamlPropertySourceFactory.class, ignoreResourceNotFound = false)
@ConfigurationProperties(prefix = "url")
public class GXLocalUrlWhiteListsConfigProperties extends GXUrlWhiteListsConfigProperties {
    @NestedConfigurationProperty
    protected List<String> whiteLists = Collections.emptyList();

    public GXLocalUrlWhiteListsConfigProperties() {
        log.info("Url白名单的配置使用的是LOCAL配置");
    }

    public List<String> getWhiteLists() {
        return whiteLists;
    }
}
