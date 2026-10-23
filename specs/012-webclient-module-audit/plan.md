# Implementation Plan: leaf-base-webclient 正确性、线程安全与性能修复

**Branch**: `012-webclient-module-audit` | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-webclient-module-audit/spec.md`

## Summary

对 `leaf-base-webclient` 模块（8 个生产源码文件 + 1 装配清单 + 5 测试类）的审查已定位 19 条发现（高危 3：LB 截杀直连寻址与误导性合成 503、远端统一响应错误详情丢失、请求上下文 ThreadLocal 与订阅线程断裂）。本计划的技术路线：**在契约冻结前提下做缺陷收敛**——以"显式上下文快照 + 双形状错误解析 + 幂等重试护栏 + 连接池释放闭环 + 日志掩码与耗时口径分离"五条修复主线为核心，新增配置一律走 `@ConfigurationProperties` 且不改任何既有配置键/默认值，任何生产改动同变更补齐测试（用例数从 17 单调增长，每修复项附旧代码失败证据）。

## Technical Context

**Language/Version**: Java 21（模块 `maven.compiler.source/target=21`；宪法基线 JDK 21 + Spring Boot 4.1.x，本次实测构建环境 JDK 25 可编译 21 目标）

**Primary Dependencies**: Spring Boot 4.1.x（`spring-boot-starter-webflux`）、Spring Framework 7.0.9（`spring-web`/`spring-webflux`，本次以 7.0.9 源码为语义基线）、Reactor / reactor-netty 1.3.x（`ConnectionProvider extends reactor.core.Disposable` 已 javap 核对）、Spring Cloud LoadBalancer（`spring-cloud-commons` 5.0.3，`@LoadBalanced` 语义已按源码核对）、`leaf-base-framework`（公共契约提供方，**禁止修改**）、Lombok / JSpecify（`@NullUnmarked` 现状约定）。**不引入任何新第三方依赖**（宪法附加约束 3；测试用 JDK 自带 `com.sun.net.httpserver.HttpServer`）

**Storage**: N/A（无持久化；资源面为 reactor-netty 连接池与后台驱逐线程）

**Testing**: JUnit 5（Jupiter）+ AssertJ + `reactor-test`（`StepVerifier`）+ `spring-boot-test`（`ApplicationContextRunner`、`@SpringBootTest`）+ JDK `HttpServer`（本地回环真实 HTTP）；构建期 ErrorProne + NullAway（`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`，构建失败即不合格）

**Target Platform**: JVM 服务端（框架库，被下游 Spring Boot 应用以依赖形式引入；已知下游 `leaf-base-eureka-client`）

**Project Type**: library（Maven 多模块框架的单一能力模块 `leaf-base-webclient`）

**Performance Goals**: 按 spec SC-006 可复算口径——150s token 有效期内 token 生成次数从"每请求 1 次"降为"每窗口 ≤1 次"（或记录保持决策）；每请求环境查找次数从 2+ 降为 0（属性绑定后）；duration 日志分离网络耗时与退避等待。不设绝对吞吐/时延门禁

**Constraints**: 宪法 II 契约冻结（Bean 名称/作用域、`maple.framework.web.client.*` 配置键与默认值、默认请求头、`GXBusinessException` 结构与 `code` 语义、过滤器注册顺序、`@AutoConfiguration` 可排序性）；AGENTS.md 硬性规则（仅改本模块、日志 ASCII、禁止无复用抽取、触碰即补测试）；测试门槛 FR-019～FR-023；三类"危险默认"（非幂等自动重试、敏感头明文入日志、连接池泄漏）按缺陷收敛，需旧代码失败证据 + 兼容说明

**Scale/Scope**: 修复面 19 条发现（高 3 / 中 9 / 低 7）+ 8 条保护断言（P-01～P-08 不得回退）；涉及 4 个生产类 + 装配清单 + **新增 4 个生产类型**（`GXWebClientProperties`/`GXWebClientContextSnapshot`/`GXWebClientErrorResDto`/`GXWebClientResourceHolder`）+ 1 个测试支撑类型（`GXWebClientTestSupport`）；测试从 17 条增长至 40～55 条；跨模块触碰仅限（a）根 POM 版本属性迁移、（b）`docs/leaf-base-webclient开发手册.md` 补齐，均需最小影响面说明

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | 门禁（宪法条款） | 判定 | 依据 / 约束落点 |
| --- | --- | --- | --- |
| G1 | I 模块自治与按需装配 | PASS（附条件） | 修复 100% 落在 `leaf-base-webclient`；仅两处跨模块触碰（根 POM 版本属性、docs 手册）按"最小影响面 + 说明"执行（FR-024）；不新增依赖、不引入新中间件 |
| G2 | II 稳定契约优先（NON-NEGOTIABLE） | PASS（6 项偏差入 Complexity Tracking） | FR-009 冻结清单逐项锁定；三类危险默认收敛按缺陷处理，各附旧行为失败用例 + 兼容说明 + 可配置恢复路径；配置键只增不改；过滤器链增量、LB 守卫默认开启、错误体 DTO 三项偏差逐条登记（见 Complexity Tracking ④⑤⑥） |
| G3 | III 统一响应与统一异常 | PASS | 4xx/5xx 仍转 `GXBusinessException`（`code`=远端状态，P-05 保持）；仅修复消息保真（F-02）与合成失败可区分（F-01）；不引入新异常类型语义，`GXWebClientAuthTokenException` 语义不变 |
| G4 | IV 显式配置与双配置源 | PASS（附条件） | 新增键全部绑定 `GXWebClientProperties`（`@ConfigurationProperties(prefix="maple.framework.web.client")`）并补 `additional-spring-configuration-metadata.json`；既有键名/默认值不变；local/nacos 双子类模式不采用——`@ConfigurationProperties` 天然绑定任意 PropertySource（含 Nacos），双层类结构无实际收益（宪法"复杂度必须被证明"） |
| G5 | V 空值安全与上下文纪律 | PASS | F-03 修复即上下文纪律（快照显式传播 + 缺失告警）；F-04 释放闭环（`dispose()` 销毁路径 + 幂等）；触碰的公共 API 补 `@Nullable`（`getPlatform`/`getTraceId` 回落等），包级 `@NullUnmarked` 现状保留 |
| G6 | VI 测试与静态检查门槛 | PASS | FR-019～FR-023 硬门槛写入任务验收；JUnit 5 only；并发/上下文/资源/AOP/装配改动均有对应测试形态要求；ErrorProne+NullAway 构建通过为完成定义 |
| G7 | VII 命名与包结构 | PASS | 新增类型按后缀表：`GXWebClientProperties`、`GXWebClientContextSnapshot`（值对象）或 `GXWebClientContextUtils`（工具）、`GXWebClientErrorResDto`；包结构不动，`package-info.java` 触碰时保持 `@NullUnmarked` 现状 |

**Gate 结论**: 7/7 通过（G1/G4 附条件已落入任务约束）；6 项行为偏差（三类危险默认收敛 + 过滤器链增量 + LB 守卫默认开启 + 错误体 DTO）不阻断门禁，按宪法"复杂度必须被证明"登记于 Complexity Tracking。

## Project Structure

### Documentation (this feature)

```text
specs/012-webclient-module-audit/
├── spec.md              # 已完成（/speckit.specify 输出）
├── plan.md              # 本文件（/speckit.plan 输出）
├── research.md          # Phase 0 输出（10 项决策全部闭环）
├── data-model.md        # Phase 1 输出（7 个领域模型）
├── quickstart.md        # Phase 1 输出（验证运行指南）
├── contracts/           # Phase 1 输出（5 份契约）
│   ├── bean-contract.md
│   ├── config-contract.md
│   ├── filter-chain-contract.md
│   ├── error-mapping-contract.md
│   └── spi-contract.md
├── checklists/
│   └── requirements.md  # 已完成
├── tasks.md             # Phase 2 输出（/speckit.tasks 生成，含 Phase 8.5 复审补充）
└── audit-report.md      # 实现期交付（T001 建骨架 / T006–T009 填正文与矩阵）
```

### Source Code (repository root)

```text
leaf-base-webclient/
├── pom.xml                                   # 触碰点：loadbalancer.version 收敛（跨模块至根 POM，见 R-10）
└── src/
    ├── main/
    │   ├── java/cn/maple/webclient/
    │   │   ├── aspect/GXWebClientAuthTokenAspect.java   # 修复：上下文误判可区分（F-03）
    │   │   ├── config/GXWebClientConfig.java            # 修复主线（F-01/02/04/05/07/08/10/11/13/16/17/18）
    │   │   ├── config/GXWebClientProperties.java        # 新增：统一配置绑定（R-08；18 键 = 10 冻结 + 8 新增）
    │   │   ├── config/GXWebClientErrorResDto.java       # 新增：双形状错误体解析（R-03；理由见 Complexity ⑥）
    │   │   ├── config/GXWebClientResourceHolder.java    # 新增：手工路径单池复用与释放（R-05）
    │   │   ├── service/GXWebClientService.java          # 修复：SPI 钩子接线 + @Nullable + token 短窗缓存（F-09/14/06）
    │   │   ├── service/GXWebClientContextSnapshot.java  # 新增：上下文快照（R-01）
    │   │   └── handler/GXWebClientExceptionHandler.java # 修复：文案一致 + 可达性声明（F-13）
    │   └── resources/META-INF/
    │       ├── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports  # 不变
    │       └── additional-spring-configuration-metadata.json                            # 新增/补齐（宪法 IV）
    └── test/java/cn/maple/webclient/
        ├── LogMessageAsciiTest.java                    # 保留（P-04）
        ├── testsupport/GXWebClientTestSupport.java      # 新增：HttpServer + 日志捕获支撑（T002）
        ├── config/GXWebClientConfigTest.java           # 保留 + 扩展（既有 4 条断言零改，P-02）
        ├── config/GXWebClientErrorMappingTest.java     # 新增：错误体形状矩阵（T010）
        ├── config/GXWebClientLbGuardTest.java          # 新增：LB 守卫与白名单（T011/T050）
        ├── config/GXWebClientRetryGuardTest.java       # 新增：幂等重试护栏（T012）
        ├── config/GXWebClientSpringBootTest.java        # 新增：真实装配 + 本地 HttpServer 端到端（T017）
        ├── config/GXWebClientContextPropagationTest.java # 新增：上下文透传/限流告警（T018）
        ├── config/GXWebClientConcurrencyTest.java      # 新增：并发与线程形态（T023）
        ├── config/GXWebClientResourceLifecycleTest.java # 新增：连接池释放/幂等/无泄漏（T024/T027）
        ├── config/GXWebClientMetricsContractTest.java  # 新增：耗时口径/阈值（T028）
        ├── config/GXWebClientLogMaskTest.java          # 新增：日志掩码/UA（T029）
        ├── config/GXWebClientTimeoutTest.java          # 新增：四类超时接线（T047）
        ├── service/GXWebClientServiceTest.java         # 保留 + 扩展（缓存/掩码/钩子接线）
        ├── service/GXWebClientContextSnapshotTest.java  # 新增：快照捕获/应用/缺失语义
        ├── aspect/GXWebClientAuthTokenAspectTest.java   # 保留 + 扩展（无上下文语义）
        └── handler/GXWebClientExceptionHandlerTest.java # 保留 + 扩展（文案一致）

