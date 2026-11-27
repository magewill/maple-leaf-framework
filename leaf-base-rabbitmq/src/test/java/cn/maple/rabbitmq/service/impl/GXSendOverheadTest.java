package cn.maple.rabbitmq.service.impl;

import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import cn.maple.rabbitmq.support.GXAmqpMessagePublisher;
import cn.maple.rabbitmq.support.GXSendOutcome;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 发送与声明关键路径不允许输出 info 日志：结构指标可复算，避免日志成为吞吐瓶颈。
 */
@ExtendWith(MockitoExtension.class)
class GXSendOverheadTest {
    @Mock
    private GXAmqpMessagePublisher publisher;

    @Mock
    private GXAmqpManagementClient managementClient;

    private GXSendRabbitMQServiceImpl service;

    private Logger serviceLogger;

    private Level previousLevel;

    private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

    private AbstractAppender appender;

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(publisher, managementClient);
        org.mockito.Mockito.lenient().when(publisher.send(anyString(), any(Message.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(GXSendOutcome.ACCEPTED));
        serviceLogger = (Logger) LogManager.getLogger(GXSendRabbitMQServiceImpl.class);
        previousLevel = serviceLogger.getLevel();
        serviceLogger.setLevel(Level.DEBUG);

        appender = new AbstractAppender("rabbitmq-overhead-test", null,
                PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.removeAppender(appender);
        appender.stop();
        serviceLogger.setLevel(previousLevel);
        MDC.clear();
        service.shutdownAsyncExecutor();
    }

    @Test
    void shouldNotLogInfoOnSuccessfulSend() {
        service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());

        assertThat(events).noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.INFO));
    }

    @Test
    void shouldNotLogInfoOnQueueDeclaration() {
        service.createQueue("order.queue", true, false, false, null);

        assertThat(events).noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.INFO));
    }

    @Test
    void shouldNotLogInfoOnChannelSetup() {
        service.setupMessageChannel("order.queue", true, false, false, null,
                new DirectExchange("order.exchange"), "key");

        assertThat(events).noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.INFO));
    }

    @Test
    void shouldReuseTraceIdWithoutCreatingNewOne() {
        GXTraceIdContextUtils.putTraceId("existing-trace");

        service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());

        assertThat(GXTraceIdContextUtils.getNullableTraceId()).isEqualTo("existing-trace");
    }

    @Test
    void shouldStillSupportBuilderBasedQueueArguments() {
        Queue queue = QueueBuilder.durable("order.queue").build();

        assertThat(queue.isDurable()).isTrue();
        assertThat(service.createQueue("order.queue", queue.isDurable(), queue.isExclusive(),
                queue.isAutoDelete(), queue.getArguments())).isNotNull();
    }
}
