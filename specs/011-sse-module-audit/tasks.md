---
description: "Task list for leaf-base-sse 正确性、线程安全与性能审查"
---

# Tasks: leaf-base-sse 正确性、线程安全与性能审查

**Input**: Design documents from `/specs/011-sse-module-audit/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 本特性**强制测试**（`spec.md` FR-020 ~ FR-023、`leaf-base-sse/AGENTS.md` §4）。每个修复项必须先写"旧代码上失败"的用例，再实现。

**Organization**: 按 `spec.md` 的 6 个用户故事分组，每个故事可独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与同批次其它任务并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1 ~ US6）
- 每个任务均给出精确文件路径

## Path Conventions

生产代码：`leaf-base-sse/src/main/java/cn/maple/sse/...`
测试代码：`leaf-base-sse/src/test/java/cn/maple/sse/...`
文档：`docs/leaf-base-sse开发手册.md`、`leaf-base-sse/AGENTS.md`
规格产物：`specs/011-sse-module-audit/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 固定基线与上游事实，保证后续所有结论可复核

- [x] T001 在 `specs/011-sse-module-audit/audit-report.md` 建立骨架并记录依赖基线（spring-webmvc 7.0.9、spring-boot 4.1.1、tomcat-embed 11.0.24、jspecify 1.0.1、JDK 25.0.4）与"`leaf-base-sse/pom.xml` 不改"的决定
- [x] T002 [P] 从 `F:/repository/org/springframework/spring-webmvc/7.0.9/spring-webmvc-7.0.9-sources.jar` 解压核对 R-01 ~ R-05 的行号与结论，写入 `specs/011-sse-module-audit/audit-report.md` 附录 A（上游语义摘录）
- [x] T003 [P] 运行 `mvn -o -pl leaf-base-sse test` 并记录基线（10 执行 / 0 失败）到 `specs/011-sse-module-audit/audit-report.md` 附录 B（测试基线）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 提供所有故事共用的"可测缝"与测试替身；不改变任何默认行为

**⚠️ CRITICAL**: 未完成前不得开始任何用户故事

