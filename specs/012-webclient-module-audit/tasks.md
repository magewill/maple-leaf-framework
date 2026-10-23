---

description: "Task list for leaf-base-webclient audit remediation"

---

# Tasks: leaf-base-webclient 正确性、线程安全与性能修复

**Input**: Design documents from `/specs/012-webclient-module-audit/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: 用户明确要求"修改完代码之后做完备的测试用例"（spec FR-019～FR-023），**测试任务为必选项**；遵循"先写失败用例（旧代码失败证据）→ 最小实现 → 转绿"顺序。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- 生产/测试代码均在 `leaf-base-webclient/src/`（Maven 模块内，AGENTS 硬性规则 1：仅改本模块；例外见 T040/T041 的跨模块说明）
- 设计/审计文档在 `specs/012-webclient-module-audit/`
- 包根 `cn.maple.webclient`（宪法 VII）；测试镜像 `config/service/aspect/handler` 分包

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 基线锁定与共享测试支撑

- [X] T001 运行 `mvn -pl leaf-base-webclient test` 并将基线输出（**Tests run: 17, Failures: 0, Errors: 0**，分文件：aspect 4 / config 4 / handler 2 / ascii 1 / service 6）记入 `specs/012-webclient-module-audit/audit-report.md`（首版仅"基线与证据来源"小节 + 章节骨架）
- [X] T002 [P] 创建测试支撑件 `leaf-base-webclient/src/test/java/cn/maple/webclient/testsupport/GXWebClientTestSupport.java`：JDK `com.sun.net.httpserver.HttpServer` 启停助手（可编程状态码/响应体/请求计数器）+ 日志捕获助手（捕获 logback 输出供 WARN/TRACE 断言），供 US2/US3/US5 复用；并提供**环境探测与优雅跳过**约定（端口绑定失败/必需 Bean 缺失时用 `org.junit.jupiter.api.Assumptions.abort` 跳过，不得让整条流水线失败，对应 FR-022 末句与宪法 VI）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 配置绑定基础设施——所有用户故事的阻塞前置（R-08）

**⚠️ 必须完成后任何 US 才能开始**

- [X] T003 创建 `leaf-base-webclient/src/main/java/cn/maple/webclient/config/GXWebClientProperties.java`（`@ConfigurationProperties(prefix = "maple.framework.web.client")`）。字段与默认值逐项对齐 data-model §1（冻结键不得偏差）：`timeoutSeconds=30`、`memoryBufferSizeMb=16`、`maxConnections=500`、`acquireTimeoutMillis=3000`、`pendingAcquireMaxCount=1000`、`idleTimeoutMillis=30000`、`maxLifeTimeMillis=300000`、`evictInBackgroundSeconds=120`、`retryMaxAttempts=3`、`retryDelayMillis=1000`；新增键 **8 个**：`retryMethods` 默认 `GET,HEAD,OPTIONS`（"解析失败 fail-fast；空=不重试"）、`userAgentSuffix` 默认空、`connectTimeoutMillis/readTimeoutMillis/writeTimeoutMillis/responseTimeoutMillis` 默认 null（"null→回退 timeoutSeconds×1000"，read/response "0=禁用"）、`lbGuardEnabled` 默认 `true`、`lbGuardAllowHosts` 默认空集合（见 spec Clarifications 复审条目）。校验规则按 data-model §1 逐字落实：**0 的语义按键定义**（`idle-timeout-millis`/`max-life-time-millis` 允许 0；`read/response-timeout-millis` 允许 0=禁用；`retry-max-attempts`/`retry-delay-millis` 允许 0；其余数值 0/负数启动 fail-fast），文案含键名
- [X] T004 [P] 补齐 `leaf-base-webclient/src/main/resources/META-INF/additional-spring-configuration-metadata.json`：**18 键（10 冻结 + 8 新增）**的 name/type/defaultValue/description 与 data-model §1 字段表一一对应（宪法 IV）；交付前用"字段数 = 元数据条目数 = 18"做机械核对
- [X] T005 `leaf-base-webclient/src/main/java/cn/maple/webclient/config/GXWebClientConfig.java` 配置读取迁移至注入 `GXWebClientProperties`（`getConfiguredInteger` 调用点替换；无参 `webClientBuilder()` 路径经同一 Properties 读取）；**零行为变化**：既有 17 条用例原样通过（P-02 的 503×4 重试断言、P-04 池参数断言为回归底线）；`webClientBuilder(ObjectProvider, ObjectProvider, ReactorClientHttpConnector, Environment)` **4 参签名保持不变**（后续 `lbMode` 由包级私有重载承载，见 plan"签名稳定性"）

---

## Phase 3: User Story 1 — 审查结论可复核 (P1)

**Story Goal**: 独立审计报告交付：19 条发现八要素齐备、可逐条定位复现（spec US1 / FR-001～FR-008）

**Independent Test**: 随机抽取 1 条结论 → 按报告文件+行区间定位 → 按复现步骤观测到同一现象（SC-001/002/003）

- [X] T006 [US1] 编写 `specs/012-webclient-module-audit/audit-report.md` 主体：F-01～F-19 八要素全表（维度/级别/文件+行区间/触发条件/影响面/证据 E-xx/复现方式/回归测试设计），证据引用 spec.md 的 E-01～E-24
- [X] T007 [US1] `audit-report.md` 增补"复现步骤"小节：F-01（合成 503×4 假尝试）、F-02（`msg` 丢失）、F-03（异步丢头）、F-04（池不释放）、F-05（POST 重放）各给最小复现（用例设计 + 预期观测值）；**证据留存约定**：优先以可长期保留的正式用例承载（满足 FR-020/SC-005），一次性脚手架探针须先把观测输出摘录进报告再删除源码，报告中保留"探针编号 + 摘录输出"以便复核
- [X] T008 [US1] `audit-report.md` 附录：E↔F↔FR↔SC↔quickstart **V1–V10** 映射表（V9/V10 为复审新增：token 缓存/配置健壮性、处理器文案与可达性）、P-01～P-08 保护断言清单、上游核对命令（`spring-cloud-commons-5.0.3-sources.jar`、`reactor-netty-core` javap、`spring-webflux-7.0.9` DefaultWebClient 引用）
- [X] T009 [US1] 实测核对 **5 条**结论并记录进 `audit-report.md`（对应 FR-004 的五项上游语义）：E-08（`@LoadBalanced` 附着 + serviceId 取法 → 直连 URL 现状假 503）、E-10（`ConnectionProvider` 释放契约：`dispose()` 未被容器调用）、E-11（请求上下文 ThreadLocal 在订阅线程不可达）、E-05（Content-Length 阈值常量错位）、E-19（`"{}"` 哨兵语义）；每条附观测输出或交叉引用（E-10/E-11 可引用 T024/T018 探针用例的观测值）；同时输出"未验证假设"清单（FR-003），标注尚未取得实测证据的推论及后续验证任务号

---

## Phase 4: User Story 2 — 出站请求语义可判定 (P1)

**Story Goal**: 寻址/错误可见性/幂等重试三类高危语义修复（F-01/F-02/F-05，spec US2 / FR-011/FR-012）

**Independent Test**: 服务名 URL、直连 URL、4xx/5xx/网络异常 × 幂等/非幂等 6 类组合，记录最终异常、尝试次数、副作用次数（V1/V2/V5）

**Tests first（旧代码失败证据，先写并确认失败）**:

- [X] T010 [P] [US2] 创建 `leaf-base-webclient/src/test/java/cn/maple/webclient/config/GXWebClientErrorMappingTest.java`：`preservesUnifiedResponseMsgField`（500+`{"code":500,"msg":"remote detail"}` → 异常消息含 `remote detail`，旧代码失败）+ 形状矩阵（Spring `message` 形状/纯文本/空/非法 JSON 回落通用文案）+ body `code` 覆盖断言
- [X] T011 [P] [US2] 创建 `leaf-base-webclient/src/test/java/cn/maple/webclient/config/GXWebClientLbGuardTest.java`：`rejectsDirectHostWithClearError`（`https://api.example.com` → `GXBusinessException` code=400、消息含"服务名寻址"、**0 次网络尝试**，旧代码失败=现状 4 次假尝试+误报）+ 主机形态矩阵（FQDN/IP/`localhost`/带端口拦截；服务名 `my-service` 放行）+ `allowsDottedServiceIdViaAllowList`（`lb-guard-allow-hosts=svc.ns.svc.cluster.local` → 放行）+ `fallsBackToLegacyBehaviorWhenGuardDisabled`（`lb-guard-enabled=false` → 交 LB 过滤器，合成 503 可复现）+ `webClientDirectBuilder` 直连放行
- [X] T012 [P] [US2] 创建 `leaf-base-webclient/src/test/java/cn/maple/webclient/config/GXWebClientRetryGuardTest.java`：`doesNotRetryNonIdempotentMethodByDefault`（503+POST → 1 次尝试，旧代码失败=4 次）+ `retry-methods=GET,HEAD,OPTIONS,POST` 恢复旧范围（POST→4 次）+ GET 503×4 保持（P-02）+ 耗尽抛原始失败

