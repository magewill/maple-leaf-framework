# Implementation Plan: leaf-base-dubbo-nacos 正确性、线程安全与性能审查

**Branch**: `014-dubbo-nacos-module-audit`（`before_plan` 未注册 hook，未创建 git 分支） | **Date**: 2026-10-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-dubbo-nacos-module-audit/spec.md`

## Summary

把 **16 条审查发现（高 2 / 中 7 / 低 7）** 与 **6 条既定契约（P-OK-01 ~ P-OK-06）** 落成"可实测、可回归、可迁移"的交付：**异常装配与业务码保真 + 异常分类 fail-closed + 上下文与共享状态收敛 + 性能与可观测 + 契约与安全资源登记 + 测试基线补强 + 审查报告**。

技术路径（Phase 0 已逐条确认，详见 [research.md](./research.md)）：

1. **异常装配与业务码保真**（F-01、F-02，FR-005、FR-006）：`GXDubboExceptionFilter` 与 Dubbo 内置 `ExceptionFilter` 均为 `@Activate(group=provider)`；实测 `config/online-nacos-dubbo-demo.yml` 第 21 行以 `dubbo.provider.filter: dubboExceptionFilter,-exception` 排除内置 filter，但**该关键装配要求未写入模块文档**。方案：① 在 `GXDubboExceptionFilter` 所在模块新增**启动期配置校验器**（`GXDubboProviderFilterValidator`，实现 `ApplicationContextInitializer` 或 `SmartInitializingSingleton`），当检测到 provider filter 未排除内置 `exception` 时打 **ERROR** 并给出修复指引（`dubbo.provider.filter=dubboExceptionFilter,-exception`）；② 在 `docs/leaf-base-dubbo-nacos开发手册.md` 与 `AGENTS.md` 中把该配置登记为**强制装配契约**。`GXDubboCallExceptionHandler` 补 `@Order(Ordered.HIGHEST_PRECEDENCE)`，确保 `RpcException`/`GXSentinelFlowException` 的专用 handler 先于继承自 `GXExceptionHandler` 的兜底 `Exception` handler 被匹配。
2. **异常分类 fail-closed**（F-03、F-04、F-05，FR-007、FR-008、FR-009）：Sentinel 识别从"精确类名 + 消息含 FlowException"改为"`com.alibaba.csp.sentinel.slots.block.` 前缀 + `BlockException` 可赋值判定"（不新增 Sentinel 依赖，仍走类名/反射）；`getMethod` 反射失败时**不再 return**，落入"未声明运行时异常 → 包装为 `GXBusinessException`"；`onResponse` 外层 `catch(Throwable)` 保留兜底但必须记录 **WARN + 计数**，不得静默。
3. **上下文与共享状态**（F-08、F-09，FR-010）：`GXPenetrateAttachmentSelector` 的静态 `appName` 与两个 TraceId Filter 的每请求 `getEnvironmentValue` 收敛为**同一份可重置的静态缓存**（`volatile` + 显式 `resetAppNameCache()` 供测试/上下文刷新），并保证读写可见性；`GXCommonUtils.getEnvironmentValue` 调用从 TraceId Filter 热路径移除（改为读取共享缓存）。
4. **性能与可观测**（F-10、F-11，FR-011）：两个 TraceId Filter 的 `log.info` 改为 `log.debug`（或 `if (log.isDebugEnabled())` 守卫），避免每请求 INFO 刷屏；`resolveTraceId` 保持既有优先级链但**合并双读**（`getObjectAttachment` 为 null 时才读 `getAttachment`，已是现状，补充说明并避免重复 `Objects.toString`）。
5. **契约与安全资源**（F-12、F-13、F-14、F-15，FR-013、FR-016、FR-017、FR-024）：`@Log4j2` 改 `@Slf4j`；可空返回补 `@Nullable`；新增 `docs/leaf-base-dubbo-nacos开发手册.md`（模块定位、SPI 扩展名、TraceId 优先级表、异常分类矩阵、**强制装配配置**、`serialize.allowlist` 说明与不支持范围），修正 `AGENTS.md` 第 5 节与实现漂移。
6. **测试基线补强**（F-16，FR-020 ~ FR-023、FR-025）：新增 **真实 Dubbo 链**（`injvm`/直连，不依赖 Nacos 注册中心）验证"排除内置 filter 后 `GXBusinessException` 保真 / 不排除时被二次包装"两条对照用例；新增**真实 `@SpringBootTest`（组件扫描）** 验证 Dubbo 专用 advice 优先级；新增并发用例（静态 appName）、异常分类矩阵用例、空值/边界用例。既有 31 条断言全部保留，用例数只增不减。

## Technical Context

**Language/Version**: Java 21（根 POM `<java.version>21</java.version>`、`maven-compiler-plugin` `<release>21</release>`；ErrorProne 2.50.0 + NullAway 0.13.4，`-Xep:NullAway:ERROR -XepOpt:NullAway:AnnotatedPackages=cn.maple -XepOpt:NullAway:JSpecifyMode=true`）；Windows 本机实测可离线编译与测试

**Primary Dependencies**: Spring Boot 4.1.1 / Spring Framework 7.0.9；`leaf-base-framework 4.3.0-SNAPSHOT`（`GXTraceIdContextUtils`、`GXCommonUtils`、`GXSpringContextUtils`、`GXExceptionHandler`、`GXBusinessException`、`GXSentinelFlowException`、`GXResultUtils`）；`leaf-base-nacos 4.3.0-SNAPSHOT`（传递 `nacos-client 3.1.1:compile`）；`dubbo-spring-boot-starter 3.3.6`（传递 `dubbo 3.3.6`、`hessian-lite 4.0.4`）；`dubbo-nacos-spring-boot-starter 3.3.6`；`fastjson ${fastjson.version}`；`javassist ${javassist.version}`；Hutool 5.8.47；jspecify 1.0.1；Lombok。**零新增依赖**（Sentinel 识别继续用类名/反射，不引入 `sentinel-core`）

**Storage**: N/A（库模块自身不持久化；不承载 Nacos 配置存储）

**Testing**: JUnit 5（Jupiter）+ `spring-boot-starter-test`；**真实 Dubbo 链**用 `injvm://` 或直连 `dubbo://`（不依赖注册中心），或 `ProtocolFilterWrapper` 手工组装 Filter 链；**真实 Spring 容器**用 `@SpringBootTest` + `@ComponentScan("cn.maple")`（验证 advice 注册与排序）；并发用 `ExecutorService` + `CountDownLatch`；异常分类现场用可注入的 `Invoker`/`Result`/`Invocation` 替身（`AppResponse`、`RpcInvocation`）。**测试默认离线**：`spring.cloud.nacos.config.import-check.enabled=false`、`spring.cloud.nacos.discovery.enabled=false`；**确需真实 Nacos**（注册中心服务发现端到端）时先向用户申请连接信息（FR-025），缺少环境时用例优雅跳过

