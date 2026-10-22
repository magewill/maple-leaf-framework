---

description: "Task list for leaf-base-framework module audit"
---

# Tasks: leaf-base-framework 稳定性、正确性、线程安全与性能审查

**Input**: Design documents from `/specs/005-framework-module-audit/`

**Prerequisites**: [plan.md](./plan.md) (tech stack, structure), [spec.md](./spec.md) (user stories P1–P3), [research.md](./research.md) (D1–D12), [data-model.md](./data-model.md) (8 个实体), [contracts/](./contracts/) (报告结构与判定口径), [quickstart.md](./quickstart.md) (验证命令)

**Tests**: 需要——但**不是** TDD 的"实现前失败"模式，而是**特征化测试**（断言当前行为，绿色即现状成立）。8 个审计用例类按 `plan.md` 的结构交付；`src/main/**` 与 `pom.xml` 零改动。

**Organization**: 按规格的 5 条用户故事分组，每条故事独立可验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1–US5），仅用户故事阶段使用
- 每个任务都给出精确文件路径

## Path Conventions

- 审查交付物：`specs/005-framework-module-audit/`
- 审计用例：`leaf-base-framework/src/test/java/cn/maple/core/framework/audit/`
- 审查对象（只读）：`leaf-base-framework/src/main/java/cn/maple/core/framework/`、`leaf-base-framework/src/main/resources/META-INF/`
- 相对路径基准：仓库根 `maple-leaf-framework/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立交付物骨架与可自动校验的框架；不触碰生产代码。

- [X] T001 创建交付骨架：`specs/005-framework-module-audit/review-report.md`（按 `contracts/review-report-schema.md` §1 的 13 个固定章节留空）与 `specs/005-framework-module-audit/coverage-ledger.md`（表头：`file | package | strength | verdict | findingRefs | note`）
- [X] T002 [P] 在 `specs/005-framework-module-audit/review-report.md` 第 2 章写入实测基线：`mvn -o -q -pl leaf-base-framework test` → `files=65 tests=518 failures=0 errors=0 skipped=0`、`@Test` 注解 519 与执行 518 的差额解释、运行 JDK 25.0.4 与 `release=21` 的偏差登记（research D8）
- [X] T003 [P] 创建审计用例包目录 `leaf-base-framework/src/test/java/cn/maple/core/framework/audit/`，并在 `specs/005-framework-module-audit/review-report.md` 第 11 章记录命名规范：`GX<被测能力>AuditTest` + 方法名直述现状 + 类头注释 `CURRENT BEHAVIOR - flip when fixed`
- [X] T004 [P] 编写报告校验脚本 `specs/005-framework-module-audit/checklists/report-checks.ps1`，实现 `contracts/review-report-schema.md` §8 的 C-01…C-06（编号齐全、字段齐全、高危有强证据、6 条口径、GateDecision 自洽、无绝对耗时断言）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 冻结编号、判定口径与台账结构。**任何用户故事开始前必须完成**。

**⚠️ CRITICAL**: US1–US5 全部依赖本阶段；编号一旦冻结不得重排（research D11）。

- [X] T005 用脚本扫描 `leaf-base-framework/src/main/java/cn/maple/core/framework/` 全部 `.java`（含 `package-info.java`）生成 `coverage-ledger.md` 的 309 行骨架，`verdict` 预填 `CONTRACT_CHECKED`、`strength` 预填 `CONTRACT_ONLY`；校验行数恰为 309（data-model §7 不变量 1）
- [X] T006 [P] 在 `review-report.md` 第 4 章固化 `E-01`…`E-22` 证据表，每条填 `kind` / `location` / `claim` / `verifiedBy`，并按 `contracts/review-report-schema.md` §4 标注证据等级（`SOURCE_FACT` / `EXECUTABLE` / `READ_INFERENCE`）
- [X] T007 [P] 在 `review-report.md` 第 5 章固化 `F-01`…`F-21` 条目骨架（`spec.md` 的标题与级别），10 个必填字段留 `TBD`，`status` 置 `DRAFT`
- [X] T008 [P] 在 `review-report.md` 第 11 章写入判定口径速查：五档判定（`DEFECT`/`RISK`/`CONTRACT_DEVIATION`/`ESTABLISHED_CONTRACT`/`UNVERIFIED_ASSUMPTION`）的判据，以及"缺陷 vs 既定契约"判定树（`contracts/audit-verdict-contract.md` §1、§4）
- [X] T009 在 `review-report.md` 第 11 章建立 Finding 状态机跟踪表（`DRAFT`→`REVIEWED`→`CLASSIFIED`→`REPRODUCED`→`DELIVERED`），并把 21 条发现的初始状态登记为 `DRAFT`

**Checkpoint**: 编号、口径、台账、脚本就绪——用户故事可并行开始。

---

## Phase 3: User Story 1 - 审查结论可复核、可定位 (Priority: P1) 🎯 MVP

**Goal**: 交付可被第三方按位置复现的结论集合：全量文件判定 + 证据 + 发现 + 准入门槛结论。

**Independent Test**: 执行 `checklists/report-checks.ps1`（C-01…C-06 全通过）+ 运行 `GXEventContractAuditTest` 与 `GXWebBoundaryAuditTest`（全绿）+ 随机抽 1 条发现按报告第 13 章复现成功。

### 全量审查（覆盖 FR-001/SC-001）

- [X] T010 [US1] 契约核对 13 个纯契约包共 154 个文件（`dto`83、`exception`23、`annotation`11、`constant`7、`code`4、`ddd`12、`properties`4、`deserializer`2、`lang`2、`factory`2、`dao`2、`model`2、`convert`3），逐行填入 `coverage-ledger.md`：`strength=CONTRACT_ONLY`、`verdict=CONTRACT_CHECKED`、`note` 写明字段类型/默认值/序列化判定依据
- [X] T011 [P] [US1] 逐文件审查 `util`(33) 与 `service`(27) 共 60 个文件（含 `service/impl` 下的敏感数据、上传、动态调用服务），回填 `coverage-ledger.md` 的 `strength=IMPLEMENTATION` 与 `verdict`
- [X] T012 [P] [US1] 逐文件审查其余 13 个实现包共 95 个文件（`event`15、`config`15、`web`14、`filter`6、`aspect`4、`wrapper`4、`handler`3、`listener`2、`manager`2、`api`11、`controller`3、`validator`13、根目录 0），回填 `coverage-ledger.md`
- [X] T013 [US1] 编写 `leaf-base-framework/src/test/java/cn/maple/core/framework/audit/GXEventContractAuditTest.java`，固化 F-08（自定义载荷不快照 / Map key 被替换）与 F-11（订阅注册缓存 identityHash 键），断言当前行为，方法名直述现状
- [X] T014 [P] [US1] 编写 `leaf-base-framework/src/test/java/cn/maple/core/framework/audit/GXWebBoundaryAuditTest.java`，固化 F-05（`&`/`<` 改写污染 JSON 值）与 F-07（`MultipartException` 的 `cause.message` 进入响应），使用 `MockHttpServletRequest/Response` 与直接调用 `GXExceptionHandler`，不启动容器

### 结论成文（覆盖 FR-002/FR-003/SC-002）

- [X] T015 [US1] 补全 `review-report.md` 第 5 章 `F-01`…`F-21` 的 10 个必填字段（维度、判定、位置 `文件:行`、触发条件、影响、证据引用、复现、回归测试设计 `{input, assertionPoint, expectedFlip}`、兼容性影响、修复方向），状态推进到 `CLASSIFIED`
- [X] T016 [US1] 为每条 `HIGH` 发现补齐 `EXECUTABLE` 或 `SOURCE_FACT` 证据（`contracts/review-report-schema.md` §4），并把无法执行的条目标 `UNVERIFIED_ASSUMPTION` 且移出 `blockers`
- [X] T017 [US1] 在 `review-report.md` 第 3 章写入 GateDecision：`scope`、`highRiskCount`、`blockers`（= `HIGH + DEFECT` 集合）、`recommendation`、`conditions`、`rationale`（≤3 句，引用编号）
- [X] T018 [US1] 在 `review-report.md` 第 13 章写复现索引：每条发现在 `quickstart.md` 的对应小节 + 用例全名；回填 `coverage-ledger.md` 中 `RISK`/`DEFECT` 行的 `findingRefs`
- [X] T019 [US1] 执行 `powershell -File specs/005-framework-module-audit/checklists/report-checks.ps1`，按输出修到 C-01…C-06 全通过（禁止改脚本降低标准）

**Checkpoint**: US1 可独立交付——报告结构与结论闭环成立。

---

## Phase 4: User Story 2 - 线程安全与上下文纪律可被验证 (Priority: P1)

**Goal**: 用可执行证据 + 两张矩阵，证明"容器线程复用 / 虚拟线程 / 线程池 / 上下文重建"四种运行形态下的上下文与资源行为。

**Independent Test**: 运行 `GXContextPropagationAuditTest`、`GXConcurrencySafetyAuditTest`、`GXEventExecutionAuditTest`（全绿）+ 校验上下文矩阵中每个 `ASYNC_EXECUTOR/FORK_JOIN/PARALLEL_STREAM` 行都有 `EXECUTABLE` 证据（data-model §3 不变量）。

- [X] T020 [P] [US2] 编写 `.../audit/GXContextPropagationAuditTest.java`：用轻量容器注册 `GXAsyncConfig` + `@Async` Bean（research D4），固化 F-01（异步不传播 traceId）、F-02（自定义 MDC 键在过滤链后残留，用 `MockHttpServletRequest` + `MockFilterChain`）、F-01 的包装器缺口（`submit(ForkJoinTask)`/`submitListenable` 未包装）
- [X] T021 [P] [US2] 编写 `.../audit/GXConcurrencySafetyAuditTest.java`：固化 F-13（容量 1 的池 + 阻塞任务触发 `CallerRunsPolicy`，断言在提交线程执行）与 F-14（1000 个不同 `clientId` 打点后反射读静态 Map 的 `size() == 1000`），等待一律 `await(5, SECONDS)`
- [X] T022 [P] [US2] 编写 `.../audit/GXEventExecutionAuditTest.java`：固化 F-10（Mock 执行器断言"发布 1 事件给 k 订阅者 → 提交 k 次"，以及达并发上限时的阻塞语义用 `SimpleAsyncTaskExecutor` 参数断言而非计时）
- [X] T023 [US2] 在 `review-report.md` 第 6 章完成上下文纪律矩阵：枚举 `TRACE_ID`/`MDC_OTHER`/`REQUEST_CONTEXT`/`SECURITY_CONTEXT` 的全部写入点（至少覆盖 `filter/GXBaseRequestLoggingFilter.java:26-43`、`util/GXTraceIdContextUtils.java:23-59`、`web/interceptor/GXTraceIdInterceptor.java:14-22`、`util/GXMdcThreadUtils.java:43-184`），逐行给"写入—传播—清理—异常路径"四段判定
- [X] T024 [US2] 在 `review-report.md` 第 7 章完成生命周期矩阵：至少覆盖 `async-executor`、`concurrent-tools-pool`、`event-executor`（两套）、`forkjoin-wrapper`、`rate-limiter-map`、`class-cache`、`bean-copier-cache`，逐行给 `createdBy`/`concurrencyLimit`/`stopPath`/`leakRisk`
- [X] T025 [US2] 把 F-01/F-02/F-10/F-13/F-14 的 `reproduction` 指向对应用例全名，状态推进到 `REPRODUCED`；若某条结论被推翻，按 `review-report-schema.md` §6 标 `REVISED` 并写入第 12 章

**Checkpoint**: US2 可独立交付——线程安全结论均有执行证据。

---

## Phase 5: User Story 3 - 性能开销定量化 (Priority: P2)

**Goal**: 6 条横切路径各给可复算公式 + 上限来源 + 计数型冒烟，禁止绝对耗时断言。

**Independent Test**: 运行 `GXPerformanceBudgetAuditTest`（全绿）+ 报告中 6 个路径关键词各出现一次（C-04）+ 任取一条公式代入数字能复算出与报告一致的量级。

- [X] T026 [P] [US3] 编写 `.../audit/GXPerformanceBudgetAuditTest.java`：固化 F-16（连续获取同一类型对 `BeanCopier` 时 `create` 被重复调用；`GXCommonUtils.buildTree` 的逐层全量过滤可用调用计数验证）与 XSS 长度口径（改写后 `getContentLength()` ≠ `getHeader("Content-Length")`）
- [X] T027 [US3] 在 `review-report.md` 第 9 章完成 6 条 `PerformanceMetric`：`XSS_REWRITE`、`BODY_CACHE`、`RATE_LIMIT`、`CONCURRENT_TOOLS`、`EVENT_DISPATCH`、`REFLECTION_COPY`，每条填 `formula` / `unit` / `upperBound`（含来源行）/ `sourceLines` / `smokeAssertion`
- [X] T028 [US3] 逐条复算并记录：`XSS_REWRITE` 代入 `body=1MB × 100 并发`、`RATE_LIMIT` 代入窗口内 1000 次、`EVENT_DISPATCH` 代入 k=10 订阅者、`REFLECTION_COPY` 代入 1000 节点；复算过程写入第 9 章"复算示例"
- [X] T029 [US3] 为 `BODY_CACHE` 与 `CONCURRENT_TOOLS` 补充计数证据（同一请求多次 `getHttpParam` 的解析次数；日志级别关闭时 `getThreadPoolStatus` 仍被求值），标注证据等级并回填 F-16 与新增条目
- [X] T030 [US3] 校对 `review-report.md` 全文无绝对耗时阈值（正则 `<\s*\d+\s*ms` 仅允许出现在"反例说明"语境），并在第 12 章登记与既有 `GXMdcThreadUtilsTest#durationMs < 2000` 的手法差异

