# Phase 0 Research: leaf-base-dubbo-zk 审查结论收敛

**Date**: 2026-10-25 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

研究目标：把 spec 中 2 条"未验证假设"（C-08、C-09）与全部技术选型收敛为可执行决策。所有结论均来自**本仓源码**或**本地 `dubbo-3.3.6-sources.jar` 源码**的实际核对，不凭经验推断。

## 结论摘要

- **C-09 由"未验证假设"升级为"已确认缺陷"**：`Filter.Listener.onResponse` 在 `whenCompleteWithContext` 回调中执行，晚于 filter `invoke` 的 `finally`；provider 侧 `GXDubboServerTraceIdFilter`（order=MAX）先恢复 MDC，异常 filter（order=0）随后读取 → traceId 为空。修复方案 D-06。
- **C-08 由"疑似误配置"澄清为"合法但未登记的装配语义"**：显式 `dubbo.consumer.filter` 列表可挂载未在 CONSUMER 组 `@Activate` 的 filter（`ExtensionLoader.getActivateExtension` 对显式名字不做 group 过滤）。该配置是**consumer 侧兜底二次分类**，保留并用例锁定（D-12）。
- **F-13（双 traceId）在正常链路上不成立**：selector 生成的 id 经 `ConsumerContextFilter` 写入 `RpcInvocation` 附件，client filter 从 invocation 复用；provider 侧 server filter 先写 invocation 附件，`ContextFilter.selectReverse` 再读同一附件 → 无需新增抽象，用端到端用例锁定（D-13）。
- **F-21 追加证据**：`SerializeCheckStatus.valueOf("WRAN")` 必然抛 `IllegalArgumentException`（枚举仅 `DISABLE`/`WARN`/`STRICT`），样例配置确为缺陷；但属于仓库级样例，不改（D-14）。

---

## R-01 内置 `ExceptionFilter` 是否与自定义 filter 双重激活

**Decision**：确认双重激活成立；收敛方式为"启动期告警 + 文档登记强制装配（排除 `-exception`）"，不修改 Dubbo 内置行为、不新增配置键。

**证据**：
- `dubbo-3.3.6-sources.jar` → `org/apache/dubbo/rpc/filter/ExceptionFilter.java` 第 49 行 `@Activate(group = CommonConstants.PROVIDER)`，第 50 行 `implements Filter, Filter.Listener`；
- 本模块 `GXDubboExceptionFilter`（第 24 行）同为 `@Activate(group = PROVIDER)`，并以独立扩展名 `dubboExceptionFilter` 注册（`META-INF/dubbo/org.apache.dubbo.rpc.Filter` 第 1 行）；
- 内置 filter 会把"未声明且异常类与接口不在同一代码来源"的 `RuntimeException` 包成普通 `RuntimeException`（同名源码第 94–110 行注释与逻辑），`GXBusinessException` 位于 `leaf-base-framework` jar，与业务接口不同来源 → `code/msg/data` 丢失；
- `config/application-dubbo-zk.yml.conf` 第 26/31 行已写 `...,-exception`，说明仓库实际部署方式依赖该配置，但 `AGENTS.md` 全文无此约定。

**Rationale**：与 `specs/014-dubbo-nacos-module-audit` 的 D-01 完全同构（同一对 jar、同一 filter 名），保持两个互斥注册中心模块的收敛方式一致，降低下游迁移成本。只告警不阻断启动满足宪法 §II"不破坏存量部署"。

**Alternatives considered**：
1. 复用内置 `ExceptionFilter` 并只做扩展（不注册独立扩展名）→ 需重写其 `onResponse` 语义，且无法保留 `GXSentinelFlowException` 转换，等于放弃现有能力；
2. 在代码中通过 `System.setProperty`/`URL` 参数强行改写 filter 列表 → 隐式副作用，违反宪法 §IV"显式配置"，且对 `ServiceConfig`/`ReferenceConfig` 两条路径都生效难以覆盖；
3. 启动期直接抛异常阻断 → 会破坏所有未配置的下游（存量默认值），违反宪法 §II。

