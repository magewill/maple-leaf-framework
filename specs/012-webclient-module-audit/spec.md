# Feature Specification: leaf-base-webclient 正确性、线程安全与性能审查

**Feature Branch**: `012-webclient-module-audit`（本次未通过 hook 创建 git 分支；`before_specify` 无 hook 注册）

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "审查 @e:\project\java\maple-leaf-framework\leaf-base-webclient 模块的正确性、线程安全性、性能。你在修改完代码之后，一定做完备的测试用例。"

## 背景与审查结论摘要

本规格把"`leaf-base-webclient` 现有实现是否正确、是否线程安全、是否存在性能/资源结构问题"转化为可验收的交付要求，并把用户"修改完代码之后一定做完备的测试用例"固化为硬门槛。审查对象与证据来源：

1. `leaf-base-webclient` 全部 8 个生产源码文件（4 个 `package-info`、`GXWebClientConfig`、`GXWebClientService`、`GXWebClientAuthTokenAspect`、`GXWebClientExceptionHandler`）、1 个装配清单 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 与 5 个既有测试类（共 17 条用例）；
2. 契约来源：`leaf-base-webclient/AGENTS.md`、`.specify/memory/constitution.md`（重点为 II 稳定契约、III 统一异常、IV 显式配置、V 上下文与资源释放、VI 测试门槛）、下游 `leaf-base-eureka-client/AGENTS.md` 对本模块 Bean 契约的引用；
3. 上游语义核对（本地仓库源码/字节码逐条比对）：`spring-cloud-commons-5.0.3-sources.jar` 的 `LoadBalancerWebClientBuilderBeanPostProcessor` 与 `ReactorLoadBalancerExchangeFilterFunction`（`@LoadBalanced` 附着范围、serviceId 取法、无实例时的合成 503）；`reactor-netty-core` 的 `ConnectionProvider`（`extends reactor.core.Disposable`，仅 `dispose()/disposeLater()`）；`GXAuthCodeUtilsTest` 锁定的 `authCodeDecode` 失败返回 `"{}"` 语义；`GXCurrentRequestContextUtils` 的 `RequestContextHolder` 依赖；`GXBusinessException`（字段 `msg`，`code` 默认 500）与 `GXHttpInvokerApiErrorResDto`（字段 `message`）；
4. 依赖基线：`leaf-base-webclient` 依赖 `leaf-base-framework` + `spring-boot-starter-webflux` + `spring-cloud-starter-loadbalancer`（模块内 `loadbalancer.version=5.0.3`，根 POM `spring-cloud-dependencies` 2025.1.3 已管理同版本）；`reactor-netty` 1.3.x 随 Boot BOM；
5. 既有测试实测基线：`mvn -pl leaf-base-webclient test` → **Tests run: 17, Failures: 0, Errors: 0**（aspect 4 / config 4 / handler 2 / ascii 1 / service 6）；503 重试用例日志显示重试落在 `parallel-1..3` 线程（1+3 次、固定 1s 间隔，用例耗时 3.8s）；
6. 下游影响面实测：全仓检索显示 `leaf-base-eureka-client` 编译依赖本模块（`pom.xml`），`GXEurekaClientConfig` 以 `@AutoConfiguration(after = {GXFeignConfig.class, GXWebClientConfig.class, ...})` 锁定装配顺序，其测试直接 import `GXWebClientConfig`；eureka AGENTS.md 明文"不得改变 `@LoadBalanced WebClient.Builder`、`HttpServiceProxyFactory` 等 Bean 的名称、作用域或注入契约"。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 逻辑正确性 | 配置读取、token 生成/校验、注解解析、4xx/5xx 转换主链路自洽；但**远端统一响应的错误详情丢失**（`msg`/`message` 字段错位）、**合成 503 误导直连寻址失败**、**duration/阈值口径失真**、**两条 builder 创建路径语义分裂** | 存在高风险缺陷 |
| 线程安全 | 过滤器链与重试本身无共享可变状态、`METHOD_SIGNATURE_CACHE` 用 `ConcurrentHashMap` 无数据竞争；风险集中在**请求上下文/MDC 为 ThreadLocal 而过滤器在订阅线程执行**——异步、调度、reactor 并行线程上 traceId/platform/token 静默丢失 | 无致命竞态，上下文透传存在高风险缺口 |
| 性能与资源 | 每请求固定付 2 次环境查找 + 1 次 token 编码（有效期 150s 却不缓存）；**连接池 Bean 无销毁路径**、非 Spring 路径每次泄漏一个未托管连接池（含后台驱逐线程） | 存在结构性开销与泄漏，需修复 |

关键证据（均可按位置复核）：

