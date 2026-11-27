package cn.maple.canal.service.impl.rabbit;

import cn.maple.canal.constant.GXCanalConstant;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.exception.GXCanalMessageRejectedException;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import cn.maple.rabbitmq.service.GXSendRabbitMQService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXCanalRabbitMQFailureHandlerTest {
    private static final String PAYLOAD = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"INSERT\"}";

    private static final String TRACE_ID = "trace-1";

    private final GXCanalProperties canalProperties = new GXCanalProperties();

    private final GXCanalConsumeStatistics statistics = new GXCanalConsumeStatistics();

    private final GXSendRabbitMQService sendRabbitMQService = Mockito.mock(GXSendRabbitMQService.class);

    private final GXCanalRabbitMQFailureHandler handler = new GXCanalRabbitMQFailureHandler(
            canalProperties, statistics, providerReturning(sendRabbitMQService), new GXCanalLogThrottle());

    @Test
    void shouldRepublishRetryableMessageWithIncrementedHeader() {
        Mockito.doAnswer(invocation -> {
            cn.maple.rabbitmq.dto.inner.GXCorrelationData correlationData = invocation.getArgument(4);
            correlationData.settlement().complete(true);
            return null;
        }).when(sendRabbitMQService).sendRawMessage(Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any(MessageProperties.class), Mockito.any());

        handler.handle(retryableFailure(), PAYLOAD, 0, TRACE_ID);

        ArgumentCaptor<MessageProperties> captor = ArgumentCaptor.forClass(MessageProperties.class);
        Mockito.verify(sendRabbitMQService).sendRawMessage(Mockito.eq(canalProperties.getExchangeName()),
                Mockito.eq(canalProperties.getRoutingKey()), Mockito.eq(PAYLOAD), captor.capture(),
                Mockito.any(cn.maple.rabbitmq.dto.inner.GXCorrelationData.class));
        MessageProperties republished = captor.getValue();
        assertEquals(Integer.valueOf(1), republished.getHeader(GXCanalConstant.HEADER_RETRY_COUNT));
        assertEquals(TRACE_ID, republished.getHeader(GXCanalConstant.HEADER_TRACE_ID));
        assertEquals(0, statistics.deadLetterCount(), "a retried message is not dead lettered");
    }

    @Test
    void shouldRejectWhenRepublishIsNotAccepted() {
        Mockito.doAnswer(invocation -> {
            cn.maple.rabbitmq.dto.inner.GXCorrelationData correlationData = invocation.getArgument(4);
            correlationData.settlement().complete(false);
            return null;
        }).when(sendRabbitMQService).sendRawMessage(Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any(MessageProperties.class), Mockito.any());

        GXCanalMessageRejectedException exception = assertThrows(GXCanalMessageRejectedException.class,
                () -> handler.handle(retryableFailure(), PAYLOAD, 0, TRACE_ID));

        assertEquals("Canal message republish not accepted, rejected to dead letter queue: handler exploded",
                exception.getMessage());
        assertEquals(1, statistics.deadLetterCount());
    }

    @Test
    void shouldRejectWhenRepublishThrows() {
        Mockito.doThrow(new org.springframework.amqp.AmqpException("broker down"))
                .when(sendRabbitMQService).sendRawMessage(Mockito.anyString(), Mockito.anyString(),
                        Mockito.anyString(), Mockito.any(MessageProperties.class), Mockito.any());

        assertThrows(GXCanalMessageRejectedException.class,
                () -> handler.handle(retryableFailure(), PAYLOAD, 0, TRACE_ID));
        assertEquals(1, statistics.deadLetterCount());
    }

    @Test
    void shouldRejectWhenRetryLimitReached() {
        GXCanalMessageRejectedException exception = assertThrows(GXCanalMessageRejectedException.class,
                () -> handler.handle(retryableFailure(), PAYLOAD, 3, TRACE_ID));

        assertEquals("Canal message rejected: handler exploded", exception.getMessage());
        assertEquals(1, statistics.deadLetterCount());
        Mockito.verifyNoInteractions(sendRabbitMQService);
    }

    @Test
    void shouldRejectNonRetryableMessageImmediately() {
        GXCanalConsumeStatistics localStatistics = new GXCanalConsumeStatistics();
        GXCanalRabbitMQFailureHandler localHandler = new GXCanalRabbitMQFailureHandler(
                canalProperties, localStatistics, providerReturning(sendRabbitMQService), new GXCanalLogThrottle());
        GXCanalParseResultInnerDto failure = GXCanalParseResultInnerDto.nonRetryableFailure("bad payload", 0);

        assertThrows(GXCanalMessageRejectedException.class, () -> localHandler.handle(failure, PAYLOAD, 0, TRACE_ID));

        assertEquals(1, localStatistics.deadLetterCount(),
                "the listener owns outcome counters, the handler owns dead letters");
        Mockito.verifyNoInteractions(sendRabbitMQService);
    }

    @Test
    void shouldRejectWhenSendServiceIsUnavailable() {
        GXCanalRabbitMQFailureHandler withoutService = new GXCanalRabbitMQFailureHandler(
                canalProperties, statistics, providerReturning(null), new GXCanalLogThrottle());

        assertThrows(GXCanalMessageRejectedException.class,
                () -> withoutService.handle(retryableFailure(), PAYLOAD, 0, TRACE_ID));
        assertEquals(1, statistics.deadLetterCount());
    }

    private GXCanalParseResultInnerDto retryableFailure() {
        return GXCanalParseResultInnerDto.retryableFailure("handler exploded", 0);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<GXSendRabbitMQService> providerReturning(GXSendRabbitMQService service) {
        ObjectProvider<GXSendRabbitMQService> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }
}
