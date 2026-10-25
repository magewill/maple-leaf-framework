---
description: "Task list for leaf-base-dubbo-nacos 正确性、线程安全与性能审查"
---

# Tasks: leaf-base-dubbo-nacos 正确性、线程安全与性能审查

**Input**: Design documents from `/specs/014-dubbo-nacos-module-audit/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 本特性**强制测试**（`spec.md` FR-020 ~ FR-023、FR-025）。每个修复项必须先写"旧代码上失败"的用例（FR-021），再实现；不得通过删除或跳过既有用例来"通过"（P-OK-01 ~ P-OK-06 必须保留）。

**Organization**: 按 `spec.md` 的 6 个用户故事分组，每个故事可独立验证。任务描述中的 `B1~B6` 对应 `plan.md` Phase 2 预告的修复批次。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与同批次其它任务并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1 ~ US6）
- 每个任务均给出精确文件路径

## Path Conventions

生产代码：`leaf-base-dubbo-nacos/src/main/java/cn/maple/dubbo/nacos/...`
测试代码：`leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/...`
资源：`leaf-base-dubbo-nacos/src/main/resources/...`
文档：`docs/leaf-base-dubbo-nacos开发手册.md`、`leaf-base-dubbo-nacos/AGENTS.md`
规格产物：`specs/014-dubbo-nacos-module-audit/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 固定基线与上游事实，建立报告骨架，保证后续所有结论可复核（FR-014）

