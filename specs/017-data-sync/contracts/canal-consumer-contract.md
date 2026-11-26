# Contract: leaf-base-data-sync 对外契约（v4.3.0 / feature 017）

**Type**: Java 库的公共契约（公共类型与方法、配置键、扩展点、装配行为、wire format）
**Consumers**: 下游 Spring Boot 应用（实现表级处理器）、Canal sink（消息生产方）、运维（配置文件与日志）
**Stability**: 标注 `[不变]` 的条目属于宪法 §II 受保护契约，任何破坏性变更需版本升级 + 迁移说明

---

## C1. 公共类型契约

| 类型 | 契约 | 变更 |
| --- | --- | --- |
| `cn.maple.canal.service.GXProcessCanalDataService` | `Dict processUpdate/processInsert/processDelete(GXCanalDataDto, Dict)`；返回 `null` 视为失败，抛异常视为可重试失败 | `[不变]` 签名与语义不变 |
| `cn.maple.canal.service.GXCanalMessageParseService` | `Dict parseMessage(String)` `[不变]`；新增 `GXCanalParseResultInnerDto parse(String)`（结构化结局） | 仅新增方法 |
| `cn.maple.canal.dto.GXCanalDataDto` | 既有字段名、类型、反序列化行为 `[不变]`；新增字段必须可空且不改变旧载荷结果 | 不变 |
| `cn.maple.canal.listener.GXCanalRabbitMQListener#listener(String)` | 方法签名 `[不变]`；类级 `@Lazy` 移除（生产行为变更，见 CHANGELOG 说明） | 行为变更（显式声明） |
| `cn.maple.canal.config.GXCanalConfig` | 声明的 Bean 名称 `fanoutExchange` / `canalQueue` / `binding` `[不变]`；新增 DLX/DLQ Bean | 新增 Bean + 条件化 |
| `cn.maple.canal.properties.GXCanalProperties` | 配置前缀 `canal` 与既有字段默认值 `[不变]` | 新增字段 |

### C1.1 扩展点契约（表级处理器）

- 定位规则 `[不变]`：`toCamelCase("<database>_<table>_Service")` 作为 Spring Bean 名。
- 兜底规则 `[不变]`：未命中时使用 Bean 名 `defaultProcessCanalDataService`。
- 处理器实现不得依赖框架内部类型（仅 `GXCanalDataDto` + `Dict`）。
- 处理器抛出的异常 `[不变]`：不被吞掉，作为可重试失败传播（消费者据此重投/死信）。**载荷形态差异**：单个 JSON 对象载荷按历史契约向上抛出（`parseMessage` 语义不变）；JSON 数组载荷按元素捕获，失败元素以 `RETRYABLE_FAILURE` 返回，其余元素继续处理。
- 处理器返回 `null`：语义由"warning 后 ack"收紧为不可重试失败（落死信/失败记录），属**修复**，写入 CHANGELOG。

---

## C2. 配置键契约

- 前缀：`canal`（RocketMQ/AMQP 连接参数分别沿用 `rocketmq.*` / `spring.rabbitmq.*`）。
- 既有键 `[不变]`：`canal.concurrency-count`、`canal.canal-queue-name`、`canal.exchange-name`、`canal.routing-key`（默认值见 `data-model.md` §4.1）。
- 新增键：见 `data-model.md` §4.2（全部登记 `additional-spring-configuration-metadata.json`）。
- 来源切换：`canal.config-source=local|nacos`；缺省时保持既有探测行为，启动 INFO 打印实际来源。
- 禁止事项：不得删除既有键、不得改变其含义、不得引入"必须配置否则启动失败"的必填键（`mq-type=rocketmq` 的 topic/group 除外，属该模式下的必需项）。

---

## C3. 装配契约

| 条件 | 期望装配 | 断言方式 |
| --- | --- | --- |
| `canal.consumer-enabled=false` | 无 AMQP 交换/队列/绑定、无 RocketMQ 容器、无连接尝试 | `ApplicationContextRunner` 断言 Bean 缺失 + 无 `ConnectionFactory` 使用日志 |
| `canal.mq-type=rabbitmq`（缺省） | `GXCanalConfig` 的 6 个 Bean（交换机、队列、绑定 + 死信交换机/队列/绑定）+ `GXCanalRabbitMQListener` 端点已注册 | `RabbitListenerEndpointRegistry#getListenerContainers` 非空且目标容器 `isRunning()` |
| `canal.mq-type=rocketmq` + 依赖在场 | 无任何 AMQP 基础设施 Bean；存在 RocketMQ 消费 Bean | 断言 Bean 集合 |
| `canal.mq-type=rocketmq` + 依赖缺席 | 启动失败，异常信息包含 `leaf-base-rocketmq` | 断言 `BeanCreationException`/`NoSuchBeanDefinitionException` 消息 |
| 下游已声明同名同类 Bean | 本模块让位（`@ConditionalOnMissingBean`），不产生歧义 | 预置 Bean 后启动成功 |
| 仅用 RabbitMQ 的下游 | 依赖树无 `rocketmq-client` / `nacos-client` / `fastjson` 净增 | `mvn dependency:tree`（见 quickstart V6） |

