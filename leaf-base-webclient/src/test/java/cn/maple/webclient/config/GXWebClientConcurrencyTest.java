package cn.maple.webclient.config;

import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.webclient.service.GXWebClientService;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency: 50 parallel calls keep their own trace/platform headers and the
 * builder stays free of shared mutable state.
 */
class GXWebClientConcurrencyTest {
    private final GXWebClientConfig config = new GXWebClientConfig();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        GXTraceIdContextUtils.removeTraceId();
    }

    @Test
    void concurrentRequestsKeepHeadersIsolated() throws Exception {
        List<Map.Entry<String, String>> observed = new CopyOnWriteArrayList<>();
        GXWebClientService service = new GXWebClientService() {
            @Override
            public String generateHttpAuthToken() {
                return "concurrency-token";
            }
        };
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, new MockEnvironment(), service)
                .exchangeFunction(request -> {
                    observed.add(Map.entry(traceOf(request), String.valueOf(request.method())));
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();

        int concurrency = 50;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < concurrency; i++) {
                String traceId = "trace-" + i;
                executor.submit(() -> {
                    GXTraceIdContextUtils.putTraceId(traceId);
                    try {
                        webClient.get().uri("http://127.0.0.1/ping").retrieve().bodyToMono(String.class).block();
                    } finally {
                        GXTraceIdContextUtils.removeTraceId();
                    }
                });
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(observed).hasSize(concurrency);
        assertThat(observed.stream().map(Map.Entry::getKey).distinct().count()).isEqualTo(concurrency);
    }

    @Test
    void concurrentBuilderCreationDoesNotShareState() {
        var first = GXWebClientTestSupport.plainBuilder(config, new MockEnvironment(), null);
        var second = GXWebClientTestSupport.plainBuilder(config, new MockEnvironment(), null);

        assertThat(first).isNotSameAs(second);
    }

    private static String traceOf(ClientRequest request) {
        String traceId = request.headers().getFirst(GXTraceIdContextUtils.TRACE_ID_KEY);
        return traceId == null ? "" : traceId;
    }
}
