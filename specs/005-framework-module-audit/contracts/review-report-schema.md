# Contract: 审查报告结构（review-report）

**Feature**: `005-framework-module-audit` | **Version**: 1.0 | **Date**: 2026-10-22

本契约约束 `specs/005-framework-module-audit/review-report.md` 的章节顺序、条目字段与校验规则。报告不符合本契约时，视为**审查未完成**（对应 SC-002）。

---

## 1. 章节顺序（固定，不得增删顶层章节）

| # | 章节 | 必含内容 |
| --- | --- | --- |
| 1 | 标题与元信息 | 模块名、审查日期、审查基线（commit 或快照说明）、审查范围（309 文件 / 26 包 / 65 测试类） |
| 2 | 环境与基线 | research D8 表格的实测值：构建命令、既有基线 `Tests run: 518`、交付验收 `tests=550`（518 既有 + 32 审计）、`@Test` 计数口径说明（`@Test\b` 逐方法，排除 `@TestPropertySource`）、运行 JDK、Spring Boot 版本 |
| 3 | 结论摘要 | 四维度结论表 + 子节 GateDecision（`recommendation` / `blockers` / `conditions`） |
| 4 | 证据清单 | `E-NN` 全量，按编号升序，每条含 `kind` / `location` / `claim` |
| 5 | 发现清单 | `F-NN` 全量，按严重级别降序、级别内按编号升序；每条按 §2 字段展开（含 `状态`） |
| 6 | 上下文纪律矩阵 | 全量 `ContextDisciplineEntry` |
| 7 | 并发与生命周期矩阵 | 全量 `LifecycleEntry` |
| 8 | 失败收敛清单 | 全量 `FailureConvergenceEntry` |
| 9 | 性能口径表 | 6 条 `PerformanceMetric`，逐条给公式与冒烟断言 |
| 10 | 契约对照表 | 宪法条款 / `AGENTS.md` 条目 × 实现位置 × 判定 × 需要的文档改动 |
| 11 | 未覆盖、缺口与口径 | 子节：零测试覆盖类型清单、未验证假设、未涉及的运行形态、判定口径速查、审计用例命名与断言规范、Finding 状态机跟踪表 |
| 12 | 修订说明 | 本轮推翻/修正的结论（`REVISED`），含原判定与新判定的理由 |
| 13 | 发现 → 修复任务映射与排期 | 每条发现对应修复动作、依赖、兼容性等级、回归用例、建议批次（对应 FR-015 / SC-008） |
| 14 | 复现指南索引 | 指向 `quickstart.md` 对应小节 + 用例全名 |

---

## 2. 发现条目模板（字段全部必填，无值填 `N/A` 并说明原因）

```markdown
### F-NN [HIGH|MEDIUM|LOW] 标题（现象 + 影响）

- **维度**: STABILITY / CORRECTNESS / THREAD_SAFETY / PERFORMANCE / SECURITY_BOUNDARY
- **判定**: DEFECT / RISK / CONTRACT_DEVIATION / ESTABLISHED_CONTRACT / UNVERIFIED_ASSUMPTION
- **位置**: `util/xxx.java:12-34`（可多行，多个位置用 `;` 分隔）
- **触发条件**: 具体输入/运行形态/时序
- **影响**: 谁在什么场景下看到什么后果
- **证据**: E-01, E-03
- **复现**: 命令或步骤 → 预期现象；不可执行时写"不可执行 + 缺失条件"
- **回归测试设计**: 输入 = …；断言点 = …；修复后应由 X 变为 Y
- **兼容性影响**: NONE / BEHAVIOR_CHANGE / BREAKING（+ 一句说明）
- **修复方向**: …（需独立审批）
- **状态**: DELIVERED
```

---

## 3. 严重级别定义（唯一口径，不得自由裁量）

| 级别 | 判定条件（满足任一） |
| --- | --- |
| **HIGH** | ① 导致跨请求/跨用户数据或身份错配；② 敏感数据（口令/密钥/内部路径）可从外部观测；③ 安全默认值不安全且无需额外配置即生效；④ 契约承诺（快照、传播、校验）在正常路径即不成立；⑤ 失败静默导致数据错误且不可观测 |
| **MEDIUM** | ① 在特定配置或负载下失效；② 资源/结构无上限但增长需外部推动；③ 语义含混导致调用方需额外约定；④ 性能开销随规模放大但有可行绕行 |
| **LOW** | ① 仅影响可读性/可维护性；② 死代码、重复配置；③ 已有绕行且无数据影响 |

**升降级规则**：`HIGH` 降 `MEDIUM` 必须给出"触发条件不具备"的证据；`MEDIUM` 升 `HIGH` 必须满足上表任一条并补 `EXECUTABLE` 证据。

---

## 4. 证据等级与引用规则

