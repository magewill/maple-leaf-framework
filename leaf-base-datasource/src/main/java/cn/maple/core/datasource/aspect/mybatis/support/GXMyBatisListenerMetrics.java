package cn.maple.core.datasource.aspect.mybatis.support;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Process-wide delivery counters for MyBatis write-operation listeners.
 *
 * <p>The counters are intentionally static so that aspects and listener beans can record outcomes
 * without introducing a bean dependency in the dispatch path. They reset on process restart.</p>
 */
public final class GXMyBatisListenerMetrics {
    private static final LongAdder DELIVERED = new LongAdder();
    private static final LongAdder FAILED = new LongAdder();
    private static final LongAdder REJECTED = new LongAdder();
    private static final LongAdder CANCELLED = new LongAdder();
    private static final LongAdder DEGRADED = new LongAdder();
    private static final LongAdder SKIPPED = new LongAdder();
    private static final AtomicInteger ACTIVE_TASKS = new AtomicInteger();
    private static final AtomicInteger QUEUE_SIZE = new AtomicInteger();

    private GXMyBatisListenerMetrics() {
    }

    public static void recordDelivered() {
        DELIVERED.increment();
    }

    public static void recordFailed() {
        FAILED.increment();
    }

    public static void recordRejected() {
        REJECTED.increment();
    }

    public static void recordCancelled() {
        CANCELLED.increment();
    }

    public static void recordDegraded() {
        DEGRADED.increment();
    }

    public static void recordSkipped() {
        SKIPPED.increment();
    }

    public static void taskStarted() {
        ACTIVE_TASKS.incrementAndGet();
    }

    public static void taskFinished() {
        ACTIVE_TASKS.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    public static void updateQueueSize(int queueSize) {
        QUEUE_SIZE.set(Math.max(0, queueSize));
    }

    public static Snapshot snapshot() {
        return new Snapshot(DELIVERED.sum(), FAILED.sum(), REJECTED.sum(), CANCELLED.sum(),
                DEGRADED.sum(), SKIPPED.sum(), ACTIVE_TASKS.get(), QUEUE_SIZE.get());
    }

    /**
     * Read-only view of the counters. Never negative, never decreases during a process lifetime.
     */
    public record Snapshot(long delivered, long failed, long rejected, long cancelled,
                           long degraded, long skipped, int activeTasks, int queueSize) {
    }
}
