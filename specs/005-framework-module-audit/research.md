# Phase 0 Research: leaf-base-framework 审查方法与环境事实

**Feature**: `005-framework-module-audit` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

本文件只记录**会改变审查结论的决策与环境事实**，不复述规格中的发现清单。全部未知项已在此解决，无 `NEEDS CLARIFICATION` 残留。

---

## D1. 覆盖判定采用四档 + 抽样，而非"逐文件逐行"

**Decision**: 309 个文件按包分配判定强度：

| 包（文件数） | 判定强度 | 交付形式 |
| --- | --- | --- |
| util(33) / service(27) / event(15) / config(15) / web(14) / filter(6) / aspect(4) / wrapper(4) / handler(3) / listener(2) / manager(2) / api(3，不含 `api/dto` 子包) / controller(3) / validator(13) | 逐文件读实现，给"无缺陷 / 风险 / 缺陷" | 台账一行 + 发现编号（如有） |
| dto(83) / exception(23) / annotation(11) / constant(7) / code(4) / ddd(12) / properties(4) / deserializer(2) / lang(2) / factory(2) / dao(2) / model(2) / convert(3) / api/dto(8，`req`/`res` 的 DTO 与 `package-info`) | 契约核对（字段/签名/语义/序列化），抽样验证行为 | 台账判定为"仅契约核对"，附判定依据 |

**Rationale**: 规格 FR-001 要求 100% 覆盖，SC-001 要求"15 分钟内复现任一结论"。纯 DTO/常量/注解包逐行读的边际收益趋零，但其**契约属性**（字段类型、默认值、序列化行为）是下游破坏性变更的主要来源，必须以"契约核对"形式留痕，不能直接跳过。四档判定使台账每行都有明确取值域，避免"看过了"这种不可验收的表述。

**Alternatives considered**:

- 全部 309 文件逐行深审：成本约为本次的 2.5 倍，且对 `GXConditionEQ` 这类 12 行条件类无额外收益；仍会漏掉跨文件组合缺陷（真正的风险来源）。
- 只审高风险包（约 150 文件）：无法满足 FR-001 的 100% 覆盖与 SC-001 的可复核要求。
- 用静态分析工具（SpotBugs/ErrorProne 报告）替代人工审查：ErrorProne 已在构建期启用（宪法 VI），但只能发现编译期模式，无法判定"契约承诺与实现不一致""上下文清理缺失""静默降级"这类语义问题；工具结果作为旁证，不作为结论来源。

---

## D2. 审计用例采用"特征化测试"，断言当前行为

**Decision**: 8 个审计用例类全部断言**当前（有缺陷的）行为**，方法名直述现状，如 `asyncExecutorDoesNotPropagateTraceId`、`rateLimiterKeepsStaticEntriesForManyClientIds`。每类头部注释标注 `CURRENT BEHAVIOR - flip when fixed`。

**Rationale**: 规格 FR-012 禁止本次改生产代码，若用例断言"期望的正确行为"，则交付即红。特征化测试让审查结论**可被 CI 验证**（绿色即现状成立），并在修复阶段天然充当验收开关：断言必须翻转才允许合入。这与 `004-redisson-module-audit` 已确立的手法一致，团队无需重新理解。

**Alternatives considered**:

- 只写文档不写用例：违反 `AGENTS.md`"涉及线程并发/MDC/AOP/缓存/事件时必须补充冒烟测试"，且结论无法自动防回归。
- 写期望行为用例并 `@Disabled`：跳过型用例在流水线中不可见，容易长期腐烂；且 `@Disabled` 与"必须可执行"冲突。
- 写期望行为用例并让其失败：破坏 SC-007（既有与新增用例通过率 100%）。

---

## D3. 静态初始化期读取配置：从"缺陷"降级为"风险（配置时序）"

**Decision**: 静态字段初始化时调用 `GXCommonUtils.getEnvironmentValue(...)`（`filter/GXXssHttpServletRequestWrapper.java:26-30`、`event/center/SyncEventBusCenter.java:8`、`event/center/AsyncEventBusCenter.java:18`）**不判定为缺陷**，只登记为"配置时序风险"：一旦类在 `ApplicationContext` 就绪前被加载，名称与数值被固化为默认值，后续配置不再生效。

