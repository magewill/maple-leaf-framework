# Leaf-Base-RocketMQ 开发手册

## 1. 模块概述

Leaf-Base-RocketMQ 是 Maple-Leaf-Framework 框架中用于简化 Apache RocketMQ 消息队列操作的基础模块。该模块封装了 RocketMQ 的常用功能，提供了简洁易用的 API，使开发人员能够快速集成和使用 RocketMQ 进行消息发送和接收。

### 1.1 主要特性

- 支持多种消息发送模式：普通消息、延迟消息、同步消息、异步消息和单向消息
- 支持本地配置和 Nacos 配置中心两种配置方式
- 提供统一的消息发送接口，简化 RocketMQ 的使用
- 完善的异常处理和日志记录，便于问题排查
- 与框架其他模块无缝集成，如 Nacos 配置中心

### 1.2 模块依赖

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-rocketmq</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

### 1.3 核心依赖

- leaf-base-framework：框架基础模块
- leaf-base-nacos：Nacos 配置中心模块
- rocketmq-spring-boot-starter：RocketMQ 官方 Spring Boot 启动器（版本：2.3.3）

## 2. 配置说明

本模块支持通过本地配置文件 (`application.yml` 或 `application.properties`) 和 Nacos 配置中心两种方式进行配置。系统启动时，会优先尝试加载 Nacos 配置。如果 Nacos 配置不可用或未配置，则会回退到使用本地配置文件中的设置。

### 2.1 本地配置 (`cn.maple.rocketmq.properties.local.GXLocalRocketMQConfigProperties`)

在项目的 `src/main/resources` 目录下，可以创建环境相关的 RocketMQ 配置文件，例如 `dev/rocket-mq.yml`。这些配置项由 `cn.maple.rocketmq.properties.local.GXLocalRocketMQConfigProperties` 类加载。该类通过 `@PropertySource(value = "classpath:${spring.profiles.active:}/rocket-mq.yml", factory = GXYamlPropertySourceFactory.class, ignoreResourceNotFound = true)` 注解加载 YAML 文件，并使用 `@ConfigurationProperties(prefix = "rocketmq")` 来绑定配置项。`ignoreResourceNotFound = true` 表示如果特定环境的配置文件不存在，不会报错。当 Nacos 相关类 `com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties` 不存在于类路径时，此本地配置类会被激活。

```yaml
rocketmq: # 注意：根据 GXLocalRocketMQConfigProperties 和 GXNacosRocketMQConfigProperties 的 @ConfigurationProperties(prefix = "rocketmq")，前缀应为 rocketmq
  # 是否启用 RocketMQ 功能，默认为 true。如果设置为 false，则不会初始化生产者。
  enabled: true
  # RocketMQ NameServer 地址，**必填**。多个地址用分号 (;) 分隔。
  # 示例: "192.168.1.100:9876;192.168.1.101:9876"
  name-server: "127.0.0.1:9876"
  # 生产者组名，**必填**。建议使用应用名或业务模块名作为前缀，保证全局唯一。
  # 示例: "my_app_order_producer_group"
  producer-group: "default_producer_group"
  # 发送消息超时时间（毫秒），默认为 3000 (3秒)。
  send-message-timeout: 3000
  # 消息体压缩阈值（字节）。当消息体大小超过此值时，SDK会自动进行压缩。默认为 4096 (4KB)。
  compress-message-body-threshold: 4096
  # 同步发送模式下，发送失败时的重试次数。默认为 2 次。
  # 注意：这不包括第一次发送尝试。总共会尝试 1 + retryTimesWhenSendFailed 次。
  retry-times-when-send-failed: 2
  # 异步发送模式下，发送失败时的重试次数。默认为 2 次。
  retry-times-when-send-async-failed: 2
  # 是否开启VIP通道，默认为 true。如果 NameServer 版本较低 (如低于 3.5.8)，可能需要设置为 false。
  # VIP通道允许客户端直接连接到Broker，绕过NameServer进行消息发送，可以提高发送性能。
  vip-channel-enabled: true
  # RocketMQ AccessKey，用于ACL (Access Control List) 权限控制。如果 Broker 开启了ACL，则必须配置。
  access-key: ""
  # RocketMQ SecretKey，用于ACL权限控制。如果 Broker 开启了ACL，则必须配置。
  secret-key: ""
  # 实例名称，默认为 "DEFAULT"
  instance-name: "DEFAULT"
  # 客户端回调线程数，默认为CPU核心数
  client-callback-executor-threads: #不设置为系统默认
  # 拉取消息的线程数，默认为CPU核心数
  pull-message-thread-pool-nums: #不设置为系统默认
  # 单元化模式下，单元名称
  unit-name: ""
  # 是否开启了OpenTelemetry Tracing，默认为false
  open-telemetry-tracing-enabled: false
```

