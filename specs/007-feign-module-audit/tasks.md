# Tasks: leaf-base-feign 逻辑正确性、线程安全、性能与契约一致性审查

**Input**: Design documents from `specs/007-feign-module-audit/`

**Prerequisites**: plan.md（技术栈与结构）、spec.md（6 个用户故事与 21 条 FR）、research.md（D-01~D-12）、data-model.md（矩阵与模型）、contracts/（4 份契约）、quickstart.md（验证命令）

**Tests**: 本特性按 TDD 交付（`AGENTS.md` 第 63 行、spec FR-014 明确要求"旧代码失败、新代码通过"的回归用例），因此每个用户故事都包含"先写失败用例 → 再实现"的任务序列。

**Organization**: 任务按用户故事分组，每个故事可独立实现、独立验证、独立交付。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与同阶段其它任务并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1~US6），仅用户故事阶段使用
- 所有任务均包含明确文件路径

## Path Conventions

- 生产代码：`leaf-base-feign/src/main/java/cn/maple/feign/`
- 测试代码：`leaf-base-feign/src/test/java/cn/maple/feign/`
- 资源：`leaf-base-feign/src/main/resources/META-INF/`
- 已授权跨模块：`config/{dev,test,pre,prod}/app-environment.yml`
- 审查产物：`specs/007-feign-module-audit/audit-report.md`

---

## Phase 1: Setup

**Purpose**: 建立审查报告载体与不可动摇的测试基线

- [X] T001 创建 `specs/007-feign-module-audit/audit-report.md` 骨架，包含章节：审查范围、证据索引（E-01~E-22 表头）、发现清单（F-01~F-16 表头，含 id/severity/dimension/location/trigger/impact/evidence/reproduction/regressionDesign/status 列）、四张验证矩阵（头透传 / 错误语义 / 装配 / 配置键）、契约对齐、结论与风险排序
- [X] T002 在 `specs/007-feign-module-audit/audit-report.md` 的"测试基线"章节记录 `mvn -pl leaf-base-feign test` 的实际输出（期望 `Tests run: 25, Failures: 0, Errors: 0`、`BUILD SUCCESS`），并附命令、执行日期与 JDK 版本

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有用户故事共用的证据与测试基础设施

**⚠️ CRITICAL**: 本阶段完成前不得开始任何用户故事的实现任务

- [X] T003 逐条复核 `specs/007-feign-module-audit/spec.md` 中 E-01~E-22 的代码位置（`leaf-base-feign/src/main/java/cn/maple/feign/` 全部 15 个文件 + 5 个测试类 + `config/*/app-environment.yml`），把实际行号回填到 `audit-report.md` 证据索引，行号不一致的条目必须改写为准确区间
- [X] T004 [P] 新增进程内 HTTP 桩基类 `leaf-base-feign/src/test/java/cn/maple/feign/support/GXFeignTestHttpServer.java`：基于 JDK `com.sun.net.httpserver.HttpServer`，支持按路径注册"状态码 + 响应体 + Content-Type"处理器、记录每次请求的 method/headers/body 与命中次数、随机空闲端口、`AutoCloseable` 释放
- [X] T005 [P] 新增测试客户端 fixture `leaf-base-feign/src/test/java/cn/maple/feign/support/GXFeignTestClients.java`：定义 `@FeignClient(url = 动态占位)` 的 GET/POST 接口、带 `@GXFeignHeader` 的接口、带 `fallback`/`fallbackFactory` 的接口，供 US2/US3 复用

---

## Phase 3: User Story 1 - 审查结论可复核、可构造故障现场 (Priority: P1) 🎯 MVP

**Goal**: 让每条结论都能跳到代码位置，并能用进程内服务端构造出同样的现象（F-01~F-03 的故障现场在本故事闭环，F-04~F-06 的现场由 US3/US4 产出后回填）。

**Independent Test**: 随机抽取报告中的一条结论，按给出的位置与命令复现，观察到的现象与结论一致；三条 F-01~F-03 结论必须能各自构造出故障现场。

### Implementation for User Story 1

