# Phase 1 数据模型：leaf-base-dubbo-nacos 审查

**Feature**: 014-dubbo-nacos-module-audit | **Date**: 2026-10-25 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

本文件定义审查交付物与修复后行为所需的**领域对象**（不含实现代码）。所有实体服务于两个用途：让结论可复核（Finding / Evidence / 各矩阵），让修复可验收（异常分类矩阵 / TraceId 链 / 共享状态 / 性能口径 / SPI 契约 / 测试矩阵）。

---

## 1. Finding（审查发现）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `F-nn` | 唯一、稳定（修复后不复用编号） | 与 spec 的 F-01 ~ F-16 一一对应 |
| `severity` | `HIGH / MEDIUM / LOW` | 必填 | 高 2 / 中 7 / 低 7 |
| `dimension` | `CORRECTNESS / THREAD_SAFETY / PERFORMANCE / SECURITY_CONTRACT` | 必填、可多值 | 与 spec 维度表一致 |
| `location` | `文件#行区间` | 必填、可多条 | 只指向**当前**源码 |
| `trigger` | 文本 | 必填 | 触发条件 |
| `impact` | 文本 | 必填 | 影响面 |
| `evidenceRefs` | `E-nn[]` | 至少 1 条 | 指向 Evidence |
| `repro` | 文本 | 必填 | 既有用例 / 新增用例设计 / 构造步骤 |
| `regressionTest` | 文本 | 必填 | "旧代码失败、新代码通过"的用例名与断言点 |
| `fixBatch` | `B1..B6` | 必填 | 归属批次（供 `/speckit.tasks` 拆解） |
| `verified` | `boolean` | 必填 | `false` 时判定降级为"未验证假设"（FR-003）；C-02 已在 Phase 0 升级为 `true` |
| `status` | `OPEN / FIXED / ACCEPTED_RISK` | 初始 `OPEN` | `ACCEPTED_RISK` 必须给出理由与到期版本 |

**校验规则**：`severity=HIGH` 必须同时具备 `repro` 与 `regressionTest`（SC-007）；`status=FIXED` 必须存在通过的同名用例（FR-021）。

### 1.1 发现清单