**Rationale**: 实测语义为"安全降级"——`GXCommonUtils.getEnvironmentValue(key, type)` 在 `Environment` 为空时返回 `getClassDefaultValue(clazzType)`（`GXCommonUtils.java:107-112`），带默认值重载直接返回 `defaultValue`（`155-159`），不抛异常。因此不会出现启动崩溃，但存在"配置静默不生效"，属宪法 IV"禁止静默降级"的**风险**而非崩溃缺陷。若不先核对这一点，报告会把风险夸大为"启动即崩"，导致计划阶段排错优先级。

**Alternatives considered**:

- 按"静态初始化读 Spring 环境必然 NPE"处理：与代码事实冲突（已有 null 分支）。
- 完全不计：漏掉"配置改了不生效"这类最难排查的问题。

---

## D4. 上下文传播的验证方式：轻量容器 + 直接执行器调用

**Decision**: 分三层验证，均不启动真实 Servlet 容器：

1. **执行器层**：`AnnotationConfigApplicationContext` 注册 `GXAsyncConfig` 与一个 `@Async` 测试 Bean，提交任务后读 `GXTraceIdContextUtils.getNullableTraceId()`，断言与提交线程不一致（F-01）。
2. **过滤器层**：`MockHttpServletRequest` + `MockHttpServletResponse` + `MockFilterChain`，链内写入自定义 MDC 键，链后断言该键仍残留（F-02）。
3. **包装器层**：直接实例化 `GXMdcWrapperForkJoinPool` / `GXMdcWrapperThreadPoolTaskExecutor`，分别走 `submit(ForkJoinTask)`、`invoke`、`submitListenable` 路径，断言 MDC 未传播（F-01/E-03）。

**Rationale**: `@SpringBootTest` 启全容器在本模块会引入 `GXFilterConfig`、`GXCORSConfig`、actuator 等无关装配，且启动耗时（实测 `GXWebAdviceMvcTest` 约 15 秒）会放大到 8 个类。`AnnotationConfigApplicationContext` 只需 2 个 Bean 即可复现 `@Async` 代理路径；`MockFilterChain` 足够覆盖 `finally` 语义。三层分别对应"谁丢了上下文"，报告可精确定位到层。

**Alternatives considered**:

- 全量 `@SpringBootTest(webEnvironment = RANDOM_PORT)`：真实但慢，且虚拟线程下端口/线程数不确定，断言易 flaky。
- 只读代码不验证：F-01 属最高危项，无执行证据无法满足 SC-008。
- 用 `ThreadPoolTaskExecutor` 替换 `@Async` 验证：会绕过 `GXAsyncConfig`，测不到真实装配（正是缺陷所在）。

---

## D5. 事件 BPP 的代理路径验证：`ApplicationContextRunner` + 接口代理

**Decision**: 用 `ApplicationContextRunner` 装载 `GuavaSyncEventBusBeanPostProcessor` + 一个被 `@Transactional`/自定义切面代理的订阅者 Bean（强制 JDK 动态代理，`proxyTargetClass=false`），断言订阅方法**未被注册**（发布事件后订阅方计数为 0），即 F-09。

**Rationale**: `GuavaSyncEventBusBeanPostProcessor` 以 `bean.getClass()` + `method.isAnnotationPresent(Subscribe.class)` 匹配（`event/processor/GuavaSyncEventBusBeanPostProcessor.java:57`、`94-108`）。JDK 代理类的方法不携带注解，这是可稳定复现的事实；`ApplicationContextRunner` 是 `spring-boot-test` 自带能力，零新增依赖。prototype 多实例漏注册（`62-73` 以 `beanName` 去重）用同一 Runner 加 `@Scope("prototype")` 即可覆盖。

**Alternatives considered**:

- Mockito mock 一个类并手动调用 `postProcessAfterInitialization`：可复现"方法无注解"，但无法证明 Spring 真实代理形态下的行为，证据等级低。
- `@SpringBootTest` 全容器：同样慢，且需要额外配置 `@EnableTransactionManagement` 才有代理，隐蔽依赖多。

---

## D6. 性能结论用"计数口径"，禁止绝对耗时断言

**Decision**: 6 条路径各定义计数公式（详见 `contracts/audit-verdict-contract.md`），审计用例只断言**可计数的事实**：

