# Phase 0 研究与决策：leaf-base-dubbo-nacos 审查

**Feature**: 014-dubbo-nacos-module-audit | **Date**: 2026-10-25 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

本文件记录两件事：**R 系列 = 已核对的事实**（可复核，不做假设）；**D 系列 = 设计决策**（决策 / 理由 / 被否决的替代方案）。所有事实均来自本仓源码或本次实测命令，不引用外部经验。

---

## 一、事实核对（R-01 ~ R-12）

### R-01 依赖真相（实测）

- 命令：`mvn -o -pl leaf-base-dubbo-nacos dependency:tree "-Dincludes=com.alibaba.nacos:*,org.apache.dubbo:*"`
- 结果：
  - `cn.maple.framework:leaf-base-nacos:jar:4.3.0-SNAPSHOT:compile` → `com.alibaba.nacos:nacos-client:jar:3.1.1:compile`（含 `nacos-client-basic`、`nacos-auth-plugin`、`nacos-encryption-plugin`、`nacos-logback-adapter`、`nacos-log4j2-adapter` 等传递项）。
  - `org.apache.dubbo:dubbo-spring-boot-starter:jar:3.3.6:compile` → `dubbo-spring-boot-autoconfigure` / `dubbo-spring-boot-autoconfigure-compatible` / `dubbo:jar:3.3.6`（→ `hessian-lite:4.0.4`）。
  - `org.apache.dubbo:dubbo-nacos-spring-boot-starter:jar:3.3.6:compile`。
- 影响：Nacos client 与 Dubbo 全在 compile 类路径，测试上下文必须显式关闭 Nacos 自动配置（R-11）。

### R-02 内置 `ExceptionFilter` 的行为（字节码实测，F-01 的机制证据）

- 命令：
  - `javap -v -cp "F:\repository\org\apache\dubbo\dubbo\3.3.6\dubbo-3.3.6.jar" org.apache.dubbo.rpc.filter.ExceptionFilter`
  - 读取 jar 内 `META-INF/dubbo/internal/org.apache.dubbo.rpc.Filter`
- 结果：
  - 类声明：`public class org.apache.dubbo.rpc.filter.ExceptionFilter implements org.apache.dubbo.rpc.Filter, org.apache.dubbo.rpc.BaseFilter$Listener`。
  - 常量池含 `Lorg/apache/dubbo/common/extension/Activate;`、`group`、`provider` → 等价 `@Activate(group = "provider")`，**provider 侧默认激活**。
  - 字节码逻辑：`GenericService` 跳过 → `!(RuntimeException) && (Exception)` 直接 return（checked 异常透传）→ 反射方法签名匹配则 return → `java./javax./jakarta.` 前缀 return → `RpcException` return → 其余**未声明运行时异常** `new RuntimeException(StringUtils.toString(exception))` 并 `setException`。
  - SPI 声明：`exception=org.apache.dubbo.rpc.filter.ExceptionFilter`。
- 推论：`GXBusinessException`（`cn.maple.core.framework.exception`，非常见 JDK 前缀、非 `RpcException`、通常不在 provider 接口方法 `throws` 声明中）会被内置 filter **包装为普通 `RuntimeException`**，`code/msg/data` 丢失。

### R-03 生产装配真相（F-01 的直接证据）

- `config/online-nacos-dubbo-demo.yml` 第 21 行：`filter: dubboExceptionFilter,-exception`（位于 `dubbo.provider` 下）。
- 全仓 `*.{yml,yaml,properties,md}` 检索 `dubbo.provider.filter` / `dubbo.consumer.filter` / `-exception`：仅命中该文件的 `filter: dubboExceptionFilter,-exception`（其余命中均为 MyBatis 的 `propagate-exception`，无关）。
- 推论：模块**必须**依赖 `dubbo.provider.filter=dubboExceptionFilter,-exception` 才能让内置 `ExceptionFilter` 让位；该要求只存在于示例配置，**模块 `AGENTS.md` 与手册均未登记**（无手册）。这是"隐性装配契约"。

### R-04 双重 `@RestControllerAdvice` 装配（F-02 的证据）

