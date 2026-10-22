package cn.maple.feign.config;

import cn.maple.feign.support.GXFeignTestHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GXFeignCircuitBreakerFallbackTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FeignAutoConfiguration.class,
                    GXFeignConfig.class,
                    GXCircuitBreakerConfig.class,
                    FrameworkRetryAutoConfiguration.class))
            .withPropertyValues(
                    "spring.cloud.openfeign.circuitbreaker.enabled=true",
                    "spring.cloud.openfeign.circuitbreaker.group.enabled=false",
                    "spring.cloud.openfeign.circuitbreaker.alphanumeric-ids.enabled=true");

    @Test
    void feignClientFallbackIsInvokedWhenRemoteCallFails() {
        contextRunner.withUserConfiguration(FallbackClientConfig.class)
                .run(context -> {
                    FallbackClient client = context.getBean(FallbackClient.class);

                    assertThat(client.ping("jack")).isEqualTo("fallback:jack");
                });
    }

    @Test
    void feignClientFallbackFactoryReceivesTheCauseWhenRemoteCallFails() {
        contextRunner.withUserConfiguration(FallbackFactoryClientConfig.class)
                .run(context -> {
                    FallbackFactoryClient client = context.getBean(FallbackFactoryClient.class);
                    ProductionFallbackFactory support = context.getBean(ProductionFallbackFactory.class);

                    assertThat(client.ping("jack")).startsWith("factory-fallback:jack:");
                    assertThat(support.getLastCause()).isNotNull();
                    assertThat(support.getLastCause().getMessage()).isNotBlank();
                });
    }

    @Test
    void fallbackSwallowsBusinessErrorAndSendsSingleRequest() {
        try (GXFeignTestHttpServer server = GXFeignTestHttpServer.start()) {
            server.stub("/bad-request", 400, "{\"code\":400,\"msg\":\"invalid parameter\"}");
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            FeignAutoConfiguration.class,
                            GXFeignConfig.class,
                            GXCircuitBreakerConfig.class,
                            FrameworkRetryAutoConfiguration.class))
                    .withPropertyValues(
                            "spring.cloud.openfeign.circuitbreaker.enabled=true",
                            "spring.cloud.openfeign.circuitbreaker.group.enabled=false",
                            "maple.framework.feign.error-decoder.enabled=true",
                            "gx.feign.test.url=" + server.baseUrl())
                    .withUserConfiguration(BusinessErrorClientConfig.class)
                    .run(context -> {
                        BusinessErrorClient client = context.getBean(BusinessErrorClient.class);

                        assertThat(client.badRequest()).isEqualTo("business-fallback");
                    });
            assertThat(server.hits("/bad-request")).isEqualTo(1);
        }
    }

    @Configuration
    @EnableFeignClients(clients = FallbackClient.class)
    static class FallbackClientConfig {
        @Bean
        ProductionFallbackClientFallback fallbackClientFallback() {
            return new ProductionFallbackClientFallback();
        }
    }

    @Configuration
    @EnableFeignClients(clients = FallbackFactoryClient.class)
    static class FallbackFactoryClientConfig {
        @Bean
        ProductionFallbackFactory fallbackFactorySupport() {
            return new ProductionFallbackFactory();
        }
    }

    @FeignClient(name = "fallback-client", url = "http://127.0.0.1:1", fallback = ProductionFallbackClientFallback.class)
    interface FallbackClient {
        @GetMapping("/ping")
        String ping(@RequestParam("name") String name);
    }

    static class ProductionFallbackClientFallback implements FallbackClient {
        @Override
        public String ping(String name) {
            return "fallback:" + name;
        }
    }

    @FeignClient(name = "fallback-factory-client", url = "http://127.0.0.1:1",
            fallbackFactory = ProductionFallbackFactory.class)
    interface FallbackFactoryClient {
        @GetMapping("/ping")
        String ping(@RequestParam("name") String name);
    }

    static class ProductionFallbackFactory implements FallbackFactory<FallbackFactoryClient> {
        private final AtomicReference<Throwable> lastCause = new AtomicReference<>();

        @Override
        public FallbackFactoryClient create(Throwable cause) {
            lastCause.set(cause);
            return new FallbackFactoryClientFallback(cause);
        }

        Throwable getLastCause() {
            return lastCause.get();
        }
    }

    static class FallbackFactoryClientFallback implements FallbackFactoryClient {
        private final Throwable cause;

        FallbackFactoryClientFallback(Throwable cause) {
            this.cause = Objects.requireNonNull(cause, "cause");
        }

        @Override
        public String ping(String name) {
            return "factory-fallback:" + name + ":" + cause.getClass().getSimpleName();
        }
    }

    @Configuration
    @EnableFeignClients(clients = BusinessErrorClient.class)
    static class BusinessErrorClientConfig {
        @Bean
        BusinessErrorFallback businessErrorFallback() {
            return new BusinessErrorFallback();
        }
    }

    @FeignClient(name = "fallback-business", url = "${gx.feign.test.url}",
            fallback = BusinessErrorFallback.class)
    interface BusinessErrorClient {
        @GetMapping("/bad-request")
        String badRequest();
    }

    static class BusinessErrorFallback implements BusinessErrorClient {
        @Override
        public String badRequest() {
            return "business-fallback";
        }
    }
}
