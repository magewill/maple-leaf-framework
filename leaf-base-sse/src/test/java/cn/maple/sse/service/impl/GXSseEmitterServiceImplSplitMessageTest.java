package cn.maple.sse.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXSseEmitterServiceImplSplitMessageTest {
    private TestableSseService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
            service = null;
        }
    }

    @Test
    void blankMessage_ReturnsEmptyList() {
        service = new TestableSseService();

        assertTrue(service.splitMessage("", 10).isEmpty());
        assertTrue(service.splitMessage(" ", 10).isEmpty());
        assertTrue(service.splitMessage("\t\n", 10).isEmpty());
    }

    @Test
    void nullMessage_ReturnsEmptyList() {
        service = new TestableSseService();

        assertTrue(service.splitMessage(null, 10).isEmpty());
    }

    @Test
    void lengthBoundaries_ClampedTo1To100() {
        service = new TestableSseService();
        String message = "x".repeat(250);

        for (int length : new int[]{-1, 0, 1}) {
            List<String> chunks = service.splitMessage(message, length);
            assertEquals(message, String.join("", chunks));
            assertTrue(chunks.stream().allMatch(chunk -> chunk.length() == 1), "length " + length);
        }

        List<String> bounded = service.splitMessage(message, 101);
        assertEquals(message, String.join("", bounded));
        assertTrue(bounded.stream().allMatch(chunk -> !chunk.isEmpty() && chunk.length() <= 100));
    }

    @Test
    void requestedLengthIsClampedToOneHundredCharacters() {
        service = new TestableSseService();
        String message = "y".repeat(1000);

        List<String> chunks = service.splitMessage(message, 500);

        assertEquals(message, String.join("", chunks));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 100),
                "length is clamped to 100 characters, not kilobytes");
        assertTrue(chunks.size() >= 10, "a 1000 char message needs at least 10 fragments of at most 100 chars");
    }

    @Test
    void surrogatePairs_AreNeverBroken() {
        service = new TestableSseService();
        List<String> messages = List.of("a😀b🎉c你好d", "😀", "😀😀😀", "a😀", "😀a");

        for (String message : messages) {
            for (int length = 1; length <= 5; length++) {
                for (int round = 0; round < 20; round++) {
                    List<String> chunks = service.splitMessage(message, length);
                    assertEquals(message, String.join("", chunks), message + " length " + length);
                    assertTrue(chunks.stream().noneMatch(GXSseEmitterServiceImplSplitMessageTest::hasUnpairedSurrogate),
                            message + " length " + length + " chunks " + chunks);
                }
            }
        }
    }

    @Test
    void splitPreservesEveryCharacter() {
        service = new TestableSseService();
        String message = "The quick brown fox jumps over the lazy dog 你好，世界！🎉";

        List<String> chunks = service.splitMessage(message, 7);

        assertEquals(message, String.join("", chunks));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 7));
    }

    private static boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }
}
