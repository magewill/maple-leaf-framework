# Feature Specification: leaf-base-feign 逻辑正确性、线程安全、性能与契约一致性审查

**Feature Branch**: `007-feign-module-audit`（`before_specify` hook 未注册，本次未创建 git 分支；当前分支为 `dev4.3.0-spec/006-rocketmq-module-audit`）

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "审查 @leaf-base-feign 模块。它是对 openfeign 的封装。"

## 背景与审查结论摘要

本规格把"`leaf-base-feign` 对 OpenFeign 的封装是否正确、是否线程安全、是否存在性能结构问题、对外契约是否自洽"转化为可验收的交付要求。审查对象与证据来源：

1. `leaf-base-feign` 全部 16 个生产源码文件（含 7 个 `package-info.java`）、5 个既有测试类、1 个自动装配注册文件；
2. 契约来源：`leaf-base-feign/AGENTS.md`、`.specify/memory/constitution.md`、根 `pom.xml`、`config/{dev,test,pre,prod}/app-environment.yml`；
3. 依赖基线：`spring-cloud-starter-openfeign`、`spring-cloud-starter-circuitbreaker-framework-retry`、`leaf-base-framework`（提供 `GXAuthCodeUtils`、`GXCurrentRequestContextUtils`、`GXTraceIdContextUtils`、`GXBusinessException`）；
4. **既有测试实测基线**：本次执行 `mvn -pl leaf-base-feign test`，结果 **25 个用例、0 失败、BUILD SUCCESS**（codec 3 / circuit-breaker-config 6 / circuit-breaker-fallback 2 / feign-config 10 / interceptor 4）。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 逻辑正确性 | 请求头写入均为"先移除再写入"的幂等写法，敏感头黑名单有效，错误体读取有上限与降级；但 **默认装配下鉴权 token 从不透传**、**token 生成失败直接中断业务调用**、**错误解码器默认关闭且开启后错误码被压平为 500**、**熔断重试默认对所有 HTTP 方法生效（重复写风险待实测）** 四条链路存在"无报错但行为与承诺不符" | 存在高风险缺陷 |
| 线程安全 | 拦截器与解码器无可变状态；切面的静态方法签名缓存为 `ConcurrentHashMap` 且键集合有界；风险集中在 **静态缓存跨容器/类加载器持有**、**traceId 生成后未回写上下文导致链路断点** | 无致命竞态，需补约束 |
| 性能 | 每次调用固定执行注解反射查找、多次环境值查询、`System.getProperty`、`String.format`、6–8 次 header 写入、3–5 条 debug 日志；出站请求被注入 `Cache-Control: no-cache` 抑制代理缓存 | 存在可量化固定开销，非阻断 |
| 契约一致性 | `AGENTS.md` 声称"统一注册日志级别、请求拦截器、错误解码器等基础 Bean"，实际错误解码器默认不装配；四个环境的熔断配置键 `maple.feign.*` 与代码前缀 `maple.framework.feign.*` 不一致，配置全部空转；模块无开发手册、无配置元数据 | 契约漂移事实成立 |

关键证据（均可按位置复核）：

