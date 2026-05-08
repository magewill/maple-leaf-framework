package cn.maple.canal.config;

import cn.maple.canal.properties.GXCanalProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXCanalConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GXCanalConfig.class, Config.class);

    @Test
    void shouldCreateDurableExchangeWithoutAutoDelete() {
        contextRunner.run(context -> {
            FanoutExchange exchange = (FanoutExchange) context.getBean(Exchange.class);

            assertEquals("exchange.fanout.canal", exchange.getName());
            assertTrue(exchange.isDurable());
            assertFalse(exchange.isAutoDelete());
        });
    }

    @Test
    void shouldCreateQueueAndBindingFromProperties() {
        contextRunner.run(context -> {
            Queue queue = context.getBean(Queue.class);
            Binding binding = context.getBean(Binding.class);

            assertEquals("canalQueue", queue.getName());
            assertEquals("exchange.fanout.canal", binding.getExchange());
            assertEquals("canal.example.exchange.routingkey", binding.getRoutingKey());
        });
    }

    @Configuration
    static class Config {
        @Bean
        GXCanalProperties canalProperties() {
            return new GXCanalProperties();
        }
    }
}