- `leaf-base-framework` 的 `@GXEnableLeafFramework`（`annotation/GXEnableLeafFramework.java` 第 12–20 行）：`@ComponentScan(value = {"cn.maple"}, ...)`。
- `GXExceptionHandler`（`handler/GXExceptionHandler.java` 第 42 行）：`@RestControllerAdvice`；第 226–231 行含 `@ExceptionHandler(Exception.class) public GXResultUtils<Dict> handleException(Exception e) { ... "系统内部错误" }`。
- `GXDubboCallExceptionHandler`（第 14 行）：`@RestControllerAdvice`，**无 `@Order`/`Ordered`**；`handleRpcException`（第 17–21 行）、`handleSentinelFlowException`（第 23–27 行）。
- 推论：两个 advice 同时注册；Spring 按 advice 顺序取"第一个能匹配的 advice"，基础 advice 的 `Exception` 兜底可匹配 `RpcException`/`GXSentinelFlowException`（均为 `RuntimeException`），专用 handler 是否生效取决于排序（既有测试用 `standaloneSetup` 只注册子类，无法暴露）。

### R-05 MDC 上下文语义（P-OK-01 的依据）

- `GXTraceIdContextUtils`（`leaf-base-framework`）：
  - `getTraceId()`：`MDC.get(KEY)`，null → `""`。
  - `getNullableTraceId()`：直接返回 `MDC.get(KEY)`（可能为 null）。
  - `putTraceId(traceId)`：blank → `removeTraceId()`；否则 `MDC.put`。
  - `restoreTraceId(traceId)`：blank → `removeTraceId()`；否则 `putTraceId`。
- 推论：TraceId Filter 的 `originalTraceId = getNullableTraceId()` + `finally restoreTraceId(originalTraceId)` 在"进入前为空"时也会正确清除 MDC，无泄漏（5 条既有用例锁定）。

### R-06 上游工具类的失败语义

- `GXCommonUtils.getEnvironmentValue(String key, Class<R> type, R defaultValue)`：每次调用 `GXSpringContextUtils.getEnvironment()`；Environment 为 null 返回 defaultValue；简单类型直接返回/默认值；复杂类型走 `ObjectMapper`（可能再取一次 bean）。
- `GXCommonUtils.getGenericClassType(Class<?>, Integer)`：`ClassUtil.getTypeArgument(clazz, index)`，不可解析时返回 null（不抛）；参数为 null 抛 `IllegalArgumentException`。
- `GXCommonUtils.reflectCallObjectMethod(Object, String, Object...)`：目标为 null / 方法缺失 / 调用异常 → 记 DEBUG 后返回 null，**不抛**。
- 推论：`GXDubboRpcApiBeanPostProcessor` 的 `staticBindServeServiceClass` 失败是**静默**的（返回 null，调用方不检查）；`@Log4j2` 是模块内唯一直接使用 Log4j2 API 的类型。

### R-07 既有基线（实测）

- 命令：`mvn -o -pl leaf-base-dubbo-nacos test`
- 结果：`Tests run: 31, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS`；分布：`AsciiSourceGuardTest` 1、`GXDubboExceptionFilterTest` 11、`GXDubboTraceIdFilterTest` 5、`GXDubboCallExceptionHandlerTest` 2、`GXDubboCallExceptionHandlerSpringBootTest` 2、`GXDubboRpcApiBeanPostProcessorTest` 4、`GXDubboRpcApiBeanPostProcessorSpringBootTest` 1、`GXPenetrateAttachmentSelectorTest` 5。
- 日志中的 `ERROR Got unchecked and undeclared exception ...` 是 `GXDubboExceptionFilterTest` 触发 filter 自身日志的**预期输出**，非测试失败。

### R-08 交付集缺口与文档漂移（F-13 的证据）

- `docs/` 20 份手册中**无** `leaf-base-dubbo-nacos开发手册.md`（有 datasource、feign、rocketmq、sso、redisson 等）。
- `AGENTS.md` 第 89 行："当前模块下没有现成的 `src/test` 测试目录"；实际 `src/test/java` 下 9 个测试类。
- `AGENTS.md` 第 67 行提到 `serialize.allowlist` 仅含 `HashBasedTable`，但未说明来源与影响面。

### R-09 序列化白名单现状（F-12 的证据）

- `src/main/resources/security/serialize.allowlist` 全文仅一行：`com.google.common.collect.HashBasedTable`（40 B）。
- 无注释、无来源说明；`pom.xml` 未显式声明 Guava 依赖（经其它传递依赖引入）。

### R-10 构建门槛（实测）