**Target Platform**: JDK 21 库（jar，随 `leaf-framework` 聚合门面下发）；Windows（CRLF）与 Linux（LF）行为一致

**Project Type**: library（Maven 多模块仓库中的单个 `leaf-base-*` 模块）

**Performance Goals**: 不设吞吐/时延验收门禁（SC-005），只收敛可复算口径：每请求 `getEnvironmentValue` 次数 **1 → 0**（改为共享静态缓存）、每请求 INFO 日志 **1 → 0**（降 DEBUG）、每请求 `getBean`/`getApplicationContext` 次数 **1 → 0**（热路径）、`resolveTraceId` 的 attachment 读取次数保持"每来源最多 2 次"且不新增。**工程目标（非门禁）**：既有 31 条用例墙钟保持在秒级。

**Constraints**: 只改 `leaf-base-dubbo-nacos` 模块 + 模块文档（`docs/leaf-base-dubbo-nacos开发手册.md` 新增、`AGENTS.md` 修订）；零新增依赖；默认行为与 SPI 扩展名保持不变（`dubboExceptionFilter` / `dubboClientTraceIdFilter` / `dubboServerTraceIdFilter` / `dubboPenetrateAttachmentSelector`）；ErrorProne + NullAway 零错误；日志与断言消息 ASCII；`serialize.allowlist` 语义不变（仅补说明）；测试默认离线

