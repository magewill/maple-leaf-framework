package cn.maple.canal.service.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXCanalLogThrottleTest {
    private final GXCanalLogThrottle throttle = new GXCanalLogThrottle();

    @Test
    void shouldAllowFirstThreeOccurrencesOnly() {
        assertTrue(throttle.shouldLog("invalid-json"));
        assertTrue(throttle.shouldLog("invalid-json"));
        assertTrue(throttle.shouldLog("invalid-json"));
        assertFalse(throttle.shouldLog("invalid-json"));
        assertFalse(throttle.shouldLog("invalid-json"));
    }

    @Test
    void shouldTrackKeysIndependently() {
        for (int index = 0; index < 3; index++) {
            throttle.shouldLog("key-a");
        }

        assertFalse(throttle.shouldLog("key-a"));
        assertTrue(throttle.shouldLog("key-b"));
    }

    @Test
    void shouldExposeWarnAndErrorHelpers() {
        for (int index = 0; index < 4; index++) {
            throttle.warn("warn-key", "Canal warning for test: index={}", index);
            throttle.error("error-key", "Canal error for test: index={}", index);
        }

        assertFalse(throttle.shouldLog("warn-key"));
        assertFalse(throttle.shouldLog("error-key"));
    }
}
