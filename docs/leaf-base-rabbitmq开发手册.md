# leaf-base-rabbitmq 开发手册

## 1. 模块概述

`leaf-base-rabbitmq` 模块是 `maple-leaf-framework` 框架中负责与 RabbitMQ 消息队列进行交互的核心组件。它提供了一套便捷的 API 和配置，简化了 RabbitMQ 在 Spring Boot 项目中的集成和使用。该模块旨在提供可靠、高效、易于扩展的消息发送和接收能力，支持消息确认、重试、死信队列等高级特性，并考虑了线程安全和性能优化。

主要功能包括：

*   **消息发送与接收**：提供简单易用的 API 发送和接收消息。
*   **动态队列与交换机管理**：支持在运行时动态创建、删除和管理队列、交换机及其绑定关系。
*   **消息确认机制**：支持 `publisher-confirms` 和 `publisher-returns` 机制，确保消息的可靠投递。
*   **重试与恢复**：集成 Spring Retry，提供消息发送失败后的自动重试和恢复机制。
*   **配置灵活性**：支持本地配置文件和 Nacos 配置中心两种方式加载 RabbitMQ 连接属性。
*   **异步处理**：支持异步发送消息和异步创建消息通道，提高系统吞吐量。
*   **消息追踪**：自动为发送的消息添加追踪信息，便于问题排查。

## 2. 核心组件与关键类职责

### 2.1. Maven 依赖

```xml
<dependency>
    <groupId>cn.maple</groupId>
    <artifactId>leaf-base-rabbitmq</artifactId>
    <version>${maple-leaf-framework.version}</version>
</dependency>
```

该模块依赖于 `leaf-base-framework`、`leaf-base-nacos` (可选，用于 Nacos 配置) 和 `leaf-base-retry`，并引入了 `spring-boot-starter-amqp`。

### 2.2. 核心配置类

*   **`GXRabbitMQConfig`**: 核心配置类，负责创建和配置 RabbitMQ 相关的 Bean，如 `RabbitTemplate`、`RabbitAdmin`、`AsyncRabbitTemplate` 和 `RabbitMessagingTemplate`。
    *   配置线程安全的 `RabbitTemplate`，使用 JSON 消息转换器，并增强反序列化安全性。
    *   配置消息确认回调 (`GXConfirmCallback`) 和重试恢复回调 (`GXRecoveryCallback`)。
    *   配置 `AsyncRabbitTemplate` 以支持异步消息发送，并使用自定义线程池 (`rabbitTaskScheduler`) 进行优化。
    *   配置 `RabbitMessagingTemplate` 以简化与 Spring Messaging API 的集成。
    *   提供自定义线程工厂 (`RabbitThreadFactory`)，确保线程命名唯一性和异常处理。

### 2.3. 属性配置类

*   **`GXRabbitMQProperties`**: RabbitMQ 配置属性的基类 (空接口，作为标记)。
*   **`GXLocalRabbitMQProperties`**: 本地 RabbitMQ 配置属性类。通过 `@ConfigurationProperties(prefix = "maple.framework.mq.rabbitmq")` 从 `rabbit.yml` 或 `application.yml` 加载配置。在没有 Nacos 依赖时生效 (`@ConditionalOnMissingClass("com.alibaba.nacos.api.config.ConfigService")`)。
*   **`GXNacosRabbitMQProperties`**: Nacos RabbitMQ 配置属性类。通过 `@NacosConfigurationProperties(dataId = "rabbit.yml", groupId = "DEFAULT_GROUP", autoRefreshed = true)` 从 Nacos 配置中心加载 `rabbit.yml` 配置。在存在 Nacos 依赖时生效 (`@ConditionalOnClass(name = "com.alibaba.nacos.api.config.ConfigService")`)。

    两个实现类都包含以下核心属性：

    *   `enable`: 是否启用 RabbitMQ 功能 (boolean, 默认为 `false`)。
    *   `addresses`: RabbitMQ 服务器地址，多个地址用逗号分隔 (String)。
    *   `username`: 用户名 (String)。
    *   `password`: 密码 (String)。
    *   `virtualHost`: 虚拟主机 (String, 默认为 `/`)。
    *   `publisherConfirmType`: 消息确认类型 (CorrelationData.ConfirmType, SIMPLE 或 CORRELATED)。
    *   `publisherReturns`: 是否启用 `publisher-returns` (boolean, 默认为 `false`)。
    *   `defaultQueueName`: 默认监听的队列名称 (String, 默认为 `maple.default.queue`)。
    *   `listener`: 监听器相关配置，如并发数、最大并发数等。
    *   `retry`: 重试相关配置，如最大尝试次数、初始间隔、最大间隔、乘数等。
    *   `thread-pool`: 异步操作线程池配置，如核心线程数、最大线程数、队列容量。

