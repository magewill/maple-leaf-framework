package cn.maple.eureka.client.config;

import cn.maple.eureka.client.support.GXTestRegistrationServer;
import com.netflix.discovery.EurekaClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that registration actually leaves the application: a real HTTP request has to reach the
 * configured default zone, not just be present in the environment.
 */
@SpringBootTest(classes = GXEurekaClientRegistrationTest.RegistrationTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GXEurekaClientRegistrationTest {
    private static final GXTestRegistrationServer REGISTRATION_SERVER = GXTestRegistrationServer.start();

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @DynamicPropertySource
    static void registrationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.application.name", () -> "it-app");
        registry.add("eureka.client.service-url.defaultZone", () -> REGISTRATION_SERVER.baseUrl() + "/eureka/");
        registry.add("eureka.client.register-with-eureka", () -> "true");
        registry.add("eureka.client.fetch-registry", () -> "false");
    }

    @AfterAll
    static void stopRegistrationServer() {
        REGISTRATION_SERVER.close();
    }

    @Test
    void registrationIsSentToConfiguredDefaultZone() {
        assertThat(environment.getProperty("eureka.client.service-url.defaultZone"))
                .isEqualTo(REGISTRATION_SERVER.baseUrl() + "/eureka/");
        assertThat(context.getBeansOfType(EurekaClient.class)).isNotEmpty();
        assertThat(REGISTRATION_SERVER.awaitRegistration("IT-APP"))
                .as("registration request for IT-APP; recorded requests: %s", REGISTRATION_SERVER.recordedRequests())
                .isTrue();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class RegistrationTestApplication {
    }
}
