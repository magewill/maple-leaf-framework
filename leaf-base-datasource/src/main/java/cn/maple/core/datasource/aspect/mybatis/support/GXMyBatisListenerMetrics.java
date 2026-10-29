package cn.maple.core.datasource.aspect.mybatis.support;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Process-wide delivery counters for MyBatis write-operation listeners.
 *
 * <p>The counters are intentionally static so that aspects and listener beans can record outcomes
 * without introducing a bean dependency in the dispatch path. They reset on process restart.</p>
 *
 * <p>Counting semantics: {@code delivered} counts events whose delivery call returned successfully
 * (for asynchronous deliveries: successful submission or after-commit registration, the callback
 * itself may still be pending), while {@code failed} counts callback or delivery-layer failures.
 * Each failure is counted exactly once: callback failures are counted by the dispatch layer,
 * delivery-layer failures by the publish layer. A rejected asynchronous submission is counted only
 * in {@code rejected} by the executor and is not counted again in {@code failed}, so the four
 * categories stay distinguishable. An isolated callback failure therefore increments both
 * {@code delivered} (the delivery succeeded) and {@code failed} (the callback failed); the two
 * counters are not mutually exclusive.</p>
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
     * Read-only view of the counters. The first six values are cumulative counters: never negative
     * and never decreasing during a process lifetime (they reset only on restart), while
     * {@code activeTasks} and {@code queueSize} are instantaneous gauges that rise and fall.
     */
    public record Snapshot(long delivered, long failed, long rejected, long cancelled,
                           long degraded, long skipped, int activeTasks, int queueSize) {
    }
}
