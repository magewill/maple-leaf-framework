package cn.maple.webclient.config;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.webclient.service.GXWebClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GXWebClientConfigTest {
    private final GXWebClientConfig config = new GXWebClientConfig();

    @Test
    void webClientBuilderBeanIsPrototypeScoped() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXWebClientConfig.class, TestWebClientServiceConfig.class)
                .run(context -> {
                    WebClient.Builder firstBuilder = context.getBeanProvider(WebClient.Builder.class).getObject();
                    WebClient.Builder secondBuilder = context.getBeanProvider(WebClient.Builder.class).getObject();

                    assertThat(firstBuilder).isNotSameAs(secondBuilder);
                    assertThat(context).hasBean("mapleWebClientConnectionProvider");
                    assertThat(context).hasBean("mapleWebClientConnector");
                    assertThat(context.getBean("mapleWebClientConnectionProvider", ConnectionProvider.class))
                            .isSameAs(context.getBean("mapleWebClientConnectionProvider", ConnectionProvider.class));
                    assertThat(context.getBean("mapleWebClientConnector", ReactorClientHttpConnector.class))
                            .isSameAs(context.getBean("mapleWebClientConnector", ReactorClientHttpConnector.class));
                    assertThat(context).hasSingleBean(WebClient.class);
                });
    }

    @Test
    void webClientBuilderUsesConfiguredConnectionPoolLimitWhenPresent() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXWebClientConfig.class, TestWebClientServiceConfig.class)
                .withPropertyValues("maple.framework.web.client.max-connections=123")
                .run(context -> {
                    ConnectionProvider connectionProvider = context.getBean("mapleWebClientConnectionProvider", ConnectionProvider.class);
                    assertThat(connectionProvider.maxConnections()).isEqualTo(123);
                });
    }

    @Test
    void webClientFailsFastWhenTokenGenerationFails() {
        GXWebClientService webClientService = new GXWebClientService() {
            @Override
            public String getTraceId() {
                return "trace-1";
            }

            @Override
            public String generateHttpAuthToken() {
                throw new GXBusinessException("missing token");
            }
        };

        WebClient webClient = webClientBuilder(serviceProvider(webClientService))
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build()))
                .build();

        StepVerifier.create(webClient.get().uri("https://example.test").retrieve().bodyToMono(String.class))
                .expectErrorSatisfies(throwable -> {
                    assertThat(throwable).isInstanceOf(GXBusinessException.class);
                    assertThat(throwable).hasMessage("Generate WebClient auth token failed");
                })
                .verify();
    }

    @Test
    void webClientRetriesRetryableHttpStatusBeforeReturningBusinessException() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = webClientBuilder(emptyServiceProvider())
                .exchangeFunction(request -> Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                            .header("Content-Type", "application/json")
                            .body("{\"message\":\"remote unavailable\"}")
                            .build());
                }))
                .build();

        StepVerifier.create(webClient.get().uri("https://example.test").retrieve().bodyToMono(String.class))
                .expectErrorSatisfies(throwable -> {
                    assertThat(throwable).isInstanceOf(GXBusinessException.class);
                    GXBusinessException businessException = (GXBusinessException) throwable;
                    assertThat(businessException.getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(businessException.getMessage()).contains("remote unavailable");
                })
                .verify();

        assertThat(attempts).hasValue(4);
    }

    private WebClient.Builder webClientBuilder(ObjectProvider<GXWebClientService> webClientServiceProvider) {
        MockEnvironment environment = new MockEnvironment();
        ConnectionProvider connectionProvider = config.mapleWebClientConnectionProvider(environment);
        return config.webClientBuilder(
                webClientServiceProvider,
                jsonMapperProvider(),
                config.mapleWebClientConnector(connectionProvider, environment),
                environment);
    }

    private ObjectProvider<GXWebClientService> serviceProvider(GXWebClientService webClientService) {
        return new ObjectProvider<>() {
            @Override
            public GXWebClientService getIfAvailable() {
                return webClientService;
            }
        };
    }

    private ObjectProvider<GXWebClientService> emptyServiceProvider() {
        return new ObjectProvider<>() {
            @Override
            public GXWebClientService getIfAvailable() {
                return null;
            }
        };
    }

    private ObjectProvider<JsonMapper> jsonMapperProvider() {
        return new ObjectProvider<>() {
            @Override
            public JsonMapper getIfAvailable() {
                return null;
            }
        };
    }

    @Configuration
    static class TestWebClientServiceConfig {
        @Bean
        GXWebClientService gxWebClientService() {
            return new GXWebClientService() {
                @Override
                public String generateHttpAuthToken() {
                    return "token";
                }

                @Override
                public String getTraceId() {
                    return "trace-id";
                }
            };
        }
    }
}
