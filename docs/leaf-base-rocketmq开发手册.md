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

### 2.1 配置方式

Leaf-Base-RocketMQ 模块支持两种配置方式：

1. **本地配置**：通过本地 YAML 文件进行配置
2. **Nacos 配置**：通过 Nacos 配置中心进行配置

系统会根据是否引入 Nacos 相关依赖自动选择配置方式。

### 2.2 本地配置

当未引入 Nacos 配置中心时，系统使用本地配置。配置文件路径为：`classpath:/${spring.profiles.active}/rocket-mq.yml`

```java
@Data
@Slf4j
@Component
@SuppressWarnings("all")
@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@PropertySource(value = {"classpath:/${spring.profiles.active}/rocket-mq.yml"}, factory = GXYamlPropertySourceFactory.class, encoding = "utf-8", ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "rocketmq")
public class GXLocalRocketMQConfigProperties {
    public GXLocalRocketMQConfigProperties() {
        log.info("RocketMQ数据源的配置使用的是本地配置");
    }
}
```

### 2.3 Nacos 配置

当引入 Nacos 配置中心时，系统使用 Nacos 配置。配置文件为 Nacos 中的 `rocket-mq.yml`

```java
@Data
@Slf4j
@Component
@SuppressWarnings("all")
@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@NacosConfigurationProperties(dataId = "rocket-mq.yml",
        groupId = "${spring.cloud.nacos.config.group:${nacos.config.group:}}",
        properties = @NacosProperties(
                serverAddr = "${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:}}",
                namespace = "${spring.cloud.nacos.config.namespace:${nacos.config.namespace:}}",
                username = "${spring.cloud.nacos.username:${nacos.config.username:}}",
                password = "${spring.cloud.nacos.password:${nacos.config.password:}}"))
@ConfigurationProperties(prefix = "rocketmq")
public class GXNacosRocketMQConfigProperties {
    public GXNacosRocketMQConfigProperties() {
        log.info("RocketMQ数据源的配置使用的是NACOS配置");
    }
}
```

### 2.4 配置示例

以下是 RocketMQ 配置文件示例（适用于本地配置和 Nacos 配置）：

```yaml
rocketmq:
  name-server: 127.0.0.1:9876  # RocketMQ 名称服务器地址
  producer:
    group: my-producer-group    # 生产者组名
    send-message-timeout: 3000  # 消息发送超时时间（毫秒）
    retry-times-when-send-failed: 2  # 同步发送消息失败重试次数
    retry-times-when-send-async-failed: 2  # 异步发送消息失败重试次数
    max-message-size: 4194304   # 消息最大长度（字节）
    compress-message-body-threshold: 4096  # 消息体压缩阈值
    retry-next-server: false    # 是否在内部发送失败时重试另一个broker
```

## 3. 核心功能

### 3.1 消息请求DTO

`GXRocketMQMessageReqDto` 类是消息发送的核心数据传输对象，用于封装发送到 RocketMQ 的消息数据。

#### 3.1.1 主要属性

| 属性名 | 类型 | 描述 |
| ------ | ---- | ---- |
| topic | String | 消息主题，必填 |
| tag | String | 消息标签，可选 |
| body | String | 消息内容，必填，建议使用JSON格式 |
| deliverTime | Integer | 延迟发送时间（秒），用于延迟消息 |
| messageKey | String | 消息唯一标识，可选，建议设置，便于消息追踪 |

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
customMessage.setBody(JSONUtil.toJsonStr(order));
customMessage.setDeliverTime(3600);  // 1小时后发送
customMessage.setMessageKey("ORD123456_REFUND");
```

### 3.2 消息发送服务

`GXSendRocketMQService` 接口及其实现类 `GXSendRocketMQServiceImpl` 提供了多种消息发送模式。

#### 3.2.1 发送普通消息

普通消息是最基础的消息类型，不保证消息顺序，适用于一般的业务场景。

```java
/**
 * 发送普通消息
 *
 * @param messageReqDto 待发送的消息对象
 */
