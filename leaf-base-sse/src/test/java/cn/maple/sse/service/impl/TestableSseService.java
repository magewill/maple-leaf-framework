package cn.maple.sse.service.impl;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service subclass that lets tests decide which emitter a connection gets, using the
 * {@code createEmitter} seam instead of reflection.
 */
class TestableSseService extends GXSseEmitterServiceImpl {
    private final Queue<SseEmitter> supplied = new ConcurrentLinkedQueue<>();

    TestableSseService supply(SseEmitter emitter) {
        supplied.add(emitter);
        return this;
    }

    @Override
    protected SseEmitter createEmitter(long timeoutMillis) {
        SseEmitter emitter = supplied.poll();
        return emitter != null ? emitter : super.createEmitter(timeoutMillis);
    }
}