### 2.4. 消息发送服务

*   **`GXSendRabbitMQService` (接口)**: 定义了 RabbitMQ 消息发送和队列管理的服务接口。
    *   `sendNormalMessage(GXRabbitMQMessageReqDto messageReqDto)`: 发送常规消息。
    *   `createQueue(...)`, `createDurableQueue(String queueName)`, `createTemporaryQueue(String queueName)`: 创建不同类型的队列。
    *   `deleteQueue(String queueName)`: 删除队列。
    *   `queueExists(String queueName)`: 检查队列是否存在。
    *   `purgeQueue(String queueName)`: 清空队列消息。
    *   `createExchange(AbstractExchange exchange)`: 创建交换机。
    *   `bindQueueToExchange(...)`, `unbindQueueFromExchange(...)`: 管理绑定关系。
    *   `setupMessageChannel(...)`: 一站式创建消息通道（队列、交换机、绑定）。
    *   `setupMessageChannelAsync(...)`: 异步一站式创建消息通道。
    *   `setupDurableMessageChannel(...)`: 一站式创建持久化消息通道。
*   **`GXSendRabbitMQServiceImpl` (实现类)**: `GXSendRabbitMQService` 的实现。
    *   使用 `RabbitTemplate` 和 `RabbitAdmin` 进行操作。
    *   实现了消息发送、动态队列/交换机/绑定管理。
    *   提供异步操作 API，使用专用线程池 (`rabbitMqAsyncExecutor`)。
    *   内置消息追踪，通过 `enhanceMessageWithTracing` 方法为消息添加 `X-Trace-Id`, `X-Timestamp`, `X-Source-Service` 等头部信息。
    *   使用 `ConcurrentHashMap` 缓存队列信息 (`queueCache`)，并使用 `ReentrantLock` (`queueOperationLock`) 保护关键操作，确保线程安全。

### 2.5. 消息数据传输对象 (DTO)

*   **`GXRabbitMQMessageReqDto`**: 封装 RabbitMQ 消息发送请求的数据结构。
    *   继承自 `GXBaseReqDto`。
    *   包含 `exchange` (交换机名称), `routingKey` (路由键), `data` (消息内容, `cn.hutool.core.lang.Dict` 类型), `messageProperties` (`org.springframework.amqp.core.MessageProperties`), `correlationData` (`org.springframework.amqp.rabbit.connection.CorrelationData`), `tag` (消费者标签) 等字段。
    *   支持 Builder 模式创建对象。
    *   通过同步方法和不可变对象设计确保线程安全。

### 2.6. 消息监听器

*   **`GXRabbitMQQueueListener` (接口)**: 定义了处理 RabbitMQ 队列消息的标准方法。
    *   `process(Message data)`: 处理接收到的消息。
*   **`GXDefaultRabbitMQQueueListenerImpl` (实现类)**: `GXRabbitMQQueueListener` 的默认实现。
    *   使用 `@RabbitListener` 注解监听队列，队列名通过 SpEL 表达式 `#{T(cn.maple.core.framework.util.GXSpELToolUtils).callBeanMethodSpELExpression(Class.forName('cn.maple.rabbitmq.properties.GXRabbitMQProperties'), 'getDefaultQueueName', Class.forName('java.lang.String'), new Class[0])}` 动态获取，即调用 `GXRabbitMQProperties` 实现类的 `getDefaultQueueName()` 方法。
    *   通过 `@ConditionalOnExpression("${maple.framework.mq.rabbitmq.enable:false}")` 和 `@ConditionalOnClass(name = {"org.springframework.amqp.rabbit.connection.ConnectionFactory"})` 控制是否启用。
    *   使用 `@Lazy` 注解实现懒加载。
    *   `process` 方法将消息体转为字符串，尝试按 JSON 解析为 `Dict` 对象，否则直接记录原始消息。
    *   提供了 `processMessageContent`, `processJsonMessage`, `processNonJsonMessage` 和 `handleComplexJsonMessage` (异步示例) 等辅助方法。

### 2.7. 回调接口

*   **`GXConfirmCallback`**: 消息发送到交换机后的确认回调接口。
    *   `confirm(CorrelationData correlationData, boolean ack, String cause)`: 处理确认结果。
*   **`GXRecoveryCallback`**: 消息发送重试耗尽后的恢复回调接口 (与 Spring Retry 集成)。
    *   `recover(RetryContext retryContext)`: 执行恢复逻辑。
