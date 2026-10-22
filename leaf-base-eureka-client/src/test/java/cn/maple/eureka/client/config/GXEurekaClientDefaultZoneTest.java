package cn.maple.eureka.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the framework contributes no registration defaults and that an unconfigured
 * default zone is reported instead of being applied silently.
 */
@SpringBootTest(classes = GXEurekaClientDefaultZoneTest.DefaultZoneTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.application.name=default-zone-test"})
@ExtendWith(OutputCaptureExtension.class)
class GXEurekaClientDefaultZoneTest {
    private static final String WARN_TEXT = "Eureka client default zone is not configured";

    private static final String DEFAULT_ZONE_KEY = "eureka.client.service-url.defaultZone";

    @Autowired
    private Environment environment;

    @Test
    void frameworkProvidesNoRegistrationDefaults() {
        assertThat(environment.getProperty("eureka.client.register-with-eureka")).isNull();
        assertThat(environment.getProperty(DEFAULT_ZONE_KEY)).isNull();
        assertThat(environment.getProperty("spring.cloud.openfeign.circuitbreaker.enabled")).isNull();
    }

    @Test
    void warnsWhenDefaultZoneIsMissing(CapturedOutput output) {
        assertThat(output).contains(WARN_TEXT);
    }

    @Test
    void defaultZoneDetectionFollowsConfiguration() {
        assertThat(GXEurekaClientConfig.isDefaultZoneConfigured(environmentFrom(Map.of()))).isFalse();
        assertThat(GXEurekaClientConfig.isDefaultZoneConfigured(
                environmentFrom(Map.of(DEFAULT_ZONE_KEY, "http://127.0.0.1:9/eureka/")))).isTrue();
    }

    /**
     * A disabled client never registers, so the "registration will target the Spring Cloud default"
     * warning would be a false signal and must be suppressed.
     */
    @Test
    void warningIsSuppressedWhenClientIsDisabled() {
        assertThat(GXEurekaClientConfig.shouldWarnAboutDefaultZone(environmentFrom(Map.of()))).isTrue();
        assertThat(GXEurekaClientConfig.shouldWarnAboutDefaultZone(
                environmentFrom(Map.of("eureka.client.enabled", "true")))).isTrue();
        assertThat(GXEurekaClientConfig.shouldWarnAboutDefaultZone(
                environmentFrom(Map.of("eureka.client.enabled", "false")))).isFalse();
        assertThat(GXEurekaClientConfig.shouldWarnAboutDefaultZone(environmentFrom(
                Map.of("eureka.client.enabled", "false", DEFAULT_ZONE_KEY, "http://127.0.0.1:9/eureka/")))).isFalse();
        assertThat(GXEurekaClientConfig.shouldWarnAboutDefaultZone(
                environmentFrom(Map.of(DEFAULT_ZONE_KEY, "http://127.0.0.1:9/eureka/")))).isFalse();
    }

    private StandardEnvironment environmentFrom(Map<String, Object> properties) {
        StandardEnvironment standardEnvironment = new StandardEnvironment();
        standardEnvironment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return standardEnvironment;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class DefaultZoneTestApplication {
    }
}
