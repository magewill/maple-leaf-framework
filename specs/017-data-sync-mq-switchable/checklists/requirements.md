# Specification Quality Checklist: leaf-base-data-sync 正确性 / 线程安全 / 性能审查与消息中间件可切换改造

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-27
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)

  - 说明：本规格属于"既有模块审查 + 集成改造"，证据段（E-01～E-20）按 `specs/016` 的既有风格引用文件与行号，属于审计结论的可复核依据；需求段（FR-001～FR-042）与成功标准（SC-001～SC-010）均以行为、结局、可观测结果描述。FR-001/FR-002/FR-003 显式点名既有契约类型，因为"不得破坏的契约"本身就是需求对象。

- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain

  - 说明：本规格对 5 个高影响决策（互斥单选 / 抽象落点 / 默认 MQ / 顺序消费 / 真实验证方式）采用"取合理默认 + 显式记录"的方式处理，记录在 `## Clarifications` 的 `Session 2026-10-27` 小节，供 `/speckit.clarify` 复核推翻。

- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable

  - 说明：SC-004 给出"框架侧单条消息耗时下降 ≥ 80%"与基准方法（10 万条本地微基准）；SC-005 给出并发规模（32 线程 × 1000 条）与重复次数；SC-003 给出重投上限（≤3 次）；SC-002 给出"无结局路径 = 0"的绝对判定。

- [x] Success criteria are technology-agnostic (no implementation details)

  - 说明：除 SC-009 必须引用 `mvn` 作为仓库统一门禁命令外，其余标准均以用户/运维可观测结果表述。未出现框架、数据库、缓存等实现细节作为成功判据。

- [x] All acceptance scenarios are defined
- [x] Edge cases are identified

  - 说明：覆盖空载荷、数组载荷、库表缺失、库表命名非法与命名冲突、DDL 类型、处理器异常/返回 null/抛 Error、重复投递、Broker 断连、双 MQ 参数并存、运行期刷新装配期键、下游同名 Bean 冲突。

- [x] Scope is clearly bounded

  - 说明：`## Out of Scope` 明确排除运维、幂等、批量/事务/顺序消息、跨 MQ 双跑、新建通用抽象模块、修改兄弟 MQ 模块。

- [x] Dependencies and assumptions identified

  - 说明：依赖载体已定案为 `leaf-base-rocketmq`（唯一坐标），并以 `<optional>true</optional>` 声明；传递依赖影响（`rocketmq-client` / `nacos-client` / `fastjson` 1.x）在 F-17、FR-044、FR-047、SC-011、SC-012 与 Assumptions 中成对给出"影响 + 约束 + 验证方式"。

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria

  - 说明：FR-004～FR-012 对应 US2 的 4 组验收场景；FR-013～FR-017 对应 US4 场景 1/3；FR-018～FR-023 对应 US4 场景 1/2 与 SC-004/SC-005；FR-024～FR-031 对应 US3 的 4 组场景；FR-032～FR-036 对应 US5；FR-037～FR-042 为表达/文档/测试门禁；FR-043～FR-047 为依赖载体与复用边界，对应 SC-011/SC-012/SC-013 与"业务零改动"前提。

- [x] User scenarios cover primary flows

  - 说明：US1 审查可复核（P1）、US2 结局确定与毒消息治理（P1）、US3 MQ 切换（P2，需求直接来源）、US4 性能与并发可量化（P2）、US5 装配/配置/追踪（P3）。

- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- 待实施期定案的三项已标注在证据段，不阻塞规划，但必须在 `/speckit.plan` 中列为先决验证任务：
  1. **E-02 / F-03**：`@Lazy` + `@RabbitListener` 是否导致端点未注册（结论将决定"生产是否真的在消费"，并影响 US1 场景 3 的验收基线）；
  2. **E-08 / F-04**：Canal 实际投递载荷是单个对象还是 JSON 数组（结论决定 FR-009 的兼容范围与实现优先级）；
  3. **E-22 / F-18**：`rocketmq-spring-boot-starter 2.3.6` 在 Spring Boot 4.1 基线下的消费容器（`@RocketMQMessageListener`）是否可正常注册与消费（本仓库从未使用该路径）；不可用时需给出替代装配方案并登记。
- 本规格未创建任何分支：`.specify/extensions.yml` 中未注册 `before_specify` / `after_specify` 钩子（仅有 `after_implement` 的 review 可选钩子）。
- 宪法合规要点（`.specify/memory/constitution.md` v1.3.0）：FR-001/002/003 对应原则 §II 稳定契约；FR-032/034/045 对应 §IV 双配置源与配置元数据；FR-014/016/036 对应 §V 空值与上下文纪律；FR-040/041/042 对应 §VI 测试与静态检查门槛；FR-038 对应附加约束 §9 能力完备性登记；FR-043/044/047 对应附加约束 §2/§3 依赖治理与根 POM 版本集中；FR-046 对应附加约束 §4 反重复造轮子与 §8 反过度设计的边界划分；`## Out of Scope` 中"不新建通用抽象模块"对应附加约束 §8。