| ID | 级别 | 维度 | 位置 | 触发条件 | 摘要 | 批次 | 已验证 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | HIGH | CORRECTNESS | `GXDubboExceptionFilter.java#28`、`config/online-nacos-dubbo-demo.yml#21` | provider 未配置 `dubbo.provider.filter` 或未含 `-exception`，且抛出未声明 `GXBusinessException` | 内置 `ExceptionFilter` 未排除时把 `GXBusinessException` 包成 `RuntimeException`，业务码丢失；排除要求仅存在于示例配置 | B1 | ✅ R-02/R-03 |
| F-02 | HIGH | CORRECTNESS | `GXDubboCallExceptionHandler.java#14-27`、`GXExceptionHandler.java#42,226` | 组件扫描加载基础 advice + 本模块 advice；请求抛 `RpcException`/`GXSentinelFlowException`；基础 advice 先匹配 | 两个 `@RestControllerAdvice` 同时注册且无 `@Order`，`RpcException` 可能被兜底 `Exception` handler 遮蔽 | B1 | ✅ R-04 |
| F-03 | MEDIUM | CORRECTNESS | `GXDubboExceptionFilter.java#105-119` | 抛出 Sentinel `DegradeException`/`AuthorityException` 等非 Flow/Block；或普通异常消息含 "FlowException" | Sentinel 仅识别 Flow/Block 精确类名 + 消息含 "FlowException"，其它阻断类型漏转、业务异常误判 | B2 | ✅ |
| F-04 | MEDIUM | CORRECTNESS | `GXDubboExceptionFilter.java#66-77` | `invocation.getParameterTypes()` 与实际签名不一致（泛型擦除/重载/原始类型）且抛出未声明运行时异常 | 反射解析 `NoSuchMethodException` 时直接 `return`，未声明运行时异常 fail-open | B2 | ✅ |
| F-05 | MEDIUM | CORRECTNESS | `GXDubboExceptionFilter.java#97-101` | filter 自身逻辑抛异常（`invoker.getInterface()` 为 null、`getMethod` 抛非 `NoSuchMethodException`） | 外层 `catch(Throwable)` 静默吞掉 filter 自身异常（仅 WARN，无计数） | B2 | ✅ |
| F-06 | LOW | CORRECTNESS | `GXDubboCallExceptionHandler.java#17-21` | consumer 收到非超时类 `RpcException`（网络/序列化/限流等） | 所有 `RpcException` 子类统一 500，仅 `errorType` 区分 | B2 | ✅（按风险接受，见 D-08） |
| F-07 | LOW | CORRECTNESS | `GXDubboExceptionFilter.java#142-154` | provider 抛出的 `GXBusinessException` 子类 `data == null` | `data==null` 时 `GXBusinessException` 子类被降级为基类 | B2 | ✅（按风险接受，见 D-09） |
| F-08 | MEDIUM | THREAD_SAFETY | `GXPenetrateAttachmentSelector.java#26,118-128` | 同一 JVM 多上下文；或并发首次调用 `getAppName()` | 静态 `volatile appName` 跨上下文缓存 + 惰性初始化非原子 | B3 | ✅ |
| F-09 | MEDIUM | THREAD_SAFETY / PERFORMANCE | `GXDubboClientTraceIdFilter.java#31`、`GXDubboServerTraceIdFilter.java#31` | 每请求进入 TraceId Filter（热路径） | 每请求读 Spring 上下文单例；Filter 不缓存 appName 而 Selector 缓存 | B3/B4 | ✅ |
| F-10 | MEDIUM | PERFORMANCE | `GXDubboClientTraceIdFilter.java#32`、`GXDubboServerTraceIdFilter.java#32` | 每请求进入 TraceId Filter 且日志级别为 INFO（默认） | 每请求 INFO 日志；环境查找不受日志级别影响 | B4 | ✅ |
| F-11 | LOW | PERFORMANCE | `GXDubboClientTraceIdFilter.java#50-68`、`GXDubboServerTraceIdFilter.java#50-70` | 每请求解析 TraceId，且 attachment 多来源 | TraceId 解析多路双读、无解析结果缓存 | B4 | ✅ |
| F-12 | MEDIUM | SECURITY_CONTRACT | `security/serialize.allowlist#1` | 新增跨进程传输对象触发 Dubbo 序列化安全校验 | 白名单仅 `HashBasedTable`、无来源/影响说明 | B5 | ✅ |
| F-13 | LOW | SECURITY_CONTRACT | `docs/`、`AGENTS.md#89` | 使用方按文档/AGENTS 排查或配置测试 | 缺开发手册；`AGENTS.md` 称"无 src/test"与实际 9 测试类漂移 | B5 | ✅ |
| F-14 | LOW | SECURITY_CONTRACT | `GXDubboRpcApiBeanPostProcessor.java#20` | 启动/运行 `GXDubboRpcApiBeanPostProcessor` 输出日志 | `@Log4j2` 违反 SLF4J 门面约定 | B5 | ✅ |
| F-15 | LOW | SECURITY_CONTRACT | 4 个 `package-info.java` | NullAway 构建检查 / IDE 空值提示 | 空值契约零 `@Nullable` | B5 | ✅ |
| F-16 | LOW | CORRECTNESS / TEST | `GXDubboCallExceptionHandlerSpringBootTest.java#25-27` | 依赖既有测试判断 advice 排序正确性 | 名为 SpringBootTest 实为 `standaloneSetup`，未验证真实 advice 装配；缺真实 Dubbo 链用例 | B6 | ✅ R-12 |

### 1.2 既定契约（P-OK，不得破坏）