- [X] T006 [P] [US1] 判定 `leaf-base-feign/src/main/java/cn/maple/feign/annotation/GXFeignHeader.java`、`annotation/package-info.java`、`service/GXFeignService.java`、`service/package-info.java`，在 `audit-report.md` 为每个文件给出"无缺陷 / 风险 / 缺陷 / 未覆盖原因"判定与一句话依据
- [X] T007 [US1] 判定 `leaf-base-feign/src/main/java/cn/maple/feign/codec/GXFeignCustomErrorDecoder.java`、`codec/package-info.java`、`interceptor/GXFeignRequestInterceptor.java`、`interceptor/package-info.java`，按 F-04、F-07、F-08、F-10、F-15 逐步对照并写入判定
- [X] T008 [US1] 判定 `leaf-base-feign/src/main/java/cn/maple/feign/aspect/GXFeignAuthTokenAspect.java`、`aspect/package-info.java`、`config/GXFeignConfig.java`、`config/GXCircuitBreakerConfig.java`、`config/GXFeignFallbackConfig.java`、`config/package-info.java`、`properties/GXCircuitBreakerProperties.java`、`properties/package-info.java`，按 F-03、F-05、F-09、F-11、F-16 逐步对照并写入判定
- [X] T009 [US1] 判定 `leaf-base-feign/src/test/java/cn/maple/feign/` 下 5 个测试类（`codec/GXFeignCustomErrorDecoderTest`、`config/GXCircuitBreakerConfigTest`、`config/GXFeignCircuitBreakerFallbackTest`、`config/GXFeignConfigTest`、`interceptor/GXFeignRequestInterceptorTest`），逐条列出已覆盖场景与缺口（切面零覆盖、错误码矩阵缺失、开关矩阵缺失）
- [X] T010 [US1] 为 F-01/F-02/F-03 构造故障现场并写入 `audit-report.md`：① 无 `GXFeignService` Bean 时发起调用，记录 warn 原文与缺失的 `X-Auth-Token`；② 有 Bean 但缺 `maple.framework.web.feign.token` 时记录异常类型与 code，证明调用在发出前失败；③ `@GXHttpInvokerAuthToken`（默认值 `feign`）标注方法在无 Bean 时抛 `GXBusinessException`，记录消息全文
- [X] T011 [US1] 汇总 `audit-report.md` 的"审查结论摘要"表（四维度 × 判定），逐条与 `spec.md` 的同名摘要核对，确保无相互矛盾表述；F-04~F-06 的结论行先标记 `PENDING-MEASUREMENT`，待 T027/T036 回填

**Checkpoint**: US1 完成后即可对外交付"可信、可复现"的审查结论（F-01~F-03 完整，F-04~F-06 待实测回填）。

---

## Phase 4: User Story 2 - 出站请求头透传矩阵可验证 (Priority: P1)

**Goal**: 5 类头 × 4 种上下文组合全部有唯一定性；顺带修掉 F-07（响应类头写入请求）、F-08（traceId 不回写）、F-10（注解头静默跳过）、F-15（每调用固定开销）。

**Independent Test**: 运行 `GXFeignRequestInterceptorTest` 与 `GXFeignHttpRoundTripTest`，20 个矩阵格子的"是否出现 / 值来源 / 是否重复"与服务端记录一致。

### Tests for User Story 2（先写，必须失败）

- [X] T012 [P] [US2] 在 `leaf-base-feign/src/test/java/cn/maple/feign/interceptor/GXFeignRequestInterceptorTest.java` 新增头透传矩阵用例：`headerCategory(5) × context(4)` 共 20 格，断言 `present`/`valueSource(REQUEST|GENERATED|CONFIG|NONE)`/`duplicated`；同时新增"六个响应类安全头不存在"与"显式 `User-Agent` 不被覆盖"的断言
- [X] T013 [US2] 在 `leaf-base-feign/src/test/java/cn/maple/feign/interceptor/GXFeignRequestInterceptorTest.java` 新增 token 开关用例：`maple.framework.feign.auth-token.enabled` 默认 `true` 且缺配置时抛 `GXBusinessException`；置 `false` 时不抛异常、跳过注入、warn 一条（含缺失键名与 methodKey）；无 Bean 时仍 warn 且 traceId 正常
- [X] T014 [US2] 在 `leaf-base-feign/src/test/java/cn/maple/feign/interceptor/GXFeignRequestInterceptorTest.java` 新增 traceId 上下文用例：新生成分支调用后 MDC 中 traceId 恢复为调用前值（原为空则仍为空），请求头携带生成的 id；无 `HttpServletRequest` 时不抛异常

