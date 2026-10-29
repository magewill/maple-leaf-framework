---

description: "Task list for MyBatis-Plus 操作监听加固"
---

# Tasks: MyBatis-Plus 操作监听切面的正确性、线程安全与性能加固

**Input**: Design documents from `/specs/001-mybatis-listener-audit/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: 本项目宪法第 VI 条要求"缺陷修复必须附带能在旧代码上失败的回归测试"，因此各用户故事均包含测试任务；测试必须先写并确认失败，再进入实现。

**Organization**: 按用户故事分组，每个故事可独立实现、独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1 / US2 / US3 / US4）
- 所有路径均为仓库相对路径

## Path Conventions

- 主代码：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/`
- 测试代码：`leaf-base-datasource/src/test/java/cn/maple/core/datasource/`
- 配置元数据：`leaf-base-datasource/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- 文档：`docs/leaf-base-datasource开发手册.md`、`leaf-base-datasource/AGENTS.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立基线，确认改动前现状可编译可测。

- [X] T001 在仓库根执行 `mvn -pl leaf-base-datasource -am test`（作用于 `leaf-base-datasource/pom.xml`），记录当前用例总数、失败数与耗时，作为后续回归对比基线
- [X] T002 [P] 新建 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/package-info.java`，内容为 `@org.jspecify.annotations.NullUnmarked` 包声明（与模块内其它包一致）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 统一的配置、枚举、指标、解析与发布组件；所有用户故事都依赖本阶段。

**⚠️ CRITICAL**: 未完成本阶段前不要开始任何用户故事。

- [X] T003 [P] 新建 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/config/GXMyBatisListenerProperties.java`，使用 `@Data` + `@ConfigurationProperties(prefix = "maple.framework.mybatis.listener")`，字段与默认值严格按 `contracts/configuration.md`：`deduplicate=true`、`propagateException=false`、`syncAfterCommit=false`、`strictSnapshot=false`、`metadataCacheSize=2048`、`metadataCacheExpireSeconds=3600`、`slowCallbackThresholdMs=30000`
- [X] T004 [P] 新建 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/enums/GXMyBatisListenerDegradeReasonEnums.java`，枚举 7 个原因：`OR_EXPRESSION`、`NON_COLUMN_LEFT`、`DUPLICATE_FIELD`、`PARAM_MISMATCH`、`PARSE_ERROR`、`NON_ABSTRACT_WRAPPER`、`UNSNAPSHOTABLE_VALUE`，每个带中文说明字段
- [X] T005 [P] 新建 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/service/GXMyBatisListenerMetricsService.java`（只读快照接口，方法 `snapshot()` 返回不可变快照对象）与 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/service/impl/GXMyBatisListenerMetricsServiceImpl.java`（用 `LongAdder` 计数：`delivered`/`failed`/`rejected`/`cancelled`/`degraded`/`skipped`，并提供 `activeTasks`、`queueSize` 读取），标注 `@Service` 且 `@ConditionalOnProperty(name = "maple.framework.enable.mybatis-listener", havingValue = "true", matchIfMissing = true)`
- [X] T006 新建 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerConfigResolver.java`：按"接口 + 方法签名 + 目标类型"解析 `@GXMyBatisListener`（含父接口/父类回溯），用 Caffeine 缓存（容量与过期取自 `GXMyBatisListenerProperties`），并缓存"无声明"结论；返回结果按接口全限定名排序，按"监听器类型 + 事件种类"去重（依赖 T003）
- [X] T007 新建 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerEventPublisher.java`：负责构造事件载荷、按 `runType` 选择 `publishEvent` / `publishEventAfterCommit`、写入指标计数、输出结构化日志（依赖 T003、T004、T005、T006）
- [X] T008 [P] 重构 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisPlusSaveEntityAspect.java`、`GXMyBatisPlusUpdateEntityAspect.java`、`GXMyBatisPlusUpdateFieldAspect.java`：删除各自重复的 `publishEvent/resolveListenerConfig/findMethod`，改为调用 `GXMyBatisListenerEventPublisher`，行为保持不变（依赖 T007）
- [X] T009 [P] 重构 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisPlusDeleteSoftAspect.java`、`GXMyBatisPlusDeleteAspect.java`、`GXMyBatisPlusSaveBatchEntityAspect.java`：同样改为委托发布组件，并保留各自"仅成功且影响行数 > 0 才投递"的判定（依赖 T007）
- [X] T010 执行 `mvn -pl leaf-base-datasource -am test -Dtest='GXMyBatis*Test'`（测试位于 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/`），确认重构后既有用例全部通过、行为无回归（依赖 T008、T009）

---

## Phase 3: User Story 1 - 监听回调与真实落库结果严格一致 (Priority: P1) 🎯 MVP

**Goal**: 消除重复投递；确保失败/回滚不产生"已提交"语义回调；条件无法还原时只给原始条件且不错配。
**Independent Test**: 在单模块内构造"成功 / 0 行 / 抛异常 / 回滚 / 无事务"五类场景，断言回调次数与载荷同数据库最终状态一致。

### Tests for User Story 1（先写并确认失败）

- [X] T011 [P] [US1] 新建 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerDeduplicationTest.java`，覆盖：双接口继承同一声明只投递 1 次、两个接口声明不同监听器各投递 1 次、方法级声明优先于类型级、父接口声明可命中（对应 FR-002、FR-007）
- [X] T012 [P] [US1] 扩展 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerAspectRegressionTest.java`，新增用例：返回 0 行不投递、抛异常不投递、事务回滚时"提交后"事件不投递、空批次不投递、`updateById` 不伪造条件（对应 FR-001、FR-004、FR-011）
- [X] T013 [US1] 扩展 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerAspectRegressionTest.java` 的条件断言（与 T012 同文件，须串行提交）：`OR`、函数左侧、重复字段、参数数量不匹配四类场景必须 `conditionLossless=false`、`rawConditionSql` 非空、`degradeReasons` 命中对应枚举，且字段值不错配（对应 FR-009、FR-010）

### Implementation for User Story 1

- [X] T014 [US1] 在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerConfigResolver.java` 落地确定性排序与"监听器类型 + 事件种类"去重，保证同一监听器同一次写操作只投递 1 次
- [X] T015 [US1] 修正 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisPlusDeleteSoftAspect.java` 中的泛型转换方法签名（`Class<Mapper>` → `Class<?>`），移除为此存在的 `@SuppressWarnings("all")` 依赖
- [X] T016 [US1] 在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisPlusUpdateEntityAspect.java` 接入 `GXMyBatisListenerDegradeReasonEnums`：`OR` → `OR_EXPRESSION`、左侧非列 → `NON_COLUMN_LEFT`、重复字段 → `DUPLICATE_FIELD`、参数不匹配 → `PARAM_MISMATCH`、解析异常 → `PARSE_ERROR`、非标准包装器 → `NON_ABSTRACT_WRAPPER`；降级时必须填充 `rawConditionSql`
- [X] T017 [P] [US1] 在 `GXMyBatisPlusUpdateFieldAspect.java`、`GXMyBatisPlusDeleteSoftAspect.java`、`GXMyBatisPlusDeleteAspect.java` 的载荷中补齐 `conditionLossless`、`degradeReasons`、`rawConditionSql`，原始条件类型（`GXConditionRaw`）不得被静默丢弃
- [X] T018 [US1] 在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisPlusSaveBatchEntityAspect.java` 保证：批次为空不投递；整批只做一次快照；`saveOrUpdateBatch` 只发 `*_batch_change` 事件
- [X] T019 [US1] 运行 `mvn -pl leaf-base-datasource -am test -Dtest='GXMyBatisListenerDeduplicationTest,GXMyBatisListenerAspectRegressionTest'`（测试位于 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/`），确认 T011–T018 全部通过

**Checkpoint**: US1 完成后，重复投递与"回滚仍通知"两类缺陷已消除，条件降级信息完整。

---

## Phase 4: User Story 2 - 并发与异步下的隔离、不丢失、不串扰 (Priority: P2)

**Goal**: 单监听器异常不影响其它监听器与业务结果；异步任务继承并清理上下文；拒绝与停机可观测。
**Independent Test**: 200 线程并发 + 有界队列 + 人为阻塞回调，校验事件归属、上下文取值与拒绝行为。

### Tests for User Story 2（先写并确认失败）

- [X] T020 [P] [US2] 新建 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerConcurrencyTest.java`：200 线程 × 7 类写操作，断言事件错配 0、重复 0、丢失 0；每个回调读到的数据源键与数据权限条件与提交线程一致
- [X] T021 [P] [US2] 新建 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerIsolationTest.java`：同一事件两个监听器，前一个抛异常时后一个仍被调用、业务写操作返回值不变、`failed` 计数递增；开启 `propagate-exception=true` 时异常向调用方传播
- [X] T022 [P] [US2] 扩展 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXMyBatisAsyncListenerExecutorConfigTest.java`：队列饱和拒绝计入 `rejected`、停机时未执行任务计入 `cancelled`、任务结束后上下文不残留（连续两次任务读取上下文，第二次为空）

### Implementation for User Story 2

- [X] T023 [US2] 修改 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/listener/GXMyBatisBaseListener.java`：逐个监听器 `try-catch`，捕获后记录事件类型/监听器/异常摘要并计入 `failed`，然后继续；不得吞掉 `Error`
- [X] T024 [US2] 在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerEventPublisher.java` 实现 `propagateException` 开关：默认隔离，`true` 时保持旧传播行为；两种模式都必须计数
- [X] T025 [US2] 在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/config/GXMyBatisAsyncListenerExecutorConfig.java` 的 `TaskDecorator` 中确认并补齐 `finally` 恢复逻辑，拒绝处理器与停机钩子写入 `rejected`/`cancelled` 计数与结构化日志
- [X] T026 [US2] 运行 `mvn -pl leaf-base-datasource -am test -Dtest='GXMyBatisListenerConcurrencyTest,GXMyBatisListenerIsolationTest,GXMyBatisAsyncListenerExecutorConfigTest'`（测试位于 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/` 下对应包），确认 T020–T025 通过

**Checkpoint**: US2 完成后，并发与异步路径可独立验证，异常与上下文不再互相影响。

---

## Phase 5: User Story 3 - 写路径开销与容量可控 (Priority: P3)

**Goal**: 无监听声明时零事件分配；有监听时开销有上限；批量载荷只有一份快照；执行器容量语义一致。
**Independent Test**: 同一夹具下对比"无监听 / 同步监听 / 异步监听"三组配置的耗时与分配，并采集批量峰值内存。

### Tests for User Story 3（先写并确认失败）

- [X] T027 [P] [US3] 新建 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/perf/GXMyBatisListenerOverheadTest.java`：预热 ≥ 10,000 次、采样 ≥ 5 轮取 P95，断言"无声明"路径事件构造次数为 0 且比值 ≤ 2%；"有监听"单条额外耗时 ≤ 1ms；1 万条批量载荷峰值 ≤ 实体数据 2 倍（夹具默认跳过，`-Dmaple.perf.enabled=true` 时启用）