- 根 `pom.xml`：`<java.version>21</java.version>`、`maven-compiler-plugin` `<release>21</release>`；`-Xplugin:ErrorProne -XepDisableAllChecks -Xep:NullAway:ERROR -XepOpt:NullAway:AnnotatedPackages=cn.maple -XepOpt:NullAway:JSpecifyMode=true`；`error-prone.version=2.50.0`、`nullaway.version=0.13.4`。
- 根 `<modules>` 第 26 行注册 `leaf-base-dubbo-nacos`。

### R-11 测试上下文约束（FR-025 的依据）

- Nacos client 在 compile 类路径（R-01），`@SpringBootTest` 启动会尝试解析 Nacos 配置；既有 `GXDubboRpcApiBeanPostProcessorSpringBootTest` 已用 `.properties("spring.cloud.nacos.config.import-check.enabled=false")` 规避。
- Dubbo `injvm`/直连协议不需要注册中心；本模块的 Filter/Selector 行为可在无 Nacos 环境下完整验证（既有 31 条用例即证明）。
- 真实 Nacos 仅在"注册中心服务发现下 Filter 实际激活顺序/端到端"场景才需要 → 由用户提供连接信息后执行（FR-025）。

### R-12 测试真实性与命名（F-16 的证据）

- `GXDubboCallExceptionHandlerSpringBootTest` 第 25–27 行使用 `MockMvcBuilders.standaloneSetup(new TestController()).setControllerAdvice(new GXDubboCallExceptionHandler())`，**不启动 Spring Boot 上下文、不做组件扫描**。
- 因此它验证的是"子类 advice 自身能处理"，而非"两个 advice 同时注册时的顺序"（F-02 因此不会被发现）。

---

## 二、设计决策（D-01 ~ D-12）

### D-01 内置 `exception` filter 的收敛方式

**Decision**：
1. 在模块内新增 `GXDubboProviderFilterValidator`（`@Component`，`SmartInitializingSingleton`），启动后读取 Dubbo 配置的 `provider.filter`（`Environment.getProperty("dubbo.provider.filter")`），若存在且**未包含** `-exception`（或未包含 `dubboExceptionFilter`），输出 **ERROR**：`"DUBBO FILTER: built-in ExceptionFilter is active; configure dubbo.provider.filter=dubboExceptionFilter,-exception to preserve GXBusinessException code/msg/data"`。
2. 在 `docs/leaf-base-dubbo-nacos开发手册.md` 与 `AGENTS.md` 把该配置登记为**强制装配契约**。
3. 校验器**只告警、不阻断启动**（兼容存量）。**不新增 `check-strict` 配置键**（`/speckit.tasks` 裁决：YAGNI，宪法附加约束 §8；D-01b 不采纳）。

**Rationale**：F-01 的后果是业务错误码静默丢失，而正确装配只存在于示例配置；启动期把"隐性要求"变"显式可观测"是唯一能在不破坏既有部署的前提下收敛的方式。Dubbo 不支持从一个扩展内部移除另一个已激活扩展，因此无法在 filter 代码内自我修复。

**Alternatives considered**：
- 只在文档写要求 → 已证实无效（示例配置有、模块文档无，下游升级不会重读）。
- 删除自定义 filter、改用内置 + 在 `GXExceptionHandler` 兜底 → 会把 Dubbo 语义（`RpcException`、Sentinel）倒灌进框架模块，违反宪法 I。
- 用 `@Activate(order)` 让自定义 filter 覆盖内置 → 两者是不同扩展名，order 只影响链顺序，不能移除内置。

**D-01b（不采纳）**：原拟新增 `maple.framework.dubbo.filter.check-strict`（`true` 时校验失败抛 `GXBusinessException` 阻断启动）。`/speckit.tasks` 裁决**不采纳**：校验器已能 ERROR 告警，额外开关属投机性配置（违反宪法附加约束 §8 YAGNI），且阻断启动会放大升级风险。若后续确有强制失败需求，再按宪法 §IV 补 `additional-spring-configuration-metadata.json` 后引入。

### D-02 Web 层 Dubbo 异常的 advice 排序

**Decision**：给 `GXDubboCallExceptionHandler` 加 `@Order(Ordered.HIGHEST_PRECEDENCE)`。

**Rationale**：两个 `@RestControllerAdvice` 同时注册时，Spring 按 advice 顺序匹配；子类已是"基础 handler 全集 + Dubbo 专用 handler"的超集，只要它先被检查，`RpcException`/`GXSentinelFlowException` 就会命中专用方法，其余异常仍由继承的基础 handler 兜底。一行改动即可稳定语义。

