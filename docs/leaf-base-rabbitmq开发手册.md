# leaf-base-rabbitmq 开发手册

## 1. 模块概述

`leaf-base-rabbitmq` 是 `maple-leaf-framework` 中负责 RabbitMQ 集成的基础模块，提供：

* **消息发送**：`GXSendRabbitMQService` 发送 JSON 消息并补充追踪头。
* **动态资源管理**：运行时声明/删除队列、交换机、绑定，支持一站式创建消息通道（同步/异步）。
* **发布确认与退回**：`GXConfirmCallback`、`GXReturnsCallback`、`GXRecoveryCallback` 扩展点。
* **双配置来源**：`spring.rabbitmq.*` 由本地 `rabbit.yml` 或 Nacos `rabbit.yml` 提供，二选一。
* **默认监听器**：`GXDefaultRabbitMQQueueListenerImpl`，带失败分层处置与计数。

本模块不承载消费编排、死信治理、幂等与消息事务，相关能力边界见 §8。

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
不再依赖 `leaf-base-retry`（`GXRecoveryCallback` 需调用方自行设置 `RetryTemplate` 才会触发）。

### 2.2 装配类 `GXRabbitMQConfig`

* 构造注入 `ConnectionFactory` 与 `GXRabbitMQProperties`，`@PostConstruct` 把扩展属性（`cacheMode`、`channelCacheSize`、`connectionLimit`、`channelCheckoutTimeout`）应用到 `CachingConnectionFactory`，并打印一条 ASCII 启动日志。
* `RabbitTemplate`：`JacksonJsonMessageConverter` + `DefaultClassMapper`（信任 `cn.hutool.core.lang`、`cn.hutool.core`、`cn.maple`），装配三个回调。
* `rabbitTaskScheduler`：`ThreadPoolTaskScheduler`，`poolSize=max(2, CPU)`，JDK ≥21 使用虚拟线程，30 秒优雅关闭；由 `maple.framework.rabbitmq.scheduler.expose` 控制是否作为 `TaskScheduler` Bean 暴露（默认 `true`）。
* `rabbitAdmin`、`asyncRabbitTemplate`、`rabbitMessagingTemplate` 与历史一致。

### 2.3 配置属性

* **`GXRabbitMQProperties`**：绑定 `spring.rabbitmq`，在 Boot `RabbitProperties` 之上扩展 `defaultQueueName`、`connectionLimit`、`channelCacheSize`、`cacheMode`、`channelCheckoutTimeout`。
* **`GXLocalRabbitMQProperties`**：`maple.framework.rabbitmq.config-source=local` 时装配，从
  `classpath:/<profile>/rabbit.yml` 与 `file:./config/<profile>/rabbit.yml` 加载（两处都存在时后者覆盖）。
* **`GXNacosRabbitMQProperties`**：未配置或 `config-source=nacos` 时装配（`@Primary`）。真正的拉取由
  `GXRabbitMQEnvironmentPostProcessor` 完成：仅在配置了 `spring.cloud.nacos.config.server-addr`（或 `nacos.config.server-addr`）时，用 Nacos 客户端拉取 `dataId=rabbit.yml`，超时 2 秒，失败只告警；属性以**最低优先级**加入，`application.yml` 仍可覆盖；启动期一次性加载，不支持动态刷新。

常用配置键：

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `maple.framework.rabbitmq.config-source` | `nacos` | `local` / `nacos` |
| `maple.framework.rabbitmq.scheduler.expose` | `true` | 是否暴露 `rabbitTaskScheduler` |
| `maple.framework.rabbitmq.thread-pool.*` | 见 §3.2 | 异步声明线程池 |
| `maple.framework.mq.rabbitmq.enable` | `false` | 默认监听器启停 |
| `maple.framework.mq.rabbitmq.default-queue-name` | `maple.default.queue` | 默认监听队列 |

### 2.4 发送服务 `GXSendRabbitMQService`

| 方法 | 说明 |
| --- | --- |
| `sendNormalMessage` | 发送 JSON 消息；返回 `null` 仅表示已提交给 `RabbitTemplate` |
| `createQueue` / `createDurableQueue` / `createTemporaryQueue` | 每次真实声明；同队列并发由单飞收敛 |
| `deleteQueue` / `queueExists` / `purgeQueue` | 一律查询 Broker，不使用长期缓存 |
| `createExchange` / `bindQueueToExchange` / `unbindQueueFromExchange` | 幂等声明 |
| `setupMessageChannel` / `setupDurableMessageChannel` | 交换机→队列→绑定，失败异常携带 `completedSteps` |
| `setupMessageChannelAsync` | 异步版本；失败以 `failedFuture` 返回 |

失败语义：

* 参数非法 → `GXBusinessException`。
* Broker 操作失败 → `GXRabbitMQOperationException`（`operation` 标识步骤，`detail.completedSteps` 标识已完成步骤）。
* `deleteQueue` 返回 `false` 仅表示"队列不存在"。
* `purgeQueue` 成功返回 `true`；队列不存在时由 Broker 返回 404，包装为 `GXRabbitMQOperationException`（不返回 `false`）。
* `unbindQueueFromExchange` 幂等，`removeBinding` 成功即返回 `true`（不返回 `false`）。

发送前的处理顺序：校验 → `MessageProperties` 深拷贝 → 写入 `X-Trace-Id`（优先复用 MDC）、`X-Timestamp`、`X-Source-Service`、`__TypeId__`、缺省 `messageId` → Hutool 序列化 → `convertAndSend`。
调用方 `MessageProperties` 不会被修改；`tag` 字段仅为兼容保留，发送侧忽略。

