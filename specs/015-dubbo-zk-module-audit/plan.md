# Implementation Plan: leaf-base-dubbo-zk 正确性、线程安全与性能审查

**Branch**: `015-dubbo-zk-module-audit` | **Date**: 2026-10-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-dubbo-zk-module-audit/spec.md`

**Note**: 本次为"审查 + 缺陷收敛 + 完备测试"一体化交付。范围严格限定在 `leaf-base-dubbo-zk` 模块（`AGENTS.md` §2、宪法 §I/§II）。

## Summary

对 `leaf-base-dubbo-zk` 的 10 个生产源码文件与 3 个运行时资源逐文件审查后确认 23 条发现（F-01～F-23），其中 4 条为高危：内置 `ExceptionFilter` 双重激活导致业务码丢失（F-01）、Web 层专用 advice 可能被基础兜底遮蔽（F-02）、`GXSentinelFlowException` 在 provider 侧被二次包装丢 406/data（F-03）、模块零测试（F-04）。

技术路径（Phase 0 已逐条用源码/字节码核对，详见 [research.md](./research.md)）：

1. **装配契约显式化**（F-01、F-08）：新增启动期校验器 `GXDubboProviderFilterValidator`（`SmartInitializingSingleton`，只告警不阻断），并在 `AGENTS.md` 与新增开发手册中把 `dubbo.provider.filter=dubboServerTraceIdFilter,dubboExceptionFilter,-exception` 登记为强制装配契约（与同族模块 `014-dubbo-nacos-module-audit` 的收敛方式一致）。
2. **异常分类收敛**（F-03、F-05、F-06、F-07）：`GXSentinelFlowException` 提前透传；Sentinel 判定改为"`com.alibaba.csp.sentinel.slots.block.` 前缀 + `BlockException` 可赋值 + 完整消息 `SentinelBlockException: FlowException`"，删除裸 `"FlowException"` 子串判定；方法签名反射失败改为 fail-closed（不再 `return`，落入日志 + 包装）；外层 `catch (Throwable)` 保留兜底但增加失败计数。
3. **TraceId 全链路一致**（F-09、F-13）：异常 filter 的 traceId 解析改为"invocation 附件优先、MDC 回落"（Dubbo listener 回调晚于 filter `finally`，MDC 此时已被恢复，已由 `FilterChainBuilder` 源码确认）；selector 与 filter 复用同一 id 的路径用用例锁定（正常情况下 selector 生成的 id 经 `addObjectAttachments` 进入 invocation，filter 复用，无双生成）。
4. **热路径性能**（F-14、F-18、F-19）：新增共享 `getCachedAppName()`（`GXPenetrateAttachmentSelector` 内，3 处复用）+ `resetAppNameCache()`；两个 filter 的每请求 INFO 日志降为 DEBUG；`resolveRpcErrorType` 单次处理只计算一次；traceId 解析结果在单次 filter 调用内只算一次。
5. **Web 层与装配可观测性**（F-02、F-16、F-17）：`@Order(Ordered.HIGHEST_PRECEDENCE)`；`enrichTraceId` 改为复制 `Dict`；`GXDubboRpcApiBeanPostProcessor` 的 `@Log4j2` 改 `@Slf4j`，绑定后用 `GXBaseServeApi#getServeServiceClass()` 回读校验并 WARN。
6. **测试基线从 0 到 1**（F-04、SC-006）：新增 `src/test`，按"异常分类 / TraceId / selector / Web advice / BeanPostProcessor / 装配校验 / 过滤器链行为 / ASCII 守卫"8 类组织，目标 ≥28 条用例，全部离线可跑。

## Technical Context

**Language/Version**: Java 21（根 POM `java.version`，`release` 编译；Windows 本机为 JDK 25 运行 Maven 亦可）

**Primary Dependencies**: `leaf-base-framework`（4.3.0-SNAPSHOT）、`leaf-base-nacos`（compile，导致 Nacos 类恒在类路径）、`dubbo-spring-boot-starter` 3.3.6、`dubbo-zookeeper-curator5-spring-boot-starter` 3.3.6、`fastjson`、`javassist`；新增 **test** 作用域：`spring-boot-starter-test`

**Storage**: N/A（模块不持久化状态；状态全部为 MDC / `RpcContext` / Dubbo `Result` 附件 / 静态缓存）

**Testing**: JUnit 5（Jupiter）+ Mockito（经 `spring-boot-starter-test`）+ `MockMvc` + `@SpringBootTest`（最小配置，不启用 ZK/Nacos 自动装配）

