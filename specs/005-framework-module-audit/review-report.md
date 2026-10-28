# leaf-base-framework 审查报告

## 1. 标题与元信息

**模块**: `leaf-base-framework`（26 个包 / 309 个生产源码文件）
**审查日期**: 2026-10-22
**审查基线**: 工作区快照（未提交变更），`leaf-base-framework` 4.3.0-SNAPSHOT
**审查范围**: `src/main/java/cn/maple/core/framework/**`、`src/main/resources/META-INF/**`、`src/test/java` 下 65 个测试类
**判定口径**: [contracts/audit-verdict-contract.md](./contracts/audit-verdict-contract.md)
**覆盖台账**: [coverage-ledger.md](./coverage-ledger.md)

---

## 2. 环境与基线

| 项 | 实测值 | 说明 |
| --- | --- | --- |
| 构建命令 | `mvn -o -q -pl leaf-base-framework test` | 离线可执行，父 POM 与 `leaf-base-framework` 已安装到本地仓库 |
| 测试结果 | `files=65 tests=518 failures=0 errors=0 skipped=0` | 既有用例全绿，0 跳过 |
| `@Test` 计数 | 既有 519（2026-10-28 复测） | 以 `@Test\b` 逐方法统计；交付时既有 518（此前"519"是按 `@Test` 子串按行统计，多计了 `@TestPropertySource` 一行（`src/test/java/.../GXBotNotificationExceptionServiceSpringBootTest.java:20`））。+1 来自后续提交 `ab91dbfe` 为 `GXWebMvcConfigurerTest` 新增的 1 个用例（见 §12） |
| 运行 JDK | 25.0.4 | 编译目标 `release=21`；虚拟线程调度细节与目标环境存在差异，登记为风险 |
| Spring Boot | 4.1.1 / Spring Framework 7.x | `ThreadPoolTaskExecutor.submitListenable` 在 Spring 7 已移除（E-23），影响 F-01 的证据范围 |
| 审计用例 | 8 个类 / 34 个用例，`mvn -o -pl leaf-base-framework test -Dtest=GX*AuditTest` 全绿 | 特征化用例，断言当前行为 |
| 交付验收 | `mvn -o -pl leaf-base-framework test` → `files=73 tests=553 failures=0 errors=0 skipped=0` | 2026-10-28 复测：519 既有 + 34 审计；交付时为 550（518 + 32），差额来源见 §12。本特性交付未改生产代码（`src/main/**`、`pom.xml`），后续特性的独立改动见 §12 |

**基线用途**：区分"既有失败"与"审查引入的失败"；同时证明本报告的所有结论都不依赖修改生产代码。

---

## 3. 结论摘要

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 稳定性 | 异步执行、事件订阅、缓存装配三条主链路各有一个"静默失效"入口；失败路径普遍以 `return null` / `return 入参` / `catch ignored` 收敛，运维无法观测 | 存在 8 条高危缺陷 |
| 正确性 | "事件发布即快照"对自定义对象不成立；Map 的复合键被替换；默认缓存 key 会跨方法共享 | 存在契约级不一致 |
| 线程安全 | 请求结束只恢复 traceId 一个键；三个 MDC 包装器生产代码零装配；限流状态以不可信入参为键且无上限；上传服务用 `volatile` 实例字段跨请求传值 | 存在跨请求污染路径 |
| 性能 | XSS 链路在构造期物化整个请求体（上限 50MB）；限流器每次调用做 O(n) 遍历；并发工具在日志实参位无条件格式化；BeanCopier 缓存被弱键+新建字符串键抵消 | 存在可定位的结构性开销 |

**一句话结论**：该模块目前**不适合**以"基础依赖"的强度对外承诺；必须先收敛 8 条高危缺陷，其余 13 条按批次排期。

### 准入门槛结论

- **审查范围**: leaf-base-framework（309 文件 / 26 包），基线 4.3.0-SNAPSHOT 工作区快照
- **高危缺陷数**: 8（F-01, F-02, F-03, F-04, F-05, F-06, F-07, F-08）
- **blockers**: F-01, F-02, F-03, F-04, F-05, F-06, F-07, F-08
- **结论**: NOT_SUITABLE_AS_BASELINE
- **理由**: F-02 会造成跨请求身份串扰，F-03/F-04 让口令与内部路径可被外部观测，F-01 让所有异步链路的可观测性失效；这四条任一存在都不满足"基础模块"的稳定性与安全底线，另四条（F-05/F-06/F-07/F-08）为契约级不一致，需与文档同步后重新评估。

---

## 4. 证据清单

| 编号 | 等级 | 位置 / 来源 | 支撑的事实 |
| --- | --- | --- | --- |
| `E-01` | SOURCE_FACT | `config/GXAsyncConfig.java:24-29` | `getAsyncExecutor()` 每次调用新建 `SimpleAsyncTaskExecutor` 并启用虚拟线程，无 `TaskDecorator`；全仓库 `new GXMdcWrapper*` 仅出现在测试中 |
| `E-02` | SOURCE_FACT, EXECUTABLE | `filter/GXBaseRequestLoggingFilter.java:26-43`；`util/GXTraceIdContextUtils.java:43-49` | 请求结束只恢复 traceId 单键；`GXContextPropagationAuditTest#customMdcKeyLeaksAfterRequestFilterChain` 复现自定义键残留 |
| `E-03` | SOURCE_FACT, EXECUTABLE | `wrapper/mdc/GXMdcWrapperForkJoinPool.java:45-53` | `submit(ForkJoinTask)` / `invoke(ForkJoinTask)` 未包装；`#forkJoinSubmitOfForkJoinTaskIsNotWrapped` 复现 |
| `E-04` | SOURCE_FACT, EXECUTABLE | `util/GXCurrentRequestContextUtils.java:100`、`630-649`、`417-420` | 静态无上限 `requestTimestamps`；`size()` 为 O(n)；`isRPC()` 以"无请求上下文"等价于 RPC；`#rateLimiterKeepsOneEntryPerDistinctClientId` 复现 1000 个键 |
| `E-05` | SOURCE_FACT, EXECUTABLE | `util/GXConcurrentToolsUtils.java:39`、`83-93`、`163-166` | `static final` 池 + `CallerRunsPolicy`；`getThreadPoolStatus()` 作为日志实参无条件求值；`#sharedPoolUsesCallerRunsPolicy` 复现 |
| `E-06` | SOURCE_FACT, EXECUTABLE | `event/GXBaseEvent.java:95-153`、`129`、`211-223` | 快照兜底 `return source`；Map 的 key 也被快照；深度 64 / 节点 10 万硬上限；`#customPayloadIsNotSnapshotted`、`#mutableArrayKeyIsReplacedBySnapshotCopy` 复现 |
| `E-07` | SOURCE_FACT | `event/processor/GuavaSyncEventBusBeanPostProcessor.java:57`、`62-73`、`94-108` | 以 `bean.getClass()` + 方法注解匹配；以 `beanName` 去重 |
| `E-08` | SOURCE_FACT | `event/config/GuavaEventBusConfig.java:77-89`；`event/center/AsyncEventBusCenter.java:30-39` | 虚拟线程执行器 `concurrencyLimit=CPU*2` 且达上限阻塞发布线程；另有一套 daemon 执行器（队列 `CPU*2048`、`CallerRunsPolicy`、不关闭） |
| `E-09` | SOURCE_FACT, EXECUTABLE | `util/GXEventPublisherUtils.java:157-159`、`22` | 注册缓存以 `identityHashCode` 为键；`#registerCacheKeyUsesIdentityHashCode` 复现 |
| `E-10` | SOURCE_FACT, EXECUTABLE | `util/GXCaffeineCacheUtils.java:141-144`；`config/cache/GXCaffeineCacheConfig.java:48`、`126-131` | 工具默认缓存无 TTL；`setCacheNames(empty)` 关闭动态创建；全模块无 `@EnableCaching` 与 `KeyGenerator` Bean；`GXCacheIsolationAuditTest` 复现 |
| `E-11` | SOURCE_FACT | `util/GXCacheKeysUtils.java:63-66`、`84-89` | 未知配置名落到 `maple:default:<key>`；空白 key 统一为 `"key"` |
| `E-12` | SOURCE_FACT, EXECUTABLE | `filter/GXXssHttpServletRequestWrapper.java:26-30`、`54-81`、`127-145`、`276-290` | 构造期读全量（上限 50MB）、逐字符串值改写、`getContentLength*` 与 `Content-Length` 头不一致；`GXWebBoundaryAuditTest#contentLengthHeaderStaysStaleAfterRewrite` 复现 |
| `E-13` | SOURCE_FACT, EXECUTABLE | `web/advice/GXRequestBodyAdvice.java:80-98`；`web/advice/GXResponseBodyAdvice.java:46-74` | 完整请求体 JSON 写入请求属性；`cors.allow.credentials=true` 且配置 `cors.cookie.virusId` 时每响应下发 `UserData` Cookie，默认密钥 `CF3417E3CF6B0F28`；`#requestBodyIsStoredWithSensitiveFields` 复现 |
| `E-14` | SOURCE_FACT, EXECUTABLE | `handler/GXExceptionHandler.java:118-125`、`127-136`、`210-224`、`66-78` | 兜底与容器异常的 `cause.message` 进入响应；参数格式错误以 `ok(500)` 返回；`#multipartExceptionEchoesCauseMessage` 复现 |
| `E-15` | SOURCE_FACT | `service/impl/GXFileUploadServiceImpl.java:31`、`45`、`61`、`79` | `volatile String fileStoragePath` 实例字段在请求路径读写 |
| `E-16` | SOURCE_FACT, EXECUTABLE | `service/impl/GXSensitiveFieldDeEncryptServiceImpl.java:22-29`、`37-44`、`52-57`；`GXSensitiveDataDecryptServiceImpl.java:45-49`；`GXSensitiveDataEncryptServiceImpl.java:41-42` | 加解密异常时返回入参；默认密钥硬编码；两个批量服务 `catch (Exception ignored)`；`#decryptReturnsInputWhenCiphertextIsInvalid`、`#encryptUsesHardcodedDefaultKeyDeterministically` 复现 |
| `E-17` | SOURCE_FACT, EXECUTABLE | `util/cglib/GXBeanCopierCache.java:11`、`28-34` | `WeakKeyConcurrentMap` + 每次新建 `String` 键；`GXPerformanceBudgetAuditTest#beanCopierCacheKeyIsNewInstanceOnEveryLookup` 复现 |
| `E-18` | SOURCE_FACT, EXECUTABLE | `service/impl/GXDynamicCallMethodServiceImpl.java:66-85`；对照 `GXParseDynamicCallParamServiceImpl.java:250-258` | `Class.forName` 加载调用方给定的任意类名；缓存满 1000 整体 `clear`；同模块解析侧存在 `cn.maple.` 前缀白名单；`#arbitraryClassCanBeLoadedByName` 复现 |
| `E-19` | SOURCE_FACT | `util/GXSpELToolUtils.java:85`、`107`、`128`、`263`、`361`、`175` | 使用 `StandardEvaluationContext`；部分入口把 `methodName` 拼进表达式文本，安全性依赖存在性预校验 |
| `E-20` | SOURCE_FACT | `web/config/GXWebMvcConfigurer.java:40-47`；`web/config/GXHandlerMethodArgumentResolverConfigurer.java:23-33` | 拦截器按 Bean 名排序、未设 include/exclude；以 `assert` 做生产判空、解析器插入内建解析器之前 |
| `E-21` | SOURCE_FACT | `util/GXCommonUtils.java:452-463`、`476-512`、`626-676` | 反射缓存超 4096 整体清空；重载以打分近似匹配；`buildTree` 逐层全量过滤并逐元素反射调用 |
| `E-22` | SOURCE_FACT | `AGENTS.md`"关键行为"2/3 条；`.specify/memory/constitution.md` §V | 文档要求"`ThreadLocal`/MDC/TraceId 必须有清理、恢复与异常传播路径""`ThreadLocal` 成对 push/poll"，实现未满足 |
| `E-23` | DEPENDENCY_FACT | Spring Framework 7（`AsyncTaskExecutor`） | `submitListenable` 已移除，`GXMdcWrapperThreadPoolTaskExecutor` 不再存在该覆写缺口；F-01 的证据收窄为"未装配 + 无 `TaskDecorator`"与 ForkJoin 路径 |
| `E-24` | EXECUTABLE | `GXHTMLFilter.filter` 实测 | `a&b<c` → `a&amp;b`（尾段被丢弃）；`5 < 6 and 7 > 6` → `5  6`；`AT&T` → `AT&amp;T`；`<b>bold</b>` 原样保留 |
| `E-25` | SOURCE_FACT | `filter/GXXssHttpServletRequestWrapper.java:32`；`service/impl/GXBaseCacheLockServiceImpl.java:26`、`:149`；`api/GXBaseServeApiImpl.java:35`、`:284-290`、`:337-341` | 三处 `ThreadLocal`：`HTML_FILTER` 无 `remove` 调用；`THREAD_LOCKS` 仅在 `releaseAllLocks()` 内移除；绑定类仅在 `runInThreadLocalContext` 的 `finally` 清理 |

