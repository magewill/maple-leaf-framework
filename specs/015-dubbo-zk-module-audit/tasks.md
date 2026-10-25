---
description: "Task list for leaf-base-dubbo-zk 正确性、线程安全与性能审查（015）"
---

# Tasks: leaf-base-dubbo-zk 正确性、线程安全与性能审查

**Input**: Design documents from `/specs/015-dubbo-zk-module-audit/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 用户明确要求"修改完代码之后，一定做功能完备性的测试"，且 spec FR-018～FR-020、FR-024 将测试定为硬门槛，因此每个用户故事阶段都包含**先失败后通过**的测试任务。

**Organization**: 按 spec 的 6 个用户故事分组。US1/US2/US6 为 P1，US3/US4/US5 为 P2；US6（交付集与测试门槛）排在最后，因为它必须描述前 5 个故事的最终状态，属单向依赖。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1～US6）
- 所有路径均相对仓库根 `e:\project\java\maple-leaf-framework`

## Path Conventions

- 生产代码：`leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/`
- 测试代码：`leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/`（本阶段新建）
- 资源：`leaf-base-dubbo-zk/src/main/resources/`（**不得修改**）
- 文档：`leaf-base-dubbo-zk/AGENTS.md`、`docs/leaf-base-dubbo-zk开发手册.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立本模块首个测试基线（当前 `src/test` 不存在，用例数为 0，见 F-04）

- [X] T001 在 `leaf-base-dubbo-zk/pom.xml` 的 `<dependencies>` 末尾追加 `org.springframework.boot:spring-boot-starter-test`（`<scope>test</scope>`，不带 `<version>`，由父 POM `dependencyManagement` 管理）
- [X] T002 [P] 创建测试目录骨架 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/{filter,handler,processor,selector,config,support}/` 与 `leaf-base-dubbo-zk/src/test/java/{com/alibaba/csp/sentinel/slots/block/{degrade,authority}/,org/mybatis/spring/}`
- [X] T003 [P] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/AsciiSourceGuardTest.java`：遍历 `src/main/java` 下全部 `.java`，断言无非 ASCII 字符（对应 FR-017，锁定 S-02/F-22 类回归）
- [X] T004 [P] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/support/GXDubboTestSupport.java`：`invoker(Class<?>)`、`invocation(String[, Class<?>[], Object[]])`、`appResponse(Throwable)`、`resetAppNameCache()` 四个静态夹具，全部基于 Mockito 替身或 `AppResponse`（禁止反射访问私有字段，FR-023）
- [X] T005 [P] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/support/GXDubboTestApp.java`：`@SpringBootConfiguration` + `@EnableWebMvc` + `@Import({GXExceptionHandler.class, GXDubboCallExceptionHandler.class})`，**不启用** `@EnableAutoConfiguration`/`@ComponentScan("cn.maple")`，确保离线
- [X] T006 [P] 新增三个测试桩类：`com/alibaba/csp/sentinel/slots/block/degrade/DegradeException.java`、`com/alibaba/csp/sentinel/slots/block/authority/AuthorityException.java`（均继承 `com.alibaba.csp.sentinel.slots.block.BlockException` 的轻量桩）、`org/mybatis/spring/MyBatisSystemException.java`（继承 `RuntimeException`）
- [X] T007 执行基线：`mvn -o -pl leaf-base-dubbo-zk test`，记录改动前 `Tests run: 0` 作为 F-04 证据；确认 T001～T006 后模块可编译（NullAway 零错误）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 三个故事（US2/US3/US4）共用的 `appName` 缓存与新包骨架，必须在故事开始前就位

**⚠️ CRITICAL**: 本阶段完成前不要开始 Phase 3 及之后的生产代码改动

- [X] T008 创建 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/config/package-info.java`，内容与既有包一致：`@org.jspecify.annotations.NullUnmarked` + `package cn.maple.dubbo.zk.config;`
- [X] T009 改造 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/selector/GXPenetrateAttachmentSelector.java`：把私有 `getAppName()` 提升为 `public static String getCachedAppName()`（逻辑不变：`UnknownApp` 不缓存、`volatile` 读、只写非 `UnknownApp` 值），新增 `public static void resetAppNameCache()`（置 null），两者补 Javadoc 说明"供 filter 复用 + 测试/上下文刷新"；`logDebug` 内部改调 `getCachedAppName()`
- [X] T010 执行 `mvn -o -pl leaf-base-dubbo-zk test`，确认 T003/T006 编译通过且无新增告警

