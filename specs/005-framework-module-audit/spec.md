# Feature Specification: leaf-base-framework 稳定性、正确性、线程安全与性能审查

**Feature Branch**: `005-framework-module-audit`（本次未通过 hook 创建 git 分支）

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "审查 @leaf-base-framework 模块，该模块是整个框架的基础，对于稳定性、正确性、线程安全性与性能的要求都需要非常的高。"

## 背景与审查结论摘要

本规格把"`leaf-base-framework` 作为全框架基础模块，是否达到稳定性、正确性、线程安全性与性能的高要求"这一审查问题，转化为可验收的交付要求。审查对象与证据来源：

1. `leaf-base-framework` 全部 309 个生产源码文件（含各包 `package-info.java`）、`src/main/resources` 下的 `META-INF` 元数据；
2. 既有 65 个测试类、518 个 `@Test` 用例（按 `@Test\b` 逐方法统计）（`src/test/java` 下全部文件）；
3. 契约来源：`leaf-base-framework/AGENTS.md`、`.specify/memory/constitution.md`、根 `README.md`；
4. 依赖基线：JDK 21（虚拟线程）、Spring Boot 4.1.x / Spring Framework 7.x、ErrorProne + NullAway（`AnnotatedPackages=cn.maple`）。

审查结论（按四维度）：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 稳定性 | 异步执行、事件订阅、缓存装配三条主链路均存在"静默失效"入口：异步不传播上下文、代理 Bean 的订阅方法不被注册、未注册的缓存名取到 `null`；失败路径普遍以 `return null` / `return 入参` / `catch ignored` 收敛 | 存在高危缺陷，不建议按当前状态作为基础依赖对外承诺 |
| 正确性 | 事件载荷"发布即快照"的契约对自定义对象不成立；动态方法调用与同模块解析侧的白名单策略不一致；全局异常处理把容器内部信息回给调用方 | 存在契约级不一致 |
| 线程安全 | `ThreadLocal`/MDC 清理不完整（只恢复单一 traceId 键）、限流器使用静态无界结构、上传服务用 `volatile` 字段跨请求传递状态、拷贝缓存以弱引用键导致缓存退化 | 存在跨请求/跨容器污染路径 |
| 性能 | XSS 改写对最多 50MB 请求体做 2 倍内存物化、限流器每请求 O(n) 遍历、并发工具在日志参数位无条件求值格式化、反射近似匹配与树构建为 O(n²) | 存在可定位的结构性开销 |

关键证据（均可按位置复核，路径相对 `src/main/java/cn/maple/core/framework`）：

