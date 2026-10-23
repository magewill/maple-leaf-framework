package cn.maple.webclient.config;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.webclient.testsupport.GXWebClientTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Logging contract: duration excludes retry backoff, terminal failures are always
 * logged and the Content-Length warning uses the configured limit.
 */
class GXWebClientMetricsContractTest {
    private static final Pattern RESPONSE_PATTERN = Pattern.compile(
            "durationMs=(\\d+), attempts=(\\d+), retryDelayMs=(\\d+)");

    private final GXWebClientConfig config = new GXWebClientConfig();

    @Test
    void durationExcludesRetryBackoff() {
        AtomicInteger attempts = new AtomicInteger();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.web.client.retry-delay-millis", "300")
                .withProperty("maple.framework.web.client.retry-max-attempts", "3");
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, environment, null)
                .exchangeFunction(request -> Mono.defer(() -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 3) {
                        return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                                .header("Content-Type", "application/json")
                                .body("{\"message\":\"busy\"}")
                                .build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("done").build());
                }))
                .build();

        try (GXWebClientTestSupport.CapturedLogs logs =
                     GXWebClientTestSupport.capture(GXWebClientConfig.class.getName())) {
            long start = System.currentTimeMillis();
            String body = webClient.get().uri("http://127.0.0.1/ping").retrieve().bodyToMono(String.class).block();
            long wallMillis = System.currentTimeMillis() - start;

            assertThat(body).isEqualTo("done");
            assertThat(attempts).hasValue(3);
            assertThat(wallMillis).isGreaterThanOrEqualTo(600L);

            List<String> responseLogs = logs.messages().stream()
                    .filter(message -> message.startsWith("HTTP response:"))
                    .toList();
            assertThat(responseLogs).isNotEmpty();
            Matcher matcher = RESPONSE_PATTERN.matcher(responseLogs.get(responseLogs.size() - 1));
            assertThat(matcher.find()).isTrue();
            long durationMs = Long.parseLong(matcher.group(1));
            long reportedAttempts = Long.parseLong(matcher.group(2));
            long retryDelayMs = Long.parseLong(matcher.group(3));

            assertThat(reportedAttempts).isEqualTo(3L);
            assertThat(retryDelayMs).isEqualTo(600L);
            assertThat(durationMs).isLessThan(600L);
        }
    }

    @Test
    void logsFailureWithDurationAndAttempts() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.web.client.retry-max-attempts", "0");
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, environment, null)
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Content-Type", "application/json")
                        .body("{\"message\":\"unavailable\"}")
                        .build()))
                .build();

        try (GXWebClientTestSupport.CapturedLogs logs =
                     GXWebClientTestSupport.capture(GXWebClientConfig.class.getName())) {
            Throwable error = catchThrowable(() -> webClient.get().uri("http://127.0.0.1/ping")
                    .retrieve().bodyToMono(String.class).block());

            assertThat(error).isInstanceOf(GXBusinessException.class);
            assertThat(logs.contains("HTTP request failed:")).isTrue();
            assertThat(logs.messages()).anySatisfy(message -> {
                if (message.startsWith("HTTP request failed:")) {
                    assertThat(message).contains("attempts=1");
                }
            });
        }
    }

    @Test
    void performsNoEnvironmentLookupOnRequestPath() {
        GXWebClientTestSupport.CountingEnvironment environment = new GXWebClientTestSupport.CountingEnvironment();
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, environment, null)
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build()))
                .build();

        environment.resetLookups();
        String body = webClient.get().uri("http://127.0.0.1/ping").retrieve().bodyToMono(String.class).block();

        assertThat(body).isEqualTo("ok");
        assertThat(environment.lookups()).isZero();
    }

    @Test
    void contentLengthThresholdUsesConfiguredLimit() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.web.client.memory-buffer-size-mb", "1");
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, environment, null)
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Length", String.valueOf(2L * 1024 * 1024))
                        .body("payload")
                        .build()))
                .build();

        try (GXWebClientTestSupport.CapturedLogs logs =
                     GXWebClientTestSupport.capture(GXWebClientConfig.class.getName())) {
            webClient.get().uri("http://127.0.0.1/ping").retrieve().bodyToMono(String.class).block();

            assertThat(logs.contains("Response body size exceeds configured limit")).isTrue();
        }
    }
}