---

## 5. 发现清单

### F-01 [HIGH] 异步执行不传播上下文，MDC 包装器从未装配

- **维度**: STABILITY, THREAD_SAFETY
- **判定**: DEFECT
- **位置**: `config/GXAsyncConfig.java:24-29`；`wrapper/mdc/GXMdcWrapperForkJoinPool.java:45-53`；`wrapper/mdc/GXMdcWrapperThreadPoolTaskExecutor.java:13-41`
- **触发条件**: 任意 `@Async` 方法；任意 `ForkJoinPool` 的 `submit(ForkJoinTask)` / `invoke(ForkJoinTask)`；并行流
- **影响**: 异步链路 traceId 丢失，跨线程日志无法串联；一旦下游把包装器接入，ForkJoin 路径仍会丢
- **证据**: E-01（SOURCE_FACT + EXECUTABLE）、E-03（SOURCE_FACT + EXECUTABLE）
- **复现**: `mvn -o -pl leaf-base-framework test -Dtest=GXContextPropagationAuditTest` → `asyncExecutorDoesNotPropagateTraceId`、`forkJoinSubmitOfForkJoinTaskIsNotWrapped` 均断言"捕获到 null"
- **回归测试设计**: 输入 = 提交线程写入 traceId 后执行异步任务；断言点 = 任务内 traceId；修复后应由 `null` 变为与提交线程一致
- **兼容性影响**: NONE（只增加传播，不改公开签名）
- **修复方向**: 为 `@Async` 提供带 `TaskDecorator` 的执行器并注册为 Bean；补齐 ForkJoin 的两个重载包装（需独立审批）
- **状态**: DELIVERED

### F-02 [HIGH] MDC 清理不完整，线程复用导致跨请求残留

- **维度**: THREAD_SAFETY, STABILITY
- **判定**: DEFECT
- **位置**: `filter/GXBaseRequestLoggingFilter.java:26-43`；`util/GXTraceIdContextUtils.java:43-49`；`web/interceptor/GXTraceIdInterceptor.java:14-22`
- **触发条件**: 业务代码在请求线程写入任意 MDC 键；容器线程复用
- **影响**: 下一个请求（可能是另一个用户）可读到上一请求的 `userId` 等键，日志归属与鉴权判定都可能被污染
- **证据**: E-02（SOURCE_FACT + EXECUTABLE）
- **复现**: `GXContextPropagationAuditTest#customMdcKeyLeaksAfterRequestFilterChain` → 过滤链结束后 `MDC.get("audit-user-id")` 仍为 `u-42`
- **回归测试设计**: 输入 = 链内 `MDC.put(customKey)`；断言点 = 链结束后该键为 `null`；修复后断言翻转
- **兼容性影响**: BEHAVIOR_CHANGE（依赖残留 MDC 的下游会失效，属预期修正）
- **修复方向**: 改为请求前后整份 MDC 快照保存/恢复（`MDC.clear()` + `setContextMap`）；`GXTraceIdInterceptor.afterCompletion` 补清理（需独立审批）
- **状态**: DELIVERED

### F-03 [HIGH] 加解密静默降级且默认密钥硬编码

- **维度**: SECURITY_BOUNDARY, CORRECTNESS
- **判定**: DEFECT
- **位置**: `service/impl/GXSensitiveFieldDeEncryptServiceImpl.java:22-29`、`37-44`、`52-57`；`service/impl/GXSensitiveDataDecryptServiceImpl.java:45-49`；`service/impl/GXSensitiveDataEncryptServiceImpl.java:41-42`
- **触发条件**: 密文非法/密钥不匹配/字段值非密文；未配置 `common.sensitive.data.key`
- **影响**: 解密失败把密文当明文返回，加密失败把明文当成功返回，全程无日志；默认密钥随源码分发，任何拿到源码的环境都能解开
- **证据**: E-16（SOURCE_FACT + EXECUTABLE）
- **复现**: `GXSecurityBoundaryAuditTest#decryptReturnsInputWhenCiphertextIsInvalid`（返回入参）、`#encryptUsesHardcodedDefaultKeyDeterministically`（同一明文两次密文相同且非明文）
- **回归测试设计**: 输入 = 非法密文 + 未配置密钥；断言点 = 抛异常或 `warn` 级日志 + 不返回原文；修复后应由"静默返回"变为"显式失败"
- **兼容性影响**: BREAKING（错误输入由静默成功变显式失败，需迁移说明）
- **修复方向**: 失败抛 `GXBusinessException` 并记录 `warn/error`；默认密钥改为启动期必填校验；AES 增加 IV 与完整性校验（需独立审批 + 迁移方案）
- **状态**: DELIVERED

### F-04 [HIGH] 敏感数据进入可观测与响应链路

- **维度**: SECURITY_BOUNDARY
- **判定**: DEFECT
- **位置**: `web/advice/GXRequestBodyAdvice.java:80-98`；`web/advice/GXResponseBodyAdvice.java:46-74`
- **触发条件**: 任意 `@RequestBody` 请求；`cors.allow.credentials=true` 且配置 `cors.cookie.virusId`
- **影响**: 含口令的完整 JSON 被写入请求属性，任何日志/审计组件打印属性即泄露；响应 Cookie 使用硬编码默认密钥，可离线伪造
- **证据**: E-13（SOURCE_FACT + EXECUTABLE）
- **复现**: `GXSecurityBoundaryAuditTest#requestBodyIsStoredWithSensitiveFields` → 属性值包含 `s3cr3t-value`
- **回归测试设计**: 输入 = 含 `password` 的请求体；断言点 = 属性中不出现该字段（或整体不落属性）；修复后断言翻转
- **兼容性影响**: BEHAVIOR_CHANGE（可能有下游依赖该属性做日志/审计）
- **修复方向**: 落属性前按 `@GXSensitiveField` 脱敏或改为开关默认关闭；Cookie 密钥缺失时拒绝启动（需独立审批）
- **状态**: DELIVERED

### F-05 [HIGH] XSS 改写在正常业务上造成数据丢失与内存放大

- **维度**: CORRECTNESS, STABILITY, PERFORMANCE
- **判定**: DEFECT
- **位置**: `filter/GXXssHttpServletRequestWrapper.java:26-30`、`54-81`、`127-145`、`276-290`；`filter/GXHTMLFilter.java:210-217`
- **触发条件**: JSON 请求体中出现 `<`、`&`、比较符号、未闭合标签
- **影响**: `5 < 6 and 7 > 6` 被改写为 `5  6`（文本被吞），`a&b<c` 变成 `a&amp;b`（尾段丢失）；写入数据库的是被污染的文本；构造期一次性读全量（上限 50MB），峰值内存约 2×body×并发；`Content-Length` 头仍是旧值，直读头的下游会截断
- **证据**: E-12（SOURCE_FACT + EXECUTABLE）、E-24（EXECUTABLE）
- **复现**: `GXWebBoundaryAuditTest#jsonBodyValuesAreHtmlRewritten`、`#contentLengthHeaderStaysStaleAfterRewrite`（wrapper=18，header=16）
- **回归测试设计**: 输入 = `{"note":"5 < 6 and 7 > 6"}`；断言点 = 读出内容与原始一致；修复后应不再丢字符，`getContentLength()` 与头部一致
- **兼容性影响**: BEHAVIOR_CHANGE（下游若依赖改写后的值需评估）
- **修复方向**: 默认关闭正文改写或改为"输出编码"；超过阈值直接拒绝而非物化；同步修正长度头（需独立审批）
- **状态**: DELIVERED

### F-06 [HIGH] 动态方法调用无类白名单

- **维度**: SECURITY_BOUNDARY, CORRECTNESS
- **判定**: DEFECT
- **位置**: `service/impl/GXDynamicCallMethodServiceImpl.java:66-85`；对照 `service/impl/GXParseDynamicCallParamServiceImpl.java:250-258`
- **触发条件**: 调用方（或经解析侧拼装后）传入任意 `serviceClassName`
- **影响**: 可加载并反射调用模块外任意类；同模块另一条链路已有 `cn.maple.` 前缀白名单，两条入口安全口径不一致；缓存满即整体清空造成抖动
- **证据**: E-18（SOURCE_FACT + EXECUTABLE）
- **复现**: `GXSecurityBoundaryAuditTest#arbitraryClassCanBeLoadedByName` → `java.lang.ProcessBuilder` 被成功加载
- **回归测试设计**: 输入 = `java.lang.Runtime`；断言点 = 抛 `GXBusinessException` 且 `CLASS_CACHE` 不增长；修复后应拒绝
- **兼容性影响**: BREAKING（依赖跨包调用的下游会失败，需白名单配置）
- **修复方向**: 与解析侧共用同一前缀白名单；缓存改 LRU/容量上限（需独立审批）
- **状态**: DELIVERED

### F-07 [HIGH] 全局异常处理回显内部信息且状态码语义错误

- **维度**: SECURITY_BOUNDARY, CORRECTNESS
- **判定**: DEFECT
- **位置**: `handler/GXExceptionHandler.java:118-125`、`127-136`、`210-224`、`66-78`
- **触发条件**: 兜底异常、`MultipartException`、参数类型错误
- **影响**: `cause.getMessage()`（可能含临时目录绝对路径、SQL 片段）直接进入响应 `msg`/`dict`；参数格式错误以 `ok(500)` 返回，客户端无法区分"成功但码是 500"与真实失败
- **证据**: E-14（SOURCE_FACT + EXECUTABLE）
- **复现**: `GXWebBoundaryAuditTest#multipartExceptionEchoesCauseMessage` → 响应的 `msg` 等于构造的 `/srv/internal/upload-dir is not writable`
- **回归测试设计**: 输入 = 带内部路径的 `MultipartException`；断言点 = 响应 `msg` 为固定文案且不含路径；修复后断言翻转
- **兼容性影响**: BEHAVIOR_CHANGE（前端若依赖原 message 需同步）
- **修复方向**: 对外只回固定文案，细节进日志与异常通知；`ok(500)` 改为 `error`（需独立审批）
- **状态**: DELIVERED

### F-08 [HIGH] 事件"发布即快照"契约对自定义对象不成立