**Alternatives considered**：
- 移除基础 `GXExceptionHandler` 的 `@RestControllerAdvice` → 影响所有其它模块（跨模块破坏，违反宪法 II）。
- 让子类不继承基础类 → 需复制 20+ handler，违反附加约束 4（重复造轮子）。
- 用 `@ExceptionHandler` 在基础类上排除 `RpcException` → 框架模块反向依赖 Dubbo（违反宪法 I）。

### D-03 Sentinel 阻断类型覆盖

**Decision**：`isSentinelFlowException` 改为：
1. 遍历 cause 链，若 `className.startsWith("com.alibaba.csp.sentinel.slots.block.")` 且以 `Exception` 结尾（覆盖 `FlowException`/`DegradeException`/`AuthorityException`/`SystemBlockException`/`ParamFlowException`/`BlockException` 自身）→ 命中；
2. 保留对 `BlockException` 可赋值判定的反射兜底（若 Sentinel 在类路径，尝试 `Class.forName("com.alibaba.csp.sentinel.slots.block.BlockException").isAssignableFrom(current.getClass())`）；
3. **删除**"消息含 `FlowException`"的宽松匹配，改为仅在类名为空且消息以 `SentinelBlockException:` / `FlowException` 精确片段开头时兜底（降低误判）。

**Rationale**：F-03 要求不遗漏官方阻断类型并避免误判；不引入 `sentinel-core` 依赖（模块定位与 AGENTS.md 第 38 行明确 Sentinel 适配归 `leaf-base-dubbo-nacos-sentinel`）。

**Alternatives considered**：
- 引入 `sentinel-core` 用 `instanceof BlockException` → 新增依赖且与模块定位冲突。
- 保持只认 Flow/Block → F-03 不解决。

### D-04 方法签名反射失败 fail-closed

**Decision**：`onResponse` 中 `getMethod(...)` 抛 `NoSuchMethodException` 时，**不 return**，继续落入"未声明运行时异常 → 包装为 `GXBusinessException`"，并记录 DEBUG（含方法名与参数类型）。

**Rationale**：FR-008 要求 fail-closed；当前 return 会让 provider 内部错误以原始异常类型泄露给 consumer，且跳过日志。

**Alternatives considered**：保持 return → 与 FR-008 冲突。

### D-05 外层异常兜底的可观测性

**Decision**：`onResponse` 的外层 `catch (Throwable)` 保留（防止 filter 自身异常破坏 RPC 响应），但必须 `LOGGER.warn` + 递增计数（`LongAdder` 或 `AtomicLong` 静态计数器）；不改变已产生的业务异常语义。

**Rationale**：FR-009 要求不静默；兜底是必要的防御，但必须可观测。

**Alternatives considered**：直接抛出 → filter 异常会覆盖原始响应，风险更大。

### D-06 `appName` 共享静态缓存

**Decision**：把 `GXPenetrateAttachmentSelector.appName` 提升为包内可见的共享持有者（例如 `selector/GXPenetrateAttachmentSelector` 暴露 `static String getAppName()` + `static void resetAppNameCache()`，或抽到 `filter` 包内的包级常量类），两个 TraceId Filter 改为调用该共享方法；`resetAppNameCache()` 供测试与上下文刷新使用。

**Rationale**：F-08/F-09 要求消除热路径环境查找并统一口径；`volatile` + 显式重置在"单应用上下文"下正确，在多上下文（测试/热部署）下可通过重置隔离。

**Alternatives considered**：
- 各自缓存 → 三份静态状态，漂移风险更高。
- 继续每请求读取 → F-09 不解决。

### D-07 日志级别

**Decision**：两个 TraceId Filter 的"propagate/receive TraceId"日志由 INFO 降为 DEBUG（或 `if (log.isDebugEnabled())` 守卫）；`GXDubboExceptionFilter` 的"Got unchecked and undeclared exception"保持 ERROR（这是真正的服务端错误，需保留）；`GXDubboRpcApiBeanPostProcessor` 的绑定信息保持 INFO（启动期低频）。

**Rationale**：F-10 要求消除每请求 INFO 刷屏；区分"热路径高频"与"启动期/异常低频"。

**Alternatives considered**：全部降 DEBUG → 丢失启动期可观测性。

### D-08 `RpcException` 状态码语义

