# Feature Specification: leaf-base-dubbo-nacos 正确性、线程安全与性能审查

**Feature Branch**: `014-dubbo-nacos-module-audit`（本次未通过 hook 创建 git 分支；`before_specify` 无 hook 注册）

**Created**: 2026-10-25

**Status**: Draft

**Input**: User description: "审查 @e:\project\java\maple-leaf-framework\leaf-base-dubbo-nacos 模块的正确性、线程安全性、性能。你在修改完代码之后，一定做功能完备性的测试。"

## 背景与审查结论摘要

本规格把"`leaf-base-dubbo-nacos` 现有实现是否正确、是否线程安全、是否存在性能结构问题"转化为可验收的交付要求，并把"改动后必须做完备测试"固化为硬门槛。审查对象与证据来源：

1. `leaf-base-dubbo-nacos` 全部 **10 个生产源码文件**（6 个功能类 + 4 个 `package-info`）+ **3 个运行时资源**（`META-INF/dubbo/org.apache.dubbo.rpc.Filter`、`META-INF/dubbo/org.apache.dubbo.rpc.PenetrateAttachmentSelector`、`security/serialize.allowlist`）与 **9 个既有测试类（实测 31 条用例）**，逐文件覆盖，无跳过；
2. 契约来源：`leaf-base-dubbo-nacos/AGENTS.md`、`.specify/memory/constitution.md`（重点为 II 稳定契约、III 统一响应、V 空值安全与资源释放、VI 测试门槛、VII 命名与包结构、附加约束 §8/§9）；
3. 上游语义核对：直接读取本仓 `leaf-base-framework` 的 `GXTraceIdContextUtils`（基于 MDC，`getTraceId` 对 null 返回空串、`getNullableTraceId` 返回 null、`putTraceId(blank)` 等价 `removeTraceId`）、`GXCommonUtils`（`getEnvironmentValue` 每次走 `GXSpringContextUtils.getEnvironment()`、`getGenericClassType` 基于 Hutool `ClassUtil.getTypeArgument`、`reflectCallObjectMethod` 对失败静默返回 null）、`GXSpringContextUtils`（`getApplicationContext` 读静态单例）、`GXExceptionHandler`（`@RestControllerAdvice` 且第 226 行含兜底 `@ExceptionHandler(Exception.class)`）、`GXSentinelFlowException`（`code/msg/data` 三字段 + `getCode()/getMsg()/getData()`）逐条比对，不凭类名或经验推断；
4. 依赖基线：`leaf-base-dubbo-nacos` 依赖 `leaf-base-framework` + `leaf-base-nacos`（compile）+ `dubbo-spring-boot-starter` 3.3.6 + `dubbo-nacos-spring-boot-starter` 3.3.6 + `fastjson` + `javassist`（编译期） + `spring-boot-starter-test`（test）；模块以 `@Activate` + `META-INF/dubbo/*` SPI 声明装配，无 `spring.factories`；
5. 既有测试实测基线：`mvn -o -pl leaf-base-dubbo-nacos test` → **Tests run: 31, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS**（`AsciiSourceGuardTest` 1、`GXDubboExceptionFilterTest` 11、`GXDubboTraceIdFilterTest` 5、`GXDubboCallExceptionHandlerTest` 2、`GXDubboCallExceptionHandlerSpringBootTest` 2、`GXDubboRpcApiBeanPostProcessorTest` 4、`GXDubboRpcApiBeanPostProcessorSpringBootTest` 1、`GXPenetrateAttachmentSelectorTest` 5）；
6. 装配事实核对：`GXDubboExceptionFilter` 继承 Dubbo 内置 `ExceptionFilter` 并以独立 SPI 扩展名 `dubboExceptionFilter` 注册，二者均为 `@Activate(group = CommonConstants.PROVIDER)` 默认激活——这是本模块最高风险的结构性问题来源（详见 C-02）。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 逻辑正确性 | TraceId 透传/恢复、异常分类主链路自洽，31 条用例全绿；但 **`GXDubboExceptionFilter` 与内置 `ExceptionFilter` 双重激活可能把 `GXBusinessException` 二次包装丢业务码**、**Sentinel 识别只覆盖 Flow/Block 两类且消息兜底过宽**、**Web 层 Dubbo 异常 handler 与框架兜底 advice 双重注册可能被遮蔽**、**反射解析方法签名失败时 fail-open** | 存在高风险缺陷 |
| 线程安全 | 无致命数据竞争：MDC 上下文 `finally` 恢复 + 异步 `whenCompleteWithContext` 二次捕获正确；风险集中在**静态 `volatile appName` 跨上下文缓存**、**每请求读 Spring 上下文单例的口径不一致** | 需补约束 |
| 性能 | 每请求固定付出：1 次 `getEnvironmentValue`（含 `getApplicationContext` 链）+ 多条 INFO 日志 + 多路 attachment 双读；TraceId Filter 不缓存 appName 而 Selector 缓存，口径不一 | 结构性开销，非阻断 |
| 契约（安全/文档） | **缺 `docs/leaf-base-dubbo-nacos开发手册.md`**、**`AGENTS.md` 声称"无 src/test"与实际漂移**、**`@Log4j2` 违反 SLF4J 门面约定**、**空值契约零 `@Nullable`** | 需收敛 |