- **维度**: CORRECTNESS
- **判定**: DEFECT
- **位置**: `event/GXBaseEvent.java:95-153`、`129`、`211-223`
- **触发条件**: 事件载荷含自定义可变对象；载荷 Map 使用数组/集合等身份比较对象作 key；载荷超深或超大
- **影响**: 发布后修改原对象，订阅方读到修改后的值（并发下顺序不确定）；原 key 在新 Map 中查不到；超限直接抛异常打断业务发布
- **证据**: E-06（SOURCE_FACT + EXECUTABLE）
- **复现**: `GXEventContractAuditTest#customPayloadIsNotSnapshotted`（读到 `after`）、`#mutableArrayKeyIsReplacedBySnapshotCopy`（`containsKey` 为 false）
- **回归测试设计**: 输入 = 可变 DTO + 发布后修改；断言点 = 订阅方读到发布时值；修复后应深拷贝或改为不可变载荷
- **兼容性影响**: BEHAVIOR_CHANGE（依赖"共享引用"的下游会受影响）
- **修复方向**: 显式要求载荷不可变并做防御性校验，或对自定义对象提供可插拔快照；Map key 不参与快照（需独立审批）
- **状态**: DELIVERED

### F-09 [MEDIUM] 事件订阅在代理 Bean 与 prototype 实例上漏注册

- **维度**: CORRECTNESS, STABILITY
- **判定**: RISK
- **位置**: `event/processor/GuavaSyncEventBusBeanPostProcessor.java:57`、`62-73`、`94-108`；`event/processor/GuavaASyncEventBusBeanPostProcessor.java:26-108`
- **触发条件**: 订阅者被 JDK 动态代理包装；prototype 作用域多次获取同一 Bean
- **影响**: 代理场景"零订阅"静默通过（日志只显示处理完成）；prototype 只注册第一个实例，后续实例的事件被漏消费
- **证据**: E-07（SOURCE_FACT）
- **复现**: `GXEventContractAuditTest#jdkProxySubscriberIsNotRegistered`（JDK 代理 → `isBeanRegistered` 为 false）、`#sameBeanNameSecondInstanceIsSkipped`（同 `beanName` 两实例 → 注册数为 1）
- **回归测试设计**: 输入 = 代理 Bean / 同 beanName 两个实例；断言点 = 注册数 = 1（修复后 = 2 或显式告警）
- **兼容性影响**: NONE（修复为补齐注册能力）
- **修复方向**: 沿接口/目标类解析注解；同 `beanName` 多实例时按实例去重并告警（需独立审批）
- **状态**: DELIVERED

### F-10 [MEDIUM] 事件执行模型不确定：达上限阻塞发布线程 + 两套执行器

- **维度**: STABILITY, PERFORMANCE
- **判定**: RISK
- **位置**: `event/config/GuavaEventBusConfig.java:77-89`；`event/center/AsyncEventBusCenter.java:30-39`；`util/GXEventPublisherUtils.java:113-121`
- **触发条件**: 异步事件并发达到 `CPU*2`；Spring 上下文缺失导致回退到静态中心；订阅者内再次发布事件
- **影响**: 达上限时发布线程被阻塞（订阅者内再发布会自锁/长尾）；两套线程模型（虚拟线程 vs daemon 池）行为不一致；静态中心永不关闭
- **证据**: E-08（SOURCE_FACT + EXECUTABLE）
- **复现**: `GXEventExecutionAuditTest#staticAsyncCenterOwnsUnmanagedExecutorWithCallerRunsPolicy`、`#asyncDispatchSubmitsOncePerSubscriber`
- **回归测试设计**: 输入 = 并发上限 + 订阅者内再发布；断言点 = 发布线程未被阻塞（超时即失败）；修复后应改为拒绝或排队并计数
- **兼容性影响**: NONE
- **修复方向**: 统一为单套执行器；达上限时拒绝并计数，不阻塞调用方（需独立审批）
- **状态**: DELIVERED

### F-11 [MEDIUM] 订阅注册缓存以 identityHash 为键且无上限、无调用方注销

- **维度**: CORRECTNESS, STABILITY
- **判定**: RISK
- **位置**: `util/GXEventPublisherUtils.java:22`、`123-139`、`157-159`
- **触发条件**: 短生命周期监听器大量发布；`identityHashCode` 碰撞/对象回收后地址复用
- **影响**: 碰撞时误判"已注册"而跳过 `register`，事件静默丢失；缓存只增不减
- **证据**: E-09（SOURCE_FACT + EXECUTABLE）
- **复现**: `GXEventContractAuditTest#registerCacheKeyUsesIdentityHashCode`
- **回归测试设计**: 输入 = 两个内容相等但不同实例；断言点 = 键不同且都完成注册；修复后应改用 `WeakHashMap`/按实例键
- **兼容性影响**: NONE
- **修复方向**: 以实例为键的弱引用结构 + 显式注销路径（需独立审批）
- **状态**: DELIVERED

### F-12 [MEDIUM] 缓存装配缺少 key 隔离与命名空间保护

- **维度**: CORRECTNESS, PERFORMANCE
- **判定**: RISK
- **位置**: `util/GXCaffeineCacheUtils.java:68-92`、`141-144`、`173-176`；`config/cache/GXCaffeineCacheConfig.java:48`、`126-131`；`util/GXCacheKeysUtils.java:63-66`、`84-89`；`config/support/GXCachingConfigurerSupport.java:26-34`
- **触发条件**: 下游启用 `@EnableCaching` 后使用同一 `cacheName`；使用未声明的缓存名；未知前缀/空白 key
- **影响**: 不同方法相同参数共用 key（串数据）；未声明缓存名取到 `null`；工具默认缓存无过期；命名空间缺前缀时键归并
- **证据**: E-10（SOURCE_FACT + EXECUTABLE）、E-11（SOURCE_FACT）
- **复现**: `GXCacheIsolationAuditTest#frameworkCachingConfigurerProvidesNoKeyGenerator`、`#defaultKeyGeneratorProducesSameKeyForDifferentMethods`（两个方法均得到 `shared-argument`）、`#undeclaredCacheNameIsNotCreated`
- **回归测试设计**: 输入 = 两个不同方法 + 相同参数；断言点 = key 不同；修复后断言翻转
- **兼容性影响**: BEHAVIOR_CHANGE（key 变化会导致缓存全部失效一次）
- **修复方向**: 提供默认 `KeyGenerator`（含方法名）或文档强制显式 `key`；未声明缓存名改为快速失败（需独立审批）
- **状态**: DELIVERED

### F-13 [MEDIUM] 并发工具的拒绝与生命周期语义含混

- **维度**: STABILITY, THREAD_SAFETY
- **判定**: RISK
- **位置**: `util/GXConcurrentToolsUtils.java:39`、`83-93`、`163-166`、`200-202`、`460-507`
- **触发条件**: 队列满；`shutdownThreadPool()` 之后继续提交
- **影响**: 队满时任务在提交线程执行，请求线程被长任务占用且结果写入时序反转；池关闭后不可重建，"失败"与"结果为空"都以同一标记表达
- **证据**: E-05（SOURCE_FACT + EXECUTABLE）
- **复现**: `GXConcurrencySafetyAuditTest#sharedPoolUsesCallerRunsPolicy`、`#sharedPoolTasksRunOnFrameworkOwnedThreads`
- **回归测试设计**: 输入 = 容量 1 的池 + 阻塞任务；断言点 = 第二个任务未在提交线程执行（或返回显式拒绝）；修复后断言翻转
- **兼容性影响**: BEHAVIOR_CHANGE
- **修复方向**: 改为 `AbortPolicy` + 显式降级，或提供可重建池；失败用异常区分（需独立审批）
- **状态**: DELIVERED

### F-14 [MEDIUM] 限流状态静态无上限且以不可信入参为键

- **维度**: THREAD_SAFETY, STABILITY
- **判定**: RISK
- **位置**: `util/GXCurrentRequestContextUtils.java:100`、`630-657`
- **触发条件**: 大量不同 `clientId` 在 60 秒窗口内打点
- **影响**: 静态 Map 无上限增长且容器关闭后不释放；每次调用 `removeIf` + `size()` 为 O(n) 遍历，长窗口下单次判定成本线性上升
- **证据**: E-04（SOURCE_FACT + EXECUTABLE）
- **复现**: `GXConcurrencySafetyAuditTest#rateLimiterKeepsOneEntryPerDistinctClientId` → 注册 1000 个键
- **回归测试设计**: 输入 = 10 万个不同 `clientId`；断言点 = 规模有上界（如 ≤ 窗口上限）；修复后应改为有界结构
- **兼容性影响**: NONE
- **修复方向**: 使用 Caffeine（`maximumSize` + `expireAfterWrite`）替换静态 Map（需独立审批）
- **状态**: DELIVERED

### F-15 [MEDIUM] 上传服务用实例字段跨请求传递状态

- **维度**: THREAD_SAFETY
- **判定**: RISK
- **位置**: `service/impl/GXFileUploadServiceImpl.java:31`、`45`、`61`、`79`
- **触发条件**: 并发上传/删除；不同业务在调用前设置不同的存储路径
- **影响**: 请求 A 设置的路径可能被请求 B 覆盖，删除操作落到其他请求的子目录；扩展名取自原始文件名（可伪造），无类型/大小白名单
- **证据**: E-15（SOURCE_FACT）
- **复现**: `GXSecurityBoundaryAuditTest#uploadStoragePathIsSharedInstanceState`——同一实例先后代表两个请求写入 `fileStoragePath`，`getStoragePath()` 只返回后者；真实并发交错仍属"未验证假设"
- **回归测试设计**: 输入 = 两个并发请求各自指定目录；断言点 = 各自操作落在自己的目录；修复后应由参数传递路径
- **兼容性影响**: BEHAVIOR_CHANGE（公开方法若去掉 setter 属破坏性）
- **修复方向**: 路径改为方法参数或请求上下文；扩展名白名单 + 大小上限（需独立审批）
- **状态**: DELIVERED

### F-16 [MEDIUM] 反射与拷贝缓存实际失效或整体清空

- **维度**: PERFORMANCE, STABILITY
- **判定**: RISK
- **位置**: `util/cglib/GXBeanCopierCache.java:11`、`28-34`；`util/GXCommonUtils.java:452-463`、`476-512`、`626-676`
- **触发条件**: 每次 `GXCglibUtils.copy/copyList`；反射方法缓存超过 4096；`buildTree` 大输入
- **影响**: 每次拷贝都可能重新生成 `BeanCopier` 字节码（弱键 + 新建字符串键）；缓存满即整体清空导致周期性抖动；`buildTree` 为 O(n²)
- **证据**: E-17（SOURCE_FACT + EXECUTABLE）、E-21（SOURCE_FACT）
- **复现**: `GXPerformanceBudgetAuditTest#beanCopierCacheKeyIsNewInstanceOnEveryLookup`、`#beanCopierCacheIsBackedByWeakKeys`
- **回归测试设计**: 输入 = 同一类型对连续取 100 次；断言点 = `BeanCopier.create` 只发生 1 次；修复后断言翻转
- **兼容性影响**: NONE
- **修复方向**: 强引用 `Class` 键 + 有界 LRU；树构建改为单次分组（需独立审批）
- **状态**: DELIVERED

### F-17 [MEDIUM] 表达式求值能力过宽且依赖预校验兜底

