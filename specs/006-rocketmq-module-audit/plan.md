# Implementation Plan: leaf-base-rocketmq 逻辑正确性、线程安全与性能审查

**Branch**: `006-rocketmq-module-audit` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-rocketmq-module-audit/spec.md`

## Summary

本次交付分两层：**审查结论**（独立报告，覆盖 18 个生产文件、27 个既有用例与真实 Broker 实测）与**修复设计**（按 spec 的 5 项澄清结论，把 3 个高危、11 个中危发现落成"测试先行"的改造方案）。

技术路线：不动公开方法签名与配置键（宪法 II）。核心改造集中在四处——

1. **配置装配**：用嵌套条件替代 `@ConditionalOnMissingClass` 单条件，让 `rocketmq.config-source` 三态（`local` / `nacos` / 未配置）真正决定装配结果，并补一条可检索启动日志。
2. **异步发送可观测**：新增可选 `GXRocketMQAsyncSendFailureHandler` 扩展点，`ObjectProvider` 注入，无实现时退化为 error 日志，`sendAsync` 契约不变。
3. **CDC 分层失败处置**：解析失败走"warn + 计数 + 可选死信"，监听器异常走"逐监听器隔离 + 汇总抛出"，新增 `GXCdcDispatchException` 保留现场。
4. **开销治理**：成功路径日志降级、CDC 事件 diff 只算一次、事件对象只读化，均以结构指标（往返/日志/分配次数）验收，不设绝对吞吐门禁。

真实 Broker 验证以 `@EnabledIfEnvironmentVariable` 门控的 `integration` 用例入库，缺环境自动跳过。

## Technical Context

**Language/Version**: Java 21（项目基线，`maven.compiler.release=21`）；构建使用 ErrorProne + NullAway（`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`）

**Primary Dependencies**: `rocketmq-spring-boot-starter 2.3.6`（传递 `rocketmq-spring-boot`）、根 POM 强制的 `rocketmq-client/rocketmq-common/rocketmq-remoting/rocketmq-acl 5.3.2`、`spring-boot 4.1.1`、`hutool`、`jspecify`、`leaf-base-framework`、`leaf-base-nacos`（→ `nacos-client 3.1.1`、`spring-cloud-starter-alibaba-nacos-config 2025.1.0.0`）；`micrometer-metrics` 与 `actuator` 经框架传递可用，无需新增依赖

**Storage**: N/A（消息存储由外部 Broker 承担；本模块无本地持久化）

**Testing**: JUnit 5（Jupiter）+ `spring-boot-starter-test` + Mockito + AssertJ；`ApplicationContextRunner` 做条件装配断言；真实 Broker 用 `@EnabledIfEnvironmentVariable` 门控 + `@Tag("integration")`

**Target Platform**: JVM 库（被 Spring Boot 4.1.x 应用以 Maven 依赖引入）；下游需 `@GXEnableLeafFramework` 或等价 `cn.maple` 包扫描

**Project Type**: library（多模块 Maven 工程中的 `leaf-base-rocketmq` 单一模块）

**Performance Goals**: 结构开销口径——单条消息网络往返 ≤1 次、成功路径 info 日志 0 条、CDC 每事件容器分配 ≤4 次且 diff 仅 1 次；真实 Broker 采样保持顺序 avg≈1.3ms / p95≈2.3ms 量级（仅作复现参照，非门禁）

**Constraints**: 不改 `GXSendRocketMQService` 方法签名/返回值/异常语义；不重命名或删除既有配置键；只改 `leaf-base-rocketmq`（如需动根 POM 必须单独说明影响面）；不新增第三方依赖；不引入 Testcontainers；ErrorProne/NullAway 零告警

**Scale/Scope**: 18 个生产源码文件、4 个测试类（含 1 个 integration）、3 个高危 + 11 个中危发现；目标改动文件数 ≤ 12

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 说明 |
| --- | --- | --- |
| I 模块自治与按需装配 | PASS | 改动全部落在 `leaf-base-rocketmq`；不新增模块、不改依赖方向；条件装配用 Spring 原生条件，不硬编码下游依赖 |
| II 稳定契约优先 **NON-NEGOTIABLE** | PASS | `rocketmq.config-source` 保留原名与默认语义（把"文档承诺"变成"实现生效"，属对齐而非变更）；`sendAsync` / `sendNormalMessage` / `syncSend` 签名与异常语义不变；新扩展点为可选、无实现时行为等价于现状 |
| III 统一响应与统一异常 | PASS | 新增 `GXCdcDispatchException extends GXBusinessException`，携带 code 与 `Dict` 扩展信息，复用统一处理链路；不新增裸 Map/String 返回 |
| IV 显式配置与双配置源 | PASS（含 1 项计划内收敛） | 配置走 `@ConfigurationProperties` + 条件装配，装配过程输出可检索日志；`cdc.active.status` 未命名空间化的问题按"新增 `maple.framework.cdc.active-status` 为优先键、旧键保留为回退并 warn"处理，不破坏既有部署；新增键补 `additional-spring-configuration-metadata.json` |
| V 空值安全与上下文纪律 | PASS | 新增类型所在包补 `package-info.java` 与 `@Nullable` 标注；异步回调与 CDC 分发的 MDC/TraceId 传递复用既有 `GXMdcWrapper*`，不新建线程、不自造上下文 |
| VI 测试与静态检查门槛 | PASS | 每个缺陷修复附"旧代码失败、新代码通过"的回归用例；真实 Broker 用例缺环境自动跳过；`mvn -pl leaf-base-rocketmq -am test` 与 ErrorProne/NullAway 零错误为完成门槛 |
| VII 命名与包结构一致性 | PASS | 新类型沿用 `GX` 前缀（`GXRocketMQAsyncSendFailureHandler`、`GXCdcDispatchException`、`GXCdcDispatchStatistics`）；异常入 `exception` 包，扩展点入 `listener`/`handler` 语义包，不破坏现有分层 |

**Gate 结论**：无违反项，无需 Complexity Tracking 豁免。设计完成后（Phase 1 末）复查：上述 7 条仍为 PASS，唯一需要注意项是"新增配置键必须补 metadata 且不得复用生产默认值"。

**Phase 1 复查结果（2026-10-22）**：核对 `research.md` / `data-model.md` / `contracts/*` 后确认——新增类型 3 个（`GXRocketMQAsyncSendFailureContext`、`GXRocketMQAsyncSendFailureHandler`、`GXCdcDispatchException`）均为 `GX` 前缀且落在既有分层；新增配置键 2 个（`maple.framework.cdc.active-status`、`maple.framework.cdc.dead-letter-topic`）均为 `maple.framework.*` 命名空间且带默认值，需同步补 `additional-spring-configuration-metadata.json`；未新增第三方依赖、未改公开方法签名、未触及其他模块。**7 条门禁保持 PASS。**

## Project Structure

### Documentation (this feature)

```text
specs/006-rocketmq-module-audit/
├── plan.md              # 本文件
├── spec.md              # 规格（已澄清）
├── research.md          # Phase 0：技术决策与备选方案
├── data-model.md        # Phase 1：审查产物与新增类型的数据模型
├── quickstart.md        # Phase 1：端到端验证指南
├── contracts/           # Phase 1：对外契约（发送 / 配置来源 / CDC 与异步失败扩展点）
├── checklists/
│   └── requirements.md  # 规格质量清单
└── review-report.md     # 实施阶段产出：独立审查报告（本命令不创建）
```

### Source Code (repository root)

```text
leaf-base-rocketmq/
├── src/main/java/cn/maple/rocketmq/
│   ├── dispatcher/{,impl/}                 # CDC 分发：分层失败处置 + 监听器隔离
│   ├── dto/inner/                          # GXCdcEvent 只读化、请求 DTO 构造器收敛
│   ├── exception/                           # 新增 GXCdcDispatchException
│   ├── handler/                             # 新增 GXRocketMQAsyncSendFailureHandler + 上下文对象
│   ├── listener/                            # GXCdcEventListener（不变）
│   ├── properties/{local,nacos}/            # 三态条件装配 + 启动日志 + metadata
│   └── service/{,impl/}                     # 发送服务：日志降级、校验补齐、失败回调
├── src/main/resources/META-INF/
│   ├── additional-spring-configuration-metadata.json   # 新增（当前模块缺失）
│   └── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports  # 评估项，见 research D-07
└── src/test/java/cn/maple/rocketmq/
    ├── properties/                          # 三态装配回归（修复当前失败用例）
    ├── service/impl/                        # 发送语义与并发回归
    ├── dispatcher/                          # CDC 分层处置与隔离回归
    └── integration/GXRocketMQRealBrokerIT.java  # env 门控真实 Broker 用例
```

**Structure Decision**: 沿用现有单模块分层（`properties` / `service` / `dispatcher` / `dto` / `listener`），仅新增 `exception`、`handler`、`resources/META-INF` 三处；不新增子模块、不引入新包层级，符合宪法"不为已有能力再包一层抽象"。

## Phase 0 研究结论（详见 research.md）

| 编号 | 决策 | 关键理由 |
| --- | --- | --- |
| D-01 | 用 `AnyNestedCondition` 实现 `config-source` 三态，替代 `@ConditionalOnMissingClass` 单条件 | 单条件在"类路径含 nacos-client"时恒假，正是 F-01 根因 |
| D-02 | 本地 `@PropertySource` 占位符补默认值 `${spring.profiles.active:default}` | 现有写法在未设置 profile 时占位符不可解析 |
| D-03 | `nacos` 分支改由下游 `spring.config.import=nacos:<dataId>` 承担，模块只负责开关与校验 | 依赖树无 `nacos-spring-boot` 处理器，注解无执行者；引入该 starter 与 Boot 4.1 冲突风险高 |
| D-04 | 异步失败用可选 SPI + `ObjectProvider`，无实现退化为 error 日志 | 保持 `sendAsync` 契约不变，接入方零改动 |
| D-05 | CDC 解析失败"warn + `LongAdder` 计数 + 可选死信"，监听器异常"隔离后汇总抛 `GXCdcDispatchException`" | 毒消息重试无意义，业务失败需重试；两者必须分开处理 |
| D-06 | 指标用 `ObjectProvider<MeterRegistry>` 可选注册，不新增依赖 | `micrometer-metrics` 已由框架传递可用 |
| D-07 | 不新增自动装配 `imports` 文件，改为在装配缺失时输出 warn | 改动面最小；自动装配会改变下游现有扫描语义（宪法 II） |
| D-08 | 成功路径 info 日志降为 debug，仅保留失败 error | 结构指标可验收（info 日志 0 条），且不损失故障可观测性 |

## Phase 1 设计产物

- [research.md](./research.md)：8 条决策（决策 / 理由 / 备选方案 / 影响面 / 验证方式）。
- [data-model.md](./data-model.md)：审查产物模型 + 修复引入的 3 个新类型字段与不变量。
- [contracts/send-contract.md](./contracts/send-contract.md)：5 类发送的返回值、失败表达、投递语义与校验规则。
- [contracts/config-source-contract.md](./contracts/config-source-contract.md)：`rocketmq.config-source` 三态矩阵、日志与 fail-fast 规则。
- [contracts/cdc-dispatch-contract.md](./contracts/cdc-dispatch-contract.md)：CDC 分层失败处置、监听器隔离、计数与死信契约。
- [contracts/async-failure-contract.md](./contracts/async-failure-contract.md)：异步失败扩展点签名、调用时机、异常隔离与兼容性。
- [quickstart.md](./quickstart.md)：单元 / 装配 / 真实 Broker / 性能复现四类验证命令与期望结果。

## 实施顺序（供 /speckit.tasks 使用）

1. **基线固化**（无生产改动）：跑通 27 个既有用例并记录 1 个失败；补齐审查报告骨架与证据索引。
2. **装配层（F-01 / FR-017）**：三态条件 + 占位符默认值 + 启动日志 + metadata；修复 `loadsLocalConfigSource` 失败用例，新增 `config-source=nacos` 与"未配置"两态用例。
3. **发送层（F-02 / F-04–F-07 / FR-020）**：失败扩展点、异常语义说明、校验补齐、日志降级；回归覆盖 `null` body、topic 含 `:`、非 `SEND_OK`。
4. **CDC 层（F-03 / F-08–F-10 / FR-018）**：分层失败处置、监听器隔离、事件只读化、计数指标；回归覆盖毒消息、未知 op、监听器抛错后其余监听器仍执行。
5. **开销与观测（F-11 / F-12 / F-15 / FR-007）**：MDC 传递、diff 单次计算、关键路径日志复核。
6. **真实 Broker 集成用例（FR-019）**：env 门控 IT，覆盖 5 类发送 + ACL 拒绝 + 并发 200 条采样。

## Complexity Tracking

无宪法违反项，无需豁免记录。
