package cn.maple.rocketmq.integration;

import cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto;
import cn.maple.rocketmq.service.GXSendRocketMQService;
import cn.maple.rocketmq.service.impl.GXSendRocketMQServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real broker integration test.
 *
 * <p>Skipped unless {@code ROCKETMQ_NAME_SERVER}, {@code ROCKETMQ_ACCESS_KEY} and
 * {@code ROCKETMQ_SECRET_KEY} are provided as environment variables. Credentials are never
 * hardcoded, so the class is safe to keep in the repository.</p>
 */
@Slf4j
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_NAME_SERVER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ROCKETMQ_SECRET_KEY", matches = ".+")
@SpringBootTest(
        classes = GXRocketMQRealBrokerIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.config.import-check.enabled=false",
                "spring.profiles.active=default",
                "rocketmq.name-server=${ROCKETMQ_NAME_SERVER}",
                "rocketmq.producer.group=leaf-rocketmq-audit-it-group",
                "rocketmq.producer.access-key=${ROCKETMQ_ACCESS_KEY}",
                "rocketmq.producer.secret-key=${ROCKETMQ_SECRET_KEY}",
                "rocketmq.producer.send-message-timeout=3000",
                "logging.level.org.apache.rocketmq=WARN",
                "logging.level.cn.maple.rocketmq.service.impl=OFF"
        }
)
class GXRocketMQRealBrokerIT {
    private static final String TOPIC = "leaf-rocketmq-audit-it-topic";

    @Resource
    private GXSendRocketMQService sendRocketMQService;

    @Test
    void sendsEveryMessageKindAgainstRealBroker() {
        assertThat(sendRocketMQService.syncSend(messageReqDto())).isTrue();

        GXRocketMQMessageReqDto delay = messageReqDto();
        delay.setDeliverTime(5);
        assertThat(sendRocketMQService.sendDelayMessage(delay)).isNotBlank();

        assertThat(sendRocketMQService.sendAsync(messageReqDto())).isTrue();
        assertThat(sendRocketMQService.sendOneway(messageReqDto())).isTrue();
        sendRocketMQService.sendNormalMessage(messageReqDto());
    }

    @Test
    void rejectsWrongSecretWhenBrokerEnablesAcl() throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer("leaf-rocketmq-audit-it-bad-group",
                new AclClientRPCHook(new SessionCredentials(System.getenv("ROCKETMQ_ACCESS_KEY"),
                        "wrong-" + UUID.randomUUID())));
        producer.setNamesrvAddr(System.getenv("ROCKETMQ_NAME_SERVER"));
        producer.setSendMsgTimeout(3000);
        producer.start();
        try {
            Message message = new Message(TOPIC, "acl", "acl-check".getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> producer.send(message))
                    .isInstanceOf(MQClientException.class)
                    .hasMessageContaining("failed");
        } finally {
            producer.shutdown();
        }
    }

    @Test
    void failsThroughServiceWhenSecretIsWrong() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(WrongCredentialConfig.class)
                .withPropertyValues(
                        "spring.cloud.nacos.config.import-check.enabled=false",
                        "rocketmq.name-server=" + System.getenv("ROCKETMQ_NAME_SERVER"),
                        "rocketmq.producer.group=leaf-rocketmq-audit-it-bad-service-group",
                        "rocketmq.producer.access-key=" + System.getenv("ROCKETMQ_ACCESS_KEY"),
                        "rocketmq.producer.secret-key=wrong-" + UUID.randomUUID(),
                        "rocketmq.producer.send-message-timeout=3000",
                        "logging.level.org.apache.rocketmq=OFF")
                .run(context -> {
                    GXSendRocketMQService service = context.getBean(GXSendRocketMQService.class);

                    assertThatThrownBy(() -> service.syncSend(messageReqDto()))
                            .isInstanceOf(cn.maple.core.framework.exception.GXBusinessException.class)
                            .hasMessageContaining("Failed to send sync message")
                            .satisfies(thrown -> assertThat(thrown.getCause())
                                    .isInstanceOf(org.springframework.messaging.MessagingException.class)
                                    .hasMessageContaining("Send [3] times, still failed"));
                });
    }

    @Test
    void samplesSequentialAndConcurrentLatency() throws Exception {
        for (int i = 0; i < 20; i++) {
            sendRocketMQService.syncSend(messageReqDto());
        }

        List<Long> sequential = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            long start = System.nanoTime();
            sendRocketMQService.syncSend(messageReqDto());
            sequential.add((System.nanoTime() - start) / 1000);
        }

        int threads = 8;
        int perThread = 25;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        List<Long> concurrent = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                try {
                    startGate.await(5, TimeUnit.SECONDS);
                    for (int i = 0; i < perThread; i++) {
                        long start = System.nanoTime();
                        sendRocketMQService.syncSend(messageReqDto());
                        concurrent.add((System.nanoTime() - start) / 1000);
                    }
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    doneGate.countDown();
                }
            }, "audit-sender-" + t);
            worker.start();
        }
        long concurrentStart = System.nanoTime();
        startGate.countDown();
        assertThat(doneGate.await(180, TimeUnit.SECONDS)).isTrue();
        long elapsedMicros = (System.nanoTime() - concurrentStart) / 1000;

        assertThat(errors).isEmpty();
        assertThat(concurrent).hasSize(threads * perThread);

        log.info("=== SAMPLING sequential {}", describe(sequential));
        log.info("=== SAMPLING concurrent {} totalMicros={} throughput={}/s",
                describe(concurrent), elapsedMicros, threads * perThread * 1_000_000L / elapsedMicros);
    }

    private String describe(List<Long> costs) {
        List<Long> sorted = new ArrayList<>(costs);
        Collections.sort(sorted);
        long sum = sorted.stream().mapToLong(Long::longValue).sum();
        return "count=" + sorted.size()
                + " avgUs=" + (sum / sorted.size())
                + " p50Us=" + sorted.get(sorted.size() / 2)
                + " p95Us=" + sorted.get((int) (sorted.size() * 0.95))
                + " maxUs=" + sorted.get(sorted.size() - 1);
    }

    private GXRocketMQMessageReqDto messageReqDto() {
        GXRocketMQMessageReqDto dto = new GXRocketMQMessageReqDto();
        dto.setTopic(TOPIC);
        dto.setTag("it");
        dto.setMessageKey("it-" + UUID.randomUUID());
        dto.setBody("{\"probe\":\"" + UUID.randomUUID() + "\"}");
        return dto;
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration(org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration.class)
    @Import(GXSendRocketMQServiceImpl.class)
    static class TestApplication {
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration(org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration.class)
    @Import(GXSendRocketMQServiceImpl.class)
    static class WrongCredentialConfig {
    }
}
