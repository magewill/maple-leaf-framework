package cn.maple.rabbitmq.service.impl;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
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
import org.springframework.amqp.core.MessageProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class GXTracePropagationTest {
    @Mock
    private GXAmqpMessagePublisher publisher;

    @Mock
    private GXAmqpManagementClient managementClient;

    private GXSendRabbitMQServiceImpl service;

    private final List<Message> captured = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(publisher, managementClient);
        doAnswer(invocation -> {
            captured.add(invocation.getArgument(1));
            return CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED);
        }).when(publisher).send(anyString(), any(Message.class), anyLong());
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        service.shutdownAsyncExecutor();
    }

    @Test
    void shouldReuseTraceIdFromContext() {
        GXTraceIdContextUtils.putTraceId("trace-from-context");

        service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());

        assertThat((Object) captured.getFirst().getMessageProperties().getHeader("X-Trace-Id"))
                .isEqualTo("trace-from-context");
    }

    @Test
    void shouldGenerateTraceIdOnlyWhenContextMissing() {
        service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());
        String first = traceIdOf(captured.getFirst());

        GXTraceIdContextUtils.putTraceId("fixed-trace");
        service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());
        String second = traceIdOf(captured.get(1));

        assertThat(first).isNotBlank();
        assertThat(second).isEqualTo("fixed-trace");
    }

    @Test
    void shouldKeepContextUnchangedAfterSend() {
        GXTraceIdContextUtils.putTraceId("stable-trace");

        service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());

        assertThat(GXTraceIdContextUtils.getNullableTraceId()).isEqualTo("stable-trace");
    }

    @Test
    void shouldUseDistinctTraceIdsPerThread() throws Exception {
        int threadCount = 8;
        ExecutorService workers = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        try {
            for (int index = 0; index < threadCount; index++) {
                int threadIndex = index;
                workers.submit(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        GXTraceIdContextUtils.putTraceId("thread-trace-" + threadIndex);
                        service.sendNormalMessage(GXRabbitMQMessageReqDto.builder()
                                .exchange("test.exchange")
                                .routingKey("test.key")
                                .data(Dict.create().set("thread", threadIndex))
                                .messageProperties(new MessageProperties())
                                .build());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        MDC.clear();
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            workers.shutdownNow();
        }

        List<String> traceIds = captured.stream().map(GXTracePropagationTest::traceIdOf).toList();
        assertThat(traceIds).hasSize(threadCount).doesNotHaveDuplicates();
    }

    private static String traceIdOf(Message message) {
        return (String) message.getMessageProperties().getHeader("X-Trace-Id");
    }
}