- [X] T001 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 建立骨架（章节：依赖基线 / E 序列 / F 八要素 / 四张矩阵 / P-OK 序列 / 性能口径 / 兼容声明 / 验证记录），并记录实测事实：`dubbo:3.3.6`、`nacos-client:3.1.1:compile`、`config/online-nacos-dubbo-demo.yml` 第 21 行 `dubboExceptionFilter,-exception`、`mvn -o -pl leaf-base-dubbo-nacos test` = 31 执行 / 0 失败
- [X] T002 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 写出附录 A「上游语义摘录」：`GXTraceIdContextUtils`（第 18–59 行）、`GXCommonUtils`（第 99–190、716–724、395–429 行）、`GXSpringContextUtils`（第 24–63 行）、`GXExceptionHandler`（第 42、226–231 行）、`GXSentinelFlowException`（第 16–82 行）、`GXEnableLeafFramework`（第 12–20 行），每条附"支撑哪个 E/F"
- [X] T003 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 写出附录 B「保留断言清单」：把 P-OK-01 ~ P-OK-06 映射到 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/` 下具体测试类与方法名（作为"一条不删"的核对基线，FR-012）
- [X] T004 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 写出附录 C「命令与输出」：`mvn -o -pl leaf-base-dubbo-nacos dependency:tree "-Dincludes=com.alibaba.nacos:*,org.apache.dubbo:*"`、`javap -v -cp "F:\repository\org\apache\dubbo\dubbo\3.3.6\dubbo-3.3.6.jar" org.apache.dubbo.rpc.filter.ExceptionFilter`、jar 内 SPI、`Select-String -Path "config\online-nacos-dubbo-demo.yml" -Pattern "filter"`、`mvn test` 的原始片段（R-01 ~ R-12）
- [X] T005 运行 `mvn -o -pl leaf-base-dubbo-nacos test`，确认 31 条全绿，把输出写入 `specs/014-dubbo-nacos-module-audit/audit-report.md` §验证记录

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 提供所有故事共用的测试支撑；不改变任何生产行为

**⚠️ CRITICAL**: 未完成前不得开始任何用户故事

- [X] T006 [P] 新增 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/support/GXDubboTestApp.java`：`@SpringBootConfiguration` + `@EnableWebMvc` + `@Import({GXExceptionHandler, GXDubboCallExceptionHandler})`，`properties = {"spring.cloud.nacos.config.import-check.enabled=false", "spring.cloud.nacos.discovery.enabled=false"}`（供真实 advice 装配用例，R-11）。**实施期修订**：不采用 `@ComponentScan("cn.maple")`（会加载 datasource/nacos 等无关自动配置，违反离线约束 FR-025），改为 `@Import` 精确装配两个 advice
- [X] T007 [P] 新增 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/support/GXDubboTestSupport.java`：`Invoker`/`Invocation`/`Result` 的 mock 工厂、`AppResponse` 构造、Logback `ListAppender` 获取与清理、`resetAppNameCache()` 调用封装（FR-023，禁止反射构造现场）
- [X] T008 运行 `mvn -o -pl leaf-base-dubbo-nacos test`，确认测试支撑类可编译且既有 31 条仍绿

**Checkpoint**: 测试支撑就绪、基线未破。

---

## Phase 3: User Story 1 - 审查结论可复核 (Priority: P1) 🎯 MVP

**Goal**: 交付独立报告（`audit-report.md`），且每条高危结论都能用一条可运行的复现用例支撑（对应 B1）。

**Independent Test**: 随机抽取报告中的任一 F，按其"复现方式"运行对应用例，观察当前（旧）代码的输出与报告一致。

### Tests（先写，必须在旧代码上失败）

- [X] T009 [US1] 新增 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/filter/GXDubboAuditReproTest.java` 并实现 `f01BusinessExceptionWrappedWhenBuiltInFilterActive`：手工依次调用内置 `ExceptionFilter.onResponse` 与 `GXDubboExceptionFilter.onResponse`，断言 `GXBusinessException` 最终变成普通 `RuntimeException`（旧代码行为）
- [X] T010 [US1] 在同文件实现 `f02AdviceShadowedByBaseHandler`：用 `MockMvcBuilders.standaloneSetup(...).setControllerAdvice(new GXExceptionHandler(), new GXDubboCallExceptionHandler())` 注册两个 advice（不排序），断言当前 `RpcException` 的响应可能是"系统内部错误"且无 `data.errorType`
- [X] T011 [US1] 在同文件实现 `f03SentinelDegradeNotConverted`：构造类名 `com.alibaba.csp.sentinel.slots.block.degrade.DegradeException` 的替身（测试内定义同包名类），经 `GXDubboExceptionFilter`，断言未被转换为 `GXSentinelFlowException`
- [X] T012 [US1] 在同文件实现 `f04SignatureUnresolvableFailsOpen`：mock `invocation.getParameterTypes()` 返回与方法不匹配的类型 + 未声明 `RuntimeException`，断言当前直接透传（未包装为 `GXBusinessException`）
- [X] T013 [US1] 在同文件实现 `f13AgentsDocClaimsNoTests`：读取 `leaf-base-dubbo-nacos/AGENTS.md`，断言当前包含"没有现成的 `src/test` 测试目录"（证明文档漂移）
- [X] T014 [US1] 运行 `mvn -o -pl leaf-base-dubbo-nacos test "-Dtest=GXDubboAuditReproTest"`，把 5 条用例的失败输出贴入 `specs/014-dubbo-nacos-module-audit/audit-report.md` §故障现场（证明 FR-021）

### Implementation（报告本体）

- [X] T015 [US1] 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 写入 E-01 ~ E-20 表（字段：`id`/`kind`/`locator`/`fact`/`verified`，按 `data-model.md` §2）
- [X] T016 [US1] 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 写入 F-01 ~ F-16 八要素表（级别/维度/位置/触发/影响/证据/复现/回归设计 + `fixBatch` B1~B6 + `verified`，按 `data-model.md` §1）
- [X] T017 [US1] 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 写入四张矩阵与 P-OK 序列：异常分类矩阵（§3）、TraceId 优先级链（§4）、共享状态清单（§5）、SPI 契约表（§7）、P-OK-01 ~ P-OK-06（`data-model.md` §1.2）

