# Phase 1 Data Model — 审查交付物模型

**Feature**: `009-eureka-client-module-audit` | **Date**: 2026-10-22

本特性无运行期数据实体；"数据"是被审查对象的结构化结论。以下 8 个模型同时约束 `audit-report.md`（实施期产出）与研究/验收过程的填写完整性。

## 1. Finding（审查发现）

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `id` | `F-01` ~ `F-13` | 唯一、连续、不复用已关闭编号 |
| `dimension` | 枚举：装配边界 / 默认配置语义 / 测试可信度 / 依赖治理 / 职责边界 / 契约一致性 | 单选 |
| `severity` | 枚举：高 / 中 / 低 | 高 = 影响面跨项目或导致门禁失败；中 = 单模块内可观测行为偏差；低 = 惯例/可读性 |
| `location` | `<file>:<line-range>` | 必须可直接跳转（相对仓库根） |
| `trigger` | 文本 | 触发条件（引入方式 / 配置组合 / 平台） |
| `impact` | 文本 | 受影响范围，高等级必须写"谁受影响"（如"引入聚合门面的全部应用"） |
| `evidence` | `E-xx` 列表 | ≥ 1 条 |
| `reproduction` | 文本 | 已有用例 / 新用例名 / 构造步骤；纯阅读判断必须写"未验证假设" |
| `regression` | `RC-xx` 或"无（原因）" | 必须有"旧失败、新通过"的差异说明 |
| `verdict` | 枚举：缺陷 / 风险 / 既定契约 / 惯例 | `既定契约` 与 `惯例` 不计入缺陷统计，但必须给出文档对齐动作 |

**校验规则**：`severity=高` 的条目缺少 `impact` 受影响范围 → 报告不合格（FR-020）；`verdict=缺陷` 且 `regression` 为空 → 不合格（FR-017）。

## 2. Evidence（证据）

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `id` | `E-01` ~ `E-27` | 与 `spec.md` 编号一致；新增证据续编，不改号 |
| `type` | `SOURCE`（代码/配置/文档） / `MEASURED`（本次执行） | `MEASURED` 必须带命令与时间 |
| `locator` | `<file>:<line>` 或命令 | `SOURCE` 精确到行；`MEASURED` 精确到命令 |
| `excerpt` | 文本 | 原文片段（截断用 `...`），不得改写 |
| `collectedAt` | `YYYY-MM-DD HH:mm` | 必填 |

**当前状态**：`E-01`~`E-20` 为 `SOURCE`（`E-07`、`E-09` 为 `MEASURED`）；实施期新增 7 条 `MEASURED` 证据，均含探针/命令与"探针文件已删除"说明：`E-21`（开关关闭线程）、`E-22`（不可达启动）、`E-23`（覆盖顺序）、`E-24`（熔断键生效路径）、`E-25`（启动成本与线程）、`E-26`（默认域名 DNS/TCP/HTTP 可达性）、`E-27`（"引入但不注册"组合矩阵）。

## 3. AssemblyMatrixRow（装配矩阵）

| 字段 | 取值 |
| --- | --- |
| `appShape` | 仅本模块 / 聚合门面 / 开关关闭 / 显式 `eureka.client.enabled=false` |
| `switchValue` | `true` / `false` / 未设置（默认 `true`） |
| `webType` | `SERVLET` / `REACTIVE` / `NONE` |
| `eurekaBeans` | 观测到的 Bean 名（无则 `none`） |
| `registrationAttempted` | `yes` / `no` + 目标 URL |
| `startupResult` | `success` / `fail` + 异常文本 |
| `threads` | Eureka 相关线程名列表 |

**校验规则**：4 行 × 3 列（`eurekaBeans`/`registrationAttempted`/`startupResult`）不得为空（SC-003）。

## 4. ConfigSourceRow（配置来源矩阵）

| 字段 | 取值 |
| --- | --- |
| `key` | `maple.framework.enable.eureka-client`、`eureka.client.register-with-eureka`、`eureka.client.service-url.defaultZone`、`spring.cloud.openfeign.circuitbreaker.*` |
| `frameworkDefaultBefore` | 修复前框架是否提供 + 取值（无则 `-`） |
| `frameworkDefaultAfter` | 修复后（`-` 表示框架不再提供） |
| `hostValue` | 宿主显式值 |
| `effectiveValue` | 最终生效值 |
| `winner` | 框架 / 宿主 / 上游默认 / 命令行 |
| `stableAcrossRuns` | 3 次连续启动是否一致（本次已实测 2 次一致） |

**校验规则**：`winner` 不得为"不确定"；若 3 次不一致，必须在报告中单列并发起设计变更（FR-006 / SC-004）。

