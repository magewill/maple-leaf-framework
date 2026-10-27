package cn.maple.rabbitmq.exception;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.rabbitmq.exception.GXRabbitMQOperationException.Operation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GXRabbitMQOperationExceptionTest {
    @Test
    void shouldExposeOperationAndResource() {
        GXRabbitMQOperationException exception = new GXRabbitMQOperationException(
                Operation.DECLARE_QUEUE, "order.queue", "queue declare failed");

        assertThat(exception).isInstanceOf(GXBusinessException.class);
        assertThat(exception.getOperation()).isEqualTo(Operation.DECLARE_QUEUE);
        assertThat(exception.getResource()).isEqualTo("order.queue");
        assertThat(exception.getDetail().getStr("operation")).isEqualTo("DECLARE_QUEUE");
    }

    @Test
    void shouldKeepCauseTypeInDetail() {
        IllegalStateException cause = new IllegalStateException("broker down");
        GXRabbitMQOperationException exception = new GXRabbitMQOperationException(
                Operation.SEND, "test.exchange", "send failed", cause);

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getDetail().getStr("causeType")).isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    void shouldRecordProgressWithImmutableSteps() {
        GXRabbitMQOperationException exception = new GXRabbitMQOperationException(
                Operation.BIND, "order.queue", "bind failed");

        exception.withProgress(List.of("DECLARE_EXCHANGE", "DECLARE_QUEUE"), "BIND");

        assertThat(exception.getDetail().get("completedSteps")).isEqualTo(List.of("DECLARE_EXCHANGE", "DECLARE_QUEUE"));
        assertThat(exception.getDetail().getStr("failedStep")).isEqualTo("BIND");
    }
}
