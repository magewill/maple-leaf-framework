package cn.maple.eureka.client.config;

import cn.maple.feign.config.GXFeignConfig;
import cn.maple.webclient.config.GXWebClientConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Spring Boot auto-configuration for Eureka Client discovery.
 *
 * <p>Import this module to register the application with Eureka. Business
 * applications should enable Feign scanning explicitly with
 * {@code @EnableFeignClients} so their own package layout remains in control.</p>
 */
@AutoConfiguration(after = {GXFeignConfig.class, GXWebClientConfig.class, EurekaClientAutoConfiguration.class})
@ConditionalOnClass({FeignClient.class, EurekaClientAutoConfiguration.class})
public class GXEurekaClientConfig {
}