- **E-01**：过滤器注册顺序 `requestFilter → responseFilter → retryFilter → errorResponseHandleFilter`（`GXWebClientConfig.java:289–294`）。token 生成失败在 `requestFilter` 内短路（第 203–206 行返回 `Mono.error`），不进入 `retryFilter` → fail-fast 不重试（既有用例 `webClientFailsFastWhenTokenGenerationFails` 锁定）；而 4xx/5xx 在 `errorResponseHandleFilter` 转错误后被 `retryFilter` 捕获重试（`webClientRetriesRetryableHttpStatusBeforeReturningBusinessException` 实测 503 共 4 次尝试）。
- **E-02**：`handle4xxError`/`handle5xxError` 用 `bodyToMono(GXHttpInvokerApiErrorResDto.class)` 解析错误体（第 359–361、375–377 行），该 DTO 字段为 `message`（`GXHttpInvokerApiErrorResDto.java:18`），而本框架统一响应错误字段是 `msg`（`GXBusinessException.msg`）→ 对端返回 `{"code":x,"msg":"..."}` 时 `errorBody.getMessage()` 为 `null`，回落到 `getHttpStatusDescription` 的通用文案（第 363–365、379–381 行），远端业务错误详情不可见。既有用例只覆盖 `{"message":"..."}` 形状（Spring 默认错误体），未覆盖框架统一响应形状。
- **E-03**：`isRetryableException`（第 397–402 行）对**任意** `WebClientRequestException` 与 code∈{408,429,500,502,503,504} 的 `GXBusinessException` 一律重试，不区分方法幂等性 → POST/PUT/DELETE 在连接重置/超时类失败下会自动重放，存在重复副作用风险；`retryWhen` 重订阅还会重放请求体，非可重放体（流/表单流）的失败语义未定义。
- **E-04**：`responseFilter` 的 `startTime` 在过滤器函数体内取值（第 218 行），而 `retryFilter` 在其内层（E-01）→ 实测口径的 `durationMs` **包含全部重试退避时间**（4 次 503 场景下"响应耗时"≈ 真实耗时 + 3s）；且 `flatMap`（第 219 行）只在成功发射时执行，**终态失败的请求完全不产生耗时日志**。
- **E-05**：Content-Length 告警阈值硬编码为 `DEFAULT_MEMORY_BUFFER_SIZE_MB`（第 243 行 = 16MB 常量），而编解码缓冲上限由 `maple.framework.web.client.memory-buffer-size-mb` 配置（第 167 行）→ 配置值 ≠16 时告警阈值与真实上限不一致。
- **E-06**：`requestFilter`/`responseFilter` 的 TRACE 级头输出（第 173–176、252–255 行）逐字打印全部请求/响应头，**包含 `X-Auth-Token`、`Authorization`、`Cookie`、`Set-Cookie` 明文**；SPI 的掩码契约 `getSensitiveHeaderFields()`（`GXWebClientService.java:111–119`）在模块内**零调用**，掩码从未生效。
- **E-07**：SPI 日志钩子 `logRequestResponse`（两重载）与 `logRequestException`（`GXWebClientService.java:94–104`）在模块内**零调用**，仅被测试当作 no-op 调用；实际耗时/大小日志内联在 `responseFilter` 中 → 用户覆盖钩子无法观测任何流量，属死扩展点。
- **E-08**：`webClientBuilder` 标注 `@LoadBalanced`（第 158 行）→ `LoadBalancerWebClientBuilderBeanPostProcessor` 给每个 builder 实例附加 `DeferringLoadBalancerExchangeFilterFunction`；`ReactorLoadBalancerExchangeFilterFunction.filter`（spring-cloud-commons-5.0.3 源码已核对）对**一切**请求取 `url().getHost()` 作为 serviceId，无直连主机旁路；未知主机（如 `https://api.example.com`）不发网络请求，直接返回合成 503 → 本模块再转成 `GXBusinessException("服务器处理错误(5xx): 服务不可用")` 并重试 3 次 → **直连外网 URL 得到 4 次假尝试 + 误导性"服务不可用"**。该行为对服务名寻址（配合 `leaf-base-eureka-client`）是设计意图，但语义未文档化、失败形态误导、且无官方直连出口。
- **E-09**：非注解路径 `webClientBuilder()`（第 308–325 行）不经 Bean 后置处理器 → **无 LB 过滤器**，与 `webClient` Bean 语义分裂（E-08）；当容器缺 `ReactorClientHttpConnector` Bean（或多于一个，`GXSpringContextUtils.getBean(Class)` 对 `NoUniqueBeanDefinitionException` 静默返回 null，`GXSpringContextUtils.java:52–53`）时，该方法现场 `mapleWebClientConnectionProvider(environment)` 新建**未托管连接池**（含 `evictInBackground` 后台线程）且无任何释放路径 → 每次调用泄漏一个池。
- **E-10**：`mapleWebClientConnectionProvider` Bean（第 98–115 行）持有的 `ConnectionProvider` 经 javap 核对为 `extends reactor.core.Disposable`，只有 `dispose()/disposeLater()`，无 `close()/shutdown()`、非 Spring `DisposableBean` → Spring 的 @Bean 销毁方法推断（只认 `close`/`shutdown`）不会触发，**应用关闭时连接池与驱逐线程不释放**；多上下文（测试、热部署、多模块集成）下累积泄漏。
- **E-11**：`getTraceId`/`getPlatform`/`checkTokenValidity` 依赖 `RequestContextHolder`（`GXCurrentRequestContextUtils.java:126–131、205+`）与 MDC（`GXTraceIdContextUtils`），均为 ThreadLocal；`requestFilter` 在订阅线程上取值（第 191–212 行）→ 调用发生在 `@Async`、调度线程、reactor 并行线程（实测重试即在 `parallel-1..3`）或手动线程时，`X-Trace-Id`/`PLATFORM`/`X-Auth-Token` 头**静默缺失**（仅 `isNotBlank` 判断后跳过，无告警）；切面的 `checkTokenValidity()` 在无请求上下文的线程上恒 false → 校验失败被误判为"token 无效"。模块未使用框架既有的 MDC/TraceId 异步包装能力（宪法 V）。
- **E-12**：每请求固定开销：`generateHttpAuthToken()` 每次执行 2 次 `getEnvironmentValue`（token 源 + secret，`GXWebClientService.java:35、87`，内部走 `GXSpringContextUtils.getEnvironment()` + 属性解析）+ 1 次 `GXAuthCodeUtils.authCodeEncode` 加密（token 有效期 `WEB_CLIENT_TOKEN_EXPIRE = 5*30 = 150s`，`GXTokenConstant.java:14`，却**逐请求重新生成**）+ `String.format` User-Agent + 时间戳头；无任何缓存。
- **E-13**：`User-Agent` 内嵌 `System.getProperty("os.name")`（第 187–188 行）→ 每个出站请求向对端暴露服务端操作系统信息（信息泄露面）。
- **E-14**：重试复用同一份已处理 `ClientRequest`（`requestFilter` 在 `retryFilter` 外层，E-01）→ `X-Request-Start-Time`（第 185 行）在退避后已陈旧；该头的语义（对端测延迟用）在重试场景失真。
- **E-15**：`webClient`、`httpServiceProxyFactory`、`httpServiceProxyFactoryBuilder`（第 139–149、327–330 行）无 `@ConditionalOnMissingBean`，下游覆盖即 `BeanDefinitionOverrideException`；`mapleWebClientConnectionProvider`/`mapleWebClientConnector` 有按名 `@ConditionalOnMissingBean`（第 99、118 行）——可覆盖性口径不一致。`@ConditionalOnClass(WebClient.class)`（第 56 行）未守护 `@LoadBalanced` 所需的 loadbalancer 类（当前靠非 optional 依赖兜底）。
- **E-16**：`getConfiguredInteger`（第 348–353 行）对非数字属性在启动期抛转换异常（fail-fast 可接受），但对 `max-connections=0/-1`、`timeout-seconds=0/-1` 等**无钳制/校验**，错误值直达 `ConnectionProvider.builder` 与 Netty 选项，失败形态依赖上游异常文本。
- **E-17**：测试基线 17 条全部通过（见证据 5）；但覆盖缺口集中在：4xx/5xx 仅覆盖 `{"message":...}` 单一错误体形状、无 4xx 中 408/429 重试分支、无并发用例、无真实 HTTP 端到端、无连接池释放验证、无 LB 语义（`@LoadBalanced` 附着）验证、无上下文缺失分支、无超时/非可重放体分支；`LogMessageAsciiTest` 只扫 `src/main/java`。
- **E-18**：`GXWebClientExceptionHandler` 两类文案中英混杂（`GXWebClientExceptionHandler.java:18` 中文 vs `:25` 英文）；且对本模块配置的 `WebClient`，4xx/5xx 已在过滤器链内转为 `GXBusinessException`，`retrieve()` 路径**不会**产生 `WebClientResponseException` → `handleWebClientResponseException` 对自家客户端不可达（仅对外来 `WebClient` 传播的异常有效），语义未文档化。
- **E-19**：`checkTokenValidity` 对解码结果判 `"{}"`（`GXWebClientService.java:75`）与 `GXAuthCodeUtils.authCodeDecode` 的失败语义一致（错钥/畸形/过期/空输入均返回 `"{}"`，`GXAuthCodeUtilsTest` 锁定）→ 该防御是**正确实现**；但 `catch (Exception e) → return false`（第 76–78 行）吞掉一切异常且无日志，误配置（secret 缺失抛 `GXBusinessException`）与"token 真无效"不可区分。
- **E-20**：治理与文档漂移：模块 `pom.xml:18` 在模块内声明 `loadbalancer.version=5.0.3`，与根 POM `spring-cloud-dependencies` 2025.1.3（`spring-cloud-commons.version=5.0.3`）冗余，违反宪法附加约束 2 的"版本只在根 POM `<properties>` 声明 + 冲突覆盖附注"；`AGENTS.md` 2.5/4.2 称"尚未建立有效测试基线"（实际已有 17 条）；`docs/` 下无 `leaf-base-webclient开发手册.md`（宪法交付清单要求）。
- **E-21**：`METHOD_SIGNATURE_CACHE` 为 static `ConcurrentHashMap`（`GXWebClientAuthTokenAspect.java:37`），无清理路径（热部署/多 ClassLoader 下的类加载器滞留风险，量级受限于拦截方法数）；缓存值 `declaringTypeName + "." + name` 丢弃重载区分（仅影响日志可读性）。
- **E-22**：切面语义正确：方法级注解优先于类级（`findAuthTokenAnnotation`，第 83–98 行）、非 `webClient` 注解值跳过（第 61–63 行）、无 `GXWebClientService` Bean 显式抛业务异常（第 64–69 行）、校验失败抛 `GXWebClientAuthTokenException`（第 74–78 行）——既有 4 条用例锁定；多 `GXWebClientService` Bean 时 `getBean` 静默返回 null（E-09 同源）→ 报错文案"Please implement ... interface"误导。
- **E-23**：超时口径：`CONNECT_TIMEOUT`、`ReadTimeoutHandler`、`WriteTimeoutHandler`、`responseTimeout` 共用一个 `maple.framework.web.client.timeout-seconds`（默认 30s，第 122–129 行）→ 连接超时被绑成 30s（偏长）；`ReadTimeoutHandler` 按读空闲断连 → **流式/长轮询响应超过 30s 即被切断**，模块无"流式安全"模式或文档声明。
- **E-24**：token 契约为 fail-fast：只要容器存在 `GXWebClientService` Bean 且未配置 `maple.framework.web.client.token`/`secret`，所有出站调用直接失败（`generateHttpAuthToken`/`getAuthTokenSecret` 抛 `GXBusinessException`，`GXWebClientService.java:36–38、88–90`）；经 requestFilter 捕获转 `Mono.error`（`GXWebClientConfig.java:203–206`）。属既定语义，但无开关可关（不需要 token 的内部调用也被强制要求配置），需在契约中显式声明。