---

## Phase 3: User Story 1 - 异常契约无损且可判定 (Priority: P1) 🎯 MVP

**Goal**: `GXBusinessException`/`GXSentinelFlowException`/声明异常/未声明运行时异常经 Dubbo 链与 Web 层后，`code/msg/data` 与 HTTP 语义无损，且 Web 层专用 handler 稳定生效

**Independent Test**: 构造 E-1～E-14 各类异常，在 filter 链与 `@SpringBootTest` + `MockMvc` 下断言最终类型、`code/msg/data`、日志与 traceId

**契约**: [contracts/dubbo-exception-contract.md](./contracts/dubbo-exception-contract.md) §1/§4；矩阵见 [data-model.md](./data-model.md) §2

### Tests（先写，必须先失败）

- [X] T011 [P] [US1] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/filter/GXDubboFilterChainBehaviorTest.java`（3 条）：① 内置 `ExceptionFilter` 先执行后再执行本模块 filter → `GXBusinessException` 不再是 `GXBusinessException` 且 `code 4001` 丢失（F-01 特征化用例）；② 排除内置 filter（只跑本模块）→ 原对象、`code=4001`、`data.orderId`、`data.X-B3-TraceId` 全保留；③ consumer 侧收到 provider 已分类的 `GXBusinessException` → 原对象透传（F-08 语义锁定）
- [X] T012 [US1] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/filter/GXDubboExceptionFilterTest.java`（第一批 8 条，本文件由后续任务继续追加，勿并行）：E-1 业务异常保留、E-2 `data == null` 降级并注入 traceId（`wrapsBusinessExceptionWhenDataIsNullToExposeTraceId`）、E-3 子类保留、E-5 声明异常、E-6 checked 异常、E-7 `RpcException`、E-8 JDK 异常、E-9 `MyBatisSystemException`、E-13 `GenericService` 跳过但附加附件
- [X] T013 [US1] 在 `GXDubboExceptionFilterTest.java` 追加第二批 5 条：E-4 `keepsSentinelFlowExceptionUnchanged`（`code=406`、`data` 原样、不重建）、E-10 `convertsDegradeAndAuthorityExceptions` + `convertsFlowByMessageWithoutLeakingArguments`（断言 `data` 不含 `arguments` 键）、E-14 `doesNotMisclassifyBusinessMessageContainingFlowException`、E-12 `wrapsUndeclaredRuntimeWhenMethodSignatureUnresolvable`、F-07 `recordsFilterInternalFailure`（断言 `filterFailureCount()` 递增且原异常不受影响）
- [X] T014 [P] [US1] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/handler/GXDubboCallExceptionHandlerTest.java`（2 条）：① `rpcExceptionSubtypesKeepStableCode` 参数化覆盖 9 个子类（timeout/network/serialization/forbidden/authorization/limitExceed/noInvoker/validation/biz），断言 `code=500`、`data.errorType`/`data.rpcMessage`/`data.rpcCode`；② `sentinelFlowKeepsCodeAndCopiesData`（断言返回 `data` 是副本、原异常 `data` 未被写入 traceId）+ `adviceDeclaresHighestPrecedence`（`@Order` 注解值断言）
- [X] T015 [P] [US1] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/handler/GXDubboCallExceptionHandlerSpringBootTest.java`（3 条，`@SpringBootTest(classes = {GXDubboTestApp.class, ...TestController.class})` + `MockMvcBuilders.webAppContextSetup`）：① 基础 advice 与 Dubbo advice 同时注册；② `/dubbo/rpc-error` → `code=500` + `data.errorType` 存在（**不得**是"系统内部错误"）；③ `/dubbo/flow-error` → `code=406` + `data.methodName`
- [X] T016 [US1] 运行 `mvn -o -pl leaf-base-dubbo-zk test -Dtest="GXDubboFilterChainBehaviorTest,GXDubboExceptionFilterTest,GXDubboCallExceptionHandlerTest,GXDubboCallExceptionHandlerSpringBootTest"`，**记录失败清单**作为"旧代码失败"证据（E-4/E-12/E-14/`@Order`/矩阵用例应失败）

### Implementation

