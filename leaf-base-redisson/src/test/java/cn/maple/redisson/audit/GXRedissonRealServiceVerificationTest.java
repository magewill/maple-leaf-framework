package cn.maple.redisson.audit;

import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.redisson.properties.GXRedissonStreamMQProperties;
import cn.maple.redisson.stream.GXRedissonStreamMQManager;
import cn.maple.redisson.util.GXRedissonDelayMQUtils;
import cn.maple.redisson.util.GXRedissonMQUtils;
import cn.maple.redisson.util.GXRedissonUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.redisson.Redisson;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in verification against a real Redis server.
 * <p>
 * Skipped unless {@code AUDIT_REDIS_HOST} is set, so the default pipeline stays
 * independent of external middleware. Credentials are read from the environment
 * and never hard-coded:
 *
 * <pre>
 * $env:AUDIT_REDIS_HOST="&lt;redis-host&gt;"
 * $env:AUDIT_REDIS_PASSWORD="&lt;from-environment&gt;"
 * mvn -pl leaf-base-redisson -o test -Dtest=GXRedissonRealServiceVerificationTest
 * </pre>
 * <p>
 * Optional: {@code AUDIT_REDIS_PORT} (default 6379), {@code AUDIT_REDIS_DATABASE}
 * (default 0), {@code AUDIT_REDIS_USERNAME}.
 */
@EnabledIfEnvironmentVariable(named = "AUDIT_REDIS_HOST", matches = ".+")
@DisplayName("Real service verification (opt-in)")
class GXRedissonRealServiceVerificationTest {

    private static final String KEY_PREFIX = "audit:verify:";
    private static final long AWAIT_SECONDS = 10L;

    private static RedissonClient client;

    @BeforeAll
    static void connect() {
        Config config = new Config();
        config.setCodec(new JsonJacksonCodec());
        SingleServerConfig single = config.useSingleServer()
                .setAddress("redis://" + host() + ":" + port())
                .setDatabase(database())
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(8)
                .setTimeout(5_000)
                .setConnectTimeout(5_000);
        if (isNotBlank(env("AUDIT_REDIS_USERNAME"))) {
            single.setUsername(env("AUDIT_REDIS_USERNAME"));
        }
        if (isNotBlank(env("AUDIT_REDIS_PASSWORD"))) {
            single.setPassword(env("AUDIT_REDIS_PASSWORD"));
        }

        try {
            client = Redisson.create(config);
            // Probe the connection: any failure means the environment is absent, so the
            // whole class is skipped instead of failing the pipeline.
            client.getBucket(KEY_PREFIX + "probe").isExists();
        } catch (Exception e) {
            Assumptions.assumeTrue(false,
                    "Redis at " + host() + ":" + port() + " is not reachable: " + e.getMessage());
        }
    }

    @AfterAll
    static void disconnect() {
        GXRedissonMQUtils.clearLocalCache();
        GXRedissonDelayMQUtils.clearDelayedQueueCache();
        if (client != null) {
            try {
                client.getKeys().deleteByPattern(KEY_PREFIX + "*");
            } finally {
                client.shutdown();
                client = null;
            }
        }
    }

    @Test
    @DisplayName("reliable topic delivers to the subscriber and stops after unsubscribe")
    void reliableTopicDeliversAndStopsAfterUnsubscribe() throws InterruptedException {
        String topic = KEY_PREFIX + "topic:" + UUID.randomUUID();
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        try (MockedStatic<GXSpringContextUtils> context = mockClient()) {
            GXRedissonMQUtils.clearLocalCache();
            String listenerId = GXRedissonMQUtils.subscribe(topic, String.class, (channel, message) -> {
                received.set(message);
                delivered.countDown();
            });

            assertNotNull(listenerId);
            long subscriberCount = GXRedissonMQUtils.publish(topic, "hello-real-redis");
            assertEquals(1L, subscriberCount);
            assertTrue(delivered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "subscriber did not receive the message");
            assertEquals("hello-real-redis", received.get());

            GXRedissonMQUtils.unsubscribe(topic, listenerId);
            assertFalse(GXRedissonMQUtils.hasListener(topic, String.class));
            // The consumer group is destroyed with the last listener, so nobody is left to count.
            assertEquals(0L, GXRedissonMQUtils.publish(topic, "after-unsubscribe"));
        }
    }