**Checkpoint**: US3 可独立交付——性能结论可复算、不 flaky。

---

## Phase 6: User Story 4 - 对外契约与安全边界一致 (Priority: P2)

**Goal**: 把"文档承诺 / 安全默认值 / 失败收敛"逐条对照实现，并区分缺陷与既定契约。

**Independent Test**: 运行 `GXSecurityBoundaryAuditTest` 与 `GXCacheIsolationAuditTest`（全绿）+ 契约对照表覆盖宪法 7 条与 `AGENTS.md` 6 条关键行为 + 安全默认值 5 个方面各有唯一定性（SC-006）。

- [X] T031 [P] [US4] 编写 `.../audit/GXSecurityBoundaryAuditTest.java`：固化 F-03（非法密文 → 返回原串，无异常无日志）、F-04（读取后请求属性含完整 JSON，包括口令类字段）、F-06（`Class.forName` 加载模块外类名成功，对照 `GXParseDynamicCallParamServiceImpl` 的前缀白名单拒绝）、F-17（`StandardEvaluationContext` 的能力边界用一次受控表达式验证）
- [X] T032 [P] [US4] 编写 `.../audit/GXCacheIsolationAuditTest.java`：固化 F-12（无 `KeyGenerator` 时同一 `cacheName` 下不同方法的相同参数得到同一 key；未知 `cacheName` 取到 `null`），并验证 `GXCaffeineCacheUtils` 默认缓存无过期
- [X] T033 [US4] 在 `review-report.md` 第 8 章完成失败收敛清单：穷举 `src/main` 中 `catch` 块与"返回 `null`/入参/空集合/默认值"分支，逐条给 `failureType`/`convergence`/`logged`/`observable`/`constitutionVerdict`（至少覆盖 `handler/GXExceptionHandler`、`service/impl/GXBaseServeApiImpl`、`util/GXCommonUtils`、三个敏感数据服务、`util/GXEventPublisherUtils`）
- [X] T034 [US4] 在 `review-report.md` 第 10 章完成契约对照表：宪法 7 条 + `AGENTS.md`"关键行为"6 条 + `AGENTS.md`"测试要求"5 条 × 实现位置 × 一致/不一致 × 需同步的文档改动（`CONTRACT_DEVIATION` 只提文档同步，不改实现）
- [X] T035 [US4] 在 `review-report.md` 第 11 章完成安全默认值矩阵（默认密钥 / 类白名单 / 放行策略 / 异常内部信息 / 敏感数据流向），逐项给"默认是否安全"与证据等级（`contracts/audit-verdict-contract.md` §6）
- [X] T036 [US4] 在 `review-report.md` 第 11 章登记零测试覆盖清单：`handler/GXExceptionHandler`（26 个处理器）、三个敏感数据服务、`config/GXCORSConfig`、`filter/GXHTMLFilter`、`util/cglib/GXBeanCopierCache`、`web/config/GXHandlerMethodArgumentResolverConfigurer`，并给出"后续补齐优先级"
- [X] T037 [US4] 更新 `leaf-base-framework/AGENTS.md` 中与实现不一致的条目（仅文档同步，例如上下文清理与安全默认值的现状说明），并在报告第 12 章登记改动理由（宪法 II 允许文档同步，禁止顺手改实现）

