package cn.maple.canal.config;

import cn.maple.canal.properties.GXCanalProperties;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;

/**
 * Canal配置类
 * <p>
 * 此配置类负责创建和配置与Canal数据同步相关的RabbitMQ组件，包括交换机、队列和绑定关系。
 * 通过Spring的自动配置机制，将这些组件注册到Spring容器中，用于处理Canal捕获的数据变更事件。
 * </p>
 * <p>
 * Canal是阿里巴巴开源的一款基于MySQL binlog的增量订阅和消费组件，可用于构建数据同步系统。
 * 本配置类将Canal捕获的数据变更事件通过RabbitMQ进行分发，实现数据的实时同步。
 * </p>
 * <p>
 * 安全性考虑：
 * <ul>
 *   <li>使用持久化的交换机和队列，确保消息在RabbitMQ重启后不会丢失</li>
 *   <li>通过属性配置文件管理连接参数，便于不同环境的配置隔离</li>
 *   <li>使用资源注入方式获取配置属性，避免硬编码</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在application.yml中配置Canal相关属性
 * canal:
 *   enabled: true
 *   exchange-name: canal.exchange
 *   canal-queue-name: canal.queue
 *   routing-key: canal.routing.key
 *   
 * // 2. 创建消息监听器处理Canal消息
 * @Component
 * public class CanalMessageListener {
 *     @RabbitListener(queues = "${canal.canal-queue-name}")
 *     public void handleMessage(Message message) {
 *         // 处理Canal捕获的数据变更消息
 *         String messageBody = new String(message.getBody());
 *         // 解析消息并执行相应的业务逻辑
 *     }
 * }
 * </pre>
 * </p>
 */
@Configuration
public class GXCanalConfig {
    /**
     * Canal配置属性
     * <p>
     * 包含Canal数据同步所需的RabbitMQ相关配置，如交换机名称、队列名称和路由键等。
     * 这些属性通常在application.yml或application.properties中配置。
     * </p>
     */
    @Resource
    private GXCanalProperties canalProperties;

    /**
     * 创建Canal消息队列与交换机的绑定关系
     * <p>
     * 将Canal队列绑定到指定的交换机上，并设置路由键，确保消息能够正确路由。
     * 使用Spring的依赖注入自动获取已配置的交换机和队列实例。
     * </p>
     *
     * @param fanoutExchange 扇出交换机，用于广播消息到所有绑定的队列
     * @param canalQueue Canal消息队列，用于存储待处理的数据变更消息
     * @return 绑定关系对象
     */
    @Bean
    public Binding binding(Exchange fanoutExchange, Queue canalQueue) {
        return BindingBuilder.bind(canalQueue).to(fanoutExchange).with(canalProperties.getRoutingKey()).noargs();
    }

    /**
     * 创建扇出交换机
     * <p>
     * 配置一个持久化的扇出交换机，用于广播Canal捕获的数据变更消息到所有绑定的队列。
     * 扇出交换机会将收到的消息发送给所有绑定到它的队列，不考虑路由键。
     * </p>
     * <p>
     * 参数说明：
     * <ul>
     *   <li>第一个参数：交换机名称，从配置属性中获取</li>
     *   <li>第二个参数：是否持久化，设置为true确保RabbitMQ重启后交换机不会丢失</li>
     *   <li>第三个参数：是否自动删除，设置为true表示当所有队列都不再使用此交换机时自动删除</li>
     * </ul>
     * </p>
     *
     * @return 配置好的扇出交换机实例
     */
    @Bean
    public Exchange fanoutExchange() {
        return new FanoutExchange(canalProperties.getExchangeName(), true, true);
    }

    /**
     * 创建Canal消息队列
     * <p>
     * 配置一个用于存储Canal数据变更消息的队列。队列名称从配置属性中获取。
     * 默认创建的是持久化队列，确保消息在RabbitMQ重启后不会丢失。
     * </p>
     * <p>
     * 注意：如果需要更细粒度的队列配置（如TTL、最大长度等），可以使用QueueBuilder进行构建。
     * 例如：
     * <pre>
     * return QueueBuilder.durable(canalProperties.getCanalQueueName())
     *     .withArgument("x-message-ttl", 60000) // 消息过期时间，单位毫秒
     *     .withArgument("x-max-length", 1000)   // 队列最大长度
     *     .build();
     * </pre>
     * </p>
     *
     * @return 配置好的Canal消息队列实例
     */
    @Bean
    public Queue canalQueue() {
        return new Queue(canalProperties.getCanalQueueName());
    }
}
