# Phase 0 研究结论：leaf-base-feign 审查与修复设计

**Feature**: `007-feign-module-audit` | **Date**: 2026-10-22

本文件回答 plan.md 中 Technical Context 的全部未知项，并把 spec 的 12 项澄清落成可实现的技术决策。所有"已核实"结论均来自本仓库源码或 `F:\repository` 中的依赖源码（`spring-cloud-openfeign-core 5.0.3-sources`、`spring-cloud-circuitbreaker-framework-retry 5.0.3-sources`、`spring-core 7.0.9` 的 `javap` 签名）。

---

## D-01 出站公共头收敛

- **Decision**：`GXFeignRequestInterceptor.appendCommonHeaders` 只保留 `X-Request-Source`（`spring.application.name` 非空时）与 `X-Request-Start-Time`；`User-Agent` 仅在 `RequestTemplate` 尚未包含该头时补 `Maple-Leaf-Feign/1.0 (<os>)`；删除 `X-Content-Type-Options`、`X-Frame-Options`、`X-XSS-Protection`、`Cache-Control`、`Pragma`、`Expires` 六个写入项。
- **Rationale**：这六个是 HTTP **响应**安全头，写入请求不产生任何防护效果，却会让上游网关/代理把请求视为不可缓存（`Cache-Control: no-cache`），在高频幂等 GET 上放大回源压力；无条件覆盖 `User-Agent` 会让服务端风控与灰度路由无法区分调用方（现状证据 E-10）。
- **Alternatives considered**：
  - 全部保留：与 F-07 结论冲突，且无法解释"为什么请求要带 `X-Frame-Options`"。
  - 做成逐头开关：7 个开关只为兼容错误语义，属于过度设计（附加约束 4）。
- **Impact**：`ServiceHeadersAndCommonHeadersAreIdempotent` 现有断言（`X-Frame-Options` 存在）需同步改为"不存在"；属于可观测的行为变更，写入 PR 与手册。
- **Verification**：头矩阵用例断言六个头为空、`User-Agent` 在显式设置时保持调用方值。

## D-02 认证 token 传播开关

- **Decision**：新增 `maple.framework.feign.auth-token.enabled`（`@ConditionalOnProperty` 不作用于 Bean，而在 `propagateServiceHeaders` 内读取，默认 `true`）。`true` 时保持现状 fail-fast（`generateHttpAuthToken` 抛 `GXBusinessException` 直接冒泡）；`false` 时跳过生成、不抛异常，输出一条 warn（含缺失键名候选与 `methodKey`）后继续。
- **Rationale**：spec Q1 已定"保持 fail-fast 为默认"，同时需要一个不牵连 platform/traceId 传播的独立出口；整体关闭 `request-interceptor.enabled` 会连 traceId 一起丢掉。
- **Alternatives considered**：
  - 在 `GXFeignService` 默认方法内降级：会让所有调用方（含 aspect 校验路径）失去 fail-fast，影响面更大。
  - 捕获 `GXBusinessException` 后静默继续：等于默认 fail-open，与 Q1 冲突。
- **Impact**：拦截器新增一次 Environment 读取（缓存后为字段读取）；不改 `GXFeignService` 签名。
- **Verification**：用例覆盖 `enabled=true` 抛异常、`enabled=false` 继续且只 warn、无 Bean 时仍 warn 且 traceId 正常。

## D-03 错误解码语义保真

- **Decision**：`GXFeignCustomErrorDecoder` 改为三件事：① `GXBusinessException` 的 `code` 取远端 HTTP 状态码；② 当响应体可解析为 `GXResultUtils` 形态（`{"code":..,"msg":..,"data":..}`）时，用其 `code`/`msg`/`data` 覆盖；③ 解析失败或非 JSON 时降级为现有固定文案（body 读取上限 8192 与 `...[truncated]` 保留）。自定义处理状态码在原有 7 个基础上增加 `409`、`422`、`429`；其余仍回退 `ErrorDecoder.Default`。
- **Rationale**：现状把 400/401/403/404/500/503/504 全部映射为 `code=HTTP_INTERNAL_ERROR`（500），使上游无法区分"参数错"和"服务挂"，且 6 个分支读取 body 后丢弃；这与宪法 III 的"错误码必须来自统一枚举语义"直接冲突（E-05、E-06、E-15）。
- **Alternatives considered**：
  - 只改 `code` 不解析 body：远端业务提示仍丢失，验收过不了 SC-004。
  - 解析失败时把原始 body 拼进消息：可能把堆栈/内部路径回显给上游，违反宪法 III 的"不得回显"。