| 路径 | 可执行断言形式 |
| --- | --- |
| XSS 改写 | 包装器构造后 `request.getContentAsByteArray().length` 与原文长度关系；`getHeader("Content-Length")` 与 `getContentLength()` 不一致 |
| 请求体缓存 | 同一请求调用 `getHttpParam` N 次，断言底层 JSON 解析次数（以 `JSONUtil.toBean` 的可见副作用或属性读取次数计） |
| 限流 | N 个不同 `clientId` 打点后，反射读静态 Map 的 `size()`，断言等于 N（无上限事实） |
| 并发工具 | 队满时用单线程池 + 阻塞任务触发 `CallerRunsPolicy`，断言任务在提交线程执行（记录 `Thread.currentThread()`） |
| 事件分发 | 发布 1 个事件给 k 个订阅者，断言执行器提交次数 = k（Mock 执行器计数） |
| 反射/拷贝 | 连续 `BeanCopier` 获取 N 次，断言 `BeanCopier.create` 被调用次数（以不同类型对计）> 1（缓存未命中事实） |

固有复杂度（O(n²)、2×body）以**公式 + 代码位置**在报告中给出，不由用例断言。

**Rationale**: 本机为 JDK 25.0.4 运行、release 21 编译（见 D8），绝对耗时在同一机器上也可能有 2 倍以上波动；既有 `GXMdcThreadUtilsTest` 的 `durationMs < 2000` 类断言在 CI 上必然 flaky。计数型断言与机器无关、可复算，满足 SC-004"复算结果 100% 一致"。

**Alternatives considered**:

- 引入 JMH：需新增依赖（违反约束），且基准测试不进 `mvn test` 默认流水线，无法满足"可执行验证"。
- 用 JFR/`ThreadMXBean` 统计分配：能测到分配量，但阈值仍需人为设定，同样 flaky。
- 只做纯文字推算：无法满足 SC-004 的"可执行冒烟作为旁证"。

---

## D7. 并发与时间的确定性控制：`CountDownLatch` + 有界等待

**Decision**: 所有并发用例使用 `CountDownLatch`/`CyclicBarrier` 控制时序，等待一律 `await(5, TimeUnit.SECONDS)` / `future.get(5, TimeUnit.SECONDS)`；禁止 `Thread.sleep` 作为同步手段（仅允许 `sleep` 制造"过期"语义，如限流窗口）。

**Rationale**: 规格涉及"线程复用""容器关闭与在途任务""并发 clear 与 get"等竞态。固定 sleep 既慢又不稳；闩锁 + 有界等待失败时直接给出可读的超时信息。5 秒上限与既有 `GXAsyncConfigTest`、`GXConcurrentToolsUtilsTest` 保持一致。

**Alternatives considered**:

- Awaitility：表达力更好，但需新增依赖，且对"必须复现的时序"帮助有限。
- 无界 `future.get()`：CI 挂死风险高，违反"不得让流水线失败"的精神。

---

## D8. 环境事实核对：构建基线与运行版本

**Decision**: 以实测结果为准入基线，写入报告"环境与基线"章节：

| 项 | 实测值 | 用途 |
| --- | --- | --- |
| 构建命令 | `mvn -o -q -pl leaf-base-framework test` | 离线可执行，`leaf-base-framework:4.3.0-SNAPSHOT` 已安装 |
| 测试结果 | `Tests run: 518, Failures: 0, Errors: 0, Skipped: 0`（65 个报告文件） | SC-007 对照基线 |
| `@Test` 注解数 | 519 | 与执行数差 1，差额需在报告中说明（同名/嵌套/条件执行），不得含糊 |
| 运行 JDK | 25.0.4（`GXWebAdviceMvcTest` 启动日志） | 与 `release=21` 的差异登记为风险 |
| Spring Boot | 4.1.1 | 依赖行为核对基准 |

**Rationale**: SC-007 要求"518/519 全部执行并记录结果"。先跑一次基线，才能区分"审查引入的失败"与"既有失败"；同时 519 与 518 的差额若不解释，会被后续读者当作漏测。JDK 25 运行 + 21 编译意味着虚拟线程调度细节可能与目标环境不同，这是本计划明确接受的偏差。

**Alternatives considered**:

- 不跑基线，只静态审查：无法证明 SC-007，且后续任务无法判断回归来源。
- 切到 JDK 21 再验证：需要额外安装运行环境（本机仅 25.0.4），收益低于成本；改为在报告中显式登记偏差。

---

## D9. `GXSQLFilter` 与 `GXHTMLFilter` 的判定边界

**Decision**:

- `filter/GXSQLFilter.java` **不是** Servlet Filter（`GXFilterConfig` 未注册），只是静态工具；其"词边界黑名单"既误杀正常文本（`A and B`、`table tennis`）也漏掉 `sleep(`、`benchmark(`、`into outfile`、`order by`。判定为**既定契约 + 安全边界不足**：不主张"应改成过滤器"，只主张"调用方不应把它当作 SQL 注入防护"。
- `filter/GXHTMLFilter.java` 明示非线程安全（内部 `vTagCounts` 状态），由 `GXXssHttpServletRequestWrapper` 的 `ThreadLocal` 兜住；`&`/`<` 的实体改写属原库行为。判定为**缺陷（数据污染）+ 既定契约（实体表）**：报告要求区分"过滤器会改写"（事实）与"改写成什么"（现状），修复建议不动实体映射表。

**Rationale**: 审查报告最容易被误用的是"把设计选择当成缺陷"。这两个类的现有行为已被 `GXSQLFilterTest`（4 例）、`GXXssHttpServletRequestWrapperTest`（参数/头/JSON 体/上限）部分锁定，直接要求改造会与既有用例冲突，必须按"缺陷 + 契约"双标签处理。

**Alternatives considered**:

- 统一按缺陷处理：会导致计划阶段排出"重写过滤器"的大任务，超出审查结论的支撑强度。
- 统一按契约处理：会掩盖"50MB 全量读 + 2 倍内存 + 长度头不一致"的真实稳定性风险。

---

## D10. 缓存相关结论的口径：能力缺失而非功能失效

**Decision**: 全模块无 `@EnableCaching`、无 `KeyGenerator` Bean（仅空接口 `service/GXCacheKeyGeneratorService.java`），判定为"**模块不提供缓存 key 隔离能力**"，而非"缓存不生效"。同时明确：`@Cacheable` 是否生效由下游是否启用缓存决定，本模块只负责 `CacheManager`/`Cache` 装配。

**Rationale**: 若断言"缓存不生效"，与 `GXCaffeineCacheConfigTest` 已证明的 `CacheManager` 装配成功相矛盾。真实风险是"下游启用缓存后，同一 `cacheName` 下不同方法、相同参数会共用 key"，这是 `SimpleKeyGenerator` 的既定行为；把它定位为"能力缺失"，修复建议才能落到"提供 KeyGenerator 或文档明确要求显式 key"。

**Alternatives considered**:

- 判定为缺陷：会被 `GXCaffeineCacheConfigTest` 的既有断言反驳，降低报告可信度。
- 不提：遗漏下游最容易踩的缓存串数据问题。

---

## D11. 报告与规格的编号对齐规则

**Decision**: `review-report.md` 沿用 `E-01…E-22` / `F-01…F-21` 编号；审查中若推翻某条结论，保留原编号并在条目内标注 `REVISED`（附修订理由），不得删除或重排；新增结论从 `E-23` / `F-22` 继续追加。

**Rationale**: 规格 SC-008 把"高危 F-01…F-08"写成了固定集合，`tasks.md` 会按编号引用。重排编号会让规格、报告、任务三者失联。`004` 的实践已验证"保留编号 + REVISED 标注"可读性最好（该轮修正了 F-05/F-06）。

**Alternatives considered**:

- 交付时重新编号：读者无法对照规格，且历史讨论失效。
- 双编号（旧/新）：维护成本高，易出现断链。

---

## D12. 安全边界结论的证据等级要求

**Decision**: 安全类发现（默认密钥、类白名单、放行策略、异常信息、敏感数据流向）必须达到"可执行验证"等级才可标 `缺陷`；仅靠阅读得出的标 `风险` 并注明"未执行验证"。默认密钥一项额外要求给出"未配置时是否可用"的运行证据（构造函数直接调用即可，无需容器）。

**Rationale**: 安全结论误报代价最高（会触发全量密钥轮换）。`GXSensitiveFieldDeEncryptServiceImpl` 的降级行为可直接构造（传非法密文 → 返回原串），证据成本极低；而"动态调用可被利用"需要在报告写明"需上游传入可控类名"的前提，不能直接等同于远程代码执行。

**Alternatives considered**:

- 安全项一律按最高级处理：会造成"狼来了"效应，稀释真正的高危项。
- 安全项只做代码阅读：无法支撑 FR-008 的"默认是否安全"判定。
