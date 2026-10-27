package cn.maple.rabbitmq.service.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.rabbitmq.exception.GXRabbitMQOperationException;
import cn.maple.rabbitmq.exception.GXRabbitMQOperationException.Operation;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GXMessageChannelFailureTest {
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RabbitAdmin rabbitAdmin;

    private GXSendRabbitMQServiceImpl service;

    private final DirectExchange exchange = new DirectExchange("order.exchange");

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(rabbitTemplate, rabbitAdmin);
    }

    @AfterEach
    void tearDown() {
        service.shutdownAsyncExecutor();
    }

    @Test
    void shouldReportCompletedExchangeWhenQueueFails() {
        doThrow(new AmqpException("PRECONDITION_FAILED")).when(rabbitAdmin).declareQueue(any());

        assertThatThrownBy(() -> service.setupMessageChannel("order.queue", true, false, false, null, exchange, "key"))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> {
                    GXRabbitMQOperationException operationException = (GXRabbitMQOperationException) e;
                    assertThat(operationException.getOperation()).isEqualTo(Operation.DECLARE_QUEUE);
                    assertThat(operationException.getDetail().get("completedSteps")).isEqualTo(List.of("DECLARE_EXCHANGE"));
                    assertThat(operationException.getDetail().getStr("failedStep")).isEqualTo("DECLARE_QUEUE");
                });
    }

    @Test
    void shouldReportNoCompletedStepWhenExchangeFails() {
        doThrow(new AmqpException("channel closed")).when(rabbitAdmin).declareExchange(any());

        assertThatThrownBy(() -> service.setupMessageChannel("order.queue", true, false, false, null, exchange, "key"))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> {
                    GXRabbitMQOperationException operationException = (GXRabbitMQOperationException) e;
                    assertThat(operationException.getOperation()).isEqualTo(Operation.DECLARE_EXCHANGE);
                    assertThat(operationException.getDetail().get("completedSteps")).isEqualTo(List.of());
                });
    }

    @Test
    void shouldReportTwoCompletedStepsWhenBindingFails() {
        when(rabbitAdmin.declareQueue(any())).thenReturn("order.queue");
        doThrow(new AmqpException("no route")).when(rabbitAdmin).declareBinding(any());

        assertThatThrownBy(() -> service.setupMessageChannel("order.queue", true, false, false, null, exchange, "key"))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> {
                    GXRabbitMQOperationException operationException = (GXRabbitMQOperationException) e;
                    assertThat(operationException.getOperation()).isEqualTo(Operation.BIND);
                    assertThat(operationException.getDetail().get("completedSteps"))
                            .isEqualTo(List.of("DECLARE_EXCHANGE", "DECLARE_QUEUE"));
                });
    }

    @Test
    void shouldReturnFailedFutureWhenAsyncSetupFails() {
        doThrow(new AmqpException("boom")).when(rabbitAdmin).declareQueue(any());

        CompletableFuture<Boolean> future = service.setupMessageChannelAsync(
                "order.queue", true, false, false, null, exchange, "key");

        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(GXRabbitMQOperationException.class);
        assertThat(future).isCompletedExceptionally();
    }

    @Test
    void shouldRejectSynchronouslyWhenAsyncArgumentsInvalid() {
        assertThatThrownBy(() -> service.setupMessageChannelAsync(
                " ", true, false, false, null, exchange, "key"))
                .isInstanceOf(GXBusinessException.class);
    }

    @Test
    void shouldReturnFailedFutureWhenExecutorRejects() throws Exception {
        GXSendRabbitMQServiceImpl saturated = GXTestRabbitFixtures.newService(rabbitTemplate, rabbitAdmin, 1, 1, 0);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(rabbitAdmin).declareExchange(any());

        CompletableFuture<Boolean> first = saturated.setupMessageChannelAsync(
                "order.queue", true, false, false, null, exchange, "key");
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Boolean> second = saturated.setupMessageChannelAsync(
                "order.queue.two", true, false, false, null, new DirectExchange("order.exchange.two"), "key");

        assertThatThrownBy(second::join).hasCauseInstanceOf(GXRabbitMQOperationException.class);
        assertThat(second).isCompletedExceptionally();

        release.countDown();
        first.join();
        saturated.shutdownAsyncExecutor();
    }

    @Test
    void shouldDeclareQueueEvenWhenExchangeAlreadyDeclared() {
        when(rabbitAdmin.declareQueue(any())).thenReturn("order.queue");

        service.setupMessageChannel("order.queue", true, false, false, null, exchange, "key");
        service.setupMessageChannel("order.queue", true, false, false, null, exchange, "key");

        org.mockito.Mockito.verify(rabbitAdmin, org.mockito.Mockito.times(2)).declareQueue(any());
    }
}