- **Impact**：异常消息与 code 变化属对外可见变更；PR 需写清"远端 400 现在得到 code=400 而非 500"的迁移说明。
- **Verification**：10 个状态码矩阵用例 + 空 body / 非 JSON / 超长 body / 读取 IO 异常四类降级分支。

## D-04 重试默认值与业务异常排除

- **Decision**：`GXCircuitBreakerProperties.maxRetries` 默认由 `3` 改为 `0`；显式配置 >0 时，`FrameworkRetryCircuitBreakerCustomizer` 用 `RetryPolicy.builder().predicate(t -> !(t instanceof GXBusinessException e) || e.getCode() >= 500)` 排除客户端业务错误（`code < 500`），保留 5xx 与传输异常的可重试性；`backoff` / `backoffMultiplier` 保留（无重试时不生效）。
- **Rationale**：已在依赖源码核实两点——`FeignClientsConfiguration.feignRetryer()` 返回 `Retryer.NEVER_RETRY`（OpenFeign 侧本就不重试），`FrameworkRetryCircuitBreaker.run` 用 `RetryTemplate` 对任意 `Throwable` 重试并最终 `fallback.apply(t)`。因此框架层默认 3 次重试是"额外叠加"的重试，对 POST/PUT/DELETE 构成重复写风险（E-07、E-08）。`RetryPolicy.$Builder`（spring-core 7.0.9）已核实提供 `includes`/`excludes`/`predicate`。
- **Alternatives considered**：
  - 保留 3 次但按 HTTP 方法过滤：`CircuitBreaker` 层拿不到 HTTP 方法，需要替换 `Feign` 客户端装饰链，改动面远超本模块边界。
  - 仅改默认值为 0、不加 predicate：显式开启后业务异常仍会被重试，隐患保留。
- **Impact**：默认可靠性行为变更（不再自动重试），属 spec Q5 已授权的受控变更；下游若依赖重试需显式配置。
- **Verification**：`maxRetries=0` 时断言服务端只收到 1 次；`maxRetries=2` 且远端返回 500 时断言收到 3 次；远端返回 400 且开启重试时断言只收到 1 次（业务异常被排除）。

## D-05 default 规格与 Spring Cloud 默认配置的关系（实测修正）

- **原假设**：`GXFeignFallbackConfig` 的 `Encoder` 与 `FeignClientsConfiguration` 重复，属可删除的冗余声明。
- **实测结论（2026-10-22）**：删除 `Encoder` 后 `GXFeignCircuitBreakerFallbackTest` 两个用例立即失败：`IllegalStateException: No bean found of type interface feign.codec.Encoder for fallback-client`。Spring Cloud 2025.1.3 中 `@EnableFeignClients` 注册的客户端不会自动获得 `FeignClientsConfiguration` 作为默认配置，`FeignClientFactory` 的 default 规格只有框架注册的 `default.mapleFeignFallback`，因此该规格是 `Encoder` 与 `FeignHttpMessageConverters` 的必要来源。
- **Decision**：保留 `Encoder` 与 `FeignHttpMessageConverters`；新增开关 `maple.framework.feign.fallback-config.enabled`（默认 `true`），允许由应用自行提供 `Encoder`；手册写明关闭开关的前提条件。
- **Alternatives considered**：
  - 删除整份规格：实测会让所有 Feign 客户端创建失败，不可行。
  - 依赖 `FeignClientsConfiguration` 自动生效：该配置在 2025.1.3 不会自动作用于 `@EnableFeignClients` 客户端。
  - 用 `@Order` 控制注册顺序：`@ConditionalOnMissingBean` 的评估顺序不保证，且这里不存在竞争方。
- **Impact**：装配行为与实施前一致；新增一个可关闭开关；F-11 由"缺陷"降级为"既定契约 + 文档补齐"。
- **Verification**：`fallbackConfigurationCanBeDisabledWhenUserProvidesEncoder`（关闭开关 + 用户 `Encoder` → 客户端仍可创建）、`feignClientReceivesEncoderFromFrameworkFallbackSpecification`（默认 → 子上下文中 `Encoder` 非空）。

## D-06 traceId 回写与恢复

