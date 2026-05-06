package cn.maple.core.framework.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXTraceIdContextUtilsTest {
    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void getTraceIdReturnsEmptyStringWhenAbsent() {
        assertEquals("", GXTraceIdContextUtils.getTraceId());
        assertNull(GXTraceIdContextUtils.getNullableTraceId());
    }

    @Test
    void setTraceIdOnlySetsWhenAbsent() {
        GXTraceIdContextUtils.setTraceId("first");
        GXTraceIdContextUtils.setTraceId("second");

        assertEquals("first", GXTraceIdContextUtils.getTraceId());
    }

    @Test
    void putRestoreRemoveAndClearTraceId() {
        GXTraceIdContextUtils.putTraceId("first");
        GXTraceIdContextUtils.putTraceId("second");
        assertEquals("second", GXTraceIdContextUtils.getTraceId());

        GXTraceIdContextUtils.restoreTraceId("restored");
        assertEquals("restored", GXTraceIdContextUtils.getTraceId());

        GXTraceIdContextUtils.removeTraceId();
        assertEquals("", GXTraceIdContextUtils.getTraceId());

        MDC.put("other", "value");
        GXTraceIdContextUtils.clearTraceId();
        assertNull(MDC.get("other"));
    }

    @Test
    void setTraceIdIfAbsentGeneratesValueWithoutOverwritingExistingTraceId() {
        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class)) {
            commonUtils.when(() -> GXCommonUtils.getEnvironmentValue("spring.application.name", String.class)).thenReturn("app");

            GXTraceIdContextUtils.setTraceIdIfAbsent();
            String generated = GXTraceIdContextUtils.getTraceId();
            GXTraceIdContextUtils.setTraceIdIfAbsent();

            assertTrue(generated.startsWith("app:"));
            assertEquals(generated, GXTraceIdContextUtils.getTraceId());
        }
    }

    @Test
    void generateTraceIdWorksWithoutApplicationName() {
        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class)) {
            commonUtils.when(() -> GXCommonUtils.getEnvironmentValue("spring.application.name", String.class)).thenReturn("");

            String first = GXTraceIdContextUtils.generateTraceId();
            String second = GXTraceIdContextUtils.generateTraceId();

            assertNotNull(first);
            assertNotEquals(first, second);
        }
    }
}