- [x] T004 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 新增 `protected SseEmitter createEmitter(long timeoutMillis)` 作为唯一创建点，并把 `createSseConnect` 中的 `new SseEmitter(...)` 改为调用它（FR-023，行为不变）
- [x] T005 [P] 新增 `leaf-base-sse/src/test/java/cn/maple/sse/support/GXSseTestEmitters.java`：提供 4 个测试替身——`failing()`（`send(SseEventBuilder)` 恒抛 `IOException`）、`completed()`（恒抛 `IllegalStateException`）、`blocking(long ms)`（首投阻塞后抛 `IOException`）、`recording()`（记录已发送的 `SseEventBuilder` 序列）；全部继承 `SseEmitter` 并覆盖 `send(SseEventBuilder)`
- [x] T006 运行 `mvn -o -pl leaf-base-sse test` 确认 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplTest.java` 与 `leaf-base-sse/src/test/java/cn/maple/sse/service/GXSseEmitterServiceSpringBootTest.java` 的 10 条用例仍全绿（证明 T004 零行为变化）

**Checkpoint**: 创建点可注入、测试替身可用、基线未破。

---

## Phase 3: User Story 1 - 审查结论可复核 (Priority: P1) 🎯 MVP

**Goal**: 交付独立报告，且每条结论都能用一条可运行的复现用例或上游源码引用支撑。

**Independent Test**: 随机抽取报告中的任一 F，按其"回归设计"运行对应用例，观察当前（旧）代码的失败输出与报告一致。

### Tests（先写，必须在旧代码上失败）

- [x] T007 [P] [US1] 新增 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterAuditReproTest.java` 并实现 `f01RetryExhaustedConnectionIsNotClosed`（`failing()` 注入后发送 → 期望 4 次尝试 + 缓存为空 + 关闭后 `send` 抛 `IllegalStateException`；旧代码最后一断言失败）
- [x] T008 [P] [US1] 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterAuditReproTest.java` 实现 `f02FailedMessageIsOvertakenByNextMessage`（第 1 条失败、第 2 条成功 → 期望接收顺序 M1、M2；旧代码为 M2、M1）
- [x] T009 [P] [US1] 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterAuditReproTest.java` 实现 `f04ConcurrentReplaceReturnsEmitterInsteadOfException`（50 线程同 `clientId` → 期望零异常且缓存 1 条；旧代码抛 `GXBusinessException`）
- [x] T010 [US1] 运行 `mvn -o -pl leaf-base-sse test -Dtest=GXSseEmitterAuditReproTest`，把 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterAuditReproTest.java` 的失败输出贴入 `specs/011-sse-module-audit/audit-report.md` 的"故障现场"章节（证明 FR-021）

### Implementation（报告本体）

- [x] T011 [US1] 在 `specs/011-sse-module-audit/audit-report.md` 写入 E-01 ~ E-24 表（字段：`id`/`kind`/`location`/`quote`/`supports`，按 `specs/011-sse-module-audit/data-model.md` A-2 校验）
- [x] T012 [P] [US1] 在 `specs/011-sse-module-audit/audit-report.md` 写入 F-01 ~ F-23 八要素表（级别/维度/位置/触发/影响/证据/复现/回归设计，按 `specs/011-sse-module-audit/data-model.md` A-1 校验）
- [x] T013 [P] [US1] 在 `specs/011-sse-module-audit/audit-report.md` 写入四张矩阵：连接终止矩阵（5 类终止）、并发风险矩阵（4 个共享对象）、消息顺序契约（4 场景）、性能口径表（5 项）
- [x] T014 [P] [US1] 在 `specs/011-sse-module-audit/audit-report.md` 写入 P-01 ~ P-06 既定契约清单与索引（供 T045 复核）

**Checkpoint**: 报告八要素完整、3 条复现用例在旧代码上失败、结论可逐条定位。

---

## Phase 4: User Story 2 - 连接生命周期与失败处置可判定 (Priority: P1)

**Goal**: 任何"从缓存移除"的路径都同时结束连接；首帧失败与并发替换语义明确。

**Independent Test**: 让连接进入"重试耗尽 / 心跳失败 / 显式心跳失败 / 关闭 / 销毁"五条终止路径，逐条断言"缓存为空 + 再次 `send` 抛 `IllegalStateException`"。

### Tests（先写）

- [x] T015 [P] [US2] 新增 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplSendFailureTest.java` 并实现 `retryExhausted_ClosesConnectionAndRemovesFromCache`（4 次尝试、缓存空、关闭后可观测）
- [x] T016 [P] [US2] 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplSendFailureTest.java` 实现 `heartbeatFailure_ClosesConnection` 与 `sendHeartbeatFailure_ClosesConnection`
- [x] T017 [P] [US2] 新增 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplLifecycleTest.java` 并实现 `destroy_CancelsQueuedRetriesAndAwaitsTermination`、`createSseConnect_AfterDestroyThrowsBusinessException`、`destroy_IsIdempotent`
- [x] T018 [P] [US2] 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplSendFailureTest.java` 实现首帧两分语义用例：`initialSendFailure_WhenReplaced_ReturnsEmitterWithoutException`、`initialSendFailure_WhenStillCurrent_ThrowsBusinessException`

### Implementation

- [x] T019 [US2] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 新增 `closeAndRemove(String, SseEmitter, String)`：条件删除成功后才 `complete()`（`try/catch (Exception)` + DEBUG 日志），并把 `cleanupConnections` 可见性改为包私有以便测试
- [x] T020 [US2] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 把心跳失败、`sendHeartbeat` 失败、重试耗尽三条路径改为调用 `closeAndRemove`（同时预留 `drops`/`failures` 计数接入点，计数实现于 T035）
- [x] T021 [US2] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 实现 D-02 两分语义：捕获 `IllegalStateException` 且 `!isCurrentEmitter` → INFO 并返回 emitter；其余情况 → `closeAndRemove` + `GXBusinessException`
- [x] T022 [US2] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 实现 D-06 销毁语义：`@PostConstruct init()` 启动调度器与执行器、`destroy()` 置标记 → 关全部连接 → 清空管线 → `shutdownNow` → 两级 `awaitTermination(5s)`；销毁后 `createSseConnect` 抛 `GXBusinessException`、`send*` 静默降级
- [x] T023 [US2] 运行 `mvn -o -pl leaf-base-sse test`，确认 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplSendFailureTest.java` 与 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplLifecycleTest.java` 由红转绿、既有 10 条仍绿

**Checkpoint**: 五条终止路径全部"移除即关闭"，销毁语义可观测且幂等。

---

## Phase 5: User Story 3 - 并发与顺序安全可验证 (Priority: P1)

**Goal**: 同连接保序、跨连接并行、慢连接不拖垮全局。

**Independent Test**: 保序用例（M1 失败、M2 成功）+ 慢连接隔离用例 + 并发注册用例全部通过，且并发下无异常抛出。

### Tests（先写）

- [x] T024 [P] [US3] 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplSendFailureTest.java` 新增 `failedMessage_IsRetriedBeforeNextMessage`（接收顺序 M1、M2）与 `splitChunks_ShareMsgIdAndCarryChunkIndex`（同 `msgId` + 注释含 `chunk=1/3`）
- [x] T025 [P] [US3] 新增 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplConcurrencyTest.java` 并实现 `concurrentSameClientId_EndsWithSingleConnectionAndNoException`（50 线程）、`blockingConnection_DoesNotDelayOtherConnections`（一个连接阻塞 2s）、`broadcastWhileClosing_DoesNotThrow`（200 轮）

### Implementation

- [x] T026 [US3] 新增 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseClientSendPipeline.java`（包私有）：`ArrayDeque` 队列 + `AtomicBoolean draining` + `closed` 标记，按 `specs/011-sse-module-audit/data-model.md` B-3 实现 `enqueue/poll/close`，容量 256、超限丢弃队尾最旧
- [x] T027 [US3] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 接入 D-03：发送失败时入队并提交 drain；有 pending 时新消息入队尾；drain 内按 100/200/400ms 串行重试，`IllegalStateException` 立即丢弃
- [x] T028 [US3] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 实现 D-05 执行器拆分：`scheduler` 只做触发（每分钟提交心跳批次），`taskExecutor = Executors.newVirtualThreadPerTaskExecutor()` 承载心跳分片（`batchSize=256`）与 drain；广播不再在调用线程退避
- [x] T029 [US3] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 与 `leaf-base-sse/src/main/java/cn/maple/sse/dto/GXSseMessageInnerReqDto.java` 实现 D-09：同一原始消息的分片共享 `msgId`，`comment` 追加 `:chunk=<i>/<n>`
- [x] T030 [US3] 运行 `mvn -o -pl leaf-base-sse test`，确认 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplConcurrencyTest.java` 与 `GXSseEmitterServiceImplSendFailureTest.java` 由红转绿、US2 用例不回退

**Checkpoint**: 单连接有序、跨连接并行、并发注册无异常；最小可发布修复集（US1+US2+US3）可交付。

---

## Phase 6: User Story 4 - 性能开销定量化 (Priority: P2)

**Goal**: 六项指标可获取，且默认路径的固定开销可复算。

**Independent Test**: `getMetrics()` 的六项在"成功、失败、丢弃、重试、关闭"五种操作后按预期单调变化；默认路径 INFO 日志每连接 ≤ 2 条。

### Tests（先写）

- [x] T031 [P] [US4] 新增 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplMetricsTest.java` 并实现 `metricsTrackAttemptsAndSuccesses`、`metricsTrackRetriesAndDrops`、`metricsConnectionsReflectActiveCache`、`defaultGetMetricsReturnsZeroSnapshot`
- [x] T032 [P] [US4] 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplMetricsTest.java` 实现 `logLevelIsInfoOnlyForLifecycle`（由于模块使用 Log4j2 而非 Logback，改用 Log4j2 `AbstractAppender` 断言移除路径为 DEBUG）；"单条消息只使用一个 `msgId`" 由 T024 的 `splitChunks_ShareMsgIdAndCarryChunkIndex` 覆盖（DTO 的 `@Builder.Default msgId` 为兼容既有手工 builder 用法而保留）

### Implementation

- [x] T033 [US4] 新增 `leaf-base-sse/src/main/java/cn/maple/sse/service/GXSseMetrics.java`（`record`，6 字段，按 `specs/011-sse-module-audit/data-model.md` B-4）
- [x] T034 [US4] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/GXSseEmitterService.java` 新增 `default GXSseMetrics getMetrics()`（全 0 快照）并补 Javadoc
- [x] T035 [US4] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 用 6 个 `LongAdder` 实现计数并覆盖 `getMetrics()`，在 T020/T027 的失败与丢弃分支插桩
- [x] T036 [US4] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 按 `specs/011-sse-module-audit/contracts/sse-observability-contract.md` §2 调整日志级别（移除/清理/丢弃 → DEBUG，DEBUG 前加 `isDebugEnabled()` 守卫），去掉每分片 DEBUG 的字符串拼接开销