关键证据（均可按位置复核）：

- **C-01**：`GXDubboCallExceptionHandler`（第 14–16 行）`@RestControllerAdvice` 并继承 `GXExceptionHandler`（`leaf-base-framework`，第 42 行同样 `@RestControllerAdvice`，第 226–231 行含兜底 `@ExceptionHandler(Exception.class)` → 返回"系统内部错误"）。两者均无 `@Order`；Spring MVC 按 advice 排序取"第一个能处理的 advice"，`RpcException`/`GXSentinelFlowException`（均为 `RuntimeException`）可能被基础 advice 的兜底 `Exception` handler 抢先，Dubbo 专用 handler 失效。现有 `GXDubboCallExceptionHandlerSpringBootTest` 用 `MockMvcBuilders.standaloneSetup(...).setControllerAdvice(new GXDubboCallExceptionHandler())`（第 25–27 行）**仅注册子类**，无法暴露"基础 advice 也注册"的真实装配。
- **C-02**：`GXDubboExceptionFilter`（第 28 行 `@Activate(group = CommonConstants.PROVIDER)`）继承 Dubbo 内置 `ExceptionFilter`（同样 `@Activate(group = PROVIDER)`）并以独立扩展名 `dubboExceptionFilter` 注册（`META-INF/dubbo/org.apache.dubbo.rpc.Filter` 第 1 行）；模块**无任何排除内置 `exception` filter 的配置**。内置 `ExceptionFilter.onResponse` 会把不在接口同包/JDK/Dubbo 内的未声明异常包装为普通 `RuntimeException`——`GXBusinessException`（`cn.maple.core.framework.exception` 与 provider 接口不同包）会被内置 filter 二次包装，**丢失 `code/msg/data`**，使本模块第 61–64 行的 `GXBusinessException` 透传逻辑形同虚设。属最高风险项；**已在 Phase 0 通过 Dubbo 3.3.6 字节码实测与配置核对确认**（`research.md` R-02/R-03），并发现唯一排除配置位于 `config/online-nacos-dubbo-demo.yml` 第 21 行 `dubbo.provider.filter: dubboExceptionFilter,-exception`，该要求未在模块文档登记。收敛方案见 `plan.md` D-01（启动期校验器 + 手册登记强制装配契约）。
- **C-03**：`GXDubboExceptionFilter.isSentinelFlowException`（第 105–119 行）仅精确匹配类名 `...flow.FlowException` / `...block.BlockException`，或消息含 `"FlowException"`。Sentinel 的 `DegradeException`、`AuthorityException`、`SystemBlockException`、`ParamFlowException` 等其他 `BlockException` 子类不匹配类名、且消息不含 "FlowException" → 不转换为 `GXSentinelFlowException`；反向地，任何业务异常消息含 "FlowException" 子串即被误判为限流。
- **C-04**：`GXDubboExceptionFilter.onResponse` 第 66–77 行 `invoker.getInterface().getMethod(...)` 在参数类型不完全匹配（泛型擦除、原始类型/包装类型、重载）时抛 `NoSuchMethodException`，`catch` 直接 `return` → 未声明运行时异常不被包装为 `GXBusinessException`，fail-open（且该 `return` 也跳过了后续日志）。
- **C-05**：`GXDubboExceptionFilter.onResponse` 第 97–101 行外层 `catch (Throwable)` 吞掉 filter 自身逻辑的一切异常（仅 WARN），过滤逻辑自身的 NPE/反射失败被静默掩盖。
- **C-06**：`GXDubboCallExceptionHandler.handleRpcException`（第 18–21 行）对 timeout/forbidden/serialization/network 等所有 `RpcException` 子类统一返回 code=500，仅 `data.errorType` 区分（第 38–67 行）；超时、限流等语义未映射到更精确的 HTTP 状态。
- **C-07**：`GXDubboExceptionFilter.attachTraceId(GXBusinessException)`（第 142–154 行）在 `data == null` 时 `new GXBusinessException(exception.getMsg(), exception.getCode(), data, exception)`，把原异常具体子类（如 `GXConciseBusinessException` 及其字段/语义）降级为基类。
- **T-01**：`GXPenetrateAttachmentSelector` 第 26 行 `private static volatile String appName` + 第 118–128 行惰性初始化：跨 Spring 上下文（测试、多租户）缓存 `spring.application.name` 到静态字段；读改写非原子（两个线程重复计算，良性但无同步语义），且静态缓存在多上下文下串用。
- **T-02**：`GXDubboClientTraceIdFilter` 第 31 行、`GXDubboServerTraceIdFilter` 第 31 行**每请求**调用 `GXCommonUtils.getEnvironmentValue("spring.application.name", ...)`（内部走 `GXSpringContextUtils.getEnvironment()`→`getApplicationContext()` 读静态单例），而 Selector 缓存 appName、Filter 不缓存——口径不一致。
- **P-01**：`GXDubboClientTraceIdFilter` 第 32 行、`GXDubboServerTraceIdFilter` 第 32 行每请求 `log.info(...)`；虽为参数化日志，但 `getEnvironmentValue` 在日志级别关闭时仍每请求执行。
- **P-02**：`resolveTraceId`（Client 第 50–68 行、Server 第 50–70 行）每请求 `getObjectAttachment` + `getAttachment` 双读 + ThreadLocal 读 + 多次空白判断，无解析结果缓存。
- **S-01**：`security/serialize.allowlist` 仅含 `com.google.common.collect.HashBasedTable`，无来源注释说明为何需要 Guava 集合进白名单；白名单过窄/过宽的风险未评估、未登记。
- **S-02**：`docs/` 下**无** `leaf-base-dubbo-nacos开发手册.md`（宪法 §I 交付集要求）；`AGENTS.md` 第 5 节声称"当前模块下没有现成的 src/test 测试目录"，与当前 9 个测试类、31 条用例的实际状态漂移。
- **S-03**：`GXDubboRpcApiBeanPostProcessor` 第 20 行 `@Log4j2`（Log4j2 API），违反宪法 VII"新增类型用 `@Slf4j`（SLF4J 门面）"；与同模块 `GXDubboExceptionFilter`（`LoggerFactory`）、`GXPenetrateAttachmentSelector`（`LoggerFactory`）、`GXDubboCallExceptionHandler`（`@Slf4j`）不一致。
- **S-04**：空值契约零标注：4 个 `package-info` 全 `@NullUnmarked`，模块**零 `@Nullable`**；实际可空返回（`getAttachment`/`getInvocationAttachment` 返回 null、`GXCommonUtils.getEnvironmentValue` 返回 null、`GXCommonUtils.getGenericClassType` 返回 null、`GXSpringContextUtils.getApplicationContext` 返回 null）均未标注，与 NullAway 强制检查口径未对齐。

