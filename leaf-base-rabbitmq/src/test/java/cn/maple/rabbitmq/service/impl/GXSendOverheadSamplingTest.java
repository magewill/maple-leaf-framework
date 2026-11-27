package cn.maple.rabbitmq.service.impl;

import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import cn.maple.rabbitmq.support.GXAmqpMessagePublisher;
import cn.maple.rabbitmq.support.GXSendOutcome;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 无 Broker 的发送耗时采样（SC-006）：500 条样本，输出 avg/p50/p95/max。
 *
 * <p>用途是给出可复算口径与回归基线，不作为绝对吞吐门槛。</p>
 */
@ExtendWith(MockitoExtension.class)
class GXSendOverheadSamplingTest {
    private static final int SAMPLE_COUNT = 500;

    @Mock
    private GXAmqpMessagePublisher publisher;

    @Mock
    private GXAmqpManagementClient managementClient;

    private GXSendRabbitMQServiceImpl service;

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(publisher, managementClient);
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED));
        MDC.put("X-B3-TraceId", "sampling-trace");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        service.shutdownAsyncExecutor();
    }

    @Test
    void shouldSampleSendLatencyWithoutBroker() {
        long[] samples = new long[SAMPLE_COUNT];
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            long start = System.nanoTime();
            service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());
            samples[index] = System.nanoTime() - start;
        }

        long[] sorted = Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted);
        long total = 0L;
        for (long sample : sorted) {
            total += sample;
        }
        long avgMicros = TimeUnit.NANOSECONDS.toMicros(total / sorted.length);
        long p50Micros = TimeUnit.NANOSECONDS.toMicros(sorted[sorted.length / 2]);
        long p95Micros = TimeUnit.NANOSECONDS.toMicros(sorted[(int) (sorted.length * 0.95d)]);
        long maxMicros = TimeUnit.NANOSECONDS.toMicros(sorted[sorted.length - 1]);

        System.out.printf("rabbitmq overhead sampling: samples=%d, avgUs=%d, p50Us=%d, p95Us=%d, maxUs=%d%n",
                sorted.length, avgMicros, p50Micros, p95Micros, maxMicros);

        assertThat(p95Micros).isLessThan(TimeUnit.MILLISECONDS.toMicros(50L));
    }
}
