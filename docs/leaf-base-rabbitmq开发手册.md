# Leaf-Base-RabbitMQ 开发手册

## 1. 模块介绍

Leaf-Base-RabbitMQ 是 Maple-Leaf-Framework 框架中用于集成 RabbitMQ 消息队列的模块。它简化了 RabbitMQ 的配置和使用，提供了消息发送、接收以及相关回调处理的标准化接口和实现。该模块旨在帮助开发者快速构建可靠的、基于消息驱动的应用程序。

### 1.1 主要功能

- **简化的配置**：支持通过本地 `rabbit.yml` 文件或 Nacos 配置中心进行 RabbitMQ 的连接和行为配置。
- **标准化的消息发送**：提供 `GXSendRabbitMQService` 接口用于发送消息，封装了 `RabbitTemplate` 的核心功能。
- **消息监听**：提供 `GXRabbitMQQueueListener` 接口和默认实现 `GXDefaultRabbitMQQueueListenerImpl`，方便消费队列中的消息。
- **回调机制**：定义了 `GXConfirmCallback` (消息发送到交换机确认)、`GXReturnsCallback` (消息无法路由到队列时的返回) 和 `GXRecoveryCallback` (消息重试耗尽后的恢复) 接口，允许开发者自定义处理逻辑。
- **核心组件自动装配**：自动配置 `RabbitTemplate`、`RabbitAdmin`、`AsyncRabbitTemplate` 等核心组件。
- **JSON消息格式支持**：默认使用 Jackson 进行消息的序列化和反序列化，支持 JSON 格式的消息内容。

### 1.2 依赖说明

模块主要依赖以下组件：

- Spring Boot AMQP：提供 RabbitMQ 的核心集成能力。
- Hutool：提供工具类支持。
- Leaf-Base-Framework：框架基础功能。
- Leaf-Base-Nacos (可选)：用于从 Nacos 加载配置。

## 2. 快速开始

### 2.1 添加依赖

在项目的 `pom.xml` 文件中添加以下依赖：

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-rabbitmq</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

### 2.2 配置 RabbitMQ

#### 2.2.1 本地配置方式

在 `src/main/resources/{profile}/rabbit.yml` 文件中添加配置（例如 `src/main/resources/dev/rabbit.yml`）：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    publisher-confirm-type: correlated # 发送方确认模式
    publisher-returns: true             # 投递失败返回
    template:
      mandatory: true                 # 开启强制委派，投递失败时会将消息退回给生产者
      retry:
        enabled: true                 # 开启重试
        initial-interval: 1000ms      # 初始重试间隔
        max-attempts: 3               # 最大重试次数
        multiplier: 2                 # 重试间隔乘数
