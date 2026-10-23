# Phase 1 Data Model: leaf-base-retry 审查实体与矩阵

**Feature**: `010-retry-module-audit` | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

本特性不产生运行时数据模型，需建模的是**审查产物**：发现、证据、三张语义矩阵、性能口径、修复项与回归用例。下列实体既是 `audit-report.md` 的骨架，也是 `tasks.md` 的输入。

## 1. 实体定义

### 1.1 Finding（审查发现）

| 字段 | 类型/取值 | 约束（来自 FR） |
| --- | --- | --- |
| `id` | `F-01` ~ `F-21` | 全局唯一；与 `spec.md` 一致，不得新增未登记的编号 |
| `severity` | `高` / `中` / `低` | 高 = 影响正确性或可观测结果；中 = 有约束但可绕行；低 = 代码质量/契约瑕疵 |
| `dimension` | `正确性` / `线程安全` / `性能` | 每条至少 1 维，可多选（FR-002） |
| `location` | `文件:起-止行` | 必须能唯一定位；行号以当前工作副本为准（FR-002） |
| `trigger` | 构造条件 | 必须可复现（FR-003） |
| `impact` | 影响面（调用方/下游/运维） | 不得写"可能有问题"这类无法判定表述 |
| `evidenceIds` | `E-xx` 列表 | 至少 1 条（FR-002） |
| `regressionDesign` | 输入 + 断言点 + 期望差异 | 必须"旧代码失败、新代码通过"（FR-012） |
| `disposition` | `修复` / `文档收敛` / `既定契约` | 由 Clarifications 决策：F-01 = 文档收敛；F-02/F-03/F-04/F-08 = 修复；P-01 ~ P-05 = 既定契约 |

**校验规则**：`severity` 与 `disposition` 均非空；`高` 级别必须同时具备 `regressionDesign` 与 `trigger`；`既定契约` 不得出现在 `disposition` 为 `修复` 的条目上。

### 1.2 Evidence（证据）

| 字段 | 说明 |
| --- | --- |
| `id` | `E-01` ~ `E-28` |
| `source` | `本模块源码` / `上游 spring-core|spring-context 源码` / `实测输出` / `文档原文` |
| `locator` | 文件 + 行区间（上游源码须给出行号，如 `RetryTemplate.java:171-172`） |
| `statement` | 单句事实陈述，不含推断 |
| `reproducible` | `是` / `否（标注为未验证假设）`（FR-003） |

### 1.3 RemediationItem（修复项）

| 字段 | 说明 |
| --- | --- |
| `rId` | `R-01` ~ `R-12`（与 research 决策 `D-xx` 一对多） |
| `findingIds` | 覆盖的发现编号 |
| `files` | 计划改动的绝对路径清单 |
| `apiImpact` | `无` / `加法式新增` / `行为变更（附迁移）` |
| `testIds` | 对应回归用例 |

### 1.4 TestCase（回归用例）

| 字段 | 说明 |
| --- | --- |
| `tId` | `T-01` ~ `T-20` |
| `kind` | `unit` / `spring-boot` / `concurrency` / `async` |
| `oldBehavior` / `newBehavior` | 旧代码实际结果 vs 新代码期望结果（必须不同才算回归用例） |
| `runtime` | 预期墙钟（`ms`），用于控制套件耗时 |

## 2. 重试语义矩阵（FR-005 / SC-003）

| 入口 | 默认 includes | 默认 attempts / 退避 | 是否受 `GXRetryConfig` 默认值影响 | 实测/源码依据 |
| --- | --- | --- | --- | --- |
| `GXRetryUtil.retryOperation(...)` 默认 | `Exception`（全部，含 `GXBusinessException`、`InterruptedException`；不含 `Error`） | 3 次；1000ms × 2.0，上限 10000ms，无抖动、无总超时 | 仅受类路径常量影响，不受容器 Bean 影响 | `GXRetryUtil.java:29-32,233-239`；`ExceptionTypeFilter` 不匹配 `Error` |
| `RetryTemplate` Bean（`GXRetryConfig`） | `GXBusinessException`、`IOException`、`TimeoutException`、`SocketTimeoutException` | 3 次；同上 | 是（但工具类与 `@Retryable` 都不用） | `GXRetryConfig.java:48-66` |
| `@Retryable`（`@EnableResilientMethods`） | 注解自身 `includes`，默认全部 `Throwable` | 注解自身 `maxRetries`（默认 3 次重试 = 4 次尝试） | **否**（`RetryAnnotationBeanPostProcessor` 只读注解） | `RetryAnnotationBeanPostProcessor.java:96-113` |

**入口 × 异常类型（逐格，SC-003 要求"至少 4 类异常"）**

