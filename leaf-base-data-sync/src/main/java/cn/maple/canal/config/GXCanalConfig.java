package cn.maple.canal.config;

import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.rabbitmq.service.GXSendRabbitMQService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RabbitMQ infrastructure used by Canal message consumption (AMQP 1.0).
 *
 * <p>AMQP 1.0 has no in-protocol topology declaration, so the topology is created eagerly through
 * {@link GXSendRabbitMQService} while the context is still starting. Doing it in a bean factory
 * method guarantees the declarations happen before the listener container starts.</p>
 *
 * <p>The main queue carries dead letter arguments, so rejected messages are routed to
 * {@code canal.dlx}/{@code canal.dlq} instead of being dropped or endlessly requeued.</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "canal", name = "mq-type", havingValue = "rabbitmq", matchIfMissing = true)
@ConditionalOnBooleanProperty(prefix = "canal", name = "consumer-enabled", havingValue = true, matchIfMissing = true)
public class GXCanalConfig {
    private static final int DEFAULT_CREDITS = 100;

    private final GXCanalProperties canalProperties;

    public GXCanalConfig(GXCanalProperties canalProperties, Environment environment) {
        this.canalProperties = canalProperties;
        int credits = environment.getProperty("spring.amqp.listener.initial-credits", Integer.class, DEFAULT_CREDITS);
        int concurrency = parseConcurrency();
        log.info("Canal RabbitMQ consumer configured: queue={}, exchange={}, concurrency={}, credits={}, "
                        + "inFlightLimit={}, deadLetter={}/{}/{}",
                canalProperties.getCanalQueueName(), canalProperties.getExchangeName(), concurrency, credits,
                (long) concurrency * credits, canalProperties.getDeadLetter().getExchange(),
                canalProperties.getDeadLetter().getQueue(), canalProperties.getDeadLetter().getRoutingKey());
    }

    /**
     * Declares the Canal topology and keeps the declared names for diagnostics and tests.
     *
     * @param sendRabbitMQService AMQP 1.0 send and topology service
     * @return the declared topology descriptor
     */
    @Bean
    @ConditionalOnMissingBean(name = "canalRabbitTopology")
    public CanalRabbitTopology canalRabbitTopology(GXSendRabbitMQService sendRabbitMQService) {
        String queueName = canalProperties.getCanalQueueName();
        String exchangeName = canalProperties.getExchangeName();
        String routingKey = canalProperties.getRoutingKey();
        String deadLetterExchange = canalProperties.getDeadLetter().getExchange();
        String deadLetterQueue = canalProperties.getDeadLetter().getQueue();
        String deadLetterRoutingKey = canalProperties.getDeadLetter().getRoutingKey();

        sendRabbitMQService.createDurableQueue(deadLetterQueue);
        sendRabbitMQService.createExchange(new DirectExchange(deadLetterExchange, true, false));
        sendRabbitMQService.bindQueueToExchange(deadLetterQueue, deadLetterExchange, deadLetterRoutingKey);

        Map<String, Object> queueArguments = new LinkedHashMap<>(2);
        queueArguments.put("x-dead-letter-exchange", deadLetterExchange);
        queueArguments.put("x-dead-letter-routing-key", deadLetterRoutingKey);
        sendRabbitMQService.setupMessageChannel(queueName, true, false, false, queueArguments,
                new FanoutExchange(exchangeName, true, false), routingKey);

        return new CanalRabbitTopology(queueName, exchangeName, routingKey, deadLetterExchange, deadLetterQueue);
    }

    private int parseConcurrency() {
        try {
            return Integer.parseInt(canalProperties.getConcurrencyCount());
        } catch (NumberFormatException e) {
            log.warn("Invalid canal.concurrency-count, fallback to 1: value={}", canalProperties.getConcurrencyCount());
            return 1;
        }
    }

    /**
     * Declared Canal topology (queue, exchange, routing key and dead letter pair).
     */
    public record CanalRabbitTopology(String queue, String exchange, String routingKey,
                                      String deadLetterExchange, String deadLetterQueue) {
    }
}
