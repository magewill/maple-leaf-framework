# Phase 0 Research: leaf-base-retry 审查修复设计决策

**Feature**: `010-retry-module-audit` | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

本文件只记录"会影响实现与验收"的决策与事实核对。技术上下文无 `NEEDS CLARIFICATION`：语言、依赖、测试、平台、约束均已在 [plan.md](./plan.md) 的 Technical Context 中确定，5 项产品级歧义已在 spec 的 Clarifications 中闭环。

## 事实核对（R-01 ~ R-06）

| 编号 | 核对项 | 结论 | 证据 |
| --- | --- | --- | --- |
| R-01 | 默认策略是否覆盖业务异常 | **是**。未显式传参时 `GXRetryUtil` 使用 `Map.of(Exception.class, true)`；`GXBusinessException extends RuntimeException` | `GXRetryUtil.java:233-239`；`GXBusinessException.java:16`；既有用例 `testRetryOperation_DefaultParams_AllAttemptsFail` 用 `RuntimeException` 证明默认重试 3 次 |
| R-02 | 监听器"重试开始"判据是否可达 | **不可达**。上游先自增再回调 | `RetryTemplate.java:171-172`（increaseRetryCount 在 beforeRetry 之前） |
| R-03 | `retryCount` 三个数值的关系 | `RetryException.getRetryCount()` = 重试次数；`getExceptions().size()` = 尝试数；回调侧 0 基序号数值上等于重试次数 | `RetryException.java:84-87,94-100`；`GXRetryUtil.java:126,143` |
| R-04 | 超时/抖动能力是否已在上游可用 | **可用**，无需自建 | `RetryPolicy.java:164`（`DEFAULT_JITTER=0`）、`:181`（`DEFAULT_TIMEOUT=ZERO`）；`Builder.timeout/jitter` |
| R-05 | include/exclude 匹配是否遍历 cause 链、exclude 是否优先 | **均成立**；exclude 命中即不重试，includes 为空则匹配所有 | `ExceptionTypeFilter.java:93-121` |
| R-06 | 异步 MDC 是否有现成能力 | **有**：`GXMdcThreadUtils.supplyAsync(Supplier, Executor)` 已实现包装与恢复 | `GXMdcThreadUtils.java:112-120` |

> 未做真机压测与外部依赖验证：本模块无外部依赖，性能结论以可复算口径与同环境重跑为准（SC-005）。

## 决策（D-01 ~ D-12）

### D-01 交付物形态：审查报告独立成文

- **Decision**：`specs/010-retry-module-audit/audit-report.md` 为唯一审查报告；`spec.md` 只保留摘要、证据编号与结论分级，两者互引（符合 FR-011）。
- **Rationale**：SC-001/SC-002 要求"结论可复核 + 发现八要素齐备"；报告独立后，计划阶段的每条任务可直接引用 `F-xx` / `E-xx`，不污染规格。
- **Alternatives considered**：把报告内联进 `spec.md`（`spec.md` 会膨胀到不可评审）；放到 `docs/`（与模块手册混淆，且 `docs/` 面向使用者而非开发评审）。

### D-02 默认常量单点定义

- **Decision**：把 `GXRetryConfig` 的 `DEFAULT_MAX_ATTEMPTS`(3)、`DEFAULT_INITIAL_INTERVAL`(1000)、`DEFAULT_MULTIPLIER`(2.0)、`DEFAULT_MAX_INTERVAL`(10000) 由 `private` 提升为 `public static final`；`GXRetryUtil` 的同名常量删除并改为引用；新增用例断言"两处默认值一致"以防止再次分叉。
- **Rationale**：满足 FR-018 的"单点定义"，且是纯加法（不新增类型、不改方法签名），不影响下游编译。
- **Alternatives considered**：新建 `GXRetryConstant` 常量类（多一个类型，且常量归属更远）；仅在测试里断言一致而保留两份（不满足 FR-018 的"单点"要求）。

### D-03 上下文"复制前递"