**Checkpoint**: 报告八要素完整、5 条复现用例在旧代码上失败、结论可逐条定位。

---

## Phase 4: User Story 2 - 异常分类与透传边界可判定 (Priority: P1)

**Goal**: `GXBusinessException` 业务码无损到达 consumer；Sentinel 全类型可识别；反射失败 fail-closed（对应 B1/B2）。

**Independent Test**: 对 `GXBusinessException`、声明异常、checked 异常、`RpcException`、JDK/jakarta 异常、MyBatis 异常、未声明运行时异常、Sentinel 各阻断类型各构造一次，记录最终异常类型与业务字段。

### Tests（先写）

- [X] T018 [P] [US2] 在 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/filter/GXDubboExceptionFilterTest.java` 追加：`convertsDegradeAndAuthorityExceptions`、`doesNotMisclassifyBusinessMessageContainingFlowException`、`wrapsUndeclaredRuntimeWhenMethodSignatureUnresolvable`、`recordsFilterInternalFailure`（保留既有 11 条）
- [X] T019 [P] [US2] 新增 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/filter/GXDubboFilterChainBehaviorTest.java`（**实施期修订**：原定 `...SpringBootTest` 更名，避免重复 F-16 的误导性后缀）：按真实链顺序依次调用 `ExceptionFilter.onResponse` 与 `GXDubboExceptionFilter.onResponse`，实现 `businessExceptionLosesCodeWhenBuiltInFilterActive` 与 `businessExceptionPreservedWhenBuiltInFilterExcluded` 两条对照
- [X] T020 [P] [US2] 改造 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/handler/GXDubboCallExceptionHandlerSpringBootTest.java` 为真实 `@SpringBootTest(classes = GXDubboTestApp.class)` + 组件扫描（替换 `standaloneSetup`），实现 `rpcExceptionUsesDedicatedHandlerUnderComponentScan`（断言 `data.errorType` 存在，保留既有 2 条断言）
- [X] T021 [P] [US2] 新增 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/config/GXDubboProviderFilterValidatorTest.java`：`未配置`/`已配置 -exception`/`配置了但未排除` 三态各 1 条，断言 ERROR 日志内容与不阻断启动
- [X] T022 [US2] 运行上述用例，把失败输出归档到 `specs/014-dubbo-nacos-module-audit/audit-report.md` §故障现场

### Implementation

- [X] T023 [US2] 新增 `leaf-base-dubbo-nacos/src/main/java/cn/maple/dubbo/nacos/config/GXDubboProviderFilterValidator.java`：`@Component` + `SmartInitializingSingleton`，读取 `Environment.getProperty("dubbo.provider.filter")`；为空或未含 `-exception`/`dubboExceptionFilter` 时输出 **ERROR**（ASCII，含修复指引 `dubbo.provider.filter=dubboExceptionFilter,-exception`）。**本批不新增 `check-strict` 配置键**（遵循宪法附加约束 §8 YAGNI，仅告警）
- [X] T024 [US2] 改造 `leaf-base-dubbo-nacos/src/main/java/cn/maple/dubbo/nacos/filter/GXDubboExceptionFilter.java`：① `isSentinelFlowException` 改为 `com.alibaba.csp.sentinel.slots.block.` 前缀 + `BlockException` 反射兜底，删除"消息含 FlowException"宽松匹配；② `getMethod` 的 `NoSuchMethodException` 分支不再 `return`，落入 `GXBusinessException(500)` 包装；③ 外层 `catch (Throwable)` 保留但 WARN + `AtomicLong` 计数（`contracts/dubbo-exception-contract.md` §2/§3）
- [X] T025 [US2] 改造 `leaf-base-dubbo-nacos/src/main/java/cn/maple/dubbo/nacos/handler/GXDubboCallExceptionHandler.java`：加 `@Order(Ordered.HIGHEST_PRECEDENCE)`，确保 `RpcException`/`GXSentinelFlowException` 先于基础 `Exception` 兜底被匹配（`contracts/dubbo-exception-contract.md` §4，F-02）
- [X] T026 [US2] 运行 `mvn -o -pl leaf-base-dubbo-nacos test`，确认 T018 ~ T021 由红转绿、既有 11 + 2 条仍绿

