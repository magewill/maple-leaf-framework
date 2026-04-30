package cn.maple.core.framework.service;

import cn.maple.core.framework.config.aware.GXApplicationContextAware;
import cn.maple.core.framework.event.GXExceptionNotifyEvent;
import cn.maple.core.framework.exception.GXBusinessException;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest(classes = GXBotNotificationExceptionServiceSpringBootTest.NotificationTestConfig.class)
@TestPropertySource(properties = "bot.notification.exception=cn.maple.core.framework.exception.GXBusinessException")
class GXBotNotificationExceptionServiceSpringBootTest {
    @Resource
    private GXBotNotificationExceptionService service;

    @Resource
    private AtomicReference<GXExceptionNotifyEvent> eventReference;

    @Test
    void shouldPublishEventWhenConfiguredExceptionMatchesInSpringContext() {
        GXBusinessException exception = new GXBusinessException("biz error");

        service.botNotificationException(exception);

        GXExceptionNotifyEvent event = eventReference.get();
        assertNotNull(event);
        assertSame(exception, event.getSource().getThrowable());
    }

    @SpringBootConfiguration
    static class NotificationTestConfig {
        @Bean
        GXApplicationContextAware gxApplicationContextAware() {
            return new GXApplicationContextAware();
        }

        @Bean
        GXBotNotificationExceptionService gxBotNotificationExceptionService() {
            return new GXBotNotificationExceptionService() {
            };
        }

        @Bean
        AtomicReference<GXExceptionNotifyEvent> eventReference() {
            return new AtomicReference<>();
        }

        @Bean
        ApplicationListener<GXExceptionNotifyEvent> exceptionNotifyEventListener(AtomicReference<GXExceptionNotifyEvent> eventReference) {
            return eventReference::set;
        }
    }
}
