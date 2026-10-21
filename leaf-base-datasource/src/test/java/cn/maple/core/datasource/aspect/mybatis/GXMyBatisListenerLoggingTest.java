package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerConfigResolver;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.enums.GXMyBatisListenerDegradeReasonEnums;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

/**
 * Asserts the structured log contract required by US4/AC1: delivery failures, degrade events and slow
 * callbacks must all carry {@code operationId}, {@code eventType}, {@code mode} and a reason.
 */
class GXMyBatisListenerLoggingTest {
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;
    private Logger publisherLogger;
    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        appender = new AbstractAppender("listener-log-capture", null,
                PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        publisherLogger = (Logger) LogManager.getLogger(GXMyBatisListenerEventPublisher.class);
        previousLevel = publisherLogger.getLevel();
        publisherLogger.setLevel(Level.WARN);
        publisherLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        publisherLogger.removeAppender(appender);
        publisherLogger.setLevel(previousLevel);
        appender.stop();
        messages.clear();
    }

    @Test
    void deliveryFailureLogCarriesOperationIdTypeModeAndReason() throws Exception {
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = LogMapper.class.getMethod("insert", Object.class);

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any()))
                    .thenThrow(new IllegalStateException("listener boom"));
            publisher.publish(new LogMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                    Dict.create().set("id", 1));
        }

        String failureLog = firstMessageContaining("delivery failed");
        assertTrue(failureLog.contains("operationId="), failureLog);
        assertTrue(failureLog.contains("eventType=sync_save_entity"), failureLog);
        assertTrue(failureLog.contains("mode=sync"), failureLog);
        assertTrue(failureLog.contains("reason=listener boom"), failureLog);
    }

    @Test
    void degradeLogCarriesOperationIdTypeModeAndReasons() throws Exception {
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = LogMapper.class.getMethod("insert", Object.class);
        Dict source = Dict.create()
                .set("id", 1)
                .set("conditionLossless", Boolean.FALSE)
                .set("degradeReasons", List.of(GXMyBatisListenerDegradeReasonEnums.OR_EXPRESSION));

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            publisher.publish(new LogMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY, source);
        }

        String degradeLog = firstMessageContaining("payload degraded");
        assertTrue(degradeLog.contains("operationId="), degradeLog);
        assertTrue(degradeLog.contains("eventType=sync_save_entity"), degradeLog);
        assertTrue(degradeLog.contains("mode=sync"), degradeLog);
        assertTrue(degradeLog.contains("reasons="), degradeLog);
        assertTrue(degradeLog.contains("OR_EXPRESSION"), degradeLog);
    }

    @Test
    void slowCallbackLogCarriesOperationIdTypeAndMode() throws Exception {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setSlowCallbackThresholdMs(1L);
        GXMyBatisListenerEventPublisher publisher = publisher(properties);
        Method method = LogMapper.class.getMethod("insert", Object.class);

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                Thread.sleep(10L);
                return null;
            });
            publisher.publish(new LogMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                    Dict.create().set("id", 1));
        }

        String slowLog = firstMessageContaining("Slow MyBatis listener callback");
        assertTrue(slowLog.contains("operationId="), slowLog);
        assertTrue(slowLog.contains("eventType=sync_save_entity"), slowLog);
        assertTrue(slowLog.contains("mode=sync"), slowLog);
    }

    private String firstMessageContaining(String token) {
        return messages.stream()
                .filter(message -> message.contains(token))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log message containing '" + token + "': " + messages));
    }

    private GXMyBatisListenerEventPublisher publisher(GXMyBatisListenerProperties properties) {
        GXMyBatisListenerConfigResolver resolver = new GXMyBatisListenerConfigResolver(properties);
        return new GXMyBatisListenerEventPublisher(resolver, properties, new StandardEnvironment());
    }

    @GXMyBatisListener(listenerClazz = LogListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface LogMapper {
        Object insert(Object entity);
    }

    private static class LogMapperImpl implements LogMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class LogListener implements GXMybatisListenerService<Object> {
    }
}