- **维度**: SECURITY_BOUNDARY
- **判定**: RISK
- **位置**: `util/GXSpELToolUtils.java:85`、`107`、`128`、`263`、`361`、`175`、`235-240`、`350-357`
- **触发条件**: 表达式文本来自配置/数据库/请求，或方法名可由外部拼装
- **影响**: `StandardEvaluationContext` 允许类型引用与任意方法调用；当前安全性依赖"方法存在性预校验"，一旦放宽即成为注入面
- **证据**: E-19（SOURCE_FACT）
- **复现**: 未执行验证（需构造可控表达式来源，缺真实业务入口）；报告中标注为未验证部分
- **回归测试设计**: 输入 = `T(java.lang.Runtime).getRuntime()`；断言点 = 求值被拒；修复后应使用 `SimpleEvaluationContext`
- **兼容性影响**: BEHAVIOR_CHANGE（现有复杂表达式可能失效）
- **修复方向**: 无写权限场景改 `SimpleEvaluationContext`；方法名改为变量绑定而非拼接（需独立审批）
- **状态**: UNVERIFIED_ASSUMPTION

### F-18 [MEDIUM] Web 装配顺序与可排除性缺失

- **维度**: CORRECTNESS, STABILITY
- **判定**: RISK
- **位置**: `web/config/GXWebMvcConfigurer.java:40-47`；`web/config/GXHandlerMethodArgumentResolverConfigurer.java:23-33`
- **触发条件**: 多拦截器、需要放行登录/健康检查/静态资源；生产环境关闭 `-ea`
- **影响**: 拦截器顺序随 Bean 名变化，行为不可预测；无法按路径排除；`assert` 失效后判空退化为 NPE
- **证据**: E-20（SOURCE_FACT）
- **复现**: 未执行验证（需真实 MVC 容器装配）；结论来自源码事实
- **回归测试设计**: 输入 = 三个不同 `@Order` 的拦截器；断言点 = 执行顺序按 `@Order`；修复后断言翻转
- **兼容性影响**: NONE
- **修复方向**: 按 `@Order`/`Ordered` 排序；暴露 include/exclude 配置；`assert` 改 `Objects.requireNonNull`（需独立审批）
- **状态**: UNVERIFIED_ASSUMPTION

### F-22 [MEDIUM] 三处非 MDC 的 ThreadLocal 清理不成对

- **维度**: THREAD_SAFETY, STABILITY
- **判定**: RISK
- **位置**: `filter/GXXssHttpServletRequestWrapper.java:32`；`service/impl/GXBaseCacheLockServiceImpl.java:26`、`:149`；`api/GXBaseServeApiImpl.java:35`、`:284-290`、`:337-341`
- **触发条件**: 容器线程复用；调用 `callBindTargetServeSericeClass` 后未执行对应 runner；加锁后只走 `releaseLock` 而不调 `releaseAllLocks`；XSS 包装器每次请求新建实例
- **影响**: `HTML_FILTER`（内含可变 `vTagCounts` 状态）随线程长期存活且从不移除；锁持有表在异常/部分释放路径下残留，后续请求可能读到上一请求的计数；绑定类在未进入 runner 时泄漏到下一请求
- **证据**: E-25（SOURCE_FACT）
- **复现**: `GXContextPropagationAuditTest#nonMdcThreadLocalsAreNotCleared`——构造包装器后 `HTML_FILTER` 在本线程有实例且无清除 API；`tryLock` + `releaseLock` 后 `THREAD_LOCKS` 仍持有空 Map。`DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL` 为源码事实（未执行）
- **回归测试设计**: 输入 = 同一线程"加锁 → 仅 `releaseLock` → 再次加锁"与"绑定类 → 不执行 runner"；断言点 = 第二次操作的 `THREAD_LOCKS` 为空、`getServeServiceClass()` 为 `null`；修复后由"残留"变为"清理"
- **兼容性影响**: NONE（内部清理，不改公开签名）
- **修复方向**: 三处改为方法/请求作用域并补 `finally` 清理；`HTML_FILTER` 改局部实例或显式 `remove()`（需独立审批）
- **状态**: DELIVERED

### F-19 [LOW] 语义与重复配置

- **维度**: CORRECTNESS
- **判定**: RISK
- **位置**: `util/GXCurrentRequestContextUtils.java:417-420`；`util/GXTraceIdContextUtils.java:18-21`；`config/GXCORSConfig.java:39-51`、`73-79`；`web/interceptor/GXAuthorizationInterceptor.java:12-15`；`web/interceptor/GXBaseSSOPermissionInterceptor.java:3-4`
- **触发条件**: 异步/定时任务中调用 `isRPC()`；调用方用 `== null` 判断 traceId；CORS 同时配置 MVC 与 `CorsFilter`
- **影响**: 异步场景被误判为 RPC；`getTraceId()` 返回空串使空值判断失效；CORS 存在死分支与双份配置；鉴权基类默认放行、SSO 拦截器为空类
- **证据**: E-04（SOURCE_FACT）、E-22（SOURCE_FACT）
- **复现**: `GXTraceIdContextUtilsTest` 已锁定返回空串的行为（既定契约）
- **回归测试设计**: 输入 = 无请求上下文调用 `isRPC()`；断言点 = 返回 false；修复后断言翻转（需先确认下游是否依赖）
- **兼容性影响**: BEHAVIOR_CHANGE
- **修复方向**: `isRPC()` 改为显式标志；`getTraceId()` 保持契约但在文档标注（需独立审批）
- **状态**: DELIVERED

### F-20 [LOW] 可观测性缺口

- **维度**: STABILITY
- **判定**: RISK
- **位置**: `util/GXLoggerUtils.java:21-32`、`112-122`；`config/support/GXCachingConfigurerSupport.java:36-62`；`api/GXBaseServeApiImpl.java:344-364`；`util/GXCommonUtils.java:334-336`
- **触发条件**: 使用 `logInfo/logWarn/logDebug` 打异常；缓存读写失败；反射调用失败
- **影响**: 部分级别不提取 `Throwable` 导致栈丢失；缓存异常只记日志不重抛、无指标；反射失败以 `warn` + `null` 收敛，查询结果退化为空集合
- **证据**: E-22（SOURCE_FACT）
- **复现**: `GXLoggerUtilsTest` 覆盖 `logError` 分支，未覆盖其余级别（缺口已在第 11 章登记）
- **回归测试设计**: 输入 = `logWarn("x {}", ex)`；断言点 = 日志中包含栈；修复后应保留栈
- **兼容性影响**: NONE
- **修复方向**: 统一提取末尾 `Throwable`；缓存异常计数并暴露指标（需独立审批）
- **状态**: DELIVERED

### F-21 [LOW] 契约文档与实现不一致

- **维度**: CORRECTNESS
- **判定**: CONTRACT_DEVIATION
- **位置**: `leaf-base-framework/AGENTS.md`"关键行为"2/3 条、宪法 §V；对照 `filter/GXBaseRequestLoggingFilter.java:26-43`、`config/GXAsyncConfig.java:24-29`
- **触发条件**: 下游按文档认为"MDC/TraceId 在异步链路已传播且清理完整"
- **影响**: 使用方基于错误认知设计异步链路，排查成本被转嫁
- **证据**: E-22（SOURCE_FACT）
- **复现**: 对照文档条目与实现位置即可复核，无需运行
- **回归测试设计**: 输入 = `leaf-base-framework/AGENTS.md` 全文；断言点 = 是否包含"当前实现边界（2026-10-22 审查登记）"一节及其 5 条现状条目；修复后应由 `false` 变为 `true`（当前已落地）
- **兼容性影响**: NONE（仅文档）
- **修复方向**: 在 `AGENTS.md` 明确"异步执行器未接入 MDC 包装、请求结束仅恢复 traceId"的现状与使用约束
- **状态**: DELIVERED

---

## 6. 上下文纪律矩阵

| 上下文项 | 写入点 | 传播路径 | 清理点 | 异常路径 | 判定 | 发现 | 证据 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `TRACE_ID` | `filter/GXBaseRequestLoggingFilter.java:28` | `SYNC` | `:42`（`restoreTraceId`） | RESTORED | OK | — | — |
| `TRACE_ID` | `util/GXTraceIdContextUtils.java:34-41` | `SYNC` | 无自动清理 | NOT_EXERCISED | GAP | F-02 | `GXContextPropagationAuditTest#customMdcKeyLeaksAfterRequestFilterChain` |
| `TRACE_ID` | 同上 | `ASYNC_EXECUTOR` | 无 | NOT_EXERCISED | GAP | F-01 | `GXContextPropagationAuditTest#asyncExecutorDoesNotPropagateTraceId` |
| `TRACE_ID` | 同上 | `FORK_JOIN` | 无 | NOT_EXERCISED | GAP | F-01 | `GXContextPropagationAuditTest#forkJoinSubmitOfForkJoinTaskIsNotWrapped` |
| `TRACE_ID` | 同上 | `EVENT_BUS` | 无 | NOT_EXERCISED | GAP | F-10 | — |
| `TRACE_ID` | `web/interceptor/GXTraceIdInterceptor.java:14-22`（`preHandle` 读取并回写响应头，`afterCompletion` 空实现） | `SYNC` | 无（`:21-22` 空） | NOT_EXERCISED | GAP | F-02 | — |
| `MDC_OTHER` | 业务代码任意位置 | `SYNC` | `filter:42` 只恢复 traceId | LEAKED | GAP | F-02 | `GXContextPropagationAuditTest#customMdcKeyLeaksAfterRequestFilterChain` |
| `MDC_OTHER` | `util/GXMdcThreadUtils.java:33-130`（`getMdcContext`/`wrap`/`wrapSupplier`/`supplyAsync`/`runAsync`：`:34`、`:44-57`、`:65-78`、`:85-89`、`:94-107`、`:113-129`） | `ASYNC_EXECUTOR` | `finally` 内 `MDC.clear()` + 还原 | RESTORED | OK | — | `GXMdcThreadUtilsTest#testWrapRunnableMdcPropagation`、`#testWrapCallableMdcPropagation`、`#testWrapSupplierMdcPropagation`、`#testSupplyAsyncMdcPropagation`、`#testRunAsyncMdcPropagation`、`#testMdcContextCleanup`、`GXContextPropagationAuditTest#explicitWrapRestoresTraceIdInsideWorkerThread` |
| `MDC_OTHER` | `util/GXMdcThreadUtils.java:132-184`（`contextWrapper`/`consumerWrapper`/`biFunctionWrapper`/`biConsumerWrapper`，逐元素/逐阶段重建包装） | `PARALLEL_STREAM` | `finally` 内 `MDC.clear()` + 还原（`:150-154`） | RESTORED | OK（成本见 F-16） | — | `GXMdcThreadUtilsTest#testCompletableFutureThenApplyExplicitWrapperMdcPropagation`、`#testCompletableFutureThenComposeExplicitWrapperMdcPropagation`、`#testCompletableFutureWhenCompleteExplicitWrapperMdcPropagation`、`GXContextPropagationAuditTest#parallelStreamElementWrappersPropagateMdcPerElement`、`GXContextPropagationAuditTest#parallelStreamElementWrapperRestoresMdcAfterException` |
| `MDC_OTHER` | `wrapper/mdc/GXMdcWrapperForkJoinPool.java:26-43` | `FORK_JOIN` | 委托 `GXMdcThreadUtils.wrap` | RESTORED | OK | — | `GXMdcWrapperForkJoinPoolTest#testExecuteMdcPropagation`、`#testSubmitMdcPropagation`、`#testInvokeAllMdcPropagation`、`#testMdcContextCleanup` |
| `MDC_OTHER` | `wrapper/mdc/GXMdcWrapperForkJoinPool.java:45-53` | `FORK_JOIN` | 无包装 | NOT_EXERCISED | GAP | F-01 | `GXContextPropagationAuditTest#forkJoinSubmitOfForkJoinTaskIsNotWrapped` |
| `MDC_OTHER` | `wrapper/mdc/GXMdcWrapperThreadPoolTaskExecutor.java:13-41`（`execute`/`submit`/`submitCompletable` 全系经 `GXMdcThreadUtils.wrap`；E-01 装配缺口见 F-01） | `ASYNC_EXECUTOR` | 任务内 `wrap` 的 `finally` 还原 | RESTORED | OK | — | `GXMdcWrapperThreadPoolTaskExecutorTest#testExecuteMdcPropagation`、`#testSubmitMdcPropagation`、`#testMdcContextCleanup` |
| `MDC_OTHER` | `wrapper/mdc/GXMdcWrapperThreadPoolExecutor.java:32-75`（`execute`/`submit`/`invokeAll` 全系经 `GXMdcThreadUtils.wrap`；E-01 装配缺口见 F-01） | `ASYNC_EXECUTOR` | 任务内 `wrap` 的 `finally` 还原 | RESTORED | OK | — | `GXMdcWrapperThreadPoolExecutorTest#testExecuteMdcPropagation`、`#testSubmitMdcPropagation`、`#testMdcContextCleanup` |
| `REQUEST_CONTEXT` | `util/GXCurrentRequestContextUtils.java` | `SYNC` | 依赖容器请求作用域 | NOT_EXERCISED | RISK | F-19 | — |
| `REQUEST_CONTEXT` | 同上 | `ASYNC_EXECUTOR` | 无传播 | NOT_EXERCISED | GAP | F-19 | — |
| `REQUEST_CONTEXT` | `web/advice/GXRequestBodyAdvice.java:80-98`（写入 `JSON_REQUEST_BODY` 属性） | `SYNC` | 依赖容器请求作用域，无显式清理 | NOT_EXERCISED | GAP | F-04 | `GXSecurityBoundaryAuditTest#requestBodyIsStoredWithSensitiveFields` |
| `MDC_OTHER` | `util/GXConcurrentToolsUtils.java:120-135`（`withMdcContext` 保存/还原） | `ASYNC_EXECUTOR` | `finally` 中 `setContextMap(original)` 或 `clear()` | RESTORED | OK | — | `GXConcurrentToolsUtilsTest#composerFutureCompletesSuccessfullyAndPropagatesMdc`、`#completedResultsDoNotLeakTaskLocalMdc` |
| `MDC_OTHER` | `util/GXConcurrentToolsUtils.java:157`（`composerFuture` 提交侧捕获，`:168` 进 `withMdcContext`） | `ASYNC_EXECUTOR` | `finally` 还原（`withMdcContext:129-135`） | RESTORED | OK | — | `GXConcurrentToolsUtilsTest#composerFutureCompletesSuccessfullyAndPropagatesMdc` |
| `MDC_OTHER` | `util/GXConcurrentToolsUtils.java:349`（`allOf` 等待侧捕获，`:356` 进 `withMdcContext`） | `SYNC` | `finally` 还原（`withMdcContext:129-135`） | RESTORED | OK | — | `GXConcurrentToolsUtilsTest#allOfCompletesNormally`、`#allOfDefaultTimeoutOverloadCompletesNormally` |
| `THREAD_LOCAL_OTHER` | `filter/GXXssHttpServletRequestWrapper.java:32`（`HTML_FILTER`） | `SYNC`（容器线程复用） | 无（从不 `remove`） | LEAKED | GAP | F-22 | `GXContextPropagationAuditTest#nonMdcThreadLocalsAreNotCleared` |
| `THREAD_LOCAL_OTHER` | `service/impl/GXBaseCacheLockServiceImpl.java:26`（`THREAD_LOCKS`） | `SYNC` | 仅 `releaseAllLocks()` 内 `remove()`（`:149`） | NOT_EXERCISED | GAP | F-22 | `GXContextPropagationAuditTest#nonMdcThreadLocalsAreNotCleared` |
| `THREAD_LOCAL_OTHER` | `api/GXBaseServeApiImpl.java:35`（`DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL`） | `SYNC` | 仅 `runInThreadLocalContext` 的 `finally`（`:337-341`） | PARTIAL | GAP | F-22 | —（源码事实） |
| `SECURITY_CONTEXT` | 未在本模块发现写入点 | — | — | — | OK | — | — |

