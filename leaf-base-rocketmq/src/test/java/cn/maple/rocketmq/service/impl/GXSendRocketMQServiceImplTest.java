package cn.maple.rocketmq.service.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;
import cn.maple.rocketmq.service.GXSendRocketMQService;
import jakarta.annotation.Resource;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = GXSendRocketMQServiceImplTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.config.import-check.enabled=false",
                "logging.level.cn.maple.rocketmq.service.impl.GXSendRocketMQServiceImpl=OFF"
        }
)
class GXSendRocketMQServiceImplTest {
    @Resource
    private GXSendRocketMQService sendRocketMQService;

    @MockitoBean
    private RocketMQTemplate rocketMQTemplate;

    @Test
    void contextLoadsServiceBean() {
        assertThat(sendRocketMQService).isInstanceOf(GXSendRocketMQServiceImpl.class);
    }

    @Test
    void sendNormalMessageSendsTrimmedDestinationAndMessageKey() {
        when(rocketMQTemplate.syncSend(eq("order:created"), any(Message.class)))
                .thenReturn(sendResult(SendStatus.SEND_OK, "msg-1"));

        GXRocketMQMessageReqDto reqDto = messageReqDto(" order ", " created ", " key-1 ");

        assertThatCode(() -> sendRocketMQService.sendNormalMessage(reqDto)).doesNotThrowAnyException();

        ArgumentCaptor<Message<String>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(eq("order:created"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getPayload()).isEqualTo("{\"id\":1}");
        assertThat(messageCaptor.getValue().getHeaders().get(RocketMQHeaders.KEYS)).isEqualTo("key-1");
    }

    @Test
    void sendNormalMessageThrowsWhenBrokerStatusIsNotOk() {
        when(rocketMQTemplate.syncSend(eq("order:created"), any(Message.class)))
                .thenReturn(sendResult(SendStatus.SLAVE_NOT_AVAILABLE, "msg-normal-failed"));

        assertThatThrownBy(() -> sendRocketMQService.sendNormalMessage(messageReqDto("order", "created", "key-1")))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Normal message send failed")
                .hasMessageContaining("SLAVE_NOT_AVAILABLE");
    }

    @Test
    void syncSendReturnsTrueWhenBrokerStatusIsOk() {
        when(rocketMQTemplate.syncSend(eq("order:created"), any(Message.class)))
                .thenReturn(sendResult(SendStatus.SEND_OK, "msg-2"));

        assertThat(sendRocketMQService.syncSend(messageReqDto("order", "created", "key-1"))).isTrue();
    }

    @Test
    void syncSendThrowsWhenBrokerStatusIsNotOk() {
        when(rocketMQTemplate.syncSend(eq("order:created"), any(Message.class)))
                .thenReturn(sendResult(SendStatus.FLUSH_DISK_TIMEOUT, "msg-3"));

        assertThatThrownBy(() -> sendRocketMQService.syncSend(messageReqDto("order", "created", "key-1")))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Sync message send failed")
                .hasMessageContaining("FLUSH_DISK_TIMEOUT");
    }

    @Test
    void sendDelayMessageReturnsMessageIdAndUsesFutureDeliveryTime() {
        when(rocketMQTemplate.syncSendDeliverTimeMills(eq("order:timeout"), any(Message.class), anyLong()))
                .thenReturn(sendResult(SendStatus.SEND_OK, "delay-msg-1"));

        GXRocketMQMessageReqDto reqDto = messageReqDto("order", "timeout", "order-1");
        reqDto.setDeliverTime(30);
        long beforeSend = System.currentTimeMillis();

        assertThat(sendRocketMQService.sendDelayMessage(reqDto)).isEqualTo("delay-msg-1");

        ArgumentCaptor<Long> deliveryTimeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(rocketMQTemplate).syncSendDeliverTimeMills(eq("order:timeout"), any(Message.class), deliveryTimeCaptor.capture());
        assertThat(deliveryTimeCaptor.getValue()).isGreaterThanOrEqualTo(beforeSend + 30_000L);
    }

    @Test
    void sendDelayMessageThrowsWhenBrokerReturnsNullResult() {
        when(rocketMQTemplate.syncSendDeliverTimeMills(eq("order:timeout"), any(Message.class), anyLong()))
                .thenReturn(null);

        GXRocketMQMessageReqDto reqDto = messageReqDto("order", "timeout", "order-1");
        reqDto.setDeliverTime(30);

        assertThatThrownBy(() -> sendRocketMQService.sendDelayMessage(reqDto))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Delay message send failed");
    }

    @Test
    void sendDelayMessageRejectsNonPositiveDelayTime() {
        GXRocketMQMessageReqDto reqDto = messageReqDto("order", "timeout", "order-1");
        reqDto.setDeliverTime(0);

        assertThatThrownBy(() -> sendRocketMQService.sendDelayMessage(reqDto))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Delay time must be greater than 0 seconds");
    }

    @Test
    void sendDelayMessageRejectsOverflowDeliveryTime() {
        GXRocketMQMessageReqDto reqDto = messageReqDto("order", "timeout", "order-1");
        reqDto.setDeliverTime(Long.MAX_VALUE);

        assertThatThrownBy(() -> sendRocketMQService.sendDelayMessage(reqDto))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Delay time is too large");
    }

    @Test
    void sendAsyncSubmitsCallbackAndKeepsRequestSnapshot() {
        GXRocketMQMessageReqDto reqDto = messageReqDto(" order ", " created ", " key-1 ");

        assertThat(sendRocketMQService.sendAsync(reqDto)).isTrue();
        reqDto.setTopic("mutated");
        reqDto.setTag("mutated");

        ArgumentCaptor<Message<String>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<SendCallback> callbackCaptor = ArgumentCaptor.forClass(SendCallback.class);
        verify(rocketMQTemplate).asyncSend(eq("order:created"), messageCaptor.capture(), callbackCaptor.capture());
        assertThat(messageCaptor.getValue().getHeaders().get(RocketMQHeaders.KEYS)).isEqualTo("key-1");

        assertThatCode(() -> callbackCaptor.getValue().onSuccess(sendResult(SendStatus.SEND_OK, "async-msg-1")))
                .doesNotThrowAnyException();
        assertThatCode(() -> callbackCaptor.getValue().onSuccess(sendResult(SendStatus.FLUSH_SLAVE_TIMEOUT, "async-msg-2")))
                .doesNotThrowAnyException();
        assertThatCode(() -> callbackCaptor.getValue().onSuccess(null))
                .doesNotThrowAnyException();
        assertThatCode(() -> callbackCaptor.getValue().onException(new RuntimeException("network error")))
                .doesNotThrowAnyException();
        assertThatCode(() -> callbackCaptor.getValue().onException(null))
                .doesNotThrowAnyException();
    }

    @Test
    void sendOnewaySendsTopicOnlyWhenTagIsBlank() {
        GXRocketMQMessageReqDto reqDto = messageReqDto("order", " ", null);

        assertThat(sendRocketMQService.sendOneway(reqDto)).isTrue();

        ArgumentCaptor<Message<String>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).sendOneWay(eq("order"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getHeaders()).doesNotContainKey(RocketMQHeaders.KEYS);
    }

    @Test
    void sendOnewayWrapsTemplateException() {
        GXRocketMQMessageReqDto reqDto = messageReqDto("order", "created", "key-1");
        doThrow(new IllegalStateException("producer not ready"))
                .when(rocketMQTemplate).sendOneWay(eq("order:created"), any(Message.class));

        assertThatThrownBy(() -> sendRocketMQService.sendOneway(reqDto))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Failed to send oneway message")
                .hasMessageContaining("producer not ready");
    }

    @Test
    void sendNormalMessageRejectsNullRequest() {
        assertThatThrownBy(() -> sendRocketMQService.sendNormalMessage(null))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Message request must not be null");
    }

    @Test
    void sendNormalMessageRejectsBlankBody() {
        GXRocketMQMessageReqDto reqDto = messageReqDto("order", "created", "key-1");
        reqDto.setBody(" ");

        assertThatThrownBy(() -> sendRocketMQService.sendNormalMessage(reqDto))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Message body must not be blank");
    }

    @Test
    void sendOnewayRejectsBlankTopic() {
        GXRocketMQMessageReqDto reqDto = messageReqDto(" ", "created", "key-1");

        assertThatThrownBy(() -> sendRocketMQService.sendOneway(reqDto))
                .isInstanceOf(GXBusinessException.class)
                .hasMessageContaining("Message topic must not be blank");
    }

    private GXRocketMQMessageReqDto messageReqDto(String topic, String tag, String messageKey) {
        GXRocketMQMessageReqDto reqDto = new GXRocketMQMessageReqDto();
        reqDto.setTopic(topic);
        reqDto.setTag(tag);
        reqDto.setMessageKey(messageKey);
        reqDto.setBody("{\"id\":1}");
        return reqDto;
    }

    private SendResult sendResult(SendStatus sendStatus, String msgId) {
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(sendStatus);
        sendResult.setMsgId(msgId);
        return sendResult;
    }

    @SpringBootConfiguration
    @Import(GXSendRocketMQServiceImpl.class)
    static class TestApplication {
    }
}
