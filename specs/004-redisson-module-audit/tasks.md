---

description: "Task list for leaf-base-redisson audit delivery"
---

# Tasks: leaf-base-redisson 逻辑正确性、线程安全与性能审查

**Input**: Design documents from `/specs/004-redisson-module-audit/`

**Prerequisites**: `plan.md`、`spec.md`、`research.md`、`data-model.md`、`contracts/review-report-schema.md`、`contracts/audit-coverage-contract.md`、`quickstart.md`

**Tests**: 本特性的"测试"是**交付物本身**（特征化用例 `CURRENT BEHAVIOR`），不是可选步骤。每个用例断言当前行为并标注修复后应翻转的断言，因此测试任务按用户故事归入各阶段，不单列为 TDD 步骤。

**Organization**: 任务按 spec.md 的 5 个用户故事分组；同一故事内可独立完成与验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 归属用户故事（US1…US5）
- 所有路径相对仓库根 `e:\project\java\maple-leaf-framework`

## Path Conventions

- 审计用例：`leaf-base-redisson/src/test/java/cn/maple/redisson/audit/`
- 审查报告与台账：`specs/004-redisson-module-audit/`
- 生产代码：**只读**，本任务列表不含任何 `src/main` 修改任务

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立可复现的基线事实，供后续所有结论引用