已确认为**正确实现**（不得在修复中被破坏）：

- **P-OK-01**：两个 TraceId Filter 的 MDC 上下文恢复链正确：`originalTraceId = getNullableTraceId()` → `finally` 中 `restoreTraceId`；异步结果走 `whenCompleteWithContext` + `attachTraceIdWithContext` 二次捕获/恢复，MDC 不泄漏（`GXDubboTraceIdFilterTest` 5 条用例锁定）。
- **P-OK-02**：`GXDubboExceptionFilter.buildErrorData`（第 121–133 行）仅含 `argumentCount`/`parameterTypes`，**不泄露参数值**（用例 `convertsSentinelFlowExceptionByMessageWithoutLeakingArguments` 第 48 行 `assertFalse(...containsKey("arguments"))` 锁定）。
- **P-OK-03**：`GXDubboExceptionFilter` 对 `GXBusinessException` 透传（含 traceId 增强）、`GenericService` 跳过、checked 异常/声明异常/JDK/jakarta/`RpcException`/MyBatis 异常分别透传的边界已用 11 条用例锁定。
- **P-OK-04**：`GXPenetrateAttachmentSelector` 只透传 TraceId（返回 1 项）、优先级（invocation → client/server → ThreadLocal → generate）、null 安全（null invocation/attachment）已用 5 条用例锁定。
- **P-OK-05**：`GXDubboRpcApiBeanPostProcessor` 的 ref 空、泛型解析失败、目标 bean 缺失、多候选、成功绑定分支已用 4（单元）+ 1（Spring Boot 启动）条用例锁定；`postProcessAfterInitialization` 返回原 bean。
- **P-OK-06**：既有测试 31 条全绿（实测基线如上），其中 11 条覆盖异常分类边界、5 条覆盖 TraceId 优先级与恢复、5 条覆盖 attachment 选择、5 条覆盖 BeanPostProcessor、2+2 条覆盖 Web 异常 handler。

