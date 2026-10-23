# Phase 1 Data Model: leaf-base-sse 审查与改造

**Feature**: `011-sse-module-audit` | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

本文件描述两类模型：**A. 审查产物模型**（发现、证据、矩阵，用于报告与排期）与 **B. 运行时模型**（连接、发送管线、指标快照，用于实现）。

## A. 审查产物模型

### A-1 Finding（审查发现）

| 字段 | 类型 | 约束 | 来源 |
| --- | --- | --- | --- |
| `id` | `F-01` ~ `F-23` | 全局唯一、稳定不复用 | spec.md 发现清单 |
| `severity` | `HIGH` / `MEDIUM` / `LOW` | 高危必须附可构造故障现场 | FR-002 |
| `dimension` | `CORRECTNESS` / `THREAD_SAFETY` / `PERFORMANCE` / `CONTRACT` | 单值 | FR-002 |
| `location` | 文件 + 行区间 | 必须指向当前快照，行号漂移时以符号名为准 | FR-002 |
| `trigger` | 文本 | 可直接构造的操作序列 | FR-003 |
| `impact` | 文本 | 必须说明"线上表现" | FR-002 |
| `evidence` | `E-xx[]` | 至少 1 条，引用 spec 中 E 编号 | FR-003 |
| `repro` | `EXISTING_TEST` / `NEW_TEST` / `MANUAL_STEPS` / `UNVERIFIED` | `UNVERIFIED` 不得进入已确认项 | FR-003 |
| `regression` | 用例设计 | 输入 + 断言点 + 期望差异 | FR-012 |
| `fixBatch` | `B1` ~ `B6` | 见 plan.md Phase 2 预告 | — |

**校验规则**

1. 每条 Finding 的 `evidence` 集合非空，且每条 Evidence 的 `location` 与被引用位置一致；
2. `severity = HIGH` 时 `repro` 不得为 `UNVERIFIED`；
3. 同一 `regression` 用例设计不得服务两个互斥的 `fixBatch`；
4. 报告中不得出现"仅凭阅读"的结论混入 P 序列（既定契约）。

### A-2 Evidence（证据）

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `id` | `E-01` ~ `E-24` | 唯一 |
| `kind` | `LOCAL_CODE` / `UPSTREAM_SOURCE` / `MEASURED` / `DOC` | `UPSTREAM_SOURCE` 必须给出版本与行号 |
| `location` | 文件 + 行区间 | 上游文件必须标注 jar 版本（7.0.9） |
| `quote` | 关键代码/文档片段 | ≤ 2 行 |
| `supports` | `F-xx[]` | 反向索引，供报告交叉校验 |

### A-3 Matrix（矩阵，报告章节产物）

| 矩阵 | 维度 | 取值数 | 用途 |
| --- | --- | --- | --- |
| 连接终止矩阵 | 完成 / 超时 / 错误 / 主动关闭 / 重试耗尽 | 5 | 定性"缓存变化 × 是否 complete × 客户端行为"（FR-005） |
| 并发风险矩阵 | 连接缓存 / 调度器 / 写锁 / 回调与队列 | 4 | 说明"共享范围 × 现状保护 × 收敛路径"（FR-006） |
| 消息顺序契约 | 首投成功 / 首投失败+重试 / 分片 / 并发广播 | 4 | 唯一定性"是否保序"（FR-008、SC-004） |
| 性能口径表 | UUID 次数 / 分配清单 / 串行写次数 / 重连周期 / 分片事件数 | 5 | 可复算口径（FR-007、SC-005） |

## B. 运行时模型

### B-1 Connection（连接项）

现状以 `Map<String, SseEmitter>` 承载，`clientId` 为键、`SseEmitter` 为值；本次不引入新包装类型（保持 `getSseEmitterByClientId` 返回 `SseEmitter` 的既有契约）。

**状态机**

```text
[不存在]
   │ createSseConnect(clientId, timeout)
   ▼
[已注册] ──send 成功──▶ [已注册]
   │
   ├── onCompletion（容器）───────────▶ [已移除]（仅条件删除）
   ├── onTimeout（容器，绝对超时）────▶ [已移除]（仅条件删除）
   ├── onError（容器）────────────────▶ [已移除]（仅条件删除）
   ├── closeConnect ──────────────────▶ [已关闭]（发 close 事件 + complete + 条件删除）
   ├── 心跳失败 / sendHeartbeat 失败 ─▶ [已关闭]（条件删除 + complete，D-01）
   ├── 重试耗尽（IOException × 3）────▶ [已关闭]（条件删除 + complete，D-01）
   ├── send 抛 IllegalStateException ─▶ [已移除]（丢弃消息，不计失败重试）
   └── createSseConnect 同 clientId ──▶ [已关闭]（旧值 complete）+ [已注册]（新值）
```

