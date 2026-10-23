package cn.maple.webclient.config;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.webclient.testsupport.GXWebClientTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Retry guard matrix: safe methods keep the historical 1+3 retry behaviour,
 * non idempotent methods are no longer replayed by default.
 */
class GXWebClientRetryGuardTest {
    private static final String UNAVAILABLE_BODY = "{\"message\":\"remote unavailable\"}";

    private final GXWebClientConfig config = new GXWebClientConfig();

    @Test
    void doesNotRetryNonIdempotentMethodByDefault() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = client(environment(), attempts);

        Throwable error = catchThrowable(() -> webClient.post().uri("http://127.0.0.1/ping")
                .bodyValue("payload")
                .retrieve().bodyToMono(String.class).block());

        assertThat(error).isInstanceOf(GXBusinessException.class);
        assertThat(((GXBusinessException) error).getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(attempts).hasValue(1);
    }

    @Test
    void retriesAgainWhenNonIdempotentMethodIsExplicitlyEnabled() {
        AtomicInteger attempts = new AtomicInteger();
        MockEnvironment environment = environment()
                .withProperty("maple.framework.web.client.retry-methods", "GET,HEAD,OPTIONS,POST");
        WebClient webClient = client(environment, attempts);

        Throwable error = catchThrowable(() -> webClient.post().uri("http://127.0.0.1/ping")
                .bodyValue("payload")
                .retrieve().bodyToMono(String.class).block());

        assertThat(error).isInstanceOf(GXBusinessException.class);
        assertThat(attempts).hasValue(4);
    }

    @Test
    void keepsRetryContractForSafeMethod() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = client(environment(), attempts);

        Throwable error = catchThrowable(() -> webClient.get().uri("http://127.0.0.1/ping")
                .retrieve().bodyToMono(String.class).block());

        assertThat(error).isInstanceOf(GXBusinessException.class);
        GXBusinessException businessException = (GXBusinessException) error;
        assertThat(businessException.getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(businessException.getMessage()).contains("remote unavailable");
        assertThat(attempts).hasValue(4);
    }

    @Test
    void disablesRetryWhenMaxAttemptsIsZero() {
        AtomicInteger attempts = new AtomicInteger();
        MockEnvironment environment = environment()
                .withProperty("maple.framework.web.client.retry-max-attempts", "0");
        WebClient webClient = client(environment, attempts);

        Throwable error = catchThrowable(() -> webClient.get().uri("http://127.0.0.1/ping")
                .retrieve().bodyToMono(String.class).block());

        assertThat(error).isInstanceOf(GXBusinessException.class);
        assertThat(attempts).hasValue(1);
    }

    private MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("maple.framework.web.client.retry-delay-millis", "10");
    }

    private WebClient client(MockEnvironment environment, AtomicInteger attempts) {
        return GXWebClientTestSupport.plainBuilder(config, environment, null)
                .exchangeFunction(request -> Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                            .header("Content-Type", "application/json")
                            .body(UNAVAILABLE_BODY)
                            .build());
                }))
                .build();
    }
}