void sendNormalMessage(GXRocketMQMessageReqDto messageReqDto);
```

使用示例：

```java
GXRocketMQMessageReqDto normalMsg = new GXRocketMQMessageReqDto();
normalMsg.setTopic("topic_normal");
normalMsg.setTag("tag_normal");
normalMsg.setBody("{\"key\":\"value\"}");
normalMsg.setMessageKey("unique_key_001");
sendRocketMQService.sendNormalMessage(normalMsg);
```

#### 3.2.2 发送延迟消息

延迟消息是指消息发送后，不会立即投递，而是在指定的时间后才投递到消费者进行消费。适用于定时任务、订单超时取消等场景。

```java
/**
 * 发送延迟消息
 *
 * @param messageReqDto 待发送的消息
 * @return 消息ID
 */
String sendDelayMessage(GXRocketMQMessageReqDto messageReqDto);
```

使用示例：

```java
GXRocketMQMessageReqDto delayMsg = new GXRocketMQMessageReqDto();
delayMsg.setTopic("topic_delay");
delayMsg.setTag("tag_delay");
delayMsg.setBody("{\"order_id\":\"12345\",\"status\":\"pending\"}");
delayMsg.setDeliverTime(10); // 10秒后投递
delayMsg.setMessageKey("order_timeout_12345");
String msgId = sendRocketMQService.sendDelayMessage(delayMsg);
```

#### 3.2.3 发送同步消息

同步消息是指消息发送方发出数据后，会在收到接收方发回响应之后才发下一个数据包。适用于重要的通知消息，如重要通知邮件、营销短信等。

```java
/**
 * 同步消息
 *
 * @param messageReqDto 待发送的消息对象
 * @return 发送是否成功
 */
boolean syncSend(GXRocketMQMessageReqDto messageReqDto);
```

使用示例：

```java
GXRocketMQMessageReqDto syncMsg = new GXRocketMQMessageReqDto();
syncMsg.setTopic("topic_sync");
syncMsg.setTag("tag_sync");
syncMsg.setBody("{\"notification\":\"important_update\"}");
boolean syncResult = sendRocketMQService.syncSend(syncMsg);
```

#### 3.2.4 发送异步消息

异步消息是指发送方发出数据后，不等接收方发回响应，接着发送下个数据包。适用于可靠性要求不高，但要求高吞吐量的场景，如日志收集。

```java
/**
 * 异步发送
 *
 * @param messageReqDto 待发送的消息
 * @return 发送请求是否成功提交
 */
boolean sendAsync(GXRocketMQMessageReqDto messageReqDto);
```

使用示例：

```java
GXRocketMQMessageReqDto asyncMsg = new GXRocketMQMessageReqDto();
asyncMsg.setTopic("topic_async");
asyncMsg.setTag("tag_async");
asyncMsg.setBody("{\"log\":\"system_operation_log\"}");
boolean asyncResult = sendRocketMQService.sendAsync(asyncMsg);
```

#### 3.2.5 发送单向消息

单向消息是指只负责发送消息，不等待服务器回应且没有回调函数触发。适用于不关心发送结果的场景，如监控数据上报。相比于异步消息，单向消息发送方式耗时非常短，性能最高。

```java
/**
 * 快速发送
 *
 * @param messageReqDto 待发送的消息对象
 * @return 发送请求是否成功提交
 */
boolean sendOneway(GXRocketMQMessageReqDto messageReqDto);
```

使用示例：

```java
GXRocketMQMessageReqDto onewayMsg = new GXRocketMQMessageReqDto();
onewayMsg.setTopic("topic_oneway");
onewayMsg.setTag("tag_oneway");
onewayMsg.setBody("{\"metric\":\"system_status\"}");
boolean onewayResult = sendRocketMQService.sendOneway(onewayMsg);
```

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