**Scale/Scope**: 10 个生产源码文件（审查覆盖 100%）+ 3 个运行时资源；16 条发现；预计修改 6 个生产类型（`GXDubboClientTraceIdFilter`、`GXDubboServerTraceIdFilter`、`GXDubboExceptionFilter`、`GXDubboCallExceptionHandler`、`GXDubboRpcApiBeanPostProcessor`、`GXPenetrateAttachmentSelector`）+ 新增 1 个校验器（`GXDubboProviderFilterValidator`）+ 4 个 `package-info` 补 `@Nullable` 说明；新增 1 份开发手册、修订 `AGENTS.md`；新增 3~4 个测试类、修改若干既有测试类

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 依据 / 约束 |
| --- | --- | --- |
| I. 模块自治与按需装配 | PASS | 仍只依赖 `leaf-base-framework` + `leaf-base-nacos` + Dubbo starter；零新增依赖；新增校验器用 Spring 生命周期接口（`SmartInitializingSingleton`/`ApplicationContextInitializer`），不引入中间件 |
| II. 稳定契约优先（NON-NEGOTIABLE） | PASS（附兼容声明） | SPI 扩展名、`--exception` 装配方式、`serialize.allowlist`、公开方法签名全部保留；**唯一行为修正**是"未排除内置 filter 时输出 ERROR 告警"（新增可观测信号，不改变成功路径）；`GXDubboCallExceptionHandler` 的 `@Order` 只改变 handler 选中顺序（把原本可能被兜底的异常交回专用 handler），属缺陷修复；逐条见下方兼容性声明表 |
| III. 统一响应与统一异常 | PASS（附边界说明） | `GXDubboCallExceptionHandler` 仍以 `GXResultUtils.error(...)` 返回；`handleRpcException` 的 500 语义保持不变（F-06 只作为"低优先级可选优化"，不强制改 HTTP 码，避免破坏既有下游契约）；`GXBusinessException` 继续作为唯一业务异常载体；不新增异常类型 |
| IV. 显式配置与双配置源 | PASS | 不新增配置键（`dubbo.provider.filter` 是 Dubbo 既有键，只在文档登记）；`serialize.allowlist` 不变；无 `@Value` 成组配置 |
| V. 空值安全与上下文纪律 | PASS | 对可空返回补 `@Nullable`（`getAttachment`/`getInvocationAttachment`/`getEnvironmentValue`/`getGenericClassType`/`getApplicationContext`）；MDC 的 `put/restore` 成对语义**保持不变**（P-OK-01）；新增静态 `appName` 缓存用 `volatile` + 显式重置，不做无界 ThreadLocal |
| VI. 测试与静态检查门槛 | PASS | 每个修复项先给"旧代码失败"用例（FR-021）；新增真实 Dubbo 链与真实 `@SpringBootTest` 用例（FR-020）；不改编译参数；`mvn -pl leaf-base-dubbo-nacos test` 须全绿且用例数从 31 单调增长 |
| VII. 命名与包结构一致性 | PASS | 新增 `GXDubboProviderFilterValidator`（`GX<概念>Validator`，与宪法 VII 后缀表既有角色一致）；`@Log4j2` 改 `@Slf4j`；日志 ASCII；不重命名历史类型 |
| 附加约束 2/3（依赖治理） | PASS | 零新增依赖；Sentinel 识别用类名/反射而非 `sentinel-core`；不重复造轮子（复用 `GXSpringContextUtils`、`GXTraceIdContextUtils`） |
| 附加约束 8（YAGNI） | PASS（附说明） | 新增校验器是"防止业务码静默丢失"的必要防护（F-01 的直接后果），非投机抽象；`@Order` 为一行改动；不新增缓存层/中间层 |
| 附加约束 9（能力完备性） | PASS（附说明） | Sentinel 阻断类型覆盖从 2 类扩到全部 `BlockException` 子类；不支持范围（如需 Sentinel 原生指标）在手册登记 |

**Post-Phase 1 复评**：`contracts/` 三份契约（异常分类、TraceId 上下文、配置与可观测）与 `data-model.md` 未引入新的原则冲突。新增的 `GXDubboProviderFilterValidator` 必要性已在 Complexity Tracking 说明；未引入跨模块改动。

**兼容性声明（非违反项，需在 PR 与手册中体现）**：