### Implementation for User Story 2

- [X] T015 [US2] 在 `leaf-base-feign/src/main/java/cn/maple/feign/interceptor/GXFeignRequestInterceptor.java` 实现 D-01：`appendCommonHeaders` 只保留 `X-Request-Source`（`spring.application.name` 非空时）与 `X-Request-Start-Time`；`User-Agent` 仅在 `requestTemplate.headers()` 不含该头时补 `Maple-Leaf-Feign/1.0 (<os>)`；删除 `X-Content-Type-Options`/`X-Frame-Options`/`X-XSS-Protection`/`Cache-Control`/`Pragma`/`Expires`
- [X] T016 [US2] 在 `leaf-base-feign/src/main/java/cn/maple/feign/interceptor/GXFeignRequestInterceptor.java` 实现 D-02：新增读取 `maple.framework.feign.auth-token.enabled`（默认 `true`）；`false` 时跳过 `generateHttpAuthToken()`、不抛异常、输出含缺失键名候选与 `methodKey` 的 warn；`true` 时保持 fail-fast 语义不变
- [X] T017 [US2] 在 `leaf-base-feign/src/main/java/cn/maple/feign/interceptor/GXFeignRequestInterceptor.java` 实现 D-06：`propagateTraceId` 的新生成分支执行 `putTraceId` → 写头 → `finally` 中 `restoreTraceId(previous)`，`previous` 取自 `GXTraceIdContextUtils.getNullableTraceId()`；`GXFeignService#getTraceId` 保持纯函数
- [X] T018 [US2] 在 `leaf-base-feign/src/main/java/cn/maple/feign/interceptor/GXFeignRequestInterceptor.java` 实现 D-07：用 `ConcurrentHashMap<Method, GXFeignHeader>`（含"无注解"空标记）缓存 `@GXFeignHeader` 查找；`spring.application.name` 用 `volatile` 字段惰性解析一次；`os.name` 与 `User-Agent` 模板在类初始化时构造；无 Servlet 上下文的跳过分支补一条 debug 日志
- [X] T019 [US2] 在 `leaf-base-feign/src/test/java/cn/maple/feign/integration/GXFeignHttpRoundTripTest.java` 完成头部分的真实往返验证：用 T004 的桩服务端记录入站请求头，断言与 T012 矩阵一致，且用例不依赖外网与固定端口
- [X] T020 [US2] 把 20 格矩阵结果回填 `specs/007-feign-module-audit/audit-report.md` 的"请求头透传矩阵"，并把 F-07/F-08/F-10/F-15 的状态更新为 `CONFIRMED` 或 `CONTRACT`

**Checkpoint**: US2 独立可验证——请求头语义、敏感头阻断、traceId 上下文三项均有通过用例支撑。

---

## Phase 5: User Story 3 - 错误语义与熔断重试可验证 (Priority: P1)

**Goal**: 远端各状态码在本地得到"异常类型 + code + msg 来源"的唯一定性；熔断重试的实际请求次数可量化。

**Independent Test**: `GXFeignCustomErrorDecoderTest` 与 `GXCircuitBreakerConfigTest` 全绿，桩服务端记录的请求次数与断言一致。

### Tests for User Story 3（先写，必须失败）

- [X] T021 [P] [US3] 在 `leaf-base-feign/src/test/java/cn/maple/feign/codec/GXFeignCustomErrorDecoderTest.java` 新增 13 个状态码矩阵用例（400/401/403/404/409/422/429/500/503/504 + 418/204/301），断言"异常类型 + `code` + 消息是否含 body"三元组，其中 401/403 必须断言消息**不含**响应体
- [X] T022 [US3] 在 `leaf-base-feign/src/test/java/cn/maple/feign/codec/GXFeignCustomErrorDecoderTest.java` 新增远端业务响应解析用例：400 + `{"code":1001,"msg":"库存不足","data":{}}` → `code=1001`、消息含"库存不足"；并覆盖四类降级（body 空 / 非 JSON / 9000 字符 / 读取中断）
- [X] T023 [P] [US3] 在 `leaf-base-feign/src/test/java/cn/maple/feign/config/GXCircuitBreakerConfigTest.java` 新增重试用例：默认 `max-retries` 为 `0` 时桩服务端只收到 1 次请求；`max-retries=2` + 远端 500 时收到 3 次；`max-retries=2` + 远端 400 时只收到 1 次（业务异常被排除）；`max-retries=-1` 启动失败
- [X] T024 [P] [US3] 在 `leaf-base-feign/src/test/java/cn/maple/feign/integration/GXFeignHttpRoundTripTest.java` 新增"配置 fallback + 远端 400"用例，断言调用方拿到 fallback 返回值而非异常，用于证明 fallback 会吞掉业务异常