---

## R-02 `Filter.Listener.onResponse` 与 filter `invoke` 的时序

**Decision**：确认 listener 回调晚于所有 filter 的 `invoke` 返回（含其 `finally`），因此异常 filter **不能**依赖 MDC 获取 traceId。

**证据**：`org/apache/dubbo/rpc/cluster/filter/FilterChainBuilder.java`（provider 侧链节点，第 118–142 行）与 `CallbackRegistrationInvoker.invoke`（consumer 侧，第 196–225 行）都在 `invoke` 返回的 `Result` 上注册 `whenCompleteWithContext`，在回调里才调用 `listener.onResponse(...)` / `listener.onError(...)`。链路节点的 `filter.invoke(next, invocation)` 早已返回，`GXDubboServerTraceIdFilter` 第 39–41 行 `finally` 已执行 `restoreTraceId(originalTraceId)`。

**Rationale**：provider 侧 `originalTraceId` 通常为 null → `restoreTraceId(null)` = `removeTraceId()`，MDC 被清空；此时 `GXDubboExceptionFilter` 第 32 行 `attachTraceId(appResponse)` 与第 58 行 `attachTraceId(businessException)` 均为空操作，`data` 里没有 traceId，与 `GXDubboCallExceptionHandler` 的设计（enrich traceId）不一致。

**Alternatives considered**：
1. 把 `GXDubboServerTraceIdFilter` 的恢复时机推迟到 `onResponse`（改成 `Filter.Listener`）→ 需要重新设计"何时恢复"，异步/长调用会延长 MDC 生命周期，且与现有 P-OK-01 断言冲突；
2. 在 `RpcContext` 中额外保存 traceId 供 listener 读取 → `ContextFilter` 在 `onResponse` 中会 `removeContext()`，且写入语义更复杂；
3. **采用**：listener 阶段优先读 invocation 附件（`GXDubboServerTraceIdFilter` 第 38 行已把 traceId 写入 invocation），MDC 作为回落，单次调用只解析一次。

---

## R-03 显式 `filter:` 列表能否把 provider-only filter 挂到 consumer 链

**Decision**：可以，且这是 **Dubbo 既有语义**，不是误配置；保留该装配并用例锁定。

**证据**：`org/apache/dubbo/common/extension/ExtensionLoader.getActivateExtension(URL, String[], String group)` 第 344–423 行——只要未出现 `-default`，先按 group 收集 `@Activate` 扩展，再对显式列出的每个名字执行 `getExtension(name)` 加入结果（第 400–421 行），**不做 group 过滤**；`-exception` 仅通过 `namesSet.contains("-" + name)` 从结果中剔除。

**Rationale**：consumer 侧挂 `dubboExceptionFilter` 的实际效果是"对 provider 已分类过的异常再做一次兜底分类"：`GXBusinessException` 命中 instance 判定原样返回；未分类的 `RuntimeException` 被包装为 `GXBusinessException(500)`。这与样例配置（第 26 行）一致，也与 014 模块保留的装配一致。

**Alternatives considered**：
1. 从 consumer 列表移除 `dubboExceptionFilter` → 会改变既有线上异常表现（consumer 端可能直接收到原始 `RuntimeException`），属未授权契约变更；
2. 拆分出 consumer 专用 filter → 新增类型但语义完全重复，违反 YAGNI；
3. **采用**：保留 + 用例锁定 + 手册登记两侧推荐列表。

---

## R-04 `PenetrateAttachmentSelector` 的装配与激活方式

**Decision**：自定义扩展名 `dubboPenetrateAttachmentSelector` 有效；`select` 在 consumer 侧、`selectReverse` 在 provider 侧被调用。