docs/leaf-base-webclient开发手册.md            # 新增（宪法交付清单，跨 docs 区，见 R-10）
pom.xml                                       # 触碰点：<loadbalancer.version> 属性迁移（根 POM properties）
```

**Structure Decision**: 沿用模块既有 `config/service/aspect/handler` 分包与 `cn.maple.webclient` 包根（宪法 VII 映射）；新增生产类型 **4 个**（`GXWebClientProperties` 配置绑定、`GXWebClientContextSnapshot` 值对象、`GXWebClientErrorResDto` 错误体解析、`GXWebClientResourceHolder` 资源持有者）+ 测试支撑 1 个，均在既有包内、不新建生产子包（快照并入 `service`，错误体与持有者并入 `config`）；测试按既有 `config/service/aspect/handler` 镜像组织并新增 `testsupport`，集成测试命名 `GX<能力>SpringBootTest`（宪法 VI）。不采用任何"通用客户端框架"式抽象（宪法附加约束 4：禁止重复造轮子）；`GXWebClientErrorResDto` 的复用取舍见 Complexity Tracking ⑥，禁止再为其它用途复制同类 DTO。

## Constitution Check（Post-Design 复检，Phase 1 后）

*GATE: Phase 1 设计定稿后复评。*

| # | 门禁 | 复检判定 | 设计落点核对 |
| --- | --- | --- | --- |
| G1 | I 模块自治 | PASS | 新增类型全部在 `cn.maple.webclient` 内（`GXWebClientProperties`/`GXWebClientContextSnapshot`/`GXWebClientErrorResDto`/`GXWebClientResourceHolder`）；跨模块仅根 POM 属性与 docs 手册两处（R-10，零行为变化）；零新增第三方依赖 |
| G2 | II 稳定契约 | PASS（6 偏差已证） | `contracts/bean-contract.md` 与 `config-contract.md` 给出逐项冻结清单与增量表；六处偏差各带兼容路径：`retry-methods` 一行恢复旧重试范围、`durationMs` 新口径可复算总墙钟、UA 可选后缀恢复自定义标识、过滤器链仅内侧增量且 `lb-guard-enabled=false` 可回退、LB 守卫可关/可白名单、错误体 DTO 限定单一用途 |
| G3 | III 统一异常 | PASS | `error-mapping-contract.md`：异常类型/结构/前缀公式冻结，仅 `{detail}` 取值修复；LB 守卫错误复用 `GXBusinessException`（400 段），不引入新异常类型 |
| G4 | IV 显式配置 | PASS | `GXWebClientProperties` + `additional-spring-configuration-metadata.json` 双落地；键名零变更；local/nacos 双子类不采用的理由已记录（R-08，复杂度不被证明） |
| G5 | V 空值与上下文 | PASS | `spi-contract.md` 给出 `@Nullable` 标注清单；`data-model §5/§6` 给出释放状态机与快照空值规则；"静默丢失"由限流 WARN 消灭（FR-010 底线） |
| G6 | VI 测试门槛 | PASS | `data-model §7` 测试矩阵（**9 修复项**（F-01～F-08 + F-11）× 7 用例形态）+ `quickstart.md` 证据表骨架 **V1～V10**（V9 token 缓存/配置健壮性、V10 处理器文案与可达性）对应 FR-019～FR-023、FR-025、FR-026；完成定义含 ErrorProne/NullAway |
| G7 | VII 命名 | PASS（附说明） | `GXWebClientProperties` 命中 `GX<能力>Properties`、`GXWebClientErrorResDto` 命中 `ResDto`；`GXWebClientContextSnapshot`（值对象）与 `GXWebClientResourceHolder`（持有者）属后缀表未列条目，按既有"描述性语义 + GX 前缀"惯例命名并在此登记为非表内条目（不视为违反）；`package-info` 保持 `@NullUnmarked` 现状 |

**复检结论**: 7/7 通过；设计未引入新的宪法偏差（Complexity Tracking 6 项均有兼容路径与回滚方式；`/speckit.analyze` 复审增补 ④⑤⑥ 与签名稳定性约束）。设计定稿，可进入 `/speckit.tasks`。

## Complexity Tracking

> 仅登记 Constitution Check 中需要论证的行为偏差（宪法"复杂度必须被证明"）。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| ① 默认重试行为收敛：POST/PATCH 不再被网络类失败默认自动重放（现行为会重放，新默认仅 GET/HEAD/OPTIONS 可重试） | 现行为是"危险默认"（spec F-05）：连接重置/超时下非幂等请求自动重放产生重复副作用，属正确性缺陷而非契约特性 | "保持现状 + 仅文档告警"被拒：文档无法阻止线上重复扣款类事故；"移除全部重试"被拒：会破坏 P-02 既有断言与 408/429/5xx 的既有重试契约。折中：新键 `maple.framework.web.client.retry-methods` 可恢复旧范围，旧行为有迁移路径 |
| ② `durationMs` 语义收紧：从"含退避的墙钟"改为"纯网络耗时（各次尝试之和）"，新增 `attempts`/`retryDelayMs` 字段 | 现口径把重试退避计入响应耗时（spec F-07、US5 验收场景 3 明确要求"不再混计退避等待"），监控与慢请求告警失真 | "保持 durationMs 语义 + 只加字段"被拒：同名字段语义分裂比收紧更难治理；兼容路径：`totalDurationMs` 可由 durationMs+retryDelayMs 复算，变更写入兼容说明 |
| ③ 跨模块触碰 2 处：根 POM `<loadbalancer.version>` 属性迁移 + `docs/leaf-base-webclient开发手册.md` 新增 | 宪法附加约束 2（版本只在根 POM properties）与交付清单（每模块 docs 手册）；宪法 VI 要求"触碰相关代码时一并收敛已知偏差" | "模块内保留版本号 + 登记遗留"被拒：属已知偏差且触碰面为零行为变化，收敛成本极低；"完整重写全部模块文档"被拒：超出本特性范围 |
| ④ 过滤器链增量：新增 `lbGuardFilter`、内层时间戳过滤器，`X-Request-Start-Time` 由 `requestFilter` 迁出（FR-009 冻结"过滤器注册顺序"） | FR-011 要求"直连误用 0 次尝试"必须在 LB 过滤器之前短路；E-14 要求时间戳按尝试刷新——两者都无法在既有 4 个过滤器内部实现 | "复用 `requestFilter` 内联判定"被拒：requestFilter 在 `retryFilter` 外层，无法做到逐尝试刷新且与 token 短路语义耦合；"把守卫放到最内层"被拒：LB 过滤器已先生成合成 503，无法区分真因。兼容口径：既有 #1–#4 相对顺序不变，新增过滤器只插入内侧，旧行为可由 `lb-guard-enabled=false` 完整恢复 |
| ⑤ LB 守卫默认开启且新增 `lb-guard-enabled`/`lb-guard-allow-hosts` 两键（宪法 IV 要求功能开关默认关闭） | F-01 的失败形态误导是**缺陷**（合成 503 + 假重试），按缺陷修复应默认收敛；但合法带点服务名（`svc.ns.svc.cluster.local`）需要放行通道，否则 US2 场景 1"服务名寻址现状保持"被破坏 | "默认关闭（默认交旧行为）"被拒：缺陷仍然存在，FR-011 无法验收；"只做文档、不加键"被拒：带点服务名会硬失败。折中：默认开启 + 白名单键 + 逃生开关，并在手册与 AGENTS 显式声明该默认。**Converge T051 补强**：正式开关迁移到宪法 IV 命名 `maple.framework.enable.webclient-lb-guard`（配置后覆盖旧键），旧键保留为兼容别名，默认 `true` 作为已登记的豁免 |
| ⑥ 模块内新增 `GXWebClientErrorResDto`（与 `leaf-base-framework` 的 `GXHttpInvokerApiErrorResDto` 近似） | 既有 DTO 的字段固定为 `message`，而框架统一响应是 `msg`（F-02 根因）；给公共 DTO 补字段属跨模块破坏性变更（宪法 II + 本特性"框架模块禁止修改"假设） | "解析成 `Map`/`JsonNode` 后取值"被拒：丢失类型安全且与既有 `bodyToMono` 链路割裂；"复制到 module 后再扩散复用"被拒：限定为单一用途的双形状解析器，禁止作为通用 DTO 使用 |

**签名稳定性（T051 验收点）**：`webClientBuilder(ObjectProvider, ObjectProvider, ReactorClientHttpConnector, Environment)` 的 4 参签名**保持不变**（既有测试与下游按此调用）；`lbMode` 通过新增包级私有重载承载，Bean 方法委托并默认 `lbMode=true`。任何签名调整视为破坏性变更，须走 FR-009 兼容说明流程。