- **E-01**：全仓库引用 `GXFeignService` 的仅 5 个文件（`GXFeignService` 自身、拦截器、配置类、切面、拦截器测试），**不存在任何 `implements GXFeignService` 或 `@Bean GXFeignService`**；该接口全部为 `default` 方法，Spring 不会为其生成 Bean。
- **E-02**：`GXFeignRequestInterceptor.resolveFeignService()` 在 `ObjectProvider` 有值但无候选时返回 `null`（第 69–74 行），`propagateServiceHeaders` 随即 `log.warn("Failed to get FeignService instance, skipping authentication header propagation")` 并 `return`（第 76–80 行）→ 每次 Feign 调用丢弃 `X-Auth-Token` 与 platform，并产生一条 warn。
- **E-03**：`GXFeignService.generateHttpAuthToken()` 在 `maple.framework.web.feign.token` 缺失时抛 `GXBusinessException`（第 36–42 行），`getAuthTokenSecret()` 在 `maple.framework.web.feign.secret` 缺失时抛 `GXBusinessException`（第 83–89 行）；拦截器第 82 行直接调用且未捕获 → 该异常从 `RequestInterceptor.apply` 抛出，Feign 调用在发出请求前失败。
- **E-04**：错误解码器 Bean 的开关 `maple.framework.feign.error-decoder.enabled` **没有** `matchIfMissing`（`GXFeignConfig` 第 38–43 行），与同文件中 logger/interceptor/aspect 三个开关的 `matchIfMissing = true`（第 32、60、71 行）不一致；`GXFeignConfigTest` 第 43 行以 `doesNotHaveBean(ErrorDecoder.class)` 把"默认不装配"固化为既有行为。
- **E-05**：`GXFeignCustomErrorDecoder` 对 400/401/403/404/500/503/504 一律 `new GXBusinessException(msg)`（第 41–71 行），而 `GXBusinessException(String)` 的构造器把 `code` 固定为 `HTTP_INTERNAL_ERROR`（`GXBusinessException` 第 42–44 行）→ 远端 400/404 在本进程表现为 code=500；远端 `GXResultUtils` 的 `code/msg/data` 未被解析。
- **E-06**：401/403/404/500/503/504 六个分支虽然在 `extractErrorMessage` 中读取了响应体（第 34 行），但最终异常消息里只保留固定文案（第 46–69 行），读取结果被丢弃；仅 400 分支把响应体拼进消息（第 56 行）。
- **E-07**：`GXCircuitBreakerConfig` 的 `maple.framework.feign.circuit-breaker.enabled` 为 `matchIfMissing = true`（第 24–28 行），`GXCircuitBreakerProperties` 默认 `maxRetries=3`、`backoff=100ms`、`backoffMultiplier=2.0`、`openTimeout=20s`、`resetTimeout=5s`（第 11–19 行）。
- **E-08**：四个环境配置均开启 `spring.cloud.openfeign.circuitbreaker.enabled=true`、`group.enabled=false`、`alphanumeric-ids.enabled=true`（`config/{dev,test,pre,prod}/app-environment.yml` 第 1–9 行）→ 熔断与 fallback 作用于全部 Feign 客户端，包括 4xx 业务异常。
- **E-09**：四个环境配置写在 `maple.feign.circuit-breaker.*`（`config/dev/app-environment.yml` 第 23–31 行），而绑定前缀是 `maple.framework.feign.circuit-breaker`（`GXCircuitBreakerProperties` 第 9 行）→ 该段配置从未生效；`GXCircuitBreakerConfigTest` 只验证了正确前缀（第 52–57 行），故 CI 无法暴露。
- **E-10**：`appendCommonHeaders` 把 `X-Content-Type-Options`、`X-Frame-Options`、`X-XSS-Protection`、`Cache-Control`、`Pragma`、`Expires` 写入**出站请求**头（第 119–124 行），并强制覆盖 `User-Agent`（第 126–127 行）。
- **E-11**：无请求上下文且 MDC 无 traceId 时，`GXFeignService.getTraceId()`（第 155–160 行）与拦截器回退（第 102–104 行）调用 `GXTraceIdContextUtils.generateTraceId()`，但均未 `putTraceId` 回写 MDC → 本进程日志无法关联该次调用。
- **E-12**：5 个测试类中**没有** `GXFeignAuthTokenAspect` 的任何测试；`AGENTS.md` 第 75–79 行明确要求该切面覆盖方法级/类级注解、缺失 Bean 异常、校验成功/失败、代理与最具体方法解析。
- **E-13**：切面 pointcut 使用 `@annotation(...) || @within(...)`（第 39–40 行）；`@GXHttpInvokerAuthToken` 的 `@Target` 含 `TYPE`（注解第 7 行），但"接口/代理类型上的类级注解是否被 AOP 命中"从未验证；`GXSpringContextUtils.getBean` 缺失时抛 `GXBusinessException` 的分支（第 62–67 行）同样零覆盖。
- **E-14**：`@GXFeignHeader` 仅 `@Target({METHOD})`（注解第 9 行）；拦截器在无 `HttpServletRequest` 时直接 `return`（第 141–144 行），无任何日志，与其它分支的 debug 输出不一致。
- **E-15**：未命中自定义状态码时回退 `defaultErrorDecoder.decode(...)`（第 29–32、70 行）→ 抛出的是 Feign `FeignException`，不属于 `GXBusinessException` 家族，框架全局异常处理只能兜底为"系统内部错误"，与"统一异常"原则不一致。
- **E-16**：模块 `src/main/resources` 只有 `META-INF/spring/...AutoConfiguration.imports`，无 `additional-spring-configuration-metadata.json`；`docs/` 目录 15 个手册中无 `leaf-base-feign开发手册.md`；六个对外配置键（`logger-level.enabled`、`error-decoder.enabled`、`request-interceptor.enabled`、`auth-token-aspect.enabled`、`circuit-breaker.*`、`maple.framework.web.feign.token/secret`）在 IDE 中无提示。
- **E-17**：`GXFeignConfig` 注册 `default.mapleFeignFallback.FeignClientSpecification`（第 45–53 行），把 `GXFeignFallbackConfig` 作为**所有客户端**的 default 规格；`GXFeignFallbackConfig` 无条件声明 `FeignHttpMessageConverters` 与 `Encoder` 两个 `@Bean`（第 16–33 行），与 Spring Cloud 默认 `FeignClientsConfiguration` 的同类型 `@ConditionalOnMissingBean` 定义构成注册顺序竞争。
- **E-18**：`GXFeignFallbackConfig` 无 `@Configuration`、无任何 `@ConditionalOnProperty` 开关（第 15 行开始），无法被关闭，也无法被显式替换。
- **E-19**：`GXFeignService.getSensitiveHeaderFields()`（第 96–104 行）在全仓库无调用点，属于无消费方的公开 API。
- **E-20**：每次 `apply()` 固定执行：一次 `ObjectProvider` 解析、按注解逐头 `AnnotationUtils.findAnnotation` + `request.getHeaders`、`generateHttpAuthToken`（两次环境值查询 + 编码）、`getPlatform`（一次环境值查询）、`getTraceId`（可能一次环境值查询 + UUID 生成）、`getEnvironmentValue("spring.application.name")`、`System.getProperty("os.name")`、`String.format`、6–8 次 `removeHeader`+`header`、3–5 条 `log.debug`（第 61–171 行）。
- **E-21**：`GXFeignAuthTokenAspect.METHOD_SIGNATURE_CACHE` 为 `static final ConcurrentHashMap`，键为 `point.getSignature().toString()`，无容量上限、无清理（第 37、107–113 行）。
- **E-22**：全部包级 `package-info.java` 标注 `@NullUnmarked`（如 `cn.maple.feign.annotation` 第 1 行），而 `GXFeignService.getPlatform()`/`getTraceId()` 可返回 `null`/空白且未标 `@Nullable`（第 111–113、145–162 行），空值语义不可被 IDE 与静态检查验证。