- **E-01**：`config/GXAsyncConfig.java:24-29` 在 `getAsyncExecutor()` 内 `new SimpleAsyncTaskExecutor("maple-async-vt-")` 并启用虚拟线程，无 `TaskDecorator`；`wrapper/mdc/GXMdcWrapper*` 三个包装器在全仓库生产代码中无任何 Bean 注册点（仅测试引用）。
- **E-02**：`filter/GXBaseRequestLoggingFilter.java:26-43` 仅保存并恢复 traceId，`finally` 调用 `GXTraceIdContextUtils.restoreTraceId`（`util/GXTraceIdContextUtils.java:43-49`）只操作单个 MDC 键，其余 MDC 键不清理。
- **E-03**：`wrapper/mdc/GXMdcWrapperForkJoinPool.java:45-53` 的 `submit(ForkJoinTask)` / `invoke(ForkJoinTask)` 直接委派父类，未做上下文包装。（原列的"`GXMdcWrapperThreadPoolTaskExecutor` 未覆写 `submitListenable`"经复核不成立：Spring Framework 7 已移除该方法，见报告 E-23。）
- **E-04**：`util/GXCurrentRequestContextUtils.java:100` 声明静态 `ConcurrentMap<String, ConcurrentLinkedDeque<Long>> requestTimestamps`；`isRateLimited`（630-649）以 `size()`（O(n)）判定并对 `clientId` 直接建键；`isRPC()`（417-420）以"无请求上下文"等价于"是 RPC"。
- **E-05**：`util/GXConcurrentToolsUtils.java:39` 为 `static final` 线程池，`shutdownThreadPool()`（460-507）后无重建路径；`83-93` 使用 `CallerRunsPolicy` + `submit()`；`163-166`、`226-231`、`248-250` 把 `getThreadPoolStatus()`（内部 `String.format` + 6 次原子读）作为日志实参传入。
- **E-06**：`event/GXBaseEvent.java:95-153` 的 `snapshot` 仅覆盖 `Date`/`Calendar`/`Dict`/`Map`/`Set`/`Collection`/数组，兜底 `return source`（153 行）——自定义 DTO 载荷不被快照；`129` 行连 Map 的 key 也一并快照；`211-223` 以深度 64 / 节点 10 万为硬上限直接抛异常。
- **E-07**：`event/processor/GuavaSyncEventBusBeanPostProcessor.java:57`、`94-108` 用 `bean.getClass()` 与 `method.isAnnotationPresent(Subscribe.class)` 匹配，代理类方法不带注解时空集返回；`62-73` 以 `beanName` 去重。`event/processor/GuavaASyncEventBusBeanPostProcessor.java:26-108` 同构。
- **E-08**：`event/config/GuavaEventBusConfig.java:77-89` 的异步执行器 `concurrencyLimit = CPU*2` 且 `setRejectTasksWhenLimitReached(false)`（达上限时阻塞调用线程）；`event/center/AsyncEventBusCenter.java:30-39` 另建一套 daemon 执行器（队列 `CPU*2048`、`CallerRunsPolicy`、不关闭），两套并存。
- **E-09**：`util/GXEventPublisherUtils.java:157-159` 以 `System.identityHashCode` 作为订阅注册缓存键（`22`、`123-139` 为无上限缓存且无 `unregister` 生产调用点）。
- **E-10**：`util/GXCaffeineCacheUtils.java:141-144` 默认缓存仅 `maximumSize(1024)`、无过期；`68-92` 的 `clear` 不清 loader 映射、`101-129` 先移缓存后移 loader；`173-176` 的 `cast` 无类型校验。`config/cache/GXCaffeineCacheConfig.java:48` 关闭动态缓存创建（`setCacheNames`）、`126-131` 默认 `softValues` + 24h access 过期；全模块无 `@EnableCaching`、无 `KeyGenerator` Bean。
- **E-11**：`util/GXCacheKeysUtils.java:63-66` 未知配置名统一落到 `maple:default:<key>`；`84-89` 空白 key 统一为 `"key"`。
- **E-12**：`filter/GXXssHttpServletRequestWrapper.java:26-30` 默认 50MB 上限并在构造期读全量请求体；`127-145` 逐个字符串值做 HTML 过滤；`276-290` 改写 `getContentLength*` 但 `getHeader("Content-Length")` 仍为原值。`filter/GXHTMLFilter.java:210-217` 中 `&`→`&amp;`、`P_QUOTE` 与 `P_LEFT_ARROW` 同为 `<`。实测：`a&b<c` → `a&amp;b`（尾段被丢弃）、`5 < 6 and 7 > 6` → `5  6`；改写后 `getContentLength()=18` 而 `Content-Length` 头仍为 `16`。
- **E-13**：`web/advice/GXRequestBodyAdvice.java:80-98` 每次读取后把完整请求体 JSON 写入请求属性（含口令类字段）；`web/advice/GXResponseBodyAdvice.java:46-74` 在 `cors.allow.credentials=true` 且配置 `cors.cookie.virusId` 时，每个响应下发 `UserData` Cookie，`62` 行默认密钥为硬编码常量。
- **E-14**：`handler/GXExceptionHandler.java:127-136` 把 `MultipartException` 的 `getCause().getMessage()` 直接作为响应消息；`118-125` 的兜底处理把 `e.getCause().getMessage()` 放进响应 `dict` 的 `msg`；`210-224` 把 `IllegalArgumentException` 的原始 message 切片放入响应 `dict`；`66-78` 参数类型错误返回 `ok(500)`。
- **E-15**：`service/impl/GXFileUploadServiceImpl.java:31` 以 `volatile String fileStoragePath` 作为实例字段，`45`、`61`、`79` 在请求路径上读写。
- **E-16**：`service/impl/GXSensitiveFieldDeEncryptServiceImpl.java:22-29`、`37-44` 加解密异常时原样返回入参；`52-57` 默认密钥为硬编码字符串。`service/impl/GXSensitiveDataDecryptServiceImpl.java:45-49`、`GXSensitiveDataEncryptServiceImpl.java:41-42` 为 `catch (Exception ignored)`。
- **E-17**：`util/cglib/GXBeanCopierCache.java:11` 使用 `WeakKeyConcurrentMap`，`28-34` 的 `genKey` 每次新建 `String` 键，条目随即失去强引用。
- **E-18**：`service/impl/GXDynamicCallMethodServiceImpl.java:66-85` 以 `Class.forName(className)` 加载调用方给定的任意类名，`68-75` 在缓存达 1000 时整体 `clear`；同模块 `service/impl/GXParseDynamicCallParamServiceImpl.java:250-258` 却存在 `cn.maple.` 前缀白名单。
- **E-19**：`util/GXSpELToolUtils.java:85`、`107`、`128`、`263`、`361` 全部使用 `StandardEvaluationContext`；`175`、`362-363` 把 `methodName` 拼入表达式文本，依赖 `235-240`、`350-357` 的存在性预校验拦截注入。
- **E-20**：`web/config/GXWebMvcConfigurer.java:40-47` 按 Bean 名排序注册拦截器，`addInterceptor` 未设置 include/exclude 路径；`web/config/GXHandlerMethodArgumentResolverConfigurer.java:23-33` 以 `assert` 做生产判空。
- **E-21**：`util/GXCommonUtils.java:452-463` 反射缓存超 4096 整体 `clear`；`476-512` 以打分方式选择重载方法；`626-676` 的 `buildTree` 每层全量过滤并逐元素反射调用。
- **E-22**：测试基线为 65 个测试类、518 个 `@Test`（实测执行 518）；`config/GXAsyncConfigTest` 仅断言虚拟线程与异常处理器委派，无上下文传播断言；`handler/GXExceptionHandler`（26 个处理器）无任何测试类。

