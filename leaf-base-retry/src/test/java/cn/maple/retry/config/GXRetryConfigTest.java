package cn.maple.retry.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.core.retry.RetryTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void createCustomRetryTemplate_GeneratesIncludesAndExcludes() {
        Map<Class<? extends Throwable>, Boolean> retryExceptions = Map.of(
                IOException.class, true,
                IllegalArgumentException.class, false);

        RetryTemplate retryTemplate = retryConfig.createCustomRetryTemplate(2, 100L, 1.0, 1000L, retryExceptions);
        String policy = retryTemplate.getRetryPolicy().toString();

        assertTrue(policy.contains("includes="), policy);
        assertTrue(policy.contains("java.io.IOException"), policy);
        assertTrue(policy.contains("excludes="), policy);
        assertTrue(policy.contains("java.lang.IllegalArgumentException"), policy);
    }

    @Test
    void createCustomRetryTemplate_EmptyExceptionMapFallsBackToDefaultFourTypes() {
        RetryTemplate retryTemplate = retryConfig.createCustomRetryTemplate(2, 100L, 1.0, 1000L, Map.of());
        String policy = retryTemplate.getRetryPolicy().toString();

        assertTrue(policy.contains("GXBusinessException"), policy);
        assertTrue(policy.contains("java.io.IOException"), policy);
        assertTrue(policy.contains("java.util.concurrent.TimeoutException"), policy);
        assertTrue(policy.contains("java.net.SocketTimeoutException"), policy);
    }

    @Test
    void createCustomRetryTemplate_RejectsNaNMultiplier() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                retryConfig.createCustomRetryTemplate(2, 100L, Double.NaN, 1000L));

        assertTrue(exception.getMessage().contains("multiplier"), exception.getMessage());
    }

    @Test
    void createCustomRetryTemplate_RejectsNegativeTimeoutAndJitter() {
        IllegalArgumentException timeoutException = assertThrows(IllegalArgumentException.class, () ->
                retryConfig.createCustomRetryTemplate(2, 100L, 1.0, 1000L, -1L, 0L, Map.of()));
        assertTrue(timeoutException.getMessage().contains("timeout"), timeoutException.getMessage());

        IllegalArgumentException jitterException = assertThrows(IllegalArgumentException.class, () ->
                retryConfig.createCustomRetryTemplate(2, 100L, 1.0, 1000L, 0L, -1L, Map.of()));
        assertTrue(jitterException.getMessage().contains("jitter"), jitterException.getMessage());
    }

    @Test
    void createCustomRetryTemplate_ZeroTimeoutAndJitterEqualsLegacySignature() {
        Map<Class<? extends Throwable>, Boolean> retryExceptions = Map.of(IOException.class, true);

        RetryTemplate legacy = retryConfig.createCustomRetryTemplate(2, 100L, 1.5, 2000L, retryExceptions);
        RetryTemplate withDisabledTiming = retryConfig.createCustomRetryTemplate(2, 100L, 1.5, 2000L, 0L, 0L, retryExceptions);

        assertEquals(legacy.getRetryPolicy().toString(), withDisabledTiming.getRetryPolicy().toString());
    }

    @Test
    void createCustomRetryTemplate_RejectsUpperBoundViolations() {
        assertMessageContains("maxAttempts", () -> retryConfig.createCustomRetryTemplate(101, 100L, 1.0, 1000L));
        assertMessageContains("initialInterval", () -> retryConfig.createCustomRetryTemplate(2, 300001L, 1.0, 1000L));
        assertMessageContains("maxInterval", () -> retryConfig.createCustomRetryTemplate(2, 100L, 1.0, 3600001L));
        assertMessageContains("multiplier", () -> retryConfig.createCustomRetryTemplate(2, 100L, 0.5, 1000L));
        assertMessageContains("multiplier", () -> retryConfig.createCustomRetryTemplate(2, 100L, 12.0, 1000L));
        assertMessageContains("maxInterval", () -> retryConfig.createCustomRetryTemplate(2, 5000L, 1.0, 1000L));
    }

    @Test
    void retryTemplate_UsesSinglePointDefaultConstants() {
        RetryTemplate retryTemplate = retryConfig.retryTemplate();
        String policy = retryTemplate.getRetryPolicy().toString();

        assertEquals(3, GXRetryConfig.DEFAULT_MAX_ATTEMPTS);
        assertEquals(1000L, GXRetryConfig.DEFAULT_INITIAL_INTERVAL);
        assertEquals(2.0, GXRetryConfig.DEFAULT_MULTIPLIER);
        assertEquals(10000L, GXRetryConfig.DEFAULT_MAX_INTERVAL);
        assertFalse(policy.isBlank());
    }

    private void assertMessageContains(String expectedField, Executable executable) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, executable);
        assertTrue(exception.getMessage().contains(expectedField), exception.getMessage());
    }
}
