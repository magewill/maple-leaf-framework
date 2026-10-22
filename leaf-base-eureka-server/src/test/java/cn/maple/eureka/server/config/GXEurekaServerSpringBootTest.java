package cn.maple.eureka.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts a real Eureka Server context and verifies the registry endpoints respond.
 *
 * <p>The context uses a random port so the test can run in any environment without
 * port conflicts. No external registry is required.</p>
 */
@SpringBootTest(
        classes = GXEurekaServerSpringBootTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "maple.framework.enable.eureka-server=true",
                "eureka.client.register-with-eureka=false",
                "eureka.client.fetch-registry=false",
                "eureka.server.enable-self-preservation=false",
                "eureka.server.wait-time-in-ms-when-sync-empty=0",
                "logging.level.com.netflix.eureka=WARN"
        })
class GXEurekaServerSpringBootTest {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final String INSTANCE_PAYLOAD = """
            {"instance":{"instanceId":"gx-audit-1","hostName":"localhost","app":"GX-AUDIT-APP",
            "ipAddr":"127.0.0.1","status":"UP","overriddenstatus":"UNKNOWN",
            "port":{"$":8080,"@enabled":"true"},"securePort":{"$":443,"@enabled":"false"},
            "countryId":1,"dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"},
            "leaseInfo":{"renewalIntervalInSecs":30,"durationInSecs":90,"registrationTimestamp":0,
            "lastRenewalTimestamp":0,"evictionTimestamp":0,"serviceUpTimestamp":0},
            "metadata":{},"vipAddress":"gx-audit-app","secureVipAddress":"gx-audit-app"}}
            """;

    @Autowired
    private Environment environment;

    @Test
    void eurekaServerExposesAppsEndpoint() throws Exception {
        var response = HTTP_CLIENT.send(get("/eureka/apps"), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void registeredInstanceIsDiscoverable() throws Exception {
        var registerRequest = HttpRequest.newBuilder(uri("/eureka/apps/GX-AUDIT-APP"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(INSTANCE_PAYLOAD))
                .build();
        var registerResponse = HTTP_CLIENT.send(registerRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(registerResponse.statusCode()).isBetween(200, 299);

        var queryResponse = HTTP_CLIENT.send(get("/eureka/apps/GX-AUDIT-APP"), HttpResponse.BodyHandlers.ofString());

        assertThat(queryResponse.statusCode()).isEqualTo(200);
        assertThat(queryResponse.body()).contains("GX-AUDIT-APP");
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private URI uri(String path) {
        var port = environment.getProperty("local.server.port", Integer.class);
        return URI.create("http://localhost:" + port + path);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
