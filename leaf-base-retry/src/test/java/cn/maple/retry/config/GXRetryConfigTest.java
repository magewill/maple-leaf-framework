package cn.maple.retry.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXRetryConfigTest {

    private final GXRetryConfig retryConfig = new GXRetryConfig();

    @Test
    void createCustomRetryTemplate_RejectsNullExceptionType() {
        Map<Class<? extends Throwable>, Boolean> retryExceptions = new HashMap<>();
        retryExceptions.put(null, true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                retryConfig.createCustomRetryTemplate(2, 100L, 1.0, 1000L, retryExceptions));

        assertTrue(exception.getMessage().contains("retry exception type"));
    }

    @Test
    void createCustomRetryTemplate_RejectsNullRetryMapping() {
        Map<Class<? extends Throwable>, Boolean> retryExceptions = new HashMap<>();
        retryExceptions.put(IOException.class, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                retryConfig.createCustomRetryTemplate(2, 100L, 1.0, 1000L, retryExceptions));

        assertTrue(exception.getMessage().contains("retry exception mapping"));
    }
}