- [X] T001 [P] 执行基线测试并把命令、JDK/Maven 版本、38 个用例通过情况写入 `specs/004-redisson-module-audit/checklists/baseline-test-run.md`（命令：`mvn -pl leaf-base-redisson -o test`）
- [X] T002 [P] 按 `quickstart.md` §2 提取 `redisson-4.7.0-sources.jar` 并核对四项依赖事实（`removeListenerAsync` 顺序、`drainToAsync` 单段 Lua、`QueueTransferService` 引用计数、`RDelayedQueue` 弃用日志），结果写入 `specs/004-redisson-module-audit/checklists/dependency-facts.md`
- [X] T003 [P] 生成 52 行覆盖率台账骨架（51 个生产 java + `application.yml`），字段按 `data-model.md` §6，写入 `specs/004-redisson-module-audit/checklists/coverage-ledger.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 报告骨架与用例支撑设施，任何用户故事开始前必须就绪

**⚠️ CRITICAL**: 本阶段完成前，US1～US5 的报告写入都无法进行

- [X] T004 按 `contracts/review-report-schema.md` §1 创建 `specs/004-redisson-module-audit/review-report.md` 骨架，含全部 10 个固定章节标题，F-01…F-16 各留空条目占位（字段顺序固定，禁止增删章节）
- [X] T005 [P] 创建审计用例支撑类 `leaf-base-redisson/src/test/java/cn/maple/redisson/audit/GXRedissonAuditTestSupport.java`，提供 `RedissonClient`/`RBlockingQueue`/`RDelayedQueue`/`RReliableTopic`/`RStream` 的 Mock 工厂与反射注入 helper（沿用 `ReflectionTestUtils`，不新增依赖、不连接真实 Redis）
- [X] T006 在 `review-report.md` §9 填充 E-01～E-13 证据附录，每条含 `level`（S/D/T）、`source`、`excerpt`（≤2 行）、`usedBy`，编号与 `spec.md` 完全一致
- [X] T007 在 `review-report.md` §2 写入审查范围与基线：51 个生产文件与 1 个 `application.yml`、38 个既有用例、Redisson 4.7.0、三级证据等级说明（引用 `research.md` R-01）

---

## Phase 3: User Story 1 - 审查结论可复核、可定位 (Priority: P1) 🎯 MVP

**Goal**: 16 条发现全部成形（维度/级别/位置/触发条件/影响/证据/复现），任何读者可按位置复现结论。

**Independent Test**: 抽取任一 Finding，按其 `位置` 打开代码、按 `复现` 执行，断言现象与描述一致；报告 §1 与 `spec.md` 结论表逐字一致。

- [X] T008 [US1] 在 `review-report.md` §3 写入 F-01 / F-09 / F-10（主题链路：订阅缓存不可自愈、静态缓存跨容器、业务异常被吞），含 `regressionTestDesign` 与 "修复后断言翻转" 说明
- [X] T009 [US1] 在 `review-report.md` §3 写入 F-02 / F-05 / F-06 / F-08 / F-16（延迟转发链路：至多一次、无背压、重试无退避、引用计数泄漏、弃用 API）
- [X] T010 [US1] 在 `review-report.md` §3 写入 F-03 / F-12（stream 可靠性与去重：无 handler 丢弃、毒消息常驻、check-then-act、恢复饥饿）
- [X] T011 [US1] 在 `review-report.md` §3 写入 F-04 / F-07 / F-14（stream 全局锁、消费者并发度不确定、生命周期不闭合）
- [X] T012 [US1] 在 `review-report.md` §3 写入 F-11 / F-13 / F-15（锁与计数器语义、连接配置边界、既定契约），其中 F-15 必须标注 `修复批次 = 仅文档`
- [X] T013 [P] [US1] 创建 `leaf-base-redisson/src/test/java/cn/maple/redisson/audit/GXRedissonStreamReliabilityAuditTest.java`，覆盖 F-03（无 handler 时 `ack` 被调用且无 DLQ 记录）与 F-12（`markProcessed` 失败仍 `ack`），每个用例注释标注 `CURRENT BEHAVIOR` 与修复后应翻转的断言
- [X] T014 [US1] 复核 `review-report.md` 中全部 `位置` 行区间（16 条），并写 §10 复现索引（每条 F 对应命令或用例全名）
- [X] T015 [US1] 把 `review-report.md` §1 结论摘要与 `spec.md` 的"审查结论"表逐字对齐（维度、结论、判定三列）

---

## Phase 4: User Story 2 - 并发与资源释放风险可被验证 (Priority: P1)

**Goal**: 用可执行用例固定"线程不释放、并发度漂移、订阅缓存失效、in-flight 无界、引用计数虚高"五类现象。

**Independent Test**: 执行 `mvn -pl leaf-base-redisson -o test -Dtest="GXRedissonTopicSubscriptionAuditTest,GXRedissonConsumerConcurrencyAuditTest,GXRedissonDelayForwardingAuditTest"`，全部通过且不连接真实 Redis。

- [X] T016 [P] [US2] 创建 `leaf-base-redisson/src/test/java/cn/maple/redisson/audit/GXRedissonTopicSubscriptionAuditTest.java`，覆盖 F-01（`removeListener` 抛异常后再 `subscribe` 返回旧 id 且不再 `addListener`）、F-09（静态缓存持有旧客户端句柄）、F-10（`wrapListener` 吞业务异常后不重投）
- [X] T017 [P] [US2] 创建 `leaf-base-redisson/src/test/java/cn/maple/redisson/audit/GXRedissonConsumerConcurrencyAuditTest.java`，覆盖 F-07（不同注册顺序下 `getConsumerCountForQueue` 结果不同）与 F-14（`GXRedissonStreamMQManager.start()` 半途失败后 `stop()` 不回收已启动线程；`blockingTimeoutMillis = 0` 被接受）
- [X] T018 [P] [US2] 创建 `leaf-base-redisson/src/test/java/cn/maple/redisson/audit/GXRedissonDelayForwardingAuditTest.java`，覆盖 F-02（重投失败时消息只留在 `inFlightMessages`）、F-05（handler 永不完成时 `inFlightMessages.size()` 单调增长）、F-06（重试间隔固定为 1s 线性递增、无抖动，最坏窗口约 2 分钟）、F-08（重复注册后 `destroy()` 只减一次引用计数）
- [X] T019 [US2] 在 `review-report.md` §5 填充并发与生命周期矩阵（5 个组件 × 创建时机 × 并发度来源 × 停止路径 × 泄漏风险），每个"泄漏风险 ≠ 低"的单元必须回指 T016～T018 的用例或 F 编号
- [X] T020 [US2] 更新 `checklists/coverage-ledger.md`：为 `processor`、`stream`、`util` 包下被上述用例引用的文件填写 `referencedBy`，未覆盖文件补齐 `gapReason`
- [X] T021 [US2] 回填 F-01 / F-02 / F-07 / F-08 / F-14 的 `证据等级`（由 S 提升为 T）与 `复现` 字段（指向 T016～T018 的用例全名）

---

## Phase 5: User Story 3 - 性能瓶颈定量化 (Priority: P2)

**Goal**: 三条关键路径与锁工具给出可复算的往返计数、单轮上限与临界区范围，且数字与用例 `verify` 次数一致。

**Independent Test**: 按 `quickstart.md` §4 的步骤独立数一遍代码路径，结果与报告 §6 完全一致；报告中无"约/大概/左右"。

- [X] T022 [P] [US3] 创建 `leaf-base-redisson/src/test/java/cn/maple/redisson/audit/GXRedissonStreamTransferAuditTest.java`，覆盖 F-04：断言单条消息触发 `hash.get`/`transferSet.add`/`zset.remove`/`publish`/`hash.remove`/`transferSet.remove` 各 1 次（共 6 次），且两个 topic 在同一把 `mq:delay:lock` 下串行
- [X] T023 [P] [US3] 创建 `leaf-base-redisson/src/test/java/cn/maple/redisson/audit/GXRedissonLockSemanticsAuditTest.java`，覆盖 F-11：`tryLock` 失败时 `executeWithLock` 返回 `null`；释放路径依次调用 `isLocked`/`isHeldByCurrentThread`/`unlock`（3 次往返）；`getCounterValue` 异常返回 `0`
- [X] T024 [US3] 在 `review-report.md` §6 写入四条路径的性能口径表（延迟队列转发 / stream 延迟转移 / stream 立即消费 / 锁工具），列 `单条往返次数`、`单轮上限`、`临界区`、`复算方式`，数字为整数
- [X] T025 [US3] 回填 F-04 / F-05 / F-06 / F-11 的 `影响` 与 `复现`：写入往返计数、单轮上限与锁粒度，并注明"批量接口按实现计 1 次往返"（对照 `contracts/audit-coverage-contract.md` §4）

---

## Phase 6: User Story 4 - 对外契约与文档一致 (Priority: P3)

**Goal**: 文档承诺与实现的每条偏差都有判定，且"既定契约"与"缺陷"不混淆。

**Independent Test**: 按 `review-report.md` §7 抽取任一行，核对 `AGENTS.md` 或方法签名原文，断言判定为"一致/不一致/未覆盖"三者之一。

- [X] T026 [US4] 在 `review-report.md` §7 写入契约对照表：`AGENTS.md` 关键行为（可靠主题幂等、延迟转发可观测、缓存 TTL 语义）、公开方法签名（`executeWithLock`/`getCounterValue`/`subscribe`）、配置键（`maple.framework.mq.redisson.enable`、`blockingTimeoutMillis`、`streamMaxLen`）逐条对照
- [X] T027 [P] [US4] 创建 `leaf-base-redisson/src/test/java/cn/maple/redisson/audit/GXRedissonConnectConfigAuditTest.java`，覆盖 F-13：地址仅含分隔符时抛数组越界而非可读配置异常；`isClusterConfig` 仅凭键名含 `cluster` 判定
- [X] T028 [US4] 在 `review-report.md` §7 登记 F-15（`updateCacheExpiredTime` 对 TTL=-1 返回 `true`，已有用例 `updateCacheExpiredTimeKeepsExistingNoExpiryEntry` 锁定）与 F-16（`RDelayedQueue` 弃用，替代为 `RReliableQueue`）的处置口径：前者仅文档、后者列为升级风险

---

## Phase 7: User Story 5 - 结论可驱动后续修复排期 (Priority: P3)

**Goal**: 每条发现可直接拆成"测试先行"的任务，且报告通过全部机器可校验规则。

**Independent Test**: 对任取一条 Finding，按 `fixBatch` 与 `regressionTestDesign` 能在不追问作者的情况下写出任务；`quickstart.md` §6 的四条检查命令全部符合预期。

- [X] T029 [US5] 补齐全部 16 条 Finding 的 `修复批次`（P0：F-01/F-02/F-03；P1：F-04/F-05/F-08/F-09/F-14；P2：F-06/F-07/F-10/F-11/F-12/F-13/F-16；仅文档：F-15）与 `契约影响`（无影响写 `none`）
- [X] T030 [US5] 校验每条 `regressionTestDesign` 均含"输入 / 断言点 / 修复后翻转"，缺失者补齐；确保 P0 三条同时具备可执行用例（T013/T016/T018）
- [X] T031 [US5] 在 `review-report.md` §8 写入未验证假设清单（无内容时写 `（无）`，章节不得删除）
- [X] T032 [US5] 执行 C-01～C-10 自检：字段计数（每条 10 个）、F/E 编号与 `spec.md` 一致、级别与维度枚举、高危条目证据等级 ∈ {D,T}、性能章节无"约/大概/左右"、缺陷计数与摘要一致
- [X] T033 [US5] 修正 `spec.md` 与报告之间的任何不一致（以 `spec.md` 需求为准），保持 FR/SC 引用有效

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T034 执行 `mvn -pl leaf-base-redisson -o test`（含 T013/T016/T017/T018/T022/T023/T027 新增用例），把命令与结果追加到 `specs/004-redisson-module-audit/checklists/baseline-test-run.md`，确保既有 38 用例全绿
- [X] T035 交付边界核对：`git status --short` 仅含 `specs/004-redisson-module-audit/**` 与 `leaf-base-redisson/src/test/java/cn/maple/redisson/audit/**`；`git status --short -- leaf-base-redisson/src/main leaf-base-redisson/pom.xml` 为空（L-01～L-05）
- [X] T036 完成 `checklists/coverage-ledger.md` 全量判定：52 行均有 `verdict`，`verdict = 缺陷` 的行必须指向 F 编号，`未覆盖` 仅允许 `package-info.java` 且 `gapReason` 非空
- [X] T037 按 `quickstart.md` §6 输出最终验收记录（四条命令的结果），并把审查结论摘要同步到 `checklists/requirements.md` 的 Notes 中

---

## Dependencies

```text
Phase 1 (T001-T003)  ──> Phase 2 (T004-T007)  ──> 所有用户故事
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
   US1 (T008-T015)             US2 (T016-T021)             US3 (T022-T025)
   报告主体 + stream 可靠性用例   并发/资源用例 + §5 矩阵      性能用例 + §6 口径
        │                           │                           │
        └───────────────┬───────────┴───────────┬───────────────┘
                        ▼                       ▼
                 US4 (T026-T028)          US5 (T029-T033)
                 契约对照 + 连接配置用例    批次/回归设计/自检
                                    │
                                    ▼
                            Phase 8 (T034-T037)
```

**关键约束**：

- US1 可先于 US2/US3 开始（报告字段允许引用尚未落地的用例名），但 **US5 的 C-05 校验必须在 US2/US3 用例落地后执行**。
- T021、T025 是"回填"任务，必须在对应用例（T016～T018、T022～T023）通过后执行。
- T036 依赖 T020（台账 `referencedBy`）与 US1 全部 F 条目。
- 任一任务不得修改 `leaf-base-redisson/src/main/**` 与 `pom.xml`（L-01/L-02）。

### Within Each User Story

- 先建用例（可执行证据）→ 再写报告章节 → 最后回填字段，避免"先写结论再找证据"。
- 同一文件的任务必须串行：`review-report.md` 的 §3 由 T008～T012 顺序写入；`checklists/coverage-ledger.md` 由 T020 与 T036 顺序更新。

---

## Parallel Example: User Story 2

```text
# 三个用例文件互不重叠，可同时开始：
T016  GXRedissonTopicSubscriptionAuditTest.java
T017  GXRedissonConsumerConcurrencyAuditTest.java
T018  GXRedissonDelayForwardingAuditTest.java

# 三者完成后串行：
T019  §5 并发与生命周期矩阵
T020  覆盖率台账 referencedBy
T021  回填 F-01/F-02/F-07/F-08/F-14
```

## Parallel Example: User Story 3

```text
T022  GXRedissonStreamTransferAuditTest.java      # 6 次往返 + 全局锁
T023  GXRedissonLockSemanticsAuditTest.java       # 加锁 1 + 释放 3
# 之后串行：
T024  §6 性能口径表
T025  回填 F-04/F-05/F-06/F-11
```

---

## Implementation Strategy

### MVP First（US1 单独可交付）

1. 完成 Phase 1 + Phase 2（基线与报告骨架）。
2. 完成 US1（T008～T015）：16 条发现 + 证据附录 + 复现索引 + stream 可靠性用例。
3. **STOP and VALIDATE**：抽取 3 条发现按 `位置` 与 `复现` 复核，确认 SC-001/SC-002 成立。
4. 此时报告已可交付给维护者做决策——这就是本次审查的最小价值闭环。

### Incremental Delivery

1. US1 → 结论可复核（MVP）。
2. US2 → 并发与资源风险有可执行证据，`§5` 矩阵成形（SC-003）。
3. US3 → 性能口径定量化（SC-004）。
4. US4 → 契约对照与文档漂移登记（SC-005 的文档部分）。
5. US5 + Phase 8 → 自检、边界核对、验收记录（SC-006/SC-007、L-01～L-05）。

### Parallel Team Strategy

- Phase 1～2 由 1 人完成（20 分钟内）。
- US1 完成后：A 做 US2 三个用例，B 做 US3 两个用例，C 做 US4 契约对照（不同文件，互不冲突）。
- 汇合后由同一人执行 T021/T025/T029～T033，避免 `review-report.md` 并发写冲突。

---

## Notes

- `[P]` 仅用于不同文件的用例类；报告与台账为单文件，一律串行。
- 所有新增用例必须标注 `CURRENT BEHAVIOR`，并写明"修复后应翻转为 …"，它们是 P0 修复的验收开关。
- 性能数字只允许整数计数；一旦需要写"约"，说明口径未对齐 `contracts/audit-coverage-contract.md` §4。
- 每完成一个 Phase 提交一次，提交信息遵循 `docs(spec): 004 审查…` 格式。
- 审查对象只读：任何"顺手修复"的想法写入 `§3` 的 `修复建议`，不落到代码。

---

## Phase 9: Convergence

> 由 `/speckit.converge` 于 2026-10-22 追加，按严重度排序；仅追加，未改动 T001–T037。

- [X] T038 将 `specs/004-redisson-module-audit/review-report.md` §5 的并发与生命周期矩阵扩为"5 个组件 × 4 条释放路径（重复注册 / 启动失败 / 容器关闭 / Redis 关闭中）"的逐格判定表，每格给出判定与依据（用例名或 F 编号），未覆盖的格子明确标注"未覆盖"及其原因 per SC-003 + contracts §7 (partial)
- [X] T039 在 `specs/004-redisson-module-audit/review-report.md` §6 增补"可靠主题批量发布（`GXRedissonMQUtils#publishBatch`）"一行：单条 1 次 `publish` 往返、失败仅 `LOGGER.error` 且 `successCount` 不回滚、返回值为成功条数，并给出 1000 条输入的冒烟复算方式（成功数 / 失败数 / 是否静默吞掉）per US3/AC4 + FR-005 (missing)
- [X] T040 在 `specs/004-redisson-module-audit/review-report.md` §1 增补"风险排序依据：影响面 × 触发概率 × 可观测性"的说明，并在 §3 每条 Finding 增加"排序三因子"字段（三档取值），使批次划分可被复核 per FR-015 (missing)
- [X] T041 在 `specs/004-redisson-module-audit/review-report.md` §7 增补"日志级别与可观测性"对照表：逐条核对 F-03（无 handler 丢弃）、F-12（`markProcessed` 失败）、F-14（建组失败降级为 warn、启动半途失败）的失败路径，判定"已启用但未生效"是否达到 warn 及以上、是否被 debug 掩盖 per FR-013 (partial)
- [X] T042 将 `specs/004-redisson-module-audit/checklists/coverage-ledger.md:84`（`services/impl/GXRedissonCacheServiceImpl.java`）的 `verdict` 由"既定契约"改为 FR-001 枚举内的"无缺陷"，并在发现列保留 `F-15` 与"既定契约、仅需文档同步"的说明；同步修正第 134 行汇总表的计数 per FR-001 (contradicts)
- [X] T043 在 `specs/004-redisson-module-audit/review-report.md` §2 记录与 `plan.md` 的两处偏差及理由：新增 9 个 java 文件（8 个测试类 + 1 个 support）超出"≤ 8 个文件"，且 support 仅提供反射 helper 与属性构造、未提供 `RBlockingQueue`/`RDelayedQueue` 等 mock 工厂 per plan: 结构决策 (partial)
- [X] T044 在 `specs/004-redisson-module-audit/review-report.md` §2 标注 `GXRedissonRealServiceVerificationTest` 的来源（现场验证需求，非 spec/plan 原始范围）、默认跳过条件（`AUDIT_REDIS_HOST` 未设置即 `Skipped: 5`）与边界（只确认正常路径可用，不推翻任何缺陷结论），供下次 plan 修订时登记 per plan: 范围 (unrequested)

---

## Phase 10: Convergence

> 由 `/speckit.converge` 于 2026-10-22 追加（第二轮），按严重度排序；仅追加，未改动 T001–T044。

- [X] T045 将 `specs/004-redisson-module-audit/checklists/baseline-test-run.md` §4 的汇总行由 `Tests run: 60, Failures: 0, Errors: 0, Skipped: 0` 更新为当前实测 `Tests run: 65, Failures: 0, Errors: 0, Skipped: 5`，并注明"60 个为实际执行用例、5 个为未设置 `AUDIT_REDIS_HOST` 时跳过的真实服务用例"，使该行与同节第 75 行及表内各类用例数（38 + 22 + 5）相互一致 per SC-006 (partial)
- [X] T046 在 `specs/004-redisson-module-audit/data-model.md` §5 性能口径表补"可靠主题批量发布（`publishBatch`）"一行（每条 1 次 `publish`、无内建上限、无锁、`verify` 计数），并在 §4 并发矩阵末尾引用报告 §5.1 的"4 条释放路径"判定，使设计文档与 `review-report.md` §5.1/§6 对齐 per FR-005 (partial)
- [X] T047 将 `quickstart.md:111`、`review-report.md:22`、`review-report.md:400`、`checklists/baseline-test-run.md:72` 中的具体内网地址统一替换为 `<redis-host>` 占位符，并在同处说明"地址与密码均由 `AUDIT_REDIS_HOST` / `AUDIT_REDIS_PASSWORD` 提供"，避免文档固化环境地址 per Constitution IV (partial)

---

## Phase 11: Convergence

> 由 `/speckit.converge` 于 2026-10-22 追加（第三轮）；仅追加，未改动 T001–T047。

- [X] T048 统一三处测试总数口径为当前实测 `Tests run: 65, Failures: 0, Errors: 0, Skipped: 5`（60 执行 + 5 跳过）：①`specs/004-redisson-module-audit/checklists/requirements.md` "实现后回填"的"测试结果"行；②`specs/004-redisson-module-audit/quickstart.md` §7 末段"流水线结果仍为 `Tests run: 60` 全绿"；③`quickstart.md` §1 的基线预期补充"整模块执行 60 个用例全绿、另有 5 个真实服务用例默认跳过"，使三处与 `checklists/baseline-test-run.md` §4 的汇总行完全一致 per SC-006 + FR-012 (partial)

---

## Phase 12: Convergence

> 由 `/speckit.converge` 于 2026-10-22 追加（第四轮）；仅追加，未改动 T001–T048。

- [X] T049 在 `specs/004-redisson-module-audit/review-report.md` §5 增补"5.2 共享可变状态与竞态判定（FR-004）"小节，显式映射 FR-004 的三类场景：共享可变状态（F-09 的静态 topic/listener 缓存、`properties/nacos/*` 热更新实例的非 `volatile` 字段）、生命周期（§5 与 §5.1 的组件与释放路径）、竞态（F-07 注册顺序影响消费者数、F-08 重复注册抬高引用计数）；并在小节末尾给出"三类场景 → 对应发现/用例"的映射表，使 `checklists/coverage-ledger.md:72` 的"报告 §5 记录"引用成立（或将该引用改为指向 §5.2） per FR-004 (partial)
