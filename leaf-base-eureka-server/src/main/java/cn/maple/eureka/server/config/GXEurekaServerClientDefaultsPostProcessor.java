package cn.maple.eureka.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps the Eureka client side aligned with the role this application actually plays.
 *
 * <p>The Eureka server starter drags in the Eureka client auto-configuration, whose own defaults are
 * "register with {@code http://localhost:8761/eureka/} and fetch its registry". Two consequences are
 * handled here:</p>
 *
 * <ul>
 *   <li>When the application is <strong>not</strong> a servlet registry, nothing is registered and the
 *   client is disabled unless {@code eureka.client.enabled} was configured explicitly.</li>
 *   <li>When the application <strong>is</strong> a servlet registry, the position independent
 *   single machine semantics ({@code register-with-eureka=false}, {@code fetch-registry=false}) are
 *   supplied as lowest precedence defaults, so a switch-only deployment does not try to register
 *   itself to an unconfigured peer zone. Peer members opt in by setting them to {@code true}.</li>
 * </ul>
 */
public class GXEurekaServerClientDefaultsPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXEurekaServerClientDefaultsPostProcessor.class);

    static final String SERVER_SWITCH = "maple.framework.enable.eureka-server";

    static final String CLIENT_ENABLED = "eureka.client.enabled";

    static final String REGISTER_WITH_EUREKA = "eureka.client.register-with-eureka";

    static final String FETCH_REGISTRY = "eureka.client.fetch-registry";

    static final String WEB_APPLICATION_TYPE = "spring.main.web-application-type";

    private static final String PROPERTY_SOURCE_NAME = "mapleEurekaServerClientDefaults";

    private static final String SINGLE_MACHINE_PROPERTY_SOURCE_NAME = "mapleEurekaServerSingleMachineDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (isServletRegistry(environment, application)) {
            applySingleMachineDefaults(environment);
            return;
        }

        if (environment.getProperty(CLIENT_ENABLED) != null) {
            return;
        }

        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }

        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME,
                Map.of(CLIENT_ENABLED, "false")));
        LOGGER.info("Eureka client disabled by default because this application does not run as a servlet registry; set {} explicitly to keep client behaviour",
                CLIENT_ENABLED);
    }

    /**
     * Supplies the single machine defaults expected from a registry whose peer zone is not configured.
     * Values configured by the application always win: a key already present in the environment is
     * never overridden.
     */
    private void applySingleMachineDefaults(ConfigurableEnvironment environment) {
        if (environment.getPropertySources().contains(SINGLE_MACHINE_PROPERTY_SOURCE_NAME)) {
            return;
        }

        Map<String, Object> defaults = new LinkedHashMap<>();
        if (environment.getProperty(REGISTER_WITH_EUREKA) == null) {
            defaults.put(REGISTER_WITH_EUREKA, "false");
        }
        if (environment.getProperty(FETCH_REGISTRY) == null) {
            defaults.put(FETCH_REGISTRY, "false");
        }

        if (defaults.isEmpty()) {
            return;
        }

        environment.getPropertySources().addLast(new MapPropertySource(SINGLE_MACHINE_PROPERTY_SOURCE_NAME, defaults));
        LOGGER.info("Eureka Server runs as a standalone registry: {} default to false at lowest precedence; set them to true explicitly for peer replication",
                defaults.keySet());
    }

    /**
     * The registry auto-configuration requires a servlet web application, so the single machine
     * defaults only apply when both the switch and the servlet stack are present.
     */
    private boolean isServletRegistry(ConfigurableEnvironment environment, SpringApplication application) {
        boolean serverEnabled = environment.getProperty(SERVER_SWITCH, Boolean.class, Boolean.FALSE);

        return serverEnabled && isServletWebApplication(environment, application);
    }

    private boolean isServletWebApplication(ConfigurableEnvironment environment, SpringApplication application) {
        var configuredType = environment.getProperty(WEB_APPLICATION_TYPE);
        if (configuredType != null) {
            return "servlet".equalsIgnoreCase(configuredType.trim());
        }

        if (application != null && application.getWebApplicationType() != null) {
            return application.getWebApplicationType() == WebApplicationType.SERVLET;
        }

        return true;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