- [X] T017 [US1] 改造 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/filter/GXDubboExceptionFilter.java`：① 在 `GenericService` 检查之后、Sentinel 判定之前新增 `GXSentinelFlowException` **原样透传**分支；② Sentinel 判定收敛为"类名前缀 `com.alibaba.csp.sentinel.slots.block.` ∪ `BlockException` 可赋值（`static final Class<?>` 经 `Class.forName(name,false,loader)` 解析，缺失为 null）∪ 消息含完整常量 `SentinelBlockException: FlowException`"，删除裸 `"FlowException"` 子串判定；③ `NoSuchMethodException` 分支改为 DEBUG 记录后继续走"未声明运行时异常"路径（fail-closed），传入 `invocation.getParameterTypes()` 为空数组兜底；④ 外层 `catch (Throwable)` 增加 `AtomicLong FILTER_FAILURE_COUNT` 自增 + 包级 `static long filterFailureCount()`；⑤ 日志文案全 ASCII，`serviceName(invoker)` 兜底 null；⑥ 类与私有方法补必要 Javadoc（判定顺序即契约）
- [X] T018 [US1] 改造 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/handler/GXDubboCallExceptionHandler.java`：① 类上加 `@org.springframework.core.annotation.Order(Ordered.HIGHEST_PRECEDENCE)` 并补 Javadoc 说明原因；② `enrichTraceId` 改为新建 `Dict` + `putAll` 复制（不再原地修改异常 `data`）；③ `buildRpcErrorData` 内 `resolveRpcErrorType` 只计算一次并把结果传给 `resolveRpcMessage(...)`，方法签名改为接收 `String errorType`；④ 补 `@Nullable`（`data` 入参/返回值）
- [X] T019 [US1] 重跑 T016 的命令，确认 4 个测试类全绿；跨故事文件（`GXDubboExceptionFilter.java`）此时不要动 traceId 解析逻辑（留给 US2）

**Checkpoint**: US1 独立可验证——异常矩阵 E-1～E-14 中除 traceId 注入外全部通过，Web 层专用 handler 稳定生效

---

## Phase 4: User Story 2 - TraceId 全链路一致 (Priority: P1)

**Goal**: 一次调用从 selector 到两个 filter 始终同一 traceId；异常响应的 `data`/附件携带 traceId；MDC 恢复语义不被破坏

**Independent Test**: 覆盖 4 类来源组合、异步结果、异常路径、端到端一致性；清空 MDC 后异常 filter 仍能取到 id

**契约**: [contracts/dubbo-trace-context-contract.md](./contracts/dubbo-trace-context-contract.md) §1～§4

### Tests（先写）