**Checkpoint**: 六项指标可读、日志级别符合契约、无 INFO 级高频日志。

---

## Phase 7: User Story 5 - 对外契约与文档一致 (Priority: P3)

**Goal**: 空值契约显式化、`splitMessage` 语义统一、手册与 `AGENTS.md` 与实现一致。

**Independent Test**: `mvn -o -pl leaf-base-sse test` 的 NullAway 零错误；`rg` 检查手册中不存在 `sendHeartbeatToAll|SSE_CLIENT_CACHE|removeUser|1KB`；`splitMessage(" ")` 返回空列表。

### Tests（先写）

- [x] T037 [P] [US5] 新增 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplSplitMessageTest.java` 并实现 `blankMessage_ReturnsEmptyList`、`lengthBoundaries_ClampedTo1To100`（`-1/0/1/100/101`）、`surrogatePairs_AreNeverBroken`、`nullMessage_ReturnsEmptyList`
- [x] T038 [P] [US5] 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplTest.java` 新增 `messageWithNullData_IsDroppedWithoutThrowing`、`getSseEmitterByClientId_NullClientIdReturnsNull`、`closeConnections_NullPredicateReturnsZero`

### Implementation

- [x] T039 [US5] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/GXSseEmitterService.java`、`leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java`、`leaf-base-sse/src/main/java/cn/maple/sse/dto/GXSseMessageInnerReqDto.java` 按 D-08 补 `@Nullable`（可空返回值/参数/字段），并把 `buildEvent` 的 `Objects.requireNonNull` 改为"WARN + `drops++` + 跳过"
- [x] T040 [US5] 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 把 `splitMessage` 的 blank 判定改为 `CharSequenceUtil.isBlank`（返回空列表），保留代理对边界回退逻辑
- [x] T041 [US5] 修正 `leaf-base-sse/src/main/java/cn/maple/sse/service/GXSseEmitterService.java` 全部 Javadoc：明确"超时是绝对的、心跳不保活、`closeConnect` 不阻止重连、无断点续传、重试仅对 `IOException` 生效"
- [x] T042 [US5] 重写 `docs/leaf-base-sse开发手册.md` 的 2.3 / 5.1 / 5.2 / 6 / 7 章节（删除静态字段、`removeUser`、`sendHeartbeatToAll`、`sendMessageToAllClient(msg,true)`、同步块、1KB–2KB、"心跳保活"、"错误回调发送恢复提示"；新增"连接生命周期与超时真相""顺序与重试""可观测性"三节）
- [x] T043 [US5] 更新 `leaf-base-sse/AGENTS.md` §3/§4：新增"移除即关闭""同连接保序""销毁语义"三条红线与对应测试清单

**Checkpoint**: 空值标注完整、blank 语义一致、手册零漂移。

---

## Phase 8: User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

**Goal**: 每条发现可直接转任务，且"既定契约"被显式保护。

**Independent Test**: 抽取任一 F，报告已含编号/级别/维度/位置/触发/影响/证据/回归设计/batch；抽取任一 P，能在用例集中找到对应断言。

- [x] T044 [P] [US6] 在 `specs/011-sse-module-audit/audit-report.md` 追加"F → `fixBatch`（B1~B6）→ 用例类 → 任务 ID"追溯矩阵，与 `specs/011-sse-module-audit/tasks.md` 的任务编号对齐
- [x] T045 [P] [US6] 在 `specs/011-sse-module-audit/audit-report.md` 追加"P-01 ~ P-06 → 保留用例名"对照表，并核对 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplTest.java` 中 3 条 `staleXxxCallback_*`、`sendMessageMethods_Ignore*`、`destroy_*` 未被删除或跳过
- [x] T046 [US6] 在 `specs/011-sse-module-audit/quickstart.md` 校验 6 个手工核对项（C-01 ~ C-06）与 `specs/011-sse-module-audit/tasks.md` 一一对应，补齐缺失的验证命令