**注意：** `GXLocalRocketMQConfigProperties` 类中并没有显式定义上述所有配置项的字段，它依赖于 Spring Boot 的 `@ConfigurationProperties` 自动绑定机制。实际可配置的属性以 RocketMQ Spring Boot Starter 官方文档为准，此处列出的是常见配置。
    # 是否启用 RocketMQ 功能，默认为 true。如果设置为 false，则不会初始化生产者。
    enabled: true
    # RocketMQ NameServer 地址，**必填**。多个地址用分号 (;) 分隔。
    # 示例: "192.168.1.100:9876;192.168.1.101:9876"
    name-server: "127.0.0.1:9876"
    # 生产者组名，**必填**。建议使用应用名或业务模块名作为前缀，保证全局唯一。
    # 示例: "my_app_order_producer_group"
    producer-group: "default_producer_group"
    # 发送消息超时时间（毫秒），默认为 3000 (3秒)。
    send-message-timeout: 3000
    # 消息体压缩阈值（字节）。当消息体大小超过此值时，SDK会自动进行压缩。默认为 4096 (4KB)。
    compress-message-body-threshold: 4096
    # 同步发送模式下，发送失败时的重试次数。默认为 2 次。
    # 注意：这不包括第一次发送尝试。总共会尝试 1 + retryTimesWhenSendFailed 次。
    retry-times-when-send-failed: 2
    # 异步发送模式下，发送失败时的重试次数。默认为 2 次。
    retry-times-when-send-async-failed: 2
    # 是否开启VIP通道，默认为 true。如果 NameServer 版本较低 (如低于 3.5.8)，可能需要设置为 false。
    # VIP通道允许客户端直接连接到Broker，绕过NameServer进行消息发送，可以提高发送性能。
    vip-channel-enabled: true
    # RocketMQ AccessKey，用于ACL (Access Control List) 权限控制。如果 Broker 开启了ACL，则必须配置。
    access-key: ""
    # RocketMQ SecretKey，用于ACL权限控制。如果 Broker 开启了ACL，则必须配置。
    secret-key: ""
    # 实例名称，默认为 "DEFAULT"
    instance-name: "DEFAULT"
    # 客户端回调线程数，默认为CPU核心数
    client-callback-executor-threads: #不设置为系统默认
    # 拉取消息的线程数，默认为CPU核心数
    pull-message-thread-pool-nums: #不设置为系统默认
    # 单元化模式下，单元名称
    unit-name: ""
    # 是否开启了OpenTelemetry Tracing，默认为false
    open-telemetry-tracing-enabled: false
```

### 2.2 Nacos 配置 (`cn.maple.rocketmq.properties.nacos.GXNacosRocketMQConfigProperties`)

如果您的项目集成了 Nacos 作为配置中心，本模块可以通过 `cn.maple.rocketmq.properties.nacos.GXNacosRocketMQConfigProperties` 类从 Nacos 加载 RocketMQ 配置。该类通过 `@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})` 判断 Nacos 环境是否存在。如果存在，则此配置类会被激活。

**启用条件**:
- 项目中引入了 `spring-cloud-starter-alibaba-nacos-config` 依赖。
- 正确配置了 Nacos 服务器地址 (`spring.cloud.nacos.config.server-addr`)。
- 在 Nacos 中创建了对应的 Data ID，并且配置了 RocketMQ 相关参数。

**Data ID**: `GXNacosRocketMQConfigProperties` 类通过 `@NacosConfigurationProperties(prefix = "rocketmq", dataId = "${spring.application.name:rocket-mq}-rocket-mq.yml", groupId = "${spring.cloud.nacos.config.group:${nacos.config.group:DEFAULT_GROUP}}", autoRefreshed = true)` 注解来指定配置来源。
    - `prefix`: "rocketmq"
    - `dataId`: 默认为 `${spring.application.name:rocket-mq}-rocket-mq.yml`。如果应用定义了 `spring.application.name`，则 Data ID 为 `[应用名]-rocket-mq.yml`；否则为 `rocket-mq-rocket-mq.yml`。
    - `groupId`: 默认为 `${spring.cloud.nacos.config.group:${nacos.config.group:DEFAULT_GROUP}}`。优先使用 `spring.cloud.nacos.config.group`，其次是 `nacos.config.group`，最后是 `DEFAULT_GROUP`。
    - `autoRefreshed`: `true`，表示配置变更时会自动刷新。

**Nacos 配置内容示例 (YAML格式)**:

在 Nacos 控制台对应的 Data ID 和 Group 下，配置如下（以 `rocketmq` 为根配置项）：

```yaml
rocketmq:
    # Nacos中配置的NameServer地址，会覆盖本地配置
    name-server: "your-nacos-managed-nameserver1:9876;your-nacos-managed-nameserver2:9876"
    # Nacos中配置的生产者组名
    producer-group: "nacos_configured_producer_group"
    send-message-timeout: 5000 # 示例：Nacos中配置了不同的超时时间
    access-key: "nacosConfiguredAccessKey"
    secret-key: "nacosConfiguredSecretKey"
    # ... 其他所有在 RocketMQ Spring Boot Starter 中支持的属性均可在 Nacos 中配置 ...
