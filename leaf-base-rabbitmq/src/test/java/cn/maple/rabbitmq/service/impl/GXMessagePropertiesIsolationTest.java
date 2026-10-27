package cn.maple.rabbitmq.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class GXMessagePropertiesIsolationTest {
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RabbitAdmin rabbitAdmin;

    private GXSendRabbitMQServiceImpl service;

    private final List<Message> captured = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(rabbitTemplate, rabbitAdmin);
        doAnswer(invocation -> {
            captured.add(invocation.getArgument(2));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), nullable(CorrelationData.class));
    }

    @AfterEach
    void tearDown() {
        org.slf4j.MDC.clear();
        service.shutdownAsyncExecutor();
    }

    @Test
    void shouldNotMutateCallerMessageProperties() {
        MessageProperties callerProperties = new MessageProperties();
        String initialContentType = callerProperties.getContentType();
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setMessageProperties(callerProperties);

        service.sendNormalMessage(request);

        assertThat(callerProperties.getContentType()).isEqualTo(initialContentType);
        assertThat(callerProperties.getContentEncoding()).isNull();
        assertThat((Object) callerProperties.getHeader("X-Trace-Id")).isNull();
        assertThat(callerProperties.getMessageId()).isNull();
        assertThat(callerProperties.getTimestamp()).isNull();
        assertThat(captured).hasSize(1);
    }

    @Test
    void shouldKeepSnapshotsIndependentWhenRequestReused() {
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();

        service.sendNormalMessage(request);
        request.getMessageProperties().setMessageId("second-call");
        service.sendNormalMessage(request);

        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getMessageProperties().getMessageId())
                .isNotEqualTo("second-call");
        assertThat(captured.get(1).getMessageProperties().getMessageId()).isEqualTo("second-call");
    }

    @Test
    void shouldNotReflectPayloadMutationAfterSend() {
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();

        service.sendNormalMessage(request);
        request.getData().set("id", 999);

        assertThat(new String(captured.getFirst().getBody())).contains("\"id\":1").doesNotContain("999");
    }

    @Test
    void shouldNotMixHeadersAcrossThreads() throws Exception {
        int threadCount = 16;
        int messagesPerThread = 100;
        ExecutorService workers = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount * messagesPerThread);

        try {
            for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
                int index = threadIndex;
                workers.submit(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        cn.maple.core.framework.util.GXTraceIdContextUtils.putTraceId("trace-" + index);
                        for (int sequence = 0; sequence < messagesPerThread; sequence++) {
                            GXRabbitMQMessageReqDto request = GXRabbitMQMessageReqDto.builder()
                                    .exchange("test.exchange")
                                    .routingKey("test.key")
                                    .data(Dict.create().set("thread", index).set("sequence", sequence))
                                    .messageProperties(new MessageProperties())
                                    .build();
                            service.sendNormalMessage(request);
                            done.countDown();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        org.slf4j.MDC.clear();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            workers.shutdownNow();
        }

        assertThat(captured).hasSize(threadCount * messagesPerThread);
        for (Message message : captured) {
            JSONObject payload = JSONUtil.parseObj(new String(message.getBody()));
            String expectedTraceId = "trace-" + payload.getInt("thread");
            assertThat((Object) message.getMessageProperties().getHeader("X-Trace-Id")).isEqualTo(expectedTraceId);
        }
    }
}