**Checkpoint**: 报告与任务双向可追溯，P 序列有断言保护。

---

## Phase 9: Polish & Cross-Cutting Concerns

- [x] T047 新增 `leaf-base-sse/src/test/java/cn/maple/sse/service/GXSseEmitterHttpSpringBootTest.java`：`@SpringBootTest(webEnvironment = RANDOM_PORT)` + 测试内 `@RestController` 端点，用 `HttpClient` 读 `text/event-stream`，断言 `id:201`、`event: Push Msg`、`retry:2000`、`data:` 与关闭帧（FR-020 端到端门槛）
- [x] T048 运行 `mvn -o -pl leaf-base-sse clean test` 并把全绿输出（用例总数、各类耗时）归档到 `specs/011-sse-module-audit/audit-report.md`；确认 NullAway 零错误
- [x] T049 [P] 检查 `leaf-base-sse/src/main/java/**/*.java` 中全部日志为 ASCII：`rg -n "log\.(info|warn|error|debug)" leaf-base-sse/src/main/java`，逐条确认无中文与非 ASCII 字符
- [x] T050 [P] 复核 `specs/011-sse-module-audit/contracts/sse-service-contract.md`、`specs/011-sse-module-audit/contracts/sse-delivery-contract.md`、`specs/011-sse-module-audit/contracts/sse-observability-contract.md` 与实现一致（事件线格式、丢弃矩阵、指标字段、日志级别）
- [x] T051 [P] 清理 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 中的冗余注释与无复用价值的抽取方法；确认 `git status` 仅包含 `leaf-base-sse/`、`docs/leaf-base-sse开发手册.md` 与 `specs/011-sse-module-audit/`
- [x] T052 在 `specs/011-sse-module-audit/audit-report.md` 产出 PR 说明素材：默认值未变清单、7 项兼容声明（见 `specs/011-sse-module-audit/plan.md`）、验证命令与结果、风险与遗留（含宪法 IV 偏差登记）