# 自定义配置 (可选)
enable-rabbitmq: true # 是否启用RabbitMQ，默认为true，如果GXDefaultRabbitMQQueueListenerImpl需要生效，则必须为true
# gx:
#   rabbitmq:
#     default-queue-name: "my.default.queue" # GXDefaultRabbitMQQueueListenerImpl 监听的默认队列名，不配置则不监听
```

#### 2.2.2 Nacos 配置方式

如果项目中集成了 Nacos 配置中心，可以在 Nacos 中创建 `rabbit.yml` 配置文件，配置内容与本地配置相同。模块会自动识别并加载 Nacos 中的配置。

## 2. 模块核心类及功能概览

- **`cn.maple.rabbitmq.config.GXRabbitMQConfig`**: RabbitMQ 的核心配置类。负责自动装配和配置 `RabbitTemplate` (包括消息转换器和回调)、`RabbitAdmin`、`AsyncRabbitTemplate` 和 `RabbitMessagingTemplate` 等关键 Spring AMQP 组件。
- **`cn.maple.rabbitmq.properties.*`**: RabbitMQ 配置属性类。
    - `GXRabbitMQProperties`: 所有 RabbitMQ 配置属性类的基类 (标记接口)。
    - `local.GXLocalRabbitMQProperties`: 用于从本地 `rabbit.yml` 文件加载配置。当项目中不存在 Nacos 相关依赖时，此配置类生效。
    - `nacos.GXNacosRabbitMQProperties`: 用于从 Nacos 配置中心加载 `rabbit.yml`。当项目中存在 Nacos 相关依赖时，此配置类生效。
- **`cn.maple.rabbitmq.dto.GXRabbitMQMessageReqDto`**: 数据传输对象 (DTO)，用于封装发送 RabbitMQ 消息时所需的全部参数，包括目标交换机、路由键、消息体 (`Dict`)、消息属性 (`MessageProperties`) 以及用于追踪的 `CorrelationData`。
- **`cn.maple.rabbitmq.service.GXSendRabbitMQService`**: 定义发送 RabbitMQ 消息操作的服务接口。
- **`cn.maple.rabbitmq.service.impl.GXSendRabbitMQServiceImpl`**: `GXSendRabbitMQService` 的默认实现。它使用注入的 `RabbitTemplate` 来发送消息，并处理 `CorrelationData` 的自动生成以及消息属性的设置。
- **`cn.maple.rabbitmq.listener.GXRabbitMQQueueListener`**: 消息队列监听器接口，定义了处理接收到的 RabbitMQ 消息的标准方法。
- **`cn.maple.rabbitmq.listener.impl.GXDefaultRabbitMQQueueListenerImpl`**: `GXRabbitMQQueueListener` 的默认实现。通过 `@RabbitListener` 注解，它可以动态监听在配置文件中指定的默认队列 (通过 `gx.rabbitmq.default-queue-name` 或 `GXRabbitMQProperties` 子类中的 `getDefaultQueueName()` 方法)。该监听器默认处理 JSON 格式的消息，并将其转换为 `Dict` 对象。
- **`cn.maple.rabbitmq.callback.*`**: 包含与消息发送可靠性相关的回调接口：
    - `GXConfirmCallback`: 用于处理消息成功发送到 Exchange 或发送失败的确认回调。
    - `GXReturnsCallback`: 用于处理消息成功发送到 Exchange 但无法路由到任何 Queue 时的返回消息回调。
    - `GXRecoveryCallback`: 用于处理当消息发送使用 Spring Retry 机制且所有重试尝试均失败后的恢复逻辑回调。

## 3. 核心组件说明

### 3.1 GXRabbitMQConfig

<mcsymbol name="GXRabbitMQConfig" filename="GXRabbitMQConfig.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java" startline="26" type="class"></mcsymbol> 是 RabbitMQ 的核心配置类，负责自动装配和配置 Spring AMQP 的关键组件。

- **`RabbitTemplate`**：
    - 通过 `rabbitTemplate()` Bean 方法创建和配置。
    - 设置了 `ConnectionFactory`。
    - **消息转换器**: 配置了 `Jackson2JsonMessageConverter` 作为默认消息转换器，支持 JSON 格式的消息。为了增强反序列化时的安全性，通过 `DefaultClassMapper` 设置了信任的包路径为 `cn.hutool.core`。这意味着只有 `cn.hutool.core` 包及其子包下的类可以被安全地反序列化。如果应用需要处理其他包中的自定义类型，可能需要调整此配置或提供自定义的 `ClassMapper`。
    - **回调注册**: 
        - `GXReturnsCallback` (消息无法路由到队列时的返回回调)
        - `GXConfirmCallback` (消息发送到交换机确认回调)
        - `GXRecoveryCallback` (消息重试耗尽后的恢复回调)
        这些回调接口的实现类会通过 `cn.maple.core.framework.util.GXSpringContextUtils.getBean()` 方法从 Spring 容器中动态获取。因此，开发者需要将自定义的回调实现注册为 Spring Bean，模块会自动发现并使用它们。
- **`RabbitAdmin`**：
    - 通过 `rabbitAdmin()` Bean 方法创建，用于管理 RabbitMQ 的元数据，如声明队列、交换机和绑定。它会自动检测 Spring 容器中定义的 `Queue`、`Exchange` 和 `Binding` 类型的 Bean，并在 RabbitMQ 服务器上创建它们（如果不存在）。
- **`AsyncRabbitTemplate`**：
    - 通过 `asyncRabbitTemplate()` Bean 方法创建，基于已配置的 `RabbitTemplate`，提供异步消息发送能力，返回 `ListenableFuture`。
- **`RabbitMessagingTemplate`**：
    - 通过 `simpleMessageTemplate()` Bean 方法创建，与 Spring Messaging API 集成。值得注意的是，此方法内部创建了一个新的 `RabbitTemplate` 实例，并使用 `GenericMessageConverter` 作为其消息转换器，而不是复用上面配置的 `rabbitTemplate()` Bean。这为需要不同消息转换策略的场景提供了灵活性。

### 3.2 GXRabbitMQProperties 及其子类

- <mcsymbol name="GXRabbitMQProperties" filename="GXRabbitMQProperties.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/properties/GXRabbitMQProperties.java" startline="3" type="class"></mcsymbol>: RabbitMQ 配置属性的基类。它本身不包含任何属性，主要作为类型标记和被其他特定配置类继承。
- <mcsymbol name="GXLocalRabbitMQProperties" filename="GXLocalRabbitMQProperties.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/properties/local/GXLocalRabbitMQProperties.java" startline="13" type="class"></mcsymbol>:
    - 继承自 `GXRabbitMQProperties`。
    - **生效条件**: 使用 `@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})` 注解，表示仅当项目中**不存在** Nacos 配置相关的类时，此本地配置才会生效。
    - **配置加载**: 通过 `@PropertySource(value = {"classpath:/${spring.profiles.active}/rabbit.yml"}, factory = GXYamlPropertySourceFactory.class, ignoreResourceNotFound = false)` 从类路径下的当前激活profile对应的 `rabbit.yml` 文件加载配置。例如，如果 `spring.profiles.active=dev`，则会加载 `classpath:/dev/rabbit.yml`。
    - **属性绑定**: 使用 `@ConfigurationProperties(prefix = "spring.rabbitmq")` 将加载到的配置项绑定到该类的属性上（通常是继承自 Spring Boot 的 RabbitMQProperties）。
- <mcsymbol name="GXNacosRabbitMQProperties" filename="GXNacosRabbitMQProperties.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/properties/nacos/GXNacosRabbitMQProperties.java" startline="15" type="class"></mcsymbol>:
    - 继承自 `GXRabbitMQProperties`。
    - **生效条件**: 使用 `@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})` 注解，表示仅当项目中**存在** Nacos 配置相关的类时，此 Nacos 配置才会生效。
    - **配置加载**: 通过 `@NacosConfigurationProperties(dataId = "rabbit.yml", groupId = "...")` 从 Nacos 配置中心加载 `dataId` 为 `rabbit.yml` 的配置文件。`groupId`、`serverAddr`、`namespace` 等 Nacos 连接信息会从 Spring 环境中动态获取。
    - **属性绑定**: 同样使用 `@ConfigurationProperties(prefix = "spring.rabbitmq")` 绑定配置。

### 3.3 GXRabbitMQMessageReqDto

<mcsymbol name="GXRabbitMQMessageReqDto" filename="GXRabbitMQMessageReqDto.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/dto/GXRabbitMQMessageReqDto.java" startline="12" type="class"></mcsymbol> (请注意，根据代码上下文，实际路径可能是 `cn.maple.rabbitmq.dto.GXRabbitMQMessageReqDto` 而非 `dto.inner`) 是一个关键的数据传输对象，用于封装向 RabbitMQ 发送消息时所需的所有信息。它继承自 `cn.maple.core.framework.dto.GXBaseReqDto`。

主要属性包括：

- `exchange` (String): 消息需要发送到的目标交换机名称。
- `routingKey` (String): 消息的路由键，交换机将根据此键和绑定规则将消息路由到相应的队列。
- `data` (Dict): 实际的消息内容，使用 `cn.hutool.core.lang.Dict` 类型。这允许消息体以灵活的键值对形式存在，通常会被序列化为 JSON 字符串进行传输。
- `tag` (String): 消费者标签。这是一个可选字段，通常由消费者在订阅队列时指定，用于标识消费者。在发送消息时设置此字段的场景较少，除非有特定的业务需求。
- `correlationData` (CorrelationData): 消息的关联数据，类型为 `org.springframework.amqp.rabbit.connection.CorrelationData`。这个对象主要用于实现消息发送的确认机制 (`publisher-confirms`)。发送方可以在发送消息时提供一个 `CorrelationData` 实例 (通常包含一个唯一ID)，当 RabbitMQ Broker 确认收到消息后，会在回调 (如 `GXConfirmCallback`) 中返回这个 `CorrelationData`，从而使得发送方能够将确认信息与原始消息关联起来。
- `messageProperties` (MessageProperties): AMQP 消息的属性，类型为 `org.springframework.amqp.core.MessageProperties`。通过此对象，可以设置消息的多种元数据，例如：
    - 投递模式 (Delivery Mode): 持久化 (`MessageDeliveryMode.PERSISTENT`) 或非持久化 (`MessageDeliveryMode.NON_PERSISTENT`)。
    - 消息优先级 (Priority)。
    - 消息过期时间 (Expiration)。
    - 消息头部信息 (Headers): 自定义的键值对，可用于传递额外的应用级元数据。
    - 内容类型 (Content Type): 如 `application/json`。
    - 内容编码 (Content Encoding): 如 `UTF-8`。

`GXSendRabbitMQServiceImpl` 在发送消息前，会从 `GXRabbitMQMessageReqDto` 中提取这些信息来构建和发送 AMQP 消息。

### 3.4 GXSendRabbitMQService 与 GXSendRabbitMQServiceImpl

这是用于发送 RabbitMQ 消息的核心服务接口及其实现。

- <mcsymbol name="GXSendRabbitMQService" filename="GXSendRabbitMQService.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/GXSendRabbitMQService.java" startline="12" type="class"></mcsymbol>: 定义了发送消息的操作。
    - `void sendNormalMessage(GXRabbitMQMessageReqDto message)`: 发送一个标准的消息。参数 <mcsymbol name="GXRabbitMQMessageReqDto" filename="GXRabbitMQMessageReqDto.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/dto/GXRabbitMQMessageReqDto.java" startline="12" type="class"></mcsymbol> 封装了发送消息所需的所有信息。
- <mcsymbol name="GXSendRabbitMQServiceImpl" filename="GXSendRabbitMQServiceImpl.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java" startline="23" type="class"></mcsymbol>: `GXSendRabbitMQService` 的默认实现。
    - **依赖注入**: 自动注入了在 <mcsymbol name="GXRabbitMQConfig" filename="GXRabbitMQConfig.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java" startline="30" type="class"></mcsymbol> 中配置的 `RabbitTemplate`。
    - **`sendNormalMessage` 方法核心逻辑**:
        1.  从传入的 `GXRabbitMQMessageReqDto` 对象中获取交换机名称 (`exchange`)、路由键 (`routingKey`) 和消息体 (`data`，类型为 `Dict`)。
        2.  **CorrelationData 处理**: 
            - 检查 `GXRabbitMQMessageReqDto` 中的 `correlationData` 字段。如果用户没有提供，则会自动创建一个新的 `CorrelationData` 实例，并将其 `id` 设置为一个随机生成的 UUID (`UUID.randomUUID().toString()`)。这个 `id` 对于追踪消息和在 `GXConfirmCallback` 中关联确认信息至关重要。
        3.  **消息发送**: 调用 `rabbitTemplate.convertAndSend(exchange, routingKey, data, correlationData)` 方法发送消息。
            - `convertAndSend` 方法会使用 `RabbitTemplate` 配置的 `MessageConverter` (默认为 `Jackson2JsonMessageConverter`) 将 `data` (Dict 对象) 序列化为消息体 (通常是 JSON 字符串)。
            - **消息属性处理**: `GXSendRabbitMQServiceImpl` 内部会处理 `GXRabbitMQMessageReqDto` 中的 `messageProperties`。如果用户在 `messageProperties` 中设置了如持久化、过期时间等属性，这些属性会在构建最终的 AMQP `Message` 时被应用。默认情况下，模块会设置 `Content-Type` 为 `application/json` 和 `Content-Encoding` 为 `UTF-8`。
        4.  **日志记录**: 记录消息发送的尝试日志，包括交换机、路由键和 `correlationId`。如果发送过程中抛出异常，会捕获并记录错误日志。


### 3.6 GXRabbitMQQueueListener 与 GXDefaultRabbitMQQueueListenerImpl

- <mcsymbol name="GXRabbitMQQueueListener" filename="GXRabbitMQQueueListener.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/listener/GXRabbitMQQueueListener.java" startline="3" type="interface"></mcsymbol>: 定义了处理队列消息的监听器接口。
    - `void process(Message data)`: 处理接收到的消息。
- <mcsymbol name="GXDefaultRabbitMQQueueListenerImpl" filename="GXDefaultRabbitMQQueueListenerImpl.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/listener/impl/GXDefaultRabbitMQQueueListenerImpl.java" startline="26" type="class"></mcsymbol>: `GXRabbitMQQueueListener` 的默认实现。
    - **懒加载**: 使用 `@Lazy` 注解，表示该 Bean 会被延迟初始化，直到第一次被引用时才会创建，有助于减少应用启动时间。
    - **生效条件**:
        - `@ConditionalOnExpression("'${enable-rabbitmq}'.equals('true')")`: 仅当配置文件中 `enable-rabbitmq` 属性为 `true` 时，该监听器 Bean 才会被创建。
        - `@ConditionalOnClass(name = {"org.springframework.amqp.rabbit.connection.ConnectionFactory"})`: 仅当类路径下存在 `ConnectionFactory` (即已正确引入 Spring AMQP 依赖) 时生效。
    - **队列监听**: 使用 `@RabbitListener` 注解监听队列。其监听的队列名称通过一个复杂的 SpEL 表达式动态获取：
      `queues = "#{T(cn.maple.core.framework.util.GXSpELToolUtils).callBeanMethodSpELExpression(Class.forName('cn.maple.rabbitmq.properties.GXRabbitMQProperties'), 'getDefaultQueueName', Class.forName('java.lang.String'), new Class[0])}"`
      这个表达式的含义是：
        1. 调用 `cn.maple.core.framework.util.GXSpELToolUtils` 工具类的 `callBeanMethodSpELExpression`静态方法。
        2. 该方法会尝试从 Spring 容器中获取类型为 `cn.maple.rabbitmq.properties.GXRabbitMQProperties` (或其子类，如 `GXLocalRabbitMQProperties` 或 `GXNacosRabbitMQProperties`) 的 Bean。
        3. 然后调用该 Bean 的 `getDefaultQueueName()` 方法，期望其返回一个字符串作为队列名。
        **因此，要使此默认监听器工作，开发者必须**：
            a. 在其 `GXRabbitMQProperties` 的实现类中提供一个名为 `getDefaultQueueName()` 的公共方法，该方法返回要监听的队列名字符串。
            b. **或者** (更常见的方式)，在 `rabbit.yml` 配置文件中通过 `gx.rabbitmq.default-queue-name` 属性指定队列名。`GXSpELToolUtils` 内部逻辑可能会进一步解析此配置属性值。如果此属性未配置，且 `getDefaultQueueName()` 方法也不可用或返回 `null`/空字符串，则监听器可能无法启动或监听任何队列。
    - **消息处理 (`process` 方法)**：
        1.  将接收到的 `Message` 的 body (字节数组) 转换为 UTF-8 编码的字符串。
        2.  检查消息内容是否为空白。
        3.  使用 `JSONUtil.isTypeJSON()` 判断消息内容是否为 JSON 格式。
        4.  如果是 JSON，则尝试使用 `JSONUtil.toBean(messageContent, Dict.class)` 将其转换为 `cn.hutool.core.lang.Dict` 对象，并记录调试日志。开发者可以在此基础上扩展业务逻辑。
        5.  如果不是 JSON，则直接记录原始字符串内容的调试日志。
        6.  包含了对 `AmqpException` 和通用 `Exception` 的捕获和错误日志记录。

    **重要提示**：要使 `GXDefaultRabbitMQQueueListenerImpl` 成功监听并处理消息，请确保：
    1.  在 `rabbit.yml` (或 Nacos 中的 `rabbit.yml`) 中设置 `enable-rabbitmq: true`。
    2.  通过 `gx.rabbitmq.default-queue-name` 配置项指定一个有效的队列名称，或者确保您的 `GXRabbitMQProperties` 实现类提供了可用的 `getDefaultQueueName()` 方法。

### 3.5 回调接口 (Callbacks)

`leaf-base-rabbitmq` 模块提供了三个关键的回调接口，允许开发者在消息传递的不同阶段注入自定义逻辑。这些回调的实现需要注册为 Spring Bean，模块会自动通过 `GXSpringContextUtils.getBean()` 发现并使用它们。

- **<mcsymbol name="GXConfirmCallback" filename="GXConfirmCallback.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/callback/GXConfirmCallback.java" startline="13" type="class"></mcsymbol>**: 消息发送到 Exchange 后的确认回调。
    - **接口方法**: `void confirm(CorrelationData correlationData, boolean ack, String cause)`
    - **触发时机**: 当消息被成功投递到 RabbitMQ Broker 上的 Exchange (`ack=true`) 或投递失败 (`ack=false`) 时触发。此回调的触发前提是 `publisher-confirm-type` 配置为 `correlated` 或 `simple`。
    - **参数说明**:
        - `correlationData`: 发送消息时传递的关联数据，通常包含消息的唯一ID，用于将回调与原始消息对应起来。如果发送时未提供，则可能为 `null` (尽管 <mcsymbol name="GXSendRabbitMQServiceImpl" filename="GXSendRabbitMQServiceImpl.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java" startline="23" type="class"></mcsymbol> 会自动生成)。
        - `ack`: 布尔值，`true` 表示消息已成功到达 Exchange，`false` 表示失败。
        - `cause`: 如果 `ack` 为 `false`，此参数可能包含失败的原因描述；如果 `ack` 为 `true`，则通常为 `null`。
    - **用途**: 开发者可以实现此接口来处理消息发送到 Exchange 的最终状态，例如记录日志、更新消息状态、或进行重试补偿等。确保在实现中处理好幂等性和可能的并发问题。

- **<mcsymbol name="GXReturnsCallback" filename="GXReturnsCallback.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/callback/GXReturnsCallback.java" startline="14" type="class"></mcsymbol>**: 消息未能路由到 Queue 时的返回回调。
    - **接口方法**: `void returnedMessage(ReturnedMessage returned)`
    - **触发时机**: 当消息成功发送到 Exchange，但 Exchange 无法根据自身的类型和路由键找到任何匹配的 Queue 时，此回调被触发。这通常需要生产者设置 `mandatory=true` (通过 `RabbitTemplate.setMandatory(true)`) 并且 `publisher-returns` (或 `spring.rabbitmq.publisher-returns`) 配置为 `true`。模块中 `RabbitTemplate` 默认配置了 `mandatory=true`。
    - **参数说明**:
        - `returned`: `org.springframework.amqp.core.ReturnedMessage` 对象，封装了被退回的消息的详细信息，包括：
            - `getMessage()`: 原始的 `Message` 对象。
            - `getReplyCode()`: AMQP 的回复码 (例如 312 表示 NO_ROUTE)。
            - `getReplyText()`: 回复文本描述 (例如 "NO_ROUTE")。
            - `getExchange()`: 消息发送到的目标 Exchange 名称。
            - `getRoutingKey()`: 使用的路由键。
    - **用途**: 开发者可以实现此接口来处理那些虽然成功到达 Exchange 但最终未能投递到任何 Queue 的消息。常见的处理方式包括记录日志、将消息发送到备用 Exchange/Queue、或通知相关系统进行人工干预。

- **<mcsymbol name="GXRecoveryCallback" filename="GXRecoveryCallback.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/callback/GXRecoveryCallback.java" startline="15" type="class"></mcsymbol>**: 消息重试耗尽后的恢复回调 (配合 Spring Retry 使用)。
    - **接口方法**: `Object recover(RetryContext retryContext) throws Exception`
    - **触发时机**: 当使用 Spring AMQP 的重试机制 (例如通过 `RabbitTemplate` 配置的 `RetryTemplate`) 发送消息，并且所有配置的重试尝试都失败后，此回调被触发。这通常用于处理持久性故障，在多次重试后仍然无法成功发送消息的情况。
    - **参数说明**:
        - `retryContext`: `org.springframework.retry.RetryContext` 对象，包含了关于重试操作的上下文信息，例如：
            - `getLastThrowable()`: 最后一次重试尝试抛出的异常。
            - `getRetryCount()`: 已经执行的重试次数。
            - 还可以通过 `getAttribute(String name)` 获取在重试过程中设置的自定义属性。
    - **返回值说明**:
        - 如果恢复操作成功并产生了一个结果，可以返回该结果。这个结果会成为 `RetryTemplate.execute()` 方法的最终返回值。
        - 如果恢复操作没有特定的返回值，或者只是执行了一些副作用 (如记录日志、发送到死信队列)，可以返回 `null`。
        - 如果恢复操作本身也失败了，可以抛出异常。
    - **用途**: 实现此接口允许开发者定义在消息发送经过多次重试仍然失败后的最终处理逻辑。常见的恢复策略包括：
        - 将消息持久化到数据库或专门的错误队列 (例如死信队列) 以供后续分析和处理。
        - 发送告警通知给运维或开发人员。
        - 执行业务上的降级逻辑。
        - 记录详细的错误信息和上下文。
    - **线程安全**: 实现类需要保证线程安全，因为回调可能在并发环境中被调用。

**实现与注册**: 开发者需要创建实现这些接口的类，并将它们声明为 Spring Bean (例如使用 `@Component` 或在 `@Configuration` 类中通过 `@Bean` 方法定义)。模块的 <mcsymbol name="GXRabbitMQConfig" filename="GXRabbitMQConfig.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java" startline="30" type="class"></mcsymbol> 会在初始化 `RabbitTemplate` 时，尝试通过 `GXSpringContextUtils.getBean()` 从 Spring 容器中获取这些回调接口的 Bean。如果找到了对应的 Bean，就会将其设置到 `RabbitTemplate` 中。

## 4. 使用示例

### 4.1 发送普通消息

```java
import cn.hutool.core.lang.Dict;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.service.GXSendRabbitMQService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class MyMessageProducer {

    @Resource
    private GXSendRabbitMQService sendRabbitMQService;

    public void sendMyMessage(String exchange, String routingKey, String messageContent) {
        GXRabbitMQMessageReqDto messageDto = new GXRabbitMQMessageReqDto();
        messageDto.setExchange(exchange);
        messageDto.setRoutingKey(routingKey);
        messageDto.setData(Dict.create().set("content", messageContent));
        // 可以设置 CorrelationData 和 MessageProperties
        // messageDto.setCorrelationData(new CorrelationData("my-unique-id"));
        // messageDto.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        sendRabbitMQService.sendNormalMessage(messageDto);
    }
}
```

## 4. 如何实现自定义的消息监听器

`leaf-base-rabbitmq` 模块提供了两种主要方式来实现自定义的消息监听器：

### 4.1 实现 `GXRabbitMQQueueListener` 接口

这是模块推荐的标准化方式。开发者可以创建一个类实现 <mcsymbol name="GXRabbitMQQueueListener" filename="GXRabbitMQQueueListener.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/listener/GXRabbitMQQueueListener.java" startline="3" type="interface"></mcsymbol> 接口，并将其注册为 Spring Bean。

```java
package com.example.listener;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.rabbitmq.listener.GXRabbitMQQueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component("customQueueListener") // 注册为 Spring Bean
public class CustomQueueListener implements GXRabbitMQQueueListener {

