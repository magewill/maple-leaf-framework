package cn.maple.rabbitmq.listener;

import cn.maple.rabbitmq.listener.impl.GXAmqpListenerErrorHandler;
import org.apache.qpid.protonj2.client.Delivery;
import org.apache.qpid.protonj2.client.DeliveryState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 毒消息兜底：转换失败必须 reject，其他异常必须原样抛出交给容器处理。
 */
class GXAmqpListenerErrorHandlerTest {
    private final GXAmqpListenerErrorHandler handler = new GXAmqpListenerErrorHandler();

    @Test
    void shouldRejectUnconvertiblePayload() throws Exception {
        Delivery delivery = Mockito.mock(Delivery.class);
        ListenerExecutionFailedException failure = new ListenerExecutionFailedException(
                "Failed to convert", new MessageConversionException("bad json"));

        Object result = handler.handleError(delivery, null, failure);

        assertThat(result).isNull();
        ArgumentCaptor<DeliveryState> stateCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        Mockito.verify(delivery).disposition(stateCaptor.capture(), Mockito.eq(true));
        assertThat(stateCaptor.getValue().getType()).isEqualTo(DeliveryState.Type.REJECTED);
    }

    @Test
    void shouldNotRejectAlreadySettledDelivery() throws Exception {
        Delivery delivery = Mockito.mock(Delivery.class);
        Mockito.when(delivery.settled()).thenReturn(true);
        ListenerExecutionFailedException failure = new ListenerExecutionFailedException(
                "Failed to convert", new MessageConversionException("bad json"));

        handler.handleError(delivery, null, failure);

        Mockito.verify(delivery, Mockito.never()).disposition(Mockito.any(), Mockito.anyBoolean());
    }

    @Test
    void shouldRethrowNonConversionFailure() {
        Delivery delivery = Mockito.mock(Delivery.class);
        ListenerExecutionFailedException failure = new ListenerExecutionFailedException(
                "handler exploded", new IllegalStateException("boom"));

        assertThatThrownBy(() -> handler.handleError(delivery, null, failure))
                .isSameAs(failure);
        Mockito.verifyNoInteractions(delivery);
    }
}
