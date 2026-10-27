package cn.maple.rabbitmq.service.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.exception.GXRabbitMQOperationException;
import cn.maple.rabbitmq.exception.GXRabbitMQOperationException.Operation;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GXSendRabbitMQServiceImplTest {
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RabbitAdmin rabbitAdmin;

    private GXSendRabbitMQServiceImpl service;

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(rabbitTemplate, rabbitAdmin);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        service.shutdownAsyncExecutor();
    }

    @Test
    void shouldDispatchMessageWithTracingHeaders() {
        GXTraceIdContextUtils.putTraceId("trace-abc");

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
        service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());

        String traceId = (String) captureSentMessage().getMessageProperties().getHeader("X-Trace-Id");
        assertThat(traceId).isNotBlank();
    }

    @Test
    void shouldKeepCallerProvidedMessageId() {
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
    void shouldAllowDefaultExchange() {
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setExchange("");

        service.sendNormalMessage(request);

        verify(rabbitTemplate).convertAndSend(eq(""), eq("test.key"), any(Message.class), nullable(CorrelationData.class));
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
    void shouldPropagateAmqpExceptionFromTemplate() {
        doThrow(new AmqpException("broker down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class), nullable(CorrelationData.class));

        assertThatThrownBy(() -> service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest()))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("broker down");
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldNotWriteConsumerTagFromLegacyTagField() {
        GXRabbitMQMessageReqDto request = GXTestRabbitFixtures.newMessageRequest();
        request.setTag("legacy-tag");

        service.sendNormalMessage(request);

        assertThat(captureSentMessage().getMessageProperties().getConsumerTag()).isNull();
    }

    @Test
    void shouldDeclareQueueOnEveryCall() {
        when(rabbitAdmin.declareQueue(any())).thenReturn("order.queue");

        service.createQueue("order.queue", true, false, false, null);
        service.createQueue("order.queue", true, false, false, null);

        verify(rabbitAdmin, times(2)).declareQueue(any());
    }

    @Test
    void shouldDeclareAgainWhenAttributesDiffer() {
        when(rabbitAdmin.declareQueue(any())).thenReturn("order.queue");

        service.createQueue("order.queue", true, false, false, null);
        service.createQueue("order.queue", false, false, true, null);

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin, times(2)).declareQueue(captor.capture());
        assertThat(captor.getAllValues().get(0).isDurable()).isTrue();
        assertThat(captor.getAllValues().get(1).isDurable()).isFalse();
        assertThat(captor.getAllValues().get(1).isAutoDelete()).isTrue();
    }

    @Test
    void shouldWrapQueueDeclarationFailure() {
        doThrow(new AmqpException("PRECONDITION_FAILED")).when(rabbitAdmin).declareQueue(any());

        assertThatThrownBy(() -> service.createQueue("order.queue", true, false, false, null))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> assertThat(((GXRabbitMQOperationException) e).getOperation())
                        .isEqualTo(Operation.DECLARE_QUEUE));
    }

    @Test
    void shouldDeclareDurableQueueWithExpectedFlags() {
        when(rabbitAdmin.declareQueue(any())).thenReturn("order.queue");

        Queue queue = service.createDurableQueue("order.queue");

        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.isAutoDelete()).isFalse();
    }

    @Test
    void shouldDeclareTemporaryQueueAsNonExclusiveAndAutoDelete() {
        when(rabbitAdmin.declareQueue(any())).thenReturn("temp.queue");

        Queue queue = service.createTemporaryQueue("temp.queue");

        assertThat(queue.isDurable()).isFalse();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.isAutoDelete()).isTrue();
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
        when(rabbitAdmin.deleteQueue("order.queue")).thenReturn(true);

        assertThat(service.deleteQueue("order.queue")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenQueueAbsentOnDelete() {
        when(rabbitAdmin.deleteQueue("order.queue")).thenReturn(false);

        assertThat(service.deleteQueue("order.queue")).isFalse();
    }

    @Test
    void shouldThrowWhenDeleteFails() {
        doThrow(new AmqpException("channel closed")).when(rabbitAdmin).deleteQueue("order.queue");

        assertThatThrownBy(() -> service.deleteQueue("order.queue"))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> assertThat(((GXRabbitMQOperationException) e).getOperation())
                        .isEqualTo(Operation.DELETE_QUEUE));
    }

    @Test
    void shouldReportQueueExistsFromBroker() {
        Properties properties = new Properties();
        properties.setProperty("QUEUE_NAME", "order.queue");
        when(rabbitAdmin.getQueueProperties("order.queue")).thenReturn(properties);

        assertThat(service.queueExists("order.queue")).isTrue();
    }

    @Test
    void shouldReportQueueMissingWhenBrokerReturnsNull() {
        when(rabbitAdmin.getQueueProperties("order.queue")).thenReturn(null);

        assertThat(service.queueExists("order.queue")).isFalse();
    }

    @Test
    void shouldThrowWhenQueuePropertyQueryFails() {
        doThrow(new AmqpException("connect refused")).when(rabbitAdmin).getQueueProperties("order.queue");

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
        verifyNoInteractions(rabbitAdmin);
    }

    @Test
    void shouldRejectBlankQueueArgumentName() {
        java.util.Map<String, Object> arguments = new java.util.HashMap<>();
        arguments.put(" ", 1000);

        assertThatThrownBy(() -> service.createQueue("order.queue", true, false, false, arguments))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("队列参数名不能为空");
        verifyNoInteractions(rabbitAdmin);
    }

    @Test
    void shouldPurgeQueue() {
        when(rabbitAdmin.purgeQueue("order.queue")).thenReturn(3);

        assertThat(service.purgeQueue("order.queue")).isTrue();
    }

    @Test
    void shouldThrowWhenPurgeFails() {
        doThrow(new AmqpException("not found")).when(rabbitAdmin).purgeQueue("order.queue");

        assertThatThrownBy(() -> service.purgeQueue("order.queue"))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> assertThat(((GXRabbitMQOperationException) e).getOperation())
                        .isEqualTo(Operation.PURGE_QUEUE));
    }

    @Test
    void shouldDeclareExchange() {
        assertThat(service.createExchange(new DirectExchange("order.exchange"))).isTrue();

        verify(rabbitAdmin).declareExchange(any());
    }

    @Test
    void shouldThrowWhenExchangeDeclarationFails() {
        doThrow(new AmqpException("PRECONDITION_FAILED")).when(rabbitAdmin).declareExchange(any());

        assertThatThrownBy(() -> service.createExchange(new DirectExchange("order.exchange")))
                .isInstanceOf(GXRabbitMQOperationException.class)
                .satisfies(e -> assertThat(((GXRabbitMQOperationException) e).getOperation())
                        .isEqualTo(Operation.DECLARE_EXCHANGE));
    }

    @Test
    void shouldBindQueue() {
        assertThat(service.bindQueueToExchange("order.queue", "order.exchange", "order.created")).isTrue();

        verify(rabbitAdmin).declareBinding(any());
    }

    @Test
    void shouldThrowWhenBindingFails() {
        doThrow(new AmqpException("no exchange")).when(rabbitAdmin).declareBinding(any());

        assertThatThrownBy(() -> service.bindQueueToExchange("order.queue", "order.exchange", "order.created"))
                .isInstanceOf(GXRabbitMQOperationException.class);
    }

    @Test
    void shouldUnbindQueue() {
        assertThat(service.unbindQueueFromExchange("order.queue", "order.exchange", "order.created")).isTrue();

        verify(rabbitAdmin).removeBinding(any());
    }

    @Test
    void shouldThrowWhenUnbindFails() {
        doThrow(new AmqpException("channel closed")).when(rabbitAdmin).removeBinding(any());

        assertThatThrownBy(() -> service.unbindQueueFromExchange("order.queue", "order.exchange", "order.created"))
                .isInstanceOf(GXRabbitMQOperationException.class);
    }

    @Test
    void shouldSetUpMessageChannel() {
        when(rabbitAdmin.declareQueue(any())).thenReturn("order.queue");

        boolean created = service.setupMessageChannel("order.queue", true, false, false, null,
                new DirectExchange("order.exchange"), "order.created");

        assertThat(created).isTrue();
        verify(rabbitAdmin).declareExchange(any());
        verify(rabbitAdmin).declareQueue(any());
        verify(rabbitAdmin).declareBinding(any());
    }

    @Test
    void shouldSetUpDurableMessageChannel() {
        when(rabbitAdmin.declareQueue(any())).thenReturn("order.queue");

        boolean created = service.setupDurableMessageChannel("order.queue",
                new DirectExchange("order.exchange"), "order.created");

        assertThat(created).isTrue();
        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin).declareQueue(captor.capture());
        assertThat(captor.getValue().isDurable()).isTrue();
    }

    @Test
    void shouldRejectSetupWithoutExchange() {
        assertThatThrownBy(() -> service.setupMessageChannel("order.queue", true, false, false, null, null, "key"))
                .isInstanceOf(GXBusinessException.class);
    }

    private Message captureSentMessage() {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).convertAndSend(eq("test.exchange"), eq("test.key"), captor.capture(), nullable(CorrelationData.class));
        return captor.getValue();
    }
}
