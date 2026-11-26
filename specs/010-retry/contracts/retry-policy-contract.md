# 契约：重试策略与缓存

**Feature**: `010-retry-module-audit` | **依据**: FR-005、FR-018、FR-019、FR-020；research D-02/D-05/D-06/D-09/D-10

## 1. 默认策略（不得变更，Q1）

| 项 | `GXRetryUtil` 默认 | `RetryTemplate` Bean 默认 | 依据 |
| --- | --- | --- | --- |
| 最大尝试次数 | 3 | 3 | `GXRetryConfig.DEFAULT_MAX_ATTEMPTS`（单点，D-02） |
| 初始间隔 | 1000ms | 1000ms | 同上 |
| 倍率 | 2.0 | 2.0 | 同上 |
| 最大间隔 | 10000ms | 10000ms | 同上 |
| 总超时 | 无（0 = 不设置） | 无 | Q3 |
| 抖动 | 无（0） | 无 | Q3 |
| 可重试异常 | `Exception`（全部） | `GXBusinessException`、`IOException`、`TimeoutException`、`SocketTimeoutException` | 现状保持 |

**约束**：`GXRetryUtil` 的默认常量**必须**引用 `GXRetryConfig.DEFAULT_*`，不得再保留独立字面量；新增用例断言两者一致。

## 2. 匹配规则（上游行为，审查已核对）

- `includes` 为空 → 匹配所有异常；`includes` 非空 → 异常或其任一 `cause` 命中即匹配。
- `excludes` 命中（含 `cause` 链）→ 一律不重试，**exclude 优先于 include**。
- `Error` 不在默认集合内 → 不重试、不包装。
- `retryExceptions` 中值为 `false` 的条目进入 `excludes`；为 `true` 的进入 `includes`。

## 3. 参数校验（含 F-17 修复）

| 参数 | 允许范围 | 现状问题 | 修复要求 |
| --- | --- | --- | --- |
| `maxAttempts` | 1 ~ 100 | — | 不变 |
| `initialInterval` | 100 ~ 300000 ms | — | 不变 |
| `multiplier` | 1.0 ~ 10.0 | `NaN` 两个比较均为 false，能通过本模块校验并在上游抛异常 | **必须**显式 `Double.isFinite(multiplier)` 检查，命中时报本模块的 `IllegalArgumentException` |
| `maxInterval` | 1000 ~ 3600000 ms | — | 不变 |
| `initialInterval <= maxInterval` | 必须成立 | — | 不变 |
| `timeout`（新增） | `>= 0`，0 = 不设置 | — | 新增校验 |
| `jitter`（新增） | `>= 0`，0 = 不设置 | — | 新增校验 |

## 4. 退避与最坏阻塞（可复算）

- 第 k 次重试的等待 = `min(initialInterval × multiplier^(k-1), maxInterval)`，`k = 1..maxAttempts-1`；抖动启用时在该值附近随机偏移并夹到 `[initialInterval, maxInterval]`。
- 默认参数（3 次尝试）总等待 = 1000 + 2000 = **3000ms**。
- 上限组合（100 次尝试、初始 300000ms、倍率 10、上限 3600000ms）：300000ms + 98 × 3600000ms ≈ **353100s ≈ 98 小时**。此值必须在手册中逐项列出，作为"长重试必须显式设置超时"的依据。
- 修复后**不收紧**上限（Q3），仅新增可选超时；超时语义 = "从首次尝试开始的总耗时上限"，超时后抛最后一次业务异常。

## 5. 模板缓存契约（FR-019）

| 项 | 规则 |
| --- | --- |
| 键组成 | `(maxAttempts, initialInterval, multiplier, maxInterval, timeout, jitter, 不可变 retryExceptions 快照)` |
| 键排除 | 实例身份、`GXRetryConfig` 类名（D-10） |
| 容量上限 | 256 |
| 淘汰触发 | `size() > 256` |
| 淘汰策略 | 专用锁内二次检查后裁剪到 192（`3/4`），单次 `O(size - 192)` |
| 主路径 | `computeIfAbsent` 无锁；锁仅在超阈值时进入 |
| 并发断言 | 32 线程 × 8 组参数并发后 `size() <= 256` 且每个键对应策略与入参一致 |
| 未来约束 | 若引入策略属性，键必须纳入策略快照（保留注释与用例位） |

## 6. 恢复回调触发条件（不变，需文档化）

- 触发：任何导致重试链终止的失败（耗尽、不可重试、中断、超时）都执行恢复回调。
- 不触发：`token` 取消（新增分支）、参数校验失败（未进入执行）。
- 恢复回调入参 `retryCount` 语义见 `retry-api-contract.md` §3。
