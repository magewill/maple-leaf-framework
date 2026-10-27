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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real RabbitMQ verification, gated by {@code RABBITMQ_HOST}.
 *
 * <p>Without the variable the class is skipped, so the build never depends on a broker. When the
 * variable is present the test proves end-to-end consumption and dead letter routing.</p>
 */
@EnabledIfEnvironmentVariable(named = "RABBITMQ_HOST", matches = ".+")
@SpringBootTest(classes = GXCanalRabbitRealBrokerTest.BrokerConfig.class)
class GXCanalRabbitRealBrokerTest {
    private static final String VALID_PAYLOAD =
            "{\"database\":\"demo\",\"table\":\"sys_user\",\"type\":\"UPDATE\",\"data\":[{\"id\":\"1\"}]}";

    private static final String INVALID_PAYLOAD = "{not-a-json-payload";

    private static final String DEAD_LETTER_QUEUE = "canal.dlq";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CountingHandler countingHandler;

    @Autowired
    private GXCanalConsumeStatistics statistics;

    @Test
    void shouldConsumeValidPayloadAndDeadLetterInvalidPayload() throws Exception {
        rabbitTemplate.convertAndSend("exchange.fanout.canal", "canal.example.exchange.routingkey", VALID_PAYLOAD);
        assertTrue(countingHandler.awaitInvocation(20, TimeUnit.SECONDS),
                "the table handler must receive the published change event");
        assertTrue(statistics.successCount() >= 1);

        rabbitTemplate.convertAndSend("exchange.fanout.canal", "canal.example.exchange.routingkey", INVALID_PAYLOAD);
        assertNotNull(awaitDeadLetter(), "an unparsable payload must end up in the dead letter queue");
        assertTrue(statistics.nonRetryableFailureCount() >= 1);
    }

    private Object awaitDeadLetter() throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            Object received = rabbitTemplate.receiveAndConvert(DEAD_LETTER_QUEUE);
            if (received != null) {
                return received;
            }
            Thread.sleep(500L);
        }
        return null;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableRabbit
    @EnableConfigurationProperties
    @Import({GXCanalPropertiesConfig.class, GXCanalConfig.class, GXCanalMessageParseServiceImpl.class,
            GXCanalHandlerResolver.class, GXCanalConsumeStatistics.class, GXCanalLogThrottle.class,
            GXCanalRabbitMQFailureHandler.class, GXCanalRabbitMQListener.class})
    static class BrokerConfig {
        @Bean
        ConnectionFactory connectionFactory() {
            CachingConnectionFactory factory = new CachingConnectionFactory(
                    System.getenv().getOrDefault("RABBITMQ_HOST", "localhost"),
                    Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672")));
            factory.setUsername(System.getenv().getOrDefault("RABBITMQ_USERNAME", "guest"));
            factory.setPassword(System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest"));
            factory.setVirtualHost(System.getenv().getOrDefault("RABBITMQ_VHOST", "/"));
            return factory;
        }

        @Bean
        RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
            return new RabbitTemplate(connectionFactory);
        }

        @Bean
        RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
            return new RabbitAdmin(connectionFactory);
        }

        @Bean
        SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
            SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
            factory.setConnectionFactory(connectionFactory);
            factory.setPrefetchCount(10);
            return factory;
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
