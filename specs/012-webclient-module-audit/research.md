# Research: leaf-base-webclient 修复技术决策（Phase 0）

**Date**: 2026-10-23 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Technical Context 无未知项（语言/依赖/测试/平台/规模均来自实测基线），无 `NEEDS CLARIFICATION` 残留。以下 10 项决策覆盖 spec 全部修复方向（F-01～F-19）与三处上游语义依赖。

---

## R-01 上下文透传修复（F-03，高危）

**Decision**: 采用"**显式上下文快照 + ThreadLocal 回落 + 缺失告警**"三段式：
1. 新增 `cn.maple.webclient.service.GXWebClientContextSnapshot`：`capture()` 从 `RequestContextHolder`/MDC 提取 traceId、platform、token 源引用，`apply(ClientRequest.Builder)` 注入对应头；常量 `ATTRIBUTE_KEY` 供 `.attribute()` 挂载。
2. `requestFilter` 取值顺序：`ClientRequest.attribute(ATTRIBUTE_KEY)` 快照 → 现行 ThreadLocal/MDC 路径 → 三者皆空时输出**限流 WARN**（每 N 秒至多 1 条，ASCII 文案）并跳过头注入。
3. 传播入口：调用方在发起请求处 `.attribute(GXWebClientContextSnapshot.ATTRIBUTE_KEY, GXWebClientContextSnapshot.capture())`（快照在**调用线程**捕获）；异步包装场景优先复用框架既有 `GXMdcWrapper*` 能力做线程池侧传播。

**Rationale**: 已按 `spring-webflux-7.0.9-sources.jar` 核实 `DefaultWebClient.exchange()`（第 462–464 行）在 `Mono.defer` 内执行 `finalFilterFunction.apply(...).exchange(request)`——**过滤器链在订阅时、订阅线程执行**；而请求规格与 `attribute` 在装配时（调用线程）构建（`initRequestBuilder()`，第 445 行）。因此"调用线程捕获快照 → 订阅线程读 attribute"是唯一不依赖订阅时机的确定性通道；ThreadLocal 回落保持同步调用现状零变化；限流 WARN 消灭"静默丢失"（FR-010 验收底线）。

**Alternatives considered**:
- *仅文档声明"只支持请求线程"*：被拒——spec FR-010 明确"静默丢失必须消失"，纯文档不满足验收路线 2 的告警要求。
- *Reactor Context（`contextWrite`）全量传播*：被拒为主方案——要求调用方整链路响应式改造，侵入面大；作为快照 API 之上的进阶通道在手册中提及。
- *包装 `WebClient` 接口在 `method()` 处自动捕获*：被拒——需代理 15+ 个 spec 接口方法，复杂度不被证明（宪法"复杂度必须被证明"）。
- *InheritableThreadLocal*：被拒——线程池复用线程导致串扰，行业共识禁用。

---

## R-02 `@LoadBalanced` 寻址守卫与直连出口（F-01，高危）

**Decision**: 三件套：
1. **LB 守卫过滤器**（并入 `webClientBuilder` 构建，标志位 `lbMode`）：`lbMode=true` 且请求主机形态为 FQDN（含 `.`）/IP/`localhost`/带端口字面量时，**在 LB 解析前**直接返回 `Mono.error(GXBusinessException)`，错误码取 400 段（不在重试集合内），文案点明"负载均衡客户端仅支持服务名寻址，直连请使用非 LB builder"；服务名形态（无点简单名）放行。**复审补充**：合法带点服务名经 `lb-guard-allow-hosts` 白名单放行；`lb-guard-enabled=false` 时整个守卫关闭并回退旧行为（详见 spec Clarifications 复审条目与 filter-chain-contract 寻址模式表）。手工 `webClientBuilder()` 路径 `lbMode=false` 不设守卫。
2. **直连出口**：新增 prototype Bean `webClientDirectBuilder`（无 `@LoadBalanced`，其余过滤器/连接池语义与默认 builder 完全一致），命名入契约附录（新增不破坏）。
3. **文档化**：手册与 AGENTS.md 写明"默认 `webClient` 仅服务名寻址"的既定设计（配合 eureka/服务发现栈）。

