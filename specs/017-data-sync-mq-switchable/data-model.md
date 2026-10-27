# Phase 1 Data Model: leaf-base-data-sync 审查修复与消息中间件可切换

**Feature**: `specs/017-data-sync-mq-switchable` | **Date**: 2026-10-27 | **Plan**: [plan.md](./plan.md)

本模块无数据库存储，数据模型指**运行期领域对象、配置键空间与消息状态机**。所有类型遵守宪法 §VII（`GX` 前缀、后缀表）与 §V（JSpecify 空值语义）。

---

## 1. 枚举

### 1.1 `GXCanalOutcomeEnums`（`cn.maple.canal.enums`）

消费结局，决定 ack / 重投 / 死信走向（FR-004）。

| 枚举值 | 语义 | RabbitMQ 动作 | RocketMQ 动作 |
| --- | --- | --- | --- |
| `SUCCESS` | 处理器正常返回非空结果 | ack | `CONSUME_SUCCESS` |
| `SKIPPED` | DDL 事件、`consumer-enabled=false` 等无需处理 | ack + 计数 | `CONSUME_SUCCESS` + 计数 |
| `RETRYABLE_FAILURE` | 处理器抛出异常（含基础设施瞬时失败） | 未达上限 → 自增 `x-canal-retry-count` 重投后 ack；达上限 → `AmqpRejectAndDontRequeueException` → DLX → DLQ | 抛异常 → `RECONSUME_LATER`，超过注解固定值（3 次）后进入 `%DLQ%<group>` |
| `NON_RETRYABLE_FAILURE` | 空载荷、非 JSON、缺库表、未知操作类型、载荷结构不符、处理器类型不匹配、处理器返回 `null` | `AmqpRejectAndDontRequeueException` → DLX → DLQ | 转发 `canal.rocketmq.dead-letter-topic`（未配置则仅记录+计数）→ `CONSUME_SUCCESS` |

约束：不存在第 5 种结局；`SUCCESS` 与 `SKIPPED` 都不写死信。

### 1.2 `GXCanalMqTypeEnums`（`cn.maple.canal.enums`）

| 枚举值 | 语义 |
| --- | --- |
| `RABBITMQ` | 装配 AMQP 交换/队列/绑定/DLQ + `@RabbitListener` 消费 |
| `ROCKETMQ` | 装配 RocketMQ 消费容器；不声明任何 AMQP 基础设施 |

绑定自 `canal.mq-type`；非法取值在启动期绑定失败（异常信息包含合法取值）。

---

## 2. 载荷与解析结果

### 2.1 `GXCanalDataDto`（既有，`cn.maple.canal.dto`，字段语义不变）

| 字段 | 类型 | 可空 | 说明 / 规则 |
| --- | --- | --- | --- |
| `database` | `String` | 是 | 空 → `NON_RETRYABLE_FAILURE`（缺库表） |
| `table` | `String` | 是 | 空 → `NON_RETRYABLE_FAILURE`；含 `.`/`-`/大写 → 命中解析时按"命名不合法"给出告警级信号（FR-011），仍允许尝试匹配 |
| `type` | `String` | 是 | `INSERT`/`UPDATE`/`DELETE` 分发；DDL 类型 → `SKIPPED`；其它 → `NON_RETRYABLE_FAILURE` |
| `isDdl` | `Boolean` | 是 | `true` → `SKIPPED`（优先于 `type`） |
| `data` | `List<Dict>` | 是 | 新行值；与处理器共享引用，语义写入 Javadoc（FR-016） |
| `old` | `List<Dict>` | 是 | UPDATE 前镜像 |
| `pkNames` | `List<String>` | 是 | 主键列名 |
| `mysqlType` / `sqlType` | `Dict` | 是 | 列类型映射 |
| `sql` | `String` | 是 | DDL 原始 SQL |
| `es` / `ts` | `Long` | 是 | 事件/事务时间（毫秒） |
| `id` | `Long` | 是 | 非 Canal 标准字段，保持原样（不得删除，避免破坏既有反序列化结果） |