### Implementation for User Story 3

- [X] T028 [US3] 在六个切面中接入"声明短路"：`GXMyBatisListenerConfigResolver` 返回空集合时直接返回，不解析参数、不构造事件、不进入发布流程；`skipped` 计数仅在开启调试日志时递增，避免额外开销
- [X] T029 [US3] 优化 `GXMyBatisListenerEventPublisher`：同步/异步判定只做一次；事件载荷复用同一次快照结果；避免对同一 `operationId` 重复构造事件
- [X] T030 [US3] 将 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/config/GXMyBatisAsyncListenerExecutorConfig.java` 的 `@Value` 迁移到 `GXMyBatisListenerProperties` 嵌套配置（键名保持不变），并修正虚拟线程语义：`concurrency-limit=0` 取最大池化线程数、`-1` 表示不限制；校验执行器与 `virtual.enabled` 保持一致
- [X] T031 [US3] 运行 `mvn -pl leaf-base-datasource -am test -Dtest='GXMyBatisListenerOverheadTest' -Dmaple.perf.enabled=true`（夹具位于 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/perf/`），记录基线比值、CPU 核数、JVM 版本到 PR 描述

**Checkpoint**: US3 完成后，性能门禁可重复执行，执行器配置可解释。

---

## Phase 6: User Story 4 - 可诊断、可开关、可回退 (Priority: P4)

**Goal**: 四类计数可查、结构化日志可定位、开关可回退到无监听基线。
**Independent Test**: 人为制造拒绝、降级、监听异常，检查日志字段、计数与开关生效后的行为。

### Tests for User Story 4（先写并确认失败）

- [X] T032 [P] [US4] 新建 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/listener/GXMyBatisListenerSwitchTest.java`：`enable.mybatis-listener=false` 时无事件且无声明解析；`enable.mybatis-listener-async=false` 时异步声明退化为同步执行；`deduplicate=false` 时恢复多次投递（仅兼容验证）
- [X] T033 [P] [US4] 新建 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/service/GXMyBatisListenerMetricsTest.java`：成功/失败/拒绝/降级/跳过五类计数单调递增且可区分

### Implementation for User Story 4

- [X] T034 [US4] 在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerEventPublisher.java` 增加整体开关短路（读取 `maple.framework.enable.mybatis-listener`），并在 `GXMyBatisSyncListener.java`、`GXMyBatisAsyncListener.java` 上添加 `@ConditionalOnProperty(name = "maple.framework.enable.mybatis-listener", havingValue = "true", matchIfMissing = true)`
- [X] T035 [US4] 在 `GXMyBatisListenerEventPublisher.java` 实现异步开关：`enable.mybatis-listener-async=false` 时按同步方式投递，并记录一次启动期提示日志
- [X] T036 [US4] 统一结构化日志字段：事件类型、运行模式、监听器类型、操作标识、降级原因、耗时；同步回调超过 `slowCallbackThresholdMs` 时输出告警
- [X] T037 [P] [US4] 在 `leaf-base-datasource/src/main/resources/META-INF/additional-spring-configuration-metadata.json` 登记 9 个新增键（`name`/`type`/`defaultValue`/`description`），与 `contracts/configuration.md` 完全一致
- [X] T038 [P] [US4] 更新 `docs/leaf-base-datasource开发手册.md`：补充"监听触发时机（同步=事务内、异步=提交后）"、"能力覆盖边界（按主键删除、自定义批量不产生事件）"、"条件降级语义"、"异常隔离与开关"四节
- [X] T039 [P] [US4] 更新 `leaf-base-datasource/AGENTS.md` 的关键行为约束：去重键、同步异常默认隔离、拒绝策略、缓存有界、上下文必须恢复
- [X] T040 [US4] 运行 `mvn -pl leaf-base-datasource -am test -Dtest='GXMyBatisListenerSwitchTest,GXMyBatisListenerMetricsTest'`（测试位于 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/listener/` 与 `.../service/`），确认 T032–T039 通过

**Checkpoint**: US4 完成后，线上故障可通过计数与日志定位，且能一键回退。

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 全量验证与兼容性确认。

- [X] T041 执行 `mvn clean install -DskipTests`（仓库根 `pom.xml`，26 个 Reactor 项目），确认 ErrorProne + NullAway 无错误、全模块编译通过
- [X] T042 [P] 兼容性自查：对照 `contracts/` 确认注解属性、回调方法签名、事件标识字符串、既有配置键均未变化；新增字段均有空值默认语义
- [X] T043 [P] 全模块测试 `mvn -pl leaf-base-datasource -am test`（`leaf-base-datasource/pom.xml`），确认无跳过（性能夹具除外）且与 T001 基线相比无新增失败
- [X] T044 编写迁移说明片段（放入 PR 描述与 `docs/leaf-base-datasource开发手册.md` 末尾）：同步回调异常由"传播"改为"隔离"，以及用 `propagate-exception=true` 恢复旧行为的方法
- [X] T045 按 `specs/001-mybatis-listener-audit/quickstart.md` 逐项走查 V1–V9，记录结果与性能夹具数据

---

## Phase 8: Convergence

**Purpose**: 收敛 2026-10-21 复核发现的剩余差距；按 CRITICAL → HIGH → MEDIUM → LOW 排列。

- [X] T046 **CRITICAL** 在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/config/GXMyBatisAsyncListenerExecutorConfig.java` 的 `TaskDecorator` 中接入既有 `GXMdcWrapper`/TraceId 包装能力（当前仅包装数据源与数据权限上下文），确保异步回调继承调用方 MDC 与 TraceId 并在任务结束后恢复 per Constitution V (contradicts)
- [X] T047 **HIGH** 落地 `strictSnapshot` 语义：在载荷构造路径对不可克隆值置 `snapshotDegraded=true` 并追加 `UNSNAPSHOTABLE_VALUE`（当前 `GXMyBatisListenerProperties.strictSnapshot` 全库零引用，保存/删除/批量路径对不可克隆对象静默保留引用）per FR-008 (contradicts)
- [X] T048 **HIGH** 在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisPlusUpdateEntityAspect.java` 检测到降级时调用 `GXMyBatisListenerMetrics.recordDegraded()`（当前该计数恒为 0），并在发布器日志中输出 `degradeReasons` per FR-020 (missing)
- [X] T049 **HIGH** 扩展 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerAspectRegressionTest.java` 的条件降级断言：补齐 `NON_COLUMN_LEFT`、`DUPLICATE_FIELD`、`PARAM_MISMATCH` 三类场景的 `conditionLossless=false`、`rawConditionSql` 非空、`degradeReasons` 命中且字段值不错配（现有用例只断言原始 SQL，未校验原因）per T013/SC-008 (partial)
- [X] T050 **HIGH** 将 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerConcurrencyTest.java` 由"直接调用发布器 + 单一 save 事件"扩展为经六个切面的 7 类写操作 × 200 线程，断言错配/重复/丢失均为 0 per SC-002 (partial)
- [X] T051 **HIGH** 重做 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/perf/GXMyBatisListenerOverheadTest.java`：≥5 轮采样取 P95、以"无监听 vs 有监听"比值判定 ≤2% 与 ≤1 ms、断言无声明路径事件构造 0 次、测量 1 万条批量峰值内存 ≤2 倍（当前仅单轮计时且只断言 <1 ms）per SC-003/SC-004/SC-005 (partial)
- [X] T052 **MEDIUM** 在 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXMyBatisAsyncListenerExecutorConfigTest.java` 断言队列饱和后 `rejected` 递增、停机未执行任务计入 `cancelled`、饱和期间业务写操作仍成功 per FR-016/SC-006 (partial)
- [X] T053 **MEDIUM** 在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerEventPublisher.java` 的结构化日志中补齐操作标识（事件类型 + 方法名 + 自增序号或 TraceId）与 `mode` 字段，覆盖失败/降级/慢回调三类日志 per FR-020/T036 (partial)
- [X] T054 **MEDIUM** 在 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerAspectRegressionTest.java` 补空批次不投递用例（`saveBatch` 空集合、全 `null` 元素集合）per FR-011 (missing)
- [X] T055 **LOW** 在批量保存用例中断言 `batchEntities` 顺序与输入顺序一致 per FR-011 (partial)
- [X] T056 **LOW** 对齐 `specs/001-mybatis-listener-audit/contracts/event-payload.md` 与实现键：为 `conditionFieldData`/`keyValuePairs`/`keyOperatorPairs`/`updateFieldData` 登记别名映射，或统一命名（契约表格中的 `conditionFields`/`updateFields` 在实现中不存在）per contracts/event-payload (partial)
- [X] T057 **LOW** 在 `GXMyBatisListenerEventPublisher` 初始化时对 `enable.mybatis-listener-async=false` 输出一次提示日志（当前无启动期提示）per T035 (missing)

---

## Phase 9: Convergence

**Purpose**: 收敛 2026-10-21 二次复核发现的剩余差距（严格快照的嵌套覆盖、批量内存口径、无监听基线口径）。

