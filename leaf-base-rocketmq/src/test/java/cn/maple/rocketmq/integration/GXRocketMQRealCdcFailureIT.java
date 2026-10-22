package cn.maple.rocketmq.integration;

import cn.maple.rocketmq.dispatcher.GXCdcDispatchStatistics;
import cn.maple.rocketmq.dispatcher.GXCdcMessageDispatcher;
import cn.maple.rocketmq.dispatcher.impl.GXCdcMessageDispatcherImpl;
import cn.maple.rocketmq.dto.inner.GXCdcEvent;
import cn.maple.rocketmq.exception.GXCdcDispatchException;
import cn.maple.rocketmq.listener.GXCdcEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Exercises CDC failure handling with messages that really travel through RocketMQ.
 *
 * <p>Each run publishes its own batch under a unique tag and consumes only that tag, so
 * historical messages on the shared topic do not affect the assertions.</p>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_NAME_SERVER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_SECRET_KEY", matches = ".+")
class GXRocketMQRealCdcFailureIT {
    private static final String TOPIC = "cdc-failure-verify-topic";

    private static final String ILLEGAL_JSON = "{\"op\":\"c\",\"after\":{\"id\":1}";

    private static final String UNKNOWN_OP = "{\"op\":\"x\",\"after\":{\"id\":2}}";

    private static final String VALID_CREATE = "{\"before\":null,\"after\":{\"id\":3,\"username\":\"bot\"},"
            + "\"source\":{\"db\":\"test\",\"table\":\"user\"},\"op\":\"c\",\"ts_ms\":1792664000000}";

    private static final String VALID_UPDATE = "{\"before\":{\"id\":3,\"username\":\"bot\"},"
            + "\"after\":{\"id\":3,\"username\":\"bot2\"},\"source\":{\"db\":\"test\",\"table\":\"user\"},"
            + "\"op\":\"u\",\"ts_ms\":1792664001000}";

    @org.junit.jupiter.api.BeforeAll
    static void resetEvidence() throws Exception {
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target", "cdc-failure-evidence.txt"));
    }

    @Test
    void poisonAndUnknownOperationDoNotBlockFollowingRealMessages() throws Exception {
        String tag = "f" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        publish(tag, ILLEGAL_JSON, UNKNOWN_OP, VALID_CREATE, VALID_UPDATE);

        List<String> bodies = consumeByTag(tag, 4, 20);
        assertThat(bodies).as("all published failure-probe messages must be consumed").hasSize(4);

        RecordingListener listener = new RecordingListener();
        GXCdcDispatchStatistics statistics = newStatistics();
        GXCdcMessageDispatcher dispatcher = newDispatcher(listener, statistics);

        for (String body : bodies) {
            assertThatCode(() -> dispatcher.dispatch(body, "user"))
                    .as("malformed or unsupported messages must not throw: %s", body)
                    .doesNotThrowAnyException();
        }

        assertThat(statistics.parseFailureCount()).isEqualTo(1);
        assertThat(statistics.unknownOperationCount()).isEqualTo(1);
        assertThat(statistics.successCount()).isEqualTo(2);
        assertThat(listener.events).hasSize(2);
        assertThat(listener.events).containsExactlyInAnyOrder(
                "CREATE:bot", "UPDATE:bot2");

        writeEvidence("poison-and-unknown-op",
                "consumed=" + bodies.size()
                        + " parseFailure=" + statistics.parseFailureCount()
                        + " unknownOp=" + statistics.unknownOperationCount()
                        + " success=" + statistics.successCount()
                        + " listenerEvents=" + listener.events);
    }

    @Test
    void listenerFailureOnRealMessageIsAggregatedAndOtherListenersStillRun() throws Exception {
        String tag = "f" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        publish(tag, VALID_CREATE);

        List<String> bodies = consumeByTag(tag, 1, 20);
        assertThat(bodies).hasSize(1);
        String realPayload = bodies.get(0);

        RecordingListener recording = new RecordingListener();
        ThrowingListener throwing = new ThrowingListener();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
                Map.of("aThrowingListener", throwing, "bRecordingListener", recording));
        GXCdcDispatchStatistics statistics = newStatistics();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.cdc.active-status", "enabled");
        GXCdcMessageDispatcher dispatcher = new GXCdcMessageDispatcherImpl(
                environment,
                beanFactory.getBeanProvider(GXCdcEventListener.class),
                statistics,
                beanFactory.getBeanProvider(RocketMQTemplate.class));

