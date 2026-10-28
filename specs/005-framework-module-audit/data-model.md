# Phase 1 Data Model: 审查交付物结构

**Feature**: `005-framework-module-audit` | **Date**: 2026-10-22

本文件定义审查交付物的**结构语义**（字段、取值域、约束、状态流转），不定义实现类。所有结构以 Markdown 表格/条目形式落在 `review-report.md` 与 `coverage-ledger.md` 中；`contracts/` 下的两份契约给出校验规则。

---

## 1. Finding（审查发现）

审查的原子单位，对应 `F-01`…`F-21`（本轮既有）与 `F-22+`（追加）。

| 字段 | 类型 | 必填 | 取值域 / 规则 |
| --- | --- | --- | --- |
| `id` | string | 是 | `F-NN`，两位数字；一经发布不可重排（research D11） |
| `title` | string | 是 | 一句话结论，须含"现象 + 影响"，禁止只写"存在问题" |
| `severity` | enum | 是 | `HIGH` / `MEDIUM` / `LOW`，定义见 `contracts/review-report-schema.md` |
| `dimensions` | enum[] | 是 | `STABILITY` / `CORRECTNESS` / `THREAD_SAFETY` / `PERFORMANCE` / `SECURITY_BOUNDARY`，至少 1 项 |
| `location` | string | 是 | `相对路径:起行-止行`，路径相对 `src/main/java/cn/maple/core/framework` 或 `src/main/resources` |
| `trigger` | string | 是 | 触发条件（输入形态 / 运行形态 / 时序），须可构造 |
| `impact` | string | 是 | 影响面：功能、数据、安全、可观测性，写清"谁会看到什么后果" |
| `evidenceRefs` | string[] | 是 | `E-NN` 列表，至少 1 条；`HIGH` 项至少 1 条非 `READ_INFERENCE` |
| `verdict` | enum | 是 | `DEFECT` / `RISK` / `CONTRACT_DEVIATION` / `ESTABLISHED_CONTRACT` / `UNVERIFIED_ASSUMPTION` |
| `reproduction` | string | 是 | 可执行步骤（命令 + 预期现象）或"不可执行 + 原因"；`UNVERIFIED_ASSUMPTION` 必填原因 |
| `regressionTestDesign` | object | 是 | `{input, assertionPoint, expectedFlip}`——预期在修复后由 A 变为 B |
| `compatibilityImpact` | enum | 是 | `NONE` / `BEHAVIOR_CHANGE` / `BREAKING`，对应 FR-016 |
| `recommendation` | string | 是 | 修复方向（不含具体实现代码），须标注"需独立审批" |
| `status` | enum | 是 | 见 §1.1 状态机 |

### 1.1 Finding 状态机

```text
DRAFT ──复核──> REVIEWED ──定级──> CLASSIFIED ──复现──> REPRODUCED ──交付──> DELIVERED
  │                 │                  │                    │
  └──证据不足──────> UNVERIFIED_ASSUMPTION（终态，不计入缺陷统计）
```

- **DRAFT → REVIEWED**：位置与触发条件被第二人按行号核对通过。
- **REVIEWED → CLASSIFIED**：`severity` + `verdict` + `compatibilityImpact` 三项已定。
- **CLASSIFIED → REPRODUCED**：`reproduction` 被执行过并记录现象；或由审计用例覆盖（标注用例全名）。
- **REPRODUCED → DELIVERED**：条目写入 `review-report.md` 且 `coverage-ledger.md` 已引用其 `id`。
- **任一状态 → UNVERIFIED_ASSUMPTION**：无法在本环境构造；必须写明"缺少什么条件才能验证"。

**不变量**：`status=DELIVERED` 的条目，其 `regressionTestDesign.expectedFlip` 必须非空；`verdict=ESTABLISHED_CONTRACT` 的条目不得出现在 SC-009 的阻断清单中。

---

## 2. Evidence（证据）

| 字段 | 类型 | 必填 | 取值域 / 规则 |
| --- | --- | --- | --- |
| `id` | string | 是 | `E-NN`，`E-01`…`E-22` 已占用，新增从 `E-23` 起 |
| `kind` | enum | 是 | `SOURCE_FACT`（本仓库代码位置）/ `EXECUTABLE`（用例或命令输出）/ `DEPENDENCY_FACT`（依赖源码/文档）/ `READ_INFERENCE`（阅读推断） |
| `location` | string | 条件 | `SOURCE_FACT`/`DEPENDENCY_FACT` 必填；`EXECUTABLE` 填用例全名或命令 |
| `claim` | string | 是 | 该证据能支撑的唯一事实，一句一事实 |
| `verifiedBy` | string | 条件 | `EXECUTABLE` 必填：执行时间 + 结果摘要（如 `518 passed`） |