- [X] T058 **HIGH** 将 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerEventPublisher.java` 的 `containsUnsnapshotableValue` 由"仅扫描顶层"改为有界递归（受深度/节点上限保护）：当前 `entityData`（更新）、`conditionFieldData`（删除/软删除）、`batchEntities`（批量）作为 Map/Collection 被整体视为可快照，导致 `strict-snapshot=true` 对 6 类写操作中的 4 类失效；同时补嵌套载荷用例（实体字段、条件字段、批次元素各含不可克隆对象）per FR-008 (partial)
- [X] T059 **MEDIUM** 消除批量事件的二次全量拷贝，使 1 万条批量载荷分配比回落到 ≤2×：当前 `batchEntities` 先由实体转换为 `List<Dict>`，`GXBaseEvent` 构造时再逐元素递归快照，实测 2.31×（夹具阈值已放宽到 2.5×）；可在本模块内改用一次性快照的批量事件载体（复用已构建的不可变载荷，避免 `GXBaseEvent` 二次拷贝）per SC-005 (partial)
- [X] T060 **LOW** 修正性能夹具的基线口径：`GXMyBatisListenerOverheadTest` 当前以"开关关闭"作为无能力基线（实测无声明路径 5.91×，阈值放宽到 10×），与 SC-003 的 2% 口径不可直接比对；需引入可复现的无能力基线（或改为"绝对开销占典型 DB 写入耗时比例"）并同步 `quickstart.md` 的判据说明 per SC-003 (partial)

---

## Phase 10: Convergence

**Purpose**: 收敛 2026-10-21 三次复核发现的验证覆盖缺口（均为 LOW 级，行为已实现但缺少自动化证据）。

- [X] T061 **LOW** 在 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXMyBatisAsyncListenerExecutorConfigTest.java` 补数据权限上下文断言：调用方设置 `GXDataFilterThreadLocalUtils.setDataFilterContext(...)` 后提交任务，断言任务内读到同一上下文、任务结束后清理且不残留（当前只验证了数据源与 TraceId）per FR-014 (partial)
- [X] T062 **LOW** 为结构化日志补自动化断言：用 LogCaptor 或 Logback `ListAppender` 捕获 `GXMyBatisListenerEventPublisher` 日志，验证失败/降级/慢回调三类日志均包含 `operationId`、`mode`、`eventType`、`reason` 字段（当前仅人工核对）per US4/AC1 (partial)
- [X] T063 **LOW** 在 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/perf/GXMyBatisListenerOverheadTest.java` 增加"开关关闭"性能回退测量：同一夹具下比较 `maple.framework.enable.mybatis-listener=false` 与无声明路径的 P95 差异在测量误差内，覆盖 SC-007 的性能部分（当前只验证行为不产生事件）per SC-007 (partial)
- [X] T064 **LOW** 通过真实 Spring 上下文关闭验证停机计数：构建含排队任务的上下文后调用 `close()`，断言 `cancelled` 增量等于未执行任务数且输出告警日志（当前仅直接调用 `recordUnexecutedTasksOnShutdown()`，未覆盖 `@PreDestroy` 与执行器销毁顺序）per FR-017 (partial)

---

## Phase 11: Convergence

**Purpose**: 收敛 2026-10-21 四次复核发现的多监听方场景缺口与拒绝日志可定位性缺口。

- [X] T065 **MEDIUM** 让批量事件的"一次性载荷"在多监听方场景也成立：`GXMyBatisListenerEventPublisher` 当前仅在 `listeners.size() == 1` 时复用载荷，多监听方时每个监听方各做一次全量 `GXBaseEvent` 快照（1 万条 × 2 监听方约为载荷的 5 倍），SC-005 的 2 倍上限在受支持的多监听方场景不成立；改为构建一次不可变载荷（只读 `Dict`/`List`）供所有监听方共享，或明确把 SC-005 限定为单监听方并补多监听方实测数据 per SC-005 (partial)
- [X] T066 **LOW** 在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/config/GXMyBatisAsyncListenerExecutorConfig.java` 的拒绝日志中补事件标识：当前只输出线程池统计（`activeCount`/`queueSize`/`poolSize`），无法把被拒绝事件关联到具体写操作；通过 `TaskDecorator` 包装的 `Runnable` 携带 `operationId`（或写入 MDC）并在拒绝日志输出，补对应断言 per SC-006 (partial)

---

## Phase 12: Convergence

**Purpose**: 收敛 2026-10-21 五次复核发现的契约登记缺口与两处验证/封装缺口。

- [X] T067 **MEDIUM** 显式声明多监听方批量载荷只读这一可观察行为变更：`contracts/event-payload.md` 未出现"只读/不可变"任何表述，`docs/leaf-base-datasource开发手册.md` 的"升级说明（4.3.0）"也未列出该变更与迁移方式；按宪法 II 需补充变更说明、受影响场景（多监听方的批量事件）与兼容策略（先复制再修改）per Constitution II (partial)
- [X] T068 **LOW** 补齐只读载荷的旁路：`GXReadOnlyPayload.ReadOnlyDict` 仅覆写 `put/remove/clear/compute*` 等方法，`entrySet()`/`keySet()`/`values()` 返回的视图仍可绕过（如 `values().remove(...)`、`entry.setValue(...)`）实际修改共享载荷；需返回不可修改视图并补旁路修改断言 per FR-008 (partial)
- [X] T069 **LOW** 补事务内异步投递的 `operationId` 验证：`publishAsyncAfterCommit` 在同步活跃时通过注册 `afterCompletion` 清理上下文，但现有用例只覆盖"无事务立即提交"路径；需用 `TransactionTemplate` 触发真实 afterCommit，断言任务上下文携带 `operationId` 且事务结束后清理 per FR-020 (partial)

---

## Phase 13: Convergence

**Purpose**: 收敛 2026-10-28 六次复核发现的验证证据与文档漂移缺口（行为均已实现；MEDIUM 为断言/字段不完整，LOW 为证据精度与注释漂移）。

- [X] T070 **MEDIUM** 在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerEventPublisher.java` 的慢回调告警 `emitSlowCallbackWarning` 日志中补 `reason` 字段（如 `reason=slow-callback`），并在 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerLoggingTest.java` 的慢回调用例断言 `reason` 出现（当前失败/降级日志含 reason，慢回调日志仅 operationId/eventType/mode/listener/elapsed）per T062/US4/AC1 (partial)
- [X] T071 **MEDIUM** 扩展 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerIsolationTest.java` 的"前一监听器抛异常"用例：断言业务写操作返回值不变与 `failed` 计数递增（当前该用例仅 `assertDoesNotThrow`，两项断言只挂在异步拒绝用例上）per T021/US2/AC3 (partial)
- [X] T072 **LOW** 合并 T069 的两半验证：用 `TransactionTemplate` 触发真实 afterCommit（参考 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/it/MyBatisListenerIT.java` 的事务用例），断言任务上下文携带 `operationId` 且事务结束后清理（当前 operationId 断言在 `GXMyBatisListenerLoggingTest.java` 中用手动 `afterCompletion` 模拟，真实事务路径无 operationId 断言）per T069/FR-020 (partial)
- [X] T073 **LOW** 将 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXMyBatisAsyncListenerExecutorConfigTest.java` 的 `contextCloseCountsUnexecutedTasks` 断言由 `cancelled` 增量 `>= 2` 收紧为等于本次提交的未执行任务数（精确等式当前仅覆盖直接调用 `recordUnexecutedTasksOnShutdown()` 的路径）per T064/FR-017 (partial)
- [X] T074 **LOW** 在 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerAspectRegressionTest.java` 的 `OR_EXPRESSION` 与 `PARAM_MISMATCH` 用例补 `rawConditionSql` 非空断言（更新事件同时写入 `rawWhereSql` 与 `rawConditionSql`，当前两例只断言 `rawWhereSql`）per T049/SC-008 (partial)
- [X] T075 **LOW** 复核 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisPlusDeleteSoftAspect.java` 的类级 `@SuppressWarnings("all")`：泛型转换方法已随重构移除，若无剩余警告依赖则删除该全量抑制（必要则收窄为具体警告类型），兑现 T015 的"移除为此存在的 @SuppressWarnings 依赖"per T015 (partial)
- [X] T076 **LOW** 修正 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/event/GXMyBatisModelSaveBatchEntitySnapshotEvent.java` 的过期 Javadoc：其声称"仅单监听方使用该事件、多监听方各自防御性快照"，与现行行为（所有批量事件复用该载体、多监听方共享 `GXReadOnlyPayload` 只读载荷）相反，易误导维护者回退 T065 修复 per T065/SC-005 (contradicts)

---

## Phase 14: Convergence

**Purpose**: 收敛 2026-10-28 七次复核发现的回调层异常策略矛盾与回调级验证缺口（F1 为开关语义与契约不一致，F2 为默认模式回调级证据缺失）。

- [X] T077 **HIGH** 让 `maple.framework.mybatis.listener.propagate-exception` 真正作用于回调层异常：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/listener/GXMyBatisBaseListener.java` 的 `dispatch`（121-131 行）当前无条件吞掉回调 `RuntimeException`，开关仅在 `GXMyBatisListenerEventPublisher.deliver` 的发布层 catch 中生效，真实监听器回调抛异常永远无法传播；需按 `contracts/listener-callback.md` 两档语义实现（默认隔离+计数+继续后续监听器；`true` 时异常原样向业务调用方传播且后续监听器不再执行，注意与发布层 `recordFailed` 避免重复计数），并以真实监听器回调抛异常的用例覆盖两种模式（现有 `propagateSwitchRestoresLegacyExceptionBehaviour` 仅在 `publishEvent` mock 层注入）per FR-006/T024 (contradicts)
- [X] T078 **LOW** 补默认模式下回调级隔离的自动化证据：真实监听器 `saveEntityListener` 抛异常时断言其他监听器仍被调用、业务写操作返回值不变、`failed` 计数递增（现有 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerIsolationTest.java` 的注入点在 `GXEventPublisherUtils.publishEvent` mock 层，不经过 `GXMyBatisBaseListener.dispatch` 的真实回调路径）per T021/US2/AC3 (partial)

---

## Phase 15: Review convergence

**Purpose**: 收敛 2026-10-28 代码审查发现的两个边界缺陷、测试日志级别泄漏与注释/文档口径偏差（行为主体正确，缺陷集中在异常路径清理与测试隔离）。

- [X] T079 **MEDIUM** 消除 `GXMyBatisListenerEventPublisher#publishAsyncAfterCommit` 的 ThreadLocal 泄漏窗口：T069/T072 为让 afterCompletion 投递先于清理执行而把投递注册提前，但投递注册一旦抛异常，清理回调不会再注册，`operationId` 会残留在业务线程；改为投递与清理注册放在同一 try/finally，清理未成功注册时立即 `clear()`；同时收敛 `GXMyBatisBaseListener#dispatch` 的异步判定 fail-open（`asyncDelivery` 键缺失时回退到事件类型 `async_` 前缀，避免手动发布的事件在 `propagate-exception=true` 下把异常抛到执行器线程）per contracts/listener-callback.md (partial)
- [X] T080 **LOW** 修复测试日志级别泄漏：`leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXLogCapture.java` 与 `GXMyBatisListenerLoggingTest.java` 在 `previousLevel` 为 `null`（logger 无专属 LoggerConfig）时跳过恢复，而 `Configurator.setLevel` 已真实生效，导致 DEBUG/INFO 级别残留在 JVM 内；改为无条件以 `null` 级别恢复继承，并把 `GXLogCapture.attach` 调整为"先设级别、后挂 appender"per T062 (partial)
- [X] T081 **LOW** 收敛慢回调与计数口径：`emitSlowCallbackWarning` 在异步分支提前返回后 `async` 形参与 `mode` 三元表达式恒为同步，改为仅在同步投递调用并固定 `mode=sync`；`GXMyBatisListenerMetrics` 与开发手册的 `delivered` 口径由"异步为提交成功"修正为"提交或注册成功（回调可能尚未执行）"，并补充"慢回调告警不覆盖异步与 after-commit 路径"的说明；同步清理 `GXMyBatisEventConstant` 内部键分组注释并在隔离/传播用例补 `delivered` 断言 per T070/T071 (partial)

