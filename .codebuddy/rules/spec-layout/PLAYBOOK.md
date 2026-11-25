# 规格文档维护 Playbook

**对应规则版本**: 1.2.0 | **更新**: 2026-11-25

本文件按需读取，不自动进入上下文。执行规格文档迁移、收敛、归档或提交前自检时再打开。

## 1. 目录骨架

每个 `specs/<feature>/` 必须存在：

```text
changes/
  INDEX.md
  CR-YYYYMMDD-NNN.md
history/
  README.md
  tasks-YYYY-MM-DD.md
```

`changes/INDEX.md` 固定表头：

```markdown
| 编号 | 类型 | 状态 | 影响 | 标题 | 日期 |
| --- | --- | --- | --- | --- | --- |
```

`history/README.md` 固定表头：

```markdown
| 文件 | 日期 | 内容 |
| --- | --- | --- |
```

## 2. CR 模板

```markdown
# CR-YYYYMMDD-NNN：标题

- 类型：`bugfix` / `task` / `clarification` / `converge`
- 状态：`proposed` / `in-progress` / `applied` / `rejected`
- 影响：`spec.md#FR-xxx` / `tasks.md#Txxx`
- 日期：YYYY-MM-DD
- 关联命令：clarify / converge / implement / review / manual
- 原始记录位置：`Session YYYY-MM-DD` / `Phase N` / `Txxx-Tyyy`

## 背景 / 现象

## 变更前

## 变更后

## 验收标准

- [ ] ...

## 代码 / 测试落点

- ...
```

至少保留类型、状态、影响、背景、变更前后、验收标准、代码/测试落点；日期、关联命令、原始记录位置建议补充。

## 3. CR 粒度与聚合

- 常规变更：一个 CR 对应一个事件（一次 Clarifications Session、一个 Convergence Phase、一个 bugfix/任务批次）。
- 历史归档：允许按 feature + 类型创建聚合 CR，但必须满足：
  - 标题注明“归档”；
  - 影响列出全部 Session / Phase / 任务范围；
  - 正文保留每个原始块；
  - `changes/INDEX.md` 只登记一行。
- `NNN` 在仓库内全局递增；同一天多个 CR 按创建顺序编号。
- 状态流转：`proposed` → `in-progress` → `applied`；`rejected` 必须写明原因。归档型 CR 可直接标 `applied`，但必须有验收证据。

## 4. tasks.md 占位

无 active 任务时：

```markdown
## Active Tasks

当前无 active 任务。

新增任务请先创建 `changes/CR-YYYYMMDD-NNN.md` 并在 `changes/INDEX.md` 登记，不要直接追加到本文件。

历史任务与完成证据见 `history/README.md`，变更记录见 `changes/INDEX.md`。
```

有 active 任务时只保留 active 行，不保留已完成行。

## 5. 命令清理清单

### `/speckit.clarify`

- 把 `## Clarifications` / `### Session YYYY-MM-DD` 移入 clarification CR。
- 在 `changes/INDEX.md` 登记。
- 恢复 `spec.md`，不保留追加块。

### `/speckit.converge`

- 把 `## Phase N: Convergence` 移入 converge CR。
- `tasks.md` 只保留一行 active 引用；无 active 时用占位模板。
- 完整任务与执行记录写入 `history/tasks-YYYY-MM-DD.md`。

### `/speckit.implement`

- 已完成任务行与验证记录写入 `history/tasks-YYYY-MM-DD.md`。
- 从 `tasks.md` 删除已完成行。

### 非 Convergence 已完成批次

Review Follow-up、测试补强、Architecture Guard、PR 评审修复等：

- 按 `task` 类型建 CR；
- 任务行与验证记录写入 history；
- 不把批次正文留在 `tasks.md`。

### `/speckit.token-budget.compact`

- 只作用于 `spec.md`、`plan.md`、`tasks.md`、`research.md`、`data-model.md`、`contracts/`、`quickstart.md`、constitution。
- 跳过 `changes/` 与 `history/`。
- `compact.exclude_paths` 必须包含 `changes/` 与 `history/`。
- 若生成 `changes/**/*.full.md` 或 `history/**/*.full.md`，立即删除。

### `/speckit.archive.run`

- 已安装：归档 `changes/`，`history/` 只在归档时读取，排除 `*.full.md`。
- 未安装：按手动归档流程执行；归档位置按团队约定。

## 6. history 表结构

主任务表固定：

```markdown
| 任务 | 完成日期 | 结论 / 证据 |
| --- | --- | --- |
```

原始验证、评审或执行记录可使用独立小节与主题表，例如：

```markdown
## 实施期验证记录（YYYY-MM-DD）

| 任务 | 落地内容 | 验证 |
| --- | --- | --- |
```

但主题表不得替代主任务表。

其他规则：

- 同一天多次写入合并到同一 `tasks-YYYY-MM-DD.md`。
- 不同日期拆分为不同文件。
- 每次写入后，在 `history/README.md` 增加一行索引。
- `history/README.md` 只做索引，不堆正文。

## 7. 上下文读取

日常默认跳过：

- `**/*.full.md`
- `history/tasks-*.md` 正文

日常允许读取 `history/README.md` 索引。`history/tasks-*.md` 正文仅在归档或用户明确要求时读取。

`.full.md` 正文只允许在 `/speckit.token-budget.restore` 时读取；`/speckit.token-budget.usage` 可以统计 token 数，但不得注入正文。

`.full.md` 的 guard：优先以仓库根 `AGENTS.md` 为准；根文件不存在时，以 `.specify/extensions/token-budget/token-budget-config.yml` 的 `guard_memory_file` 与 `compact.exclude_paths` 为准。

## 8. 提交前自检

```text
每个 specs/<feature>/ 均存在 changes/INDEX.md 与 history/README.md
changes/INDEX.md 中每个 CR 行都有对应文件，且状态与 CR 头部一致
rg -n "^## Clarifications" specs -g "spec.md"                  -> 0
rg -n "^## Phase \d+: .*onvergence" specs -g "tasks.md"        -> 0
rg -n "^- \[[xX]\]" specs -g "tasks.md"                        -> 0
rg --files -g "*.full.md"                                      -> 0
```

## 9. 常见边界

- 需求变更必须在合并/发布里程碑并入 `spec.md`，并在 INDEX 标 `applied`；纯 bugfix 不修改 `spec.md`。
- 已完成任务不得回填到 `tasks.md`；需要追溯时写 history。
- `plan.md` 只在方案或架构实质变化时更新；执行记录和验证结论不进 `plan.md`。
- `audit-report.md` / `review-report.md` 保留审查证据，但执行日志与验证结论仍写入 history。
- 规则修改后必须新建会话才会生效。