**Checkpoint**: 业务码保真、Sentinel 全类型、反射失败 fail-closed、advice 顺序稳定。

---

## Phase 5: User Story 3 - TraceId 透传与上下文恢复可验证 (Priority: P1)

**Goal**: 证明四个方向的优先级链、异步回调、异常抛出时的上下文恢复均正确（对应 P-OK-01/P-OK-04 的扩展）。

**Independent Test**: 覆盖四个方向 × 四类来源 × 三种恢复场景，逐条断言透传值与恢复后的 MDC。

### Tests（先写）

- [X] T027 [P] [US3] 在 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/filter/GXDubboTraceIdFilterTest.java` 追加：`resolveTraceIdKeepsClientCustomOrder`、`resolveTraceIdKeepsServerCustomOrder`、`exceptionRestoresMdcWhenEnteredEmpty`、`asyncCallbackRestoresMdcOnCallbackThread`（保留既有 5 条）
- [X] T028 [P] [US3] 在 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/selector/GXPenetrateAttachmentSelectorTest.java` 追加：`selectReversePrefersServerAttachment`、`nullAttachmentsFallbackToThreadLocalOrGenerate`（保留既有 5 条）
- [X] T029 [US3] 运行并归档失败/通过输出到 `specs/014-dubbo-nacos-module-audit/audit-report.md`

### Implementation

- [X] T030 [US3] 对 `GXDubboClientTraceIdFilter` / `GXDubboServerTraceIdFilter` / `GXPenetrateAttachmentSelector` 的可空辅助方法补 `@Nullable`（`getInvocationAttachment`/`getAttachment`/`resolveTraceId`）并补必要 Javadoc；**行为不变**（`contracts/dubbo-trace-context-contract.md` §1/§2/§3）
- [X] T031 [US3] 运行 `mvn -o -pl leaf-base-dubbo-nacos test`，确认既有 5 + 5 条与新增全绿

**Checkpoint**: 四方向优先级链、异步/异常恢复语义有断言锁定。

---

## Phase 6: User Story 4 - 共享状态与并发安全可判定 (Priority: P2)

**Goal**: 静态 `appName` 不再每请求回源、跨上下文可隔离、并发读一致（对应 F-08/F-09，B3）。

**Independent Test**: 并发触发 `getAppName()`；两个不同 `spring.application.name` 上下文经 `resetAppNameCache()` 隔离；统计第二次调用不再读 Environment。

### Tests（先写）

- [X] T032 [P] [US4] 在 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/selector/GXPenetrateAttachmentSelectorTest.java` 追加 `appNameCacheIsolatesAcrossReset`：`ExecutorService` 并发读一致；`resetAppNameCache()` 后重新解析出新的 `spring.application.name`
- [X] T033 [P] [US4] 在 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/filter/GXDubboTraceIdFilterTest.java` 追加 `filterReusesSharedAppNameCache`：第二次调用不再触发 `GXCommonUtils.getEnvironmentValue`（用 `MockedStatic` 计数）

### Implementation

- [X] T034 [US4] 改造 `leaf-base-dubbo-nacos/src/main/java/cn/maple/dubbo/nacos/selector/GXPenetrateAttachmentSelector.java`：`appName` 提升为共享 `static volatile`，提供包内 `static String getCachedAppName()` 与 `static void resetAppNameCache()`（`contracts/dubbo-trace-context-contract.md` §4）
- [X] T035 [US4] 改造 `leaf-base-dubbo-nacos/src/main/java/cn/maple/dubbo/nacos/filter/GXDubboClientTraceIdFilter.java` 与 `.../GXDubboServerTraceIdFilter.java`：删除每请求 `GXCommonUtils.getEnvironmentValue("spring.application.name", ...)`，改用共享缓存
- [X] T036 [US4] 运行 `mvn -o -pl leaf-base-dubbo-nacos test`，确认并发/隔离用例全绿