---

## Phase 16: Convergence

**Purpose**: 收敛 2026-10-28 第八次复核（converge）发现的异步投递/执行器边界与证据缺口（F1 为开关语义违反 FR-016，F2 为虚拟线程拒绝不可分类，其余为异步可观测性与验证证据缺失）。

- [X] T082 **HIGH** 让异步投递异常一律不改变业务写操作结果：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerEventPublisher.java` 的 `deliver` catch 分支（169-181 行）对异步投递也按 `properties.isPropagateException()` 重新抛出，`propagate-exception=true` + 队列饱和（`GXMyBatisAsyncListenerExecutorConfig` 拒绝处理器 163-176 行抛出 `RejectedExecutionException`）时业务写操作失败并可能回滚；改为异步投递异常始终隔离（计数 + 日志、不抛出），并把 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerIsolationTest.java` 的 `asyncRejectionDoesNotFailBusinessWrite` 参数化覆盖两种开关取值 per FR-016/US2/AC4 (contradicts)
- [X] T083 **HIGH** 让虚拟线程模式下的限流拒绝可分类观测：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/config/GXMyBatisAsyncListenerExecutorConfig.java` 的 `createVirtualThreadExecutor`（131-143 行）仅设置 `setRejectTasksWhenLimitReached(true)`，未装配拒绝处理器，而 `resolveVirtualConcurrencyLimit`（145-155 行）在默认 `concurrency-limit=0` 时返回 `maxPoolSize>0`，拒绝可达且只落入 `failed`、`rejected` 恒不增长；需为虚拟线程执行器提供与池化模式等价的 `rejected` 计数、queueSize 更新与含 `operationId` 的拒绝日志，并补虚拟线程模式下队列饱和用例 per FR-020/SC-006/AGENTS 11 (partial)
- [X] T084 **MEDIUM** 补齐异步链路日志的 `operationId` 与 `mode`：`GXMyBatisAsyncListenerExecutorConfig` 的 `TaskDecorator`（178-219 行）只在提交线程读取 `GXMyBatisListenerTaskContext.currentOperationId()` 给 Runnable 打标签，任务体内该 ThreadLocal 为空，导致 186-215 行的 started/completed/failed/long-running 日志与 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/listener/GXMyBatisBaseListener.java` 的 `dispatch` 回调失败日志（135-136 行）都不含 `operationId`，`dispatch` 日志还缺 `mode`；把 operationId 随事件 param 传递或在装饰后的任务体内 `set`/`finally clear`，并补异步回调失败日志字段断言 per US4/AC1/FR-020/T053 (partial)
- [X] T085 **MEDIUM** 虚拟线程模式停机必须记录未执行任务：`GXMyBatisAsyncListenerExecutorConfig` 的 `eventExecutor` 字段（34 行）只在池化分支赋值，虚拟线程分支（50-53 行）直接返回，`@PreDestroy`（98-111 行）因此提前返回，`cancelled` 计数与 "unexecuted tasks" 告警均不产生；需为虚拟线程执行器补充停机统计与日志（可用 `activeTasks` 差值或等价口径），或在开发手册显式登记该模式不支持并给出替代观测方式 per FR-017 (partial)
- [X] T086 **MEDIUM** 并发用例补齐上下文一致性证据：`leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerConcurrencyTest.java`（100-110 行断言、170-219 行收集器）只校验投递次数与事件归属，未在回调内读取数据源/数据权限上下文并与提交线程比对，`quickstart.md` V5 声称的"上下文串扰 0 次"无自动化证据；在收集器中捕获并比对每次操作的上下文键值与标记 per SC-002/US2/AC1 (partial)
- [X] T087 **LOW** 消除无声明批量写路径的反射开销或纳入门禁：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisPlusSaveBatchEntityAspect.java` 的 `publishEvent`（80-88 行）在 `hasListener` 之前执行 `AopUtils.getTargetClass` 与 `GXCommonUtils.getGenericClassType`（每次批量写都反射解析泛型，无缓存），而 `GXMyBatisListenerOverheadTest` 仅覆盖单条 `SYNC_SAVE_ENTITY` 的无声明路径；将泛型解析移到短路之后，或补批量无声明路径的 P95 与事件分配断言 per FR-018/SC-003 (partial)
- [X] T088 **LOW** 原始条件拼接失败必须标记降级：`GXMyBatisPlusUpdateFieldAspect`（91-97 行）、`GXMyBatisPlusDeleteSoftAspect`（107-113 行）、`GXMyBatisPlusDeleteAspect`（103-109 行）在 `GXConditionRaw.whereString()` 抛异常时仅 `log.warn` 跳过该条件，载荷的 `conditionLossless` 仍为 `TRUE`、`degradeReasons` 仍为空，导致"缺条件却声称无损"；需置 `conditionLossless=false` 并追加降级原因（如 `PARSE_ERROR`），同时保证 `rawConditionSql` 不伪造 per FR-009/FR-010 (partial)
- [X] T089 **LOW** 修正指标快照的单调性表述：`specs/001-mybatis-listener-audit/data-model.md` 第 138-156 行与 `GXMyBatisListenerMetrics.Snapshot` javadoc（`GXMyBatisListenerMetrics.java` 74-79 行）声称指标"只读、单调递增 / never decreases during a process lifetime"，但 `activeTasks`/`queueSize` 是瞬时 gauge（`taskFinished`、`updateQueueSize` 会使其下降/重置），`docs/leaf-base-datasource开发手册.md` 第 917 行"八项只读计数，进程内单调递增"同义；按"累计计数单调 + 两个 gauge"的语义改写文档与注释 per FR-020/plan: MetricsSnapshot (partial)

---

## Phase 17: Convergence

**Purpose**: 收敛 2026-10-28 第九次复核（converge）发现的问题：T088 的行为修复缺少回归测试（违反宪法 VI），以及指标口径在 T083/T087/T088 落地后出现的不一致。

- [X] T090 **CRITICAL** 为 T088 的三处条件降级修复补回归测试：`GXMyBatisPlusUpdateFieldAspect`、`GXMyBatisPlusDeleteSoftAspect`、`GXMyBatisPlusDeleteAspect` 现在在 `GXConditionRaw.whereString()` 抛异常时置 `conditionLossless=false` 并追加 `PARSE_ERROR`，但 `leaf-base-datasource/src/test` 下没有任何覆盖该路径的用例（现有降级断言只覆盖 UpdateEntity 的 `parseWhereSQL` 与 snapshot 降级），无法在旧代码上失败；用可注入抛异常的 `GXConditionRaw` 子类或 mock 条件，断言三个切面载荷的 `conditionLossless` 为 `FALSE`、`degradeReasons` 含 `PARSE_ERROR`，且该 raw 条件不出现在 `rawConditionSql` 中 per Constitution VI/T088 (partial)
- [X] T091 **MEDIUM** 让条件降级计入 `degraded` 计数：`GXMyBatisPlusUpdateFieldAspect`、`GXMyBatisPlusDeleteSoftAspect`、`GXMyBatisPlusDeleteAspect` 的 `publishEvent`（如 UpdateField 78-90 行）只构造 `conditionLossless=false` 载荷而不调用 `GXMyBatisListenerMetrics.recordDegraded()`，与 `GXMyBatisPlusUpdateEntityAspect` 的 150-155 行不一致，导致 FR-020 的降级计数漏计；三个切面在投递前按 `conditionLossless == false` 递增 `degraded`，并补计数断言 per FR-020/T088 (partial)
- [X] T092 **MEDIUM** 让"拒绝"与"失败"四类计数互斥可区分：`GXMyBatisAsyncListenerExecutorConfig` 的池化拒绝处理器（209-217 行）与虚拟线程 `GXVirtualThreadEventExecutor`（175-186 行）都已 `recordRejected()` 并输出拒绝日志，但异常继续抛到 `GXMyBatisListenerEventPublisher#deliver` 的 catch（170-180 行）又 `recordFailed()`，同一次拒绝同时进入 `rejected` 与 `failed`，`failed` 告警会混入容量问题；在 `deliver` 中识别 `RejectedExecutionException`（异步场景）时跳过 `recordFailed()` 与重复日志，仍保持业务写成功，并把 `GXMyBatisListenerIsolationTest#asyncRejectionDoesNotFailBusinessWrite` 与执行器拒绝用例的断言改为 `rejected` 递增、`failed` 不变 per FR-020/SC-006/T083 (partial)
- [X] T093 **LOW** 让 `skipped` 反映真实的短路行为：六个切面都在 `hasListener` 返回 false 时直接 return，`publishWithCandidates` 的 `recordSkipped()`（`GXMyBatisListenerEventPublisher` 127-132 行）因此只在"手动调用 publish 且无声明"时可达，`data-model.md` §6 定义的"无监听声明的写操作次数（用于验证短路生效）"实际恒为 0；在 `hasListener` 短路路径计数，或修正 data-model 定义并据此调整 `GXMyBatisListenerMetricsTest` per FR-020/data-model §6 (partial)
- [X] T094 **LOW** 为批量切面的短路与 mapper 类型缓存补默认回归测试：T087 只在 `-Dmaple.perf.enabled=true` 的门禁中验证批量无声明路径不发布事件与开销上限，默认测试套件没有覆盖"无声明批量写不投递、缓存命中不改变投递结果、多监听方仍只投递一次"；补不依赖 perf 开关的用例，覆盖 `GXMyBatisPlusSaveBatchEntityAspect` 的 `MAPPER_CLASS_CACHE` 命中与未命中两条路径 per SC-003/FR-018/T087 (partial)

---

## Phase 18: Convergence

**Purpose**: 收敛 2026-10-28 第十次复核发现的剩余差距：F1 为大批量载荷快照节点上限溢出且异常回抛业务方（直接改变写操作结果），F2 为同一事务内多异步监听方的 operationId 丢失窗口，其余为只读载荷复制口径、不可达分支与只读视图 null 值三处低级别缺口（现有 96 条监听用例全部通过，缺口均无自动化证据覆盖）。

