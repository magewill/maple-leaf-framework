package cn.maple.sso.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.sso.properties.GXUrlWhiteListsConfigProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Local file URL whitelist, activated by {@code sso.config.source=local}.
 */
@Data
@Slf4j
@Component
@SuppressWarnings("all")
@EqualsAndHashCode(callSuper = true)
@ConditionalOnProperty(name = "sso.config.source", havingValue = "local")
@PropertySource(value = {"classpath:/${spring.profiles.active:dev}/url-white-lists.yml"},
        factory = GXYamlPropertySourceFactory.class, ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "url")
public class GXLocalUrlWhiteListsConfigProperties extends GXUrlWhiteListsConfigProperties {
    public GXLocalUrlWhiteListsConfigProperties() {
        log.info("URL whitelist config source: local.");
    }
}