        GXCdcDispatchException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> dispatcher.dispatch(realPayload, "user"), GXCdcDispatchException.class);
        assertThat(failure).isNotNull();
        assertThat(failure.getSuppressed()).hasSize(1);

        assertThat(recording.events)
                .as("the second listener must still receive the real event")
                .containsExactly("CREATE:bot");
        assertThat(statistics.listenerFailureCount()).isEqualTo(1);
        assertThat(statistics.successCount()).isZero();

        writeEvidence("listener-exception",
                "suppressed=" + failure.getSuppressed().length
                        + " listenerFailure=" + statistics.listenerFailureCount()
                        + " success=" + statistics.successCount()
                        + " otherListenerEvents=" + recording.events);
    }

    @Test
    void listenerFailureTriggersBrokerRedeliveryForTheSameRealMessage() throws Exception {
        String tag = "f" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        publish(tag, VALID_CREATE);

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
                Map.of("throwingListener", new ThrowingListener()));
        GXCdcDispatchStatistics statistics = newStatistics();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.cdc.active-status", "enabled");
        GXCdcMessageDispatcher dispatcher = new GXCdcMessageDispatcherImpl(environment,
                beanFactory.getBeanProvider(GXCdcEventListener.class),
                statistics,
                beanFactory.getBeanProvider(RocketMQTemplate.class));

        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch twoAttempts = new CountDownLatch(2);
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("cdc-retry-consumer-" + tag,
                new AclClientRPCHook(new SessionCredentials(System.getenv("ROCKETMQ_ACCESS_KEY"),
                        System.getenv("ROCKETMQ_SECRET_KEY"))));
        consumer.setNamesrvAddr(System.getenv("ROCKETMQ_NAME_SERVER"));
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setMaxReconsumeTimes(2);
        consumer.subscribe(TOPIC, tag);
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (var message : messages) {
                attempts.incrementAndGet();
                try {
                    dispatcher.dispatch(new String(message.getBody(), StandardCharsets.UTF_8), "user");
                } catch (GXCdcDispatchException e) {
                    context.setDelayLevelWhenNextConsume(1);
                    twoAttempts.countDown();
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                twoAttempts.countDown();
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        try {
            assertThat(twoAttempts.await(25, TimeUnit.SECONDS))
                    .as("the broker must redeliver the message after a listener failure")
                    .isTrue();
        } finally {
            consumer.shutdown();
        }

        assertThat(attempts.get())
                .as("same message must be delivered more than once (at-least-once)")
                .isGreaterThanOrEqualTo(2);
        assertThat(statistics.listenerFailureCount()).isEqualTo(attempts.get());

        writeEvidence("broker-redelivery",
                "attempts=" + attempts.get()
                        + " listenerFailure=" + statistics.listenerFailureCount());
    }

    private void writeEvidence(String section, String detail) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("target", "cdc-failure-evidence.txt"),
                    "[" + section + "] " + detail + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to write CDC failure evidence", e);
        }
    }

    private void publish(String tag, String... payloads) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("cdc-failure-producer-" + tag,
                new AclClientRPCHook(new SessionCredentials(System.getenv("ROCKETMQ_ACCESS_KEY"),
                        System.getenv("ROCKETMQ_SECRET_KEY"))));
        producer.setNamesrvAddr(System.getenv("ROCKETMQ_NAME_SERVER"));
        producer.setSendMsgTimeout(5000);
        producer.start();
        try {
            for (String payload : payloads) {
                producer.send(new Message(TOPIC, tag, payload.getBytes(StandardCharsets.UTF_8)));
            }
        } finally {
            producer.shutdown();
        }
    }

    private List<String> consumeByTag(String tag, int expected, int timeoutSeconds) throws Exception {
        List<String> bodies = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(expected);
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("cdc-failure-consumer-" + tag,
                new AclClientRPCHook(new SessionCredentials(System.getenv("ROCKETMQ_ACCESS_KEY"),
                        System.getenv("ROCKETMQ_SECRET_KEY"))));
        consumer.setNamesrvAddr(System.getenv("ROCKETMQ_NAME_SERVER"));
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe(TOPIC, tag);
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (var message : messages) {
                bodies.add(new String(message.getBody(), StandardCharsets.UTF_8));
                latch.countDown();
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        try {
            latch.await(timeoutSeconds, TimeUnit.SECONDS);
            return bodies;
        } finally {
            consumer.shutdown();
        }
    }

    private GXCdcDispatchStatistics newStatistics() {
        return new GXCdcDispatchStatistics(new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class));
    }

    private GXCdcMessageDispatcher newDispatcher(RecordingListener listener, GXCdcDispatchStatistics statistics) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("listener", listener));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.cdc.active-status", "enabled");
        return new GXCdcMessageDispatcherImpl(environment,
                beanFactory.getBeanProvider(GXCdcEventListener.class),
                statistics,
                beanFactory.getBeanProvider(RocketMQTemplate.class));
    }

    static class RecordingListener implements GXCdcEventListener {
        private final List<String> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public boolean supports(GXCdcEvent event) {
            return "user".equals(event.getBizType());
        }

        @Override
        public void onCreate(GXCdcEvent event) {
            events.add("CREATE:" + event.getAfter().getStr("username"));
        }

        @Override
        public void onUpdate(GXCdcEvent event) {
            events.add("UPDATE:" + event.getAfter().getStr("username"));
        }

        @Override
        public void onDelete(GXCdcEvent event) {
            events.add("DELETE");
        }
    }

    static class ThrowingListener implements GXCdcEventListener {
        @Override
        public boolean supports(GXCdcEvent event) {
            return "user".equals(event.getBizType());
        }

        @Override
        public void onCreate(GXCdcEvent event) {
            throw new IllegalStateException("simulated business failure");
        }

        @Override
        public void onUpdate(GXCdcEvent event) {
            throw new IllegalStateException("simulated business failure");
        }
    }
}