**Rationale**: 已按 `spring-cloud-commons-5.0.3-sources.jar` 核实 `ReactorLoadBalancerExchangeFilterFunction.filter`（`serviceId = originalUrl.getHost()`，**无直连旁路**；无实例返回合成 503 `ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)`，不发网络请求）。合成 503 会被现有错误转换 + 重试放大为"4 次假尝试 + 误报服务不可用"。主机形态前置判定是确定性的、可单测的，且不依赖 spring-cloud 内部实现细节（不靠嗅探合成响应）；守卫错误码选非重试段，天然短路假重试。

**Alternatives considered**:
- *去掉 `@LoadBalanced`*：被拒——eureka-client AGENTS.md 明文冻结该 Bean 契约，且是服务发现栈设计意图（spec Clarifications Q3）。
- *嗅探合成响应（空头/报文体匹配）*：被拒——依赖 spring-cloud 报文细节，版本升级易碎。
- *在 `handle4xx/5xxError` 中对 503 做二次分类*：被拒——响应已过 LB，无法区分合成与真实 503 的信息在响应端已丢失。

---

## R-03 错误体双形状解析（F-02，高危）

**Decision**: 在模块内新增 `GXWebClientErrorResDto`（或等价 `Dict`/`Map` 提取器）解析错误体，字段兼容两种形状：
- 框架统一响应：`{"code":int,"msg":String,...}` → 提取 `msg`；
- Spring 默认错误体：`{"status":int,"message":String,...}` → 提取 `message`；
- 纯文本/空/非法 JSON：回落 `getHttpStatusDescription`（现状行为不变）。
提取优先级 `msg` → `message` → 通用文案；`code` 优先取 body 内 code，缺省用 HTTP 状态。**不修改 `leaf-base-framework` 的 `GXHttpInvokerApiErrorResDto`**。

**Rationale**: 字段错位根因（`message` vs `msg`）在框架 DTO 上，但 AGENTS 硬性规则 1 禁止跨模块修改；模块内双形状 DTO 是最小闭合修复且向后兼容（旧形状解析行为保持）。`bodyToMono(String.class)` + 本地反序列化避免对框架 DTO 的错误依赖。

**Alternatives considered**:
- *改框架 DTO 加 `@JsonAlias("msg")`*：被拒——跨模块触碰，且影响所有使用该 DTO 的反序列化面（宪法 II 波及面评估超标）。
- *只解析 String 做正则提取*：被拒——脆弱；用 Jackson/`JSONUtil` 结构化提取更稳。

---

## R-04 重试幂等护栏与重放保护（F-05，中危）

**Decision**: `retryFilter` 增加方法维度判定：
1. 默认可重试方法集合 = `{GET, HEAD, OPTIONS}`（安全方法）；新增配置键 `maple.framework.web.client.retry-methods`（逗号分隔，默认 `GET,HEAD,OPTIONS`）可显式扩大（如 `GET,POST`），空值=不重试。
2. 现有"异常类型 + 状态码（408/429/500/502/503/504）"判定保持（P-02 不动）。
3. 重试次数/延迟默认值不变（3 次 / 1000ms）。
4. `X-Request-Start-Time` 改由**内层过滤器**逐尝试刷新（重试语义不再陈旧，E-14 闭合）。

**Rationale**: 三类危险默认之一（spec Clarifications Q4 允许缺陷收敛）；按方法集合配置比布尔开关表达力强且与 HTTP 语义对齐；默认收敛到安全方法后，非可重放体（流式 body）的重放风险随方法限制基本消除（GET/HEAD/OPTIONS 常规无体）。兼容路径：`retry-methods=GET,HEAD,OPTIONS,POST,PUT,DELETE` 一行恢复旧范围。