发现清单（分级）：

- **F-01（高）默认装配下鉴权 token 与 platform 从不透传**：仓库内无 `GXFeignService` 实现，`ObjectProvider` 解析为 `null` 后静默跳过认证头传播，仅留一条 warn；调用方会误判"框架已透传鉴权"（E-01、E-02）。
- **F-02（高）token 生成失败即中断业务调用**：缺少 `maple.framework.web.feign.token` / `secret` 时，任意 Feign 调用在 `RequestInterceptor` 阶段抛 `GXBusinessException`，即使目标接口无需鉴权；无降级、无独立开关（E-03）。
- **F-03（高）`@GXHttpInvokerAuthToken` 在缺失实现时使接口整体 500**：该注解默认值为 `feign`（`GXHttpInvokerAuthToken` 第 11 行），下游只要标注了它，而应用未实现 `GXFeignService`，切面即抛"请实现 cn.maple.feign.service.GXFeignService接口"；`leaf-framework` 聚合门面默认引入本模块，影响面覆盖全部使用聚合门面的应用（E-01、E-13）。
- **F-04（高）错误解码器默认关闭且开启后错误码压平**：默认走 Feign `FeignException`（非 `GXBusinessException`），开启后 400/404/503 全部变为 code=500，远端 body 在 6 个分支被丢弃，统一响应契约丢失（E-04、E-05、E-06、E-15）。
- **F-05（高）熔断重试默认开启并覆盖非幂等请求**：`maxRetries=3` 对 POST/PUT/DELETE 同样生效，远端返回 5xx 或连接异常时可能重复写；同时 fallback 会把 4xx 业务异常吞成默认返回值。是否重复、重试几次需实测确认（E-07、E-08）——当前为"待验证风险"而非已确认缺陷；处置结论见 Clarifications 与 FR-006。
- **F-06（高）熔断配置键与实际前缀不一致，四个环境配置全部空转**：`maple.feign.*` 从未被绑定，线上实际使用代码默认值，配置与行为"看起来一致"纯属默认值巧合（E-09）。
- **F-07（中）出站请求被注入响应安全头并强制覆盖 `User-Agent`**：请求头承载响应语义无实际效果，`Cache-Control: no-cache` 会抑制幂等 GET 的代理缓存，`User-Agent` 覆盖使服务端无法区分调用方（E-10）。
- **F-08（中）traceId 生成不回写上下文**：非 Web 线程发起 Feign 调用时，生成的 traceId 只出现在下游，本进程日志无关联 id，链路断点（E-11）。
- **F-09（中）鉴权切面零测试覆盖、类级注解匹配可行性未验证**：模块无 `GXFeignAuthTokenAspectTest`，`@within` 对接口/代理类型注解的命中、缺失 Bean 的异常语义、方法级优先于类级均无回归锁定（E-12、E-13）。
- **F-10（中）`@GXFeignHeader` 能力边界不清**：仅方法级生效、仅 Servlet 上下文取值、无上下文时静默跳过无日志，使用方难以判断"为什么头没透传"（E-14）。
- **F-11（中）默认 fallback 规格与 Spring Cloud 默认配置重复**：`default.mapleFeignFallback` 规格对所有客户端生效，`Encoder`/`FeignHttpMessageConverters` 与 Spring Cloud 默认配置同类型竞争，胜负取决于注册顺序；未与 `@EnableFeignClients` 共存验证（E-17、E-18）。
- **F-12（中）契约文档与配置元数据缺失**：无开发手册、无 `additional-spring-configuration-metadata.json`，六个配置键无 IDE 提示；`AGENTS.md` 与实现存在"错误解码器默认装配"的表述冲突（E-16、E-04）。
- **F-13（低）无消费方的公开 API**：`getSensitiveHeaderFields()` 无任何调用点（E-19）。
- **F-14（低）空值契约缺失**：包级 `@NullUnmarked` 与可空返回值未标注，静态检查无法验证（E-22）。
- **F-15（低）每调用固定开销未定量化**：注解反射、环境查询、`String.format`、header 重复写入与 debug 日志均位于调用关键路径（E-20）。
- **F-16（低）静态方法签名缓存无上限**：跨容器与类加载器持有，缺少容量与生命周期约束（E-21）。

