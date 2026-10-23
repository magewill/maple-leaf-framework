# Feature Specification: leaf-base-retry 正确性、线程安全与性能审查

**Feature Branch**: `010-retry-module-audit`（本次未通过 hook 创建 git 分支；`before_specify` 无 hook 注册）

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "审查 @e:\project\java\maple-leaf-framework\leaf-base-retry 模块的正确性、线程安全性、性能。"

## 背景与审查结论摘要

本规格把"`leaf-base-retry` 现有实现是否正确、是否线程安全、是否存在性能结构问题"转化为可验收的交付要求。审查对象与证据来源：

1. `leaf-base-retry` 全部 11 个生产源码文件（含 4 个 `package-info`）、4 个既有测试类；
2. 契约来源：`leaf-base-retry/AGENTS.md`、`docs/leaf-base-retry开发手册.md`、`.specify/memory/constitution.md`（重点为 II 稳定契约、IV 显式配置与双配置源、V 空值安全与上下文纪律、VI 测试门槛，以及 `VII` 后缀表对 `GX<用途>Utils` 与 `GX<能力>Config` 的约定）；
3. 依赖基线：模块仅依赖 `leaf-base-framework` + 测试依赖；重试实现基于 **Spring Framework 7.0.9** 的 `org.springframework.core.retry`（`RetryTemplate` / `RetryPolicy` / `RetryListener`）与 `org.springframework.resilience.annotation`，**不依赖 spring-retry**；
4. 上游语义核对：直接读取本地仓库 `spring-core-7.0.9-sources.jar`、`spring-context-7.0.9-sources.jar` 中 `RetryTemplate`、`RetryPolicy`、`RetryState`、`RetryException`、`Retryable`、`ExceptionTypeFilter`、`RetryAnnotationBeanPostProcessor` 源码逐条比对；
5. 既有测试实测基线：`mvn -o -pl leaf-base-retry test` → **Tests run: 30, Failures: 0, Errors: 0**（其中 `GXRetryUtilTest` 21 条全部通过，耗时 **16.57s**，主要消耗在真实退避 sleep）。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 逻辑正确性 | 单次重试的"执行—失败—退避—再执行—恢复"主链路在代码层面自洽，异常原样传播、`Error` 不被重试、参数非法前置拒绝均成立；但**默认重试集合过宽（含业务异常与中断）**、**恢复回调与中断/耗尽不可区分**、**上下文属性不跨尝试生效**、**三套入口的重试策略互不一致**，属于"能跑但语义与承诺不符" | 存在高风险缺陷 |
| 线程安全 | 共享的 `RetryTemplate` 与静态缓存本身并发安全（执行期状态全部方法内局部）；风险集中在**缓存淘汰的 check-then-act**、**缓存键不区分配置来源**、**异步取消不中断工作线程**、**监听器双实例** | 无致命竞态，需补约束 |
| 性能 | 每次调用固定付出"容器类型查询 + 键拷贝 + record 分配"；重试同步阻塞调用线程且**无总超时、无抖动**，最坏单次调用可阻塞约 98 小时；每失败尝试 1 条 WARN、每次重试成功 1 条 INFO，均无 `isEnabled` 守卫 | 存在结构性上限，非阻断 |

关键证据（均可按位置复核）：

