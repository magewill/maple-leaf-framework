package cn.maple.canal.integration;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.config.GXCanalConfig;
import cn.maple.canal.config.GXCanalPropertiesConfig;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.listener.GXCanalRabbitMQListener;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.service.GXProcessCanalDataService;
import cn.maple.canal.service.impl.GXCanalMessageParseServiceImpl;
import cn.maple.canal.service.impl.rabbit.GXCanalRabbitMQFailureHandler;
import cn.maple.canal.service.support.GXCanalHandlerResolver;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import cn.maple.rabbitmq.config.GXRabbitMQConfig;
import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import cn.maple.rabbitmq.service.GXSendRabbitMQService;
import org.apache.qpid.protonj2.client.Connection;
import org.apache.qpid.protonj2.client.Delivery;
import org.apache.qpid.protonj2.client.Receiver;
import org.apache.qpid.protonj2.client.ReceiverOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.client.AmqpConnectionFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real RabbitMQ verification (AMQP 1.0), gated by {@code RABBITMQ_HOST}.
 *
 * <p>Without the variable the class is skipped, so the build never depends on a broker. When the
 * variable is present the test proves end-to-end consumption and dead letter routing.</p>
 */
@EnabledIfEnvironmentVariable(named = "RABBITMQ_HOST", matches = ".+")
@SpringBootTest(classes = GXCanalRabbitRealBrokerTest.BrokerConfig.class,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "spring.cloud.bootstrap.import-check.enabled=false"
        })
class GXCanalRabbitRealBrokerTest {
    private static final String VALID_PAYLOAD =
            "{\"database\":\"demo\",\"table\":\"sys_user\",\"type\":\"UPDATE\",\"data\":[{\"id\":\"1\"}]}";

    private static final String INVALID_PAYLOAD = "{not-a-json-payload";

    private static final String DEAD_LETTER_QUEUE = "canal.dlq";

    @Autowired
    private GXSendRabbitMQService sendRabbitMQService;

    @Autowired
    private AmqpConnectionFactory amqpConnectionFactory;

    @Autowired
    private CountingHandler countingHandler;

    @Autowired
    private GXCanalConsumeStatistics statistics;

    @DynamicPropertySource
    static void amqpProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.amqp.host", () -> env("RABBITMQ_HOST", "localhost"));
        registry.add("spring.amqp.port", () -> Integer.parseInt(env("RABBITMQ_PORT", "5672")));
        registry.add("spring.amqp.username", () -> env("RABBITMQ_USERNAME", "guest"));
        registry.add("spring.amqp.password", () -> env("RABBITMQ_PASSWORD", "guest"));
        registry.add("spring.amqp.virtual-host", () -> env("RABBITMQ_VHOST", "/"));
        registry.add("maple.framework.rabbitmq.management.url",
                () -> "http://" + env("RABBITMQ_HOST", "localhost") + ":"
                        + env("RABBITMQ_MANAGEMENT_PORT", "15672"));
    }

    @Test
    void shouldConsumeValidPayloadAndDeadLetterInvalidPayload() throws Exception {
        publish(VALID_PAYLOAD);
        assertTrue(countingHandler.awaitInvocation(20, TimeUnit.SECONDS),
                "the table handler must receive the published change event");
        assertTrue(statistics.successCount() >= 1);

        // An unparsable body fails inside the message converter, before the listener runs, so it is
        // rejected by GXAmqpListenerErrorHandler and dead lettered without touching the counters.
        publish(INVALID_PAYLOAD);
        assertNotNull(awaitDeadLetter(), "an unparsable payload must end up in the dead letter queue");
    }

    private void publish(String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        sendRabbitMQService.sendRawMessage("exchange.fanout.canal", "canal.example.exchange.routingkey",
                payload, properties, null);
    }

    private Object awaitDeadLetter() throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            String received = receiveDeadLetter();
            if (received != null) {
                return received;
            }
            Thread.sleep(500L);
        }
        return null;
    }

    private String receiveDeadLetter() {
        Connection connection = amqpConnectionFactory.getConnection();
        try (Receiver receiver = connection.openReceiver("/queues/" + DEAD_LETTER_QUEUE,
                new ReceiverOptions().creditWindow(1))) {
            receiver.openFuture().get(10, TimeUnit.SECONDS);
            Delivery delivery = receiver.receive(500L, TimeUnit.MILLISECONDS);
            if (delivery == null) {
                return null;
            }
            String body = new String((byte[]) delivery.message().body(), StandardCharsets.UTF_8);
            delivery.accept();
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableConfigurationProperties
    @ComponentScan(basePackages = "cn.maple.rabbitmq.service")
    @Import({GXRabbitMQConfig.class, GXCanalPropertiesConfig.class, GXCanalConfig.class,
            GXCanalMessageParseServiceImpl.class, GXCanalHandlerResolver.class, GXCanalConsumeStatistics.class,
            GXCanalLogThrottle.class, GXCanalRabbitMQFailureHandler.class, GXCanalRabbitMQListener.class})
    static class BrokerConfig {
        @Bean
        GXRabbitMQProperties gxRabbitMQProperties() {
            GXRabbitMQProperties properties = new GXRabbitMQProperties();
            properties.setHost(env("RABBITMQ_HOST", "localhost"));
            properties.setPort(Integer.parseInt(env("RABBITMQ_PORT", "5672")));
            properties.setUsername(env("RABBITMQ_USERNAME", "guest"));
            properties.setPassword(env("RABBITMQ_PASSWORD", "guest"));
            properties.setVirtualHost(env("RABBITMQ_VHOST", "/"));
            return properties;
        }

        @Bean("demoSysUserService")
        CountingHandler demoSysUserService() {
            return new CountingHandler();
        }
    }

    static class CountingHandler implements GXProcessCanalDataService {
        private final CountDownLatch invoked = new CountDownLatch(1);

        boolean awaitInvocation(long timeout, TimeUnit unit) throws InterruptedException {
            return invoked.await(timeout, unit);
        }

        @Override
        public Dict processUpdate(GXCanalDataDto canalData, Dict param) {
            invoked.countDown();
            return Dict.create().set("status", "success");
        }

        @Override
        public Dict processInsert(GXCanalDataDto canalData, Dict param) {
            invoked.countDown();
            return Dict.create().set("status", "success");
        }

        @Override
        public Dict processDelete(GXCanalDataDto canalData, Dict param) {
            invoked.countDown();
            return Dict.create().set("status", "success");
        }
    }
}
