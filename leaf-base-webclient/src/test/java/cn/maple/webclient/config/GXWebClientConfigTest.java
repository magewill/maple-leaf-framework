package cn.maple.webclient.config;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.webclient.service.GXWebClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
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
                    assertThat(context).hasSingleBean(WebClient.class);
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

        WebClient webClient = config.webClientBuilder(serviceProvider(webClientService), jsonMapperProvider())
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
        WebClient webClient = config.webClientBuilder(emptyServiceProvider(), jsonMapperProvider())
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
