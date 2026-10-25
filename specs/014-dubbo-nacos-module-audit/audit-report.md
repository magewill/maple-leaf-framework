# 审查报告：leaf-base-dubbo-nacos 正确性、线程安全与性能

**Feature**: 014-dubbo-nacos-module-audit | **Date**: 2026-10-25
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Data Model**: [data-model.md](./data-model.md) | **Contracts**: [contracts/](./contracts/)

> 本报告是独立交付物（FR-014）。`spec.md` 只保留摘要与引用；两份文件在交付时相互一致。

---

## 1. 依赖与装配基线

| 项 | 值 | 证据 |
| --- | --- | --- |
| 模块 | `cn.maple.framework:leaf-base-dubbo-nacos:4.3.0-SNAPSHOT` | 根 `pom.xml` 第 26 行 |
| Dubbo | `dubbo:3.3.6`（经 `dubbo-spring-boot-starter:3.3.6`） | `dependency:tree` |
| Nacos client | `com.alibaba.nacos:nacos-client:3.1.1:compile`（经 `leaf-base-nacos`） | `dependency:tree` |
| 内置异常 Filter | `exception=org.apache.dubbo.rpc.filter.ExceptionFilter`（`@Activate(group=provider)`） | `javap` + jar 内 SPI |
| 生产装配 | `config/online-nacos-dubbo-demo.yml` 第 21 行 `filter: dubboExceptionFilter,-exception` | 源码 |
| 既有测试 | **31 执行 / 0 失败 / BUILD SUCCESS**（8 个测试类） | `mvn -o -pl leaf-base-dubbo-nacos test` |

---

## 2. 附录 A：上游语义摘录

| 上游类型 | 位置 | 事实 | 支撑 |
| --- | --- | --- | --- |
| `GXTraceIdContextUtils` | 第 18–59 行 | `getTraceId()` 对 null 返回 `""`；`getNullableTraceId()` 返回 null；`putTraceId(blank)`/`restoreTraceId(blank)` 均等价 `removeTraceId()` | P-OK-01、E-14 |
| `GXCommonUtils.getEnvironmentValue` | 第 99–190 行 | 每次调用 `GXSpringContextUtils.getEnvironment()`；Environment 为 null 返回默认值 | F-09、F-10、E-13 |
| `GXCommonUtils.getGenericClassType` | 第 716–724 行 | Hutool `ClassUtil.getTypeArgument`，不可解析返回 null | F-15、E-15 |
| `GXCommonUtils.reflectCallObjectMethod` | 第 383–429 行 | 目标 null/方法缺失/调用异常 → 返回 null（不抛） | F-15 |
| `GXSpringContextUtils` | 第 24–26、45–62、133–144 行 | `getApplicationContext()` 读静态单例；`getBean` 失败返回 null | F-08、F-09 |
| `GXExceptionHandler` | 第 42、226–231、305–317 行 | `@RestControllerAdvice`；兜底 `@ExceptionHandler(Exception.class)`；`exceptionNotify` null-safe | F-02、E-06 |
| `GXSentinelFlowException` | 第 16–82 行 | `code/msg/data` 三字段 + `getCode()`/`getMsg()`/`getData()` | F-03、E-09 |
| `GXEnableLeafFramework` | 第 12–20 行 | `@ComponentScan(value={"cn.maple"})` | F-02、E-07 |

---

## 3. 附录 B：保留断言清单（P-OK，一条不删）

