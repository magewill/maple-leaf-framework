# 审计报告：leaf-base-webclient 正确性 / 线程安全 / 性能

**基线提交**：修复前（`4.3.0-SNAPSHOT`，`mvn -pl leaf-base-webclient test` = 17 执行 / 0 失败）
**报告日期**：2026-10-23 ｜ **关联规格**：[spec.md](./spec.md) ｜ **计划**：[plan.md](./plan.md) ｜ **验证**：[quickstart.md](./quickstart.md)
**修复后基线**：`mvn -pl leaf-base-webclient test` = **70 执行 / 0 失败**（0 删除、0 跳过）；`mvn -pl leaf-base-webclient clean install -DskipTests` 通过（ErrorProne + NullAway 零错误）；`mvn -pl leaf-base-eureka-client test` 全绿（23 条）。

## 1. 基线与证据来源

1. 修复前源码：`leaf-base-webclient` 8 个生产文件 + 1 装配清单 + 5 个测试类（17 用例）；修复前行号见下文"位置"列。
2. 上游语义：`spring-cloud-commons-5.0.3-sources.jar`（`LoadBalancerWebClientBuilderBeanPostProcessor`、`ReactorLoadBalancerExchangeFilterFunction`）、`reactor-netty-core`（javap：`ConnectionProvider extends Disposable`）、`spring-webflux-7.0.9`。
3. 下游影响面：`leaf-base-eureka-client`（编译依赖 + `@AutoConfiguration(after = GXWebClientConfig.class)` + 测试直接引用）。
4. 修复后证据：本报告第 3～6 节的矩阵与命令输出。

## 2. 发现清单（F-01～F-19，八要素）

> 维度：正确性 / 线程安全 / 性能与资源 / 契约与治理。**位置**均为修复前文件行号，便于按历史版本复核。

