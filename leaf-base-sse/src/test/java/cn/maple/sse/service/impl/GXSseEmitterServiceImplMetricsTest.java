package cn.maple.sse.service.impl;

import cn.hutool.core.lang.Dict;
import cn.maple.sse.service.GXSseEmitterService;
import cn.maple.sse.service.GXSseMetrics;
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
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXSseEmitterServiceImplMetricsTest {
    private TestableSseService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
            service = null;
        }
    }

    @Test
    void defaultGetMetricsReturnsZeroSnapshot() {
        GXSseEmitterService bare = Mockito.mock(GXSseEmitterService.class, Mockito.CALLS_REAL_METHODS);

        assertEquals(GXSseMetrics.empty(), bare.getMetrics());
        assertEquals(0, bare.getMetrics().connections());
    }

    @Test
    void metricsTrackAttemptsAndSuccesses() {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("metrics-ok", 600L);

        service.sendMessageToOneClient("metrics-ok", "one", false);
        service.sendMessageToOneClient("metrics-ok", "two", false);
        service.sendMessageToOneClient("metrics-ok", "three", false);

        GXSseMetrics metrics = service.getMetrics();
        assertEquals(3L, metrics.attempts());
        assertEquals(3L, metrics.successes());
        assertEquals(0L, metrics.failures());
        assertEquals(0L, metrics.retries());
        assertEquals(0L, metrics.drops());
        assertEquals(1, metrics.connections());
        assertEquals(3, countMessages(emitter), "three messages, handshake excluded");
    }

    @Test
    void metricsTrackRetriesAndFailures() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("metrics-fail", 600L);
        emitter.failFrom(2);

        service.sendMessageToOneClient("metrics-fail", "payload", false);
        awaitTrue(() -> service.getMetrics().failures() == 1L, 5_000L);

        GXSseMetrics metrics = service.getMetrics();
        assertEquals(4L, metrics.attempts());
        assertEquals(0L, metrics.successes());
        assertEquals(3L, metrics.retries());
        assertEquals(1L, metrics.failures());
        assertEquals(0, metrics.connections());
    }

    @Test
    void metricsConnectionsReflectActiveCache() {
        service = new TestableSseService();
        service.createSseConnect("metrics-a", 600L);
        service.createSseConnect("metrics-b", 600L);
        assertEquals(2, service.getMetrics().connections());

        service.closeConnect("metrics-a");

        assertEquals(1, service.getMetrics().connections());
        assertEquals(service.getActiveConnectionCount(), service.getMetrics().connections());
    }

    @Test
    void metricsTrackDropsForDeadConnections() {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("metrics-dead", 600L);
        emitter.markDead();

        service.sendMessageToOneClient("metrics-dead", "payload", false);

        GXSseMetrics metrics = service.getMetrics();
        assertEquals(1L, metrics.drops());
        assertEquals(0L, metrics.retries());
        assertEquals(0L, metrics.successes());
    }

    @Test
    void logLevelIsInfoOnlyForLifecycle() {
        service = new TestableSseService();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.Logger logger =
                context.getLogger(GXSseEmitterServiceImpl.class.getName());
        Level originalLevel = logger.getLevel();
        CopyOnWriteArrayList<LogEvent> events = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender("sse-test", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        logger.addAppender(appender);
        Configurator.setLevel(GXSseEmitterServiceImpl.class.getName(), Level.DEBUG);
        try {
            GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
            service.supply(emitter);
            service.createSseConnect("log-client", 600L);
            emitter.failFrom(2);
            service.cleanupConnections();
        } finally {
            logger.removeAppender(appender);
            Configurator.setLevel(GXSseEmitterServiceImpl.class.getName(), originalLevel);
            appender.stop();
        }

        assertTrue(events.stream().anyMatch(event -> event.getMessage().getFormattedMessage().contains("Created SSE connection")),
                "connection creation stays at INFO");
        assertTrue(events.stream().anyMatch(event -> event.getLevel() == Level.DEBUG
                        && event.getMessage().getFormattedMessage().contains("Removed SSE connection")),
                "connection removal is logged at DEBUG");
        assertFalse(events.stream().anyMatch(event -> event.getLevel() == Level.INFO
                        && event.getMessage().getFormattedMessage().contains("Removed SSE connection")),
                "connection removal must not be logged at INFO");
    }

    @Test
    void mdcContextPropagatesToDrainTask() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("mdc-client", 600L);
        emitter.failOnceAt(2);

        MDC.put("traceId", "trace-converge");
        try {
            service.sendMessageToOneClient("mdc-client", "payload", false);
        } finally {
            MDC.clear();
        }
        awaitTrue(() -> emitter.sendCount() >= 3, 3_000L);

        assertTrue(emitter.traceIds().size() >= 3, "three sends expected: " + emitter.traceIds());
        assertEquals("trace-converge", emitter.traceIds().get(2), "retry task must inherit the MDC context");
    }

    @Test
    void blankBroadcastIsWarnedEvenWithoutConnections() {
        service = new TestableSseService();

        try (LogCapture capture = LogCapture.start(Level.INFO)) {
            service.sendMessageToAllClient(" ");

            List<LogEvent> events = capture.events();
            assertTrue(events.stream().anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getMessage().getFormattedMessage().contains("Skip broadcasting blank SSE message")),
                    "blank broadcast must be warned regardless of the connection count");
        }
    }

    @Test
    void broadcastWritesEveryClientOnCallerThread() {
        service = new TestableSseService();
        int connections = 100;
        List<GXSseTestEmitters.ScriptedEmitter> emitters = new CopyOnWriteArrayList<>();
        for (int index = 0; index < connections; index++) {
            GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
            emitters.add(emitter);
            service.supply(emitter);
            service.createSseConnect("cast-" + index, 600L);
        }
        String callerThread = Thread.currentThread().getName();

        service.sendMessageToAllClient("hello");

        assertEquals(connections, service.getMetrics().attempts(), "broadcast performs one write per connection");
        assertEquals(connections, service.getMetrics().successes());
        for (GXSseTestEmitters.ScriptedEmitter emitter : emitters) {
            assertTrue(emitter.threadNames().stream().allMatch(callerThread::equals),
                    "success path writes on the caller thread: " + emitter.threadNames());
        }
    }

    /**
     * Captures log events of one logger until closed.
     */
    private record LogCapture(org.apache.logging.log4j.core.Logger logger, AbstractAppender appender,
                              CopyOnWriteArrayList<LogEvent> captured) implements AutoCloseable {
        static LogCapture start(Level level) {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            org.apache.logging.log4j.core.Logger logger =
                    context.getLogger(GXSseEmitterServiceImpl.class.getName());
            CopyOnWriteArrayList<LogEvent> events = new CopyOnWriteArrayList<>();
            AbstractAppender appender = new AbstractAppender("sse-capture", null, null, false, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel().isMoreSpecificThan(level) || event.getLevel().equals(level)) {
                        events.add(event.toImmutable());
                    }
                }
            };
            appender.start();
            logger.addAppender(appender);
            return new LogCapture(logger, appender, events);
        }

        List<LogEvent> events() {
            return List.copyOf(captured);
        }

        @Override
        public void close() {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    private static long countMessages(GXSseTestEmitters.ScriptedEmitter emitter) {
        return emitter.payloads().stream().filter(Dict.class::isInstance).count();
    }

    private static void awaitTrue(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
    }
}
