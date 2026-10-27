package cn.maple.rabbitmq.listener;

import cn.maple.rabbitmq.listener.impl.GXDefaultRabbitMQQueueListenerImpl;
import cn.maple.rabbitmq.listener.impl.GXRabbitMQListenerStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GXDefaultRabbitMQQueueListenerTest {
    @Mock
    private GXRabbitMQListenerStatistics statistics;

    private GXDefaultRabbitMQQueueListenerImpl listener;

    @BeforeEach
    void setUp() {
        listener = new GXDefaultRabbitMQQueueListenerImpl(statistics);
    }

    @Test
    void shouldRejectEmptyBody() {
        Message message = new Message(new byte[0], new MessageProperties());

        assertThatThrownBy(() -> listener.process(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Empty");
        verify(statistics).rejected("empty");
    }

    @Test
    void shouldRejectBlankBody() {
        Message message = message("   ");

        assertThatThrownBy(() -> listener.process(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(statistics).rejected("empty");
    }

    @Test
    void shouldRejectUnparseableJson() {
        Message message = message("{\"a\":}");

        assertThatThrownBy(() -> listener.process(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("Unparseable");
        verify(statistics).rejected("unparseable");
    }

    @Test
    void shouldAcceptValidJson() {
        Message message = message("{\"orderId\":1}");

        assertThatCode(() -> listener.process(message)).doesNotThrowAnyException();
        verify(statistics).processed();
    }

    @Test
    void shouldAcceptPlainText() {
        Message message = message("plain-text-payload");

        assertThatCode(() -> listener.process(message)).doesNotThrowAnyException();
        verify(statistics).processed();
    }

    @Test
    void shouldRethrowUnexpectedFailureAndCountIt() {
        Message message = org.mockito.Mockito.mock(Message.class);
        when(message.getBody()).thenThrow(new IllegalStateException("broken"));

        assertThatThrownBy(() -> listener.process(message))
                .isInstanceOf(IllegalStateException.class);
        verify(statistics).failed(org.mockito.ArgumentMatchers.any(IllegalStateException.class));
        verify(statistics, org.mockito.Mockito.never()).processed();
    }

    @Test
    void shouldRejectNullMessage() {
        assertThatThrownBy(() -> listener.process(null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private static Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