### Implementation for User Story 3

- [X] T025 [US3] 在 `leaf-base-feign/src/main/java/cn/maple/feign/codec/GXFeignCustomErrorDecoder.java` 实现 D-03：`code` 取远端 HTTP 状态码；解析 `{"code","msg","data"}` 形态覆盖 code/msg/data（`code` 缺失或 0 时回退状态码）；解析失败降级为现有固定文案；新增 409/422/429 进入自定义处理；保留 8192 字符上限与 `...[truncated]`；401/403 不把 body 写入消息
- [X] T026 [US3] 在 `leaf-base-feign/src/main/java/cn/maple/feign/properties/GXCircuitBreakerProperties.java` 把 `maxRetries` 默认值改为 `0` 并保留 `<0` 校验；在 `leaf-base-feign/src/main/java/cn/maple/feign/config/GXCircuitBreakerConfig.java` 实现 D-04：`maxRetries > 0` 时用 `RetryPolicy.builder().maxRetries(...).delay(...).multiplier(...).predicate(t -> !(t instanceof GXBusinessException)).build()`
- [X] T027 [US3] 把错误语义矩阵（13 个状态码 × 解码器开关）与重试实测数字（GET/POST 各一组请求次数与总耗时）回填 `audit-report.md`，并把 F-04/F-05 状态从 `UNVERIFIED_HYPOTHESIS` 更新为 `CONFIRMED` 或 `CONTRACT`

**Checkpoint**: US3 独立可验证——错误码不再压平、业务异常不重试、默认只发一次请求。

---

## Phase 6: User Story 4 - 装配与配置键一致性可验证 (Priority: P2)

**Goal**: 五个+两个开关的实际效果、`default.mapleFeignFallback` 规格的最终胜负、6 类配置键的生效情况全部有唯一定性；顺带修掉 F-06（配置键空转）、F-09（切面零覆盖）、F-11（Encoder 抢占）、F-12（元数据缺失）、F-16（静态缓存无界）。

**Independent Test**: 分别以"仅框架自动配置"与"`@EnableFeignClients` + Pageable + 自定义 Bean"两种形态启动上下文，断言 Bean 集合、`Encoder` 类型与属性值。

### Tests for User Story 4（先写，必须失败）

- [X] T028 [P] [US4] 在 `leaf-base-feign/src/test/java/cn/maple/feign/config/GXFeignConfigTest.java` 扩展开关矩阵：`logger-level` / `error-decoder` / `request-interceptor` / `auth-token-aspect` / `fallback-config` 各自置 `false` 时对应 Bean 不存在，默认值分别为 `true/false/true/true/true`
- [X] T029 [US4] 在 `leaf-base-feign/src/test/java/cn/maple/feign/config/GXFeignConfigTest.java` 新增装配用例：`@EnableFeignClients` + Spring Data `Pageable` 在类路径时最终 `Encoder` 为 `PageableSpringEncoder`；用户自定义 `Encoder` 存在时框架不覆盖；`fallback-config.enabled=false` 时 `default.mapleFeignFallback.FeignClientSpecification` 不存在
- [X] T030 [P] [US4] 在 `leaf-base-feign/src/test/java/cn/maple/feign/config/GXCircuitBreakerConfigTest.java` 新增配置键用例：`maple.framework.feign.circuit-breaker.max-retries=1` 生效；旧写法 `maple.feign.circuit-breaker.max-retries=1` **不生效**（属性保持默认值 `0`）
- [X] T031 [P] [US4] 新增 `leaf-base-feign/src/test/java/cn/maple/feign/aspect/GXFeignAuthTokenAspectTest.java`，覆盖 6 类场景：方法级注解生效、类级注解生效、`value` 不等于 `feign` 时跳过、无 `GXFeignService` Bean 抛 `GXBusinessException`、token 校验失败抛 `GXFeignAuthTokenException`、JDK 代理 + 最具体方法解析；并新增一条"接口上的类级注解是否被 `@within` 命中"的实测用例（允许结论为"不命中"，但必须有断言与结论记录）