**Implementation**:

- [X] T013 [US2] 创建 `leaf-base-webclient/src/main/java/cn/maple/webclient/config/GXWebClientErrorResDto.java`（双形状字段 `msg`/`message`/`code`/`status`）并接入 `GXWebClientConfig.java` 的 `handle4xxError`/`handle5xxError`：detail 优先级 `msg` → `message` → `getHttpStatusDescription`（修复 F-02）；消息前缀公式冻结：`"客户端请求错误(4xx): {detail}"` / `"服务器处理错误(5xx): {detail}"`（contracts/error-mapping-contract.md）
- [X] T014 [US2] `GXWebClientConfig.java` 增加 LB 守卫过滤器：**`lbMode` 由新增的包级私有重载承载（`@Bean webClientBuilder` 的 4 参签名不变，委托时 `lbMode=true`，见 plan"签名稳定性"）**；判定顺序 = ①`lbGuardAllowHosts` 命中 → 放行 ②`lbGuardEnabled=false` → 跳过守卫（回旧行为） ③主机为 IP/`localhost`/含 `.`/带端口字面量 → `Mono.error(GXBusinessException)` code=400（不重试段） ④简单服务名 `^[A-Za-z0-9_-]+$` → 放行；拦截时输出 ASCII 日志 `Load-balanced client rejects direct host`（修复 F-01，白名单语义见 spec Clarifications 复审条目）
- [X] T015 [US2] `GXWebClientConfig.java` 新增 `webClientDirectBuilder` Bean（`WebClient.Builder`、prototype、**无** `@LoadBalanced`、过滤器/池语义与默认 builder 一致；`lbMode=false`）；核对 `specs/012-webclient-module-audit/contracts/bean-contract.md` 增量表逐项一致
- [X] T016 [US2] `GXWebClientConfig.java` `retryFilter` 增加 `retryMethods` 集合护栏（"默认 GET,HEAD,OPTIONS；空=不重试"；`onRetryExhaustedThrow` 透传保持，P-02）+ 新增内层时间戳过滤器逐尝试刷新 `X-Request-Start-Time` 并从 `requestFilter` 移除该头（E-14 闭合）
- [X] T017 [US2] 创建 `leaf-base-webclient/src/test/java/cn/maple/webclient/config/GXWebClientSpringBootTest.java`（`@SpringBootTest` + T002 HttpServer 真实装配）：重试矩阵端到端（GET 503×4 / POST 503×1 / retry-methods 恢复 POST×4）、三形状错误体端到端、LB 过滤器在链断言、`@SpringBootTest` 下 `webClient` Bean 单例语义