```

**注意：** `GXNacosRocketMQConfigProperties` 类中并没有显式定义上述所有配置项的字段，它依赖于 Spring Boot 的 `@ConfigurationProperties` 和 Nacos 的 `@NacosConfigurationProperties` 自动绑定机制。实际可配置的属性以 RocketMQ Spring Boot Starter 官方文档为准。 # 注意：根据 GXLocalRocketMQConfigProperties 和 GXNacosRocketMQConfigProperties 的 @ConfigurationProperties(prefix = "rocketmq")，前缀应为 rocketmq
  # 是否启用 RocketMQ 功能，默认为 true。如果设置为 false，则不会初始化生产者。
  enabled: true
  # RocketMQ NameServer 地址，**必填**。多个地址用分号 (;) 分隔。
  # 示例: "192.168.1.100:9876;192.168.1.101:9876"
  name-server: "127.0.0.1:9876"
  # 生产者组名，**必填**。建议使用应用名或业务模块名作为前缀，保证全局唯一。
  # 示例: "my_app_order_producer_group"
  producer-group: "default_producer_group"
  # 发送消息超时时间（毫秒），默认为 3000 (3秒)。
  send-message-timeout: 3000
  # 消息体压缩阈值（字节）。当消息体大小超过此值时，SDK会自动进行压缩。默认为 4096 (4KB)。
  compress-message-body-threshold: 4096
  # 同步发送模式下，发送失败时的重试次数。默认为 2 次。
  # 注意：这不包括第一次发送尝试。总共会尝试 1 + retryTimesWhenSendFailed 次。
  retry-times-when-send-failed: 2
  # 异步发送模式下，发送失败时的重试次数。默认为 2 次。
  retry-times-when-send-async-failed: 2
  # 是否开启VIP通道，默认为 true。如果 NameServer 版本较低 (如低于 3.5.8)，可能需要设置为 false。
  # VIP通道允许客户端直接连接到Broker，绕过NameServer进行消息发送，可以提高发送性能。
  vip-channel-enabled: true
  # RocketMQ AccessKey，用于ACL (Access Control List) 权限控制。如果 Broker 开启了ACL，则必须配置。
  access-key: ""
  # RocketMQ SecretKey，用于ACL权限控制。如果 Broker 开启了ACL，则必须配置。
  secret-key: ""
  # 实例名称，默认为 "DEFAULT"
  instance-name: "DEFAULT"
  # 客户端回调线程数，默认为CPU核心数
  client-callback-executor-threads: #不设置为系统默认
  # 拉取消息的线程数，默认为CPU核心数
  pull-message-thread-pool-nums: #不设置为系统默认
  # 单元化模式下，单元名称
  unit-name: ""
  # 是否开启了OpenTelemetry Tracing，默认为false
  open-telemetry-tracing-enabled: false
```

**注意：** `GXLocalRocketMQConfigProperties` 类中并没有显式定义上述所有配置项的字段，它依赖于 Spring Boot 的 `@ConfigurationProperties` 自动绑定机制。实际可配置的属性以 RocketMQ Spring Boot Starter 官方文档为准，此处列出的是常见配置。
    # Nacos中配置的NameServer地址，会覆盖本地配置
    name-server: "your-nacos-managed-nameserver1:9876;your-nacos-managed-nameserver2:9876"
    # Nacos中配置的生产者组名
    producer-group: "nacos_configured_producer_group"
    send-message-timeout: 5000 # 示例：Nacos中配置了不同的超时时间
    access-key: "nacosConfiguredAccessKey"
    secret-key: "nacosConfiguredSecretKey"
    # ... 其他所有在 GXLocalRocketMQConfigProperties 中定义的属性均可在 Nacos 中配置 ...
    # Nacos特有的配置，用于控制是否自动刷新配置，默认为 true
    # nacos-auto-refreshed: true # (此配置通常由Spring Cloud Nacos控制，此处仅为说明)
