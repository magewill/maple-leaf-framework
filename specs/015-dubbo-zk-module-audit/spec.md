# Feature Specification: leaf-base-dubbo-zk 正确性、线程安全与性能审查

**Feature Branch**: `015-dubbo-zk-module-audit`（本次未创建 git 分支；`.specify/extensions.yml` 未注册 `before_specify` hook）

**Created**: 2026-10-25

**Status**: Draft

**Input**: User description: "审查 @e:\project\java\maple-leaf-framework\leaf-base-dubbo-zk 模块的正确性、线程安全性、性能。你在修改完代码之后，一定做功能完备性的测试。"

## 背景与审查结论摘要

本规格把"`leaf-base-dubbo-zk` 现有实现是否正确、是否线程安全、是否存在性能结构问题"转化为可验收的交付要求，并把"改动后必须做完备测试"固化为硬门槛。审查对象与证据来源：

1. `leaf-base-dubbo-zk` 全部 **10 个生产源码文件**（6 个功能类 + 4 个 `package-info`）+ **3 个运行时资源**（`META-INF/dubbo/org.apache.dubbo.rpc.Filter`、`META-INF/dubbo/org.apache.dubbo.rpc.PenetrateAttachmentSelector`、`security/serialize.allowlist`），逐文件覆盖，无跳过；
2. **`src/test` 目录不存在**：本模块当前 **0 个测试类、0 条用例**（同仓 `leaf-base-dubbo-nacos` 已有 9 个测试类 / 31 条用例），这是本模块最大的交付风险（F-18）；
3. 契约来源：`leaf-base-dubbo-zk/AGENTS.md`、`.specify/memory/constitution.md`（重点为 II 稳定契约、III 统一响应、V 空值安全与资源释放、VI 测试门槛、VII 命名与包结构、附加约束 §8/§9）；
4. 上游语义核对（读本仓源码，不凭经验推断）：`GXTraceIdContextUtils`（基于 MDC，`getTraceId()` 对 null 返回空串、`getNullableTraceId()` 返回 null、`putTraceId(blank)` 等价 `removeTraceId`、`generateTraceId()` 内部会再查一次 `spring.application.name`）、`GXCommonUtils`（`getEnvironmentValue` 每次走 `GXSpringContextUtils.getEnvironment()`、`getGenericClassType` 基于 Hutool `ClassUtil.getTypeArgument`、`reflectCallObjectMethod` 失败静默返回 null）、`GXExceptionHandler`（`@RestControllerAdvice`，第 226–231 行含兜底 `@ExceptionHandler(Exception.class)` → 返回"系统内部错误"）、`GXSentinelFlowException`（继承 `RuntimeException` **而非** `GXBusinessException`，字段 `msg/code/data`）；
5. Dubbo 3.3.6 语义核对（本地仓库源码 `dubbo-3.3.6-sources.jar` 实测）：内置 `ExceptionFilter` 第 49 行为 `@Activate(group = CommonConstants.PROVIDER)`；`ContextFilter` 为 `@Activate(group = PROVIDER, order = Integer.MIN_VALUE)` 且通过 `getSupportedExtensionInstances()` 加载 `PenetrateAttachmentSelector` 并调用 `selectReverse`；`ConsumerContextFilter` 为 `@Activate(group = CONSUMER, order = Integer.MIN_VALUE)` 并调用 `select`；
6. 装配事实核对：`config/application-dubbo-zk.yml.conf` 第 26 行 `filter: dubboClientTraceIdFilter,dubboExceptionFilter,-exception`（consumer）、第 31 行 `filter: dubboServerTraceIdFilter,dubboExceptionFilter,-exception`（provider）——即**内置 `exception` filter 必须被排除**、且 **provider 专用的 `dubboExceptionFilter` 也被挂到了 consumer 链上**；该装配契约未在模块 `AGENTS.md` 登记。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 逻辑正确性 | MDC 透传与恢复主链路自洽；但 **`GXSentinelFlowException` 会在 provider 侧被再次包装成 `GXBusinessException(500)` 丢 406/code/data**、**Sentinel 识别通过"消息含 FlowException"判定且沿 cause 链遍历，存在误判与二次转换**、**内置 `ExceptionFilter` 双重激活（需按样本配置排除 `-exception`，但未登记为强制契约）**、**Web 层 advice 双注册且无 `@Order`，专用 handler 可能被基础兜底遮蔽** | 存在高风险缺陷 |
| 线程安全 | 无致命数据竞争：MDC 在 `finally` 中成对恢复、异步结果走 `whenCompleteWithContext` 二次捕获；风险集中在**静态 `volatile appName` 跨上下文缓存且读改写非原子**、**异常对象内 `Dict` 被原地修改**、**consumer 侧写入 `RpcContext.getServerAttachment()`（响应上下文）**、**selector 与两个 filter 三处各自可生成 traceId，同一调用可能产出不一致链路标识** | 需补约束 |
| 性能 | 每请求固定付出：**最多 3 次 Spring `Environment` 查询**（client filter 的 appName + server filter 的 appName + `generateTraceId` 内部一次）+ **consumer/provider 各 1 条 INFO 日志** + 多路 attachment 双读；异常路径另有 `getCanonicalName()` 字符串比较与 `resolveRpcErrorType` 重复计算 | 结构性开销，非阻断 |
| 契约（测试/文档/安全） | **`src/test` 完全缺失**、**`AGENTS.md` 与实现大范围漂移**（声称不承载 TraceId/Selector/BeanPostProcessor）、**缺 `docs/leaf-base-dubbo-zk开发手册.md`**、**`@Log4j2` 违反 SLF4J 门面**、**空值契约零 `@Nullable`**、**样本配置 `serialize-check-status: WRAN` 拼写非法** | 需收敛 |