发现清单（分级）：

- **F-01（高）内置 `ExceptionFilter` 双重激活风险**：`GXBusinessException` 可能被内置 filter 二次包装丢 code/msg/data（C-02）。
- **F-02（高）Web 层 Dubbo 异常 handler 可能被基础兜底 advice 遮蔽**：`RpcException`/`GXSentinelFlowException` 落入"系统内部错误"（C-01）。
- **F-03（中）Sentinel 识别覆盖不全且消息兜底过宽**：Degrade/Authority/System/ParamFlow 不转换；含 "FlowException" 的业务异常误判（C-03）。
- **F-04（中）反射解析方法签名失败时 fail-open**：未声明运行时异常不被包装（C-04）。
- **F-05（中）异常 filter 自身逻辑异常被静默吞掉**（C-05）。
- **F-06（低）RpcException 语义未映射到精确 HTTP 状态**（C-06）。
- **F-07（低）`GXBusinessException` 子类在 data 为空时被降级为基类**（C-07）。
- **F-08（中）静态 `appName` 跨上下文缓存 + 惰性初始化非原子**（T-01）。
- **F-09（中）每请求读 Spring 上下文单例、appName 缓存口径不一**（T-02）。
- **F-10（中）每请求 INFO 日志 + 无条件环境查找**（P-01）。
- **F-11（低）TraceId 解析无缓存、多路双读**（P-02）。
- **F-12（中）`serialize.allowlist` 无来源与影响评估**（S-01）。
- **F-13（低）缺开发手册、AGENTS.md 与实现漂移**（S-02）。
- **F-14（低）`@Log4j2` 违反 SLF4J 门面约定**（S-03）。
- **F-15（低）空值契约零标注**（S-04）。
- **F-16（低）测试命名误导**：`GXDubboCallExceptionHandlerSpringBootTest` 实际用 `standaloneSetup` 而非 `@SpringBootTest`，无法验证真实 advice 注册与排序（C-01 因此不会被发现）。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 审查结论可复核 (Priority: P1)

框架维护者要判断"能否把该模块直接用于生产环境做 Dubbo + Nacos RPC 的 TraceId 透传与异常收敛"，需要按维度分级、可逐条跳到代码位置的结论，并能用模块自带测试与最小复现用例验证。

**Why this priority**: 审查价值取决于结论可信度；F-01、F-02 任一条成立都意味着线上 RPC 业务异常码可能丢失或被兜底吞掉。

**Independent Test**: 随机抽取一条结论，按报告给出的文件与行区间定位，用报告给出的命令或用例复现同一现象。

**Acceptance Scenarios**:

1. **Given** 结论"内置 `ExceptionFilter` 双重激活"，**When** 在真实 Dubbo provider 链中抛出一个 `GXBusinessException` 并观察最终响应异常类型，**Then** 能观察到其被二次包装为普通 `RuntimeException`（或确认已通过排除内置 filter 收敛），并能在报告中指出决策点与验证命令。
2. **Given** 结论"Web 层 Dubbo 异常 handler 可能被遮蔽"，**When** 在真实 `@SpringBootTest` + 组件扫描（含基础 `GXExceptionHandler`）下抛 `RpcException`，**Then** 报告能给出"返回 `code=500` 与 `msg=系统内部错误`"或"返回 Dubbo 专用 handler 结构"的实测判定。
3. **Given** 结论"Sentinel 识别覆盖不全"，**When** 构造一个 `DegradeException`（类名不含 Flow），**Then** 报告指出其**不会**被转换为 `GXSentinelFlowException`。

---

### User Story 2 - 异常分类与透传边界可判定 (Priority: P1)

接入方需要明确：provider 侧哪些异常透传、哪些包装、哪些触发日志；`GXBusinessException` 的业务码能否无损到达 consumer。

**Why this priority**: 异常分类错误会直接放大为"业务错误码丢失""敏感堆栈泄露"或"错误码被二次包装"。

**Independent Test**: 对 `GXBusinessException`、声明异常、checked 异常、`RpcException`、JDK/jakarta 异常、MyBatis 异常、未声明运行时异常、Sentinel 各阻断类型各构造一次，记录最终响应异常类型与业务字段。

**Acceptance Scenarios**:

1. **Given** 一个 `GXBusinessException`，**When** 经过 provider 异常 filter，**Then** 其 `code/msg/data` 无损透传（而不是被包装为普通 `RuntimeException`）。
2. **Given** 一个 `DegradeException`/`AuthorityException`，**When** 经过 provider 异常 filter，**Then** 其被转换为 `GXSentinelFlowException`（或明确记录为不支持范围）。
3. **Given** 一个未声明运行时异常，**When** 方法签名无法被反射解析，**Then** 仍被包装为 `GXBusinessException`（fail-closed），而非静默透传。