    @Override
    public void process(Message message) {
        String messageContent = new String(message.getBody());
        log.info("CustomQueueListener 接收到消息: {}", messageContent);
        try {
            if (JSONUtil.isTypeJSON(messageContent)) {
                Dict data = JSONUtil.toBean(messageContent, Dict.class);
                log.info("解析为 Dict: {}", data);
                // 在这里添加您的业务处理逻辑
                // 例如：根据 data 中的内容调用相应的服务
            } else {
                log.warn("接收到的消息不是有效的 JSON 格式: {}", messageContent);
            }
        } catch (Exception e) {
            log.error("处理消息时发生错误: ", e);
            // 考虑异常处理策略，例如消息重试或发送到死信队列
        }
    }
}
```

**关键点**：

- **注册为 Bean**: 必须将实现类注册为 Spring Bean，例如使用 `@Component`、`@Service` 等注解。
- **`process` 方法**: 实现 `process(Message message)` 方法来处理接收到的原始 `Message` 对象。您可以从 `Message` 对象中获取消息体 (`message.getBody()`) 和消息属性 (`message.getMessageProperties()`)。
- **与 `GXDefaultRabbitMQQueueListenerImpl` 的关系**: 如果您只想使用自定义的监听器处理特定队列，并且不希望 <mcsymbol name="GXDefaultRabbitMQQueueListenerImpl" filename="GXDefaultRabbitMQQueueListenerImpl.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/listener/impl/GXDefaultRabbitMQQueueListenerImpl.java" startline="26" type="class"></mcsymbol> 监听 `gx.rabbitmq.default-queue-name` 指定的队列，可以不配置 `gx.rabbitmq.default-queue-name` 或者将其指向一个不由自定义监听器处理的队列。
- **队列绑定**: 这种方式定义的监听器本身并不直接绑定到某个队列。您需要额外使用 Spring AMQP 的标准方式 (例如 `@RabbitListener` 注解，或通过 `SimpleMessageListenerContainer` 配置) 来将这个 Bean 的 `process` 方法绑定到具体的队列上。 **然而，更常见的做法是结合下面的 `@RabbitListener` 注解使用。**

### 4.2 使用 `@RabbitListener` 注解

Spring AMQP 提供的 `@RabbitListener` 注解是声明消息监听器的强大而灵活的方式。您可以将此注解直接用在实现了 `GXRabbitMQQueueListener` 接口的 Bean 的方法上，或者用在任何其他 Spring Bean 的方法上。

```java
package com.example.listener;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnotherCustomListener {

    // 监听名为 "my.specific.queue" 的队列
    @RabbitListener(queues = "my.specific.queue")
    public void handleMessage(Message message) { // 可以直接接收 Message 对象
        String messageContent = new String(message.getBody());
        log.info("AnotherCustomListener (Message) 接收到来自 'my.specific.queue' 的消息: {}", messageContent);
        // 处理逻辑...
    }

    // 监听名为 "my.another.queue" 的队列，并自动进行消息转换
    // 需要确保 RabbitTemplate 配置了合适的消息转换器 (本模块默认为 Jackson2JsonMessageConverter)
    // 并且消息体可以被转换为 Dict 类型
    @RabbitListener(queues = "my.another.queue")
    public void handleConvertedMessage(Dict data) { // 也可以直接接收转换后的对象
        log.info("AnotherCustomListener (Dict) 接收到来自 'my.another.queue' 的消息: {}", data);
        // 处理逻辑...
    }

    // 监听 "my.class.level.queue"，可以与 @RabbitHandler 结合使用进行方法分发
    @RabbitListener(queues = "my.class.level.queue")
    public static class MultiTypeListener {

        @RabbitHandler
        public void handleString(String messageContent) {
            log.info("MultiTypeListener (String) 接收到消息: {}", messageContent);
        }

        @RabbitHandler
        public void handleDict(Dict data) {
            log.info("MultiTypeListener (Dict) 接收到消息: {}", data);
        }

        @RabbitHandler(isDefault = true)
        public void handleDefault(Object object) {
            log.info("MultiTypeListener (Default) 接收到未知类型消息: {}", object);
        }
    }
}
```

**关键点**：

- **`@RabbitListener` 注解**: 
    - `queues`: 指定要监听的队列名称。可以是一个或多个队列。
    - `bindings`: 更复杂的场景下，可以通过 `@QueueBinding`、`@Exchange`、`@Queue` 来动态声明并绑定队列、交换机。
    - `containerFactory`: 可以指定自定义的 `RabbitListenerContainerFactory` 来控制并发、事务、消息转换等行为。
- **方法签名**: 
    - 可以直接接收 `org.springframework.amqp.core.Message`。
    - 也可以接收自动转换后的消息体类型 (如 `String`, `byte[]`, 或自定义的 POJO，如 `Dict`)，前提是配置了相应的 `MessageConverter`。
    - 还可以包含 `com.rabbitmq.client.Channel` 和 `@Header` 注解的参数来获取消息头信息。
- **`@RabbitHandler`**: 当一个类上有多个方法被 `@RabbitHandler` 注解，并且该类被 `@RabbitListener` 注解时，Spring AMQP 会根据消息的类型将消息分发到匹配的 `@RabbitHandler` 方法。
- **启用 `@RabbitListener`**: 需要在 Spring Boot 的主配置类或任何 `@Configuration` 类上添加 `@EnableRabbit` 注解。

**推荐做法**: 

对于大多数场景，直接使用 `@RabbitListener` 注解在自定义的 Spring Bean 方法上是最简洁和强大的方式。如果需要统一的消息处理入口或共享某些逻辑，可以先实现 `GXRabbitMQQueueListener` 接口，然后在实现类的方法上使用 `@RabbitListener`。

确保在 `rabbit.yml` (或 Nacos) 中正确配置了 RabbitMQ 的连接信息，并且要监听的队列、交换机和绑定关系已在 RabbitMQ 服务器上正确声明 (可以通过 `RabbitAdmin` 自动声明，或手动创建)。
```