### 2.2 `GXCanalParseResultInnerDto`（新增，`cn.maple.canal.dto.inner`，`record`）

| 组件 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| `outcome` | `GXCanalOutcomeEnums` | 否 | 该元素/单条消息的结局 |
| `reason` | `String` | 是 | 可读原因（用于日志与计数维度，ASCII） |
| `event` | `GXCanalDataDto` | 是 | 解析成功时的规范化事件；失败/跳过时为 `null` |
| `operation` | `String` | 是 | `insert`/`update`/`delete`（小写，作为处理器 `param` 的 `operator` 值） |
| `handlerResult` | `Dict` | 是 | 处理器返回值，供历史入口 `parseMessage` 保持原契约 |
| `batchIndex` | `int` | 否 | 批内序号（单对象固定 0），用于日志定位 |

不变式：`outcome == SUCCESS` ⟺ `event != null && operation != null`；`retryable()` 为派生访问器（`outcome == RETRYABLE_FAILURE`），不作为独立分量以避免不一致。

---

## 3. 处理器路由

### 3.1 `GXCanalHandlerResolver`（新增，`cn.maple.canal.service.support`）

| 成员 | 类型 | 说明 |
| --- | --- | --- |
| `snapshot` | `Map<String, GXProcessCanalDataService>`（不可变） | 启动期由 `getBeansOfType` 构建；键为 Bean 名（含 `defaultProcessCanalDataService` 单独持有） |
| `resolvedCache` | `ConcurrentHashMap<String, GXProcessCanalDataService>` | 键 `database + "." + table`；值可为 `NO_HANDLER` 哨兵（负缓存） |
| `cacheSizeLimit` | `int` | 来自 `canal.handler-cache-size`，默认 4096；超限整体清空（有界，非 LRU） |
| `resolve(database, table)` | 方法 | 命中缓存 → 返回；否则 `toCamelCase(db_table_Service)` → 查 `snapshot` → 未命中用默认处理器 → 写缓存 |

规则：
- Bean 名冲突检测：若 `db=a_b/table=c` 与 `db=a/table=b_c` 在同一 JVM 内都出现过，第二次解析时记录 WARN（含两个库表组合）并计数——不改变当时的兜底行为（FR-011）。
- 命名不合法（含 `.`/`-`/空段）时记录 WARN（首见）并计数，随后仍按兜底执行。
- 运行期 Bean 集合变化不在支持范围（Nacos 刷新不新增 Bean）；若需要，由 `ContextRefreshedEvent` 触发的重建属未实现能力，登记于不支持范围。

### 3.2 默认兜底

`defaultProcessCanalDataService` 缺失时：`NON_RETRYABLE_FAILURE`（现状为 `status=error` 后 ack，改为可检索 + 落死信）。

---

## 4. 配置键空间（`canal.*`）

图例：**生效期** = `启动`（装配期读取，运行期刷新不生效，需重启） / `热`（每条消息读取当前值）。

### 4.1 既有键（不得改名、不得改默认值）

| 键 | 类型 | 默认值 | 生效期 | 备注 |
| --- | --- | --- | --- | --- |
| `canal.concurrency-count` | `String` | `10` | 启动 | 传给 `@RabbitListener(concurrency=...)`；保持 `String` 类型以兼容既有取值形式 |
| `canal.canal-queue-name` | `String` | `canalQueue` | 启动 | 主队列名 |
| `canal.exchange-name` | `String` | `exchange.fanout.canal` | 启动 | fanout 交换机 |
| `canal.routing-key` | `String` | `canal.example.exchange.routingkey` | 启动 | fanout 下仅写入 Binding 元数据 |

### 4.2 新增键

