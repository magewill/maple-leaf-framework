package cn.maple.sse.service.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.sse.service.GXSseMetrics;
import cn.maple.sse.support.GXSseTestEmitters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXSseEmitterServiceImplLifecycleTest {
    private TestableSseService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
            service = null;
        }
    }

    @Test
    void destroy_ClosesEveryConnectionAndReportsMetrics() {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter first = GXSseTestEmitters.recording();
        GXSseTestEmitters.ScriptedEmitter second = GXSseTestEmitters.recording();
        service.supply(first).supply(second);
        service.createSseConnect("destroy-a", 600L);
        service.createSseConnect("destroy-b", 600L);

        service.destroy();

        assertEquals(0, service.getActiveConnectionCount());
        assertTrue(first.isCompleted());
        assertTrue(second.isCompleted());
        GXSseMetrics metrics = service.getMetrics();
        assertEquals(0, metrics.connections());
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void destroy_CancelsQueuedRetriesAndAwaitsTermination() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("destroy-retry", 600L);
        emitter.failFrom(2);

        service.sendMessageToOneClient("destroy-retry", "payload", false);
        awaitTrue(() -> emitter.sendCount() >= 2, 2_000L);

        service.destroy();
        Thread.sleep(100L);
        int sendsAfterDestroy = emitter.sendCount();
        Thread.sleep(600L);

        assertEquals(sendsAfterDestroy, emitter.sendCount(), "no retry may run after destroy");
        assertEquals(0, service.getActiveConnectionCount());
        assertTrue(service.getMetrics().drops() >= 1L, "the abandoned in-flight message is counted as a drop");
    }

    @Test
    void createSseConnect_AfterDestroyThrowsBusinessException() {
        service = new TestableSseService();
        service.createSseConnect("before-destroy", 600L);
        service.destroy();

        assertThrows(GXBusinessException.class, () -> service.createSseConnect("after-destroy", 600L));
        assertEquals(0, service.getActiveConnectionCount());
    }

    @Test
    void sendAfterDestroy_IsIgnoredWithoutThrowing() {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("send-after-destroy", 600L);
        service.destroy();
        int sends = emitter.sendCount();

        long dropsBefore = service.getMetrics().drops();

        assertDoesNotThrow(() -> {
            service.sendMessageToOneClient("send-after-destroy", "payload", false);
            service.sendMessageToAllClient("payload");
        });
        assertEquals(sends, emitter.sendCount());
        assertEquals(dropsBefore + 2L, service.getMetrics().drops(),
                "both post-destroy sends must be counted as drops");
    }

    @Test
    void closeConnections_PropagatesPredicateFailure() {
        service = new TestableSseService();
        service.createSseConnect("predicate-a", 600L);
        service.createSseConnect("predicate-b", 600L);

        assertThrows(IllegalStateException.class,
                () -> service.closeConnections(clientId -> {
                    throw new IllegalStateException("predicate boom");
                }));
        assertEquals(2, service.getActiveConnectionCount(), "batch close is not atomic");
    }

    @Test
    void destroy_IsIdempotent() {
        service = new TestableSseService();
        service.createSseConnect("idempotent", 600L);

        service.destroy();

        assertDoesNotThrow(service::destroy);
        assertDoesNotThrow(service::destroy);
    }

    @Test
    void init_IsIdempotentAndSafeWithoutContainer() {
        service = new TestableSseService();

        service.init();
        service.init();

        assertDoesNotThrow(() -> service.createSseConnect("manual", 600L));
        assertEquals(1, service.getActiveConnectionCount());
    }

    @Test
    void closeConnections_OnlyClosesMatchingConnections() {
        service = new TestableSseService();
        service.createSseConnect("keep-a", 600L);
        service.createSseConnect("drop-a", 600L);

        int closed = service.closeConnections(clientId -> clientId.startsWith("drop-"));

        assertEquals(1, closed);
        assertEquals(1, service.getActiveConnectionCount());
        assertFalse(service.getSseEmitterByClientId("keep-a") == null);
        assertTrue(service.getSseEmitterByClientId("drop-a") == null);
    }

    @Test
    void pipelineIsReleasedWhenConnectionIsReplaced() {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter first = GXSseTestEmitters.recording();
        service.supply(first);
        SseEmitter firstEmitter = service.createSseConnect("replaced-pipeline", 600L);
        first.failFrom(2);
        service.sendMessageToOneClient("replaced-pipeline", "payload", false);

        SseEmitter secondEmitter = service.createSseConnect("replaced-pipeline", 600L);

        assertEquals(secondEmitter, service.getSseEmitterByClientId("replaced-pipeline"));
        assertFalse(firstEmitter == secondEmitter);
        assertEquals(1, service.getActiveConnectionCount());
    }

    private static void awaitTrue(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
    }
}
