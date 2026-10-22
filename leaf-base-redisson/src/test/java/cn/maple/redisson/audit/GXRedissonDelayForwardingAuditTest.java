package cn.maple.redisson.audit;

import cn.maple.redisson.listener.GXRedissonDelayMQListener;
import cn.maple.redisson.processor.GXRedissonDelayMQPostProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-02, F-05, F-06, F-08: delayed-queue forwarding reliability, backpressure,
 * retry cadence and delayed-queue lifecycle.
 */
@SuppressWarnings("deprecation")
class GXRedissonDelayForwardingAuditTest {

    private static final String CONFIG_CLASS = "cn.maple.redisson.processor.GXRedissonDelayMQPostProcessor$QueueListenerConfig";
    private static final String PENDING_CLASS = "cn.maple.redisson.processor.GXRedissonDelayMQPostProcessor$PendingMessage";
    private static final String QUEUE_NAME = "audit-delay-queue";
    private static final String TOPIC_NAME = "audit-delay-topic";

    private Class<?> configClass;
    private Class<?> pendingClass;
    private GXRedissonDelayMQPostProcessor processor;
    private RBlockingQueue<String> blockingQueue;
    private RDelayedQueue<String> delayedQueue;
    private BlockingQueue<Object> localQueue;
    private Map<String, String> inFlightMessages;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        configClass = GXRedissonAuditTestSupport.loadClass(CONFIG_CLASS);
        pendingClass = GXRedissonAuditTestSupport.loadClass(PENDING_CLASS);
        processor = new GXRedissonDelayMQPostProcessor();
        blockingQueue = mock(RBlockingQueue.class);
        delayedQueue = mock(RDelayedQueue.class);
        localQueue = new LinkedBlockingQueue<>(5000);
        inFlightMessages = new ConcurrentHashMap<>();
    }

    @AfterEach
    void tearDown() {
        processor.destroy();
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": message stays in memory when requeue is rejected")
    void requeueFailureKeepsMessageInMemoryOnly() {
        when(blockingQueue.offer("m-1")).thenReturn(false);
        inFlightMessages.put("p-1", "m-1");

        GXRedissonAuditTestSupport.invoke(processor, "requeuePendingMessage",
                new Class<?>[]{configClass, pendingClass},
                config(new GXRedissonDelayMQListener() {
                    @Override
                    public CompletableFuture<Boolean> execute(String topicName, String message) {
                        return CompletableFuture.completedFuture(true);
                    }
                }),
                pending("p-1", "m-1"));

        // CURRENT BEHAVIOR: the message is no longer in Redis and only the local
        // in-flight map still references it. A crash here loses the message.
        assertEquals(1, inFlightMessages.size());
        assertEquals("m-1", inFlightMessages.get("p-1"));
        verify(delayedQueue, never()).offer(anyString(), anyLong(), any());
        verify(blockingQueue, times(1)).offer("m-1");
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": in-flight messages grow beyond the consumer count")
    void inFlightMessagesGrowWithoutBackpressure() throws InterruptedException {
        List<String> batch = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            batch.add("batch-" + i);
        }
        AtomicBoolean firstCall = new AtomicBoolean(true);
        doAnswer(invocation -> {
            List<String> target = invocation.getArgument(0);
            if (firstCall.getAndSet(false)) {
                target.addAll(batch);
            }
            return null;
        }).when(blockingQueue).drainTo(any(), anyInt());

        // The listener never completes, so nothing is acknowledged downstream.
        GXRedissonDelayMQListener stallingListener = new GXRedissonDelayMQListener() {
            @Override
            public CompletableFuture<Boolean> execute(String topicName, String message) {
                return new CompletableFuture<>();
            }
        };
        Object config = config(stallingListener);

        int consumers = (int) GXRedissonAuditTestSupport.invoke(processor, "getConsumerCountForQueue", new Class<?>[]{});
        GXRedissonAuditTestSupport.invoke(processor, "startLocalQueueConsumer", new Class<?>[]{configClass}, config);
        GXRedissonAuditTestSupport.invoke(processor, "startAsyncFetchTask", new Class<?>[]{configClass}, config);

        long deadline = System.currentTimeMillis() + 5_000L;
        while (inFlightMessages.size() < batch.size() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }

        // CURRENT BEHAVIOR: no permit or window bounds in-flight work; only the local
        // queue (capacity 5000) is bounded, so 20 in-flight entries coexist with a
        // handful of consumers.
        assertEquals(batch.size(), inFlightMessages.size());
        assertTrue(inFlightMessages.size() > consumers,
                "expected more in-flight messages than consumers, consumers=" + consumers);
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": retry delay is a fixed linear step without jitter")
    void retryDelayIsFixedLinearStep() throws InterruptedException {
        AtomicInteger invocations = new AtomicInteger();
        CountDownLatch retried = new CountDownLatch(1);
        GXRedissonDelayMQListener countingListener = new GXRedissonDelayMQListener() {
            @Override
            public CompletableFuture<Boolean> execute(String topicName, String message) {
                invocations.incrementAndGet();
                retried.countDown();
                return CompletableFuture.completedFuture(false);
            }
        };
        Object config = config(countingListener);

        GXRedissonAuditTestSupport.invoke(processor, "scheduleRetry",
                new Class<?>[]{configClass, pendingClass, String.class, int.class},
                config, pending("p-2", "m-2"), "audit-thread", 1);

        // CURRENT BEHAVIOR: delay = RETRY_DELAY * attempt = 1000ms for the first
        // retry, 2000ms for the second, and so on: linear, no jitter, no cap. The
        // total window grows with the per-attempt handler timeout (30s).
        assertFalse(retried.await(300, TimeUnit.MILLISECONDS), "first retry must wait one fixed step");
        assertTrue(retried.await(2, TimeUnit.SECONDS), "first retry must fire after ~1s");
        assertTrue(invocations.get() >= 1);
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": duplicate registration skips without creating a second delayed queue")
    void duplicateRegistrationSkipsSecondDelayedQueue() {
        RedissonClient client = mock(RedissonClient.class);
        RBlockingQueue<String> redisQueue = mock(RBlockingQueue.class);
        RDelayedQueue<String> redisDelayedQueue = mock(RDelayedQueue.class);
        when(client.<String>getBlockingQueue(QUEUE_NAME)).thenReturn(redisQueue);
        when(client.getDelayedQueue(redisQueue)).thenReturn(redisDelayedQueue);

        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean("redissonMQClient", RedissonClient.class)).thenReturn(client);
        GXRedissonAuditTestSupport.setField(processor, "applicationContext", applicationContext);

        GXRedissonDelayMQListener listener = new GXRedissonDelayMQListener() {
            @Override
            public CompletableFuture<Boolean> execute(String topicName, String message) {
                return CompletableFuture.completedFuture(true);
            }
        };
        Class<?>[] types = {GXRedissonDelayMQListener.class, String.class, String.class, int.class};
        GXRedissonAuditTestSupport.invoke(processor, "startListenerTask", types, listener, QUEUE_NAME, TOPIC_NAME, 30);
        GXRedissonAuditTestSupport.invoke(processor, "startListenerTask", types, listener, QUEUE_NAME, TOPIC_NAME, 30);

        // CURRENT BEHAVIOR: the guard is a check-then-put on the config map, so the
        // duplicate path returns early and the second RDelayedQueue is never created;
        // the reference count held by the first one is released exactly once.
        verify(client, times(1)).getDelayedQueue(redisQueue);
        verify(redisDelayedQueue, never()).destroy();

        processor.destroy();

        verify(redisDelayedQueue, times(1)).destroy();
    }

    private Object config(GXRedissonDelayMQListener listener) {
        return GXRedissonAuditTestSupport.newRecord(CONFIG_CLASS,
                new Class<?>[]{String.class, String.class, int.class, GXRedissonDelayMQListener.class,
                        RBlockingQueue.class, RDelayedQueue.class, BlockingQueue.class, Map.class},
                QUEUE_NAME, TOPIC_NAME, 30, listener, blockingQueue, delayedQueue, localQueue, inFlightMessages);
    }

    private Object pending(String id, String message) {
        return GXRedissonAuditTestSupport.newRecord(PENDING_CLASS, new Class<?>[]{String.class, String.class}, id, message);
    }
}
