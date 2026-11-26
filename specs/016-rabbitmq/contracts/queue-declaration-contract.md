# 契约：队列与绑定声明

**Feature**: `016-rabbitmq-module-audit` | **Date**: 2026-10-25

## 核心规则

1. **每次声明都触达 Broker**：`createQueue` 不再有"永久缓存命中即返回"的快路径；同队列并发调用由**单飞**合并为 1 次 Broker 声明。
2. **缓存不是事实来源**：`queueExists` 一律 `rabbitAdmin.getQueueProperties`；返回 `null`/空即不存在。
3. **无全局锁**：删除 `queueOperationLock`；同队列通过单飞互斥，不同队列完全并行。
4. **删除顺序**：`deleteQueue` 先删 Broker，成功后才更新本地可观测状态（本版本无缓存，仅日志）。
5. **失败必须可定位**：`GXRabbitMQOperationException#operation` 标识步骤，`detail` 携带 `completedSteps` 与 `failedStep`。

## 单飞声明时序

```text
caller A ─┐
caller B ─┼─ computeIfAbsent(key) ──> IN_FLIGHT future ──> declareQueue(1 次 RPC)
caller C ─┘                                   │
                                    ok: 所有调用方得到同一 Queue
                                    fail: 所有调用方得到同一异常，条目移除
```

- `key = name#fingerprint`，指纹包含 `durable/exclusive/autoDelete/arguments`（键排序）。
- 条目在 `whenComplete` 中移除；异常完成同样移除，避免"错误缓存"。
- 不同的 `arguments` 产生不同 key，会各自声明；Broker 若判定冲突则抛 `PRECONDITION_FAILED` → 包装为 `GXRabbitMQOperationException`。

## 临时队列

| 项 | 旧 | 新 |
| --- | --- | --- |
| `durable` | `false` | `false` |
| `exclusive` | `true` | `false` |
| `autoDelete` | `true` | `true` |
| 可被其他连接消费 | 否（不可用） | 是 |
| 排他队列 | 声称支持 | **不支持**，登记在 `AGENTS.md` 能力边界 |

## 通道创建

`setupMessageChannel` 顺序固定：声明交换机 → 声明队列 → 建立绑定。

- 交换机声明幂等；已存在且类型不同 → Broker 关闭 channel → 包装为 `GXRabbitMQOperationException`（`operation=DECLARE_EXCHANGE`）。
- 队列声明失败 → 不再尝试绑定；异常 `detail.completedSteps=[DECLARE_EXCHANGE]`。
- 绑定失败 → 异常 `detail.completedSteps=[DECLARE_EXCHANGE, DECLARE_QUEUE]`。
- 不做自动回滚（删除已声明的交换机可能影响其他消费者）；由调用方按 `completedSteps` 决策，契约中明确"部分创建"语义。

## 参数校验（前置，不触达 Broker）

| 参数 | 规则 |
| --- | --- |
| `queueName` | 非空白；长度 ≤ 255 |
| `exchange.getName()` | 非空白；长度 ≤ 255 |
| `routingKey` | 非空白；长度 ≤ 255 |
| `arguments` | `null` 允许；声明前做不可变拷贝 |
| `name` 含控制字符 | 拒绝（`GXBusinessException`） |

## 验证要点

| 场景 | 断言 |
| --- | --- |
| 32 线程同队列并发声明 | Broker 声明次数 = 1；全部返回同一 `Queue` |
| 同一队列先 `durable=true` 后 `durable=false` | 第二次真实触达 Broker，冲突时抛异常 |
| Broker 侧删除后 `queueExists` | 返回 `false`（不再命中缓存） |
| `deleteQueue` 时 Broker 抛异常 | 抛 `GXRabbitMQOperationException`，不报"已删除" |
| 10 个不同队列并发 | 总耗时显著小于串行（无全局锁） |
| 交换机已存在但类型不同 | 异常 `operation=DECLARE_EXCHANGE`，`completedSteps` 为空 |
