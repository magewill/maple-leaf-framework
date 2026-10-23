# Specification Quality Checklist: leaf-base-retry 正确性、线程安全与性能审查

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

- 审查类特性的审查对象本身就是代码，因此"无实现细节"一条按"证据定位"口径判定：`spec.md` 中的类名、方法名、行号与上游源码行号仅用于让结论可复核（对应 FR-002、FR-003、FR-004），不作为实现方案的规定；行文主线仍是"结论可信、可复现、可排期"的用户价值。
- SC 条款以覆盖率、条目数、可复算口径与偏差阈值为度量（SC-001/002/003/004/006/008 均为 100% 计数量；SC-005 为可独立复算的时长推导，误差 < 1%），均可在不依赖具体实现的前提下判定。
- 澄清项已在 `spec.md` 的 Clarifications 章节闭环：`/speckit.specify` 阶段 3 项（交付边界、是否需外部系统、性能判定口径）+ `/speckit.clarify` 阶段 5 项（默认异常集、上下文属性传递、退避安全边界、配置单一来源、`retryCount` 语义），共 8 条问答、无重复，无遗留 `[NEEDS CLARIFICATION]` 标记。
- 基线事实（本次实测）：`mvn -o -pl leaf-base-retry test` = **30 执行 / 0 失败**；`GXRetryUtilTest` 21 条通过、耗时 16.57s（真实退避 sleep 占用墙钟）。上游语义已按 `spring-core-7.0.9-sources.jar` / `spring-context-7.0.9-sources.jar` 源码逐条核对（`RetryTemplate` 回调时序、`RetryException.getRetryCount` 定义、`RetryPolicy.Builder` 的 `timeout`/`jitter` 默认值、`ExceptionTypeFilter` 的 cause 遍历与 exclude 优先规则、`RetryAnnotationBeanPostProcessor` 不读取全局默认值）。
- 第 2 轮校验（宪法对齐）：`.specify/memory/constitution.md` 已逐条比对并回写规格——宪法 V 明确"各子包普遍标注 `@NullUnmarked`"，故 F-20 已由"包级 opt-out 即缺陷"修正为"公共 API 未标注 `@Nullable` 的空值契约缺失"；宪法 V 禁止绕过 `GXMdcWrapper*`，故新增 E-24 与 FR-021 覆盖异步重试的 MDC/TraceId 传播；宪法 IV 的"类型安全配置 + `additional-spring-configuration-metadata.json`"作为 F-15 的判定依据。
- 判定：所有条目通过（2 轮校验后无残留问题），规格可进入 `/speckit.clarify` 或 `/speckit.plan`。