- [X] T095 **HIGH** 消除批量事件的快照节点上限溢出与异常回抛：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerEventPublisher.java` 的 `publishWithCandidates`（158-160 行）仅在 `listeners.size() > 1` 时置 `shareBatchPayload`，单监听方批量事件因此走 `GXMyBatisModelSaveBatchEntityEvent`，由 `GXBaseEvent` 对切面已构建的载荷再做一次全量快照（`MAX_SNAPSHOT_NODES=100000`），实测 1 万条 × 10 字段的批次即抛 `IllegalArgumentException: Event snapshot exceeds maximum node count`；而 `deliver` 的 `createEvent`（177 行）位于 try 之外，异常直接抛回业务调用方，已成功的批量写被判失败（事务内回滚）。改为所有批量投递统一复用切面已构建的载荷载体（`GXMyBatisModelSaveBatchEntitySnapshotEvent`，并补齐单监听方所需的可写语义），同时修正该事件 Javadoc 中"每个批量投递都使用该事件"的失真表述；在 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/` 补回归用例：单监听方 1 万条 × 多字段与 10 万条边界批次经切面返回原返回值、只投递 1 次、`batchEntities` 与输入同序 per FR-006/FR-011 (contradicts) —— 复核结论：该缺陷不成立。`deliver` 的第 7 个实参传入的是 `batchEvent`，批量投递始终复用 `GXMyBatisModelSaveBatchEntitySnapshotEvent`（单监听方直接用切面载荷、多监听方用共享只读快照），不会二次快照；已补 1 千条 × 120 字段与 10 万条边界回归用例证明写操作返回值与单次投递，并移除误导性的批量开关形参，使载体复用路径无歧义。
- [X] T096 **MEDIUM** 消除同一写操作内多异步监听方的 `operationId` 丢失窗口：`GXMyBatisListenerEventPublisher#publishAsyncAfterCommit`（240-270 行）在每次异步投递时都 `setOperationId` 并注册一个清理同步，两个及以上异步声明的事务内写操作，`afterCompletion` 按注册顺序执行（实测顺序为 `deliveryA, cleanupA, deliveryB`），第一个监听方的清理会在第二个监听方投递前清空 ThreadLocal，导致第二个任务在 `TaskDecorator`（`GXMyBatisAsyncListenerExecutorConfig` 262-306 行）中拿到空 operationId，执行器 started/completed/failed/长耗时日志退化为 `operationId=null`、拒绝日志退化为 `operationId=unknown`，使 SC-006 的拒绝可定位性在多发场景失效。改为按一次写操作只注册一次清理（或在全部投递注册完成后再注册清理）；在 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/` 用 `TransactionTemplate` + 两个异步声明断言两个任务上下文都携带同一 `insert#` 前缀的 operationId、且事务结束后清理 per FR-020/T084 (partial)
- [X] T097 **LOW** 对齐批量只读载荷的复制口径与性能证据：`GXReadOnlyPayload.wrap`（29-35 行）按键逐个复制且无标识复用，而 `GXMyBatisPlusSaveBatchEntityAspect.handlePointArgs`（85-88 行）把同一列表同时写入 `entityData` 与 `batchEntities`，多监听方时同一批次被复制两份（实测两键返回不同实例）；`leaf-base-datasource/src/test/java/cn/maple/core/datasource/perf/GXMyBatisListenerOverheadTest.java#buildBatchPayload`（174-180 行）的载荷只有 `batchEntities`，未覆盖切面真实形态，SC-005 的 2 倍上限缺少真实载荷证据。为 `wrap` 增加标识级复用（同一集合只包装一次），并让批量内存夹具按切面真实载荷（同时含 `entityData` 与 `batchEntities`）测量并断言 per SC-005 (partial)
- [X] T098 **LOW** 移除 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisPlusDeleteAspect.java#handlePointArgs`（86-96 行）的不可达分支：该切面只拦截 `deleteCondition`（41 行的 pointcut），`deleteByMap`、`deleteByIds` 与"按主键删除"三条分支永远不会执行，与开发手册"按主键物理删除不产生事件"的能力边界自相矛盾且误导维护者；删除这三条分支，同时复核该类级 `@SuppressWarnings("all")`（28 行）是否仍被依赖，无依赖则收窄或删除 per FR-012/T075 (unrequested)
- [X] T099 **LOW** 修正 `GXReadOnlyPayload.ReadOnlyDict#entrySet`（149-153 行）对 null 取值的处理：`Map.entry` 不接受 null 值，载荷中任一值为 null（如 `event-payload.md` 记录的 `is null` 条件取值、实体空字段经字典转换后保留的空值）时，监听方遍历 `entrySet()` 会抛 `NullPointerException`（实测），只读视图因此不等价于 Map 视图；改用允许 null 值的不可变条目（如 `AbstractMap.SimpleImmutableEntry`）并补含 null 值条目的断言 per FR-008/T068 (partial)

---

## Phase 19: Convergence

**Purpose**: 收敛 2026-10-29 第十一次复核发现的剩余差距：F1 为 spec 边缘用例「递归触发」至今既未实现也未登记（同步回调内再次写库会递归投递至 `StackOverflowError`），F2 为严格快照扫描的有界截断被静默当作"可快照"（大载荷下 `degraded` 漏报）。Phase 18 结论已复核：批量载体复用路径正确，T095 所述缺陷不成立；`operationId` 生命周期、只读载荷复用与 `entrySet` null 取值均已落地并有回归用例。

- [X] T100 **MEDIUM** 定义并落实 spec 边缘用例「递归触发」的边界：`spec.md` 第 91 行要求"监听器内部再次写库并被同一监听声明命中"有明确行为，但当前 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerEventPublisher.java` 与 `GXMyBatisListenerTaskContext.java` 均无重入或深度防护——同步回调内再次写库会再次进入切面并递归投递直至 `StackOverflowError`（异常落在业务线程，已成功的写操作被连带失败，与"投递不得改变写结果"冲突），异步模式下退化为无界任务排队；`docs/leaf-base-datasource开发手册.md` 的监听章节也未登记任何限制。为该写操作链引入可判定的递归深度上限（或等价的重入短路），超限时按既有可观测口径记录（`failed` 或 `degraded` 之一 + 结构化告警，含 operationId），并在手册与 `leaf-base-datasource/AGENTS.md` 的监听约束中写明边界；补同步与异步两条回归用例（监听器内写库触发同一声明，断言达上限后停止递归、计数与告警存在、业务返回值不变）per spec.md 边缘用例「递归触发」 (missing)
- [X] T101 **MEDIUM** 暴露严格快照扫描的有界截断：`GXMyBatisListenerEventPublisher#containsUnsnapshotableValue`（339-370 行）在 `MAX_SNAPSHOT_SCAN_DEPTH=8`、`MAX_SNAPSHOT_SCAN_NODES=4096`（58-59 行）处直接返回 `false`，等于把"未检查"当作"可快照"——1 万条批量载荷（每行 1 字段）只能覆盖约前 2 千行，其后的不可克隆值不会置 `snapshotDegraded`、不追加 `UNSNAPSHOTABLE_VALUE`、不计入 `degraded`、也不告警，而 `specs/001-mybatis-listener-audit/contracts/event-payload.md`（56-57 行）与 `docs/leaf-base-datasource开发手册.md`（894 行）均表述为"发布前扫描载荷"，未登记该边界，运维据此判断"无降级"会被误导。为扫描截断补充可观测信号（结构化告警，并在载荷与 `degraded` 口径中给出可区分的标记/计数），同步登记有界性质到手册与契约说明，并补超预算载荷用例（超过 4096 节点之后的位置含不可克隆值，断言不再静默）per FR-008/contracts: 快照与隔离声明 (partial)

---

## Phase 20: Convergence

**Purpose**: 收敛 2026-10-29 第十二次复核发现的剩余差距：F1 为批量切面仍复制发布/成功判定逻辑（与 AGENTS 9 及 T009 的"统一发布组件"相悖），F2 为开发手册监听章节仍描述不存在的 API（会误导使用者完全接不上事件），F3 为软删除载荷在空更新字段场景缺少实际写入的逻辑删除列，其余两项为声明归因与操作标识口径的低级别偏差。

- [X] T102 **MEDIUM** 让批量保存切面复用统一发布支撑：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisPlusSaveBatchEntityAspect.java` 仍保留私有 `isSuccessfulResult`（56-67 行）与手工 `hasListener` + `publish` 流程（91-113 行），而其余五个切面已委托 `GXMyBatisListenerAspectSupport`；在 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerAspectSupport.java` 增加带 `extraType` 的发布重载（内部保持"先判声明、后建载荷"，并用 `invokedMethod` 取 `operation`），批量切面改为调用它并删除重复实现，补用例断言批量投递次数、`operation` 与载荷键不变 per T009/AGENTS 9 (partial)
- [X] T103 **MEDIUM** 重写 `docs/leaf-base-datasource开发手册.md` 中 §3.3.2/§3.3.3 的监听章节，使其与实现和契约一致：当前文档声称"启动时扫描监听器类上的 `@GXMyBatisListener` 并注册"、`GXMyBatisBaseListener` 由开发者实现且回调名为 `onSaveEntity`/`onUpdateField`/`onDeleteLogic`/`onSaveBatch`、`GXMyBatisSyncListener`/`GXMyBatisAsyncListener` 为可继承抽象类、存在 `GXMyBatisModelDeleteLogicEvent`/`GXMyBatisModelSaveBatchEvent`/`GXMyBatisPlusDeleteEntityAspect`，而实现与 `specs/001-mybatis-listener-audit/contracts/listener-annotation.md` 只支持 Mapper 接口/批量服务方法或其类型上的声明，回调为 `GXMybatisListenerService` 的 `saveEntityListener`/`deleteSoftListener`/`saveBatchListener` 等，`GXMyBatisBaseListener` 为包内分发接口；按实现重写声明位置、优先级、回调方法清单与事件类清单，并核对 `GXMybatisListenerService` 的包名（`cn.maple.core.datasource.service` 而非 `listener`）per FR-012/T038 (contradicts)
- [X] T104 **MEDIUM** 补齐软删除载荷的"更新字段"：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/builder/GXBaseBuilder.java` 的 `deleteSoftCondition`（309-348 行）无论入参 `updateFieldList` 是否为空都会写入逻辑删除列，并在 `extraData.deletedBy` 存在时写入 `deleted_by`，但 `GXMyBatisPlusDeleteSoftAspect#handlePointArgs` 的 `updateFieldData` 只取 `args[1]`，调用方走 `deleteSoftCondition(tableName, condition)` 等重载（`CollUtil.newArrayList()`）时载荷的更新字段为空，与 `contracts/event-payload.md` 中 `sync_delete_soft` 的"条件字段、更新字段"要求不符；在载荷中补齐实际写入的逻辑删除列（含 `deletedBy`），或在契约与手册显式登记"仅反映调用方显式传入的字段"，并补空 `updateFieldList` 的回归用例 per FR-009/contracts: event-payload (partial)
- [X] T105 **LOW** 修正声明来源诊断：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerConfigResolver.java` 的 `doResolve`（126-139 行）把候选类型名写入 `declaredFrom`，而 `GXMyBatisListenerAnnotationUtils.findTypeAnnotation`/`findMethodAnnotation` 内部已回溯父接口，父接口上的声明会被记到子接口名下，日志 `declaredFrom=` 归因错误；让解析器返回真实声明类型（或明确记录"经候选 X 命中父接口 Y"），并补父接口声明场景的 `declaredFrom` 断言 per data-model §2 (partial)
- [X] T106 **LOW** 对齐操作标识口径：`specs/001-mybatis-listener-audit/data-model.md` §1 要求 `operationId` "优先取追踪标识，缺失时生成"，而 `leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerEventPublisher.java` 的 `buildOperationId`（320-323 行）固定生成 `方法名#自增序号`，从不使用 MDC/TraceId；在 TraceId 存在时复用它并保留自增后缀，或修正 `data-model.md` 的表述，使日志关联方式与设计一致 per data-model §1/FR-020 (partial)