```

**核心逻辑**:
- 当 Nacos 中 `rocketmq` 前缀下的配置发生变更时，这些属性会自动刷新到应用中。`DefaultMQProducer` 实例是否会根据新的配置重建取决于 `RocketMQTemplate` 的具体实现和相关配置的变更影响。
- 如果 `rocketmq.enabled` 在 Nacos 中被设置为 `false`，即使本地配置为 `true`，RocketMQ 生产者也不会被初始化或在运行时被关闭（具体行为取决于 `RocketMQTemplate` 如何响应配置变更）。

**优先级**:
1. Nacos 配置 (`GXNacosRocketMQConfigProperties`)
2. 本地 `application.yml` 或 `application.properties` 配置 (`GXLocalRocketMQConfigProperties`)

如果 Nacos 服务不可达或未配置相关 Data ID，启动时会打印警告日志，并使用本地配置。如果本地配置也不完整（例如缺少 `name-server` 或 `producer-group`），则生产者初始化会失败。

## 3. 核心功能

### 3.1 消息请求DTO

`cn.maple.rocketmq.dto.inner.GXRocketMQMessageReqDto` 类是消息发送的核心数据传输对象，用于封装发送到 RocketMQ 的消息数据。该类继承自 `cn.maple.core.framework.dto.req.GXBaseReqDto`，可以利用基类提供的通用功能。

**核心特性与考量：**
- **安全性**：
    - 消息体在构造方法中或通过 `setBody` 方法传入对象时，会使用 `cn.hutool.json.JSONUtil.toJsonStr()` 进行序列化为JSON字符串，确保数据可读性和兼容性。
    - 对于敏感信息，建议在调用构造方法或 `setBody` 前进行脱敏处理。
- **性能**：
    - 使用高效的JSON工具进行序列化。
    - 合理设置消息KEY (`messageKey`) 可以提高消息路由和查询效率。
    - 精确控制延时消息的 `deliverTime` 可以优化系统资源使用。

#### 3.1.1 主要属性

| 属性名 | 类型 | 描述 |
| ------ | ---- | ---- |
| topic | String | 消息主题，**必填**。消息的第一级分类，RocketMQ的消息必须有主题。最长不超过255个字符，由字母、数字、中划线和下划线构成。命名建议：使用有意义的业务名称（如 `order_topic`），下划线分隔。避免特殊字符和中文。 |
| tag | String | 消息标签，可选，默认为空字符串。消息的第二级分类，用于同一主题下的消息过滤。命名建议：简洁明了，表达消息具体类型或操作（如 `created`, `paid`）。 |
| body | String | 消息内容，**必填**。通常是业务对象的JSON格式字符串。在构造方法中，如果传入的是一个Object，会自动转换为JSON字符串。安全建议：避免包含敏感信息（如密码、密钥），必要时进行脱敏处理，控制消息大小。 |
| deliverTime | long | 延迟发送时间（秒），用于延迟消息。设置后，消息将在指定的时间后才被消费者接收。适用于订单超时、预约提醒等场景。注意：RocketMQ的延时等级是固定的 (例如 1s 5s 10s 30s 1m ... 2h)，`GXSendRocketMQServiceImpl` 中的 `sendDelayMessage` 方法会将此秒级延时转换为毫秒级的时间戳 (`System.currentTimeMillis() + messageReqDto.getDeliverTime() * 1000L`)，并设置到 `Message` 的 `setDeliverTimeMs` 属性中，由 `RocketMQTemplate` 的 `syncSend` 方法处理。如果需要非常精确的延时，可能需要在应用层面进一步处理或选择其他机制。 |
| messageKey | String | 消息唯一标识，可选，但**强烈建议设置**。用于消息的查询和跟踪。建议使用业务唯一标识（如订单号、用户ID）。好处：方便控制台查询跟踪、问题排查、业务分析、实现消息幂等性。 |

#### 3.1.2 构造方法

```java
// 1. 创建简单消息（只有消息体）
Order order = new Order("ORD123456", 100.00);
GXRocketMQMessageReqDto messageDto = new GXRocketMQMessageReqDto(order);

// 2. 创建带标签的消息
GXRocketMQMessageReqDto taggedMessage = new GXRocketMQMessageReqDto("order_created", order);

// 3. 创建完整消息（主题、标签、消息体、延时时间、消息KEY）
GXRocketMQMessageReqDto fullMessage = new GXRocketMQMessageReqDto(
    "order_topic",           // 主题
    "order_paid",            // 标签
    order,                    // 消息体
    60,                       // 延时发送时间（秒）
    "ORD123456"              // 消息KEY
);

// 4. 使用setter方法逐个设置属性
GXRocketMQMessageReqDto customMessage = new GXRocketMQMessageReqDto();
customMessage.setTopic("order_topic");
customMessage.setTag("order_refunded");
customMessage.setBody(JSONUtil.toJsonStr(order)); //可以直接传入对象，内部会自动转json
customMessage.setDeliverTime(3600);  // 1小时后发送
customMessage.setMessageKey("ORD123456_REFUND");