- **E-01**：`GXRetryUtil.getRetryableExceptionMap` 在未显式指定时返回 `Map.of(Exception.class, true)`（第 233–239 行），即**默认重试所有 `Exception`**；`GXRetryConfig.createDefaultRetryExceptionMap` 则把 `GXBusinessException` 列为可重试（第 59–66 行）。两处默认集合不同。
- **E-02**：`GXRetryConfig.createRetryPolicy` 只设置 `maxRetries/delay/multiplier/maxDelay`（第 120–124 行），**未调用** `timeout(...)` 与 `jitter(...)`；上游 `RetryPolicy.Builder.DEFAULT_TIMEOUT = Duration.ZERO`（`RetryPolicy.java:181`，ZERO 表示不设超时）、`DEFAULT_JITTER = 0`（`RetryPolicy.java:164`）。
- **E-03**：参数校验允许 `maxAttempts ≤ 100`（第 35 行）、`initialInterval ≤ 300000`（第 38 行）、`multiplier ≤ 10`（第 41 行）、`maxInterval ≤ 3600000`（第 44 行）；组合后按 `ExponentialBackOff` 的最坏路径约 **300s + 98×3600s ≈ 98 小时**，期间调用线程被 `Thread.sleep` 占用。
- **E-04**：`GXRetryUtil.retryOperation` 每次尝试 `new GXRetryContext(...)`（第 126 行），上一次尝试写入的属性不进入下一次；`GXRetryContext.parent` 无任何赋值点（第 31、37–41 行），`GXRetryContext.from(RetryState)`（第 43–49 行）与 `setAttribute/getAttributes/attributeNames`（第 67–96 行）在模块内**零调用**，仅测试使用。
- **E-05**：`GXRetryUtil` 自建模板（第 200–215 行）并只从容器取 `GXRetryConfig`（第 206 行），**从不使用容器中的 `RetryTemplate` Bean**；`GXRetryConfig.retryTemplate()` 上的 `@ConditionalOnMissingBean`（第 49 行）对工具类无效。
- **E-06**：静态常量重复定义：`GXRetryUtil` 第 29–32 行与 `GXRetryConfig` 第 29–32 行各写一份 3/1000/2.0/10000，无共享来源。
- **E-07**：`@EnableResilientMethods`（`GXRetryConfig` 第 27 行）驱动的 `@Retryable` 由 `RetryAnnotationBeanPostProcessor` 按注解属性构造 `MethodRetrySpec`（源码第 96–113 行），**不读取任何 `RetryTemplate` Bean 或全局默认值**，因此声明式重试与 `GXRetryConfig` 的默认值无关。
- **E-08**：`retryOperation` 的恢复上下文用 `exception.getExceptions().size()`（第 143 行）= **总尝试数**；回调上下文用 0 基尝试序号（第 126 行）；上游 `RetryException.getRetryCount()` = **重试次数**（`RetryException.java:84–87`）。同名 `retryCount` 存在三种含义；不可重试异常时恢复回调看到 `retryCount=1` 而实际 0 次重试（测试第 183 行已固化）。
- **E-09**：`GXRetryListener.beforeRetry` 用 `retryState.getRetryCount() == 0` 作为"重试开始"判据（第 36 行），但上游 `RetryTemplate` 先 `increaseRetryCount()`（源码第 171 行）再回调 `beforeRetry`（第 172 行），该分支**恒不成立**。
- **E-10**：`onRetryableExecution` 在首轮失败后即被调用（上游源码第 142 行），且 `isSuccessful()` 此时为 false，于是"不可重试的异常"也会输出 `retry execution failed ... retryCount: 0`（第 49–60 行）；耗尽时 `onRetryableExecution` 与 `onRetryPolicyExhaustion` 各输出一条 WARN。
- **E-11**：`GXRetryListener` 的 `RETRYABLE_TYPE_CACHE`/`MAX_CACHE_SIZE`/淘汰逻辑（第 23–25、116–124 行）只在 `retryable.getName()` 为空时可达（第 109–111 行），而 `GXRetryUtil` 恒返回 `"GXRetryUtil"`（第 136–138 行）、上游 `Retryable.getName()` 默认返回非空类名（`Retryable.java:49–51`），该缓存与 `clearCache()/getConfigInfo()`（第 145–155 行）实质为死代码。
- **E-12**：`LOG_LEVEL`/`ENABLE_VERBOSE_LOGGING` 在类初始化时从 `System.getProperty` 快照（第 29–32 行），无法用 `application.yml`/profile 配置；`shouldLogAtLevel` 对未知值走 `default -> true`（第 141 行），配置 `OFF`/`TRACE` 反而全开。
- **E-13**：`GXRetryConfig` 用静态 `new GXRetryListener()`（第 46 行）注册到模板（第 82 行），而 `GXRetryListener` 同时是 `@Component`，Spring 版实例**不参与任何模板**，替换 Bean 无法改变日志行为。
- **E-14**：`@ConditionalOnMissingBean(RetryTemplate.class)` 用在普通 `@Configuration` 的 `@Bean` 方法上（第 48–50 行），装配顺序不可控；用户自定义 `RetryTemplate` 与本 Bean 并存时，按类型注入（手册 3.3 节推荐用法）将抛 `NoUniqueBeanDefinitionException`。
- **E-15**：模块**无 `src/main/resources`**，无 `AutoConfiguration.imports`/`spring.factories`；`GXRetryConfig`/`GXRetryListener` 仅在下游扫描 `cn.maple`（`GXEnableLeafFramework` 的 `@ComponentScan("cn.maple")`）时生效。
- **E-16**：`GXRetryUtil.MAX_RETRY_TEMPLATE_CACHE_SIZE = 256`（第 34 行），淘汰逻辑为"到达上限即删 128 条、非 LRU"（第 222–231 行），且每次调用都在 `computeIfAbsent` 前执行 `size()` 检查（第 211 行）。
- **E-17**：缓存键包含 `retryConfig.getClass().getName()`（第 210 行），无 Spring 上下文时用的 `STANDALONE_RETRY_CONFIG`（第 36 行）与容器 Bean 是**同一类型**，键无法区分两者；也记录不到配置实例的策略快照。
- **E-18**：`retryOperationAsync` 仅 `CompletableFuture.supplyAsync(...)`（第 155、178 行），无超时；失败统一包装为 `GXBusinessException`（第 161、185 行），`InterruptedException` 不恢复中断标志；`CompletableFuture.cancel(true)` 不会中断工作线程。
- **E-19**：`validateRetryParameters` 的 `multiplier` 区间判断（第 161–165 行）对 `NaN` 两个比较均为 false，`NaN` 通过本模块校验后在 `RetryPolicy.Builder.multiplier` 抛 Spring 自带异常。
- **E-20**：`GXRetryContext` 在单线程重试路径上使用 `AtomicInteger`/`AtomicReference`（`GXRetryUtil` 第 119–120 行），每次尝试构造 `ConcurrentHashMap` + `ConcurrentLinkedQueue`（第 27–29 行），`setAttribute` 为 `putIfAbsent` + `put` 两次哈希（第 70–75 行）。
- **E-21**：4 个 `package-info.java` 均为 `@NullUnmarked`（如 `context/package-info.java:1`）——包级 opt-out 符合宪法 V 的现状约定，但模块**未在任何公共 API 上标注 `@Nullable`**：`setAttribute(String, Object)`/`getAttribute(String)` 显式接受并返回 `null`（第 67、78 行、用例第 42–47 行）、`retryOperation` 的返回值可为 `null`、`GXRetryContext` 无 `@NullMarked` 约束，宪法 V 要求的"公共 API 可能为空处显式标注"未落实。
- **E-22**：`docs/leaf-base-retry开发手册.md` 仍以 spring-retry 为基线：`@EnableRetry`（第 22、158 行）、`SimpleRetryPolicy`/`ExponentialBackOffPolicy`（第 25–31 行）、`org.springframework.retry.support.RetryTemplate` 与 `execute(callback, recovery)` 双参用法（第 166、181–192 行）、`RetryCallback`（第 213、233 行）、"依赖 `spring-retry`"（第 75 行）；第 57 行"每次调用都会创建一个新的 `RetryTemplate`"与静态缓存实现相反；第 276 行"轻微性能开销"亦基于该错误前提。
- **E-23**：测试基线 30 条全部通过，但 `GXRetryConfigTest` 仅有 2 条 null 校验（第 16–36 行），未覆盖 `AGENTS.md` 第 4.4 条要求的 include/exclude 生成与默认映射；无任何 `@SpringBootTest`（违反第 4.6 条）；`GXRetryUtilTest` 标注 `@ExtendWith(MockitoExtension.class)`（第 22 行）但无 mock。
- **E-24**：`retryOperationAsync` 直接使用调用方传入的 `Executor`（第 151、174 行），未使用框架既有的 `GXMdcWrapper*` 包装能力（`leaf-base-framework` 的 `cn.maple.core.framework.wrapper.mdc`），因此 MDC/TraceId 不会传播到工作线程与重试日志（宪法 V 明确禁止绕过上下文传播）。

