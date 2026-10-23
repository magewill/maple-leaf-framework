package cn.maple.webclient.config;

import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.webclient.service.GXWebClientContextSnapshot;
import cn.maple.webclient.testsupport.GXWebClientTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context propagation contract: missing context is never silent and an explicit
 * snapshot restores the trace, platform and token headers.
 */
class GXWebClientContextPropagationTest {
    private final GXWebClientConfig config = new GXWebClientConfig();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        GXTraceIdContextUtils.removeTraceId();
    }

    @Test
    void warnsWhenContextDroppedOnAsyncThread() {
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, new MockEnvironment(), null)
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build()))
                .build();

        try (GXWebClientTestSupport.CapturedLogs logs =
                     GXWebClientTestSupport.capture(GXWebClientConfig.class.getName())) {
            for (int i = 0; i < 3; i++) {
                webClient.get().uri("http://127.0.0.1/ping").retrieve().bodyToMono(String.class).block();
            }

            assertThat(logs.count("HTTP request dropped context")).isEqualTo(1L);
        }
    }

    @Test
    void warnsOnEveryRequestWhenThrottleWindowIsZero() {
        config.contextWarningIntervalMillis = 0L;
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, new MockEnvironment(), null)
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build()))
                .build();

        try (GXWebClientTestSupport.CapturedLogs logs =
                     GXWebClientTestSupport.capture(GXWebClientConfig.class.getName())) {
            for (int i = 0; i < 3; i++) {
                webClient.get().uri("http://127.0.0.1/ping").retrieve().bodyToMono(String.class).block();
            }

            assertThat(logs.count("HTTP request dropped context")).isEqualTo(3L);
        }
    }

    @Test
    void snapshotRestoresHeadersOnAsyncThread() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, new MockEnvironment(), null)
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();
        GXWebClientContextSnapshot snapshot =
                new GXWebClientContextSnapshot("propagated-trace", "propagated-app", "propagated-token", 1L);

        webClient.get().uri("http://127.0.0.1/ping")
                .retrieve().bodyToMono(String.class)
                .contextWrite(context -> context.put(GXWebClientContextSnapshot.ATTRIBUTE_KEY, snapshot))
                .block();

        ClientRequest request = captured.get();
        assertThat(request.attribute(GXWebClientContextSnapshot.ATTRIBUTE_KEY)).isNotNull();
        assertThat(request.headers().getFirst(GXTraceIdContextUtils.TRACE_ID_KEY)).isEqualTo("propagated-trace");
        assertThat(request.headers().getFirst("platform")).isEqualTo("propagated-app");
        assertThat(request.headers().getFirst("X-Auth-Token")).isEqualTo("propagated-token");
    }
}
