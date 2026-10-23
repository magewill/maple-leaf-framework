# Specification Quality Checklist: leaf-base-webclient 正确性、线程安全与性能审查

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-23
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

- 本特性的审查对象就是代码本身，"无实现细节"按"证据定位"口径判定（与 011 同口径）：`spec.md` 中的类名、方法名、行号、上游 jar 版本仅用于让结论可复核（对应 FR-002～FR-004），不作为实现方案的规定；行文主线是"结论可信、失败可判、资源可释放、修复可证"的维护者/接入方价值。
- SC 条款以覆盖率、条目数、可复算口径与通过率度量（SC-001/002/003/004/005/007/008 为计数或 100% 口径；SC-006 为**计数器精确相等**的三项口径——token 生成 60s 窗口 1 次 / 环境查找 0 次 / durationMs+retryDelayMs=总墙钟 ±50ms），均可脱离具体实现判定。
- 澄清项已在 `spec.md` 的 Clarifications 章节闭环：共 12 条问答（首轮 7 条：交付边界、下游影响面、`@LoadBalanced` 语义定性、危险默认可改范围、上下文透传验收路线、性能判定口径、测试完备性最低标准；`/speckit.analyze` 复审 5 条：带点服务名与守卫开关、快照 token 语义、限流实现与可测性、token 缓存窗口口径、错误体 DTO 取舍），全部以宪法 II/IV/V/VI 与既有用例为依据做出决策，无遗留 `[NEEDS CLARIFICATION]` 标记。
- 基线事实（本次实测）：`mvn -pl leaf-base-webclient test` = **17 执行 / 0 失败**（aspect 4 / config 4 / handler 2 / ascii 1 / service 6）；503 重试用例实测 1+3 次尝试、固定 1s 退避、重试线程为 `parallel-1..3`。
- 上游语义核对（可按构件复核）：`spring-cloud-commons-5.0.3-sources.jar` 的 `LoadBalancerWebClientBuilderBeanPostProcessor`（`@LoadBalanced` 附着）与 `ReactorLoadBalancerExchangeFilterFunction.filter`（`serviceId = url.getHost()`，无直连旁路，无实例返回合成 503）；`reactor-netty-core` 的 `ConnectionProvider`（javap：`extends reactor.core.Disposable`，仅 `dispose()/disposeLater()`）；`GXAuthCodeUtilsTest`（`authCodeDecode` 失败返回 `"{}"`）；`GXBusinessException`（字段 `msg`）与 `GXHttpInvokerApiErrorResDto`（字段 `message`）错位；`GXCurrentRequestContextUtils` 基于 `RequestContextHolder`。
- 下游影响面实测：`leaf-base-eureka-client` 编译依赖本模块并锁定装配顺序（`@AutoConfiguration(after = ...GXWebClientConfig.class)`）与 Bean 契约；已写入 FR-009 冻结清单与 SC-007 回归要求。
- 一致性核对：`spec.md` 的 E-01～E-24 与发现 F-01～F-19 一一对应（每条发现至少引用一条证据），P-01～P-08 与 FR-009/FR-021 相互引用，无孤立证据、无无证据发现。
- 判定：所有条目通过（1 轮校验，无残留问题），规格可进入 `/speckit.clarify`（可选）或 `/speckit.plan`。
