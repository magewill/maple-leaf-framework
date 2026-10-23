# Tasks: leaf-base-retry 正确性、线程安全与性能审查

**Input**: Design documents from `/specs/010-retry-module-audit/`

**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需，含 6 个用户故事）、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 本特性**包含测试任务**。依据 `AGENTS.md` §4、`spec.md` FR-012（每条发现必须给出"旧失败、新通过"的回归设计）与 FR-009（既有测试必须全部执行），测试任务按"先写测试 → 再实现"的顺序编排。

**Organization**: 任务按用户故事分组，每个故事可独立实现与验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1 ~ US6），仅用户故事阶段使用
- 所有任务含仓库相对路径

## Path Conventions

单模块仓库：生产代码 `leaf-base-retry/src/main/java/cn/maple/retry/`，测试 `leaf-base-retry/src/test/java/cn/maple/retry/`，文档 `docs/` 与 `leaf-base-retry/AGENTS.md`，本特性产物 `specs/010-retry-module-audit/`。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立审查报告骨架与实测基线，锁定改动范围

- [X] T001 创建 `specs/010-retry-module-audit/audit-report.md` 骨架，节序固定为：①基线 ②证据 E-01~E-24 ③发现 F-01~F-21 ④四张矩阵 ⑤性能口径 ⑥修复映射 ⑦兼容性声明 ⑧附录（PR 说明草稿）
- [X] T002 [P] 复跑基线并逐字记录到 `specs/010-retry-module-audit/audit-report.md` §1：`mvn -o -pl leaf-base-retry test` 应输出 `Tests run: 30, Failures: 0, Errors: 0`，`GXRetryUtilTest` 21 条约 16.57s
- [X] T003 [P] 记录范围护栏到 `specs/010-retry-module-audit/audit-report.md` §1：`leaf-base-retry/pom.xml` 与根 `pom.xml` 零改动、零新增依赖；允许改动的文件全集为 4 个生产文件 + 3 个新生产类型 + 1 个 `package-info` + 7 个测试文件 + 2 份文档

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 单点默认值与策略参数载体，被 US2/US3/US4 共同依赖

**⚠️ CRITICAL**: 本阶段完成前不要开始任何用户故事

- [X] T004 [P] 把 `leaf-base-retry/src/main/java/cn/maple/retry/config/GXRetryConfig.java` 的 `DEFAULT_MAX_ATTEMPTS`(3)、`DEFAULT_INITIAL_INTERVAL`(1000L)、`DEFAULT_MULTIPLIER`(2.0)、`DEFAULT_MAX_INTERVAL`(10000L) 由 `private static final` 提升为 `public static final`，Javadoc 标注"单点默认值，`GXRetryUtil` 引用此常量"
- [X] T005 [P] 新增 `leaf-base-retry/src/main/java/cn/maple/retry/config/GXRetryOptions.java`：`record GXRetryOptions(int maxAttempts, long initialInterval, double multiplier, long maxInterval, long timeout, long jitter, Map<Class<? extends Throwable>, Boolean> retryExceptions)`；紧凑构造器校验 `maxAttempts` 1~100、`initialInterval` 100~300000、`multiplier` 1.0~10.0 且 `Double.isFinite(multiplier)` 必须为真、`maxInterval` 1000~3600000、`initialInterval <= maxInterval`、`timeout >= 0`、`jitter >= 0`；提供 `defaults()`（3 / 1000 / 2.0 / 10000 / 0 / 0 / 空 map）
- [X] T006 `leaf-base-retry/src/main/java/cn/maple/retry/util/GXRetryUtil.java` 删除本地 `DEFAULT_*` 四个常量，改为引用 `GXRetryConfig.DEFAULT_*`；既有 8 个 `retryOperation` 重载与 2 个 `retryOperationAsync` 重载全部委托到 `GXRetryOptions.defaults()`（依赖 T004、T005）
- [X] T007 `leaf-base-retry/src/main/java/cn/maple/retry/config/GXRetryConfig.java` 新增 7 参 `createCustomRetryTemplate(int maxAttempts, long initialInterval, double multiplier, long maxInterval, long timeout, long jitter, Map<Class<? extends Throwable>, Boolean> retryExceptions)`：仅当 `timeout > 0` 时调用 `RetryPolicy.Builder.timeout(Duration.ofMillis(timeout))`、仅当 `jitter > 0` 时调用 `.jitter(...)`；原 5 参签名保留并委托；`validateRetryParameters` 增加 `Double.isFinite` 与 `timeout/jitter >= 0` 校验（依赖 T004、T005）
- [X] T008 [P] `leaf-base-retry/src/test/java/cn/maple/retry/config/GXRetryConfigTest.java` 补用例：①`includes/excludes` 生成（`true → includes`、`false → excludes`、空 map → 默认四类）②`multiplier=Double.NaN` 被本模块 `IllegalArgumentException` 拒绝且消息含 `multiplier` ③`timeout<0`、`jitter<0` 被拒绝 ④`timeout=0/jitter=0` 时策略与 5 参版本等价（`RetryPolicy.toString()` 不含 `timeout`/`jitter` 相关差异）（依赖 T007）