// 5. 更多构造方法说明
// - GXRocketMQMessageReqDto(): 默认构造函数，后续需手动设置所有必要属性，尤其是 topic。
// - GXRocketMQMessageReqDto(Object body): 仅指定消息体，后续需手动设置 topic。
// - GXRocketMQMessageReqDto(String tag, Object body): 指定标签和消息体，后续需手动设置 topic。
// - GXRocketMQMessageReqDto(String topic, String tag, Object body, long deliverTime, String messageKey): 全参数构造，推荐使用以确保所有属性都被正确初始化 (注意 deliverTime 类型为 long)。
```

### 3.2 消息发送服务

`cn.maple.rocketmq.service.GXSendRocketMQService` 接口及其实现类 `cn.maple.rocketmq.service.impl.GXSendRocketMQServiceImpl` 提供了多种消息发送模式。该服务实现类依赖 `org.apache.rocketmq.spring.core.RocketMQTemplate` 进行消息发送。

#### 3.2.1 普通消息发送 (`sendNormalMessage`)

- **方法签名**: `void sendNormalMessage(GXRocketMQMessageReqDto messageReqDto)`
- **描述**: 发送普通消息。内部调用 `rocketMQTemplate.send(destination, message)`，这是一个同步发送操作。如果发送成功，记录成功日志；如果发送失败，记录错误日志并抛出 `GXBusinessException`。
- **参数**: `messageReqDto` - 包含消息主题、标签、内容、消息Key等信息的请求对象。`topic` 和 `body` 不能为空。
- **返回**: `void`
- **使用场景**: 对消息可靠性要求非常高，需要明确知道消息是否成功投递到 Broker 的场景。例如：重要的交易指令、订单状态变更等。
- **注意事项**:
    - **阻塞**: `rocketMQTemplate.send()` 是同步操作，会阻塞当前线程。
    - **异常处理**: 方法内部会捕获所有异常，记录日志，并包装为 `GXBusinessException` 抛出。
    - **消息构造**: 使用 `MessageBuilder` 构建消息。如果 `messageKey` 不为空，会通过 `messageBuilder.setHeader(RocketMQHeaders.KEYS, messageKey)` 设置。
    - **目标地址**: 通过内部私有方法 `getDestination(messageReqDto)` 构建，格式为 `topic:tag` (如果tag存在) 或 `topic` (如果tag不存在)。

#### 3.2.2 延迟消息发送 (`sendDelayMessage`)

- **方法签名**: `String sendDelayMessage(GXRocketMQMessageReqDto messageReqDto)`
- **描述**: 发送延迟消息。消息将在 `messageReqDto` 中指定的 `deliverTime` (秒) 之后才对消费者可见。内部将 `deliverTime` (秒) 转换为毫秒级的时间戳 (`System.currentTimeMillis() + messageReqDto.getDeliverTime() * 1000L`)，然后调用 `rocketMQTemplate.syncSendDeliverTimeMills(getDestination(messageReqDto), message, deliveryTimeMills)` 进行发送。
- **参数**: `messageReqDto` - 包含消息主题、标签、内容、消息Key以及 `deliverTime` (延迟秒数，必须大于0) 的请求对象。`topic` 和 `body` 不能为空。
- **返回**: `String` - 发送成功后返回 `SendResult` 中的 `MsgId`。如果发送失败，记录错误日志并抛出 `GXBusinessException`。
- **使用场景**: 需要在未来某个特定时间点触发的业务逻辑。例如：订单创建30分钟后未支付则自动关闭订单、会议开始前15分钟发送提醒。
- **注意事项**:
    - **延迟时间**: `deliverTime` 单位为秒，且必须大于0，否则抛出 `GXBusinessException`。
    - **实现**: 内部调用 `rocketMQTemplate.syncSendDeliverTimeMills()` 方法，这是一个同步发送操作，会阻塞当前线程。
    - **精确度**: `syncSendDeliverTimeMills` 允许指定消息投递的绝对时间戳（毫秒），相比基于固定延迟等级的发送方式，理论上可以提供更灵活的延迟控制，但具体精确度仍受Broker端调度影响。
    - **异常处理**: 方法内部会捕获所有异常，记录日志，并包装为 `GXBusinessException` 抛出。

#### 3.2.3 异步消息发送 (`sendAsync`)

- **方法签名**: `boolean sendAsync(GXRocketMQMessageReqDto messageReqDto)`
- **描述**: 异步发送消息。消息发送请求提交后，业务线程不会阻塞等待 Broker 响应，而是立即返回。内部调用 `rocketMQTemplate.asyncSend(destination, message, SendCallback)`。
- **参数**: `messageReqDto`: 消息请求对象。`topic` 和 `body` 不能为空。
- **返回**: `boolean` - `true` 表示异步发送请求已成功提交。如果请求提交过程中发生异常 (如参数校验失败)，则记录错误日志并抛出 `GXBusinessException` (此时不会返回 `false`)。
- **使用场景**: 对响应时间敏感，且可以接受消息最终一致性的场景。例如：用户操作日志记录、用户行为数据采集、通知类消息（如图文推送结果通知）。可以显著提高系统的吞吐量。
- **注意事项**: 
    - **回调处理**: `GXSendRocketMQServiceImpl` 内部实现了一个匿名 `SendCallback`：
        - `onSuccess(SendResult sendResult)`: 记录包含主题、标签和消息ID的成功日志。
        - `onException(Throwable throwable)`: 记录包含主题、标签、错误信息和异常堆栈的错误日志。
      业务方无法直接提供自定义的 `SendCallback` 实例给此方法。
    - **返回值**: 返回 `true` 仅表示异步发送任务已提交给 `RocketMQTemplate`，不代表消息最终发送成功。实际的发送结果由内部 `SendCallback` 处理。
    - **异常处理**: 如果在准备异步发送（如参数校验、消息构建）阶段发生错误，会抛出 `GXBusinessException`。`SendCallback` 中的 `onException` 处理的是异步发送过程中的网络或Broker错误。
    - **线程池**: 异步发送依赖于生产者内部的线程池。如果业务量过大，需要关注线程池的配置和状态。
    - **资源管理**: 回调方法中应避免执行耗时操作，以免阻塞回调线程池，影响其他异步消息的处理。

#### 3.2.4 单向消息发送 (`sendOneway`)

- **方法签名**: `boolean sendOneway(GXRocketMQMessageReqDto messageReqDto)`
- **描述**: 单向（Oneway）发送消息。消息发送请求提交后，不等待 Broker 的任何响应，也不关心消息是否发送成功。内部调用 `rocketMQTemplate.sendOneWay(getDestination(messageReqDto), message)`。
- **参数**: `messageReqDto`: 消息请求对象。`topic` 和 `body` 不能为空。
- **返回**: `boolean` - `true` 表示单向发送请求已成功提交。如果请求提交过程中发生异常 (如参数校验失败)，则记录错误日志并抛出 `GXBusinessException` (此时不会返回 `false`)。
- **使用场景**: 对消息可靠性要求不高，允许少量消息丢失的场景。例如：非核心业务的监控数据上报、应用性能指标采样、可选的通知等。适用于需要极高吞吐量且能容忍数据丢失的场景。
- **注意事项**: 
    - **消息丢失风险**: 由于不等待 Broker 确认，网络抖动、Broker故障等都可能导致消息丢失，且客户端无感知。
    - **返回值**: 返回 `true` 仅表示单向发送任务已提交给 `RocketMQTemplate`，不代表消息最终发送成功或被Broker接收。
    - **异常处理**: 如果在准备单向发送（如参数校验、消息构建）阶段发生错误，会抛出 `GXBusinessException`。
    - **适用性评估**: 仅在业务可以完全容忍消息丢失的情况下使用。

#### 3.2.5 同步消息发送 (`syncSend`)

- **方法签名**: `boolean syncSend(GXRocketMQMessageReqDto messageReqDto)`
- **描述**: 同步发送消息。内部调用 `rocketMQTemplate.syncSend(getDestination(messageReqDto), message)`。如果发送成功，记录成功日志并返回 `true`；如果发送失败，记录错误日志并抛出 `GXBusinessException` (此时不会返回 `false`)。
- **参数**: `messageReqDto` - 消息请求对象。`topic` 和 `body` 不能为空。
- **返回**: `boolean` - `true` 表示消息发送成功。
- **使用场景**: 需要明确知道消息是否成功投递，并且希望通过返回值直接判断的场景（尽管失败时仍会抛出异常）。
- **注意事项**:
    - **阻塞**: 同步发送会阻塞当前业务线程。
    - **异常处理**: 方法内部会捕获所有异常，记录日志，并包装为 `GXBusinessException` 抛出。这意味着调用方通常需要通过 `try-catch` 来处理发送失败的情况，而不是仅仅依赖 `false` 返回值。
    - **与 `sendNormalMessage` 的区别**: `sendNormalMessage` 返回 `void`，失败时抛出异常。`syncSend` 返回 `boolean` (成功时为 `true`)，失败时同样抛出异常。从异常处理角度看，两者在失败场景下的行为类似，都需要调用方捕获异常。

*(文档原有章节 3.2.5 `同步批量消息发送 (sendBatch)` 在 `GXSendRocketMQService` 接口和 `GXSendRocketMQServiceImpl` 实现中未找到对应方法。如果需要批量发送功能，`RocketMQTemplate` 本身支持批量发送，但当前模块未封装。)*

### 3.3 内部辅助方法

#### 3.3.1 `getDestination`

- **方法签名**: `private String getDestination(GXRocketMQMessageReqDto messageReqDto)`
- **描述**: 根据 `GXRocketMQMessageReqDto` 中的 `topic` 和 `tag` 构建 RocketMQ 的目标字符串 (destination)。
- **参数**: `messageReqDto` - 消息请求对象。`messageReqDto` 和 `messageReqDto.getTopic()` 不能为空。
- **返回**: `String` - 格式为 `topic:tag` (如果 `tag` 不为空) 或 `topic` (如果 `tag` 为空或空字符串)。
- **逻辑**: 
    - 对 `messageReqDto` 进行 null 检查。
    - 对 `messageReqDto.getTopic()` 进行空检查，如果为空则抛出 `GXBusinessException`。
    - 如果 `messageReqDto.getTag()` 为空或空字符串，则返回 `topic`。
    - 否则，使用 `CharSequenceUtil.format("{}:{}", topic, tag)` 返回 `topic:tag`。
- **用途**: 在所有消息发送方法中，用于确定消息发送的目标地址。

- **方法签名**: `SendResult sendBatch(List<GXRocketMQMessageReqDto> messages)`
- **描述**: 同步发送批量消息。将多条消息打包成一个批次一次性发送给 Broker，可以减少网络请求次数，提高发送效率。
- **参数**: `messages` - `GXRocketMQMessageReqDto` 的列表。
- **返回**: `SendResult` - 代表整个批次消息的发送结果。
- **使用场景**: 需要一次性发送大量结构相似、目标 Topic 相同的消息。例如：批量导入数据后发送通知、批量更新状态后发送事件。
- **注意事项**:
    - **消息限制**:
        - **相同 Topic**: 批次内的所有消息必须发送到同一个 Topic。
        - **不支持延迟/事务**: 批量消息不支持延迟投递和事务消息。
        - **大小限制**: 整个批次消息的总大小不能超过 Broker 的限制（默认为4MB，具体值由 `maxMessageSize` 配置决定）。如果超出，SDK 会尝试将批次拆分为更小的批次进行发送。
    - **原子性**: 批量发送不是原子操作。如果批次中的部分消息发送失败，`SendResult` 可能表示成功，但部分消息可能未投递。Broker 不保证批次消息的原子性投递。
    - **错误处理**: 如果批量发送失败（例如，由于消息过大或 Broker 问题），需要业务方根据 `SendResult` 和异常信息进行判断和处理，可能需要重试或记录失败的消息。
    - **实现**: `GXSendRocketMQServiceImpl` 中的 `sendBatch` 方法会将 `List<GXRocketMQMessageReqDto>` 转换为 `List<Message>`，然后调用 `DefaultMQProducer` 的 `send(Collection<Message> msgs)` 方法。

### 3.4 安全性、内存与性能考量

- **安全性**:
    - **消息内容安全**: 
        - 避免在消息体中直接传输敏感信息（如密码、API密钥、身份证号等）。
        - 如果必须传输，应在业务层面进行加密处理，并在消费端解密。
        - `GXRocketMQMessageReqDto` 的 `body` 属性建议使用JSON字符串，注意防范JSON注入等问题，虽然 `Hutool JSONUtil` 具有一定的安全性，但业务数据本身的校验仍很重要。
    - **认证与授权 (ACL)**: 
        - 生产环境中，强烈建议为 RocketMQ启用 ACL (Access Control List)，确保只有授权的生产者和消费者才能访问特定的 Topic。
        - 本模块通过 `accessKey` 和 `secretKey` 配置支持 ACL。
    - **网络安全**: 确保 NameServer 和 Broker 的端口仅对必要的应用开放，使用防火墙规则进行限制。
- **内存管理**:
    - **生产者实例**: `DefaultMQProducer` 是一个重量级对象，应在应用启动时创建并复用，避免频繁创建和销毁。
    - **消息大小**: 控制单条消息和批量消息的大小，过大的消息会消耗更多内存和网络带宽，并可能导致发送失败。
    - **异步回调**: 在 `sendAsync` 的回调方法中，避免创建大量对象或执行内存密集型操作，防止内存泄漏或OOM。
    - **队列积压**: 如果生产者速度远超消费者速度，会导致 Broker 端消息大量积压，占用 Broker 内存和磁盘。需要监控消费情况，及时扩容消费者或优化消费逻辑。
- **性能优化**:
    - **选择合适的发送方式**: 
        - 对可靠性要求高，选择同步发送 (`send`, `sendDelay`)。
        - 对吞吐量要求高，响应时间敏感，选择异步发送 (`sendAsync`)。
        - 对可靠性要求极低，追求极致吞吐量，选择单向发送 (`sendOneway`)。
    - **批量发送**: 当有大量小消息需要发送到同一 Topic 时，使用 `sendBatch` 可以显著提升性能。
    - **消息Key (`messageKey`)**: 为消息设置有意义的 `messageKey`，不仅便于追踪，还可以帮助 Broker 进行更高效的路由和存储（如果 Broker 端有相应优化）。
    - **Tag (`tag`)**: 合理使用 `tag` 进行消息过滤，避免消费者接收不必要的消息，减轻消费端压力。
    - **生产者参数调优**:
        - `sendMessageTimeout`: 合理设置发送超时时间。
        - `compressMsgBodyOverHowmuch`: 消息体压缩阈值，默认为4KB。压缩可以减少网络传输，但会增加CPU消耗。
        - `retryTimesWhenSendFailed`: 同步发送失败时的重试次数。
        - `retryTimesWhenSendAsyncFailed`: 异步发送失败时的重试次数。
    - **Broker性能**: Broker 的磁盘I/O、CPU、网络等都会影响发送性能。需要监控 Broker 状态。
    - **客户端并发**: 如果是Web应用，生产者通常是单例。如果需要更高的发送并发，可以考虑应用层面的并发控制或多个生产者实例（需谨慎管理）。

## 4. 最佳实践

### 4.1 消息发送模式选择

根据业务场景选择合适的消息发送模式：

| 发送模式 | 适用场景 | 特点 |
| ------- | ------- | ---- |
| 普通消息 | 一般业务场景，如用户注册后发送欢迎邮件 | 基础消息类型，不保证顺序 |
| 延迟消息 | 定时任务、订单超时取消等场景 | 指定时间后投递，适合需要延时处理的业务 |
| 同步消息 | 重要通知，如重要邮件、营销短信等 | 可靠性高，有阻塞等待，会影响发送方性能 |
| 异步消息 | 日志收集等可靠性要求不高但要求高吞吐量的场景 | 不阻塞发送线程，通过回调获知结果 |
| 单向消息 | 监控数据上报等不关心发送结果的场景 | 性能最高，无结果反馈，存在消息丢失风险 |

### 4.2 消息设计建议

1. **主题命名规范**：
   - 使用有意义的业务名称，如 `order_topic`, `user_topic` 等
   - 使用下划线分隔多个单词
   - 避免使用特殊字符和中文

2. **标签使用建议**：
   - 标签应简洁明了，表达消息的具体类型或操作
   - 同一主题下的不同业务操作可使用不同标签
   - 例如：订单主题下可设置 `created`, `paid`, `shipped` 等标签

3. **消息体设计**：
   - 使用 JSON 格式，确保数据的可读性和兼容性
   - 只包含必要的业务数据，避免过大的消息体
   - 对于敏感信息，在转换为 JSON 前进行脱敏处理

4. **消息 KEY 设置**：
   - 建议始终设置消息 KEY，便于消息追踪和问题排查
   - KEY 应具有业务含义，如订单号、用户 ID 等
   - 可以使用多个业务标识组合，如 `orderId_userId_action`

### 4.3 异常处理

模块内部已经对各种异常进行了处理，但在业务代码中仍需注意：

1. 捕获 `GXBusinessException` 异常，这是模块抛出的业务异常
2. 对于重要的消息发送，应有重试机制
3. 记录关键操作的日志，便于问题排查

```java
try {
    sendRocketMQService.sendNormalMessage(messageReqDto);
} catch (GXBusinessException e) {
    log.error("消息发送失败: {}", e.getMessage(), e);
    // 进行重试或其他补偿措施
}
```

### 4.4 性能优化

1. **批量发送**：对于大量小消息，考虑批量发送以提高吞吐量
2. **消息压缩**：对于大消息，考虑在发送前进行压缩
3. **合理设置超时时间**：根据网络状况和消息大小设置合理的超时时间
4. **选择合适的发送模式**：对于性能敏感场景，考虑使用异步或单向发送

## 5. 常见问题

### 5.1 消息发送失败

可能的原因：
- RocketMQ 服务器不可用
- 网络连接问题
- 消息格式错误
- 超时设置不合理

解决方案：
- 检查 RocketMQ 服务器状态
- 检查网络连接
- 验证消息格式是否正确
- 调整超时设置
- 实现重试机制

### 5.2 消息丢失

可能的原因：
- 使用单向发送模式但未确认消息是否发送成功
- 生产者或消费者异常退出
- 消息过期被自动删除

解决方案：
- 对于重要消息，使用同步或异步发送并确认结果
- 实现消息重试和补偿机制
- 设置合理的消息保留时间

### 5.3 配置问题

可能的原因：
- 配置文件路径错误
- 配置内容格式错误
- Nacos 配置中心连接问题

解决方案：
- 检查配置文件路径是否正确
- 验证配置内容格式
- 检查 Nacos 连接配置

## 6. 版本历史

| 版本 | 日期 | 变更内容 |
| ---- | ---- | -------- |
| 4.1.2-SNAPSHOT | 当前版本 | 当前版本 |

## 7. 参考资料

- [Apache RocketMQ 官方文档](https://rocketmq.apache.org/docs/quick-start/)
- [RocketMQ Spring Boot Starter 文档](https://github.com/apache/rocketmq-spring)