**Target Platform**: Spring Boot 4.1.x / Spring Framework 7.x / Dubbo 3.3.6 JVM 应用（provider 与 consumer 双侧）

**Project Type**: 单模块 Java 库（framework library）

**Performance Goals**: 每请求 `Environment` 查询次数从最多 3 次降为 ≤1 次（仅首次读 appName，之后命中静态缓存）；核心链路 INFO 日志 2 条 → 0 条（DEBUG 可开）；异常路径 `resolveRpcErrorType` 2 次 → 1 次

**Constraints**: 不改公开类名/方法签名/SPI 扩展名/返回结构/异常语义；`GXBusinessException` 的 `code/msg/data` 必须无损；日志全部 ASCII；NullAway（`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`）零错误；离线可测

**Scale/Scope**: 1 个 Maven 模块；6 个功能类 + 4 个 `package-info` + 3 个资源 + 1 个 `pom.xml`；新增 1 个校验器类、1 个开发手册、约 10 个测试类

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 说明 |
| --- | --- | --- |
| I. 模块自治与按需装配 | PASS | 只在 `leaf-base-dubbo-zk` 内改动；新增校验器类放在模块内 `config` 包；不新增跨模块依赖（`Validation` 仅用 Spring 自带 `SmartInitializingSingleton`/`Environment`）；交付集补齐 `docs/leaf-base-dubbo-zk开发手册.md` |
| II. 稳定契约优先（NON-NEGOTIABLE） | PASS（附兼容声明） | SPI 扩展名（`dubboExceptionFilter`/`dubboClientTraceIdFilter`/`dubboServerTraceIdFilter`/`dubboPenetrateAttachmentSelector`）、公开方法签名、`GXResultUtils` 返回结构、`RpcException`→500 语义、`X-B3-TraceId` 附件键全部保留；行为调整仅 5 处且均为缺陷修复，逐条列入下方兼容性声明 |
| III. 统一响应与统一异常 | PASS | 仍以 `GXResultUtils.error(...)` 返回；不新增异常类型；`GXBusinessException` 保持唯一业务异常载体；兜底不回显堆栈/SQL/内部路径 |
| IV. 显式配置与双配置源 | PASS | 不新增业务配置键；`dubbo.provider.filter` 是 Dubbo 既有键，仅登记 + 启动期告警；不新增 `@Value`/`@ConfigurationProperties` |
| V. 空值安全与上下文纪律 | PASS | 为可空返回/入参补 `@Nullable`（`getAttachment`/`getInvocationAttachment`/`getEnvironmentValue`/`getGenericClassType`/`getApplicationContext`）；MDC `put/restore` 成对语义不变；静态 appName 缓存 `volatile` + 显式重置；异常路径不泄漏上下文 |
| VI. 测试与静态检查门槛 | PASS | 每条修复先落"旧代码失败"用例；覆盖成功/失败/异常/空值/边界/并发/Web 集成；不改编译参数；`mvn -o -pl leaf-base-dubbo-zk test` 全绿且用例数从 0 单调增长 |
| VII. 命名与包结构一致性 | PASS | 新增类沿用 `GX` 前缀与既有后缀（`GXDubboProviderFilterValidator`）；移除 `@Log4j2` 统一 SLF4J；每个新包补 `package-info.java` |
| 附加约束 §8 反对过度设计（YAGNI） | PASS | 不新增配置键（不引入 `check-strict`）；不新增未被 2 处以上复用的抽象；`getCachedAppName()` 有 3 个调用点；不新建自研反射/缓存工具 |
| 附加约束 §9 能力完备性对齐官方特性 | PASS | 对齐 Dubbo 官方 `Filter.Listener` 语义、`PenetrateAttachmentSelector` 双向选择、`ServiceBean` 绑定扩展点；不支持范围（Sentinel 原生指标、注册中心端到端）在手册登记 |
| 开发流程 §4 提交前检查清单 | PASS（计划） | 见 [quickstart.md](./quickstart.md) 验证命令与 [contracts/](./contracts/) 的契约核对表 |

**Gate 结论**: 通过，无违反门禁项，`Complexity Tracking` 留空。

## Project Structure

### Documentation (this feature)