- **Decision**：`GXRetryUtil` 用 `AtomicReference<GXRetryContext> previous` 串起尝试链；每次尝试构造 `new GXRetryContext(attemptIndex, lastThrowable, previous.get())`，构造器内先把 `parent` 的全部属性复制到本实例（`parent.getAttributes().forEach(this::setAttribute)`），再发布；`from(RetryState)` 增加 `from(RetryState, @Nullable GXRetryContext parent)` 重载，原签名委托。
- **Rationale**：兑现"属性可跨尝试传递"（FR-015），同时保留"回调拿到的 `retryCount`/`lastThrowable` 不会被后续尝试改写"的现有语义；无属性时复制是空循环，热路径开销可忽略（O(属性数)，通常 0）。
- **Alternatives considered**：全程复用同一实例（B 方案，被 Clarifications 否决：回调持有的引用会被后续尝试改写）；只删 `parent`/`from` 并声明"不跨尝试"（C 方案，被否决：能力承诺被移除）。

### D-04 `retryCount` 统一为"重试次数"

- **Decision**：恢复上下文改为 `new GXRetryContext(exception.getRetryCount(), cause, previous.get())`；`GXRetryContext` 新增 `getAttemptIndex()`（返回与 `retryCount` 相同的值，即 0 基尝试序号），`getRetryCount()` 保留为兼容别名并在 Javadoc 标明"= 已执行的重试次数"；`from(RetryState)` 保持使用 `retryState.getRetryCount()`。
- **Rationale**：仅改恢复侧一处即三处对齐（R-03）；回调侧数值本就等于重试次数，无需改动，降低回归面。
- **Alternatives considered**：统一为"总尝试数"（B 方案，需改回调侧全部数值与断言，且与上游 `RetryState.getRetryCount()` 语义相反）；只改文档（C 方案，被否决：`3 次尝试报 3 次重试`仍会误导告警口径）。

### D-05 可选超时与抖动的 API 形态

- **Decision**：新增 `cn.maple.retry.config.GXRetryOptions`（record：`maxAttempts, initialInterval, multiplier, maxInterval, timeout, jitter, retryExceptions`，紧凑构造器复用 `GXRetryConfig` 的校验并追加 `timeout >= 0`、`jitter >= 0`）；新增重载 `retryOperation(callback, @Nullable recovery, GXRetryOptions)` 与 `retryOperationAsync(callback, @Nullable recovery, GXRetryOptions, Executor)`、`retryOperationAsync(..., @Nullable GXCancellationToken)`；既有 10 个 `retryOperation` 重载与 2 个 `retryOperationAsync` 重载全部委托到 `GXRetryOptions.defaults()`/等价参数（`timeout=0, jitter=0`）。
- **Rationale**：避免重载组合爆炸（`Complexity Tracking` 已登记）；默认值保持"无超时、无抖动"（FR-020）。
- **Alternatives considered**：逐个参数加重载（组合爆炸）；在 `RetryPolicy` 层直接暴露 `Predicate`/`BackOff`（把上游 API 泄漏进稳定契约）。

### D-06 超时/抖动的生效方式与缓存键

- **Decision**：`GXRetryConfig.createCustomRetryTemplate(...)` 保留原 5 参签名（内部委托到 7 参版本），新增 7 参重载：仅当 `timeout > 0` 时调用 `builder.timeout(Duration.ofMillis(timeout))`；仅当 `jitter > 0` 时调用 `builder.jitter(...)`；`RetryTemplateCacheKey` 增加 `timeout`/`jitter` 两个字段。
- **Rationale**：`0` 表示"不设置"，从而与上游默认（ZERO/0）一致，保证不传即行为不变；缓存键不纳入则不同超时配置会共享同一模板，属错误复用（F-12 同类问题）。
- **Alternatives considered**：用 `Duration` 作为参数类型（需再引一层单位转换与空值判断，且与既有 `long` 毫秒风格不一致，违反"与现有风格一致"）；把 `timeout` 默认设为 `maxInterval × maxAttempts` 推导值（会改变既有行为）。

### D-07 异步取消语义