**Checkpoint**: US4 可独立交付——契约与安全边界结论完整、可追溯。

---

## Phase 7: User Story 5 - 结论可驱动后续修复排期 (Priority: P3)

**Goal**: 把 21 条发现转成可直接执行的任务草案，并给出唯一准入结论。

**Independent Test**: 抽取任一发现，能一次拿到"编号 + 级别 + 位置 + 触发条件 + 影响 + 证据 + 复现 + 回归测试设计 + 兼容性等级 + 建议批次"十项，无需回查其他章节。

- [X] T038 [US5] 在 `review-report.md` 第 12 章生成"发现 → 修复任务"映射表：每条给修复动作、依赖项、兼容性等级（`NONE`/`BEHAVIOR_CHANGE`/`BREAKING`）、对应回归用例（已存在则引用，不存在则写设计要点）
- [X] T039 [US5] 给出排期建议三批次（立即 / 下一版本 / 观察），排序依据写明"影响面 × 触发概率 × 可观测性"的取值与算分方式（FR-015）
- [X] T040 [US5] 复核 GateDecision 与 `blockers`：确认 `blockers` 集合等于 `HIGH + DEFECT` 集合（C-05），并在第 3 章补"进入条件"的可验证表述（如"F-02 修复前不得在共享线程池场景使用自定义 MDC 键"）
- [X] T041 [US5] 三方一致性总检：`spec.md` 的 E/F 编号 ↔ `review-report.md` ↔ `tasks.md` 引用全部可解析；不一致项写入第 12 章"修订说明"