- [X] T020 [P] [US2] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/filter/GXDubboTraceIdFilterTest.java`（8 条）：① client 优先级（invocation → client → server → MDC → 生成）；② server 优先级（invocation → server → client → MDC → 生成）；③ 正常返回后 MDC 恢复为进入前值（null → 清除）；④ `invoker.invoke` 抛 `RuntimeException` 后 MDC 仍恢复；⑤ `AsyncRpcResult` 完成回调内写入附件后回调线程 MDC 恢复；⑥ `oneTraceIdAcrossConsumerAndProvider`（selector `select` → 写入 invocation → client filter → server filter 读到同一个 id）；⑦ 附件写入位置锁定（client filter 写 invocation + client/service attachment + Result；server filter 写 invocation + client/server attachment + Result）——T-03 已接受契约；⑧ `Result` 为 null 时不 NPE
- [X] T021 [P] [US2] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/selector/GXPenetrateAttachmentSelectorTest.java`（5 条）：① `select` 优先级与 `selectReverse` 优先级各 1 条；② 返回值只含 `X-B3-TraceId` 一个键；③ `invocation`/`clientAttachment`/`serverAttachment` 为 null 时不抛异常；④ `getObjectAttachment` 为 null 但 `getAttachment` 非 null 时取到值
- [X] T022 [US2] 在 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/filter/GXDubboExceptionFilterTest.java` 追加 1 条（同文件，不标 [P]）：`attachesTraceIdFromInvocationWhenMdcCleared`——`invocation` 附件含 `trace-x`、`MDC.clear()` 后走 `GXBusinessException` 分支，断言 `data.X-B3-TraceId == "trace-x"`（F-09 修复的失败用例）
- [X] T023 [US2] 运行 US2 用例，记录 `attachesTraceIdFromInvocationWhenMdcCleared` 失败（旧代码只读 MDC）

### Implementation

- [X] T024 [US2] 改造 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/filter/GXDubboExceptionFilter.java`：新增 `private @Nullable String resolveTraceId(Invocation)`（invocation 附件 `getObjectAttachment` → `getAttachment` → MDC，空白返回 null），`onResponse` 入口只解析一次并传给 `attachTraceId(Result, String)` 与 `attachTraceId(GXBusinessException, String)`；`buildErrorData` 复用同一 traceId 参数而非再次读 MDC
- [X] T025 [P] [US2] 改造 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/filter/GXDubboClientTraceIdFilter.java` 与 `.../GXDubboServerTraceIdFilter.java`：① `GXCommonUtils.getEnvironmentValue(...)` 改调 `GXPenetrateAttachmentSelector.getCachedAppName()`；② `getInvocationAttachment` 补 `@Nullable`；③ 保持 MDC `put/restore` 与异步回调逻辑不变；④ 日志文案保持 ASCII
- [X] T026 [US2] 重跑 US2 用例（T020～T022 共 14 条）全绿，并确认 US1 用例未回归

**Checkpoint**: US1 + US2 独立可验证——异常语义与 TraceId 一致性同时成立

---

## Phase 5: User Story 3 - 并发与共享状态安全 (Priority: P2)

**Goal**: 静态 `appName` 缓存、异常内 `Dict`、`RpcContext` 双向写入三类共享状态的并发语义可判定，已接受项有文档与用例锁定

**Independent Test**: 双线程首启、连续两次 consumer 调用残留检查、共享异常实例并发注入、双 Spring 上下文缓存重置

**契约**: [data-model.md](./data-model.md) §4；[contracts/dubbo-trace-context-contract.md](./contracts/dubbo-trace-context-contract.md) §2

- [X] T027 [P] [US3] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/GXDubboSharedStateContractTest.java`（4 条）：① 两线程同时首次 `getCachedAppName()` → 返回值一致且非 `UnknownApp`；② `resetAppNameCache()` 后重新读取到新值（模拟第二上下文，F-10）；③ 同一 `GXBusinessException` 实例被两线程并发走 `onResponse` → 不抛异常、`data` 最终含 traceId（记录"原地注入为已接受契约"）；④ `RpcContext.getServiceContext().clearAttachments()` 后连续两次调用不携带上一次 traceId（F-12 语义边界）
- [X] T028 [P] [US3] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/handler/GXDubboExceptionDataContractTest.java`（2 条）：① `handleSentinelFlowException` 返回的 `data` 是副本（修改返回值不影响原异常 `data`）；② 原异常 `data` 中无新增 traceId 键（F-11 修复点）
- [X] T029 [US3] 为 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/filter/GXDubboExceptionFilter.java` 的 `attachTraceId(GXBusinessException, String)` 与 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/filter/{GXDubboClientTraceIdFilter,GXDubboServerTraceIdFilter}.java` 的 `RpcContext` 双向写入补 Javadoc：说明"原地注入异常 `data`"、"双向写入为已接受契约（下游可能读 `getServerAttachment()`）"，并注明异常实例不得跨线程共享
- [X] T030 [US3] 运行 `mvn -o -pl leaf-base-dubbo-zk test -Dtest="GXDubboSharedStateContractTest,GXDubboExceptionDataContractTest"` 连续执行 3 次确认稳定（并发用例无抖动）

**Checkpoint**: 三类共享状态均有"现状 + 契约 + 用例"，F-10/F-11/F-12 收敛完成

---

## Phase 6: User Story 4 - 热路径开销可复算 (Priority: P2)

**Goal**: 每请求 `Environment` 查询、INFO 日志、traceId 解析次数可复算且不高于修复前

**Independent Test**: `resetAppNameCache()` 前后统计环境查询次数；核心链路无 INFO；异常处理单次判定

**契约**: [contracts/dubbo-trace-context-contract.md](./contracts/dubbo-trace-context-contract.md) §5；[data-model.md](./data-model.md) §6

- [X] T031 [P] [US4] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/filter/GXDubboPerformanceContractTest.java`（3 条）：① `doesNotQueryEnvironmentPerRequest`——`resetAppNameCache()` 后第一次 filter 调用查环境、第二次不再查（用 `Environment` 替身计数或缓存值断言）；② `corePathHasNoInfoLogs`——挂 Logback `ListAppender` 到 `cn.maple.dubbo.zk` logger，跑一次 client+server filter 调用，断言 level ≥ INFO 的记录为 0；③ `resolvesTraceIdOncePerExceptionResponse`——在同一次 `onResponse` 内用可计数 `Invocation` 替身断言 traceId 解析只发生 1 次
- [X] T032 [US4] 改造 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/filter/{GXDubboClientTraceIdFilter,GXDubboServerTraceIdFilter}.java`：`log.info(...)` 改为 `log.debug(...)`（字符串模板与参数不变，仅级别）；确认 `log.isDebugEnabled()` 守卫存在
- [X] T033 [US4] 复核 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/filter/GXDubboExceptionFilter.java` 与 `.../handler/GXDubboCallExceptionHandler.java`：确认 traceId 解析单次、`resolveRpcErrorType` 单次（T017/T018/T024 已完成部分需在此核对），删除任何重复计算
- [X] T034 [US4] 按 [data-model.md](./data-model.md) §6 复算 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/` 五项口径（`envLookups`/`infoLogs`/`attachmentReads`/`traceIdResolutions`/`errorTypeComputations`），与"修复前"数值对照并记录在提交说明（不新增文档文件）

**Checkpoint**: 五项性能口径可复算，`envLookups` 与 `infoLogs` 不高于修复前

---

## Phase 7: User Story 5 - 装配契约显式且可自检 (Priority: P2)

**Goal**: 缺失 `-exception` 时启动期告警；BeanPostProcessor 绑定失败可观测；装配方式有测试证明

**Independent Test**: 三种配置启动场景的告警断言；最小上下文下 processor 装配与绑定回读

**契约**: [contracts/dubbo-config-observability-contract.md](./contracts/dubbo-config-observability-contract.md) §1～§3

### Tests

- [X] T035 [P] [US5] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/config/GXDubboProviderFilterValidatorTest.java`（3 条）：① 未配置 → `isBuiltInExceptionFilterExcluded("")` 为 false 且 `afterSingletonsInstantiated` 输出 ERROR（用 `ListAppender` 断言）；② 配置含 `-exception` → 无 ERROR；③ 只写 `dubboExceptionFilter` 无 `-exception` → 有 ERROR，且构造/执行不抛异常（不阻断启动）
- [X] T036 [P] [US5] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/processor/GXDubboRpcApiBeanPostProcessorTest.java`（6 条）：`ref` 为 null、`getGenericClassType` 返回 null、`getBeanNamesForType` 为空、多候选（记录 INFO 且仍绑定）、绑定成功、绑定后 `getServeServiceClass()` 与目标不一致时 WARN 且返回原 bean
- [X] T037 [P] [US5] 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/processor/GXDubboRpcApiBeanPostProcessorSpringBootTest.java`（1～2 条）：`@SpringBootTest(classes = 最小配置类)` + `@Import(GXDubboRpcApiBeanPostProcessor.class)`，断言 processor 作为 bean 存在且 `postProcessAfterInitialization` 对普通 bean 原样返回