- **Decision**：`GXFeignRequestInterceptor.propagateTraceId` 在"必须新生成 traceId"的分支里 `String previous = GXTraceIdContextUtils.getNullableTraceId(); putTraceId(traceId); try { 写入头 } finally { restoreTraceId(previous); }`；`GXFeignService.getTraceId()` 保持纯函数语义（不回写）。
- **Rationale**：现状生成后不回写，导致本进程该次调用的日志没有 traceId（F-08、E-11）；若不恢复，线程池复用会造成 trace 串号（宪法 V 的成对 push/poll 要求）。
- **Alternatives considered**：
  - 只在 `GXFeignService` 里回写：SPI 是用户可覆盖的纯函数，加副作用会让自定义实现语义不一致。
  - 只打日志不回写：仍无法让同线程后续日志带上 id。
- **Impact**：`apply()` 多一次 MDC put/restore（纳秒级）；无 Web 上下文时行为从"不回写"变为"临时回写并恢复"。
- **Verification**：用例断言调用后在 MDC 中的 traceId 恢复为原值（原为空则仍为空），且请求头携带生成的 id。

## D-07 每调用开销收敛

- **Decision**：① 用 `ConcurrentHashMap<Method, GXFeignHeader>` 缓存注解查找结果（含"无注解"的空对象标记）；② `spring.application.name` 用 `volatile` 字段惰性解析一次；③ `os.name` 在类初始化时读取一次；④ `User-Agent` 模板字符串预先构造。
- **Rationale**：现状每次调用都做 `AnnotationUtils.findAnnotation`（含元注解搜索）、多次 `GXCommonUtils.getEnvironmentValue`、`System.getProperty` 与 `String.format`（E-20）；这些值在应用生命周期内不变，缓存不引入可见性风险（字段 `volatile`/`static final`）。
- **Alternatives considered**：
  - 引入 Caffeine：为两个字符串引入缓存库，违反"不新增依赖"。
  - 在 `apply()` 中做无锁单次初始化：需处理并发初始化，`volatile` + 局部变量写法已足够。
- **Impact**：`RequestTemplate` 语义不变，头矩阵用例可锁定。
- **Verification**：基准用例报告 ops/s 与固定开销清单（每调用头写入次数、环境查询 0 次、日志 3 条 debug）。

## D-08 鉴权切面测试与 `@within` 命中

- **Decision**：新增 `GXFeignAuthTokenAspectTest`，覆盖：方法级注解、类级注解、`value` 不匹配跳过、`GXFeignService` 缺失抛 `GXBusinessException`、token 校验失败抛 `GXFeignAuthTokenException`、JDK 代理 + 最具体方法解析。"接口上的类级注解是否被 `@within` 命中"以 `ApplicationContextRunner` + `@EnableAspectJAutoProxy` 实测；**若不命中，不改 pointcut**，在手册中明确"类级注解仅对实现类生效"，并把该结论写入审查报告。
- **Rationale**：当前该切面零测试（E-12），而它决定"标注了注解的接口是否可用"；扩张 pointcut 属于契约变更，必须先有实测证据。
- **Alternatives considered**：
  - 直接加 `@target(...)` 让接口注解命中：未经实测扩张匹配面，可能让不该拦截的 Bean 被拦截。
- **Impact**：仅新增测试，生产代码最多调整缓存键（D-09）。
- **Verification**：6 个用例全部通过；实测结论回写 `audit-report.md` 与 `contracts/auth-spi-contract.md`。

## D-09 方法签名缓存收敛

- **Decision**：`METHOD_SIGNATURE_CACHE` 键由 `point.getSignature().toString()` 改为 `MethodSignature.getMethod()`（`Method` 的 `equals/hashCode` 稳定），值仍为 `声明类型名.方法名`。
- **Rationale**：`toString()` 包含参数类型串，同一方法在不同代理形态下可能产生多个键；`Method` 键与实际方法一一对应，缓存规模上界为应用中被注解的方法数量（有界），并在 Javadoc 中写明该前提。
- **Alternatives considered**：
  - 直接删除缓存：异常消息里的方法描述会产生字符串分配，但收益有限；且移除后每次异常都重新拼装，影响不大却丢掉"稳定诊断串"的特性。
- **Impact**：异常消息内容不变（仍是 `声明类型.方法名`）。
- **Verification**：重复触发同一方法断言缓存键数量不增长（反射读取静态字段或通过公开行为间接断言）。

## D-10 配置元数据与文档补齐