发现清单（分级）：

- **F-01（高·正确性）直连 URL 被 LB 过滤器截杀且失败形态误导**：`@LoadBalanced` 语义未文档化，未知主机得到合成 503 + 3 次假重试 + "服务不可用"文案，与真实原因（目标未注册/不支持直连）完全错位（E-08、E-02）。
- **F-02（高·正确性）远端统一响应错误详情丢失**：`msg`/`message` 字段错位导致框架对端返回的业务错误不可见，排障只能拿到通用文案（E-02）。
- **F-03（高·线程安全）请求上下文 ThreadLocal 与响应式/异步线程断裂**：traceId/platform/token 头在非请求线程静默丢失，切面校验误判；无传播机制、无告警（E-11）。
- **F-04（中·资源）连接池无释放路径**：Bean 无销毁方法推断、非 Spring 路径每次泄漏未托管池 + 后台驱逐线程（E-09、E-10）。
- **F-05（中·正确性）非幂等方法无重试护栏**：网络类失败对 POST/PUT/DELETE 自动重放，重复副作用 + 非可重放体语义未定义（E-03、E-14）。
- **F-06（中·性能）每请求 token 重新生成**：150s 有效期内逐请求 2 次环境查找 + 1 次加密，无缓存（E-12）。
- **F-07（中·可观测）耗时与阈值口径失真**：duration 含重试退避、失败无耗时日志、Content-Length 告警阈值无视配置（E-04、E-05）。
- **F-08（中·安全）敏感头明文入日志**：TRACE 头输出含 token/cookie 明文，掩码契约存在但零调用（E-06）。
- **F-09（中·契约）日志 SPI 为死扩展点**：`logRequestResponse`/`logRequestException` 无调用方，用户覆盖无效（E-07）。
- **F-10（中·契约）两条 builder 创建路径语义分裂**：注解路径带 LB、非注解路径不带；多 Connector/缺失 Connector 时静默降级并泄漏（E-08、E-09）。
- **F-11（中·正确性）超时口径耦合 + 流式响应被切断**：四类超时共用一键，读空闲 30s 断连对流式/长响应不安全且无文档（E-23）。
- **F-12（中·测试）覆盖缺口与 AGENTS.md 第 4 节要求不符**：并发、真实 HTTP、LB 附着、池释放、多形状错误体、上下文缺失分支全部缺失（E-17）。
- **F-13（低·正确性）异常文案中英混杂 + 处理分支可达性未声明**：`GXWebClientExceptionHandler` 两类文案语言不一致；`WebClientResponseException` 分支对自家客户端不可达（E-18）。
- **F-14（低·正确性）`checkTokenValidity` 吞异常无日志**：配置缺失与 token 无效不可区分（E-19）。
- **F-15（低·治理）版本声明与文档漂移**：模块内 `loadbalancer.version` 冗余覆盖、AGENTS.md 陈旧陈述、缺 docs 开发手册（E-20）。
- **F-16（低·安全）User-Agent 泄露服务器 OS**（E-13）。
- **F-17（低·健壮性）配置值无钳制**：0/负数直入连接池与 Netty 选项；`METHOD_SIGNATURE_CACHE` 静态缓存无清理路径（E-16、E-21）。
- **F-18（低·契约）Bean 可覆盖性口径不一致**：`webClient`/`HttpServiceProxyFactory*` 无 `@ConditionalOnMissingBean`，下游只能靠 BeanDefinitionOverride 硬顶（E-15）；多 `GXWebClientService` Bean 时报错文案误导（E-22）。
- **F-19（低·契约）token fail-fast 无开关**：不需要 token 的调用方也被强制配置（E-24）。