**Alternatives considered**:
- *声明式 `@Idempotent` 标记*：被拒——需要调用方注解面，侵入大；配置键足够。
- *仅按异常类型区分（网络错误不重试非幂等、5xx 重试）*：被拒——5xx 对 POST 同样可能已产生副作用，区分无语义依据。
- *体可重放性探测*：被拒——`ClientRequest.body()` 的 `BodyInserter` 无公开可重放标志，探测不可靠。

---

## R-05 连接池释放闭环（F-04，中危）

**Decision**:
1. `mapleWebClientConnectionProvider` Bean 声明改 `@Bean(destroyMethod = "dispose")`——显式绑定 `ConnectionProvider.dispose()`（javap 已核实其存在且为 `reactor.core.Disposable` 唯一释放入口）。
2. 非 Spring 路径 `webClientBuilder()`：优先复用容器内 `mapleWebClientConnector`/`mapleWebClientConnectionProvider` Bean（按名 → 按类型）；均缺失才自建，并把自建池登记进模块内 `GXWebClientResourceHolder`（单例，提供 `dispose()`；若上下文可用则注册 `ContextClosedEvent` 监听自动释放，否则注册 JVM 关闭钩子兜底）；连续调用复用同一 holder，**禁止每次调用新建池**。
3. 释放幂等：`dispose()` 可重复调用；销毁后再取 builder 的语义为"重建或抛出"（验收以不泄漏为准）。

**Rationale**: `ConnectionProvider` 无 `close()`/`shutdown()`，Spring 的 `@Bean` 销毁方法推断（只认这两个名字）永远不会触发——显式 `destroyMethod` 是最小修复；holder 解决"多 connector 静默新建"的泄漏根因（E-09），同时保留该公共方法签名（宪法 II）。

**Alternatives considered**:
- *包装 `DisposableBean` 适配器*：被拒——多一层间接，`destroyMethod` 已足。
- *删除 no-arg `webClientBuilder()`*：被拒——公共 API（宪法 II），下游可能在用。
- *依赖 reactor-netty 池的守护线程自生自灭*：被拒——多上下文/热部署场景累积泄漏（宪法 V 资源释放纪律）。

---

## R-06 日志掩码与耗时口径（F-07/F-08/F-16，中低危）

**Decision**:
1. **掩码**：`requestFilter`/`responseFilter` 的 TRACE 头输出统一经 `GXWebClientService.getSensitiveHeaderFields()` 映射脱敏（无 SPI 实例时用内置默认映射）；新增纯函数 `GXWebClientLogMaskUtils`（或并入快照类）便于单测。
2. **耗时口径**（按 spec US5 验收场景 3）：`durationMs` = 各次尝试的网络耗时之和（**不含退避**）；新增 `attempts`（尝试次数）与 `retryDelayMs`（退避累计）字段；终态失败补 `HTTP request failed: durationMs={}, attempts={}, exceptionType={}` 日志（ERROR）。
3. **Content-Length 告警阈值**改读 `GXWebClientProperties.memoryBufferSizeMb`（F-07 的常量错位闭合）。
4. **User-Agent** 去除 `os.name`（改为固定 `Maple-Leaf-WebClient/1.0`，可选 `maple.framework.web.client.user-agent-suffix` 追加自定义标识，默认空）。

**Rationale**: 掩码契约（`getSensitiveHeaderFields`）本就存在只是零调用，接线即可兑现承诺；耗时口径变更与 spec SC-004/US5 直接对应；UA 改动是信息泄露面收敛（危险默认三类之外，但为兼容保留可选后缀）。

**Alternatives considered**:
- *删除敏感头再输出*：被拒——丢失排障信息，掩码映射已有现成契约。
- *durationMs 含退避 + 新增纯网络字段*：被拒——见 plan.md Complexity Tracking ②。

---