---

## Phase 5: User Story 3 — 并发与上下文透传安全 (P1)

**Story Goal**: 上下文"静默丢失"消除：快照传播 + 限流告警 + 切面语义区分（F-03/F-18，spec US3 / FR-010）

**Independent Test**: 请求线程 / `@Async` 线程池 / 调度线程 / reactor 并行线程四个必需形态各发一次调用（虚拟线程为补充），对比出站头集合与切面校验结果；50 并发无头污染（V3）

**Tests first**:

- [X] T018 [P] [US3] 创建 `leaf-base-webclient/src/test/java/cn/maple/webclient/config/GXWebClientContextPropagationTest.java`：`warnsWhenContextDroppedOnAsyncThread`（无快照的异步线程 → 限流 WARN `HTTP request dropped context` + 三头跳过，旧代码失败=静默跳过）+ `snapshotRestoresHeadersOnAsyncThread`（`.attribute(GXWebClientContextSnapshot.ATTRIBUTE_KEY, capture())` 后头齐全）+ 限流窗口断言（同包直接覆写 `CONTEXT_WARNING_INTERVAL_MILLIS` 实例字段使窗口可测，**不用反射**：窗口内连发 5 次仅 1 条 WARN）
- [X] T019 [P] [US3] 扩展 `leaf-base-webclient/src/test/java/cn/maple/webclient/aspect/GXWebClientAuthTokenAspectTest.java`：无请求上下文线程上的校验语义可区分"无上下文"与"token 无效"（两类不同可断言结果，旧代码失败=一律误判无效）；多 `GXWebClientService` Bean 时 WARN + 不误导文案

**Implementation**:

- [X] T020 [P] [US3] 创建 `leaf-base-webclient/src/main/java/cn/maple/webclient/service/GXWebClientContextSnapshot.java`：`capture()`（调用线程读 `RequestContextHolder`/MDC）、`apply(ClientRequest.Builder)`、`ATTRIBUTE_KEY` 常量；字段 `traceId/platform/authToken/capturedAtMillis` 全 `@Nullable`（"全部字段 @Nullable；apply 对 null 字段跳过注入；capture() 在无上下文环境返回全空快照（非 null）"，data-model §6）；**`capture()` 不生成 token**（token 依赖 `Environment` 而非 ThreadLocal，仍由 `requestFilter` 在订阅线程生成；需要携带时由调用方显式提供 `authToken` 的重载，见 spec Clarifications 复审条目）
- [X] T021 [US3] `GXWebClientConfig.java` `requestFilter` 取值顺序改造：`ClientRequest.attribute(ATTRIBUTE_KEY)` 快照 > 现行 ThreadLocal/MDC > 三者皆空输出限流 WARN（"每 30s ≤1 条"，ASCII）并跳过上下文头注入（修复 F-03）；限流状态用**实例字段** `AtomicLong lastContextWarningAtMillis` + 同包可覆写常量 `CONTEXT_WARNING_INTERVAL_MILLIS = 30_000`（非静态共享状态，不新增配置键，见 spec Clarifications 复审条目）
- [X] T022 [US3] `GXWebClientAuthTokenAspect.java` 与 `GXWebClientConfig.java` 的 `GXWebClientService` 获取改 ObjectProvider 语义（0 或 1 可用；">1 时 WARN 并取第一个"；报错文案区分"未实现"与"实现多于一个"，F-18）；`checkTokenValidity` 无上下文分支与"token 无效"分支语义区分
- [X] T023 [US3] 创建 `leaf-base-webclient/src/test/java/cn/maple/webclient/config/GXWebClientConcurrencyTest.java`：**五形态**（请求线程 / `@Async` 线程池 / 调度线程 / reactor 并行线程 / 虚拟线程 executor）× 50 并发发起调用，断言头内容线程间无污染、无共享状态错乱（名称与 spec FR-022 对齐：`@Async` 形态必须存在，虚拟线程为补充形态）；并发创建 builder 用例

