# leaf-base-rabbitmq 开发手册（AMQP 1.0）

## 1. 模块概述

`leaf-base-rabbitmq` 是 `maple-leaf-framework` 中负责 RabbitMQ 集成的基础模块，**基于 AMQP 1.0 协议**
（Spring Boot 4.2 + `spring-amqp-client` + Qpid ProtonJ2），提供：

* **消息发送**：`GXSendRabbitMQService` 发送 JSON 消息并补充追踪头，支持异步结算回调与同步等待。
* **动态资源管理**：运行时声明/删除队列、交换机、绑定，支持一站式创建消息通道（同步/异步）。
  协议本身没有拓扑操作，统一通过 RabbitMQ Management HTTP API 完成。
* **发布结果回调**：`GXConfirmCallback`、`GXReturnsCallback`、`GXRecoveryCallback` 扩展点。
* **双配置来源**：`spring.rabbitmq.*`（兼容）或 `spring.amqp.*`（Boot 原生）由本地 `rabbit.yml`
  或 Nacos `rabbit.yml` 提供，二选一。
* **默认监听器**：`GXDefaultRabbitMQQueueListenerImpl`，`@AmqpListener` + 显式 ack + 计数。

本模块不承载消费编排、死信治理、幂等与消息事务，相关能力边界见 §8。

### 1.1 与 0-9-1 版本的关键差异

| 维度 | 0-9-1（迁移前） | AMQP 1.0（当前） |
| --- | --- | --- |
| 依赖 | `spring-boot-rabbitmq`（`spring-rabbit`） | `spring-boot-starter-amqp`（`spring-amqp-client` + ProtonJ2） |
| 发送 | `RabbitTemplate.convertAndSend` | `GXAmqpMessagePublisher` → `/exchanges/...` 或 `/queues/...` |
| 确认 | `basic.ack` / `basic.nack` | delivery outcome：`accepted` / `rejected` / `released` |
| 退回 | `basic.return` | `released` outcome |
| 拓扑 | `RabbitAdmin`（协议内声明） | `GXAmqpManagementClient`（Management HTTP API） |
| 消费 | `@RabbitListener` | `@AmqpListener(addresses = "/queues/<queue>")` |
| ack 时机 | 容器 `AcknowledgeMode` | 容器 `autoAccept=false`，监听器显式 `ACCEPT/REJECT/REQUEUE` |
| 调度器 | `rabbitTaskScheduler` | 已删除（AMQP 1.0 客户端自带执行器） |

## 2. 核心组件

### 2.1 Maven 依赖

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-rabbitmq</artifactId>
    <version>${maple-leaf-framework.version}</version>
