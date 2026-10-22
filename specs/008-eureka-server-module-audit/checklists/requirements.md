# Specification Quality Checklist: leaf-base-eureka-server 装配边界、配置语义与依赖治理审查

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-22
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- 审查类特性的审查对象本身就是代码，因此"无实现细节"一条按"证据定位"口径判定：`spec.md` 中的类名、依赖坐标、行号仅用于让结论可复核（对应 FR-002、FR-003），不作为实现方案的规定；行文主线仍是"结论可信、可复现、可排期"的用户价值。
- SC 条款以覆盖率、断言点数量、依赖矩阵组数、重跑偏差为度量，均可在不依赖具体实现的前提下判定；JDK/Spring 版本、`ApplicationContextRunner` 等属于验证前提，已写入 Assumptions。
- 澄清项已在 `spec.md` 的 Clarifications 章节闭环（12 项：交付边界、跨模块授权范围、devtools 定性与处置、库内 `application.yml` 定性与落位、集群示例定性、测试依赖约束、性能判定口径、报告存放位置、装配开关默认值、Jersey 代际收敛方向、默认配置中环境相关项处置、示例配置交付形态），无遗留 `[NEEDS CLARIFICATION]`。
- 本轮 `clarify` 新落地的 5 项决策已写入 `FR-007`、`FR-008`、`FR-016`、`FR-017`、`SC-011` 与 Assumptions：开关 `maple.framework.enable.eureka-server` 默认 `false` 且本模块移出聚合门面（破坏性变更，附迁移清单）；devtools 彻底移除；以 `jersey-apache5-connector 4.0.2` 替换无 4.x 版本的 `jersey-apache-connector 3.1.12` 并移除 HttpClient 4.x 声明；默认配置只保留 `false/false` 单机语义；示例配置移入 `src/main/resources/examples/`。
- 基线事实（本次实测）：`mvn -pl leaf-base-eureka-server test` = 2 执行 / 0 失败 / BUILD SUCCESS（耗时 6.3s），证据 E-16。
- 已确认的证据性结论：聚合门面无条件依赖本模块且自动装配无开关（E-02、E-04）；starter 传递引入 `spring-boot-starter-web`、`starter-tomcat`、`starter-actuator`（E-05）；`spring-boot-devtools` 为 compile 作用域（E-06）；Jersey 3.1.12 与 Jersey 4.0.2/4.0.3 同栈（E-07）；`eureka.server.version=5.0.2` 与 BOM 同值（E-10）；示例配置 `register-with-eureka=false`+`fetch-registry=false`+`defaultZone`→7002/7003 自相矛盾（E-11）。
- 待验证项已显式标注为"未验证假设"：库内 `classpath:/application.yml` 与宿主同名配置的覆盖方向（F-05 / FR-007）、集群对等复制是否发生（F-02 / FR-004），必须在实测后才可定性；`spec.md` 未预先断言二者为已确认缺陷。
- 范围边界：本规格不修改任何生产代码；`leaf-framework/pom.xml`、根 `pom.xml`、`docs/`、`config/` 仅作为引用与修复候选登记，修改需在计划阶段单独授权（FR-012、FR-015）。
- 已知遗留：`leaf-base-eureka-client` 的配置质量不在本次审查范围内，仅作为默认值对照被引用。