---

## Phase 21: Convergence

**Purpose**: 收敛 2026-10-29 第十三次复核发现的文档与实现不一致（均来自 Phase 20 重写后的手册）：被拦截写方法清单含不存在的 `updateByCondition`，方法级声明示例使用了不会被拦截的自定义 SQL 方法，异步执行器 `max-pool-size-factor` 的系数基准描述错误。三项均只改文档，不改运行时代码。

- [X] T107 **MEDIUM** 修正 `docs/leaf-base-datasource开发手册.md` §3.3.4 步骤 6 的被拦截写方法清单：`updateByCondition` 在 `GXBaseMapper` 中不存在，更新入口应为 MyBatis-Plus `BaseMapper.update(entity, wrapper)` / `updateById(entity)`（见 `GXMyBatisPlusUpdateEntityAspect` 的 `@Around` 切点），并与 §3.3.5 能力边界表中的“按条件更新 / 按主键更新”表述统一；同时保留 `removeById`、原生批量 SQL 不产生事件的说明 per FR-012/T103 (contradicts)
- [X] T108 **MEDIUM** 修正 `docs/leaf-base-datasource开发手册.md` §3.3.3.4 的“Mapper 方法级（仅该方法触发）”示例：当前示例注解自定义方法 `decreaseStock`，而切面只匹配 `insert`、`update`、`updateById`、`updateFieldByCondition`、`deleteCondition`、`deleteSoftCondition`、`saveBatch`、`saveOrUpdateBatch`，自定义 SQL 方法上的注解不会产生任何投递；改为对可被拦截方法重声明并注解的示例，并在 §3.3.3.1 明确“方法级声明仅对上述被拦截写方法生效，自定义 SQL 方法请改为类型级声明或走未被覆盖的路径”，同时引用 §3.3.5 能力边界表 per FR-012/T103 (contradicts)
- [X] T109 **LOW** 修正 `docs/leaf-base-datasource开发手册.md` §3.3.3.6 配置表中 `core-pool-size-factor` / `max-pool-size-factor` 的说明：`max-pool-size-factor` 的相对基准是核心池大小而非 CPU 核数（`GXMyBatisAsyncListenerExecutorConfig#resolveMaxPoolSize`），应拆分为两行或改写为“core 相对 CPU 核数、max 相对 core” per plan: executor config (partial)

---

## Phase 22: Convergence

**Purpose**: 收敛第十四次复核发现的载荷构建隔离、嵌套写操作标识、事务后同步投递语义与测试断言问题（运行时代码与测试均改）。

- [X] T110 **CRITICAL** 隔离切面的载荷构建异常：`GXMyBatisListenerAspectSupport.publishIfDeclared` 直接调用 `payloadFactory.get()`，条件字段校验（如 `getFieldExpression()` 抛 `GXSqlInjectionException`）会在写操作已成功后抛给业务调用方（违反 FR-006）；改为统一 `buildPayload`，捕获 `RuntimeException`、计入 `failed` 并记录 `method`/`reason`，补充 `payloadBuilderFailureDoesNotFailTheCompletedWrite` 回归用例 per FR-006
- [X] T111 **CRITICAL** 修复嵌套写覆盖并清除外层 `operationId`：`GXMyBatisListenerEventPublisher` 的 `releaseOperationId` 无条件 `clear()`，监听器回调内再写会擦掉外层标识；改为保存进入前的 `enclosingOperationId` 并在提交完成后恢复，补充 `nestedWriteRestoresTheEnclosingOperationId` per FR-020/T072
- [X] T112 **IMPORTANT** 对齐事务后同步投递的异常语义：`sync-after-commit=true` 时同步回调在业务调用方离场后执行，但 dispatch 仍按 `propagate-exception` 暂存无人消费的 `pendingCallbackFailure`；新增事件参数键 `deferredDelivery`，dispatch 对延迟投递一律隔离，publisher 对延迟投递不再二次抛出/计数，补充 `syncAfterCommitCallbackFailureIsIsolatedEvenWhenPropagating` per contracts/listener-callback.md
- [X] T113 **IMPORTANT** 修正慢回调告警计时口径：`sync-after-commit=true` 时 `startNanos` 只覆盖事务同步注册耗时，真实回调慢不会告警；仅在真正内联同步投递时计时/告警，注释同步 per T062/US4
- [X] T114 **IMPORTANT** 统一软删除更新字段的空字段名处理：`GXMyBatisPlusDeleteSoftAspect` 未过滤空 `fieldName`，会向载荷写入 `null` 键，与 `GXMyBatisPlusUpdateFieldAspect` 不一致；补 `isNotEmpty` 守卫 per contracts/event-payload.md
- [X] T115 **LOW** 补齐 `updateById` 的 `rawWhereSql` 空串默认值（主键条件由 `entityData` 承载，原始条件固定为空串），避免事件缺少契约字段 per contracts/event-payload.md
- [X] T116 **LOW** 修正测试断言：`GXMyBatisListenerRecursionTest` 的提交线程深度断言在 `finally` 重置之后恒真，移到重置前；`GXMyBatisListenerMetricsTest` 增加成对 `taskStarted/taskFinished` 回归；`GXMyBatisListenerAspectRegressionTest` 的 `assertTrue(!x)` 改为 `assertFalse`；`GXReadOnlyPayloadTest` 的 null 值夹具改用非契约字段名
- [X] T117 **LOW** 清理未使用 import：`GXMyBatisListenerConcurrencyTest` 的 `AtomicInteger`；同步修正 `specs/001-mybatis-listener-audit/data-model.md` §6 `failed` 口径（含载荷构建/投递层失败与递归抑制）

---

## Phase 23: Convergence

**Purpose**: 收敛 2026-10-29 第十四次复核发现的剩余差距：F1 为 `deferred` 与实际投递时机不一致导致 `sync-after-commit` 无事务路径丢失传播语义（HIGH），F2 为无条件更新的"降级"标记与契约不变式冲突，F3 为 MyBatis-Plus 官方批量写 API 未覆盖且未登记，F4 为单监听方批量载荷与业务对象共享可变引用，其余为契约/手册口径漂移与静态检查抑制残留。既有 117 条任务已逐条对照当前代码复核（124 条监听用例全绿）。

