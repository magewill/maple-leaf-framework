package cn.maple.eureka.client.config;

import cn.maple.eureka.client.testapp.OrderClient;
import cn.maple.eureka.client.testapp.OrderAdminClient;
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

import static org.assertj.core.api.Assertions.assertThat;

class GXEurekaClientConfigTest {
    @Test
    void autoConfigurationImportContainsEurekaClientConfig() throws IOException {
        try (var inputStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(inputStream).isNotNull();
            String imports = new String(inputStream.readAllBytes());
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
                .run(context -> {
                    assertThat(context.getBeanFactory().containsBeanDefinition(OrderClient.class.getName()))
                            .isFalse();
                });
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

    @Test
    void applicationYamlContainsExpectedClientDefaults() throws IOException {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(inputStream).isNotNull();
            String yaml = new String(inputStream.readAllBytes());
            assertThat(yaml).contains("register-with-eureka: true");
            assertThat(yaml).contains("spring:\n  cloud:\n    openfeign:\n      circuitbreaker:\n        enabled: true");
            assertThat(yaml).contains("alphanumeric-ids:\n          enabled: true");
            assertThat(yaml).doesNotContain("feign.hystrix.enabled");
        }
    }

    @EnableFeignClients(clients = {OrderClient.class, OrderAdminClient.class})
    static class UserFeignClientConfiguration {
    }
}