---

### User Story 3 - TraceId 透传与上下文恢复可验证 (Priority: P1)

维护者需要知道：consumer/provider 两端 TraceId 优先级、异步响应、嵌套调用、异常抛出的上下文恢复是否安全。

**Why this priority**: TraceId 是排障与链路追踪的基础；上下文泄漏会导致日志串扰。

**Independent Test**: 覆盖 TraceId 各来源优先级、进入前已有/为空、异步完成、嵌套调用、异常抛出时的恢复。

**Acceptance Scenarios**:

1. **Given** invocation/client attachment/server attachment/ThreadLocal 同时存在不同 TraceId，**When** 解析，**Then** 按既定优先级取一个，且 consumer 与 provider 优先级差异与文档一致。
2. **Given** 异步响应完成回调，**When** 在回调线程上写 attachment，**Then** 回调结束后原线程 TraceId 恢复原值。
3. **Given** invoker 抛异常，**When** filter 的 `finally` 执行，**Then** MDC 恢复到进入前状态（null → 清除）。

---

### User Story 4 - 共享状态与并发安全可判定 (Priority: P2)

维护者需要知道：静态 `appName`、Spring 上下文单例读取、测试夹具这些共享状态在并发与多应用上下文下是否安全。

**Why this priority**: 并发问题单线程用例不暴露，线上表现为"日志 appName 错乱""多上下文静态串用"。

**Independent Test**: 并发读取静态 appName、多上下文加载、并行运行测试。

**Acceptance Scenarios**:

1. **Given** 两个线程同时首次触发 `getAppName()`，**When** 都完成，**Then** 返回一致且无脏值（或明确记录"重复计算为良性"契约）。
2. **Given** 同一 JVM 依次启动两个不同 `spring.application.name` 的上下文，**When** 第二个上下文读 appName，**Then** 返回第二个名字（不被静态缓存串用）——或明确声明"静态缓存为一次性"。

---

### User Story 5 - 性能开销定量化 (Priority: P2)

维护者需要把"每请求 TraceId 透传与异常转换"折算成可复算口径，判断其能否承受高 QPS。

**Why this priority**: 该模块位于每个 RPC 请求关键路径，无口径的"有点慢"无法转化为改造计划。

**Independent Test**: 按报告口径统计每请求的环境查找次数、日志条数、attachment 读取次数、`getBean`/反射次数。

**Acceptance Scenarios**:

1. **Given** 一次带 TraceId 的 RPC 调用，**When** 统计开销，**Then** 报告给出"getEnvironmentValue 1 次 / INFO 日志 1 条 / attachment 双读 N 次 / ThreadLocal 读 N 次"的可复算清单。
2. **Given** 日志级别高于 INFO，**When** 统计环境查找，**Then** 报告指出环境查找仍每请求发生（不受日志级别影响）。

---

### User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

计划阶段需要把发现直接转成任务：每条发现具备编号、级别、维度、位置、触发条件、影响、证据、回归设计。

**Why this priority**: 审查不是终点；不能落到可执行任务的结论等于没有结论。

**Independent Test**: 抽取任一发现，检查八项要素是否齐备；抽取任一"既定契约"检查是否被明确标注，避免被误改。

**Acceptance Scenarios**:

1. **Given** F-01，**When** 交由计划阶段，**Then** 无需补充上下文即可拆出"先写暴露双包装的用例、再收敛为取代/排除内置 filter"的任务。
2. **Given** P-OK-01/P-OK-02/P-OK-03 为既有断言，**When** 任何修复改动 TraceId 恢复、异常分类或参数泄露防护逻辑，**Then** 这些断言必须保持通过。

---

### Edge Cases