### Implementation

- [X] T038 [US5] 新增 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/config/GXDubboProviderFilterValidator.java`：`@Component` + `SmartInitializingSingleton`，构造注入 `Environment`，`afterSingletonsInstantiated` 读 `dubbo.provider.filter`，未含 `-exception` 时输出 ERROR（文案见契约 §2，ASCII，含修复指引与 current 值）；`static boolean isBuiltInExceptionFilterExcluded(String)` 供测试
- [X] T039 [US5] 改造 `leaf-base-dubbo-zk/src/main/java/cn/maple/dubbo/zk/processor/GXDubboRpcApiBeanPostProcessor.java`：① `@Log4j2` 改 `@Slf4j`；② 绑定调用后回读 `GXBaseServeApi#getServeServiceClass()`（经 `reflectCallObjectMethod`），与 `targetService` 不一致时输出 WARN（含 bean 名与期望类型），一致时保留原 INFO；③ `getBeanNamesForType`/`getApplicationContext` 相关变量补 `@Nullable`
- [X] T040 [US5] 运行 US5 三个测试类全绿，并确认无 INFO 级每请求日志新增

**Checkpoint**: 装配缺失可自检、绑定失败可观测、装配方式有启动期测试

---

## Phase 8: User Story 6 - 交付集与测试门槛 (Priority: P1)