| 编号 | 维度 | 级别 | 位置 | 触发条件 | 影响面 | 证据 | 复现方式 | 回归测试设计 | 修复后用例 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | 正确性 | 高 | `GXWebClientConfig.java:158,289-294` | 默认 builder 请求 FQDN / IP / `localhost` | 直连调用得到合成 503 + 3 次假重试 + "服务不可用"误导文案 | E-08 | 默认 client 请求 `https://api.example.com`，观测 0 次真实请求、4 次尝试 | 主机形态矩阵 + 0 次尝试断言 | `GXWebClientLbGuardTest`（6） |
| F-02 | 正确性 | 高 | `GXWebClientConfig.java:355-385` | 对端返回框架统一响应 `{"code","msg","data"}` | 远端业务错误详情丢失，只剩通用文案 | E-02 | 500 + `{"code":500,"msg":"remote detail"}`，观测异常消息 | 双形状 + 4 种退化形状矩阵 | `GXWebClientErrorMappingTest`（6） |
| F-03 | 线程安全 | 高 | `GXWebClientConfig.java:191-212` | 在 `@Async` / 调度 / reactor 并行线程发起调用 | traceId/platform/token 头静默丢失；切面误判 token 无效 | E-11 | 无请求上下文线程发起调用，对比出站头集合 | 四线程形态 + 限流 WARN + 快照恢复 | `GXWebClientContextPropagationTest`（3）、`GXWebClientConcurrencyTest`（2）、`GXWebClientAuthTokenAspectTest`（2 新增） |
| F-04 | 性能与资源 | 中 | `GXWebClientConfig.java:98-115,308-325` | 容器关闭；或无 connector Bean 时手工创建 | 连接池与后台驱逐线程不释放；手工路径每次泄漏一个池 | E-09、E-10 | 关闭上下文后观测 `dispose()`；连续 10 次手工创建统计池数量 | `dispose()` 调用 + 幂等 + 单池复用 + dispose 后拒绝重建 | `GXWebClientResourceLifecycleTest`（5） |
| F-05 | 正确性 | 中 | `GXWebClientConfig.java:397-402` | POST/PUT/DELETE 发生连接重置 / 超时 | 非幂等请求被自动重放，可能重复副作用 | E-03 | 503 + POST，统计请求次数（现状 4 次） | 方法护栏 + 恢复开关 + 耗尽透传 | `GXWebClientRetryGuardTest`（4）、`GXWebClientSpringBootTest`（3） |
| F-06 | 性能与资源 | 中 | `GXWebClientService.java:34-39` | 每次出站调用 | 150s 有效期内逐请求 2 次环境查找 + 1 次加密 | E-12 | 连续 5 次调用，统计 `authCodeEncode` 次数 | 窗口内复用 + source/secret 变更失效 | `GXWebClientServiceTest#reusesGeneratedTokenWithinCacheWindow` |
| F-07 | 正确性 | 中 | `GXWebClientConfig.java:217-258` | 发生重试；或终态失败；或 `memory-buffer-size-mb≠16` | duration 含退避；失败无耗时日志；Content-Length 阈值与配置不一致 | E-04、E-05 | 2 次重试后成功，对比 duration 与墙钟 | 口径分离 + 失败日志 + 阈值取配置 | `GXWebClientMetricsContractTest`（3） |
| F-08 | 线程安全/安全 | 中 | `GXWebClientConfig.java:173-176,252-255` | TRACE 级别开启 | token / Cookie / Authorization 明文入日志 | E-06 | 打开 TRACE 发起带敏感头请求，检查日志 | 掩码断言（含大小写）+ 明文不出现 | `GXWebClientLogMaskTest`（3） |
| F-09 | 契约与治理 | 中 | `GXWebClientService.java:94-104` | 用户覆盖日志钩子 | 钩子从不被调用，观测失效 | E-07 | 计数实现后发起成功/失败调用 | 成功/失败路径各 ≥1 次 + 异常隔离 | `GXWebClientServiceTest#invokesSpiLoggingHooksOnSuccessAndFailure` |
| F-10 | 契约与治理 | 中 | `GXWebClientConfig.java:139-149,308-330` | 使用非注解路径或容器缺/多 connector | 两条创建路径语义分裂（有无 LB）；缺 connector 时静默新建池 | E-08、E-09 | 对比两条路径的过滤器链与池数量 | 新增 `webClientDirectBuilder` + 单池复用 | `GXWebClientLbGuardTest#directBuilderAllowsDirectHost`、`GXWebClientResourceLifecycleTest#reusesSinglePoolForManualBuilder` |
| F-11 | 正确性 | 中 | `GXWebClientConfig.java:122-129` | 流式 / 长轮询 > 30s；或需要更短连接超时 | 四类超时共用一个键；读空闲切断流式响应 | E-23 | 慢响应 1.5s + `timeout-seconds=1` | 独立超时键 + 0=禁用 + null 回退 | `GXWebClientTimeoutTest`（4） |
| F-12 | 契约与治理 | 中 | `src/test/java`（整体） | — | 覆盖缺口：无并发/真实 HTTP/LB 附着/池释放/多形状错误体/上下文缺失 | E-17 | 统计既有 17 条用例覆盖面 | 按 AGENTS 第 4 节补齐各类形态 | 新增 10 个测试类，用例 17 → 70 |
| F-13 | 正确性 | 低 | `GXWebClientExceptionHandler.java:18,25` | 处理器被触发 | 文案中英混杂；`WebClientResponseException` 分支对自家客户端不可达未声明 | E-18 | 直接构造两类异常调用处理器 | 统一中文 + 返回码冻结 + 可达性声明 | `GXWebClientExceptionHandlerTest`（2） |
| F-14 | 正确性 | 低 | `GXWebClientService.java:76-78` | 解码异常 / 配置缺失 | 异常被吞且无日志，配置错误与 token 无效不可区分 | E-19 | 构造解码异常，观测日志与返回值 | WARN（仅 exceptionType）+ false | `GXWebClientServiceTest#checkTokenValidityLogsWarningWithoutSecret` |
| F-15 | 契约与治理 | 低 | `leaf-base-webclient/pom.xml:18`、`AGENTS.md:47,74` | — | 模块内重复声明版本；AGENTS 称"无测试基线" | E-20 | 核对根 POM 与 AGENTS 文本 | 版本迁移 + 文档修正 + 手册补齐 | 根 `pom.xml` 属性 + `docs/leaf-base-webclient开发手册.md` |
| F-16 | 安全 | 低 | `GXWebClientConfig.java:187-188` | 每次出站调用 | `User-Agent` 暴露服务端 `os.name` | E-13 | 读取出站 UA | UA 固定 + 可选后缀 + 不含 os.name | `GXWebClientLogMaskTest#userAgentExcludesServerOperatingSystem` |
| F-17 | 健壮性 | 低 | `GXWebClientConfig.java:348-353` | 配置为 0 / 负数 / 非数字 | 错误值直达连接池与 Netty 选项，失败文案不可定位 | E-16 | 设 `max-connections=0` 启动 | 分键 fail-fast + 文案含键名 | `GXWebClientTimeoutTest#rejectsIllegalTimeoutValue`、`GXWebClientProperties` 校验 |
| F-18 | 契约与治理 | 低 | `GXWebClientConfig.java:139-149,327-330`、`GXWebClientAuthTokenAspect.java:64-69` | 下游覆盖 Bean；或存在多个 SPI Bean | 覆盖即 `BeanDefinitionOverrideException`；多 Bean 时报错文案误导 | E-15、E-22 | 注册自定义 Bean；注册两个 SPI Bean | `@ConditionalOnMissingBean` + 首 Bean + WARN | `GXWebClientSpringBootTest#exposesFrozenBeanContract`、`GXWebClientAuthTokenAspectTest#usesFirstServiceBeanWhenMultipleImplementationsExist` |
| F-19 | 契约与治理 | 低 | `GXWebClientService.java:36-38,88-90` | 未配置 token/secret | 所有出站调用 fail-fast，无开关 | E-24 | 仅注册 SPI、不配置键 | 结论：不提供开关，手册显式声明 | `docs/leaf-base-webclient开发手册.md` §2、`AGENTS.md` §5.4 |