## 5. 如何实现自定义的回调

`leaf-base-rabbitmq` 模块允许开发者通过实现特定的回调接口并将其注册为 Spring Bean 来定制消息发送过程中的确认、返回和重试恢复逻辑。模块的 <mcsymbol name="GXRabbitMQConfig" filename="GXRabbitMQConfig.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java" startline="30" type="class"></mcsymbol> 会自动查找这些 Bean 并配置到 `RabbitTemplate`。

### 5.1 实现 `GXConfirmCallback`

用于处理消息发送到 Exchange 后的确认。

```java
package com.example.callback;

import cn.maple.rabbitmq.callback.GXConfirmCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Component;

@Slf4j
@Component // 必须注册为 Spring Bean
public class MyConfirmCallback implements GXConfirmCallback {

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        String id = correlationData != null ? correlationData.getId() : "N/A";
        if (ack) {
            log.info("消息发送成功确认, ID: {}", id);
            // 例如：更新数据库中消息的状态为“已发送到Exchange”
        } else {
            log.error("消息发送失败确认, ID: {}, 原因: {}", id, cause);
            // 例如：记录失败日志，尝试重发，或触发告警
        }
    }
}
```

### 5.2 实现 `GXReturnsCallback`

用于处理消息成功发送到 Exchange 但无法路由到任何 Queue 时的返回。

