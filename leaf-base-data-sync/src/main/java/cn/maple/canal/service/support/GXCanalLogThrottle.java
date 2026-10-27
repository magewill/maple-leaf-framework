package cn.maple.canal.service.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Throttles repetitive hot path logs so a failure storm does not amplify into a log storm.
 *
 * <p>The first {@link #FIRST_LOG_COUNT} occurrences of a key are logged, later occurrences only update
 * the counters in {@link cn.maple.canal.metrics.GXCanalConsumeStatistics}. State is per instance, not
 * static, so several application contexts in one JVM stay isolated.</p>
 */
@Slf4j
@Component
public class GXCanalLogThrottle {
    private static final int FIRST_LOG_COUNT = 3;

    private final ConcurrentHashMap<String, AtomicInteger> occurrences = new ConcurrentHashMap<>();

    /**
     * Whether this occurrence of the key should be logged.
     *
     * @param key stable throttle key, for example {@code invalid-json} or a database/table pair
     * @return true for the first {@link #FIRST_LOG_COUNT} occurrences
     */
    public boolean shouldLog(String key) {
        AtomicInteger counter = occurrences.computeIfAbsent(key, ignored -> new AtomicInteger());
        return counter.incrementAndGet() <= FIRST_LOG_COUNT;
    }

    /**
     * Warns only for the first occurrences of the key.
     *
     * @param key      throttle key
     * @param format   SLF4J format, ASCII only
     * @param arguments format arguments
     */
    public void warn(String key, String format, Object... arguments) {
        if (shouldLog(key)) {
            log.warn(format, arguments);
        }
    }

    /**
     * Logs an error only for the first occurrences of the key.
     *
     * @param key      throttle key
     * @param format   SLF4J format, ASCII only
     * @param arguments format arguments
     */
    public void error(String key, String format, Object... arguments) {
        if (shouldLog(key)) {
            log.error(format, arguments);
        }
    }
}
