package cn.maple.feign.config;

import cn.maple.feign.properties.GXCircuitBreakerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryAutoConfiguration;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryCircuitBreaker;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GXCircuitBreakerConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    GXCircuitBreakerConfig.class,
                    FrameworkRetryAutoConfiguration.class));

    @Test
    void autoConfigurationIsDiscoveredFromImports() {
        String imports = readAutoConfigurationImports();

        assertThat(imports).contains(GXCircuitBreakerConfig.class.getName());
    }

    @Test
    void createsDefaultFrameworkRetryCircuitBreakerCustomizer() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Customizer.class);

            FrameworkRetryCircuitBreaker circuitBreaker = createCircuitBreaker(context.getBean(CircuitBreakerFactory.class));

            assertThat(circuitBreaker.getCircuitBreakerPolicy()).isNotNull();
            assertThat(circuitBreaker.getCircuitBreakerPolicy().getOpenTimeout()).isEqualTo(Duration.ofSeconds(20));
            assertThat(circuitBreaker.getCircuitBreakerPolicy().getResetTimeout()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void appEnvironmentPropertiesOverrideCircuitBreakerDefaults() {
        contextRunner.withPropertyValues(
                        "maple.feign.circuit-breaker.max-retries=1",
                        "maple.feign.circuit-breaker.backoff=10ms",
                        "maple.feign.circuit-breaker.backoff-multiplier=1.5",
                        "maple.feign.circuit-breaker.open-timeout=2s",
                        "maple.feign.circuit-breaker.reset-timeout=1s")
                .run(context -> {
                    GXCircuitBreakerProperties properties = context.getBean(GXCircuitBreakerProperties.class);
                    FrameworkRetryCircuitBreaker circuitBreaker =
                            createCircuitBreaker(context.getBean(CircuitBreakerFactory.class));

                    assertThat(properties.getMaxRetries()).isEqualTo(1);
                    assertThat(properties.getBackoff()).isEqualTo(Duration.ofMillis(10));
                    assertThat(properties.getBackoffMultiplier()).isEqualTo(1.5);
                    assertThat(circuitBreaker.getCircuitBreakerPolicy().getOpenTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(circuitBreaker.getCircuitBreakerPolicy().getResetTimeout()).isEqualTo(Duration.ofSeconds(1));
                });
    }

    @Test
    void circuitBreakerCustomizerCanBeDisabled() {
        contextRunner.withPropertyValues("maple.feign.circuit-breaker.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(Customizer.class));
    }

    @Test
    void customCircuitBreakerCustomizerBacksOffFrameworkDefault() {
        contextRunner.withUserConfiguration(CustomCircuitBreakerCustomizerConfig.class)
                .run(context -> assertThat(context.getBean(Customizer.class))
                        .isSameAs(CustomCircuitBreakerCustomizerConfig.CUSTOMIZER));
    }

    @Test
    void userDefinedCustomizerWithDifferentBeanNameOverridesFrameworkDefaultWhenOrderedLater() {
        contextRunner.withUserConfiguration(UserOverrideCircuitBreakerCustomizerConfig.class)
                .run(context -> {
                    FrameworkRetryCircuitBreaker circuitBreaker =
                            createCircuitBreaker(context.getBean(CircuitBreakerFactory.class));

                    assertThat(circuitBreaker.getCircuitBreakerPolicy().getOpenTimeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(circuitBreaker.getCircuitBreakerPolicy().getResetTimeout()).isEqualTo(Duration.ofSeconds(2));
                });
    }

    private FrameworkRetryCircuitBreaker createCircuitBreaker(CircuitBreakerFactory<?, ?> factory) {
        assertThat(factory).isInstanceOf(FrameworkRetryCircuitBreakerFactory.class);
        return (FrameworkRetryCircuitBreaker) factory.create("test-service");
    }

    private String readAutoConfigurationImports() {
        try (var inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Configuration
    static class CustomCircuitBreakerCustomizerConfig {
        private static final Customizer<FrameworkRetryCircuitBreakerFactory> CUSTOMIZER = factory -> {
        };

        @Bean("frameworkRetryCircuitBreakerCustomizer")
        Customizer<FrameworkRetryCircuitBreakerFactory> customCircuitBreakerCustomizer() {
            return CUSTOMIZER;
        }
    }

    @Configuration
    static class UserOverrideCircuitBreakerCustomizerConfig {
        @Bean
        @Order(Ordered.LOWEST_PRECEDENCE)
        Customizer<FrameworkRetryCircuitBreakerFactory> userOverrideCircuitBreakerCustomizer() {
            return factory -> factory.configureDefault(id -> new org.springframework.cloud.circuitbreaker.retry.FrameworkRetryConfigBuilder(id)
                    .retryPolicy(org.springframework.core.retry.RetryPolicy.builder()
                            .maxRetries(1)
                            .delay(Duration.ofMillis(50))
                            .multiplier(1.2)
                            .build())
                    .openTimeout(Duration.ofSeconds(3))
                    .resetTimeout(Duration.ofSeconds(2))
                    .build());
        }
    }
}