---

## Phase 6: User Story 4 — 资源生命周期可释放 (P2)

**Story Goal**: 连接池释放闭环：容器关闭即 dispose、手工路径单池复用（F-04/F-10，spec US4 / FR-013）

**Independent Test**: 创建并关闭上下文 10 次 → 活跃池=0、驱逐线程退出；无参路径连调 10 次 → 活跃池=1；重复销毁幂等（V4）

**Tests first**:

- [X] T024 [P] [US4] 创建 `leaf-base-webclient/src/test/java/cn/maple/webclient/config/GXWebClientResourceLifecycleTest.java`：`disposesConnectionProviderOnContextClose`（探针断言 `dispose()` 被调用，旧代码失败=不调用）+ `disposeIsIdempotent`（二次 dispose 无异常）+ `reusesSinglePoolForManualBuilder`（无池环境 `new GXWebClientConfig().webClientBuilder()` ×10 → 活跃池=1，旧代码失败=泄漏 9 个）

**Implementation**:

- [X] T025 [US4] `GXWebClientConfig.java` `mapleWebClientConnectionProvider` Bean 声明改 `@Bean(destroyMethod = "dispose")`（`ConnectionProvider` 仅 `dispose()`，javap 已核对；修复 F-04 的容器路径）
- [X] T026 [US4] 创建 `leaf-base-webclient/src/main/java/cn/maple/webclient/config/GXWebClientResourceHolder.java`（全局至多 1 个自建池、`dispose()` 幂等、`ContextClosedEvent` 监听释放 + JVM shutdown hook 兜底）；`GXWebClientConfig.webClientBuilder()` 无参路径改"优先复用容器 Bean（按名→按类型）→ 缺失才经 holder 单池"（禁止每次调用新建，E-09 闭合；多 Connector 时不再静默新建）；**销毁后再调用 MUST 抛出 `GXBusinessException`（不自动重建）**，语义见 data-model §5"销毁后语义"
- [X] T027 [US4] 扩展 `GXWebClientResourceLifecycleTest.java` 生命周期集成：10 次创建/关闭上下文循环后池与驱逐线程归零；**销毁后再取 builder → 断言抛出 `GXBusinessException` 且不新建连接池**（data-model §5"销毁后语义"）

---

## Phase 7: User Story 5 — 性能开销可复算、修复可证 (P2)

**Story Goal**: 三类口径修复：token 短窗缓存、durationMs 分离退避、日志脱敏（F-06/F-07/F-08/F-16，spec US5 / FR-007、FR-014、FR-025）

**Independent Test**: 修复前后同口径对比（token 生成次数/环境查找次数/重试时延构成），SC-006 三项可独立复算（V6）

**Tests first**:

- [X] T028 [P] [US5] 创建 `leaf-base-webclient/src/test/java/cn/maple/webclient/config/GXWebClientMetricsContractTest.java`：`durationExcludesRetryBackoff`（2 次重试后成功 → `durationMs`≈3×单次网络耗时、`retryDelayMs`≈2000、`attempts=3`，旧代码失败=durationMs 含退避）+ `logsFailureWithDuration`（终态失败必有 `HTTP request failed:` ERROR 行，旧代码失败=无日志）+ Content-Length 告警阈值=配置值断言（`memory-buffer-size-mb=1` 时 2MB 响应告警）
- [X] T029 [P] [US5] 创建 `leaf-base-webclient/src/test/java/cn/maple/webclient/config/GXWebClientLogMaskTest.java`：`masksSensitiveHeadersInTraceLogs`（TRACE 输出 `X-Auth-Token`/`Cookie`/`Authorization` 值为 `******`，旧代码失败=明文）+ `userAgentExcludesOsName`（UA=`Maple-Leaf-WebClient/1.0` 可选后缀，旧代码失败=含 os.name）+ 大小写头名掩码
- [X] T030 [P] [US5] 扩展 `leaf-base-webclient/src/test/java/cn/maple/webclient/service/GXWebClientServiceTest.java`：`reusesGeneratedTokenWithinCacheWindow`（同 source+secret 窗口内 `authCodeEncode` 次数=1，旧代码失败=每请求 1 次）+ source/secret 变更失效重生成 + `getSensitiveHeaderFields()` 被消费断言

**Implementation**:

- [X] T031 [US5] `leaf-base-webclient/src/main/java/cn/maple/webclient/service/GXWebClientService.java` `generateHttpAuthToken()` 默认实现短窗缓存（"窗口 ≤60s 且 ≤expiry/2，按 source+secret 失效"，F-06/FR-025；签名与异常语义不变）；口径与 SC-006 一致：同一 `source+secret` 的 60s 窗口内恰 1 次生成、150s 有效期内 ≤3 次
- [X] T032 [US5] `GXWebClientConfig.java` `responseFilter` 口径分离：`durationMs`=各次尝试网络耗时之和（**不含退避**）+ 新字段 `attempts`/`retryDelayMs`（contracts/filter-chain-contract.md 日志格式）；Content-Length 告警阈值改读 `memoryBufferSizeMb`（修复 E-05）
- [X] T033 [US5] `GXWebClientConfig.java` TRACE 头输出接入 `getSensitiveHeaderFields()` 掩码（无 SPI 实例用内置映射，F-08）；`User-Agent` 固定 `Maple-Leaf-WebClient/1.0` + `userAgentSuffix` 追加（F-16）
- [X] T034 [US5] `specs/012-webclient-module-audit/audit-report.md` 附录补"性能口径复算记录"：修复前后 token 生成次数、环境查找次数、重试时延构成对比（SC-006 验收材料）

---

## Phase 8: User Story 6 — 测试完备与契约一致 (P3)

**Story Goal**: SPI 死扩展点接线、文案/治理收敛、文档同步（F-09/F-13/F-14/F-15/F-17，spec US6 / FR-015、FR-018、FR-024、FR-026）

**Independent Test**: 抽取任一修复项核对"失败→通过证据链"；AGENTS/手册与实现逐条一致（V7/V8）

**Tests first**:

- [X] T035 [P] [US6] 扩展 `leaf-base-webclient/src/test/java/cn/maple/webclient/service/GXWebClientServiceTest.java`：`invokesSpiLoggingHooks`（成功路径 `logRequestResponse` 全参+简版各 ≥1 次、失败路径 `logRequestException` ≥1 次，旧代码失败=零调用）+ 回调抛异常不影响主链路 + `checkTokenValidity` 解码异常记 WARN 后返回 false
- [X] T036 [P] [US6] 扩展 `leaf-base-webclient/src/test/java/cn/maple/webclient/handler/GXWebClientExceptionHandlerTest.java`：`WebClientResponseException` 文案由英文改中文（FR-026 允许收敛）、`WebClientRequestException` 的 `"WebClient网络请求异常"` 为**冻结文案（断言不得改动）**、返回码断言保持（500/远端状态）；并直接构造 `WebClientResponseException` 调用处理器，锁定"该分支仅服务未经本模块过滤器的外来客户端"的可达性声明

**Implementation**:

- [X] T037 [US6] `GXWebClientConfig.java` SPI 接线（R-07 时序表）：`responseFilter` 末尾调用 `logRequestResponse`（全参在 body 可得时、简版恒调）、链路 `doOnError` 调用 `logRequestException`（含重试耗尽/LB 守卫/token 失败）；回调 try-catch 隔离
- [X] T038 [US6] `leaf-base-webclient/src/main/java/cn/maple/webclient/handler/GXWebClientExceptionHandler.java` 文案统一中文（保留返回码语义）；`GXWebClientService.java` `checkTokenValidity` 的 `catch(Exception)` 改记 WARN（`exceptionType` only，不含敏感值）后 `return false`（F-14）
- [X] T039 [US6] `@Nullable` 标注补齐（contracts/spi-contract.md 清单：`getPlatform()` 返回、日志钩子可空参数、快照字段）+ `GXWebClientConfig.java`/`GXWebClientProperties.java` 配置钳制 fail-fast 文案含键名（F-17）；`mvn -pl leaf-base-webclient clean install -DskipTests` NullAway 零错误验证
- [X] T040 [P] [US6] 治理（跨模块，最小影响面）：`leaf-base-webclient/pom.xml` 删除模块内 `<loadbalancer.version>` 属性改引用 `${loadbalancer.version}`；根 `pom.xml` `<properties>` 增 `<loadbalancer.version>5.0.3</loadbalancer.version>`（中文注释：与 spring-cloud-dependencies 2025.1.3 对齐、显式钉住防漂移）。零行为变化，`mvn -pl leaf-base-webclient test` 全绿验证
- [X] T041 [US6] 文档同步：修正 `leaf-base-webclient/AGENTS.md`（删除"尚未建立有效测试基线"陈述、补寻址/流式超时/上下文三限制入口）；新增 `docs/leaf-base-webclient开发手册.md`（以 contracts/ 5 份契约为基线：18 键配置表、寻址模式与白名单、错误映射表、重试表、快照 API 示例、资源释放责任），并**必须包含四节专门说明**：①非可重放请求体（流/表单流）在重试场景的失败语义（FR-012）；②token fail-fast 既定语义及"本次不提供关闭开关"的结论（FR-018/F-19）；③`GXWebClientExceptionHandler` 文案语言与 `WebClientResponseException` 分支可达性（FR-026）；④`lb-guard-enabled` 默认开启的理由与回退方式（Complexity ⑤）

---

## Phase 8.5: 复审补充（`/speckit.analyze` 输出，Phase 9 之前完成）

**Purpose**: 收敛需求覆盖缺口（FR-016 / FR-017 / FR-005）与契约兼容性缺口（FR-009 签名稳定性）