| ID | 契约 | 锁定用例 |
| --- | --- | --- |
| P-OK-01 | TraceId Filter 的 `finally` 恢复 + 异步 `whenCompleteWithContext` 二次捕获/恢复，MDC 不泄漏 | `GXDubboTraceIdFilterTest`（5 条） |
| P-OK-02 | `buildErrorData` 不泄露参数值（仅 `argumentCount`/`parameterTypes`） | `convertsSentinelFlowExceptionByMessageWithoutLeakingArguments` |
| P-OK-03 | `GXBusinessException` 透传、`GenericService` 跳过、checked/声明/JDK/jakarta/`RpcException`/MyBatis 异常分别透传 | `GXDubboExceptionFilterTest`（11 条） |
| P-OK-04 | Selector 只透传 TraceId（1 项）、优先级、null 安全 | `GXPenetrateAttachmentSelectorTest`（5 条） |
| P-OK-05 | BeanPostProcessor 各跳过/成功分支、返回原 bean | `GXDubboRpcApiBeanPostProcessorTest`（4 条）+ `...SpringBootTest`（1 条） |
| P-OK-06 | 既有 31 条用例全绿 | `mvn -o -pl leaf-base-dubbo-nacos test` |

---

## 2. Evidence（证据）

| ID | 类型 | 定位 | 事实 | verified |
| --- | --- | --- | --- | --- |
| E-01 | SOURCE | `GXDubboExceptionFilter.java#28` | `@Activate(group = CommonConstants.PROVIDER)`，继承 `ExceptionFilter` | ✅ |
| E-02 | COMMAND | `javap -v -cp dubbo-3.3.6.jar org.apache.dubbo.rpc.filter.ExceptionFilter` | 内置 `ExceptionFilter` 为 `@Activate(group=provider)`，实现 `Filter, BaseFilter$Listener`，把未声明运行时异常包成 `new RuntimeException(StringUtils.toString(e))` | ✅ |
| E-03 | SOURCE | `dubbo-3.3.6.jar!/META-INF/dubbo/internal/org.apache.dubbo.rpc.Filter` | `exception=org.apache.dubbo.rpc.filter.ExceptionFilter` | ✅ |
| E-04 | SOURCE | `config/online-nacos-dubbo-demo.yml#21` | `filter: dubboExceptionFilter,-exception`（唯一的排除配置） | ✅ |
| E-05 | SOURCE | `GXDubboExceptionFilter.java#61-64` | `GXBusinessException` 分支依赖 filter 被激活且内置已排除 | ✅ |
| E-06 | SOURCE | `GXExceptionHandler.java#42,226-231` | 基础 advice 是 `@RestControllerAdvice` 且含 `@ExceptionHandler(Exception.class)` 兜底 | ✅ |
| E-07 | SOURCE | `GXEnableLeafFramework.java#12-20` | `@ComponentScan({"cn.maple"})` | ✅ |
| E-08 | SOURCE | `GXDubboCallExceptionHandler.java#14` | `@RestControllerAdvice`，无 `@Order` | ✅ |
| E-09 | SOURCE | `GXDubboExceptionFilter.java#105-119` | Sentinel 精确类名 + 消息 `contains("FlowException")` | ✅ |
| E-10 | SOURCE | `GXDubboExceptionFilter.java#66-77` | `NoSuchMethodException` → `return` | ✅ |
| E-11 | SOURCE | `GXDubboExceptionFilter.java#97-101` | 外层 `catch (Throwable)` 仅 WARN | ✅ |
| E-12 | SOURCE | `GXPenetrateAttachmentSelector.java#26,118-128` | 静态 `volatile appName` + 惰性读改写 | ✅ |
| E-13 | SOURCE | `GXDubboClientTraceIdFilter.java#31`、`GXDubboServerTraceIdFilter.java#31` | 每请求 `getEnvironmentValue` | ✅ |
| E-14 | SOURCE | `GXTraceIdContextUtils.java#18-59` | `getTraceId` 对 null 返回 `""`；`put/restore(blank)` 等价 remove | ✅ |
| E-15 | SOURCE | `GXCommonUtils.java#99-190,716-724,395-429` | 环境查找每次走上下文；泛型解析可返回 null；反射失败返回 null 不抛 | ✅ |
| E-16 | SOURCE | `security/serialize.allowlist#1` | 仅 `HashBasedTable` | ✅ |
| E-17 | SOURCE | `GXDubboRpcApiBeanPostProcessor.java#20` | `@Log4j2` | ✅ |
| E-18 | SOURCE | `AGENTS.md#89` | "当前模块下没有现成的 src/test 测试目录" | ✅ |
| E-19 | COMMAND | `mvn -o -pl leaf-base-dubbo-nacos test` | 31 执行 / 0 失败 / BUILD SUCCESS | ✅ |
| E-20 | SOURCE | `GXDubboCallExceptionHandlerSpringBootTest.java#25-27` | `standaloneSetup`，非真实 Spring Boot 上下文 | ✅ |