**Checkpoint**: 热路径零环境查找、静态缓存可重置、并发安全。

---

## Phase 7: User Story 5 - 性能开销定量化 (Priority: P2)

**Goal**: 消除每请求 INFO 刷屏，并把性能折算成可复算口径（对应 F-10/F-11，B4）。

**Independent Test**: 用 Logback `ListAppender` 断言 INFO 无输出、DEBUG 有输出；按 `data-model.md` §6 逐项复算。

### Tests（先写）

- [X] T037 [P] [US5] 在 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/filter/GXDubboTraceIdFilterTest.java` 追加 `traceIdLoggingIsDebugLevel`：INFO 级别下 `ListAppender` 无该文案，DEBUG 级别下有 1 条

### Implementation

- [X] T038 [US5] 把 `GXDubboClientTraceIdFilter` 与 `GXDubboServerTraceIdFilter` 的"propagate/receive TraceId" `log.info` 改为 `log.debug`（保持 ASCII）
- [X] T039 [US5] 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 写「性能口径」表：每请求 `getEnvironmentValue`（1→0）、INFO 日志（1→0）、attachment 双读（≤2）、`getBean`/反射（0）、测试墙钟（按 `data-model.md` §6）
- [X] T040 [US5] 运行 `mvn -o -pl leaf-base-dubbo-nacos test`，确认日志用例全绿、既有断言不破

**Checkpoint**: 热路径无 INFO、性能口径可复算。

---

## Phase 8: User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

**Goal**: 补齐使用方手册与契约文档，使每条发现无需补充上下文即可排期（对应 F-12/F-13/F-14/F-15，B5）。

**Independent Test**: 抽取任一发现，检查八要素齐备；检查手册包含 SPI/优先级/异常矩阵/强制配置/allowlist/不支持范围。

### Implementation

- [X] T041 [P] [US6] 新增 `docs/leaf-base-dubbo-nacos开发手册.md`：模块定位、SPI 扩展名契约、TraceId 优先级表、异常分类矩阵、**强制装配配置**（`dubbo.provider.filter=dubboExceptionFilter,-exception`）、`serialize.allowlist` 现状与维护要求、不支持范围（Sentinel 原生指标、服务发现端到端）、离线测试说明
- [X] T042 [P] [US6] 修订 `leaf-base-dubbo-nacos/AGENTS.md`：§2 补强制装配配置、§3 补 filter 互斥与 `ExceptionFilter` 关系、§5 把"没有现成的 src/test"改为"已有 9 个测试类 / ≥31 条用例，新增改动须在其基础上补齐"
- [X] T043 [P] [US6] 把 `leaf-base-dubbo-nacos/src/main/java/cn/maple/dubbo/nacos/processor/GXDubboRpcApiBeanPostProcessor.java` 的 `@Log4j2` 改为 `@Slf4j`（日志文案不变，F-14）
- [X] T044 [P] [US6] 在 `docs/leaf-base-dubbo-nacos开发手册.md` 登记 `src/main/resources/security/serialize.allowlist` 的条目 `com.google.common.collect.HashBasedTable` 的来源/用途/新增对象评估流程（F-12，内容不变）
- [X] T045 [US6] 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 写「兼容声明」与「后续任务拆解」，并与 `spec.md`/`plan.md`/`contracts/` 互引（FR-014）

**Checkpoint**: 手册与 AGENTS 一致、`@Log4j2` 清零、兼容声明可交付。

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 全量验收、静态检查、文档一致性

- [X] T046 运行 `mvn -o -pl leaf-base-dubbo-nacos clean test`：用例数 **≥ 31**、0 失败；把全量输出写入 `specs/014-dubbo-nacos-module-audit/audit-report.md` §验证记录
- [X] T047 运行 `mvn -o -pl leaf-base-dubbo-nacos clean install -DskipTests`：ErrorProne + NullAway 零错误（R-10）
- [X] T048 校验：`Select-String -Path "leaf-base-dubbo-nacos\src\main\java" -Pattern "@Log4j2|System\.out|printStackTrace"` 无输出；`AsciiSourceGuardTest` 通过（日志 ASCII）
- [X] T049 校验离线与优雅跳过（FR-025）：所有新增 `@SpringBootTest` 关闭 Nacos 自动配置；需真实 Nacos 的用例用 `Assumptions.assumeTrue(...)` 并在任务说明中记录"已向用户申请连接信息"；不臆造地址/凭据
- [X] T050 一致性核对：`spec.md` / `plan.md` / `data-model.md` / `contracts/` / `quickstart.md` / `audit-report.md` 的 F 编号、E 编号、矩阵与用例名互相一致；输出"新增 / 修改 / 保留"用例清单，确认 P-OK-01 ~ P-OK-06 全部保留

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup；**阻塞所有用户故事**（T006/T007 是真实链与真实容器用例的前置）
- **US1 (Phase 3)**: 依赖 Foundational；产出报告与复现用例
- **US2 (Phase 4)**: 依赖 Foundational；与 US1 可并行（不同文件），但 T020 依赖 T006（`GXDubboTestApp`）
- **US3 (Phase 5)**: 依赖 Foundational；与 US2 可并行（不同文件）
- **US4 (Phase 6)**: **依赖 US3**（同一批 TraceId Filter 文件，避免冲突）
- **US5 (Phase 7)**: **依赖 US4**（同一批 TraceId Filter 文件）
- **US6 (Phase 8)**: 依赖 US1（报告已定稿）与 US2（异常语义已定）
- **Polish (Phase 9)**: 依赖全部

### Within Each User Story

- Tests（先写）MUST 在旧代码上失败 → Implementation → `mvn test` 全绿
- 生产代码变更后必须重跑既有 31 条断言

### Parallel Opportunities

- Phase 2 的 T006/T007 可并行
- Phase 1 的 T002/T003/T004 与 US1 的 T009~T013、T015~T017 均写入**同一文件**（`audit-report.md` / `GXDubboAuditReproTest.java`），须顺序执行（故不标 `[P]`）
- US2 的 T018/T019/T020/T021 可并行（不同文件）
- US3 的 T027/T028 可并行（不同文件）
- US6 的 T041~T044 可并行（不同文件）
- **不可并行**：US4 与 US5（同一批 Filter 文件），必须顺序执行

### Parallel Example: US2

```text
Task: "T018 追加 GXDubboExceptionFilterTest 的 Sentinel/fail-closed/计数用例"
Task: "T019 新增 GXDubboFilterChainSpringBootTest 真实 Dubbo 链对照"
Task: "T020 改造 GXDubboCallExceptionHandlerSpringBootTest 为真实 @SpringBootTest"
Task: "T021 新增 GXDubboProviderFilterValidatorTest 三态"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 Setup
2. 完成 Phase 2 Foundational
3. 完成 Phase 3 US1（报告 + 5 条复现用例）
4. **STOP and VALIDATE**：随机抽取 F-01/F-02，按报告复现，确认旧代码失败输出一致
5. 此时已具备"可复核的审查交付"，可先行评审

