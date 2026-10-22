package cn.maple.eureka.client.config;

import cn.maple.eureka.client.testapp.OrderAdminClient;
import cn.maple.eureka.client.testapp.OrderClient;
import cn.maple.eureka.client.testapp.TestApplication;
import cn.maple.feign.config.GXFeignConfig;
import cn.maple.webclient.config.GXWebClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClientSpecification;
import org.springframework.cloud.openfeign.loadbalancer.FeignLoadBalancerAutoConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GXEurekaClientConfigTest {
    @Test
    void autoConfigurationImportContainsEurekaClientConfig() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(inputStream).isNotNull();
            String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports).contains(GXEurekaClientConfig.class.getName());
        }
    }

    @Test
    void autoConfigurationDoesNotRegisterFeignClientsByItself() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestApplication.class)
                .withConfiguration(AutoConfigurations.of(
                        FeignAutoConfiguration.class,
                        FeignLoadBalancerAutoConfiguration.class,
                        GXFeignConfig.class,
                        GXWebClientConfig.class,
                        GXEurekaClientConfig.class))
                .withPropertyValues(
                        "eureka.client.enabled=false",
                        "spring.cloud.discovery.enabled=false",
                        "spring.cloud.service-registry.auto-registration.enabled=false")
                .run(context -> assertThat(context.getBeanFactory().containsBeanDefinition(OrderClient.class.getName()))
                        .isFalse());
    }

    @Test
    void userEnabledFeignClientsKeepSeparateContextConfigurations() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestApplication.class, UserFeignClientConfiguration.class)
                .withConfiguration(AutoConfigurations.of(
                        FeignAutoConfiguration.class,
                        FeignLoadBalancerAutoConfiguration.class,
                        GXFeignConfig.class,
                        GXWebClientConfig.class,
                        GXEurekaClientConfig.class))
                .withPropertyValues(
                        "eureka.client.enabled=false",
                        "spring.cloud.discovery.enabled=false",
                        "spring.cloud.service-registry.auto-registration.enabled=false")
                .run(context -> {
                    assertThat(context.getBeanFactory().containsBeanDefinition(OrderClient.class.getName()))
                            .isTrue();
                    assertThat(context.getBeanFactory().containsBeanDefinition(OrderAdminClient.class.getName()))
                            .isTrue();
                    assertThat(context.getBeanNamesForType(FeignClientSpecification.class))
                            .contains("order-service.FeignClientSpecification",
                                    "orderAdminClient.FeignClientSpecification");
                });
    }

    /**
     * The framework must not ship a {@code classpath:/application.yml}: it shares the configuration
     * location with the host application, so the effective value would depend on classpath order and
     * could silently override (or be silently discarded in favour of) the host configuration.
     */
    @Test
    void applicationYamlIsAbsentFromClasspathRoot() {
        assertThat(getClass().getClassLoader().getResource("application.yml")).isNull();
    }

    /**
     * Example configuration ships in {@code examples/} (a non config location) so it is never loaded
     * automatically and can be copied by the host application on purpose.
     */
    @Test
    void exampleConfigsAreShippedOutsideTheConfigRoot() {
        ClassLoader classLoader = getClass().getClassLoader();

        assertThat(classLoader.getResource("examples/eureka-client-default.yml")).isNotNull();
        assertThat(classLoader.getResource("examples/eureka-client-multi-registry.yml")).isNotNull();
        assertThat(classLoader.getResource("application.yml")).isNull();
    }

    @EnableFeignClients(clients = {OrderClient.class, OrderAdminClient.class})
    static class UserFeignClientConfiguration {
    }
}
