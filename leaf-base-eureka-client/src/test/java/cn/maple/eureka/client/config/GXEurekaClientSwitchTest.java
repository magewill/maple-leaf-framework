package cn.maple.eureka.client.config;

import com.netflix.discovery.EurekaClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code maple.framework.enable.eureka-client} switch contract: when disabled nothing
 * from the Eureka client is assembled, and the framework never writes Eureka defaults on its own.
 */
@SpringBootTest(classes = GXEurekaClientSwitchTest.SwitchTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.application.name=switch-test"})
@ExtendWith(OutputCaptureExtension.class)
class GXEurekaClientSwitchTest {
    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void switchMissingKeepsUpstreamBehaviour() {
        assertThat(environment.getProperty("eureka.client.enabled")).isNull();
        assertThat(context.getBeansOfType(EurekaClient.class)).isNotEmpty();
    }

    @Nested
    @SpringBootTest(classes = SwitchTestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = {"spring.application.name=switch-test", "maple.framework.enable.eureka-client=false"})
    @ExtendWith(OutputCaptureExtension.class)
    class SwitchOff {
        @Autowired
        private ApplicationContext context;

        @Autowired
        private Environment environment;

        @Test
        void switchOffDisablesEurekaClient(CapturedOutput output) {
            assertThat(context.getBeansOfType(EurekaClient.class)).isEmpty();
            assertThat(context.containsBean("eurekaAutoServiceRegistration")).isFalse();
            assertThat(environment.getProperty("eureka.client.enabled")).isEqualTo("false");
            assertThat(output).contains("Eureka client disabled via maple.framework.enable.eureka-client=false");
        }
    }

    @Nested
    @SpringBootTest(classes = SwitchTestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = {"spring.application.name=switch-test", "eureka.client.enabled=false"})
    @ExtendWith(OutputCaptureExtension.class)
    class ExplicitClientDisabled {
        @Autowired
        private ApplicationContext context;

        @Autowired
        private Environment environment;

        @Test
        void explicitClientDisabledWinsOverMissingSwitch() {
            assertThat(context.getBeansOfType(EurekaClient.class)).isEmpty();
            assertThat(environment.getProperty("eureka.client.enabled")).isEqualTo("false");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class SwitchTestApplication {
    }
}