### Incremental Delivery

1. Setup + Foundational → 基线与测试支撑就绪
2. US1 → 审查报告（MVP）
3. US2 → 业务码保真与异常分类收敛（最高业务价值）
4. US3 → TraceId 语义锁定
5. US4 → 共享状态收敛
6. US5 → 性能与日志
7. US6 → 手册与契约
8. Polish → 全量验收

### Parallel Team Strategy

1. 全员先完成 Setup + Foundational
2. 之后：A 做 US2（异常），B 做 US3（TraceId），C 做 US1 报告补充
3. US4/US5 由同一人顺序完成（同文件）
4. US6 由文档负责人并行推进

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- 每个修复项都必须有"旧代码失败"用例证据（FR-021），先红后绿
- 测试默认离线（Dubbo `injvm`/直连 + 关闭 Nacos 自动配置）；真实 Nacos 需先向用户申请（FR-025）
- 不新增 `maple.framework.dubbo.filter.check-strict` 配置键（YAGNI，宪法附加约束 §8）；校验器仅 ERROR 告警
- 既有 31 条断言一条不删；用例总数只增不减（SC-009）
- 日志与断言消息必须 ASCII

---

## 实施期修订记录（2026-10-25）

| 任务 | 原计划 | 实际 | 原因 |
| --- | --- | --- | --- |
| T006 | `@ComponentScan("cn.maple")` | `@EnableWebMvc` + `@Import` 两个 advice | 全量组件扫描会加载 datasource/nacos 自动配置，违反离线约束（FR-025） |
| T019 | `GXDubboFilterChainSpringBootTest`（真实 Dubbo 链） | `GXDubboFilterChainBehaviorTest`（按真实顺序调用两个 `onResponse`） | 避免 `SpringBootTest` 误导性后缀（F-16）；链式调用足以刻画 F-01 |
| T020 | `standaloneSetup` → 真实 `@SpringBootTest` | 已改为真实 `@SpringBootTest`（`GXDubboTestApp` + 双 advice 注册） | 兑现 F-16：真实上下文中验证 advice 优先级 |
| T023 | 可选 `check-strict` 开关 | **不新增**该配置键，仅 ERROR 告警 | 宪法附加约束 §8 YAGNI；阻断启动会放大升级风险 |
| T037 | Logback `ListAppender` 断言日志级别 | 源码断言（无 `log.info` / 有 `log.debug`） | 本仓日志后端为 Log4j2（`spring-boot-starter-log4j2`），无 Logback |
| T027/T028 | `appNameCacheIsolatesAcrossReset` 放 Selector | Selector + TraceId Filter 各自覆盖；Filter 侧用 `MockedStatic` 计数 | 覆盖 F-08（Selector 静态缓存）与 F-09（Filter 复用缓存） |

