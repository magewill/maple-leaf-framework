package cn.maple.rabbitmq.service.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.exception.GXRabbitMQOperationException;
import cn.maple.rabbitmq.exception.GXRabbitMQOperationException.Operation;
import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import cn.maple.rabbitmq.support.GXAmqpMessagePublisher;
import cn.maple.rabbitmq.support.GXSendOutcome;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GXSendRabbitMQServiceImplTest {
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
        service.shutdownAsyncExecutor();
    }

    @Test
    void shouldDispatchMessageWithTracingHeaders() {
        GXTraceIdContextUtils.putTraceId("trace-abc");
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED));

        service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());

        Message sent = captureSentMessage();
        MessageProperties properties = sent.getMessageProperties();
        assertThat((Object) properties.getHeader("X-Trace-Id")).isEqualTo("trace-abc");
        assertThat((Object) properties.getHeader("X-Timestamp")).isInstanceOf(Long.class);
        assertThat((Object) properties.getHeader("X-Source-Service")).isEqualTo("test-app");
        assertThat((Object) properties.getHeader("__TypeId__")).isEqualTo("cn.hutool.core.lang.Dict");
        assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(properties.getContentEncoding()).isEqualTo("UTF-8");
        assertThat(properties.getMessageId()).isEqualTo("trace-abc");
        assertThat(new String(sent.getBody())).contains("\"id\":1");
    }

    @Test
    void shouldGenerateTraceIdWhenContextMissing() {
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED));

        service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());

        String traceId = (String) captureSentMessage().getMessageProperties().getHeader("X-Trace-Id");
        assertThat(traceId).isNotBlank();
    }

    @Test
    void shouldKeepCallerProvidedMessageId() {
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED));
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.getMessageProperties().setMessageId("caller-id");

        service.sendNormalMessage(request);

        assertThat(captureSentMessage().getMessageProperties().getMessageId()).isEqualTo("caller-id");
    }

    @Test
    void shouldRejectNullRequest() {
        assertThatThrownBy(() -> service.sendNormalMessage(null))
                .isInstanceOf(GXBusinessException.class);
    }

    @Test
    void shouldRejectNullExchange() {
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setExchange(null);

        assertThatThrownBy(() -> service.sendNormalMessage(request))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("交换机名称不能为null");
    }

    @Test
    void shouldMapDefaultExchangeToQueueAddress() {
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED));
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setExchange("");
        request.setRoutingKey("order.queue");

        service.sendNormalMessage(request);

        verify(publisher).send(eq("/queues/order.queue"), any(Message.class), anyLong());
    }

    @Test
    void shouldMapNamedExchangeToExchangeAddress() {
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED));

        service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());

        verify(publisher).send(eq("/exchanges/test.exchange/test.key"), any(Message.class), anyLong());
    }

    @Test
    void shouldRejectBlankRoutingKey() {
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setRoutingKey("");

        assertThatThrownBy(() -> service.sendNormalMessage(request))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("路由键不能为空");
    }

    @Test
    void shouldRejectNullPayload() {
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setData(null);

        assertThatThrownBy(() -> service.sendNormalMessage(request))
                .isInstanceOf(GXBusinessException.class);
    }

    @Test
    void shouldRejectNullMessageProperties() {
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setMessageProperties(null);

        assertThatThrownBy(() -> service.sendNormalMessage(request))
                .isInstanceOf(GXBusinessException.class);
    }

    @Test
    void shouldPropagateAmqpExceptionFromPublisher() {
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenThrow(new AmqpException("broker down"));

        assertThatThrownBy(() -> service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest()))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("broker down");
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldNotWriteConsumerTagFromLegacyTagField() {
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED));
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setTag("legacy-tag");

        service.sendNormalMessage(request);

        assertThat(captureSentMessage().getMessageProperties().getConsumerTag()).isNull();
    }

    @Test
    void shouldDeclareQueueOnEveryCall() {
        service.createQueue("order.queue", true, false, false, null);
        service.createQueue("order.queue", true, false, false, null);

        verify(managementClient, times(2)).declareQueue(eq("order.queue"), eq(true), eq(false), anyMap());
    }

    @Test
    void shouldDeclareAgainWhenAttributesDiffer() {
        service.createQueue("order.queue", true, false, false, null);
        service.createQueue("order.queue", false, false, true, null);

        verify(managementClient).declareQueue("order.queue", true, false, Map.of());
        verify(managementClient).declareQueue("order.queue", false, true, Map.of());
    }

    @Test
    void shouldRejectExclusiveQueue() {
        assertThatThrownBy(() -> service.createQueue("order.queue", true, true, false, null))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("不支持排他队列");
        verifyNoInteractions(managementClient);
    }

    @Test
    void shouldDeclareDurableQueueWithExpectedFlags() {
        Queue queue = service.createDurableQueue("order.queue");

        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.isAutoDelete()).isFalse();
        verify(managementClient).declareQueue("order.queue", true, false, Map.of());
    }

    @Test
    void shouldDeclareTemporaryQueueAsDurableNonExclusiveAutoDelete() {
        Queue queue = service.createTemporaryQueue("temp.queue");

        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.isAutoDelete()).isTrue();
        verify(managementClient).declareQueue("temp.queue", true, true, Map.of());
    }

    @Test
    void shouldRejectBlankQueueName() {
        assertThatThrownBy(() -> service.createQueue(" ", true, false, false, null))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("队列名称不能为空");
    }

    @Test
    void shouldRejectTooLongQueueName() {
        String name = "q".repeat(256);

        assertThatThrownBy(() -> service.createQueue(name, true, false, false, null))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("长度不能超过");
    }

    @Test
    void shouldRejectControlCharacterInQueueName() {
        assertThatThrownBy(() -> service.createQueue("queue\u0001name", true, false, false, null))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("控制字符");
    }

    @Test
    void shouldReturnTrueWhenQueueDeleted() {
        when(managementClient.deleteQueue("order.queue")).thenReturn(true);

        assertThat(service.deleteQueue("order.queue")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenQueueAbsentOnDelete() {
        when(managementClient.deleteQueue("order.queue")).thenReturn(false);

        assertThat(service.deleteQueue("order.queue")).isFalse();
    }

    @Test
    void shouldThrowWhenDeleteFails() {
        when(managementClient.deleteQueue("order.queue"))
                .thenThrow(new cn.maple.rabbitmq.support.GXAmqpManagementException("boom", 500));

        assertThatThrownBy(() -> service.deleteQueue("order.queue"))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> assertThat(((GXRabbitMQOperationException) e).getOperation())
                        .isEqualTo(Operation.DELETE_QUEUE));
    }

    @Test
    void shouldReportQueueExistsFromBroker() {
        when(managementClient.queueExists("order.queue")).thenReturn(true);

        assertThat(service.queueExists("order.queue")).isTrue();
    }

    @Test
    void shouldReportQueueMissingWhenBrokerReturnsNotFound() {
        when(managementClient.queueExists("order.queue")).thenReturn(false);

        assertThat(service.queueExists("order.queue")).isFalse();
    }

    @Test
    void shouldThrowWhenQueuePropertyQueryFails() {
        when(managementClient.queueExists("order.queue"))
                .thenThrow(new cn.maple.rabbitmq.support.GXAmqpManagementException("connect refused", 0));

        assertThatThrownBy(() -> service.queueExists("order.queue"))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> assertThat(((GXRabbitMQOperationException) e).getOperation())
                        .isEqualTo(Operation.QUERY_QUEUE));
    }

    @Test
    void shouldRejectNullQueueArgumentValue() {
        java.util.Map<String, Object> arguments = new java.util.HashMap<>();
        arguments.put("x-message-ttl", null);

        assertThatThrownBy(() -> service.createQueue("order.queue", true, false, false, arguments))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("队列参数值不能为null");
        verifyNoInteractions(managementClient);
    }

    @Test
    void shouldRejectBlankQueueArgumentName() {
        java.util.Map<String, Object> arguments = new java.util.HashMap<>();
        arguments.put(" ", 1000);

        assertThatThrownBy(() -> service.createQueue("order.queue", true, false, false, arguments))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("队列参数名不能为空");
        verifyNoInteractions(managementClient);
    }

    @Test
    void shouldPassQueueArgumentsToBroker() {
        service.createQueue("order.queue", true, false, false, Map.of("x-message-ttl", 1000));

        verify(managementClient).declareQueue("order.queue", true, false, Map.of("x-message-ttl", 1000));
    }

    @Test
    void shouldPurgeQueue() {
        assertThat(service.purgeQueue("order.queue")).isTrue();

        verify(managementClient).purgeQueue("order.queue");
    }

    @Test
    void shouldThrowWhenPurgeFails() {
        doThrow(new cn.maple.rabbitmq.support.GXAmqpManagementException("not found", 404))
                .when(managementClient).purgeQueue("order.queue");

        assertThatThrownBy(() -> service.purgeQueue("order.queue"))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> assertThat(((GXRabbitMQOperationException) e).getOperation())
                        .isEqualTo(Operation.PURGE_QUEUE));
    }

    @Test
    void shouldDeclareExchange() {
        assertThat(service.createExchange(new DirectExchange("order.exchange"))).isTrue();

        verify(managementClient).declareExchange(any());
    }

    @Test
    void shouldThrowWhenExchangeDeclarationFails() {
        doThrow(new cn.maple.rabbitmq.support.GXAmqpManagementException("PRECONDITION_FAILED", 400))
                .when(managementClient).declareExchange(any());

        assertThatThrownBy(() -> service.createExchange(new DirectExchange("order.exchange")))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> assertThat(((GXRabbitMQOperationException) e).getOperation())
                        .isEqualTo(Operation.DECLARE_EXCHANGE));
    }

    @Test
    void shouldBindQueue() {
        assertThat(service.bindQueueToExchange("order.queue", "order.exchange", "order.created")).isTrue();

        verify(managementClient).bindQueue("order.queue", "order.exchange", "order.created");
    }

    @Test
    void shouldThrowWhenBindingFails() {
        doThrow(new cn.maple.rabbitmq.support.GXAmqpManagementException("no exchange", 404))
                .when(managementClient).bindQueue(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.bindQueueToExchange("order.queue", "order.exchange", "order.created"))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> assertThat(((GXRabbitMQOperationException) e).getOperation())
                        .isEqualTo(Operation.BIND));
    }

    @Test
    void shouldUnbindQueue() {
        when(managementClient.unbindQueue("order.queue", "order.exchange", "order.created")).thenReturn(true);

        assertThat(service.unbindQueueFromExchange("order.queue", "order.exchange", "order.created")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUnbindTargetAbsent() {
        when(managementClient.unbindQueue("order.queue", "order.exchange", "order.created")).thenReturn(false);

        assertThat(service.unbindQueueFromExchange("order.queue", "order.exchange", "order.created")).isFalse();
    }

    @Test
    void shouldSetUpMessageChannel() {
        boolean created = service.setupMessageChannel("order.queue", true, false, false, null,
                new DirectExchange("order.exchange"), "order.created");

        assertThat(created).isTrue();
        verify(managementClient).declareExchange(any());
        verify(managementClient).declareQueue(eq("order.queue"), eq(true), eq(false), anyMap());
        verify(managementClient).bindQueue("order.queue", "order.exchange", "order.created");
    }

    @Test
    void shouldReportCompletedStepsWhenBindingFails() {
        doThrow(new cn.maple.rabbitmq.support.GXAmqpManagementException("boom", 500))
                .when(managementClient).bindQueue(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.setupDurableMessageChannel("order.queue",
                new DirectExchange("order.exchange"), "order.created"))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> {
                    GXRabbitMQOperationException ex = (GXRabbitMQOperationException) e;
                    assertThat(ex.getDetail().get("completedSteps").toString())
                            .contains("DECLARE_EXCHANGE")
                            .contains("DECLARE_QUEUE");
                    assertThat(ex.getDetail().get("failedStep")).isEqualTo("BIND");
                });
    }

    @Test
    void shouldRejectSetupWithoutExchange() {
        assertThatThrownBy(() -> service.setupMessageChannel("order.queue", true, false, false, null, null, "key"))
                .isInstanceOf(GXBusinessException.class);
    }

    @Test
    void shouldFailTopologyWhenManagementClientMissing() {
        GXSendRabbitMQServiceImpl noManagement = GXTestRabbitFixtures.newService(publisher, null);
        try {
            assertThatThrownBy(() -> noManagement.createQueue("order.queue", true, false, false, null))
                    .isInstanceOf(GXRabbitMQOperationException.class)
                    .hasMessageContaining("Management API");
        } finally {
            noManagement.shutdownAsyncExecutor();
        }
    }

    @Test
    void shouldReturnSettlementFromAwaitPath() {
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED));

        assertThat(service.sendNormalMessageAwait(GXTestRabbitFixtures.newMessageRequest(),
                java.time.Duration.ofSeconds(1))).isTrue();
    }

    @Test
    void shouldReturnFalseWhenAwaitPathIsReleased() {
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.RELEASED));

        assertThat(service.sendNormalMessageAwait(GXTestRabbitFixtures.newMessageRequest(),
                java.time.Duration.ofSeconds(1))).isFalse();
    }

    @Test
    void shouldCompleteSettlementWithTrueOnAccepted() {
        cn.maple.rabbitmq.dto.inner.GXCorrelationData correlationData =
                new cn.maple.rabbitmq.dto.inner.GXCorrelationData("id-accepted");
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setCorrelationData(correlationData);
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED));

        service.sendNormalMessage(request);

        assertThat(correlationData.settlement()).isCompletedWithValue(true);
    }

    @Test
    void shouldCompleteSettlementWithFalseOnReleased() {
        cn.maple.rabbitmq.dto.inner.GXCorrelationData correlationData =
                new cn.maple.rabbitmq.dto.inner.GXCorrelationData("id-released");
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setCorrelationData(correlationData);
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.RELEASED));

        service.sendNormalMessage(request);

        assertThat(correlationData.settlement()).isCompletedWithValue(false);
    }

    @Test
    void shouldCompleteSettlementWithFalseWhenSettlementFails() {
        cn.maple.rabbitmq.dto.inner.GXCorrelationData correlationData =
                new cn.maple.rabbitmq.dto.inner.GXCorrelationData("id-failed");
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setCorrelationData(correlationData);
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.failedFuture(new org.springframework.amqp.AmqpException("closed")));

        service.sendNormalMessage(request);

        assertThat(correlationData.settlement()).isCompletedWithValue(false);
    }

    @Test
    void shouldSendRawPayloadWithoutReserialization() {
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED));
        MessageProperties properties = new MessageProperties();
        String initialContentType = properties.getContentType();

        service.sendRawMessage("test.exchange", "test.key", "{\"raw\":true}", properties, null);

        Message sent = captureSentMessage();
        assertThat(new String(sent.getBody())).isEqualTo("{\"raw\":true}");
        // 原始载荷可能是数组或非 Dict 结构，不能写死 __TypeId__，否则消费端转换必然失败。
        assertThat((Object) sent.getMessageProperties().getHeader("__TypeId__")).isNull();
        assertThat((Object) sent.getMessageProperties().getHeader("X-Trace-Id")).isNotNull();
        assertThat(properties.getContentType()).isEqualTo(initialContentType);
    }

    @Test
    void shouldCompleteSettlementWhenPublisherThrowsSynchronously() {
        cn.maple.rabbitmq.dto.inner.GXCorrelationData correlationData =
                new cn.maple.rabbitmq.dto.inner.GXCorrelationData("id-sync-failure");
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setCorrelationData(correlationData);
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenThrow(new AmqpException("connection refused"));

        assertThatThrownBy(() -> service.sendNormalMessage(request))
                .isInstanceOf(AmqpException.class);

        assertThat(correlationData.settlement()).isCompletedWithValue(false);
    }

    @Test
    void shouldCompleteSettlementWithFalseOnUnknownOutcome() {
        cn.maple.rabbitmq.dto.inner.GXCorrelationData correlationData =
                new cn.maple.rabbitmq.dto.inner.GXCorrelationData("id-unknown");
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setCorrelationData(correlationData);
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.UNKNOWN));

        service.sendNormalMessage(request);

        assertThat(correlationData.settlement()).isCompletedWithValue(false);
    }

    @Test
    void shouldWrapSynchronousSendFailureOnAwaitPath() {
        when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenThrow(new AmqpException("connection refused"));

        assertThatThrownBy(() -> service.sendNormalMessageAwait(GXTestRabbitFixtures.newMessageRequest(),
                java.time.Duration.ofSeconds(1)))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .hasCauseInstanceOf(AmqpException.class);
    }

    @Test
    void shouldRejectNonPositiveAwaitTimeout() {
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();

        assertThatThrownBy(() -> service.sendNormalMessageAwait(request, null))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("超时时间必须为正数");
        assertThatThrownBy(() -> service.sendNormalMessageAwait(request, java.time.Duration.ZERO))
                .isInstanceOf(GXBusinessException.class);
        assertThatThrownBy(() -> service.sendNormalMessageAwait(request, java.time.Duration.ofSeconds(-1)))
                .isInstanceOf(GXBusinessException.class);
        verifyNoInteractions(publisher);
    }

    private Message captureSentMessage() {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(publisher).send(anyString(), captor.capture(), anyLong());
        return captor.getValue();
    }
}