- TraceId：`null` / `""` / `" "` / 超长；invocation/client/server/ThreadLocal 四路来源的组合与冲突。
- `Result` 为 `null` / 非 `AsyncRpcResult` / `AsyncRpcResult` 但 future 未完成即抛出；`invoker.invoke` 抛 `RpcException` / `RuntimeException` / `Error`。
- 异常：`appResponse.hasException()` 为 true 但 `getException()` 返回 null；异常 `getMessage()` 为 null（Sentinel 消息兜底判断）。
- 方法签名反射：重载方法、原始类型 vs 包装类型、泛型擦除、`invocation.getParameterTypes()` 返回 null。
- `GXBusinessException` 的 `data` 为 null / 为空 `Dict` / 已含 TraceId。
- Sentinel：`FlowException` / `BlockException` / `DegradeException` / `AuthorityException` / `SystemBlockException` / `ParamFlowException` / 消息含 "FlowException" 的普通业务异常。
- `GXPenetrateAttachmentSelector`：`invocation` 为 null、`clientAttachment`/`serverAttachment` 为 null、`getObjectAttachment` 返回 null 但 `getAttachment` 非 null。
- `GXDubboRpcApiBeanPostProcessor`：`ref` 为 null、`getGenericClassType` 抛 `IllegalArgumentException`、`targetService` 为 null、`getBeanNamesForType` 返回空/多候选/抛 `BeansException`、`getApplicationContext()` 返回 null、`staticBindServeServiceClass` 方法不存在/抛异常。
- Web 层：`RpcException` 各子类（timeout/network/serialization/forbidden/authorization/limitExceed/noInvoker/validation/biz）、`GXSentinelFlowException` 的 `data` 为 null。
- 多 Spring 上下文（测试/热部署）下静态 `appName` 串用。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖模块全部 10 个生产源码文件、3 个运行时资源与 9 个既有测试类；每个文件 MUST 有明确判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因），不得跳过。
- **FR-002**: 每条发现 MUST 标注维度（逻辑正确性 / 线程安全 / 性能 / 安全与契约）、严重级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件与影响面。
- **FR-003**: 每条发现 MUST 附带复现方式：既有用例、可新增的回归用例设计或明确构造步骤；仅凭阅读的判断 MUST 标注为"未验证假设"（C-02 的内置 filter 双重激活在计划阶段 MUST 以真实 Dubbo 链实测收敛）。
- **FR-004**: 审查 MUST 以**本仓上游 `leaf-base-framework` 源码**为准核对 `GXTraceIdContextUtils`（MDC 语义、`getTraceId` 对 null 返回空串）、`GXCommonUtils`（`getEnvironmentValue`/`getGenericClassType`/`reflectCallObjectMethod` 的失败语义）、`GXExceptionHandler`（兜底 `Exception` handler 与 `@RestControllerAdvice`）、`GXSentinelFlowException`（`code/msg/data`），不得仅凭类名或经验推断。
- **FR-005**: 异常分类 MUST 收敛为唯一边界并逐条定性：`GXBusinessException` 透传、声明异常透传、checked 异常透传、`RpcException`/JDK/jakarta/MyBatis 异常透传、Sentinel 各阻断类型转换、其余未声明运行时异常包装；`GXDubboExceptionFilter` 与内置 `ExceptionFilter` 的双重激活 MUST 被验证并收敛为"取代或排除内置"，MUST NOT 让 `GXBusinessException` 被二次包装丢 `code/msg/data`（对应 F-01）。
- **FR-006**: Web 层 Dubbo 异常处理 MUST 保证 `RpcException`/`GXSentinelFlowException` 的专用 handler 优先级高于基础 `GXExceptionHandler` 的兜底 `Exception` handler，且 MUST 用真实 `@SpringBootTest`（含组件扫描，而非 `standaloneSetup`）验证（对应 F-02、F-16）。
- **FR-007**: Sentinel 识别 MUST 覆盖官方 `BlockException` 主要子类（Flow/Degrade/Authority/System/ParamFlow），或显式登记不支持范围；MUST NOT 仅凭"消息含 FlowException"判定（对应 F-03）。
- **FR-008**: 方法签名反射解析失败时 MUST fail-closed：未声明运行时异常仍 MUST 被包装为 `GXBusinessException`，不得静默透传（对应 F-04）。
- **FR-009**: 异常 filter 自身逻辑的异常 MUST NOT 被静默吞掉：必须记录并保留可观测信号，且不改变已产生的业务异常语义（对应 F-05）。
- **FR-010**: 并发安全结论 MUST 覆盖静态 `appName` 惰性初始化、每请求 Spring 上下文单例读取、测试夹具三类共享状态，并给出各对象的现状保护手段与收敛路径（对应 F-08、F-09）。
- **FR-011**: 性能结论 MUST 给出可复算口径：每请求 `getEnvironmentValue` 次数、INFO 日志条数、attachment 双读次数、ThreadLocal 读次数、`getBean`/反射次数；MUST NOT 以绝对吞吐或时延数值作为通过门槛（对应 F-10、F-11）。
- **FR-012**: 既有测试 MUST 全部执行并记录结果（当前基线：31 执行 / 0 失败）；MUST NOT 通过删除或跳过用例来"通过"；覆盖缺口 MUST 单列为发现（对应 F-16）。
- **FR-013**: 对外契约（`AGENTS.md`、SPI 扩展名、配置键、`serialize.allowlist`、缺失的开发手册）MUST 与实现逐条对照，不一致处 MUST 单列为发现；审查阶段 MUST NOT 修改生产代码（对应 F-12、F-13、F-14）。
- **FR-014**: 审查报告 MUST 独立成文（`audit-report.md`），`spec.md` 只保留摘要与引用，两者在交付时 MUST 相互一致。
- **FR-015**: 每条发现 MUST 给出"能在旧代码上失败、在新代码上通过"的回归测试设计（输入、断言点、期望差异），且该测试 MUST 无需外部中间件即可运行。
- **FR-016**: 空值语义 MUST 按 JSpecify 收敛：可空返回（`getAttachment`/`getInvocationAttachment`/`getEnvironmentValue`/`getGenericClassType`/`getApplicationContext`）与可空入参 MUST 显式标注 `@Nullable` 或改为不允许为空，并 MUST 与 NullAway 构建检查一致（对应 F-15）。
- **FR-017**: 新增/修改日志 MUST 使用 SLF4J 门面（`@Slf4j` 或 `LoggerFactory`），MUST NOT 引入或保留 `@Log4j2` 直接依赖（对应 F-14）；全部日志文案 MUST 为 ASCII。
- **FR-018**: 任何"写 attachment / 写 MDC"的路径 MUST 保证设置与恢复成对出现；异步响应回调 MUST 二次捕获/恢复上下文，禁止跨线程上下文泄漏（对应 P-OK-01，作为不可破坏契约）。
- **FR-019**: `GXPenetrateAttachmentSelector` 的透传范围（当前仅 TraceId）MUST 保持既有契约并有用例锁定；若需扩展透传项，MUST 先评估跨服务数据泄露风险并显式登记（对应 P-OK-04）。
- **FR-020**: 任何生产代码改动 MUST 在同一变更中补齐测试：受影响路径的成功、失败、异常、空值、边界用例各至少 1 条；涉及并发/缓存/生命周期的改动 MUST 补并发用例；涉及 Web 层语义的改动 MUST 至少补 1 个真实 `@SpringBootTest`（含组件扫描）用例。
- **FR-021**: 每个修复项 MUST 附"先在旧代码上失败"的用例证据（用例名 + 失败断言），再给出实现改动；仅补实现不补用例的变更 MUST 视为未完成。
- **FR-022**: 交付前 MUST 提供 `mvn -pl leaf-base-dubbo-nacos test` 的全绿执行输出（含用例总数与耗时），并 MUST 给出"新增 / 修改 / 保留"用例清单，其中保留清单 MUST 包含 P-OK-01～P-OK-06 对应的既有断言。
- **FR-023**: 测试 MUST NOT 依赖反射访问私有字段/方法来构造"内置 filter 双重激活""Sentinel 异常"等现场；这些现场 MUST 通过可注入的扩展点或真实 Dubbo 链构造。
- **FR-024**: 模块交付集 MUST 补齐 `docs/leaf-base-dubbo-nacos开发手册.md`（模块定位、SPI 扩展名契约、TraceId 优先级表、异常分类矩阵、配置键、不支持范围），并 MUST 修正 `AGENTS.md` 与实现漂移的部分（对应 F-13）。
- **FR-025**: 测试默认 MUST NOT 依赖真实 Nacos 等外部中间件，须优先使用离线手段（Dubbo `injvm`/直连协议、`spring.cloud.nacos.config.import-check.enabled=false`、可注入的 `Invoker`/`Result` 替身）构造现场；确需真实 Nacos（如注册中心服务发现端到端）才能覆盖的验证，实施前 MUST 先向用户申请并获取真实连接信息，MUST NOT 臆造地址、账号或密钥，且这类用例 MUST 在缺少环境时可优雅跳过（对应离线测试门槛）。