</dependency>
```

依赖 `leaf-base-framework`、`leaf-base-nacos`、`spring-boot-starter-amqp`、`tools.jackson`（Jackson 3）。
不再依赖 `spring-boot-rabbitmq`、`spring-rabbit`、`spring-rabbit-test`、`leaf-base-retry`。

### 2.2 装配类 `GXRabbitMQConfig`

`@EnableAmqp`，连接工厂与 `AmqpClient` 由 Boot `AmqpAutoConfiguration` 提供，本类补齐 Maple Leaf 约定：

* **`AmqpConnectionDetails`**：`spring.amqp.host/port/username/password` 显式配置优先，
  回退 `spring.rabbitmq.*`，使既有 `rabbit.yml` 继续生效。
* **`ConnectionOptionsCustomizer`**：补 vhost（`spring.amqp.virtual-host` →
  `spring.rabbitmq.virtual-host` → `/`）、重连（1s 起、最大 30s、指数退避）与请求超时 10s。
  vhost 必须显式设置：AMQP 1.0 通过 open 帧的 hostname 传递，Boot 的 `AmqpProperties` 未暴露该字段。
* **`JacksonJsonMessageConverter`**：`DefaultClassMapper` 信任 `cn.hutool.core.lang`、`cn.hutool.core`、
  `cn.maple`，保持 `__TypeId__` 契约。
* **`GXAmqpMessagePublisher`**：匿名 sender + 结算映射，`close()` 随容器销毁。
* **`GXAmqpManagementClient`**：拓扑管理；`maple.framework.rabbitmq.management.enabled=false` 时不创建。
* **`amqpListenerContainerFactory`**：覆盖 Boot 默认工厂，`autoAccept=false` + 应用
  `spring.amqp.listener.*`（并发、credits、receive-timeout、优雅关闭）。

### 2.3 配置属性

* **`GXRabbitMQProperties`**：独立 POJO，绑定 `spring.rabbitmq`：
  `host`、`port`、`username`、`password`、`virtualHost`、`defaultQueueName`、`confirmTimeout`、
  `management.*`。不再继承 `RabbitProperties`，经典连接工厂字段（`connectionLimit`、
  `channelCacheSize`、`cacheMode`、`channelCheckoutTimeout`）已移除。
* **`GXLocalRabbitMQProperties`**：`maple.framework.rabbitmq.config-source=local` 时装配，从
  `classpath:/<profile>/rabbit.yml` 与 `file:./config/<profile>/rabbit.yml` 加载（两处都存在时后者覆盖）。
* **`GXNacosRabbitMQProperties`**：未配置或 `config-source=nacos` 时装配（`@Primary`）。真正的拉取由
  `GXRabbitMQEnvironmentPostProcessor` 完成：仅在配置了 `spring.cloud.nacos.config.server-addr`
  （或 `nacos.config.server-addr`）时，用 Nacos 客户端拉取 `dataId=rabbit.yml`，超时 2 秒，失败只告警；
  属性以**最低优先级**加入，`application.yml` 仍可覆盖；启动期一次性加载，不支持动态刷新。

常用配置键：

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.amqp.host/port/username/password` | Boot 默认 | AMQP 1.0 原生前缀，显式配置时优先 |
| `spring.rabbitmq.host/port/username/password` | `localhost` / `5672` | 历史前缀，作为回退来源 |
| `spring.rabbitmq.addresses` | 无 | 历史 `host1:5672,host2:5672`，取第一个作为端点（优先于 host/port） |
| `spring.rabbitmq.virtual-host` | `/` | vhost，注入 open 帧 |
| `maple.framework.rabbitmq.config-source` | `nacos` | `local` / `nacos` |
| `maple.framework.rabbitmq.confirm-timeout` | `5000` | 异步发送等待结算的毫秒数 |
| `maple.framework.rabbitmq.management.enabled` | `true` | 关闭后拓扑方法抛异常 |
| `maple.framework.rabbitmq.management.url` | `http://<host>:15672` | Management API 基地址；凭据回退 `management.*` → `spring.amqp.*` → `spring.rabbitmq.*` |
| `maple.framework.rabbitmq.thread-pool.*` | 见 §3.3 | 异步声明线程池 |
| `maple.framework.mq.rabbitmq.enable` | `false` | 默认监听器启停 |
| `maple.framework.mq.rabbitmq.default-queue-name` | `maple.default.queue` | 默认监听队列 |

### 2.4 发送服务 `GXSendRabbitMQService`

| 方法 | 说明 |
| --- | --- |
| `sendNormalMessage` | 异步发送；返回 `null` 仅表示已写出，结算结果走回调 |
| `sendNormalMessageAwait` | 同步等待结算，返回是否 `accepted` |
| `sendRawMessage` | 发送原始载荷（不重新序列化），用于原样重投 |
| `createQueue` / `createDurableQueue` / `createTemporaryQueue` | 每次真实声明；同队列并发由单飞收敛 |
| `deleteQueue` / `queueExists` / `purgeQueue` | 一律查询 Broker，不使用长期缓存 |
| `createExchange` / `bindQueueToExchange` / `unbindQueueFromExchange` | 幂等声明 |
| `setupMessageChannel` / `setupDurableMessageChannel` | 交换机→队列→绑定，失败异常携带 `completedSteps` |
| `setupMessageChannelAsync` | 异步版本；失败以 `failedFuture` 返回 |

地址映射（`GXAmqpAddress`，RFC 3986 编码）：