---

## 3. ExceptionClassificationMatrix（异常分类矩阵）

行 = 异常类型，列 = 观测点。修复后期望值以 **`-exception` 已排除内置 filter** 为前提（F-01 的强制装配契约）。

| 异常类型 | 处置 | 最终异常类型 | 业务字段 | 日志 | TraceId 增强 | 证据（E / 用例） | 现状 → 期望 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `GXBusinessException`（data 非空） | 透传 | 原 `GXBusinessException` | 保留 `code/msg/data` | 不额外记 | `data.traceId` + `Result` attachment | E-05 / `#keepsBusinessExceptionUnchanged` | 现状：内置激活时被包成 `RuntimeException` → 期望：原样透传 |
| `GXBusinessException`（data 为 null） | 透传 + 包装 | 新 `GXBusinessException`（同 code/msg，cause=原） | `data.traceId` 新增 | 不额外记 | 同上 | `#wrapsBusinessExceptionWhenDataIsNullToExposeTraceId` | 现状 = 期望（已按设计包装） |
| 声明异常（`throws` 中） | 透传 | 原异常 | 保留 | 不额外记 | `Result` attachment | `#keepsDeclaredExceptionUnchanged` | 现状 = 期望 |
| checked 异常（非 Runtime） | 透传 | 原异常 | 保留 | 不额外记 | `Result` attachment | `#keepsCheckedExceptionUnchanged` | 现状 = 期望 |
| `RpcException` | 透传 | 原 `RpcException` | 保留 | 不额外记 | `Result` attachment | `#keepsRpcExceptionUnchanged` | 现状 = 期望 |
| `java.*` / `jakarta.*` 异常 | 透传 | 原异常 | 保留 | 记 ERROR | `Result` attachment | `#keepsJdkExceptionUnchanged` | 现状 = 期望 |
| MyBatis `MyBatisSystemException` | 透传 | 原异常 | 保留 | 不额外记 | `Result` attachment | `#keepsMyBatisSystemExceptionUnchanged` | 现状 = 期望 |
| Sentinel `BlockException` 全子类 | 转换 | `GXSentinelFlowException(406)` | `data.traceId/errorType/interfaceName/methodName/argumentCount/parameterTypes` | 不记（由外层统一） | `data.traceId` + attachment | E-09 / `#convertsSentinelFlowExceptionByMessageWithoutLeakingArguments` + `#convertsDegradeAndAuthorityExceptions` | 现状：Degrade/Authority 被误判为 providerRuntime → 期望：406 |
| 未声明运行时异常（签名可解析） | 包装 | `GXBusinessException(500)` | `data.errorType=providerRuntime` | 记 ERROR | `data.traceId` + attachment | `#wrapsUndeclaredRuntimeExceptionFromDifferentCodebase` | 现状 = 期望 |
| 未声明运行时异常（签名**不可解析**） | 包装（fail-closed） | `GXBusinessException(500)` | `data.errorType=providerRuntime` | 记 ERROR | 同上 | E-10 / `#wrapsUndeclaredRuntimeWhenMethodSignatureUnresolvable` | 现状：直接透传 → 期望：包装 |
| `GenericService` 调用 | 跳过分类 | 原异常 | 保留 | 不额外记 | `Result` attachment | `#skipsGenericServiceExceptionWrappingButStillAttachesTraceId` | 现状 = 期望 |

**Filter 装配矩阵（F-01 的核心）**：