| ID | 契约 | 锁定用例 |
| --- | --- | --- |
| P-OK-01 | TraceId Filter 的 `finally` 恢复 + 异步二次捕获/恢复，MDC 不泄漏 | `GXDubboTraceIdFilterTest`：`clientFilterPropagatesInvocationTraceIdAndRestoresOriginalTraceId`、`serverFilterUsesServerAttachmentAndRestoresTraceIdWhenInvokerThrows`、`clientFilterGeneratesTraceIdWhenAllSourcesAreEmpty`、`nestedServerThenClientCallKeepsSameTraceIdAcrossServices`、`clientFilterAttachesTraceIdWhenAsyncResultCompletes` |
| P-OK-02 | `buildErrorData` 不泄露参数值 | `GXDubboExceptionFilterTest#convertsSentinelFlowExceptionByMessageWithoutLeakingArguments` |
| P-OK-03 | `GXBusinessException`/声明/checked/JDK/jakarta/`RpcException`/MyBatis/`GenericService` 分类 | `GXDubboExceptionFilterTest`（11 条） |
| P-OK-04 | Selector 只透传 TraceId、优先级、null 安全 | `GXPenetrateAttachmentSelectorTest`（5 条） |
| P-OK-05 | BeanPostProcessor 跳过/成功分支、返回原 bean | `GXDubboRpcApiBeanPostProcessorTest`（4 条）+ `...SpringBootTest`（1 条） |
| P-OK-06 | 既有 31 条全绿 | 基线命令 |

---

## 4. 附录 C：命令与输出

```powershell
mvn -o -pl leaf-base-dubbo-nacos dependency:tree "-Dincludes=com.alibaba.nacos:*,org.apache.dubbo:*"
# leaf-base-nacos:jar:4.3.0-SNAPSHOT:compile
#   \- com.alibaba.nacos:nacos-client:jar:3.1.1:compile
# org.apache.dubbo:dubbo-spring-boot-starter:jar:3.3.6:compile
#   \- org.apache.dubbo:dubbo:jar:3.3.6:compile
# org.apache.dubbo:dubbo-nacos-spring-boot-starter:jar:3.3.6:compile
```

```powershell
javap -v -cp "F:\repository\org\apache\dubbo\dubbo\3.3.6\dubbo-3.3.6.jar" org.apache.dubbo.rpc.filter.ExceptionFilter
# implements org.apache.dubbo.rpc.Filter, org.apache.dubbo.rpc.BaseFilter$Listener
# 常量池: Lorg/apache/dubbo/common/extension/Activate; group provider
# 字节码: 未声明运行时异常 -> new RuntimeException(StringUtils.toString(e)) -> setException
```

```powershell
# jar 内 META-INF/dubbo/internal/org.apache.dubbo.rpc.Filter
# exception=org.apache.dubbo.rpc.filter.ExceptionFilter

Select-String -Path "config\online-nacos-dubbo-demo.yml" -Pattern "filter"
# 21:    filter: dubboExceptionFilter,-exception
```

```powershell
mvn -o -pl leaf-base-dubbo-nacos test
# Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS
```

---

## 5. 证据（E-01 ~ E-20）

