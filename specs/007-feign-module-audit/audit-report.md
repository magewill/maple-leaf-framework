# leaf-base-feign 审查报告

**Feature**: `007-feign-module-audit` | **Date**: 2026-10-22
**审查对象**: `leaf-base-feign` 全部 16 个生产源码文件、5 个既有测试类、1 个自动装配注册文件、4 个环境配置模板
**规格**: [spec.md](./spec.md) | **计划**: [plan.md](./plan.md) | **决策**: [research.md](./research.md)

> 证据中的行号对应**实施前**的代码状态（审查基线）。实施后代码已变更，可按 `git diff` 与下方"修复位置"列对照。

---

## 1. 审查范围

| 类别 | 文件 |
| --- | --- |
| 注解 | `annotation/GXFeignHeader.java`、`annotation/package-info.java` |
| 切面 | `aspect/GXFeignAuthTokenAspect.java`、`aspect/package-info.java` |
| 编解码 | `codec/GXFeignCustomErrorDecoder.java`、`codec/package-info.java` |
| 配置 | `config/GXFeignConfig.java`、`config/GXCircuitBreakerConfig.java`、`config/GXFeignFallbackConfig.java`、`config/package-info.java` |
| 拦截器 | `interceptor/GXFeignRequestInterceptor.java`、`interceptor/package-info.java` |
| 属性 | `properties/GXCircuitBreakerProperties.java`、`properties/package-info.java` |
| SPI | `service/GXFeignService.java`、`service/package-info.java` |
| 资源 | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`、`META-INF/additional-spring-configuration-metadata.json`（本次新增） |
| 测试 | `codec/GXFeignCustomErrorDecoderTest`、`config/GXCircuitBreakerConfigTest`、`config/GXFeignCircuitBreakerFallbackTest`、`config/GXFeignConfigTest`、`interceptor/GXFeignRequestInterceptorTest`；本次新增 `aspect/GXFeignAuthTokenAspectTest`、`integration/GXFeignHttpRoundTripTest`、`support/*` |
| 配置模板 | `config/{dev,test,pre,prod}/app-environment.yml` |

### 1.1 逐文件判定

| 文件 | 判定 | 依据 |
| --- | --- | --- |
| `annotation/GXFeignHeader.java` | 风险（已文档化） | 仅方法级、仅 Servlet 上下文（F-10） |
| `annotation/package-info.java` | 无缺陷 | `@NullUnmarked` 与仓库现状一致 |
| `aspect/GXFeignAuthTokenAspect.java` | 缺陷（已修） | 缓存键无界（F-16）、零测试（F-09）、fail-closed 未文档化（F-03） |
| `aspect/package-info.java` | 无缺陷 | — |
| `codec/GXFeignCustomErrorDecoder.java` | 缺陷（已修） | `code` 压平、body 丢弃（F-04） |
| `codec/package-info.java` | 无缺陷 | — |
| `config/GXFeignConfig.java` | 缺陷（已修） | 解码器开关无默认（F-04/契约）、default 规格无开关（F-11） |
| `config/GXCircuitBreakerConfig.java` | 缺陷（已修） | 默认重试 3 次、无异常过滤（F-05） |
| `config/GXFeignFallbackConfig.java` | 无缺陷（契约） | 是 `Encoder` 的必要来源（E-26） |
| `config/package-info.java` | 无缺陷 | — |
| `interceptor/GXFeignRequestInterceptor.java` | 缺陷（已修） | 响应类头、UA 覆盖、traceId 不回写、无缓存（F-07/F-08/F-15） |
| `interceptor/package-info.java` | 无缺陷 | — |
| `properties/GXCircuitBreakerProperties.java` | 缺陷（已修） | `maxRetries=3`（F-05） |
| `properties/package-info.java` | 无缺陷 | — |
| `service/GXFeignService.java` | 风险（已修） | 缺配置 fail-fast（F-02）、可空返回值未标注（F-14） |
| `service/package-info.java` | 无缺陷 | — |
| 5 个既有测试类 | 覆盖不足 | 无切面测试、无配置键旧前缀用例、无真实往返（已在本次补齐） |

## 2. 测试基线

| 阶段 | 命令 | 结果 |
| --- | --- | --- |
| 审查前 | `mvn -pl leaf-base-feign test` | **25 执行 / 0 失败**，BUILD SUCCESS（JDK 25 运行时，编译目标 21） |
| 审查后 | `mvn -pl leaf-base-feign test` | **76 执行 / 0 失败**，BUILD SUCCESS |
| 增量 | — | +51 用例（切面 6、真实往返 5、解码器 15、熔断 6、装配 3、拦截器 15、模板 3） |
| 静态检查 | `mvn -pl leaf-base-feign clean install -DskipTests` | BUILD SUCCESS（ErrorProne + NullAway 零错误，16 个源文件） |
| 依赖 | `mvn -pl leaf-base-feign dependency:tree`；`pom.xml` 未改动 | 无新增第三方依赖 |

## 3. 证据索引

| ID | 类型 | 结论 | 复核位置（实施前） |
| --- | --- | --- | --- |
| E-01 | SOURCE | 仓库内无 `GXFeignService` 实现或 Bean 定义（仅 5 个文件引用该类型） | 全仓库检索 `implements GXFeignService` / `@Bean GXFeignService` |
| E-02 | SOURCE | `ObjectProvider` 无候选时返回 null，随即 warn 并跳过认证头 | `interceptor/GXFeignRequestInterceptor.java:69-80` |
| E-03 | SOURCE | 缺 `maple.framework.web.feign.token` / `secret` 时抛 `GXBusinessException`，拦截器未捕获 | `service/GXFeignService.java:36-42,83-89`；`interceptor:82` |
| E-04 | SOURCE | 错误解码器开关无 `matchIfMissing`，默认不装配；同文件其它开关均为 `matchIfMissing=true` | `config/GXFeignConfig.java:38-43` vs `32,60,71` |
| E-05 | SOURCE | 7 个状态码统一 `new GXBusinessException(msg)`，`code` 固定 500 | `codec/GXFeignCustomErrorDecoder.java:41-71`；`GXBusinessException:42-44` |
| E-06 | SOURCE | 401/403/404/500/503/504 读取 body 后丢弃，仅 400 拼接 | `codec:34,46-69` |
| E-07 | SOURCE | 熔断开关 `matchIfMissing=true`，默认 `maxRetries=3` | `config/GXCircuitBreakerConfig.java:24-39`；`properties:11-19` |
| E-08 | SOURCE | 四个环境均开启 `spring.cloud.openfeign.circuitbreaker.enabled=true` | `config/*/app-environment.yml:1-9` |
| E-09 | SOURCE | 模板写 `maple.feign.circuit-breaker.*`，绑定前缀为 `maple.framework.feign.circuit-breaker` | `config/dev/app-environment.yml:23-31`；`properties:9` |
| E-10 | SOURCE | 6 个响应类安全头写入出站请求；`User-Agent` 被强制覆盖 | `interceptor:112-128` |
| E-11 | SOURCE | 生成的 traceId 不回写 MDC | `interceptor:102-104`；`service:155-160` |
| E-12 | SOURCE | 5 个测试类中无切面测试 | `src/test/java/cn/maple/feign/` |
| E-13 | SOURCE | pointcut 使用 `@annotation \|\| @within`；缺 Bean 分支零覆盖 | `aspect:39-40,62-67` |
| E-14 | SOURCE | `@GXFeignHeader` 仅 METHOD；无上下文时静默 return | `annotation:9`；`interceptor:141-144` |
| E-15 | SOURCE | 未处理状态码回退 `FeignException`，非 `GXBusinessException` | `codec:29-32,70` |
| E-16 | SOURCE | 无配置元数据；`docs/` 无 feign 手册 | `src/main/resources`；`docs/` |
| E-17 | SOURCE | default 规格把 `Encoder`/`FeignHttpMessageConverters` 作用于所有客户端 | `config/GXFeignConfig.java:45-53`；`GXFeignFallbackConfig:16-33` |
| E-18 | SOURCE | `GXFeignFallbackConfig` 无 `@Configuration`、无开关 | `config/GXFeignFallbackConfig.java:15` 起 |
| E-19 | SOURCE | `getSensitiveHeaderFields()` 无调用点 | 全仓库检索 |
| E-20 | SOURCE | 每调用执行注解反射、多次环境查询、`String.format`、6–8 次头写入、3–5 条 debug | `interceptor:61-171` |
| E-21 | SOURCE | 静态缓存键为 `signature.toString()`，无上限 | `aspect:37,107-113` |
| E-22 | SOURCE | 包级 `@NullUnmarked`，可空返回值未标注 | 各 `package-info.java:1`；`service:111-113` |
| E-23 | DEPENDENCY | OpenFeign 默认 `Retryer.NEVER_RETRY` | `FeignClientsConfiguration:138-142`（5.0.3） |
| E-24 | DEPENDENCY | 熔断器对任意 `Throwable` 重试并在耗尽后走 fallback | `FrameworkRetryCircuitBreaker:53-84`（5.0.3） |
| E-25 | DEPENDENCY | `RetryPolicy.Builder` 支持 `includes/excludes/predicate` | `spring-core 7.0.9` `javap` |
| E-26 | RUNTIME | 删除 `GXFeignFallbackConfig.Encoder` 后客户端创建失败：`No bean found of type interface feign.codec.Encoder` | `GXFeignCircuitBreakerFallbackTest` 实测 |

## 4. 发现清单

| ID | 级别 | 维度 | 位置 | 触发条件 | 影响 | 证据 | 复现/回归设计 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | 高 | 正确性 | `interceptor:69-80` | 无 `GXFeignService` Bean | 认证 token/platform 从不透传，每调用一条 warn；调用方误判"已透传" | E-01,E-02 | `missingFeignServiceStillPropagatesTraceIdSafely`、`webContextWithoutServiceBeanHasNoTokenButKeepsTraceId` | CONFIRMED（保留 warn，已文档化） |
| F-02 | 高 | 正确性 | `service:36-42,83-89`；`interceptor:82` | 有 Bean 但缺 token/secret | 任意 Feign 调用在发请求前抛 `GXBusinessException`，即使目标无需鉴权 | E-03 | `authTokenEnabledFailsFastWhenTokenConfigurationIsMissing`、`authTokenDisabledSkipsTokenGenerationAndKeepsOtherHeaders` | CONFIRMED（已修：新增开关） |
| F-03 | 高 | 正确性 | `aspect:62-67` | 方法标注 `@GXHttpInvokerAuthToken`（默认 `feign`）且无 Bean | 接口整体 500；`leaf-framework` 聚合门面默认引入本模块 | E-01,E-13 | `missingFeignServiceBeanThrowsBusinessException` | CONFIRMED（fail-closed 为既定契约，已文档化） |
| F-04 | 高 | 正确性 | `codec:41-71` | decoder 开启 + 远端 400/404/503 等 | `code` 统一 500，远端 `code/msg/data` 与 body 丢失 | E-04,E-05,E-06,E-15 | `overridesCodeAndMessageWithRemoteBusinessResult` 等 18 条 | CONFIRMED（已修） |
| F-05 | 高 | 正确性 | `config/GXCircuitBreakerConfig:24-39` | 熔断开启 + 远端 5xx/连接异常 | 默认重试 3 次，POST/PUT/DELETE 可能重复写；fallback 吞掉 4xx 业务异常 | E-07,E-08,E-23,E-24 | `defaultPolicySendsRequestOnlyOnce`（1 次）、`serverSideFailureIsRetriedWhenMaxRetriesConfigured`（3 次）、`clientSideBusinessFailureIsNotRetried`（1 次） | CONFIRMED（已修：默认 0 + 排除 4xx） |
| F-06 | 高 | 契约 | `config/*/app-environment.yml:23-31` | 任意环境启动 | 熔断配置全部空转，实际使用代码默认值 | E-09 | `legacyMapleFeignPrefixIsNotBound`；模板检索无 `maple.feign.` 残留 | CONFIRMED（已修） |
| F-07 | 中 | 正确性 | `interceptor:112-128` | 每次调用 | 6 个响应类头写入请求；`Cache-Control: no-cache` 抑制代理缓存；`User-Agent` 被覆盖 | E-10 | `responseSemanticHeadersAreNotWrittenToOutboundRequest`、`defaultUserAgentIsAppliedWhenCallerDidNotProvideOne`、`responseSemanticHeadersAreNotSentOverRealHttp` | CONFIRMED（已修） |
| F-08 | 中 | 线程安全 | `interceptor:102-104` | 非 Web 线程发起调用 | 生成的 traceId 不落 MDC，本进程日志无法关联 | E-11 | `noWebContextWithoutServiceBeanGeneratesTraceIdWithoutLeakingContext`、`existingTraceIdIsPreservedWithoutExtraGeneratedValue` | CONFIRMED（已修：put→finally restore） |
| F-09 | 中 | 契约 | `aspect:39-40` | 注解在接口上、实现在实现类 | `@within` 不命中，鉴权静默失效 | E-12,E-13 | `interfaceLevelAnnotationIsNotInterceptedOnImplementation`（断言"不拦截"，结论已写入手册） | CONTRACT（不改 pointcut，文档明确） |
| F-10 | 中 | 契约 | `annotation:9`；`interceptor:141-144` | 无 Servlet 上下文 | 声明头静默丢弃，无日志 | E-14 | `annotatedHeadersAreSkippedWithoutWebContext` | CONTRACT（已补 debug 日志与 Javadoc） |
| F-11 | 中 | 正确性 | `config/GXFeignConfig:45-53`；`GXFeignFallbackConfig` | 与 Spring Cloud 默认配置共存 | 原假设"`Encoder` 重复声明/抢占 `PageableSpringEncoder`"**被实测否定**：移除后客户端直接创建失败 | E-17,E-26 | `fallbackConfigurationCanBeDisabledWhenUserProvidesEncoder`、`feignClientReceivesEncoderFromFrameworkFallbackSpecification` | CONTRACT（保留 Bean，新增开关 + 文档） |
| F-12 | 中 | 契约 | `src/main/resources`；`docs/` | 任意接入方 | 12 个配置键无 IDE 提示；无开发手册；`AGENTS.md` 与实现冲突 | E-16,E-04 | `mvn -pl leaf-base-feign test` 后检查 `target/classes/META-INF/additional-spring-configuration-metadata.json` | CONFIRMED（已修） |
| F-13 | 低 | 契约 | `service:96-104` | — | `getSensitiveHeaderFields()` 无消费方 | E-19 | 全仓库检索无调用点 | CONTRACT（保留公开 API） |
| F-14 | 低 | 契约 | 各 `package-info.java`；`service:111-113` | 静态检查 | 可空返回值未标注，NullAway 无法验证 | E-22 | `GXFeignService#getPlatform` 已补 `@Nullable`；编译零告警 | CONFIRMED（已修） |
| F-15 | 低 | 性能 | `interceptor:61-171` | 高频调用 | 每调用反射 + 环境查询 + 格式化 + 多次头写入 | E-20 | `applyThroughputBaseline`（见 §7） | CONFIRMED（已修） |
| F-16 | 低 | 线程安全 | `aspect:37,107-113` | 长生命周期应用 | 静态缓存无上限、键为 `toString()` | E-21 | 缓存键改 `Method`，Javadoc 声明上界 | CONFIRMED（已修） |

## 5. 验证矩阵

### 5.1 请求头透传矩阵（5 类 × 4 上下文）

| 上下文 | auth token | platform | traceId | `@GXFeignHeader` | 公共头 |
| --- | --- | --- | --- | --- | --- |
| 有请求 + 有 Bean | `generated-token`（GENERATED，单值） | `web`（SPI 覆盖） | `trace-1`（SPI） | `X-Tenant-Id=tenant-1` | `X-Request-Start-Time` 单值、UA 单值 |
| 有请求 + 无 Bean | 缺失（NONE） | 缺失（NONE） | 新生成（GENERATED，单值） | `X-Tenant-Id=tenant-1` | 同上 |
| 无请求 + 有 Bean | `generated-token`（GENERATED） | `web` | `trace-1` | 缺失（跳过） | 同上 |
| 无请求 + 无 Bean | 缺失（NONE） | 缺失（NONE） | 新生成（GENERATED），MDC 调用后为空 | 缺失（跳过） | 同上 |

**重复性**：所有格子 `duplicated=false`（连续两次 `apply()` 后每个头仍只有一个值）。

### 5.2 错误语义矩阵（解码器开启）

| 状态 | 异常类型 | `code` | `msg` 来源 | body 是否进入消息 |
| --- | --- | --- | --- | --- |
| 400（业务 JSON） | `GXBusinessException` | 远端 `code`（如 1001） | 远端 `msg` | 是（≤8192，超长截断） |
| 400（非 JSON） | `GXBusinessException` | 400 | 固定文案 + 原始 body | 是 |
| 409 / 422 / 429 | `GXBusinessException` | 状态码或远端 `code` | 远端 `msg` → 固定文案 | 是 |
| 401 / 403 | `GXBusinessException` | 401 / 403 | 固定文案 | **否**（不泄露） |
| 404 | `GXBusinessException` | 404 | 固定文案 + URL | 否 |
| 500 / 503 / 504 | `GXBusinessException` | 对应状态码 | 固定文案 | 否 |
| 418 / 204 / 301 | `FeignException` | N/A | 默认解码器 | 默认解码器 |

**解码器关闭**：全部走 `FeignException`（默认行为，未改变）。

**fallback 交互（实测）**：`fallbackSwallowsBusinessErrorAndSendsSingleRequest` 让桩服务端返回 400 + 业务 JSON、客户端配置 `fallback`，实测结果为"调用方拿到 fallback 返回值 `business-fallback`、服务端仅收到 1 次请求"——即业务异常被 fallback 吞掉且不触发重试。

### 5.3 装配矩阵

| 场景 | 结果 |
| --- | --- |
| 仅框架自动配置 | `Logger.Level`(BASIC)、`RequestInterceptor`、`GXFeignAuthTokenAspect`、`FeignClientSpecification` 各 1 个；`ErrorDecoder` 不存在 |
| `error-decoder.enabled=true` | `ErrorDecoder` 为 `GXFeignCustomErrorDecoder` |
| 5 个既有开关 + `auth-token` / `fallback-config` 置 `false` | 对应 Bean 均不存在 |
| `@EnableFeignClients` 默认 | 子上下文可取得 `Encoder`（由 default 规格提供），客户端可创建 |
| `fallback-config.enabled=false` + 用户 `Encoder` | default 规格不注册，客户端仍可创建 |
| `fallback-config.enabled=false` 且无用户 `Encoder` | 客户端创建失败（已在手册说明前提） |
| 用户自定义 `Logger.Level` / `ErrorDecoder` / `RequestInterceptor` | 用户优先，框架默认 back off |

### 5.4 配置键矩阵

| 键 | 默认 | 生效验证 |
| --- | --- | --- |
| `maple.framework.feign.circuit-breaker.max-retries` | `0`（原 `3`） | 正确前缀生效；`maple.feign.*` 旧前缀**不生效** |
| `maple.framework.feign.circuit-breaker.enabled` | `true` | 置 `false` → 无 `Customizer` |
| `backoff` / `backoff-multiplier` / `open-timeout` / `reset-timeout` | `100ms` / `2.0` / `20s` / `5s` | 属性覆盖与熔断策略断言 |
| `maple.framework.feign.error-decoder.enabled` | `false` | 显式 `true` 才装配 |
| `logger-level.enabled` | `true` | 置 `false` → 无 `Logger.Level` |
| `request-interceptor.enabled` | `true` | 置 `false` → 无拦截器 |
| `auth-token-aspect.enabled` | `true` | 置 `false` → 无切面（含组件扫描场景） |
| `auth-token.enabled`（新增） | `true` | 置 `false` → 跳过生成、warn、其余头照常 |
| `fallback-config.enabled`（新增） | `true` | 置 `false` → 无 default 规格 |
| `maple.framework.web.feign.token` / `secret` | 无 | 缺失 → fail-fast（可被 `auth-token.enabled=false` 解除） |

**模板**：四个环境已改为 `maple.framework.feign.circuit-breaker.*`，无 `maple.feign.` 残留。逐环境 × 逐键判定（由 `GXFeignConfigTemplateTest` 的 `templatesDeclareEveryCircuitBreakerKeyWithTheCodeDefault` 断言，4 环境 × 6 键 = 24 组合，全部与代码默认值一致）：

| 环境 | `enabled=true` | `max-retries=0` | `backoff=100ms` | `backoff-multiplier=2.0` | `open-timeout=20s` | `reset-timeout=5s` |
| --- | --- | --- | --- | --- | --- | --- |
| dev | PASS | PASS | PASS | PASS | PASS | PASS |
| test | PASS | PASS | PASS | PASS | PASS | PASS |
| pre | PASS | PASS | PASS | PASS | PASS | PASS |
| prod | PASS | PASS | PASS | PASS | PASS | PASS |

嵌套结构（`framework → feign → circuit-breaker`）与「无 `maple.feign.` 残留」由同文件的另外两个用例覆盖，4 个环境同为 PASS。

### 5.5 空值契约清单

| 方法 | 返回类型 | 可能为空 | `@Nullable` | 结论 |
| --- | --- | --- | --- | --- |
| `generateHttpAuthToken()` | `String` | 否 | 不需要 | 缺失配置以异常表达，保持 |
| `generateHttpAuthToken(String,int)` | `String` | 否 | 不需要 | 同上 |
| `checkTokenValidity()` | `boolean` | N/A | N/A | 内部捕获异常，永不抛出，保持 |
| `getAuthTokenSecret()` | `String` | 否 | 不需要 | 缺失时抛 `GXBusinessException`，保持 |
| `getSensitiveHeaderFields()` | `Map<String,String>` | 否 | 不需要 | `Map.of` 常量，保持 |
| `getPlatform()` | `String` | **是**（无请求上下文/头缺失） | **已标注** | 本次收敛（F-14） |
| `getTraceId()` | `String` | 否（末级生成） | 不需要 | 保持 |
| `generateHmac(Object,String)` | `String` | 否 | 不需要 | 失败抛 `GXBusinessException`，保持 |
| `checkHmac(String,String,Object)` | `boolean` | N/A | N/A | 保持 |

**包级策略**：`cn.maple.feign.*` 维持 `@NullUnmarked`（与仓库现状一致，宪法 V 允许"缺失的包在触碰时补齐"的渐进收敛）。切换到 `@NullMarked` 会牵连 `GXCommonUtils`、`GXCurrentRequestContextUtils` 等上游签名，超出本模块范围，列入后续独立特性。

## 6. 既定契约（不计入缺陷）

1. 错误解码器默认关闭——需显式 `maple.framework.feign.error-decoder.enabled=true`（`AGENTS.md` 已同步）。
2. `@GXFeignHeader` 仅方法级、仅 Servlet 上下文、无上下文静默跳过。
3. `GXFeignAuthTokenAspect` 缺失 `GXFeignService` 时 fail-closed（抛 `GXBusinessException`）。
4. 接口上的类级 `@GXHttpInvokerAuthToken` 不被 `@within` 命中（实测），需标注在方法或实现类。
5. 配置 fallback 后业务异常会被 fallback 吞掉（Spring Cloud CircuitBreaker 行为）。
6. `GXFeignFallbackConfig` 的 `Encoder` 是客户端创建的必要来源，不是冗余声明。
7. `getSensitiveHeaderFields()` 为对外公开 API，当前仓库内无调用方。

## 7. 性能口径

`applyThroughputBaseline`：5 万次预热 + 10 万次采样（JDK 25 运行时，单线程，无 Web 上下文）。

| 运行 | p50 | p95 | p99 | ops/s |
| --- | --- | --- | --- | --- |
| 第 1 次 | 1300ns | 2900ns | 4600ns | 584,341 |
| 第 2 次 | 1200ns | 3300ns | 5300ns | 574,366 |
| 偏差 | 7.7% | 13.8% | 15.2% | 1.7% |

**固定开销（每调用，可复算）**：

| 项 | 次数 | 说明 |
| --- | --- | --- |
| `AnnotationUtils.findAnnotation` | 0（缓存命中后） | 首次后走 `ConcurrentHashMap<Method, Optional<GXFeignHeader>>` |
| `GXCommonUtils.getEnvironmentValue` | 1 | `auth-token.enabled` 开关读取（不缓存，支持运行期变更） |
| `spring.application.name` 解析 | 0（首次成功后） | `volatile` 字段惰性缓存 |
| `System.getProperty` | 0 | `os.name` 在类初始化时读取 |
| `String.format` | 0 | `User-Agent` 模板为 `static final` 预拼接 |
| 头写入 | 6–12 次 map 操作 | token/platform/traceId 各 1 组 `remove+add`，`X-Request-Start-Time` 1 组，`X-Request-Source`/`User-Agent` 视情况 |
| `HttpServletRequest#getHeaders` | 每个**有效**声明头 1 次 | 与声明头数量成正比；被阻断的头（如 `Authorization`）与源缺失的头不调用。实测：1 个声明头 = 1 次，4 个有效声明头 = 4 次，`Authorization` = 0 次（`annotatedHeaderLookupScalesWithValidDeclaredHeaderCount`） |
| debug 日志 | 3–4 条 | token、platform、traceId、注解头 |

**缓存影响**：移除 `Cache-Control`/`Pragma`/`Expires` 后，幂等 GET 不再被强制标记为不可缓存，可被企业网关/反向代理缓存（机制：请求头中的 `Cache-Control: no-cache` 会被多数缓存实现当作"绕过缓存"指令）。改动前请求必带这三个头，改动后不再携带。

## 8. 风险排序与修复批次

排序口径：影响面 × 触发概率 × 可观测性（越难观测越靠前）。

| 批次 | 发现 | 影响面 | 触发概率 | 可观测性 | 处理 |
| --- | --- | --- | --- | --- | --- |
| 批次 1（已交付） | F-02、F-04、F-05、F-06 | 全部下游 | 高 | 低（无报错但行为不符） | 新增 token 开关、`code` 保真、重试默认 0、模板修正 |
| 批次 1（已交付） | F-07、F-08、F-15、F-16 | 全部下游 | 高 | 中 | 头收敛、traceId 回写、缓存与常量提取 |
| 批次 2（已交付） | F-09、F-10、F-11、F-12、F-14 | 接入方 | 中 | 中 | 切面测试补齐、边界文档、装配测试、元数据与手册 |
| 批次 3（文档） | F-01、F-03、F-13 | 接入方 | 中 | 低 | 手册与 `AGENTS.md` 说明；不改变 fail-closed 语义 |

## 9. 结论

- **逻辑正确性**：4 条高危链路（token 传播、错误码、重试、配置键）已修复并有回归用例锁定；2 条原先怀疑的链路（`Encoder` 抢占、接口类级注解命中）经实测判定为"假设不成立 / 既定契约"，已写入文档。
- **线程安全**：拦截器与解码器无可变共享状态；切面静态缓存改为以 `Method` 为键并在 Javadoc 声明上界；traceId 采用 `put → finally restore`，无上下文泄漏。
- **性能**：`apply()` 约 57–58 万次/秒，p50≈1.25µs、p99≈5µs；每调用仅 1 次环境查询、0 次反射与属性读取。
- **契约一致性**：12 个配置键全部有元数据与手册条目；四个环境模板键前缀修正；`AGENTS.md` 与实现对齐。

**交付判定**：审查与修复均可复核，回归用例 76/0 通过，满足 spec 的 SC-001 ~ SC-010。
