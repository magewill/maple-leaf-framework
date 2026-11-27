package cn.maple.rabbitmq.service.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.rabbitmq.exception.GXRabbitMQOperationException;
import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import cn.maple.rabbitmq.support.GXAmqpManagementException;
import cn.maple.rabbitmq.support.GXAmqpMessagePublisher;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 锁定"声明状态必须来自 Broker"的语义：Broker 侧删除后不得继续报告存在。
 */
@ExtendWith(MockitoExtension.class)
class GXQueueDeclarationSemanticsTest {
    @Mock
    private GXAmqpMessagePublisher publisher;

    @Mock
    private GXAmqpManagementClient managementClient;

    private GXSendRabbitMQServiceImpl service;

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(publisher, managementClient);
    }

    @Test
    void shouldReportMissingAfterBrokerSideDeletion() {
        when(managementClient.queueExists("order.queue")).thenReturn(true, false);

        assertThat(service.queueExists("order.queue")).isTrue();
        assertThat(service.queueExists("order.queue")).isFalse();
        verify(managementClient, times(2)).queueExists("order.queue");
    }

    @Test
    void shouldDeclareAgainAfterBrokerSideDeletion() {
        service.createQueue("order.queue", true, false, false, null);
        when(managementClient.queueExists("order.queue")).thenReturn(false);
        assertThat(service.queueExists("order.queue")).isFalse();
        service.createQueue("order.queue", true, false, false, null);

        verify(managementClient, times(2)).declareQueue(anyString(), anyBoolean(), anyBoolean(), anyMap());
    }

    @Test
    void shouldNotReportSuccessWhenDeleteFails() {
        when(managementClient.deleteQueue("order.queue"))
                .thenThrow(new GXAmqpManagementException("closed", 500));

        assertThatThrownBy(() -> service.deleteQueue("order.queue"))
                .isInstanceOf(GXRabbitMQOperationException.class);
    }

    @Test
    void shouldRejectBlankNameOnExistenceCheck() {
        assertThatThrownBy(() -> service.queueExists(" "))
                .isInstanceOf(GXBusinessException.class);
    }

    @Test
    void shouldReturnDeclaredQueueInstance() {
        Queue queue = service.createQueue("order.queue", true, false, false, null);

        assertThat(queue.getName()).isEqualTo("order.queue");
        assertThat(queue.isDurable()).isTrue();
    }
}