| ID | 类型 | 定位 | 事实 | verified |
| --- | --- | --- | --- | --- |
| E-01 | SOURCE | `GXDubboExceptionFilter.java#28` | `@Activate(group=PROVIDER)`，继承 `ExceptionFilter` | ✅ |
| E-02 | COMMAND | `javap ... ExceptionFilter` | 内置 filter `@Activate(group=provider)`，未声明运行时异常被包成 `RuntimeException` | ✅ |
| E-03 | SOURCE | jar SPI | `exception=org.apache.dubbo.rpc.filter.ExceptionFilter` | ✅ |
| E-04 | SOURCE | `config/online-nacos-dubbo-demo.yml#21` | `filter: dubboExceptionFilter,-exception`（唯一排除配置） | ✅ |
| E-05 | SOURCE | `GXDubboExceptionFilter.java#61-64` | `GXBusinessException` 透传依赖自定义 filter 生效且内置已排除 | ✅ |
| E-06 | SOURCE | `GXExceptionHandler.java#42,226-231` | 基础 advice 含兜底 `Exception` handler | ✅ |
| E-07 | SOURCE | `GXEnableLeafFramework.java#12-20` | `@ComponentScan("cn.maple")` | ✅ |
| E-08 | SOURCE | `GXDubboCallExceptionHandler.java#14` | `@RestControllerAdvice`，无 `@Order` | ✅ |
| E-09 | SOURCE | `GXDubboExceptionFilter.java#105-119` | Sentinel 精确类名 + 消息 `contains("FlowException")` | ✅ |
| E-10 | SOURCE | `GXDubboExceptionFilter.java#66-77` | `NoSuchMethodException` → `return` | ✅ |
| E-11 | SOURCE | `GXDubboExceptionFilter.java#97-101` | 外层 `catch(Throwable)` 仅 WARN | ✅ |
| E-12 | SOURCE | `GXPenetrateAttachmentSelector.java#26,118-128` | 静态 `volatile appName` + 惰性读改写 | ✅ |
| E-13 | SOURCE | `GXDubboClientTraceIdFilter.java#31`、`GXDubboServerTraceIdFilter.java#31` | 每请求 `getEnvironmentValue` | ✅ |
| E-14 | SOURCE | `GXTraceIdContextUtils.java#18-59` | MDC 语义（见附录 A） | ✅ |
| E-15 | SOURCE | `GXCommonUtils.java#99-190,716-724,395-429` | 工具类失败语义 | ✅ |
| E-16 | SOURCE | `security/serialize.allowlist#1` | 仅 `HashBasedTable` | ✅ |
| E-17 | SOURCE | `GXDubboRpcApiBeanPostProcessor.java#20` | `@Log4j2` | ✅ |
| E-18 | SOURCE | `AGENTS.md#89` | 声称"没有现成的 src/test 测试目录" | ✅ |
| E-19 | COMMAND | `mvn test` | 31 / 0 失败 | ✅ |
| E-20 | SOURCE | `GXDubboCallExceptionHandlerSpringBootTest.java#25-27` | `standaloneSetup`，非真实 Spring Boot 上下文 | ✅ |

---

## 6. 发现（F-01 ~ F-16）

> 完整八要素见 [data-model.md](./data-model.md) §1.1；此处给出级别、位置、触发条件、影响、复现与回归设计摘要。