| 入参 | AMQP 1.0 地址 |
| --- | --- |
| `exchange=""` + `routingKey=q` | `/queues/q` |
| `exchange=e` + `routingKey=k` | `/exchanges/e/k` |
| `exchange=e` + `routingKey=""` | `/exchanges/e` |

失败语义：

* 参数非法 → `GXBusinessException`；`exclusive=true` 同样抛 `GXBusinessException`。
* Broker 操作失败 → `GXRabbitMQOperationException`（`operation` 标识步骤，`detail.completedSteps`
  标识已完成步骤，管理 API 的 `reason` 会拼进异常消息）。
* `deleteQueue` 返回 `false` 仅表示"队列不存在"；`unbindQueueFromExchange` 返回 `false` 表示绑定不存在。
* `purgeQueue` 成功返回 `true`；队列不存在时包装为 `GXRabbitMQOperationException`。
* 管理 API 未启用时拓扑方法抛带提示的 `GXRabbitMQOperationException`。

发送前的处理顺序：校验 → `MessageProperties` 深拷贝 → 写入 `X-Trace-Id`（优先复用 MDC）、
`X-Timestamp`、`X-Source-Service`、`__TypeId__`、缺省 `messageId` → Hutool 序列化 → 发送。
调用方 `MessageProperties` 不会被修改；`tag` 字段仅为兼容保留，发送侧忽略。
这些 header 在 AMQP 1.0 中作为 message application properties 传输，消费端还原为同名 header。

### 2.5 请求 DTO 与关联数据

`GXRabbitMQMessageReqDto`（`@Builder`/`@Data`）字段：`exchange`（默认交换机传空串）、`routingKey`、
`data`（`Dict`）、`tag`（已废弃）、`correlationData`（`GXCorrelationData`，`@Nullable`）、
`messageProperties`。

`GXCorrelationData` 替代经典 `CorrelationData`：`id` + 可选 `payload` + `settlement()`（可等待的结算
Future，`await(Duration)` 便捷方法）。

### 2.6 监听器

* `GXRabbitMQQueueListener`：只声明 `process(Message)`；实现方不得吞异常。
* `GXDefaultRabbitMQQueueListenerImpl`：`@ConditionalOnProperty(maple.framework.mq.rabbitmq.enable=true)`，
  方法 `onMessage(Message, AmqpAcknowledgment)`，地址为
  `/queues/${maple.framework.mq.rabbitmq.default-queue-name:...}`。
* 结算语义（容器 `autoAccept=false`）：成功 `ACCEPT`；空消息/无法解析 JSON（`AmqpRejectAndDontRequeueException`）
  → `REJECT`；其他运行时异常 → `REQUEUE`。
* Bean 必须饿汉初始化：`@Lazy` 会导致 `@AmqpListener` 端点永不注册。
* 计数：`rabbitmq.listener.messages.processed` / `.rejected`（`reason=empty|unparseable`）/
  `.failed`（`type`），无 `MeterRegistry` 时仅记 debug。
* 毒消息兜底：`GXAmqpListenerErrorHandler` 在消息体无法被转换器解析时直接 `REJECT` 该 delivery，
  避免监听器方法还没执行就被容器无限 release 重投；日志关键字 `RABBITMQ_LISTENER_REJECTED reason=unconvertible`。

自定义监听器写法：

```java
@AmqpListener(addresses = "/queues/order.queue")
public void onMessage(Message message, AmqpAcknowledgment acknowledgment) {
    try {
        handle(message);
        acknowledgment.acknowledge(AmqpAcknowledgment.Status.ACCEPT);
    } catch (AmqpRejectAndDontRequeueException e) {
        acknowledgment.acknowledge(AmqpAcknowledgment.Status.REJECT);
    } catch (RuntimeException e) {
        acknowledgment.acknowledge(AmqpAcknowledgment.Status.REQUEUE);
    }
}
```

> 消费地址必须是 `/queues/<queue>`；裸队列名会被 RabbitMQ 以 `amqp_address_v1_not_permitted` 拒绝。
> 队列必须已存在（v2 地址不再自动声明）。