发现清单（分级）：

- **F-01（高）异步与线程池不传播上下文**：`@Async` 执行器无上下文包装、ForkJoin 与 `submitListenable` 路径未覆写，三个 MDC 包装器在生产链路中从未装配；跨线程日志断链，链路上任何依赖 traceId 的排查手段失效（E-01、E-03）。
- **F-02（高）MDC 清理不完整**：请求结束只恢复 traceId 一个键，容器线程复用后其他键（如用户标识）串到下一个请求（E-02）。
- **F-03（高）加解密静默降级且默认密钥硬编码**：解密失败直接把密文当明文返回、加密失败直接把明文返回，无任何日志；默认密钥随源码分发（E-16）。
- **F-04（高）敏感数据进入可观测与响应链路**：完整请求体（含口令）被序列化进请求属性；响应侧在启用凭据 Cookie 时下发含用户数据的 Cookie，且使用硬编码默认密钥（E-13）。
- **F-05（高）XSS 改写在正常业务上产生数据丢失与内存放大**：`&`、`<` 被改写导致正常文本被实体化甚至整段丢弃（实测 `5 < 6 and 7 > 6` → `5  6`）；50MB 上限 + 读全量 + 二次渲染使峰值内存约为请求体 2 倍；改写后 `Content-Length` 头与实际长度不一致（E-12）。
- **F-06（高）动态方法调用无类白名单**：任意类名可被加载并反射调用，与同模块解析侧的白名单策略不一致；缓存满时整体清空会造成抖动（E-18）。
- **F-07（高）全局异常处理泄漏内部信息**：兜底异常处理、容器异常与原始参数异常 message 均可直接进入响应体（`msg` 或 `dict`）；参数类型错误以成功码语义返回（E-14）。
- **F-08（高）事件"发布即快照"契约不成立**：自定义对象载荷共享引用，发布后修改对订阅方可见；Map 的 key 被替换为副本，订阅方用原 key 取值可能失败（E-06）。
- **F-09（中）事件订阅在代理 Bean 与多实例 Bean 上漏注册**：代理类方法不带注解导致"零订阅"静默通过；`beanName` 去重使 prototype 多实例只注册第一个（E-07）。
- **F-10（中）事件执行模型不确定**：达并发上限时阻塞发布线程（订阅方内再发布会自锁风险），且存在两套线程模型不一致的执行器与静态回退路径（E-08）。
- **F-11（中）订阅注册缓存用 identityHash 作键且无上限、无注销**：哈希碰撞会误判"已注册"而丢事件；短生命周期订阅者永久占位（E-09）。
- **F-12（中）缓存装配缺少 key 隔离**：无 `KeyGenerator`，同一缓存名下不同方法、相同参数会共用 key；未注册的缓存名取到 `null`；工具默认缓存无过期；不同命名空间在缺前缀时键归并（E-10、E-11）。
- **F-13（中）并发工具语义含混**：队满时在提交线程执行导致时序反转；线程池关闭后不可重建且失败与"结果为空"不可区分（E-05）。
- **F-14（中）限流状态静态无界**：以不可信入参为键的静态 Map 在 60 秒窗口内可被无上限撑大，且在容器关闭后不释放（E-04）。
- **F-15（中）上传服务跨请求共享可变状态**：`volatile` 实例字段在并发请求间互相覆盖，删除操作可能落到其他请求的子目录（E-15）。
- **F-16（中）反射与拷贝缓存实际失效**：BeanCopier 缓存因弱引用键几乎每次重建（每次生成字节码）；反射缓存满即整体清空；`buildTree` 为 O(n²)（E-17、E-21）。
- **F-17（中）表达式求值能力过宽**：使用可调用任意类型/方法的求值上下文，且部分入口把方法名拼进表达式文本，安全性依赖预校验（E-19）。
- **F-18（中）Web 装配顺序与可排除性缺失**：拦截器按 Bean 名排序而非声明顺序、无 include/exclude；解析器以 `assert` 判空（E-20）。
- **F-19（低）语义与重复配置**：`isRPC()` 以"无上下文"替代"是否 RPC"；`getTraceId()` 返回空串使 `== null` 判断失效；CORS 存在配置死分支与 MVC/CorsFilter 双份配置；`GXAuthorizationInterceptor` 默认放行、`GXBaseSSOPermissionInterceptor` 为空类。
- **F-20（低）可观测性缺口**：日志助手在部分级别不提取 `Throwable` 导致栈丢失；缓存异常处理器只记日志不重抛、无指标；反射失败以 `warn` + `null` 收敛，查询结果退化为空集合。
- **F-21（低）契约文档与实现不一致**：`AGENTS.md` 要求"涉及 ThreadLocal/MDC/TraceId/异步执行必须有清理、恢复和异常传播路径"、宪法要求"`ThreadLocal` 成对 push/poll"，实现未满足；需单列为契约偏差而非顺手修改。