### Implementation for User Story 4

- [X] T032 [US4] 在 `leaf-base-feign/src/main/java/cn/maple/feign/aspect/GXFeignAuthTokenAspect.java` 实现 D-09：`METHOD_SIGNATURE_CACHE` 键改为 `MethodSignature.getMethod()`，值仍为 `声明类型名.方法名`，并在 Javadoc 中写明缓存规模上界为"被注解方法数量"
- [X] T033 [US4] 在 `leaf-base-feign/src/main/java/cn/maple/feign/config/GXFeignFallbackConfig.java` 实现 D-05：删除 `Encoder` 声明，只保留 `FeignHttpMessageConverters` 兜底；在 `leaf-base-feign/src/main/java/cn/maple/feign/config/GXFeignConfig.java` 为 `default.mapleFeignFallback.FeignClientSpecification` 增加 `@ConditionalOnProperty(prefix="maple.framework.feign.fallback-config", name="enabled", havingValue="true", matchIfMissing=true)`
- [X] T034 [P] [US4] 把 `config/dev/app-environment.yml`、`config/test/app-environment.yml`、`config/pre/app-environment.yml`、`config/prod/app-environment.yml` 中的 `maple.feign.circuit-breaker.*` 改为 `maple.framework.feign.circuit-breaker.*`（含 `enabled`、`max-retries`、`backoff`、`backoff-multiplier`、`open-timeout`、`reset-timeout`），`max-retries` 写 `0`；不得改动同文件其它键
- [X] T035 [P] [US4] 新增 `leaf-base-feign/src/main/resources/META-INF/additional-spring-configuration-metadata.json`，收录 7 个开关与 6 个熔断参数（`defaultValue` 必须与代码一致，`error-decoder.enabled` 为 `false`，`circuit-breaker.max-retries` 为 `0`），描述使用 ASCII
- [X] T036 [US4] 把装配矩阵与配置键矩阵回填 `audit-report.md`，并把 F-06/F-09/F-11/F-12/F-16 状态更新；`@within` 命中结论同时写入 `specs/007-feign-module-audit/contracts/auth-spi-contract.md` 的"已知边界"

**Checkpoint**: US4 独立可验证——开关与配置键"说到的都生效，没生效的都有结论"。

---

## Phase 7: User Story 5 - 性能与每调用开销定量化 (Priority: P2)

**Goal**: 给出可复算口径（每调用反射/环境查询/头写入/日志条数 + `apply()` 分位），而非"变快了"。

**Independent Test**: 重跑基准两次，分位与 ops/s 偏差 ≤ 30%，固定开销清单每项可对应到代码位置。

### Implementation for User Story 5

- [X] T037 [US5] 在 `leaf-base-feign/src/test/java/cn/maple/feign/interceptor/GXFeignRequestInterceptorTest.java` 新增 `applyThroughputBaseline` 用例：5 万次预热 + ≥10 万次 `apply()`，输出 ops/s 与 p50/p95/p99；不引入 JMH，用 `System.nanoTime()` 采样
- [X] T038 [US5] 统计并记录每调用固定开销：头写入次数（按头类别）、`GXCommonUtils.getEnvironmentValue` 调用次数（期望 0，除首次惰性解析）、`System.getProperty` 次数（期望 0）、debug 日志条数、注解查找缓存命中率，写入 `audit-report.md` 的"性能口径"章节
- [X] T039 [US5] 评估并记录 D-01 的缓存影响：移除 `Cache-Control`/`Pragma`/`Expires` 后幂等 GET 是否恢复可被代理缓存（给出机制说明与改动前后请求头对比），把结论写入 `specs/007-feign-module-audit/audit-report.md` 的"性能口径"章节，并记录两次基准重跑的偏差值以证明 ≤30%

**Checkpoint**: US5 独立可验证——性能结论以口径与样本呈现，不依赖绝对数值门槛。

