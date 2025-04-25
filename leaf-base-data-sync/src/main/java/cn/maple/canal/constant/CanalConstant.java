package cn.maple.canal.constant;

/**
 * Canal常量类
 * <p>
 * 该类定义了Canal数据同步相关的常量，包括RabbitMQ配置参数的默认值。
 * 这些常量用于配置Canal与RabbitMQ的集成，确保消息能够正确地从Canal发送到RabbitMQ，
 * 并被应用程序接收和处理。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 在配置类中使用这些常量
 * @Bean
 * public Queue canalQueue() {
 *     return new Queue(CanalConstant.RABBITMQ_CANAL_QUEUE_NAME);
 * }
 * 
 * @Bean
 * public Exchange fanoutExchange() {
 *     return new FanoutExchange(CanalConstant.RABBITMQ_CANAL_EXCHANGE_NAME, true, true);
 * }
 * </pre>
 * </p>
 * <p>
 * 注意：这些常量值应与Canal服务端配置保持一致，确保消息能够正确路由。
 * </p>
 */
public class CanalConstant {
    /**
     * RabbitMQ消费Canal消息的并发线程数量
     * <p>
     * 该常量定义了RabbitMQ监听器的并发消费者数量，用于提高消息处理能力。
     * 根据系统负载和硬件资源，可适当调整此值以获得最佳性能。
     * </p>
     * <p>
     * 默认值为"10"，表示同时启动10个消费者线程处理消息。
     * </p>
     */
    public static final String RABBITMQ_CANAL_CONCURRENCY_COUNT = "10";

    /**
     * Canal消息队列名称
     * <p>
     * 该常量定义了RabbitMQ中用于接收Canal消息的队列名称。
     * 应用程序通过监听此队列来接收数据库变更事件。
     * </p>
     * <p>
     * 默认值为"example"，与Canal服务端默认实例名称对应。
     * 此值应与Canal服务端配置的instance.properties中的canal.instance.name保持一致。
     * </p>
     */
    public static final String RABBITMQ_CANAL_QUEUE_NAME = "example";

    /**
     * Canal消息交换机名称
     * <p>
     * 该常量定义了RabbitMQ中用于分发Canal消息的交换机名称。
     * Canal服务将数据库变更事件发送到此交换机，然后由交换机根据路由规则分发到相应的队列。
     * </p>
     * <p>
     * 默认值为"exchange.fanout.canal"，对应Canal服务端配置文件conf/canal.properties中的
     * rabbitmq.exchange = exchange.fanout.canal配置项。
     * </p>
     */
    public static final String RABBITMQ_CANAL_EXCHANGE_NAME = "exchange.fanout.canal";

    /**
     * Canal消息路由键
     * <p>
     * 该常量定义了RabbitMQ中用于路由Canal消息的路由键。
     * 在使用直连交换机(Direct Exchange)或主题交换机(Topic Exchange)时，
     * 此路由键用于确定消息应该被发送到哪个队列。
     * </p>
     * <p>
     * 默认值为"canal.example.exchange.routingkey"，对应Canal服务端配置文件
     * conf/example/instance.properties中的canal.mq.topic = canal.example.exchange.routingkey配置项。
     * </p>
     */
    public static final String RABBITMQ_CANAL_ROUTING_KEY = "canal.example.exchange.routingkey";

    /**
     * 私有构造函数
     * <p>
     * 防止实例化常量类，遵循常量类的最佳实践。
     * </p>
     */
    private CanalConstant() {
        // 防止实例化
    }
}