发现清单（分级）：

- **F-01（高）默认重试集合过宽，业务异常与中断被重试**：`GXRetryUtil` 默认 `Exception.class → true`，`GXRetryConfig` 默认含 `GXBusinessException`。参数错误、余额不足、权限不足等确定性失败会被重复执行最多 3 次，放大非幂等副作用；`InterruptedException` 同样进入重试（E-01、E-08）。
- **F-02（高）无总超时与抖动，最坏阻塞时间不可接受**：未设置 `timeout`/`jitter`，同步路径占用调用线程，参数上限组合约 98 小时；`RetryPolicy` 已支持的两个"防雪崩"能力均未启用（E-02、E-03）。
- **F-03（高）上下文属性不跨尝试传递，`parent` 与 `from` 为死代码**：承诺的"上下文对象与属性传递"在多次尝试间失效，接入方无法用上下文做"第 N 次尝试换端点"这类编排（E-04）。
- **F-04（高）三套重试入口策略互不一致，"统一配置"不成立**：`RetryTemplate` Bean（4 类异常）≠ `GXRetryUtil`（所有异常）≠ `@Retryable`（注解自身默认）；用户自定义 `RetryTemplate` Bean 对工具类无效；默认常量重复两份（E-01、E-05、E-06、E-07）。
- **F-05（高）文档整体错位到不可用**：手册描述的是 spring-retry API，示例代码在本模块依赖下无法编译；"每次调用新建模板"与实现的静态缓存相反（E-22）。
- **F-06（中）中断与耗尽不可区分，恢复回调一律执行**：`catch (RetryException)` 覆盖"中断""超时""耗尽"三种终止原因，中断后仍执行恢复逻辑（可能继续 I/O）（E-18 上游行为）。
- **F-07（中）异步重试不可取消、不可限时、丢失追踪上下文**：`cancel(true)` 不中断工作线程，重试继续跑；失败统一包装为 `GXBusinessException`，声明异常类型 E 丢失，中断标志不恢复；未用既有 MDC 包装能力，重试日志无法与原请求关联（E-18、E-24）。
- **F-08（中）`retryCount` 一名三义**：回调=0 基尝试序号、恢复=总尝试数、`from(RetryState)`=重试次数；不可重试异常时恢复回调报"1 次"，与事实不符（E-08）。
- **F-09（中）监听器"重试开始"日志恒不触发**：`beforeRetry` 的 `== 0` 判据被上游调用时序证伪，日志承诺缺失（E-09）。
- **F-10（中）监听器对"未发生重试"的失败报重试失败，耗尽时双重告警**：日志内容与真实重试次数不匹配，排障时误导（E-10）。
- **F-11（中）模板缓存无界键空间 + 非 LRU 淘汰**：键含连续型参数，256 上限后随机删一半，热点模板可能反复重建；每次调用额外付出容器查询、键拷贝、record 分配（E-16）。
- **F-12（中）缓存键不区分配置来源**：同类型实例共享键，无上下文与有上下文模板互相复用；`GXRetryConfig` 一旦增加状态即静默复用旧策略（E-17）。
- **F-13（中）`@ConditionalOnMissingBean` 用错位置**：普通 `@Configuration` 上条件不可靠，可能产生两个 `RetryTemplate` Bean（E-14）。
- **F-14（中）监听器双实例、扩展点失效**：模板用静态 `new`，`@Component` 版本从不参与；`getConfigInfo()/clearCache()` 仅测试可见（E-11、E-13）。
- **F-15（中）无自动装配、无外部化配置、日志开关不可配**：无 `src/main/resources` 与 `AutoConfiguration.imports`；默认值硬编码；日志级别只能靠 `System.getProperty` 且类初始化时快照，`OFF` 语义反向（E-12、E-15）。
- **F-16（中）测试覆盖与 AGENTS 硬性要求不符**：缺容器启动测试、缺 include/exclude 与日志分支测试、缺并发/取消/淘汰边界测试；测试套件因真实 sleep 需 16.57s（E-23）。
- **F-17（低）`multiplier` 校验非 NaN/Infinity 安全**：NaN 通过本模块校验，异常信息来自上游（E-19）。
- **F-18（低）上下文实现冗余**：单线程路径用原子类型、每尝试两个并发容器、`setAttribute` 两次哈希（E-20）。
- **F-19（低）日志成本与日志放大**：每失败尝试 1 条 WARN（4 个参数装箱）、每次重试成功 1 条 INFO，均无 `isXxxEnabled` 守卫（E-10）。
- **F-20（低）公共 API 空值契约缺失**：包级 `@NullUnmarked` 本身符合宪法 V 的现状约定，但模块未标注任何 `@Nullable`，而 `setAttribute/getAttribute`、`retryOperation` 返回值、回调结果都允许 `null`，与 `AGENTS.md` 第 11 条及宪法 V 的"可能为空处显式标注"不一致（E-21）。
- **F-21（低）重载集合易误用**：`retryOperation(cb, null)` 在 `GXRecoveryCallback` 与 `Map` 间歧义；`(cb, int)` 与 `(cb, int, long)` 语义靠参数个数区分。