**Goal**: `AGENTS.md` 与实现零漂移；新增开发手册；全量测试全绿且用例数从 0 单调增长

**Independent Test**: 抽查任一能力声明与代码一致；`mvn -o -pl leaf-base-dubbo-zk test` 全绿

**依据**: [plan.md](./plan.md) D-07；[quickstart.md](./quickstart.md) §7

- [X] T041 [US6] 重写 `leaf-base-dubbo-zk/AGENTS.md`：① §1 模块定位补齐四项事实能力（Consumer TraceId 透传、Provider TraceId 恢复、`PenetrateAttachmentSelector`、`ServiceBean` 绑定增强）；② §4 功能基线改为与实际判定一致（Sentinel 三类判定条件、handler 同时处理 `RpcException` 与 `GXSentinelFlowException`、删除"按代码来源决定透传"的错误描述、补 `-exception` 强制装配）；③ §6 测试要求改为"已有 N 个测试类 / M 条用例，新增改动必须在其基础上补齐"并写入实测数字
- [X] T042 [US6] 新增 `docs/leaf-base-dubbo-zk开发手册.md`：模块定位、SPI 扩展名表、TraceId 优先级表（consumer/provider/selector/exception filter）、异常分类矩阵（引用契约 §1）、强制装配配置（provider 与 consumer 两侧推荐值 + 缺失后果）、启动期告警说明、`serialize.allowlist` 用途、已知问题（`data == null` 降级、`WRAN` 拼写）、不支持范围、离线测试说明
- [X] T043 [US6] 逐条核对 `spec.md` 的 23 条发现状态（[data-model.md](./data-model.md) §1.2）：确认 17 条 `Remediated` 均有对应生产代码改动 + 通过的用例，6 条 `Accepted` 均有 Javadoc/手册条目；把缺失项补进手册或代码注释（不新增文档）
- [X] T044 [US6] 执行 `mvn -o -pl leaf-base-dubbo-zk test`，断言 `Tests run ≥ 28`、`Failures: 0`、`Errors: 0`、`Skipped: 0`，并把"测试类数 / 用例数 / 总耗时"写回 T041 的 AGENTS 与 T042 手册
- [X] T045 [US6] 在 `e:\project\java\maple-leaf-framework` 执行 `git diff --stat` 与 `git diff`，确认 `leaf-base-dubbo-zk/src/main/resources/**`（`META-INF/dubbo/*`、`security/serialize.allowlist`）零改动、公开类名/方法签名零变更、配置键零新增、`config/` 未被修改

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 残留清理与提交前门禁

- [X] T046 [P] 执行 `rg -n "@Log4j2|log\.info" leaf-base-dubbo-zk/src/main/java`，确认核心链路无 INFO、无 `@Log4j2` 残留（`GXDubboCallExceptionHandler` 的异常 ERROR 日志允许保留）
- [X] T047 [P] 执行 `rg -n "public static" leaf-base-dubbo-zk/src/main/java`，确认新增公共 API 均被 ≥2 处使用（`getCachedAppName`/`resetAppNameCache`），`filterFailureCount()` 保持包级可见且有测试使用者（宪法附加约束 §8）
- [X] T048 [P] 执行 `rg -n "FlowException" leaf-base-dubbo-zk/src/main/java`，确认不存在裸 `"FlowException"` 子串判定
- [X] T049 执行 `specs/015-dubbo-zk-module-audit/quickstart.md` §7 交付检查单（`leaf-base-dubbo-zk/**` 与 `docs/leaf-base-dubbo-zk开发手册.md`），逐项打勾并记录最终命令输出

---

## Dependencies

```text
Phase 1 (T001-T007, Setup)
   └─> Phase 2 (T008-T010, Foundational)
          ├─> US1 (T011-T019, P1)  ← MVP
          ├─> US2 (T020-T026, P1)  ← 依赖 T017（同一文件 GXDubboExceptionFilter.java 先完成判定顺序改造）
          ├─> US3 (T027-T030, P2)  ← 依赖 T020/T023（用例夹具）与 T009（appName 缓存）
          ├─> US4 (T031-T034, P2)  ← 依赖 T025（共享 appName）与 T024（traceId 单次解析）
          ├─> US5 (T035-T040, P2)  ← 仅依赖 Phase 1/2，可与其他故事并行
          └─> US6 (T041-T045, P1)  ← 依赖 US1～US5 全部实现（文档必须描述最终状态）
                 └─> Phase 9 (T046-T049)
```

