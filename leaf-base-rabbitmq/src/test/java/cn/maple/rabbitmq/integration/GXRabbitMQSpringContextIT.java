package cn.maple.rabbitmq.integration;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.rabbitmq.config.GXRabbitMQConfig;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import cn.maple.rabbitmq.service.GXSendRabbitMQService;
import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.MDC;
import org.springframework.amqp.client.annotation.AmqpListener;
import org.springframework.amqp.core.AmqpAcknowledgment;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端验证：Spring 上下文装配 + AMQP 1.0 发送 + {@code @AmqpListener} 消费 + 显式 ack。
 *
 * <p>队列在上下文启动前通过管理 API 创建（AMQP 1.0 不会自动声明队列）。</p>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "RABBITMQ_HOST", matches = ".+")
@SpringBootTest(classes = GXRabbitMQSpringContextIT.TestApp.class,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "spring.cloud.bootstrap.import-check.enabled=false"
        })
class GXRabbitMQSpringContextIT {
    private static final String QUEUE_NAME = "maple.it.spring." + UUID.randomUUID();

    private static final GXAmqpManagementClient SETUP = GXIntegrationSupport.managementClient();

    static {
        SETUP.declareQueue(QUEUE_NAME, true, false, null);
    }

    @DynamicPropertySource
    static void amqpProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.amqp.host", GXIntegrationSupport::host);
        registry.add("spring.amqp.port", GXIntegrationSupport::port);
        registry.add("spring.amqp.username", GXIntegrationSupport::username);
        registry.add("spring.amqp.password", GXIntegrationSupport::password);
        registry.add("spring.amqp.virtual-host", GXIntegrationSupport::vhost);
        registry.add("spring.amqp.listener.initial-credits", () -> 10);
        registry.add("maple.framework.rabbitmq.management.url",
                () -> "http://" + GXIntegrationSupport.host() + ":" + GXIntegrationSupport.managementPort());
        registry.add("test.amqp.queue", () -> QUEUE_NAME);
    }

    @AfterAll
    static void tearDown() {
        SETUP.deleteQueue(QUEUE_NAME);
    }

    @Autowired
    private GXSendRabbitMQService sendService;

    @Autowired
    private RecordingListener recordingListener;

    @Test
    void shouldConsumeMessagePublishedThroughAmqp10() throws Exception {
        recordingListener.reset();
        MDC.put("X-B3-TraceId", "spring-it-trace");
        try {
            GXRabbitMQMessageReqDto request = GXRabbitMQMessageReqDto.builder()
                    .exchange("")
                    .routingKey(QUEUE_NAME)
                    .data(Dict.create().set("marker", "spring-context"))
                    .messageProperties(new MessageProperties())
                    .build();

            assertThat(sendService.sendNormalMessageAwait(request, Duration.ofSeconds(5))).isTrue();
        } finally {
            MDC.clear();
        }

        assertThat(recordingListener.await(20, TimeUnit.SECONDS)).isTrue();
        Message received = recordingListener.message.get();
        assertThat(received).isNotNull();
        assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).contains("spring-context");
        assertThat(received.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat((Object) received.getMessageProperties().getHeader("X-Trace-Id")).isEqualTo("spring-it-trace");
        assertThat((Object) received.getMessageProperties().getHeader("X-Source-Service")).isEqualTo("unknown");
        assertThat(JSONUtil.isTypeJSON(new String(received.getBody(), StandardCharsets.UTF_8))).isTrue();
    }

    @Test
    void shouldExposeManagementClientBackedTopology() {
        assertThat(sendService.queueExists(QUEUE_NAME)).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(GXRabbitMQConfig.class)
    @ComponentScan(basePackages = "cn.maple.rabbitmq.service")
    static class TestApp {
        @Bean
        GXRabbitMQProperties gxRabbitMQProperties() {
            return GXIntegrationSupport.properties();
        }

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    static class RecordingListener {
        private final AtomicReference<Message> message = new AtomicReference<>();

        private volatile CountDownLatch latch = new CountDownLatch(1);

        @AmqpListener(addresses = "/queues/${test.amqp.queue}")
        public void onMessage(Message received, AmqpAcknowledgment acknowledgment) {
            message.set(received);
            acknowledgment.acknowledge(AmqpAcknowledgment.Status.ACCEPT);
            latch.countDown();
        }

        void reset() {
            message.set(null);
            latch = new CountDownLatch(1);
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }
}
