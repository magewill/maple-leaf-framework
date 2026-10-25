package cn.maple.dubbo.nacos.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXDubboProviderFilterValidatorTest {
    @Test
    void blankFilterIsNotExcluded() {
        assertFalse(GXDubboProviderFilterValidator.isBuiltInExceptionFilterExcluded(""));
        assertFalse(GXDubboProviderFilterValidator.isBuiltInExceptionFilterExcluded(null));
    }

    @Test
    void configuredExclusionIsRecognized() {
        assertTrue(GXDubboProviderFilterValidator.isBuiltInExceptionFilterExcluded("dubboExceptionFilter,-exception"));
        assertTrue(GXDubboProviderFilterValidator.isBuiltInExceptionFilterExcluded("-exception"));
    }

    @Test
    void enabledCustomFilterWithoutExclusionIsNotEnough() {
        assertFalse(GXDubboProviderFilterValidator.isBuiltInExceptionFilterExcluded("dubboExceptionFilter"));
    }

    @Test
    void validatorOnlyLogsAndNeverBlocksStartup() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of(GXDubboProviderFilterValidator.PROVIDER_FILTER_KEY, "dubboExceptionFilter")));

        GXDubboProviderFilterValidator validator = new GXDubboProviderFilterValidator(environment);

        assertDoesNotThrow(validator::afterSingletonsInstantiated);
    }
}
