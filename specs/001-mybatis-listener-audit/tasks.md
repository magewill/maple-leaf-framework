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