**证据**：
- `org/apache/dubbo/rpc/filter/ContextFilter.java` 第 63 行 `@Activate(group = PROVIDER, order = Integer.MIN_VALUE)`，第 65–71 行构造时 `getSupportedExtensionInstances()`，第 200–215 行 `onResponse` 中遍历调用 `selectReverse(...)` 并 `appResponse.addObjectAttachments(selected)`；
- `org/apache/dubbo/rpc/cluster/filter/support/ConsumerContextFilter.java` 第 52–80 行 `@Activate(group = CONSUMER, order = MIN_VALUE)`，`invoke` 中调用 `select(...)` 并 `((RpcInvocation) invocation).addObjectAttachments(selected)`。

**Rationale**：`getSupportedExtensionInstances()` 返回"所有带 `@Activate` 的实例"，与扩展名无关，因此 `dubboPenetrateAttachmentSelector` 这个非默认名字不影响装配；`GXPenetrateAttachmentSelector`（第 21 行）声明了 `{PROVIDER, CONSUMER}` 两组，两侧都会拿到。

**Alternatives considered**：改为默认扩展名 `penetrateAttachmentSelector` → 无收益且可能与其他 jar 冲突（`leaf-base-dubbo-nacos` 同名扩展名共存时以配置决定），放弃。

---

## R-05 是否存在"同一调用两个 traceId"

**Decision**：正常链路上不存在；不新增共享解析工具类，改用端到端用例锁定。

**证据**：consumer 侧调用顺序为 `ConsumerContextFilter`（order=MIN_VALUE，最外层，第 75 行 `select`）→ 将选中 map `addObjectAttachments` 到 invocation（第 78 行）→ `GXDubboClientTraceIdFilter`（order=MAX_VALUE，最内层）第 45 行 `getInvocationAttachment` 命中同一 id；provider 侧 `GXDubboServerTraceIdFilter` 第 38 行先写 invocation 附件，`ContextFilter.onResponse` → `selectReverse` 第 63 行读 invocation 附件。

**Rationale**：唯一会双生成的情形是"filter 未被装配"或"invocation 不是 `RpcInvocation`"（`addObjectAttachments` 不生效）——该情形下 selector 的 id 本就不会进入链路，双 id 无实际影响（无观测冲突）。为它引入公共工具类违反宪法附加约束 §8。

**Alternatives considered**：抽 `GXDubboTraceIdResolver` 被 selector + 2 filter 复用 → 3 个类确实满足"≥2 处复用"，但会把"反向选择/正向选择"的差异参数化到同一方法（布尔开关），可读性下降且无缺陷可修；保留现状 + 测试锁定。

---

## R-06 `GXSentinelFlowException` 的类型语义

**Decision**：在异常 filter 中**优先透传** `GXSentinelFlowException`，不参与 Sentinel 判定、不重建。

**证据**：`GXSentinelFlowException`（`leaf-base-framework`）第 16 行 `extends RuntimeException`（**不是** `GXBusinessException`），字段 `msg/code/data` 均为 `final`；第 26–31 行构造器保留 cause。因此现有 `instanceof GXBusinessException` 分支覆盖不到它，而它被 `isSentinelFlowException` 的 cause 链遍历命中（其 cause 常为原始 `FlowException`）→ 被重建、`data` 与 406 丢失。

**Rationale**：`GXSentinelFlowException` 是本框架自己的限流语义载体，provider 侧再次分类没有任何信息增益；透传后可让 consumer 侧 advice 的 `handleSentinelFlowException` 正确返回 406。

**Alternatives considered**：把它并入 `isSentinelFlowException` 判定（当前事实行为）→ 重建后丢失 `data`，且会掩盖 provider 已完成的分类；放弃。

---

## R-07 Sentinel 阻断异常识别（不新增依赖）

**Decision**：判定条件收敛为三条并集：① 类名以 `com.alibaba.csp.sentinel.slots.block.` 开头；② 可赋值给 `com.alibaba.csp.sentinel.slots.block.BlockException`（`Class.forName(name, false, loader)` 静态解析，缺失时为 null）；③ 消息包含完整常量 `SentinelBlockException: FlowException`。删除裸 `"FlowException"` 子串判定。