| 键 | 类型 | 默认值 | 生效期 | 校验 |
| --- | --- | --- | --- | --- |
| `canal.mq-type` | `GXCanalMqTypeEnums` | `RABBITMQ` | 启动 | 非法值启动失败并列出 `rabbitmq`/`rocketmq` |
| `canal.consumer-enabled` | `boolean` | `true` | 启动 | `false` → 不声明任何 MQ 基础设施、不建连（FR-035） |
| `canal.config-source` | `String` | 空（等价旧行为） | 启动 | 仅接受 `local`/`nacos`；空时按类路径探测，日志打印实际来源 |
| `canal.retry.max-attempts` | `int` | `3` | 启动 | `0..10`；`0` 表示不重投直接死信 |
| `canal.dead-letter.exchange` | `String` | `canal.dlx` | 启动 | 非空 |
| `canal.dead-letter.queue` | `String` | `canal.dlq` | 启动 | 非空 |
| `canal.dead-letter.routing-key` | `String` | `canal.dead` | 启动 | 非空 |
| `canal.handler-cache-size` | `int` | `4096` | 热 | `>0` |
| `canal.handler-miss-log-level` | `String` | `warn-once` | 热 | `off`/`debug`/`warn-once`/`warn` |
| `canal.rocketmq.topic` | `String` | 空 | 启动 | `mq-type=rocketmq` 时必填；`^[%a-zA-Z0-9_-]{1,127}$` |
| `canal.rocketmq.consumer-group` | `String` | 空 | `mq-type=rocketmq` 时必填 |
| `canal.rocketmq.tag` | `String` | `*` | 启动 | 选择器表达式 |
| `canal.rocketmq.dead-letter-topic` | `String` | 空（禁用显式转发） | 启动 | 空时不可重试失败仅记录 + 计数 |

> 实现说明：RocketMQ 的 `consumeThreadMax`（20）与 `maxReconsumeTimes`（3）都是 `@RocketMQMessageListener` 的注解属性，**不接受占位符或配置覆盖**，因此不再提供对应配置键（曾规划的 `canal.rocketmq.consume-thread-max` / `max-reconsume-times` 已移除），实际生效值由启动日志打印。`canal.retry.max-attempts` 只影响 RabbitMQ 侧重投次数（见 `AGENTS.md` §9.4、§10）。

### 4.3 复用而不新增的键

| 键 | 归属 | 用途 |
| --- | --- | --- |
| `spring.rabbitmq.listener.simple.prefetch` | Spring Boot | 在途上限的第二因子；启动日志打印 `concurrency × prefetch` 实际值（FR-022） |
| `rocketmq.name-server` / `rocketmq.producer.group` | `leaf-base-rocketmq` | RocketMQ 连接与发送；本模块不重复定义 |
| `nacos.config.group` | Spring Cloud Alibaba | Nacos 配置源分组 |

**配置元数据**：以上 `canal.*` 全部键登记 `leaf-base-data-sync/src/main/resources/META-INF/additional-spring-configuration-metadata.json`（含 `hints` 的枚举取值），并同步 `AGENTS.md` 与 `docs/leaf-base-data-sync开发手册.md` 的键表（FR-034）。

---

## 5. 计数与指标

### 5.1 `GXCanalConsumeStatistics`（新增，`cn.maple.canal.metrics`）

| 计数器 | 语义 | 指标名（有 `MeterRegistry` 时） |
| --- | --- | --- |
| `success` | 成功处理的消息条数（数组载荷按元素计） | `canal.consumer.success` |
| `skipped` | 跳过条数（DDL / 关闭开关） | `canal.consumer.skipped` |
| `retryableFailure` | 可重试失败条数 | `canal.consumer.retryable.failure` |
| `nonRetryableFailure` | 不可重试失败条数 | `canal.consumer.non.retryable.failure` |
| `handlerMiss` | 走默认兜底的条数 | `canal.consumer.handler.miss` |
| `deadLetter` | 进入死信/死信 topic 的条数 | `canal.consumer.dead.letter` |
| `batchSize` 分布（可选） | 单条消息内事件数（仅记录最大值/总条数） | `canal.consumer.batch.events` |

