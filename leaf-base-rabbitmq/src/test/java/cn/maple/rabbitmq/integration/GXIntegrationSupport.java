package cn.maple.rabbitmq.integration;

import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import cn.maple.rabbitmq.service.impl.GXSendRabbitMQServiceImpl;
import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import cn.maple.rabbitmq.support.GXAmqpMessagePublisher;
import cn.maple.rabbitmq.support.GXRabbitMQJsonSupport;
import org.apache.qpid.protonj2.client.Connection;
import org.apache.qpid.protonj2.client.ConnectionOptions;
import org.apache.qpid.protonj2.client.Delivery;
import org.apache.qpid.protonj2.client.Receiver;
import org.apache.qpid.protonj2.client.ReceiverOptions;
import org.springframework.amqp.client.SingleAmqpConnectionFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 真实 Broker 集成测试公共构造。
 *
 * <p>凭据只从环境变量读取，不入库：</p>
 * <pre>
 * RABBITMQ_HOST / RABBITMQ_PORT / RABBITMQ_USERNAME / RABBITMQ_PASSWORD / RABBITMQ_VHOST
 * RABBITMQ_MANAGEMENT_PORT（默认 15672）
 * </pre>
 */
final class GXIntegrationSupport {
    private GXIntegrationSupport() {
    }

    static String host() {
        return envOrDefault("RABBITMQ_HOST", "localhost");
    }

    static int port() {
        return Integer.parseInt(envOrDefault("RABBITMQ_PORT", "5672"));
    }

    static String username() {
        return envOrDefault("RABBITMQ_USERNAME", "guest");
    }

    static String password() {
        return envOrDefault("RABBITMQ_PASSWORD", "guest");
    }

    static String vhost() {
        return envOrDefault("RABBITMQ_VHOST", "/");
    }

    static int managementPort() {
        return Integer.parseInt(envOrDefault("RABBITMQ_MANAGEMENT_PORT", "15672"));
    }

    static SingleAmqpConnectionFactory connectionFactory() {
        ConnectionOptions options = new ConnectionOptions()
                .user(username())
                .password(password())
                .virtualHost(vhost());
        SingleAmqpConnectionFactory factory = new SingleAmqpConnectionFactory();
        factory.setHost(host());
        factory.setPort(port());
        factory.setConnectionOptions(options);
        return factory;
    }

    static GXAmqpManagementClient managementClient() {
        return new GXAmqpManagementClient("http://" + host() + ":" + managementPort(), vhost(),
                username(), password(), GXRabbitMQJsonSupport.jsonMapper());
    }

    static GXRabbitMQProperties properties() {
        GXRabbitMQProperties properties = new GXRabbitMQProperties();
        properties.setHost(host());
        properties.setPort(port());
        properties.setUsername(username());
        properties.setPassword(password());
        properties.setVirtualHost(vhost());
        return properties;
    }

    static GXSendRabbitMQServiceImpl service(GXAmqpMessagePublisher publisher,
                                            GXAmqpManagementClient managementClient) {
        GXSendRabbitMQServiceImpl service = new GXSendRabbitMQServiceImpl(publisher, managementClient);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "confirmTimeoutMillis", 5_000L);
        return service;
    }

    /**
     * 直接从队列取一条消息，返回 body 与 application properties。
     */
    static ConsumedMessage receive(SingleAmqpConnectionFactory connectionFactory, String queueName,
                                   long timeoutMillis) {
        Connection connection = connectionFactory.getConnection();
        Receiver receiver = null;
        try {
            receiver = connection.openReceiver("/queues/" + queueName,
                    new ReceiverOptions().creditWindow(0));
            receiver.openFuture().get(10, TimeUnit.SECONDS);
            receiver.addCredit(1);
            Delivery delivery = receiver.receive(timeoutMillis, TimeUnit.MILLISECONDS);
            if (delivery == null) {
                return null;
            }
            org.apache.qpid.protonj2.client.Message<?> message = delivery.message();
            Map<String, Object> properties = new java.util.LinkedHashMap<>();
            message.forEachProperty(properties::put);
            ConsumedMessage consumed = new ConsumedMessage((byte[]) message.body(), properties,
                    message.contentType(), message.contentEncoding());
            delivery.accept();
            return consumed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while receiving", e);
        } catch (Exception e) {
            throw new IllegalStateException("receive failed: " + e.getMessage(), e);
        } finally {
            if (receiver != null) {
                receiver.close();
            }
        }
    }

    record ConsumedMessage(byte[] body, Map<String, Object> properties,
                           String contentType, String contentEncoding) {
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