已确认为**正确实现**（不得在修复中被破坏）：

- **P-01**：请求头写入统一采用"`removeHeader` 后 `header`"的幂等写法，重复 `apply()` 不产生重复头，`X-Auth-Token`/platform/traceId/`User-Agent`/`X-Request-Start-Time` 已由既有用例锁定。
- **P-02**：`BLOCKED_PROPAGATION_HEADERS` 白名单式阻断 `Authorization`、`Cookie`、`X-Auth-Token`、`X-HMAC-Signature`、`X-API-Key` 及 hop-by-hop 头，`@GXFeignHeader` 无法被用来越权透传敏感头，既有用例覆盖。
- **P-03**：无 `GXFeignService` 时 traceId 仍能被补全且不抛异常（`missingFeignServiceStillPropagatesTraceIdSafely`）。
- **P-04**：错误响应体读取有 8192 字符上限、截断标记与读取失败降级文案，三个分支均有测试。
- **P-05**：熔断参数 setter 对负值/`null`/非正 multiplier 做前置拒绝，环境属性覆盖与自定义 `Customizer` 回退均有测试。
- **P-06**：五个开关（logger、error-decoder、request-interceptor、auth-token-aspect、circuit-breaker）均支持"用户自定义 Bean 优先"语义，`AutoConfiguration.imports` 注册与 `ApplicationContextRunner` 装配有测试覆盖。

## Clarifications

### Session 2026-10-22

- Q: 本次交付是"只给审查结论与验证基线"，还是"结论 + 直接修复"？ → A: 结论 + 可执行验证基线为本次必交付；生产代码修复由后续 `/speckit.plan` 依据发现分级排期。本规格只要求每条发现可复现、可写出"旧代码失败、新代码通过"的回归测试设计。
- Q: 审查范围是否包含 `leaf-base-framework` 与 `leaf-base-webclient` 的对应实现？ → A: 不包含。`GXAuthCodeUtils`、`GXCurrentRequestContextUtils`、`GXTraceIdContextUtils`、`GXWebClientAuthTokenAspect` 仅作为行为对照与证据引用，不得修改；本模块只审查自己持有的封装逻辑。
- Q: `maple.feign.*` 与 `maple.framework.feign.*` 的配置键冲突如何收敛？ → A: 以代码前缀 `maple.framework.feign.*` 为唯一契约（与宪法第 IV 条和既有测试一致）；四个环境模板按本会话后续授权项直接修正，代码侧不保留旧键别名。
- Q: 熔断重试对 POST/PUT/DELETE 的重复写风险，是判定为缺陷还是既定契约？ → A: 先判定为"待验证风险"。规格要求用进程内可控服务端实测"同一逻辑调用实际发起几次 HTTP 请求"，再据此定性；在实测之前不得断言为缺陷，也不得据此改动默认值。
- Q: 默认不装配错误解码器（`error-decoder.enabled` 无 `matchIfMissing`）是否属于缺陷？ → A: 属于"契约与文档不一致"而非纯缺陷：既有测试已把默认关闭固定为行为，本规格要求先明确"默认值应取哪一种"的决策，再要求文档与测试同时对齐，不得只改其中一侧。
- Q: 真实验证是否允许引入新的测试依赖（如 MockWebServer/Testcontainers）？ → A: 不引入。使用 JDK 自带的进程内 HTTP 服务与 `ApplicationContextRunner`/`@SpringBootTest` 构造"真实 HTTP 往返"，保持依赖零增长（宪法附加约束 3）。
- Q: 性能以什么标准判定"改到位"？ → A: 不设绝对吞吐/时延门禁；只要求给出可复算口径（每调用 header 写入次数、环境值查询次数、日志条数、`apply()` 单位时间吞吐与分位）与同环境重跑同量级（偏差 ≤ 30%）的复现要求。
- Q: 认证 token 的配置或密钥缺失时，Feign 调用的默认行为应当是什么？ → A: 保持 fail-fast 为默认（缺配置即中断调用，向后兼容），同时新增独立开关 `maple.framework.feign.auth-token.enabled`（默认 `true`）；显式置为 `false` 时跳过 token 透传、输出 warn 并继续调用。
- Q: 自定义错误解码器的默认开关状态应当如何确定？ → A: 保持默认关闭（不改变下游可捕获的异常类型），把 `AGENTS.md` 与文档改为"需显式开启"；缺陷修复集中在"开启后 MUST 保留远端 HTTP 状态码与响应体业务码"，而不是调整默认开关。
- Q: 四个环境配置模板的配置键修正是否纳入本特性的交付范围？ → A: 纳入。`config/{dev,test,pre,prod}/app-environment.yml` 改为 `maple.framework.feign.circuit-breaker.*`；本次对此跨模块改动予以授权，但仅限这四个文件的该段配置，不得顺带改动其它键。
- Q: 独立的审查报告应当存放在哪个位置、以什么文件名交付？ → A: `specs/007-feign-module-audit/audit-report.md`；报告与规格同目录、同生命周期，`spec.md` 仅保留摘要与指向该报告的引用。
- Q: 如果实测确认非幂等请求会被重复发送，重试的默认行为应当如何处理？ → A: 默认不重试：把 `maxRetries` 默认值收敛为 `0`（与 OpenFeign 原生 `Retryer.NEVER_RETRY` 语义一致），需要重试者显式配置；熔断相关的 `enabled` / `open-timeout` / `reset-timeout` / `backoff` 语义保持不变。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 审查结论可复核、可构造故障现场 (Priority: P1)