**矩阵结论**：`MDC_OTHER` 的清理在过滤层只覆盖 traceId 单键（F-02），异步与 ForkJoin 两条传播路径存在确定性缺口（F-01）；`GXMdcThreadUtils` 与 `GXConcurrentToolsUtils` 自身的包装/还原实现是正确的，缺口在"没有接入"，不在"实现错误"。三处非 MDC 的 `ThreadLocal`（`HTML_FILTER`、`THREAD_LOCKS`、`DYNAMIC_SERVE_SERVICE_CLASS_THREAD_LOCAL`）清理不成对，统一登记为 F-22。

**证据口径**（对应 `data-model.md` §3 不变量）：`判定=OK` 且传播路径含 `ASYNC_EXECUTOR`/`FORK_JOIN`/`PARALLEL_STREAM` 的行，其"证据"列必须引用可执行用例全名（`类#方法`）；本矩阵此类行均引用既有单元测试或审计特征化用例（2026-10-28 复测全绿）。`GXMdcWrapperThreadPoolTaskExecutor`/`GXMdcWrapperThreadPoolExecutor` 两行的 `OK` 仅指包装实现本身正确，二者在生产链路中从未注册为 Bean（E-01，装配缺口归 F-01）。全部 `ThreadLocal`/`MDC` 使用点（`grep -E '\bThreadLocal\b|org\.slf4j\.MDC'` 命中的 9 个生产文件 + `GXTraceIdInterceptor`/`GXBaseRequestLoggingFilter` 两个 traceId 触点）均已在本表逐点判定。

---

## 7. 并发与生命周期矩阵

| 组件 | 创建方 | 并发上限 | 停止路径 | 泄漏风险 | 发现 |
| --- | --- | --- | --- | --- | --- |
| `async-executor` | `config/GXAsyncConfig.java:26`（每次调用新建） | `UNBOUNDED`（虚拟线程，无并发限制） | 未注册为 Bean，无 `destroy` | NEVER_RELEASED（无句柄） | F-01 |
| `concurrent-tools-pool` | `util/GXConcurrentToolsUtils.java:83`（静态初始化） | `core=CPU×1.0`、`max=CPU×2.0`、队列 10000 | JVM shutdown hook + `shutdownThreadPool()` | NEVER_RELEASED（关闭后不可重建） | F-13 |
| `event-executor(spring)` | `event/config/GuavaEventBusConfig.java:78-88` | `CPU*2`，达上限阻塞 | `close()`（`cancelRemainingTasksOnClose`） | NONE | F-10 |
| `event-executor(static)` | `event/center/AsyncEventBusCenter.java:30-37` | `CPU*2`、队列 `CPU*2048` | 无 | NEVER_RELEASED | F-10 |
| `forkjoin-wrapper` | 调用方按需 `new` | `parallelism` 由调用方指定 | `shutdown()` 透传 | NONE | F-01 |
| `rate-limiter-map` | `util/GXCurrentRequestContextUtils.java:100` | `UNBOUNDED` | 无 | GROWS_UNBOUNDED | F-14 |
| `class-cache` | `service/impl/GXDynamicCallMethodServiceImpl.java:18` | 1000（满即全清） | 无 | STALE_AFTER_REBUILD | F-06 |
| `bean-copier-cache` | `util/cglib/GXBeanCopierCache.java:11` | 弱键，无上限 | 依赖 GC | NONE（但缓存无效） | F-16 |
| `reflection-method-cache` | `util/GXCommonUtils.java:452` | 4096（满即全清） | 无 | NONE | F-16 |
| `file-upload-state` | `service/impl/GXFileUploadServiceImpl.java:31` | 单值（跨请求覆盖） | 无 | STALE_AFTER_REBUILD | F-15 |

---

## 8. 失败收敛清单

**统计口径**（与代码逐项对应，可复算）：

```powershell
(Get-ChildItem -Recurse -Filter *.java leaf-base-framework/src/main | Select-String -Pattern 'catch\s*\(').Count        # 175
(Get-ChildItem -Recurse -Filter *.java leaf-base-framework/src/main | Select-String -Pattern 'return null;').Count       # 173
```

判定规则：`静默 catch`（空 catch 体）> 0 → `VIOLATES_IV`；否则 `return null;` > 0 → `VIOLATES_III`；否则 `COMPLIANT`（失败被显式记录或转为异常）。已被既有用例锁定的行为记 `CONTRACT`。
所有分支均落入 §8.2 的某一行（按文件聚合），高危/典型分支在 §8.1 逐条展开。

### 8.1 高风险与典型分支（逐条）

| 位置 | 失败类型 | 收敛方式 | 日志级别 | 可观测 | 宪法判定 |
| --- | --- | --- | --- | --- | --- |
| `service/impl/GXSensitiveFieldDeEncryptServiceImpl.java:27-29` | INPUT_RETURN | 返回入参 | NONE | 否 | VIOLATES_IV |
| `service/impl/GXSensitiveFieldDeEncryptServiceImpl.java:42-44` | INPUT_RETURN | 返回入参 | NONE | 否 | VIOLATES_IV |
| `service/impl/GXSensitiveDataDecryptServiceImpl.java:45-49` | EXCEPTION_CAUGHT | `catch (Exception ignored)` | NONE | 否 | VIOLATES_IV |
| `service/impl/GXSensitiveDataEncryptServiceImpl.java:41-42` | EXCEPTION_CAUGHT | 静默忽略 | NONE | 否 | VIOLATES_IV |
| `handler/GXExceptionHandler.java:118-125` | DEFAULT_VALUE | `cause.message` 进响应 | ERROR | 是（日志）/否（客户端） | VIOLATES_III |
| `handler/GXExceptionHandler.java:66-78` | DEFAULT_VALUE | `ok(500)` | ERROR | 是 | VIOLATES_III |
| `service/impl/GXDynamicCallMethodServiceImpl.java:39-45` | NULL_RETURN | 返回 `null` | ERROR | 是 | VIOLATES_III |
| `service/impl/GXFileUploadServiceImpl.java`（保存/删除失败分支） | NULL_RETURN | 返回 `null`/`false` | WARN | 是 | VIOLATES_III |
| `util/GXConcurrentToolsUtils.java:200-202` | DEFAULT_VALUE | `completeExceptionally` + 标记值 | DEBUG | 否 | VIOLATES_III |
| `util/GXConcurrentToolsUtils.java:617-635` | DEFAULT_VALUE | 超时返回 | WARN | 是 | COMPLIANT |
| `api/GXBaseServeApiImpl.java:344-364` | NULL_RETURN | 反射失败返回 `null` | WARN | 是 | VIOLATES_III |
| `util/GXCommonUtils.java:334-336` | NULL_RETURN | 转换失败返回 `null` | DEBUG | 否 | VIOLATES_III |
| `util/GXEventPublisherUtils.java:51-56` | EXCEPTION_CAUGHT | 提交后发布失败仅记日志 | ERROR | 是 | COMPLIANT（已声明非 outbox） |
| `config/support/GXCachingConfigurerSupport.java:40-57` | EXCEPTION_CAUGHT | 只记日志不重抛 | ERROR | 是 | COMPLIANT（缓存降级） |
| `config/cache/GXCaffeineCacheConfig.java:65-68` | EXCEPTION_CAUGHT | 转 `IllegalStateException` | ERROR | 是 | COMPLIANT（失败快速） |
| `web/interceptor/GXVerifyDeployEnvironmentInterceptor.java:33-41` | DEFAULT_VALUE | 写 body 但 HTTP 200 | WARN | 是 | VIOLATES_III |
| `util/GXTraceIdContextUtils.java:18-21` | DEFAULT_VALUE | 返回空串 | NONE | 否 | CONTRACT（既有用例锁定） |
| `filter/GXSQLFilter.java:39-41` | NULL_RETURN | 空输入返回 `null` | NONE | 否 | CONTRACT（既有用例锁定） |

