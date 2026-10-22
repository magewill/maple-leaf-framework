# Phase 1 数据模型：审查产物与修复引入的类型

**Feature**: `006-rocketmq-module-audit` | **Date**: 2026-10-22

本特性没有数据库实体。这里的"数据模型"分两部分：**A. 审查产物模型**（报告与矩阵，供 task 生成与验收使用）；**B. 修复引入的运行时类型**（新增/变更的公共契约类型及其不变量）。

## A. 审查产物模型

### A-1 Finding（审查发现）

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| id | `F-xx` | 是 | 全报告唯一，稳定不复用 |
| dimension | `CORRECTNESS` / `THREAD_SAFETY` / `PERFORMANCE` | 是 | 一个发现只归一个主维度 |
| severity | `HIGH` / `MEDIUM` / `LOW` | 是 | 依据"影响面 × 触发概率 × 可观测性" |
| location | 文件 + 行区间 | 是 | 行号以当前分支代码为准 |
| trigger | string | 是 | 可构造的输入/环境组合 |
| impact | string | 是 | 必须写明"用户/业务可见的后果" |
| evidence | `E-xx` 引用 | 是 | 指向 spec 的证据索引 |
| reproduce | 命令或用例设计 | 是 | 无 Broker 时可跳过 |
| regression | 输入 + 断言点 + 期望差异 | 是 | 必须"旧代码失败、新代码通过" |
| fixRef | FR 编号 | 是 | 对应到 spec 的 FR |

**不变量**：不得存在 `evidence` 为空或 `regression` 为空的高危项（对应 SC-002、SC-008）。

### A-2 发送语义矩阵（每方法一行）

| 字段 | 取值域 |
| --- | --- |
| method | `sendNormalMessage` / `syncSend` / `sendDelayMessage` / `sendAsync` / `sendOneway` |
| returnValue | `void` / `boolean` / `String(msgId)` |
| failureExpression | 抛 `GXBusinessException` / 仅日志 |
| deliverySemantics | `AT_LEAST_ONCE` / `AT_MOST_ONCE` / `BEST_EFFORT` |
| blocker | 是否阻塞调用线程 |
| rttPerMessage | 网络往返次数（0 / 1） |
| brokerVerified | 真实 Broker 实测结论（通过 / 未覆盖 + 原因） |

### A-3 配置来源矩阵（每来源一行）

| 字段 | 取值域 |
| --- | --- |
| source | `local`（`config/<profile>/rocket-mq.yml`）/ `nacos` / `downstream-application.yml` |
| activationCondition | 键值或类路径条件 |
| actuallyLoaded | `YES` / `NO`（以启动上下文断言为准，不靠注解推断） |
| evidence | 上下文断言或日志片段 |

### A-4 并发风险矩阵（每对象一行）

| 字段 | 取值域 |
| --- | --- |
| target | 请求 DTO / CDC 事件 / 监听器集合 / 异步回调 |
| sharingScope | 单线程独占 / 跨监听器共享 / 跨回调线程共享 |
| currentGuard | 无 / 不可变快照 / 只读约定 |
| residualRisk | 无 / 具体后果 |

### A-5 性能口径

| 指标 | 口径定义 |
| --- | --- |
| rttPerMessage | 一次业务发送触达 Broker 的请求-响应次数（含客户端内建重试则注明倍数） |
| infoLogsPerSuccess | 成功路径上 `info` 级日志条数（目标 0） |
| cdcAllocPerEvent | 每事件创建的容器对象数（`Dict` / `List` / 差异对象） |
| cdcDiffPerEvent | 每事件内 `Maps.difference` 调用次数（目标 1） |
| sample | 顺序 ≥200、并发 ≥200（8×25 或 8×50），记录 avg/p50/p95/max |

## B. 修复引入的运行时类型

### B-1 `GXRocketMQAsyncSendFailureContext`（新增，不可变 record）

| 字段 | 类型 | 可空 | 说明 |
| --- | --- | --- | --- |
| topic | `String` | 否 | 发送时的 topic（快照值，非调用方后续修改值） |
| tag | `String` | 否 | 空字符串表示未设置 |
| messageKey | `String` | 否 | 空字符串表示未设置 |
| msgId | `String` | 是 | Broker 返回时才有 |
| sendStatus | `SendStatus` | 是 | 回调成功但状态非 `SEND_OK` 时存在；`onException` 时为 `null` |
| cause | `Throwable` | 是 | `onException` 时的原始异常 |

**不变量**：`sendStatus == null` 时 `cause != null`；`cause == null` 时 `sendStatus != null && sendStatus != SEND_OK`。

### B-2 `GXRocketMQAsyncSendFailureHandler`（新增，函数式接口）

- 方法：`void onFailure(GXRocketMQAsyncSendFailureContext context)`。
- 发现方式：`ObjectProvider`；0 个实现 → 仅 error 日志；>1 个实现 → 全部按 `@Order` 调用，单个实现抛异常不影响其他实现（逐个 try-catch）。

### B-3 `GXCdcDispatchException`（新增，`cn.maple.rocketmq.exception`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | `int` / 结果码枚举 | 复用 `GXResultStatusCode` 体系 |
| failedListenerCount | `int` | 本次分发失败的监听器数量 |
| bizType / op / tsMs | `String` | 事件定位信息，便于排障 |
| suppressed | `Throwable[]` | 每个监听器的原始异常 |

**不变量**：`failedListenerCount >= 1`；`getSuppressed().length == failedListenerCount`；消息体不含原始消息全文（只含长度与定位字段）。

### B-4 `GXCdcDispatchStatistics`（新增）

| 计数键 | 语义 |
| --- | --- |
| `success` | 事件解析成功且所有监听器执行完成 |
| `parseFailure` | 非 JSON / payload 为空 / 结构不可读 |
| `unknownOperation` | `op` 缺失或不在 `c/r/u/d` |
| `listenerFailure` | 至少一个监听器失败 |
| `deadLetter` | 转发死信成功（未配置时为 0） |

指标名（存在 `MeterRegistry` 时）：`rocketmq.cdc.dispatch.{success,parse.failure,unknown.operation,listener.failure,dead.letter}`。

## CDC 分发状态迁移

```text
[原始消息]
   │
   ├─ 开关关闭 ──────────────→ IGNORED（warn，计数 skipped）
   ├─ 空白/null ─────────────→ INVALID（warn，计数 parseFailure）
   ├─ 解析失败 ──────────────→ INVALID（warn，计数 parseFailure，可选死信）
   ├─ op 未知 ───────────────→ UNSUPPORTED（warn，计数 unknownOperation，可选死信）
   └─ 解析成功
        │
        ├─ 无匹配监听器 ─────→ NO_TARGET（debug，计数 success？否 → 单独 noTarget）
        ├─ 全部监听器成功 ───→ SUCCESS
        └─ 任一监听器失败 ───→ PARTIAL（全部执行完后抛 GXCdcDispatchException，计数 listenerFailure）
```

**约束**：`PARTIAL` 必须保证"所有 `supports` 且未 `shouldIgnore` 的监听器都被调用一次"；重复投递由消费方重试产生，业务幂等由业务模块负责（spec Assumptions）。