    @Test
    @DisplayName("delayed queue moves the message to the blocking queue after the delay")
    void delayedQueueDeliversExpiredMessage() throws InterruptedException {
        String queue = KEY_PREFIX + "delay:" + UUID.randomUUID();
        RBlockingQueue<String> blockingQueue = client.getBlockingQueue(queue);

        try (MockedStatic<GXSpringContextUtils> context = mockClient()) {
            GXRedissonDelayMQUtils.sendDelayMessage(queue, "delayed-payload", 500, TimeUnit.MILLISECONDS);

            long start = System.currentTimeMillis();
            String payload = blockingQueue.poll(AWAIT_SECONDS, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            assertNotNull(payload, "delayed message did not reach the blocking queue");
            assertTrue(payload.contains("delayed-payload"), "unexpected payload: " + payload);
            assertTrue(elapsed >= 400L, "message arrived before the delay elapsed: " + elapsed + "ms");
        }
    }

    @Test
    @DisplayName("counter increments and expires according to the configured ttl")
    void counterIncrementsAndExpires() throws InterruptedException {
        String key = KEY_PREFIX + "counter:" + UUID.randomUUID();

        try (MockedStatic<GXSpringContextUtils> context = mockClient()) {
            assertEquals(1L, GXRedissonUtils.incrementAndGet(key, 2, TimeUnit.SECONDS));
            assertEquals(2L, GXRedissonUtils.incrementAndGet(key, 2, TimeUnit.SECONDS));

            long ttl = client.getAtomicLong(key).remainTimeToLive();
            assertTrue(ttl > 0L && ttl <= 2_000L, "unexpected ttl: " + ttl);

            Thread.sleep(2_500L);
            assertFalse(GXRedissonUtils.counterExists(key), "counter should have expired");
            assertEquals(0L, GXRedissonUtils.getCounterValue(key));
        }
    }

    @Test
    @DisplayName("distributed lock serialises concurrent operations")
    void lockSerialisesConcurrentOperations() throws Exception {
        String lockName = KEY_PREFIX + "lock:" + UUID.randomUUID();
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(1);
        int workers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(workers);

        try {
            List<Future<?>> futures = new ArrayList<>(workers);
            for (int i = 0; i < workers; i++) {
                futures.add(pool.submit(() -> {
                    ready.await();
                    // Mockito static mocks are thread-scoped, so each worker installs its own.
                    try (MockedStatic<GXSpringContextUtils> context = mockClient()) {
                        GXRedissonUtils.executeWithLock(lockName, () -> {
                            executions.incrementAndGet();
                            int current = concurrent.incrementAndGet();
                            maxConcurrent.accumulateAndGet(current, Math::max);
                            try {
                                Thread.sleep(150L);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                concurrent.decrementAndGet();
                            }
                            return null;
                        });
                    }
                    return null;
                }));
            }
            ready.countDown();
            for (Future<?> future : futures) {
                future.get(AWAIT_SECONDS + 10L, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(workers, executions.get(), "every operation must acquire the lock and run");
        assertEquals(1, maxConcurrent.get(), "lock allowed concurrent execution");
    }

    @Test
    @DisplayName("stream queue publishes and consumes an immediate message")
    void streamQueueRoundTrip() throws InterruptedException {
        String topic = KEY_PREFIX + "stream:" + UUID.randomUUID();
        GXRedissonStreamMQProperties props = new GXRedissonStreamMQProperties();
        props.setConsumerGroup("audit-verify-group");
        props.setConsumerName("audit-verify-consumer");
        props.setBlockingTimeoutMillis(300L);
        props.setPendingRecoveryInterval(600_000L);
        props.setDelayScanInterval(600_000L);
        props.setConsumerThreads(1);
        props.validate();

        GXRedissonStreamMQManager manager = new GXRedissonStreamMQManager(client, props);
        CountDownLatch delivered = new CountDownLatch(1);
        try {
            manager.subscribe(topic, message -> delivered.countDown());
            manager.start();
            manager.sendImmediate(topic, "stream-payload");
            assertTrue(delivered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "stream consumer did not receive the message");
        } finally {
            manager.stop();
        }
    }

    private static MockedStatic<GXSpringContextUtils> mockClient() {
        MockedStatic<GXSpringContextUtils> context = Mockito.mockStatic(GXSpringContextUtils.class);
        context.when(() -> GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class))
                .thenReturn(client);
        context.when(() -> GXSpringContextUtils.getBean("redissonClient", RedissonClient.class))
                .thenReturn(client);
        return context;
    }

    private static String host() {
        return env("AUDIT_REDIS_HOST");
    }

    private static int port() {
        String value = env("AUDIT_REDIS_PORT");
        return isNotBlank(value) ? Integer.parseInt(value) : 6379;
    }

    private static int database() {
        String value = env("AUDIT_REDIS_DATABASE");
        return isNotBlank(value) ? Integer.parseInt(value) : 0;
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
