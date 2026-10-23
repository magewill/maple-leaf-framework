package cn.maple.webclient.config;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.webclient.testsupport.GXWebClientTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Load balancer addressing guard: direct hosts are rejected before any network
 * attempt, allow listed dotted service ids pass through, and disabling the guard
 * restores the legacy behaviour.
 */
class GXWebClientLbGuardTest {
    private final GXWebClientConfig config = new GXWebClientConfig();

    @Test
    void rejectsDirectHostWithClearError() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = GXWebClientTestSupport.lbBuilder(config, new MockEnvironment(), null)
                .exchangeFunction(okResponse(attempts))
                .build();

        Throwable error = catchThrowable(() -> webClient.get().uri("https://api.example.com/ping")
                .retrieve().bodyToMono(String.class).block());

        assertThat(error).isInstanceOf(GXBusinessException.class);
        GXBusinessException businessException = (GXBusinessException) error;
        assertThat(businessException.getCode()).isEqualTo(400);
        assertThat(businessException.getMessage()).contains("服务名寻址");
        assertThat(attempts).hasValue(0);
    }

    @Test
    void rejectsIpAndLocalhostHosts() {
        assertGuardRejects("http://127.0.0.1:8080/ping");
        assertGuardRejects("http://localhost:8080/ping");
    }

    @Test
    void allowsSimpleServiceName() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = GXWebClientTestSupport.lbBuilder(config, new MockEnvironment(), null)
                .exchangeFunction(okResponse(attempts))
                .build();

        String body = webClient.get().uri("http://my-service/ping").retrieve().bodyToMono(String.class).block();

        assertThat(body).isEqualTo("ok");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void allowsDottedServiceIdViaAllowList() {
        AtomicInteger attempts = new AtomicInteger();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.web.client.lb-guard-allow-hosts", "svc.ns.svc.cluster.local");
        WebClient webClient = GXWebClientTestSupport.lbBuilder(config, environment, null)
                .exchangeFunction(okResponse(attempts))
                .build();

        String body = webClient.get().uri("http://svc.ns.svc.cluster.local/ping")
                .retrieve().bodyToMono(String.class).block();

        assertThat(body).isEqualTo("ok");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void fallsBackToLegacyBehaviorWhenGuardDisabled() {
        AtomicInteger attempts = new AtomicInteger();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.web.client.lb-guard-enabled", "false");
        WebClient webClient = GXWebClientTestSupport.lbBuilder(config, environment, null)
                .exchangeFunction(okResponse(attempts))
                .build();

        String body = webClient.get().uri("https://api.example.com/ping")
                .retrieve().bodyToMono(String.class).block();

        assertThat(body).isEqualTo("ok");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void disablesGuardViaUnifiedFeatureSwitch() {
        AtomicInteger attempts = new AtomicInteger();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.enable.webclient-lb-guard", "false");
        WebClient webClient = GXWebClientTestSupport.lbBuilder(config, environment, null)
                .exchangeFunction(okResponse(attempts))
                .build();

        String body = webClient.get().uri("https://api.example.com/ping")
                .retrieve().bodyToMono(String.class).block();

        assertThat(body).isEqualTo("ok");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void unifiedFeatureSwitchWinsOverLegacyAlias() {
        AtomicInteger attempts = new AtomicInteger();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.enable.webclient-lb-guard", "true")
                .withProperty("maple.framework.web.client.lb-guard-enabled", "false");
        WebClient webClient = GXWebClientTestSupport.lbBuilder(config, environment, null)
                .exchangeFunction(okResponse(attempts))
                .build();

        Throwable error = catchThrowable(() -> webClient.get().uri("https://api.example.com/ping")
                .retrieve().bodyToMono(String.class).block());

        assertThat(error).isInstanceOf(GXBusinessException.class);
        assertThat(((GXBusinessException) error).getCode()).isEqualTo(400);
        assertThat(attempts).hasValue(0);
    }

    @Test
    void allowsAllowListEntryConfiguredWithPort() {
        AtomicInteger attempts = new AtomicInteger();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.web.client.lb-guard-allow-hosts", "api.example.com:8443");
        WebClient webClient = GXWebClientTestSupport.lbBuilder(config, environment, null)
                .exchangeFunction(okResponse(attempts))
                .build();

        String body = webClient.get().uri("https://api.example.com:8443/ping")
                .retrieve().bodyToMono(String.class).block();

        assertThat(body).isEqualTo("ok");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void ignoresInvalidAllowListEntryAndWarns() {
        AtomicInteger attempts = new AtomicInteger();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.web.client.lb-guard-allow-hosts", "http://bad/path");

        try (GXWebClientTestSupport.CapturedLogs logs =
                     GXWebClientTestSupport.capture(GXWebClientConfig.class.getName())) {
            WebClient webClient = GXWebClientTestSupport.lbBuilder(config, environment, null)
                    .exchangeFunction(okResponse(attempts))
                    .build();
            Throwable error = catchThrowable(() -> webClient.get().uri("https://api.example.com/ping")
                    .retrieve().bodyToMono(String.class).block());

            assertThat(error).isInstanceOf(GXBusinessException.class);
            assertThat(((GXBusinessException) error).getCode()).isEqualTo(400);
            assertThat(attempts).hasValue(0);
            assertThat(logs.contains("Invalid lb-guard-allow-hosts entries ignored")).isTrue();
        }
    }

    @Test
    void directBuilderAllowsDirectHost() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = GXWebClientTestSupport.directBuilder(config, new MockEnvironment(), null)
                .exchangeFunction(okResponse(attempts))
                .build();

        String body = webClient.get().uri("https://api.example.com/ping")
                .retrieve().bodyToMono(String.class).block();

        assertThat(body).isEqualTo("ok");
        assertThat(attempts).hasValue(1);
    }

    private void assertGuardRejects(String uri) {
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = GXWebClientTestSupport.lbBuilder(config, new MockEnvironment(), null)
                .exchangeFunction(okResponse(attempts))
                .build();

        Throwable error = catchThrowable(() -> webClient.get().uri(uri)
                .retrieve().bodyToMono(String.class).block());

        assertThat(error).isInstanceOf(GXBusinessException.class);
        assertThat(((GXBusinessException) error).getCode()).isEqualTo(400);
        assertThat(attempts).hasValue(0);
    }

    private static ExchangeFunction okResponse(AtomicInteger attempts) {
        return request -> {
            attempts.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
        };
    }
}
