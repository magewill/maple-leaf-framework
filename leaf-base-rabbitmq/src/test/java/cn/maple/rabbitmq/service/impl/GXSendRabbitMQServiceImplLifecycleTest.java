package cn.maple.rabbitmq.service.impl;

import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import cn.maple.rabbitmq.support.GXAmqpMessagePublisher;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.core.DirectExchange;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class GXSendRabbitMQServiceImplLifecycleTest {
    @Mock
    private GXAmqpMessagePublisher publisher;

    @Mock
    private GXAmqpManagementClient managementClient;

    private GXSendRabbitMQServiceImpl service;

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(publisher, managementClient);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        service.shutdownAsyncExecutor();
    }

    @Test
    void shouldPropagateMdcIntoAsyncTask() {
        AtomicReference<String> observed = new AtomicReference<>();
        doAnswer(invocation -> {
            observed.set(MDC.get("X-B3-TraceId"));
            return null;
        }).when(managementClient).declareExchange(any());
        MDC.put("X-B3-TraceId", "async-trace");

        CompletableFuture<Boolean> future = service.setupMessageChannelAsync(
                "order.queue", true, false, false, null, new DirectExchange("order.exchange"), "key");

        assertThat(future.join()).isTrue();
        assertThat(observed.get()).isEqualTo("async-trace");
    }

    @Test
    void shouldKeepCallerMdcUnchangedByAsyncTask() {
        MDC.put("X-B3-TraceId", "async-trace");

        service.setupMessageChannelAsync("order.queue", true, false, false, null,
                new DirectExchange("order.exchange"), "key").join();

        assertThat(MDC.get("X-B3-TraceId")).isEqualTo("async-trace");
    }

    @Test
    void shouldAllowShutdownWithoutInitializedExecutor() {
        assertThatCode(() -> service.shutdownAsyncExecutor()).doesNotThrowAnyException();
    }

    @Test
    void shouldShutdownIdempotentlyAfterUse() {
        service.setupMessageChannelAsync("order.queue", true, false, false, null,
                new DirectExchange("order.exchange"), "key").join();

        assertThatCode(() -> {
            service.shutdownAsyncExecutor();
            service.shutdownAsyncExecutor();
        }).doesNotThrowAnyException();
    }
}