- **Decision**：新增 `src/main/resources/META-INF/additional-spring-configuration-metadata.json`，收录 7 个键（`logger-level.enabled`、`error-decoder.enabled`、`request-interceptor.enabled`、`auth-token-aspect.enabled`、`auth-token.enabled`、`fallback-config.enabled`、`circuit-breaker.{enabled,max-retries,backoff,backoff-multiplier,open-timeout,reset-timeout}` 的组描述）；新增 `docs/leaf-base-feign开发手册.md`；更新 `AGENTS.md` 中"错误解码器等基础 Bean 自动注册"的表述与新增开关、`maxRetries` 默认值。
- **Rationale**：宪法 I 的模块交付集要求开发手册，宪法 IV 要求新增开关同步写元数据（E-16、F-12）。
- **Alternatives considered**：
  - 只写 `AGENTS.md`：IDE 仍无提示，配置键仍靠猜。
- **Impact**：纯文档/资源文件，无运行时行为变化。
- **Verification**：`mvn -pl leaf-base-feign test` 后检查 `target/classes/META-INF/additional-spring-configuration-metadata.json` 存在且 JSON 合法；人工核对手册键表与 `@ConditionalOnProperty` 一一对应。

## D-11 `@GXFeignHeader` 边界不做扩展

- **Decision**：保持 `@Target({METHOD})`；在 Javadoc 与手册中明确"仅方法级生效、仅从当前 Servlet 请求取值、无上下文时静默跳过"。
- **Rationale**：扩展注解目标会改变公共契约（宪法 II），且无需求支撑；真正的问题是"静默无日志"，已在 D-07 的注解缓存路径上补一条 debug 日志。
- **Alternatives considered**：
  - 支持类型级：需要新的解析顺序与优先级规则，属于新能力。
  - 无上下文时抛异常：会让定时任务里的 Feign 调用直接失败，破坏既有可用性。
- **Impact**：无签名变化；补一条 debug 日志。
- **Verification**：用例断言"无上下文时头未透传且不抛异常"（既有用例保持）。

## D-12 空值契约收敛范围

- **Decision**：`GXFeignService.getPlatform()`、`getTraceId()`（返回承诺非空，但 `getAuthTokenSecret` 可能抛）、`GXFeignHeader.names()` 之外的可空返回值统一补 `@Nullable`；包级继续使用 `@NullUnmarked`，在手册中说明"逐步收敛"的现状。
- **Rationale**：宪法 V 要求可空语义可被 IDE 与静态检查验证；但一次性切换全部包到 `@NullMarked` 会触发大量 NullAway 告警，超出本特性范围。
- **Alternatives considered**：
  - 全量 `@NullMarked`：违反"每次只改当前模块的最小集合"，且会牵连 `GXCommonUtils` 等上游签名。
- **Impact**：仅注解，无运行时变化。
- **Verification**：`mvn -pl leaf-base-feign -am compile` 零 NullAway 告警。

---

## 依赖行为核实记录（用于审查报告的证据链）

| 事实 | 来源 | 结论 |
| --- | --- | --- |
| OpenFeign 默认重试策略为不重试 | `spring-cloud-openfeign-core 5.0.3` `FeignClientsConfiguration#feignRetryer`（第 138–142 行） | `Retryer.NEVER_RETRY`，支持 D-04 的默认值收敛 |
| 熔断器对任意异常重试并在耗尽后走 fallback | `spring-cloud-circuitbreaker-framework-retry 5.0.3` `FrameworkRetryCircuitBreaker#run`（第 53–84 行） | 4xx 业务异常也会进 fallback，重试次数由 `RetryPolicy` 决定 |
| `RetryPolicy` 支持按异常过滤 | `spring-core 7.0.9` `RetryPolicy$Builder`（`javap`） | 提供 `includes`/`excludes`/`predicate`，支撑 D-04 |
| `FeignClientsConfiguration` 虽声明 `FeignHttpMessageConverters` 与两个 `Encoder` 分支，但不会自动作用于 `@EnableFeignClients` 客户端 | `FeignClientsConfiguration` 第 101–121、182–220 行 + 实测（删除 `GXFeignFallbackConfig.Encoder` 后 `No bean found of type feign.codec.Encoder`） | 该规格的 `Encoder` 是必要来源而非重复声明，支撑 D-05 修正 |
| `FrameworkRetryConfigBuilder` 默认 `openTimeout=20s`、`resetTimeout=5s`、`RetryPolicy.withDefaults()` | `FrameworkRetryConfigBuilder` 第 34–38 行 | 与模块默认值一致，改 `maxRetries` 不影响熔断窗口语义 |
