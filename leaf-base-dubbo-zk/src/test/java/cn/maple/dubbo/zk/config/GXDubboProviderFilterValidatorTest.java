package cn.maple.dubbo.zk.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXDubboProviderFilterValidatorTest {
    @Test
    void warnsWhenProviderFilterIsMissing() {
        GXDubboProviderFilterValidator validator = new GXDubboProviderFilterValidator(new MockEnvironment());

        assertDoesNotThrow(validator::afterSingletonsInstantiated);

        assertFalse(GXDubboProviderFilterValidator.isBuiltInExceptionFilterExcluded(""), "blank filter must not be treated as configured");
        String warning = GXDubboProviderFilterValidator.buildWarningMessage("");
        assertNotNull(warning);
        assertTrue(warning.contains("-exception"));
        assertTrue(warning.contains(GXDubboProviderFilterValidator.REQUIRED_FILTER));
    }

    @Test
    void passesWhenBuiltInExceptionFilterIsExcluded() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("dubbo.provider.filter", "dubboServerTraceIdFilter,dubboExceptionFilter,-exception");
        GXDubboProviderFilterValidator validator = new GXDubboProviderFilterValidator(environment);

        assertDoesNotThrow(validator::afterSingletonsInstantiated);

        assertNull(GXDubboProviderFilterValidator.buildWarningMessage(environment.getProperty("dubbo.provider.filter", "")));
        assertTrue(GXDubboProviderFilterValidator.isBuiltInExceptionFilterExcluded("dubboExceptionFilter,-exception"));
    }

    @Test
    void warnsWhenOnlyCustomFilterIsConfigured() {
        MockEnvironment environment = new MockEnvironment().withProperty("dubbo.provider.filter", "dubboExceptionFilter");

        String warning = GXDubboProviderFilterValidator.buildWarningMessage(environment.getProperty("dubbo.provider.filter", ""));

        assertNotNull(warning);
        assertTrue(warning.contains("current=dubboExceptionFilter"));
        assertDoesNotThrow(() -> new GXDubboProviderFilterValidator(environment).afterSingletonsInstantiated(),
                "the advisory check must never block startup");
    }
}