## Clarifications

### Session 2026-10-22

- Q: 本次交付是"只给审查结论与测试基线"，还是"结论 + 直接修复已确认缺陷"？ → A: 审查结论 + 测试基线为本次必交付；缺陷修复由后续 `/speckit.plan` 依据本规格的发现分级另行排期，本规格只要求"每条缺陷可验证、可写出回归测试设计"，不要求本次改动生产代码。
- Q: 审查范围是全部包，还是只审高风险包（上下文/并发/事件/Web）？ → A: 全覆盖 309 个生产文件；DTO、注解、常量、`ddd` 等纯契约包以"契约核对 + 抽样行为验证"方式判定，其余包逐文件判定，全部计入覆盖台账。
- Q: 是否要求引入真实中间件或压测环境？ → A: 不要求。性能结论以代码路径的可复算口径（往返/分配/复杂度/上限）为主，可执行冒烟仅作旁证；验证用例必须能在无外部依赖环境运行或优雅跳过。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 基础模块可用性结论可复核 (Priority: P1)

框架维护者要在"下游业务是否可以直接依赖该模块"这个决策上签字，需要的是分级结论与逐条证据，而不是"整体看起来还行"。每条结论都必须能跳到具体位置并有可复现验证方式。

**Why this priority**: 该模块被全部下游模块依赖，结论不可复核等于把风险转嫁给所有使用方。

**Independent Test**: 随机抽取结论条目，按报告给出的位置与命令复现，断言现象与描述一致；对高危项必须能构造出故障现场。

**Acceptance Scenarios**:

1. **Given** 一条"异步执行不传播上下文"的结论，**When** 在 `@Async` 方法内打印当前 traceId，**Then** 观察到与提交线程不一致（或为空），与结论一致。
2. **Given** 一条"解密失败静默返回入参"的结论，**When** 传入长度非法的密文，**Then** 返回原字符串且无异常、无日志，与结论一致。
3. **Given** 一条"XSS 改写污染 JSON 字符串值"的结论，**When** 提交含 `&` 与 `<` 的合法业务文本，**Then** 读取到的值已被改写，与结论一致。
4. **Given** 一条"事件载荷不快照自定义对象"的结论，**When** 发布后修改原对象字段，**Then** 订阅方读到修改后的值，与结论一致。

---

### User Story 2 - 线程安全与上下文纪律可被验证 (Priority: P1)

