package cn.maple.canal.listener;

import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.exception.GXCanalMessageRejectedException;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.service.GXCanalMessageParseService;
import cn.maple.canal.service.support.GXCanalFailureHandler;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.client.annotation.AmqpListener;
import org.springframework.amqp.core.AmqpAcknowledgment;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.context.annotation.Lazy;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the AMQP 1.0 listener contract: the bean must stay eager, the address must be an AMQP 1.0 v2
 * queue address, and every delivery must be settled explicitly.
 */
class GXCanalListenerRegistrationTest {
    private static final String PAYLOAD = "{\"database\":\"demo\",\"table\":\"sys_user\",\"type\":\"UPDATE\"}";

    private final GXCanalMessageParseService parseService = Mockito.mock(GXCanalMessageParseService.class);

    private final GXCanalFailureHandler failureHandler = Mockito.mock(GXCanalFailureHandler.class);

    private final AmqpAcknowledgment acknowledgment = Mockito.mock(AmqpAcknowledgment.class);

    private final GXCanalRabbitMQListener listener = new GXCanalRabbitMQListener(
            parseService, failureHandler, new GXCanalConsumeStatistics(), new GXCanalLogThrottle());

    @Test
    void shouldStayEagerSoTheEndpointRegisters() {
        assertFalse(GXCanalRabbitMQListener.class.isAnnotationPresent(Lazy.class),
                "GXCanalRabbitMQListener must stay eager, otherwise the Canal endpoint may never register");
    }

    @Test
    void shouldListenOnAmqp10QueueAddress() {
        Method onMessage = findAmqpListenerMethod();

        AmqpListener annotation = onMessage.getAnnotation(AmqpListener.class);
        assertEquals(1, annotation.addresses().length);
        assertTrue(annotation.addresses()[0].startsWith("/queues/"),
                "RabbitMQ only accepts AMQP 1.0 v2 addresses, e.g. /queues/canalQueue");
        assertFalse(annotation.concurrency().isEmpty(), "concurrency must stay configurable");
    }

    @Test
    void shouldAcceptAfterSuccessfulHandling() {
        Mockito.when(parseService.parse(PAYLOAD)).thenReturn(List.of());

        listener.onMessage(message(PAYLOAD), acknowledgment);

        Mockito.verify(acknowledgment).acknowledge(AmqpAcknowledgment.Status.ACCEPT);
    }

    @Test
    void shouldRejectWhenTheFailureHandlerRejects() {
        Mockito.when(parseService.parse(PAYLOAD)).thenThrow(new IllegalStateException("boom"));
        Mockito.doThrow(new GXCanalMessageRejectedException("bad payload"))
                .when(failureHandler).handle(Mockito.any(), Mockito.eq(PAYLOAD), Mockito.anyInt(), Mockito.any());

        listener.onMessage(message(PAYLOAD), acknowledgment);

        Mockito.verify(acknowledgment).acknowledge(AmqpAcknowledgment.Status.REJECT);
    }

    @Test
    void shouldRequeueWhenHandlingFailsUnexpectedly() {
        Mockito.when(parseService.parse(PAYLOAD)).thenThrow(new IllegalStateException("boom"));
        Mockito.doThrow(new IllegalStateException("handler exploded"))
                .when(failureHandler).handle(Mockito.any(), Mockito.eq(PAYLOAD), Mockito.anyInt(), Mockito.any());

        listener.onMessage(message(PAYLOAD), acknowledgment);

        Mockito.verify(acknowledgment).acknowledge(AmqpAcknowledgment.Status.REQUEUE);
    }

    @Test
    void shouldStillSupportDirectInvocation() {
        Mockito.when(parseService.parse(PAYLOAD)).thenReturn(List.of());

        listener.listener(PAYLOAD, "trace-direct", null);

        Mockito.verify(parseService).parse(PAYLOAD);
        Mockito.verifyNoInteractions(failureHandler);
    }

    private static Method findAmqpListenerMethod() {
        for (Method method : GXCanalRabbitMQListener.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(AmqpListener.class)) {
                assertNotNull(method);
                return method;
            }
        }
        throw new AssertionError("GXCanalRabbitMQListener must declare an @AmqpListener method");
    }

    private static Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
