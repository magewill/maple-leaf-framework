package cn.maple.rocketmq.dispatcher;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

/**
 * Local counters for CDC dispatch outcomes, optionally exposed as Micrometer gauges.
 *
 * <p>Metrics are registered only when a {@link MeterRegistry} is available, so the module
 * keeps working in applications without actuator.</p>
 */
@Slf4j
@Component
public class GXCdcDispatchStatistics {
    private static final String METRIC_PREFIX = "rocketmq.cdc.dispatch.";

    private final LongAdder success = new LongAdder();

    private final LongAdder parseFailure = new LongAdder();

    private final LongAdder unknownOperation = new LongAdder();

    private final LongAdder listenerFailure = new LongAdder();

    private final LongAdder deadLetter = new LongAdder();

    private final LongAdder skipped = new LongAdder();

    private final LongAdder noTarget = new LongAdder();

    public GXCdcDispatchStatistics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        meterRegistryProvider.ifAvailable(this::registerGauges);
    }

    public void recordSuccess() {
        success.increment();
    }

    public void recordParseFailure() {
        parseFailure.increment();
    }

    public void recordUnknownOperation() {
        unknownOperation.increment();
    }

    public void recordListenerFailure() {
        listenerFailure.increment();
    }

    public void recordDeadLetter() {
        deadLetter.increment();
    }

    public void recordSkipped() {
        skipped.increment();
    }

    public void recordNoTarget() {
        noTarget.increment();
    }

    public long successCount() {
        return success.sum();
    }

    public long parseFailureCount() {
        return parseFailure.sum();
    }

    public long unknownOperationCount() {
        return unknownOperation.sum();
    }

    public long listenerFailureCount() {
        return listenerFailure.sum();
    }

    public long deadLetterCount() {
        return deadLetter.sum();
    }

    public long skippedCount() {
        return skipped.sum();
    }

    public long noTargetCount() {
        return noTarget.sum();
    }

    private void registerGauges(MeterRegistry registry) {
        try {
            registerGauge(registry, "success", success);
            registerGauge(registry, "parse.failure", parseFailure);
            registerGauge(registry, "unknown.operation", unknownOperation);
            registerGauge(registry, "listener.failure", listenerFailure);
            registerGauge(registry, "dead.letter", deadLetter);
            registerGauge(registry, "skipped", skipped);
            registerGauge(registry, "no.target", noTarget);
        } catch (RuntimeException e) {
            log.warn("Failed to register CDC dispatch metrics. error={}", e.getMessage());
        }
    }

    private void registerGauge(MeterRegistry registry, String name, LongAdder adder) {
        Gauge.builder(METRIC_PREFIX + name, adder, Number::doubleValue).register(registry);
    }
}
