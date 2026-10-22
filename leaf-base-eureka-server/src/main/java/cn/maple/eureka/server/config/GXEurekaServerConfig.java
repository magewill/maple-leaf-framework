package cn.maple.eureka.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.core.env.Environment;

/**
 * Spring Boot auto-configuration for Eureka Server.
 *
 * <p>The registry is a deployment role rather than a library capability, so it is only started when
 * {@code maple.framework.enable.eureka-server} is explicitly set to {@code true} in a servlet
 * application. Importing this module into a reactive application does not enable the registry.</p>
 */
@AutoConfiguration
@ConditionalOnClass(EnableEurekaServer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "maple.framework.enable.eureka-server", havingValue = "true", matchIfMissing = false)
@EnableEurekaServer
public class GXEurekaServerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXEurekaServerConfig.class);

    public GXEurekaServerConfig(Environment environment) {
        LOGGER.info("Eureka Server enabled via maple.framework.enable.eureka-server=true, server.port={} (0 = random port)",
                environment.getProperty("server.port", "<unset>"));
    }
}