```text
specs/015-dubbo-zk-module-audit/
├── plan.md                              # 本文件
├── research.md                          # Phase 0：R-01～R-14 研究结论与决策
├── data-model.md                        # Phase 1：发现/异常矩阵/TraceId 决策链/共享状态/装配契约/性能口径
├── quickstart.md                        # Phase 1：可执行验证指南
├── contracts/
│   ├── dubbo-exception-contract.md      # 异常分类矩阵与 Handler 契约
│   ├── dubbo-trace-context-contract.md  # TraceId 优先级、恢复语义、一致性契约
│   └── dubbo-config-observability-contract.md # 装配契约、启动期告警、日志级别契约
├── checklists/requirements.md
└── tasks.md                             # /speckit.tasks 生成（非本命令产物）
```

### Source Code (repository root)

```text
leaf-base-dubbo-zk/
├── pom.xml                              # 追加 spring-boot-starter-test（test）
├── AGENTS.md                            # 修订：能力清单、装配契约、Sentinel 规则、测试基线
├── src/main/java/cn/maple/dubbo/zk/
│   ├── config/                          # 新增
│   │   ├── GXDubboProviderFilterValidator.java
│   │   └── package-info.java
│   ├── filter/
│   │   ├── GXDubboExceptionFilter.java  # 改造：SentinelFlow 透传、Sentinel 判定、fail-closed、失败计数、traceId 来源、ASCII
│   │   ├── GXDubboClientTraceIdFilter.java # 改造：共享 appName、DEBUG 日志、@Nullable
│   │   ├── GXDubboServerTraceIdFilter.java # 改造：同上
│   │   └── package-info.java
│   ├── handler/
│   │   ├── GXDubboCallExceptionHandler.java # 改造：@Order、enrich 复制、错误类型只算一次
│   │   └── package-info.java
│   ├── processor/
│   │   ├── GXDubboRpcApiBeanPostProcessor.java # 改造：@Slf4j、绑定回读校验
│   │   └── package-info.java
│   └── selector/
│       ├── GXPenetrateAttachmentSelector.java  # 改造：public static getCachedAppName/resetAppNameCache、@Nullable
│       └── package-info.java
├── src/main/resources/                  # 不变（SPI 扩展名、allowlist 内容保持不变）
└── src/test/java/
    ├── cn/maple/dubbo/zk/AsciiSourceGuardTest.java
    ├── cn/maple/dubbo/zk/support/GXDubboTestSupport.java
    ├── cn/maple/dubbo/zk/support/GXDubboTestApp.java
    ├── cn/maple/dubbo/zk/config/GXDubboProviderFilterValidatorTest.java
    ├── cn/maple/dubbo/zk/filter/GXDubboExceptionFilterTest.java
    ├── cn/maple/dubbo/zk/filter/GXDubboTraceIdFilterTest.java
    ├── cn/maple/dubbo/zk/filter/GXDubboFilterChainBehaviorTest.java
    ├── cn/maple/dubbo/zk/handler/GXDubboCallExceptionHandlerTest.java
    ├── cn/maple/dubbo/zk/handler/GXDubboCallExceptionHandlerSpringBootTest.java
    ├── cn/maple/dubbo/zk/processor/GXDubboRpcApiBeanPostProcessorTest.java
    ├── cn/maple/dubbo/zk/processor/GXDubboRpcApiBeanPostProcessorSpringBootTest.java
    ├── cn/maple/dubbo/zk/selector/GXPenetrateAttachmentSelectorTest.java
    ├── com/alibaba/csp/sentinel/slots/block/degrade/DegradeException.java   # 桩
    ├── com/alibaba/csp/sentinel/slots/block/authority/AuthorityException.java # 桩
    └── org/mybatis/spring/MyBatisSystemException.java                       # 桩
docs/leaf-base-dubbo-zk开发手册.md        # 新增（跨目录但仅新增文档，见 D-07）
```

**Structure Decision**: 保持既有 5 个包（`filter`/`handler`/`processor`/`selector`）不变，仅新增 `config` 包承载启动期装配校验；测试按"被测类型同包 + `support` 公共夹具"组织，与 `leaf-base-dubbo-nacos` 保持一致。

## Phase 0 决策摘要

完整论证与备选方案见 [research.md](./research.md)。

