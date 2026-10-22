package cn.maple.rocketmq.handler;

import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;
import cn.maple.rocketmq.service.GXSendRocketMQService;
import cn.maple.rocketmq.service.impl.GXSendRocketMQServiceImpl;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GXRocketMQAsyncSendFailureHandlerTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.cloud.nacos.config.import-check.enabled=false");

    @Test
    void doesNotThrowWhenNoHandlerIsRegistered() {
        contextRunner.withUserConfiguration(NoHandlerConfig.class).run(context -> {
            RocketMQTemplate template = context.getBean(RocketMQTemplate.class);
            GXSendRocketMQService service = context.getBean(GXSendRocketMQService.class);

            assertThat(service.sendAsync(messageReqDto())).isTrue();

            ArgumentCaptor<SendCallback> callbackCaptor = ArgumentCaptor.forClass(SendCallback.class);
            verify(template).asyncSend(eq("order:created"), any(Message.class), callbackCaptor.capture());
            SendCallback callback = callbackCaptor.getValue();

            assertThatCode(() -> callback.onException(new RuntimeException("network error"))).doesNotThrowAnyException();
            assertThatCode(() -> callback.onSuccess(sendResult(SendStatus.FLUSH_SLAVE_TIMEOUT))).doesNotThrowAnyException();
            assertThatCode(() -> callback.onSuccess(null)).doesNotThrowAnyException();
        });
    }

    @Test
    void notifiesHandlerWithCauseWhenCallbackFails() {
        contextRunner.withUserConfiguration(SingleHandlerConfig.class).run(context -> {
            RocketMQTemplate template = context.getBean(RocketMQTemplate.class);
            GXSendRocketMQService service = context.getBean(GXSendRocketMQService.class);
            RecordingHandler handler = context.getBean(RecordingHandler.class);

            assertThat(service.sendAsync(messageReqDto())).isTrue();

            ArgumentCaptor<SendCallback> callbackCaptor = ArgumentCaptor.forClass(SendCallback.class);
            verify(template).asyncSend(eq("order:created"), any(Message.class), callbackCaptor.capture());
            callbackCaptor.getValue().onException(new RuntimeException("boom"));

            assertThat(handler.contexts).hasSize(1);
            GXRocketMQAsyncSendFailureContext context1 = handler.contexts.get(0);
            assertThat(context1.topic()).isEqualTo("order");
            assertThat(context1.tag()).isEqualTo("created");
            assertThat(context1.messageKey()).isEqualTo("key-1");
            assertThat(context1.sendStatus()).isNull();
            assertThat(context1.cause()).hasMessage("boom");
        });
    }

    @Test
    void notifiesHandlerWithStatusForNonOkSendResult() {
        contextRunner.withUserConfiguration(SingleHandlerConfig.class).run(context -> {
            RocketMQTemplate template = context.getBean(RocketMQTemplate.class);
            GXSendRocketMQService service = context.getBean(GXSendRocketMQService.class);
            RecordingHandler handler = context.getBean(RecordingHandler.class);

            service.sendAsync(messageReqDto());

            ArgumentCaptor<SendCallback> callbackCaptor = ArgumentCaptor.forClass(SendCallback.class);
            verify(template).asyncSend(eq("order:created"), any(Message.class), callbackCaptor.capture());
            callbackCaptor.getValue().onSuccess(sendResult(SendStatus.FLUSH_DISK_TIMEOUT));
            callbackCaptor.getValue().onSuccess(null);

            assertThat(handler.contexts).hasSize(2);
            assertThat(handler.contexts.get(0).sendStatus()).isEqualTo(SendStatus.FLUSH_DISK_TIMEOUT);
            assertThat(handler.contexts.get(0).cause()).isNull();
            assertThat(handler.contexts.get(1).sendStatus()).isNull();
            assertThat(handler.contexts.get(1).cause()).isNotNull();
        });
    }

    @Test
    void handlerFailureDoesNotBlockOtherHandlers() {
        contextRunner.withUserConfiguration(TwoHandlerConfig.class).run(context -> {
            RocketMQTemplate template = context.getBean(RocketMQTemplate.class);
            GXSendRocketMQService service = context.getBean(GXSendRocketMQService.class);
            RecordingHandler handler = context.getBean(RecordingHandler.class);

            service.sendAsync(messageReqDto());

            ArgumentCaptor<SendCallback> callbackCaptor = ArgumentCaptor.forClass(SendCallback.class);
            verify(template).asyncSend(eq("order:created"), any(Message.class), callbackCaptor.capture());

            assertThatCode(() -> callbackCaptor.getValue().onException(new RuntimeException("boom")))
                    .doesNotThrowAnyException();
            assertThat(handler.contexts).hasSize(1);
        });
    }

    private GXRocketMQMessageReqDto messageReqDto() {
        GXRocketMQMessageReqDto reqDto = new GXRocketMQMessageReqDto();
        reqDto.setTopic("order");
        reqDto.setTag("created");
        reqDto.setMessageKey("key-1");
        reqDto.setBody("{\"id\":1}");
        return reqDto;
    }

    private SendResult sendResult(SendStatus sendStatus) {
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(sendStatus);
        sendResult.setMsgId("msg-1");
        return sendResult;
    }

    static class RecordingHandler implements GXRocketMQAsyncSendFailureHandler {
        private final List<GXRocketMQAsyncSendFailureContext> contexts = new CopyOnWriteArrayList<>();

        @Override
        public void onFailure(GXRocketMQAsyncSendFailureContext context) {
            contexts.add(context);
        }
    }

    static class ThrowingHandler implements GXRocketMQAsyncSendFailureHandler {
        @Override
        public void onFailure(GXRocketMQAsyncSendFailureContext context) {
            throw new IllegalStateException("handler failure");
        }
    }

    @SpringBootConfiguration
    @Import(GXSendRocketMQServiceImpl.class)
    static class NoHandlerConfig {
        @Bean
        RocketMQTemplate rocketMQTemplate() {
            return mock(RocketMQTemplate.class);
        }
    }

    @SpringBootConfiguration
    @Import(GXSendRocketMQServiceImpl.class)
    static class SingleHandlerConfig {
        @Bean
        RocketMQTemplate rocketMQTemplate() {
            return mock(RocketMQTemplate.class);
        }

        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }

    @SpringBootConfiguration
    @Import(GXSendRocketMQServiceImpl.class)
    static class TwoHandlerConfig {
        @Bean
        RocketMQTemplate rocketMQTemplate() {
            return mock(RocketMQTemplate.class);
        }

        @Bean
        @org.springframework.core.annotation.Order(1)
        ThrowingHandler throwingHandler() {
            return new ThrowingHandler();
        }

        @Bean
        @org.springframework.core.annotation.Order(2)
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }
}
