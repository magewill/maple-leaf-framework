package cn.maple.rabbitmq.integration;

import cn.hutool.core.lang.Dict;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.service.impl.GXSendRabbitMQServiceImpl;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 性能采样：只打印可复算口径，不设绝对吞吐门槛。
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "RABBITMQ_HOST", matches = ".+")
class GXRabbitMQThroughputSamplingIT {
    private static final int SEQUENTIAL_SAMPLES = 500;

    private static final int CONCURRENT_THREADS = 8;

    private static final int MESSAGES_PER_THREAD = 50;

    private static CachingConnectionFactory connectionFactory;

    private static GXSendRabbitMQServiceImpl service;

    private static RabbitAdmin rabbitAdmin;

    @BeforeAll
    static void setUp() {
        String host = System.getenv("RABBITMQ_HOST");
        int port = Integer.parseInt(envOrDefault("RABBITMQ_PORT", "5672"));
        connectionFactory = new CachingConnectionFactory(host, port);
        connectionFactory.setUsername(envOrDefault("RABBITMQ_USERNAME", "guest"));
        connectionFactory.setPassword(envOrDefault("RABBITMQ_PASSWORD", "guest"));
        connectionFactory.setVirtualHost(envOrDefault("RABBITMQ_VHOST", "/"));
        rabbitAdmin = new RabbitAdmin(connectionFactory);
        service = GXTestRabbitFixtures.newService(rabbitTemplate(), rabbitAdmin);
    }

    @AfterAll
    static void tearDown() {
        if (service != null) {
            service.shutdownAsyncExecutor();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldSampleSequentialSendLatency() {
        String queueName = "maple.it.perf." + UUID.randomUUID();
        try {
            service.createQueue(queueName, false, false, true, null);
            long[] samples = new long[SEQUENTIAL_SAMPLES];
            for (int index = 0; index < SEQUENTIAL_SAMPLES; index++) {
                long start = System.nanoTime();
                sendOnce(queueName, index);
                samples[index] = System.nanoTime() - start;
            }
            report("sequential", samples, System.nanoTime());
        } finally {
            service.deleteQueue(queueName);
        }
    }

    @Test
    void shouldSampleConcurrentSendLatency() throws Exception {
        String queueName = "maple.it.perf.concurrent." + UUID.randomUUID();
        ExecutorService workers = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        try {
            service.createQueue(queueName, false, false, true, null);
            long start = System.nanoTime();
            List<Future<?>> futures = new ArrayList<>();
            for (int threadIndex = 0; threadIndex < CONCURRENT_THREADS; threadIndex++) {
                int index = threadIndex;
                futures.add(workers.submit(() -> {
                    for (int sequence = 0; sequence < MESSAGES_PER_THREAD; sequence++) {
                        sendOnce(queueName, index * MESSAGES_PER_THREAD + sequence);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
            long elapsedNanos = System.nanoTime() - start;
            double throughput = (CONCURRENT_THREADS * MESSAGES_PER_THREAD) / (elapsedNanos / 1_000_000_000.0d);
            System.out.printf("rabbitmq perftest concurrent: threads=%d, total=%d, elapsedMs=%d, throughput=%.2f msg/s%n",
                    CONCURRENT_THREADS, CONCURRENT_THREADS * MESSAGES_PER_THREAD,
                    TimeUnit.NANOSECONDS.toMillis(elapsedNanos), throughput);
            assertThat(throughput).isGreaterThan(0.0d);
        } finally {
            workers.shutdownNow();
            service.deleteQueue(queueName);
        }
    }

    private static void report(String label, long[] samples, long ignored) {
        long[] sorted = Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted);
        long total = 0L;
        for (long sample : sorted) {
            total += sample;
        }
        System.out.printf("rabbitmq perftest %s: samples=%d, avgUs=%d, p50Us=%d, p95Us=%d, maxUs=%d%n",
                label, sorted.length,
                TimeUnit.NANOSECONDS.toMicros(total / sorted.length),
                TimeUnit.NANOSECONDS.toMicros(sorted[sorted.length / 2]),
                TimeUnit.NANOSECONDS.toMicros(sorted[(int) (sorted.length * 0.95d)]),
                TimeUnit.NANOSECONDS.toMicros(sorted[sorted.length - 1]));
    }

    private static void sendOnce(String queueName, int sequence) {
        service.sendNormalMessage(GXRabbitMQMessageReqDto.builder()
                .exchange("")
                .routingKey(queueName)
                .data(Dict.create().set("sequence", sequence))
                .messageProperties(new MessageProperties())
                .build());
    }

    private static RabbitTemplate rabbitTemplate() {
        return new RabbitTemplate(connectionFactory);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