跨故事文件冲突点（**必须串行**）：

| 文件 | 涉及任务 | 顺序 |
| --- | --- | --- |
| `filter/GXDubboExceptionFilter.java` | T017 → T024 | US1 先，US2 后 |
| `filter/GXDubboExceptionFilterTest.java` | T012 → T013 → T022 | 同文件内顺序执行 |
| `filter/{GXDubboClient,Server}TraceIdFilter.java` | T025 → T032 | US2 先，US4 后（仅日志级别） |

## Parallel Example: 可并行启动的任务组

```text
# Setup 阶段（不同文件）
T002 目录骨架 | T003 AsciiSourceGuardTest | T004 GXDubboTestSupport | T005 GXDubboTestApp | T006 桩类

# US1 阶段测试先行
T011 GXDubboFilterChainBehaviorTest | T014 GXDubboCallExceptionHandlerTest | T015 ...SpringBootTest

# US5 可在 US1/US2 进行时并行（不与主链路文件重叠）
T035 ValidatorTest | T036 BeanPostProcessorTest | T037 ...SpringBootTest | T038 Validator 实现 | T039 Processor 改造

# US2 阶段
T020 GXDubboTraceIdFilterTest | T021 GXPenetrateAttachmentSelectorTest | T025 两个 TraceId Filter 改造
```

## Implementation Strategy

### MVP First（US1 + US2，均为 P1）

1. Phase 1 Setup（T001-T007）：先把测试基线立起来，记录 `Tests run: 0`
2. Phase 2 Foundational（T008-T010）：appName 缓存与新包就位
3. Phase 3 US1（T011-T019）：先失败后通过，交付"业务码无损 + Web handler 稳定"——这是线上风险最高的部分
4. Phase 4 US2（T020-T026）：TraceId 一致性与异常响应 traceId
5. **STOP & VALIDATE**：`mvn -o -pl leaf-base-dubbo-zk test` 全绿，跑通 [quickstart.md](./quickstart.md) §4 的 F-01/F-02/F-03/F-09 四行

### Incremental Delivery

1. Setup + Foundational → 基线可测
2. US1 → MVP（异常契约）→ 独立验证
3. US2 → TraceId 一致 → 独立验证
4. US3 → 并发契约固化（不改行为，只补文档与用例）
5. US4 → 性能口径达标（日志降级 + 单次解析）
6. US5 → 装配自检（新增校验器 + 绑定可观测）
7. US6 → 文档与测试门槛收口（用例数写入 AGENTS/手册）
8. Polish → 残留扫描 + 交付检查单

### 并行团队策略

- 开发者 A：US1（T011-T019）
- 开发者 B：US5（T035-T040，文件不重叠）+ Phase 9 的 T046-T048
- 开发者 C：US2（T020-T026，注意 `GXDubboExceptionFilter.java` 需等 A 的 T017 合并）
- US3/US4 在 B/C 完成后接手（依赖 T009、T025）

## Notes

- **[P] 任务**：不同文件且无未完成依赖；同一 `.java` 文件的多个任务一律不加 `[P]`
- **测试先行**：每个故事的测试任务必须先运行并记录失败（T016/T023 等），实现后转为通过；无法先失败的用例（如 `@Order` 注解断言）需在任务描述中说明"无旧代码失败形态，属防回归锁定"
- **禁止**：修改 `src/main/resources/**`、`config/**`、其他 Maven 模块；删除或 `@Disabled` 任何用例；用反射访问私有成员构造现场（FR-023）
- **提交粒度**：每个故事完成即一次提交，提交信息格式 `fix(dubbo-zk): ...` / `test(dubbo-zk): ...` / `docs(dubbo-zk): ...`
- **离线**：全部用例不得连接 Zookeeper/Nacos；`@SpringBootTest` 只用 `GXDubboTestApp` 这类最小配置

---

## Phase 10: Convergence

**Purpose**: 补齐实现阶段遗留的装配验证、行为锁定与文档一致性缺口。本阶段由 `/speckit.converge` 追加，不修改既有任务。

