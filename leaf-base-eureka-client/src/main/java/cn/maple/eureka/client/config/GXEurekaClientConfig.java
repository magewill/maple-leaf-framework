package cn.maple.eureka.client.config;

import cn.maple.feign.config.GXFeignConfig;
import cn.maple.webclient.config.GXWebClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring Boot auto-configuration for Eureka Client discovery.
 *
 * <p>Import this module to register the application with Eureka. Business
 * applications should enable Feign scanning explicitly with
 * {@code @EnableFeignClients} so their own package layout remains in control.</p>
 *
 * <p>The framework contributes no registration defaults: neither the registry address nor the
 * registration flag is supplied here. When the switch is on and no default zone is configured, a
 * single warning is emitted so the implicit fallback of Spring Cloud is visible instead of silent.
 * Set {@code maple.framework.enable.eureka-client=false} to keep the dependency without registering.</p>
 */
@AutoConfiguration(after = {GXFeignConfig.class, GXWebClientConfig.class, EurekaClientAutoConfiguration.class})
@ConditionalOnClass({FeignClient.class, WebClient.class, EurekaClientAutoConfiguration.class})
@ConditionalOnProperty(prefix = "maple.framework.enable", name = "eureka-client", havingValue = "true",
        matchIfMissing = true)
public class GXEurekaClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXEurekaClientConfig.class);

    private static final String DEFAULT_ZONE_KEY = "eureka.client.service-url.defaultZone";

    private static final String DEFAULT_ZONE_INDEXED_KEY = "eureka.client.service-url[defaultZone]";

    private static final String CLIENT_ENABLED_KEY = "eureka.client.enabled";

    public GXEurekaClientConfig(Environment environment) {
        if (shouldWarnAboutDefaultZone(environment)) {
            LOGGER.warn("Eureka client default zone is not configured ({}); registration will target the Spring Cloud default.",
                    DEFAULT_ZONE_KEY);
        }
    }

    /**
     * The warning only makes sense when the client is actually going to register: with
     * {@code eureka.client.enabled=false} nothing is registered, so the message would be a false signal.
     */
    static boolean shouldWarnAboutDefaultZone(Environment environment) {
        return isClientEnabled(environment) && !isDefaultZoneConfigured(environment);
    }

    static boolean isDefaultZoneConfigured(Environment environment) {
        return hasText(environment.getProperty(DEFAULT_ZONE_KEY))
                || hasText(environment.getProperty(DEFAULT_ZONE_INDEXED_KEY));
    }

    private static boolean isClientEnabled(Environment environment) {
        return environment.getProperty(CLIENT_ENABLED_KEY, Boolean.class, Boolean.TRUE);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
