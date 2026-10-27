package cn.maple.canal.config;

import cn.maple.canal.properties.GXCanalProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXCanalConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GXCanalConfig.class, Config.class);

    @Test
    void shouldCreateDurableExchangeWithoutAutoDelete() {
        contextRunner.run(context -> {
            FanoutExchange exchange = context.getBean("fanoutExchange", FanoutExchange.class);

            assertEquals("exchange.fanout.canal", exchange.getName());
            assertTrue(exchange.isDurable());
            assertFalse(exchange.isAutoDelete());
        });
    }

    @Test
    void shouldCreateQueueAndBindingFromProperties() {
        contextRunner.run(context -> {
            Queue queue = context.getBean("canalQueue", Queue.class);
            Binding binding = context.getBean("binding", Binding.class);

            assertEquals("canalQueue", queue.getName());
            assertTrue(queue.isDurable());
            assertEquals("exchange.fanout.canal", binding.getExchange());
            assertEquals("canal.example.exchange.routingkey", binding.getRoutingKey());
        });
    }

    @Test
    void shouldDeclareDeadLetterQueueWithMatchingArguments() {
        contextRunner.run(context -> {
            Queue queue = context.getBean("canalQueue", Queue.class);
            Queue deadLetterQueue = context.getBean("canalDeadLetterQueue", Queue.class);
            DirectExchange deadLetterExchange = context.getBean("canalDeadLetterExchange", DirectExchange.class);
            Binding deadLetterBinding = context.getBean("canalDeadLetterBinding", Binding.class);

            assertEquals("canal.dlq", deadLetterQueue.getName());
            assertTrue(deadLetterQueue.isDurable());
            assertTrue(deadLetterExchange.isDurable());
            assertEquals("canal.dlx", deadLetterExchange.getName());
            assertEquals("canal.dlq", deadLetterBinding.getDestination());
            assertEquals("canal.dead", queue.getArguments().get("x-dead-letter-routing-key").toString());
        });
    }

    @Test
    void shouldNotDeclareInfrastructureWhenConsumerDisabled() {
        contextRunner.withPropertyValues("canal.consumer-enabled=false").run(context -> {
            assertEquals(0, context.getBeanNamesForType(Queue.class).length);
            assertEquals(0, context.getBeanNamesForType(Exchange.class).length);
            assertEquals(0, context.getBeanNamesForType(Binding.class).length);
        });
    }

    @Test
    void shouldNotDeclareInfrastructureWhenRocketMqSelected() {
        contextRunner.withPropertyValues("canal.mq-type=rocketmq").run(context -> {
            assertEquals(0, context.getBeanNamesForType(Queue.class).length);
            assertEquals(0, context.getBeanNamesForType(Exchange.class).length);
        });
    }

    @Test
    void shouldYieldToDownstreamQueueBean() {
        contextRunner.withUserConfiguration(CustomQueueConfig.class).run(context -> {
            assertNotNull(context.getBean("customCanalQueue", Queue.class));
            assertEquals("custom-queue", context.getBean("customCanalQueue", Queue.class).getName());
        });
    }

    @Configuration
    static class Config {
        @Bean
        GXCanalProperties canalProperties() {
            return new GXCanalProperties();
        }
    }

    @Configuration
    static class CustomQueueConfig {
        @Bean
        Queue customCanalQueue() {
            return new Queue("custom-queue");
        }
    }
}