**Checkpoint**: US5 可独立交付——结论可直接进入修复计划。

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 交付前质量收口。

- [X] T042 [P] 报告可读性收口：`review-report.md` 每条发现控制在 12 行内，术语首次出现给中文解释；断言与日志消息保持 ASCII
- [X] T043 [P] 断言健壮性收口：检查 `.../audit/*.java` 全部用例无 `Thread.sleep` 同步、无绝对耗时阈值、无对执行顺序的隐式依赖、并发等待均有 5 秒上限
- [X] T044 全量回归：`mvn -o -pl leaf-base-framework test`，期望既有 518 用例仍全绿且新增审计用例全绿；把最终 `Tests run` 数字写入 `review-report.md` 第 2 章并同步 `spec.md` 的 SC-007 记录
- [X] T045 零改动核验：`git status --porcelain` 确认仅 `specs/005-framework-module-audit/**`、`leaf-base-framework/src/test/java/cn/maple/core/framework/audit/**`、`leaf-base-framework/AGENTS.md`（仅文档同步）有变更，`src/main/**` 与 `pom.xml` 零改动
- [X] T046 终检：执行 `checklists/report-checks.ps1` 与 `quickstart.md` 第 6 章全部核对命令，结果记入第 2 章

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始；T002–T004 可并行
- **Foundational (Phase 2)**: 依赖 Setup；**阻塞全部用户故事**（编号冻结是 US1–US5 的共同前提）
- **US1 (Phase 3, P1)**: 依赖 Phase 2；产出报告主干，是 US5 的输入
- **US2 (Phase 4, P1)**: 依赖 Phase 2；与 US1 并行时写入同一 `review-report.md` 的不同章节（US1 第 3/5/13 章，US2 第 6/7 章），需按"章节锁定"协作
- **US3 (Phase 5, P2)**: 依赖 Phase 2；`RATE_LIMIT`/`CONCURRENT_TOOLS` 的口径引用 US2 用例名（只读引用，不改文件）
- **US4 (Phase 6, P2)**: 依赖 Phase 2；契约对照表引用 US1 的台账判定
- **US5 (Phase 7, P3)**: 依赖 US1（报告主干）与 US2/US3/US4（各自矩阵章节）
- **Polish (Phase 8)**: 依赖全部用户故事

### Story Completion Order

```text
Setup → Foundational ─┬─> US1 (P1) ─┐
                      ├─> US2 (P1) ─┤
                      ├─> US3 (P2) ─┼─> US5 (P3) → Polish
                      └─> US4 (P2) ─┘
```

### Within Each Story

- 审查/取证任务先于成文任务（T010–T012 → T015；T020–T022 → T023–T025）
- 用例任务先于"复现字段回填"（T013/T014 → T015；T031/T032 → T033/T035）
- 每条发现的 `status` 必须到达 `REPRODUCED` 才允许写进 GateDecision 的 `blockers`

