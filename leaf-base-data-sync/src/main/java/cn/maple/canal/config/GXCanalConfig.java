package cn.maple.canal.config;

import cn.maple.canal.properties.GXCanalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * RabbitMQ infrastructure used by Canal message consumption.
 *
 * <p>The main queue is declared with dead letter arguments, so rejected messages are routed to
 * {@code canal.dlx}/{@code canal.dlq} instead of being dropped or endlessly requeued.</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "canal", name = "mq-type", havingValue = "rabbitmq", matchIfMissing = true)
@ConditionalOnBooleanProperty(prefix = "canal", name = "consumer-enabled", havingValue = true, matchIfMissing = true)
public class GXCanalConfig {
    private static final int DEFAULT_PREFETCH = 250;

    private final GXCanalProperties canalProperties;

    public GXCanalConfig(GXCanalProperties canalProperties, Environment environment) {
        this.canalProperties = canalProperties;
        int prefetch = environment.getProperty("spring.rabbitmq.listener.simple.prefetch", Integer.class, DEFAULT_PREFETCH);
        int concurrency = parseConcurrency();
        log.info("Canal RabbitMQ consumer configured: queue={}, exchange={}, concurrency={}, prefetch={}, "
                        + "inFlightLimit={}, deadLetter={}/{}/{}",
                canalProperties.getCanalQueueName(), canalProperties.getExchangeName(), concurrency, prefetch,
                (long) concurrency * prefetch, canalProperties.getDeadLetter().getExchange(),
                canalProperties.getDeadLetter().getQueue(), canalProperties.getDeadLetter().getRoutingKey());
    }

    @Bean
    @ConditionalOnMissingBean(name = "binding")
    public Binding binding(@Qualifier("fanoutExchange") Exchange fanoutExchange,
                           @Qualifier("canalQueue") Queue canalQueue) {
        return BindingBuilder.bind(canalQueue).to(fanoutExchange).with(canalProperties.getRoutingKey()).noargs();
    }

    @Bean
    @ConditionalOnMissingBean(name = "fanoutExchange")
    public Exchange fanoutExchange() {
        return new FanoutExchange(canalProperties.getExchangeName(), true, false);
    }

    @Bean
    @ConditionalOnMissingBean(name = "canalQueue")
    public Queue canalQueue() {
        return QueueBuilder.durable(canalProperties.getCanalQueueName())
                .deadLetterExchange(canalProperties.getDeadLetter().getExchange())
                .deadLetterRoutingKey(canalProperties.getDeadLetter().getRoutingKey())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "canalDeadLetterExchange")
    public DirectExchange canalDeadLetterExchange() {
        return new DirectExchange(canalProperties.getDeadLetter().getExchange(), true, false);
    }

    @Bean
    @ConditionalOnMissingBean(name = "canalDeadLetterQueue")
    public Queue canalDeadLetterQueue() {
        return QueueBuilder.durable(canalProperties.getDeadLetter().getQueue()).build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "canalDeadLetterBinding")
    public Binding canalDeadLetterBinding(@Qualifier("canalDeadLetterExchange") DirectExchange deadLetterExchange,
                                          @Qualifier("canalDeadLetterQueue") Queue deadLetterQueue) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange)
                .with(canalProperties.getDeadLetter().getRoutingKey());
    }

    private int parseConcurrency() {
        try {
            return Integer.parseInt(canalProperties.getConcurrencyCount());
        } catch (NumberFormatException e) {
            log.warn("Invalid canal.concurrency-count, fallback to 1: value={}", canalProperties.getConcurrencyCount());
            return 1;
        }
    }
}
