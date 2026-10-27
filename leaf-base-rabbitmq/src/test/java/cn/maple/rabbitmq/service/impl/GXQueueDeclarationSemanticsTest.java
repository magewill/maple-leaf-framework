package cn.maple.rabbitmq.service.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 锁定"声明状态必须来自 Broker"的语义：Broker 侧删除后不得继续报告存在。
 */
@ExtendWith(MockitoExtension.class)
class GXQueueDeclarationSemanticsTest {
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RabbitAdmin rabbitAdmin;

    private GXSendRabbitMQServiceImpl service;

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(rabbitTemplate, rabbitAdmin);
    }

    @Test
    void shouldReportMissingAfterBrokerSideDeletion() {
        Properties properties = new Properties();
        properties.setProperty("QUEUE_NAME", "order.queue");
        when(rabbitAdmin.getQueueProperties("order.queue")).thenReturn(properties, (Properties) null);

        assertThat(service.queueExists("order.queue")).isTrue();
        assertThat(service.queueExists("order.queue")).isFalse();
        verify(rabbitAdmin, times(2)).getQueueProperties("order.queue");
    }

    @Test
    void shouldDeclareAgainAfterBrokerSideDeletion() {
        when(rabbitAdmin.declareQueue(any())).thenReturn("order.queue");

        service.createQueue("order.queue", true, false, false, null);
        when(rabbitAdmin.getQueueProperties("order.queue")).thenReturn(null);
        assertThat(service.queueExists("order.queue")).isFalse();
        service.createQueue("order.queue", true, false, false, null);

        verify(rabbitAdmin, times(2)).declareQueue(any());
    }

    @Test
    void shouldNotReportSuccessWhenDeleteFails() {
        when(rabbitAdmin.deleteQueue("order.queue")).thenThrow(new org.springframework.amqp.AmqpException("closed"));

        assertThatThrownBy(() -> service.deleteQueue("order.queue"))
                .isInstanceOf(cn.maple.rabbitmq.exception.GXRabbitMQOperationException.class);
    }

    @Test
    void shouldRejectBlankNameOnExistenceCheck() {
        assertThatThrownBy(() -> service.queueExists(" "))
                .isInstanceOf(GXBusinessException.class);
    }

    @Test
    void shouldReturnDeclaredQueueInstance() {
        when(rabbitAdmin.declareQueue(any())).thenReturn("order.queue");

        Queue queue = service.createQueue("order.queue", true, false, false, null);

        assertThat(queue.getName()).isEqualTo("order.queue");
        assertThat(queue.isDurable()).isTrue();
    }
}