### 2.5 请求 DTO

`GXRabbitMQMessageReqDto`（`@Builder`/`@Data`）字段：`exchange`（默认交换机传空串）、`routingKey`、`data`（`Dict`）、`tag`（已废弃）、`correlationData`（`@Nullable`）、`messageProperties`。

### 2.6 监听器

* `GXRabbitMQQueueListener`：只声明 `process(Message)`；实现方不得吞异常。
* `GXDefaultRabbitMQQueueListenerImpl`：`@ConditionalOnProperty(maple.framework.mq.rabbitmq.enable=true)`，队列名取自
  `${maple.framework.mq.rabbitmq.default-queue-name:${spring.rabbitmq.default-queue-name:${spring.rabbitmq.defaultQueueName:maple.default.queue}}}`。
* 失败处置：空消息/无法解析 JSON → `AmqpRejectAndDontRequeueException`；其他运行时异常原样抛出交给容器重试。
* 计数：`rabbitmq.listener.messages.processed` / `.rejected`（`reason=empty|unparseable`）/ `.failed`（`type`），无 `MeterRegistry` 时仅记 debug。

### 2.7 回调

* `GXConfirmCallback#confirm(CorrelationData, boolean, String)`：发送到交换机的确认。
* `GXReturnsCallback#returnedMessage(ReturnedMessage)`：路由失败退回。
* `GXRecoveryCallback#recover(Throwable)`：**仅当调用方为 `RabbitTemplate` 设置 `RetryTemplate` 时触发**；模块本身不引入 `spring-retry`。

实现类通过 `GXSpringContextUtils.getBean` 按类型查找；存在多个实现时按"未找到"处理并记录告警。

## 3. 配置示例

### 3.1 本地配置

```yaml
maple:
  framework:
    rabbitmq:
      config-source: local
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

`config-source=local` 时，`rabbit.yml` 需位于类路径 `<profile>/rabbit.yml` 或工作目录 `./config/<profile>/rabbit.yml`。

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
* `leaf-base-retry`：**不再依赖**。

## 6. 线程安全与性能

### 6.1 线程安全

* `RabbitTemplate`、`RabbitAdmin` 线程安全，可被并发复用。
* `GXSendRabbitMQServiceImpl` 无 `queueCache` 与全局锁；同队列并发声明通过
  `ConcurrentHashMap<String, CompletableFuture<Queue>>` 单飞收敛，异常完成同样移除条目。
* 发送前对 `MessageProperties` 做深拷贝，调用方对象不会被并发发送串改。
* 异步执行器为 `GXMdcWrapperThreadPoolExecutor`，提交任务时自动捕获并恢复 MDC（无需手工拷贝）；异步执行器与调度器均有 `@PreDestroy`/容器销毁路径，关闭时等待在途任务。

### 6.2 性能

* 每条消息：1 次 JSON 序列化 + 1 次属性快照 + 1 次 RTT；成功路径 0 条 info 日志。
* traceId 仅在 MDC 缺失时生成；`spring.application.name` 装配期注入，不逐条查询 Environment。
* 调度器并发度 `max(2, CPU)`，JDK 21 使用虚拟线程；不再"每次触发新建执行器并阻塞等待"。
* `max-size` 的 JDK 语义：`LinkedBlockingQueue` 未满时不会扩容到 max；需要严格上限时设置 `queue-capacity<=0`。

## 7. 运维与排障

* 配置来源：启动日志 `RabbitMQ config source: ...`（不含口令）。
* 监听失败：检索 `RABBITMQ_LISTENER_REJECTED` / `RABBITMQ_LISTENER_FAILED`。
* 声明失败：异常 `detail` 中的 `operation`、`completedSteps`、`failedStep`。
* 队列名/交换机名长度上限 255；含控制字符会被前置拒绝。

## 8. 能力边界

| 能力 | 状态 | 替代方案 |
| --- | --- | --- |
| 排他队列 | 不支持 | 自行持有连接并用 `QueueBuilder` 声明 |
| 批量发送 | 不支持 | 业务侧合并消息体或循环发送 |
| 延迟/优先级/TTL | 未封装 | `MessageProperties` 或队列 `arguments` |
| 死信治理 | 未封装 | Broker 侧配置 DLX，依赖监听器拒绝语义 |
| 消费重试策略 | 交给容器 | `spring.rabbitmq.listener.simple.retry.*` |
| 消息大小校验 | 未实现 | 调用方控制载荷，Broker 限制兜底 |
| 指标 | 监听计数 | `MeterRegistry` 采集 `rabbitmq.listener.messages.*` |
| Nacos 动态刷新 | 不支持 | 重启生效或改用 `spring.config.import` |
| 独立关闭监听基础设施 | 不支持 | `@EnableRabbit` 随 `GXRabbitMQConfig` 生效；需要禁用时排除该配置类 |

## 9. 测试与验证

```powershell
# 单元 + 上下文 + 并发；无 Broker 时集成用例自动 Skip
mvn -o -B -pl leaf-base-rabbitmq test

# 真实 Broker（可选）
$env:RABBITMQ_HOST="127.0.0.1"
mvn -o -B -pl leaf-base-rabbitmq test -Dgroups=integration
```

基线：125 个用例（120 执行 + 5 集成跳过），连续 3 次结果一致（2026-10-25）。

---
本文档与实现保持一致；行为变更必须同步更新本文件、`AGENTS.md` 与 `specs/016-rabbitmq-module-audit/contracts/`。
