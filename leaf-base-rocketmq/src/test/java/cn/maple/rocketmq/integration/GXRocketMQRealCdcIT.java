package cn.maple.rocketmq.integration;

import cn.maple.rocketmq.dispatcher.GXCdcDispatchStatistics;
import cn.maple.rocketmq.dispatcher.GXCdcMessageDispatcher;
import cn.maple.rocketmq.dispatcher.impl.GXCdcMessageDispatcherImpl;
import cn.maple.rocketmq.dto.inner.GXCdcEvent;
import cn.maple.rocketmq.dto.inner.GXCdcOperation;
import cn.maple.rocketmq.listener.GXCdcEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumes real Debezium CDC messages from RocketMQ and runs them through
 * {@link GXCdcMessageDispatcherImpl}.
 *
 * <p>Gated on {@code ROCKETMQ_NAME_SERVER}, {@code ROCKETMQ_ACCESS_KEY} and
 * {@code ROCKETMQ_SECRET_KEY}; the topic defaults to
 * {@code debezium-test-user-dev} and can be overridden with
 * {@code ROCKETMQ_CDC_TOPIC}.</p>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_NAME_SERVER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_SECRET_KEY", matches = ".+")
class GXRocketMQRealCdcIT {
    private static final String DEFAULT_TOPIC = "debezium-test-user-dev";

    @Test
    void dispatchesRealDebeziumEventsConsumedFromRocketMQ(TestReporter reporter) throws Exception {
        String topic = System.getenv().getOrDefault("ROCKETMQ_CDC_TOPIC", DEFAULT_TOPIC);
        List<String> bodies = consumeBodies(topic, 20);
        if (bodies.isEmpty()) {
            TimeUnit.SECONDS.sleep(3);
            bodies = consumeBodies(topic, 20);
        }

        assertThat(bodies)
                .as("expected at least one Debezium record on topic %s", topic)
                .isNotEmpty();
        assertThat(bodies.get(0)).contains("\"op\"").contains("\"source\"").contains("\"ts_ms\"");

        RecordingListener listener = new RecordingListener();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(Map.of("listener", listener));
        GXCdcDispatchStatistics statistics = new GXCdcDispatchStatistics(
                new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("maple.framework.cdc.active-status", "enabled");
        GXCdcMessageDispatcher dispatcher = new GXCdcMessageDispatcherImpl(
                environment,
                beanFactory.getBeanProvider(GXCdcEventListener.class),
                statistics,
                beanFactory.getBeanProvider(RocketMQTemplate.class));

        for (String body : bodies) {
            dispatcher.dispatch(body, "user");
        }

        reporter.publishEntry("CDC_VERIFY",
                "topic=" + topic
                        + " consumed=" + bodies.size()
                        + " dispatched=" + listener.operations.size()
                        + " parseFailure=" + statistics.parseFailureCount()
                        + " unknownOp=" + statistics.unknownOperationCount()
                        + " opHistogram=" + listener.histogram());
        reporter.publishEntry("CDC_VERIFY_SAMPLE", abbreviate(bodies.get(0)));

        java.util.Map<GXCdcOperation, String> samples = new java.util.LinkedHashMap<>();
        for (int i = 0; i < bodies.size(); i++) {
            samples.putIfAbsent(listener.operations.get(i), abbreviate(bodies.get(i)));
        }
        StringBuilder evidence = new StringBuilder()
                .append("topic=").append(topic)
                .append(System.lineSeparator()).append("consumed=").append(bodies.size())
                .append(System.lineSeparator()).append("dispatched=").append(listener.operations.size())
                .append(System.lineSeparator()).append("opHistogram=").append(listener.histogram())
                .append(System.lineSeparator()).append("parseFailure=").append(statistics.parseFailureCount())
                .append(System.lineSeparator()).append("unknownOp=").append(statistics.unknownOperationCount());
        samples.forEach((operation, payload) -> evidence.append(System.lineSeparator())
                .append("sample[").append(operation).append("]=").append(payload));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target", "cdc-verify-evidence.txt"),
                evidence.toString(), StandardCharsets.UTF_8);

        assertThat(statistics.parseFailureCount()).isZero();
        assertThat(statistics.unknownOperationCount()).isZero();
        assertThat(listener.operations)
                .as("every consumed record must be dispatched to the listener")
                .hasSize(bodies.size());
        assertThat(listener.operations).isNotEmpty().doesNotContainNull();
        assertThat(listener.usernames).hasSize(listener.operations.size());

        // Negative paths: a real consumer can also deliver garbage or an unsupported op.
        dispatcher.dispatch("not-json", "user");
        dispatcher.dispatch("{\"op\":\"x\"}", "user");
        assertThat(statistics.parseFailureCount()).isEqualTo(1);
        assertThat(statistics.unknownOperationCount()).isEqualTo(1);
        assertThat(listener.operations).hasSize(bodies.size());
    }

    private List<String> consumeBodies(String topic, int timeoutSeconds) throws Exception {
        List<String> bodies = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch firstRecord = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger();

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("cdc-verify-" + UUID.randomUUID(),
                new AclClientRPCHook(new SessionCredentials(System.getenv("ROCKETMQ_ACCESS_KEY"),
                        System.getenv("ROCKETMQ_SECRET_KEY"))));
        consumer.setNamesrvAddr(System.getenv("ROCKETMQ_NAME_SERVER"));
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (var message : messages) {
                bodies.add(new String(message.getBody(), StandardCharsets.UTF_8));
                received.incrementAndGet();
                firstRecord.countDown();
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        consumer.start();
        try {
            boolean gotOne = firstRecord.await(timeoutSeconds, TimeUnit.SECONDS);
            if (gotOne) {
                TimeUnit.SECONDS.sleep(3);
            }
            if (!gotOne) {
                bodies.clear();
            }
            return bodies;
        } finally {
            consumer.shutdown();
        }
    }

    private String abbreviate(String body) {
        return body.length() <= 600 ? body : body.substring(0, 600) + "...";
    }

    static class RecordingListener implements GXCdcEventListener {
        private final List<GXCdcOperation> operations = Collections.synchronizedList(new ArrayList<>());

        private final List<String> usernames = Collections.synchronizedList(new ArrayList<>());

        @Override
        public boolean supports(GXCdcEvent event) {
            return "user".equals(event.getBizType());
        }

        @Override
        public void onCreate(GXCdcEvent event) {
            record(GXCdcOperation.CREATE, event);
        }

        @Override
        public void onUpdate(GXCdcEvent event) {
            record(GXCdcOperation.UPDATE, event);
        }

        @Override
        public void onDelete(GXCdcEvent event) {
            record(GXCdcOperation.DELETE, event);
        }

        @Override
        public void onRead(GXCdcEvent event) {
            record(GXCdcOperation.READ, event);
        }

        private void record(GXCdcOperation operation, GXCdcEvent event) {
            operations.add(operation);
            usernames.add(event.getAfter().getStr("username"));
        }

        private String histogram() {
            Map<GXCdcOperation, Long> counts = operations.stream()
                    .collect(java.util.stream.Collectors.groupingBy(op -> op, java.util.stream.Collectors.counting()));
            return counts.toString();
        }
    }
}
