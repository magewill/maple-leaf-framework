# Implementation Plan: leaf-base-retry 正确性、线程安全与性能审查

**Branch**: `010-retry-module-audit`（`before_plan` hook 未注册，未创建 git 分支） | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-retry-module-audit/spec.md`

## Summary

把 21 条审查发现（高 5 / 中 11 / 低 5）与 5 条既定契约（P-01 ~ P-05）落成"可实测、可回归、可迁移"的交付：**审查报告（`audit-report.md`）+ 上下文与计数语义修复 + 可选超时/抖动 + 缓存与异步健壮性 + 监听器可观测性修复 + 测试补强 + 文档重写**。

技术路径（Phase 0 已逐条确认，详见 [research.md](./research.md)）：

1. **上下文可传递**（FR-015）：每次尝试仍新建 `GXRetryContext`，但以上一次实例为 `parent` 并**复制其属性**，`retryCount`/`lastThrowable` 仍表示本次尝试；`from(RetryState)` 增加带 `parent` 的重载。
2. **计数语义统一**（FR-023）：恢复回调改用 `RetryException.getRetryCount()`（重试次数，首次执行为 0）；回调侧新增 `getAttemptIndex()` 作为同值别名，`getRetryCount()` 保持兼容；3 条既有断言按新语义更新。
3. **超时与抖动可选**（FR-020）：新增 `GXRetryOptions` 承载 7 个策略参数，仅当显式传入且 > 0 时才调用 `RetryPolicy.Builder.timeout/jitter`；既有重载全部委托到 `defaults()`，默认仍为"无超时、无抖动"，上限不变。
4. **缓存与异步健壮**（FR-019、FR-021）：缓存键纳入 `timeout`/`jitter`；淘汰改为"超阈值后在专用锁内裁剪到 3/4"，主路径保持无锁；异步路径改用 `GXMdcThreadUtils.supplyAsync` 传播 MDC/TraceId，并新增 `GXCancellationToken` 让取消在下一次尝试前生效。
5. **可观测性与装配**（F-09 ~ F-14、FR-018）：`beforeRetry` 判据由恒假的 `== 0` 改为 `== 1`；日志文案去掉"必然重试"暗示并全部加 `isEnabled` 守卫；日志级别改为阈值语义并支持运行时变更；模板监听器改由 `GXRetryConfig` 构造注入提供（Spring 路径用容器 Bean，无参构造回退进程内单例供工具类使用）；默认常量单点定义在 `GXRetryConfig`，`GXRetryUtil` 引用之。
6. **文档与测试**（FR-017、FR-022、FR-016）：手册以 Spring Framework 7 `org.springframework.core.retry` 为唯一基线重写，新增"默认异常集风险"专章与三入口 `retryCount` 对照表；新增 Spring Boot 装配测试、并发测试、超时/抖动/取消/MDC 测试。

## Technical Context

**Language/Version**: Java 21（根 POM `<release>21</release>`；`maven-compiler-plugin` 3.15.0 + ErrorProne（`NullAway:ERROR`、`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`））

**Primary Dependencies**: Spring Boot 4.1.1 / Spring Framework 7.0.9（`spring-core` 的 `org.springframework.core.retry.*`、`spring-context` 的 `org.springframework.resilience.annotation.EnableResilientMethods`）；`leaf-base-framework 4.3.0-SNAPSHOT`（`GXBusinessException`、`GXSpringContextUtils`、`GXMdcThreadUtils`）；Lombok（`@Slf4j`）；Hutool `MapUtil`。**不含 spring-retry**；不新增任何第三方依赖

**Storage**: N/A（库模块，无持久化；仅 JVM 内静态缓存）

**Testing**: JUnit 5（Jupiter，随 `spring-boot-starter-test`）；新增 `@SpringBootTest` 装配测试；并发与取消用例用 `ExecutorService`/`CountDownLatch`；`GXMdcThreadUtils` 用于 MDC 断言；无外部依赖，全部可在离线构建中执行

**Target Platform**: JDK 21 库（jar，随 `leaf-framework` 聚合门面下发）；Windows（CRLF 工作区）与 Linux（LF）双平台结果一致

**Project Type**: library（Maven 多模块仓库中的单个 `leaf-base-*` 模块）

**Performance Goals**: 不设吞吐/时延验收门禁（SC-005）。只保留可复算口径：单次调用容器类型查询 1 次、键拷贝 1 次（`LinkedHashMap` + 不可变包装）、`RetryTemplateCacheKey` 分配 1 个、每次尝试 `GXRetryContext` 1 个（复制前递时为 O(属性数)）；每失败尝试日志 1 条；最坏阻塞时长由参数上限反推。**工程目标（非门禁）**：既有测试套件墙钟由 16.57s 降至 ≤ 8s（把默认 1s/2s 退避的用例改为显式 100ms，仅保留 1 条真实默认退避用例）

**Constraints**: 只改 `leaf-base-retry` 模块 + 2 处文档（`docs/leaf-base-retry开发手册.md`、`AGENTS.md`）；根 POM 与其它模块不改；不新增依赖、不新增配置属性（FR-018）；不改既有公开方法签名与默认行为（仅新增重载/新增方法；FR-023 的计数值变更按兼容说明执行）；ErrorProne + NullAway 零错误

**Scale/Scope**: 11 个生产源码文件 + 4 个既有测试类；21 条发现（高 5 / 中 11 / 低 5）；新增 3 个生产类型（`GXRetryOptions`、`GXCancellationToken`、`GXRetryCancelledException`）+ 1 个 `package-info`，修改 4 个生产文件，新增 3 个测试类、修改 4 个测试类，重写 1 份手册 + 修改 `AGENTS.md`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 依据 / 约束 |
| --- | --- | --- |
| I. 模块自治与按需装配 | PASS | 仍只依赖 `leaf-base-framework`；保持 `@Configuration` + 组件扫描（FR-018 明确不迁自动装配），不改下游装配前提；无新增可选中间件 |
| II. 稳定契约优先 | PASS（附兼容性声明） | 公开方法签名与默认参数不变；**唯一的数值语义变更**是恢复上下文 `retryCount`（3→2、不可重试时 1→0），由 Clarifications 显式决策并由 FR-023 要求兼容说明 + 断言更新；删除 `GXRetryListener.clearCache()` 属内部诊断方法（仅测试引用），在手册"升级必查"中列明 |
| III. 统一响应与统一异常 | PASS | 不新增对外响应；取消语义沿用既有 `GXBusinessException` 包装约定，不引入新的异常传播形状 |
| IV. 显式配置与双配置源 | PASS（含已知偏差） | 本计划**不新增任何配置键**（FR-018 显式否决外部化），因此不触发 `@ConfigurationProperties` 与元数据要求；`maple.framework.retry.log.level` 沿用既有系统属性通道，偏差已登记在 `contracts/observability-contract.md` §4 |
| V. 空值安全与上下文纪律 | PASS | 异步路径改用 `GXMdcThreadUtils.supplyAsync`（复用既有包装能力）；新增 API 在 `@NullUnmarked` 包内对可能为空的参数/返回值补 `@Nullable`；上下文属性仍由 `ConcurrentHashMap` 承载，跨线程只读快照 |
| VI. 测试与静态检查门槛 | PASS | 修 bug 先补"旧失败、新通过"回归；新增 `GXRetry*SpringBootTest`；并发/取消/MDC 用例可离线执行；镜像：不改任何编译参数 |
| VII. 命名与包结构一致性 | PASS | 新增类型 `GXRetryOptions`（`GX<能力>Options`）、`GXCancellationToken`（`GX<概念>`）；工具类保持历史名 `GXRetryUtil`（宪法允许历史例外，不重命名）；日志 ASCII |
| 附加约束 2/3（依赖治理） | PASS | 零新增依赖；缓存淘汰用 `ConcurrentHashMap` + 专用锁对象实现，不引入 Caffeine/Guava |

**Post-Phase 1 复评**：`contracts/` 三份契约（API、策略、可观测性）与 `data-model.md` 未引入新的原则冲突；`GXRetryOptions` 与 `GXCancellationToken` 的必要性已在 `Complexity Tracking` 说明；复评结论 **PASS**。

**兼容性声明（非违反项，需在 PR 与手册中体现）**：

| 变更 | 影响面 | 迁移路径 |
| --- | --- | --- |
| 恢复上下文 `retryCount` 语义改为"重试次数" | 用该值做告警/对账/重试次数统计的下游 | 数值 -1（3 次尝试由 3 变 2；不可重试由 1 变 0）；如需"尝试数"改用 `getAttemptIndex() + 1` |
| 新增 `getAttemptIndex()` / `GXRetryOptions` / `GXCancellationToken` / 异步 token 重载 | 无破坏性影响 | 加法式 API，可不使用 |
| 删除 `GXRetryListener.clearCache()` 与类型缓存 | 直接调用该静态方法的代码（模块内仅测试） | 删除调用；缓存本身从未生效（research D-08） |
| `beforeRetry` 日志由"恒不触发"变为"首次重试时输出 1 条 INFO" | 日志量增加 1 条/重试链 | 如需静默设 `maple.framework.retry.log.level=WARN` |
| 日志级别语义改为阈值 + 支持运行时变更 | 设 `OFF` 的用户由"全开"变为"全关"（修正行为） | 无需迁移；属缺陷修复 |

## Project Structure

### Documentation (this feature)

```text
specs/010-retry-module-audit/
├── plan.md                       # 本文件
├── research.md                   # Phase 0：D-01 ~ D-12 决策 + R-01 ~ R-04 事实核对
├── data-model.md                 # Phase 1：发现/证据/矩阵/回归用例/交付物实体
├── quickstart.md                 # Phase 1：可执行验证指南
├── contracts/
│   ├── retry-api-contract.md     # 公开 API 增量与语义（上下文、重试次数、取消、选项）
│   ├── retry-policy-contract.md  # 策略契约（默认异常集、include/exclude 匹配、退避与缓存键）
│   └── observability-contract.md # 日志事件表、级别开关、禁止事项
├── audit-report.md               # 实施期产出：审查报告（与 spec 摘要互引，FR-011）
└── checklists/requirements.md    # 规格质量清单（16/16 通过）
```

### Source Code (repository root)

```text
leaf-base-retry/
├── pom.xml                                            # 不改（零新增依赖）
└── src/
    ├── main/java/cn/maple/retry/
    │   ├── config/
    │   │   ├── GXRetryConfig.java                     # 改：默认常量提升为 public static final；新增 timeout/jitter 重载；不再 new 监听器（改为入参）
    │   │   ├── GXRetryOptions.java                    # 新增：7 个策略参数的不可变载体（含校验与 defaults()）
    │   │   └── package-info.java                      # 保持
    │   ├── context/
    │   │   ├── GXRetryContext.java                    # 改：复制前递（parent 链 + 属性复制）；新增 getAttemptIndex()
    │   │   ├── GXCancellationToken.java               # 新增：取消句柄（AtomicBoolean 封装）
    │   │   └── package-info.java                      # 保持
    │   ├── exception/
    │   │   ├── GXRetryCancelledException.java         # 新增：继承 GXBusinessException（宪法 III/命名）
    │   │   └── package-info.java                      # 新增（新包必须带 package-info）
    │   ├── listener/
    │   │   └── GXRetryListener.java                   # 改：beforeRetry 判据、文案、级别阈值、isEnabled 守卫、删除类型缓存
    │   └── util/
    │       └── GXRetryUtil.java                       # 改：引用单点常量；缓存键含 timeout/jitter；淘汰原子化；异步走 GXMdcThreadUtils + 可取消
    └── test/java/cn/maple/retry/
        ├── config/GXRetryConfigTest.java              # 改：补 include/exclude 生成、默认映射、NaN、timeout/jitter 校验
        ├── config/GXRetrySpringBootTest.java          # 新增：Bean 装配、@ConditionalOnMissingBean 冲突、@Retryable 与默认值无关
        ├── context/GXRetryContextTest.java            # 改：补跨尝试属性传递、parent 链、跨线程快照
        ├── listener/GXRetryListenerTest.java          # 改：替换 clearCache 用例；补级别阈值与"未重试失败"文案
        └── util/
            ├── GXRetryUtilTest.java                   # 改：更新 3 条计数值断言；默认退避用例收敛为 1 条
            ├── GXRetryUtilConcurrencyTest.java        # 新增：32 线程 × 8 组参数、缓存上限与淘汰原子性
            └── GXRetryUtilAsyncTest.java              # 新增：超时、抖动、取消、MDC 传播、RejectedExecution

