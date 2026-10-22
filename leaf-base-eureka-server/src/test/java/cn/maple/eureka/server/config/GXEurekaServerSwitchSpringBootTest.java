package cn.maple.eureka.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the registry endpoints only exist when the switch is enabled.
 */
class GXEurekaServerSwitchSpringBootTest {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void registryEndpointsAreAbsentWhenSwitchIsMissing() throws Exception {
        var port = freePort();

        try (var ignored = start(port, null)) {
            assertThat(statusOfApps(port)).isEqualTo(404);
        }
    }

    @Test
    void registryEndpointsArePresentWhenSwitchIsEnabled() throws Exception {
        var port = freePort();

        try (var ignored = start(port, "true")) {
            assertThat(statusOfApps(port)).isEqualTo(200);
        }
    }

    /**
     * Regression for F-15 / FR-019: with the switch missing and no {@code eureka.client.*} configured,
     * the environment post-processor must default the Eureka client to disabled, otherwise the
     * application would try to register itself with an unreachable registry.
     */
    @Test
    void applicationStartsWhenSwitchIsMissingAndClientPropertiesAreUnset() throws Exception {
        var port = freePort();

        try (var context = startWithDefaultClient(port)) {
            assertThat(context.getEnvironment().getProperty("eureka.client.enabled")).isEqualTo("false");
            assertThat(statusOfApps(port)).isEqualTo(404);
        }
    }

    /**
     * Regression for FR-008 / plan D-05: a switch-only single-machine registry must neither register
     * itself nor fetch a registry from the unconfigured default peer zone.
     */
    @Test
    void switchOnlySingleMachineDefaultsToNoRegistration() throws Exception {
        var port = freePort();

        try (var context = startSwitchOnly(port)) {
            var clientConfig = context.getBean(EurekaClientConfigBean.class);

            assertThat(context.getEnvironment().getProperty("eureka.client.register-with-eureka")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("eureka.client.fetch-registry")).isEqualTo("false");
            assertThat(clientConfig.shouldRegisterWithEureka()).isFalse();
            assertThat(clientConfig.shouldFetchRegistry()).isFalse();
            assertThat(statusOfApps(port)).isEqualTo(200);
        }
    }

    private AutoCloseable start(int port, String switchValue) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.port", String.valueOf(port));
        properties.put("spring.main.banner-mode", "off");
        properties.put("eureka.client.register-with-eureka", "false");
        properties.put("eureka.client.fetch-registry", "false");
        if (switchValue != null) {
            properties.put("maple.framework.enable.eureka-server", switchValue);
        }

        var context = new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(properties)
                .run();

        return context::close;
    }

    private ConfigurableApplicationContext startWithDefaultClient(int port) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.port", String.valueOf(port));
        properties.put("spring.main.banner-mode", "off");

        return new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(properties)
                .run();
    }

    private ConfigurableApplicationContext startSwitchOnly(int port) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.port", String.valueOf(port));
        properties.put("spring.main.banner-mode", "off");
        properties.put("maple.framework.enable.eureka-server", "true");

        return new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(properties)
                .run();
    }

    private int statusOfApps(int port) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/eureka/apps"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
