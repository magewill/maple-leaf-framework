package cn.maple.sse.service.impl;

import cn.maple.sse.support.GXSseTestEmitters;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the scheduled heartbeat trigger ({@code triggerHeartbeat}), which is the only
 * production path the minute-timer drives but which unit tests never used to reach.
 */
class GXSseEmitterServiceImplHeartbeatTest {
    private TestableSseService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
            service = null;
        }
    }

    @Test
    void heartbeatTriggerRemovesInvalidConnections() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter healthy = GXSseTestEmitters.recording();
        GXSseTestEmitters.ScriptedEmitter broken = GXSseTestEmitters.recording();
        service.supply(healthy).supply(broken);
        service.createSseConnect("heartbeat-healthy", 600L);
        service.createSseConnect("heartbeat-broken", 600L);
        broken.failFrom(2);

        service.triggerHeartbeat();
        awaitTrue(() -> service.getActiveConnectionCount() == 1, 3_000L);

        assertEquals(1, service.getActiveConnectionCount());
        assertTrue(broken.isCompleted(), "a dead connection must be closed by the heartbeat round");
    }

    @Test
    void heartbeatTriggerScansMoreThanOneBatch() throws InterruptedException {
        service = new TestableSseService();
        int connections = 300;
        CopyOnWriteArrayList<GXSseTestEmitters.ScriptedEmitter> emitters = new CopyOnWriteArrayList<>();
        for (int index = 0; index < connections; index++) {
            GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
            emitters.add(emitter);
            service.supply(emitter);
            service.createSseConnect("hb-" + index, 600L);
        }

        Configurator.setLevel(GXSseEmitterServiceImpl.class.getName(), Level.DEBUG);
        HeartbeatLogCapture capture = new HeartbeatLogCapture();
        try {
            service.triggerHeartbeat();
            awaitTrue(() -> emitters.stream().allMatch(emitter -> emitter.sendCount() >= 2), 15_000L);
        } finally {
            capture.close();
            Configurator.setLevel(GXSseEmitterServiceImpl.class.getName(), (Level) null);
        }

        assertTrue(emitters.stream().allMatch(emitter -> emitter.sendCount() == 2),
                "every connection receives exactly one heartbeat");
        long batchCount = capture.messages().stream()
                .filter(message -> message.contains("SSE heartbeat batch finished")).count();
        assertTrue(batchCount >= 2, "300 connections must span at least 2 batches of 256, saw " + batchCount);
        assertEquals(connections, service.getActiveConnectionCount());
    }

    private static void awaitTrue(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
    }

    /**
     * Captures DEBUG messages of the SSE service logger.
     */
    private static final class HeartbeatLogCapture implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger logger;
        private final AbstractAppender appender;
        private final CopyOnWriteArrayList<String> messages = new CopyOnWriteArrayList<>();

        private HeartbeatLogCapture() {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            this.logger = context.getLogger(GXSseEmitterServiceImpl.class.getName());
            this.appender = new AbstractAppender("heartbeat-capture", null, null, false, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel() == Level.DEBUG) {
                        messages.add(event.getMessage().getFormattedMessage());
                    }
                }
            };
            appender.start();
            logger.addAppender(appender);
        }

        List<String> messages() {
            return List.copyOf(messages);
        }

        @Override
        public void close() {
            logger.removeAppender(appender);
            appender.stop();
        }
    }
}