docs/leaf-base-retry开发手册.md                        # 重写（Spring 7 基线 + 风险专章 + retryCount 对照表）
leaf-base-retry/AGENTS.md                              # 改：§3 补日志级别与 retryCount 语义、§4 补测试清单
```

**Structure Decision**: 保持"单模块 + 包内分层"现状，不新增子工程。生产代码新增 3 个类型（`GXRetryOptions`、`GXCancellationToken`、`GXRetryCancelledException`）与 1 个 `package-info`，修改 4 个既有类型；测试侧新增 3 个类、修改 4 个类；文档改 2 份。`pom.xml`、根 POM、其它模块**不改**。

## Phase 0 → Phase 1 结论摘要

- **R-01（默认策略覆盖面）**：`GXRetryUtil` 未显式传参时实际包含 `Exception.class → true`，`GXBusinessException extends RuntimeException`（`leaf-base-framework` 第 16 行），因此业务异常确实被重试；既有用例 `testRetryOperation_DefaultParams_AllAttemptsFail` 已用 `RuntimeException` 证明默认重试 3 次。
- **R-02（上游回调时序）**：`RetryTemplate.execute` 先 `retryState.increaseRetryCount()` 再 `beforeRetry`（spring-core 7.0.9 `RetryTemplate.java:171-172`），故 `beforeRetry` 中 `getRetryCount() == 0` 恒假 → F-09 成立，修复判据改为 `== 1`。
- **R-03（`retryCount` 三个数值）**：`RetryException.getRetryCount()` 返回 `getSuppressed().length`（`RetryException.java:84-87`）= 重试次数；`getExceptions().size()` = 尝试数；回调侧 0 基序号与"重试次数"数值相同 → FR-023 只改恢复侧一处即可对齐。
- **R-04（能力可用性）**：`RetryPolicy.Builder` 在 7.0.9 已提供 `timeout(Duration)`（默认 `ZERO`）与 `jitter(Duration)`（默认 `0`）（`RetryPolicy.java:164,181`），本计划无需自建退避实现；`GXMdcThreadUtils.supplyAsync(Supplier, Executor)` 可直接替换 `CompletableFuture.supplyAsync`。
- 其余设计决策（常量单点、缓存淘汰、取消语义、监听器改造、测试与文档）见 [research.md](./research.md)。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| 新增 `GXRetryOptions` 类型 | 需要在不破坏 8 个既有重载的前提下承载 `timeout`/`jitter` | 直接加重载会产生"次数 × 间隔 × 超时 × 抖动 × 异常映射"的组合爆炸（≥ 32 个重载）；改用可变 setter 会破坏不可变与线程安全 |
| 新增 `GXCancellationToken` + `GXRetryCancelledException` | `CompletableFuture.cancel(true)` 无法中断正在 `Thread.sleep` 的重试链（FR-019 要求"异步取消可中断"） | 只改文档不满足 FR-019；改用 `Future` 返回类型会破坏既有 `CompletableFuture` 契约；用 `AtomicBoolean` 裸参数暴露到公开 API 过于原始且无法携带取消原因 |
| 缓存淘汰引入专用锁对象 | 需要"超阈值后裁剪"原子化，且不得把锁加到主路径 | 全量加锁会污染热路径；引入 Caffeine 违反"零新增依赖"；`compute` 内淘汰会与自身更新冲突 |