**最终结果**：`mvn -o -pl leaf-base-dubbo-nacos clean test` → **57 执行 / 0 失败 / BUILD SUCCESS**；`clean install -DskipTests` → BUILD SUCCESS（ErrorProne + NullAway 零错误）。

---

## Phase 10: Convergence

**Purpose**: 补齐实现与规格/计划/任务之间的剩余差距（按 CRITICAL/HIGH → LOW 排序）。本阶段由 `/speckit.converge` 追加，不修改既有任务。

- [X] T051 新增 `leaf-base-dubbo-nacos/src/main/java/cn/maple/dubbo/nacos/config/package-info.java`（内容：`@org.jspecify.annotations.NullUnmarked` + `package cn.maple.dubbo.nacos.config;`），使新增 `config` 包与模块其余 4 个包的空值策略一致 per Constitution V/VII (missing)
- [X] T052 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 增加「逐文件审查判定表」：10 个生产源码 + 3 个运行时资源 + 9 个测试类逐项列出，判定取值 {无缺陷 / 风险 / 缺陷 / 未覆盖原因} 并附证据（E-xx）per FR-001 (missing)
- [X] T053 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` §6 与 `data-model.md` §1.1 为 F-01 ~ F-16 补「触发条件」列/值（配置组合、并发、输入、装配缺失等），确保每条发现可按触发条件复现 per FR-002 (partial)
- [X] T054 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 登记 FR-016 的上游范围例外：`GXCommonUtils.getEnvironmentValue` / `getGenericClassType`、`GXSpringContextUtils.getApplicationContext` 位于 `leaf-base-framework`，本模块不改；若需收敛 `@Nullable`，另立框架模块变更单（避免违反模块边界与 AGENTS.md §4.1）per FR-016 (partial)
- [X] T055 在 `specs/014-dubbo-nacos-module-audit/audit-report.md` 记录 FR-006 的等价实现与证据：`GXDubboTestApp` 以 `@EnableWebMvc` + `@Import({GXExceptionHandler, GXDubboCallExceptionHandler})` 替代 `@ComponentScan("cn.maple")`，在真实 `@SpringBootTest` 上下文中验证双 advice 排序；说明全量组件扫描会加载 datasource/nacos 自动配置、违反离线约束（FR-025）per FR-006 (partial)
- [X] T056 在 `specs/014-dubbo-nacos-module-audit/data-model.md` §3（异常分类矩阵）与 §4（TraceId 优先级链）为每个单元格补证据引用（E-xx 或用例名）及「现状值 → 期望值」标注 per SC-003/SC-004 (partial)
- [X] T057 移除未被使用的 `GXDubboExceptionFilter.resetFilterFailureCount()`，或在 `GXDubboExceptionFilterTest#recordsFilterInternalFailure` 中显式使用它重置计数（消除未被调用方使用的 API，遵守 YAGNI）per Constitution 附加约束 §8 (unrequested)

