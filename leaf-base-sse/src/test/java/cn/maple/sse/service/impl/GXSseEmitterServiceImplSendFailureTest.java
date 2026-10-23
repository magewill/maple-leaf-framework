package cn.maple.sse.service.impl;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.sse.dto.GXSseMessageInnerReqDto;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXSseEmitterServiceImplSendFailureTest {
    private TestableSseService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
            service = null;
        }
    }

    @Test
    void retryExhausted_ClosesConnectionAndRemovesFromCache() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("exhausted", 600L);
        emitter.failFrom(2);

        service.sendMessageToOneClient("exhausted", "payload", false);
        awaitTrue(() -> service.getActiveConnectionCount() == 0, 5_000L);

        assertEquals(0, service.getActiveConnectionCount());
        assertTrue(emitter.isCompleted(), "connection must be completed, not only unregistered");
        assertEquals(5, emitter.sendCount(), "1 handshake + 4 message attempts");
        assertEquals(1L, service.getMetrics().failures());
        assertEquals(3L, service.getMetrics().retries());
        assertEquals(4L, service.getMetrics().attempts(), "1 initial attempt + 3 retries");
    }

    @Test
    void heartbeatFailure_ClosesConnection() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("heartbeat", 600L);
        emitter.failFrom(2);

        service.cleanupConnections();
        awaitTrue(() -> service.getActiveConnectionCount() == 0, 2_000L);

        assertEquals(0, service.getActiveConnectionCount());
        assertTrue(emitter.isCompleted(), "heartbeat failure must close the connection");
    }

    @Test
    void sendHeartbeatFailure_ClosesConnection() {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("heartbeat-one", 600L);
        emitter.failFrom(2);

        assertFalse(service.sendHeartbeat("heartbeat-one"), "failed heartbeat returns false");
        assertEquals(0, service.getActiveConnectionCount());
        assertTrue(emitter.isCompleted());
    }

    @Test
    void initialSendFailure_WhenStillCurrent_ThrowsBusinessException() {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording().markDead();
        service.supply(emitter);

        assertThrows(GXBusinessException.class, () -> service.createSseConnect("dead-handshake", 600L));
        assertEquals(0, service.getActiveConnectionCount());
        assertTrue(emitter.isCompleted(), "failed handshake must not leave a registered connection");
    }

    @Test
    void initialSendFailure_WhenReplaced_ReturnsEmitterWithoutException() throws Exception {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter first = GXSseTestEmitters.recording().holdFirstSend();
        service.supply(first);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<SseEmitter> firstConnect = pool.submit(() -> service.createSseConnect("replaced", 600L));
            assertTrue(first.awaitEntered(5L, TimeUnit.SECONDS));

            SseEmitter replacement = service.createSseConnect("replaced", 600L);
            first.releaseFirstSend();

            assertNull(awaitFailure(firstConnect));
            assertEquals(1, service.getActiveConnectionCount());
            assertEquals(replacement, service.getSseEmitterByClientId("replaced"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedMessage_IsRetriedBeforeNextMessage() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("ordered", 600L);
        emitter.failOnceAt(2);

        service.sendMessageToOneClient("ordered", "M1", false);
        service.sendMessageToOneClient("ordered", "M2", false);
        awaitTrue(() -> messages(emitter).size() == 2, 5_000L);

        assertEquals(List.of("M1", "M2"), messages(emitter));
    }

    @Test
    void splitChunks_ShareMsgIdAndCarryChunkIndex() {
        service = new TestableSseService();
        List<String> fragments = service.splitMessage("abcdefghij", 3);

        String msgId = "shared-msg-id";
        List<GXSseMessageInnerReqDto> chunks = new ArrayList<>();
        for (int index = 0; index < fragments.size(); index++) {
            chunks.add(service.buildMessageDto("chunked", fragments.get(index), msgId, index + 1, fragments.size()));
        }

        assertTrue(chunks.size() > 1, "message must be split");
        for (int index = 0; index < chunks.size(); index++) {
            GXSseMessageInnerReqDto chunk = chunks.get(index);
            assertEquals(msgId, chunk.getMsgId(), "all chunks share the message id");
            assertTrue(chunk.getComment().contains("chunk=" + (index + 1) + "/" + chunks.size()),
                    "chunk index must be exposed in the comment: " + chunk.getComment());
            assertEquals("chunked", chunk.getClientId());
        }
    }

    @Test
    void unsplitMessageUsesDtoDefaultMsgId() {
        service = new TestableSseService();

        GXSseMessageInnerReqDto withoutId = service.buildMessageDto("unsplit", "payload", "", 1, 1);
        GXSseMessageInnerReqDto ignoredId = service.buildMessageDto("unsplit", "payload", "explicit-id", 1, 1);
        GXSseMessageInnerReqDto chunked = service.buildMessageDto("unsplit", "payload", "explicit-id", 1, 2);

        assertNotNull(withoutId.getMsgId());
        assertFalse(withoutId.getMsgId().isBlank(), "an unsplit message falls back to the DTO default id");
        assertNotEquals("explicit-id", ignoredId.getMsgId(),
                "the passed id is ignored when there is only one chunk, so no extra UUID is generated");
        assertEquals("explicit-id", chunked.getMsgId(), "chunked messages share the explicit id");
    }

    @Test
    void broadcastQueuesBehindPendingRetry() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("broadcast-pending", 600L);
        emitter.failOnceAt(2);

        service.sendMessageToOneClient("broadcast-pending", "M1", false);
        service.sendMessageToAllClient("M2");
        awaitTrue(() -> messages(emitter).size() == 2, 5_000L);

        assertEquals(List.of("M1", "M2"), messages(emitter),
                "a broadcast to a recovering connection must queue behind the pending message");
    }

    @Test
    void deadConnection_DropsMessageWithoutRetry() throws InterruptedException {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("dead", 600L);
        emitter.markDead();

        service.sendMessageToOneClient("dead", "payload", false);
        awaitTrue(() -> service.getActiveConnectionCount() == 0, 2_000L);

        assertEquals(0, service.getActiveConnectionCount());
        assertEquals(2, emitter.sendCount(), "completed connection must not be retried");
        assertEquals(1L, service.getMetrics().drops());
        assertEquals(0L, service.getMetrics().retries());
    }

    @Test
    void pendingQueueDropsOldestWhenFull() {
        GXSseClientSendPipeline pipeline = new GXSseClientSendPipeline("full-client", 2);

        assertEquals(GXSseClientSendPipeline.Outcome.STARTED,
                pipeline.offer(new GXSseClientSendPipeline.PendingMessage(message("m1"), 0)).outcome());
        assertEquals(GXSseClientSendPipeline.Outcome.QUEUED,
                pipeline.offer(new GXSseClientSendPipeline.PendingMessage(message("m2"), 0)).outcome());
        GXSseClientSendPipeline.OfferResult overflow =
                pipeline.offer(new GXSseClientSendPipeline.PendingMessage(message("m3"), 0));

        assertEquals(1, overflow.dropped(), "only the oldest message is discarded");
        assertEquals(GXSseClientSendPipeline.Outcome.QUEUED, overflow.outcome());
        assertEquals("m2", payloadOf(pipeline.poll()));
        assertEquals("m3", payloadOf(pipeline.poll()));
        assertNull(pipeline.poll());
    }

    private static GXSseMessageInnerReqDto message(String content) {
        return GXSseMessageInnerReqDto.builder()
                .data(Dict.create().set("message", content))
                .eventName("Push Msg")
                .build();
    }

    private static String payloadOf(GXSseClientSendPipeline.PendingMessage pending) {
        assertNotNull(pending);
        return String.valueOf(pending.message().getData().get("message"));
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
            Thread.sleep(10L);
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
