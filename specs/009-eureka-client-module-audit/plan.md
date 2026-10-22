# Implementation Plan: leaf-base-eureka-client 装配边界、默认配置与契约一致性审查

**Branch**: `009-eureka-client-module-audit`（`before_plan` hook 未注册；未创建 git 分支，当前为 `dev4.3.0-spec/008-eureka-server-module-audit`） | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-eureka-client-module-audit/spec.md`

## Summary

把 13 条审查发现（高 5 / 中 5 / 低 3）与 6 条既定契约（P-01 ~ P-06）落成"可实测、可回归、可迁移"的交付：**审查报告（`audit-report.md`）+ 装配开关 + 默认配置示例化 + 测试改造 + 契约文档收敛**。

技术路径（Phase 0 已实测确认）：

1. **装配可控**：新增开关 `maple.framework.enable.eureka-client`（默认 `true`，加法式变更），关闭时由 `EnvironmentPostProcessor` 以最低优先级写入 `eureka.client.enabled=false`，确保"无 Bean、无注册、无 `eureka.*` 残留值"，并输出一条 ASCII 日志。
2. **配置去风险**：删除 jar 根 `classpath:/application.yml`（实测证明宿主存在同名文件时框架值被静默丢弃、宿主无同名文件时才生效，两种结果都不可控），默认值改由 `src/main/resources/examples/` 交付；`register-with-eureka` 与 `defaultZone` 不再由框架默认声明，未配置 `defaultZone` 时输出一条 ASCII WARN。
3. **职责归位**：`spring.cloud.openfeign.circuitbreaker.*` 三个键移出本模块——真实生效路径是 `config/{dev,test,pre,prod}/app-environment.yml`（已存在），无需改 `leaf-base-feign` 代码。
4. **测试可信**：把 YAML 文本断言换成 `Environment`/`EurekaClientConfigBean` 语义断言；新增 `@SpringBootTest` + JDK `HttpServer` 模拟注册端点，覆盖"注册请求确实发出""开关两向""宿主配置生效"；双平台一致。

## Technical Context

**Language/Version**: Java 21（`maven-compiler-plugin` `<release>21</release>`；ErrorProne 2.50.0 + NullAway 0.13.4，`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`）

**Primary Dependencies**: Spring Boot 4.1.1；Spring Cloud 2025.1.3（`spring-cloud-netflix 5.0.2` → `spring-cloud-starter-netflix-eureka-client 5.0.2` → `eureka-client 2.0.6` + `httpclient5 5.6.4`；`spring-cloud-openfeign 5.0.3`；`spring-cloud-loadbalancer 5.0.3`）；框架内 `leaf-base-feign`、`leaf-base-webclient`、`leaf-base-framework`（`4.3.0-SNAPSHOT`）；`org.jspecify:jspecify 1.0.1`

**Storage**: N/A（库模块，无持久化）

**Testing**: JUnit 5（Jupiter 6.0.3）+ AssertJ 3.27.7 + `ApplicationContextRunner` + `@SpringBootTest` + JDK 内置 `com.sun.net.httpserver.HttpServer`（模拟 Eureka 注册端点，不引入新依赖）

**Target Platform**: JDK 21 库（jar，随 `leaf-framework` 聚合门面下发）；需在 Windows（CRLF 工作区）与 Linux（LF）双平台测试一致

**Project Type**: library（Maven 多模块仓库中的单个 `leaf-base-*` 模块）

**Performance Goals**: 不设吞吐/时延门禁。只要求可复算口径：`WebEnvironment.NONE` 应用上下文启动耗时与常驻线程增量。**实测基线（Phase 0）**：22 个线程，其中 Eureka 相关 4 个（`DiscoveryClient-%d` ×3、`DiscoveryClient-InstanceInfoReplicator-%d`）；上下文启动 6.4s（第二次运行）/ 9.9s（首次，含 JVM 预热）；同环境重跑偏差 ≤ 30%

**Constraints**: 不新增第三方依赖；不依赖外网与外部注册中心；Windows/Linux 测试结果必须一致；ErrorProne + NullAway 零错误；改动限于本模块 + 2 处跨模块文本（新增 `docs/leaf-base-eureka-client开发手册.md`、更新 `README.md` 模块表本模块行）；根 `pom.xml`、`leaf-framework/pom.xml`、`leaf-base-feign`、`leaf-base-webclient` **不改**

**Scale/Scope**: 4 个生产交付物 + 1 个测试类（4 用例，当前 1 失败）；13 条发现；3 项"必须实测"事实已在 Phase 0 完成

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 依据 / 约束 |
| --- | --- | --- |
| I. 模块自治与按需装配 | PASS（修复后） | 当前"引入即注册、无开关"违反"对可选能力使用条件装配降级"；本计划以 `maple.framework.enable.eureka-client` + `EnvironmentPostProcessor` 补齐降级路径。**不移除** `leaf-framework` 依赖（移除会使下游静默失去服务发现，已在 spec Clarifications 判定为独立后续决策） |
| II. 稳定契约优先 | PASS（附兼容性声明） | 开关默认 `true`、不自动开启 `@EnableFeignClients`、保持门面依赖 → 默认行为不变。**3 项显式行为变更**：①框架不再提供 `defaultZone` 默认值；②框架不再声明 `register-with-eureka`；③Feign 熔断键移出本模块。三项均在报告与手册的"升级必查"中列明迁移路径，不属"顺手重构" |
| III. 统一响应与统一异常 | N/A | 本模块不产生对外响应与业务异常；新增代码不引入自定义异常类型 |
| IV. 显式配置与双配置源 | PASS | 开关命名遵循 `maple.framework.enable.<feature>`；默认值语义写入 `additional-spring-configuration-metadata.json`；装配/降级各输出一条 ASCII 日志（禁止静默降级） |
| V. 空值安全与上下文纪律 | PASS | 新增类型位于 `cn.maple.eureka.client.config`（已有 `@NullUnmarked` 包级声明）；`EnvironmentPostProcessor` 不做线程/资源持有，无泄漏路径；测试包补 `package-info.java` |
| VI. 测试与静态检查门槛 | PASS | JUnit 5；既有失败用例先"改写为语义断言"再转绿（禁止删除/跳过）；新增"旧失败、新通过"回归用例；双平台一致；ErrorProne/NullAway 构建参数不动 |
| VII. 命名与包结构一致性 | PASS | 新增 `GXEurekaClientDefaultsPostProcessor`（`GX` 前缀 + 角色后缀）；沿用 `cn.maple.eureka.client.config`；日志 ASCII、面向用户的异常消息中文 |
| 附加约束 2/3（依赖治理） | PASS | 不新增第三方依赖；`httpclient 4.5.14` 保留并补注释说明"eureka-client 2.0.6 声明 4.5.3，由根 POM 统一抬升"，不删除既有冲突覆盖 |

**Post-Phase 1 复评**：设计（`contracts/` 三份契约 + `data-model.md`）未引入新原则冲突；第 II 条的三项行为变更已在 `contracts/configuration-contract.md` §4 具备迁移表，复评结论 **PASS**。

**兼容性声明（非违反项，需在 PR 与手册中体现）**：

| 变更 | 影响面 | 迁移路径 |
| --- | --- | --- |
| 移除 jar 内 `classpath:/application.yml`（含 `defaultZone`、`register-with-eureka`） | 依赖框架默认地址完成注册的应用 | 从 `examples/` 复制配置到宿主 `application.yml`，或显式设置 `eureka.client.service-url.defaultZone` |
| 移除 `spring.cloud.openfeign.circuitbreaker.*` | 仅引入 eureka-client 且未使用 `config/{env}/app-environment.yml` 的应用 | 使用环境模板（已含三个键）或显式配置 |
| 新增开关（默认 `true`） | 无破坏性影响 | 需要"引入但不注册"的应用设置 `maple.framework.enable.eureka-client=false` |

## Project Structure

### Documentation (this feature)

```text
specs/009-eureka-client-module-audit/
├── plan.md                       # 本文件
├── research.md                   # Phase 0：D-01 ~ D-10 决策 + R-01 ~ R-03 实测结论
├── data-model.md                 # Phase 1：发现/证据/矩阵/回归用例/交付物实体
├── quickstart.md                 # Phase 1：可执行验证指南
├── contracts/
│   ├── assembly-contract.md      # 装配契约（开关真值表、Bean、日志）
│   ├── configuration-contract.md # 配置键契约（新增/移除/示例/迁移）
│   └── dependency-contract.md    # 依赖契约（版本来源、exclusion 规则）
├── audit-report.md               # 实施期产出：审查报告（与 spec 摘要互引）
└── checklists/requirements.md    # 规格质量清单（已通过）
```

### Source Code (repository root)

```text
leaf-base-eureka-client/
├── pom.xml                                        # 依赖注释治理（exclusion 意图说明）
└── src/
    ├── main/
    │   ├── java/cn/maple/eureka/client/config/
    │   │   ├── GXEurekaClientConfig.java          # 加开关条件 + 未配置 defaultZone 的 ASCII WARN
    │   │   ├── GXEurekaClientDefaultsPostProcessor.java  # 新增：开关关闭时注入 eureka.client.enabled=false
    │   │   └── package-info.java                  # 保持 @NullUnmarked
    │   └── resources/
    │       ├── application.yml                    # 删除（移入 examples/）
    │       ├── examples/
    │       │   ├── eureka-client-default.yml      # 单注册中心示例（占位符，无固定地址）
    │       │   └── eureka-client-multi-registry.yml
    │       ├── META-INF/spring.factories          # 新增：注册 EnvironmentPostProcessor
    │       ├── META-INF/additional-spring-configuration-metadata.json  # 新增：开关元数据
    │       └── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/java/cn/maple/eureka/client/
        ├── config/GXEurekaClientConfigTest.java   # 改写：去掉 YAML 文本断言与平台依赖
        ├── config/GXEurekaClientSwitchTest.java   # 新增：开关两向 + 无残留值 + 日志
        ├── config/GXEurekaClientRegistrationTest.java # 新增：HttpServer 收到注册请求
        ├── support/GXTestRegistrationServer.java  # 新增：JDK HttpServer 夹具
        ├── testapp/OrderClient.java               # 既有夹具（保持）
        ├── testapp/OrderAdminClient.java          # 既有夹具（保持）
        └── testapp/TestApplication.java           # 既有夹具（保持）