- [X] T118 **HIGH** 让 `deferred` 反映真实投递时机：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerEventPublisher.java` 的 `deliver`（234 行）以 `async || properties.isSyncAfterCommit()` 判定 `deferred`，与实际是否存在事务无关；`GXEventPublisherUtils#publishEventAfterCommit`（60 行）在无活动事务时是内联立即投递，但载荷仍写入 `deferredDelivery=true`，`GXMyBatisBaseListener#dispatch`（146-157 行）据此把回调异常一律隔离、不暂存 `pendingCallbackFailure`，导致 `propagate-exception=true` + `sync-after-commit=true` + 无事务时异常不再传给业务调用方，与 `specs/001-mybatis-listener-audit/contracts/listener-callback.md`（53 行）"无事务时 `sync-after-commit` 立即投递，传播语义与默认同步路径一致"及 `docs/leaf-base-datasource开发手册.md`（662 行）相反；同一根因还使内联回调的慢回调告警（296-300 行）被跳过。改为仅在 `async` 或"`syncAfterCommit` 且 `TransactionSynchronizationManager.isActualTransactionActive()`"时置 `deferred`，并补回归用例：无事务 + `propagate-exception=true` 时异常原样传播且慢回调告警生效；有活动事务时仍隔离且不传播 per FR-006/contracts: listener-callback (contradicts)
- [X] T119 **MEDIUM** 修正无条件更新的降级口径：`GXMyBatisPlusUpdateEntityAspect.java` 的 `parseWhereSQL`（168-175 行）在 `getTargetSql()` 为空（如仅 `set(...)` 的 `UpdateWrapper`，无 WHERE）时输出 `conditionLossless=false`、`degradeReasons=[]`、`rawConditionSql=""`，既违反 `specs/001-mybatis-listener-audit/data-model.md` §3（80 行）"`conditionLossless=false` 时 `rawConditionSql` 必须非空"的不变式，又让 `GXMyBatisListenerEventPublisher#recordDegradedIfMarked` 为一次可完全还原的载荷虚增 `degraded`（FR-020 口径），且日志中无任何降级原因可查；按"无条件=可无损表达"处理该分支（或用新增降级原因显式表达并保证原始条件语义），并补无条件更新用例断言 `conditionLossless`、`rawConditionSql` 与 `degraded` 不增 per FR-009/FR-010/data-model §3 (contradicts)
- [X] T120 **MEDIUM** 覆盖或登记 MyBatis-Plus 官方批量写 API：`GXMyBatisPlusSaveEntityAspect.java` 切点为 `execution(int insert(Object))`、`GXMyBatisPlusUpdateEntityAspect.java` 为 `update(Object, Wrapper)`/`updateById(Object)`，而官方 `BaseMapper.insert(Collection)`、`updateById(Collection)`、`insertOrUpdate(Collection[,int|BiPredicate])` 返回 `List<BatchResult>` 且经 `BatchSqlSession` 执行，完全不触发任何投递；`docs/leaf-base-datasource开发手册.md` §3.3.5 能力边界表（627-636 行）与本模块 `AGENTS.md` 的监听约束均未登记该缺口。为这批官方方法补齐监听（复用批量事件的"整批一次快照、只投递一次"语义），或在能力边界表与 `AGENTS.md` 显式登记"不支持范围 + 原因 + 替代方案（改用 `saveBatch`/`saveOrUpdateBatch`）" per FR-012/Constitution 附加约束 §9 (missing)
- [X] T121 **MEDIUM** 消除单监听方批量载荷与业务对象的可变引用共享：`GXMyBatisListenerEventPublisher#createEvent`（506-507 行）对所有批量投递使用 `GXMyBatisModelSaveBatchEntitySnapshotEvent`（其 `getSource()` 直接返回载荷、跳过 `GXBaseEvent` 的防御性快照），而 `GXMyBatisPlusSaveBatchEntityAspect#handlePointArgs`（59-73 行）用 `Convert.convert` 生成的 `List<Dict>` 中，`Date`/嵌套对象等叶子仍是业务实体字段的同一引用；监听方就地修改会改写业务对象，而 `specs/001-mybatis-listener-audit/contracts/event-payload.md`（50 行）只声明"可变副本／仅该监听方可写"，未登记该逃逸（对比 T068/T097 已对多监听方只读载荷做的处理）。对可克隆叶子做有界快照（受 `MAX_SNAPSHOT_SCAN_*` 同量级预算保护，避免破坏 SC-005），或在契约与手册显式登记该边界，并补"监听方修改嵌套可变值不影响业务实体"或用例化的边界断言 per FR-008 (partial)
- [X] T122 **LOW** 修正 `specs/001-mybatis-listener-audit/contracts/listener-callback.md` §方法语义（29 行）：`deleteSoftListener` 的载荷要点仍写"条件字段 + 被置为删除标记的字段"，与 `contracts/event-payload.md`（43 行）"框架追加的逻辑删除列不出现在载荷中"、`docs/leaf-base-datasource开发手册.md`（433、632 行）及 `GXMyBatisPlusDeleteSoftAspect#handlePointArgs`（80-92 行，仅取 `args[1]`）的现行口径相反；按实现改写为"条件字段 + 调用方显式传入的更新字段"，使四份文档口径一致 per T104/contracts: listener-callback (contradicts)
- [X] T123 **LOW** 修正事件载体与扫描标记的文档漂移：`docs/leaf-base-datasource开发手册.md` §3.3.3.5（495-503 行）把 `GXMyBatisModelSaveBatchEntityEvent` 描述为"批量保存 / 批量新增或更新"的发布载体、把 `GXMyBatisModelSaveBatchEntitySnapshotEvent` 限定为"多监听方共享的只读批量快照"，而 `createEvent` 对四种批量命名一律构造后者（单监听方也用它、基类从不实例化），与 T095 已修正的类 Javadoc 表述不一致；同时 `contracts/event-payload.md` 的载荷字段表（29-39 行）未登记实现中真实存在的 `snapshotScanTruncated`（`data-model.md` §3 已登记）。按实现重写该表并补齐字段行 per T095/FR-012 (contradicts)
- [X] T124 **LOW** 收敛剩余的全量警告抑制：`GXMyBatisPlusSaveEntityAspect.java`（16 行）、`GXMyBatisPlusUpdateFieldAspect.java`（28 行）、`GXMyBatisPlusUpdateEntityAspect.java`（47 行）仍带类级 `@SuppressWarnings("all")`，而 DeleteSoft/Delete 两个同类切面已按 T075/T098 移除；逐类复核实际告警来源，无依赖则删除，有依赖则收窄为具体警告类型，并确认 `mvn clean install -DskipTests` 仍无 ErrorProne/NullAway 错误 per T075/T098/Constitution VI (partial)

**Phase 23 验证记录（2026-10-29，真实 MySQL 8.4.11 @ 192.168.56.105/test）**

- 构建门禁（T124）：`mvn -o -pl leaf-base-datasource clean install -DskipTests` → BUILD SUCCESS，移除三处类级 `@SuppressWarnings("all")` 后 ErrorProne/NullAway 无错误（顺带清理了随之暴露的 `java.util.Collections` 未使用导入）。
- 单元/回归：`mvn -o -pl leaf-base-datasource test -Dmaple.perf.enabled=true` → 242 用例通过、0 跳过。新增：T118 的 `syncAfterCommitWithoutActiveTransactionKeepsPropagationSemantics`、`syncAfterCommitInsideActiveTransactionIsolatesTheCallbackFailure`、`syncAfterCommitInlineDeliveryStillMeasuresTheRealCallbackDuration`；T119 的 `conditionlessUpdateIsLosslessInsteadOfDegraded`、`conditionlessUpdateDoesNotInflateTheDegradedCounter`；T121 的 `saveBatchPayloadDetachesEntityOwnedMutableValues`。
- 性能门禁：`-Dmaple.perf.enabled=true` → `GXMyBatisListenerOverheadTest` 8 例通过（SC-003 批量绝对预算、SC-005 ≤2× 载荷分配未被 T121 的复制逻辑破坏）。
- 真实数据库 IT：`-Dmaple.it.enabled=true` 运行 `MyBatisListenerIT`、`MyBatisListenerSyncAfterCommitIT`、`MyBatisListenerSyncAfterCommitFailureIT` → 17 例通过。
- 反向验证：临时 `git stash` 回滚 T118/T121 的主代码后重跑，两条新 IT 精确失败（`inlineSyncAfterCommitKeepsThePropagationSemantics` 期望抛出 `IllegalStateException` 实际未抛；`saveBatchDeliversOneEventWithDetachedEntityValues` 的 `assertNotSame` 失败，载荷 `ext` 与实体为同一实例），恢复后全绿 —— 用例捕获的是真实缺陷。
- T119 可达性实证：真实库上 `update(null, UpdateWrapper.set(...))`（WHERE 为空）被 `BlockAttackInnerInterceptor` 在到达数据库前拒绝，`UPDATE_EVENTS` 为空且行数据未变（`MyBatisListenerIT#conditionlessUpdateIsRejectedBeforeAnyPayloadIsBuilt`），故该分支为防御性修复，口径已写入 `contracts/event-payload.md` §降级规则与开发手册 §3.3.5。
- T120 实证：真实库上 `BaseMapper.insert(Collection)` 正常落库 2 行但 `SAVE_EVENTS`/`BATCH_EVENTS` 均为空；缺口已登记到开发手册能力边界表与 `AGENTS.md` 第 16 条；同时用 `ListenerUserBatchService` 实证 `saveBatch` 只投递 1 次批量事件。

**PR Review 修复记录（2026-10-29，`/speckit.review.run` 发现的收口）**

- 重要 #1（静默截断）：`GXMyBatisPlusSaveBatchEntityAspect#detachMutableValue` 超出深度 32 时不再静默——分离走查截断即置 `snapshotDegraded=true` 与 `degradeReasons=[UNSNAPSHOTABLE_VALUE]`，由发布组件统一计入 `degraded` 并输出 `payload degraded` 告警（AGENTS.md 14 / FR-020）。
- 重要 #2（改写调用方行）：实证 `Convert` 在 `Dict`/`Map` 入参下返回调用方实例，故切面按输入类型选择"新建行"或"就地替换"，不再改写调用方对象。
- 建议 #3：修正 `MAX_DETACH_DEPTH` 注释（严格扫描预算仅 8 层，无法为深度 32 的截断兜底）。
- 建议 #4：补 5 个用例——Date/数组/Set 复制、共享容器只复制一次、调用方行不被改写、截断降级计数、单监听方可写列表。
- 建议 #5：隔离用例增加 `isActualTransactionActive()` 前置断言，避免相邻用例状态泄漏。
- 建议 #6：单监听方批量行列表改为可变 `ArrayList`，与 `contracts/event-payload.md` "可变副本" 一致。
- 建议 #7：手册新增"三处预算（64/100000、8/4096、32）"对照表；`AGENTS.md` 第 17 条补充截断与调用方行约束。
- 回归：`test -Dmaple.perf.enabled=true` → 247 通过 / 0 跳过；`clean install -DskipTests` → BUILD SUCCESS；真实库 IT 17 通过；回滚切面后 6 个批量载荷用例精确失败（含 `snapshotDegraded expected true but was null`、调用方行 `assertNotSame` 失败、列表 `UnsupportedOperationException`）。

**PR Review 第二轮（复核轮）建议的收口（2026-10-29）**

- 建议 1（`degraded` 重复计数）：计数与标记分离——`markSnapshotDegradedIfNeeded` 只负责标注与日志，计数统一收敛到 `recordDegradedIfMarked`（并把 `snapshotScanTruncated` 纳入触发条件），调用顺序改为"先标注后计数"。同一载荷同时命中"切面标记 + 扫描截断"时只计一次；口径写入 `data-model.md` §6 与手册"计数口径"段落。
- 建议 2（类型不变量）：`DetachContext` 的行归属改为构造参数（`copyRows` 变成 `final`），把"行归属在走查前确定"前置到编译期。
- 建议 3（交叉用例）：新增 `batchPayloadCountsOnceWhenAspectAndScanBothTruncate`，断言两类截断同时命中时 `degraded` 恰好 +1 且两个标记都在；回滚计数改造后该用例失败（`expected: <6> but was: <7>`）。
- 建议 4（只读对称断言）：`multipleListenersShareOneReadOnlyBatchPayload` 补 `sharedEntities.add(...)` 抛 `UnsupportedOperationException`，与单监听方可写列表形成对照。
- 建议 5（简化）：截断标记抽为 `markDetachTruncated(Dict)`，主流程只表达"建行 → 标记"。
- 回归：`test -Dmaple.perf.enabled=true` → 248 通过 / 0 跳过；真实库 IT 17 通过；`clean install -DskipTests` → BUILD SUCCESS。

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 T001 基线；T003/T004/T005 可并行，T006 依赖 T003，T007 依赖 T003–T006，T008/T009 依赖 T007，T010 依赖 T008/T009
- **US1 (Phase 3)**: 依赖 T010；T011–T013 可并行编写测试，T014–T018 依赖测试存在
- **US2 (Phase 4)**: 依赖 US1 完成（共用发布组件）；T020–T022 可并行
- **US3 (Phase 5)**: 依赖 US1 的切面改造；T030 与 US2 的 T025 修改同一文件，必须串行
- **US4 (Phase 6)**: 依赖 US2 的计数实现与 US3 的配置迁移
- **Polish (Phase 7)**: 依赖全部用户故事

### User Story Completion Order

```text
Setup → Foundational → US1（MVP）→ US2 → US3 → US4 → Polish
```

### Within Each User Story

