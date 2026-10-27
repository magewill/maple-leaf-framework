package cn.maple.canal.properties;

import cn.maple.canal.constant.GXCanalConstant;
import lombok.Data;

/**
 * Dead letter destinations used by the RabbitMQ consumer.
 *
 * <p>Fields are {@code volatile} so consumer threads observe a rebind.</p>
 */
@Data
public class GXCanalDeadLetterProperties {
    /**
     * Dead letter exchange name.
     */
    private volatile String exchange = GXCanalConstant.DEFAULT_DEAD_LETTER_EXCHANGE_NAME;

    /**
     * Dead letter queue name.
     */
    private volatile String queue = GXCanalConstant.DEFAULT_DEAD_LETTER_QUEUE_NAME;

    /**
     * Routing key used to bind the dead letter queue to the dead letter exchange.
     */
    private volatile String routingKey = GXCanalConstant.DEFAULT_DEAD_LETTER_ROUTING_KEY;
}