| provider filter 配置 | 内置 `exception` | 自定义 `dubboExceptionFilter` | `GXBusinessException` 结果 | 判定 | 证据 |
| --- | --- | --- | --- | --- | --- |
| 未配置（默认） | 激活 | 激活 | 被内置包成 `RuntimeException`，业务码丢失 | **缺陷（F-01）** | E-02 / `GXDubboFilterChainBehaviorTest#businessExceptionLosesCodeWhenBuiltInFilterActive` |
| `dubboExceptionFilter,-exception` | 排除 | 激活 | 原样透传（+traceId 增强） | 正确（强制契约） | E-04 / `#businessExceptionPreservedWhenBuiltInFilterExcluded` |
| `-exception` 但未列 `dubboExceptionFilter` | 排除 | 未激活 | 无人包装，原始异常直达 consumer | 不推荐（丢失统一收敛） | E-03 |
| `dubboExceptionFilter`（未 `-exception`） | 激活 | 激活 | 同"未配置"，仍可能被内置包装 | 缺陷 | `GXDubboProviderFilterValidatorTest#enabledCustomFilterWithoutExclusionIsNotEnough` |

**Web 层 advice 矩阵（F-02 的核心）**：

| advice 排序 | `RpcException` 命中 | `GXSentinelFlowException` 命中 | 判定 | 证据 |
| --- | --- | --- | --- | --- |
| 子类（Dubbo handler）先 | `handleRpcException` → code 500 + `errorType` | `handleSentinelFlowException` → code 406 | 正确（D-02 后稳定） | `@Order(HIGHEST_PRECEDENCE)` / `GXDubboCallExceptionHandlerSpringBootTest#rpcExceptionAdviceHandlesRealMvcRequest` |
| 基础 advice 先 | `handleException` → "系统内部错误" | `handleException` → "系统内部错误" | **缺陷（F-02）** | E-06 / `GXDubboAuditReproTest#f02AdviceShadowedByBaseHandler` |

---

## 4. TraceIdPriorityChain（TraceId 优先级链）

| 方向 | 优先级（高 → 低） | 恢复语义 | 证据 | 现状 → 期望 |
| --- | --- | --- | --- | --- |
| Consumer 请求（`GXDubboClientTraceIdFilter`） | invocation attachment → client attachment → server attachment → ThreadLocal → generate | `finally restoreTraceId(original)`；异步 `whenCompleteWithContext` 二次捕获/恢复 | P-OK-01 / `#clientFilterPropagatesInvocationTraceIdAndRestoresOriginalTraceId` + `#resolveTraceIdKeepsClientCustomOrder` | 现状 = 期望（已正确） |
| Provider 请求（`GXDubboServerTraceIdFilter`） | invocation attachment → **server** attachment → client attachment → ThreadLocal → generate | 同上 | P-OK-01 / `#serverFilterUsesServerAttachmentAndRestoresTraceIdWhenInvokerThrows` + `#resolveTraceIdKeepsServerCustomOrder` | 现状 = 期望（已正确） |
| Selector 请求（`select`） | invocation attachment → client attachment → server attachment → ThreadLocal → generate | 无（只读，返回只含 TraceId 的 Map） | P-OK-04 / `#selectUsesInvocationTraceIdFirstAndOnlyReturnsTraceId` | 现状 = 期望（已正确） |
| Selector 响应（`selectReverse`） | invocation attachment → **server** attachment → client attachment → ThreadLocal → generate | 无（只读） | P-OK-04 / `#selectReversePrefersServerAttachment` | 现状 = 期望（已正确） |

**约束**：四条链的差异（client 先 client、server 先 server）是**有意镜像**，不得"统一成一版"；响应链 `selectReverse` 必须 server 优先（provider 回传的 TraceId 在 server attachment）。

---

## 5. SharedStateInventory（共享状态清单）

