package cn.maple.core.datasource.service;

import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerMetrics;
import cn.maple.core.datasource.service.impl.GXMyBatisListenerMetricsServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXMyBatisListenerMetricsTest {
    private final GXMyBatisListenerMetricsService metricsService = new GXMyBatisListenerMetricsServiceImpl();

    @Test
    void countersAreMonotonicAndDistinguishable() {
        GXMyBatisListenerMetrics.Snapshot before = metricsService.snapshot();

        GXMyBatisListenerMetrics.recordDelivered();
        GXMyBatisListenerMetrics.recordFailed();
        GXMyBatisListenerMetrics.recordRejected();
        GXMyBatisListenerMetrics.recordCancelled();
        GXMyBatisListenerMetrics.recordDegraded();
        GXMyBatisListenerMetrics.recordSkipped();

        GXMyBatisListenerMetrics.Snapshot after = metricsService.snapshot();
        assertTrue(after.delivered() > before.delivered());
        assertTrue(after.failed() > before.failed());
        assertTrue(after.rejected() > before.rejected());
        assertTrue(after.cancelled() > before.cancelled());
        assertTrue(after.degraded() > before.degraded());
        assertTrue(after.skipped() > before.skipped());
    }

    @Test
    void activeTaskCounterNeverGoesBelowZero() {
        // The gauge is process-wide, so only its invariant (non-negative, non-increasing on finish)
        // is asserted rather than an absolute zero that depends on test execution order.
        int before = metricsService.snapshot().activeTasks();
        GXMyBatisListenerMetrics.taskFinished();
        GXMyBatisListenerMetrics.taskFinished();

        int after = metricsService.snapshot().activeTasks();
        assertTrue(after >= 0, "Active tasks must never go negative");
        assertTrue(after <= before, "Finishing a task must never increase active tasks");

        // A balanced start/finish pair must return the gauge to its baseline instead of only
        // verifying the clamp, so a lost decrement would be observable.
        GXMyBatisListenerMetrics.taskStarted();
        GXMyBatisListenerMetrics.taskStarted();
        GXMyBatisListenerMetrics.taskFinished();
        GXMyBatisListenerMetrics.taskFinished();
        assertEquals(before, metricsService.snapshot().activeTasks(),
                "A balanced start/finish pair must restore the active task gauge");
    }
}