**Decision**：**保持** `handleRpcException` 统一返回 `code=500` + `data.errorType` 区分（timeout/network/...），不改为 504/403 等精确码。

**Rationale**：F-06 为低优先级；改变 HTTP 码属破坏性契约（下游可能按 500 做重试），宪法 II 要求稳定契约。仅在手册登记"errorType 为语义区分字段"。

**Alternatives considered**：映射精确码 → 破坏下游重试策略，收益低。

### D-09 `GXBusinessException` 子类在 `data` 为空时的降级

**Decision**：**保持现状**（`data == null` 时新建基类 `GXBusinessException` 并挂 traceId），但在手册登记"子类字段不保证保留"；如需保留子类，调用方应在构造时提供非空 `data`。

**Rationale**：F-07 为低优先级；改为反射复制子类字段属过度设计（违反附加约束 8）。

**Alternatives considered**：反射复制 → 复杂且脆弱。

### D-10 空值与日志门面

**Decision**：`GXDubboRpcApiBeanPostProcessor` 的 `@Log4j2` 改 `@Slf4j`；对可空返回（`getAttachment`/`getInvocationAttachment`/`getEnvironmentValue`/`getGenericClassType`/`getApplicationContext`）补 `@Nullable`（方法返回值与参数）；包级保持 `@NullUnmarked`（不改为 `@NullMarked`，避免一次性引入大量 NullAway 报错）。

**Rationale**：宪法 VII（SLF4J）与 V（JSpecify）；保持 `@NullUnmarked` 是与仓库其余模块一致的既有偏差收敛方式。

**Alternatives considered**：改 `@NullMarked` → 会触发全包 NullAway 报错，超出本次范围。

### D-11 文档与 allowlist

**Decision**：
1. 新增 `docs/leaf-base-dubbo-nacos开发手册.md`：模块定位、SPI 扩展名契约、TraceId 优先级表、异常分类矩阵、**强制装配配置**（`dubbo.provider.filter`）、`serialize.allowlist` 现状与维护要求、不支持范围（Sentinel 原生指标、服务发现端到端）。
2. `AGENTS.md` 第 5 节改为"模块已有 9 个测试类 / 31 条用例，新增改动必须在其基础上补齐"，并补强制装配配置。
3. `serialize.allowlist` 保持内容不变，仅补注释来源（若允许注释；否则在手册说明）。

**Rationale**：F-12/F-13/FR-024。

**Alternatives considered**：仅改 AGENTS.md → 使用方无手册可查。

### D-12 测试策略（离线优先，真实 Nacos 需申请）

**Decision**：
1. **真实 Dubbo 链**：用 `org.apache.dubbo.rpc.protocol.injvm` 或手工 `ProtocolFilterWrapper` 组装 Filter 链，覆盖"排除内置 `exception` → `GXBusinessException` 保真"与"不排除 → 被内置包装"两条对照。
2. **真实 Spring 上下文**：`@SpringBootTest(classes = TestApp.class)` + `@ComponentScan("cn.maple")` 验证两个 advice 的实际顺序与 `RpcException` 命中。
3. **异常分类矩阵**：单元测试用 `AppResponse` + mock `Invoker`/`Invocation`，覆盖 R-02 所列所有分支。
4. **并发**：静态 `appName` 缓存并发读、`resetAppNameCache()` 隔离。
5. **真实 Nacos**：仅当"注册中心服务发现下 Filter 激活顺序"必须端到端验证时，先向用户申请连接信息（FR-025）；默认关闭 Nacos 自动配置，缺环境跳过。

**Rationale**：FR-020/FR-023/FR-025；既有 31 条用例证明核心逻辑可离线验证。

**Alternatives considered**：全部依赖真实 Nacos → 违反 FR-025 且不可在 CI 稳定运行。

---

## 三、研究结论

- **C-02 由"未验证假设"升级为"已确认缺陷"**：R-02（字节码）+ R-03（示例配置）共同证明——不配置 `dubbo.provider.filter=-exception` 时，内置 `ExceptionFilter` 会把 `GXBusinessException` 包成普通 `RuntimeException`，业务 `code/msg/data` 丢失。F-01 在 `data-model.md` 中标记 `verified=true`。
- **F-02 已确认**：R-04 证明两个 advice 同时注册；是否需要 `@Order` 取决于 Spring 排序，属"不可依赖的巧合"。
- 所有 `NEEDS CLARIFICATION` 均已消解；无阻塞项。
