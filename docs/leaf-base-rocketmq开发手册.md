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

### 2.1 本地配置 (`GXLocalRocketMQConfigProperties`)

在项目的 `src/main/resources/application.yml` (或 `.properties`) 文件中，可以配置以下 RocketMQ 相关参数。这些配置项由 `cn.maple.rocketmq.config.GXLocalRocketMQConfigProperties` 类加载。

```yaml
gx:
  rocketmq:
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

### 2.2 Nacos 配置 (`GXNacosRocketMQConfigProperties`)

如果您的项目集成了 Nacos 作为配置中心，本模块可以通过 `cn.maple.rocketmq.config.GXNacosRocketMQConfigProperties` 类从 Nacos 加载 RocketMQ 配置。Nacos 中的配置项会覆盖本地配置文件中的同名配置项。

**启用条件**:
- 项目中引入了 `spring-cloud-starter-alibaba-nacos-config` 依赖。
- 正确配置了 Nacos 服务器地址 (`spring.cloud.nacos.config.server-addr`)。
- 在 Nacos 中创建了对应的 Data ID，并且配置了 RocketMQ 相关参数。

**Data ID 示例**: 假设您的应用在 Nacos 中的 `spring.application.name` 为 `my-application`，并且配置文件格式为 `yaml`，则默认的 Data ID 可能为 `my-application.yaml` 或您自定义的共享配置 Data ID。

**Nacos 配置内容示例 (YAML格式)**:

在 Nacos 控制台对应的 Data ID 下，配置如下（仅展示与本地配置不同的部分或Nacos特有的）：

```yaml
gx:
  rocketmq:
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
- `GXNacosRocketMQConfigProperties` 类使用 `@NacosConfigurationProperties` 注解，并指定 `prefix = "gx.rocketmq"` 和 `autoRefreshed = true`。这意味着当 Nacos 中 `gx.rocketmq` 前缀下的配置发生变更时，这些属性会自动刷新到应用中，`DefaultMQProducer` 实例会根据新的配置重建（如果关键配置如 `name-server` 或 `producer-group` 发生变化）。
- 如果 `gx.rocketmq.enabled` 在 Nacos 中被设置为 `false`，即使本地配置为 `true`，RocketMQ 生产者也不会被初始化或在运行时被关闭。

**优先级**:
1. Nacos 配置 (`GXNacosRocketMQConfigProperties`)
2. 本地 `application.yml` 或 `application.properties` 配置 (`GXLocalRocketMQConfigProperties`)

如果 Nacos 服务不可达或未配置相关 Data ID，启动时会打印警告日志，并使用本地配置。如果本地配置也不完整（例如缺少 `name-server` 或 `producer-group`），则生产者初始化会失败。

## 3. 核心功能

### 3.1 消息请求DTO

`GXRocketMQMessageReqDto` 类是消息发送的核心数据传输对象，用于封装发送到 RocketMQ 的消息数据。该类继承自 `GXBaseReqDto`，可以利用基类提供的通用功能。

**核心特性与考量：**
- **安全性**：
    - 消息体默认使用 `Hutool JSONUtil` 进行序列化，确保数据可读性和兼容性。
    - 对于敏感信息，建议在调用构造方法或 `setBody` 前进行脱敏处理。
- **性能**：
    - 使用高效的JSON工具进行序列化。
    - 合理设置消息KEY (`messageKey`) 可以提高消息路由和查询效率。
    - 精确控制延时消息的 `deliverTime` 可以优化系统资源使用。

#### 3.1.1 主要属性

