package cn.maple.sso.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.sso.properties.GXSSOConfigProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Local file configuration source, activated by {@code sso.config.source=local}.
 *
 * <p>The previous class-path condition could never be true because this module depends on
 * {@code leaf-base-nacos} (which brings the Nacos annotations). The switch makes the local
 * variant reachable and testable, and keeps Nacos as the default.</p>
 */
@Data
@Slf4j
@Component
@SuppressWarnings("all")
@EqualsAndHashCode(callSuper = true)
@ConditionalOnProperty(name = "sso.config.source", havingValue = "local")
@PropertySource(value = {"classpath:/${spring.profiles.active:dev}/sso.yml"},
        factory = GXYamlPropertySourceFactory.class, ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "sso")
public class GXLocalSSOConfigProperties extends GXSSOConfigProperties {
    public GXLocalSSOConfigProperties() {
        log.info("SSO config source: local.");
    }
}