已确认为**正确实现**（不得在修复中被破坏）：

- **P-01**：共享 `RetryTemplate` 并发安全——字段仅在构造期赋值，执行期状态为方法内局部的 `MutableRetryState`；`RETRY_TEMPLATE_CACHE` 用 `computeIfAbsent` 读路径安全（E-16 上游源码）。
- **P-02**：`copyRetryExceptions` 生成不可变快照（第 217–220 行），调用方事后修改入参不影响缓存键与在途重试。
- **P-03**：参数校验先于模板创建与执行，非法参数不触达回调（`maxAttempts=0` 用例已固化，第 240–252 行）。
- **P-04**：默认 `includes=Exception` 使 `Error` 不被重试，且 `Error` 原样抛出而非被包装（第 141–153 行用例）。
- **P-05**：`RetryException.getCause()` 非空保证 + `throwAs` sneaky throw，受检异常类型得以保留（`IOException` 用例）。

## Clarifications

### Session 2026-10-23

- Q: 本次交付是"只给审查结论与验证基线"，还是"结论 + 直接修复已确认缺陷"？ → A: 结论 + 可执行验证基线为本次必交付；生产代码修复由 `/speckit.plan` 依据发现分级排期，本规格只要求每条发现可复现、可写出回归测试设计，不要求本次改动生产代码。
- Q: 审查是否需要外部系统（真实下游服务、注册中心、消息中间件）？ → A: 不需要。本模块无外部依赖，全部结论可在纯 JVM 内以既有测试 + 新增回归用例 + 本地启动的 Spring 上下文复现；不引入 Testcontainers。
- Q: 性能结论按什么口径判定？ → A: 不设绝对吞吐/时延门禁；只保留可复算口径（每次调用的容器查询次数、键拷贝与对象分配次数、最坏阻塞时长推导、每失败尝试日志条数）与同环境重跑同量级的复现要求。
- Q: 默认重试策略是否应改为"只重试明确声明的异常"，从而不再自动重试业务异常和线程中断？ → A: 不改默认行为（Option A）：默认异常集保持现状（`GXRetryUtil` 为所有 `Exception`，`RetryTemplate` Bean 含 `GXBusinessException`），仅通过手册章节、Javadoc 与可直接复制的显式排除示例披露风险；F-01 按"文档与可观测性收敛"处置，不计入默认值变更。
- Q: 重试上下文的属性应当如何跨多次尝试传递，才能让"第 2 次尝试读取第 1 次写入的值"成立？ → A: 复制前递（Option A）：每次尝试新建实例，把上一次尝试的属性复制进新实例，`parent` 指向上一实例，`retryCount`/`lastThrowable` 仍表示本次尝试；不采用跨尝试共享同一实例。
- Q: 退避策略的安全边界（总超时、抖动、次数与间隔上限）应当改到什么程度？ → A: 仅暴露能力（Option A）：新增可选总超时与抖动参数，默认仍为"无超时、无抖动"，`maxAttempts ≤ 100` 与 `maxInterval ≤ 3600000ms` 等上限保持不变；用文档给出最坏阻塞推导与"长重试必须显式设置超时"的硬性建议。
- Q: 重试默认参数应当收敛到哪一个"单一来源"，以及容器里的 `RetryTemplate` Bean 是否必须先被工具类使用？ → A: 最小收敛（Option A）：仅把重复的默认常量集中到单点，`GXRetryUtil` 继续自建模板、不读取容器中的 `RetryTemplate` Bean，不新增配置属性，装配方式保持组件扫描；`@ConditionalOnMissingBean` 的共存冲突按文档说明处置。
- Q: 同一个 `retryCount` 在回调、恢复回调与 `from(RetryState)` 中含义不同，应当如何统一？ → A: 统一为"重试次数，首次执行为 0"（Option A）：恢复回调改用 `RetryException.getRetryCount()`（3 次尝试的恢复上下文由 3 变 2），回调侧保留 `getRetryCount()` 作为兼容别名并新增 `getAttemptIndex()`（0 基尝试序号），`from(RetryState)` 与之对齐，配套兼容说明与断言更新。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 审查结论可复核 (Priority: P1)