| 编号 | 决策 | 对应发现 |
| --- | --- | --- |
| D-01 | 新增 `GXDubboProviderFilterValidator`（`SmartInitializingSingleton` + `Environment`，ERROR 告警不阻断），沿用 014 的实现模式；不新增配置键 | F-01、F-08 |
| D-02 | `GXSentinelFlowException` 在异常 filter 中**优先透传**（保留 type/code/msg/data），再进入 Sentinel 判定 | F-03 |
| D-03 | Sentinel 判定 = `com.alibaba.csp.sentinel.slots.block.` 前缀 ∪ `BlockException` 可赋值（`Class.forName` 静态解析，缺失返回 null）∪ 完整消息 `SentinelBlockException: FlowException`；删除裸 `"FlowException"` 子串 | F-05 |
| D-04 | 方法签名反射失败 → fail-closed（DEBUG 记录后继续走"未声明运行时异常"分支），不再 `return` | F-06 |
| D-05 | 外层 `catch (Throwable)` 保留，增加 `AtomicLong FILTER_FAILURE_COUNT` + 包级可见只读访问器（唯一使用者为测试，避免未使用 API） | F-07 |
| D-06 | 异常 filter 的 traceId 解析顺序改为 invocation 附件 → MDC；`attachTraceId` 复用同一结果，单次调用只解析一次 | F-09、F-18 |
| D-07 | 新增 `docs/leaf-base-dubbo-zk开发手册.md` 并重写 `AGENTS.md` 的能力/装配/测试章节 | F-15、F-20 |
| D-08 | `GXPenetrateAttachmentSelector` 暴露 `public static getCachedAppName()` + `resetAppNameCache()`（3 处复用），两个 filter 改用之；INFO 降 DEBUG | F-14 |
| D-09 | `GXDubboCallExceptionHandler` 加 `@Order(HIGHEST_PRECEDENCE)`；`enrichTraceId` 改为复制 `Dict`；`resolveRpcErrorType` 单次计算 | F-02、F-19 |
| D-10 | `GXDubboRpcApiBeanPostProcessor` 改 `@Slf4j`；绑定后用 `GXBaseServeApi#getServeServiceClass()` 回读校验并 WARN | F-16、F-22 |
| D-11 | 保留 `GXBusinessException.data` 原地注入与"data 为空降级为基类"行为（`data` 为 `final`，无法替换引用；降级是为了让 traceId 可回传），改为 Javadoc + 用例显式锁定（**对 spec FR-013/FR-026 的收敛调整，见兼容性声明 R-3**） | F-11、F-17 |
| D-12 | 保留两个 filter 对 `RpcContext` client/server 附件的双向写入（下游业务代码可能读取 `getServerAttachment()`），改为用例锁定 + 手册登记 | F-12 |
| D-13 | 双 traceId 不引入新抽象：selector 生成的 id 经 `ConsumerContextFilter.addObjectAttachments` 进入 invocation，filter 复用同一 id；用端到端用例锁定（无双生成） | F-13 |
| D-14 | `config/application-dubbo-zk.yml.conf` 的 `WRAN` 拼写与 filter 列表**本次不改**（跨模块/仓库级配置，超出模块边界），在手册"已知问题"登记并交由后续样例配置专项目处理 | F-21 |
| D-15 | 加 `spring-boot-starter-test`（test）与 3 个测试桩类（Sentinel `DegradeException`/`AuthorityException`、MyBatis `MyBatisSystemException`），全部用例离线可跑 | F-04 |

## Phase 1 设计产物

- [data-model.md](./data-model.md)：发现模型、异常分类矩阵（9 类异常 × 5 观测点）、TraceId 决策链（5 来源 × 4 组件）、共享状态清单、装配契约表、性能口径表。
- [contracts/dubbo-exception-contract.md](./contracts/dubbo-exception-contract.md)：provider/consumer 双侧异常定性、Web 层 handler 优先级与响应结构、`RpcException` 分类矩阵。
- [contracts/dubbo-trace-context-contract.md](./contracts/dubbo-trace-context-contract.md)：TraceId 来源优先级（consumer/provider/selector/exception filter）、恢复语义、附件写入位置、一致性要求。
- [contracts/dubbo-config-observability-contract.md](./contracts/dubbo-config-observability-contract.md)：强制装配配置、启动期告警文案、日志级别、失败计数、不支持范围登记。
- [quickstart.md](./quickstart.md)：编译/测试/离线验证/手工压测口径与预期结果。

## 兼容性声明（对外可见行为变化）