已确认为**正确实现**（不得在修复中被破坏）：

- **P-01**：token 生成失败 fail-fast 不重试（requestFilter 短路 retryFilter，E-01；用例 `webClientFailsFastWhenTokenGenerationFails` 锁定）。
- **P-02**：可重试状态码（408/429/5xx）固定延迟 1+3 次重试，耗尽后抛**原始**失败（`onRetryExhaustedThrow` 透传 `retrySignal.failure()`，第 286 行），状态码经 `GXBusinessException.code` 保真（用例锁定 4 次尝试 + code=503）。
- **P-03**：`checkTokenValidity` 对 `"{}"` 哨兵的防御与 `GXAuthCodeUtils` 失败语义严格一致（E-19）。
- **P-04**：连接池/连接器 Bean 均有 `@ConditionalOnMissingBean(name=...)` 覆盖点、配置键有默认值；`LogMessageAsciiTest` 守卫日志 ASCII（AGENTS 硬性规则 1）。
- **P-05**：4xx/5xx → `GXBusinessException` 且 `code` 保留远端 HTTP 状态，上层可按码分流（消息保真问题单列 F-02，不影响 code 语义）。
- **P-06**：切面注解解析优先级（方法 > 目标类 > 声明类）、非 `webClient` 值跳过、缺失 Bean 显式报错、类级注解生效（4 条用例锁定，E-22）。
- **P-07**：`GXWebClientService` 全部 default 方法可覆盖；blank source/expiry<=0 显式拒绝、HMAC 委托、no-op 日志钩子安全、`getTraceId` 属性优先/MDC 回落（6 条用例锁定）。
- **P-08**：`webClientBuilder` 为 prototype 作用域（可变 builder 不共享，用例 `webClientBuilderBeanIsPrototypeScoped` 锁定），请求/响应过滤器无可变共享状态。

## Clarifications

### Session 2026-10-23

- Q: 本次交付是"只给审查结论"还是"结论 + 直接修复"？ → A: 按 `/speckit.specify` 职责边界，本次交付**可验收规格 + 可复核结论**；生产代码修复与配套测试在 `/speckit.plan` → `/speckit.implement` 阶段执行。用户"改完代码必须做完备测试用例"固化为 FR-019～FR-023 硬门槛。
- Q: 下游影响面多大？能否自由修改？ → A: `leaf-base-eureka-client` 编译依赖本模块并锁定装配顺序与 Bean 契约（证据 6），属**已识别下游**；修复受宪法 II 约束，Bean 名称、作用域、配置键、默认请求头名、异常结构、过滤器顺序、重试/超时默认值全部列入冻结清单（FR-009），变更须兼容说明与迁移路径。
- Q: `@LoadBalanced` 只支持服务名寻址是缺陷还是设计？ → A: 配合 eureka/服务发现栈是**设计意图**，保留为默认契约（Option A）；修复限定为：①合成失败与远端失败可区分（FR-011）；②语义与限制文档化；③提供语义一致的直连创建出口（新增能力，不改变默认 Bean 行为）。禁止"顺手摘掉 `@LoadBalanced`"。
- Q: 非幂等方法自动重试、敏感头入日志、连接池泄漏这类"危险默认"能否直接改？ → A: 此三类按**缺陷修复**处理（而非行为契约）：允许收敛，但必须给出旧行为的失败用例证据、兼容说明与可关闭开关（如需要）；其余默认值（重试次数 3、延迟 1000ms、超时 30s、池参数、头名称）一律不变。
- Q: 上下文透传修复到什么程度？ → A: 两条等价验收路线任选其一并落到文档：①在异步/响应式线程上**显式传播**请求上下文（复用框架既有的 MDC/TraceId 包装能力）；②显式声明"仅请求线程保证透传"的契约并补检测告警。无论哪条，"静默丢失"必须消失（FR-010）。
- Q: 性能结论按什么口径判定？ → A: 不设绝对吞吐/时延门禁；保留可复算口径（每请求 token 生成次数、环境查找次数、重试时延构成、连接池参数与释放路径）与"修复前后同口径对比"要求（FR-007、SC-006）。
- Q: 测试完备性的最低标准是什么？ → A: 任何生产改动同变更补齐受影响路径的成功/失败/异常/空值/边界用例、涉及并发/上下文/资源的补并发与生命周期用例、涉及装配/AOP/过滤器链的补 Spring Boot 集成用例；每修复项附"旧代码失败、新代码通过"证据；`mvn -pl leaf-base-webclient test` 全绿且用例数从 17 单调增长；不得删除/跳过既有用例（P-01、P-02、P-04、P-06、P-07 为必须保留断言）。

### Session 2026-10-23（`/speckit.analyze` 复审补充）