**等级关系**：`SOURCE_FACT` = `DEPENDENCY_FACT` > `EXECUTABLE` > `READ_INFERENCE`。引用 `READ_INFERENCE` 的 Finding 只能是 `RISK` 或 `UNVERIFIED_ASSUMPTION`（research D12）。

---

## 3. ContextDisciplineEntry（上下文纪律矩阵项）

覆盖所有 `ThreadLocal` / `MDC` / 请求上下文 / 安全上下文使用点，对应 FR-005、SC-003。

| 字段 | 类型 | 必填 | 取值域 / 规则 |
| --- | --- | --- | --- |
| `contextItem` | enum | 是 | `TRACE_ID` / `MDC_OTHER` / `REQUEST_CONTEXT` / `SECURITY_CONTEXT` / `DATA_SOURCE_CONTEXT` / `TENANT_CONTEXT` / `THREAD_LOCAL_OTHER`（非 MDC 的线程局部缓存或状态，如 `HTML_FILTER`、`THREAD_LOCKS`、动态服务类绑定；仅要求判定"写入—清理"是否成对） |
| `writePoint` | string | 是 | `文件:行` |
| `propagatePath` | enum[] | 是 | `SYNC` / `ASYNC_EXECUTOR` / `FORK_JOIN` / `PARALLEL_STREAM` / `EVENT_BUS` / `SCHEDULER` |
| `cleanPoint` | string | 条件 | `文件:行`；无清理点填 `NONE` |
| `exceptionPathVerdict` | enum | 是 | `RESTORED` / `LEAKED` / `NOT_EXERCISED`（无 `finally` 或未覆盖） |
| `verdict` | enum | 是 | `OK` / `GAP` / `RISK` |
| `findingRefs` | string[] | 条件 | `verdict != OK` 时必填 |
| `evidence` | string | 条件 | `verdict=OK` 且 `propagatePath` 含 `ASYNC_EXECUTOR`/`FORK_JOIN`/`PARALLEL_STREAM` 时必填：`EXECUTABLE` 用例全名（`类#方法`，多个以 `、` 分隔）；其余行可填复现用例全名或 `-` |

**不变量**：`propagatePath` 含 `ASYNC_EXECUTOR`/`FORK_JOIN`/`PARALLEL_STREAM` 且 `verdict=OK` 的项，其 `evidence` 必须附 `EXECUTABLE` 证据（可执行用例全名）；否则降级为 `RISK`。

---

## 4. LifecycleEntry（并发与生命周期矩阵项）

对应 FR-004、User Story 2。

| 字段 | 类型 | 必填 | 取值域 / 规则 |
| --- | --- | --- | --- |
| `component` | string | 是 | 如 `async-executor` / `concurrent-tools-pool` / `event-executor` / `forkjoin-wrapper` / `rate-limiter-map` / `class-cache` |
| `createdBy` | string | 是 | `文件:行` + 创建时机（Bean 单例 / 每次调用 / 静态初始化） |
| `concurrencyLimit` | string | 是 | 数值或 `UNBOUNDED` / `CPU*n`，附来源行号 |
| `stopPath` | enum | 是 | `DESTROY_HOOK` / `SHUTDOWN_CALL` / `NOT_STOPPABLE` / `DAEMON_ONLY` |
| `leakRisk` | enum | 是 | `NONE` / `GROWS_UNBOUNDED` / `NEVER_RELEASED` / `STALE_AFTER_REBUILD` |
| `findingRefs` | string[] | 条件 | `leakRisk != NONE` 时必填 |

---

## 5. FailureConvergenceEntry（失败收敛项）

对应 FR-007、SC-005：穷举生产代码中"失败不抛"的分支。

| 字段 | 类型 | 必填 | 取值域 / 规则 |
| --- | --- | --- | --- |
| `location` | string | 是 | `文件:行` |
| `failureType` | enum | 是 | `EXCEPTION_CAUGHT` / `NULL_RETURN` / `INPUT_RETURN` / `EMPTY_COLLECTION` / `DEFAULT_VALUE` |
| `convergence` | string | 是 | 当前处理方式（如 `catch ignored` / `return dataStr`） |
| `logged` | enum | 是 | `ERROR` / `WARN` / `DEBUG` / `NONE` |
| `observable` | boolean | 是 | 运维能否在指标/日志中发现该失败 |
| `constitutionVerdict` | enum | 是 | `COMPLIANT` / `VIOLATES_III`（禁止 null/空集合表达失败）/ `VIOLATES_IV`（禁止静默降级）/ `CONTRACT`（被既有用例锁定） |

---

## 6. PerformanceMetric（性能口径项）

对应 FR-006、SC-004，覆盖 6 条横切路径。