**不变式**

1. `getSseEmitterByClientId(k)` 返回的实例一旦与缓存中的值不等，即视为"已被替换"，任何清理路径只能条件删除（P-01）；
2. 任何从缓存移除的路径，若由本模块主动触发（非容器回调），必须保证 `complete()` 被调用一次（幂等）；
3. `clientId` 与 `SseEmitter` 的对应关系在任一时刻至多一条；
4. 销毁后不再接受新连接。

### B-2 GXSsePendingMessage（待投递消息，包私有）

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `clientId` | `String` | 非空，与管线键一致 |
| `msgId` | `String` | 同一原始消息的所有分片共享；由外层生成一次 |
| `comment` | `String` | 非空，格式 `<base>:<clientId>` 或 `<base>:<clientId>:chunk=<i>/<n>` |
| `eventName` | `String` | 默认 `Push Msg`（默认值不变） |
| `reconnectTimeMillis` | `long` | 默认 2000（默认值不变） |
| `data` | `Dict` | 非空（空负载在入队前被拦截并计 `drops`） |
| `attempt` | `int` | 初始 0，每次重试 +1；上限 3 |
| `enqueuedAt` | `long` | 纳秒，用于超龄丢弃（可选，仅日志） |

**校验规则**

1. 入队前 `clientId`/`data` 非空，否则 `drops++` 并丢弃；
2. 队列长度 > 256 时**丢弃队首最旧的 1 条，并在尾部追加新消息**（保留新消息，避免恢复后推送陈旧数据），被丢弃条数累加进 `drops`；
3. `attempt > 3` 时强制丢弃并触发连接关闭（D-01）。

### B-3 GXSseClientSendPipeline（单连接发送管线，包私有）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `clientId` | `String` | 键 |
| `queue` | `ArrayDeque<GXSsePendingMessage>` | 仅在 `synchronized(this)` 下读写 |
| `draining` | `AtomicBoolean` | 保证同一连接同时只有一个 drain 任务 |
| `closed` | `volatile boolean` | 管线失效标记（连接关闭/销毁） |

**行为契约**

- `enqueue(msg)`：入队后若 `draining` 从 `false` 翻转为 `true`，返回 `true` 表示调用方需提交 drain；
- `poll()`：返回队首或 `null`（空队列）；
- `close()`：置 `closed=true` 并清空队列（剩余消息计 `drops`）；
- 串行性由 `draining` + "同一时刻仅一个 drain 任务" 保证，**不加全局锁**；
- 管线与连接同生命周期：`closeAndRemove` 时一并 `remove(clientId)`（先 `close()` 再移除）。

### B-4 GXSseMetrics（指标快照，对外）

| 字段 | 类型 | 语义 | 单调性 |
| --- | --- | --- | --- |
| `attempts` | `long` | 发送尝试次数（含首投与重试） | 单调递增 |
| `successes` | `long` | 成功写入次数 | 单调递增 |
| `failures` | `long` | 最终失败并关闭连接的次数 | 单调递增 |
| `retries` | `long` | 实际执行的重试次数 | 单调递增 |
| `drops` | `long` | 被丢弃的消息数（连接失效/队列超限/负载为空/管线关闭） | 单调递增 |
| `connections` | `int` | 当前活跃连接数 | 可增可减 |

**不变式**：`attempts = successes + failures + 在途数`（在途数 ≥ 0，不对外暴露）；快照为调用瞬间的弱一致视图，字段间不做原子协调（与 `LongAdder#sum` 语义一致）。

### B-5 配置与常量（保持不变）

| 常量 | 值 | 变更 |
| --- | --- | --- |
| 最小超时 | 60s | 不变 |
| 最大重试次数 | 3 | 不变 |
| 退避基数 | 100ms（100/200/400） | 不变 |
| 默认事件名 | `Push Msg` | 不变 |
| 重连间隔 | 2000ms | 不变 |
| 拆分上限 | 100 字符 | 不变 |
| 心跳周期 | 60s | 不变（仅改为分片提交） |
| 队列容量 | 256 | 新增（仅失败路径生效） |