*   **`GXReturnsCallback`**: 无法路由的消息返回回调接口。
    *   `returnedMessage(ReturnedMessage returned)`: 处理返回的消息。

    这些接口的实现类会被注入到 `GXRabbitMQConfig` 中配置给 `RabbitTemplate`。

## 3. 对外提供的接口与配置

### 3.1. 主要服务接口

*   `GXSendRabbitMQService`: 用于发送消息和管理 RabbitMQ 资源。
*   `GXRabbitMQQueueListener`: 用于实现自定义消息监听逻辑。

### 3.2. 配置项 (application.yml 或 Nacos)

在 `maple.framework.mq.rabbitmq` 前缀下配置：

```yaml
maple:
  framework:
    mq:
      rabbitmq:
        enable: true  # 是否启用 RabbitMQ，默认为 false
        addresses: localhost:5672 # RabbitMQ 服务器地址，多个用逗号分隔
        username: guest # 用户名
        password: guest # 密码
        virtual-host: / # 虚拟主机
        publisher-confirm-type: CORRELATED # 消息确认类型: NONE, SIMPLE, CORRELATED
        publisher-returns: true # 是否启用 publisher-returns
        default-queue-name: maple.default.queue # 默认监听队列名
        listener:
          simple:
            concurrency: 1 # 最小消费者数量
            max-concurrency: 10 # 最大消费者数量
            prefetch-count: 1 # 每次拉取消息数量
        retry:
          enabled: true # 是否启用发送重试
          max-attempts: 3 # 最大重试次数
          initial-interval: 1000 # 初始重试间隔 (ms)
          max-interval: 10000 # 最大重试间隔 (ms)
          multiplier: 2.0 # 重试间隔乘数
        thread-pool: # 异步操作线程池配置
          core-size: #{T(java.lang.Runtime).getRuntime().availableProcessors()} # 核心线程数
          max-size: #{T(java.lang.Runtime).getRuntime().availableProcessors() * 2} # 最大线程数
          queue-capacity: 1000 # 队列容量
```

## 4. 典型使用场景示例

### 4.1. 发送消息

```java
@Service
public class OrderService {

    @Resource
    private GXSendRabbitMQService sendRabbitMQService;

    public void createOrder(Order order) {
        // ... 创建订单逻辑 ...

        GXRabbitMQMessageReqDto messageDto = GXRabbitMQMessageReqDto.builder()
                .exchange("order.exchange")
                .routingKey("order.created")
                .data(Dict.create().set("orderId", order.getId()).set("amount", order.getAmount()))
                .messageProperties(new MessageProperties())
                .build();
        try {
            sendRabbitMQService.sendNormalMessage(messageDto);
            log.info("订单创建消息已发送: {}", order.getId());
        } catch (AmqpException e) {
            log.error("发送订单创建消息失败: {}", order.getId(), e);
            // 可进行补偿处理，例如记录到本地表，后续重试
        }
    }
}
```

### 4.2. 监听消息 (使用默认队列)

如果配置了 `maple.framework.mq.rabbitmq.enable=true` 和 `maple.framework.mq.rabbitmq.default-queue-name`，`GXDefaultRabbitMQQueueListenerImpl` 会自动监听该队列。
我们通常需要自定义处理逻辑，可以继承 `GXDefaultRabbitMQQueueListenerImpl` 并重写 `processJsonMessage` 或 `processNonJsonMessage`，或者完全自定义实现 `GXRabbitMQQueueListener`。

### 4.3. 自定义消息监听器

```java
@Component
@Slf4j
public class MyCustomQueueListener implements GXRabbitMQQueueListener {

    @RabbitListener(queues = "my.custom.queue") // 直接指定监听的队列名
    @RabbitHandler
    @Override
    public void process(Message data) {
        String messageBody = new String(data.getBody(), StandardCharsets.UTF_8);
        log.info("接收到自定义队列消息: {}", messageBody);
        // 自定义处理逻辑
        try {
            OrderUpdateDto orderUpdate = JSONUtil.toBean(messageBody, OrderUpdateDto.class);
            // ... 处理订单更新 ...
        } catch (Exception e) {
            log.error("处理自定义队列消息失败: {}", messageBody, e);
            // 根据需要决定是否抛出异常，影响消息确认
            // throw new AmqpRejectAndDontRequeueException("处理失败，消息不再重入队列");
        }
    }
}
```

确保 `my.custom.queue` 队列、对应的交换机和绑定关系已存在，或通过 `GXSendRabbitMQService` 动态创建。

### 4.4. 动态创建消息通道并发送消息

