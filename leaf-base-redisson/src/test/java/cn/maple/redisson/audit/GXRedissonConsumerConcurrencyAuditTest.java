package cn.maple.redisson.audit;

import cn.maple.redisson.processor.GXRedissonDelayMQPostProcessor;
import cn.maple.redisson.properties.GXRedissonStreamMQProperties;
import cn.maple.redisson.stream.GXRedissonStreamMQManager;
import cn.maple.redisson.stream.queue.GXRedissonStreamDelayedMQ;
import cn.maple.redisson.stream.queue.GXRedissonStreamImmediateMQ;
import cn.maple.redisson.stream.queue.GXRedissonStreamPendingMessageRecovery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * F-07, F-14: consumer concurrency depends on registration order and stream
 * lifecycle does not close on partial start failures.
 */
class GXRedissonConsumerConcurrencyAuditTest {

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": per-queue consumers depend on the number of registered queues")
    void consumerCountDependsOnRegisteredQueueCount() {
        GXRedissonDelayMQPostProcessor processor = new GXRedissonDelayMQPostProcessor();
        Map<String, Object> configs = new HashMap<>();
        GXRedissonAuditTestSupport.setField(processor, "listenerConfigs", configs);

        configs.put("queue-1", null);
        int singleQueueConsumers = (int) GXRedissonAuditTestSupport.invoke(
                processor, "getConsumerCountForQueue", new Class<?>[]{});

        for (int i = 2; i <= 64; i++) {
            configs.put("queue-" + i, null);
        }
        int manyQueueConsumers = (int) GXRedissonAuditTestSupport.invoke(
                processor, "getConsumerCountForQueue", new Class<?>[]{});

        // CURRENT BEHAVIOR: the value is derived from listenerConfigs.size() (which
        // already includes the queue being registered), so capacity depends on bean
        // registration order and total queue count.
        assertEquals(4, singleQueueConsumers);
        assertEquals(1, manyQueueConsumers);

        // Remove the placeholder entries before shutting down: destroy() dereferences
        // every stored config, and the placeholders exist only to drive the size().
        GXRedissonAuditTestSupport.setField(processor, "listenerConfigs", new java.util.concurrent.ConcurrentHashMap<>());
        processor.destroy();
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": zero blocking timeout is accepted")
    void zeroBlockingTimeoutIsAccepted() {
        GXRedissonStreamMQProperties props = GXRedissonAuditTestSupport.streamProperties();
        props.setBlockingTimeoutMillis(0L);

        // CURRENT BEHAVIOR: only negative values are rejected; 0 becomes an
        // indefinite blocking read in readGroup.
        assertDoesNotThrow(props::validate);
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": stop() does not release components after a failed start()")
    void stopDoesNotReleaseAfterPartialStartFailure() {
        RedissonClient client = mock(RedissonClient.class);
        GXRedissonStreamMQManager manager = new GXRedissonStreamMQManager(client,
                GXRedissonAuditTestSupport.streamProperties());

        GXRedissonStreamImmediateMQ immediateQueue = mock(GXRedissonStreamImmediateMQ.class);
        GXRedissonStreamDelayedMQ delayedQueue = mock(GXRedissonStreamDelayedMQ.class);
        GXRedissonStreamPendingMessageRecovery recovery = mock(GXRedissonStreamPendingMessageRecovery.class);
        doThrow(new IllegalStateException("scheduler rejected")).when(delayedQueue).start();

        GXRedissonAuditTestSupport.setField(manager, "immediateQueue", immediateQueue);
        GXRedissonAuditTestSupport.setField(manager, "delayedQueue", delayedQueue);
        GXRedissonAuditTestSupport.setField(manager, "pendingRecovery", recovery);

        assertThrows(IllegalStateException.class, manager::start);

        // CURRENT BEHAVIOR: started stays false, so stop() returns immediately and the
        // already-started immediate queue keeps its consumer threads.
        manager.stop();
        verify(immediateQueue, never()).stop();
        verify(recovery, never()).stop();
    }
}