运维与开发需要知道：在容器线程复用、虚拟线程、线程池、上下文重建四种运行形态下，是否会出现跨请求污染、静态状态无界增长、资源不释放。这些问题在单线程功能测试中不会暴露。

**Why this priority**: 基础模块的上下文污染会以"另一个用户的身份"表现出来，属于不可接受的安全级缺陷。

**Independent Test**: 在隔离测试中构造"同一线程连续处理两个请求""异步/并行流任务""容器关闭后重建""大量不同 clientId 打点"四类场景，断言上下文、静态结构规模与资源释放结果。

**Acceptance Scenarios**:

1. **Given** 请求 A 在线程 T 上写入自定义 MDC 键，**When** 线程 T 处理请求 B，**Then** 请求 B 观察不到 A 的键（或该问题被报告明确列为缺陷并给出复现用例）。
2. **Given** 提交到 `@Async` 与 ForkJoin 的任务，**When** 在任务内读取 traceId，**Then** 与提交侧一致。
3. **Given** 使用 10 万个不同 `clientId` 调用限流入口，**When** 观察静态结构规模，**Then** 报告给出增长上界与释放时机，而不是"理论上不会涨"。
4. **Given** 容器关闭后重新创建，**When** 再次使用上下文相关工具，**Then** 不出现持有旧容器/旧执行器的现象。

---

### User Story 3 - 性能开销定量化 (Priority: P2)

维护者需要判断该模块作为公共横切层（每个请求、每次调用都会经过）是否会引入不可接受的开销，并且要能复算：单请求内存峰值、单次调用的分配次数、算法复杂度、上限配置。

**Why this priority**: 横切层开销会被全部下游放大，但不像功能缺陷那样立刻可见，必须以口径固定。

**Independent Test**: 按报告口径复算一遍（分配次数 × 调用次数、复杂度 × 规模、上限 × 并发），断言与报告数值一致；对可执行项跑一次批量冒烟。

**Acceptance Scenarios**:

1. **Given** XSS 改写链路，**When** 报告给出"请求体峰值内存 ≈ 2 倍 body × 并发数"，**Then** 该口径可按代码路径逐条复算。
2. **Given** 并发工具在日志参数位求值，**When** 以 1 万任务运行，**Then** 报告给出格式化次数与可优化点，并说明日志级别关闭时是否仍发生。
3. **Given** 限流入口，**When** 按每 `clientId` 窗口内 N 次请求计算，**Then** 报告给出单次调用的遍历代价与窗口上限。
4. **Given** 树构建与反射匹配，**When** 以 1000 节点输入，**Then** 报告给出复杂度量级与实际耗时的量级关系。

---

### User Story 4 - 对外契约与安全边界一致 (Priority: P2)

使用方依赖注解、DTO、异常语义、配置键与安全默认值判断行为；安全默认值（密钥、白名单、放行策略）必须以"显式配置"为前提。文档与实现不一致时必须作为独立发现记录。

**Why this priority**: 契约漂移与不安全默认值会被下游无感继承，影响面大于单点缺陷。

**Independent Test**: 逐条核对 `AGENTS.md`、宪法与实现：上下文清理、事件快照、统一响应/异常、配置开关；对安全默认值逐项给出"默认是否安全"判定。

**Acceptance Scenarios**:

1. **Given** 宪法要求"业务失败必须抛异常，不得用返回 `null`/空集合表达失败"，**When** 核对异常处理与工具方法，**Then** 报告逐条给出符合/不符合及位置。
2. **Given** 任一齐默认密钥常量，**When** 未配置对应配置项就启动，**Then** 报告给出"是否可用默认值即上线"的明确判定。
3. **Given** 动态调用入口，**When** 传入模块外的类名，**Then** 报告给出当前实际行为（可调用 / 被拒）与安全影响。

---

### User Story 5 - 结论可驱动后续修复排期 (Priority: P3)

计划阶段需要把发现直接转成任务：每条发现都应有独立编号、严重级别、影响面、修复方向与"能在旧代码失败、在新代码通过"的回归测试设计。

**Why this priority**: 审查不是终点；不能落到可执行任务的结论等于没有结论。

**Independent Test**: 抽取任一发现，检查是否同时具备"编号 + 级别 + 维度 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归测试设计"九项，缺任一项即不合格。

**Acceptance Scenarios**:

