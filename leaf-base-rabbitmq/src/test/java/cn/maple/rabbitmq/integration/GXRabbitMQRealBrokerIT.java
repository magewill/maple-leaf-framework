package cn.maple.rabbitmq.integration;

import cn.hutool.core.lang.Dict;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.service.impl.GXSendRabbitMQServiceImpl;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Broker 验证。缺少 {@code RABBITMQ_HOST} 时自动跳过。
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "RABBITMQ_HOST", matches = ".+")
class GXRabbitMQRealBrokerIT {
    private static CachingConnectionFactory connectionFactory;

    private static RabbitAdmin rabbitAdmin;

    private static RabbitTemplate rabbitTemplate;

    private static GXSendRabbitMQServiceImpl service;

    @BeforeAll
    static void setUp() {
        String host = System.getenv("RABBITMQ_HOST");
        int port = Integer.parseInt(envOrDefault("RABBITMQ_PORT", "5672"));
        connectionFactory = new CachingConnectionFactory(host, port);
        connectionFactory.setUsername(envOrDefault("RABBITMQ_USERNAME", "guest"));
        connectionFactory.setPassword(envOrDefault("RABBITMQ_PASSWORD", "guest"));
        connectionFactory.setVirtualHost(envOrDefault("RABBITMQ_VHOST", "/"));
        rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        service = GXTestRabbitFixtures.newService(rabbitTemplate, rabbitAdmin);
    }

    @AfterAll
    static void tearDown() {
        if (service != null) {
            service.shutdownAsyncExecutor();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldDeclareSendReceiveAndDeleteQueue() {
        String queueName = "maple.it." + UUID.randomUUID();
        try {
            Queue queue = service.createQueue(queueName, false, false, true, null);
            assertThat(queue.getName()).isEqualTo(queueName);
            assertThat(service.queueExists(queueName)).isTrue();

            String body = sendAndReceive(queueName, "order-created");

            assertThat(body).contains("order-created");
        } finally {
            service.deleteQueue(queueName);
        }
        assertThat(service.queueExists(queueName)).isFalse();
    }

    @Test
    void shouldDeclareTemporaryQueueConsumable() {
        String queueName = "maple.it.temp." + UUID.randomUUID();
        try {
            Queue queue = service.createTemporaryQueue(queueName);
            assertThat(queue.isExclusive()).isFalse();
            assertThat(queue.isAutoDelete()).isTrue();

            String body = sendAndReceive(queueName, "temp-message");

            assertThat(body).contains("temp-message");
        } finally {
            service.deleteQueue(queueName);
        }
    }

    @Test
    void shouldPurgeQueue() {
        String queueName = "maple.it.purge." + UUID.randomUUID();
        try {
            service.createQueue(queueName, false, false, true, null);
            sendAndReceive(queueName, "to-be-purged");
            queueNameProducer(queueName);

            assertThat(service.purgeQueue(queueName)).isTrue();
            assertThat(rabbitTemplate.receive(queueName, 500L)).isNull();
        } finally {
            service.deleteQueue(queueName);
        }
    }

    private static void queueNameProducer(String queueName) {
        assertThat(queueName).isNotBlank();
    }

    private static String sendAndReceive(String queueName, String marker) {
        MDC.put("X-B3-TraceId", "it-trace-" + marker);
        try {
            GXRabbitMQMessageReqDto request = GXRabbitMQMessageReqDto.builder()
                    .exchange("")
                    .routingKey(queueName)
                    .data(Dict.create().set("marker", marker))
                    .messageProperties(new MessageProperties())
                    .build();
            service.sendNormalMessage(request);
        } finally {
            MDC.clear();
        }

        Message received = rabbitTemplate.receive(queueName, 5000L);
        assertThat(received).isNotNull();
        return new String(received.getBody(), StandardCharsets.UTF_8);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
