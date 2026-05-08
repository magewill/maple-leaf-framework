package cn.maple.sse.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXSseEmitterServiceImplTest {
    private GXSseEmitterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GXSseEmitterServiceImpl();
    }

    @AfterEach
    void tearDown() {
        service.destroy();
    }

    @Test
    void createSseConnect_WhenClientIdIsBlankCreatesGeneratedConnectionAndUsesMinimumTimeout() {
        SseEmitter emitter = service.createSseConnect(" ", 1L);

        Map<String, SseEmitter> cache = getClientCache(service);
        assertEquals(1, cache.size());

        String generatedClientId = cache.keySet().iterator().next();
        assertFalse(generatedClientId.isBlank());
        assertSame(emitter, service.getSseEmitterByClientId(generatedClientId));
        assertNull(service.getSseEmitterByClientId(" "));
        assertEquals(TimeUnit.SECONDS.toMillis(60L), emitter.getTimeout());
    }

    @Test
    void createSseConnect_WhenClientIdAlreadyExistsReplacesOldEmitter() {
        SseEmitter firstEmitter = service.createSseConnect("client-1", 120L);
        SseEmitter secondEmitter = service.createSseConnect("client-1", 120L);

        assertNotSame(firstEmitter, secondEmitter);
        assertEquals(1, service.getActiveConnectionCount());
        assertSame(secondEmitter, service.getSseEmitterByClientId("client-1"));
    }

    @Test
    void closeConnect_RemovesExistingConnectionAndIgnoresBlankOrMissingClientId() {
        service.createSseConnect("client-1", 120L);

        assertDoesNotThrow(() -> service.closeConnect(" "));
        assertEquals(1, service.getActiveConnectionCount());

        service.closeConnect("client-1");

        assertEquals(0, service.getActiveConnectionCount());
        assertNull(service.getSseEmitterByClientId("client-1"));
        assertDoesNotThrow(() -> service.closeConnect("missing-client"));
    }

    @Test
    void sendMessageMethods_IgnoreBlankOrMissingTargetsAndKeepActiveConnections() {
        service.createSseConnect("client-1", 120L);

        assertDoesNotThrow(() -> service.sendMessageToAllClient(" "));
        assertDoesNotThrow(() -> service.sendMessageToOneClient(" ", "message", true));
        assertDoesNotThrow(() -> service.sendMessageToOneClient("missing-client", "message", true));
        assertDoesNotThrow(() -> service.sendMessageToOneClient("client-1", "message", false));

        assertEquals(1, service.getActiveConnectionCount());
    }

    @Test
    void splitMessage_ReturnsCompleteFragmentsWithinEffectiveLengthLimit() {
        assertTrue(service.splitMessage("", 10).isEmpty());

        String message = "abcdefghijklmnopqrstuvwxyz";
        List<String> chunks = service.splitMessage(message, 3);

        assertEquals(message, String.join("", chunks));
        assertTrue(chunks.stream().allMatch(chunk -> !chunk.isEmpty() && chunk.length() <= 3));
    }

    @Test
    void splitMessage_ClampsInvalidAndTooLargeLengthLimits() {
        String message = "x".repeat(250);

        List<String> singleCharChunks = service.splitMessage(message, 0);
        assertEquals(message, String.join("", singleCharChunks));
        assertTrue(singleCharChunks.stream().allMatch(chunk -> chunk.length() == 1));

        List<String> boundedChunks = service.splitMessage(message, 200);
        assertEquals(message, String.join("", boundedChunks));
        assertTrue(boundedChunks.stream().allMatch(chunk -> !chunk.isEmpty() && chunk.length() <= 100));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, SseEmitter> getClientCache(GXSseEmitterServiceImpl service) {
        try {
            Field field = GXSseEmitterServiceImpl.class.getDeclaredField("sseClientCache");
            field.setAccessible(true);
            return (Map<String, SseEmitter>) field.get(service);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read SSE client cache", e);
        }
    }
}
