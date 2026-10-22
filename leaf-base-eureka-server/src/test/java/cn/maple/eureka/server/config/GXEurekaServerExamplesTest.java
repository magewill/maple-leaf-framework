package cn.maple.eureka.server.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the packaging contract: the module must not ship a classpath root {@code application.yml}
 * that competes with the host application, and the shipped examples must stay environment neutral.
 */
class GXEurekaServerExamplesTest {
    @Test
    void classpathRootHasNoApplicationYml() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(inputStream).isNull();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void standaloneExampleDisablesRegistrationAndFetching() {
        var content = read("examples/eureka-server-standalone.yml");

        assertThat(content).contains("eureka-server: true");
        assertThat(content).contains("register-with-eureka: false");
        assertThat(content).contains("fetch-registry: false");
        assertThat(content).doesNotContain("defaultZone");
        assertThat(content).doesNotContain("hostname");
        assertThat(content).doesNotContain("127.0.0.1");
        assertThat(content).doesNotContain("eureka7001.com");
    }

    @Test
    void peerExampleEnablesRegistrationFetchingAndUsesPlaceholders() {
        var content = read("examples/eureka-server-peer.yml");

        assertThat(content).contains("eureka-server: true");
        assertThat(content).contains("register-with-eureka: true");
        assertThat(content).contains("fetch-registry: true");
        assertThat(content).contains("defaultZone:");
        assertThat(content).contains("${SERVER_PORT:7001}");
        assertThat(content).contains("${EUREKA_INSTANCE_HOSTNAME:localhost}");
        assertThat(content).doesNotContain("127.0.0.1");
        assertThat(content).doesNotContain("eureka7001.com");
    }

    private String read(String resource) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(inputStream).as("resource %s must exist", resource).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