- [X] T047 [US2/US6] 契约健壮性（跨故事）——四类超时接线（FR-016，`GXWebClientConfig.java`）：`mapleWebClientConnector` 中 `ChannelOption.CONNECT_TIMEOUT_MILLIS` 取 `connectTimeoutMillis`、`HttpClient.responseTimeout` 取 `responseTimeoutMillis`（0=不设置）、`doOnConnected` 内按 `readTimeoutMillis`/`writeTimeoutMillis` 决定是否加 `ReadTimeoutHandler`/`WriteTimeoutHandler`（0=不加）；四个键为 null 时一律回退 `timeoutSeconds×1000`；新建 `leaf-base-webclient/src/test/java/cn/maple/webclient/config/GXWebClientTimeoutTest.java` 断言独立生效、0=禁用、null 回退（非法值 fail-fast 断言并入 T039）
- [X] T048 [US2/US6] 契约健壮性（跨故事）——Bean 可覆盖性口径统一（FR-017，`GXWebClientConfig.java`）：`webClient`/`httpServiceProxyFactoryBuilder`/`httpServiceProxyFactory` 补 `@ConditionalOnMissingBean`；用例断言下游同名 Bean 覆盖时不再抛 `BeanDefinitionOverrideException`，且默认装配下三者仍为单例
- [X] T049 [US1] `audit-report.md` 增补"过滤器顺序 × 请求/响应阶段 × 重试交互"唯一定性矩阵（FR-005）：五列（token 短路 / 4xx-5xx 转换 / 重试捕获 / 耗时归属 / LB 守卫位置）与 `contracts/filter-chain-contract.md` 逐行对齐，避免两处口径漂移
- [X] T050 [US2] 签名稳定性与既有用例零改回归（FR-009）：确认 `webClientBuilder(ObjectProvider, ObjectProvider, ReactorClientHttpConnector, Environment)` 4 参签名保留、`GXWebClientConfigTest` 4 条 / `GXWebClientAuthTokenAspectTest` 4 条 / `GXWebClientServiceTest` 6 条断言原文未改；`mvn -pl leaf-base-webclient clean install -DskipTests` 零错误

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T042 全量验证：`mvn -pl leaf-base-webclient clean install -DskipTests`（ErrorProne+NullAway 零错误）+ `mvn -pl leaf-base-webclient test` 全绿（用例数从 17 单调增长至 **70**、0 删除/跳过；P-01/P-02/P-04/P-06/P-07 断言保留清单核对）
- [X] T043 下游契约回归：`mvn -pl leaf-base-eureka-client test` 全绿（SC-007；装配顺序与 Bean 契约未破坏）
- [X] T044 [P] 填满 `specs/012-webclient-module-audit/quickstart.md` 附录"旧代码失败证据表"：按 **F-01～F-19 全量建行**（T010/T011/T012/T018/T019/T024/T028/T029/T030/T035/T036/T039/T047 对应行填写用例名 + 失败断言原文；属"新增能力"而非"旧代码失败"的条目显式标注）+ 兼容说明**六处**成文（`retry-methods` 恢复旧重试范围、`durationMs` 口径迁移公式、UA 变更、过滤器链增量与 `X-Request-Start-Time` 迁移、LB 守卫默认开启与回退方式、错误体 DTO 新增）
- [X] T045 [P] 清理：临时探针/调试残留删除、`System.out` 检查、`LogMessageAsciiTest` 保持绿、日志 ASCII 终检（AGENTS 硬性规则 1）
- [X] T046 最终交付核对：tasks 全勾选、audit-report 的 E↔F↔V 映射无孤儿、按宪法流程 3 整理 PR 描述素材（动机/影响面/契约变更/验证命令与结果/兼容策略）

---

## Dependencies

```text
Phase 1 (T001–T002)
  └─► Phase 2 (T003–T005)   [阻塞所有 US：配置绑定是共享基础设施]
        ├─► Phase 3 US1 (T006–T009)          [P1，仅文档，可最早交付]
        ├─► Phase 4 US2 (T010–T017)          [P1，高危修复；T010–T012 先行]
        ├─► Phase 5 US3 (T018–T023)          [P1，高危修复；T018–T019 先行]
        ├─► Phase 6 US4 (T024–T027)          [P2；T024 先行]
        ├─► Phase 7 US5 (T028–T034)          [P2；T028–T030 先行]
        └─► Phase 8 US6 (T035–T041)          [P3；T035–T036 先行；T040 独立]
              └─► Phase 8.5 复审补充 (T047–T050) [FR-016/017/005/009 缺口；T049 可随 US1 并行]
                    └─► Phase 9 Polish (T042–T046) [全量回归收口]
```

- **Story 完成顺序**：US1 → US2 → US3 → US4 → US5 → US6 → 复审补充（T047–T050）→ Polish（P1 优先；US2/US3 无相互依赖，可并行）
- **跨 Story 文件约束**：`GXWebClientConfig.java` 被 US2/US3/US4/US5/US6 与复审补充依次修改（T013–T016→T021–T022→T025–T026→T032–T033→T037→T047–T048），严格按序执行避免冲突
- **测试先行约束**：每 US 的 T0xx 失败用例组必须先跑出"旧代码失败"并记录断言原文，再动实现任务

