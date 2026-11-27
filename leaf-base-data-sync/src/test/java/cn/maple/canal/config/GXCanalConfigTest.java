package cn.maple.canal.config;

import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.rabbitmq.service.GXSendRabbitMQService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AMQP 1.0 has no in-protocol topology declaration, so the configuration must drive the declarations
 * through {@link GXSendRabbitMQService}.
 */
class GXCanalConfigTest {
    private final GXSendRabbitMQService sendRabbitMQService = Mockito.mock(GXSendRabbitMQService.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GXCanalConfig.class)
            .withBean(GXCanalProperties.class, GXCanalProperties::new)
            .withBean(GXSendRabbitMQService.class, () -> sendRabbitMQService);

    @Test
    void shouldDeclareFanoutExchangeQueueAndBinding() {
        contextRunner.run(context -> {
            GXCanalConfig.CanalRabbitTopology topology =
                    context.getBean(GXCanalConfig.CanalRabbitTopology.class);

            assertEquals("canalQueue", topology.queue());
            assertEquals("exchange.fanout.canal", topology.exchange());
            assertEquals("canal.example.exchange.routingkey", topology.routingKey());

            ArgumentCaptor<AbstractExchange> exchangeCaptor = ArgumentCaptor.forClass(AbstractExchange.class);
            Mockito.verify(sendRabbitMQService).setupMessageChannel(
                    Mockito.eq("canalQueue"), Mockito.eq(true), Mockito.eq(false), Mockito.eq(false),
                    Mockito.anyMap(), exchangeCaptor.capture(),
                    Mockito.eq("canal.example.exchange.routingkey"));
            assertTrue(exchangeCaptor.getValue() instanceof FanoutExchange);
            assertEquals("exchange.fanout.canal", exchangeCaptor.getValue().getName());
            assertTrue(exchangeCaptor.getValue().isDurable());
            assertFalse(exchangeCaptor.getValue().isAutoDelete());
        });
    }

    @Test
    void shouldDeclareDeadLetterQueueWithMatchingArguments() {
        contextRunner.run(context -> {
            ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
            Mockito.verify(sendRabbitMQService).setupMessageChannel(
                    Mockito.eq("canalQueue"), Mockito.eq(true), Mockito.eq(false), Mockito.eq(false),
                    argsCaptor.capture(), Mockito.any(AbstractExchange.class), Mockito.anyString());

            Map<String, Object> arguments = argsCaptor.getValue();
            assertEquals("canal.dlx", arguments.get("x-dead-letter-exchange"));
            assertEquals("canal.dead", arguments.get("x-dead-letter-routing-key"));

            Mockito.verify(sendRabbitMQService).createDurableQueue("canal.dlq");
            Mockito.verify(sendRabbitMQService).bindQueueToExchange("canal.dlq", "canal.dlx", "canal.dead");
        });
    }

    @Test
    void shouldNotDeclareInfrastructureWhenConsumerDisabled() {
        contextRunner.withPropertyValues("canal.consumer-enabled=false").run(context -> {
            assertEquals(0, context.getBeanNamesForType(GXCanalConfig.CanalRabbitTopology.class).length);
            Mockito.verifyNoInteractions(sendRabbitMQService);
        });
    }

    @Test
    void shouldNotDeclareInfrastructureWhenRocketMqSelected() {
        contextRunner.withPropertyValues("canal.mq-type=rocketmq").run(context -> {
            assertEquals(0, context.getBeanNamesForType(GXCanalConfig.CanalRabbitTopology.class).length);
            Mockito.verifyNoInteractions(sendRabbitMQService);
        });
    }

    @Test
    void shouldAllowDownstreamTopologyBean() {
        contextRunner.withUserConfiguration(CustomTopologyConfig.class).run(context -> {
            assertNotNull(context.getBean("customCanalRabbitTopology", GXCanalConfig.CanalRabbitTopology.class));
            assertEquals("custom-queue",
                    context.getBean("customCanalRabbitTopology", GXCanalConfig.CanalRabbitTopology.class).queue());
            assertNotNull(context.getBean("canalRabbitTopology", GXCanalConfig.CanalRabbitTopology.class));
        });
    }

    @Configuration
    static class CustomTopologyConfig {
        @Bean
        GXCanalConfig.CanalRabbitTopology customCanalRabbitTopology() {
            return new GXCanalConfig.CanalRabbitTopology("custom-queue", "custom-exchange", "custom-key",
                    "custom.dlx", "custom.dlq");
        }
    }
}