```java
@Service
public class NotificationService {

    @Resource
    private GXSendRabbitMQService sendRabbitMQService;

    public void sendNotification(String userId, String messageContent) {
        String queueName = "user.notification." + userId;
        String exchangeName = "notification.exchange";
        String routingKey = "user." + userId;

        // 创建一个Direct类型的交换机
        DirectExchange exchange = new DirectExchange(exchangeName, true, false);

        // 一站式创建队列、交换机并绑定
        boolean channelCreated = sendRabbitMQService.setupMessageChannel(
                queueName,      // 队列名
                true,           // 持久化
                false,          // 非排他
                false,          // 非自动删除
                null,           // 队列参数
                exchange,       // 交换机对象
                routingKey      // 路由键
        );

        if (channelCreated) {
            GXRabbitMQMessageReqDto messageDto = GXRabbitMQMessageReqDto.builder()
                    .exchange(exchangeName)
                    .routingKey(routingKey)
                    .data(Dict.create().set("userId", userId).set("content", messageContent))
                    .build();
            sendRabbitMQService.sendNormalMessage(messageDto);
            log.info("通知消息已发送给用户: {}", userId);
        } else {
            log.error("创建用户通知通道失败: {}", userId);
        }
    }

    // 异步创建示例
    public CompletableFuture<Void> sendNotificationAsync(String userId, String messageContent) {
        String queueName = "user.notification.async." + userId;
        String exchangeName = "notification.async.exchange";
        String routingKey = "user.async." + userId;
        DirectExchange exchange = new DirectExchange(exchangeName, true, false);

        return sendRabbitMQService.setupMessageChannelAsync(
                queueName, true, false, false, null, exchange, routingKey
        ).thenCompose(created -> {
            if (created) {
                GXRabbitMQMessageReqDto messageDto = GXRabbitMQMessageReqDto.builder()
                        .exchange(exchangeName)
                        .routingKey(routingKey)
                        .data(Dict.create().set("userId", userId).set("content", messageContent))
                        .build();
                sendRabbitMQService.sendNormalMessage(messageDto);
                log.info("异步通知消息已发送给用户: {}", userId);
                return CompletableFuture.completedFuture(null);
            } else {
                log.error("异步创建用户通知通道失败: {}", userId);
                return CompletableFuture.failedFuture(new RuntimeException("Channel creation failed"));
            }
        });
    }
}
```

## 5. 与其他模块的交互关系

*   **`leaf-base-core`**: 依赖核心框架的功能，如 `GXBusinessService`、`GXBaseReqDto`、`GXSpELToolUtils` 等。
*   **`leaf-base-nacos`**: 如果项目中引入了 Nacos 依赖，`GXNacosRabbitMQProperties` 会被激活，从 Nacos 配置中心加载 RabbitMQ 配置。
*   **`leaf-base-retry`**: `GXRabbitMQConfig` 中配置的 `RabbitTemplate` 会使用 `leaf-base-retry` 提供的重试机制 (如果 `maple.framework.mq.rabbitmq.retry.enabled=true`)。`GXRecoveryCallback` 接口的实现用于处理重试耗尽后的恢复逻辑。

## 6. 线程安全与性能考量

### 6.1. 线程安全

*   **`RabbitTemplate`**: Spring AMQP 提供的 `RabbitTemplate` 本身是线程安全的，可以被多线程共享用于发送消息。
*   **`RabbitAdmin`**: 用于管理声明，也是线程安全的。
*   **`GXSendRabbitMQServiceImpl`**: 
    *   消息发送操作委托给线程安全的 `RabbitTemplate`。
    *   动态创建队列/交换机/绑定等操作使用了 `ReentrantLock` (`queueOperationLock`) 进行同步，确保原子性和避免竞态条件。
    *   使用 `ConcurrentHashMap` (`queueCache`) 缓存已声明的队列信息，保证缓存读写的线程安全。
*   **`GXRabbitMQMessageReqDto`**: 通过 Builder 模式创建不可变或事实不可变的对象 (如果 `Dict` 和 `MessageProperties` 在构建后不再修改)，或者通过其 getter/setter 的同步控制 (如果需要可变性) 来保证线程安全。当前实现中，`data` (Dict) 和 `messageProperties` 是可变的，使用时需注意并发修改问题，建议构建完成后不再修改，或在并发访问时自行同步。
*   **`GXDefaultRabbitMQQueueListenerImpl`**: 该监听器实现是无状态的，`process` 方法的每次调用处理独立的消息，因此是线程安全的。Spring AMQP 会为每个并发消费者创建一个独立的线程来调用监听器方法。
*   **自定义监听器 (`GXRabbitMQQueueListener` 实现)**: 开发者需要自行确保其 `process` 方法的线程安全性，避免共享可变状态或对共享状态的访问进行同步。
*   **异步操作线程池 (`rabbitMqAsyncExecutor`)**: 在 `GXSendRabbitMQServiceImpl` 中用于异步创建消息通道，使用的是 `ThreadPoolExecutor`，其参数 (核心线程数、最大线程数、队列容量) 可配置，并使用了自定义线程工厂和拒绝策略。