### 2.7 回调

* `GXConfirmCallback#confirm(GXCorrelationData, boolean ack, String cause)`：`ack=true` 仅表示
  `accepted`；`rejected`、`released` 与发送异常一律 `ack=false`。
* `GXReturnsCallback#returnedMessage(GXReturnedMessage)`：不可路由（`released`）退回，
  等价于经典 `basic.return`。
* `GXRecoveryCallback#recover(Throwable)`：结算失败（连接中断、链路关闭、结算超时）时触发。

实现类通过 `GXSpringContextUtils.getBean` 按类型查找；不存在时 confirm 失败只记 warn，returns 静默跳过。

## 3. 配置示例

### 3.1 本地配置

```yaml
maple:
  framework:
    rabbitmq:
      config-source: local
      management:
        url: http://${RABBITMQ_HOST:localhost}:15672
    mq:
      rabbitmq:
        enable: true
        default-queue-name: maple.default.queue
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: 5672
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    virtual-host: /
```

`config-source=local` 时，`rabbit.yml` 需位于类路径 `<profile>/rabbit.yml` 或工作目录
`./config/<profile>/rabbit.yml`。

### 3.2 Nacos 配置

```yaml
maple:
  framework:
    rabbitmq:
      config-source: nacos
spring:
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:}
        group: DEFAULT_GROUP
```

### 3.3 线程池

```yaml
maple:
  framework:
    rabbitmq:
      thread-pool:
        core-size: 4          # 默认 CPU 核数
        max-size: 8           # 仅在队列满时生效
        queue-capacity: 1000  # <=0 使用 SynchronousQueue，使 max-size 立即生效
```

### 3.4 监听并发（Boot 原生）

```yaml
spring:
  amqp:
    listener:
      concurrency: 8            # 每个地址的消费者数
      initial-credits: 100
      receive-timeout: 1s
      graceful-shutdown-period: 30s
```

## 4. 使用示例

### 4.1 发送消息

```java
GXRabbitMQMessageReqDto request = GXRabbitMQMessageReqDto.builder()
        .exchange("order.exchange")
        .routingKey("order.created")
        .data(Dict.create().set("orderId", order.getId()))
        .messageProperties(new MessageProperties())
        .build();
sendRabbitMQService.sendNormalMessage(request);

// 需要同步确认时
boolean accepted = sendRabbitMQService.sendNormalMessageAwait(request, Duration.ofSeconds(5));
```

### 4.2 动态创建通道

```java
boolean ready = sendRabbitMQService.setupMessageChannel(
        "order.queue", true, false, false, null,
        new DirectExchange("order.exchange"), "order.created");
```

失败时抛 `GXRabbitMQOperationException`，`getDetail().get("completedSteps")` 可用于判断是否需要补偿。

### 4.3 异步创建通道

```java
sendRabbitMQService.setupMessageChannelAsync("order.queue", true, false, false, null,
                new DirectExchange("order.exchange"), "order.created")
        .thenAccept(ready -> log.debug("channel ready: {}", ready))
        .exceptionally(error -> {
            log.error("channel setup failed: {}", error.getClass().getName());
            return null;
        });
```

## 5. 与其他模块的关系

* `leaf-base-framework`：`GXBusinessService`、`GXBaseReqDto`、`GXTraceIdContextUtils`、`GXSpringContextUtils`。
* `leaf-base-nacos`：提供 `nacos-client`，供启动期拉取 `rabbit.yml`。
* `leaf-base-data-sync`：Canal 消费链路使用本模块的发送服务与 `@AmqpListener` 契约。
* `leaf-base-retry`：**不再依赖**。

## 6. 线程安全与性能

### 6.1 线程安全

* `GXAmqpMessagePublisher` 内部对 sender 链路加锁创建，链路被关闭时自动重建；发送本身可并发。
* `GXSendRabbitMQServiceImpl` 无 `queueCache` 与全局锁；同队列并发声明通过
  `ConcurrentHashMap<String, CompletableFuture<Queue>>` 单飞收敛，异常完成同样移除条目。