| 异常类型 | `GXRetryUtil` 默认 | `RetryTemplate` Bean 默认 | `@Retryable` 默认 |
| --- | --- | --- | --- |
| `GXBusinessException` | 重试 | 重试 | 重试 |
| `InterruptedException` | 重试（3 次，中断标志不被清除） | 不重试 | 重试 |
| `IOException` | 重试 | 重试 | 重试 |
| `IllegalArgumentException` | 重试 | 不重试 | 重试 |
| `Error` | 不重试、不包装 | 不重试 | 重试（`includes` 空 → 匹配所有 `Throwable`） |
| `GXRetryCancelledException` | 不重试（带 token 时显式排除） | 不重试 | 重试 |

**判定**：三入口语义不一致（F-04）成立；本计划**不改默认值**（Q1），只做文档化（FR-022）。

## 3. 失败终止矩阵（FR-008 / SC-004）

| 终止原因 | 调用方收到 | 恢复回调是否执行 | 中断标志 | 现状问题 |
| --- | --- | --- | --- | --- |
| 重试耗尽 | 原始异常类型（sneaky throw） | 执行（`retryCount` = 重试次数，修复前为尝试数） | 保持 | F-08（修复） |
| 不可重试异常 | 原始异常类型 | **执行** | 保持 | 语义为"任意失败兜底"，需在手册写明 |
| 退避期间线程中断 | 最后一次业务异常（`RetryException` 的 cause） | **执行** | `interrupt()` 已恢复 | F-06：与"耗尽"不可区分 |
| 超时（`timeout`） | 最后一次业务异常 | **执行** | 保持 | 现状不可达（无超时配置）；D-06 引入后与本行一致 |
| 取消（`GXCancellationToken`） | `GXRetryCancelledException`（新增） | **不执行** | 保持 | 新增能力（D-07） |

**判定**：四类终止在现状下有三类落到同一分支（F-06 成立）；修复只增加"取消"这一独立分支，**不改**其余三类的恢复行为（避免契约破坏）。

## 4. 并发风险矩阵（FR-006）

| 对象 | 共享范围 | 现状保护 | 收敛动作 |
| --- | --- | --- | --- |
| `RETRY_TEMPLATE_CACHE` | 进程级静态 | `ConcurrentHashMap`（读安全） | 键补 `timeout`/`jitter`；淘汰加专用锁裁剪（D-09、D-10） |
| `RetryTemplate` 实例 | 跨线程共享 | 执行期状态方法内局部（P-01） | 不做改动 |
| `GXRetryListener` | 静态实例 + Bean 双份 | 无状态字段 | 改为"Bean 优先、静态回退"单实例（D-08） |
| `GXRetryContext` | 单线程内逐尝试新建 | `ConcurrentHashMap` 属性 | 复制前递 + `parent` 链（D-03）；跨线程只读快照 |
| 异步工作线程 | 调用方 `Executor` | 无 | MDC 包装 + token 取消（D-07） |

## 5. 性能口径（FR-007 / SC-005）

| 指标 | 口径 | 现状取值 | 修复后允许偏差 |
| --- | --- | --- | --- |
| 容器类型查询 | 每次 `retryOperation` 调用 `GXSpringContextUtils.getBean(GXRetryConfig.class)` 次数 | 1 | 1（不变） |
| 键拷贝 | 每次调用 `LinkedHashMap` + `unmodifiableMap` 包装次数 | 1 | 1（不变） |
| 缓存键对象 | 每次调用 `RetryTemplateCacheKey` 分配数 | 1 | 1（+2 字段） |
| 上下文分配 | 每次尝试 `GXRetryContext`（含 `ConcurrentHashMap` + `ConcurrentLinkedQueue`）数 | 1 | 1（复制前递为 O(属性数)，通常 0） |
| 每失败尝试日志 | WARN 条数 | 1（无 `isEnabled` 守卫） | 1（加守卫，不减少条数） |
| 最坏阻塞时长 | `Σ` 退避（`initialInterval × multiplier^k`，受 `maxInterval` 截断） | 参数上限组合 ≈ 353100s（≈98 小时） | 不变（上限未收紧，Q3），由文档告警 + 可选超时缓解 |
| 测试墙钟 | `mvn -o -pl leaf-base-retry test` 总耗时 | 16.57s（`GXRetryUtilTest`） | 工程目标 ≤ 8s（非门禁） |

## 6. 交付物清单

| 交付物 | 出处 | 验收 |
| --- | --- | --- |
| `audit-report.md` | 本计划 Phase 0+1 | SC-001/SC-002：21 条发现八要素齐备、28 条证据可定位 |
| 生产代码修改（4 个文件 + 3 个新类型） | `plan.md` Source Code | ErrorProne/NullAway 零错误；既有签名不变 |
| 测试（新增 3、修改 4） | `plan.md` Source Code | 全绿且含"旧失败新通过"的回归用例 |
| `docs/leaf-base-retry开发手册.md` 重写 | D-12 | SC-008：类名/签名/依赖声明与实现 100% 一致 |
| `AGENTS.md` 条款更新 | D-12 | 与手册不冲突 |