框架维护者要判断"是否可在新业务中直接使用该模块的默认重试能力"，需要按维度分级、可逐条跳到代码位置的结论，并能用模块自带测试与最小回归用例复现。

**Why this priority**: 审查的唯一价值是结论可信；结论不可复核时，后续计划与修复都建立在猜测上。当前结论中最关键的四条（F-01/F-02/F-03/F-04）都直接影响线上行为。

**Independent Test**: 随机抽取一条结论，按报告给出的文件与行区间定位，用报告给出的命令或用例复现同一现象。

**Acceptance Scenarios**:

1. **Given** 结论"默认重试所有 `Exception`"，**When** 以默认参数重试一个抛 `IllegalArgumentException` 的回调，**Then** 回调被执行 3 次后抛出原异常，与结论一致。
2. **Given** 结论"恢复回调的 `retryCount` 是总尝试数"，**When** 用 3 次尝试耗尽后执行恢复，**Then** 现状恢复上下文 `getRetryCount()==3` 而最后一次回调上下文为 `2`（同名不同义），修复后按 FR-023 统一为 `2`。
3. **Given** 结论"上下文属性不跨尝试"，**When** 在第一次尝试 `setAttribute("x",1)`、第二次尝试读取，**Then** 读不到该属性。
4. **Given** 结论"`@Retryable` 不受 `GXRetryConfig` 默认值影响"，**When** 启动最小 Spring 上下文并对比注解默认与 `RetryTemplate` Bean 策略，**Then** 两者参数不一致且无关联。

---

### User Story 2 - 重试语义与失败处置可判定 (Priority: P1)

接入方需要明确知道：哪些异常会被重试、哪些必须快速失败；重试耗尽、中断、超时三种终止如何区分；恢复回调在什么条件下执行。

**Why this priority**: 重试语义直接决定副作用次数。默认重试业务异常会在生产放大"重复扣款/重复下单"类故障。

**Independent Test**: 对"可重试异常、不可重试异常、中断、耗尽、参数非法"五类输入各构造一次调用，记录执行次数、异常类型、恢复回调是否执行与日志条数。

**Acceptance Scenarios**:

1. **Given** 默认参数，**When** 回调抛 `GXBusinessException`，**Then** 报告给出"是否被重试、执行几次"的唯一定性。
2. **Given** 默认参数，**When** 回调抛 `InterruptedException`，**Then** 报告给出"是否被重试、中断标志是否保留"的唯一定性。
3. **Given** 显式 `retryExceptions` 只含 `IOException.class → true`，**When** 回调抛 `NumberFormatException`，**Then** 只执行 1 次并原样抛出（现状应通过，需回归锁定）。
4. **Given** 线程在退避期间被中断，**When** 重试终止，**Then** 报告给出"恢复回调是否执行、调用方收到什么异常"的判定。

---

### User Story 3 - 并发与生命周期安全可验证 (Priority: P1)

维护者需要知道：多线程同时调用工具类是否安全、共享模板与静态缓存是否有竞态、异步重试能否取消、上下文是否会在线程间串数据。

**Why this priority**: 并发问题在单线程测试中不暴露，上线表现为"重试串用配置""取消无效""日志串号"，排查成本高。

**Independent Test**: 多线程并发调用工具类（含不同参数组合与超过缓存上限的键），断言执行结果正确、缓存大小有界；对异步重试调用 `cancel(true)` 并观察工作线程是否继续。

**Acceptance Scenarios**:

1. **Given** 32 个线程以 8 种不同参数组合并发调用 `retryOperation`，**When** 全部完成，**Then** 每次调用的执行次数与异常类型符合各自参数，无交叉污染。
2. **Given** 缓存已达 256 条，**When** 新键进入，**Then** 报告给出"淘汰条数、是否可能淘汰热点模板、并发下是否可能超过上限"的判定。
3. **Given** 异步重试正在退避，**When** 调用 `CompletableFuture.cancel(true)`，**Then** 报告给出"工作线程是否停止、后续尝试是否仍执行"的判定。
4. **Given** 同一 `GXRetryContext` 被回调跨线程持有并读写属性，**When** 并发读写，**Then** 报告给出"是否有可见性/一致性问题"的判定。

---

### User Story 4 - 性能开销定量化 (Priority: P2)