---

## Phase 11: Convergence

**Purpose**: 补齐 plan 中声明但尚未落地的测试分支，并修正 audit-report 中引用不存在用例的偏差。本阶段由 `/speckit.converge` 追加，不修改既有任务。

- [X] T058 在 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/handler/GXDubboCallExceptionHandlerTest.java` 新增 `rpcExceptionSubtypesKeepStableCode`：参数化覆盖 `RpcException` 的 9 个子类（`TIMEOUT_EXCEPTION`、`NETWORK_EXCEPTION`、`SERIALIZATION_EXCEPTION`、`FORBIDDEN_EXCEPTION`、`AUTHORIZATION_EXCEPTION`、`LIMIT_EXCEEDED_EXCEPTION`、`NO_INVOKER_AVAILABLE_AFTER_FILTER`、`VALIDATION_EXCEPTION`、`BIZ_EXCEPTION`），断言 `code=500` 且 `data.errorType` / `data.rpcMessage` 与 `data-model.md` §3 一致；使 `audit-report.md` F-06 的回归设计引用真实存在 per plan: handler 测试（保留 2 条 + RpcException 子类矩阵）/ audit-report F-06 (partial)
- [X] T059 在 `leaf-base-dubbo-nacos/src/test/java/cn/maple/dubbo/nacos/processor/GXDubboRpcApiBeanPostProcessorTest.java` 新增「反射失败分支」用例：构造一个不含 `staticBindServeServiceClass` 方法的 RPC API 实现（或该方法抛异常），断言 `postProcessAfterInitialization` 不抛异常、返回原 bean，且不因绑定失败中断启动 per plan: processor 测试（保留 4 条 + 反射失败分支） (partial)

---

## Phase 12: Convergence

**Purpose**: 使审查报告的验证记录与当前实测一致。本阶段由 `/speckit.converge` 追加，不修改既有任务。

- [X] T060 更新 `specs/014-dubbo-nacos-module-audit/audit-report.md` §11「验证记录」至最新实测：`Tests run: 57` → **59**；`GXDubboCallExceptionHandlerTest` 2 → **3**；`GXDubboRpcApiBeanPostProcessorTest` 4 → **5**；表头「9 个测试类，31 → 57，+26」→「**11 个测试类，31 → 59，+28**」；确保与 `mvn -o -pl leaf-base-dubbo-nacos clean test` 的当前输出一致 per FR-022 / FR-014 (partial)