**Checkpoint**: 常量单点化与参数载体就绪，用户故事可并行开始

---

## Phase 3: User Story 1 - 审查结论可复核 (Priority: P1) 🎯 MVP

**Goal**: 产出可逐条定位、可复现的审查报告（21 条发现、24 条证据、四张矩阵、性能口径）

**Independent Test**: 随机抽取 3 条结论，按报告给出的文件行号与命令复现，现象与结论一致

- [X] T009 [US1] 在 `specs/010-retry-module-audit/audit-report.md` §2 写全 24 条证据：每条含 `source`（本模块/上游 `spring-core|spring-context` 7.0.9/实测/文档原文）、`locator`（文件:起-止行，上游须给行号如 `RetryTemplate.java:171-172`）、单句事实；无法实测的标注"未验证假设"
- [X] T010 [US1] 在 `specs/010-retry-module-audit/audit-report.md` §3 写全 21 条发现 F-01~F-21：每条含 `severity`（高/中/低，分布 5/11/5）、`dimension`（正确性/线程安全/性能）、`location`、`trigger`、`impact`、`evidenceIds`、`regressionDesign`（输入+断言点+期望差异）、`disposition`（修复 / 文档收敛 / 既定契约）
- [X] T011 [US1] 在 `specs/010-retry-module-audit/audit-report.md` §4 落四张矩阵，直接引用 [data-model.md](./data-model.md) §2~§4（重试语义矩阵 3 入口 × 默认异常集、失败终止矩阵 5 类终止、并发风险矩阵 5 个对象，另加性能口径表）
- [X] T012 [US1] 在 `specs/010-retry-module-audit/audit-report.md` §5 写性能口径：容器查询 1 次/调用、键拷贝 1 次/调用、键对象 1 个/调用、上下文 1 个/尝试、每失败尝试 WARN 1 条、最坏阻塞 `300000 + 98 × 3600000 = 353100000ms ≈ 98h`（附公式与代入值）、测试墙钟基线 16.57s
- [X] T013 [US1] 按 [quickstart.md](./quickstart.md) §2 抽检 3 条结论（F-01/F-04/F-07）并复现，把命令与输出粘贴到 `specs/010-retry-module-audit/audit-report.md` §1 的"抽检记录"

**Checkpoint**: 报告可独立评审，MVP 成立

---

## Phase 4: User Story 2 - 重试语义与失败处置可判定 (Priority: P1)

**Goal**: 上下文属性可跨尝试传递、`retryCount` 三处同义

**Independent Test**: 同一回调第 1 次尝试写属性、第 2 次尝试读到；3 次尝试耗尽后恢复上下文 `getRetryCount()==2`、不可重试异常时为 `0`

