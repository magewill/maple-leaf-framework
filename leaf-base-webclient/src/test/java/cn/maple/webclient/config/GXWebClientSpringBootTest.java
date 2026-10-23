package cn.maple.webclient.config;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.webclient.testsupport.GXWebClientTestSupport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Real assembly integration: bean contract plus end to end retry behaviour against
 * a loopback HTTP server.
 */
class GXWebClientSpringBootTest {

    @Test
    void exposesFrozenBeanContract() {
        new ApplicationContextRunner()
                .withUserConfiguration(GXWebClientConfig.class)
                .withPropertyValues("maple.framework.web.client.retry-delay-millis=10")
                .run(context -> {
                    assertThat(context).hasSingleBean(WebClient.class);
                    assertThat(context).hasBean("webClientBuilder");
                    assertThat(context).hasBean("webClientDirectBuilder");
                    assertThat(context).hasBean("httpServiceProxyFactory");
                    assertThat(context.getBean("webClientBuilder", WebClient.Builder.class))
                            .isNotSameAs(context.getBean("webClientBuilder", WebClient.Builder.class));
                    assertThat(context.getBean("webClientDirectBuilder", WebClient.Builder.class)).isNotNull();
                });
    }

    @Test
    void retriesSafeMethodEndToEnd() {
        AtomicInteger counter = new AtomicInteger();
        HttpServer server = GXWebClientTestSupport.startServer(HttpStatus.SERVICE_UNAVAILABLE.value(),
                "{\"msg\":\"down\"}", counter);
        try {
            new ApplicationContextRunner()
                    .withUserConfiguration(GXWebClientConfig.class)
                    .withPropertyValues("maple.framework.web.client.retry-delay-millis=10")
                    .run(context -> {
                        WebClient webClient = context.getBean("webClientDirectBuilder", WebClient.Builder.class).build();
                        Throwable error = catchThrowable(() -> webClient.get()
                                .uri(GXWebClientTestSupport.baseUrl(server) + "/ping")
                                .retrieve().bodyToMono(String.class).block());

                        assertThat(error).isInstanceOf(GXBusinessException.class);
                        assertThat(((GXBusinessException) error).getMessage()).contains("down");
                        assertThat(counter).hasValue(4);
                    });
        } finally {
            GXWebClientTestSupport.stop(server);
        }
    }

    @Test
    void doesNotReplayNonIdempotentMethodEndToEnd() {
        AtomicInteger counter = new AtomicInteger();
        HttpServer server = GXWebClientTestSupport.startServer(HttpStatus.SERVICE_UNAVAILABLE.value(),
                "{\"msg\":\"down\"}", counter);
        try {
            new ApplicationContextRunner()
                    .withUserConfiguration(GXWebClientConfig.class)
                    .withPropertyValues("maple.framework.web.client.retry-delay-millis=10")
                    .run(context -> {
                        WebClient webClient = context.getBean("webClientDirectBuilder", WebClient.Builder.class).build();
                        Throwable error = catchThrowable(() -> webClient.post()
                                .uri(GXWebClientTestSupport.baseUrl(server) + "/ping")
                                .bodyValue("payload")
                                .retrieve().bodyToMono(String.class).block());

                        assertThat(error).isInstanceOf(GXBusinessException.class);
                        assertThat(counter).hasValue(1);
                    });
        } finally {
            GXWebClientTestSupport.stop(server);
        }
    }
}
