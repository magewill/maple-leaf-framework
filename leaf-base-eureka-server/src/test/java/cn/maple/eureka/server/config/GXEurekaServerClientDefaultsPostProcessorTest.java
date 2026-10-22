package cn.maple.eureka.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class GXEurekaServerClientDefaultsPostProcessorTest {
    private final GXEurekaServerClientDefaultsPostProcessor postProcessor =
            new GXEurekaServerClientDefaultsPostProcessor();

    private final SpringApplication application = new SpringApplication();

    @Test
    void disablesClientWhenRegistrySwitchIsMissing() {
        var environment = new MockEnvironment();

        postProcessor.postProcessEnvironment(environment, application);

        assertThat(environment.getProperty("eureka.client.enabled")).isEqualTo("false");
    }

    @Test
    void keepsClientUntouchedWhenServletRegistryIsEnabled() {
        var environment = new MockEnvironment()
                .withProperty("maple.framework.enable.eureka-server", "true");

        postProcessor.postProcessEnvironment(environment, application);

        assertThat(environment.getProperty("eureka.client.enabled")).isNull();
    }

    @Test
    void disablesClientWhenSwitchIsEnabledButApplicationIsReactive() {
        var environment = new MockEnvironment()
                .withProperty("maple.framework.enable.eureka-server", "true")
                .withProperty("spring.main.web-application-type", "reactive");

        postProcessor.postProcessEnvironment(environment, application);

        assertThat(environment.getProperty("eureka.client.enabled")).isEqualTo("false");
    }

    @Test
    void appliesSingleMachineDefaultsWhenServletRegistryIsEnabled() {
        var environment = new MockEnvironment()
                .withProperty("maple.framework.enable.eureka-server", "true");

        postProcessor.postProcessEnvironment(environment, application);

        assertThat(environment.getProperty("eureka.client.register-with-eureka")).isEqualTo("false");
        assertThat(environment.getProperty("eureka.client.fetch-registry")).isEqualTo("false");
        assertThat(environment.getProperty("eureka.client.enabled")).isNull();
    }

    @Test
    void keepsExplicitRegistrationSettingsWhenServletRegistryIsEnabled() {
        var environment = new MockEnvironment()
                .withProperty("maple.framework.enable.eureka-server", "true")
                .withProperty("eureka.client.register-with-eureka", "true");

        postProcessor.postProcessEnvironment(environment, application);

        assertThat(environment.getProperty("eureka.client.register-with-eureka")).isEqualTo("true");
        assertThat(environment.getProperty("eureka.client.fetch-registry")).isEqualTo("false");
    }

    @Test
    void keepsExplicitClientSetting() {
        var environment = new MockEnvironment()
                .withProperty("eureka.client.enabled", "true");

        postProcessor.postProcessEnvironment(environment, application);

        assertThat(environment.getProperty("eureka.client.enabled")).isEqualTo("true");
    }
}
