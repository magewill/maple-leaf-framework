package cn.maple.sse.service.impl;

import cn.maple.sse.dto.GXSseMessageInnerReqDto;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * FIFO send pipeline for a single client connection.
 * <p>
 * A pipeline is created only after the first send failure of a connection. From then
 * on every message for that connection is queued and delivered by a single drain task,
 * which keeps {@code M1, M2, ...} ordering while retries are being retried. Once the
 * connection is closed the pipeline is discarded together with the emitter.
 * </p>
 * <p>
 * The queue is guarded by the pipeline instance monitor; {@link #poll()} releases the
 * drain ownership flag inside the same critical section, so a concurrent
 * {@link #offer(PendingMessage)} can never be left without a drain task.
 * </p>
 */
final class GXSseClientSendPipeline {
    private final String clientId;
    private final int capacity;
    private final Deque<PendingMessage> queue = new ArrayDeque<>();
    private boolean draining;
    private boolean closed;

    GXSseClientSendPipeline(String clientId, int capacity) {
        this.clientId = clientId;
        this.capacity = capacity;
    }

    /**
     * Appends a message, dropping the oldest one when the queue is full so that the newest
     * messages survive on a slow connection.
     *
     * @param message message to append
     * @return outcome of the offer plus the number of messages discarded to make room;
     * {@link Outcome#STARTED} means the caller must submit a drain task
     */
    synchronized OfferResult offer(PendingMessage message) {
        if (closed) {
            return new OfferResult(Outcome.CLOSED, 0);
        }
        int dropped = 0;
        while (queue.size() >= capacity) {
            queue.pollFirst();
            dropped++;
        }
        queue.addLast(message);
        if (!draining) {
            draining = true;
            return new OfferResult(Outcome.STARTED, dropped);
        }
        return new OfferResult(Outcome.QUEUED, dropped);
    }

    /**
     * @return head message, or {@code null} when the queue is empty (drain ownership released)
     */
    synchronized PendingMessage poll() {
        PendingMessage message = queue.pollFirst();
        if (message == null) {
            draining = false;
        }
        return message;
    }

    /**
     * @return number of messages discarded by this call
     */
    synchronized int close() {
        closed = true;
        int discarded = queue.size();
        queue.clear();
        draining = false;
        return discarded;
    }

    @Override
    public String toString() {
        return "GXSseClientSendPipeline[" + clientId + "]";
    }

    /**
     * Message waiting in the pipeline.
     *
     * @param message payload and event metadata
     * @param attempt attempts already performed for this message, {@code 0} when it never left the queue
     */
    record PendingMessage(GXSseMessageInnerReqDto message, int attempt) {
    }

    /**
     * Result of {@link #offer(PendingMessage)}.
     *
     * @param outcome what the caller has to do next
     * @param dropped number of old messages discarded to make room
     */
    record OfferResult(Outcome outcome, int dropped) {
    }

    /**
     * Action required after an offer.
     */
    enum Outcome {
        /**
         * Queued and the caller must submit a drain task.
         */
        STARTED,
        /**
         * Queued, an existing drain task will pick it up.
         */
        QUEUED,
        /**
         * Rejected because the pipeline is closed.
         */
        CLOSED
    }
}