* 发送前对 `MessageProperties` 做深拷贝，调用方对象不会被并发发送串改。
* 结算等待在虚拟线程上完成，不占用调用线程。
* 异步执行器为 `GXMdcWrapperThreadPoolExecutor`，提交任务时自动捕获并恢复 MDC；执行器有
  `@PreDestroy` 关闭路径，关闭时等待在途任务。

### 6.2 性能

* 每条消息：1 次 JSON 序列化 + 1 次属性快照 + 1 次结算 RTT；成功路径 0 条 info 日志。
* traceId 仅在 MDC 缺失时生成；`spring.application.name` 装配期注入，不逐条查询 Environment。
* 实测（本机 → 局域网 RabbitMQ 4.4.1）：顺序发送 p95 ≈ 1.4 ms；8 线程并发 ≈ 3100 msg/s。
* `max-size` 的 JDK 语义：`LinkedBlockingQueue` 未满时不会扩容到 max；需要严格上限时设置
  `queue-capacity<=0`。

## 7. 运维与排障

* 配置来源：启动日志 `RabbitMQ config source: ...`（不含口令）。
* 监听失败：检索 `RABBITMQ_LISTENER_REJECTED`（业务拒绝或 `reason=unconvertible` 毒消息）/ `RABBITMQ_LISTENER_REQUEUE`。
* 声明失败：异常 `detail` 中的 `operation`、`completedSteps`、`failedStep`；管理 API 失败会带上
  HTTP 状态与 RabbitMQ `reason`（例如 `transient_nonexcl_queues` 未开启）。
* 发送未路由：日志 `RabbitMQ message not routed: address=...`（`released`），并触发
  `GXReturnsCallback`。
* 队列名/交换机名长度上限 255；含控制字符会被前置拒绝。
* 拓扑依赖 Management 插件（默认 15672）；生产环境若关闭该插件，请改为部署期声明并设置
  `management.enabled=false`。

## 8. 能力边界

| 能力 | 状态 | 替代方案 |
| --- | --- | --- |
| 排他队列 | 不支持 | 自行持有连接声明 |
| 拓扑声明 | 依赖 Management API | 部署期声明（IaC / rabbitmqadmin） |
| 批量发送 | 不支持 | 业务侧合并消息体或循环发送 |
| 延迟/优先级/TTL | 未封装 | `MessageProperties`（TTL 映射为 `timeToLive`） |
| 死信治理 | 未封装 | Broker 侧配置 DLX，依赖监听器 `REJECT` 语义 |
| 消费重试策略 | 交给调用方 | 监听器内重投或 `REQUEUE` |
| 消息大小校验 | 未实现 | 调用方控制载荷，Broker 限制兜底 |
| 指标 | 监听计数 | `MeterRegistry` 采集 `rabbitmq.listener.messages.*` |
| Nacos 动态刷新 | 不支持 | 重启生效或改用 `spring.config.import` |
| 独立关闭监听基础设施 | 不支持 | `@EnableAmqp` 随 `GXRabbitMQConfig` 生效；需要禁用时排除该配置类 |

## 9. 测试与验证

```bash
# 单元 + 上下文 + 并发；无 Broker 时集成用例自动 Skip
mvn -o -B -pl leaf-base-rabbitmq test

# 真实 Broker
RABBITMQ_HOST=192.168.7.214 RABBITMQ_PORT=5672 \
RABBITMQ_USERNAME=<user> RABBITMQ_PASSWORD=<password> \
mvn -o -B -pl leaf-base-rabbitmq test
```

集成用例覆盖：管理 API 拓扑声明/查询/清空/删除、默认交换机发布、交换机 + 路由键发布、
不可路由结算、并发发送、Spring 上下文端到端消费与显式 ack、顺序/并发吞吐采样。
凭据只从环境变量读取，不入库。

基线：158 个用例（148 执行 + 10 集成），真实 RabbitMQ 4.4.1 全绿（2026-11-27）。

---
本文档与实现保持一致；行为变更必须同步更新本文件、`AGENTS.md` 与 `../specs/016-rabbitmq`。