框架维护者要判断"能否在下游生产直接启用本模块的封装能力"，需要按维度分级的结论；每条结论都必须能跳到代码位置，并能用进程内服务端构造出同样的现象。

**Why this priority**: 审查的唯一价值是结论可信；结论不可复核时，后续计划与修复都建立在猜测上。

**Independent Test**: 随机抽取一条结论，按报告给出的位置与命令复现；高风险项必须能构造故障现场（例如在未实现 `GXFeignService` 的应用里发起一次 Feign 调用，观察 warn 与缺失的认证头）。

**Acceptance Scenarios**:

1. **Given** 一条"默认装配下认证头从不透传"的结论，**When** 以最小 Spring 上下文发起一次 Feign 调用，**Then** 服务端收到 0 个 `X-Auth-Token` 且日志出现 warn，与结论一致。
2. **Given** 一条"错误响应体在 401/403/404/500/503/504 被丢弃"的结论，**When** 服务端返回带业务 JSON 的这些状态码，**Then** 异常消息中不含该 JSON，与结论一致。
3. **Given** 一条"请求头写入幂等"的正向结论，**When** 对同一 `RequestTemplate` 连续 `apply()` 两次，**Then** 每个头只有一个值，与结论一致。

---

### User Story 2 - 出站请求头透传矩阵可验证 (Priority: P1)

下游接入方需要明确知道：哪些头会被透传、由谁生成、在有无 Web 上下文与有无 `GXFeignService` Bean 的四种组合下分别是什么结果。

**Why this priority**: 请求头是跨服务鉴权与链路追踪的唯一载体；语义不清会直接表现为"线上 401"或"链路断点"。

**Independent Test**: 对 5 类头（auth token、platform、traceId、注解声明头、公共头）× 4 种上下文组合各执行一次调用，记录实际出现在服务端请求中的头值集合。

**Acceptance Scenarios**:

1. **Given** 无 `GXFeignService` Bean、有 Web 上下文，**When** 发起调用，**Then** 报告中明确 `X-Auth-Token`/platform 是否缺失、traceId 是否来自请求、warn 是否每调用一次。
2. **Given** 有 `GXFeignService` Bean 但缺少 `maple.framework.web.feign.token`，**When** 发起调用，**Then** 报告给出"调用是否在发出前失败、失败异常类型与 code"的确切结论。
3. **Given** `@GXFeignHeader` 声明了敏感头与普通头，**When** 发起调用，**Then** 普通头透传、敏感头被阻断的判定与既有用例一致。
4. **Given** 无 Web 上下文（非请求线程），**When** 发起调用，**Then** 报告给出 traceId 是否被新生成、是否回写 MDC、下游是否拿到的唯一定性。

---

### User Story 3 - 错误语义与熔断重试可验证 (Priority: P1)

使用方需要明确：远端返回各状态码时，本地得到什么异常类型、`code` 是多少、`msg` 来自哪里；以及熔断与重试会额外发起几次请求。

**Why this priority**: 错误语义决定上游能否正确重试与补偿；重试次数决定是否产生重复写。

**Independent Test**: 用进程内服务端按状态码逐一返回带业务 JSON 的响应，记录异常类型/`code`/`msg`；同时统计服务端收到的请求次数。

**Acceptance Scenarios**:

1. **Given** 解码器开关关闭（默认），**When** 服务端返回 400/404/500，**Then** 报告给出异常类型与 `code`，并说明是否为 `GXBusinessException`。
2. **Given** 解码器开关开启，**When** 服务端返回 400/401/403/404/500/503/504 与 3 个未处理状态码，**Then** 报告给出全部 10 个状态码的"异常类型 + code + msg 来源"三元定性。
3. **Given** 熔断重试默认参数，**When** 对 POST 与 GET 各触发一次远端 5xx，**Then** 报告给出服务端实际收到的请求次数与总耗时，并据此判定是否存在重复写风险。
4. **Given** 配置了 `fallback` 的客户端，**When** 远端返回 400，**Then** 报告明确 fallback 是否被触发、业务异常是否被吞。

---

### User Story 4 - 装配与配置键一致性可验证 (Priority: P2)

维护者需要判断自动装配的实际结果：哪些 Bean 会被注册、五个开关的默认值是否符合文档、六个配置键是否真的生效、default 规格是否与 Spring Cloud 默认配置冲突。

**Why this priority**: 配置不生效不会报错，只会让所有接入方在错误前提下调参。

**Independent Test**: 用 `ApplicationContextRunner` 与 `@SpringBootTest` 分别在开关打开/关闭、配置键正确/错误四类输入下启动上下文，断言实际 Bean 与属性值。

**Acceptance Scenarios**:

1. **Given** `maple.feign.circuit-breaker.max-retries=1`（四个环境的实际写法），**When** 启动上下文，**Then** 报告给出该键是否影响 `GXCircuitBreakerProperties` 的结论与证据。
2. **Given** `@EnableFeignClients` 与自定义 `Encoder` 同时存在，**When** 启动上下文，**Then** 报告明确最终生效的 `Encoder` 是哪一个、`default.mapleFeignFallback` 规格是否覆盖了用户配置。
3. **Given** 五个开关分别置为 `false`，**When** 启动上下文，**Then** 报告给出每个开关的实际效果，并与 `AGENTS.md` 的描述逐条对照。
4. **Given** 只想使用部分能力（例如只要 traceId 透传、不要 token 生成），**When** 按现有配置键组合，**Then** 报告说明是否可行及替代方案。

---

### User Story 5 - 性能与每调用开销定量化 (Priority: P2)

维护者需要判断该封装在目标 QPS 下的固定成本。结论必须给出可复算口径：每调用做几次反射、几次环境查询、几次头写入、几条日志，以及 `apply()` 的吞吐分位。

**Why this priority**: 无口径的性能结论无法转化为"改到什么程度算达标"的计划。

**Independent Test**: 对 `apply()` 执行 10 万次以上采样，记录吞吐与分位；按代码路径复算每调用的查询与写入次数，两次运行偏差不超过 30%。

**Acceptance Scenarios**:

1. **Given** 空 `RequestTemplate`，**When** 连续调用 `apply()` 不少于 10 万次，**Then** 报告给出 ops/s 与 p50/p95/p99，并说明重跑是否同量级。
2. **Given** 带 5 个注解声明头的调用，**When** 统计头写入与 `getHeaders` 调用次数，**Then** 报告给出与头数量成正比的口径。
3. **Given** 一次完整调用，**When** 统计环境值查询、`System.getProperty`、`String.format`、debug 日志条数，**Then** 报告给出固定开销清单，且每项可被代码位置对应。
4. **Given** `Cache-Control: no-cache` 被写入请求头，**When** 评估对幂等 GET 缓存的影响，**Then** 报告给出定性结论与量化依据（是否命中代理缓存）。

---

### User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

计划阶段需要把发现直接转成任务：每条发现都应有编号、级别、维度、影响面、证据、复现方式与"旧代码失败、新代码通过"的回归设计。

**Why this priority**: 审查不是终点；不能落到可执行任务的结论等于没有结论。

**Independent Test**: 抽取任一发现，检查是否具备八项要素（编号、级别、维度、位置、触发条件、影响、证据、回归设计），缺一即不合格。

**Acceptance Scenarios**:

1. **Given** 一条高危发现，**When** 交由计划阶段，**Then** 无需补充上下文即可拆出"测试先行"的任务。
2. **Given** 被判定为既定契约的行为（如默认不装配错误解码器、`@GXFeignHeader` 仅方法级），**When** 查阅报告，**Then** 明确标注为契约而非缺陷，避免被误改。

---

### Edge Cases