### Key Entities *(include if data involved)*

- **审查发现（Finding）**：编号、级别、维度、代码位置、触发条件、影响面、证据、复现方式、回归测试设计、修复建议。
- **异常分类矩阵**：异常类型（`GXBusinessException` / 声明异常 / checked 异常 / `RpcException` / JDK / jakarta / MyBatis / Sentinel 各子类 / 未声明运行时异常）× 处置（透传 / 包装 / 转换）× 是否触发日志 × TraceId 是否增强。
- **TraceId 优先级链**：来源（invocation attachment / client attachment / server attachment / ThreadLocal / generate）× 方向（consumer 请求 / provider 请求 / 响应回传）× 恢复语义。
- **共享状态清单**：对象（静态 `appName` / Spring 上下文单例 / MDC）× 共享范围 × 现状保护手段 × 收敛路径。
- **性能口径**：每请求 `getEnvironmentValue` 次数、INFO 日志条数、attachment 双读次数、ThreadLocal 读次数、`getBean`/反射次数、测试墙钟耗时。
- **SPI 契约表**：扩展名（`dubboExceptionFilter` / `dubboClientTraceIdFilter` / `dubboServerTraceIdFilter` / `dubboPenetrateAttachmentSelector`）× 实现类 × 激活分组 × 与内置扩展的冲突关系。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查覆盖 100% 的 10 个生产源码文件、3 个运行时资源与 9 个既有测试类，且 100% 的发现记录在独立报告中；新维护者可在 15 分钟内复现任一结论。
- **SC-002**: 100% 的发现具备"级别 + 维度 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归设计"；0 条仅凭阅读的结论混入已确认项（C-02 已在 Phase 0 由字节码与配置核对确认机制，运行时对照用例在实现阶段补齐）。
- **SC-003**: 异常分类矩阵覆盖 ≥8 类异常 × 5 个观测点（最终异常类型 / 业务字段是否保留 / 日志 / TraceId / 客户端表现），判定率 100%，每条判定附上游源码或实测证据。
- **SC-004**: TraceId 优先级链覆盖 4 类来源 × 2 方向 × 3 种恢复场景（正常 / 异步 / 异常），覆盖率 100%，且明确标出各场景期望值与现状值。
- **SC-005**: 性能结论给出可复算口径，且"每请求 `getEnvironmentValue` 次数""每请求 INFO 日志条数""每请求 attachment 双读次数"三项可被独立复算（误差 0）；MUST NOT 以绝对吞吐或时延数值作为通过门槛。
- **SC-006**: 既有测试执行率 100%（基线 31 执行 / 0 失败），测试覆盖缺口 100% 被记录为发现。
- **SC-007**: 高危发现（F-01 / F-02）100% 具备可构造的故障现场与回归测试设计，计划阶段无需补充调研即可拆分为任务。
- **SC-008**: SPI 契约表覆盖 `META-INF/dubbo` 下 100% 的扩展名，且与内置 Dubbo 扩展的冲突关系 100% 显式标注。
- **SC-009**: 修复阶段每个改动项的"旧代码失败用例"证据覆盖率 100%（FR-021），且 `mvn -pl leaf-base-dubbo-nacos test` 全绿、用例数从 31 单调增长（不减少）。

