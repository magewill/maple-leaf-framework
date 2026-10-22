package cn.maple.eureka.client.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The host configuration file must be the only source of registration values: the framework ships no
 * {@code classpath:/application.yml} and therefore cannot shadow (or be shadowed by) the host file.
 *
 * <p>The host file lives in {@code src/test/resources/host/application.yml} and is loaded through
 * {@code spring.config.location}, which is exactly the position a downstream application's own
 * {@code application.yml} occupies.</p>
 */
@SpringBootTest(classes = GXEurekaClientHostConfigTest.HostConfigTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.config.location=classpath:/host/")
class GXEurekaClientHostConfigTest {
    @Autowired
    private Environment environment;

    @Test
    void valuesComeFromTheHostConfigurationFile() {
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("host-config-app");
        assertThat(environment.getProperty("eureka.client.register-with-eureka")).isEqualTo("false");
        assertThat(environment.getProperty("eureka.client.service-url.defaultZone"))
                .isEqualTo("http://host-file:9000/eureka/");
    }

    @Test
    void frameworkContributesNoCompetingValues() {
        assertThat(environment.getProperty("spring.cloud.openfeign.circuitbreaker.enabled")).isNull();
        assertThat(environment.getProperty("maple.framework.enable.eureka-client")).isNull();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class HostConfigTestApplication {
    }
}