维护者需要把"重试模块的开销"折算成可复算口径，判断它能否放在热路径上。

**Why this priority**: 该模块被定位为基础能力，会被无差别引入；无口径的"较慢"无法转化为改造计划。

**Independent Test**: 按报告口径统计一次成功调用的容器查询次数、对象分配次数、日志条数；按参数上限推导最坏阻塞时长并与实测退避曲线对照。

**Acceptance Scenarios**:

1. **Given** 一次首轮成功的调用，**When** 统计固定开销，**Then** 报告给出"容器类型查询次数 / 键拷贝次数 / 分配对象清单 / 日志条数"，且可逐条对应代码位置。
2. **Given** `maxAttempts=100, initialInterval=300000, multiplier=10, maxInterval=3600000`，**When** 推导退避总时长，**Then** 报告给出可复算的最坏阻塞时长量级。
3. **Given** 既有测试套件，**When** 记录总耗时与各测试类耗时，**Then** 报告给出"真实 sleep 占用的墙钟时间"口径。
4. **Given** 每次失败尝试的日志，**When** 统计条数与参数装箱，**Then** 报告给出"每失败尝试日志条数"与降级建议。

---

### User Story 5 - 对外契约与文档一致 (Priority: P3)

使用方依赖 `AGENTS.md`、开发手册、方法签名与默认常量判断行为，不一致处必须单列为发现，而不是在审查中顺手改掉。

**Why this priority**: 契约漂移不直接导致运行时错误，但会让接入方按错误前提设计（例如按手册写 `execute(callback, recovery)` 双参调用）。

**Independent Test**: 逐条核对文档声明的技术栈、类名、方法签名、默认参数、线程安全结论与实现，给出"一致 / 不一致 / 未覆盖"判定。

**Acceptance Scenarios**:

1. **Given** 手册称依赖 `spring-retry` 并使用 `org.springframework.retry.support.RetryTemplate`，**When** 核对模块依赖与 import，**Then** 该声明被判为不一致并给出证据。
2. **Given** 手册称"每次 `retryOperation` 创建新模板"，**When** 核对实现，**Then** 记录实际为静态缓存复用。
3. **Given** 手册称 `GXRetryListener` 实现 `open/onError/close`，**When** 核对接口方法，**Then** 记录实际实现的是 Spring 7 的六回调 `RetryListener`。

---

### User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

计划阶段需要把发现直接转成任务：每条发现具备编号、级别、维度、位置、触发条件、影响、证据、回归设计。

**Why this priority**: 审查不是终点；不能落到可执行任务的结论等于没有结论。

**Independent Test**: 抽取任一发现，检查八项要素是否齐备；抽取任一"既定契约"检查是否被明确标注，避免被误改。

**Acceptance Scenarios**:

1. **Given** F-01，**When** 交由计划阶段，**Then** 无需补充上下文即可拆出"先写暴露重复执行的测试、再收敛默认异常集"的任务。
2. **Given** `retryOperationAsync` 统一包装为 `GXBusinessException`（既有测试固化），**When** 查阅报告，**Then** 明确标注为既定契约，修复只能"新增重载/新增能力"，不得改变既有语义。

---

### Edge Cases