1. **Given** 一条高危发现，**When** 交由计划阶段，**Then** 无需补充上下文即可拆出"测试先行"的任务。
2. **Given** 一条被判定为"既定契约"的行为（如 `getTraceId()` 返回空串），**When** 查阅报告，**Then** 明确标注为契约而非缺陷，避免被误改。

---

### Edge Cases

- 请求线程池复用（同一线程连续处理不同用户请求）时的 MDC/ThreadLocal 残留。
- 虚拟线程与平台线程混用、`@Async` 嵌套调用、任务内部再次提交任务。
- 线程池已 `shutdown` 后继续提交、容器关闭后静态单例仍被访问。
- 事件订阅方为 CGLIB/JDK 代理、prototype 多实例、同名 Bean 覆盖。
- 事件载荷含自引用结构、含可变键的 Map、超深或超大结构。
- 缓存名为空、缓存名前缀未知、同一缓存名不同类型参数、并发 `clear` 与 `get`。
- 请求体为非 JSON、超大（接近上限）、空体、已被上游过滤器读取。
- 敏感字段位于父类、字段为集合/嵌套对象、密钥缺失或长度非法。
- 上传并发、原始文件名含路径分隔符、扩展名与 MIME 不一致。
- 异常类型为容器专有异常、异常 message 含内部路径、参数绑定失败。
- 拦截器/解析器在无请求上下文（异步、定时任务）中被调用。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖 `leaf-base-framework` 全部 309 个生产源码文件、`src/main/resources` 下的框架元数据与 65 个测试类；每个文件 MUST 有明确判定（无缺陷 / 风险 / 缺陷 / 仅契约核对），不得跳过。
- **FR-002**: 每条结论 MUST 标注所属维度（稳定性 / 正确性 / 线程安全 / 性能 / 安全边界）、严重级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件与影响面。
- **FR-003**: 每条结论 MUST 附带可复现方式（既有测试、可新增的回归用例设计或明确构造步骤）；仅凭阅读得出的判断 MUST 标注为"未验证假设"，不得混入已确认结论。
- **FR-004**: 线程安全结论 MUST 覆盖三类场景：共享可变状态（静态集合、`volatile` 字段、单例状态）、生命周期（执行器/虚拟线程/容器的创建与释放）、竞态（check-then-act、重复注册、关闭与在途任务）。
- **FR-005**: 上下文纪律结论 MUST 对每一处 `ThreadLocal`/MDC 使用给出"写入—传播—清理—异常路径"四段判定，并 MUST 指出未成对清理的位置。
- **FR-006**: 性能结论 MUST 给出可复算口径：单请求内存峰值与分配次数、单次调用复杂度与规模关系、队列/缓存/并发上限；不得只给"较慢/较快"的定性描述。
- **FR-007**: 稳定性结论 MUST 明确列出"失败时静默收敛"的位置（返回 `null`、返回入参、`catch` 后不记录），并 MUST 判定其是否违反宪法与 `AGENTS.md` 的"禁止静默降级"要求。
- **FR-008**: 安全边界结论 MUST 覆盖：默认密钥、类/方法白名单、鉴权放行策略、异常响应中的内部信息、敏感数据进入日志/响应/Cookie 的路径；每项 MUST 给出"默认是否安全"的判定。
- **FR-009**: 对外契约（`AGENTS.md`、宪法、公开注解/DTO/异常语义、配置键）MUST 与实现逐条对照；不一致处 MUST 单列为发现，MUST NOT 在审查阶段直接修改生产代码。
- **FR-010**: 审查报告 MUST 独立成文于特性目录，`spec.md` 只保留摘要与引用，两者在交付时 MUST 相互一致。
- **FR-011**: 每条发现 MUST 给出"能在旧代码上失败、在新代码上通过"的回归测试设计思路（输入、断言点、期望差异），供后续计划阶段直接使用。
- **FR-012**: 本次交付 MUST NOT 改变生产代码行为；若报告中提出修复建议，MUST 说明其对现有成功路径、默认行为与配置语义的影响。
- **FR-013**: 既有 518 个用例 MUST 全部执行并记录结果；新增审查验证用例 MUST 在无外部中间件环境下可运行或可优雅跳过，不得让流水线因环境缺失失败。
- **FR-014**: 审查 MUST 明确区分"缺陷"与"既定契约"；对已被测试锁定但语义可疑的行为 MUST 标注为契约并要求文档同步，不得计为缺陷。
- **FR-015**: 审查结论 MUST 包含风险排序依据（影响面 × 触发概率 × 可观测性），并 MUST 给出"是否建议作为基础依赖对外承诺"的明确结论。
- **FR-016**: 涉及公开注解、DTO、异常语义或配置键的修复建议 MUST 单独标注兼容性影响等级（无影响 / 行为变更 / 破坏性变更），供计划阶段决策。

