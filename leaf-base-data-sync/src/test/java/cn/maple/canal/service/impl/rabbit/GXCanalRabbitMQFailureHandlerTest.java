package cn.maple.canal.service.impl.rabbit;

import cn.maple.canal.constant.GXCanalConstant;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.exception.GXCanalMessageRejectedException;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXCanalRabbitMQFailureHandlerTest {
    private static final String PAYLOAD = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"INSERT\"}";

    private static final String TRACE_ID = "trace-1";

    private final GXCanalProperties canalProperties = new GXCanalProperties();

    private final GXCanalConsumeStatistics statistics = new GXCanalConsumeStatistics();

    private final RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);

    private final GXCanalRabbitMQFailureHandler handler = new GXCanalRabbitMQFailureHandler(
            canalProperties, statistics, providerReturning(rabbitTemplate), new GXCanalLogThrottle());

    @Test
    void shouldRepublishRetryableMessageWithIncrementedHeader() {
        handler.handle(retryableFailure(), PAYLOAD, 0, TRACE_ID);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        Mockito.verify(rabbitTemplate).send(Mockito.eq(canalProperties.getExchangeName()),
                Mockito.eq(canalProperties.getRoutingKey()), captor.capture());
        Message republished = captor.getValue();
        Object retryHeader = republished.getMessageProperties().getHeader(GXCanalConstant.HEADER_RETRY_COUNT);
        Object traceHeader = republished.getMessageProperties().getHeader(GXCanalConstant.HEADER_TRACE_ID);
        assertEquals(Integer.valueOf(1), retryHeader);
        assertEquals(TRACE_ID, traceHeader);
        assertEquals(PAYLOAD, new String(republished.getBody(), StandardCharsets.UTF_8));
        assertEquals(0, statistics.deadLetterCount(), "a retried message is not dead lettered");
    }

    @Test
    void shouldRejectWhenRetryLimitReached() {
        GXCanalMessageRejectedException exception = assertThrows(GXCanalMessageRejectedException.class,
                () -> handler.handle(retryableFailure(), PAYLOAD, 3, TRACE_ID));

        assertEquals("Canal message rejected: handler exploded", exception.getMessage());
        assertEquals(1, statistics.deadLetterCount());
        Mockito.verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldRejectNonRetryableMessageImmediately() {
        GXCanalConsumeStatistics localStatistics = new GXCanalConsumeStatistics();
        GXCanalRabbitMQFailureHandler localHandler = new GXCanalRabbitMQFailureHandler(
                canalProperties, localStatistics, providerReturning(rabbitTemplate), new GXCanalLogThrottle());
        GXCanalParseResultInnerDto failure = GXCanalParseResultInnerDto.nonRetryableFailure("bad payload", 0);

        assertThrows(GXCanalMessageRejectedException.class, () -> localHandler.handle(failure, PAYLOAD, 0, TRACE_ID));

        assertEquals(1, localStatistics.deadLetterCount(),
                "the listener owns outcome counters, the handler owns dead letters");
        Mockito.verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldRejectWhenRabbitTemplateIsUnavailable() {
        GXCanalRabbitMQFailureHandler withoutTemplate = new GXCanalRabbitMQFailureHandler(
                canalProperties, statistics, providerReturning(null), new GXCanalLogThrottle());

        assertThrows(GXCanalMessageRejectedException.class,
                () -> withoutTemplate.handle(retryableFailure(), PAYLOAD, 0, TRACE_ID));
        assertEquals(1, statistics.deadLetterCount());
    }

    private GXCanalParseResultInnerDto retryableFailure() {
        return GXCanalParseResultInnerDto.retryableFailure("handler exploded", 0);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<RabbitTemplate> providerReturning(RabbitTemplate template) {
        ObjectProvider<RabbitTemplate> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(template);
        return provider;
    }
}