docs/leaf-base-eureka-client开发手册.md            # 新增（跨模块授权点 3）
README.md                                          # 模块表本模块行更新（跨模块授权点 4）
leaf-base-eureka-client/AGENTS.md                  # 重写 §1 / §3 / §5，补缺失的 §2
```

**Structure Decision**: 保持"单模块 + 包内分层"现状，不新增子工程、不新增模块。生产代码只增 1 个类（`EnvironmentPostProcessor`）与 4 个资源文件（`examples/` 两份、`META-INF/spring.factories`、`META-INF/additional-spring-configuration-metadata.json`），删除 1 个资源文件（jar 根 `application.yml`）；其余为测试与文档。`leaf-framework/pom.xml`、`leaf-base-feign`、`leaf-base-webclient`、根 `pom.xml` 的 `dependencyManagement` **不改**。

## Phase 0 → Phase 1 结论摘要

- **R-01（注册中心不可达）实测**：应用**正常启动**，注册与拉取在后台重试（`foReplicator` 线程，30s 周期），仅有 `TransportException` 日志噪音 → 结论为"不阻塞启动"，因此 FR-008 不要求新增启动保护，但必须把"日志噪音 + 4 个常驻线程"写入报告的固定成本口径。
- **R-02（同名配置覆盖）实测**：宿主侧 `classpath:/application.yml` 生效（`defaultZone=http://host-config-wins:9999/eureka/`、`register-with-eureka=false`），框架 jar 内同名文件被**静默丢弃**；同时在未配置宿主文件时框架值才生效 → 证明"库内默认值"机制本身不可控，修复必须是"移出 jar 根"而非"调整取值"。
- **R-03（Feign 熔断键归属）实测**：`spring.cloud.openfeign.circuitbreaker.enabled` 在宿主有 `application.yml` 时为 `null`（框架默认未生效）；而其真实生效路径 `config/{dev,test,pre,prod}/app-environment.yml` 已含三个键 → 移出本模块无需改 `leaf-base-feign` 代码，迁移成本仅为文档。
- 其余设计决策（开关实现、示例文件、依赖注释、测试夹具、元数据、文档）见 [research.md](./research.md)。

## Complexity Tracking

> 本计划无宪法违反项，无需填写。复杂度仅为"新增 1 个 `EnvironmentPostProcessor`"，其必要性由 FR-005"关闭后不得遗留 `eureka.*` 默认值"直接驱动：仅用 `@ConditionalOnProperty` 无法阻止 Eureka 自动配置自身的默认地址与注册行为（同名配置删除后上游默认 `defaultZone=http://localhost:8761/eureka/` 仍生效），必须由最低优先级的属性后处理显式关闭客户端。
