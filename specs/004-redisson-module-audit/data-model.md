# Phase 1 Data Model: 审查产物结构

**Feature**: `004-redisson-module-audit` | **Date**: 2026-10-22

本文件定义审查交付物的结构、字段与判定规则。所有实体最终落在 `review-report.md` 与 `checklists/` 中，本文件只规定结构与约束。

## 实体一览

| 实体 | 用途 | 落点 |
| --- | --- | --- |
| Finding（审查发现） | 单条结论的最小单元 | `review-report.md` 主体 |
| Evidence（证据） | 支撑 Finding 的可核对事实 | `review-report.md` 附录 |
| 链路可靠性矩阵 | 五条消息链路的投递语义与失败归属 | `review-report.md` §可靠性 |
| 并发与生命周期矩阵 | 组件 × 并发度 × 停止路径 × 泄漏风险 | `review-report.md` §并发 |
| 性能口径 | 三条路径的往返次数与上限 | `review-report.md` §性能 |
| 覆盖率台账 | 51 个生产文件的审查与测试可达性 | `checklists/coverage-ledger.md` |

## 1. Finding

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `id` | string | 是 | `F-NN`，两位数字，全局唯一、连续，不复用已废弃编号 |
| `title` | string | 是 | ≤ 40 字，动宾结构，陈述事实而非结论（"订阅缓存无法自愈" ✓，"订阅有 bug" ✗） |
| `dimension` | enum | 是 | `逻辑正确性` / `线程安全` / `性能`，三选一（跨维度时以主因归入，另一维度在 `impact` 中说明） |
| `severity` | enum | 是 | `高` / `中` / `低`，判定规则见 §7 |
| `evidenceLevel` | enum | 是 | `S` / `D` / `T`，取该 Finding 的最高级证据（定义见 research R-01） |
| `location` | string | 是 | `文件相对路径:起止行`；跨文件时用 `;` 分隔，最多 3 处 |
| `trigger` | string | 是 | 可构造的触发条件（"removeListener 抛异常后再 subscribe"），禁止"某些情况下" |
| `impact` | string | 是 | 用户/业务可见后果（消息丢失、吞吐下降、线程不释放），禁止只写"不符合规范" |
| `evidenceRefs` | string[] | 是 | 至少 1 个 `E-NN` |
| `repro` | string | 是 | 命令或用例名 + 预期现象；高风险必须有可执行路径 |
| `regressionTestDesign` | string | 是 | 输入 + 断言点 + "修复后断言如何翻转" |
| `fixBatch` | enum | 是 | `P0` / `P1` / `P2` / `仅文档`（Research R-08） |
| `contractImpact` | string | 是 | 修复建议对公开 API/配置键/异常语义的影响；无影响写 `none` |
| `status` | enum | 是 | `draft` → `verified` → `graded` → `reproduced` → `delivered` |

**状态机**：

```text
draft ──(补齐 location/evidenceRefs)──> verified
verified ──(定 dimension/severity/fixBatch)──> graded
graded ──(repro 可执行或用例存在)──> reproduced
reproduced ──(进入 review-report.md 且与 spec 摘要一致)──> delivered
```

**校验规则**：

- 任一 Finding 处于 `delivered` 时，`repro` 与 `regressionTestDesign` 均非空；`高` 级别额外要求 `evidenceLevel ≠ S`（必须有依赖事实或可执行验证）。
- `regressionTestDesign` 必须包含"修复后断言如何翻转"，否则该 Finding 不得进入 `reproduced`。
- `location` 中的行区间在交付前必须复核一次（代码可能已变动）。

## 2. Evidence

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `id` | string | 是 | `E-NN`，与 `spec.md` 中的编号一致 |
| `level` | enum | 是 | `S` / `D` / `T` |
| `source` | string | 是 | `S`：`文件:行区间`；`D`：`类名#方法名` + 依赖坐标版本；`T`：用例全名 |
| `excerpt` | string | 是 | ≤ 2 行关键语句或语义摘要，不得整段复制 |
| `usedBy` | string[] | 是 | 引用该证据的 `F-NN` 列表 |

**不变量**：`E-NN` 编号在 `spec.md` 与 `review-report.md` 中必须一致；新增证据只在报告附录追加，不改动 `spec.md` 已有编号的语义。

## 3. 链路可靠性矩阵

| 链路 | 判定语义 | 失败时消息归属 | 守护用例 |
| --- | --- | --- | --- |
| 可靠主题订阅/发布（`GXRedissonMQUtils`） | 待报告判定（候选：尽力投递，因 `wrapListener` 吞异常） | 业务异常后消息被视为已消费 | `GXRedissonTopicSubscriptionAuditTest` |
| 延迟队列转发（`GXRedissonDelayMQPostProcessor`） | 候选：至多一次（移出 Redis 后仅存内存） | 进程崩溃 → 丢失；重投失败 → 残留 `inFlightMessages` | `GXRedissonDelayForwardingAuditTest` |
| stream 立即消费（`GXRedissonStreamImmediateMQ`） | 候选：至少一次（`ack` 后落 `processed`），存在重复窗口 | 未 `ack` → pending；无 handler → 丢弃 | `GXRedissonStreamReliabilityAuditTest` |
| stream 延迟转移（`GXRedissonStreamDelayedMQ`） | 候选：至少一次（transferSet 兜底重投） | 转移失败 → zset/hash 残留，下轮重试 | `GXRedissonStreamTransferAuditTest` |
| pending 恢复（`GXRedissonStreamPendingMessageRecovery`） | 待报告判定（受全局锁与 topic 归属限制） | 非持锁实例的 topic 长期不被恢复 | `GXRedissonStreamReliabilityAuditTest` |