```java
package com.example.callback;

import cn.maple.rabbitmq.callback.GXReturnsCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.stereotype.Component;

@Slf4j
@Component // 必须注册为 Spring Bean
public class MyReturnsCallback implements GXReturnsCallback {

    @Override
    public void returnedMessage(ReturnedMessage returned) {
        log.warn("消息被退回: message=({}), replyCode=({}), replyText=({}), exchange=({}), routingKey=({})",
                new String(returned.getMessage().getBody()),
                returned.getReplyCode(),
                returned.getReplyText(),
                returned.getExchange(),
                returned.getRoutingKey());
        // 例如：将消息保存到特定队列或数据库，进行人工处理
    }
}
```

### 5.3 实现 `GXRecoveryCallback`

用于处理 Spring Retry 重试耗尽后的恢复逻辑。

```java
package com.example.callback;

import cn.maple.rabbitmq.callback.GXRecoveryCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryContext;
import org.springframework.stereotype.Component;
// 如果需要获取原始Message或进行重新发布等操作，可能需要引入以下类
// import org.springframework.amqp.core.Message;
// import org.springframework.amqp.rabbit.core.RabbitTemplate;
// import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
// import jakarta.annotation.Resource;

@Slf4j
@Component // 必须注册为 Spring Bean
public class MyRecoveryCallback implements GXRecoveryCallback {

    // // 如果计划在恢复逻辑中重新发送消息 (例如到死信队列), 可以注入 RabbitTemplate
    // @Resource
    // private RabbitTemplate rabbitTemplate;
    // private String deadLetterExchange = "my.dlx.exchange";
    // private String deadLetterRoutingKey = "my.dlx.routing.key";

    @Override
    public Object recover(RetryContext retryContext) throws Exception {
        // Spring AMQP 通常会将原始的 Message 对象作为 "message" 属性存储在 RetryContext 中
        Object messageAttribute = retryContext.getAttribute(RetryContext.MESSAGE);
        Throwable lastThrowable = retryContext.getLastThrowable();
        int retryCount = retryContext.getRetryCount();

        log.error("消息发送重试 {} 次后仍然失败，执行恢复逻辑。最终异常: ", 
                  retryCount, lastThrowable);

        if (messageAttribute instanceof org.springframework.amqp.core.Message) {
            org.springframework.amqp.core.Message amqpMessage = (org.springframework.amqp.core.Message) messageAttribute;
            log.error("失败的消息内容: {}", new String(amqpMessage.getBody()));
            log.error("失败的消息属性: {}", amqpMessage.getMessageProperties());
            
            // 在这里实现您的恢复逻辑，例如：
            // 1. 将消息发送到死信队列 (DLX)
            //    RepublishMessageRecoverer recoverer = new RepublishMessageRecoverer(rabbitTemplate, deadLetterExchange, deadLetterRoutingKey);
            //    recoverer.recover(amqpMessage, lastThrowable);
            //    log.info("消息已发送到死信交换机: {}", deadLetterExchange);

            // 2. 将消息信息持久化到数据库
            //    saveFailedMessageToDatabase(amqpMessage, lastThrowable.getMessage());

            // 3. 发送告警通知
            //    alertService.sendAlert("RabbitMQ message send failed after retries: " + new String(amqpMessage.getBody()));
        } else {
            log.warn("RetryContext 中未找到预期的 Message 对象，实际属性值: {}", messageAttribute);
        }

        // 返回值通常为 null，除非您的 RetryTemplate.execute() 调用期望一个特定的返回结果
        return null; 
    }
}
```

**关键步骤**：

1.  **创建实现类**: 分别创建实现 <mcsymbol name="GXConfirmCallback" filename="GXConfirmCallback.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/callback/GXConfirmCallback.java" startline="13" type="class"></mcsymbol>, <mcsymbol name="GXReturnsCallback" filename="GXReturnsCallback.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/callback/GXReturnsCallback.java" startline="14" type="class"></mcsymbol>, 或 <mcsymbol name="GXRecoveryCallback" filename="GXRecoveryCallback.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/callback/GXRecoveryCallback.java" startline="15" type="class"></mcsymbol> 接口的类。
2.  **注册为 Spring Bean**: 使用 `@Component` 或其他 Bean 定义方式 (如 `@Configuration` 类中的 `@Bean` 方法) 将这些实现类注册到 Spring 容器中。
3.  **自动配置**: <mcsymbol name="GXRabbitMQConfig" filename="GXRabbitMQConfig.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java" startline="30" type="class"></mcsymbol> 会在配置 `RabbitTemplate` 时，通过 `GXSpringContextUtils.getBean()` 自动查找并注入这些回调的 Bean。如果容器中存在这些类型的 Bean，它们就会被设置到 `RabbitTemplate` 上。

**注意事项**：

-   **唯一性**: 通常情况下，每个回调接口在 Spring 容器中应该只有一个 Bean 实例。如果存在多个同类型的 Bean，`GXSpringContextUtils.getBean()` 的行为可能取决于具体的实现 (可能抛出 `NoUniqueBeanDefinitionException` 或获取到其中一个)。如果需要更复杂的选择逻辑，可能需要自定义 `GXRabbitMQConfig`。
-   **`publisher-confirm-type`**: 要使 `GXConfirmCallback` 生效，必须在 `rabbit.yml` (或 Nacos) 中将 `spring.rabbitmq.publisher-confirm-type` 设置为 `correlated` (推荐) 或 `simple`。
-   **`publisher-returns` 和 `mandatory`**: 要使 `GXReturnsCallback` 生效，必须将 `spring.rabbitmq.publisher-returns` 设置为 `true`，并且 `spring.rabbitmq.template.mandatory` 设置为 `true` (模块已默认设置 `mandatory` 为 `true`)。
-   **Retry 配置**: 要使 `GXRecoveryCallback` 生效，必须在 `RabbitTemplate` 上配置了重试策略 (例如，通过 `spring.rabbitmq.template.retry.*` 属性配置)。

通过实现这些回调，开发者可以精细地控制消息发送的可靠性，并根据业务需求处理各种异常情况。

确保这些回调实现类被 Spring 容器扫描并注册为 Bean。

## 5. 配置参数说明 (`rabbit.yml` / Nacos)

以下配置参数可以在项目的 `application.yml` (或 `application.properties`) 中，或者通过 Nacos 等配置中心进行配置。它们主要基于 Spring Boot AMQP 的标准配置，并包含了一些模块特有的配置项。