### 8.2 全量登记（按文件聚合，覆盖 100% 分支）

| 文件 | catch | 静默 catch | return null | 判定 | 关联发现 |
| --- | --- | --- | --- | --- | --- |
| `api/GXBaseServeApiImpl.java` | 5 | 0 | 11 | VIOLATES_III | F-20 |
| `aspect/GXBusinessLogAspect.java` | 4 | 0 | 0 | COMPLIANT | - |
| `aspect/GXStopWatchAspect.java` | 2 | 0 | 0 | COMPLIANT | - |
| `aspect/GXValidateRequestParamAspect.java` | 2 | 0 | 0 | COMPLIANT | - |
| `config/GXCommandLineRunner.java` | 2 | 0 | 0 | COMPLIANT | - |
| `config/cache/GXCaffeineCacheConfig.java` | 2 | 0 | 0 | COMPLIANT | F-12 |
| `controller/GXBase64DecodedMultipartFile.java` | 2 | 0 | 0 | COMPLIANT | - |
| `convert/GXCGLibDataConvert.java` | 6 | 0 | 15 | VIOLATES_III | - |
| `convert/GXHutoolDataConvert.java` | 10 | 0 | 15 | VIOLATES_III | - |
| `deserializer/req/protocol/IntegerDeserializerProtocol.java` | 2 | 0 | 3 | VIOLATES_III | - |
| `dto/GXBaseData.java` | 0 | 0 | 1 | VIOLATES_III | - |
| `dto/inner/condition/GXConditionIsNotNULL.java` | 0 | 0 | 2 | VIOLATES_III | - |
| `dto/inner/condition/GXConditionIsNULL.java` | 0 | 0 | 2 | VIOLATES_III | - |
| `dto/inner/condition/GXExclusionDeletedFieldCondition.java` | 0 | 0 | 3 | VIOLATES_III | - |
| `dto/inner/field/GXUpdateRawField.java` | 1 | 0 | 0 | COMPLIANT | - |
| `event/GXBaseEvent.java` | 0 | 0 | 1 | VIOLATES_III | F-08 |
| `event/processor/GuavaASyncEventBusBeanPostProcessor.java` | 1 | 0 | 1 | VIOLATES_III | F-09 |
| `event/processor/GuavaSyncEventBusBeanPostProcessor.java` | 1 | 0 | 1 | VIOLATES_III | F-09 |
| `filter/GXSQLFilter.java` | 0 | 0 | 1 | VIOLATES_III | - |
| `filter/GXXssFilter.java` | 1 | 0 | 0 | COMPLIANT | - |
| `filter/GXXssHttpServletRequestWrapper.java` | 4 | 0 | 2 | VIOLATES_III | F-05 |
| `handler/GXAsyncExceptionHandler.java` | 4 | 0 | 1 | VIOLATES_III | - |
| `handler/GXExceptionHandler.java` | 1 | 0 | 0 | COMPLIANT | F-07 |
| `lang/GXDict.java` | 2 | 0 | 3 | VIOLATES_III | - |
| `listener/GXApplicationStartedListener.java` | 3 | 0 | 2 | VIOLATES_III | - |
| `service/GXBusinessService.java` | 1 | 0 | 2 | VIOLATES_III | - |
| `service/impl/GXBaseCacheLockServiceImpl.java` | 6 | 0 | 0 | COMPLIANT | - |
| `service/impl/GXDynamicCallMethodServiceImpl.java` | 5 | 0 | 5 | VIOLATES_III | F-06 |
| `service/impl/GXFileUploadServiceImpl.java` | 4 | 0 | 0 | COMPLIANT | F-15 |
| `service/impl/GXParseDynamicCallParamServiceImpl.java` | 6 | 0 | 14 | VIOLATES_III | - |
| `service/impl/GXSensitiveDataDecryptServiceImpl.java` | 2 | 2 | 1 | VIOLATES_IV | F-03 |
| `service/impl/GXSensitiveDataEncryptServiceImpl.java` | 1 | 1 | 0 | VIOLATES_IV | F-03 |
| `service/impl/GXSensitiveFieldDeEncryptServiceImpl.java` | 2 | 0 | 1 | VIOLATES_III | F-03 |
| `util/GXAuthCodeUtils.java` | 2 | 0 | 0 | COMPLIANT | - |
| `util/GXCommonUtils.java` | 25 | 0 | 12 | VIOLATES_III | F-16 |
| `util/GXConcurrentToolsUtils.java` | 12 | 0 | 5 | VIOLATES_III | F-13 |
| `util/GXCookieHelperUtil.java` | 1 | 0 | 4 | VIOLATES_III | - |
| `util/GXCurrentRequestContextUtils.java` | 6 | 0 | 11 | VIOLATES_III | F-14, F-19 |
| `util/GXDBStringUtils.java` | 0 | 0 | 10 | VIOLATES_III | - |
| `util/GXEventPublisherUtils.java` | 1 | 0 | 1 | VIOLATES_III | F-11 |
| `util/GXLoggerUtils.java` | 0 | 0 | 1 | VIOLATES_III | F-20 |
| `util/GXMdcThreadUtils.java` | 0 | 0 | 3 | VIOLATES_III | - |
| `util/GXSpELToolUtils.java` | 16 | 0 | 23 | VIOLATES_III | F-17 |
| `util/GXSpringContextUtils.java` | 16 | 0 | 10 | VIOLATES_III | - |
| `util/GXSqlDbTypeResolver.java` | 2 | 0 | 3 | VIOLATES_III | - |
| `util/GXTokenManagerUtils.java` | 5 | 0 | 0 | COMPLIANT | - |
| `util/manticore/GXManticoreClient.java` | 2 | 0 | 0 | COMPLIANT | - |
| `util/manticore/GXManticoreUtils.java` | 3 | 0 | 3 | VIOLATES_III | - |
| `validator/GXValidateCallRemoteServiceValidator.java` | 2 | 0 | 0 | COMPLIANT | - |
| **合计** | **175** | **3** | **173** | — | — |

**覆盖度**：上表行数按"含分支的文件"计，`catch` 合计与 `return null` 合计分别等于代码统计值，故 348 处分支（175 + 173）100% 有归属；其中 18 处高风险/典型分支已在 §8.1 逐条给出日志级别与可观测性判定。

### 8.3 HIGH 发现与矩阵/清单交叉引用

`data-model.md` §9 不变量 2 要求每条 `HIGH` 发现至少对应一处 `GAP` 或 `VIOLATES_*` 判定项，逐条对应如下：

| 发现 | 对应矩阵/清单项 | 判定归属 |
| --- | --- | --- |
| F-01 | §6 `TRACE_ID` × `ASYNC_EXECUTOR` / `FORK_JOIN`；§7 `async-executor`、`forkjoin-wrapper` | GAP / NEVER_RELEASED |
| F-02 | §6 `MDC_OTHER` × `SYNC`（过滤层只恢复 traceId） | GAP / LEAKED |
| F-03 | §8.1 `GXSensitiveFieldDeEncryptServiceImpl`（2 行）、`GXSensitiveDataDecryptServiceImpl`、`GXSensitiveDataEncryptServiceImpl` | VIOLATES_IV |
| F-04 | §6 `REQUEST_CONTEXT` × `GXRequestBodyAdvice.java:80-98` | GAP |
| F-05 | §8.2 `GXXssHttpServletRequestWrapper`（VIOLATES_III）；§9 `XSS_REWRITE` | VIOLATES_III |
| F-06 | §7 `class-cache`（STALE_AFTER_REBUILD）；§11 安全默认值矩阵"类/方法白名单" | STALE / 默认不安全 |
| F-07 | §8.1 `GXExceptionHandler`（2 行） | VIOLATES_III |
| F-08 | §8.2 `GXBaseEvent`（VIOLATES_III） | VIOLATES_III |

`MEDIUM` / `LOW` 发现的对应关系散落在 §6/§7/§8.2 的相关行，不强制逐条建表（不变量 2 仅约束 `HIGH`）。

---

## 9. 性能口径表

| `path` | 公式 | 单位 | 上限来源 | 冒烟断言 |
| --- | --- | --- | --- | --- |
| `XSS_REWRITE` | `peak ≈ 2 × bodyBytes × concurrentRewrites`；`cpu ≈ Σ(字符串值数 × 模式数)` | BYTES | `MAX_REQUEST_SIZE = 50MB`（`filter/GXXssHttpServletRequestWrapper.java:26-30`） | 构造期即消费原始流；`getContentLength() ≠ getHeader("Content-Length")` |
| `BODY_CACHE` | `serialize ≈ 1 × body`；`parse ≈ getHttpParam() 调用次数` | CALLS | 请求体大小（`web/advice/GXRequestBodyAdvice.java:86`） | 同一请求多次读取 → 属性只写一次但解析多次 |
| `RATE_LIMIT` | `perCall ≈ O(windowSize) × 2`（`removeIf` + `size`）；`keys ≤ distinctClientIds` | ITERATIONS | 无上限（`util/GXCurrentRequestContextUtils.java:100`） | N 个 `clientId` → `size() ≥ N` |
| `CONCURRENT_TOOLS` | `formatCalls ≈ 2 × tasks`（提交 + 完成）；`atoms ≈ 6 × (1 + formatCalls)` | STRING_BUILDS | 池 `core/max/队列`（`util/GXConcurrentToolsUtils.java:53-93`） | 日志级别关闭时格式化仍发生（实参求值） |
| `EVENT_DISPATCH` | `snapshotAlloc ≈ 2 × IdentityHashMap + Σ容器节点`；`submissions = subscriberCount` | SUBMISSIONS | 深度 64 / 节点 10 万（`event/GXBaseEvent.java:211-223`） | 1 事件 × k 订阅者 → 执行器提交 k 次 |
| `REFLECTION_COPY` | `BeanCopier.create ≈ copies`（缓存不命中）；`treeCost ≈ Σ layer(n) × reflectiveCalls` | CALLS | 反射缓存 4096（`util/GXCommonUtils.java:452-463`） | 同一类型对连续取 → `create` 次数 > 1 |

**复算示例**：

1. `XSS_REWRITE`：body = 1MB、并发 100 → 峰值约 200MB（2 × 1MB × 100），且集中在构造期瞬时；50MB 单请求即 100MB 瞬时。
2. `BODY_CACHE`：单请求 body = 2KB，`getHttpParam` 被调用 5 次 → 序列化 1 次（2KB），JSON 解析 5 次（约 10KB 累计解析量）；若以 `JSONUtil.toBean` 计，单次解析的临时对象数与 body 字段数同阶。
3. `RATE_LIMIT`：窗口内 1000 次/客户端，每次调用遍历该客户端窗口内节点（最多 1001）→ 单次 2 次遍历，1000 次调用约 2×10⁶ 节点访问。
4. `CONCURRENT_TOOLS`：1 万任务、`debug` 关闭 → `formatCalls = 2 × 10000 = 20000` 次 `String.format`（提交 1 次 + 完成 1 次），每次 6 个原子读 → 约 120000 次原子读；`debug` 开启时再叠加每任务 1 次格式化。
5. `EVENT_DISPATCH`：k=10 订阅者 → 10 次执行器提交；载荷为 100 节点 Map → 约 2 个 `IdentityHashMap` + 100 次副本写入。
6. `REFLECTION_COPY`：1000 节点、三层树 → 每层全量过滤 → 约 3×1000 次遍历 + 每元素一次反射调用；同一类型对拷贝 100 次时，弱键缓存下 `BeanCopier.create` 最坏 = 100 次（缓存不命中）。

---

## 10. 契约对照表