| ID | 级别 | 位置 | 触发条件 | 影响 | 复现 | 回归设计 | 批次 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | HIGH | `GXDubboExceptionFilter.java#28` / `config/online-nacos-dubbo-demo.yml#21` | provider 未配置 `dubbo.provider.filter`，或配置中未含 `-exception`，且 provider 方法抛出未声明的 `GXBusinessException` | 内置 filter 把异常包成 `RuntimeException`，业务 `code/msg/data` 丢失 | `GXDubboFilterChainBehaviorTest#businessExceptionLosesCodeWhenBuiltInFilterActive` | `#businessExceptionPreservedWhenBuiltInFilterExcluded` + 启动 ERROR 校验 | B1 |
| F-02 | HIGH | `GXDubboCallExceptionHandler.java#14-27` / `GXExceptionHandler.java#42,226` | 组件扫描同时加载基础 `GXExceptionHandler` 与本模块 advice；请求抛出 `RpcException`/`GXSentinelFlowException`；基础 advice 先被匹配 | 业务/限流异常被兜底为"系统内部错误"，`data.errorType` 丢失 | `GXDubboAuditReproTest#f02AdviceShadowedByBaseHandler` | `GXDubboCallExceptionHandlerSpringBootTest#rpcExceptionAdviceHandlesRealMvcRequest`（双 advice 注册） | B1 |
| F-03 | MEDIUM | `GXDubboExceptionFilter.java#105-119` | provider 抛出 Sentinel `DegradeException`/`AuthorityException` 等非 Flow/Block 类型；或普通异常消息含 "FlowException" | 漏转或误判，客户端看到错误的异常分类 | `GXDubboAuditReproTest#f03SentinelDegradeNotConverted` | `GXDubboExceptionFilterTest#convertsDegradeAndAuthorityExceptions` / `#doesNotMisclassifyBusinessMessageContainingFlowException` | B2 |
| F-04 | MEDIUM | `GXDubboExceptionFilter.java#66-77` | `invocation.getParameterTypes()` 与实际方法签名不一致（泛型擦除/重载/原始类型）且抛出未声明运行时异常 | 异常直接透传，provider 内部细节泄露 | `GXDubboAuditReproTest#f04SignatureUnresolvableFailsOpen` | `GXDubboExceptionFilterTest#wrapsUndeclaredRuntimeWhenMethodSignatureUnresolvable` | B2 |
| F-05 | MEDIUM | `GXDubboExceptionFilter.java#97-101` | filter 自身逻辑抛异常（如 `invoker.getInterface()` 为 null、`getMethod` 抛非 `NoSuchMethodException`） | 仅 WARN、无计数，问题不可观测 | —（构造坏 invoker） | `GXDubboExceptionFilterTest#recordsFilterInternalFailure` | B2 |
| F-06 | LOW | `GXDubboCallExceptionHandler.java#17-21` | consumer 收到非超时类 `RpcException`（网络/序列化/限流等） | 统一 500，仅 `errorType` 区分（按风险接受，D-08） | — | `GXDubboCallExceptionHandlerTest#rpcExceptionSubtypesKeepStableCode` | B2 |
| F-07 | LOW | `GXDubboExceptionFilter.java#142-154` | provider 抛出 `GXBusinessException` 子类且 `data == null` | 子类语义降级为基类（按风险接受，D-09） | — | 文档登记 | B2 |
| F-08 | MEDIUM | `GXPenetrateAttachmentSelector.java#26,118-128` | 同一 JVM 多个 Spring 上下文；或并发首次调用 `getAppName()` | 静态 `appName` 串用/重复计算 | — | `GXPenetrateAttachmentSelectorTest#appNameCacheIsolatesAcrossReset` | B3 |
| F-09 | MEDIUM | 两个 TraceId Filter `#31` | 每请求进入 TraceId Filter（热路径） | 每请求读 Spring 上下文单例，成本固定 | — | `GXDubboTraceIdFilterTest#filterReusesSharedAppNameCache` | B4 |
| F-10 | MEDIUM | 两个 TraceId Filter `#32` | 每请求进入 TraceId Filter 且日志级别为 INFO（默认） | 高 QPS 下 INFO 刷屏 | — | `GXDubboTraceIdFilterTest#traceIdLoggingIsDebugLevel` | B4 |
| F-11 | LOW | 两个 TraceId Filter `#50-70` | 每请求解析 TraceId，且 attachment 多来源 | 多路双读、无解析缓存 | — | `GXDubboTraceIdFilterTest#resolveTraceIdKeepsClientCustomOrder` / `#resolveTraceIdKeepsServerCustomOrder` | B4 |
| F-12 | MEDIUM | `security/serialize.allowlist#1` | 新增跨进程传输对象触发 Dubbo 序列化安全校验 | 白名单无来源/影响说明 | — | 手册登记 | B5 |
| F-13 | LOW | `docs/`、`AGENTS.md#89` | 使用方按文档/AGENTS 排查或配置测试 | 缺开发手册；文档漂移 | `GXDubboAuditReproTest#f13AgentsDocClaimsNoTests` | 手册 + AGENTS 修订 | B5 |
| F-14 | LOW | `GXDubboRpcApiBeanPostProcessor.java#20` | 启动/运行 `GXDubboRpcApiBeanPostProcessor` 输出日志 | `@Log4j2` 违反 SLF4J 门面 | `Select-String @Log4j2` | `AsciiSourceGuardTest` + 静态检查 | B5 |
| F-15 | LOW | 4 个 `package-info.java` | NullAway 构建检查 / IDE 空值提示 | 空值契约零 `@Nullable` | — | 编译（NullAway）+ 代码审查 | B5 |
| F-16 | LOW | `GXDubboCallExceptionHandlerSpringBootTest.java#25-27` | 依赖既有测试判断 advice 排序正确性 | 名为 SpringBootTest 实为 standalone，未验证双 advice | — | 真实双 advice 用例 | B6 |

