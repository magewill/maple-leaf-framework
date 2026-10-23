package cn.maple.sse.service.impl;

import cn.hutool.core.lang.Dict;
import cn.maple.sse.support.GXSseTestEmitters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduction cases for the audit findings. Every case is written against the
 * behaviour the module must guarantee, so each one fails on the pre-fix code.
 */
class GXSseEmitterAuditReproTest {
    private TestableSseService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
            service = null;
        }
    }

    @Test
    void f01RetryExhaustedConnectionIsNotClosed() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("f01-client", 600L);
        emitter.failFrom(2);

        service.sendMessageToOneClient("f01-client", "payload", false);
        awaitTrue(() -> service.getActiveConnectionCount() == 0, 5_000L);

        assertEquals(0, service.getActiveConnectionCount());
        assertTrue(emitter.isCompleted(), "retry exhaustion must close the connection");
        assertEquals(5, emitter.sendCount(), "1 handshake + 4 attempts");
    }

    @Test
    void f02FailedMessageIsOvertakenByNextMessage() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("f02-client", 600L);
        emitter.failOnceAt(2);

        service.sendMessageToOneClient("f02-client", "M1", false);
        service.sendMessageToOneClient("f02-client", "M2", false);
        awaitMessageCount(emitter, 2, 5_000L);

        assertEquals(List.of("M1", "M2"), messages(emitter), "same connection must stay ordered");
    }

    @Test
    void f05ConcurrentReplaceReturnsEmitterInsteadOfException() throws Exception {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter first = GXSseTestEmitters.recording().holdFirstSend();
        service.supply(first);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<SseEmitter> firstConnect = pool.submit(() -> service.createSseConnect("f05-client", 600L));
            assertTrue(first.awaitEntered(5L, TimeUnit.SECONDS), "first handshake must be blocked in send");

            SseEmitter replacement = service.createSseConnect("f05-client", 600L);
            first.releaseFirstSend();

            assertNull(awaitFailure(firstConnect), "a replaced handshake must not throw");
            assertEquals(1, service.getActiveConnectionCount());
            assertEquals(replacement, service.getSseEmitterByClientId("f05-client"));
        } finally {
            pool.shutdownNow();
        }
    }

    private static Throwable awaitFailure(Future<?> future) throws InterruptedException {
        try {
            future.get(10L, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (TimeoutException e) {
            return e;
        }
    }

    private static void awaitTrue(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
    }

    private static void awaitMessageCount(GXSseTestEmitters.ScriptedEmitter emitter, int expected, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (messages(emitter).size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
    }

    private static List<String> messages(GXSseTestEmitters.ScriptedEmitter emitter) {
        List<String> result = new ArrayList<>();
        for (Object payload : emitter.payloads()) {
            if (payload instanceof Dict dict) {
                Object message = dict.get("message");
                if (message != null) {
                    result.add(String.valueOf(message));
                }
            }
        }
        return result;
    }

}