| 契约来源 | 条目 | 实现位置 | 判定 | 需要的文档改动 |
| --- | --- | --- | --- | --- |
| 宪法 I | 模块单向依赖、不反向依赖 | 本模块无 `leaf-base-*` 依赖（`pom.xml`） | 一致 | — |
| 宪法 II | 公开注解/DTO/异常/配置键稳定 | 本次零改动；F-03/F-04 的修复建议标注 BREAKING | 一致（需审批） | 修复时补迁移说明 |
| 宪法 III | 统一响应、业务失败抛异常、兜底不回显内部信息 | `handler/GXExceptionHandler.java:118-136`、`:66-78` | 不一致（F-07） | 无需改文档，需改实现 |
| 宪法 IV | 配置类型安全、显式开关、敏感值外部注入 | `config/GXAsyncConfig.java:26`（未注册 Bean）、硬编码密钥 `:55`、`GXResponseBodyAdvice.java:62` | 不一致（F-03/F-04） | 补配置项与元数据 |
| 宪法 V | `ThreadLocal` 成对 push/poll；异步必须走 MDC 包装 | `filter/GXBaseRequestLoggingFilter.java:42`、`GXAsyncConfig.java:26` | 不一致（F-01/F-02） | 明确现状边界 |
| 宪法 VI | 风险匹配的测试；错误路径覆盖 | 65 类 518 用例；`GXExceptionHandler` 零用例 | 部分不一致（第 10 章） | 补测试计划 |
| 宪法 VII | 命名与包结构、日志用 SLF4J | 包结构与 `GX` 前缀合规；`@Log4j2` 历史残留少量 | 一致 | — |
| `AGENTS.md` 关键行为 2 | ThreadLocal/MDC/TraceId/异步/事件必须有清理、恢复、异常传播 | 同宪法 V | 不一致（F-21） | 必须补"现状与限制" |
| `AGENTS.md` 关键行为 3 | 公共并发工具必须明确超时、取消、中断恢复、空集合语义 | `util/GXConcurrentToolsUtils.java` 有超时与中断恢复；失败语义含混（F-13） | 部分一致 | 补失败语义说明 |
| `AGENTS.md` 关键行为 4 | SpEL 缓存 key 稳定，不拼用户实参 | `util/GXSpELToolUtils.java` 参数以变量绑定（`390-408`） | 一致 | — |
| `AGENTS.md` 关键行为 5 | 配置类可读、可覆盖、可观测 | `config/cache/GXCaffeineCacheConfig.java` 抛 `IllegalStateException` 阻断启动 | 部分一致 | 补失败策略说明 |
| `AGENTS.md` 测试要求 1/2 | 行为变更必须有成功/失败/异常/空值/边界用例；并发热点必须有冒烟 | 事件/上下文/缓存均有部分覆盖；`GXExceptionHandler`、敏感数据服务零覆盖 | 不一致 | 补覆盖清单 |
| `AGENTS.md` 测试要求 3 | 性能关键节点至少可执行冒烟 | 本次新增 `GXPerformanceBudgetAuditTest`（计数型） | 一致（本次补齐） | — |
| `AGENTS.md` 测试要求 5 | DTO/协议对象变更验证序列化兼容 | 本次未变更 DTO | 不适用 | — |
| `AGENTS.md` 变更检查 4 | 评估性能、线程安全、资源释放、上下文清理 | 本报告第 5/6/8 章 | 一致 | — |

---

## 11. 未覆盖、缺口与口径

### 安全默认值矩阵

按 `contracts/audit-verdict-contract.md` §6 的 5 个方面逐项判定"默认是否安全"（不看修复后目标状态）：

| 方面 | 默认值 / 默认行为 | 默认是否安全 | 证据等级 | 关联发现 | 修复前置条件 |
| --- | --- | --- | --- | --- | --- |
| 默认密钥 | `common.sensitive.data.key` 缺省时使用 `XhFeV780D2218OBRm0xjcWvv`；`cors.cookie.secret` 缺省时使用 `CF3417E3CF6B0F28` | 否（功能照常可用） | EXECUTABLE + SOURCE_FACT | F-03, F-04 | 启动期必填校验 + 密钥轮换迁移方案 |
| 类 / 方法白名单 | `GXDynamicCallMethodServiceImpl#call(String, ...)` 不校验类名前缀；同模块解析侧有 `cn.maple.` 白名单 | 否 | EXECUTABLE | F-06 | 两条入口共用白名单 + 依赖跨包调用的兼容性评估 |
| 鉴权放行策略 | `GXAuthorizationInterceptor#preHandle` 默认返回 `true`；`GXBaseSSOPermissionInterceptor` 为空类 | 否（默认放行） | SOURCE_FACT | F-19 | 明确"子类必须覆盖"的文档约束或改为默认拒绝 |
| 异常响应内部信息 | 兜底与 `MultipartException` 回显 `cause.message`；参数格式错误返回 `ok(500)` | 否 | EXECUTABLE | F-07 | 固定对外文案 + 错误码语义调整 |
| 敏感数据流向 | 完整请求体 JSON 写入 `JSON_REQUEST_BODY` 属性；凭据 Cookie 携带加密码且可关闭 | 否（默认开启，可关闭部分未文档化） | EXECUTABLE | F-04 | 属性落盘前脱敏 + 默认关闭 + 文档声明 |

**矩阵结论**：5 个方面默认全部"不安全"，与 SC-006 的判定要求一致；其中 3 项可直接由 `GXSecurityBoundaryAuditTest` / `GXWebBoundaryAuditTest` 复现，2 项为源码事实。

### 未覆盖清单（既有单元测试零覆盖）

1. `handler/GXExceptionHandler`（26 个处理器）——P1，本次发现 F-07，需回归用例锁定修复。
2. `service/impl/GXSensitiveDataDecryptServiceImpl` / `GXSensitiveDataEncryptServiceImpl` / `GXSensitiveFieldDeEncryptServiceImpl`——P1，F-03。三者均无既有单元测试；本次 `GXSecurityBoundaryAuditTest` 已固化"解密降级返回入参"与"默认密钥可确定性加密"两项现状，但"失败必须显式抛错/告警、默认密钥必填"的修复回归仍需新增专项用例。
3. `filter/GXHTMLFilter`——P2，F-05。审计用例覆盖了 wrapper 层行为，未直接测标签白名单。
4. `config/GXCORSConfig`、`web/interceptor/GXVerifyDeployEnvironmentInterceptor`、`web/interceptor/GXAuthorizationInterceptor`——P2，F-19。
5. `util/cglib/GXBeanCopierCache` / `GXCglibUtils`——P2，F-16。审计用例覆盖键生成与缓存结构，未覆盖复制正确性。
6. `web/config/GXHandlerMethodArgumentResolverConfigurer`——P3，F-18。

### 判定口径速查

五档判定的判据（完整版见 `contracts/audit-verdict-contract.md` §1、§4）：

| 判定 | 一句话判据 | 计入 blockers |
| --- | --- | --- |
| `DEFECT` | 有明确承诺来源（宪法 / `AGENTS.md` / 公开语义 / 注释）且正常路径可复现 | 是（仅 `HIGH`） |
| `RISK` | 需特定配置、负载或调用方式才触发，现状无数据错误 | 否 |
| `CONTRACT_DEVIATION` | 文档有明确表述但实现与之不同 | 否（要求文档同步） |
| `ESTABLISHED_CONTRACT` | 行为已被既有用例断言锁定 | 否（禁止当缺陷推翻） |
| `UNVERIFIED_ASSUMPTION` | 无法在本环境构造验证 | 否（不计入统计） |

判定树：`是否被既有用例断言？` → 是则 `ESTABLISHED_CONTRACT`（若同时违反宪法明文则升级 `CONTRACT_DEVIATION`）；否 → `是否有明确承诺来源？` → 有则 `DEFECT`，无则 `RISK`；最后 `可复现吗？` → 否则降级 `UNVERIFIED_ASSUMPTION`。

### 审计用例命名与断言规范

- 类名 `GX<被测能力>AuditTest`，置于 `cn.maple.core.framework.audit`；与既有 `GX<被测类型>Test`（验证设计意图）职责分离。
- 方法名直述当前现状（如 `customMdcKeyLeaksAfterRequestFilterChain`），不得写成期望行为。
- 类头注释固定为 `CURRENT BEHAVIOR - flip when fixed.`，修复时断言必须翻转。
- 断言只允许计数、规模、布尔关系；禁止以毫秒为单位的固定耗时上限。
- 并发等待统一 `5 秒` 上限（`await` / `get`），禁止用 `Thread.sleep` 做同步。
- 不引入新依赖、不依赖真实中间件；环境缺失时必须能优雅跳过。

### Finding 状态机跟踪表

状态机定义见 `data-model.md` §1.1（`DRAFT → REVIEWED → CLASSIFIED → REPRODUCED → DELIVERED`）。

| 发现 | 级别 | 状态 | 复现依据 |
| --- | --- | --- | --- |
| F-01 | HIGH | DELIVERED | `GXContextPropagationAuditTest#asyncExecutorDoesNotPropagateTraceId`、`#forkJoinSubmitOfForkJoinTaskIsNotWrapped` |
| F-02 | HIGH | DELIVERED | `#customMdcKeyLeaksAfterRequestFilterChain` |
| F-03 | HIGH | DELIVERED | `GXSecurityBoundaryAuditTest#decryptReturnsInputWhenCiphertextIsInvalid`、`#encryptUsesHardcodedDefaultKeyDeterministically` |
| F-04 | HIGH | DELIVERED | `#requestBodyIsStoredWithSensitiveFields` |
| F-05 | HIGH | DELIVERED | `GXWebBoundaryAuditTest#jsonBodyValuesAreHtmlRewritten`、`#contentLengthHeaderStaysStaleAfterRewrite` |
| F-06 | HIGH | DELIVERED | `GXSecurityBoundaryAuditTest#arbitraryClassCanBeLoadedByName` |
| F-07 | HIGH | DELIVERED | `GXWebBoundaryAuditTest#multipartExceptionEchoesCauseMessage` |
| F-08 | HIGH | DELIVERED | `GXEventContractAuditTest#customPayloadIsNotSnapshotted`、`#mutableArrayKeyIsReplacedBySnapshotCopy` |
| F-09 | MEDIUM | DELIVERED | `GXEventContractAuditTest#jdkProxySubscriberIsNotRegistered`、`#sameBeanNameSecondInstanceIsSkipped` |
| F-10 | MEDIUM | DELIVERED | `GXEventExecutionAuditTest#staticAsyncCenterOwnsUnmanagedExecutorWithCallerRunsPolicy`、`#asyncDispatchSubmitsOncePerSubscriber` |
| F-11 | MEDIUM | DELIVERED | `GXEventContractAuditTest#registerCacheKeyUsesIdentityHashCode` |
| F-12 | MEDIUM | DELIVERED | `GXCacheIsolationAuditTest`（3 例） |
| F-13 | MEDIUM | DELIVERED | `GXConcurrencySafetyAuditTest#sharedPoolUsesCallerRunsPolicy`、`#sharedPoolTasksRunOnFrameworkOwnedThreads` |
| F-14 | MEDIUM | DELIVERED | `GXConcurrencySafetyAuditTest#rateLimiterKeepsOneEntryPerDistinctClientId` |
| F-15 | MEDIUM | DELIVERED | `GXSecurityBoundaryAuditTest#uploadStoragePathIsSharedInstanceState`；并发交错仍未构造 |
| F-16 | MEDIUM | DELIVERED | `GXPerformanceBudgetAuditTest#beanCopierCacheKeyIsNewInstanceOnEveryLookup`、`#beanCopierCacheIsBackedByWeakKeys` |
| F-17 | MEDIUM | UNVERIFIED_ASSUMPTION | 缺可控表达式来源，仅源码事实（E-19） |
| F-18 | MEDIUM | UNVERIFIED_ASSUMPTION | 需真实 MVC 容器装配，仅源码事实（E-20） |
| F-19 | LOW | DELIVERED | 既有用例已锁定空串契约（既定契约），证据等级 REPRODUCED |
| F-20 | LOW | DELIVERED | 源码事实（E-22），证据等级 REPRODUCED |
| F-21 | LOW | DELIVERED | `AGENTS.md` 已补"当前实现边界" |
| F-22 | MEDIUM | DELIVERED | `GXContextPropagationAuditTest#nonMdcThreadLocalsAreNotCleared` |