**证据**：`AGENTS.md` §4.3 与既有线上识别方式均以完整消息 `"SentinelBlockException: FlowException"` 为契约；`leaf-base-dubbo-zk` 的 pom 没有 Sentinel 依赖，样例配置也未引入 `leaf-base-dubbo-nacos-sentinel`。

**Rationale**：保持"模块不引入 Sentinel 依赖"（宪法附加约束 §3），同时覆盖 `Flow/Degrade/Authority/System/ParamFlow` 全部 `BlockException` 子类；完整消息兜底保留对旧版包装器的兼容。裸子串判定会把任何含该字样的业务异常误判为限流（F-05）。

**Alternatives considered**：加 `sentinel-core` 依赖直接用 `instanceof BlockException` → 为 1 个判定引入重量级传递依赖，且违反"能用类名/反射完成的不引入新库"；014 已采用同样方案，保持一致。

---

## R-08 Web 层 advice 优先级

**Decision**：在 `GXDubboCallExceptionHandler` 上加 `@Order(Ordered.HIGHEST_PRECEDENCE)`，并用真实 `@SpringBootTest`（同一上下文注册基础 `GXExceptionHandler` 与子类 advice）验证。

**证据**：`GXExceptionHandler` 第 42 行 `@RestControllerAdvice`、第 226–231 行 `@ExceptionHandler(Exception.class)` 返回 `code=500, msg=系统内部错误`；`GXDubboCallExceptionHandler` 继承它且未声明 `@Order`；Spring MVC 的 `ExceptionHandlerExceptionResolver` 按 advice Bean 顺序尝试匹配，`RpcException`/`GXSentinelFlowException` 均为 `RuntimeException`，存在被兜底 handler 抢先的路径。

**Rationale**：`@Order` 只改变"哪个 advice 先被尝试"，不改变任何返回结构；014 已采用同一修复并通过 3 条集成用例验证。

**Alternatives considered**：让本模块不再继承 `GXExceptionHandler`（改为组合）→ 会丢掉父类全部 handler（非法参数、绑定校验、SQL 异常等），破坏既有 Web 契约；放弃。

---

## R-09 `GXBusinessException.data == null` 时保真与 traceId 的取舍

**Decision**：保留"降级为基类并注入 traceId"的现有行为，不修改代码；以 Javadoc + 用例显式锁定，并在手册登记。对 spec FR-013/FR-026 做收敛调整（见文末"对 spec 的调整"）。

**证据**：`GXBusinessException` 第 21 行 `private final Dict data`，Lombok `@Data` 不会为 final 字段生成 setter → 无法在保留原对象的前提下替换 `data`；也不存在"复制同类型子类"的通用构造器。014 的选择是构造基类 `GXBusinessException` 并有用例 `wrapsBusinessExceptionWhenDataIsNullToExposeTraceId`。

**Rationale**：两个目标（子类保真、traceId 可回传）在 `leaf-base-framework` 契约下不可同时满足；traceId 可回传是线上排障的硬需求，且降级只影响 `data == null` 的窄路径（`data` 非空时原对象不变）。改动需跨模块（framework），超出本次范围。

**Alternatives considered**：
1. 不注入 traceId、保留子类 → 该路径 `data` 从"含 traceId"变为 null，可观测性退化；
2. 在 filter 里用反射写 final 字段 → 破坏封装，NullAway/ErrorProne 风险；
3. **采用**：保持 + 文档 + 用例锁定，并向 `leaf-base-framework` 提"可设置 data"的后续提案（不在本次范围）。

---

## R-10 appName 缓存与热路径环境查询