- [X] T014 [P] [US2] 先在 `leaf-base-retry/src/test/java/cn/maple/retry/context/GXRetryContextTest.java` 增用例（应先失败）：①第 1 次尝试 `setAttribute("k","v")` 后第 2 次尝试 `getAttribute("k")` 返回 `"v"` ②`getParent()` 链长 = 尝试数 - 1 ③`getAttemptIndex()` 依次为 0/1/2 且与 `getRetryCount()` 相等 ④`getAttributes()` 返回不可变快照且跨线程读取一致
- [X] T015 [US2] 先在 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilTest.java` 更新三条计数值断言（应先失败）：`testRetryOperation_RecoveryCallbackReceivesLastFailureAndTotalAttempts` 3→2 并改名含 `RetryCount`、`testRetryOperation_RecoveryRunsForNonRetryableExceptionWithoutRetrying` 1→0、`testRetryOperationAsync_CustomRecoveryCallback` 2→1
- [X] T016 [US2] 实现 `leaf-base-retry/src/main/java/cn/maple/retry/context/GXRetryContext.java`：新增 `GXRetryContext(int, Throwable, GXRetryContext parent)` 在构造期把 `parent` 的全部属性复制到本实例（`forEach(this::setAttribute)`）、新增 `getAttemptIndex()`（与 `getRetryCount()` 同值）、新增 `from(RetryState, GXRetryContext parent)`，原 `from(RetryState)` 委托（依赖 T014）
- [X] T017 [US2] 实现 `leaf-base-retry/src/main/java/cn/maple/retry/util/GXRetryUtil.java`：以 `AtomicReference<GXRetryContext> previous` 串链，每次尝试 `new GXRetryContext(attemptIndex, lastThrowable, previous.get())`；恢复分支改用 `exception.getRetryCount()` 并把 `previous.get()` 作为 `parent`（依赖 T015、T016）
- [X] T018 [US2] 跑 `mvn -o -pl leaf-base-retry test -Dtest=GXRetryContextTest,GXRetryUtilTest`，确认新用例全绿，并把"旧代码失败、新代码通过"的前后差异记入 `specs/010-retry-module-audit/audit-report.md` §6（依赖 T017）

**Checkpoint**: US1 与 US2 可独立验收；US2 不依赖 US3 的缓存/异步改造

---

## Phase 5: User Story 3 - 并发与生命周期安全可验证 (Priority: P1)

**Goal**: 缓存在并发下不越界、异步可取消且日志可关联原请求、监听器 Bean 参与生效

**Independent Test**: 32 线程 × 8 组参数并发后缓存 `size() <= 256`；`token.cancel()` 后不再尝试；带 TraceId 的异步重试日志可关联

- [X] T019 [P] [US3] 先写 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilConcurrencyTest.java`（应先失败）：32 线程 × 8 组参数（含不同 `maxAttempts`/`initialInterval`、共 300 个不同键），断言每组执行次数与异常类型符合各自参数、结束后 `RETRY_TEMPLATE_CACHE` 反射读到的 `size() <= 256`、整例 30s 超时
- [X] T020 [P] [US3] 先写 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilAsyncTest.java`（应先失败）：①`timeout=300`、回调阻塞 200ms、`maxAttempts=5` → 尝试次数 < 5 且抛最后一次业务异常、恢复回调执行 ②`jitter=100` 只影响耗时区间不影响尝试次数 ③`token.cancel()` 后不再有新尝试且不执行恢复回调 ④回调内 `MDC.get("traceId")` 与提交线程一致 ⑤`RejectedExecutionException` 原样抛出
- [X] T021 [P] [US3] 新增 `leaf-base-retry/src/main/java/cn/maple/retry/context/GXCancellationToken.java`：`cancel()`、`isCancelled()`，内部单个 `AtomicBoolean`，无其他状态
- [X] T022 [P] [US3] 新增 `leaf-base-retry/src/main/java/cn/maple/retry/exception/GXRetryCancelledException.java`（`extends GXBusinessException`）与同包 `package-info.java`（`@NullUnmarked`，与模块其余包一致）
- [X] T023 [US3] 改 `leaf-base-retry/src/main/java/cn/maple/retry/util/GXRetryUtil.java`：`retryOperationAsync` 内部由 `CompletableFuture.supplyAsync` 改为 `GXMdcThreadUtils.supplyAsync`；新增 `retryOperationAsync(callback, recovery, options, executor, token)` 重载，在"每次尝试前"与"每次退避前"检查 `token.isCancelled()` 与 `Thread.currentThread().isInterrupted()`，命中抛 `GXRetryCancelledException` 且不执行恢复回调；无 token 版本行为不变（依赖 T020、T021、T022）
- [X] T024 [US3] 改 `leaf-base-retry/src/main/java/cn/maple/retry/util/GXRetryUtil.java` 缓存淘汰：新增 `private static final Object EVICTION_LOCK`，`size() > 256` 时进入 `synchronized` 二次确认后裁剪到 192（单次 `O(size-192)`），`computeIfAbsent` 主路径保持无锁（依赖 T019）
- [X] T025 [US3] 改 `leaf-base-retry/src/main/java/cn/maple/retry/util/GXRetryUtil.java` 缓存键：`RetryTemplateCacheKey` 增加 `timeout`、`jitter` 两个组件，移除 `retryConfigType` 组件；保留"未来引入策略属性须把策略快照纳入键"的注释（依赖 T019、T007）
- [X] T026 [US3] 改 `leaf-base-retry/src/main/java/cn/maple/retry/config/GXRetryConfig.java` 监听器来源：删除 `private static final GXRetryListener RETRY_LISTENER`，新增构造器注入 `GXRetryListener` 与无参构造（回退到进程内单例，供 `GXRetryUtil` 自建模板路径使用）；`createCustomRetryTemplate` 统一从字段取监听器（依赖 T004）
- [X] T027 [US3] 跑 `mvn -o -pl leaf-base-retry test -Dtest=GXRetryUtilConcurrencyTest,GXRetryUtilAsyncTest` 验证 T019/T020 转绿，并把并发与取消结论补入 `specs/010-retry-module-audit/audit-report.md` §4 并发风险矩阵的"现状/收敛后"列（依赖 T023、T024、T025、T026）

**Checkpoint**: US3 独立可验；不要求 US4/US5 完成

---

## Phase 6: User Story 4 - 性能开销定量化 (Priority: P2)

**Goal**: 日志开销可控、测试墙钟达标、口径可复算

**Independent Test**: `maple.framework.retry.log.level=OFF` 时零日志；套件总墙钟 ≤ 8s；手册与报告中的口径数字一致

- [X] T028 [P] [US4] 先写/更新 `leaf-base-retry/src/test/java/cn/maple/retry/listener/GXRetryListenerTest.java`（应先失败）：①`beforeRetry` 在 `retryCount==1` 输出恰好 1 条"retry started"、`retryCount==2` 不再输出 ②首次失败且不可重试时输出文案为 `retryable execution failed`（不再暗示必然重试）③`level=OFF` 时任何事件零输出 ④`level=WARN` 时 INFO 事件零输出、WARN 保留 ⑤删除对 `clearCache()` 的引用
- [X] T029 [US4] 改 `leaf-base-retry/src/main/java/cn/maple/retry/listener/GXRetryListener.java`：`beforeRetry` 判据 `getRetryCount() == 1`；`onRetryableExecution` 文案改 `retryable execution failed`；`LOG_LEVEL`/`ENABLE_VERBOSE_LOGGING` 改 `volatile` 且每次调用重读 `System.getProperty`；`shouldLogAtLevel` 改阈值语义（`OFF < ERROR < WARN < INFO < DEBUG`，`OFF` 全关，非法值按 `INFO`）；所有日志前加 `log.isXxxEnabled()` 守卫；删除 `RETRYABLE_TYPE_CACHE`、`MAX_CACHE_SIZE` 与 `clearCache()`，`getConfigInfo()` 去掉 `cacheSize`（依赖 T028）
- [X] T030 [US4] 收敛 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilTest.java` 的退避耗时：除保留 1 条"默认参数真实退避（断言 ≥700ms）"外，其余用默认退避的用例改为显式 `initialInterval=100`（下限），并断言套件总墙钟 ≤ 8s（依赖 T018）
- [X] T031 [US4] 复跑 `mvn -o -pl leaf-base-retry test` 记录新墙钟，把"16.57s → 实测值"与每失败尝试 WARN 1 条（加守卫后未减少条数）写入 `specs/010-retry-module-audit/audit-report.md` §5（依赖 T029、T030）

