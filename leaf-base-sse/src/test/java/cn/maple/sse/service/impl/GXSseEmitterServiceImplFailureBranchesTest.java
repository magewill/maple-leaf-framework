package cn.maple.sse.service.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.sse.dto.GXSseMessageInnerReqDto;
import cn.maple.sse.support.GXSseTestEmitters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the exception branches of the send / close / handshake paths that the happy-path
 * and retry tests never reach.
 */
class GXSseEmitterServiceImplFailureBranchesTest {
    private TestableSseService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.destroy();
            service = null;
        }
    }

    @Test
    void handshakeIoFailure_ClosesConnectionAndThrows() {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording().failFrom(1);
        service.supply(emitter);

        assertThrows(GXBusinessException.class, () -> service.createSseConnect("handshake-io", 600L));
        assertEquals(0, service.getActiveConnectionCount());
        assertTrue(emitter.isCompleted(), "a failed handshake must not leave a registered connection");
    }

    @Test
    void closeEventFailure_StillCompletesAndRemoves() {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("close-io", 600L);
        emitter.failFrom(2);

        assertDoesNotThrow(() -> service.closeConnect("close-io"));

        assertEquals(0, service.getActiveConnectionCount());
        assertTrue(emitter.isCompleted(), "close event failure must not prevent the connection from completing");
    }

    @Test
    void sendThrowingRuntimeException_DropsAndCloses() {
        service = new TestableSseService();
        GXSseTestEmitters.ScriptedEmitter emitter = GXSseTestEmitters.recording();
        service.supply(emitter);
        service.createSseConnect("runtime-boom", 600L);
        emitter.failWithAt(2, new IllegalArgumentException("boom"));

        assertDoesNotThrow(() -> service.sendMessageToOneClient("runtime-boom", "payload", false));

        assertEquals(0, service.getActiveConnectionCount());
        assertEquals(1L, service.getMetrics().drops());
        assertEquals(0L, service.getMetrics().retries(), "unexpected runtime errors are not retried");
    }

    @Test
    void pipelineOfferAfterClose_ReturnsClosed() {
        GXSseClientSendPipeline pipeline = new GXSseClientSendPipeline("closed", 4);
        pipeline.close();

        GXSseClientSendPipeline.OfferResult result = pipeline.offer(
                new GXSseClientSendPipeline.PendingMessage(GXSseMessageInnerReqDto.builder()
                        .data(cn.hutool.core.lang.Dict.create().set("message", "payload"))
                        .build(), 0));

        assertSame(GXSseClientSendPipeline.Outcome.CLOSED, result.outcome());
        assertEquals(0, result.dropped());
    }
}