### 6.1 逐文件审查判定（FR-001）

**生产源码（审查时 10 个；修复后新增 2 个）**

| # | 文件 | 判定 | 依据 |
| --- | --- | --- | --- |
| 1 | `filter/GXDubboClientTraceIdFilter.java` | 风险（已修复） | F-09/F-10/F-11 |
| 2 | `filter/GXDubboServerTraceIdFilter.java` | 风险（已修复） | F-09/F-10/F-11 |
| 3 | `filter/GXDubboExceptionFilter.java` | 缺陷（已修复/接受） | F-01/F-03/F-04/F-05/F-07 |
| 4 | `filter/package-info.java` | 无缺陷 | 空值策略声明存在 |
| 5 | `handler/GXDubboCallExceptionHandler.java` | 缺陷（已修复/接受） | F-02/F-06 |
| 6 | `handler/package-info.java` | 无缺陷 | — |
| 7 | `processor/GXDubboRpcApiBeanPostProcessor.java` | 风险（已修复） | F-14 |
| 8 | `processor/package-info.java` | 无缺陷 | — |
| 9 | `selector/GXPenetrateAttachmentSelector.java` | 风险（已修复） | F-08 |
| 10 | `selector/package-info.java` | 无缺陷 | — |
| +1 | `config/GXDubboProviderFilterValidator.java` | 新增，无缺陷 | 修复 F-01 |
| +2 | `config/package-info.java` | 新增，无缺陷 | 宪法 V/VII |

**运行时资源（3 个）**

| # | 文件 | 判定 | 依据 |
| --- | --- | --- | --- |
| 1 | `META-INF/dubbo/org.apache.dubbo.rpc.Filter` | 无缺陷（扩展名契约） | 3 个扩展名 |
| 2 | `META-INF/dubbo/org.apache.dubbo.rpc.PenetrateAttachmentSelector` | 无缺陷 | 1 个扩展名 |
| 3 | `security/serialize.allowlist` | 风险（已登记） | F-12 |

**既有测试（9 个）**

| # | 文件 | 判定 | 依据 |
| --- | --- | --- | --- |
| 1 | `AsciiSourceGuardTest` | 无缺陷 | — |
| 2 | `filter/GXDubboExceptionFilterTest` | 风险（已补强） | F-03/F-04/F-05 覆盖缺口 |
| 3 | `filter/GXDubboTraceIdFilterTest` | 风险（已补强） | F-08/F-09/F-10/F-11 覆盖缺口 |
| 4 | `handler/GXDubboCallExceptionHandlerTest` | 无缺陷 | — |
| 5 | `handler/GXDubboCallExceptionHandlerSpringBootTest` | 缺陷（已改造） | F-16 |
| 6 | `processor/GXDubboRpcApiBeanPostProcessorTest` | 无缺陷 | — |
| 7 | `processor/GXDubboRpcApiBeanPostProcessorSpringBootTest` | 无缺陷 | — |
| 8 | `selector/GXPenetrateAttachmentSelectorTest` | 无缺陷（已补强） | — |
| 9 | `org/mybatis/spring/MyBatisSystemException`（替身） | 无缺陷 | 测试专用 |

**新增测试（6 个）**：`GXDubboAuditReproTest`、`GXDubboFilterChainBehaviorTest`、`GXDubboProviderFilterValidatorTest`、`GXDubboTestApp`、`GXDubboTestSupport`、Sentinel 替身 2 个（`DegradeException`/`AuthorityException`）。

### 6.2 FR-016 上游范围例外