---

## Phase 7: User Story 5 - 对外契约与文档一致 (Priority: P3)

**Goal**: 手册以 Spring Framework 7 为唯一基线，风险与计数语义可自解释

**Independent Test**: 手册中检索不到 `@EnableRetry`/`SimpleRetryPolicy`/`ExponentialBackOffPolicy`/`org.springframework.retry`/`RetryCallback`；三张表在场

- [X] T032 [US5] 重写 `docs/leaf-base-retry开发手册.md`：删除 spring-retry 全部表述（`@EnableRetry`、`SimpleRetryPolicy`、`ExponentialBackOffPolicy`、`org.springframework.retry.*`、`RetryCallback`、双参 `execute`），全部示例改为 `org.springframework.core.retry` 与 `org.springframework.resilience.annotation` 基线；修正"每次调用创建新模板"为"静态缓存复用，键为策略参数"
- [X] T033 [US5] 在 `docs/leaf-base-retry开发手册.md` 新增"默认异常集与风险"专章：列明 `GXRetryUtil` 默认重试所有 `Exception`、Bean 默认含 `GXBusinessException`，给出可直接编译的排除示例（`Exception=true` + `GXBusinessException=false` + `InterruptedException=false`）
- [X] T034 [US5] 在 `docs/leaf-base-retry开发手册.md` 新增"上下文与计数"节：贴 `retry-api-contract.md` §3 的 `retryCount` 三入口对照表（回调 0/1/2、恢复 2/0、`from(RetryState)` 同义），含"3 次尝试 = 2 次重试"的兼容说明与 `getAttemptIndex()` 用法；同时写明"`CompletableFuture.cancel(true)` 不中断在途重试"与 token 取消用法
- [X] T035 [P] [US5] 为 `GXRetryUtil`、`GXRetryConfig`、`GXRetryContext`、`GXRetryListener` 补/改 Javadoc：默认异常集风险提示（指向手册章节）、`getRetryCount()` 与 `getAttemptIndex()` 关系、`getParent()` 链语义、缓存键组成与淘汰行为
- [X] T036 [P] [US5] 更新 `leaf-base-retry/AGENTS.md` §3（补"日志级别仅可通过系统属性配置""`retryCount` 为已执行重试次数"）与 §4（补 `GXRetry*SpringBootTest`、并发/取消/MDC 用例要求），不改其余章节
- [X] T037 [US5] 一致性核查：`Select-String -Path docs\leaf-base-retry开发手册.md -Pattern "EnableRetry|SimpleRetryPolicy|ExponentialBackOffPolicy|org\.springframework\.retry|RetryCallback"` 无输出；`retryCount`/`最坏阻塞`/`默认异常集` 三个模式均命中；`grep -r "clearCache" leaf-base-retry/src` 无结果（依赖 T029、T032、T033、T034）

