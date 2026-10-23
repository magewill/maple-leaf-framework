package cn.maple.sso.properties.nacos;

import cn.maple.sso.properties.GXSSOConfigProperties;
import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Nacos configuration source, the default unless {@code sso.config.source=local}.
 */
@Data
@Slf4j
@Component
@SuppressWarnings("all")
@EqualsAndHashCode(callSuper = true)
@ConditionalOnProperty(name = "sso.config.source", havingValue = "nacos", matchIfMissing = true)
@NacosConfigurationProperties(autoRefreshed = true,
        dataId = "sso.yml",
        groupId = "${spring.cloud.nacos.config.group:${nacos.config.group:}}",
        properties = @NacosProperties(serverAddr = "${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:}}",
                namespace = "${spring.cloud.nacos.config.namespace:${nacos.config.namespace:}}",
                username = "${spring.cloud.nacos.username:${nacos.config.username:}}",
                password = "${spring.cloud.nacos.password:${nacos.config.password:}}"))
@ConfigurationProperties(prefix = "sso")
public class GXNacosSSOConfigProperties extends GXSSOConfigProperties {
    public GXNacosSSOConfigProperties() {
        log.info("SSO config source: nacos.");
    }
}
