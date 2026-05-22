package cn.maple.feign.config;

import cn.maple.feign.properties.GXCircuitBreakerProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.retry.RetryPolicy;

@AutoConfiguration
@ConditionalOnClass(FrameworkRetryCircuitBreakerFactory.class)
@EnableConfigurationProperties(GXCircuitBreakerProperties.class)
public class GXCircuitBreakerConfig {
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    @ConditionalOnMissingBean(name = "frameworkRetryCircuitBreakerCustomizer")
    @ConditionalOnProperty(
            prefix = "maple.framework.feign.circuit-breaker",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public Customizer<FrameworkRetryCircuitBreakerFactory> frameworkRetryCircuitBreakerCustomizer(
            GXCircuitBreakerProperties properties) {
        return factory -> factory.configureDefault(id -> new FrameworkRetryConfigBuilder(id)
                .retryPolicy(RetryPolicy.builder()
                        .maxRetries(properties.getMaxRetries())
                        .delay(properties.getBackoff())
                        .multiplier(properties.getBackoffMultiplier())
                        .build())
                .openTimeout(properties.getOpenTimeout())
                .resetTimeout(properties.getResetTimeout())
                .build());
    }
}