## Parallel Opportunities

- **Phase 1**: T001 ∥ T002
- **Phase 2**: T003 → T005（串行）；T004 ∥ T003
- **US1**: T006 → T007 → T008（同文件串行）；T009 紧随
- **US2**: T010 ∥ T011 ∥ T012（三个新测试类）；实现 T013→T014→T015→T016 串行（同文件）；T017 收口
- **US3**: T018 ∥ T019（不同文件）；T020 ∥（新文件）；T021→T022 串行；T023 收口
- **US4**: T024 先行；T025→T026 串行；T027 收口
- **US5**: T028 ∥ T029 ∥ T030（不同文件）；T031∥T032（不同文件）→T033 串行；T034 文档
- **US6**: T035 ∥ T036（不同文件）；T037→T038→T039 串行；T040 ∥ T041
- **复审补充**：T049 ∥ 任意（纯文档）；T047→T048→T050 串行（同文件 + 依赖前序 config 修改）
- **Polish**: T042→T043 串行；T044 ∥ T045；T046 收口
- **跨 Story**：Foundational 完成后 US1 ∥ US2 ∥ US3 可由多人并行（US2/US3 若并行，`GXWebClientConfig.java` 修改需按 T013–T016 先于 T021–T022 合并）

## Implementation Strategy

### MVP First（最小可交付增量）

1. Phase 1 Setup（基线 + 测试支撑）
2. Phase 2 Foundational（配置绑定，17 条用例全绿为门槛）
3. Phase 3 US1（审计报告交付——独立价值：结论可复核）
4. **STOP & VALIDATE**：US1 随机 3 条结论复核通过（T009 即验收动作）

### 增量交付（推荐首增量 = MVP + US2 + US3）

1. Setup + Foundational → 基础就绪
2. +US1 → 审查结论可交付（MVP）
3. +US2 → 寻址/错误/重试高危修复 → V1/V2/V5 独立验收
4. +US3 → 上下文高危修复 → V3 独立验收（P1 三故事齐 = 风险消除里程碑）
5. +US4 → 资源释放 → V4 验收
6. +US5 → 性能口径 → V6 验收
7. +US6 → 契约/文档收敛 → V7/V8 验收
8. Phase 9 → 全量回归 + 下游回归收口

### 并行团队策略

- 开发 A：US2（T010–T017）；开发 B：US3（T018–T023）——两人对 `GXWebClientConfig.java` 的修改按 T013–T016 → T021–T022 顺序合并
- 开发 C：US1 文档（T006–T009）∥ US4（T024–T027）
- 每 Story 完成即独立测试（quickstart V1–V10 对应场景），不依赖后续 Story

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖；同文件修改（尤其 `GXWebClientConfig.java`）严格串行
- 旧代码失败证据是 FR-020 硬门槛：每组失败用例先运行并记录断言原文，再实现（填入 quickstart 附录，T044 归档）
- 既有 17 条用例与 P-01/P-02/P-04/P-06/P-07 断言**只增不删**（SC-005/SC-007）
- 日志新增/修改全部 ASCII（AGENTS 硬性规则 1，`LogMessageAsciiTest` 守卫）；用户可见异常消息可用中文
- 六处行为偏差的兼容说明（T044）为交付必备：`retry-methods` 恢复旧重试范围、`durationMs` 口径公式、UA 变更、过滤器链增量与 `X-Request-Start-Time` 迁移、LB 守卫默认开启与回退、错误体 DTO 新增
- 编号约定：Phase 8.5 的 T047–T050 由 `/speckit.analyze` 复审补充，追加在原编号之后以避免既有 ID 漂移；执行顺序由上方 Dependencies 图决定，而非编号大小；T047/T048 标注为"契约健壮性（跨故事）"，按故事并行时需独立排期
- 无需求映射的支撑类任务：T002（测试支撑）、T045（清理）、T046（交付核对）只服务于流程与验证，不对应具体 FR/SC
- 任务总数 50（Setup 2 / Foundational 3 / US1 4 / US2 8 / US3 6 / US4 4 / US5 7 / US6 7 / 复审补充 4 / Polish 5）
- 提交粒度：按任务或逻辑组提交，信息格式 `fix(webclient): ...` / `test(webclient): ...`（宪法流程 2）

---

## Phase 10: Convergence

> 由 `/speckit.converge` 在 `/speckit.implement`（50/50 完成、65 测试全绿）之后追加，仅记录仍未满足规格/计划/宪法的剩余工作。基线：`GXWebClientConfig` 过滤器链 = request → retry（指标/日志/SPI/时间戳）→ errorMapping → lbGuard → LB。