```yaml
spring:
  rabbitmq:
    host: localhost                     # RabbitMQ 服务器地址 (默认: localhost)
    port: 5672                          # RabbitMQ 服务器端口 (默认: 5672)
    username: guest                     # 连接 RabbitMQ 的用户名 (默认: guest)
    password: guest                     # 连接 RabbitMQ 的密码 (默认: guest)
    virtual-host: /                     # RabbitMQ 的虚拟主机 (默认: /)
    
    # 发送方确认机制 (Publisher Confirms & Returns)
    publisher-confirm-type: correlated  # 发送方确认模式。
                                        # - none: 禁用发送方确认。
                                        # - correlated: 异步回调模式。发送消息时提供 CorrelationData，Broker 确认后会回调 RabbitTemplate.ConfirmCallback。
                                        # - simple: 同步等待模式 (不推荐用于生产环境，性能较低)。
                                        # (默认: none，但本模块 GXRabbitMQConfig 中会尝试配置 GXConfirmCallback，建议设置为 correlated)
    publisher-returns: true             # 开启投递失败返回机制 (Publisher Returns)。
                                        # 当消息成功发送到 Exchange 但无法路由到任何 Queue 时，如果此项为 true，
                                        # Broker 会将消息返回给生产者，并触发 RabbitTemplate.ReturnsCallback。
                                        # (默认: false，但本模块 GXRabbitMQConfig 中会尝试配置 GXReturnsCallback，建议设置为 true)
    template:
      mandatory: true                 # 配合 publisher-returns=true 使用。 
                                        # - true: 如果消息不能被路由，则调用 ReturnsCallback。Exchange 会将消息返回给发送者。
                                        # - false: 如果消息不能被路由，消息会被丢弃或进入死信队列 (如果配置了)。
                                        # (本模块 GXRabbitMQConfig 中默认将 RabbitTemplate 的 mandatory 设置为 true，以确保 GXReturnsCallback 能被触发)
      # 发送重试机制 (Retry)
      retry:
        enabled: true                 # 是否为 RabbitTemplate 开启发送重试机制 (默认: false)。
                                        # 如果启用，发送失败时会自动重试。
        initial-interval: 1000ms      # 初始重试间隔时间 (默认: 1s)。
        max-attempts: 3               # 最大重试次数 (包括首次尝试) (默认: 3)。
        max-interval: 10000ms         # 最大重试间隔时间 (默认: 10s)。
        multiplier: 2                 # 重试间隔的乘数因子 (默认: 1.0，即不增加)。设置为大于1的值以实现指数退避。
        # stateles: true (默认) 或 false。如果为 true，则重试不依赖于 RetryContext。

    # 监听器/消费者通用配置 (Listener Settings)
    listener:
      # 适用于 SimpleMessageListenerContainer (例如通过 @RabbitListener 注解创建的默认容器)
      simple:
        acknowledge-mode: auto        # 消费者消息确认模式。
                                        # - auto: 自动确认。消息一旦被消费者成功接收（没有抛出异常），Broker 就会认为消息已被处理并将其从队列中移除。
                                        # - manual: 手动确认。消费者需要在代码中显式调用 channel.basicAck(), channel.basicNack(), 或 channel.basicReject()。
                                        # - none: 不确认 (等同于 auto，但不推荐直接使用 none)。
                                        # (默认: auto)
        concurrency: 1                # 最小并发消费者数量 (默认: 1)。
        max-concurrency: 1            # 最大并发消费者数量 (默认: 1)。如果希望增加并发处理能力，可以调高此值。
        prefetch: 250                 # 每个消费者一次可以从 Broker 拉取并缓存的消息数量 (QoS) (默认: 250)。
                                        # 合理设置此值可以优化性能。太小可能导致网络开销，太大可能导致消息在一个消费者积压。
        default-requeue-rejected: true # 当消息被拒绝 (nack/reject) 时，是否重新入队 (默认: true)。
                                        # 如果为 false，被拒绝的消息会根据 Broker 的策略被丢弃或进入死信队列。
        retry:
          enabled: true               # 是否为消费者开启接收重试机制 (默认: false)。
                                        # 如果启用，当监听器方法抛出异常时，消息会进行重试。
          # initial-interval, max-attempts, max-interval, multiplier: 同发送端重试配置。
          # stateless: true (默认) 或 false。
          # recoverer: 可以指定一个 Bean 名称，该 Bean 实现了 MessageRecoverer 接口，用于处理重试耗尽后的消息。

      # 适用于 DirectMessageListenerContainer (通常需要显式配置 ContainerFactory)
      direct:
        # 配置与 simple 类似，但应用于 DirectMessageListenerContainer。
        acknowledge-mode: auto
        consumers-per-queue: 1        # 每个队列的消费者数量 (默认: 1)
        prefetch: 250
        # ... 其他配置与 simple 类似

# 模块自定义配置 (通常在 application.yml 或 Nacos 中配置)
# 这些配置主要影响 cn.maple.rabbitmq.listener.impl.GXDefaultRabbitMQQueueListenerImpl 的行为
enable-rabbitmq: true # (布尔值) 是否启用 RabbitMQ 相关功能，特别是 GXDefaultRabbitMQQueueListenerImpl。
                      # - true: (默认值) 模块会尝试初始化 GXDefaultRabbitMQQueueListenerImpl (如果相关条件满足)。
                      # - false: 禁用 GXDefaultRabbitMQQueueListenerImpl 的自动配置和启动。

gx:
  rabbitmq:
    default-queue-name: "your.default.queue.name" # (字符串，可选)
                                                  # GXDefaultRabbitMQQueueListenerImpl 默认监听的队列名称。
                                                  # 此配置项的值会被 cn.maple.rabbitmq.properties.GXRabbitMQProperties 的默认实现读取。
                                                  # 如果不配置此项，并且 GXRabbitMQProperties 的实现也没有提供默认队列名，
                                                  # GXDefaultRabbitMQQueueListenerImpl 将不会启动，因为它不知道要监听哪个队列。
                                                  # 您可以通过实现自定义的 GXRabbitMQProperties Bean 来动态提供此队列名，
                                                  # 例如从数据库或其他配置源加载。
                                                  # SpEL 表达式 '#{@customRabbitMQProperties.getDefaultQueueName()}' 
                                                  # 在 GXDefaultRabbitMQQueueListenerImpl 中用于获取此值。
```

**重要提示**：

*   **Nacos 配置**: 如果使用 Nacos 作为配置中心，上述 `spring.rabbitmq.*` 和模块自定义配置（`enable-rabbitmq`, `gx.rabbitmq.*`）都应该在 Nacos 中进行配置。Nacos 中的 Data ID 通常是 `应用名.yml` (例如 `my-app.yml`) 或 `应用名-profile.yml` (例如 `my-app-dev.yml`)。
*   **默认值**: 上述注释中提到的默认值是 Spring Boot AMQP 或本模块设定的。实际生效的配置取决于您的项目配置、Spring Boot 版本以及本模块的实现。
*   **回调与配置的联动**: 
    *   `GXConfirmCallback` 的有效性依赖于 `spring.rabbitmq.publisher-confirm-type` 被设置为 `correlated` 或 `simple`。
    *   `GXReturnsCallback` 的有效性依赖于 `spring.rabbitmq.publisher-returns` 和 `spring.rabbitmq.template.mandatory` 都被设置为 `true`。
    *   `GXRecoveryCallback` (用于发送端重试) 的有效性依赖于 `spring.rabbitmq.template.retry.enabled` 为 `true`。
    *   消费端的重试恢复逻辑 (如果使用 Spring AMQP 的重试机制) 可以通过 `spring.rabbitmq.listener.simple.retry.recoverer` (或 `direct`) 指定一个 `MessageRecoverer` Bean。

更多 Spring AMQP 配置的详细信息，请参考 <mcurl name="Spring Boot官方文档 - AMQP章节" url="https://docs.spring.io/spring-boot/docs/current/reference/html/messaging.html#messaging.amqp"></mcurl>。

## 6. 最佳实践

为了更高效、更可靠地使用 `leaf-base-rabbitmq` 模块和 RabbitMQ，建议遵循以下最佳实践：

1.  **消息幂等性 (Idempotency)**：
    *   **核心思想**：确保消费者对同一条消息处理多次和处理一次的效果是相同的。
    *   **为何重要**：网络问题、Broker 重启、消费者故障重试等都可能导致消息重复投递。
    *   **实现方式**：
        *   使用唯一业务ID：在消息体或消息属性中包含一个唯一标识，消费端记录已处理的ID，遇到重复ID则跳过。
        *   数据库唯一约束：利用数据库的唯一键约束来防止重复插入或更新。
        *   状态机：对于复杂流程，通过状态判断避免重复执行。

2.  **死信队列 (Dead Letter Exchange/Queue - DLX/DLQ)**：
    *   **用途**：收集处理失败 (如达到最大重试次数后仍未成功、消息被拒绝且未重新入队)、消息过期 (TTL) 或队列达到最大长度而被丢弃的消息。
    *   **配置**：为业务队列配置 `x-dead-letter-exchange` 和可选的 `x-dead-letter-routing-key` 参数，指向一个专门的死信交换机，该交换机再将消息路由到死信队列。
    *   **好处**：便于问题排查、数据恢复或手动补偿，而不是让问题消息丢失。
    *   **本模块**：虽然模块本身不直接创建DLX/DLQ，但你可以通过 `RabbitAdmin` 或手动在Broker上配置它们，并结合消费端的重试和拒绝逻辑来使用。

3.  **消息持久化 (Message Persistence)**：
    *   **确保不丢失**：对于关键业务消息，必须确保其在 RabbitMQ Broker 重启后依然存在。
    *   **三层持久化**：
        1.  **Exchange 持久化**：声明 Exchange 时设置 `durable = true`。
        2.  **Queue 持久化**：声明 Queue 时设置 `durable = true`。
        3.  **Message 持久化**：发送消息时，设置 `MessageProperties` 的 `deliveryMode` 为 `MessageDeliveryMode.PERSISTENT` (值为2)。
    *   **本模块**：`GXSendRabbitMQServiceImpl` 默认不会强制设置持久化，你需要在构造 `GXRabbitMQMessageReqDto` 时，通过其 `messageProperties` 字段设置 `setDeliveryMode(MessageDeliveryMode.PERSISTENT)`。

