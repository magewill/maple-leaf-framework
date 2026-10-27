package cn.maple.canal.metrics;

import cn.maple.canal.constant.GXCanalConstant;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

/**
 * Thread safe consumption counters.
 *
 * <p>Counters always work. Gauges are registered only when a {@link MeterRegistry} is available, so
 * applications without actuator keep working and no hard dependency is introduced.</p>
 */
@Slf4j
@Component
public class GXCanalConsumeStatistics {
    private final LongAdder success = new LongAdder();

    private final LongAdder skipped = new LongAdder();

    private final LongAdder retryableFailure = new LongAdder();

    private final LongAdder nonRetryableFailure = new LongAdder();

    private final LongAdder handlerMiss = new LongAdder();

    private final LongAdder deadLetter = new LongAdder();

    private final LongAdder batchEvents = new LongAdder();

    /**
     * Counters only instance, for environments without a metrics registry.
     */
    public GXCanalConsumeStatistics() {
        // Counters only.
    }

    /**
     * Registers gauges when a {@link MeterRegistry} is available.
     *
     * @param meterRegistryProvider optional registry provider
     */
    @Autowired
    public GXCanalConsumeStatistics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        meterRegistryProvider.ifAvailable(this::registerGauges);
    }

    public void recordSuccess() {
        success.increment();
    }

    public void recordSkipped() {
        skipped.increment();
    }

    public void recordRetryableFailure() {
        retryableFailure.increment();
    }

    public void recordNonRetryableFailure() {
        nonRetryableFailure.increment();
    }

    public void recordHandlerMiss() {
        handlerMiss.increment();
    }

    public void recordDeadLetter() {
        deadLetter.increment();
    }

    /**
     * Records how many events one consumed payload contained.
     *
     * @param count event count, values below one are ignored
     */
    public void recordBatchEvents(int count) {
        if (count > 0) {
            batchEvents.add(count);
        }
    }

    public long successCount() {
        return success.sum();
    }

    public long skippedCount() {
        return skipped.sum();
    }

    public long retryableFailureCount() {
        return retryableFailure.sum();
    }

    public long nonRetryableFailureCount() {
        return nonRetryableFailure.sum();
    }

    public long handlerMissCount() {
        return handlerMiss.sum();
    }

    public long deadLetterCount() {
        return deadLetter.sum();
    }

    public long batchEventsCount() {
        return batchEvents.sum();
    }

    /**
     * Resets all counters. Intended for tests and manual diagnosis.
     */
    public void reset() {
        success.reset();
        skipped.reset();
        retryableFailure.reset();
        nonRetryableFailure.reset();
        handlerMiss.reset();
        deadLetter.reset();
        batchEvents.reset();
    }

    /**
     * Logs a one line summary on shutdown so a final tally is always available in the logs.
     */
    @PreDestroy
    public void logSummary() {
        log.info("Canal consumer summary: success={}, skipped={}, retryableFailure={}, nonRetryableFailure={}, "
                        + "handlerMiss={}, deadLetter={}, batchEvents={}",
                success.sum(), skipped.sum(), retryableFailure.sum(), nonRetryableFailure.sum(),
                handlerMiss.sum(), deadLetter.sum(), batchEvents.sum());
    }

    private void registerGauges(MeterRegistry registry) {
        try {
            registerGauge(registry, "success", success);
            registerGauge(registry, "skipped", skipped);
            registerGauge(registry, "retryable.failure", retryableFailure);
            registerGauge(registry, "non.retryable.failure", nonRetryableFailure);
            registerGauge(registry, "handler.miss", handlerMiss);
            registerGauge(registry, "dead.letter", deadLetter);
            registerGauge(registry, "batch.events", batchEvents);
        } catch (RuntimeException e) {
            log.warn("Failed to register Canal consumer metrics. error={}", e.getMessage());
        }
    }

    private void registerGauge(MeterRegistry registry, String name, LongAdder adder) {
        Gauge.builder(GXCanalConstant.METRIC_PREFIX + name, adder, Number::doubleValue).register(registry);
    }
}