`GXCommonUtils.getEnvironmentValue(String, Class)`、`GXCommonUtils.getGenericClassType(Class<?>, Integer)`、`GXSpringContextUtils.getApplicationContext()` 位于 `leaf-base-framework`，属于本模块的上游依赖。按 `AGENTS.md` §4.1 与宪法 §I（模块自治）约束，本次**不跨模块修改**；本模块自身可空返回（`getAttachment`/`getInvocationAttachment`）已补 `@Nullable`。若后续需要为上述上游方法收敛 `@Nullable`，应另立 `leaf-base-framework` 变更单并在框架模块内验证 NullAway。

### 6.3 FR-006 真实上下文等价实现

`GXDubboTestApp` 使用 `@EnableWebMvc` + `@Import({GXExceptionHandler, GXDubboCallExceptionHandler})`，而非 `@ComponentScan("cn.maple")`。原因：全量组件扫描会加载 datasource/nacos 等无关自动配置，违反离线测试约束（FR-025）。等价性：两个 advice 均作为 Spring Bean 注册到真实 `WebApplicationContext`，由真实的 `ExceptionHandlerExceptionResolver` 按 `@Order` 排序并匹配，`GXDubboCallExceptionHandlerSpringBootTest` 同时断言两个 advice Bean 存在且 `RpcException` 命中专用 handler。

---

## 7. 矩阵

- **异常分类矩阵**：见 [data-model.md](./data-model.md) §3（异常类型 × 处置 × 业务字段 × 日志 × TraceId）。
- **Filter 装配矩阵**：见 [data-model.md](./data-model.md) §3（内置/自定义 filter × `GXBusinessException` 结果）。
- **TraceId 优先级链**：见 [data-model.md](./data-model.md) §4。
- **共享状态清单**：见 [data-model.md](./data-model.md) §5。
- **SPI 契约表**：见 [data-model.md](./data-model.md) §7。

---

## 8. 故障现场（旧代码失败证据）

首次运行 `mvn -o -pl leaf-base-dubbo-nacos test "-Dtest=GXDubboAuditReproTest"`（修复前）：

```text
Tests run: 5, Failures: 4, Errors: 0, Skipped: 0
- f02AdviceShadowedByBaseHandler: Dubbo advice must declare @Order ... ==> expected: not <null>
- f03SentinelDegradeNotConverted: expected <GXSentinelFlowException> but was <GXBusinessException>
- f04SignatureUnresolvableFailsOpen: expected <GXBusinessException> but was <org.springframework.beans.FatalBeanException>
- f13AgentsDocClaimsNoTests: expected <false> but was <true>
```

说明：`f01BusinessExceptionWrappedWhenBuiltInFilterActive` 是**刻画性用例**（记录内置 filter 的既有行为），不作为红转绿断言；F-01 的红转绿证据是 `GXDubboFilterChainBehaviorTest` 与 `GXDubboProviderFilterValidatorTest`。

修复后：`GXDubboAuditReproTest` 5/5 通过。

---

## 9. 性能口径

不设吞吐/时延门禁（SC-005），只保留可复算口径。

| 指标 | 修复前（每请求） | 修复后目标 | 复算方式 |
| --- | --- | --- | --- |
| `GXCommonUtils.getEnvironmentValue` | 1（每个 TraceId Filter） | 0（首请求后读静态缓存） | `GXDubboTraceIdFilterTest#filterReusesSharedAppNameCache`（MockedStatic 计数） |
| `getApplicationContext()`（热路径） | ≥1 | 0（缓存命中后） | 同上 |
| INFO 日志（TraceId Filter） | 1 | 0（降 DEBUG） | `#traceIdLoggingIsDebugLevel`（源码断言：无 `log.info`） |
| attachment 读取 | 每来源 ≤2 | 保持 ≤2 | 代码审查 + mock 计数 |
| `getBean`/反射（异常路径） | 0 | 0 | 代码审查 |
| 测试墙钟 | 31 条约 5s | 59 条约 5s | `mvn` 输出 |

> 说明：`getCachedAppName()` 每请求被调用一次，但仅在首次（或 `UnknownApp`）时访问 Environment，之后为 `volatile` 读。

---

## 10. 兼容声明