## 3. 请求链路语义矩阵（FR-005）

| 过滤器（由外到内） | 请求阶段 | 响应阶段 | 重试交互 | 耗时归属 | 短路条件 |
| --- | --- | --- | --- | --- | --- |
| `requestFilter` | 注入 `X-Request-Source` / `User-Agent` / `X-B3-TraceId` / `platform` / `X-Auth-Token`；快照优先、ThreadLocal 回落、全空时限流 WARN | — | 位于 `retryFilter` 外层 → **一次**；token 失败直接 `Mono.error`（P-01 不重试） | 不计时 | token 生成失败 |
| `retryFilter` | 内层 `Mono.defer` 每次尝试重建 `X-Request-Start-Time` | 累计 `attempts` / `durationMs`（纯网络）/ `retryDelayMs`；成功日志 + SPI 全参/简参；终态失败 ERROR 日志 + `logRequestException` | 持有重试循环：方法 ∉ `retry-methods` → 不重试；状态 ∈ {408,429,500,502,503,504} 或 `WebClientRequestException` → 重试；耗尽透传原始失败 | 每次尝试独立计时并累加，退避单独累计 | 方法不满足或异常不可重试 |
| `errorResponseHandleFilter` | — | 4xx/5xx → `GXBusinessException`（`msg` → `message` → 状态描述；body `code`/`status` 覆盖） | 位于 `retryFilter` 内层 → 转换后的错误会被重试判定看到 | — | 响应状态为错误 |
| `lbGuardFilter`（仅 LB builder 且 `lb-guard-enabled=true`） | 主机判定：白名单 → 放行；IP / `localhost` / 含 `.` / 带端口 → 400 | — | 位于 `retryFilter` 内层、LB 过滤器外层 → **0 次尝试、0 次重试** | 不计时 | 直连主机 |
| LB 过滤器（spring-cloud 追加） | 服务名 → 实例解析 | 无实例时合成 503（已被前置守卫消解） | 受 `retryFilter` 控制 | 计入尝试耗时 | — |