- **Decision**：新增 `cn.maple.retry.context.GXCancellationToken`（`cancel()`、`isCancelled()`，内部 `AtomicBoolean`）；新增 `retryOperationAsync(callback, recovery, options, executor, token)` 重载；执行器内每次尝试前与每次退避前检查 token 与 `Thread.currentThread().isInterrupted()`，命中即抛 `cn.maple.retry.exception.GXRetryCancelledException extends GXBusinessException`；既有 `retryOperationAsync` 无 token 版本行为不变（仍统一包装为 `GXBusinessException`），并在 Javadoc 与手册注明"`CompletableFuture.cancel(true)` 不中断在途重试"。
- **Rationale**：`CompletableFuture` 无法承载取消信号（R-04 之外的事实：`cancel` 只标记状态），只能显式传递；新异常继承 `GXBusinessException` 以复用既有异常链路（宪法 III），不改既有异步方法的异常包装契约。
- **Alternatives considered**：把返回值改成 `Future`（破坏契约）；用 `AtomicBoolean` 裸参数（API 粗糙、无法扩展取消原因）；依赖线程中断（调用方无法拿到工作线程引用）。

### D-08 监听器：判据、文案、级别与实例来源

- **Decision**：
  1. `beforeRetry` 判据改为 `retryState.getRetryCount() == 1`（首次重试），日志保留 INFO；
  2. `onRetryableExecution` 文案由 `retry execution failed` 改为 `retryable execution failed`，避免"必然重试"的误导；`onRetryPolicyExhaustion` 文案不变，二者字段不同（前者有 `exceptionCount` 仅在 verbose 时输出）；
  3. 删除 `RETRYABLE_TYPE_CACHE`、`MAX_CACHE_SIZE`、`clearCache()`；`getConfigInfo()` 保留但移除 `cacheSize` 字段（改为输出 `logLevel`/`verbose`）；
  4. `LOG_LEVEL`/`ENABLE_VERBOSE_LOGGING` 改为 `volatile` 且每次调用重读 `System.getProperty`（默认 `INFO`/`false`）；`shouldLogAtLevel` 改为阈值语义（`OFF < ERROR < WARN < INFO < DEBUG`，非法值按 `INFO`，`OFF` 全关）；
  5. 所有日志调用前加 `log.isInfoEnabled()/isWarnEnabled()/isDebugEnabled()` 守卫；
  6. `GXRetryConfig` 不再用静态 `new GXRetryListener()` 注册模板：改为**构造注入** `GXRetryListener`（Spring 路径使用容器 Bean，F-14 的"Bean 从不参与"由此修复），并保留无参构造回退到进程内单例（`GXRetryUtil` 自建模板路径；Q4=A 已明确工具类不读容器 Bean，故两条路径各用各自实例，互不共享模板）。
- **Rationale**：F-09 判据被 R-02 证伪；F-10 的误导源于文案假设；F-11 的缓存对 `GXRetryUtil` 永不生效（其 `getName()` 恒为 `"GXRetryUtil"`），删除即消除死代码与"缓存上限"假象；F-12 的 `OFF` 反向行为属缺陷；F-14 的"Bean 从不参与"由注入修复，同时保留无容器可用性（工具类静态入口必须能在纯 JUnit 下工作）。
- **Alternatives considered**：保留类型缓存（维护成本 > 收益）；把日志级别改成 Spring 配置项（FR-018 否决新增配置键）；`GXRetryUtil` 强制要求容器（破坏静态工具可用性与既有 21 条用例）。

### D-09 模板缓存的淘汰原子化

- **Decision**：引入专用 `private static final Object EVICTION_LOCK`；淘汰改为"`if (cache.size() > MAX) { synchronized (EVICTION_LOCK) { if (cache.size() > MAX) { 计算待删数量 = size - TARGET(192)；迭代器删除；} } }`"，删除后 `cacheSize` 由 `size()` 反映；`computeIfAbsent` 保持无锁。
- **Rationale**：满足 FR-019 的"淘汰原子性 + 不把锁加到主路径"；裁剪到 3/4 而不是一半，避免"立即再次触发淘汰"；单次锁内只做删除，成本 O(差值)。
- **Alternatives considered**：`ConcurrentHashMap` + `Collections.synchronizedMap`（污染读路径）；Caffeine `maximumSize`（新增依赖，被附加约束否决）；`removeEldestEntry`（仅 `LinkedHashMap` 支持，需额外同步）。