- [X] T050 新增 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/config/GXDubboProviderFilterValidatorSpringBootTest.java`：`@SpringBootTest(classes = {GXDubboTestApp.class, ...}, properties = {"spring.cloud.nacos.config.import-check.enabled=false", "spring.cloud.nacos.discovery.enabled=false"})` + `@TestConfiguration` 注册 `GXDubboProviderFilterValidator`，断言 ① 校验器 bean 存在于真实上下文、② `afterSingletonsInstantiated()` 可被执行且不抛异常（`dubbo.provider.filter` 缺失时也不阻断启动）、③ 传入 `MockEnvironment` 的等价判定与 `buildWarningMessage` 一致 per Constitution VI (partial)
- [X] T051 在 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/filter/GXDubboExceptionFilterTest.java` 新增 `convertsBusinessExceptionWhoseCauseIsBlockException`：构造 `new GXBusinessException("biz", 4100, Dict.create(), new DegradeException("degrade"))`，断言最终为 `GXSentinelFlowException(406)` 且 `data.errorType=sentinelFlow`，锁定"Sentinel 分支先于业务异常分支"的既有顺序 per FR-005 (partial)
- [X] T052 更新 `specs/015-dubbo-zk-module-audit/contracts/dubbo-exception-contract.md` §1：在判定顺序第 5 条后补注"该分支先于 `GXBusinessException` 透传，因此 `GXBusinessException` 的 cause 链含 `BlockException` 时会被转换为限流异常并丢弃业务码"；同时更新 `specs/015-dubbo-zk-module-audit/quickstart.md` §4 的用例引用为实际名称（`builtInExceptionFilterActiveLosesBusinessCode`、`builtInExceptionFilterExcludedKeepsBusinessCode`、`consumerSideWrapsUnclassifiedRuntimeException`、`readsEnvironmentOnlyOnceForCachedAppName`、`resetAppNameCacheAllowsSecondContextToBeRead`、`keepsStartupAliveWhenBindingIsNotEffective`）per FR-022 (partial)
- [X] T053 在 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/filter/GXDubboPerformanceContractTest.java` 新增 `attachmentReadsStayWithinBudget`：用可计数 `Invocation` 替身统计单次 `onResponse` 中 `getObjectAttachment` + `getAttachment` 调用次数并断言 ≤4（与 `data-model.md` §6 `attachmentReads` 口径一致）per FR-014 (partial)
- [X] T054 补边界用例：① `GXDubboExceptionFilterTest` 新增 `ignoresBlankTraceIdAndUsesMdcFallback`（invocation 附件为 `"   "`、MDC 为 `"mdc-trace"` 时取 MDC）；② `GXDubboExceptionFilterTest` 新增 `keepsJakartaPrefixedExceptionUnchanged`（`jakarta.` 前缀运行时异常透传，用 `jakarta.validation.ValidationException` 或等价类型）；③ `GXDubboTraceIdFilterTest` 新增 `regeneratesWhenAttachmentsAreBlank`（invocation/client/server 附件均为空白串时生成新 id）per spec Edge Cases (partial)

---

## Phase 11: Convergence

**Purpose**: 消除文档间关于 cause 链限流判定的表述差异，并把 FR-007 列出的其余 `BlockException` 子类纳入验证。本阶段由 `/speckit.converge` 追加，不修改既有任务。

- [X] T055 更新 `specs/015-dubbo-zk-module-audit/data-model.md` §2：为 E-1/E-3 的补充行与 E-14 增补"判定会遍历 `cause` 链，当 `GXBusinessException` 的 cause 链含 `BlockException` 子类时按 E-10 处理（丢业务码）"；同步在 `leaf-base-dubbo-zk/AGENTS.md` §4 第 3 条补写 cause 链遍历与上述后果，使三处表述（contract §1、data-model、AGENTS）完全一致 per FR-022 (partial)
- [X] T056 新增 `leaf-base-dubbo-zk/src/test/java/com/alibaba/csp/sentinel/slots/block/system/SystemBlockException.java` 与 `.../param/ParamFlowException.java` 桩类（均继承 `BlockException`），并在 `leaf-base-dubbo-zk/src/test/java/cn/maple/dubbo/zk/filter/GXDubboExceptionFilterTest.java` 新增参数化用例 `convertsAllSentinelBlockSubtypes`（Flow/Degrade/Authority/System/ParamFlow 五类各断言 `GXSentinelFlowException(406)` 且 `data.errorType=sentinelFlow`），补齐 FR-007 列出的全部官方子类验证 per FR-007 (partial)