## R-07 SPI 死扩展点接线（F-09，中危）

**Decision**: 将 `logRequestResponse`（带 body 重载在 body 可得时，简版恒调）与 `logRequestException` **真实接入**过滤器链：响应成功在 `responseFilter` 末尾调用、异常在链路 `doOnError` 调用；回调异常 try-catch 隔离（不得影响主链路）；无 `GXWebClientService` Bean 时跳过（现状语义）。**不删除**任何 SPI 方法。

**Rationale**: SPI 是公共契约（宪法 II），删除是破坏性变更；接线是增量激活，覆盖既有实现类的观测诉求。回调隔离保证用户实现缺陷不拖垮出站调用。

**Alternatives considered**: *标记 `@Deprecated` 并移除*：被拒（同上）；*仅在 DEBUG 级别调用*：被拒——钩子语义与日志级别无关，用户可能用作审计埋点。

---

## R-08 配置治理：`GXWebClientProperties`（F-15/F-17/F-19 相关）

**Decision**: 新增 `GXWebClientProperties`（`@ConfigurationProperties(prefix = "maple.framework.web.client")`）承载**全部**现有键（键名/默认值逐一不变）与 **8 个新增键**（`retry-methods`、`user-agent-suffix`、可选拆分超时键 `connect-timeout-millis`/`read-timeout-millis`/`write-timeout-millis`/`response-timeout-millis`——缺省回落 `timeout-seconds` 现行口径；`read-timeout-millis=0` 表示禁用读超时，服务流式消费；`lb-guard-enabled` 默认 `true`、`lb-guard-allow-hosts` 默认空集合——LB 守卫逃生口与带点服务名白名单，见 R-02 复审补充）；校验规则集中（按 data-model §1"0 语义表"的非法值 fail-fast 文案）；同步 `additional-spring-configuration-metadata.json`（共 18 键，与字段数/元数据条目数机械对齐）。`GXWebClientConfig` 各 `getConfiguredInteger` 调用点迁移到 Properties 注入；no-arg 路径经 holder 读取同一 Properties。**不采用** local/nacos 双子类。

**Rationale**: 宪法 IV 要求新增配置走 `@ConfigurationProperties`；既然触碰配置面，统一收口避免"新旧双轨"。键名不变保证配置文件零迁移。`@ConfigurationProperties` 从任意 `PropertySource` 绑定（Nacos 同样生效），local/nacos 双类层次是为 `@NacosConfigurationProperties` 注解绑定设计的，此处无该依赖，套用即违"复杂度必须被证明"。

**Alternatives considered**: *仅新键走 Properties、旧键保留 Environment 读取*：被拒——双轨并存更难治理；*全套 local/nacos 双实现*：被拒（无 Nacos 注解依赖，见上）。

---

## R-09 测试策略与"旧代码失败"证据采集（FR-019～FR-023）

**Decision**:
1. **测试形态映射**：纯逻辑（错误解析/掩码/快照/重试判定）→ 单元测试；装配/AOP/过滤器链/销毁钩子 → `ApplicationContextRunner` + `@SpringBootTest`（`GXWebClientSpringBootTest`，镜像 eureka-client 的 `TestApplication` 手法）；真实 HTTP → JDK `com.sun.net.httpserver.HttpServer` 回环端点；并发 → **五形态**（请求线程 / `@Async` 线程池 / 调度线程 / reactor 并行 / 虚拟线程 executor）构造并发调用与并发上下文（与 spec FR-022 术语对齐，虚拟线程为补充形态）。
2. **旧代码失败证据**：每修复项按"先提交暴露缺陷的测试 → 记录用例名与失败断言输出 → 再改实现转绿"的顺序执行，证据表（用例名/失败信息/对应 F 编号）随交付输出（进 quickstart 附录与 PR 说明）。
3. **基线保护断言**：P-01/P-02/P-04/P-06/P-07 对应既有用例全部保留并在清单标注"保留"。
4. **环境缺失优雅跳过**：无外部依赖（HttpServer 本机起停），不涉 Testcontainers。
5. **LB 守卫测试**不依赖真实注册中心：`lbMode=true` builder 单测主机形态矩阵（含 `lb-guard-allow-hosts` 放行带点服务名、`lb-guard-enabled=false` 回退旧行为两类复审用例）；`@SpringBootTest` 验证 `webClient` Bean 确实带 LB 过滤器（断言过滤器链组成或行为探针）。
6. **配置夹具**：`GXWebClientProperties` 用例以 `MockEnvironment`/`withPropertyValues` 驱动，避免新增配置文件；限流窗口用同包字段覆写而非反射（FR-023）。