## 4. 上下文透传矩阵

| 线程形态 | traceId | platform | token | 修复后语义 |
| --- | --- | --- | --- | --- |
| 请求线程 | 请求属性 > MDC | 请求头 | 环境生成或快照 | 三头齐全（回归锁定） |
| `@Async` 线程池 | 无 | 无 | 环境生成 | 限流 WARN + 跳过头；`contextWrite` 快照可补齐 |
| 调度线程 | 无 | 无 | 环境生成 | 同上 |
| reactor 并行线程 | 无 | 无 | 环境生成 | 同上 |
| 显式快照（任意线程） | 快照值优先 | 快照值优先 | 快照值优先 | 三头齐全 |

## 5. 资源生命周期矩阵

| 组件 | 获取方式 | 释放责任 | 修复后行为 |
| --- | --- | --- | --- |
| `ConnectionProvider` | 容器 Bean | 容器 | `@Bean(destroyMethod="dispose")`，关闭即释放，驱逐线程退出 |
| `ConnectionProvider` | 手工 `webClientBuilder()` | `GXWebClientResourceHolder` | 全局单池；`DisposableBean` 释放；dispose 后拒绝重建 |
| `ReactorClientHttpConnector` | 容器 Bean / 手工 | 无独立资源 | 手工路径优先复用容器 Bean |
| `WebClient.Builder` | prototype | 无需释放 | 可变对象不共享（P-08） |

## 6. 性能口径复算（SC-006）

| 口径 | 修复前 | 修复后 | 复算方式 |
| --- | --- | --- | --- |
| token 生成次数 | 每请求 1 次 | 同 `source+secret` 的 60s 窗口 1 次（150s 内 ≤3 次） | `reusesGeneratedTokenWithinCacheWindow` 断言两次结果相同 |
| 环境查找次数 | 每请求 2 次（token 源 + secret） | 0 次（属性绑定 + 窗口内缓存） | `GXWebClientProperties.from(environment)` 只在构建期调用 |
| 重试时延构成 | `durationMs` = 总墙钟（含退避） | `durationMs` = 各次尝试网络耗时之和；`retryDelayMs` = 退避累计 | `durationExcludesRetryBackoff`：2×300ms 退避、3 次尝试 → `durationMs < 600`、`retryDelayMs = 600` |

## 7. 映射与保护断言

**E ↔ F ↔ FR ↔ V**：E-01→P-01/F-05→FR-012→V5；E-02→F-02→FR-011→V1；E-08→F-01/F-10→FR-011/FR-017→V2；E-09/E-10→F-04→FR-013→V4；E-11→F-03→FR-010→V3；E-04/E-05→F-07→FR-014→V6；E-06→F-08→FR-014→V6；E-07→F-09→FR-015→V7；E-12→F-06→FR-025→V9；E-13→F-16→FR-014→V6；E-14→F-05→FR-012→V5；E-15/E-22→F-18→FR-017→V8；E-16→F-17→FR-018→V9；E-18→F-13→FR-026→V10；E-19→F-14→FR-018→V9；E-20→F-15→FR-024→V8；E-23→F-11→FR-016→V9；E-24→F-19→FR-018→V9；E-17→F-12→FR-019～FR-023→V1–V10。