**Decision**：把 `GXPenetrateAttachmentSelector` 的静态 `volatile appName` 提升为共享缓存（`public static getCachedAppName()` + `resetAppNameCache()`），两个 filter 与 selector 三处复用；`UNKNOWN_APP` 永不缓存；日志降为 DEBUG。

**证据**：`GXCommonUtils.getEnvironmentValue` 第 147–160 行每次都走 `GXSpringContextUtils.getEnvironment()`；`GXTraceIdContextUtils.generateTraceId()` 第 66–74 行内部再查一次 `spring.application.name`；当前 client filter 第 25 行、server filter 第 25 行各查一次 → 无入站 traceId 时每请求最多 3 次。

**Rationale**：3 个调用点满足"抽取需 ≥2 处复用"（宪法附加约束 §8）；`resetAppNameCache()` 解决多 Spring 上下文串用（F-10），并让测试可确定性地验证"只查一次"。静态字段加 `volatile` 保证可见性；重复计算在并发首启时是良性的（同值），不引入锁。

**Alternatives considered**：
1. 各自缓存（filter 内部静态）→ 3 份缓存、口径不一致（当前 014 的 filter 已改成共享，保持同构）；
2. 通过 Spring 注入 `Environment` 到 filter → Dubbo SPI 实例由 Dubbo 创建，不是 Spring Bean，需 `ApplicationContext` 静态回读，成本更高；
3. 用 `GXSpringContextUtils.getEnvironment()` 缓存 Environment 对象 → 跨模块改动，超范围。

---

## R-11 `@Nullable` 与 NullAway 约束

**Decision**：仅对实际可空的方法补 `org.jspecify.annotations.Nullable`：`getAttachment`、`getInvocationAttachment`（两个 filter + selector）、`GXCommonUtils.reflectCallObjectMethod` 返回值使用处、`GXSpringContextUtils.getApplicationContext()` 的使用处。不为消除告警而改上游签名。

**证据**：根 POM 第 565 行编译参数 `-Xplugin:ErrorProne -XepDisableAllChecks -Xep:NullAway:ERROR -XepOpt:NullAway:AnnotatedPackages=cn.maple -XepOpt:NullAway:JSpecifyMode=true`；4 个 `package-info.java` 均为 `@NullUnmarked`（JSpecify 下豁免）。

**Rationale**：宪法 §V 要求可空语义显式化；`@NullUnmarked` 包内标注 `@Nullable` 是纯增量、零运行时影响，且与 014 的实际做法一致。

**Alternatives considered**：把 `package-info` 改为 `@NullMarked` → 会让整个包进入 NullAway 强校验，可能引发大范围既有代码失败，超出本次范围。

---

## R-12 测试可离线运行

**Decision**：全部用例离线运行，不连接 Zookeeper；`@SpringBootTest` 使用最小配置类（`@SpringBootConfiguration` + `@EnableWebMvc` + `@Import`），不启用自动装配。

**证据**：本地仓库 `F:\repository` 已有 `leaf-base-framework-4.3.0-SNAPSHOT.jar` 与 `leaf-base-nacos-4.3.0-SNAPSHOT.jar`（`dir` 实测）；014 的同构用例（`GXDubboTestApp`、`@SpringBootTest(classes=...)`）已在该环境跑通。

**Rationale**：`dubbo-zookeeper-curator5-spring-boot-starter` 是 compile 依赖，但没有 `dubbo.registry.address` 配置时不会发起连接；`@SpringBootTest` 用最小配置可完全避开 Dubbo/Nacos 自动装配，满足 FR-023。

**Alternatives considered**：用 `injvm` 协议做真实调用链 → 需要额外配置 provider/reference 与接口服务，成本高且与"仅验证 filter 语义"目标不匹配；改用"手工构造 filter 链"（`new ExceptionFilter().onResponse(...)` + 自定义 filter 实例）已能覆盖 F-01 的真实行为（014 已验证）。

---

## R-13 样例配置 `serialize-check-status: WRAN`