- `maxAttempts = 0 / 1 / 100 / 101`；`initialInterval = 99 / 100 / 300000 / 300001`。
- `multiplier = NaN / Infinite / 1.0 / 10.0 / 1.0 以下`；`initialInterval > maxInterval`。
- `retryExceptions` 为空 map、只含 `false` 映射、含 `Exception.class → true` 且 `IllegalArgumentException → false`、含 `null` 键或 `null` 值。
- 回调抛 `Error`（`AssertionError`/`OutOfMemoryError`）、抛 `InterruptedException`、抛受检异常、返回 `null`。
- 恢复回调自身抛异常、恢复回调返回 `null`、异常不可重试时恢复回调仍被调用。
- 退避期间线程被中断；`Thread.currentThread().interrupt()` 后的后续调用。
- 缓存键数量达到 255/256/257；多线程同时触发淘汰；调用方在调用后修改入参 `Map`。
- 无 Spring 上下文调用（`STANDALONE_RETRY_CONFIG`）与有上下文调用混跑；容器中存在用户自定义 `RetryTemplate` Bean。
- `GXRetryListener` 的 `maple.framework.retry.log.level` 取 `OFF/TRACE/WARN/ERROR/DEBUG` 与非法值。
- `retryable.getName()` 返回 `null`/空白；`retryState.getExceptions()` 为空。
- 异步执行器拒绝任务（`RejectedExecutionException`）、异步路径 `cancel(true)`、`CompletableFuture` 未完成时容器关闭。
- 异步重试在带 TraceId 的请求上下文中失败，检查工作线程与监听器日志能否关联到原请求。
- 依赖恢复上下文 `retryCount` 做告警或对账的下游：3 次尝试后该值由 3 变 2、不可重试异常时由 1 变 0，需按 FR-023 迁移。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖模块全部 11 个生产源码文件与 4 个既有测试类；每个文件 MUST 有明确判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因），不得跳过。
- **FR-002**: 每条发现 MUST 标注维度（逻辑正确性 / 线程安全 / 性能）、严重级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件与影响面。
- **FR-003**: 每条发现 MUST 附带复现方式：既有用例、可新增的回归用例设计或明确构造步骤；仅凭阅读的判断 MUST 标注为"未验证假设"。
- **FR-004**: 审查 MUST 以**上游框架源码**为准核对 `RetryTemplate`/`RetryPolicy`/`RetryState`/`RetryException`/`Retryable` 的实际语义（回调时序、`retryCount` 定义、`includes/excludes` 匹配规则、`timeout` 与 `jitter` 默认值），不得仅凭类名推断。
- **FR-005**: 默认重试集合 MUST 按"入口 × 默认 includes/excludes × 实际重试的异常类型"给出矩阵，并 MUST 明确指出 `GXBusinessException` 与 `InterruptedException` 的处境（对应 F-01、F-04）。
- **FR-006**: 线程安全结论 MUST 覆盖三类场景：共享对象（静态缓存、共享 `RetryTemplate`、静态监听器）、生命周期（异步工作线程、缓存淘汰、上下文跨线程持有）、竞态（并发调用同一键、调用方事后修改入参）。
- **FR-007**: 性能结论 MUST 给出可复算口径：单次调用的容器类型查询次数、键拷贝与对象分配次数、每失败尝试日志条数、最坏退避阻塞时长推导、既有测试墙钟耗时。
- **FR-008**: 失败终止语义 MUST 按"耗尽 / 中断 / 超时 / 不可重试"四类逐条定性，说明调用方收到的异常、恢复回调是否执行、中断标志是否保留；不可区分之处 MUST 列为发现（对应 F-06、F-08）。
- **FR-009**: 既有测试 MUST 全部执行并记录结果（当前基线：30 执行 / 0 失败）；MUST NOT 通过删除或跳过用例来"通过"；覆盖缺口 MUST 单列为发现（对应 F-16）。
- **FR-010**: 对外契约（`AGENTS.md`、开发手册、公开方法签名、默认常量、`System.getProperty` 开关）MUST 与实现逐条对照，不一致处 MUST 单列为发现，MUST NOT 在审查阶段修改生产代码。
- **FR-011**: 审查报告 MUST 独立成文，`spec.md` 只保留摘要与引用，两者在交付时 MUST 相互一致。
- **FR-012**: 每条发现 MUST 给出"能在旧代码上失败、在新代码上通过"的回归测试设计（输入、断言点、期望差异），且该测试 MUST 无需外部依赖即可运行。
- **FR-013**: 本次交付 MUST NOT 改变生产代码行为；提出修复建议时 MUST 说明其对既有成功路径、默认参数、异常传播语义与既有测试断言的影响。
- **FR-014**: 审查 MUST 明确区分"缺陷"与"既定契约"（如 `retryOperationAsync` 统一包装异常、`retryOperation` sneaky throw、模板静态缓存），对既定契约 MUST 要求文档同步或新增能力，而非直接改写。
- **FR-015**: 上下文（`GXRetryContext`）审查 MUST 覆盖"属性是否跨尝试传递""`parent` 是否可达""是否可跨线程安全持有"，并 MUST 给出"该能力当前是否可用"的唯一定性（对应 F-03）。
- **FR-016**: 审查结论 MUST 包含风险排序依据（影响面 × 触发概率 × 可观测性），使计划阶段可直接确定修复批次。
- **FR-017**: 文档修订方向 MUST 按"以 Spring Framework 7 `org.springframework.core.retry` 为唯一基线重写手册示例与类名，删除 spring-retry 表述"收敛；MUST NOT 通过改回 spring-retry 来消除文档与实现差异（对应 F-05）。
- **FR-018**: 配置收敛 MUST 限于"默认常量单点定义"（消除 `GXRetryUtil` 与 `GXRetryConfig` 中重复的 3 / 1000 / 2.0 / 10000）与"文档说明"，MUST NOT 新增配置属性、MUST NOT 让 `GXRetryUtil` 改为读取容器中的 `RetryTemplate` Bean、MUST NOT 改变现有装配方式（保持 `@Configuration` + 组件扫描，不迁自动装配清单）；MUST 保持既有方法签名、默认异常集与默认参数不变；手册 MUST 说明"下游自定义 `RetryTemplate` Bean 与本模块 Bean 共存时按类型注入的冲突及规避方式（`@Qualifier` / `@Primary`）"（对应 F-04、F-13、F-15）。
- **FR-019**: 线程安全修复设计 MUST 覆盖"缓存淘汰原子性""缓存键覆盖全部影响策略的入参""异步取消可中断"三点，且 MUST 不引入新的全局锁到调用主路径；由于 FR-018 不引入配置文件、工具类也不读容器 Bean，缓存键 MUST NOT 依赖实例身份，但 MUST 保留"未来引入策略属性时同步纳入键"的注释与回归测试位（对应 F-07、F-11、F-12）。
- **FR-020**: 性能修复设计 MUST 给出"退避可加抖动""可选总超时""日志级别守卫"三项的最小改动方案与对既有测试断言的兼容说明；抖动与超时 MUST 为可选能力且默认值保持"无抖动、无超时"，`maxAttempts` 与 `maxInterval` 的上限 MUST 保持不变（对应 F-02、F-19）。
- **FR-021**: 异步重试的上下文传播 MUST 按宪法 V 收敛：要么复用既有 `GXMdcWrapper*` 包装能力传播 MDC/TraceId，要么在手册中明确"不传播"并给出替代方案；审查 MUST 给出当前"是否可关联原请求"的判定（对应 F-07）。
- **FR-022**: 默认异常集风险 MUST 以"不改默认值"的方式可发现：手册 MUST 增加专章说明"哪些异常会被默认重试、哪些场景必须显式排除"，MUST 给出可直接复制的 `Map<Class<? extends Throwable>, Boolean>` 排除示例；同一风险 MUST 在 `GXRetryUtil` 与 `GXRetryConfig` 的 Javadoc 中就近提示；排查结论 MUST 能定位到这两处文档（对应 F-01、F-05）。
- **FR-023**: `retryCount` 语义 MUST 统一为"重试次数，首次执行为 0"：恢复回调 MUST 使用 `RetryException.getRetryCount()`（不得再用 `getExceptions().size()`），回调侧 MUST 保留 `getRetryCount()` 作为兼容别名并新增 `getAttemptIndex()`（0 基尝试序号），`GXRetryContext.from(RetryState)` MUST 与该语义对齐；该变更 MUST 附兼容说明（3 次尝试的恢复上下文由 3 变 2；不可重试异常的恢复上下文由 1 变 0）并 MUST 同步更新既有断言（对应 F-08）。