---

## Dependencies

```text
Phase 1 (T001-T003)
   └─▶ Phase 2 (T004-T006)  ← 阻塞所有故事
          ├─▶ US1 (T007-T014)  ← 报告 + 复现用例（旧代码失败）
          │      └─▶ US2 (T015-T023)  ← 修复生命周期，让 F-01/F-04/F-05 复现用例转绿
          │             └─▶ US3 (T024-T030)  ← 修复顺序与并发，让 F-02 复现用例转绿
          │                    └─▶ US4 (T031-T036)  ← 指标（依赖 T020/T027 的插桩点）
          │                           └─▶ US5 (T037-T043)  ← 契约与文档（可与 US4 并行）
          └─▶ US6 (T044-T046)  ← 仅依赖 US1 的报告骨架，可与 US4/US5 并行
                                      └─▶ Phase 9 (T047-T052)  ← 全部故事完成后收尾
```

**Story 完成顺序**：US1 → US2 → US3 → （US4 ∥ US5 ∥ US6） → Polish。

## Parallel Example: User Story 1

```bash
# 三条复现用例写在不同测试方法、互不依赖，可并行
Task: "T007 f01RetryExhaustedConnectionIsNotClosed in GXSseEmitterAuditReproTest.java"
Task: "T008 f02FailedMessageIsOvertakenByNextMessage in GXSseEmitterAuditReproTest.java"
Task: "T009 f04ConcurrentReplaceReturnsEmitterInsteadOfException in GXSseEmitterAuditReproTest.java"

# 报告的四张表可分头撰写
Task: "T011 E 序列表 in specs/011-sse-module-audit/audit-report.md"
Task: "T012 F 八要素表 in specs/011-sse-module-audit/audit-report.md"
Task: "T013 四张矩阵 in specs/011-sse-module-audit/audit-report.md"
Task: "T014 P 序列清单 in specs/011-sse-module-audit/audit-report.md"
```

## Parallel Example: User Story 5