### Key Entities *(include if data involved)*

- **审查发现（Finding）**：编号、严重级别、维度、代码位置、触发条件、影响面、证据、复现方式、回归测试设计、兼容性影响、修复建议。
- **上下文纪律矩阵**：上下文项（traceId、其他 MDC 键、请求上下文、安全上下文、租户/数据源上下文）× 写入点 × 传播路径（同步/异步/并行流）× 清理点 × 异常路径判定。
- **并发与生命周期矩阵**：组件（`@Async` 执行器、通用并发工具池、事件执行器、ForkJoin 包装器、静态单例、限流结构）× 创建时机 × 并发上限来源 × 停止路径 × 泄漏风险。
- **失败收敛清单**：位置 × 失败类型 × 当前收敛方式（`null` / 入参 / 空集合 / 忽略）× 是否有日志 × 是否可观测。
- **性能口径表**：路径（XSS 改写、请求体缓存、限流、并发工具、事件分发、反射/拷贝、树构建）× 分配或复杂度 × 上限 × 复算公式。
- **契约对照表**：文档声明（宪法 / `AGENTS.md`）× 实现位置 × 一致性判定 × 需要的文档改动。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查覆盖 100% 的 309 个生产源码文件与 65 个测试类，且 100% 的发现记录在独立审查报告中；新维护者可在 15 分钟内按报告复现任一结论。
- **SC-002**: 报告的发现条目 100% 具备"级别 + 维度 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归测试设计"；0 条仅有阅读判断而无复现路径的结论混入已确认项。
- **SC-003**: 上下文纪律矩阵 100% 覆盖"写入—传播—清理—异常路径"四段，且每一处 `ThreadLocal`/MDC 使用点均有判定；未成对清理的位置 100% 被列出。
- **SC-004**: 性能口径对不少于 6 条横切路径（XSS 改写、请求体缓存、限流、并发工具、事件分发、反射/拷贝）各给出可复算公式，复算结果与报告数值 100% 一致。
- **SC-005**: 失败收敛清单 100% 覆盖生产代码中的 `catch` 块与"返回 `null`/入参/空集合"的失败分支，且每项均给出"是否可观测"判定。
- **SC-006**: 安全边界 5 个方面（默认密钥、白名单、放行策略、异常信息、敏感数据流向）各给出唯一定性，且 100% 的"默认不安全"项被列为发现。
- **SC-007**: 既有 518 个用例执行率 100%、通过率 100%；新增审查验证用例在无外部中间件环境下 100% 通过或可跳过，不引入流水线失败。
- **SC-008**: 高危发现（F-01…F-08）100% 具备可构造的故障现场与对应的回归测试设计，计划阶段无需补充调研即可拆分为任务。
- **SC-009**: 报告给出唯一结论"当前是否建议作为基础依赖对外承诺"，并列出支撑该结论的全部高危项；该结论不依赖读者自行推断。

## Assumptions

- 目标运行形态为：JDK 21、Spring Boot 4.1.x、模块以 Starter/依赖形式被下游引入；下游可能运行在 Servlet 容器、虚拟线程、异步执行与多容器测试环境中。
- 本次为审查交付，不修改生产代码；发现分级与修复排期交由 `/speckit.plan` 决定，本规格只约束"结论质量与可验证性"。
- 不引入真实数据库、消息中间件或压测环境；性能结论以代码路径的可复算口径为主，可执行冒烟仅作旁证。
- 既有 518 个用例视为当前行为基线；其中被测试锁定的行为（如 `getTraceId()` 返回空串、`GXSQLFilter` 的过滤语义）按"既定契约"处理，不因审查被推翻，但 MUST 标注并要求文档同步。
- "上下文正确"的判定以宪法第 V 条与 `AGENTS.md`"关键行为"第 2、3 条为准，不引入外部安全合规标准。
- 配置键、注解属性、公开方法签名在本规格范围内保持不变；任何修复建议若触及契约，必须标明兼容性影响等级。
- 审查证据以静态分析与隔离测试为准，不承诺生产环境长稳压测结论。