---

## Phase 8: User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

**Goal**: 每条发现可直接拆任务，既定契约不被误改

**Independent Test**: 抽取任一发现，八要素齐备；抽取任一 `既定契约` 条目，明确标注"禁止改写"

- [X] T038 [US6] 在 `specs/010-retry-module-audit/audit-report.md` §6 写"修复映射表"：`F-01~F-21 → R-01~R-12 → T0xx` 三列对齐，`disposition=既定契约` 的 5 条（P-01~P-05）单列"禁止改写"清单（依赖 T010、T018）
- [X] T039 [US6] 在 `specs/010-retry-module-audit/audit-report.md` §7 写兼容性声明表：`retryCount` 数值 -1（3→2、1→0）、新增 API 为加法式、删除 `clearCache` 的影响、`beforeRetry` 新增 1 条 INFO、`OFF` 语义修正；每行给"影响面 + 迁移路径"；附录写 PR 说明草稿（动机/影响范围/验证命令/兼容策略）（依赖 T038）

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 收口验证与一致性清理

- [X] T040 全量验证：`mvn -o -pl leaf-base-retry clean compile` 与 `mvn -o -pl leaf-base-retry test` 均通过（ErrorProne + NullAway 零错误、0 失败 0 错误）
- [X] T041 按 [quickstart.md](./quickstart.md) §3~§5 全流程走查（编译、全量测试、分场景验收、文档核对），把结果补入 `specs/010-retry-module-audit/audit-report.md` §1 的"验收记录"（依赖 T040）
- [X] T042 交叉引用自查：`spec.md` / `plan.md` / `research.md` / `data-model.md` / `audit-report.md` 的发现数（21）、证据数（24）、决策数（D-01~D-12）完全一致，指向的文件名与实际文件一致（依赖 T039）
- [X] T043 清理临时产物（如审查期解压的源码目录）并确认 `git status` 中只有 `leaf-base-retry/`、`docs/leaf-base-retry开发手册.md`、`leaf-base-retry/AGENTS.md`、`specs/010-retry-module-audit/` 四类路径被改动（依赖 T041）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup；**阻塞所有用户故事**
- **US1 (Phase 3)**: 仅依赖 Setup（报告写作不需要代码改动）→ 可与 Foundational 并行
- **US2 (Phase 4)**: 依赖 Foundational（T004/T006 的常量单点化）
- **US3 (Phase 5)**: 依赖 Foundational（T005/T007 的 options 与模板重载）
- **US4 (Phase 6)**: 依赖 US2（T030 需 T018 完成）与 US3（T029 的监听器改造与 T026 的实例来源避免同文件冲突）
- **US5 (Phase 7)**: 依赖 US2/US3/US4 的最终行为（文档需描述已实现语义）
- **US6 (Phase 8)**: 依赖 US1（报告骨架）与 US2
- **Polish (Phase 9)**: 依赖全部

### Story 完成顺序（推荐）

`Setup → Foundational → US1（可并行）→ US2 → US3 → US4 → US5 → US6 → Polish`

### Within Each User Story

- 测试任务在前且必须**先失败**
- 上下文/策略类型先于工具类改动
- 同文件改动集中在同一任务内，避免跨任务冲突（`GXRetryUtil.java` 在 T006/T017/T023/T024/T025 依次推进，必须串行）

### Same-file Serialization（禁止并行）

`GXRetryUtil.java`、`GXRetryConfig.java`、`GXRetryListener.java`、`GXRetryContext.java`、`GXRetryUtilTest.java`、`audit-report.md`、`docs/leaf-base-retry开发手册.md` 均**不可**跨任务并行。

---

## Parallel Example: Foundational

```text
# T004 与 T005 不同文件，可并行：
Task: "GXRetryConfig 默认常量提升为 public static final"
Task: "新增 GXRetryOptions record 与 defaults()"
```

## Parallel Example: User Story 3

```text
# 三个测试/新类型文件互不重叠，可并行起草：
Task: "写 GXRetryUtilConcurrencyTest"
Task: "写 GXRetryUtilAsyncTest"
Task: "新增 GXCancellationToken"
Task: "新增 GXRetryCancelledException 与 package-info"

# 之后串行进入同一个 GXRetryUtil.java：T023 → T024 → T025
```

## Parallel Example: User Story 5

