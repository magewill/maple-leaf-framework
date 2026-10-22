package cn.maple.eureka.client.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Applies the {@code maple.framework.enable.eureka-client} switch at property level.
 *
 * <p>Conditional auto-configuration alone cannot keep the Eureka client from being assembled: the
 * upstream auto-configuration carries its own defaults ({@code register-with-eureka=true} pointing at
 * {@code http://localhost:8761/eureka/}). When the switch is off, this processor therefore disables
 * the client itself, with the lowest possible precedence so an explicitly configured
 * {@code eureka.client.enabled} always wins. When the switch is on, nothing is written: the framework
 * must not supply registration addresses or registration flags.</p>
 *
 * <p>Environment post-processing runs before the logging system is initialised, so the message is
 * written through a deferred log and replayed once logging is ready.</p>
 */
public class GXEurekaClientDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String CLIENT_SWITCH = "maple.framework.enable.eureka-client";

    static final String CLIENT_ENABLED = "eureka.client.enabled";

    private static final String PROPERTY_SOURCE_NAME = "mapleEurekaClientDefaults";

    private final Log logger;

    public GXEurekaClientDefaultsPostProcessor(DeferredLogFactory logFactory) {
        this.logger = logFactory.getLog(GXEurekaClientDefaultsPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getProperty(CLIENT_SWITCH, Boolean.class, Boolean.TRUE)) {
            return;
        }

        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }

        if (environment.getProperty(CLIENT_ENABLED) == null) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME,
                    Map.of(CLIENT_ENABLED, "false")));
        }

        logger.info("Eureka client disabled via " + CLIENT_SWITCH + "=false");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