| 变更 | 影响面 | 迁移路径 |
| --- | --- | --- |
| 未排除内置 `exception` 时启动 ERROR | 未配置 `dubbo.provider.filter` 的下游 | 配置 `dubboExceptionFilter,-exception`；仅告警不阻断 |
| `GXDubboCallExceptionHandler` 增加 `@Order(HIGHEST_PRECEDENCE)` | 依赖基础 advice 兜底处理 `RpcException` 的下游 | `RpcException` 改由专用 handler 返回结构化 `data.errorType` |
| TraceId Filter INFO → DEBUG | 依赖 INFO 日志观测 TraceId 的下游 | 需要时开 DEBUG |
| Sentinel 识别扩大到全部 `BlockException` 子类 | 之前被误判的 Degrade/Authority 等 | 返回 `GXSentinelFlowException(406)` |
| 静态 `appName` 缓存新增 `resetAppNameCache()` | 多 Spring 上下文 | 上下文刷新/测试隔离时调用 |
| `@Log4j2` → `@Slf4j` | 日志门面 | 输出目标不变 |

---

## 11. 验证记录

### 修复前基线（2026-10-25）

```
mvn -o -pl leaf-base-dubbo-nacos test
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 修复后（2026-10-25）

```text
mvn -o -pl leaf-base-dubbo-nacos clean test
Tests run: 59, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```text
mvn -o -pl leaf-base-dubbo-nacos clean install -DskipTests
BUILD SUCCESS          # ErrorProne + NullAway 零错误
```

用例分布（11 个测试类，31 → 59，+28）：

| 测试类 | 用例数 | 说明 |
| --- | --- | --- |
| `AsciiSourceGuardTest` | 1 | 保留 |
| `GXDubboExceptionFilterTest` | 15 | 保留 11 + 新增 4（Sentinel 全类型/误判/反射失败/计数） |
| `GXDubboTraceIdFilterTest` | 11 | 保留 5 + 新增 6（优先级/异常恢复/异步/共享缓存/DEBUG/解析链） |
| `GXDubboCallExceptionHandlerTest` | 3 | 保留 2 + 新增 1（`RpcException` 9 子类矩阵） |
| `GXDubboCallExceptionHandlerSpringBootTest` | 3 | 改造为真实 `@SpringBootTest` + 双 advice（保留 2 + 新增 1） |
| `GXDubboRpcApiBeanPostProcessorTest` | 5 | 保留 4 + 新增 1（反射失败分支） |
| `GXDubboRpcApiBeanPostProcessorSpringBootTest` | 1 | 保留 |
| `GXPenetrateAttachmentSelectorTest` | 9 | 保留 5 + 新增 4（响应优先级/空值/缓存隔离/并发） |
| `GXDubboFilterChainBehaviorTest` | 2 | 新增（内置 filter 对照） |
| `GXDubboAuditReproTest` | 5 | 新增（F-01/F-02/F-03/F-04/F-13 复现） |
| `GXDubboProviderFilterValidatorTest` | 4 | 新增（装配校验三态 + 不阻断） |

> 保留断言：P-OK-01 ~ P-OK-06 全部保留（见附录 B）。

## 12. 后续任务与维护

| 项 | 说明 |
| --- | --- |
| 真实 Nacos 端到端 | 仅当需要验证"注册中心服务发现下 Filter 激活顺序"时执行；先向维护者申请连接信息（FR-025） |
| `RpcException` 精确状态码 | F-06 按风险接受（D-08），如需 504/403 等映射，另立破坏性变更单 |
| `GXBusinessException` 子类字段保留 | F-07 按风险接受（D-09），调用方需提供非空 `data` |
| `serialize.allowlist` 扩展 | 新增跨进程对象触发校验时，按手册 §8 评估并登记 |
| F-16 测试命名 | 新增 `GXDubboFilterChainBehaviorTest` 明确为行为测试，不再使用误导性的 `SpringBootTest` 后缀 |
