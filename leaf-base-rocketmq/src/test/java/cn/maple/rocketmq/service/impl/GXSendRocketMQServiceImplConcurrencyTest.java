package cn.maple.rocketmq.service.impl;

import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;
import cn.maple.rocketmq.handler.GXRocketMQAsyncSendFailureHandler;
import cn.maple.rocketmq.service.GXSendRocketMQService;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.messaging.Message;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXSendRocketMQServiceImplConcurrencyTest {
    @Test
    void concurrentSendsKeepDestinationAndMessageKeyConsistent() throws Exception {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        List<String> observed = Collections.synchronizedList(new ArrayList<>());
        when(rocketMQTemplate.syncSend(anyString(), any(Message.class))).thenAnswer(invocation -> {
            String destination = invocation.getArgument(0);
            Message<?> message = invocation.getArgument(1);
            Object key = message.getHeaders().get(RocketMQHeaders.KEYS);
            observed.add(destination + "|" + key);
            return sendResult();
        });

        GXSendRocketMQService service = new GXSendRocketMQServiceImpl(rocketMQTemplate, new MockEnvironment(),
                new StaticListableBeanFactory().getBeanProvider(GXRocketMQAsyncSendFailureHandler.class));

        int threads = 8;
        int perThread = 25;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        Set<String> expected = new HashSet<>();

        for (int t = 1; t <= threads; t++) {
            String topic = "order_t" + t;
            String messageKey = "key-" + t;
            expected.add(topic + ":created|" + messageKey);
            Thread worker = new Thread(() -> {
                try {
                    startGate.await(5, TimeUnit.SECONDS);
                    for (int i = 0; i < perThread; i++) {
                        service.syncSend(messageReqDto(topic, messageKey));
                    }
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    doneGate.countDown();
                }
            }, "sender-" + t);
            worker.start();
        }

        startGate.countDown();
        assertThat(doneGate.await(60, TimeUnit.SECONDS)).isTrue();

        assertThat(errors).isEmpty();
        assertThat(observed).hasSize(threads * perThread);
        assertThat(observed).allMatch(expected::contains);
    }

    @Test
    void asyncSendUsesRequestSnapshotEvenWhenCallerMutatesAfterwards() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        GXSendRocketMQService service = new GXSendRocketMQServiceImpl(rocketMQTemplate, new MockEnvironment(),
                new StaticListableBeanFactory().getBeanProvider(GXRocketMQAsyncSendFailureHandler.class));

        GXRocketMQMessageReqDto reqDto = messageReqDto("order", "key-1");
        assertThat(service.sendAsync(reqDto)).isTrue();

        reqDto.setTopic("mutated");
        reqDto.setTag("mutated");
        reqDto.setMessageKey("mutated");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Message<String>> messageCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        org.mockito.Mockito.verify(rocketMQTemplate).asyncSend(org.mockito.ArgumentMatchers.eq("order:created"),
                messageCaptor.capture(), any());
        assertThat(messageCaptor.getValue().getHeaders().get(RocketMQHeaders.KEYS)).isEqualTo("key-1");
    }

    private GXRocketMQMessageReqDto messageReqDto(String topic, String messageKey) {
        GXRocketMQMessageReqDto reqDto = new GXRocketMQMessageReqDto();
        reqDto.setTopic(topic);
        reqDto.setTag("created");
        reqDto.setMessageKey(messageKey);
        reqDto.setBody("{\"id\":1}");
        return reqDto;
    }

    private SendResult sendResult() {
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        sendResult.setMsgId("msg-1");
        return sendResult;
    }
}
