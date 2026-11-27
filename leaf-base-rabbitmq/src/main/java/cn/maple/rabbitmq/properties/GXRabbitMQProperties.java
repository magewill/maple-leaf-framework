package cn.maple.rabbitmq.properties;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * RabbitMQ 连接配置（AMQP 1.0），业务代码只依赖本类型，不依赖 local/nacos 具体变体。
 *
 * <p>前缀固定为 {@code spring.rabbitmq}，用于兼容既有 {@code rabbit.yml}；当
 * {@code spring.amqp.*}（Spring Boot 4.2 原生 AMQP 1.0 前缀）显式配置时优先采用后者，
 * 解析逻辑见 {@code GXRabbitMQConfig#gxAmqpConnectionDetails}。</p>
 *
 * <p>模块自身的开关（{@code maple.framework.rabbitmq.*}：{@code confirm-timeout}、
 * {@code management.*}、{@code thread-pool.*}）不放在本类中——本类只绑定
 * {@code spring.rabbitmq} 前缀，混放会导致键永远绑不上。</p>
 *
 * <p>AMQP 1.0 没有 channel 缓存、连接数上限等经典协议概念，因此历史字段
 * {@code connectionLimit}/{@code channelCacheSize}/{@code cacheMode}/
 * {@code channelCheckoutTimeout} 已移除。</p>
 */
@Setter
@Getter
public class GXRabbitMQProperties {
    /**
     * Broker 主机名。
     */
    private String host = "localhost";

    /**
     * AMQP 1.0 端口，RabbitMQ 4.x 与 0-9-1 共用 5672。
     */
    private int port = 5672;

    /**
     * 历史配置写法 {@code spring.rabbitmq.addresses=host1:5672,host2:5672}。
     *
     * <p>AMQP 1.0 客户端按单端点连接，这里取第一个地址；其余地址当前忽略
     * （需要多节点故障转移时改用 {@code spring.amqp.host} 指向负载均衡或 VIP）。</p>
     */
    @Nullable
    private String addresses;

    @Nullable
    private String username;

    @Nullable
    private String password;

    /**
     * 虚拟主机。AMQP 1.0 通过 open 帧的 hostname 字段传递，未配置时为 {@code /}。
     */
    private String virtualHost = "/";

    /**
     * 默认监听队列名，仅在未配置 {@code maple.framework.mq.rabbitmq.default-queue-name} 时生效。
     */
    private String defaultQueueName = "maple.default.queue";
}