| 变更 | 影响面 | 迁移路径 |
| --- | --- | --- |
| 未排除内置 `exception` filter 时启动输出 ERROR | 未配置 `dubbo.provider.filter` 的下游（当前默认） | 配置 `dubbo.provider.filter=dubboExceptionFilter,-exception`；校验器只告警不阻断启动（避免破坏存量） |
| `GXDubboCallExceptionHandler` 增加 `@Order(HIGHEST_PRECEDENCE)` | 依赖"基础 advice 兜底"处理 `RpcException` 的下游 | 行为修正：`RpcException` 改由专用 handler 返回结构化 `data.errorType`；如确需旧行为，可移除该 advice 或自定义 `@Order` |
| TraceId Filter 的 INFO 日志降为 DEBUG | 依赖 INFO 日志观测 TraceId 透传的运维/排障 | 需要时开 DEBUG；关键信息改由 `data-model.md` 指标口径承载 |
| Sentinel 识别扩大到全部 `BlockException` 子类 | 之前被误判为普通业务异常的 Degrade/Authority 等 | 行为修正：这些异常现在返回 `GXSentinelFlowException`（code=406）；如有依赖旧行为的调用方需按新语义调整 |
| 静态 `appName` 缓存新增显式重置入口 | 多 Spring 上下文（测试/热部署）场景 | 上下文刷新/测试隔离时调用 `resetAppNameCache()`；生产单上下文无感 |
| `@Log4j2` → `@Slf4j` | 日志门面切换 | 日志 API 由 Log4j2 改为 SLF4J，输出目标不变；如有自定义 Log4j2 配置需确认 appender 仍生效 |

## Project Structure

### Documentation (this feature)

```text
specs/014-dubbo-nacos-module-audit/
├── plan.md                          # 本文件
├── research.md                      # Phase 0：R-01 ~ R-12 事实 + D-01 ~ D-12 决策
├── data-model.md                    # Phase 1：Finding/Evidence/异常矩阵/TraceId 链/共享状态/性能口径/SPI 表
├── quickstart.md                    # Phase 1：可执行验证指南（含"旧代码失败"复现步骤）
├── contracts/
│   ├── dubbo-exception-contract.md          # 异常分类与 filter 装配契约
│   ├── dubbo-trace-context-contract.md      # TraceId 优先级与上下文恢复契约
│   └── dubbo-config-observability-contract.md # 配置键、SPI、allowlist、日志与指标契约
└── checklists/requirements.md       # 规格质量清单（全部通过）
```

### Source Code (repository root)

```text
leaf-base-dubbo-nacos/
├── pom.xml                                                       # 不改（零新增依赖）
└── src/
    ├── main/java/cn/maple/dubbo/nacos/
    │   ├── filter/
    │   │   ├── GXDubboClientTraceIdFilter.java                   # 改：共享 appName 缓存、INFO→DEBUG、空值标注
    │   │   ├── GXDubboServerTraceIdFilter.java                   # 改：同上
    │   │   ├── GXDubboExceptionFilter.java                       # 改：Sentinel 全 BlockException 覆盖、反射失败 fail-closed、catch 记录与计数
    │   │   └── package-info.java                                 # 改：空值策略说明（可选）
    │   ├── handler/
    │   │   ├── GXDubboCallExceptionHandler.java                  # 改：@Order(HIGHEST_PRECEDENCE)
    │   │   └── package-info.java                                 # 不改
    │   ├── processor/
    │   │   ├── GXDubboRpcApiBeanPostProcessor.java               # 改：@Log4j2 → @Slf4j
    │   │   └── package-info.java                                 # 不改
    │   ├── selector/
    │   │   ├── GXPenetrateAttachmentSelector.java                # 改：静态 appName 缓存共享 + resetAppNameCache()
    │   │   └── package-info.java                                 # 不改
    │   └── config/
    │       └── GXDubboProviderFilterValidator.java               # 新增：启动期校验 provider filter 是否排除内置 exception
    ├── main/resources/
    │   ├── META-INF/dubbo/org.apache.dubbo.rpc.Filter            # 不改（扩展名契约）
    │   ├── META-INF/dubbo/org.apache.dubbo.rpc.PenetrateAttachmentSelector # 不改
    │   └── security/serialize.allowlist                          # 不改（补文档说明）
    └── test/java/cn/maple/dubbo/nacos/
        ├── filter/GXDubboExceptionFilterTest.java                # 改：保留 11 条 + Sentinel 全类型 + 反射失败 fail-closed
        ├── filter/GXDubboTraceIdFilterTest.java                  # 改：保留 5 条 + 并发 appName
        ├── filter/GXDubboFilterChainSpringBootTest.java          # 新增：真实 Dubbo 链（排除/不排除内置 exception 对照）
        ├── handler/GXDubboCallExceptionHandlerSpringBootTest.java# 改：真实 @SpringBootTest 组件扫描，验证 advice 优先级
        ├── handler/GXDubboCallExceptionHandlerTest.java          # 改：保留 2 条 + RpcException 子类矩阵
        ├── processor/GXDubboRpcApiBeanPostProcessorTest.java     # 改：保留 4 条 + 反射失败分支
        ├── processor/GXDubboRpcApiBeanPostProcessorSpringBootTest.java # 保留 1 条
        ├── selector/GXPenetrateAttachmentSelectorTest.java       # 改：保留 5 条 + appName 缓存/重置
        └── config/GXDubboProviderFilterValidatorTest.java        # 新增：缺省/已排除/错误配置三态

docs/leaf-base-dubbo-nacos开发手册.md                              # 新增：模块定位、SPI 契约、TraceId 优先级、异常矩阵、强制配置、allowlist、不支持范围
leaf-base-dubbo-nacos/AGENTS.md                                    # 改：§2/§3/§5 补强制装配配置、修正"无 src/test"漂移、登记测试基线
```