- Q: LB 守卫如何避免误杀合法服务名（如 `svc.ns.svc.cluster.local`）？ → A: 守卫判定为"白名单优先"：`maple.framework.web.client.lb-guard-allow-hosts`（集合，默认空）中的主机一律放行；其余按"IP 字面量 / `localhost` / 含 `.` / 带端口字面量 → 拒绝（400，0 次尝试）"判定；简单服务名 `^[A-Za-z0-9_-]+$` 放行。同时提供逃生口：正式开关为宪法 IV 命名的 `maple.framework.enable.webclient-lb-guard`（默认 `true`，配置后优先），`maple.framework.web.client.lb-guard-enabled` 保留为兼容别名；关闭后完全回到旧行为（交 LB 过滤器，含合成 503）。守卫默认开启属"危险默认收敛"范畴，按缺陷修复登记，并在手册显式声明。
- Q: token 快照里是否生成 token？ → A: `capture()` **不生成** token（token 依赖 `Environment` 而非 ThreadLocal，本就不会随线程丢失）；快照字段为 `traceId`/`platform` + **可选** `authToken`（调用方显式提供时优先使用）；`authToken` 为空时仍由 `requestFilter` 在订阅线程生成，生成失败继续按 P-01 fail-fast。快照不承担 token 生成职责，避免在无上下文线程引入新的失败面。
- Q: 上下文缺失告警的限流怎么实现与测试？ → A: 限流状态是 `GXWebClientConfig` 的**实例字段**（非静态，单例 Bean 按应用上下文隔离），窗口常量 `CONTEXT_WARNING_INTERVAL_MILLIS = 30_000` 为同包（package-private）可覆写字段；测试类与配置类同包，直接设置窗口而**不使用反射**（满足 FR-023）。限流不新增配置键。
- Q: token 缓存窗口与 SC-006 的"每窗口 1 次"如何统一？ → A: 窗口定义为 **60s 且 ≤expiry/2**（`expiry=150s` 时取 60s）；SC-006 与 US5 验收场景 2 已改为"60s 窗口内 1 次、150s 有效期内 ≤3 次"，不再使用笼统的"每窗口 1 次"。
- Q: 为什么允许新增 `GXWebClientErrorResDto`（与既有 `GXHttpInvokerApiErrorResDto` 近似）？ → A: 既有 DTO 属 `leaf-base-framework` 公共契约（宪法 II，本特性假设"禁止修改"），为其补 `msg` 字段即是跨模块破坏性变更；模块内新增双形状解析 DTO 是当前约束下的最小方案，理由与替代方案（Map/JsonNode 解析）一并记入 plan 的 Complexity Tracking，并禁止再为其它用途复制该 DTO。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 审查结论可复核 (Priority: P1)

框架维护者要判断"该模块能否安全用于生产环境的服务间调用"，需要按维度分级、可逐条跳到代码位置的结论，并能用模块自带测试与最小复现步骤验证。

**Why this priority**: 审查价值取决于结论可信度；F-01～F-03 直接改变线上行为（假 503、错误不可见、上下文丢失）。

**Independent Test**: 随机抽取一条结论，按报告给出的文件与行区间定位，用报告给出的命令或用例设计复现同一现象。

**Acceptance Scenarios**:

1. **Given** 结论"远端统一响应错误详情丢失"，**When** 用 `{"code":500,"msg":"remote detail"}` 形状的 500 响应走错误转换链路，**Then** 观测到调用方拿到的异常消息为通用文案而非 `remote detail`。
2. **Given** 结论"直连 URL 被 LB 过滤器截杀"，**When** 经 `@LoadBalanced` 装配的客户端请求 `https://api.example.com`，**Then** 不产生真实网络请求，返回合成 503 并触发 4 次尝试后报"服务不可用"。
3. **Given** 结论"duration 含重试退避"，**When** 构造 3 次重试后成功的请求，**Then** 耗时日志 ≈ 真实耗时 + 3×退避间隔（而非单次真实耗时）。
4. **Given** 结论"连接池无释放路径"，**When** 关闭持有该 Bean 的应用上下文，**Then** 连接池 `dispose()` 未被调用、后台驱逐线程仍存活。

---

### User Story 2 - 出站请求语义可判定 (Priority: P1)

接入方需要明确知道：什么样的 URL 能调通、失败时能看到什么、会不会自动重试、重试会不会重复副作用。

**Why this priority**: 寻址与失败语义判断错误会放大为"外网调用莫名失败""支付类 POST 重复提交"。

**Independent Test**: 分别用服务名 URL、直连 URL、4xx/5xx/网络异常 × 幂等/非幂等方法构造 6 类组合，记录最终异常、尝试次数与副作用次数。

**Acceptance Scenarios**:

1. **Given** 服务名 URL 且注册中心可达，**When** 发起调用，**Then** 按服务实例负载均衡转发（现状保持）。
2. **Given** 直连 URL，**When** 发起调用，**Then** 失败原因必须可区分"目标未注册/不支持直连"与"远端真实不可用"，且错误文案不再误报"服务不可用"（或文档+出口方案落地后按新契约验收）。
3. **Given** 对端返回框架统一响应错误体，**When** 4xx/5xx 转换完成，**Then** 调用方异常消息包含对端 `msg` 详情（F-02 修复验收）。
4. **Given** POST 请求发生连接重置，**When** 重试策略评估，**Then** 默认不得对非幂等方法静默重放（或有显式护栏/开关且文档声明）。

---

### User Story 3 - 并发与上下文透传安全 (Priority: P1)

维护者需要知道：多线程/异步/响应式链路上发起调用时 traceId、平台标识、token 是否可靠透传，切面校验是否会被线程切换误判。

**Why this priority**: 上下文丢失是静默故障：链路追踪断链、灰度路由失效、鉴权误判，且无任何报错。

**Independent Test**: 在请求线程、`@Async` 线程、调度线程、reactor 并行线程四种形态下各发起一次调用，对比出站头集合与切面校验结果。

**Acceptance Scenarios**:

1. **Given** 请求线程内发起调用，**When** 过滤器执行，**Then** `X-Auth-Token`/TraceId/平台头齐全（现状保持，回归锁定）。
2. **Given** 无线程上下文的异步线程发起调用，**When** 过滤器执行，**Then** 头缺失不再静默：要么被显式传播补齐，要么产生可定位的告警/契约文档声明（按 FR-010 选定路线验收）。
3. **Given** 50 个线程并发创建 builder 并发起请求，**When** 全部完成，**Then** 无共享状态错乱、无过滤器串扰、头内容互相不污染。
4. **Given** 切面在无请求上下文线程执行校验，**When** 判定 token 有效性，**Then** 结果语义可区分"无上下文"与"token 无效"（不得一律误判无效）。

---

### User Story 4 - 资源生命周期可释放 (Priority: P2)

运维需要保证：应用关闭/重载后连接池、后台线程、连接不残留；非 Spring 手工创建路径同样不泄漏。

**Why this priority**: 基础框架被多应用引用，泄漏在热部署与多上下文场景下累积为线程/句柄耗尽。

**Independent Test**: 创建并关闭上下文各 10 次，统计活跃连接池数与后台线程数；再以非 Spring 路径调用 `webClientBuilder()` 10 次做同样统计。

**Acceptance Scenarios**:

1. **Given** 上下文关闭，**When** 销毁钩子执行，**Then** 连接池被释放（`dispose()` 被调用），后台驱逐线程退出。
2. **Given** 非 Spring 路径连续调用创建方法，**When** 用完弃置，**Then** 不残留未托管连接池（或该路径被显式声明"调用方负责释放"并提供释放句柄）。
3. **Given** 重复关闭/未初始化即关闭，**When** 销毁执行，**Then** 幂等、不抛异常（边界保持）。

---

### User Story 5 - 性能开销可复算、修复可证 (Priority: P2)