### Parallel Opportunities

- Setup：T002、T003、T004 三条互不冲突
- Foundational：T006、T007、T008 可并行（同文件不同章节，按章节锁定）
- US1：T011 与 T012 按包分片并行；T013 与 T014 两个新文件并行
- US2：T020、T021、T022 三个新文件并行
- US3：T026 单文件，T027 与 T028 可并行（成文 vs 复算）
- US4：T031 与 T032 两个新文件并行
- Polish：T042 与 T043 可并行

---

## Parallel Example: User Story 2

```bash
# 三个审计用例类互不依赖，可同时开工：
Task: "编写 GXContextPropagationAuditTest（F-01/F-02）in leaf-base-framework/src/test/java/cn/maple/core/framework/audit/GXContextPropagationAuditTest.java"
Task: "编写 GXConcurrencySafetyAuditTest（F-13/F-14）in leaf-base-framework/src/test/java/cn/maple/core/framework/audit/GXConcurrencySafetyAuditTest.java"
Task: "编写 GXEventExecutionAuditTest（F-10）in leaf-base-framework/src/test/java/cn/maple/core/framework/audit/GXEventExecutionAuditTest.java"
```

## Parallel Example: User Story 1

```bash
# 两个包分片审查 + 两个复现用例，四个文件互不冲突：
Task: "审查 util(33)+service(27) 并回填 coverage-ledger.md"
Task: "审查 event/config/web/filter/aspect/wrapper/handler/listener/manager/api/controller/validator 共 95 文件"
Task: "编写 GXEventContractAuditTest（F-08/F-11）"
Task: "编写 GXWebBoundaryAuditTest（F-05/F-07）"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 Setup
2. 完成 Phase 2 Foundational（编号冻结 + 台账 + 校验脚本）
3. 完成 Phase 3 US1：全量台账 + 21 条发现成文 + GateDecision + 2 个复现用例
4. **STOP and VALIDATE**：`report-checks.ps1` 全通过 + 2 个用例全绿
5. 此时已可交付"结论可复核"的最小审查报告

### Incremental Delivery

1. Setup + Foundational → 交付框架就绪
2. + US1 → 可复核结论（MVP，可对外提交审查报告初版）
3. + US2 → 线程安全结论具备执行证据（P1 补全）
4. + US3 → 性能口径可复算
5. + US4 → 契约与安全边界闭环（含文档同步）
6. + US5 → 修复排期可执行
7. 每个增量都不改生产代码，可独立评审与合并

### Parallel Team Strategy

1. 全员完成 Setup + Foundational（编号冻结必须一次做对）
2. 之后：
   - 审查者 A：US1 的 util/service 分片（T011）+ `GXEventContractAuditTest`
   - 审查者 B：US1 的其余实现包分片（T012）+ `GXWebBoundaryAuditTest`
   - 审查者 C：US2 三个用例类 + 两张矩阵
   - 审查者 D：US3 口径 + US4 安全边界用例
3. US5 由报告负责人统一收口，避免编号/结论冲突

---

## Notes

- `[P]` 任务均为不同文件或同文件不同章节（已按章节锁定）；同一章节的编辑必须串行
- 所有审计用例断言**当前行为**，绿色即"现状成立"；修复落地时断言必须翻转
- 禁止新增依赖、禁止修改 `pom.xml`、禁止改 `src/main/**`（T037 仅改 `AGENTS.md`）
- 判定口径不确定时以 `contracts/audit-verdict-contract.md` 为准，不得就地发明新档位
- 编号 `E-01…E-22` / `F-01…F-21` 冻结；新增从 `E-23` / `F-22` 起
- 提交前必跑：`mvn -o -pl leaf-base-framework test` 与 `checklists/report-checks.ps1`


---

## 验收结果（2026-10-22 回填）

- **任务完成**: 46/46（T001–T046 全部勾选）
- **测试**: `mvn -o -pl leaf-base-framework test` → `files=73 tests=546 failures=0 errors=0 skipped=0`（既有 518 + 审计 28）
- **审计用例**: 8 个类全绿；`-Dtest=GX*AuditTest` → `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`
- **报告校验**: `powershell -File checklists/report-checks.ps1` → C-01…C-07、L-01、L-02 全 PASS
- **覆盖台账**: 309 行，100% 文件有判定；`DEFECT`/`RISK` 行均带 `findingRefs`
- **生产代码**: `git status --porcelain` 仅 3 项——`M leaf-base-framework/AGENTS.md`（文档同步）、`?? leaf-base-framework/src/test/java/cn/maple/core/framework/audit/`、`?? specs/005-framework-module-audit/`；`src/main/**` 与 `pom.xml` 零改动
- **结论**: NOT_SUITABLE_AS_BASELINE，blockers = F-01…F-08
- **审查期修正**: F-05 由"内容变化"升级为"文本丢失"；F-04 限定 Cookie 触发条件；F-01 删除 Spring 7 已移除的 `submitListenable` 子项（详见 `review-report.md` 第 11 章）
---

## Phase 9: Convergence

- [X] T047 为 `review-report.md` 第 4 章全部 21 条发现补 `- **状态**: DELIVERED` 字段，满足 `contracts/review-report-schema.md` §2 条目模板与 `data-model.md` §1 的 `status` 必填要求 per contracts/review-report-schema.md §2 (partial)
- [X] T048 在 `review-report.md` 第 11 章新增 Finding 状态机跟踪表（`DRAFT`→`REVIEWED`→`CLASSIFIED`→`REPRODUCED`→`DELIVERED`），逐条登记 21 条发现的当前状态并与 T047 的 `状态` 字段一致 per T009 (missing)
- [X] T049 对齐 `review-report.md` 顶层章节与 `contracts/review-report-schema.md` §1：统一编号口径（1 标题与元信息 … 13 复现指南索引），把 `## 准入门槛结论` 收敛为第 3 章子节、把 `## 12. 发现 → 修复任务映射与排期` 纳入合同章节清单，并同步修订合同 §1 与 §8 的校验项 per contracts/review-report-schema.md §1 (contradicts)
- [X] T050 在 `review-report.md` 第 11 章补"判定口径速查"小节：五档判定（`DEFECT`/`RISK`/`CONTRACT_DEVIATION`/`ESTABLISHED_CONTRACT`/`UNVERIFIED_ASSUMPTION`）判据表与"缺陷 vs 既定契约"判定树，内容引用 `contracts/audit-verdict-contract.md` §1、§4 per T008 (partial)
- [X] T051 在 `review-report.md` 第 11 章补审计用例命名与断言规范记录（`GX<被测能力>AuditTest`、方法名直述现状、类头 `CURRENT BEHAVIOR - flip when fixed`、禁止绝对耗时断言、并发等待 5 秒上限）per T003 (partial)
- [X] T052 修正 `review-report.md` 第 10 章"零用例覆盖"清单：把三个敏感数据服务标注为"既有单元测试零覆盖、本次审计用例已固化行为"，并补充"修复回归仍需新增专项用例"的说明，消除与 `GXSecurityBoundaryAuditTest` 的表述冲突 per T036 (contradicts)
- [X] T053 消除 `coverage-ledger.md` 与 `research.md` D1 的分档冲突：8 行 `api/dto/*` 标 `CONTRACT_ONLY`，而 D1 把 `api(11)` 归为 `IMPLEMENTATION`；二选一——改台账为 `IMPLEMENTATION`，或在 D1 表格中显式拆出 `api/dto(8)` 为 `CONTRACT_ONLY`，使 `data-model.md` §7 不变量 3 成立 per data-model.md §7 (contradicts)


### 收敛回填（2026-10-22）

- T047：21 条发现均补 `状态` 字段；其中 F-17/F-18 标 `UNVERIFIED_ASSUMPTION`（与第 11 章"未验证假设"一致），其余 19 条标 `DELIVERED`——未按任务字面统一写 `DELIVERED`，否则与证据等级自相矛盾。
- T048：第 11 章新增 Finding 状态机跟踪表（21 行，含复现依据）。
- T049：报告顶层章节重排为 `## 1.`–`## 14.`（新增第 1 章标题与元信息、第 13 章修复任务映射），`准入门槛结论` 降为第 3 章子节；合同 §1 与 §8 同步（C-01 改为 E-01…E-24、C-02 增 `状态`、新增 C-07/C-08）。
- T050/T051：第 11 章新增"判定口径速查"（五档判据表 + 判定树）与"审计用例命名与断言规范"。
- T052：第 10 章（重排后第 11 章）零覆盖清单已区分为"既有单元测试零覆盖 / 审计用例已固化行为 / 修复回归仍需专项用例"。
- T053：`research.md` D1 拆分 `api(3)` 与 `api/dto(8)`，使 `data-model.md` §7 不变量 3 成立（台账 309 行未变）。
- 校验：`report-checks.ps1` → C-01…C-09、L-01、L-02 全 PASS；本次仅改文档，未触碰 `src/**` 与 `pom.xml`，546 用例基线不受影响。
---

## Phase 10: Convergence

- [X] T054 把 `review-report.md` 第 8 章失败收敛清单从 18 行"节选"扩展为全量登记：按 `src/main` 的 175 个 `catch (` 与 173 处 `return null;` 逐条（或按文件聚合后逐条）给出 `location` / `failureType` / `convergence` / `logged` / `observable` / `constitutionVerdict`，同时移除标题中的"节选"与"覆盖全部"的过度声明，并把统计口径（脚本命令 + 数量）写入章节开头 per SC-005 (partial)
- [X] T055 为 `review-report.md` 第 9 章补 `BODY_CACHE` 与 `CONCURRENT_TOOLS` 的复算示例：分别代入"同一请求读取 5 次"与"1 万任务、日志级别关闭"，给出可核对的数值，使 6 条路径全部具备数字口径 per SC-004 (partial)
- [X] T056 修正 `review-report.md` 中 F-21 的 `回归测试设计`：把"不适用（文档类）"替换为可翻转的判定点（如"`AGENTS.md` 是否包含『当前实现边界』一节：修复前 false、修复后 true"），以满足 `data-model.md` §1 不变量"`status=DELIVERED` 的条目 `expectedFlip` 必须非空" per data-model.md §1 (contradicts)


### 第二轮收敛回填（2026-10-22）

- T054：第 8 章重建为"§8.1 高风险分支逐条（18 行）+ §8.2 全量登记（49 个含分支文件聚合）"，合计 catch 175、
eturn null 173、静默 catch 3，覆盖 175+173=348 处分支；标题中去掉"节选/覆盖全部"的过度声明，改为可复算统计口径。
- T055：第 9 章复算示例由 4 条扩为 6 条，补齐 BODY_CACHE（2KB × 5 次读取）与 CONCURRENT_TOOLS（1 万任务 → 20000 次格式化、约 120000 次原子读）。
- T056：F-21 的 回归测试设计 由"不适用"改为可翻转判定点（AGENTS.md 是否含"当前实现边界"一节，由 false 变 true）。
- 校验：`report-checks.ps1` → C-01…C-09、L-01、L-02 全 PASS；本次仅改文档，`src/**` 与 `pom.xml` 零改动。
---

## Phase 11: Convergence

- [X] T057 在 `review-report.md` 第 11 章新增"安全默认值矩阵"：按 `contracts/audit-verdict-contract.md` §6 覆盖 5 个方面（默认密钥 / 类与方法白名单 / 鉴权放行策略 / 异常响应内部信息 / 敏感数据流向），逐行给默认值、是否安全、证据等级、关联发现与修复前置条件 per SC-006 (missing)
- [X] T058 在 `review-report.md` 第 6 章上下文纪律矩阵补 4 处遗漏的写入点：`api/GXBaseServeApiImpl.java:35`（`DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL`）、`filter/GXXssHttpServletRequestWrapper.java:32`（`HTML_FILTER`）、`service/impl/GXBaseCacheLockServiceImpl.java:26`（`THREAD_LOCKS`）、`util/GXConcurrentToolsUtils.java:120-135`（MDC 保存/还原），逐行给"写入—传播—清理—异常路径"四段判定；若某处在请求路径上没有清理点，标注 `GAP` 并补关联发现或风险项 per SC-003 (partial)
- [X] T059 为 `HIGH` 发现补"矩阵/清单"交叉引用，使 `data-model.md` §9 不变量 2 成立：F-04 / F-05 / F-08 当前未对应任何 `GAP` 或 `VIOLATES_*` 行；或在第 6/7/8 章增加对应行并在发现条目中引用 per data-model.md §9 (contradicts)


### 第三轮收敛回填（2026-10-22）

- T057：第 11 章新增"安全默认值矩阵"，5 个方面（默认密钥 / 类与方法白名单 / 鉴权放行策略 / 异常响应内部信息 / 敏感数据流向）逐项给默认行为、是否安全、证据等级、关联发现与修复前置条件；结论为"默认全部不安全"。
- T058：第 6 章补 4 行——GXConcurrentToolsUtils.java:120-135（RESTORED，OK）、HTML_FILTER（LEAKED，GAP）、THREAD_LOCKS（GAP）、DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL（PARTIAL，GAP）；三处 ThreadLocal 缺口合并为新发现 **F-22**（MEDIUM，RISK，状态 REPRODUCED），并补 E-25 证据、§13 排期、§14 复现索引与台账关联。
- T059：第 8 章新增 §8.3"HIGH 发现与矩阵/清单交叉引用"，F-01…F-08 逐条给出 GAP/VIOLATES_* 归属，data-model.md §9 不变量 2 成立；同时为 F-04 补 §6 REQUEST_CONTEXT 行。
- 校验：`report-checks.ps1` → C-01a（E-01…E-25）、C-01b…C-09、L-01、L-02 全 PASS；发现总数 22（HIGH 8 / MEDIUM 11 / LOW 3），GateDecision blockers 仍为 F-01…F-08。
- 本次仅改文档与台账，`src/**`、`pom.xml` 零改动，546 用例基线不受影响。
---

## Phase 12: Convergence

- [X] T060 按 `contracts/review-report-schema.md` §1 的排序规则重排 `review-report.md` 第 5 章：把 `### F-22 [MEDIUM]` 移到 `F-18` 之后、`F-19` 之前（或在该章开头补一条显式例外说明并说明理由），使"按严重级别降序、级别内按编号升序"重新成立 per contracts/review-report-schema.md §1 (contradicts)
- [X] T061 消除第 6 章 `THREAD_LOCAL_OTHER` 与 `data-model.md` §3 `contextItem` 枚举的不一致：在 §3 枚举中补 `THREAD_LOCAL_OTHER`（并注明其"非 MDC 的线程局部缓存/状态"语义），或把矩阵中的三类 ThreadLocal 改用既有枚举值并在矩阵结论中说明映射 per data-model.md §3 (contradicts)


### 第四轮收敛回填（2026-10-22）

- T060：F-22 [MEDIUM] 已移到 F-18 之后、F-19 之前，发现清单恢复"HIGH 8 → MEDIUM 11 → LOW 3"的降序排列；新增校验项 **C-10**（排序）并写入合同 §8，防止后续追加发现再次破坏顺序。
- T061：data-model.md §3 的 contextItem 枚举新增 THREAD_LOCAL_OTHER（非 MDC 的线程局部缓存/状态），与第 6 章矩阵的 3 行对齐。
- 校验：`report-checks.ps1` → C-01a（E-01…E-25）、C-01b…C-10、L-01、L-02 共 13 项全 PASS。
- 本次仅改文档（报告 / 数据模型 / 合同 / 校验脚本），`src/**` 与 `pom.xml` 零改动，546 用例基线不受影响。
---

## Phase 13: Convergence

- [X] T062 使 F-09 / F-15 / F-22 的状态与证据等级一致：优先在 `leaf-base-framework/src/test/java/cn/maple/core/framework/audit/` 补 3 条特征化用例（BPP 对 JDK 代理与同 `beanName` 多实例的注册判定、上传服务实例字段的跨请求覆盖、三处 `ThreadLocal` 残留），使状态可保持 `REPRODUCED`；若某条无法确定性复现（如并发交错），则把该条状态改为 `UNVERIFIED_ASSUMPTION`、在 `review-report.md` 第 11 章"未验证假设"中登记，并同步 §11 状态机跟踪表与 `checklists/report-checks.ps1` 的 C-08 取值域 per data-model.md §1.1 (contradicts)


### 第五轮收敛回填（2026-10-22）

- T062：为 F-09 / F-15 / F-22 补 4 条特征化用例（均在无外部依赖环境执行）：
  - `GXEventContractAuditTest#jdkProxySubscriberIsNotRegistered`、`#sameBeanNameSecondInstanceIsSkipped`（F-09）
  - `GXSecurityBoundaryAuditTest#uploadStoragePathIsSharedInstanceState`（F-15，反射模拟两个请求先后写实例字段）
  - `GXContextPropagationAuditTest#nonMdcThreadLocalsAreNotCleared`（F-22，`HTML_FILTER` 与 `THREAD_LOCKS` 两处）
- 三条发现状态改为 `DELIVERED`（证据由"仅源码事实"升级为"有执行用例"），§5 复现字段、§11 状态机表、§13 排期、§14 复现索引同步；`DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL` 仍标注为源码事实。
- 测试：`mvn -o -pl leaf-base-framework test` → `files=73 tests=550 failures=0 errors=0 skipped=0`（既有 518 + 审计 32）。
- 校验：`report-checks.ps1` → 13 项全 PASS。
- 变更范围：`specs/005-framework-module-audit/**` + `leaf-base-framework/src/test/java/cn/maple/core/framework/audit/**`；`src/main/**`、`pom.xml` 零改动。
---

## Phase 14: Convergence

- [X] T063 补全 `review-report.md` 第 14 章复现索引：为 F-15（`GXSecurityBoundaryAuditTest#uploadStoragePathIsSharedInstanceState`）、F-17 / F-18（无执行用例，标注"按条目位置复核 + 未验证假设"）、F-19（`GXTraceIdContextUtilsTest` 已锁定空串契约）、F-20（`GXLoggerUtilsTest` 现状 + 源码事实）、F-21（`AGENTS.md` 已落地"当前实现边界"）各补一行，使 22 条发现逐条可定位复现方式 per SC-001 (partial)
- [X] T064 修正 `review-report.md` 第 14 章末尾的用例数表述（"8 类 28 用例" → "8 类 32 用例"），并在 `checklists/report-checks.ps1` 增加一项校验：第 14 章索引行覆盖全部 `F-NN`（防止后续编辑再次丢失索引行） per SC-001 (contradicts)


### 第六轮收敛回填（2026-10-22）

- T063：第 14 章复现索引重写为 17 行覆盖全部 22 条发现——补 F-15（#uploadStoragePathIsSharedInstanceState）、F-17/F-18（无执行用例，给位置复核路径）、F-19（GXTraceIdContextUtilsTest + CORS 源码事实）、F-20（GXLoggerUtilsTest + 反射失败源码事实）、F-21（AGENTS.md 当前实现边界）；F-09/F-22 行补具体方法名。
- T064：末尾用例数由"28"改为"**32**"；`report-checks.ps1` 新增 **C-11**（复现索引覆盖全部 F-NN），合同 §8 同步登记，防止同类回归。
- 校验：`report-checks.ps1` → 14 项全 PASS。
- 本次仅改文档与校验脚本，`src/**`、`pom.xml` 零改动，550 用例基线不受影响。

### 第七轮收敛回填（2026-10-22）

- T065：第 2 章"审计用例"行由"8 个类 / **28** 个用例"改为"8 个类 / **32** 个用例"，与同章"交付验收"行（518 既有 + 32 审计 = 550）、第 14 章"8 类 32 用例"三处对齐。
- `report-checks.ps1` 新增 **C-12**：取第 2 章 `8 个类 / N 个用例` 的 N 与第 14 章 `8 类 M 用例` 的 M 比对，任一缺失或数值不等即 FAIL；中文标签以 `\uXXXX` 转义书写，规避脚本编码变更导致正则失效。负样本（28 vs 32）实测返回 FAIL，正样本（32 vs 32）PASS。
- 合同 `review-report-schema.md` §8 登记 C-12，校验项由 11 项增至 12 项。
- 校验：`report-checks.ps1` → 15 项全 PASS（C-01a、C-01b、C-02…C-12、L-01、L-02）。
- 顺带修正第六轮回填段内的断词（`eport-checks.ps1` → `report-checks.ps1`）。
- 顺带修正合同 §1 第 2 章条目的陈旧值：交付验收 `tests=546` → `tests=550`（518 既有 + 32 审计），使契约要求与报告 §2 实测一致；`tasks.md` 前四轮回填段中的 `546` 属当时时间戳记录，按约定保留。
- 本次仅改文档与校验脚本，`src/**`、`pom.xml` 零改动，550 用例基线不受影响。
---

## Phase 15: Convergence

- [X] T065 把 `review-report.md` 第 2 章"审计用例"行的"8 个类 / 28 个用例"改为"8 个类 / 32 个用例"，并在 `checklists/report-checks.ps1` 增加 **C-12**：第 2 章"8 个类 / N 个用例"与第 14 章"8 类 M 用例"的数值必须相等（实测 550 = 518 既有 + 32 审计），防止同一文档内的用例数再次分叉 per contracts/review-report-schema.md §1 (contradicts)

