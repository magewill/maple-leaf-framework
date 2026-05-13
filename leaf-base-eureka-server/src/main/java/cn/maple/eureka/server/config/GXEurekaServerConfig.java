package cn.maple.eureka.server.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Spring Boot auto-configuration for Eureka Server.
 *
 * <p>Import this module to start an application as a Eureka registry server.</p>
 */
@AutoConfiguration
@ConditionalOnClass(EnableEurekaServer.class)
@EnableEurekaServer
public class GXEurekaServerConfig {
}