| 对象 | 共享范围 | 现状保护 | 收敛路径 |
| --- | --- | --- | --- |
| `GXPenetrateAttachmentSelector.appName`（`static volatile`） | 全 JVM（跨 Spring 上下文） | `volatile` 保证可见性；惰性读改写非原子（良性重复计算） | D-06：提升为共享持有者 + `resetAppNameCache()`；两个 TraceId Filter 复用 |
| Spring `ApplicationContext` 单例（`GXSpringContextUtils`） | 全 JVM | 由 `GXApplicationContextSingleton` 持有 | 热路径不再每请求读取（D-06） |
| MDC（`GXTraceIdContextUtils`） | 每线程 | `put`/`restore` 成对 + `finally` | 保持（P-OK-01，不改） |
| `GXDubboRpcApiBeanPostProcessor` 的 BeanPostProcessor 状态 | 无状态 | 无字段 | 保持 |
| 测试夹具（静态 `API`/`GXApplicationContextSingleton`） | 测试类级 | `try/finally` 清理 | 保持，新增用例沿用 |

---

## 6. PerformanceMetric（性能口径）

不设吞吐/时延门禁（SC-005），只要求可复算。

| 指标 | 修复前（每请求） | 修复后目标 | 复算方式 |
| --- | --- | --- | --- |
| `GXCommonUtils.getEnvironmentValue` 调用 | 1（每个 TraceId Filter） | 0（读静态缓存） | 代码审查 + 日志/断点计数 |
| `getApplicationContext()` 读取 | ≥1 | 0（热路径） | 同上 |
| INFO 日志条数（TraceId Filter） | 1 | 0（降 DEBUG） | 测试中 `ListAppender` 计数 |
| attachment 读取（`getObjectAttachment` + `getAttachment`） | 每来源最多 2 | 保持 ≤2，不新增 | 单测 mock 计数 |
| `getBean`/反射次数（异常路径） | 0（异常 filter 不取 bean） | 0 | 代码审查 |
| `Arrays.stream(...).toList()`（`buildErrorData`） | 1（异常路径） | 保持 | 代码审查 |
| 测试墙钟 | 31 条秒级 | 保持秒级 | `mvn` 输出 |

---

## 7. SpiContractTable（SPI 契约表）

| 资源文件 | 扩展名 | 实现类 | 激活分组 | 与内置/其它扩展的关系 |
| --- | --- | --- | --- | --- |
| `META-INF/dubbo/org.apache.dubbo.rpc.Filter` | `dubboExceptionFilter` | `GXDubboExceptionFilter` | provider | 与内置 `exception` **互斥**，须 `provider.filter=...,-exception`（F-01） |
| 同上 | `dubboClientTraceIdFilter` | `GXDubboClientTraceIdFilter` | consumer | 与内置无冲突（consumer 无内置同名） |
| 同上 | `dubboServerTraceIdFilter` | `GXDubboServerTraceIdFilter` | provider | 与内置无冲突 |
| `META-INF/dubbo/org.apache.dubbo.rpc.PenetrateAttachmentSelector` | `dubboPenetrateAttachmentSelector` | `GXPenetrateAttachmentSelector` | provider + consumer | 与内置 `DefaultPenetrateAttachmentSelector` 并存（选择器可多个） |
| `security/serialize.allowlist` | — | — | — | 仅 `com.google.common.collect.HashBasedTable`（F-12） |

**契约约束**：扩展名是外部装配点，**不得重命名**（宪法 II + AGENTS.md 第 66 行）；新增扩展必须同步登记手册。

---

## 8. TestMatrix（测试与回归设计）

