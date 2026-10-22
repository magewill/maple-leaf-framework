package cn.maple.eureka.client.config;

import cn.maple.eureka.client.support.GXTestRegistrationServer;
import com.netflix.discovery.EurekaClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents what the three "bring the dependency but do not register" combinations actually do.
 * Values are pinned to the measured behaviour of Spring Cloud 2025.1.x; changing them is a contract change.
 */
class GXEurekaClientCombinationTest {
    private static final GXTestRegistrationServer REGISTRATION_SERVER = GXTestRegistrationServer.start();

    @AfterAll
    static void stopServer() {
        REGISTRATION_SERVER.close();
    }

    @Nested
    @SpringBootTest(classes = CombinationTestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = {
                    "spring.application.name=combo-app",
                    "eureka.client.service-url.defaultZone=http://127.0.0.1:1/eureka/",
                    "spring.cloud.discovery.enabled=false"
            })
    class DiscoveryDisabled {
        @Autowired
        private ApplicationContext context;

        @Test
        void discoveryDisabledRemovesEurekaClientAndRegistration() {
            assertThat(context.getBeansOfType(EurekaClient.class)).isEmpty();
            assertThat(context.containsBean("eurekaAutoServiceRegistration")).isFalse();
        }
    }

    @Nested
    @SpringBootTest(classes = CombinationTestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = {
                    "spring.application.name=combo-app",
                    "spring.cloud.service-registry.auto-registration.enabled=false"
            })
    class AutoRegistrationDisabled {
        @Autowired
        private ApplicationContext context;

        @DynamicPropertySource
        static void registrationProperties(DynamicPropertyRegistry registry) {
            registry.add("eureka.client.service-url.defaultZone", () -> REGISTRATION_SERVER.baseUrl() + "/eureka/");
        }

        @Test
        void autoRegistrationDisabledKeepsClientButSendsNoRegistration() {
            assertThat(context.getBeansOfType(EurekaClient.class)).isNotEmpty();
            assertThat(context.containsBean("eurekaAutoServiceRegistration")).isFalse();

            List<String> requests = REGISTRATION_SERVER.recordedRequests();
            assertThat(requests).noneMatch(request -> request.startsWith("POST /eureka/apps"));
        }
    }

    @Nested
    @SpringBootTest(classes = CombinationTestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = {
                    "spring.application.name=combo-app",
                    "eureka.client.enabled=false"
            })
    class ClientDisabled {
        @Autowired
        private ApplicationContext context;

        @Test
        void clientDisabledRemovesEverything() {
            assertThat(context.getBeansOfType(EurekaClient.class)).isEmpty();
            assertThat(context.containsBean("eurekaAutoServiceRegistration")).isFalse();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class CombinationTestApplication {
    }
}