```text
Task: "Javadoc 补充（4 个类型）"
Task: "AGENTS.md §3/§4 更新"
# 手册重写 T032~T034 同文件，必须串行
```

---

## Implementation Strategy

### MVP First（仅 US1）

1. Phase 1 Setup（T001~T003）
2. Phase 3 US1（T009~T013）
3. **STOP and VALIDATE**：按 quickstart §2 抽检 3 条结论
4. 此时已可交付"审查报告"，但代码缺陷未修

### 建议的实际交付顺序（审查特性）

1. Setup + Foundational（T001~T008）
2. US1 报告（T009~T013）→ 评审确认结论无误
3. US2（T014~T018）→ 语义修复 + 计数值变更
4. US3（T019~T027）→ 并发/取消/MDC
5. US4（T028~T031）→ 日志与耗时
6. US5（T032~T037）→ 文档对齐
7. US6（T038~T039）→ 排期与兼容性
8. Polish（T040~T043）

### Parallel Team Strategy

- 单人：严格按上述顺序（同文件串行）
- 两人：A 负责 US1+US6（报告与排期），B 负责 Foundational→US2→US3→US4→US5；两边在 US6 汇合
- 不要在 US2/US3 并行改 `GXRetryUtil.java`

---

## Notes

- [P] 仅表示"文件不冲突"，仍须满足该任务的依赖任务已完成
- 每个用户故事结束都要能独立验证（checkpoint），验证不通过不进入下一故事
- 计数值变更（T015）是本特性唯一的运行时语义变更，提交信息需在正文引用 `contracts/retry-api-contract.md` §3
- 禁止在实现期顺手改默认异常集（Q1 决策）、新增配置属性或迁移自动装配（Q4 决策）
- 提交粒度建议：一个 Phase 一次提交（Setup/Foundational/US1/US2/US3/US4/US5/US6/Polish）

---

## Phase 10: Convergence

**Purpose**: 收敛 spec/plan/tasks 与当前实现的剩余差距（评估日期 2026-10-23；仅追加，不改动既有任务）