**Structure Decision**: 保持"单模块 + 包内分层"现状，不新增子工程；生产侧新增 1 个类型（`config/GXDubboProviderFilterValidator`），修改 6 个既有类型；不新增资源文件；测试侧新增 2 个测试类（真实 Dubbo 链、配置校验器），修改 5 个既有测试类；文档新增 1 份手册 + 修订 `AGENTS.md`。`pom.xml`、根 POM、其它模块不改。

## Phase 0 → Phase 1 结论摘要

- **R-01（依赖真相）**：`mvn -o -pl leaf-base-dubbo-nacos dependency:tree "-Dincludes=com.alibaba.nacos:*,org.apache.dubbo:*"` 实测：`leaf-base-nacos:compile → nacos-client:3.1.1:compile`；`dubbo-spring-boot-starter:3.3.6 → dubbo:3.3.6`；`dubbo-nacos-spring-boot-starter:3.3.6`。
- **R-02（内置 ExceptionFilter 行为，字节码实测）**：`javap` 确认 `org.apache.dubbo.rpc.filter.ExceptionFilter implements Filter, BaseFilter$Listener`，注解常量池含 `Lorg/apache/dubbo/common/extension/Activate;`、`group`、`provider`；字节码显示对"未在方法签名声明、非 `java./javax./jakarta.`、非 `RpcException`"的**未声明运行时异常**执行 `new RuntimeException(StringUtils.toString(exception))` 并 `setException`。`META-INF/dubbo/internal/org.apache.dubbo.rpc.Filter` 含 `exception=org.apache.dubbo.rpc.filter.ExceptionFilter`。（F-01 的机制证据）
- **R-03（生产装配真相）**：`config/online-nacos-dubbo-demo.yml` 第 21 行 `dubbo.provider.filter: dubboExceptionFilter,-exception`；全仓 `*.{yml,yaml,properties,md}` 检索 `dubbo.provider.filter` 仅此 1 处 → **该排除要求只存在于示例配置，模块文档未登记**。
- **R-04（双重 advice 装配）**：`@GXEnableLeafFramework` 为 `@ComponentScan(value={"cn.maple"})`（`GXEnableLeafFramework.java` 第 12–20 行）；`GXExceptionHandler` 为 `@RestControllerAdvice`（第 42 行）且第 226–231 行含 `@ExceptionHandler(Exception.class)`；`GXDubboCallExceptionHandler` 也是 `@RestControllerAdvice`（第 14 行）且无 `@Order` → 两个 advice 同时注册，排序决定 `RpcException` 命中哪个 handler（F-02 成立）。
- **R-05（MDC 语义）**：`GXTraceIdContextUtils.getTraceId()` 对 null 返回 `""`、`getNullableTraceId()` 返回 null、`putTraceId(blank)` 等价 `removeTraceId`、`restoreTraceId(blank)` 等价 `removeTraceId` → 现有 `finally` 恢复链在"进入前为空"时正确清除（P-OK-01 的依据）。
- **R-06（工具类失败语义）**：`GXCommonUtils.getEnvironmentValue(String, Class, R)` 每次调用 `GXSpringContextUtils.getEnvironment()`；`getGenericClassType` 用 Hutool `ClassUtil.getTypeArgument`（不可解析返回 null）；`reflectCallObjectMethod` 对 null 目标/方法缺失/调用异常一律返回 null（不抛）→ `staticBindServeServiceClass` 绑定失败会**静默**（F-15/F-08 相关）。
- **R-07（基线）**：`mvn -o -pl leaf-base-dubbo-nacos test` = **31 执行 / 0 失败 / BUILD SUCCESS**（8 个测试类）。
- **R-08（交付集缺口）**：`docs/` 下无 `leaf-base-dubbo-nacos开发手册.md`；`AGENTS.md` 第 5 节称"当前模块下没有现成的 src/test 测试目录"，实际有 9 个测试类 → 文档漂移（F-13 成立）。
- **R-09（序列化白名单）**：`security/serialize.allowlist` 仅 `com.google.common.collect.HashBasedTable`（无注释、无来源），README/AGENTS 仅一句"新增传输对象时评估"（F-12 成立）。
- **R-10（构建门槛）**：根 POM `<release>21</release>`、ErrorProne 2.50.0 + NullAway 0.13.4 `ERROR`、`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`；模块已在根 `<modules>` 注册（第 26 行）。
- **R-11（测试上下文约束）**：Nacos client 在类路径，`@SpringBootTest` 必须关闭 Nacos 自动配置（`spring.cloud.nacos.config.import-check.enabled=false` 已在既有 `GXDubboRpcApiBeanPostProcessorSpringBootTest` 中采用）；Dubbo 链验证用 `injvm`/直连，不需要真实 Nacos（FR-025）。
- **R-12（测试命名与真实性）**：`GXDubboCallExceptionHandlerSpringBootTest` 实际用 `MockMvcBuilders.standaloneSetup(...).setControllerAdvice(new GXDubboCallExceptionHandler())`（第 25–27 行），**不是** `@SpringBootTest`，无法暴露基础 advice 也注册的真实装配（F-16 成立）。
- 其余设计决策（装配校验、advice 排序、Sentinel 覆盖、反射 fail-closed、appName 缓存、日志降级、空值标注、文档与 allowlist、测试策略、兼容声明）见 [research.md](./research.md)。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| 新增 `GXDubboProviderFilterValidator`（启动期校验 provider filter） | F-01 的后果是**业务错误码静默丢失**（`GXBusinessException` 被内置 filter 包成 `RuntimeException`），而正确装配只存在于示例配置；校验器把"隐性要求"变成"显式可观测" | 仅写文档不够：下游升级后不会重读文档，且丢失发生在运行时而非启动期；在 filter 内部动态禁用内置 filter 不可行（Dubbo 不支持从扩展内移除另一个已激活扩展） |
| `GXDubboCallExceptionHandler` 增加 `@Order(HIGHEST_PRECEDENCE)` | 两个 `@RestControllerAdvice` 都注册时，`RpcException` 可能被基础 `Exception` 兜底 handler 先匹配；`@Order` 是让专用 handler 稳定的唯一低成本手段 | 移除子类的 `@RestControllerAdvice` 并只在基础 advice 上加 Dubbo handler → 会把 Dubbo 依赖倒灌进 `leaf-base-framework`（违反宪法 I 依赖单向）；拆掉继承 → 重复实现 20+ 个基础 handler（违反附加约束 4） |
| Sentinel 覆盖扩到全部 `BlockException` 子类（类名前缀判定） | F-03 要求不遗漏官方阻断类型；不引入 `sentinel-core` 依赖的前提下只能靠类名/反射 | 引入 `sentinel-core` 做 `instanceof` → 新增依赖且与本模块"不承载 Sentinel 适配"的定位冲突（`AGENTS.md` 第 38 行明确 Sentinel 适配归 `leaf-base-dubbo-nacos-sentinel`） |
| 静态 `appName` 缓存改为共享 + 显式重置 | F-08/F-09 要求消除热路径环境查找并统一口径；同时避免多上下文静态串用 | 每个 Filter 各自缓存 → 三份静态状态更易漂移；完全不缓存 → 每请求上下文查找的成本保留（F-09 不解决） |
| 新增 1 份开发手册 | 宪法 §I 要求模块交付集含 `docs/<模块名>开发手册.md`；F-01 的强制装配配置必须落在手册 | 只改 `AGENTS.md` → 面向使用方的手册缺失，SPI 扩展名/allowlist/TraceId 优先级契约无处可查 |

## Phase 2 预告

`/speckit.tasks` 将按"测试先行"拆分为 6 个批次：**B1 异常装配与业务码保真**（F-01/F-02）→ **B2 异常分类与 fail-closed**（F-03/F-04/F-05/F-06/F-07）→ **B3 上下文与共享状态**（F-08/F-09）→ **B4 性能与可观测**（F-10/F-11）→ **B5 契约与安全资源**（F-12/F-13/F-14/F-15）→ **B6 测试基线补强**（F-16 + 横切），每批次内顺序为"先用例（旧代码失败）→ 最小实现 → `mvn -pl leaf-base-dubbo-nacos test` 全绿"。