维护者需要把"每请求固定开销、重试时延构成"折算为可复算口径，判断修复是否真实降低开销。

**Why this priority**: 无口径的"较慢"无法转化为改造计划，也无法验收优化效果。

**Independent Test**: 按口径统计单请求 token 生成/加密次数、环境查找次数、对象分配、日志条数；修复前后同口径对比。

**Acceptance Scenarios**:

1. **Given** 一次普通出站调用，**When** 统计固定开销，**Then** 报告给出"token 生成次数 / 环境查找次数 / 加密次数 / 日志条数"清单并可逐条对应代码位置。
2. **Given** 修复引入 token 缓存或等价优化，**When** 同口径重测，**Then** 同一 `source+secret` 下 60s 窗口内 token 生成次数由"每请求 1 次"降为 **1 次**（150s 有效期内 ≤3 次）；窗口或失效条件变化必须同步本场景与 SC-006 的口径。
3. **Given** 一次 1+3 重试的请求，**When** 分解时延构成，**Then** 报告给出"网络耗时 vs 退避等待"的分离口径，修复后 duration 日志不再混计退避等待。

---

### User Story 6 - 测试完备与契约一致 (Priority: P3)

交付验收方需要核对：改动是否都带回归证据、测试是否覆盖 AGENTS.md 第 4 节清单、文档/治理声明是否与实现一致。

**Why this priority**: 用户明确提出"改完代码一定做完备测试"；契约漂移不直接报错，但会让下游按错误前提设计。

**Independent Test**: 抽取任一修复项核对"旧代码失败证据 + 新增用例 + 全绿输出"；逐条核对 AGENTS.md/文档声明与实现。

**Acceptance Scenarios**:

1. **Given** 任一修复项，**When** 验收核对，**Then** 存在同变更的失败→通过回归用例与"新增/修改/保留"用例清单。
2. **Given** AGENTS.md 称"尚未建立有效测试基线"，**When** 核对现状，**Then** 该陈述被修正（或列为待修文档项）。
3. **Given** 模块内 `loadbalancer.version` 声明，**When** 核对根 POM BOM 管理版本，**Then** 冗余覆盖被收敛或附冲突说明（治理判定可复核）。

---

### Edge Cases

- 配置缺失/非法：`maple.framework.web.client.token` 与 `secret` 均缺失、仅缺其一、空串、纯空白；`timeout-seconds`/`max-connections`/`memory-buffer-size-mb` 为 0、负数、非数字、超大值；`retry-max-attempts` 为 0/1/负数；`idle-timeout-millis`/`max-life-time-millis` 为 0；`read/response-timeout-millis` 为 0（禁用）与负数（拒绝）；`lb-guard-enabled` 缺失/`false`；`lb-guard-allow-hosts` 空、含空白项、含 IP 与端口字面量。
- token 语义：`expiry=0/-1`；解码返回 `"{}"`（错钥/过期/畸形）；token 头存在但为 `" "`；`PLATFORM` 头缺失/空白。
- 错误体形状：框架统一响应 `{"code","msg","data"}`、Spring 默认 `{"timestamp","status","message","path"}`、纯文本 HTML 错误页、空 body、非法 JSON、超大 body、`Content-Length` 缺失/伪造/非数字。
- 状态码矩阵：200/204/3xx/400/401/403/404/408/429/500/502/503/504（408/429 重试分支当前零覆盖）。
- 寻址形态：服务名（有实例/无实例/简单名）、**带点服务名**（`svc.ns.svc.cluster.local`，需经 `lb-guard-allow-hosts` 放行）、FQDN、IP 字面量、`localhost`、URL 无 host、含下划线主机名、`lb-guard-enabled=false` 时的旧行为回退。
- 并发形态：并发取 builder、并发请求、并发触发重试、请求线程与异步线程交叉、上下文在请求中途被清除。
- 生命周期：上下文关闭时有 in-flight 请求；连续创建/关闭 10 个上下文；非 Spring 路径连续调用；重复销毁；`JsonMapper` Bean 缺失/多个；`ReactorClientHttpConnector` Bean 缺失/多个；多个 `GXWebClientService` Bean。
- 响应形态：流式/分块响应持续 > timeout、长轮询、`SSE` 消费（读空闲断连）、压缩响应（`compress(true)`）。
- 日志安全：TRACE 开启时头输出、`User-Agent` 的 `os.name` 为空、异常堆栈中的 URL 参数敏感信息。

## Requirements *(mandatory)*

### Functional Requirements

**审查交付**

- **FR-001**: 审查 MUST 覆盖全部 8 个生产源码文件、1 个自动装配清单、5 个既有测试类，以及 `AGENTS.md`、`pom.xml` 两份契约源；每项 MUST 有明确判定（无缺陷 / 风险 / 缺陷 / 不适用原因），不得跳过。
- **FR-002**: 每条发现 MUST 标注维度（逻辑正确性 / 线程安全 / 性能与资源 / 契约与治理）、严重级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件、影响面、证据、复现方式、回归测试设计八要素。
- **FR-003**: 仅凭阅读的判断 MUST 标注为"未验证假设"并给出验证步骤；已确认项 MUST 附既有用例、实测输出或上游源码/字节码证据（本规格 E-01～E-24 为证据基线，审计报告可直接引用）。
- **FR-004**: 审查 MUST 以上游构件为准核对五项语义：`@LoadBalanced` 附着范围与 serviceId 取法（spring-cloud-commons 5.0.3）、`ConnectionProvider` 释放契约（reactor-netty）、请求上下文的 ThreadLocal 形态、`authCodeDecode` 的 `"{}"` 失败语义、错误体 DTO 与统一响应的字段映射；核对结论 MUST 可按 jar/类/方法复核。
- **FR-005**: 请求链路语义 MUST 按"过滤器顺序 × 请求/响应阶段 × 重试交互"给出唯一定性矩阵（token 短路、4xx/5xx 转换、重试捕获、耗时口径归属各是什么行为），不得含糊。
- **FR-006**: 线程安全结论 MUST 覆盖三类场景：共享状态（过滤器、缓存、连接池、编解码器）、上下文（ThreadLocal/MDC 在异步与响应式线程的可达性）、生命周期（Bean 创建/销毁、非 Spring 路径、重复销毁）。
- **FR-007**: 性能结论 MUST 给出可复算口径：每请求 token 生成/加密次数、环境查找次数、固定对象分配与日志条数、重试时延构成（网络 vs 退避）、连接池参数与后台线程占用。
- **FR-008**: 既有测试 MUST 全部执行并记录结果（本次基线：17 执行 / 0 失败，分文件计数齐全）；MUST NOT 删除或跳过用例来"通过"；覆盖缺口 MUST 单列为发现（对应 F-12）。