---

## C4. 消息契约（wire format）

- 载荷为 UTF-8 JSON 文本，内容为 Canal `flatMessage`（单个对象或对象数组均接受）。
- 反序列化目标 `GXCanalDataDto`；未知字段忽略（不改变既有行为）。
- RabbitMQ：消息头可携带 `traceId`、`x-canal-retry-count`（由本模块写入）；`contentType` 为 `application/json` 或 `text/plain` 时按字符串接收。
- RocketMQ：`topic` = `canal.rocketmq.topic`；`tag` = `canal.rocketmq.tag`；`userProperties.traceId` 可选。
- 生产者侧约束：不改动 Canal sink 配置；同一份载荷在两种 MQ 下必须产出相同处理结果。

---

## C5. 失败处置契约（两套 MQ 的等价表）

| 场景 | 结局 | RabbitMQ | RocketMQ | 运维可见性 |
| --- | --- | --- | --- | --- |
| 处理器成功 | SUCCESS | ack | `CONSUME_SUCCESS` | `canal.consumer.success` |
| DDL / 关闭消费 | SKIPPED | ack | `CONSUME_SUCCESS` | `canal.consumer.skipped` |
| 处理器抛异常，未达上限 | RETRYABLE_FAILURE | 自增重投头后重投 + ack 原消息 | `RECONSUME_LATER` | 计数 + WARN 日志（含 `x-canal-retry-count`） |
| 处理器抛异常，达上限 | RETRYABLE_FAILURE → DLQ | `AmqpRejectAndDontRequeueException` → `canal.dlx` → `canal.dlq` | 超过 `maxReconsumeTimes` → `%DLQ%<group>` | 计数 + ERROR 日志 |
| 不可解析 / 缺库表 / 未知类型 / 处理器缺失且无兜底 / 返回 null | NON_RETRYABLE_FAILURE | reject → DLQ | 转发 `canal.rocketmq.dead-letter-topic`（未配置则仅计数） | 计数 + ERROR 日志 |
| 处理器未命中走兜底 | SUCCESS | ack | `CONSUME_SUCCESS` | `canal.consumer.handler.miss` + 首见 WARN |

不变式：
1. 任何一条消息都不会在没有记录的情况下消失（日志或计数至少其一，且落到死信时可在 broker 侧查看）。
2. 重投次数有上限（`canal.retry.max-attempts`，默认 3）。
3. 同一载荷在两种 MQ 下的"结局序列"一致（允许传输层动作不同）。
4. 批量载荷按元素逐个判定结局：单个元素失败（含处理器抛异常）不中止同批其他元素；每个失败元素单独计数并留日志，整批只执行 **一次** 传输动作，可重试与不可重试并存时优先按可重试失败处理。

---

## C6. 可观测性契约

- 日志：ASCII；消费入口、结局、失败原因、`traceId`、`database`、`table`、`type`、`batchIndex` 字段可检索。
- 指标：见 `data-model.md` §5.1；无 `MeterRegistry` 时降级为计数器 + 汇总日志。
- 启动日志必须包含：实际 MQ 类型、配置来源、`concurrency × prefetch` 计算出的在途上限、死信目的地名称、RocketMQ topic/group（如启用）。

---

## C7. 不支持范围（同步写入 `AGENTS.md` 与开发手册）

| 不支持能力 | 原因 | 替代方案 |
| --- | --- | --- |
| 顺序消费 / 分区有序 | 与既有 `concurrency=10` 无序语义对齐；顺序保证需业务侧配合 | 按表维度拆分消费组，同一表只用单一消费者 |
| 事务消息 | 数据同步链路无事务边界，Canal 自身保证 binlog 顺序 | 业务侧幂等 + 唯一键约束 |
| 批量确认（batch ack） | 逐条判定结局是"不丢消息"的前提 | RabbitMQ 调大 `spring.rabbitmq.listener.simple.prefetch`；RocketMQ 侧 `consumeThreadMax` 为注解固定值 |
| 跨 MQ 双跑 | 重复消费与幂等责任超出模块职责 | 部署级切换（改 `canal.mq-type` + 重启） |
| 跨库 routing 过滤（fanout → topic） | 会改变既有交换机类型与下游绑定语义 | 下游各自建队列消费全量，或用 RocketMQ tag 过滤 |
| 运行期刷新装配期键 | 队列/交换机/主题/并发在容器启动时固定 | 改配置后重启 |
| 运行期新增处理器 Bean | Bean 集合视为启动期静态 | 重启（或业务侧自建动态路由） |