实现约束：`LongAdder`；`ObjectProvider<MeterRegistry>` 可用时注册 `Gauge`，注册失败仅 WARN；不引入 actuator 依赖。

计数职责划分：结局计数（`success` / `skipped` / `retryableFailure` / `nonRetryableFailure`）由**监听器**按元素累加，`deadLetter` 由 **`GXCanalFailureHandler`** 在真正投递到死信时累加；两者不重复计数同一结局。

---

## 6. 消息状态机

```text
[收到载荷]
   │
   ├─ consumer-enabled=false ──────────────────────────► SKIPPED（不建连场景下不会到达此处）
   │
   ├─ 空/空白 / 非 JSON / JSON 结构不符 ──────────────► NON_RETRYABLE_FAILURE
   │
   ├─ 数组载荷 ──► 逐元素重复下列判定（batchIndex 递增）
   │
   ├─ isDdl=true 或 DDL type ─────────────────────────► SKIPPED
   │
   ├─ database/table 空 ──────────────────────────────► NON_RETRYABLE_FAILURE
   │
   ├─ type 非 INSERT/UPDATE/DELETE ───────────────────► NON_RETRYABLE_FAILURE
   │
   ├─ 处理器未命中 ──► 默认兜底 ──► 无默认处理器 ─────► NON_RETRYABLE_FAILURE
   │                        └────► 有默认处理器 ─────► 继续
   │
   ├─ 处理器抛异常 ───────────────────────────────────► RETRYABLE_FAILURE
   │        │   （单个对象载荷：异常按历史契约向上抛出，由监听器捕获后转为此结局）
   │        │   （数组载荷：该元素被捕获为此结局，其余元素继续处理）
   │        └─ 重投次数 < max-attempts → 重投（计数 retryableFailure）
   │        └─ 重投次数 ≥ max-attempts → 死信（计数 deadLetter）
   │
   └─ 处理器返回非 null ──────────────────────────────► SUCCESS
            └─ 返回 null → NON_RETRYABLE_FAILURE（计数 + 可检索日志）
```

不变式：
1. 每个进入 `parse` 的元素恰好产生一个结局，且结局必须先于 ack 决定。
2. 任何结局都至少更新一个计数器；`NON_RETRYABLE_FAILURE` 与达上限的 `RETRYABLE_FAILURE` 必须可被运维发现（日志 + 计数 + 死信）。
3. 重投不改变消息载荷，只追加 `x-canal-retry-count`（RabbitMQ）或依赖 broker 重试计数（RocketMQ）。
4. 数组载荷的元素彼此隔离：某个元素失败不会跳过同批其余元素；`retryableFailure`/`nonRetryableFailure` 按**元素**累加，日志按元素逐条（经节流器）输出，而传输动作（重投/死信）对整条载荷只执行一次。
5. 同一条载荷内可重试与不可重试失败并存时，传输动作优先按可重试失败处理（交给重投机制），不可重试元素在重投耗尽后随整批进入死信。

---

## 7. 验证规则汇总（映射到 FR）

| 规则 | 触发条件 | 违反后果 | FR |
| --- | --- | --- | --- |
| MQ 类型合法 | 启动绑定 | 启动失败（列出合法值） | FR-024 |
| RocketMQ 必填项 | `mq-type=rocketmq` 且 topic/consumer-group 为空 | 启动失败（指出缺失键） | FR-028 |
| 依赖存在 | `mq-type=rocketmq` 且 `RocketMQListener` 不在类路径 | 启动失败（提示引入 `leaf-base-rocketmq`） | FR-030 |
| 重投上限范围 | 启动绑定 | 启动失败（`0..10`） | FR-006 |
| 库表非空 | 每条事件 | `NON_RETRYABLE_FAILURE` | FR-005 |
| 处理器命名合法 | 首次解析 | 告警 + 计数 + 兜底 | FR-011 |
| 数组元素独立 | 批量载荷 | 元素级结局互不影响 | FR-009 |
| 关闭开关 | 启动 | 无 MQ Bean / 无连接 | FR-035 |
