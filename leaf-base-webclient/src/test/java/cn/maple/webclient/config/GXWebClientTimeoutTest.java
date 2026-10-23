package cn.maple.webclient.config;

import cn.maple.webclient.testsupport.GXWebClientTestSupport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Independent timeout wiring: the read timeout interrupts slow responses while
 * zero disables it, which keeps streaming style consumption working.
 */
class GXWebClientTimeoutTest {
    private static final String BODY = "{\"ok\":true}";

    private final GXWebClientConfig config = new GXWebClientConfig();

    @Test
    void readTimeoutInterruptsSlowResponse() {
        AtomicInteger counter = new AtomicInteger();
        HttpServer server = GXWebClientTestSupport.startServer(200, BODY, 1500L, counter);
        try {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("maple.framework.web.client.timeout-seconds", "1")
                    .withProperty("maple.framework.web.client.retry-max-attempts", "0");
            WebClient webClient = GXWebClientTestSupport.plainBuilder(config, environment, null).build();

            Throwable error = catchThrowable(() -> webClient.get()
                    .uri(GXWebClientTestSupport.baseUrl(server) + "/slow")
                    .retrieve().bodyToMono(String.class).block());

            assertThat(error).isNotNull();
        } finally {
            GXWebClientTestSupport.stop(server);
        }
    }

    @Test
    void zeroReadAndResponseTimeoutKeepsLongResponseUsable() {
        AtomicInteger counter = new AtomicInteger();
        HttpServer server = GXWebClientTestSupport.startServer(200, BODY, 1500L, counter);
        try {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("maple.framework.web.client.timeout-seconds", "1")
                    .withProperty("maple.framework.web.client.read-timeout-millis", "0")
                    .withProperty("maple.framework.web.client.response-timeout-millis", "0")
                    .withProperty("maple.framework.web.client.retry-max-attempts", "0");
            WebClient webClient = GXWebClientTestSupport.plainBuilder(config, environment, null).build();

            String body = webClient.get()
                    .uri(GXWebClientTestSupport.baseUrl(server) + "/slow")
                    .retrieve().bodyToMono(String.class).block();

            assertThat(body).isEqualTo(BODY);
        } finally {
            GXWebClientTestSupport.stop(server);
        }
    }

    @Test
    void rejectsIllegalTimeoutValue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.web.client.read-timeout-millis", "-1");

        assertThat(catchThrowable(() -> GXWebClientProperties.from(environment)))
                .hasMessageContaining("read-timeout-millis");
    }

    @Test
    void fallsBackToBaseTimeoutWhenSplitKeyMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.web.client.timeout-seconds", "7");

        GXWebClientProperties properties = GXWebClientProperties.from(environment);

        assertThat(properties.effectiveConnectTimeoutMillis()).isEqualTo(7000);
        assertThat(properties.effectiveReadTimeoutMillis()).isEqualTo(7000);
        assertThat(properties.effectiveWriteTimeoutMillis()).isEqualTo(7000);
        assertThat(properties.effectiveResponseTimeoutMillis()).isEqualTo(7000);
    }
}