| # | 变化 | 影响面 | 兼容性策略 |
| --- | --- | --- | --- |
| R-1 | 未排除内置 `exception` filter 时启动输出 ERROR 日志 | 未配置 `dubbo.provider.filter` 的存量部署 | 只告警不阻断启动；按日志指引补 `dubboServerTraceIdFilter,dubboExceptionFilter,-exception` |
| R-2 | `GXSentinelFlowException` 不再被二次包装；Sentinel 误判（业务消息含 `FlowException`）被消除 | 依赖"任何含 FlowException 的异常都转 406"的调用方（错误行为） | 缺陷修复；`SentinelBlockException: FlowException` 完整消息仍可识别，覆盖官方 Sentinel 包装路径 |
| R-3 | `GXBusinessException.data == null` 时仍降级为基类异常（保持现状，不修改） | 子类字段语义在该路径上不可见 | 与 014 一致；若后续需要保真，需在 `leaf-base-framework` 提供可设置 `data` 的构造/协议（跨模块变更，另行提案） |
| R-4 | 异常 filter 的 traceId 来源增加 invocation 附件回落 | MDC 已被恢复的场景（provider 异常响应） | 修复：`data.X-B3-TraceId` 从缺失变为有值；有值时行为不变 |
| R-5 | 每请求 INFO 日志改为 DEBUG；新增共享 appName 缓存 | 依赖 INFO 日志观测 TraceId 的运维脚本 | 需要时开 DEBUG；缓存可用 `resetAppNameCache()` 重置 |
| R-6 | `GXDubboCallExceptionHandler` 增加 `@Order(HIGHEST_PRECEDENCE)` | 依赖基础 advice 兜底处理 `RpcException` 的下游 | 缺陷修复：`RpcException` 改由专用 handler 返回结构化 `data` |
| R-7 | `GXDubboRpcApiBeanPostProcessor` 绑定失败新增 WARN | 绑定静默失败的存量部署 | 仅新增可观测信号，不改变成功路径与启动结果 |

## Complexity Tracking

> 无 Constitution Check 违反项，本节留空。

## Phase 1 后复评（Constitution Re-Check）

| 原则 | 复评结果 | 触发点 |
| --- | --- | --- |
| I. 模块自治与按需装配 | PASS | 新增类只在 `cn.maple.dubbo.zk.config`；无新依赖（`spring-boot-starter-test` 仅 test scope）；`docs/` 手册为交付集要求 |
| II. 稳定契约优先 | PASS | 设计产物 [contracts/](./contracts/) §5/§6 逐条列出旧→新行为；7 处行为变化全部为缺陷修复或增量信号，无签名/结构/SPI 变更 |
| III. 统一响应与统一异常 | PASS | handler 仍返回 `GXResultUtils`；`enrichTraceId` 改为复制不改变响应内容；未引入新异常类型 |
| IV. 显式配置与双配置源 | PASS | 未新增配置键；`dubbo.provider.filter` 为既有键，仅告警 + 文档 |
| V. 空值安全与上下文纪律 | PASS | `@Nullable` 仅增量标注；MDC/`RpcContext` 写入与恢复成对；静态缓存 `volatile` + 可重置；失败路径有计数与 WARN |
| VI. 测试与静态检查门槛 | PASS | 测试清单 8 类 / ≥28 条，含 1 个真实 `@SpringBootTest`、并发/空值/边界/异常路径；编译参数不变 |
| VII. 命名与包结构 | PASS | `GXDubboProviderFilterValidator` 符合 `GX<能力><角色>`；新包补 `package-info.java`；日志统一 SLF4J |
| 附加约束 §8 YAGNI | PASS | 未新增 `check-strict` 开关；未抽 `GXDubboTraceIdResolver`（R-05 论证）；未新增未使用的 reset/公共 API |
| 附加约束 §9 官方特性对齐 | PASS | 覆盖 Dubbo `Filter.Listener`、`PenetrateAttachmentSelector` 双向、`ServiceBean` 绑定；不支持范围在 [contracts/dubbo-config-observability-contract.md](./contracts/dubbo-config-observability-contract.md) §7 登记 |

**复评结论**：设计无新增违反项，可进入 `/speckit.tasks`。

## 验证命令

```powershell
# 模块级测试（离线仓库已含 leaf-base-framework / leaf-base-nacos 4.3.0-SNAPSHOT）
mvn -o -pl leaf-base-dubbo-zk test

# 若上游快照缺失，连带构建依赖
mvn -o -pl leaf-base-dubbo-zk -am test

# 只看用例清单与结果
mvn -o -pl leaf-base-dubbo-zk test | Select-String -Pattern "Tests run|GXDubbo|GXPenetrate"
```

预期：`Tests run: ≥28, Failures: 0, Errors: 0, Skipped: 0`，且 NullAway 编译零错误。