---

## Phase 8: User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

**Goal**: 每条发现具备八项要素、明确"缺陷 / 既定契约"边界，并按风险排序给出修复批次。

**Independent Test**: 抽取任一发现，八项要素齐全；既定契约清单中的条目在缺陷统计中出现次数为 0。

### Implementation for User Story 6

- [X] T040 [P] [US6] 逐条校验 `audit-report.md` 中 F-01~F-16 的八项要素（编号、级别、维度、位置、触发条件、影响面、证据、回归设计），缺项补齐后重新校验，直至 0 缺项
- [X] T041 [US6] 在 `specs/007-feign-module-audit/audit-report.md` 单列"既定契约"清单（错误解码器默认关闭、`@GXFeignHeader` 仅方法级、切面 fail-closed、fallback 吞异常属依赖库行为），确认这些条目未计入缺陷统计
- [X] T042 [US6] 在 `specs/007-feign-module-audit/audit-report.md` 生成风险排序表（影响面 × 触发概率 × 可观测性）与修复批次建议，并在报告中标注每批次的验证命令，供后续 `/speckit.implement` 直接排期

**Checkpoint**: US6 完成后报告可直接驱动实施排期，无需补充调研。

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T043 [P] 新增 `docs/leaf-base-feign开发手册.md`：目录职责、头透传规则、认证开关矩阵、错误码映射、熔断重试语义、fallback 交互注意事项、6+7 个配置键表、故障排查命令
- [X] T044 [P] 更新 `leaf-base-feign/AGENTS.md`：把"统一注册错误解码器"改为"错误解码器需显式开启"，补充 `auth-token.enabled` / `fallback-config.enabled` 两个新开关与 `max-retries` 默认 `0`、出站头收敛说明
- [X] T045 [P] 在 `leaf-base-feign/src/main/java/cn/maple/feign/service/GXFeignService.java` 为 `getPlatform()` 补 `@Nullable` 并完善 Javadoc 的空值/异常约定（不改任何签名与默认实现逻辑）
- [X] T046 [P] 在 `leaf-base-feign/src/main/java/cn/maple/feign/annotation/GXFeignHeader.java` 的 Javadoc 中明确"仅方法级、仅当前 Servlet 请求、无上下文静默跳过"，并引用 `contracts/header-propagation-contract.md`
- [X] T047 执行完成门槛：`mvn -pl leaf-base-feign -am test` 全绿、`mvn -pl leaf-base-feign -am clean install -DskipTests` 零 ErrorProne/NullAway 错误，记录两条命令的实际输出到 `audit-report.md`
- [X] T048 最终核对：`audit-report.md` 与 `spec.md` 摘要逐条一致（FR-013），矩阵数字与用例断言一一对应（SC-003~SC-007），`mvn -pl leaf-base-feign dependency:tree` 确认无新增第三方依赖（SC-009）

---

## Dependencies

### Phase Dependencies

- **Phase 1 (Setup)** → 无依赖
- **Phase 2 (Foundational)** → 依赖 Phase 1；**阻塞所有用户故事**
- **US1 (P1)** → 依赖 Phase 2；F-04~F-06 的结论行依赖 US3（T027）与 US4（T036）回填
- **US2 (P1)** / **US3 (P1)** / **US4 (P2)** / **US5 (P2)** → 均只依赖 Phase 2，彼此可并行（改动文件基本不重叠：`interceptor` / `codec`+`properties` / `config`+`aspect`+模板 / 测试基准）
- **US6 (P3)** → 依赖 US1~US5 全部完成（需要全量发现与实测数字）
- **Phase 9 (Polish)** → 依赖 US1~US6

### Within Each User Story

- 测试任务（T012~T014、T021~T024、T028~T031）必须先写并确认为**失败**
- 同一文件内的实现任务必须串行（`interceptor` 的 T015→T016→T017→T018；`codec` 的 T025 与 `properties`/`config` 的 T026 分属不同文件，可在测试转红后并行）
- 所有写入 `audit-report.md` 的任务（T001/T002/T006~T011/T020/T027/T036/T038/T040~T042/T047/T048）必须串行，避免同一文件并发覆盖
- 真实往返与报告回填在实现之后

### Story Completion Order