### Key Entities *(include if data involved)*

- **审查发现（Finding）**：编号、级别、维度、代码位置、触发条件、影响面、证据、复现方式、回归测试设计、修复建议。
- **重试语义矩阵**：入口（`GXRetryUtil` / `RetryTemplate` Bean / `@Retryable`）× 默认异常集 × 默认次数与退避 × 是否受全局配置影响。
- **失败终止矩阵**：终止原因（耗尽 / 中断 / 超时 / 不可重试）× 调用方异常 × 恢复回调是否执行 × 中断标志。
- **并发风险矩阵**：对象（静态模板缓存 / 共享模板 / 监听器 / 上下文 / 异步任务）× 共享范围 × 现状保护手段 × 收敛路径。
- **性能口径**：容器查询次数、键拷贝与分配次数、每失败尝试日志条数、最坏退避阻塞时长、测试墙钟耗时。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查覆盖 100% 的 11 个生产源码文件与 4 个既有测试类，且 100% 的发现记录在独立报告中；新维护者可在 15 分钟内复现任一结论。
- **SC-002**: 100% 的发现具备"级别 + 维度 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归设计"；0 条仅凭阅读的结论混入已确认项。
- **SC-003**: 重试语义矩阵覆盖 3 个入口 × 至少 4 类异常，判定率 100%，且每条判定附上游源码或实测证据。
- **SC-004**: 失败终止矩阵对"耗尽 / 中断 / 超时 / 不可重试"四类各给出唯一定性，覆盖率 100%。
- **SC-005**: 性能结论给出可复算口径，且最坏阻塞时长推导可被独立复算（误差 < 1%）；MUST NOT 以绝对吞吐或时延数值作为通过门槛。
- **SC-006**: 既有测试执行率 100%（基线 30 执行 / 0 失败），测试覆盖缺口 100% 被记录为发现并对应到 `AGENTS.md` 的具体条款。
- **SC-007**: 高危发现（F-01 / F-02 / F-03 / F-04 / F-05）100% 具备可构造的故障现场与回归测试设计，计划阶段无需补充调研即可拆分为任务。
- **SC-008**: 文档一致性核对覆盖手册中 100% 的类名、方法签名、依赖声明与默认参数，偏差 100% 列为发现。

## Assumptions

- 目标运行形态为 JDK 21+ + Spring Boot 4.1.x + Spring Framework 7.0.9，下游通过 `@GXEnableLeafFramework`（`@ComponentScan("cn.maple")`）装配本模块组件；模块自身不提供自动装配注册。
- 本次为审查交付，不修改生产代码；不引入新的重试库、指标库或分布式补偿能力，只审查现状。
- 重试的业务幂等性由调用方负责；本模块只保证"按策略重复执行"，不提供去重能力。
- 性能数据以单机、单 JVM、无外部 I/O 的回调为前提；结论以"可复算口径 + 同量级复现"为验收标准，不承诺生产容量。
- 既有测试中被锁定的**执行时机类**行为（`retryOperationAsync` 包装为 `GXBusinessException`、恢复回调在不可重试异常时仍执行、静态缓存复用模板）按"既定契约"处理，审查只要求文档同步或新增能力；**计数值类**断言（恢复上下文 `retryCount` 由 3 变 2、不可重试异常时由 1 变 0）按 FR-023 一并更新，不视为契约破坏。
- 上游框架源码以本地仓库 `spring-core-7.0.9-sources.jar` / `spring-context-7.0.9-sources.jar` 为准；升级导致的行为差异不在本次范围。
- 审查不覆盖 `leaf-base-framework` 的 `GXSpringContextUtils` 实现细节，只审查本模块对它的调用方式与失败形态。