## Assumptions

- 目标运行形态为 JDK 21+ / Spring Boot 4.1.x / Dubbo 3.3.6 / Nacos，下游通过 Dubbo `@Activate` + `META-INF/dubbo/*` SPI 装配 Filter/Selector，通过 `@ComponentScan("cn.maple")` 装配 `GXDubboRpcApiBeanPostProcessor` 与 `GXDubboCallExceptionHandler`（Windows 本机实测 JDK 25 + Maven 离线仓库可运行模块测试）。
- 本次为审查交付，不修改生产代码；修复在 `/speckit.plan` / `/speckit.implement` 阶段执行，并受 FR-020～FR-023 的测试门槛约束。用户"改完代码必须做完备测试"的要求固化为 FR-020～FR-023 的硬门槛，而非在本阶段改代码。
- 模块安全边界依赖上游 `leaf-base-framework`（MDC 工具、异常基类、Spring 上下文单例）；本规格只要求"不因本模块默认行为放大上游弱点"，不要求重写上游工具。
- C-02（内置 `ExceptionFilter` 双重激活）按"需在计划阶段实测收敛"处理：默认收敛方向为"以自定义 filter 取代内置 `exception` filter（提供排除配置）"，具体取代或排除方案由 `/speckit.plan` 决定，但 MUST 保证 `GXBusinessException` 业务字段无损。
- 既有用例中被锁定的行为（P-OK-01～P-OK-06）按"既定契约"处理，修复只能在不破坏这些断言的前提下进行。
- 性能数据以单机、单 JVM、本地回环网络为前提；结论以"可复算口径 + 同量级复现"为验收标准，不承诺生产容量。
- `AGENTS.md` 与（缺失的）开发手册视为**声明**而非规范：与实现冲突时以"实现 + 宪法"为准，冲突本身单列为发现，不在本阶段直接改文档。
- 测试环境默认离线：Dubbo 相关验证优先用 `injvm`/直连协议与可注入替身完成，现有 31 条用例均可在无 Nacos 环境下运行。若某项验证（如注册中心服务发现下的 Filter 实际激活顺序）确需真实 Nacos 才能闭环，实施前 MUST 先向用户申请，由用户提供真实 Nacos 服务信息后再执行；不得臆造连接参数。