- 未实现 `GXFeignService` Bean；实现了但抛异常；实现了但返回空 token；存在多个实现（`NoUniqueBeanDefinitionException`）。
- 缺少 `maple.framework.web.feign.token` / `maple.framework.web.feign.secret`；token 过期；token 被篡改；token 为空字符串。
- 无 `HttpServletRequest`（定时任务、MQ 消费线程、`@Async` 线程、虚拟线程）下发起 Feign 调用。
- `@GXFeignHeader` 声明空数组、空白头名、重复头名、大小写不同的敏感头名（`authorization` vs `Authorization`）。
- 同一 `RequestTemplate` 被重试复用多次 `apply()`；Feign 重试与熔断重试叠加。
- 远端返回 204、301、418、429 等未处理状态码；响应体为空、非 UTF-8、超 8192 字符、读取过程中连接中断。
- 远端返回业务 JSON（`code/msg/data`）但 HTTP 状态为 200 或 500；`Content-Type` 非 JSON。
- 熔断打开期间的调用；fallback 自身抛异常；`maxRetries=0`；`backoff=0ms`。
- 应用同时引入 `leaf-base-webclient`，两个实现同 value 的切面共存。
- 上下文关闭时存在在途 Feign 调用；`RequestContextHolder` 残留导致跨线程串号。
- 自定义 `Encoder`/`ErrorDecoder`/`RequestInterceptor` 与框架默认 Bean 同时存在。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖模块全部 16 个生产源码文件、5 个既有测试类与 `AutoConfiguration.imports`；每个文件 MUST 有明确判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因），不得跳过。
- **FR-002**: 每条发现 MUST 标注维度（逻辑正确性 / 线程安全 / 性能 / 契约一致性）、严重级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件与影响面。
- **FR-003**: 每条发现 MUST 附带复现方式：既有用例、可新增的回归用例设计或明确构造步骤；仅凭阅读的判断 MUST 明确标注为"未验证假设"。
- **FR-004**: 请求头透传 MUST 用真实 HTTP 往返验证，覆盖 5 类头 × 4 种上下文组合（有无 Web 上下文 × 有无 `GXFeignService`），并逐项给出"是否出现、值来源、是否重复"三项定性。
- **FR-005**: 错误语义 MUST 覆盖自定义处理的 7 个状态码与至少 3 个未处理状态码，逐个给出"异常类型 + `code` + `msg` 来源 + 响应体是否保留"四项定性。
- **FR-006**: 熔断与重试 MUST 实测服务端实际收到的请求次数（GET 与 POST 各一组），并给出"是否存在非幂等重复执行"的定性；未实测前 MUST NOT 断言为缺陷。若实测确认重复发送，修复设计 MUST 把 `maxRetries` 默认值收敛为 `0`（对齐 OpenFeign 原生不重试语义），重试 MUST 由显式配置开启，且开启后 MUST 排除 `code < 500` 的 `GXBusinessException`；`enabled` / `open-timeout` / `reset-timeout` / `backoff` 的既有语义 MUST 保持不变（对应 F-05）。
- **FR-007**: 配置契约 MUST 逐键核对：代码前缀、四个环境配置的实际写法、六个配置键的默认值与生效情况各给出"一致 / 不一致 / 未生效"判定与证据。
- **FR-008**: 自动装配 MUST 在"仅框架自动配置"与"`@EnableFeignClients` + 自定义 Bean"两种真实形态下各验证一次，明确 `default.mapleFeignFallback` 规格与 Spring Cloud 默认配置的最终胜出者。
- **FR-009**: 线程安全结论 MUST 覆盖三类场景：共享可变状态（切面静态缓存、拦截器字段）、生命周期（缓存容量与容器重启）、上下文（traceId 生成与 MDC 回写、`RequestContextHolder` 残留）。
- **FR-010**: 性能结论 MUST 给出可复算口径：每调用 header 写入次数、环境值查询次数、`System.getProperty`/`String.format` 次数、debug 日志条数、`apply()` 样本量与 p50/p95/p99；两次运行偏差 MUST ≤ 30%。
- **FR-011**: 既有测试 MUST 全部执行并记录结果（当前基线 25 执行 / 0 失败）；失败用例 MUST 作为独立发现记录，不得删除或跳过用例来"通过"。
- **FR-012**: 对外契约（`AGENTS.md` 描述、公开注解语义、配置键、异常类型与 code、默认开关值）MUST 与实现逐条对照，不一致处 MUST 单列为发现；本规格 MUST NOT 修改生产代码。
- **FR-013**: 审查报告 MUST 独立成文于 `specs/007-feign-module-audit/audit-report.md`，`spec.md` 只保留摘要与该报告的引用，两者在交付时 MUST 相互一致；报告缺失或与摘要有冲突时，交付 MUST 判定为不通过。
- **FR-014**: 每条发现 MUST 给出"能在旧代码上失败、在新代码上通过"的回归测试设计（输入、断言点、期望差异）；该测试 MUST 不引入新的测试依赖，MUST 在无外部中间件环境下可运行。
- **FR-015**: 审查 MUST 明确区分"缺陷"与"既定契约"（如错误解码器默认关闭、`@GXFeignHeader` 仅方法级），对既定契约 MUST 要求文档与测试双向对齐，而非计为缺陷。
- **FR-016**: 结论 MUST 包含风险排序依据（影响面 × 触发概率 × 可观测性），使计划阶段可直接确定修复批次；高影响面判定 MUST 说明受影响范围（如"引入聚合门面的全部应用"）。
- **FR-017**: 认证 token 传播链路 MUST 给出"配置缺失 / Bean 缺失 / 生成为空 / 生成抛异常"四种情形的确切行为；修复设计 MUST 保持 fail-fast 为默认语义（缺配置即中断调用），并 MUST 新增独立开关 `maple.framework.feign.auth-token.enabled`（默认 `true`）：显式置为 `false` 时跳过 token 透传、输出包含缺失键名与目标 methodKey 的 warn 后继续调用（对应 F-01、F-02）。
- **FR-018**: 配置键收敛 MUST 以代码前缀 `maple.framework.feign.*` 为唯一契约：四个环境模板的 `maple.feign.circuit-breaker.*` MUST 改为正确前缀并纳入本特性交付（跨模块改动已授权，改动范围限这四个文件的该段配置）；MUST NOT 在代码侧新增 `maple.feign.*` 别名绑定，MUST 在报告中说明下游自定义配置的迁移方式（对应 F-06）。
- **FR-019**: 空值契约 MUST 逐个检查公开 SPI 方法的可空性（`getPlatform`、`getTraceId`、`getAuthTokenSecret` 等），给出"是否返回 null/空白、是否应标 `@Nullable`、包级策略是否应从 `@NullUnmarked` 调整"的判定。
- **FR-020**: 真实验证 MUST 使用进程内 HTTP 服务与 Spring 上下文，MUST NOT 引入 MockWebServer/Testcontainers 等新依赖，MUST NOT 依赖外部网络。
- **FR-021**: 错误解码器 MUST 保持默认关闭（`maple.framework.feign.error-decoder.enabled` 维持无 `matchIfMissing` 的现状），文档与 `AGENTS.md` MUST 改为"需显式开启"表述；开启后的解码结果 MUST 保留远端 HTTP 状态码语义（`code` 不得统一压平为 500）与响应体中的业务码/消息，MUST 给出"远端 body 解析失败时如何降级"的明确规则（对应 F-04）。

