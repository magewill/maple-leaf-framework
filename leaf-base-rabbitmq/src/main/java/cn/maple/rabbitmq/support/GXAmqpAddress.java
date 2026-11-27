package cn.maple.rabbitmq.support;

import cn.maple.core.framework.exception.GXBusinessException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * AMQP 1.0 v2 地址构造。
 *
 * <p>RabbitMQ 4.x 只接受 v2 地址格式，且要求按 RFC 3986 对地址片段做百分号编码：</p>
 * <ul>
 *   <li>发布到交换机：{@code /exchanges/<exchange>/<routing-key>}；</li>
 *   <li>发布到队列（默认交换机）：{@code /queues/<queue>}；</li>
 *   <li>消费：{@code /queues/<queue>}。</li>
 * </ul>
 *
 * @see <a href="https://www.rabbitmq.com/docs/amqp">RabbitMQ AMQP 1.0</a>
 */
public final class GXAmqpAddress {
    private static final String EXCHANGES_PREFIX = "/exchanges/";

    private static final String QUEUES_PREFIX = "/queues/";

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private GXAmqpAddress() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    /**
     * 构造发布地址。
     *
     * @param exchange   交换机名，{@code null} 或空字符串表示默认交换机
     * @param routingKey 路由键；默认交换机时即为队列名
     */
    public static String forPublish(String exchange, String routingKey) {
        if (!StringUtils.hasText(exchange)) {
            return forQueue(routingKey);
        }
        String encodedExchange = encode(exchange);
        if (!StringUtils.hasText(routingKey)) {
            return EXCHANGES_PREFIX + encodedExchange;
        }
        return EXCHANGES_PREFIX + encodedExchange + "/" + encode(routingKey);
    }

    /**
     * 构造队列地址，用于发布到默认交换机或消费。
     */
    public static String forQueue(String queueName) {
        if (!StringUtils.hasText(queueName)) {
            throw new GXBusinessException("队列名称不能为空");
        }
        return QUEUES_PREFIX + encode(queueName);
    }

    /**
     * 按 RFC 3986 编码路径片段：仅保留 unreserved 字符（{@code ALPHA / DIGIT / - . _ ~}）。
     */
    public static String encode(String segment) {
        byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length + 8);
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            if (isUnreserved(unsigned)) {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%').append(HEX[unsigned >> 4]).append(HEX[unsigned & 0x0F]);
            }
        }
        return encoded.toString();
    }

    private static boolean isUnreserved(int value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9')
                || value == '-' || value == '.' || value == '_' || value == '~';
    }
}
