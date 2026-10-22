package cn.maple.eureka.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
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
 * Starts two independent Eureka Server contexts in-process to measure peer behaviour under
 * two configurations: the {@code register-with-eureka=false} / {@code fetch-registry=false}
 * defaults shipped by this module, and the peer configuration used by the cluster example.
 */
class GXEurekaServerPeerClusterSpringBootTest {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final String UNREACHABLE_ZONE = "http://localhost:1/eureka/";

    private static final String APP_NAME = "GX-PEER-APP";

    private static final String INSTANCE_PAYLOAD = """
            {"instance":{"instanceId":"%1$s","hostName":"localhost","app":"%2$s",
            "ipAddr":"127.0.0.1","status":"UP","overriddenstatus":"UNKNOWN",
            "port":{"$":8080,"@enabled":"true"},"securePort":{"$":443,"@enabled":"false"},
            "countryId":1,"dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"},
            "leaseInfo":{"renewalIntervalInSecs":30,"durationInSecs":90,"registrationTimestamp":0,
            "lastRenewalTimestamp":0,"evictionTimestamp":0,"serviceUpTimestamp":0},
            "metadata":{},"vipAddress":"gx-peer-app","secureVipAddress":"gx-peer-app"}}
            """;

    @Test
    void registryIsNotFetchedWhenFetchRegistryIsDisabled() throws Exception {
        var peerPort = freePort();
        try (var ignored = startNode("peer-b", peerPort, UNREACHABLE_ZONE, true, true)) {
            register(peerPort, "gx-peer-1");

            var selfPort = freePort();
            try (var ignoredSelf = startNode("peer-a", selfPort, zone(peerPort), false, false)) {
                assertThat(awaitInstance(selfPort, 5_000)).isFalse();
                assertThat(hasInstance(selfPort)).isFalse();
            }
        }
    }

    @Test
    void registrationIsAcceptedWhenPeerZoneIsConfigured() throws Exception {
        var peerPort = freePort();
        try (var ignored = startNode("peer-b", peerPort, UNREACHABLE_ZONE, true, true)) {
            var selfPort = freePort();
            try (var ignoredSelf = startNode("peer-a", selfPort, zone(peerPort), true, true)) {
                register(selfPort, "gx-peer-2");

                assertThat(hasInstance(selfPort)).isTrue();
            }
        }
    }

    private ConfigurableApplicationContext startNode(String name, int port, String defaultZone,
                                                     boolean registerWithEureka, boolean fetchRegistry) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.application.name", name);
        properties.put("server.port", String.valueOf(port));
        properties.put("maple.framework.enable.eureka-server", "true");
        properties.put("eureka.instance.hostname", "localhost");
        properties.put("eureka.instance.prefer-ip-address", "true");
        properties.put("eureka.server.enable-self-preservation", "false");
        properties.put("eureka.server.wait-time-in-ms-when-sync-empty", "0");
        properties.put("eureka.server.use-read-only-response-cache", "false");
        properties.put("eureka.server.response-cache-update-interval-ms", "1000");
        properties.put("eureka.client.service-url.defaultZone", defaultZone);
        properties.put("eureka.client.register-with-eureka", String.valueOf(registerWithEureka));
        properties.put("eureka.client.fetch-registry", String.valueOf(fetchRegistry));
        properties.put("logging.level.com.netflix.eureka", "WARN");

        return new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(properties)
                .run();
    }

    private String zone(int port) {
        return "http://localhost:" + port + "/eureka/";
    }

    private void register(int port, String instanceId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/eureka/apps/" + APP_NAME))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(INSTANCE_PAYLOAD.formatted(instanceId, APP_NAME)))
                .build();

        assertThat(HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
                .isBetween(200, 299);
    }

    private boolean awaitInstance(int port, long timeoutMillis) throws InterruptedException {
        var deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (hasInstance(port)) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    private boolean hasInstance(int port) {
        try {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/eureka/apps/" + APP_NAME))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains(APP_NAME);
        } catch (Exception exception) {
            return false;
        }
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