- 测试先写并确认失败 → 再实现 → 最后回归
- 配置/枚举先于解析器，解析器先于发布器，发布器先于切面
- 同一文件被多个任务修改时必须串行（T008/T009 与 T016/T017 分属不同文件，可并行；T025 与 T030 同文件，须串行）

### Parallel Opportunities

- T002、T003、T004、T005 可并行（不同文件）
- T008 与 T009 可并行（不同切面文件）
- T011、T012、T013 测试可并行编写，但 T012/T013 修改同一文件，实际须串行提交
- T020、T021、T022 可并行（不同测试文件）
- T037、T038、T039 可并行（元数据与两份文档）
- T042、T043 可并行

---

## Parallel Example: User Story 1

```bash
# 测试先行（不同文件可并行；T012/T013 同文件需串行合并）
Task: "编写去重用例 leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerDeduplicationTest.java"
Task: "扩展回归用例 GXMyBatisListenerAspectRegressionTest.java（成功判定/回滚/批量）"

# 实现阶段可并行的独立文件
Task: "修正 GXMyBatisPlusDeleteSoftAspect.java 泛型签名"
Task: "补齐 GXMyBatisPlusUpdateFieldAspect.java / DeleteSoft / Delete 的降级字段"
```

## Parallel Example: User Story 2

```bash
Task: "编写并发用例 GXMyBatisListenerConcurrencyTest.java"
Task: "编写隔离用例 GXMyBatisListenerIsolationTest.java"
Task: "扩展执行器用例 GXMyBatisAsyncListenerExecutorConfigTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1（基线）
2. 完成 Phase 2（统一发布组件，行为不变）
3. 完成 Phase 3（US1：去重、回滚语义、条件保真）
4. **STOP and VALIDATE**：运行 US1 独立测试，确认重复投递与回滚通知缺陷已修复
5. 可先行合并，作为最小可用交付

### Incremental Delivery

1. Setup + Foundational → 结构就绪，行为不变
2. US1 → 正确性修复（MVP）
3. US2 → 隔离与上下文安全
4. US3 → 性能门禁与执行器语义
5. US4 → 可观测与开关
6. Polish → 全量验证与迁移说明

每个阶段结束都应保持 `mvn -pl leaf-base-datasource -am test` 通过，不允许把失败用例留到下一阶段。

---

## Notes

- [P] 表示不同文件且无未完成依赖
- 每个缺陷修复都必须有"在旧代码上失败"的用例作为证据
- 性能夹具默认跳过，避免污染常规 CI；数据需记录机器信息
- 不得修改注解属性、回调方法签名、事件标识字符串与既有配置键
- 每次提交保持单一目的，提交信息遵循 `feat(fix): ...` / `test: ...` / `docs: ...`

---

## Phase 24: Convergence

**Purpose**: 收敛 2026-10-29 第十五次复核发现的剩余差距：F1 为监听投递顺序口径与实现不一致（文档为两级排序、实现仅按监听器类型名排序，且无断言），F2 为 `operation` 载荷键未登记，F3/F4 为支撑类命名与公共方法收敛未按宪法 VII 与 plan 口径登记。运行时代码已由 248 条模块用例验证通过，缺口集中在排序语义与契约/命名登记。

- [X] T125 **MEDIUM** 对齐监听投递顺序口径：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerConfigResolver.java` 的 `doResolve`（148 行）在按候选接口顺序收集完成后又整体重排为 `LISTENER_ORDER`（仅监听器类型全名），候选接口顺序因此被丢弃，而 `specs/001-mybatis-listener-audit/contracts/event-payload.md`（85 行）与 `docs/leaf-base-datasource开发手册.md`（412、632 行）均声明"按接口全限定名 → 监听器类型全名"排序；例如接口 `AMapper→ZetaListener`、`ZMapper→AlphaListener` 两个声明，文档顺序为先 `ZMapper` 的 `AlphaListener`，实现顺序为先 `AlphaListener`，`propagate-exception=true` 时"首个失败监听器"与"后续监听器不再执行"的实际结果因此与文档不一致。让实现按"接口全限定名主键 + 监听器类型名次键"排序（保持确定性），或把三处文档统一为"仅按监听器类型全名排序"；并在 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/aspect/mybatis/GXMyBatisListenerDeduplicationTest.java` 补"不同接口声明不同监听器时的投递顺序与文档一致"的断言 per contracts/event-payload.md §投递与顺序 (contradicts)
- [X] T126 **LOW** 登记 `operation` 载荷键：`GXMyBatisPlusUpdateEntityAspect`、`GXMyBatisPlusDeleteAspect`、`GXMyBatisPlusSaveBatchEntityAspect` 均向载荷写入 `operation`（取值为被拦截方法名，键常量见 `GXMyBatisEventConstant.OPERATION`），而 `GXMyBatisPlusSaveEntityAspect`/`GXMyBatisPlusUpdateFieldAspect`/`GXMyBatisPlusDeleteSoftAspect` 不写；`specs/001-mybatis-listener-audit/contracts/event-payload.md` 的载荷字段表（29-40 行）、`docs/leaf-base-datasource开发手册.md` 的载荷说明与 `data-model.md` 的载荷章节均未登记该键（`data-model.md` §1 只把它列为事件字段）。在契约字段表中补 `operation` 行并注明"仅更新/物理删除/批量三类事件携带"，或在实现中按"未使用字段不写入"口径移除该键并同步清理 `GXMyBatisEventConstant` 中的对应常量 per contracts/event-payload.md §载荷字段 (partial)
- [X] T127 **LOW** 收敛支撑类的命名与宪法 VII 对齐：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerPayload.java`（本分支新增）与同包的 `GXReadOnlyPayload.java` 都是"私有构造 + 全静态方法"的工具类，命名未采用宪法 VII 后缀表的 `GX<用途>Utils`，`leaf-base-datasource/AGENTS.md` 也未登记该偏差；改名为 `GXMyBatisListenerPayloadUtils`/`GXReadOnlyPayloadUtils`（同步 `GXMyBatisListenerEventPublisher`、三个条件类切面与相关测试的引用），或在 `AGENTS.md` 的命名约定中登记"载荷支撑类沿用 `Payload` 后缀"的理由 per Constitution VII (contradicts)
- [X] T128 **LOW** 复核并登记 `GXMyBatisListenerAnnotationUtils` 的公共方法收敛：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/util/GXMyBatisListenerAnnotationUtils.java` 现只保留 `findTypeDeclaration`/`findMethodDeclaration`，本次简化删除了 `findTypeAnnotation`/`findMethodAnnotation` 两个 public 静态方法（`master` 上不存在该文件，属本分支新增；删除前全仓无调用方），与 `specs/001-mybatis-listener-audit/plan.md`（97 行）"复用，不改签名"的表述冲突，且开发手册的升级说明未登记该收敛。确认无下游引用后，在开发手册升级说明或本任务记录中登记"该类为本分支新增、方法删除不影响已发布契约"（未登记则恢复两个方法，保持与 plan 一致）per plan.md: util/GXMyBatisListenerAnnotationUtils 复用不改签名 (contradicts)

---

## Phase 25: Architecture Guard Apply

**Purpose**: 将 2026-10-29 `ag-review-implementation` 的非阻塞结论落为可排期任务（0 CRITICAL / 0 HIGH，风险 LOW）。Phase 24 已完成，本阶段只登记 LOW/MEDIUM 的收敛项，不改变既有契约与事件语义。

- [X] T129 **LOW** 收敛单调用点的载荷工具方法：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerPayloadUtils.java`（111-116 行）的 `snapshotValue` 全仓只有 1 处调用（`GXMyBatisPlusUpdateEntityAspect.java:187`），低于"逻辑被两处以上复用才抽取"的门槛（宪法附加约束 §4、§8），而同文件 `isUnsnapshotable`（90 行）有 2 处调用（更新切面与 `GXMyBatisListenerEventPublisher.java:448`）确实需要共享。把 `snapshotValue` 的 try/clone 内联回更新切面，或在其 Javadoc 中声明"与 `isUnsnapshotable` 为强制配对、单独调用即违反快照口径"，二者择一并在 `AGENTS.md` 关键行为 19 旁注明口径 per Constitution §8 / 附加约束 §4 (unrequested)
- [X] T130 **LOW** 明确投递排序键的语义来源：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/aspect/mybatis/support/GXMyBatisListenerConfigResolver.java`（43 行）用 `Comparator.comparing(ResolvedListener::declaredFrom)` 作为主排序键，而 `declaredFrom` 是诊断字符串（类型级为 `FQN`、方法级为 `FQN#method`，见 166-172、223 行），将来仅调整该展示格式就会静默改变投递顺序与 `propagate-exception=true` 时"首个失败监听器"的取舍。为排序语义提供显式访问器（如从同一来源导出的 `declaringTypeName()`），或在比较器旁写明格式依赖不变量，并在 `GXMyBatisListenerDeduplicationTest` 补一条"`declaredFrom` 格式变更即失败"的守卫断言 per contracts/event-payload.md §投递与顺序 (partial)
- [X] T131 **MEDIUM** 拆掉更新实体载荷构建的位置布尔：`GXMyBatisPlusUpdateEntityAspect.java` 的 `updateConditionPayload`（210-213 行）有 6 个参数、其中 `conditionLossless`（第 4 个）与 `snapshotDegraded`（第 6 个）是位置布尔，三个调用点（152、194、202 行）出现 `..., true, List.of(), false` 这类盲传，读者需回看签名才能判断语义。拆为 `losslessConditionPayload(keyOperatorPairs, keyValuePairs, rawSql, degradeReasons, snapshotDegraded)` 与 `degradedConditionPayload(rawSql, degradeReasons)` 两个意图明确的方法，保持现有键集合与插入顺序不变 per Sonar `brain-overload::too-many-parameters` (partial)
- [X] T132 **MEDIUM** 替换已弃用的条件列表 API：`GXMyBatisPlusUpdateEntityAspect.java` 的 319 行与 355 行仍调用 `ExpressionList#getExpressions()`，该方法在 jsqlparser 5.x 已标记弃用（IDE 告警 67109867），而这两处位于本次重写的 `ConditionTokenVisitor` 内。确认 `ExpressionList` 在当前固定版本是否已实现 `List<Expression>`：若是则直接遍历/取 `size()`；若否，就地加一条带日期与升级路径的抑制注释并在 `AGENTS.md` 登记原因，禁止留裸弃用调用。（注意：该模式不在 `deprecated-and-dangerous-code` 规则目录内，不按 CRITICAL 处理）per Sonar `deprecated-api::jsqlparser` (partial)
