package cn.maple.rabbitmq.integration;

import cn.hutool.core.lang.Dict;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.service.impl.GXSendRabbitMQServiceImpl;
import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import cn.maple.rabbitmq.support.GXAmqpMessagePublisher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.MDC;
import org.springframework.amqp.client.SingleAmqpConnectionFactory;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Broker 验证（AMQP 1.0）。缺少 {@code RABBITMQ_HOST} 时自动跳过。
 *
 * <p>覆盖：管理 API 拓扑、默认交换机发布、交换机 + 路由键发布、不可路由结算、
 * 清空、删除、并发发送。</p>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "RABBITMQ_HOST", matches = ".+")
class GXRabbitMQRealBrokerIT {
    private static SingleAmqpConnectionFactory connectionFactory;

    private static GXAmqpMessagePublisher publisher;

    private static GXAmqpManagementClient managementClient;

    private static GXSendRabbitMQServiceImpl service;

    @BeforeAll
    static void setUp() {
        connectionFactory = GXIntegrationSupport.connectionFactory();
        managementClient = GXIntegrationSupport.managementClient();
        publisher = new GXAmqpMessagePublisher(connectionFactory);
        service = GXIntegrationSupport.service(publisher, managementClient);
    }

    @AfterAll
    static void tearDown() {
        if (publisher != null) {
            publisher.close();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldDeclareSendReceiveAndDeleteQueue() {
        String queueName = "maple.it." + UUID.randomUUID();
        try {
            Queue queue = service.createQueue(queueName, true, false, true, null);
            assertThat(queue.getName()).isEqualTo(queueName);
            assertThat(service.queueExists(queueName)).isTrue();

            GXIntegrationSupport.ConsumedMessage consumed = sendAndReceive(queueName, "order-created");

            assertThat(consumed).isNotNull();
            assertThat(new String(consumed.body(), StandardCharsets.UTF_8)).contains("order-created");
            assertThat(consumed.contentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
            assertThat(consumed.properties()).containsEntry("X-Trace-Id", "it-trace-order-created");
            assertThat(consumed.properties()).containsKey("X-Timestamp");
            assertThat(consumed.properties()).containsEntry("X-Source-Service", "unknown");
            assertThat(consumed.properties()).containsEntry("__TypeId__", "cn.hutool.core.lang.Dict");
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

            GXIntegrationSupport.ConsumedMessage consumed = sendAndReceive(queueName, "temp-message");

            assertThat(consumed).isNotNull();
            assertThat(new String(consumed.body(), StandardCharsets.UTF_8)).contains("temp-message");
        } finally {
            service.deleteQueue(queueName);
        }
    }

    @Test
    void shouldRouteThroughExchangeBinding() {
        String suffix = UUID.randomUUID().toString();
        String queueName = "maple.it.ex." + suffix;
        String exchangeName = "maple.it.exchange." + suffix;
        String routingKey = "order.created." + suffix;
        try {
            service.setupDurableMessageChannel(queueName, new DirectExchange(exchangeName), routingKey);

            GXRabbitMQMessageReqDto request = GXRabbitMQMessageReqDto.builder()
                    .exchange(exchangeName)
                    .routingKey(routingKey)
                    .data(Dict.create().set("marker", "exchange-path"))
                    .messageProperties(new MessageProperties())
                    .build();

            assertThat(service.sendNormalMessageAwait(request, Duration.ofSeconds(5))).isTrue();

            GXIntegrationSupport.ConsumedMessage consumed =
                    GXIntegrationSupport.receive(connectionFactory, queueName, 5_000L);
            assertThat(consumed).isNotNull();
            assertThat(new String(consumed.body(), StandardCharsets.UTF_8)).contains("exchange-path");
        } finally {
            service.deleteQueue(queueName);
        }
    }

    @Test
    void shouldReportUnroutableMessageAsReleased() {
        String exchangeName = "maple.it.unroutable." + UUID.randomUUID();
        try {
            service.createExchange(new DirectExchange(exchangeName));

            GXRabbitMQMessageReqDto request = GXRabbitMQMessageReqDto.builder()
                    .exchange(exchangeName)
                    .routingKey("no.binding." + UUID.randomUUID())
                    .data(Dict.create().set("marker", "unroutable"))
                    .messageProperties(new MessageProperties())
                    .build();

            assertThat(service.sendNormalMessageAwait(request, Duration.ofSeconds(5))).isFalse();
        } finally {
            managementClient.deleteExchange(exchangeName);
        }
    }

    @Test
    void shouldPurgeQueue() {
        String queueName = "maple.it.purge." + UUID.randomUUID();
        try {
            service.createQueue(queueName, true, false, false, null);
            sendAndReceive(queueName, "to-be-purged");

            assertThat(service.purgeQueue(queueName)).isTrue();
            assertThat(GXIntegrationSupport.receive(connectionFactory, queueName, 500L)).isNull();
        } finally {
            service.deleteQueue(queueName);
        }
    }

    @Test
    void shouldSendConcurrentlyWithoutCrossTalk() throws Exception {
        String queueName = "maple.it.concurrent." + UUID.randomUUID();
        int threads = 8;
        int perThread = 20;
        ExecutorService workers = Executors.newFixedThreadPool(threads);
        try {
            service.createQueue(queueName, true, false, false, null);
            List<Future<?>> futures = new ArrayList<>();
            for (int threadIndex = 0; threadIndex < threads; threadIndex++) {
                int index = threadIndex;
                futures.add(workers.submit(() -> {
                    for (int sequence = 0; sequence < perThread; sequence++) {
                        service.sendNormalMessage(GXRabbitMQMessageReqDto.builder()
                                .exchange("")
                                .routingKey(queueName)
                                .data(Dict.create().set("thread", index).set("sequence", sequence))
                                .messageProperties(new MessageProperties())
                                .build());
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            List<String> bodies = new ArrayList<>();
            for (int index = 0; index < threads * perThread; index++) {
                GXIntegrationSupport.ConsumedMessage consumed =
                        GXIntegrationSupport.receive(connectionFactory, queueName, 5_000L);
                if (consumed == null) {
                    break;
                }
                bodies.add(new String(consumed.body(), StandardCharsets.UTF_8));
            }
            assertThat(bodies).hasSize(threads * perThread);
        } finally {
            workers.shutdownNow();
            service.deleteQueue(queueName);
        }
    }

    private static GXIntegrationSupport.ConsumedMessage sendAndReceive(String queueName, String marker) {
        MDC.put("X-B3-TraceId", "it-trace-" + marker);
        try {
            GXRabbitMQMessageReqDto request = GXRabbitMQMessageReqDto.builder()
                    .exchange("")
                    .routingKey(queueName)
                    .data(Dict.create().set("marker", marker))
                    .messageProperties(new MessageProperties())
                    .build();
            assertThat(service.sendNormalMessageAwait(request, Duration.ofSeconds(5))).isTrue();
        } finally {
            MDC.clear();
        }
        return GXIntegrationSupport.receive(connectionFactory, queueName, 5_000L);
    }
}
