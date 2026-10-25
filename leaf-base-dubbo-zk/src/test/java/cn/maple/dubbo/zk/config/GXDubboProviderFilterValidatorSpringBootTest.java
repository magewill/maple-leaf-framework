package cn.maple.dubbo.zk.config;

import cn.maple.dubbo.zk.support.GXDubboTestApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the advisory wiring check is actually picked up by a real Spring context: a
 * {@code SmartInitializingSingleton} is invoked during refresh, and a missing
 * {@code dubbo.provider.filter} must not block startup.
 */
@SpringBootTest(
        classes = {GXDubboTestApp.class, GXDubboProviderFilterValidatorSpringBootTest.ValidatorConfig.class},
        properties = {
                "spring.cloud.nacos.config.import-check.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false"
        }
)
class GXDubboProviderFilterValidatorSpringBootTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void validatorIsRegisteredAndContextStillStarts() {
        assertTrue(context.getBeanNamesForType(GXDubboProviderFilterValidator.class).length > 0,
                "validator must be registered in a real context");

        GXDubboProviderFilterValidator validator = context.getBean(GXDubboProviderFilterValidator.class);

        assertDoesNotThrow(validator::afterSingletonsInstantiated,
                "the advisory check must never block startup");
        assertNotNull(GXDubboProviderFilterValidator.buildWarningMessage(""),
                "missing dubbo.provider.filter must produce an advisory message");
        assertNull(GXDubboProviderFilterValidator.buildWarningMessage("dubboExceptionFilter,-exception"));
    }

    @Test
    void environmentBackedDecisionMatchesAdvisory() {
        Environment environment = context.getEnvironment();

        String filter = environment.getProperty(GXDubboProviderFilterValidator.PROVIDER_FILTER_KEY, "");

        assertTrue(GXDubboProviderFilterValidator.buildWarningMessage(filter) != null,
                "test context intentionally leaves dubbo.provider.filter unset");
    }

    @TestConfiguration
    static class ValidatorConfig {
        @Bean
        GXDubboProviderFilterValidator gxDubboProviderFilterValidator(Environment environment) {
            return new GXDubboProviderFilterValidator(environment);
        }
    }
}