### Key Entities *(include if data involved)*

- **审查发现（Finding）**：编号、级别、维度、代码位置、触发条件、影响面、证据、复现方式、回归测试设计、修复建议。
- **请求头透传矩阵**：头的类别（auth token / platform / traceId / 注解声明头 / 公共头）× 上下文组合（有无 Servlet 上下文 × 有无 `GXFeignService`）× 结果（是否出现、值来源、是否重复）。
- **错误语义矩阵**：HTTP 状态码（7 个自定义 + ≥3 个未处理）× 异常类型 × `code` × `msg` 来源 × 响应体是否保留 × 是否被 fallback 吞掉。
- **装配矩阵**：配置类（`GXFeignConfig` / `GXCircuitBreakerConfig` / `GXFeignFallbackConfig`）× 开关状态 × 是否注册 Bean × 与 Spring Cloud 默认配置的先后关系。
- **配置键矩阵**：键名 × 代码前缀 × 四个环境的实际写法 × 是否生效 × 默认值。
- **性能口径**：每调用反射/环境查询/头写入/日志条数，`apply()` 样本量与分位。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查覆盖 100% 的 16 个生产源码文件与 5 个既有测试类，100% 的发现记录在独立报告中；新维护者可在 15 分钟内复现任一结论。
- **SC-002**: 100% 的发现具备"级别 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归设计"；0 条仅凭阅读的结论混入已确认项。
- **SC-003**: 请求头透传矩阵覆盖 5 类头 × 4 种上下文组合（20 个断言点）并 100% 有唯一定性，其中敏感头阻断路径 100% 与既有用例一致。
- **SC-004**: 错误语义矩阵覆盖 10 个及以上状态码，每个给出"异常类型 + `code` + `msg` 来源"三元定性，覆盖率 100%；其中远端业务 `code` 是否丢失的判定 MUST 明确。
- **SC-005**: 熔断重试实测给出 GET 与 POST 各一组"实际请求次数 + 总耗时"，100% 有唯一定性；结论 MUST 在实测后给出，不得预先判定；若确认重复发送，修复后 MUST 有回归用例证明默认配置下同一逻辑调用只发送 1 次请求。
- **SC-006**: 配置契约核对覆盖 6 个配置键 × 4 个环境（24 个组合），100% 给出"生效 / 未生效"判定；`maple.feign.*` 与 `maple.framework.feign.*` 的差异 MUST 有明确结论。
- **SC-007**: 性能结论给出不少于 10 万次 `apply()` 的样本量与 p50/p95/p99，重跑两次偏差 ≤ 30%，并按代码路径复算每调用固定开销；MUST NOT 以绝对吞吐或时延数值作为通过门槛。
- **SC-008**: 既有测试执行率 100%（基线 25 执行 / 0 失败），基线结果 MUST 在报告中保留并可复算。
- **SC-009**: 本规格的审查活动不修改 `leaf-base-feign` 生产代码，0 个新增第三方测试依赖；除已授权的四个环境模板配置键外，不产生其它模块或根配置改动；所有真实验证可在无外网环境运行。
- **SC-010**: 高危发现（F-01 ~ F-06）100% 具备可构造的故障现场与回归测试设计，计划阶段无需补充调研即可拆分为任务。

## Assumptions

- 目标运行形态为 JDK 21 + Spring Boot 4.1.x + Spring Cloud 2025.1.x，下游通过 `leaf-framework` 聚合门面或直接依赖引入本模块；ErrorProne + NullAway 保持启用。
- `GXFeignService` 由业务工程实现是既定设计（接口只提供 `default` 方法），本规格审查的是"缺失实现时的默认行为与可观测性"，不要求框架内置默认实现。
- `maple.framework.feign.*` 为唯一有效的配置前缀，`config/` 下的 `maple.feign.*` 属于从未生效的历史写法；四个环境模板的该段配置纳入本特性修正（已授权的跨模块改动），下游自有配置文件需由各自仓库按报告给出的迁移方式自行调整。
- `@GXHttpInvokerAuthToken` 的实际使用场景是服务端入口鉴权（`leaf-base-sso` 的拦截器据此跳过登录校验），切面的定位是"入站 token 校验"，不是"出站 token 生成"；出站 token 由 `GXFeignService.generateHttpAuthToken()` 提供。
- 本规格自身的审查活动不修改 `leaf-base-feign` 生产代码；Clarifications 中对 token 开关、错误码保留、重试默认值、配置模板的决策用于约束后续计划阶段的修复设计，不在本次审查中落地；不新增 fallback、批量调用等新能力，只审查现状。
- 性能数据以单机、单客户端实例、无网络延迟为前提，结论以"可复算口径 + 同量级复现"为验收标准，不承诺生产容量。
- 熔断重试的重复写风险在实测前按"未验证假设"处理，不作为缺陷计入；实测确认重复发送后，按 FR-006 的"默认不重试"结论收敛。
