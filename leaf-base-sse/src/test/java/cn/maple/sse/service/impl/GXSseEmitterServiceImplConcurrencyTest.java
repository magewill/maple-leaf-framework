package cn.maple.sse.service.impl;

import cn.maple.sse.support.GXSseTestEmitters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXSseEmitterServiceImplConcurrencyTest {
    private TestableSseService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
            service = null;
        }
    }

    @Test
    void concurrentSameClientId_EndsWithSingleConnectionAndNoException() throws InterruptedException {
        service = new TestableSseService();
        int attempts = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(attempts);
        try {
            for (int index = 0; index < attempts; index++) {
                pool.execute(() -> {
                    try {
                        service.createSseConnect("shared-client", 600L);
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(10L, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(List.of(), failures, "concurrent reconnects must not fail");
        assertEquals(1, service.getActiveConnectionCount());
    }

    @Test
    void concurrentCreateAndClose_KeepsCacheConsistent() throws InterruptedException {
        service = new TestableSseService();
        int rounds = 200;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(rounds * 2);
        try {
            for (int index = 0; index < rounds; index++) {
                String clientId = "churn-" + (index % 8);
                pool.execute(() -> {
                    try {
                        service.createSseConnect(clientId, 600L);
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
                pool.execute(() -> {
                    try {
                        service.closeConnect(clientId);
                        service.sendMessageToAllClient("churn");
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(20L, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(List.of(), failures);
        assertTrue(service.getActiveConnectionCount() <= 8);
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void slowRetryDoesNotDelayOtherRetries() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter slow = GXSseTestEmitters.recording()
                .failOnceAt(2)
                .blockOnSend(3, 800L)
                .failFrom(3);
        GXSseTestEmitters.ScriptedEmitter fast = GXSseTestEmitters.recording().failOnceAt(2);
        service.supply(slow).supply(fast);
        service.createSseConnect("slow-client", 600L);
        service.createSseConnect("fast-client", 600L);

        service.sendMessageToOneClient("slow-client", "slow", false);
        long startNanos = System.nanoTime();
        service.sendMessageToOneClient("fast-client", "fast", false);
        awaitTrue(() -> messageCount(fast) == 1, 3_000L);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertEquals(1, messageCount(fast), "fast client must receive its message");
        assertTrue(elapsedMillis < 600L,
                "retry of one client must not be serialized behind another, took " + elapsedMillis + "ms");
    }

    @Test
    void broadcastDuringSlowSendDeliversToOtherClients() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter slow = GXSseTestEmitters.recording().blockOnSend(2, 600L);
        GXSseTestEmitters.ScriptedEmitter fast = GXSseTestEmitters.recording();
        service.supply(slow).supply(fast);
        service.createSseConnect("broadcast-slow", 600L);
        service.createSseConnect("broadcast-fast", 600L);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.execute(() -> service.sendMessageToAllClient("hello"));
            awaitTrue(() -> slow.sendCount() >= 2, 2_000L);
        } finally {
            pool.shutdownNow();
        }

        awaitTrue(() -> messageCount(fast) == 1, 2_000L);
        assertEquals(1, messageCount(fast));
    }

    private static long messageCount(GXSseTestEmitters.ScriptedEmitter emitter) {
        return emitter.payloads().stream().filter(cn.hutool.core.lang.Dict.class::isInstance).count();
    }

    private static void awaitTrue(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
    }
}