4.  **合理的 Prefetch 值 (QoS)**：
    *   **`spring.rabbitmq.listener.simple.prefetch`** (或 `direct` 下的同名配置)。
    *   **含义**：消费者在向 Broker 请求下一批消息前，一次可以接收并缓存（未ack）的最大消息数量。
    *   **影响**：
        *   **过小**：可能导致消费者频繁请求，网络开销增加，吞吐量下降。
        *   **过大**：如果某个消费者处理慢或宕机，大量消息会积压在该消费者本地，其他消费者无法获取，导致整体处理延迟，甚至消息丢失 (如果消费者未持久化这些消息且未ack就崩溃)。
    *   **建议**：根据消息处理的平均耗时、网络状况和消费者能力进行测试和调整。通常从一个适中的值开始 (如几十到几百)，逐步优化。

5.  **监控与告警 (Monitoring & Alerting)**：
    *   **关键指标**：
        *   队列深度 (Ready/Unacked messages)。
        *   消息发布/消费速率。
        *   消费者数量和状态。
        *   连接数、通道数。
        *   Broker 资源使用 (CPU, Memory, Disk, File Descriptors)。
    *   **工具**：RabbitMQ Management Plugin, Prometheus + Grafana, ELK Stack 等。
    *   **告警**：针对关键指标设置阈值告警，及时发现和处理问题。

6.  **全面的异常处理与日志记录**：
    *   **生产者端**：
        *   实现并注册 `GXConfirmCallback` 和 `GXReturnsCallback`，详细记录发送成功、失败及未路由的情况。
        *   如果启用了发送端重试 (`spring.rabbitmq.template.retry.enabled=true`)，考虑实现 `GXRecoveryCallback` 来处理最终失败的消息。
    *   **消费者端**：
        *   在 `GXRabbitMQQueueListener` 的实现 (如 `GXDefaultRabbitMQQueueListenerImpl` 或自定义监听器) 的 `process` 方法中，对业务逻辑进行充分的 `try-catch`，记录详细的错误信息和消息内容，避免因单个消息处理异常导致整个消费者线程中断。
        *   根据 `acknowledge-mode` (auto/manual) 和业务需求，决定如何处理异常：是 `nack` 并重新入队 (可能导致无限重试，需配合DLX)，还是 `nack` 且不重新入队 (消息进入DLX或被丢弃)，或者记录后 `ack` (标记为已处理)。

7.  **连接管理与心跳 (Connection Management & Heartbeats)**：
    *   **连接池**：Spring AMQP 默认会管理连接和通道，通常不需要直接操作。
    *   **心跳** (`spring.rabbitmq.requested-heartbeat`)：客户端和 Broker 之间通过心跳检测连接是否存活。合理设置心跳间隔可以防止因网络瞬断或防火墙策略导致连接被意外关闭。默认值通常适用，但在高延迟网络下可能需要调整。

8.  **配置与环境分离**：
    *   使用 Spring Profiles (`dev`, `test`, `prod` 等) 结合不同的 `rabbit.yml` 文件，或利用 Nacos 等配置中心管理不同环境的 RabbitMQ 配置，避免硬编码。

9.  **安全考虑**：
    *   使用强密码，并为不同应用分配具有最小必要权限的 RabbitMQ 用户和 vhost。
    *   考虑启用 SSL/TLS 加密客户端与 Broker 之间的通信 (`spring.rabbitmq.ssl.enabled=true` 及相关证书配置)。

10. **避免长任务阻塞消费者线程**：
    *   如果消息处理逻辑包含耗时较长的操作 (如复杂的计算、外部API调用)，考虑将其异步化，例如提交到单独的线程池处理，以避免阻塞 RabbitMQ 的消费者线程，从而影响其他消息的消费。

11. **序列化与反序列化**：
    *   **一致性**：确保生产者和消费者使用相同的序列化机制和消息体结构。本模块默认使用 Jackson JSON。
    *   **`Jackson2JsonMessageConverter` 的信任包**：<mcsymbol name="GXRabbitMQConfig" filename="GXRabbitMQConfig.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java" startline="26" type="class"></mcsymbol> 中配置了信任的包路径 (`cn.hutool.core`)。如果你的消息体 DTO 不在此包下，需要通过自定义 `MessageConverter` 或修改 `DefaultClassMapper` 的配置来添加信任的包，否则反序列化会失败。

通过遵循这些最佳实践，可以显著提高 RabbitMQ 应用的可靠性、性能和可维护性。

## 7. 常见问题与故障排查

在使用 `leaf-base-rabbitmq` 模块时，可能会遇到以下常见问题。本节提供了一些排查思路。

### 7.1 消息发送失败

- **症状**：应用日志报错，`GXConfirmCallback` 的 `confirm` 方法参数 `ack` 为 `false`，或 `GXReturnsCallback` 被触发。
- **排查步骤**：
  1.  **网络与服务状态**：
      *   确认 RabbitMQ 服务器是否正在运行且网络可达 (ping, telnet `host` `port`)。
      *   检查 RabbitMQ Management Plugin，查看 Overview 和 Connections 页面。
  2.  **配置检查** (`application.yml` 或 Nacos)：
      *   `spring.rabbitmq.host`, `port`, `username`, `password`, `virtual-host` 是否正确？
      *   目标 Exchange 名称、类型是否正确？
      *   RoutingKey 是否与 Exchange 和 Queue 的绑定匹配？
  3.  **Exchange 和 Queue 状态** (通过 Management Plugin 或 `rabbitmqctl`)：
      *   Exchange 是否存在且类型正确？
      *   如果 `publisher-returns=true` 且 `mandatory=true`，`GXReturnsCallback` 被触发，通常意味着 Exchange 存在，但没有匹配的 Queue (或绑定键错误) 来接收消息。
      *   如果 `GXConfirmCallback` 返回 `ack=false`，`cause` 参数可能包含 Broker 返回的错误原因 (例如，Exchange 不存在)。
  4.  **生产者日志**：
      *   仔细查看应用日志中 `GXConfirmCallback` 和 `GXReturnsCallback` 的输出，它们通常包含失败的详细信息 (correlation data, reply code, reply text, exchange, routing key)。
      *   检查是否有 `AmqpException` 或其子类的异常栈信息。
  5.  **权限问题**：
      *   确认配置的 RabbitMQ 用户是否有权限向目标 Exchange 发布消息，以及是否有权限访问该 Virtual Host。
  6.  **资源限制**：
      *   RabbitMQ Broker 是否达到资源上限 (如磁盘空间、内存)？

### 7.2 消息消费不到或消费延迟

- **症状**：消息已发送到队列 (在 Management Plugin 中可见)，但消费者应用没有处理，或处理非常缓慢。
- **排查步骤**：
  1.  **消费者连接状态**：
      *   消费者应用是否已启动并成功连接到 RabbitMQ？检查应用日志和 Management Plugin 的 Consumers 列表。
      *   `enable-rabbitmq: true` 是否配置正确 (如果使用 `GXDefaultRabbitMQQueueListenerImpl`)？
  2.  **队列和绑定**：
      *   监听的队列名称 (`gx.rabbitmq.default-queue-name` 或 `@RabbitListener` 中指定的队列) 是否与消息实际进入的队列一致？
      *   Exchange 和 Queue 之间的绑定 (Binding Key) 是否正确？
  3.  **消费者配置**：
      *   `spring.rabbitmq.listener.simple.acknowledge-mode`：如果是 `manual`，是否忘记了 `ack/nack`？未 `ack` 的消息在消费者重启后会重新投递 (如果持久化)。
      *   `spring.rabbitmq.listener.simple.concurrency` 和 `max-concurrency`：并发设置是否过低导致处理不过来？
      *   `spring.rabbitmq.listener.simple.prefetch`：Prefetch 值是否设置过小或过大？
  4.  **消费者日志**：
      *   是否有反序列化错误 (如 `MessageConversionException`)？确保生产者和消费者使用兼容的消息格式和转换器。
      *   监听器方法 (`onMessage` 或 `@RabbitListener` 方法) 是否有未捕获的异常导致消息处理失败并不断重试 (如果配置了重试)？
      *   如果配置了消费端重试，并且达到最大次数后没有配置 `MessageRecoverer` 或死信队列，消息可能会被丢弃或不断重试。
  5.  **阻塞或慢消费**：
      *   监听器方法中是否有长时间阻塞的操作 (如慢SQL、外部HTTP调用超时)？这会拉低整体消费速率。
      *   检查消费者应用的 CPU、内存、IO 是否有瓶颈。
  6.  **死信队列 (DLX)**：
      *   检查消息是否因为某些原因 (如处理异常且 `requeue=false`、TTL过期) 进入了死信队列。