| 字段 | 类型 | 必填 | 取值域 / 规则 |
| --- | --- | --- | --- |
| `path` | enum | 是 | `XSS_REWRITE` / `BODY_CACHE` / `RATE_LIMIT` / `CONCURRENT_TOOLS` / `EVENT_DISPATCH` / `REFLECTION_COPY` |
| `formula` | string | 是 | 可代入的表达式，如 `peak ≈ 2 × body × concurrency`、`cost ≈ N × O(n)` |
| `unit` | enum | 是 | `BYTES` / `CALLS` / `ITERATIONS` / `SUBMISSIONS` / `STRING_BUILDS` |
| `upperBound` | string | 是 | 结构上限 + 来源（`50MB`、`CPU*2048`、`1000`、`UNBOUNDED`） |
| `sourceLines` | string[] | 是 | 至少 1 个 `文件:行` |
| `smokeAssertion` | string | 条件 | 可执行项必填；只允许计数/规模断言，禁止绝对耗时 |

---

## 7. CoverageLedgerEntry（覆盖台账项）

对应 FR-001、SC-001。`coverage-ledger.md` 每行一条，合计 **309**。

| 字段 | 类型 | 必填 | 取值域 / 规则 |
| --- | --- | --- | --- |
| `file` | string | 是 | 相对 `src/main/java/cn/maple/core/framework` 的路径（`package-info.java` 同样计行） |
| `package` | string | 是 | 首级包名（`dto`/`util`/`service`/…） |
| `strength` | enum | 是 | `IMPLEMENTATION`（逐文件读实现）/ `CONTRACT_ONLY`（契约核对） |
| `verdict` | enum | 是 | `CLEAN` / `RISK` / `DEFECT` / `CONTRACT_CHECKED` |
| `findingRefs` | string[] | 条件 | `verdict` 为 `RISK`/`DEFECT` 时必填 |
| `note` | string | 条件 | `CONTRACT_CHECKED` 必填一句判定依据（如"字段类型与默认值符合宪法 VII 命名/契约"） |

**不变量（可脚本核对）**：

1. 行数 = 309；每个文件恰好出现一次。
2. `verdict=DEFECT` 的行数 + `verdict=RISK` 的行数 ≥ `review-report.md` 中 `DEFECT`/`RISK` 条目的位置去重数（不得有发现未登记）。
3. `strength=CONTRACT_ONLY` 只允许出现在 research D1 列出的包中。
4. 任何 `DEFECT` 行的 `findingRefs` 非空。

---

## 8. GateDecision（准入门槛结论）

对应 FR-015、SC-009，报告末尾唯一结论段。

| 字段 | 类型 | 必填 | 取值域 / 规则 |
| --- | --- | --- | --- |
| `scope` | string | 是 | 固定为 `leaf-base-framework` + 审查基线 commit/日期 |
| `highRiskCount` | int | 是 | `severity=HIGH` 且 `verdict=DEFECT` 的条目数 |
| `blockers` | string[] | 条件 | `highRiskCount > 0` 时必填，列出全部高危 `F-NN` |
| `recommendation` | enum | 是 | `SUITABLE_WITH_CONDITIONS` / `NOT_SUITABLE_AS_BASELINE` |
| `conditions` | string[] | 条件 | `SUITABLE_WITH_CONDITIONS` 必填：进入条件（如"F-02 修复前不得在共享线程池场景使用自定义 MDC 键"） |
| `rationale` | string | 是 | 3 句以内，必须引用 `blockers`，不得出现"整体尚可"等无信息量表述 |

**规则**：`highRiskCount > 0` 时 `recommendation` 不得为 `SUITABLE_WITH_CONDITIONS` 以外的值；`blockers` 与 `spec.md` 的 F-01…F-08 集合不一致时，必须在报告"修订说明"中给出理由（research D11）。

---

## 9. 结构关系

```text
Finding ──evidenceRefs──> Evidence
   │                          ▲
   ├──location──> CoverageLedgerEntry.findingRefs
   ├──dimension=THREAD_SAFETY ──> ContextDisciplineEntry.findingRefs
   ├── (资源/线程) ─────────────> LifecycleEntry.findingRefs
   ├──verdict=DEFECT ───────────> FailureConvergenceEntry.location（同位置可多对一）
   └──dimension=PERFORMANCE ────> PerformanceMetric.path
GateDecision ──blockers──> Finding[severity=HIGH, verdict=DEFECT]
```

**跨结构一致性要求**：

1. `spec.md` 的 E-01…E-22 / F-01…F-21 必须在报告中全部出现（编号一致）。
2. 所有 `HIGH` Finding 至少对应 1 个 `ContextDisciplineEntry` 或 `LifecycleEntry` 或 `FailureConvergenceEntry` 的 `GAP`/`VIOLATES_*` 项。
3. `PerformanceMetric` 的 6 个 `path` 必须各出现一次，不得缺项。
4. `GateDecision.blockers` 与 `HIGH + DEFECT` 集合相等。