### D-10 缓存键组成

- **Decision**：键 = `(maxAttempts, initialInterval, multiplier, maxInterval, timeout, jitter, 不可变 retryExceptions 快照)`；不再包含 `retryConfig.getClass().getName()`；`copyRetryExceptions` 保留（防外部修改）。
- **Rationale**：FR-018 已确定"工具类不读容器 Bean、也无配置属性"，类型名不构成区分维度（F-12 的键在现状下恒为同一值）；去掉后键完全由策略入参决定，语义更准确。同时保留注释与用例位："若未来引入策略属性，必须把策略快照纳入键"。
- **Alternatives considered**：保留类型名以兼容子类化 `GXRetryConfig`（当前无子类，且策略由入参完全决定，保留反而制造"不同键指向相同策略"的缓存膨胀）。

### D-11 测试策略与耗时控制

- **Decision**：
  1. 更新 3 条计数值断言（`testRetryOperation_RecoveryCallbackReceivesLastFailureAndTotalAttempts` 3→2、`testRetryOperation_RecoveryRunsForNonRetryableExceptionWithoutRetrying` 1→0、`testRetryOperationAsync_CustomRecoveryCallback` 2→1）；
  2. 把使用默认 1s/2s 退避的用例改为显式 `initialInterval=100`（下限），仅保留 1 条验证"默认参数真实退避"的用例（断言 ≥ 700ms）；
  3. 新增 `GXRetrySpringBootTest`（`@SpringBootTest` + 最小 `@SpringBootConfiguration`），验证 `RetryTemplate` Bean 存在、用户自定义 Bean 覆盖时按类型注入的冲突与 `@Qualifier` 规避、`@Retryable` 行为不受 `GXRetryConfig` 默认值影响；
  4. 新增 `GXRetryUtilConcurrencyTest`：32 线程 × 8 组参数（含不同 `maxAttempts`/`interval` 组合与 300 个不同键），断言结果正确、缓存 `size() <= 256`；
  5. 新增 `GXRetryUtilAsyncTest`：超时生效（`timeout=300`、回调阻塞 500ms → 抛 `GXBusinessException` 且尝试次数 < 上限）、抖动不改变尝试次数、token 取消后不再尝试、MDC 在回调内可见、`RejectedExecutionException` 原样抛出。
- **Rationale**：SC-006 要求记录测试缺口；AGENTS §4 明确要求 Spring 启动测试与并发测试；耗时目标来自 plan 的工程目标（非门禁）。
- **Alternatives considered**：保留 `MockitoExtension`（无 mock，删掉以减少无关依赖信号）；用 `Thread.sleep` 做时序断言（改为 `CountDownLatch` + 上限断言，降低抖动）。

### D-12 文档重写边界

- **Decision**：`docs/leaf-base-retry开发手册.md` 整章重写：删除 spring-retry 全部表述（`@EnableRetry`、`SimpleRetryPolicy`、`ExponentialBackOffPolicy`、`org.springframework.retry.*`），改为 `org.springframework.core.retry` 基线；新增三节——"默认异常集与风险"（含可复制的排除示例，FR-022）、"`retryCount` 三入口对照表"（FR-023）、"性能与缓存口径"（替代原"轻微性能开销"的错误结论）；`AGENTS.md` 只改 §3/§4/§5 的相关条款（日志级别、`retryCount` 语义、新增测试清单），不重写全文。
- **Rationale**：F-05 的根因是"文档基线整体过期"，局部修补会留下互相矛盾的段落；`AGENTS.md` 是给 Agent 的执行规则，只需改受影响条款以控制 diff。
- **Alternatives considered**：在手册顶部加"已过期"声明（下游仍会照抄示例）；删除手册（模块仍下发，缺少使用说明违反宪法 I 的交付集约定）。