- [X] T044 CRITICAL: 为 `leaf-base-retry/src/main/java/cn/maple/retry/util/GXRetryUtil.java` 中全部 `retryOperation*`（含 `retryOperation(callback, recovery, options)`）与 `retrySupplier*` 的返回值标注 `@Nullable`，Javadoc `@return` 说明"恢复回调或回调返回 null 时原样返回"，并在 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilTest.java` 增加"恢复回调返回 null 时结果为 null"的用例，同步更新 `specs/010-retry-module-audit/contracts/retry-api-contract.md` §6 与 `docs/leaf-base-retry开发手册.md` 的空值说明 per Constitution V (contradicts)
- [X] T045 在 `docs/leaf-base-retry开发手册.md` §3.1 补"自定义 `RetryTemplate` Bean 与本模块 Bean 共存"一节：说明两者会同时存在、按类型注入需 `@Qualifier`/`@Primary` 指定，并给出可复制的示例片段 per FR-018 (missing)
- [X] T046 为 `specs/010-retry-module-audit/audit-report.md` §3 的发现表新增"复现方式"列（输入 + 命令/用例 + 期望观察），并把 F-05、F-16、F-20、F-21 四条发现中的占位证据 `E-（…）` 替换为真实 `E-xx` 编号或显式标注"未验证假设" per FR-002/FR-003/SC-002 (partial)
- [X] T047 在 `specs/010-retry-module-audit/audit-report.md` 的发现表之后新增"风险排序"小节，按"影响面 × 触发概率 × 可观测性"给出排序与修复批次（高危 5 条在前），供计划阶段直接排期 per FR-016 (missing)
- [X] T048 消除 `leaf-base-retry/src/main/java/cn/maple/retry/config/GXRetryConfig.java` 对 `GXRetryListener` 的强制依赖：构造注入改为 `ObjectProvider<GXRetryListener>` 且缺失时回退 `new GXRetryListener()`，并在 `leaf-base-retry/src/test/java/cn/maple/retry/config/GXRetrySpringBootTest.java` 增加"仅 `@Import(GXRetryConfig.class)`（无监听器 Bean）仍可启动且 `RetryTemplate` 可用"的用例 per FR-013/plan:D-08 (partial)
- [X] T049 在 `specs/010-retry-module-audit/data-model.md` §2 与 `audit-report.md` §4.1 把语义矩阵扩为"入口 × 异常类型"逐格判定（至少覆盖 `GXBusinessException`、`InterruptedException`、`IOException`、`IllegalArgumentException`、`Error`），并在 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilTest.java` 增加"默认参数下 `InterruptedException` 被重试 3 次且中断标志保留"的用例 per FR-005/SC-003/US2/AC2 (partial)
- [X] T050 在 `leaf-base-retry/src/test/java/cn/maple/retry/config/GXRetrySpringBootTest.java` 增加"用户自定义 `RetryTemplate` Bean + 本模块 Bean 共存"场景：断言 `RetryTemplate` 类型存在两个 Bean、按类型直接注入失败（`NoUniqueBeanDefinitionException`）、`@Qualifier` 指定后可注入成功 per plan:D-11/FR-018 (partial)
- [X] T051 在 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilConcurrencyTest.java` 增加"仅 `timeout` 不同（其余参数相同）的两次调用不共享模板"的断言（反射读取缓存 `size()` 增量或模板实例身份不同），锁定缓存键必须包含 `timeout`/`jitter` per FR-019 (partial)
- [X] T052 在 `specs/010-retry-module-audit/audit-report.md` §4.3 并发风险矩阵中补充两点结论：并发下淘汰是否可能短暂超过 256（锁内二次确认的作用）、裁剪到 192 时是否存在热点模板被淘汰的可能与缓解方式 per US3/AC2 (partial)

---

## Phase 11: Convergence

**Purpose**: 第二轮收敛（评估日期 2026-10-23；仅追加，不改动既有任务）

- [X] T053 在 `leaf-base-retry/src/test/java/cn/maple/retry/listener/GXRetryListenerTest.java` 增加基于日志 appender 的断言：`beforeRetry` 在 `retryCount==1` 时恰好输出 1 条 `retry started`、`retryCount==2` 时不再输出，并在用例结束时还原 `maple.framework.retry.log.level` 与 appender，锁定 T028 的日志验收项 per FR-009/T028 (partial)
- [X] T054 修正 `specs/010-retry-module-audit/quickstart.md` §3 的耗时判定口径：把"总墙钟 ≤ 8s"明确为"`GXRetryUtilTest` 单类墙钟 ≤ 8s"，并补全量 60 条用例约 11s 的实测基线，避免与 `audit-report.md` §5.3 的 7.44s 口径混淆 per SC-006 (partial)
- [X] T055 在 `docs/leaf-base-retry开发手册.md` §4.6 增加一行警示："`@Retryable` 的 `includes` 默认为空，等价于匹配全部 `Throwable`，因此会重试 `Error` 与 `InterruptedException`，与 `GXRetryUtil`/`RetryTemplate` Bean 的默认集不同"，并指向 §5 矩阵与 `audit-report.md` §4.1 per FR-022/SC-008 (partial)

---

## Phase 12: Convergence

**Purpose**: 第三轮收敛（评估日期 2026-10-23；仅追加，不改动既有任务）

- [X] T056 修正 `specs/010-retry-module-audit/quickstart.md` §3 的全量用例数为 61（T053 新增日志断言用例后的实测值），并在该行注明"用例数随新增测试同步更新，以 `mvn -o -pl leaf-base-retry test` 输出为准" per SC-008 (partial)
- [X] T057 勘误 `specs/010-retry-module-audit/contracts/retry-api-contract.md` 与 `specs/010-retry-module-audit/research.md` 的重载计数：既有 `retryOperation` 为 10 个（不是 8 个），改造后共 11 个；`retryOperationAsync` 既有 2 个 + 新增 2 个（`(options, executor)` 与 `(options, executor, token)`）；同步核对 `retrySupplier` 2 个不变 per SC-008/FR-013 (partial)

---

## Phase 13: Convergence

**Purpose**: 第四轮收敛——把"实现分支未被测试锁定"的空洞落成可执行任务（评估日期 2026-10-23；仅追加）

- [X] T058 在 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilTest.java` 增加 P-02 契约用例：用可变 `HashMap` 传入 `retryExceptions`，调用开始后立即 `clear()`/改值，断言在途重试仍按原映射执行、且后续同参数调用命中同一缓存条目（反射读 `RETRY_TEMPLATE_CACHE` 仅 +1）per spec P-02/FR-014 (missing)
- [X] T059 在 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilTest.java` 增加同步总超时用例：`GXRetryOptions.timeout=300`、`maxAttempts=5`、回调 `sleep(200)` 后抛错，断言尝试次数 < 5、抛最后一次业务异常、恢复回调被执行且 `getRetryCount()==0` per FR-020 (missing)
- [X] T060 在 `leaf-base-retry/src/test/java/cn/maple/retry/config/GXRetrySpringBootTest.java` 增加用例：上下文中定义自定义 `RetryTemplate`（策略为 `maxRetries=1`）后调用 `GXRetryUtil.retryOperation`，断言执行次数仍为模块默认 3 次（证明工具类不读取容器 Bean）；用 `ApplicationContextRunner` 承载，避免污染既有 `@SpringBootTest` per plan:D-10/Q4 (partial)
- [X] T061 在 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilConcurrencyTest.java` 增加断言：批量插入超过 256 个键后缓存条目数落到 `192`（读 `TARGET_RETRY_TEMPLATE_CACHE_SIZE`，不硬编码数字）；并用 `CountDownLatch` 让 16 线程并发请求同一键，断言该键只产生 1 个模板（以策略对象身份或构建计数验证）per FR-019 (partial)
- [X] T062 在 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilConcurrencyTest.java` 扩展键维度用例：仅 `multiplier` 不同、仅 `maxInterval` 不同、仅 `retryExceptions` 不同时，反射读缓存键对应字段，断言三者分别产生不同条目 per FR-019 (partial)
- [X] T063 在 `leaf-base-retry/src/test/java/cn/maple/retry/config/GXRetryConfigTest.java` 以表驱动补齐参数上界校验：`maxAttempts=101`、`initialInterval=300001`、`maxInterval=3600001`、`multiplier=0.5`、`multiplier=12.0`、`initialInterval>maxInterval`，均断言 `IllegalArgumentException` 且消息含对应字段名 per `contracts/retry-policy-contract.md` §3 (partial)
- [X] T064 在 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilTest.java` 补齐 3 个既有裸重载用例：`retryOperation(cb, Map)`（按映射重试）、`retryOperation(cb, recovery)`（耗尽后走恢复）、`retrySupplier(supplier)`（默认参数失败两次后成功），均使用 100ms 间隔避免拖慢套件 per FR-013/SC-006 (missing)
- [X] T065 在 `leaf-base-retry/src/test/java/cn/maple/retry/config/GXRetrySpringBootTest.java` 增加按名注入用例：`@Qualifier("retryTemplate") RetryTemplate` 可注入且监听器为容器 Bean，验证手册 §3.1 示例可用 per FR-018/T045 (partial)
- [X] T066 在 `leaf-base-retry/src/test/java/cn/maple/retry/listener/GXRetryListenerTest.java` 扩展日志事件断言：`verbose=true` 时 `onRetryableExecution` 带 `exceptionCount`；`onRetrySuccess` 输出 1 条；`onRetryPolicyInterruption`/`onRetryPolicyTimeout` 文案分别为 `retry policy interrupted`/`retry policy timed out`；`level=DEBUG` 时输出堆栈事件 per F-10/F-15 (partial)
- [X] T067 在 `leaf-base-retry/src/test/java/cn/maple/retry/context/GXRetryContextTest.java` 补充：3 层 `parent` 链（属性逐层继承、`getParent().getParent()` 可达）、`getLastThrowableOptional()` 空/非空两态、8 线程并发 `setAttribute` + `getAttributes()` 一致性 per FR-015 (partial)