### 6.2. 性能考量

*   **连接池**: `RabbitTemplate` 内部使用 `CachingConnectionFactory` 来管理连接和通道，复用资源，提高性能。
*   **JSON序列化**: 默认使用 Hutool JSON 进行消息内容的序列化和反序列化。对于性能要求极高的场景，可以考虑替换为更快的序列化库 (如 Jackson, Protobuf)。
*   **异步发送**: `GXSendRabbitMQService` 提供了 `setupMessageChannelAsync` 方法用于异步创建消息通道，避免阻塞主线程。
*   **批量操作**: 虽然当前 `GXSendRabbitMQService` 没有显式的批量发送接口，但 RabbitMQ 本身支持批量确认，`RabbitTemplate` 也可以配置批量发送 (`batchingStrategy`)。
*   **队列缓存**: `GXSendRabbitMQServiceImpl` 使用 `queueCache` 缓存已声明的队列，避免重复向 RabbitMQ Server 查询队列信息，减少网络开销。
*   **消费者并发**: 监听器的并发数可以通过 `spring.rabbitmq.listener.simple.concurrency` 和 `spring.rabbitmq.listener.simple.max-concurrency` 进行配置，以适应不同的消费能力需求。
*   **预取数量 (Prefetch Count)**: 可以通过 `spring.rabbitmq.listener.simple.prefetch` 配置消费者一次从 Broker 获取的消息数量，合理设置可以提高吞吐量。
*   **懒加载**: `GXDefaultRabbitMQQueueListenerImpl` 使用 `@Lazy` 注解，仅在实际需要时初始化，减少应用启动时间。
*   **线程池优化**: `GXSendRabbitMQServiceImpl` 中的异步操作线程池 (`rabbitMqAsyncExecutor`) 参数可配置，允许根据系统负载进行调优。

## 7. 注意事项与最佳实践

*   **幂等性处理**: 消息消费端应保证消息处理的幂等性，因为网络问题或Broker故障可能导致消息重复投递。
*   **消息确认**: 务必理解并正确配置 `publisher-confirms` 和 `publisher-returns` 机制，以及消费者端的 `ack-mode`，确保消息不丢失。
*   **异常处理**: 在消息监听器的 `process` 方法中，妥善处理可能发生的异常。未捕获的异常可能导致消息不断重入队列 (取决于 `ack-mode` 和重试配置)。可以使用 `AmqpRejectAndDontRequeueException` 来告知 Broker 不要将消息重新入队。
*   **死信队列 (DLX)**: 对于处理失败且不应重试的消息，或达到最大重试次数的消息，建议配置死信交换机和死信队列，用于后续分析和处理。
*   **配置管理**: 优先使用 Nacos 等配置中心管理 RabbitMQ 连接属性，便于不同环境的配置和动态更新。
*   **资源管理**: 动态创建的队列和交换机，如果不再使用，应及时清理，避免资源浪费。
*   **日志级别**: 合理配置日志级别，生产环境中避免过多 DEBUG 和 TRACE 日志，关键路径和异常情况记录 INFO 和 ERROR 日志。
*   **消息大小**: 注意 RabbitMQ 对消息大小的限制，避免发送过大的消息。对于大消息，可以考虑拆分或使用其他存储方案传递引用。
*   **监控**: 对 RabbitMQ集群和应用本身的消息收发情况进行监控，及时发现和处理问题。

## 8. 未来展望

*   **更细致的监控指标**: 暴露更多模块内部的监控指标，如消息发送速率、处理延迟、队列积压等，方便与监控系统集成。
*   **声明式队列/交换机/绑定**: 考虑引入注解方式声明队列、交换机和绑定，进一步简化配置。
*   **延迟消息插件支持**: 集成 RabbitMQ 延迟消息插件，提供开箱即用的延迟消息发送能力。
*   **多数据源/多集群支持**: 考虑支持连接到多个 RabbitMQ 集群或在同一应用中使用不同的 RabbitMQ 配置。
*   **更灵活的序列化配置**: 提供更便捷的方式切换或自定义消息序列化/反序列化机制。

---

本文档旨在为 `leaf-base-rabbitmq` 模块的开发者和使用者提供全面的参考。如有疑问或建议，请联系框架维护团队。