```
Phase 2 ──┬── US1 (F-01~F-03) ─────────────┐
          ├── US2 (头语义) ────────────────┤
          ├── US3 (错误/重试) ─────────────┼── US6 ── Polish
          ├── US4 (装配/配置) ─────────────┤
          └── US5 (性能口径) ──────────────┘
```

---

## Parallel Example: User Story 2

```bash
# 测试阶段三个用例同属 interceptor 测试文件，串行编写（T012 → T013 → T014），全部确认失败
Task: "T012 头矩阵 5×4 用例 in leaf-base-feign/src/test/java/cn/maple/feign/interceptor/GXFeignRequestInterceptorTest.java"

# 实现阶段同样串行（同一生产文件），顺序不可颠倒：公共头收敛 → token 开关 → traceId 回写 → 缓存
Task: "T015 D-01 公共头收敛 in .../interceptor/GXFeignRequestInterceptor.java"
```

## Parallel Example: User Story 3

```bash
# 三个测试任务可并行（codec / config / integration 不同文件）
Task: "T021 13 状态码矩阵 in .../codec/GXFeignCustomErrorDecoderTest.java"
Task: "T023 重试次数用例 in .../config/GXCircuitBreakerConfigTest.java"
Task: "T024 fallback 吞异常用例 in .../integration/GXFeignHttpRoundTripTest.java"

# 实现阶段可并行：codec 与 properties/config 是两个文件
Task: "T025 解码器实现 in .../codec/GXFeignCustomErrorDecoder.java"
Task: "T026 重试默认值与 predicate in .../properties/GXCircuitBreakerProperties.java + .../config/GXCircuitBreakerConfig.java"
```

## Parallel Example: User Story 4

```bash
# 模板与元数据互不相关，可并行
Task: "T034 四个环境模板改键 in config/{dev,test,pre,prod}/app-environment.yml"
Task: "T035 新增 metadata in leaf-base-feign/src/main/resources/META-INF/additional-spring-configuration-metadata.json"
```

---

## Implementation Strategy

### MVP First（User Story 1）

1. 完成 Phase 1 + Phase 2
2. 完成 US1（T006~T011）
3. **STOP and VALIDATE**：抽取任一条结论按报告复现
4. 即可交付"审查结论 + F-01~F-03 故障现场"

### Incremental Delivery

1. Setup + Foundational → 证据与桩就绪
2. + US1 → 结论可复核（MVP）
3. + US2 → 请求头语义可验证（含 F-07/F-08 修复）
4. + US3 → 错误语义与重试可验证（含 F-04/F-05 修复）
5. + US4 → 装配与配置键收敛（含 F-06/F-11/F-12 修复）
6. + US5 → 性能口径定量化
7. + US6 → 报告可直接排期
8. Polish → 手册/AGENTS/空值注解/全量门禁

### 关键风险控制

- `max-retries` 默认 `3 → 0` 属受控行为变更：T026 实现时必须同时更新 T034 模板与 T044 文档，避免"代码与配置各说一套"
- 头收敛（T015）会改变既有断言：T012 必须先红后绿，禁止先改实现再补测试
- `@within` 命中结论必须实测（T031），不得凭 AOP 理论推断

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- 每个用户故事的 Checkpoint 都可独立验收，不得跨故事合并验收
- 测试必须先失败再实现（`AGENTS.md` 第 63 行）
- 所有日志文案使用 ASCII；异常消息面向用户可中文
- 提交信息遵循 `类型(范围): 简述`，范围为 `feign`

---

## Phase 10: Convergence

**Purpose**: 收敛审查阶段遗留的"部分满足"缺口（2026-10-22 收敛扫描）