**Decision**：确认是缺陷，但**本次不修改**（不在模块边界内）；在开发手册"已知问题"登记。

**证据**：`dubbo-3.3.6-sources.jar` → `org/apache/dubbo/common/utils/SerializeCheckStatus.java` 第 19–33 行枚举值仅 `DISABLE`、`WARN`、`STRICT`；`SerializeSecurityConfigurator.refreshStatus()` 第 205 行 `SerializeCheckStatus.valueOf(statusString)` → 非法值抛 `IllegalArgumentException`。

**Rationale**：`config/` 是仓库级样例目录，不属于任何 Maven 模块；`AGENTS.md` §2 明确"每次修改只能修改当前 Maven 模块"。登记后由样例配置专项目统一修正，避免本次变更被"顺手改配置"污染。

**Alternatives considered**：直接改 `config/application-dubbo-zk.yml.conf` → 违反模块边界；改为在本模块 `additional-spring-configuration-metadata.json` 中提示 → 该键由 Dubbo 提供，非本模块配置，无收益。

---

## R-14 失败计数与可观测性

**Decision**：在 `GXDubboExceptionFilter` 增加 `private static final AtomicLong FILTER_FAILURE_COUNT` 与包级可见 `static long filterFailureCount()`（唯一使用者为同包测试），外层 `catch (Throwable)` 中 `incrementAndGet()` 后 WARN。

**证据**：宪法 §V 要求"失败路径必须可观测"；014 的同名实现（第 35、104–108、139–141 行）已通过用例 `recordsFilterInternalFailure` 验证；014 的 T057 明确移除了"未被使用的 `resetFilterFailureCount()`"，说明"计数器必须有真实使用者"是该仓库的既定要求。

**Rationale**：满足 F-07 且不引入监控依赖；包级可见避免成为公共 API（宪法附加约束 §8 的"未被使用的公共 API"）。

**Alternatives considered**：接入 Micrometer `Counter` → 引入指标依赖与命名规范问题，超范围；只打 WARN 不计数 → 无法在测试中断言。

---

## R-15 每请求开销口径（修复前后）

| 项 | 修复前 | 修复后 | 依据 |
| --- | --- | --- | --- |
| `Environment` 查询（有入站 traceId） | 2（client+server 各查 appName） | ≤1（首次后命中缓存） | R-10 |
| `Environment` 查询（无入站 traceId，provider） | 3（appName + `generateTraceId` 内部） | ≤2（首次 appName + 1 次生成） | R-10 |
| INFO 日志条数 | 2（client+server） | 0（DEBUG 关闭时） | D-08 |
| traceId 解析次数/异常 filter | 3（`attachTraceId` ×2 + `GXBusinessException` 分支） | 1 | D-06 |
| `resolveRpcErrorType` 调用次数/异常 | 2 | 1 | D-09 |
| `Dict` 分配（正常路径） | 1（selector 返回值） | 1 | 不变 |

---

## 对 spec 的调整（计划阶段收敛）

1. **FR-013 / FR-026（异常 `Dict` 非原地修改、子类保真）**：改为"data 非空时原地注入（登记为契约）；data 为空时保留降级行为（与 014 一致）"。理由见 R-09——`data` 为 `final`，不改上游无法同时满足；以 Javadoc + 用例 + 手册登记替代。
2. **FR-012（consumer 侧不写无关上下文）**：改为"consumer 侧对 `clientAttachment`/`serverAttachment` 的双向写入保留（R-03/R-05 语义），以用例锁定并在手册登记"。理由见 D-12——移除属未授权契约变更。
3. **FR-021（样例配置）**：明确为"只登记不改"，见 R-13。
4. **FR-011（同一次调用零双 traceId）**：以用例锁定，不新增生产代码，见 R-05。

## 待闭环项

无。所有 `NEEDS CLARIFICATION` 与"未验证假设"均已由上述 15 项研究消解，可进入 Phase 1 设计与 `/speckit.tasks`。