```bash
Task: "T037 splitMessage 用例 in leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplSplitMessageTest.java"
Task: "T038 空值与空负载用例 in leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplTest.java"
Task: "T042 手册重写 in docs/leaf-base-sse开发手册.md"
Task: "T043 AGENTS 红线更新 in leaf-base-sse/AGENTS.md"
```

## Implementation Strategy

### MVP First（US1）

1. Phase 1 + Phase 2 完成 → 创建点可注入、基线 10/0；
2. 完成 US1 → `specs/011-sse-module-audit/audit-report.md` 可交付，3 条复现用例在旧代码上稳定失败；
3. **STOP and VALIDATE**：按 `specs/011-sse-module-audit/quickstart.md` §2 逐条核对四个高危现场。

> 仅 US1 交付"可复核结论 + 失败用例"，不含生产修复。**最小可发布修复集 = US1 + US2 + US3**（生命周期闭环 + 保序 + 并发），US4 ~ US6 为增强项。

### Incremental Delivery

1. US1 → 报告可复核（评审通过门槛）
2. US2 → 僵尸连接与并发 500 消除（线上风险最高的两项）
3. US3 → 顺序与头阻塞消除（数据正确性）
4. US4 / US5 / US6 并行 → 可观测、契约、文档（可分别合入）
5. Polish → 端到端 SSE 用例 + 兼容声明 + 全量验证

### 每个任务的完成定义

1. 用例先行且**在改动前失败**（失败输出保存到 `specs/011-sse-module-audit/audit-report.md` 或 PR）；
2. 实现最小化，不改变 `specs/011-sse-module-audit/plan.md` 承诺的默认值；
3. `mvn -o -pl leaf-base-sse test` 全绿且用例数单调增长；
4. 日志 ASCII、无 `System.out`、无新增依赖。

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖，可并行
- 每个用户故事结束时都有独立 Checkpoint，可停止并交付
- 既有 10 条用例**一条不删**；`staleXxxCallback_*`、`sendMessageMethods_Ignore*`、`closeConnect_*`、`destroy_*` 为 P 序列保护对象
- 禁止用反射读写私有字段构造失败现场（FR-023）；一律用 `leaf-base-sse/src/test/java/cn/maple/sse/support/GXSseTestEmitters.java` + `createEmitter` 缝
- 单条最小验证命令：`mvn -o -pl leaf-base-sse test`

## 格式校验

- 全部 52 个任务均为 `- [x] T0xx` 形式，含 `[P]`（适用时）与 `[USn]`（仅故事阶段）标记，并给出精确文件路径；
- Setup（T001-T003）、Foundational（T004-T006）、Polish（T047-T052）阶段无 `[Story]` 标记，符合模板约定；
- 任务 ID 从 T001 严格递增到 T052，无重复、无缺号。

---

## Phase 10: Convergence

> 由 `/speckit.converge` 于 2026-10-23 追加。仅追加，不改写既有任务。实施前请先读 `specs/011-sse-module-audit/audit-report.md` §10 与三份契约。

