package cn.maple.webclient.config;

import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.webclient.testsupport.GXWebClientTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRACE level header output must mask secrets and the User-Agent must not leak
 * the server operating system.
 */
class GXWebClientLogMaskTest {
    private static final String SECRET_TOKEN = "secret-token-value";

    private final GXWebClientConfig config = new GXWebClientConfig();

    @Test
    void masksSensitiveHeadersInTraceLogs() {
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, new MockEnvironment(), null)
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Set-Cookie", "session=" + SECRET_TOKEN)
                        .header("Authorization", SECRET_TOKEN)
                        .body("ok")
                        .build()))
                .build();

        try (GXWebClientTestSupport.CapturedLogs logs =
                     GXWebClientTestSupport.capture(GXWebClientConfig.class.getName())) {
            webClient.get().uri("http://127.0.0.1/ping")
                    .header(GXCommonConstant.X_AUTH_TOKEN, SECRET_TOKEN)
                    .header("Cookie", "session=" + SECRET_TOKEN)
                    .header("authorization", SECRET_TOKEN)
                    .retrieve().bodyToMono(String.class).block();

            assertThat(logs.contains("******")).isTrue();
            assertThat(logs.messages()).noneSatisfy(message ->
                    assertThat(message).contains(SECRET_TOKEN));
            assertThat(logs.messages()).anySatisfy(message -> {
                if (message.contains("Authorization=")) {
                    assertThat(message).contains("******");
                }
            });
        }
    }

    @Test
    void userAgentExcludesServerOperatingSystem() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, new MockEnvironment(), null)
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();

        webClient.get().uri("http://127.0.0.1/ping").retrieve().bodyToMono(String.class).block();

        String userAgent = captured.get().headers().getFirst("User-Agent");
        assertThat(userAgent).isEqualTo("Maple-Leaf-WebClient/1.0");
        assertThat(userAgent).doesNotContain(System.getProperty("os.name", "unknown"));
    }

    @Test
    void appendsConfiguredUserAgentSuffix() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.web.client.user-agent-suffix", "billing");
        WebClient webClient = GXWebClientTestSupport.plainBuilder(config, environment, null)
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                })
                .build();

        webClient.get().uri("http://127.0.0.1/ping").retrieve().bodyToMono(String.class).block();

        assertThat(captured.get().headers().getFirst("User-Agent"))
                .isEqualTo("Maple-Leaf-WebClient/1.0+billing");
    }
}