**判定规则**：每条链路必须给出**唯一定性**（至少一次 / 至多一次 / 尽力投递），且必须写明"哪一行导致该定性"。禁止出现"基本可靠"等模糊表述（SC-005）。

## 4. 并发与生命周期矩阵

| 组件 | 创建时机 | 并发度来源 | 停止路径 | 泄漏风险 |
| --- | --- | --- | --- | --- |
| `fetchExecutor`（虚拟线程） | 构造器 | 每队列 1 个抓取任务 | `destroy` → `shutdownExecutor` | 低 |
| `workerExecutor`（虚拟线程） | 构造器 | `getConsumerCountForQueue`（受各队列注册顺序影响） | 同上 | 中：并发度不确定（F-07） |
| `retryScheduler`（2 线程 daemon） | 构造器 | `scheduleRetry` | 同上 | 中：任务持有 `PendingMessage` 引用 |
| `RDelayedQueue` 转移任务（上游） | `getDelayedQueue` 调用 | 上游 `QueueTransferService` 引用计数 | 仅 `destroy()` 减计数 | 中：重复注册使计数虚高（F-08） |
| stream 消费者/调度器 | `GXRedissonStreamMQManager.start()` | `consumerThreads` | `stop()`（受 `started` 守卫） | 中：半途失败不可回收（F-14） |

**不变量**：矩阵中每个"泄漏风险 ≠ 低"的单元必须有对应 Finding；每条停止路径必须注明"是否回收在途消息"。

**释放路径展开**：除上表的"停止路径"外，判定 MUST 覆盖四条路径——重复注册、启动失败、容器关闭、Redis 关闭中；展开后的逐格结论见 `review-report.md` §5.1（20 格中 16 格达标、4 格不达标，分别对应 F-08 与 F-14）。

## 5. 性能口径

| 路径 | 单条往返次数 | 单轮上限 | 临界区 | 复算方式 |
| --- | --- | --- | --- | --- |
| 延迟队列转发 | 抓取 1（Lua `drainTo`）+ 失败回退 1 | 20 | 本地队列（5000），无分布式锁 | Mock + `verify` 统计 `drainTo/offer` 次数 |
| stream 延迟转移 | 6 | 200 | 全局锁 `mq:delay:lock` | Mock + 统计 6 个操作调用次数；锁窗口按上限推算 |
| stream 立即消费 | 4（去重命中 1） | `batchSize` | 无锁 | Mock + `verify` 统计 `containsKey/fastPut/ack` |
| 锁工具（`GXRedissonUtils`） | 加锁 1 + 释放 3（`isLocked`+`isHeldByCurrentThread`+`unlock`） | — | 锁本身 | `verify` 调用次数 |
| 可靠主题批量发布（`GXRedissonMQUtils#publishBatch`） | 每条 1（`reliableTopic.publish`）；`null` 元素跳过，0 次 | 无内建上限（= 入参 `List` 大小） | 无锁，逐条串行 | `verify(reliableTopic, times(n)).publish(any())`；返回值为成功条数 |

**校验规则**：报告中的每个数字必须能由"代码行 → 调用序列"逐项复算；出现"约""大概"等修饰即判定不合格（SC-004）。

## 6. 覆盖率台账

| 字段 | 说明 |
| --- | --- |
| `file` | 生产文件相对路径 |
| `kind` | `生产代码` / `package-info` / `配置文件` |
| `verdict` | `无缺陷` / `风险` / `缺陷` / `未覆盖（附原因）` |
| `referencedBy` | 直接引用该类型的测试类（可为空） |
| `gapReason` | 无测试引用时的原因（纯声明、仅反射、仅装配条件等） |

**不变量**：台账行数 = 51（生产 java）+ 1（yml）= 52；`verdict = 缺陷` 的每一行必须指向至少 1 个 `F-NN`（SC-001）。

## 7. 级别判定规则

| 级别 | 条件（满足其一） |
| --- | --- |
| 高 | 可导致消息丢失/重复消费/资损；或线程/连接不可回收导致进程无法优雅退出；或失败路径完全静默 |
| 中 | 在特定条件（上游升级、负载上升、配置变更、并发时序）下退化为高危；或造成明显吞吐/延迟劣化；或资源泄漏可累积 |
| 低 | 语义不清、文档漂移、可观测性不足，无直接功能后果 |