- [x] T053 CRITICAL: 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 用 `GXMdcThreadUtils.getMdcContext()` + `GXMdcThreadUtils.wrap(Runnable, Map)` 包装心跳批次任务与有序投递（drain）任务，替代裸 `taskExecutor.execute(...)`，并在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplMetricsTest.java` 补一条"提交线程写入 MDC，执行线程可读到"的断言 per Constitution V (contradicts)
- [x] T054 在 `specs/011-sse-module-audit/audit-report.md` 新增"逐修复项旧代码失败证据"章节：为 F-06、F-09、F-10、F-13、F-19、F-20 各给出旧实现代码位置 + 旧行为断言 + 对应新用例名（已有实测的 F-01/F-02/F-05 复用 §7.1），并在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterAuditReproTest.java` 补可执行的旧行为对照用例 per FR-021 (partial)
- [x] T055 修正 `specs/011-sse-module-audit/audit-report.md` §4.4 的性能口径：把"单条消息 UUID 由 2 降为 1"改为实测值（`buildMessageDto` 显式调用 `.msgId(...)` 时仍会触发 `@Builder.Default` 生成，实际 2 次生成 / 1 次使用），并在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplMetricsTest.java` 补可复算断言（同一原始消息分片共享 1 个 `msgId`；每 DTO 生成次数按已知口径记录），或按需消除重复生成但不得改变 DTO 默认值语义 per SC-005 (contradicts)
- [x] T056 让队列超限策略与 `specs/011-sse-module-audit/data-model.md` B-2 一致：在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseClientSendPipeline.java` 的 `offer` 中改为"丢弃队首最旧、接受新消息"，并把被丢弃条数回传给调用方计入 `drops`；在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplSendFailureTest.java` 补"队列满时保留最新消息"用例 per data-model B-2 (contradicts)
- [x] T057 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 的"服务已销毁"分支（`sendMessageToOneClient`、`sendMessageToAllClient`）补 `drops.increment()`，并在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplLifecycleTest.java` 的 `sendAfterDestroy_IsIgnoredWithoutThrowing` 中断言 `drops` 递增 per contracts/sse-delivery-contract.md §3 (contradicts)
- [x] T058 消除 `docs/leaf-base-sse开发手册.md` 的残留漂移：把 5 处 `DEFAULT_MIN_TIMEOUT` 改为 `DEFAULT_MIN_TIMEOUT_SECONDS`，修正第 69 行 `eventName` 默认值描述、第 366–368 行"需要提升到接口"（`getMetrics()` 已在接口）、第 694 行"发送是异步的"（仅失败后经队列异步）、第 702 行"超时…而没有活动"（绝对超时）；同时把 `specs/011-sse-module-audit/contracts/sse-service-contract.md` §4 的注释格式改成实现顺序 `:chunk=<i>/<n>:<clientId>`，并用 `rg -n "DEFAULT_MIN_TIMEOUT[^_]|异步的|提升到接口" docs/leaf-base-sse开发手册.md` 验证零命中 per FR-019 (contradicts)
- [x] T059 为缺少可运行复现的发现补用例或明确回归设计：F-08（空消息广播在"有连接/无连接"两种状态下均为 WARN）、F-11（广播串行写计数）、F-16（`splitMessage` 的 `length` 口径与 Javadoc 一致）、F-18（`closeConnect` 后浏览器会重连的端到端声明）、F-22（`closeConnections` 谓词异常穿透）、F-23（`getSseEmitterByClientId` 裸 emitter 风险的 Javadoc/用例说明），用例分别落在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplTest.java`、`.../GXSseEmitterServiceImplConcurrencyTest.java`、`.../GXSseEmitterServiceImplSplitMessageTest.java`、`leaf-base-sse/src/test/java/cn/maple/sse/service/GXSseEmitterHttpSpringBootTest.java` per FR-003 (partial)
- [x] T060 在 `specs/011-sse-module-audit/audit-report.md` 增加"文件 → 判定"覆盖表，覆盖 6 个生产文件与 2 个既有测试类（无缺陷 / 风险 / 缺陷 / 未覆盖原因），与 §2/§3 的证据交叉引用 per FR-001 (partial)
- [x] T061 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplConcurrencyTest.java` 补一条"广播到 N=100 连接时调用线程串行写次数 = N（失败路径不计退避）"的实测用例，并把结果回写 `specs/011-sse-module-audit/audit-report.md` §4.4 的口径表 per US4/AC2 (partial)
- [x] T062 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 的 `closeConnections` Javadoc 声明"谓词异常直接抛给调用方、批量关闭非原子、并发新增连接可能漏关"，并在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplLifecycleTest.java` 补异常穿透断言 per FR-003/F-22 (partial)
- [x] T063 删除 `leaf-base-sse/src/test/java/cn/maple/sse/support/GXSseTestEmitters.java` 中未被任何用例使用的 `blocking(long)` 与其辅助字段（或在 `GXSseEmitterServiceImplConcurrencyTest.java` 补一个真正使用它的隔离用例），并运行 `mvn -o -pl leaf-base-sse test` 确认零未使用告警 per FR-023 (unrequested)

---

## Phase 11: Convergence

> 由 `/speckit.converge` 于 2026-10-23（第二轮）追加。仅追加，不改写既有任务。

- [x] T064 在 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseEmitterServiceImpl.java` 把 `msgId` 的生成推迟到确定分片数之后（`sendMessageToOneClient` 第 270 行、`sendMessageAllClient` 第 241 行）：仅当 `fragments.size() > 1` 时生成 `IdUtil.fastUUID()`，否则传空串并依赖 `buildMessageDto` 的 `@Builder.Default`；在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplSendFailureTest.java` 补断言"未拆分时传入的 msgId 被忽略、DTO 使用默认值（非空且不等于入参）"，并同步 `specs/011-sse-module-audit/audit-report.md` §4.4 的口径为可复算表述 per SC-005 (contradicts)
- [x] T065 修正 `specs/011-sse-module-audit/data-model.md` B-2 的自相矛盾措辞（"丢弃队尾最旧"→"丢弃队首最旧的 1 条并在尾部追加新消息"），与 `leaf-base-sse/src/main/java/cn/maple/sse/service/impl/GXSseClientSendPipeline.java` 的 `offer` 实现保持一致 per plan: data-model B-2 (partial)
- [x] T066 在 `specs/011-sse-module-audit/contracts/sse-observability-contract.md` §1 注明 `attempts` "不含握手帧"（`createSseConnect` 直接 `sendNow`，不经过 `trySend`），并更新 `specs/011-sse-module-audit/audit-report.md` §10.2 中已过时的"T032 未按字面实现"偏差行（T055 已改为单条消息只用默认值） per contracts/sse-observability-contract.md §1 (partial)

---

## Phase 12: 测试补强（用户追加要求，已完成）

> 于 `/speckit.converge` 判定收敛后，按"测试是否全面"的复核补一轮；覆盖定时心跳、容器生命周期、真实断连、分片线格式与异常分支。

- [x] T067 把 `GXSseEmitterServiceImpl#triggerHeartbeat` 由 `private` 改为包私有，并在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplHeartbeatTest.java` 新增 `heartbeatTriggerRemovesInvalidConnections` 与 `heartbeatTriggerScansMoreThanOneBatch`（300 连接跨越 256 分片，断言批次数 ≥2）per 复核缺口 R1 (missing)
- [x] T068 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/GXSseEmitterContextLifecycleSpringBootTest.java` 用 `SpringApplicationBuilder(WebApplicationType.NONE)` 自建并关闭上下文，验证 `@PreDestroy` 关闭全部连接并拒绝新建 per 复核缺口 R3 (missing)
- [x] T069 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/GXSseEmitterHttpSpringBootTest.java` 新增 `splitFramesShareIdAndCarryChunkIndex`（分片帧共享 `id:` + `chunk=1/` 注释）与 `clientDisconnectCleansUpOnNextWrite`（关闭 socket 后写失败触发清理）per 复核缺口 R2/R5 (missing)
- [x] T070 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplFailureBranchesTest.java` 新增握手 `IOException`、关闭事件 `IOException`、`send` 抛 `RuntimeException`、管线关闭后 `offer` 四条用例，并为 `GXSseTestEmitters` 增 `failWithAt(int, RuntimeException)` per 复核缺口 R4 (missing)
- [x] T071 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplSendFailureTest.java` 新增 `broadcastQueuesBehindPendingRetry`（广播到恢复中的连接必须排队）per 复核缺口 R5 (missing)
- [x] T072 在 `leaf-base-sse/src/test/java/cn/maple/sse/service/impl/GXSseEmitterServiceImplLifecycleTest.java` 的 `destroy_CancelsQueuedRetriesAndAwaitsTermination` 增加 `drops ≥ 1` 断言（覆盖重试中断分支）per 复核缺口 R4 (partial)
- [x] T073 为 `GXSseEmitterServiceImplConcurrencyTest#slowRetryDoesNotDelayOtherRetries` 与 `GXSseEmitterServiceImplLifecycleTest#destroy_CancelsQueuedRetriesAndAwaitsTermination` 加 `@RepeatedTest(3)` 以降低时序用例的偶发性 per 复核缺口 R7 (partial)
- [x] T074 记录覆盖率现状到 `specs/011-sse-module-audit/audit-report.md` §13：JaCoCo/PIT 因离线仓库不可下载而未接入，以手工分支清单替代，并列出两条不可从公开 API 到达的防御性分支 per 复核缺口 R6 (partial)