### 未验证假设（不计入缺陷统计）

- `F-15` 的并发覆盖路径：单线程可复现"后设置覆盖先设置"，但并发交错未构造。
- `F-17` 的表达式注入：缺少可控的表达式来源入口，无法构造端到端利用链。
- `F-18` 的拦截器顺序：需要真实 MVC 容器装配，未构造多拦截器场景。

### 本次未涉及的运行形态

真实 Servlet 容器（Tomcat 线程池参数）、集群/多实例、生产规模长稳。

---

## 12. 修订说明

| 编号 | 原判定 | 现判定 | 理由 |
| --- | --- | --- | --- |
| `E-03` | "`GXMdcWrapperThreadPoolTaskExecutor` 未覆写 `submitListenable`" | 删除该子项，保留 ForkJoin 缺口 | 实测 Spring Framework 7 已移除 `submitListenable`（E-23），该缺口不存在；F-01 的高危判定不受影响（未装配 + 无 `TaskDecorator` 已是充分证据） |
| `E-12` / `F-05` | "`&`、`<` 被实体化，可能与预期不符" | 升级为"正常文本被吞导致数据丢失" | 实测 `5 < 6 and 7 > 6` → `5  6`，`a&b<c` → `a&amp;b`（E-24），影响从"格式差异"变为"数据丢失" |
| `E-13` / `F-04` | "每个响应都下发 `UserData` Cookie" | 限定为"`cors.allow.credentials=true` 且配置 `cors.cookie.virusId` 时下发" | 复核 `GXResponseBodyAdvice.java:46-50`、`:65-66` 的分支条件，原表述过强 |
| `F-12` | "缓存不生效" | "模块不提供 key 隔离能力" | `GXCaffeineCacheConfigTest` 已证 `CacheManager` 装配成功；风险在于下游启用后的 key 共享（实测两方法同参数得到同一 key） |
| `E-22`（新增） | — | 契约偏差单列 | `AGENTS.md`/宪法要求与实现不一致，按宪法 II 只做文档同步 |
| `基线` | 交付验收 `tests=550`（518 既有 + 32 审计） | `tests=553`（519 既有 + 34 审计，2026-10-28 复测） | +1 既有：提交 `ab91dbfe`（`feat(fix): 完善sso模块`，2026-10-23）为 `GXWebMvcConfigurerTest` 新增 `#addInterceptorsHonoursOrderAnnotationBeforeBeanNameOrder`；+2 审计：收敛轮次 T067 为 `PARALLEL_STREAM` 矩阵行补证据用例 `GXContextPropagationAuditTest#parallelStreamElementWrappersPropagateMdcPerElement`，PR 评审修复补并行流异常路径断言 `#parallelStreamElementWrapperRestoresMdcAfterException`。`spec.md` SC-007 的"既有 518 个用例"为规格冻结表述（FR-010：spec 只留摘要与引用），不再修改；执行记录以第 2 章与本行为准 |
| `E-20` / `F-18` | "拦截器按 Bean 名排序而非声明顺序" | 排序子项已被后续实现收敛；F-18 剩余范围为 include/exclude 缺失与 `assert` 判空 | 与上一行同一差额来源提交 `ab91dbfe` 使 `GXWebMvcConfigurer` 按 `@Order`/`Ordered` 排序（Bean 名作 tie-breaker），并以 `GXWebMvcConfigurerTest#addInterceptorsHonoursOrderAnnotationBeforeBeanNameOrder` 锁定新行为；本报告为 2026-10-22 快照结论，台账 `web/config/GXWebMvcConfigurer.java` 行的 "interceptor order by bean name" 表述留待修复批次复核 |

---

## 13. 发现 → 修复任务映射与排期

| 发现 | 修复动作 | 依赖 | 兼容性 | 回归用例 | 批次 |
| --- | --- | --- | --- | --- | --- |
| F-02 | 请求前后整份 MDC 快照恢复 | 无 | BEHAVIOR_CHANGE | `GXContextPropagationAuditTest#customMdcKeyLeaksAfterRequestFilterChain`（翻转） | 立即 |
| F-03 | 失败显式抛错 + 密钥必填校验 + 日志 | 无 | BREAKING | `GXSecurityBoundaryAuditTest` 前两例（翻转） | 立即 |
| F-04 | 属性落盘前脱敏 + 默认关闭；密钥必填 | 无 | BEHAVIOR_CHANGE | `#requestBodyIsStoredWithSensitiveFields`（翻转） | 立即 |
| F-07 | 对外固定文案；参数错误改 `error` | 无 | BEHAVIOR_CHANGE | `GXWebBoundaryAuditTest#multipartExceptionEchoesCauseMessage`（翻转） | 立即 |
| F-01 | 注册带 `TaskDecorator` 的执行器 Bean；补 ForkJoin 包装 | F-02 的清理策略 | NONE | `GXContextPropagationAuditTest` 前两例（翻转） | 下一版本 |
| F-05 | 正文改写默认关闭/改输出编码；修正长度头 | F-04 的敏感数据策略 | BEHAVIOR_CHANGE | `GXWebBoundaryAuditTest` 两例（翻转） | 下一版本 |
| F-06 | 与解析侧共用白名单 | 无 | BREAKING | `#arbitraryClassCanBeLoadedByName`（翻转） | 下一版本 |
| F-08 | 载荷不可变校验或可插拔快照 | 无 | BEHAVIOR_CHANGE | `GXEventContractAuditTest` 两例（翻转） | 下一版本 |
| F-09 | 代理/多实例注册补齐 | 无 | NONE | `GXEventContractAuditTest#jdkProxySubscriberIsNotRegistered`、`#sameBeanNameSecondInstanceIsSkipped`（翻转） | 下一版本 |
| F-10 | 统一执行器；达上限拒绝并计数 | F-09 | NONE | `GXEventExecutionAuditTest` | 下一版本 |
| F-12 | 默认 `KeyGenerator` + 未声明缓存名快速失败 | 下游确认 | BEHAVIOR_CHANGE | `GXCacheIsolationAuditTest` | 下一版本 |
| F-13 | 拒绝策略与失败语义显式化 | 无 | BEHAVIOR_CHANGE | `GXConcurrencySafetyAuditTest` | 观察 |
| F-14 | 限流结构改 Caffeine 有界 | F-13 | NONE | `#rateLimiterKeepsOneEntryPerDistinctClientId`（翻转） | 观察 |
| F-15 | 路径参数化 + 类型/大小白名单 | 无 | BEHAVIOR_CHANGE | `GXSecurityBoundaryAuditTest#uploadStoragePathIsSharedInstanceState`（翻转） | 观察 |
| F-16 | 强引用 + 有界 LRU；树构建改单次分组 | 无 | NONE | `GXPerformanceBudgetAuditTest` 前两例（翻转） | 观察 |
| F-11 | 实例弱引用注册表 + 注销路径 | F-09 | NONE | `GXEventContractAuditTest#registerCacheKeyUsesIdentityHashCode`（翻转） | 观察 |
| F-17 | `SimpleEvaluationContext`；方法名变量绑定 | 下游表达式盘点 | BEHAVIOR_CHANGE | 新增求值拒绝用例 | 观察 |
| F-18 | 排序改 `@Order`；暴露 include/exclude | 无 | NONE | 新增 MVC 装配用例 | 观察 |
| F-19 | `isRPC()` 显式标志；CORS 去重 | 无 | BEHAVIOR_CHANGE | 新增用例 | 观察 |
| F-20 | 日志统一提取 `Throwable`；缓存异常计数 | 无 | NONE | `GXLoggerUtilsTest` 补分支 | 观察 |
| F-21 | 更新 `AGENTS.md` 现状与限制 | 无 | NONE | 不适用 | 立即（本次已完成） |
| F-22 | 三处 `ThreadLocal` 补成对清理（`HTML_FILTER` / `THREAD_LOCKS` / 绑定类） | 无 | NONE | `GXContextPropagationAuditTest#nonMdcThreadLocalsAreNotCleared`（翻转） | 观察 |

**排序依据**：`影响面（1-3）× 触发概率（1-3）× 可观测性倒数（1-3）`，≥18 为"立即"，12-17 为"下一版本"，<12 为"观察"。F-02（3×3×3=27）、F-03（3×3×2=18）、F-04（3×2×3=18）、F-07（2×3×3=18）落入"立即"。

---

## 14. 复现指南索引

| 发现 | 复现方式 |
| --- | --- |
| F-01 | `mvn -o -pl leaf-base-framework test -Dtest=GXContextPropagationAuditTest` |
| F-02 | `mvn -o -pl leaf-base-framework test "-Dtest=GXContextPropagationAuditTest#customMdcKeyLeaksAfterRequestFilterChain"` |
| F-03 / F-04 / F-06 | `mvn -o -pl leaf-base-framework test -Dtest=GXSecurityBoundaryAuditTest` |
| F-05 / F-07 | `mvn -o -pl leaf-base-framework test -Dtest=GXWebBoundaryAuditTest` |
| F-08 / F-11 | `mvn -o -pl leaf-base-framework test -Dtest=GXEventContractAuditTest` |
| F-09 | `mvn -o -pl leaf-base-framework test -Dtest=GXEventContractAuditTest`（`#jdkProxySubscriberIsNotRegistered`、`#sameBeanNameSecondInstanceIsSkipped`） |
| F-10 | `mvn -o -pl leaf-base-framework test -Dtest=GXEventExecutionAuditTest` |
| F-12 | `mvn -o -pl leaf-base-framework test -Dtest=GXCacheIsolationAuditTest` |
| F-13 / F-14 | `mvn -o -pl leaf-base-framework test -Dtest=GXConcurrencySafetyAuditTest` |
| F-15 | `mvn -o -pl leaf-base-framework test -Dtest=GXSecurityBoundaryAuditTest`（`#uploadStoragePathIsSharedInstanceState`） |
| F-16 | `mvn -o -pl leaf-base-framework test -Dtest=GXPerformanceBudgetAuditTest` |
| F-17 | 无执行用例：按 `util/GXSpELToolUtils.java:85`、`:107`、`:175` 复核，并见第 11 章"未验证假设" |
| F-18 | 无执行用例：按 `web/config/GXWebMvcConfigurer.java:40-47`、`web/config/GXHandlerMethodArgumentResolverConfigurer.java:23-33` 复核 |
| F-19 | 既有 `GXTraceIdContextUtilsTest`（锁定空串契约）+ 源码事实 `config/GXCORSConfig.java:39-51`、`:73-79` |
| F-20 | 既有 `GXLoggerUtilsTest`（现状）+ 源码事实 `api/GXBaseServeApiImpl.java:344-364`、`util/GXCommonUtils.java:334-336` |
| F-21 | 直接查阅 `leaf-base-framework/AGENTS.md`"当前实现边界（2026-10-22 审查登记）"一节 |
| F-22 | `mvn -o -pl leaf-base-framework test -Dtest=GXContextPropagationAuditTest`（`#nonMdcThreadLocalsAreNotCleared`） |

**整体复现**：`mvn -o -pl leaf-base-framework test -Dtest=GX*AuditTest` → 8 类 34 用例全绿（当前行为成立）。