---

## Phase 14: Convergence

**Purpose**: 第五轮收敛（评估日期 2026-10-23；仅追加）

- [X] T068 在 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilAsyncTest.java` 增加"token 存在但从未取消"的成功路径用例：`retryOperationAsync(cb, recovery, options, executor, token)` 中回调首轮失败一次后成功，断言 `future.get()` 返回结果、尝试次数为 2、恢复回调未执行、`token.isCancelled()` 为 false（覆盖 5 参重载的 happy path，与两个取消用例互补）per FR-019 (partial)

---

## Phase 15: Convergence

**Purpose**: 第六轮收敛（评估日期 2026-10-23；仅追加）

- [X] T069 在 `leaf-base-retry/src/test/java/cn/maple/retry/util/GXRetryUtilTest.java` 增加"调用线程已被中断且未传 token"的终止用例：`Thread.currentThread().interrupt()` 后调用 `retryOperation(callback)`，断言回调从未被执行、抛出 `GXRetryCancelledException`、恢复回调不执行；`finally` 中必须 `Thread.interrupted()` 清除中断标志以免污染后续用例（可用 `@AfterEach` 兜底）per `contracts/retry-api-contract.md` §4 (partial)

---

## Phase 16: Convergence

**Purpose**: 第七轮收敛（评估日期 2026-10-23；仅追加）

- [X] T070 补齐公开 API 文档：在 `specs/010-retry-module-audit/contracts/retry-api-contract.md` §1 的 `GXRetryOptions` 行补 `withRetryExceptions(Map<Class<? extends Throwable>, Boolean>)`（说明其返回新实例、用于"替换异常映射且其余参数不变"）、`GXRetryConfig` 行补 `createCustomRetryTemplate(GXRetryOptions)` 重载；在 `docs/leaf-base-retry开发手册.md` §4.2 增加一行用法说明与示例（"在默认参数上只替换异常映射"），并在 `AGENTS.md` §5 检查清单中加一条"新增公开方法必须同步契约 §1 与手册对应章节" per SC-008/FR-018 (partial)
