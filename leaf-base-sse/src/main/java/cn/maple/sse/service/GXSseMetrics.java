package cn.maple.sse.service;

/**
 * Immutable snapshot of the SSE delivery counters.
 * <p>
 * All counters except {@code connections} are monotonic; {@code connections} is the
 * number of currently registered emitters. The snapshot is weakly consistent: the
 * fields are read independently and must not be used to assert a conservation law.
 * </p>
 *
 * @param attempts    send attempts, including retries
 * @param successes   successful writes
 * @param failures    messages dropped after retries were exhausted
 * @param retries     retry attempts actually executed
 * @param drops       dropped messages (dead connection, full queue, empty payload)
 * @param connections currently registered connections
 */
public record GXSseMetrics(long attempts,
                           long successes,
                           long failures,
                           long retries,
                           long drops,
                           int connections) {
    /**
     * @return zeroed snapshot used as the interface default
     */
    public static GXSseMetrics empty() {
        return new GXSseMetrics(0L, 0L, 0L, 0L, 0L, 0);
    }
}
