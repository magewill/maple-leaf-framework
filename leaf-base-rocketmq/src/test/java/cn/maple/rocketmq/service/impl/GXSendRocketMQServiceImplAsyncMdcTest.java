package cn.maple.rocketmq.service.impl;

import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;
import cn.maple.rocketmq.handler.GXRocketMQAsyncSendFailureContext;
import cn.maple.rocketmq.handler.GXRocketMQAsyncSendFailureHandler;
import cn.maple.rocketmq.service.GXSendRocketMQService;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.messaging.Message;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GXSendRocketMQServiceImplAsyncMdcTest {
    private static final String TRACE_ID_KEY = "traceId";

    private static final String TRACE_ID_VALUE = "trace-123";

    @Test
    void restoresCallerMdcContextInsideAsyncCallback() throws Exception {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        List<String> observedTraceIds = new CopyOnWriteArrayList<>();
        GXRocketMQAsyncSendFailureHandler handler = context -> observedTraceIds.add(MDC.get(TRACE_ID_KEY));

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("handler", handler));
        GXSendRocketMQService service = new GXSendRocketMQServiceImpl(rocketMQTemplate, new MockEnvironment(),
                beanFactory.getBeanProvider(GXRocketMQAsyncSendFailureHandler.class));

        ArgumentCaptor<SendCallback> callbackCaptor = ArgumentCaptor.forClass(SendCallback.class);
        MDC.put(TRACE_ID_KEY, TRACE_ID_VALUE);
        try {
            assertThat(service.sendAsync(messageReqDto())).isTrue();
        } finally {
            MDC.clear();
        }
        verify(rocketMQTemplate).asyncSend(eq("order:created"), any(Message.class), callbackCaptor.capture());

        AtomicReference<Map<String, String>> mdcAfterCallback = new AtomicReference<>();
        Thread callbackThread = new Thread(() -> {
            MDC.clear();
            callbackCaptor.getValue().onException(new RuntimeException("boom"));
            mdcAfterCallback.set(MDC.getCopyOfContextMap());
        }, "async-callback-simulator");
        callbackThread.start();
        callbackThread.join(5_000L);

        assertThat(observedTraceIds).containsExactly(TRACE_ID_VALUE);
        assertThat(mdcAfterCallback.get()).isNullOrEmpty();
    }

    @Test
    void restoresCallbackThreadContextAfterUse() throws Exception {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        List<GXRocketMQAsyncSendFailureContext> contexts = new CopyOnWriteArrayList<>();
        GXRocketMQAsyncSendFailureHandler handler = contexts::add;

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("handler", handler));
        GXSendRocketMQService service = new GXSendRocketMQServiceImpl(rocketMQTemplate, new MockEnvironment(),
                beanFactory.getBeanProvider(GXRocketMQAsyncSendFailureHandler.class));

        ArgumentCaptor<SendCallback> callbackCaptor = ArgumentCaptor.forClass(SendCallback.class);
        MDC.clear();
        service.sendAsync(messageReqDto());
        verify(rocketMQTemplate).asyncSend(eq("order:created"), any(Message.class), callbackCaptor.capture());

        AtomicReference<Map<String, String>> leaked = new AtomicReference<>();
        Thread callbackThread = new Thread(() -> {
            MDC.put(TRACE_ID_KEY, "polluted");
            callbackCaptor.getValue().onException(new RuntimeException("boom"));
            leaked.set(MDC.getCopyOfContextMap());
        }, "async-callback-polluted");
        callbackThread.start();
        callbackThread.join(5_000L);

        assertThat(contexts).hasSize(1);
        assertThat(leaked.get()).containsEntry(TRACE_ID_KEY, "polluted");
    }

    private GXRocketMQMessageReqDto messageReqDto() {
        GXRocketMQMessageReqDto reqDto = new GXRocketMQMessageReqDto();
        reqDto.setTopic("order");
        reqDto.setTag("created");
        reqDto.setMessageKey("key-1");
        reqDto.setBody("{\"id\":1}");
        return reqDto;
    }
}