**保护断言（P-01～P-08）全部保留**：
P-01 token 失败 fail-fast（`GXWebClientConfigTest#webClientFailsFastWhenTokenGenerationFails`）；P-02 408/429/5xx 固定 1+3 重试 + 透传原始失败（`webClientRetriesRetryableHttpStatusBeforeReturningBusinessException`、`GXWebClientRetryGuardTest#keepsRetryContractForSafeMethod`）；P-03 `"{}"` 哨兵防御（`GXWebClientServiceTest#checkTokenValidityHandlesMissingInvalidAndValidToken`）；P-04 覆盖点与 ASCII 日志（`LogMessageAsciiTest`）；P-05 code 保真（错误映射矩阵）；P-06 切面注解优先级（4 条既有用例）；P-07 SPI 默认方法可覆盖（6 条既有用例）；P-08 prototype 作用域（`webClientBuilderBeanIsPrototypeScoped`）。

**未验证假设**：无。E-05（阈值常量错位）在本报告中以源码常量与配置键对比判定，并已由 `contentLengthThresholdUsesConfiguredLimit` 用例锁定。

**旧代码失败实测证据（HEAD `90fbb137` 探针，三轮共 14 条探针 / 13 条失败，F-18 未复现）**：

| 发现 | 探针断言原文 |
| --- | --- |
| F-01 | `Expecting AtomicInteger(1) to have value: 0`（旧代码无守卫，直接放行） |
| F-02 | `Expecting throwable message: "服务器处理错误(5xx): 服务器内部错误" to contain: "remote detail" but did not.` |
| F-03 | `expected: 1L but was: 0L`（无上下文丢失告警） |
| F-04 | `destroyMethod` `expected: "dispose" but was: "(inferred)"` |
| F-05 | `Expecting AtomicInteger(4) to have value: 1 but was: 4` |
| F-06 | 两次 `generateHttpAuthToken` 返回值不同（`expected: "amc2NFZud3lLaFFmd093..."` / `but was: "aGMxNWtmZFZXQ3laWUtHTmJj..."`） |
| F-07 | 2×300ms 退避后 `HTTP response: ... durationMs=694`（含退避） |
| F-08 | `Expecting no elements of: ["Request header: X-Auth-Token=probe-secret"] ...` |
| F-09 | `Expecting actual: 0`（`logRequestResponse` 调用次数为 0） |
| F-10 | `NoSuchMethodException: GXWebClientConfig.webClientDirectBuilder(...)` / `loadBalancedWebClientBuilder(...)`（旧代码两条显式入口均不存在） |
| F-13 | `Expecting actual: "WebClient remote service response exception" to contain: "响应异常"` |
| F-16 | `expected: "Maple-Leaf-WebClient/1.0" but was: "Maple-Leaf-WebClient/1.0 (Windows 10)"` |

其余条目说明：F-10 的入口缺失已由反射探针实测（见上表），"手工路径泄漏池"部分仅源码核对（未做线程计数实测）；F-11/F-14/F-17/F-19 属新增能力或决策项（不计入"旧代码失败"口径）；F-12 为覆盖缺口本身；F-15 为治理项；F-18 依赖源码核对（`ApplicationContextRunner` 默认允许 Bean 覆盖，探针未能复现，生产环境 `SpringApplication` 关闭覆盖）。逐行标注见 `quickstart.md` 证据表。

## 8. 与计划的偏差记录

1. 原计划保留 `responseFilter` 单独承担耗时统计；实现中发现请求属性在 Spring Framework 7 的 `WebClient` 规格上不落盘到 `ClientRequest`，导致跨过滤器共享指标不可靠，故将"指标 + 日志 + SPI + 重试"合并到 `retryFilter`（位置由外到内为 request → retry → errorMapping → lbGuard → LB）。语义与 FR-005 矩阵一致，`filter-chain-contract.md` 已同步。
2. 上下文快照透传由 `RequestHeadersSpec#attribute` 改为 **Reactor Context**（`contextWrite`），同时保留 `ClientRequest.attribute` 兼容读取；`data-model.md §6`、`AGENTS.md`、手册已同步。
3. 测试装配使用 `ApplicationContextRunner`（模块既有手法）而非 `@SpringBootTest`，理由：无需真实端口与 web 环境，且与 eureka-client 的集成测试选型一致；仍未脱离真实 Bean 装配、AOP 与过滤器链。
