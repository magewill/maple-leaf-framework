# leaf-base-retry 审查报告

**Feature**: `010-retry-module-audit` | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

**结论摘要**：21 条发现（高 5 / 中 11 / 低 5）+ 5 条既定契约；正确性存在"语义与承诺不符"类高风险缺陷，线程安全无致命竞态但缓存/取消/监听器需约束，性能存在结构性上限。修复方向按 [spec.md](./spec.md) Clarifications 的 5 项决策执行（全部选择"不改默认行为"的最小收敛路线）。

---

## 1. 基线、抽检与验收记录

### 1.1 测试基线（交付前）

```text
mvn -o -pl leaf-base-retry test
[INFO] Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- GXRetryConfigTest
[INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0 -- GXRetryContextTest
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- GXRetryListenerTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0 -- GXRetryUtilTest   (16.57 s)
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

`GXRetryUtilTest` 占 16.57s 的原因是 6 条用例真实等待默认退避（1s + 2s），与"默认参数"耦合。

### 1.2 抽检记录（T013）

| 结论 | 命令 | 实际输出 |
| --- | --- | --- |
| F-01 默认重试所有 `Exception` | `findstr /n "Map.of(Exception.class, true)" GXRetryUtil.java` | 第 236 行命中（`getRetryableExceptionMap` 的默认分支） |
| F-04 工具类不使用容器 `RetryTemplate` Bean | 同上文件检索 `getBean(GXRetryConfig.class)` | 第 206 行：只取配置类，不取 `RetryTemplate` |
| F-07 异步不可取消 | 同上文件检索 `CompletableFuture.supplyAsync` | 第 155、178 行两处，均无 token/中断检查 |
| F-08 恢复侧用尝试数 | 同上文件检索 `getExceptions().size()` | 第 143 行：恢复上下文 `retryCount` 取尝试数 |

### 1.3 范围护栏（T003）

- `leaf-base-retry/pom.xml`、根 `pom.xml`：**零改动、零新增依赖**。
- 允许改动集合：4 个生产文件（`GXRetryConfig`、`GXRetryContext`、`GXRetryListener`、`GXRetryUtil`）+ 3 个新生产类型（`GXRetryOptions`、`GXCancellationToken`、`GXRetryCancelledException`）+ 1 个 `package-info`（`exception` 包）+ 7 个测试文件 + 2 份文档。

### 1.4 交付后验收记录（T041）

见本文件 §5.3 与 §8。

---

## 2. 证据（E-01 ~ E-28）

| ID | 来源 | 定位 | 事实 |
| --- | --- | --- | --- |
| E-01 | 本模块源码 | `GXRetryUtil.java:233-239` | 未显式指定时返回 `Map.of(Exception.class, true)`，即默认重试所有 `Exception` |
| E-02 | 本模块源码 | `GXRetryConfig.java:59-66` | 默认异常集含 `GXBusinessException`、`IOException`、`TimeoutException`、`SocketTimeoutException` |
| E-03 | 本模块源码 | `GXRetryConfig.java:120-124` | `RetryPolicy.Builder` 只调用 `maxRetries/delay/multiplier/maxDelay`，未调用 `timeout` 与 `jitter` |
| E-04 | 上游源码 | `spring-core 7.0.9 RetryPolicy.java:164,181` | `DEFAULT_JITTER = 0`、`DEFAULT_TIMEOUT = Duration.ZERO`（ZERO 表示不设超时） |
| E-05 | 本模块源码 | `GXRetryConfig.java:34-44` | 上限为 `maxAttempts ≤ 100`、`initialInterval ≤ 300000`、`multiplier ≤ 10`、`maxInterval ≤ 3600000` |
| E-06 | 本模块源码 | `GXRetryUtil.java:126` | 每次尝试 `new GXRetryContext(...)`，属性不跨尝试 |
| E-07 | 本模块源码 | `GXRetryContext.java:31,37-41` | `parent` 字段只在构造器赋值，全模块无传入非空的调用点 |
| E-08 | 本模块源码 | `GXRetryContext.java:43-49,67-96` | `from(RetryState)`、`setAttribute/getAttributes/attributeNames` 在模块内零调用（仅测试） |
| E-09 | 本模块源码 | `GXRetryUtil.java:206` | 只从容器取 `GXRetryConfig`，从不取 `RetryTemplate` Bean |
| E-10 | 本模块源码 | `GXRetryConfig.java:49` | `@ConditionalOnMissingBean(RetryTemplate.class)` 用在普通 `@Configuration` 的 `@Bean` 上 |
| E-11 | 本模块源码 | `GXRetryUtil.java:29-32` / `GXRetryConfig.java:29-32` | 3 / 1000 / 2.0 / 10000 两组常量重复定义 |
| E-12 | 上游源码 | `RetryAnnotationBeanPostProcessor.java:96-113` | `@Retryable` 只按注解属性构建 `MethodRetrySpec`，不读取任何 Bean 或全局默认值 |
| E-13 | 本模块源码 | `GXRetryUtil.java:143` | 恢复上下文用 `exception.getExceptions().size()`（总尝试数） |
| E-14 | 上游源码 | `RetryException.java:84-87` | `RetryException.getRetryCount()` = 被抑制异常数 = 重试次数 |
| E-15 | 本模块源码 | `GXRetryListener.java:36` | `beforeRetry` 用 `getRetryCount() == 0` 作为"重试开始"判据 |
| E-16 | 上游源码 | `RetryTemplate.java:171-172` | 先 `increaseRetryCount()` 再回调 `beforeRetry`，故该判据恒假 |
| E-17 | 上游源码 | `RetryTemplate.java:142` | 首次尝试失败后立即回调 `onRetryableExecution`，此时尚未判断是否会重试 |
| E-18 | 本模块源码 | `GXRetryListener.java:109-124` | 类型缓存仅在 `getName()` 空白时可达；`GXRetryUtil` 恒返回 `"GXRetryUtil"`（第 136-138 行） |
| E-19 | 本模块源码 | `GXRetryListener.java:29-32,135-143` | 级别在类初始化时快照；未知值走 `default -> true`（`OFF` 反而全开） |
| E-20 | 本模块源码 | `GXRetryConfig.java:46,82` | 监听器为静态 `new`，`@Component` 版本不参与任何模板 |
| E-21 | 本模块源码 | `GXRetryUtil.java:155,178` | 异步路径直接 `CompletableFuture.supplyAsync`，无 MDC 包装、无 token、无超时 |
| E-22 | 本模块源码 | `GXRetryUtil.java:222-231` | 淘汰为"到达 256 即删 128 条"，无锁、非 LRU、并发下可重复触发 |
| E-23 | 本模块源码 | `GXRetryUtil.java:210` | 缓存键含 `retryConfig.getClass().getName()`，无上下文与有上下文共用同一值 |
| E-24 | 本模块源码 | `GXRetryUtil.java:161-165`（`GXRetryConfig.java:161-165`） | `multiplier` 区间比较对 `NaN` 均为 false，`NaN` 通过本模块校验后才在上游抛错 |
| E-25 | 文档原文 | `docs/leaf-base-retry开发手册.md`（交付前）第 22、75、166、181-192、213 行 | 仍以 spring-retry 为基线（`@EnableRetry`、`org.springframework.retry.support.RetryTemplate`、`RetryCallback`），示例无法用本模块依赖编译 |
| E-26 | 测试清单 | `leaf-base-retry/src/test/java/cn/maple/retry/**`（交付前 4 个测试类，30 条用例） | 无 `@SpringBootTest`、无并发/取消/MDC 用例、无日志级别分支用例 |
| E-27 | 本模块源码 | `config/package-info.java:1`、`context/package-info.java:1`、`listener/package-info.java:1`、`util/package-info.java:1` | 4 个包仅声明 `@NullUnmarked`，全模块无任何 `@Nullable` 标注（含可为 `null` 的返回值） |
| E-28 | 本模块源码 | `GXRetryUtil.java:44-95` | 11 个 `retryOperation` + 2 个 `retrySupplier` 重载；`retryOperation(cb, null)` 在 `GXRecoveryCallback` 与 `Map` 之间歧义 |

> 所有上游行号基于 `spring-core-7.0.9-sources.jar`、`spring-context-7.0.9-sources.jar`，本次均逐行打开核对；本模块行号为交付前工作副本行号。

## 3. 发现（F-01 ~ F-21）

| ID | 级别 | 维度 | 位置 | 触发条件 | 影响 | 证据 | 回归设计 | 处置 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | 高 | 正确性 | `GXRetryUtil.java:233-239`、`GXRetryConfig.java:59-66` | 使用默认参数重试会抛 `GXBusinessException`/`IllegalArgumentException`/`InterruptedException` 的操作 | 确定性失败被重复执行 3 次，放大非幂等副作用；中断被继续重试 | E-01,E-02 | 输入：回调抛 `GXBusinessException`；断言：执行 3 次；新行为=文档专章 + 可复制排除示例可消除该现象 | 文档收敛 |
| F-02 | 高 | 性能 | `GXRetryConfig.java:120-124`、`:34-44` | 显式传大参数（100 次 / 300000 / 10 / 3600000） | 单次调用最坏阻塞 ≈ 98 小时，占用请求线程且无总超时 | E-03,E-04,E-05 | 输入：`timeout=300` + 回调阻塞 200ms；旧行为=跑满 5 次；新行为=超时终止且尝试次数 < 5 | 修复 |
| F-03 | 高 | 正确性 | `GXRetryUtil.java:126`、`GXRetryContext.java:31` | 回调在尝试 1 写属性、尝试 2 读 | 属性传递能力实际失效，`parent` 恒 null | E-06,E-07 | 输入：两次尝试读写同一键；旧行为=读不到；新行为=读到且 `parent` 链长 = 尝试数-1 | 修复 |
| F-04 | 高 | 正确性 | `GXRetryUtil.java:206`、`GXRetryConfig.java:49`、`RetryAnnotationBeanPostProcessor:96-113` | 三入口混用 / 自定义 `RetryTemplate` Bean | "统一配置"不成立，用户 Bean 被静默忽略 | E-09,E-10,E-12 | 输入：定义自定义 `RetryTemplate` Bean 后按类型注入；旧行为=可能双 Bean 冲突且工具类仍自建；新行为=手册给出 `@Qualifier` 规避方式 | 文档收敛 |
| F-05 | 高 | 正确性 | `docs/leaf-base-retry开发手册.md:22,75,166,181-192,213` | 按手册示例接入 | 示例无法编译（spring-retry API 不在类路径） | E-25 | 输入：按手册示例编译；旧行为=编译失败；新行为=示例可编译 | 文档收敛 |
| F-06 | 中 | 正确性 | `GXRetryUtil.java:140-147` | 退避期线程被中断 | 中断与"耗尽"同分支，恢复回调仍执行 | E-21 | 输入：退避期 `interrupt()`；断言：调用方收到业务异常、恢复回调被执行（现状契约） | 既定契约（文档化） |
| F-07 | 中 | 正确性/线程安全 | `GXRetryUtil.java:155,178` | `future.cancel(true)` | 在途重试继续跑，最长可跑满全部退避；MDC 丢失 | E-21 | 输入：提交后立即 cancel；旧行为=仍执行后续尝试；新行为=token 取消后无新尝试 | 修复 |
| F-08 | 中 | 正确性 | `GXRetryUtil.java:143` | 恢复回调读取 `retryCount` | 3 次尝试报 3、不可重试报 1，与"重试次数"不符 | E-13,E-14 | 输入：3 次尝试耗尽；旧行为=3；新行为=2；不可重试 1→0 | 修复 |
| F-09 | 中 | 正确性（可观测） | `GXRetryListener.java:36` | 任意重试 | "重试开始"日志永不输出 | E-15,E-16 | 输入：一次重试；旧行为=0 条；新行为=1 条 | 修复 |
| F-10 | 中 | 正确性（可观测） | `GXRetryListener.java:43-66` | 首次失败但异常不可重试 | 日志谎称"retry execution failed"；耗尽时双条 WARN | E-17 | 输入：不可重试异常；断言：文案为 `retryable execution failed` | 修复 |
| F-11 | 中 | 性能/线程安全 | `GXRetryUtil.java:34,222-231` | 键空间大于 256 | 随机删一半导致热点模板反复重建；并发下可重复触发 | E-22 | 输入：32 线程 × 300 键；断言：`size() <= 256` 且无重复裁剪 | 修复 |
| F-12 | 中 | 线程安全 | `GXRetryUtil.java:210` | 同类型不同配置来源 | 键无法区分配置来源；未来引入属性会静默复用旧策略 | E-23 | 输入：先无上下文后有上下文；断言：模板策略与调用参数一致 | 修复 |
| F-13 | 中 | 正确性 | `GXRetryConfig.java:48-50` | 用户自定义 `RetryTemplate` Bean | 条件注解在非自动装配类上顺序不可靠，按类型注入可能 `NoUniqueBeanDefinitionException` | E-10 | 输入：定义自定义 Bean；断言：文档给出 `@Qualifier`/`@Primary` 规避方式 | 文档收敛 |
| F-14 | 中 | 线程安全 | `GXRetryConfig.java:46,82` | 替换 `GXRetryListener` Bean | 自定义监听器不生效；`@Component` 实例成为死 Bean | E-20 | 输入：自定义监听器 Bean；旧行为=不生效；新行为=模板使用注入实例 | 修复 |
| F-15 | 中 | 性能 | `GXRetryListener.java:29-32,135-143` | 设 `level=OFF` 或运行期改级别 | `OFF` 反而全开；级别只能类初始化时确定 | E-19 | 输入：`level=OFF`；旧行为=仍输出；新行为=零输出 | 修复 |
| F-16 | 中 | 正确性（覆盖） | `src/test/java/cn/maple/retry/**` | 无容器启动测试、无并发测试、无级别分支测试 | 装配、并发、日志分支无回归保护 | E-26 | 输入：新增 3 个测试类；断言：见 tasks T014/T019/T020/T028 | 修复 |
| F-17 | 低 | 正确性 | `GXRetryConfig.java:161-165` | `multiplier = Double.NaN` | 通过本模块校验，异常来自上游且信息不同 | E-24 | 输入：`NaN`；断言：本模块 `IllegalArgumentException` 且消息含 `multiplier` | 修复 |
| F-18 | 低 | 性能 | `GXRetryUtil.java:119-120`、`GXRetryContext.java:27-29` | 每次调用/尝试 | 单线程路径使用原子类型与两个并发容器，`setAttribute` 两次哈希 | E-06 | 输入：空属性链复制；断言：无属性时复制为 0 次写入（可通过反射断言） | 修复 |
| F-19 | 低 | 性能 | `GXRetryListener.java:49-73` | 高吞吐重试 | 每失败尝试 1 条 WARN（4 参数装箱），无 `isEnabled` 守卫 | E-17 | 输入：关闭 WARN 日志；断言：无参数装箱（以守卫短路） | 修复 |
| F-20 | 低 | 正确性 | `package-info.java` × 4 | 传 `null` 入参/返回 `null` | 空值契约缺失，与 `AGENTS.md` §5 要求不符 | E-27 | 输入：`setAttribute(null, null)`、返回 `null`；断言：签名标注 `@Nullable` | 修复 |
| F-21 | 低 | 正确性（API） | `GXRetryUtil.java:44-95` | `retryOperation(cb, null)` | 在 `GXRecoveryCallback` 与 `Map` 之间歧义，编译期报错 | E-28 | 输入：`retryOperation(cb, null)`；旧行为=编译歧义；新行为=文档给出 `(GXRecoveryCallback<T>) null` 写法 | 文档收敛 |

### 3.1 复现方式（每条发现一条可执行路径）

| 发现 | 复现方式（命令 / 用例 / 构造步骤） |
| --- | --- |
| F-01 | `mvn -o -pl leaf-base-retry test -Dtest=GXRetryUtilTest#testRetryOperation_DefaultPolicyRetriesInterruptedException`：断言默认策略把 `InterruptedException` 重试到 3 次；旧代码同路径（`GXRetryUtil.java:233-239`）行为一致 |
| F-02 | 按 §5.2 公式代入上限组合复算 353100000ms；正向验证运行 `GXRetryUtilAsyncTest#totalTimeoutStopsTheRetryChainEarly`（有超时时提前终止） |
| F-03 | `GXRetryUtilTest#testRetryOperation_AttributesAreCarriedAcrossAttempts`：旧代码第 2 次尝试读不到 `endpoint` |
| F-04 | `GXRetrySpringBootTest#retryableAnnotationUsesItsOwnMaxRetriesInsteadOfModuleDefaults`：`@Retryable(maxRetries=1)` 恰好 2 次，与模块默认 3 次无关 |
| F-05 | 用交付前手册（`git show HEAD:docs/leaf-base-retry开发手册.md`）中的 `import org.springframework.retry.support.RetryTemplate;` 示例编译 → 找不到符号 |
| F-06 | 在回调内 `Thread.currentThread().interrupt()` 后继续重试，观察恢复回调仍被执行、调用方收到最后一次业务异常 |
| F-07 | `GXRetryUtilAsyncTest#cancelledTokenSkipsRemainingAttemptsAndRecovery`（取消后 0 次新尝试）；旧代码无 token，只能观察"取消后仍继续重试" |
| F-08 | `GXRetryUtilTest#testRetryOperation_RecoveryCallbackReceivesLastFailureAndRetryCount`：旧断言为 `3`，现断言 `2` |
| F-09 | 设 `-Dmaple.framework.retry.log.level=INFO` 跑一次重试；旧代码无 `retry started` 行（`GXRetryListener.java:36` 判据恒假） |
| F-10 | `GXRetryUtilTest#testRetryOperation_SpecificExceptions_NonRetryableException` 并观察 WARN：旧文案 `retry execution failed`，现为 `retryable execution failed` |
| F-11 | `GXRetryUtilConcurrencyTest#concurrentCallsKeepResultsCorrectAndCacheBounded`：32 线程 × 300 键后 `size() <= 256` |
| F-12 | `GXRetryUtilConcurrencyTest#cacheKeyIncludesTimeoutAndJitter`：反射读取私有键的 `timeout()`/`jitter()` 值 |
| F-13 | `GXRetrySpringBootTest#customRetryTemplateBeanCanCoexistAndIsSelectableByPrimary`：自定义 Bean 与模块 Bean 共存，`@Primary` 可按类型选中 |
| F-14 | `GXRetrySpringBootTest#retryTemplateBeanUsesInjectedListener`：模板监听器与容器 Bean 同一实例 |
| F-15 | `GXRetryListenerTest#configuredLevel_UsesThresholdSemantics`：`OFF→0`、`WARN→2`、`TRACE→4`、非法值→`3` |
| F-16 | `mvn -o -pl leaf-base-retry test`：交付前 30 条、交付后 60 条；交付前无 `GXRetrySpringBootTest`/并发/取消用例 |
| F-17 | `GXRetryConfigTest#createCustomRetryTemplate_RejectsNaNMultiplier`：异常由本模块抛出且消息含 `multiplier` |
| F-18 | 阅读 `GXRetryContext` 构造链；无属性时 `getAttributes().size() == 0`（复制为 0 次写入） |
| F-19 | 设 `-Dmaple.framework.retry.log.level=OFF` 跑重试，观察零日志（`GXRetryListenerTest` 级别用例） |
| F-20 | `GXRetryUtilTest#testRetryOperation_PropagatesNullFromCallbackAndRecovery`：回调/恢复返回 `null` 时结果原样为 `null` |
| F-21 | 写 `GXRetryUtil.retryOperation(cb, null)` 编译 → 歧义报错；需写 `(GXRecoveryCallback<T>) null` |

### 3.2 风险排序（影响面 × 触发概率 × 可观测性）

| 批次 | 发现 | 排序依据 |
| --- | --- | --- |
| 批 1（立即） | F-01、F-02、F-03、F-04、F-05 | 影响面最大（所有默认调用者）；触发概率=默认配置必现；可观测性低（F-03/F-04 完全静默，F-01 只表现为"多执行几次"） |
| 批 2（本迭代） | F-06 ~ F-16 | 影响面中等（特定失败路径）；触发概率中；F-09/F-10/F-15 可观测性高（日志可见），F-12/F-14 可观测性低（静默） |
| 批 3（随版本） | F-17 ~ F-21 | 影响面小（边界输入/API 易用性）；触发概率低；可观测性中等（编译期或异常提示） |

> 结论：批 1 全部为"默认路径必现且静默"，因此优先修文档与默认语义；批 2 中 F-12/F-14 因静默需优先于其他中危项。

### 3.3 既定契约（禁止改写）

| ID | 契约 | 依据 |
| --- | --- | --- |
| P-01 | 共享 `RetryTemplate` 线程安全（字段构造期赋值，执行态为局部 `MutableRetryState`） | 上游 `RetryTemplate` 源码 |
| P-02 | `copyRetryExceptions` 生成不可变快照，外部修改入参不影响在途重试 | `GXRetryUtil.java:217-220` |
| P-03 | 参数校验先于执行，非法参数不触达回调 | 既有用例 + 新增 T008 断言 |
| P-04 | 默认集合不含 `Error`，`Error` 不被重试也不被包装 | 既有用例 |
| P-05 | `RetryException.getCause()` 非空 + sneaky throw 保留受检异常类型 | 既有用例 |

## 4. 矩阵

### 4.1 重试语义矩阵

| 入口 | 默认 includes | 默认次数 / 退避 | 受 `GXRetryConfig` 默认值影响 |
| --- | --- | --- | --- |
| `GXRetryUtil` 默认 | `Exception`（含业务异常与中断，不含 `Error`） | 3 次；1000ms × 2.0，上限 10000ms，无超时无抖动 | 否（自建模板） |
| `RetryTemplate` Bean | `GXBusinessException`、`IOException`、`TimeoutException`、`SocketTimeoutException` | 同上 | 是 |
| `@Retryable` | 注解 `includes`（默认全部） | 注解 `maxRetries`（默认 3 次重试 = 4 次尝试） | 否（只读注解） |

**入口 × 异常类型（逐格结论，SC-003）**

| 异常类型 | `GXRetryUtil` 默认 | `RetryTemplate` Bean 默认 | `@Retryable` 默认 | 依据 |
| --- | --- | --- | --- | --- |
| `GXBusinessException` | 重试（至 3 次） | 重试 | 重试 | E-01、E-02 |
| `InterruptedException` | **重试（至 3 次，中断标志不被框架清除）** | 不重试（不在默认集） | 重试 | E-01 + `GXRetryUtilTest#testRetryOperation_DefaultPolicyRetriesInterruptedException` |
| `IOException` | 重试 | 重试 | 重试 | E-02 |
| `IllegalArgumentException` | 重试 | 不重试（不在默认集） | 重试 | E-02 |
| `Error`（如 `AssertionError`） | **不重试**、不包装 | **不重试** | **会重试**（`includes` 为空时 `ExceptionTypeFilter.matchIfEmpty=true`，匹配所有 `Throwable`） | E-12 + `AbstractRetryInterceptor.invoke`（`includes(spec.includes())`） |
| `GXRetryCancelledException` | 不重试（带 token 时显式排除） | 不重试（不在默认集） | 重试（默认集不含排除项） | 实现 + `GXRetryUtilAsyncTest#cancelledTokenSkipsRemainingAttemptsAndRecovery` |

### 4.2 失败终止矩阵

| 终止原因 | 调用方收到 | 恢复回调 | 中断标志 |
| --- | --- | --- | --- |
| 重试耗尽 | 原始异常（sneaky throw） | 执行，`retryCount` = 重试次数（修复前为尝试数） | 保持 |
| 不可重试异常 | 原始异常 | 执行（任意失败兜底，需文档写明） | 保持 |
| 退避期中断 | 最后一次业务异常 | 执行 | 已恢复 |
| 超时 | 最后一次业务异常 | 执行 | 保持 |
| token 取消（新增） | `GXRetryCancelledException` | **不执行** | 保持 |

### 4.3 并发风险矩阵

| 对象 | 共享范围 | 现状保护 | 收敛后 |
| --- | --- | --- | --- |
| `RETRY_TEMPLATE_CACHE` | 进程级静态 | `ConcurrentHashMap` 读安全；淘汰无锁 | 键含 `timeout/jitter`；超阈值专用锁裁剪到 192 |
| `RetryTemplate` | 跨线程 | 线程安全（P-01） | 不变 |
| `GXRetryListener` | 静态 + Bean 双份 | 无状态 | 构造注入 + 无参回退，Bean 路径使用容器实例 |
| `GXRetryContext` | 单线程逐尝试新建 | 属性 `ConcurrentHashMap` | 复制前递 + `parent` 链 |
| 异步工作线程 | 调用方 Executor | 无 | `GXMdcThreadUtils` 包装 + token 取消 |

**并发下缓存边界结论（US3/AC2）**

1. **是否可能短暂超过 256**：可能。淘汰是"先 `size() > 256` 判断、再进锁二次确认"的两段式，`computeIfAbsent` 插入与淘汰不在同一临界区，因此在并发突发插入的瞬间，`size()` 可短暂大于 256（上界为"并发未裁剪的插入数"），下一次淘汰调用即回落到 192。断言口径因此取"并发结束后 `size() <= 256`"（`GXRetryUtilConcurrencyTest`），而非"任意时刻"。若需要严格上界，需把插入也纳入同一锁，代价是污染主路径（FR-019 明确禁止）。
2. **裁剪到 192 时热点模板是否可能被淘汰**：可能。淘汰按 `ConcurrentHashMap` 迭代顺序取前 `size-192` 个键（非 LRU、非随机抽样），热点模板若位于迭代序列前段会被删除，随后被 `computeIfAbsent` 重建（一次策略对象构造 + 一次监听器设置，成本 O(1) 级）。缓解方式：不引入访问计数（需要额外同步），而是保持"键空间由调用方参数决定"——业务侧固定参数即不会触发淘汰；若未来出现大量动态参数，应改用有界 LRU 或按业务维度显式分段缓存（记为已知限制）。

## 5. 性能口径

### 5.1 固定开销（每次调用）

| 指标 | 取值 |
| --- | --- |
| 容器类型查询 | 1 次 `getBean(GXRetryConfig.class)` |
| 键拷贝 | 1 次 `LinkedHashMap` + 1 次不可变包装 |
| 缓存键对象 | 1 个（7 字段 record） |
| 上下文对象 | 1 个/尝试（复制前递为 O(属性数)，无属性时 0 次写入） |
| 每失败尝试日志 | 1 条 WARN（加守卫后条数不变，但可被级别短路） |

### 5.2 最坏阻塞推导

- 第 k 次重试等待 = `min(initialInterval × multiplier^(k-1), maxInterval)`。
- 默认（3 次尝试）：1000 + 2000 = **3000ms**。
- 上限组合（100 次、初始 300000ms、倍率 10、上限 3600000ms）：`300000 + 98 × 3600000 = 353100000ms ≈ 98h`。
- 结论：上限未收紧（Q3 决策），风险由"可选超时 + 文档告警"缓解；`timeout` 语义为"从首次尝试起的总耗时上限"。

### 5.3 测试墙钟

| 阶段 | 全量测试墙钟 | 说明 |
| --- | --- | --- |
| 交付前 | 16.57s（`GXRetryUtilTest` 21 条） | 6 条用例真实等待默认 1s/2s 退避 |
| 交付后 | **8.76s**（`GXRetryUtilTest` 30 条） | 仅保留 1 条真实默认退避用例（3s）与 1 条必用默认参数的 `Map` 重载用例（1s），其余显式 100ms；全量 82 条用例约 13s |

## 6. 修复映射表（F → R → T）

| 发现 | 修复项 | 任务 |
| --- | --- | --- |
| F-01、F-05、F-13、F-21 | R-01 文档重写与风险披露 | T032、T033、T035、T036 |
| F-02 | R-02 可选超时与抖动 | T005、T007、T023（token 检查点） |
| F-03、F-08、F-18 | R-03 上下文复制前递与计数统一 | T014–T017 |
| F-04、F-11、F-12 | R-04 常量单点 + 缓存键/淘汰 | T004、T006、T024、T025 |
| F-06 | R-05 失败终止语义文档化 | T033、T034 |
| F-07、F-21 | R-06 取消能力与 MDC 传播 | T021–T023 |
| F-09、F-10、F-15、F-19 | R-07 监听器判据/文案/级别/守卫 | T028、T029 |
| F-14 | R-08 监听器实例来源 | T026 |
| F-16 | R-09 测试补强 | T008、T014、T019、T020、T028 |
| F-17 | R-10 参数校验 NaN 安全 | T005、T007、T008 |
| F-20 | R-11 空值标注 | T016、T021、T035 |
| （性能） | R-12 测试耗时收敛 | T030、T031 |

## 7. 兼容性声明

| 变更 | 影响面 | 迁移路径 |
| --- | --- | --- |
| 恢复上下文 `retryCount` 改为"重试次数" | 用该值做告警/对账/统计的下游 | 3 次尝试由 3 变 2；不可重试由 1 变 0；需要"尝试数"改用 `getAttemptIndex() + 1` |
| 新增 `getAttemptIndex()`、`GXRetryOptions`、`GXCancellationToken`、`GXRetryCancelledException`、异步 token 重载 | 无破坏性影响 | 加法式 API，可按需使用 |
| 删除 `GXRetryListener.clearCache()` 与类型缓存 | 直接调用该静态方法的代码（模块内仅测试） | 删除调用（缓存从未生效） |
| `beforeRetry` 新增 1 条 INFO（首次重试） | 日志量 +1 条/重试链 | 设 `maple.framework.retry.log.level=WARN` 静默 |
| 日志级别改为阈值语义，`OFF` 全关 | 设 `OFF` 的使用者（此前反而全开） | 无需迁移，属缺陷修复 |
| `multiplier=NaN` 现在被本模块拒绝 | 依赖 NaN 透传的调用（无实际意义） | 传有限值 |

## 8. 验收记录（T040/T041 回填）

| 检查 | 结果 |
| --- | --- |
| `mvn -o -pl leaf-base-retry clean compile` | **BUILD SUCCESS**（ErrorProne + NullAway 零错误） |
| `mvn -o -pl leaf-base-retry clean test` | **Tests run: 82, Failures: 0, Errors: 0, Skipped: 0**；`GXRetryUtilTest` 8.76s |
| 测试类分布 | `GXRetryConfigTest` 9、`GXRetrySpringBootTest` 7、`GXRetryContextTest` 11、`GXRetryListenerTest` 12、`GXRetryUtilTest` 31、`GXRetryUtilAsyncTest` 7、`GXRetryUtilConcurrencyTest` 5 |
| 收敛补充 | 证据扩至 28 条（E-25 文档原文、E-26 测试清单、E-27 package-info、E-28 重载清单）；新增"复现方式""风险排序"两节 |
| 文档核对 | 过期表述（`@EnableRetry`/`SimpleRetryPolicy`/`ExponentialBackOffPolicy`/`org.springframework.retry.`）命中 **0**；`默认异常集`/`retryCount`/`最坏阻塞` 命中 **10**；`clearCache` 全仓 **0** |
| 范围护栏 | `git status` 仅含 `leaf-base-retry/`、`docs/leaf-base-retry开发手册.md`、`specs/010-retry-module-audit/` |

---

## 附录 A：PR 说明草稿

**动机**：修复 leaf-base-retry 在默认重试集合、上下文传递、重试计数、缓存并发、异步可取消与可观测性上的 21 条发现（高 5/中 11/低 5），并把文档基线从 spring-retry 迁到 Spring Framework 7 `org.springframework.core.retry`。

**影响范围**：仅 `leaf-base-retry` 模块 + `docs/leaf-base-retry开发手册.md` + `leaf-base-retry/AGENTS.md`。公开方法签名与默认参数不变；唯一运行时语义变更是恢复回调 `retryCount`（见 §7）。

**验证命令**：
```powershell
mvn -o -pl leaf-base-retry clean compile
mvn -o -pl leaf-base-retry test
```

**兼容策略**：见 §7；`retryCount` 变更在手册"升级必查"中单列，并提供 `getAttemptIndex()` 作为替代口径。

**回滚方式**：本次改动无数据库与配置变更，回滚即回退本模块与两份文档；`retryCount` 语义回退需同步回退 3 条断言。