| 等级 | 定义 | 可支撑的最高判定 |
| --- | --- | --- |
| `SOURCE_FACT` | 本仓库代码位置可核对 | `DEFECT` |
| `DEPENDENCY_FACT` | 依赖库源码/官方文档可核对 | `DEFECT`（须写清版本） |
| `EXECUTABLE` | 用例或命令已执行并记录输出 | `DEFECT` |
| `READ_INFERENCE` | 仅阅读推断，未验证 | `RISK` / `UNVERIFIED_ASSUMPTION` |

- `HIGH` + `DEFECT` 至少需要 1 条 `SOURCE_FACT` 或 `EXECUTABLE`。
- 每个 `EXECUTABLE` 证据必须写 `verifiedBy`（执行时间 + 摘要），无执行记录的不得标 `EXECUTABLE`。
- 引用依赖源码时必须写清坐标与版本（如 `spring-boot 4.1.1`）。

---

## 5. "可复现"判定标准

一条结论算"可复现"，必须同时满足：

1. 给出了**唯一**的起始状态（配置值、线程形态、输入样例）；
2. 给出了**可执行**的动作（用例全名 + 方法名，或完整命令）；
3. 给出了**可观察**的现象（日志文本、返回值、结构 `size()`、异常类型），而不是"行为异常"；
4. 现象与结论一一对应，换环境后现象仍成立（不依赖本机路径/时间）。

不满足任一项的结论，`reproduction` 必须写明缺失项并降级。

---

## 6. 与 `spec.md` 的编号契约

1. `E-01…E-22`、`F-01…F-21` 必须原样出现在报告中；缺一条即视为覆盖不全。
2. 被推翻的结论保留编号，在条目内加 `> REVISED: 原判定 … → 现判定 …，理由 …`，并同步写入第 12 章。
3. 新增证据/发现从 `E-23` / `F-22` 连续编号，不得占用未使用的小号。
4. 报告中出现但 `spec.md` 未收录的高危项，必须在第 12 章说明"为何规格摘要未包含"（规格摘要在先，发现的补充在后）。

---

## 7. GateDecision 段模板（报告第 3 章末尾，逐字使用下列结构）

```markdown
## 准入门槛结论

- **审查范围**: leaf-base-framework（309 文件 / 26 包），基线 <commit/日期>
- **高危缺陷数**: N（F-xx, F-yy, …）
- **结论**: NOT_SUITABLE_AS_BASELINE | SUITABLE_WITH_CONDITIONS
- **进入条件**（仅 SUITABLE_WITH_CONDITIONS）:
  1. …
- **理由**: …（≤3 句，必须引用高危编号）
```

**校验**：`结论` 与 `高危缺陷数` 必须自洽——`N > 0` 时不得直接给"无条件下可用"。

---

## 8. 自动化可校验项（供 `/speckit.tasks` 生成检查脚本）

| 检查 | 规则 |
| --- | --- |
| C-01 | 报告包含 `E-01`…`E-25` 全部证据与 21 个 `F-NN` 标题 |
| C-02 | 每个 `F-NN` 段落含 11 个必填字段标签（§2 的 10 项 + `状态`） |
| C-03 | 每个 `HIGH` 段落含 `EXECUTABLE` 或 `SOURCE_FACT` 证据引用 |
| C-04 | 第 9 章（性能口径表）含 6 个路径关键词：`XSS_REWRITE`、`BODY_CACHE`、`RATE_LIMIT`、`CONCURRENT_TOOLS`、`EVENT_DISPATCH`、`REFLECTION_COPY` |
| C-05 | GateDecision 的 `blockers` 集合 = `HIGH + DEFECT` 集合 |
| C-06 | 报告不含绝对耗时阈值断言（正则：`<\s*\d+\s*ms` 出现在"断言"语境） |
| C-07 | 顶层章节 `## 1.`…`## 14.` 齐全、编号连续、顺序与 §1 一致 |
| C-08 | 每条发现的 `状态` 取值 ∈ {`DELIVERED`, `REPRODUCED`, `UNVERIFIED_ASSUMPTION`}，且与第 11 章状态机跟踪表逐条一致 |
| C-09 | `spec.md` 中出现的 `F-NN` 全部在报告第 5 章存在 |
| C-10 | 发现清单按"严重级别降序、级别内编号升序"排列（`HIGH` → `MEDIUM` → `LOW`） |
| C-11 | 第 14 章复现索引按行覆盖全部 `F-NN`：每条发现至少出现在一行"发现"单元格中（含 `F-03 / F-04` 这类合并行） |
| C-12 | 第 2 章"审计用例"行的 `8 个类 / N 个用例` 与第 14 章末的 `8 类 M 用例` 数值必须相等（当前 32，对应 `tests=550` = 518 既有 + 32 审计） |