**修复约束（契约冻结与缺陷收敛）**

- **FR-009**: 修复 MUST 保持冻结契约：Bean 名称（`mapleWebClientConnectionProvider`/`mapleWebClientConnector`/`webClientBuilder`/`webClient`/`httpServiceProxyFactoryBuilder`/`httpServiceProxyFactory`）、`webClientBuilder` 的 prototype 作用域与 **4 参方法签名**、既有 `maple.framework.web.client.*` 配置键与默认值（超时 30s、池 500/3000ms/1000/30s/300s、重试 3 次/1000ms、缓冲 16MB；**新增键只增不改**）、默认请求头名称与增删条件、4xx/5xx → `GXBusinessException` 的结构与 `code` 语义、**既有过滤器 #1–#4 的相对注册顺序**（新增过滤器只能插入内侧且需说明，见 `contracts/filter-chain-contract.md`）、`@AutoConfiguration` 的可排序性（eureka-client 的 `after = GXWebClientConfig` 依赖）；变更任一项 MUST 附兼容说明、迁移路径与受影响清单（至少含 `leaf-base-eureka-client`）。
- **FR-010**: 上下文透传 MUST 消除"静默丢失"：异步/调度/响应式线程上发起调用时 traceId/platform/token 头要么被显式传播（复用框架既有 MDC/TraceId 包装能力），要么产生可定位告警并有文档契约；切面在无请求上下文线程上的判定语义 MUST 可区分"无上下文"与"token 无效"（对应 F-03）。
- **FR-011**: 错误可见性 MUST 修复字段错位：对端返回统一响应（`code`/`msg`/`data` 形状）时，异常消息 MUST 携带对端 `msg` 详情；"目标未注册/不支持直连"与"远端不可用"两类合成失败 MUST 可区分（错误码或消息维度），不得再以"服务不可用"笼统覆盖（对应 F-01、F-02）。
- **FR-012**: 重试 MUST 增加幂等护栏：非幂等方法（POST/PUT/DELETE）MUST NOT 被网络类失败静默自动重放（改为不重试、或默认要求显式声明幂等、或提供开关且默认安全）；非可重放请求体的失败语义 MUST 显式定义并文档化；`X-Request-Start-Time` 在重试场景的语义 MUST 修正或声明（对应 F-05）。
- **FR-013**: 资源释放 MUST 闭环：应用上下文关闭时连接池 MUST 被释放且后台驱逐线程退出；非 Spring 创建路径 MUST 要么复用受管组件、要么提供明确的释放句柄/责任约定，MUST NOT 每次调用泄漏未托管池（对应 F-04）。
- **FR-014**: 日志安全与口径 MUST 修复：敏感头（`X-Auth-Token`/`Authorization`/`Cookie`/`Set-Cookie` 等）MUST NOT 以明文进入任何级别日志（启用 `getSensitiveHeaderFields` 掩码或等价方案）；duration 口径 MUST 分离重试退避与真实耗时（或分段计量）；终态失败 MUST 有耗时/失败日志；Content-Length 告警阈值 MUST 使用配置值；User-Agent MUST 不泄露服务器 OS 信息（或提供兼容开关并默认收敛）（对应 F-07、F-08、F-16）。
- **FR-015**: 死扩展点 MUST 处置：`logRequestResponse`/`logRequestException` 要么在链路中被真实调用（含异常路径），要么移除并给出迁移说明；`getSensitiveHeaderFields` 同理；处置后 SPI 契约变化 MUST 进入兼容说明（对应 F-09）。
- **FR-016**: 超时语义 MUST 明确：连接/读/写/响应四类超时的口径与耦合关系 MUST 文档化；流式/长响应被读超时切断的行为 MUST 要么提供分离配置/安全模式，要么显式声明为限制；新增的 `connect/read/write/response-timeout-millis` 四个键 MUST 实际接线到连接器与 Netty 处理器（null → 回退 `timeout-seconds×1000`；read/response 的 0 = 禁用），**不得只声明键而无消费方**（对应 F-11）。
- **FR-017**: 创建路径 MUST 一致或差异显式化：注解装配路径与手工 `webClientBuilder()` 路径的能力差异（LB 过滤器、Connector 获取、JsonMapper 降级）MUST 对齐或文档化；`webClient`/`HttpServiceProxyFactory*` Bean 的可覆盖性口径 MUST 统一（补 `@ConditionalOnMissingBean` 或说明为何禁止覆盖）；多 `GXWebClientService` Bean 场景的报错 MUST 不误导（对应 F-10、F-18）。
- **FR-018**: 配置健壮性 MUST 补齐：连接池与超时类配置的 0/负数/非法值 MUST 有明确钳制或 fail-fast 文案；`checkTokenValidity` 的异常吞噬 MUST 增加可定位日志或区分错误类别；token fail-fast 是否提供关闭开关 MUST 给出结论并文档化（对应 F-14、F-17、F-19）。

**测试门槛（用户硬性要求）**

- **FR-019**: 任何生产代码改动 MUST 在同一变更中补齐测试：受影响路径的成功、失败、异常、空值、边界用例各至少 1 条；涉及并发/上下文/共享状态/资源的改动 MUST 补并发与生命周期用例；涉及自动装配、AOP、过滤器链、请求上下文的改动 MUST 补接近真实运行形态的 Spring Boot 集成用例（量化门槛以 FR-021 为准，本条只约束覆盖范围）。
- **FR-020**: 每个修复项 MUST 附"先在旧代码上失败"的用例证据（用例名 + 失败断言 + 通过对照；纯新增能力无旧代码可比时 MUST 在证据表标注"新增"），仅补实现不补用例的变更视为未完成。
- **FR-021**: 交付前 MUST 提供 `mvn -pl leaf-base-webclient test` 全绿输出（含总数与分文件计数），用例总数 MUST 从 17 单调增长；MUST 给出"新增 / 修改 / 保留"清单，保留清单 MUST 包含 P-01/P-02/P-04/P-06/P-07 对应既有断言。
- **FR-022**: 并发与生命周期用例 MUST 至少覆盖：多线程上下文透传（四种线程形态：请求线程 / `@Async` 线程池 / 调度线程 / reactor 并行线程；虚拟线程可作补充形态）、并发创建/使用 builder、连接池关闭释放（含重复销毁幂等）、非 Spring 路径资源责任；依赖外部环境的用例 MUST 可优雅跳过（宪法 VI）。
- **FR-023**: 错误体与状态码用例 MUST 覆盖：统一响应形状、Spring 默认形状、纯文本/空/非法 JSON body、408/429 重试分支、非幂等方法重试护栏；测试 MUST NOT 依赖反射破坏封装构造现场（可测扩展点优先）。
- **FR-024**: 文档与治理 MUST 同步：AGENTS.md 的陈旧陈述（测试基线）修正、限制说明（寻址语义、流式超时、上下文约束、**非可重放请求体的重试语义**、**token fail-fast 既定语义与其不提供关闭开关的结论**）落档、`GXWebClientExceptionHandler` 文案语言一致性与 `WebClientResponseException` 分支可达性声明落档（详见 FR-026）、`docs/leaf-base-webclient开发手册.md` 缺失给出处置结论（补齐或明确豁免）、模块内 `loadbalancer.version` 收敛到根 POM 或附冲突说明。
- **FR-025**: token 生命周期 MUST 可复用：`GXWebClientService.generateHttpAuthToken()` 默认实现在 **≤60s 且 ≤expiry/2** 的窗口内 MUST 复用上次生成结果，并按 `source + secret` 失效；方法签名、异常类型与消息语义 MUST 保持不变；窗口与失效行为 MUST 有可复算用例（对应 F-06、SC-006）。
- **FR-026**: 异常处理器一致性 MUST 落地：`GXWebClientExceptionHandler` 两条处理器文案 MUST 语言一致（统一中文，返回码语义冻结不变）；`WebClientResponseException` 分支对本模块客户端不可达的事实 MUST 在手册中声明（对应 F-13）。