## 5. DependencyRow（依赖矩阵）

| 字段 | 取值 |
| --- | --- |
| `coordinate` | `groupId:artifactId` |
| `resolvedVersion` | 解析版本 |
| `scope` | compile / runtime / test |
| `transitive` | `yes` / `no` |
| `provider` | 引入者（starter / 根 POM / 模块声明） |
| `usage` | 实际使用者（`eureka-client` / `spring-cloud-netflix` / `leaf-base-framework` / 无） |
| `verdict` | 缺陷 / 冗余 / 无风险 |
| `action` | 保留 + 注释 / 删除重声明 / 不动 |

**覆盖范围（必须全部出现）**：`eureka-client`、`spring-cloud-netflix-eureka-client`、`httpclient`（4.5.14）、`httpclient5`（5.6.4）、`antlr-runtime`（3.5.3）、`error_prone_annotations`（2.50.0）、`spring-boot-jackson2`、`xstream`、`netflix-eventbus`。

## 6. ContractItem（契约核对）

| 字段 | 取值 |
| --- | --- |
| `doc` | `AGENTS.md` / `README.md` / `docs/*.md` / `additional-spring-configuration-metadata.json` |
| `section` | 章节或表格行号 |
| `statement` | 文档原文 |
| `implementation` | 实现事实 + 证据号 |
| `verdict` | 一致 / 不一致 |
| `action` | 保留 / 改写 / 新增 / 删除 |

**校验规则**：`AGENTS.md` 至少 4 条"不一致"（SC-009）；`README.md` 与 `docs/` 必须各至少 1 条。

## 7. RegressionCase（回归用例）

| 字段 | 取值 |
| --- | --- |
| `id` | `RC-01` ~ `RC-nn` |
| `findingRef` | `F-xx` 列表 |
| `layer` | 单元 / 上下文 / 集成（`@SpringBootTest`） |
| `className` | 测试类全名 |
| `precondition` | 开关/属性/夹具 |
| `assertion` | 断言点（语义，不断言文本格式） |
| `oldBehavior` | 修复前结果（红/差异） |
| `newBehavior` | 修复后结果（绿） |
| `platforms` | Windows(CRLF) / Linux(LF) 双平台一致性结论 |

**校验规则**：每个 `F-xx`（`verdict=缺陷`）至少 1 个 `RC`；`assertion` 中不得出现"包含换行符的字符串字面量"（D-08）。

## 8. Deliverable（交付物）

| 路径 | 归属阶段 | 对应 FR |
| --- | --- | --- |
| `audit-report.md` | 实施 | FR-002 / FR-003 / FR-016 / FR-020 |
| `leaf-base-eureka-client/src/main/java/cn/maple/eureka/client/config/GXEurekaClientConfig.java` | 实施 | FR-005 / FR-007 |
| `.../config/GXEurekaClientDefaultsPostProcessor.java` | 实施 | FR-005 |
| `.../resources/META-INF/spring.factories` | 实施 | FR-005 |
| `.../resources/META-INF/additional-spring-configuration-metadata.json` | 实施 | FR-014 / D-07 |
| `.../resources/examples/eureka-client-default.yml`、`eureka-client-multi-registry.yml` | 实施 | FR-006 / FR-007 |
| `.../resources/application.yml`（删除） | 实施 | FR-006 |
| `.../test/java/cn/maple/eureka/client/config/GXEurekaClientConfigTest.java`（改写） | 实施 | FR-009 / FR-010 |
| `.../test/java/cn/maple/eureka/client/config/GXEurekaClientSwitchTest.java`（新增） | 实施 | FR-005 / FR-011 |
| `.../test/java/cn/maple/eureka/client/config/GXEurekaClientRegistrationTest.java`（新增） | 实施 | FR-011 |
| `.../test/java/cn/maple/eureka/client/support/GXTestRegistrationServer.java`（新增） | 实施 | FR-011 |
| `leaf-base-eureka-client/pom.xml`（注释治理） | 实施 | FR-012 |
| `leaf-base-eureka-client/AGENTS.md`（重写 §1/§3/§5 + §2） | 实施 | FR-014 |
| `docs/leaf-base-eureka-client开发手册.md`（新增） | 实施 | FR-014 |
| `README.md`（模块表行更新） | 实施 | FR-014 |

**校验规则**：交付后 `mvn -o -pl leaf-base-eureka-client test` 必须 0 失败；`target/classes/application.yml` 必须不存在；`git status` 中不得出现 `leaf-framework/`、`leaf-base-feign/`、根 `pom.xml` 的改动。
