package cn.maple.rabbitmq.support;

/**
 * RabbitMQ Management HTTP API 调用失败。
 *
 * <p>仅由 {@link GXAmqpManagementClient} 抛出，服务层会转换为
 * {@code GXRabbitMQOperationException} 并保留原始状态码与响应片段。</p>
 */
public class GXAmqpManagementException extends RuntimeException {
    private final int statusCode;

    public GXAmqpManagementException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GXAmqpManagementException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