关键证据（均可按位置复核）：

- **C-01**：`GXDubboExceptionFilter`（第 24 行 `@Activate(group = CommonConstants.PROVIDER)`）继承 Dubbo 内置 `ExceptionFilter`（`dubbo-3.3.6-sources.jar` 第 49 行同样 `@Activate(group = PROVIDER)`）并以独立扩展名 `dubboExceptionFilter` 注册（`META-INF/dubbo/org.apache.dubbo.rpc.Filter` 第 1 行）。不配置 `dubbo.provider.filter=...-exception` 时两者同时生效；内置 `ExceptionFilter` 会把"未声明、且异常类与接口不在同一代码来源"的 `RuntimeException` 包装为普通 `RuntimeException` → `GXBusinessException`（位于 `leaf-base-framework` jar，与业务接口不同来源）会被二次包装，**`code/msg/data` 丢失**，使第 57–60 行透传逻辑形同虚设。目前只有样例配置做了排除，模块 `AGENTS.md`/手册均未登记。
- **C-02**：`GXDubboCallExceptionHandler`（第 14–16 行）`@RestControllerAdvice` 并继承 `GXExceptionHandler`（`leaf-base-framework`，第 42 行同样 `@RestControllerAdvice`，第 226–231 行兜底 `Exception` handler）。两者均无 `@Order`；当基础 advice 也作为 Bean 注册时，`RpcException`/`GXSentinelFlowException` 可能先被基础 advice 的兜底 `Exception` handler 匹配，返回"系统内部错误"，专用 handler 失效。本模块无任何集成测试可暴露该装配，属"不可依赖的巧合"。
- **C-03**：第 57–60 行只对 `GXBusinessException` 做透传；`GXSentinelFlowException`（`RuntimeException` 子类、非 `GXBusinessException`）不命中 `isSentinelFlowException`（类名不在白名单、消息 "Service busy, please retry later." 不含 "FlowException"），最终落到第 88–91 行被包装成 `GXBusinessException(500, "Provider error, please contact provider.")` → **406 与 data 丢失**。
- **C-04**：第 101–115 行 `isSentinelFlowException` 沿 `getCause()` 遍历并接受"消息包含 `FlowException`"作为判定。任何业务异常（含 `GXBusinessException`、含已包装的 `GXSentinelFlowException`）只要 cause 链上出现该字样即被判定为限流并被**重新构造**一个新的 `GXSentinelFlowException`，原 `code/msg/data` 丢弃；同时 `DegradeException`/`AuthorityException`/`SystemBlockException`/`ParamFlowException` 等其他 `BlockException` 子类不会命中（类名既不等于 `FlowException` 也不等于 `BlockException`，消息也不含 "FlowException"）。
- **C-05**：第 63–73 行 `invoker.getInterface().getMethod(...)` 在泛型擦除、原始类型/包装类型、重载、默认方法等场景下抛 `NoSuchMethodException`，`catch` 中直接 `return` → 未声明运行时异常不被包装（fail-open），且**跳过了第 75 行的错误日志**，线上无任何可观测信号。
- **C-06**：第 93–97 行外层 `catch (Throwable)` 吞掉 filter 自身逻辑的一切异常（仅 WARN），过滤逻辑自身的 NPE/反射失败被静默掩盖，无计数、无指标。
- **C-07**：第 138–150 行 `attachTraceId(GXBusinessException)`：`data` 非空时**原地** `data.set(...)` 修改异常内部 `Dict`；`data` 为空时 `new GXBusinessException(...)` 把具体子类（如 `GXConciseBusinessException`）降级为基类，子类语义与字段丢失。
- **C-08**：`config/application-dubbo-zk.yml.conf` 第 26 行把 provider 专用的 `dubboExceptionFilter` 放进 **consumer** filter 列表。`GXDubboExceptionFilter` 的 `@Activate` 只声明 PROVIDER，但显式 `filter:` 配置会把它加入 consumer 链，于是 consumer 侧也会对 provider 返回的异常再做一次分类/包装；该语义既未验证也未登记。
- **C-09**：`GXDubboServerTraceIdFilter` 在第 39–41 行 `finally` 中恢复 MDC，而 `GXDubboExceptionFilter.onResponse` 是 Dubbo `Filter.Listener` 回调（在 `invoke` 返回后执行）。因此异常场景下 `attachTraceId(...)`（第 131–150 行）读到的 traceId 可能已被清除 → 业务异常的 `data` 中 traceId 缺失。该时序需在真实 provider 链中实测确认（属"未验证假设"）。
- **C-10**：`GXDubboRpcApiBeanPostProcessor` 第 58 行 `GXCommonUtils.reflectCallObjectMethod(realRpcApiTarget, "staticBindServeServiceClass", targetService)`；上游实现对方法缺失/调用失败**静默返回 null**，本类未检查返回值、无失败日志分支 → 绑定失败不可观测。同时该能力在 `AGENTS.md` 中被明确声明为"本模块不承载"。
- **T-01**：`GXPenetrateAttachmentSelector` 第 26 行 `private static volatile String appName` + 第 118–128 行惰性初始化：读改写非原子（重复计算本身良性），但静态缓存跨 Spring 上下文存活，多应用上下文/热部署下会串用旧值。
- **T-02**：`GXDubboExceptionFilter.attachTraceId`（第 143–146 行）与 `GXDubboCallExceptionHandler.enrichTraceId`（第 84–88 行）原地修改异常携带的 `Dict`（Hutool `Dict` 基于 `HashMap`）。异常实例若被共享（缓存异常、静态异常、单例包装）则存在跨线程写竞争，且会污染调用方持有的同一对象。
- **T-03**：`GXDubboClientTraceIdFilter` 第 30–31 行在 **consumer** 侧同时写入 `RpcContext.getServerAttachment()`（provider 回传的响应上下文）与 `getClientAttachment()`；`GXDubboServerTraceIdFilter` 第 30–31 行在 **provider** 侧双向写入。`serverAttachment` 在 consumer 端语义上是"上行响应上下文"，写入可能污染同一线程后续调用的响应附件。
- **T-04**：`GXPenetrateAttachmentSelector.resolveTraceIdFallback`（第 74–85 行）、`GXDubboClientTraceIdFilter`（第 61 行）、`GXDubboServerTraceIdFilter`（第 61 行）三处各自可调用 `generateTraceId()`；`generateTraceId()` 每次生成新 UUID。若某一链路上 selector 与 filter 都未取到既有 traceId，则同一次调用可能产生两个不同的 traceId（selector 写入 invocation 附件的那一个与 filter 写入 MDC 的那一个），导致日志与链路附件不一致。
- **P-01**：`GXDubboClientTraceIdFilter` 第 25 行、`GXDubboServerTraceIdFilter` 第 25 行**每请求**调用 `GXCommonUtils.getEnvironmentValue("spring.application.name", ...)`（内部 `GXSpringContextUtils.getEnvironment()` → 静态单例）；`GXTraceIdContextUtils.generateTraceId()` 内部再查一次同名属性 → 无入站 traceId 时每请求最多 **3 次** Spring `Environment` 访问。
- **P-02**：两个 filter 第 26 行每请求 `log.info(...)`；即使日志级别关闭，`getEnvironmentValue` 仍会执行（参数先求值）。
- **P-03**：`resolveTraceId`（client 第 44–62 行、server 第 44–64 行）每请求 `getObjectAttachment` + `getAttachment` 双读、多路来源依次判断，无短路缓存。
- **P-04**：`GXDubboCallExceptionHandler.buildRpcErrorData`（第 29 行）与 `resolveRpcMessage`（第 69 行）各自调用一次 `resolveRpcErrorType`（第 38–67 行），单次异常处理内重复执行 8 次 `isXxx()` 判定两遍。
- **S-01**：模块无 `src/test`，0 条用例；`AGENTS.md` 第 6 节仍以"如果缺少完整 `src/test` 覆盖"的假设语气描述，与"生产在用"的事实不相称。
- **S-02**：`AGENTS.md` 与实现漂移：第 1 节声称"不承载 Consumer 侧 TraceId 透传 / Provider 侧 TraceId 恢复 / PenetrateAttachmentSelector / `ServiceBean` 启动期绑定增强"，但代码中四项全部存在；第 4.2 节称 Sentinel 仅按 `"SentinelBlockException: FlowException"` 识别，实际还包含类名白名单与 cause 链遍历；第 4.6 节称 handler "只显式处理 `RpcException`"，实际还处理 `GXSentinelFlowException`；第 4.5 节称"根据服务接口与异常类是否位于同一代码来源决定是否继续透传"，实际本模块 filter **没有**代码来源比较（该逻辑在内置 filter 中）。
- **S-03**：`docs/` 下无 `leaf-base-dubbo-zk开发手册.md`（同族 `leaf-base-dubbo-nacos` 已有），违反宪法 §I 交付集要求。
- **S-04**：`security/serialize.allowlist` 仅一行 `com.google.common.collect.HashBasedTable`，无来源与影响说明；同类配置 `config/application-dubbo-zk.yml.conf` 第 13 行 `serialize-check-status: WRAN` 非 Dubbo `SerializeCheckStatus` 合法值（`STRICT`/`WARN`/`DISABLE`），属拼写错误。
- **S-05**：`GXDubboRpcApiBeanPostProcessor` 第 20 行 `@Log4j2`，违反宪法 VII"新增类型用 SLF4J 门面"，且与同模块其余类（`LoggerFactory`/`@Slf4j`）不一致。
- **S-06**：空值契约零标注：4 个 `package-info` 全为 `@NullUnmarked`，模块**零 `@Nullable`**；可空返回（`getAttachment`/`getInvocationAttachment` 返回 null、`GXCommonUtils.getEnvironmentValue` 无默认值时可返回 null、`GXCommonUtils.getGenericClassType` 可返回 null、`GXSpringContextUtils.getApplicationContext` 可返回 null）均未标注。
- **S-07**：`GXDubboRpcApiBeanPostProcessor` 仅以 `@Component` 暴露，模块无 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`；其生效完全依赖外部 `@ComponentScan("cn.maple")`，缺少启动期可见性与验证。

已确认为**正确实现**（不得在修复中被破坏）：

- **P-OK-01**：两个 TraceId Filter 的 MDC 上下文恢复链正确：`originalTraceId = getNullableTraceId()` → `finally` 中 `restoreTraceId`；异步结果走 `whenCompleteWithContext` + `attachTraceIdWithContext` 二次捕获/恢复，不跨线程泄漏。
- **P-OK-02**：`buildErrorData`（第 117–129 行）只上报 `argumentCount`/`parameterTypes`，**不记录参数值**，避免敏感数据随异常回传。
- **P-OK-03**：`GenericService` 跳过、checked 异常透传、声明异常透传、`java.`/`jakarta.` 前缀透传、`RpcException` 透传、MyBatis 异常透传的边界划分清晰，与内置 `ExceptionFilter` 的处理顺序一致（排除内置 filter 后语义可复现）。
- **P-OK-04**：`GXPenetrateAttachmentSelector` 只透传 `X-B3-TraceId` 一项，`invocation`/`attachment` 为 null 时安全；`@Activate(group = {PROVIDER, CONSUMER})` 与 Dubbo 3.3.6 `ContextFilter`/`ConsumerContextFilter` 的 `getSupportedExtensionInstances()` 装配方式匹配（已用源码核对），扩展名 `dubboPenetrateAttachmentSelector` 不影响激活。
- **P-OK-05**：`GXDubboExceptionFilter` 除 `static final Logger` 外无可变静态状态，可作为单例被并发调用；`GXDubboRpcApiBeanPostProcessor` 无实例字段。
- **P-OK-06**：`GXDubboCallExceptionHandler` 的 `RpcException` 错误分类（timeout/network/serialization/forbidden/authorization/limitExceed/noInvoker/validation/biz）与 ASCII 文案分层清晰，未回显堆栈或内部路径。

发现清单（分级）：

- **F-01（高）内置 `ExceptionFilter` 双重激活风险**：未排除 `-exception` 时 `GXBusinessException` 被二次包装丢 `code/msg/data`（C-01）。
- **F-02（高）Web 层专用 handler 可能被基础兜底 advice 遮蔽**：`RpcException`/`GXSentinelFlowException` 落入"系统内部错误"（C-02）。
- **F-03（高）`GXSentinelFlowException` 在 provider 侧被二次包装**：406 与 data 丢失（C-03）。
- **F-04（高）模块零测试**：`src/test` 缺失，任何修复都缺少"旧代码失败、新代码通过"的证据（S-01）。
- **F-05（中）Sentinel 识别过宽且覆盖不全**：消息含 `FlowException` + cause 链遍历 → 误判/二次转换；其他 `BlockException` 子类漏判（C-04）。
- **F-06（中）方法签名反射失败 fail-open**：未声明运行时异常不包装且不记录日志（C-05）。
- **F-07（中）filter 自身异常被静默吞掉**：仅 WARN，无可观测计数（C-06）。
- **F-08（中）consumer 链挂载 provider 专用 filter**：consumer 侧异常二次包装语义未定义（C-08）。
- **F-09（中）traceId 在 `onResponse` 阶段可能已不可见**：业务异常 `data` 缺 traceId（C-09，待实测）。
- **F-10（中）静态 `appName` 跨上下文缓存**：多上下文串用、读改写非原子（T-01）。
- **F-11（中）异常内 `Dict` 原地修改**：共享异常实例下的跨线程写竞争与对象污染（T-02）。
- **F-12（中）consumer 侧写 `serverAttachment`**：响应上下文污染风险（T-03）。
- **F-13（中）同一调用可能出现两个 traceId**：selector 与 filter 各自生成（T-04）。
- **F-14（中）每请求最多 3 次 Spring `Environment` 查询 + INFO 日志**：热路径开销（P-01、P-02）。
- **F-15（中）`AGENTS.md` 与实现大范围漂移**：四项能力"声称不承载但实际存在"（S-02）。
- **F-16（中）`GXDubboRpcApiBeanPostProcessor` 绑定失败不可观测且未登记**：`staticBindServeServiceClass` 静默失败（C-10、S-07）。
- **F-17（低）`GXBusinessException` 子类在 `data` 为空时被降级为基类**（C-07）。
- **F-18（低）traceId 解析多路双读无短路**（P-03）。
- **F-19（低）`resolveRpcErrorType` 单次处理重复执行两遍**（P-04）。
- **F-20（低）缺 `docs/leaf-base-dubbo-zk开发手册.md`**（S-03）。
- **F-21（低）`serialize.allowlist` 无来源说明；样本配置 `WRAN` 拼写非法**（S-04）。
- **F-22（低）`@Log4j2` 违反 SLF4J 门面约定**（S-05）。
- **F-23（低）空值契约零标注**（S-06）。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 异常契约无损且可判定 (Priority: P1)

框架维护者要确认：provider 抛出的 `GXBusinessException` / `GXSentinelFlowException` / 声明异常 / 未声明运行时异常，经 Dubbo 链与 Web 层后，业务 `code/msg/data` 与 HTTP 语义是否无损；consumer 端与 Web 端看到的是否一致。

**Why this priority**: F-01/F-02/F-03 任一条成立，线上业务错误码就会被替换为 500 或"系统内部错误"，属对外契约破坏。

**Independent Test**: 在真实 Dubbo provider 链（`injvm` 或直连）上分别抛出上述 7 类异常，记录最终响应异常类型、`code/msg/data`、是否记录日志、traceId 是否在 `data` 中；在真实 `@SpringBootTest`（含基础 advice）下用 MockMvc 请求触发 `RpcException`，断言响应结构与预期 handler 一致。

**Acceptance Scenarios**:

1. **Given** 一个 `GXBusinessException(业务码, 消息, data)`，**When** 经 provider 链（含内置 `ExceptionFilter` 的两种装配：排除与未排除），**Then** 排除内置 filter 时 `code/msg/data` 无损；未排除时报告能给出"被包装为 `RuntimeException`"的实测证据并触发装配校验告警。
2. **Given** 一个 `GXSentinelFlowException`（`code=406`）在 provider 业务中抛出，**When** 经本模块 filter，**Then** 保持 `GXSentinelFlowException` 类型与 `code/msg/data`（或明确登记的不支持范围），不得变成 `GXBusinessException(500)`。
3. **Given** 基础 `GXExceptionHandler` 与 `GXDubboCallExceptionHandler` 同时注册，**When** 请求触发 `RpcException`，**Then** 返回 Dubbo 专用结构（`data.errorType` 等）而非"系统内部错误"。

---

### User Story 2 - TraceId 全链路一致 (Priority: P1)

维护者需要知道：一次 RPC 调用（consumer → provider → 响应）从 selector 到两个 filter，是否始终是同一个 traceId；无入站 traceId 时生成的 id 是否唯一且被复用而非重复生成；异常响应的 `data` 是否携带 traceId。

**Why this priority**: traceId 是排障主键；同一调用出现两个 traceId 等于链路断裂（F-13），异常响应缺 traceId 等于无法定位（F-09）。

**Independent Test**: 构造 4 类来源（invocation 附件 / client 附件 / server 附件 / MDC）各自的组合，分别在 selector、client filter、server filter 上断言取到的值；无任何来源时断言三处复用同一 id。

**Acceptance Scenarios**:

1. **Given** 无任何既有 traceId，**When** 一次 consumer 调用完成，**Then** selector 写入 invocation 的 id、client filter 写入 MDC 的 id、以及 provider 侧最终 id 完全一致。
2. **Given** provider 业务抛异常，**When** 异常响应经异常 filter，**Then** `data` 中 traceId 与请求 traceId 一致（若因时序无法保证，必须显式登记为不支持并给出替代方案）。
3. **Given** 进入前 MDC 已有 traceId 且调用抛异常，**When** filter 退出，**Then** MDC 恢复为进入前的值（P-OK-01 不回退）。

---

### User Story 3 - 并发与共享状态安全 (Priority: P2)

维护者需要判断：静态 `appName` 缓存、异常对象内 `Dict`、`RpcContext` 双向写入在并发与多 Spring 上下文场景下是否安全。

**Why this priority**: 并发缺陷单线程测试暴露不了，线上表现为"日志 appName 错乱""异常数据被串改""响应附件污染"。

**Independent Test**: 两线程并发首次触发 `getAppName()`；同一异常实例被两线程同时 `attachTraceId`；同一线程连续两次 consumer 调用，检查第二次的 `serverAttachment` 是否被前一次写入污染；同 JVM 启动两个不同 `spring.application.name` 的上下文。

**Acceptance Scenarios**:

1. **Given** 两个线程同时首次调用 `getAppName()`，**When** 均完成，**Then** 返回值一致且无脏值（重复计算可接受，但必须有明确契约）。
2. **Given** 一个异常实例被两个线程同时处理，**When** 都执行 `attachTraceId`，**Then** 不产生 `ConcurrentModificationException`/数据错乱（或明确声明异常实例不得共享并补文档）。
3. **Given** consumer 连续两次调用，**When** 第二次解析 traceId，**Then** 不会读到第一次写入 `serverAttachment` 的残留值。

---

### User Story 4 - 热路径开销可复算 (Priority: P2)

维护者需要把"每请求 TraceId 透传 + 异常分类"折算成可复算口径，判断其能否承受高 QPS。

**Why this priority**: 该模块位于每个 RPC 请求的关键路径；无口径的"有点慢"无法转化为改造计划。

**Independent Test**: 按报告口径统计每请求的 Spring `Environment` 查询次数、INFO 日志条数、attachment 读取次数、`Dict` 分配次数、`generateTraceId` 调用次数。

**Acceptance Scenarios**:

1. **Given** 一次带 traceId 的 consumer→provider 调用，**When** 统计开销，**Then** 报告给出"Environment 查询 N 次 / INFO 日志 N 条 / attachment 双读 N 次 / generateTraceId N 次"的可复算清单。
2. **Given** 日志级别高于 INFO，**When** 统计环境查询，**Then** 报告指出环境查询仍然发生（不受日志级别影响）。
3. **Given** 修复后的实现，**When** 复测同一口径，**Then** 每请求 `Environment` 查询次数不高于修复前，且用例数只增不减。

---

### User Story 5 - 装配契约显式且可自检 (Priority: P2)

接入方需要明确：必须配置哪些 filter 列表、内置 `exception` filter 是否必须排除、consumer 是否该挂 provider 专用 filter；配置缺失时能否在启动期得到提示。

**Why this priority**: 当前唯一"正确装配"只存在于样例 yml，未登记、无校验，换项目复制即踩坑（F-01、F-08）。

**Independent Test**: 分别以"未排除 `-exception`""未挂 TraceId filter""consumer 挂了 `dubboExceptionFilter`"三种配置启动，观察是否有明确日志/文档指引。

**Acceptance Scenarios**:

1. **Given** `dubbo.provider.filter` 未排除 `-exception`，**When** 应用启动，**Then** 输出可定位的告警日志并给出一行修复指引（不阻断启动）。
2. **Given** 模块手册，**When** 新接入方按手册配置，**Then** 能一次性配对 consumer/provider 两侧 filter 列表，无需阅读源码。

---

### User Story 6 - 交付集与测试门槛 (Priority: P1)

维护者需要在改动后得到：可运行、可回归、覆盖成功/失败/异常/空值/边界/并发的测试集，以及修正后的 `AGENTS.md` 与开发手册。

**Why this priority**: 用户明确要求"改完代码必须做功能完备性测试"；模块当前 0 用例，任何修复都无回归保护（F-04）。

**Independent Test**: 执行模块级测试命令，得到全绿输出与用例清单；随机抽取一条 `AGENTS.md` 的能力声明，核对与代码一致。

**Acceptance Scenarios**:

1. **Given** 任一修复项，**When** 交付，**Then** 存在"在旧代码上失败、在新代码上通过"的用例，且用例数从 0 单调增长。
2. **Given** 测试执行，**When** 无外部中间件（Zookeeper/Nacos），**Then** 全部用例可离线运行且通过。

---

### Edge Cases

- traceId：`null` / `""` / `"   "` / 超长；invocation / client / server / MDC 四路来源的组合与冲突；`getObjectAttachment` 为 null 但 `getAttachment` 非 null。
- `Result` 为 null、非 `AsyncRpcResult`、`AsyncRpcResult` 但 future 未完成、回调抛异常；`invoker.invoke` 抛 `RpcException` / `RuntimeException` / `Error`。
- 异常：`hasException()` 为 true 但 `getException()` 返回 null；`getMessage()` 为 null（Message 判定分支）；anonymous/local 类（`getCanonicalName()` 为 null）。
- 方法签名反射：重载、原始类型 vs 包装类型、泛型擦除、`getParameterTypes()` 为 null、`getMethod` 抛 `NoSuchMethodException`/`SecurityException`。
- `GXBusinessException`：`data` 为 null / 空 `Dict` / 已含 traceId / 被多线程共享；子类 `GXConciseBusinessException`、`GXSentinelFlowException`。
- Sentinel：`FlowException` / `BlockException` / `DegradeException` / `AuthorityException` / `SystemBlockException` / `ParamFlowException` / 消息含 "FlowException" 的普通业务异常 / `GXBusinessException` 的 cause 链含 `FlowException`。
- Web 层：`RpcException` 各子类；`GXSentinelFlowException` 的 `data` 为 null；基础 advice 与专用 advice 同时注册且 `@Order` 相同。
- `GXDubboRpcApiBeanPostProcessor`：`ref` 为 null、`getGenericClassType` 返回 null 或抛 `IllegalArgumentException`、`getBeanNamesForType` 返回空/多候选/抛 `BeansException`、`getApplicationContext()` 为 null、目标方法不存在或抛异常。
- selector：`invocation` 为 null、`clientAttachment`/`serverAttachment` 为 null、`selectReverse` 在 provider `ContextFilter` 已被 `removeContext()` 之后被调用。
- 多 Spring 上下文（测试/热部署）下静态 `appName` 串用；consumer 连续调用的 `serverAttachment` 残留。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖模块全部 10 个生产源码文件、3 个运行时资源；每个文件 MUST 有明确判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因），不得跳过。
- **FR-002**: 每条发现 MUST 标注维度（逻辑正确性 / 线程安全 / 性能 / 安全与契约）、严重级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件与影响面。
- **FR-003**: 每条发现 MUST 附带复现方式：既有用例（本模块当前为 0，需新增）、回归用例设计或明确构造步骤；仅凭阅读的判断 MUST 标注为"未验证假设"（C-09 的 listener 时序、C-08 的 consumer 侧 filter 语义 MUST 在计划阶段实测收敛）。
- **FR-004**: 审查 MUST 以**本仓上游源码**为准核对 `GXTraceIdContextUtils`、`GXCommonUtils`、`GXExceptionHandler`、`GXSentinelFlowException` 的语义，不得仅凭类名或经验推断。
- **FR-005**: 异常分类 MUST 收敛为唯一边界并逐条定性：`GXBusinessException` 透传、`GXSentinelFlowException` 透传、声明异常透传、checked 异常透传、`RpcException`/JDK/jakarta/MyBatis 异常透传、Sentinel 各阻断类型转换、其余未声明运行时异常包装；`GXDubboExceptionFilter` 与内置 `ExceptionFilter` 的双重激活 MUST 被验证并收敛为"取代或排除内置"，MUST NOT 让 `GXBusinessException` 被二次包装丢 `code/msg/data`（对应 F-01、F-03）。
- **FR-006**: Web 层 Dubbo 异常处理 MUST 保证 `RpcException`/`GXSentinelFlowException` 的专用 handler 稳定优先于基础 `GXExceptionHandler` 的兜底 `Exception` handler，且 MUST 用真实 `@SpringBootTest`（组件扫描包含基础 advice）验证，不得使用仅注册子类 advice 的 `standaloneSetup`（对应 F-02）。
- **FR-007**: Sentinel 识别 MUST 覆盖官方 `BlockException` 主要子类（Flow/Degrade/Authority/System/ParamFlow），MUST NOT 仅凭"消息含 FlowException"或 cause 链字符串判定；已转换的 `GXSentinelFlowException` MUST 不被二次转换（对应 F-05）。
- **FR-008**: 方法签名反射解析失败时 MUST fail-closed：未声明运行时异常仍 MUST 被包装为 `GXBusinessException` 并记录日志，不得静默 `return`（对应 F-06）。
- **FR-009**: 异常 filter 自身逻辑的异常 MUST NOT 被静默吞掉：必须记录可观测信号（WARN + 计数或等价手段），且不改变已产生的业务异常语义（对应 F-07）。
- **FR-010**: 装配契约 MUST 显式化：内置 `exception` filter 的排除要求、consumer/provider 两侧 filter 列表的推荐值 MUST 登记到模块 `AGENTS.md` 与开发手册；配置缺失时 MUST 在启动期输出可定位的告警日志且不阻断启动（对应 F-01、F-08）。
- **FR-011**: `GXPenetrateAttachmentSelector`、两个 TraceId Filter 与异常 filter MUST 复用同一决策得到的 traceId：同一次调用 MUST NOT 出现两个不同 traceId（对应 F-13）。
- **FR-012**: 任何"写 attachment / 写 MDC / 改异常数据"的路径 MUST 保证设置与恢复成对出现；异步响应回调 MUST 二次捕获/恢复上下文；MUST NOT 在 consumer 侧写入与本次调用无关的上下文（对应 P-OK-01、F-12）。
- **FR-013**: 共享可变状态 MUST 明确并发边界：静态 `appName` 缓存 MUST 支持上下文刷新/重置并保证可见性；异常携带的 `Dict` MUST NOT 被原地修改（改为复制或在构造时写入）（对应 F-10、F-11）。
- **FR-014**: 性能结论 MUST 给出可复算口径：每请求 `Environment` 查询次数、INFO 日志条数、attachment 双读次数、`generateTraceId` 次数、`Dict` 分配次数；MUST NOT 以绝对吞吐或时延数值作为通过门槛；修复后每请求 `Environment` 查询次数 MUST NOT 高于修复前；异常处理路径 MUST NOT 对同一异常重复执行同一分类判定（对应 F-14、F-18、F-19）。
- **FR-015**: 核心链路（TraceId 生成/透传、异常分类）MUST NOT 每请求打印 INFO 级日志；确有运维诉求时 MUST 降级为 DEBUG 并在手册登记开启方式（对应 F-14）。
- **FR-016**: 空值语义 MUST 按 JSpecify 收敛：可空返回与可空入参 MUST 显式标注 `@Nullable` 或改为不允许为空，并与 NullAway 构建检查一致（对应 F-23）。
- **FR-017**: 新增/修改日志 MUST 使用 SLF4J 门面，MUST NOT 保留 `@Log4j2` 直接依赖（对应 F-22）；全部日志文案 MUST 为 ASCII。
- **FR-018**: 每条修复 MUST 附带"能在旧代码上失败、在新代码上通过"的回归用例设计（输入、断言点、期望差异），且该用例 MUST 无需外部中间件即可运行。
- **FR-019**: 任何生产代码改动 MUST 在同一变更中补齐测试：受影响路径的成功、失败、异常、空值、边界用例各至少 1 条；涉及并发/缓存/上下文的改动 MUST 补并发用例；涉及 Web 层语义的改动 MUST 至少补 1 个真实 `@SpringBootTest` 用例（对应 F-04）。
- **FR-020**: 交付前 MUST 提供模块级测试命令的全绿输出（含用例总数与耗时），并 MUST 给出"新增 / 修改 / 保留"用例清单，用例数 MUST 从 0 单调增长；MUST NOT 通过删除或跳过用例来"通过"。
- **FR-021**: 审查与修复 MUST 只触及 `leaf-base-dubbo-zk` 模块；`config/application-dubbo-zk.yml.conf` 的 `WRAN` 拼写与 filter 列表属于样例配置，修改前 MUST 单独确认范围（对应 F-21、F-08）。
- **FR-022**: `AGENTS.md` MUST 与实现逐条对齐（能力清单、Sentinel 判定规则、handler 覆盖范围、装配契约、测试基线），并 MUST 补 `docs/leaf-base-dubbo-zk开发手册.md`（对应 F-15、F-20）。
- **FR-023**: 测试默认 MUST NOT 依赖真实 Zookeeper 等外部中间件，优先使用 Dubbo `injvm`/直连协议与可注入的 `Invoker`/`Result`/`Invocation` 替身；确需真实注册中心才能覆盖的验证 MUST 先向用户申请环境信息，不得臆造地址或密钥，且 MUST 在缺少环境时优雅跳过。
- **FR-024**: 既有被确认正确的行为（P-OK-01～P-OK-06）MUST 被用例锁定，修复 MUST NOT 破坏这些断言。
- **FR-025**: `GXDubboRpcApiBeanPostProcessor` 的绑定失败（目标方法缺失、调用抛异常、上游工具返回 null）MUST 可观测且不阻断启动；其生效方式（仅 `@Component`、依赖外部组件扫描、无自动装配注册）MUST 在开发手册与 `AGENTS.md` 中登记，并有启动期用例证明其被装配或明确不装配（对应 F-16）。
- **FR-026**: `GXBusinessException` 子类透传 MUST 保真：不得因 `data` 为空而把子类降级为基类；traceId 补充 MUST 以非破坏性方式完成，且 MUST NOT 丢失子类语义与字段（对应 F-17）。

### Key Entities *(include if data involved)*

- **审查发现（Finding）**：编号、级别、维度、代码位置、触发条件、影响面、证据、复现方式、回归测试设计、修复建议、是否已验证。
- **异常分类矩阵**：异常类型（`GXBusinessException` / 其子类 / `GXSentinelFlowException` / 声明异常 / checked 异常 / `RpcException` / JDK / jakarta / MyBatis / Sentinel 各子类 / 未声明运行时异常）× 处置（透传 / 包装 / 转换）× 是否记录日志 × traceId 是否增强 × 是否会被内置 filter 二次包装。
- **TraceId 决策链**：来源（invocation 附件 / client 附件 / server 附件 / MDC / 生成）× 参与组件（selector / client filter / server filter / exception filter）× 是否写回 MDC × 恢复语义 × 一致性要求。
- **共享状态清单**：对象（静态 `appName` / 异常内 `Dict` / `RpcContext` 附件 / MDC）× 共享范围 × 现状保护手段 × 收敛路径。
- **装配契约表**：配置键（`dubbo.provider.filter` / `dubbo.consumer.filter`）× 必需项（`dubboServerTraceIdFilter` / `dubboClientTraceIdFilter` / `dubboExceptionFilter` / `-exception`）× 缺失后果 × 是否启动期可检测。
- **性能口径**：每请求 `Environment` 查询次数、INFO 日志条数、attachment 双读次数、`generateTraceId` 次数、`Dict` 分配次数、异常路径重复判定次数。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查覆盖 100% 的 10 个生产源码文件与 3 个运行时资源；100% 的发现记录在规格与后续任务中；新维护者可在 15 分钟内复现任一结论。
- **SC-002**: 100% 的发现具备"级别 + 维度 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归设计"；标注为"未验证假设"的条目在计划阶段 100% 转化为实测或明确标记不支持。
- **SC-003**: 异常分类矩阵覆盖 ≥9 类异常 × 5 个观测点（最终异常类型 / 业务字段是否保留 / 日志 / traceId / 客户端表现），判定率 100%，每条判定附上游源码或实测证据。
- **SC-004**: TraceId 决策链覆盖 5 类来源 × 3 个参与组件 × 3 种恢复场景（正常 / 异步 / 异常），覆盖率 100%；同一次调用零"双 traceId"。
- **SC-005**: 性能结论给出可复算口径，且"每请求 `Environment` 查询次数""每请求 INFO 日志条数""每请求 attachment 双读次数"三项可被独立复算（误差 0）；修复后每请求 `Environment` 查询次数不高于修复前。
- **SC-006**: 修复后模块用例数从 0 增长到 ≥20 条且全绿（成功/失败/异常/空值/边界/并发/Web 集成各类均 ≥1 条），执行率 100%，0 跳过。
- **SC-007**: Web 层异常链路至少 1 条真实 `@SpringBootTest`（组件扫描含基础 advice）用例锁定 handler 选择结果；0 条仅用 `standaloneSetup` 的假集成用例。
- **SC-008**: 装配契约表 100% 覆盖样例配置中的 filter 键，且配置缺失场景有可定位的启动期告警；新接入方仅按手册即可完成两侧配置（无需读源码）。
- **SC-009**: `AGENTS.md` 的能力声明与实现一致率 100%（逐条核对无漂移），开发手册覆盖模块定位、SPI 扩展名、TraceId 优先级、异常矩阵、装配契约、不支持范围。

## Assumptions

- 目标运行形态为 JDK 21+ / Spring Boot 4.1.x / Dubbo 3.3.6 / Zookeeper（`dubbo-zookeeper-curator5-spring-boot-starter`），下游通过 `@Activate` + `META-INF/dubbo/*` SPI 装配 Filter/Selector，通过 `@ComponentScan("cn.maple")` 装配 `GXDubboRpcApiBeanPostProcessor` 与 `GXDubboCallExceptionHandler`。
- 本次为"审查 + 收敛 + 完备测试"的一体化交付：本规格定义验收边界，实际改码在 `/speckit.plan` → `/speckit.tasks` → `/speckit.implement` 阶段执行，并受 FR-018～FR-020 的测试门槛约束；用户"改完代码必须做完备性测试"的要求固化为 FR-018～FR-020 的硬门槛。
- 模块安全边界依赖上游 `leaf-base-framework`（MDC 工具、异常基类、Spring 上下文单例、`GXExceptionHandler`）；本规格只要求"不因本模块默认行为放大上游弱点"，不要求重写上游工具。
- C-01 的收敛方向默认与同族模块 `leaf-base-dubbo-nacos`（`specs/014-dubbo-nacos-module-audit`）保持一致：**启动期配置校验器只告警不阻断**，并在文档登记强制装配契约；具体取代或排除方案由 `/speckit.plan` 决定，但 MUST 保证 `GXBusinessException` 业务字段无损。
- 既有被确认正确的行为（P-OK-01～P-OK-06）按"既定契约"处理，修复只能在不破坏这些断言的前提下进行。
- 性能数据以单机、单 JVM、本地回环网络为前提；结论以"可复算口径 + 同量级复现"为验收标准，不承诺生产容量。
- `AGENTS.md` 与（缺失的）开发手册视为**声明**而非规范：与实现冲突时以"实现 + 宪法"为准，冲突本身单列为发现（F-15）。
- 测试环境默认离线：Dubbo 相关验证优先用 `injvm`/直连协议与可注入替身完成；若某项验证（如真实 Zookeeper 注册中心下的 filter 实际激活顺序）确需真实环境才能闭环，实施前 MUST 先向用户申请，由用户提供真实服务信息后再执行。
- 脚本与命令以 Windows + PowerShell 环境为默认执行环境（`.specify/init-options.json` 中 `script: ps`）。