**Rationale**: 与宪法 VI、AGENTS.md 第 4 节、spec FR-019～FR-023 一一对齐；`HttpServer` 技术选型与 `leaf-base-eureka-client` 既有测试一致（同仓惯用法）。

---

## R-10 治理收敛与文档（F-13/F-15，低危）

**Decision**:
1. `pom.xml`：`loadbalancer.version` 迁至根 POM `<properties>`（值保持 `5.0.3`，附中文注释"与 spring-cloud-dependencies 2025.1.3 的 commons 对齐，显式钉住防漂移"），模块内删除本地属性改引用 `${loadbalancer.version}`——零行为变化。
2. `AGENTS.md`：修正"尚未建立有效测试基线"陈述、补充寻址/流式/上下文三条限制入口。
3. 新增 `docs/leaf-base-webclient开发手册.md`：以修复后语义为基线（配置键表、寻址模式、错误映射表、重试表、快照 API 用例、资源释放责任）。
4. `GXWebClientExceptionHandler` 两类文案统一中文（用户可见消息，宪法允许中文；日志保持 ASCII），并在手册声明 `WebClientResponseException` 分支的适用范围（外来客户端）。

**Rationale**: 宪法附加约束 2 与交付清单的已知偏差，"触碰即收敛"（宪法 VI 第 6 条）；跨模块影响面为版本声明位置与文档区，均零行为变化。

---

## 上游语义依赖清单（审计已核实，实现期直接引用）

| 语义 | 来源 | 结论 |
| --- | --- | --- |
| 过滤器链执行时机 | `spring-webflux-7.0.9-sources.jar` `DefaultWebClient.exchange():462-464` | `Mono.defer` 内执行过滤器 → 订阅时/订阅线程；`initRequestBuilder():445` 装配时构建请求与 attribute |
| `@LoadBalanced` 附着 | `spring-cloud-commons-5.0.3-sources.jar` `LoadBalancerWebClientBuilderBeanPostProcessor` | 给 `@LoadBalanced` 的 `WebClient.Builder` Bean 追加 `DeferringLoadBalancerExchangeFilterFunction`（列表末位=最内层） |
| LB serviceId 与合成 503 | 同上 `ReactorLoadBalancerExchangeFilterFunction.filter` | `serviceId = url.getHost()` 无旁路；无实例 → 合成 503 不发网络请求 |
| 连接池释放 | `reactor-netty-core` javap | `ConnectionProvider extends reactor.core.Disposable`，仅 `dispose()/disposeLater()`，无 `close/shutdown` → Spring 推断销毁不触发 |
| token 失败哨兵 | `GXAuthCodeUtilsTest` | `authCodeDecode` 错钥/畸形/过期/空均返回 `"{}"` → `checkTokenValidity` 判 `"{}"` 正确（P-03 保持） |
| 错误字段错位 | `GXHttpInvokerApiErrorResDto`（`message`）vs `GXBusinessException`（`msg`） | 双形状解析的根因依据（R-03） |
| 请求上下文形态 | `GXCurrentRequestContextUtils`（`RequestContextHolder`）+ `GXTraceIdContextUtils`（MDC） | 双 ThreadLocal，异步线程不可达（R-01 依据） |