- [X] T051 CRITICAL — 将 LB 守卫开关对齐宪法 IV 的 `maple.framework.enable.<feature>` 约定：`GXWebClientProperties.lbGuardEnabled` / `maple.framework.web.client.lb-guard-enabled`（默认 `true`）不在 `maple.framework.enable.*` 前缀下，且宪法要求功能开关默认关闭；新增 `maple.framework.enable.webclient-lb-guard` 作为统一入口（保留旧键为兼容别名并在 metadata 标注），显式记录"默认开启属 F-01 缺陷收敛"的豁免决策（AGENTS §5、手册 §3、plan Complexity ⑤）；同步 `additional-spring-configuration-metadata.json` 与 `GXWebClientLbGuardTest` 的开关用例 per Constitution IV (contradicts)
- [X] T052 消除请求期环境查找：`GXWebClientConfig.java:317` 仍在每次请求调用 `GXCommonUtils.getEnvironmentValue(spring.application.name)`，与 SC-006 ② "属性绑定后环境查找次数 = 0" 不符；在 builder 构建期解析并缓存 `X-Request-Source`（以及同处 `resolveService` 的 SPI 单例快路径），新增计数型 `Environment` 用例断言请求期环境查找 0 次 per SC-006 (partial)
- [X] T053 补齐"旧代码失败"实测证据：`quickstart.md` 证据表当前为推演式记录（"旧代码：…"），未附旧代码真实运行的断言原文；需在临时分支/`git stash` 中对每项修复用例实跑一次并记录输出（或用 `-Dtest=<用例名>` 对旧提交复现），把原文回填证据表并由 SC-005 口径复核覆盖率 100% per FR-020 / SC-005 (partial)
- [X] T054 同步 `AGENTS.md` 规则 16：现文要求"保持 `WebClient` 过滤器顺序"，而实现已合并 `responseFilter` 到 `retryFilter`、新增 `lbGuardFilter`；改为"以 `contracts/filter-chain-contract.md` 的当前顺序为准（request → retry → errorMapping → lbGuard → LB），顺序变更必须同步契约与本规则"，避免后续维护者按旧链改代码 per FR-024 (partial)
- [X] T055 收敛测试接缝：`GXWebClientConfig#resourceHolder()` 仅服务于生命周期用例，属任务未要求的测试可见性扩展；改为在 `GXWebClientResourceHolder`/`GXWebClientConfigTest` 侧完成断言（同包访问 holder 或通过 `DisposableBean#destroy` 行为断言），或在该方法 Javadoc 明确"package-private test seam，禁止跨包使用"并在手册登记 per plan: ResourceLifecycle 决策 (unrequested)

---

## Phase 11: Convergence

> 由第二轮 `/speckit.converge` 追加（Phase 10 完成后），记录剩余的证据完整性与边界项。执行结果：70 测试全绿、eureka-client 23 测试全绿。

- [X] T056 补齐"旧代码失败"实测证据：在 HEAD `90fbb137` 工作树跑第二轮探针，采集 F-01（`AtomicInteger(1)` 未拦截）、F-03（告警 1L vs 0L）、F-04（`destroyMethod` `(inferred)`）、F-06（两次 token 不同）、F-07（`durationMs=694` 含退避）、F-09（钩子调用 0 次）、F-13（英文文案）断言原文并回填 `quickstart.md` 与 `audit-report.md §7`；F-18 标注"源码核对（runner 默认允许 Bean 覆盖，未复现）" per FR-020 / SC-005 (partial)
- [X] T057 规范化守卫白名单并告警非法条目：`GXWebClientProperties` 新增 `normalizeHostEntry`（剥离端口、拒绝含协议/路径/非数字端口的条目）与 `invalidLbGuardAllowHosts`；`GXWebClientConfig` 构建期 WARN `Invalid lb-guard-allow-hosts entries ignored`；新增 2 条用例（端口条目生效、非法条目被忽略且告警），并同步手 manual/config-contract 表述 per spec 边界用例（allow-hosts 含端口字面量）(partial)
- [X] T058 修正 `GXWebClientProperties` 类级 Javadoc：原"the eight keys added by this release"与实际不符，补充说明 8 键 + 1 个 `maple.framework.enable.webclient-lb-guard` 统一开关及 19 条 metadata 计数不变式 per FR-024 (partial)
- [X] T059 同步统一开关的单一事实来源：在 `spec.md` Clarifications 复审条目中改为"正式开关 `maple.framework.enable.webclient-lb-guard` 优先、`lb-guard-enabled` 为兼容别名"，并保持手册/AGENTS/contract 表述一致 per spec Clarifications（双入口）(unrequested)

---

## Phase 12: Convergence

> 由第三轮 `/speckit.converge` 追加。仅剩证据标注一致性问题；其余 26 FR / 8 SC / 21 验收场景均已实现并有 70 条用例覆盖。

- [X] T060 修正 F-10 的"旧代码失败证据"标注与口径：`quickstart.md` 证据表仍写"旧代码：无直连出口；手工路径泄漏池"（推演），而 `audit-report.md §7` 已将 F-10 归入"新增能力"；需在 HEAD `90fbb137` 工作树补一条反射探针实测"旧代码不存在 `webClientDirectBuilder` 方法"（`NoSuchMethodException`）作为该项的实测证据，并把"手工路径泄漏池"明确标注为源码核对结论（非实测），使 SC-005 的证据分类在两张表中一致 per SC-005 / FR-020 (partial)