| 发现 | 旧代码失败用例（设计） | 断言点 | 类型 |
| --- | --- | --- | --- |
| F-01 | `GXDubboFilterChainSpringBootTest#businessExceptionLosesCodeWhenBuiltInFilterActive` | 不排除内置时，consumer 收到 `RuntimeException` 且 `code` 不可读 | 真实 Dubbo 链（injvm/direct） |
| F-01 | `GXDubboFilterChainSpringBootTest#businessExceptionPreservedWhenBuiltInFilterExcluded` | 排除后，`GXBusinessException` 的 `code/msg/data` 保真 | 真实 Dubbo 链 |
| F-02 | `GXDubboCallExceptionHandlerSpringBootTest#rpcExceptionUsesDedicatedHandlerUnderComponentScan` | 真实 `@SpringBootTest` + `@ComponentScan("cn.maple")` 下，`data.errorType` 存在（非"系统内部错误"） | Spring Boot 组件扫描 |
| F-03 | `GXDubboExceptionFilterTest#convertsDegradeAndAuthorityExceptions` | `DegradeException`/`AuthorityException` 类名 → `GXSentinelFlowException` | 单元 |
| F-03 | `GXDubboExceptionFilterTest#doesNotMisclassifyBusinessMessageContainingFlowException` | 普通异常消息含 "FlowException" 不被转换 | 单元 |
| F-04 | `GXDubboExceptionFilterTest#wrapsUndeclaredRuntimeWhenMethodSignatureUnresolvable` | 参数类型不匹配时仍包装为 `GXBusinessException` | 单元 |
| F-05 | `GXDubboExceptionFilterTest#recordsFilterInternalFailure` | filter 自身抛异常时计数递增、原始异常语义不变 | 单元 |
| F-06 | `GXDubboCallExceptionHandlerTest#rpcExceptionSubtypesKeepStableCode` | 各子类 code=500 + `errorType` 正确 | 单元 |
| F-07 | `GXDubboExceptionFilterTest#businessExceptionSubclassDowngradedWhenDataNull` | 记录既有语义（文档化） | 单元 |
| F-08 | `GXPenetrateAttachmentSelectorTest#appNameCacheIsolatesAcrossReset` | 并发读一致；`resetAppNameCache()` 后重新解析 | 并发 + 单元 |
| F-09 | `GXDubboTraceIdFilterTest#filterReusesSharedAppNameCache` | 第二次调用不再读 Environment | 单元（mock 计数） |
| F-10 | `GXDubboTraceIdFilterTest#traceIdLoggingIsDebugLevel` | INFO 无输出、DEBUG 有输出 | 单元（ListAppender） |
| F-11 | `GXDubboTraceIdFilterTest#resolveTraceIdKeepsPriorityChain` | 四来源优先级与既有 5 条断言一致 | 单元（P-OK-01 扩展） |
| F-12 | `docs` 审查（无测试） | 手册登记 allowlist 来源与影响 | 文档 |
| F-13 | 文档审查 | 手册存在、AGENTS.md 与 31 条用例一致 | 文档 |
| F-14 | `AsciiSourceGuardTest` + 代码审查 | 无 `@Log4j2` | 静态 |
| F-15 | 编译（NullAway） | 可空返回标注 `@Nullable`，构建通过 | 构建 |
| F-16 | 见 F-01/F-02 的真实链与真实上下文用例 | 同上 | 集成 |

**测试矩阵最低门槛**（FR-020/FR-021）：每个修复项至少 1 条"旧代码失败"用例；涉及并发/静态缓存补并发用例；涉及 Web 语义补 1 个真实 `@SpringBootTest`；涉及 Dubbo Filter 装配补至少 1 条真实 Dubbo 链用例；既有 31 条断言全部保留，用例数只增不减。

---

## 9. Compatibility / Defaults（兼容与默认值）

| 项 | 当前默认/行为 | 修复后 | 兼容策略 |
| --- | --- | --- | --- |
| `dubbo.provider.filter` | 未配置（内置 `exception` + 自定义均激活） | 文档要求 `dubboExceptionFilter,-exception`；校验器 ERROR 告警 | 存量部署不改也能启动（只告警）；不新增 `check-strict` 键（YAGNI） |
| `GXDubboCallExceptionHandler` 顺序 | 无 `@Order` | `@Order(HIGHEST_PRECEDENCE)` | 仅改变 `RpcException` 命中方法，属缺陷修复 |
| TraceId Filter 日志级别 | INFO | DEBUG | 需要时开 DEBUG；不改变透传行为 |
| Sentinel 转换范围 | Flow/Block + 消息 | 全部 `slots.block.*Exception` | 行为扩展；误判减少 |
| `GXDubboRpcApiBeanPostProcessor` 日志门面 | Log4j2 | SLF4J | 输出目标不变 |
| 静态 `appName` | Selector 私有静态 | 共享 + 可重置 | 生产单上下文无感 |
| `GXExceptionHandler` / `GXBusinessException` / SPI 扩展名 / `serialize.allowlist` | — | **不变** | 稳定契约 |