- [X] T049 在 `leaf-base-feign/src/test/java/cn/maple/feign/interceptor/GXFeignRequestInterceptorTest.java` 为全部 4 种上下文补齐"重复性"断言：对 auth token / platform / traceId / 注解头 / 公共头这 5 类各执行两次 `apply()` 并断言每个头 `hasSize(1)`，使 `audit-report.md` 声明的 20 格矩阵中 `duplicated=false` 全部有测试支撑 per FR-004 (partial)
- [X] T050 在 `specs/007-feign-module-audit/audit-report.md` 新增"空值契约清单"章节，逐个判定 `GXFeignService` 的 9 个公开方法（`generateHttpAuthToken()`、`generateHttpAuthToken(String,int)`、`checkTokenValidity()`、`getAuthTokenSecret()`、`getSensitiveHeaderFields()`、`getPlatform()`、`getTraceId()`、`generateHmac(Object,String)`、`checkHmac(String,String,Object)`）的可空返回值、是否已标 `@Nullable`、以及包级 `@NullUnmarked` 的调整结论 per FR-019 (partial)
- [X] T051 新增用例 `leaf-base-feign/src/test/java/cn/maple/feign/config/GXFeignConfigTemplateTest.java`，逐个读取 `config/{dev,test,pre,prod}/app-environment.yml` 并断言：存在 `maple.framework.feign.circuit-breaker.` 段、不存在 `maple.feign.` 残留、`max-retries: 0`；随后把 4 环境 × 6 键的判定结果回填 `audit-report.md` §5.4 per SC-006 (partial)
- [X] T052 把 `specs/007-feign-module-audit/contracts/auth-spi-contract.md` §4 第 2 条由"本次以实测结论为准"改为已实测结论（接口上的类级 `@GXHttpInvokerAuthToken` 不被 `@within` 命中，仅方法级与实现类级可靠），并引用 `GXFeignAuthTokenAspectTest#interfaceLevelAnnotationIsNotInterceptedOnImplementation` per FR-013 (partial)
- [X] T053 在 `docs/leaf-base-feign开发手册.md` 增加"空值契约"小节：说明包级当前为 `@NullUnmarked`、`getPlatform()` 已标 `@Nullable`、其余方法不返回 `null` 的判定，以及后续逐步收敛到 `@NullMarked` 的计划 per plan:D-12 (partial)

**验收方式**：`mvn -pl leaf-base-feign test` 全绿；`audit-report.md` 与 `contracts/auth-spi-contract.md` 无"待实测/以实测为准"类未决表述；手册包含空值契约小节。
---

## Phase 11: Convergence

**Purpose**: 第二轮收敛扫描（2026-10-22）：补齐 fallback 与业务异常的实测证据、注解头开销的线性口径

- [X] T054 在 `leaf-base-feign/src/test/java/cn/maple/feign/config/GXFeignCircuitBreakerFallbackTest.java` 新增用例：注册 `@FeignClient(name = "fallback-business", url = "${gx.feign.test.url}", fallback = ...)` 并让桩服务端返回 400 + 业务 JSON，断言调用方拿到 fallback 返回值（异常被吞）、桩服务端只收到 1 次请求；随后把结论回填 `specs/007-feign-module-audit/audit-report.md` §5.2 与 §6 per US3/AC4 (missing)
- [X] T055 在 `leaf-base-feign/src/test/java/cn/maple/feign/interceptor/GXFeignRequestInterceptorTest.java` 新增用例：用统计 `getHeaders` 调用次数的 `HttpServletRequest` 包装，对比声明 1 个与 5 个 `@GXFeignHeader` 头时 `getHeaders` 的实际调用次数，断言与有效声明头数量成正比（被阻断/源缺失的头不计入），并把线性口径写入 `specs/007-feign-module-audit/audit-report.md` §7 per US5/AC2 (partial)

**验收方式**：`mvn -pl leaf-base-feign test` 全绿；报告 §5.2 的 fallback 交互与 §7 的注解头开销均有实测用例引用。
---

## Phase 12: Convergence

**Purpose**: 第三轮收敛扫描（2026-10-22）：补齐 4 环境 × 6 配置键的逐项判定（本节任务在本次 implement 中已随实现完成）

- [X] T056 扩展 `leaf-base-feign/src/test/java/cn/maple/feign/config/GXFeignConfigTemplateTest.java`，对 `config/{dev,test,pre,prod}/app-environment.yml` 的 `circuit-breaker` 块解析出 6 个键并与代码默认值逐项比对（`enabled` / `max-retries` / `backoff` / `backoff-multiplier` / `open-timeout` / `reset-timeout`），随后把 `specs/007-feign-module-audit/audit-report.md` §5.4 改为 4 环境 × 6 键判定表 per SC-006 (partial)

**验收方式**：`mvn -pl leaf-base-feign test` 全绿（76 执行 / 0 失败）；报告 §5.4 覆盖 24 个组合。