### Key Entities *(include if data involved)*

- **审查发现（Finding）**：编号、维度、级别、位置、触发条件、影响面、证据、复现方式、回归测试设计、修复建议。
- **请求链路语义矩阵**：过滤器 × 阶段（请求/响应）× 行为（头注入、耗时口径、错误转换、重试捕获、短路条件）。
- **上下文透传矩阵**：线程形态（请求线程 / 异步 / 调度 / reactor 并行）× 头（token / TraceId / PLATFORM）× 现状 × 修复后语义。
- **资源生命周期矩阵**：组件（连接池 / 驱逐线程 / 连接器 / builder）× 获取方式（Bean / 手工）× 释放路径 × 现状判定。
- **性能口径**：每请求 token 生成与加密次数、环境查找次数、固定分配清单、日志条数、重试时延构成、池参数。
- **契约冻结清单**：Bean 名称/作用域、配置键/默认值、请求头、异常结构、过滤器顺序、自动装配顺序、SPI 方法签名。
- **测试矩阵**：修复项 × 用例类型（成功/失败/异常/空值/边界/并发/生命周期/集成）× 旧代码失败证据 × 保留断言。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查覆盖 100% 的 8 个生产源码文件、1 个装配清单、5 个测试类与 2 份契约源，判定率 100%；新维护者可在 15 分钟内按报告定位并复现任一结论。
- **SC-002**: 100% 的发现具备八要素（级别 + 维度 + 位置 + 触发 + 影响 + 证据 + 复现 + 回归设计）；0 条未验证假设混入已确认项。
- **SC-003**: 高危发现（F-01/F-02/F-03）100% 具备可构造的故障现场与"旧代码失败"回归设计，计划阶段无需补充调研即可拆分为任务。
- **SC-004**: 修复后三条核心性质各有 ≥1 条自动化用例且通过率 100%：远端 `msg` 保真、异步线程上下文不静默丢失、上下文关闭后连接池归零（含后台线程）。
- **SC-005**: 测试数量从 17 单调增长、0 删除/跳过；每个修复项的"旧代码失败证据"覆盖率 100%；`mvn -pl leaf-base-webclient test` 全绿可复现。
- **SC-006**: 性能三项口径（token 生成次数、环境查找次数、重试时延构成）以**计数器精确相等**判定，不使用百分比容差：①token 生成次数——同一 `source+secret` 的 60s 窗口内恰为 1 次（150s 有效期内 ≤3 次，替代"每请求 1 次"）；②环境查找次数——属性绑定后用例断言为 0（原为每请求 2 次）；③重试时延构成——`durationMs` 与 `retryDelayMs` 之和等于总墙钟（±50ms 定时容差）。MUST NOT 以绝对吞吐/时延数值作为通过门槛。
- **SC-007**: 契约回归 0 破坏：既有 17 条用例全部通过，`leaf-base-eureka-client` 模块编译与测试不受影响（跨模块验证命令与结果入报告）。
- **SC-008**: 文档/治理偏差（AGENTS.md 陈述、docs 手册缺失、版本声明位置）100% 修正或显式登记为遗留项并给出计划。

## Assumptions

- 目标运行形态为 JDK 21+ + Spring Boot 4.1.x + Spring Framework 7.x + Spring Cloud 2025.1.x（BOM 2025.1.3）；上游语义以本地仓库 `spring-cloud-commons-5.0.3`、`reactor-netty-core` 1.3.x 字节码为准，升级版本导致的行为差异不在本次范围。
- 下游影响面以全仓检索为准：当前已知调用方为 `leaf-base-eureka-client`（编译依赖 + 装配顺序 + Bean 契约引用）；下游自建项目的使用面不可知，修复的兼容说明按"框架公共契约"级别发布。
- `@LoadBalanced` 服务名寻址是配合服务发现栈的设计意图，保留为默认契约；直连 URL 的支持以"可区分失败 + 文档 + 一致出口"为目标，不改变默认 Bean 行为。
- 按宪法 II，除"危险默认"三类（非幂等自动重试、敏感头入日志、资源泄漏，视为缺陷）与 LB 守卫默认开启（F-01 缺陷收敛，附白名单与 `lb-guard-enabled` 逃生口）外，全部默认值与行为保持不变；变更默认值须走兼容说明流程（过滤器链增量另见 plan Complexity ④）。
- 本次为规格交付；生产代码修复在 `/speckit.plan` / `/speckit.implement` 阶段执行，受 FR-019～FR-023 测试门槛约束；"改完代码必须做完备测试"为验收硬条件。
- 测试不依赖外部中间件（注册中心、真实对端服务）：LB 附着语义用可控 stub 验证，真实 HTTP 语义用 JDK 自带 `HttpServer` 或等价本地回环端点完成（与 `leaf-base-eureka-client` 测试技术选型一致）。
- 性能数据以单机、单 JVM、本地回环为前提；结论以"可复算口径 + 前后对比"验收，不承诺生产容量。
- 既有用例锁定的行为（P-01/P-02/P-04/P-06/P-07）按既定契约处理，修复只能在不破坏这些断言的前提下进行。