| 属性名 | 类型 | 描述 |
| ------ | ---- | ---- |
| topic | String | 消息主题，**必填**。消息的第一级分类，最长不超过255个字符，由字母、数字、中划线和下划线构成。命名建议：使用有意义的业务名称（如 `order_topic`），下划线分隔。避免特殊字符和中文。 |
| tag | String | 消息标签，可选，默认为空字符串。消息的第二级分类，用于同一主题下的消息过滤。命名建议：简洁明了，表达消息具体类型或操作（如 `created`, `paid`）。 |
| body | String | 消息内容，**必填**。通常是JSON格式的字符串。安全建议：避免包含敏感信息（如密码、密钥），必要时进行脱敏处理，控制消息大小。 |
| deliverTime | long | 延迟发送时间（秒），用于延迟消息。设置后，消息将在指定的时间后才被消费者接收。适用于订单超时、预约提醒等场景。注意：RocketMQ的延时等级是固定的，此处的秒级延时会在SDK层面转换为RocketMQ支持的延时等级进行发送，如果需要非常精确的延时，可能需要在应用层面进一步处理或选择其他机制。 |
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
// - GXRocketMQMessageReqDto(String topic, String tag, Object body, int deliverTime, String messageKey): 全参数构造，推荐使用以确保所有属性都被正确初始化。
```

### 3.2 消息发送服务

`GXSendRocketMQService` 接口及其实现类 `GXSendRocketMQServiceImpl` 提供了多种消息发送模式。

#### 3.2.1 普通消息发送 (send)

- **方法签名**: `SendResult send(GXRocketMQMessageReqDto messageDto)`
- **描述**: 发送普通（同步）消息。消息会立即发送，并等待 Broker 的确认。这是最可靠的发送方式。
- **参数**: `messageDto` - 包含消息主题、标签、内容、消息Key等信息的请求对象。
- **返回**: `SendResult` - 发送结果，包含发送状态、消息ID、消息队列等信息。
- **使用场景**: 对消息可靠性要求非常高，需要明确知道消息是否成功投递到 Broker 的场景。例如：重要的交易指令、订单状态变更等。
- **注意事项**:
    - **阻塞**: 同步发送会阻塞当前业务线程，直到收到 Broker 的响应或超时。在高并发或对响应时间敏感的场景下，需要谨慎评估其对系统性能的影响。
    - **超时**: 默认发送超时时间为3秒，可以通过 `gx.rocketmq.send-message-timeout` (本地配置) 或 Nacos 配置进行调整。
    - **重试**: 发送失败时，SDK内部会自动进行重试（默认2次）。
    - **异常处理**: 需要捕获并处理可能抛出的 `MQClientException`, `RemotingException`, `MQBrokerException`, `InterruptedException` 等异常。

#### 3.2.2 延迟消息发送 (sendDelay)

- **方法签名**: `SendResult sendDelay(GXRocketMQMessageReqDto messageDto)`
- **描述**: 发送延迟消息。消息将在 `messageDto` 中指定的 `deliverTime` (秒) 之后才对消费者可见。
- **参数**: `messageDto` - 必须包含 `deliverTime` 属性，指定延迟的秒数。
- **返回**: `SendResult` - 发送结果。
- **使用场景**: 需要在未来某个特定时间点触发的业务逻辑。例如：订单创建30分钟后未支付则自动关闭订单、会议开始前15分钟发送提醒。
- **注意事项**:
    - **延迟等级**: RocketMQ Broker 端预设了18个延迟等级 (例如：1s, 5s, 10s, 30s, 1m ... 2h)。SDK 会将用户设置的秒级 `deliverTime` 转换为最接近的、且不小于该时间的预设延迟等级进行发送。因此，延迟时间可能不是绝对精确的秒级，会有一定的误差。
    - **最大延迟**: 开源版 RocketMQ 最大支持2小时的延迟。
    - **实现**: 内部依然调用同步发送逻辑，因此具备同步发送的可靠性，但同样会阻塞线程。

#### 3.2.3 异步消息发送 (sendAsync)

- **方法签名**: `void sendAsync(GXRocketMQMessageReqDto messageDto, SendCallback sendCallback)`
- **描述**: 异步发送消息。消息发送请求提交后，业务线程不会阻塞等待 Broker 响应，而是立即返回。发送结果通过注册的 `SendCallback` 回调函数进行处理。
- **参数**:
    - `messageDto`: 消息请求对象。
    - `sendCallback`: 回调接口，需要实现 `onSuccess(SendResult sendResult)` 和 `onException(Throwable e)` 方法。
- **返回**: `void`
- **使用场景**: 对响应时间敏感，且可以接受消息最终一致性的场景。例如：用户操作日志记录、用户行为数据采集、通知类消息（如图文推送结果通知）。可以显著提高系统的吞吐量。
- **注意事项**:
    - **回调处理**: 必须在 `SendCallback` 中妥善处理发送成功和失败的逻辑。例如，失败时可以记录日志、尝试重发（需注意幂等性）或通知相关方。
    - **线程池**: 异步发送依赖于生产者内部的线程池。如果业务量过大，需要关注线程池的配置和状态。
    - **资源管理**: 回调方法中应避免执行耗时操作，以免阻塞回调线程池，影响其他异步消息的处理。

#### 3.2.4 单向消息发送 (sendOneway)

- **方法签名**: `void sendOneway(GXRocketMQMessageReqDto messageDto)`
- **描述**: 单向（Oneway）发送消息。消息发送请求提交后，不等待 Broker 的任何响应，也不关心消息是否发送成功。这种方式的发送效率最高，但可靠性最低。
- **参数**: `messageDto`: 消息请求对象。
- **返回**: `void`
- **使用场景**: 对消息可靠性要求不高，允许少量消息丢失的场景。例如：非核心业务的监控数据上报、应用性能指标采样、可选的通知等。适用于需要极高吞吐量且能容忍数据丢失的场景。
- **注意事项**:
    - **消息丢失风险**: 由于不等待 Broker 确认，网络抖动、Broker故障等都可能导致消息丢失，且客户端无感知。
    - **适用性评估**: 仅在业务可以完全容忍消息丢失的情况下使用。

#### 3.2.5 同步批量消息发送 (sendBatch)

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

### 3.3 安全性、内存与性能考量

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