### 7.3 连接问题

- **症状**：应用启动时或运行中断开连接，日志中出现 `ConnectException`, `TimeoutException`, `AuthenticationFailureException` 等。
- **排查步骤**：
  1.  **基础配置**：再次核对 `spring.rabbitmq.host`, `port`, `username`, `password`, `virtual-host`。
  2.  **网络防火墙**：确认应用服务器与 RabbitMQ 服务器之间的网络策略，防火墙是否允许指定端口的 TCP 连接。
  3.  **RabbitMQ 服务器状态**：
      *   服务器是否运行？
      *   是否有连接数限制 (`max_connections`) 且已达到上限？
      *   用户凭证是否正确，用户是否被锁定或删除？
  4.  **SSL/TLS 配置** (如果启用)：
      *   `spring.rabbitmq.ssl.enabled=true` 是否正确设置？
      *   Keystore 和 Truststore 的路径、密码、类型是否正确？证书是否有效？
  5.  **心跳超时 (Heartbeats)**：
      *   `spring.rabbitmq.requested-heartbeat` (客户端请求) 和 RabbitMQ Broker 配置的心跳间隔是否协调？网络延迟较高时，可能需要调整心跳间隔以避免误判为连接丢失。
  6.  **客户端资源**：
      *   客户端机器是否资源不足 (如文件句柄耗尽) 导致无法建立新连接？

### 7.4 模块特定问题

- **`GXDefaultRabbitMQQueueListenerImpl` 未启动**：
  - 确认 `enable-rabbitmq: true` (默认即为 true)。
  - 确认 `gx.rabbitmq.default-queue-name` 已配置，或者自定义的 `GXRabbitMQProperties` Bean 能够提供一个有效的队列名。
  - 检查是否有 `RabbitListenerContainerFactory` 类型的 Bean (`gxRabbitListenerContainerFactory`) 成功注册到 Spring 容器中。
- **自定义回调未生效**：
  - 确认回调实现类已正确实现接口 (`GXConfirmCallback`, `GXReturnsCallback`, `GXRecoveryCallback`)。
  - 确认回调实现类已注册为 Spring Bean (例如使用 `@Component` 或 `@Service` 注解)。
  - 确认 `GXRabbitMQConfig` 中的自动配置逻辑是否按预期将这些回调设置给了 `RabbitTemplate`。
  - 检查相关的 RabbitMQ 配置是否启用 (如 `publisher-confirm-type: correlated`, `publisher-returns: true`, `template.retry.enabled: true`)。

### 7.5 获取更多信息

- **详细日志**：调整应用日志级别 (例如，`logging.level.org.springframework.amqp=DEBUG` 和 `logging.level.com.rabbitmq.client=DEBUG`) 以获取更详细的 AMQP 交互信息。注意，DEBUG 日志量可能很大，排查后及时调回。
- **RabbitMQ 日志**：查看 RabbitMQ 服务器自身的日志文件，通常能提供 Broker 端的错误信息。
- **Wireshark/tcpdump**：在网络层面抓包分析 AMQP 协议交互，用于深度问题排查。

更多排查技巧请参考 <mcurl name="RabbitMQ 官方文档" url="https://www.rabbitmq.com/documentation.html"></mcurl> 和 <mcurl name="Spring AMQP 文档" url="https://docs.spring.io/spring-amqp/docs/current/reference/html/"></mcurl>。

## 8. 参考资料与附录

为了更深入地了解 RabbitMQ 和 Spring AMQP 的相关概念和高级用法，请参考以下官方文档和资源：

### 8.1 Spring AMQP (Spring for RabbitMQ)

-   **官方项目首页**: <mcurl name="Spring AMQP Project Page" url="https://spring.io/projects/spring-amqp"></mcurl>
    *   提供项目概览、特性、新闻和相关链接。
-   **参考文档 (Current Stable Release)**: <mcurl name="Spring AMQP Reference" url="https://docs.spring.io/spring-amqp/docs/current/reference/html/"></mcurl>
    *   最全面的指南，涵盖了从基本配置到高级特性的所有方面，包括连接管理、消息监听、RabbitTemplate、错误处理、事务等。
-   **API 文档 (JavaDocs)**: <mcurl name="Spring AMQP API Docs" url="https://docs.spring.io/spring-amqp/docs/current/api/"></mcurl>
    *   详细的类和方法说明。

### 8.2 RabbitMQ

-   **官方网站与文档首页**: <mcurl name="RabbitMQ Documentation" url="https://www.rabbitmq.com/documentation.html"></mcurl>
    *   所有 RabbitMQ 官方文档的入口。
-   **入门教程 (Get Started)**: <mcurl name="RabbitMQ Get Started" url="https://www.rabbitmq.com/getstarted.html"></mcurl>
    *   针对不同编程语言的 “Hello World” 示例以及后续的 Work Queues, Publish/Subscribe, Routing, Topics, RPC 模式教程。
-   **AMQP 0-9-1 概念概览**: <mcurl name="AMQP 0-9-1 Concepts" url="https://www.rabbitmq.com/tutorials/amqp-concepts.html"></mcurl>
    *   解释 AMQP 协议中的核心概念，如 Exchanges, Queues, Bindings, Connections, Channels, Consumers, Publishers, Vhosts 等。
-   **Java Client API 指南**: <mcurl name="RabbitMQ Java Client Guide" url="https://www.rabbitmq.com/java-client.html"></mcurl>
    *   虽然 Spring AMQP 封装了 Java Client，但了解底层客户端有助于理解 Spring AMQP 的行为和进行更底层的定制。
-   **Management Plugin 指南**: <mcurl name="RabbitMQ Management Plugin" url="https://www.rabbitmq.com/management.html"></mcurl>
    *   介绍如何使用 RabbitMQ 的管理界面监控和管理 Broker。
-   **RabbitMQ 社区**: <mcurl name="RabbitMQ Community" url="https://www.rabbitmq.com/community.html"></mcurl>
    *   包括邮件列表、Slack 频道等，可以获取帮助和参与讨论。

### 8.3 本模块 (`leaf-base-rabbitmq`) 关键类

-   <mcsymbol name="GXRabbitMQConfig" filename="GXRabbitMQConfig.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java" startline="26" type="class"></mcsymbol>: 模块的核心配置类，负责自动化配置 `RabbitTemplate`、`RabbitAdmin`、监听器容器工厂以及自定义回调。
-   <mcsymbol name="GXRabbitMQProperties" filename="GXRabbitMQProperties.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/properties/GXRabbitMQProperties.java" startline="13" type="class"></mcsymbol>: 定义了模块的可配置属性，如默认队列名、是否启用 RabbitMQ 等。
-   <mcsymbol name="GXSendRabbitMQService" filename="GXSendRabbitMQService.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/GXSendRabbitMQService.java" startline="14" type="class"></mcsymbol>: 提供了发送消息的便捷服务接口和实现 <mcsymbol name="GXSendRabbitMQServiceImpl" filename="GXSendRabbitMQServiceImpl.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java" startline="25" type="class"></mcsymbol>。
-   <mcsymbol name="GXRabbitMQQueueListener" filename="GXRabbitMQQueueListener.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/listener/GXRabbitMQQueueListener.java" startline="12" type="class"></mcsymbol>: 消息监听器接口，业务方可以实现此接口来处理消息。
-   <mcsymbol name="GXDefaultRabbitMQQueueListenerImpl" filename="GXDefaultRabbitMQQueueListenerImpl.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/listener/GXDefaultRabbitMQQueueListenerImpl.java" startline="22" type="class"></mcsymbol>: 默认的消息监听器实现，监听 `gx.rabbitmq.default-queue-name` 指定的队列。
-   回调接口:
    -   <mcsymbol name="GXConfirmCallback" filename="GXConfirmCallback.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/callback/GXConfirmCallback.java" startline="13" type="class"></mcsymbol>
    -   <mcsymbol name="GXReturnsCallback" filename="GXReturnsCallback.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/callback/GXReturnsCallback.java" startline="14" type="class"></mcsymbol>
    -   <mcsymbol name="GXRecoveryCallback" filename="GXRecoveryCallback.java" path="leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/callback/GXRecoveryCallback.java" startline="12" type="class"></mcsymbol>

这些资源将为您提供关于消息队列、Exchange 类型、绑定、消息确认、集群、策略等方面的详细信息，并帮助您更好地理解和使用 `leaf-base-rabbitmq` 模块。