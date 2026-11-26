# Implementation Plan: leaf-base-feign 逻辑正确性、线程安全、性能与契约一致性审查

**Branch**: `007-feign-module-audit` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-feign-module-audit/spec.md`

## Summary

本次交付分两层：**审查结论**（独立报告 `audit-report.md`，覆盖 15 个生产文件、5 个既有测试类与进程内 HTTP 真实往返）与**修复设计**（按 spec 的 12 项澄清结论，把 6 个高危、6 个中危、4 个低危发现落成"测试先行"的改造方案）。

技术路线：不改公开方法签名与既有配置键语义（宪法 II）。核心改造集中在五处：

1. **token 传播可控**：新增 `maple.framework.feign.auth-token.enabled`（默认 `true` 保持 fail-fast），关闭时跳过生成并 warn 后继续；`GXFeignService` 签名不变。
2. **错误语义保真**：错误解码器仍默认关闭；开启后 `code` 取远端 HTTP 状态码，解析远端 `GXResultUtils` 形态的 `code/msg/data`，解析失败降级为固定文案 + 截断 body。
3. **重试默认安全**：`maxRetries` 默认收敛为 `0`（对齐已在依赖源码中核实的 `Retryer.NEVER_RETRY`）；显式开启时通过 `RetryPolicy.predicate` 排除业务异常。
4. **请求头回归语义**：移除写入请求的响应类安全头与 `User-Agent` 覆盖，`X-Request-Source`/`X-Request-Start-Time`/traceId/auth/platform 保留；traceId 生成后回写并恢复 MDC。
5. **装配与契约对齐**：`GXFeignFallbackConfig` 保留 `Encoder` 与 `FeignHttpMessageConverters`（实测证明是 `@EnableFeignClients` 客户端的必要来源），新增 `fallback-config.enabled` 开关；四个环境模板改 `maple.framework.feign.circuit-breaker.*`；补配置元数据、开发手册与 `AGENTS.md` 对齐。

真实往返验证使用 JDK `HttpServer` + `@SpringBootTest`/`ApplicationContextRunner`，零新增依赖。

## Technical Context

**Language/Version**: Java 21（`maven.compiler.release=21`）；ErrorProne + NullAway（`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`）为编译期门槛

**Primary Dependencies**: `spring-boot 4.1.1`、`spring-cloud 2025.1.3`（`spring-cloud-openfeign-core 5.0.3`、`spring-cloud-circuitbreaker-framework-retry 5.0.3`、`spring-core 7.0.9` 的 `org.springframework.core.retry.RetryPolicy`）、`feign 13.x`（经 starter 传递）、`hutool`、`jspecify`、`leaf-base-framework`（`GXAuthCodeUtils`、`GXCurrentRequestContextUtils`、`GXTraceIdContextUtils`、`GXBusinessException`、`GXResultUtils`）；已核实 `FeignClientsConfiguration` 自带 `FeignHttpMessageConverters` / `Encoder` / `Retryer.NEVER_RETRY`

**Storage**: N/A（无本地持久化）

**Testing**: JUnit 5（Jupiter）+ `spring-boot-starter-test` + AssertJ；条件装配用 `ApplicationContextRunner`；真实往返用 `@SpringBootTest` + JDK `com.sun.net.httpserver.HttpServer`；性能用 `System.nanoTime()` 循环采样（不引入 JMH）；不引入 MockWebServer/Testcontainers

**Target Platform**: JVM 库（被 Spring Boot 4.1.x 应用以 Maven 依赖引入；`leaf-framework` 聚合门面默认包含本模块）

**Project Type**: library（多模块 Maven 工程中的 `leaf-base-feign` 单一模块 + 4 个已授权的 `config/` 模板文件）

**Performance Goals**: 结构口径为主——单次 `apply()` 的头写入次数与注解查找次数可复算；`apply()` ≥10 万次采样给出 p50/p95/p99，两次重跑偏差 ≤30%；不设绝对吞吐/时延门禁

**Constraints**: 不改 `GXFeignService` 方法签名与默认方法语义；不改 `@GXFeignHeader`/`@GXHttpInvokerAuthToken` 注解属性；错误解码器保持默认关闭；`maxRetries` 默认改为 0（受控行为变更，需 PR 说明与迁移提示）；只改 `leaf-base-feign` 与已授权的 4 个 `config/*/app-environment.yml`；不新增第三方依赖；ErrorProne/NullAway 零告警

**Scale/Scope**: 16 个生产源码文件（含 7 个 `package-info.java`）、5 个测试类（25 个用例）、6 高危 + 6 中危 + 4 低危发现；预计改动文件 ≤ 16

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 说明 |
| --- | --- | --- |
| I 模块自治与按需装配 | PASS | 改动集中在 `leaf-base-feign`；4 个 `config/` 模板改动已由澄清项显式授权，范围限定该段配置；不新增模块、不改依赖方向 |
| II 稳定契约优先 **NON-NEGOTIABLE** | PASS（含 1 项受控行为变更） | `GXFeignService` 方法签名/默认方法、两个注解属性、`error-decoder` 默认关闭语义均不变；`maxRetries` 默认 `3 → 0` 属显式声明的行为变更，理由为消除非幂等重复写风险并回归 OpenFeign `Retryer.NEVER_RETRY` 上游默认，需在 PR 与手册中给出迁移说明 |
| III 统一响应与统一异常 | PASS | 解码结果统一为 `GXBusinessException`，`code` 取远端状态码（不再压平为 500），支持解析远端 `code/msg/data`；不新增裸返回值 |
| IV 显式配置与双配置源 | PASS | 新增开关走 `maple.framework.feign.*` 命名空间并补 `additional-spring-configuration-metadata.json`；不改双配置源骨架（本模块无 `properties/local|nacos` 结构，不强行引入） |
| V 空值安全与上下文纪律 | PASS | 可空返回值补 `@Nullable`；traceId 回写采用"put → finally restore"避免线程上下文泄漏；缓存类共享状态给出有界论证 |
| VI 测试与静态检查门槛 | PASS | 每个缺陷附"旧代码失败、新代码通过"的回归用例；真实往返用例无外部依赖可直接跑；`mvn -pl leaf-base-feign test` 与 ErrorProne/NullAway 零错误为完成门槛 |
| VII 命名与包结构一致性 | PASS | 新增/变更类型沿用 `GX` 前缀与既有分包（`annotation`/`aspect`/`codec`/`config`/`interceptor`/`properties`/`service`）；不新增包层级 |

**Gate 结论**：无违反项，无需 Complexity Tracking 豁免。唯一需跟踪项为 `maxRetries` 默认值变更，已按"受控行为变更"登记。

**Phase 1 复查结果（2026-10-22）**：核对 `research.md` / `data-model.md` / `contracts/*` 后确认——未新增第三方依赖；未新增公开方法；新增配置键 2 个（`maple.framework.feign.auth-token.enabled`、`maple.framework.feign.fallback-config.enabled`）均为 `maple.framework.*` 且带默认值并补元数据；改动文件落在 `leaf-base-feign` 与已授权的 4 个模板内。**7 条门禁保持 PASS。**

## Project Structure

### Documentation (this feature)

```text
specs/007-feign-module-audit/
├── plan.md              # 本文件
├── spec.md              # 规格（已澄清 12 项）
├── research.md          # Phase 0：技术决策与备选方案
├── data-model.md        # Phase 1：审查产物模型与契约数据结构
├── quickstart.md        # Phase 1：端到端验证指南
├── contracts/           # Phase 1：对外契约
├── checklists/
│   └── requirements.md  # 规格质量清单
└── audit-report.md      # 实施阶段产出：独立审查报告（本命令不创建）
```

### Source Code (repository root)

```text
leaf-base-feign/
├── src/main/java/cn/maple/feign/
│   ├── annotation/{GXFeignHeader,package-info}.java          # 契约不变，补 Javadoc 边界说明
│   ├── aspect/GXFeignAuthTokenAspect.java                    # 缓存键收敛、诊断信息、异常语义说明
│   ├── codec/GXFeignCustomErrorDecoder.java                  # code 保真 + 远端 code/msg/data 解析 + 降级
│   ├── config/{GXFeignConfig,GXCircuitBreakerConfig,GXFeignFallbackConfig}.java  # 新增开关、默认值、去掉 Encoder 声明
│   ├── interceptor/GXFeignRequestInterceptor.java            # token 开关、公共头收敛、appName 缓存、traceId 回写
│   ├── properties/GXCircuitBreakerProperties.java            # maxRetries 默认 0 + 校验
│   └── service/GXFeignService.java                           # 仅补 @Nullable 与 Javadoc，不改签名
├── src/main/resources/META-INF/
│   ├── additional-spring-configuration-metadata.json          # 新增（当前缺失）
│   └── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports  # 不变
└── src/test/java/cn/maple/feign/
    ├── codec/GXFeignCustomErrorDecoderTest.java              # 补 10 状态码矩阵与降级分支
    ├── config/GXFeignConfigTest.java                         # 补开关矩阵、default 规格、Encoder 归属
    ├── config/GXCircuitBreakerConfigTest.java                # 补 maxRetries=0、业务异常不重试
    ├── interceptor/GXFeignRequestInterceptorTest.java        # 补头矩阵、token 开关、MDC 回写
    ├── aspect/GXFeignAuthTokenAspectTest.java                 # 新增（当前零覆盖）
    └── integration/GXFeignHttpRoundTripTest.java              # 新增：JDK HttpServer 真实往返

config/{dev,test,pre,prod}/app-environment.yml                 # 已授权：maple.feign.* → maple.framework.feign.*

docs/leaf-base-feign开发手册.md                                 # 新增（宪法 I 要求的交付集）
leaf-base-feign/AGENTS.md                                      # 对齐：错误解码器需显式开启、新增开关、重试默认值
```

**Structure Decision**: 沿用现有单模块分包（`annotation`/`aspect`/`codec`/`config`/`interceptor`/`properties`/`service`），新增测试包 `aspect`、`integration` 与资源文件 `additional-spring-configuration-metadata.json`；不新增子模块、不新增生产包。

## Phase 0 研究结论（详见 research.md）

| 编号 | 决策 | 关键理由 |
| --- | --- | --- |
| D-01 | 出站公共头只保留 `X-Request-Source`（有配置时）、`X-Request-Start-Time`；`User-Agent` 仅在请求模板未显式设置时补默认值 | 6 个响应类安全头写入请求无效果，`Cache-Control: no-cache` 抑制幂等 GET 的代理缓存；覆盖 `User-Agent` 使服务端无法识别调用方 |
| D-02 | 新增 `maple.framework.feign.auth-token.enabled`（默认 `true` = fail-fast；`false` = 跳过生成 + warn 后继续） | 保持既有失败语义（宪法 II），同时给"目标服务不需要鉴权"的场景一个显式出口，避免整体关掉拦截器 |
| D-03 | 解码器 `code` 取远端 HTTP 状态码；解析远端 `code/msg/data` 形态覆盖消息；新增 409/422/429 进入自定义处理；其余仍回退默认解码 | 现实现把 7 个状态码统一映射为 `code=500`，丢失远端语义，违反宪法 III |
| D-04 | `maxRetries` 默认 `0`；显式开启后通过 `RetryPolicy.predicate` 排除 `GXBusinessException` | `FeignClientsConfiguration.feignRetryer()` 的默认值为 `Retryer.NEVER_RETRY`（已在依赖源码核实），且 `FrameworkRetryCircuitBreaker` 对任意异常统一重试，业务异常重试无意义 |
| D-05 | 保留 `GXFeignFallbackConfig` 的 `Encoder` 与 `FeignHttpMessageConverters`，新增 `fallback-config.enabled`（默认 `true`） | 实测：删除 `Encoder` 后 `No bean found of type feign.codec.Encoder`，该规格是客户端的必要来源；原"冗余/抢占"假设被否定 |
| D-06 | traceId 生成后 `putTraceId`，在 `finally` 中 `restoreTraceId(previous)` | 不修改 MDC 会使本进程日志与下游 traceId 脱节；不恢复会造成线程池串号（宪法 V） |
| D-07 | 注解查找按 `Method` 缓存 `GXFeignHeader`，`spring.application.name` 与 `os.name` 惰性缓存为不可变字段 | 消除每调用反射与环境查询开销，缓存键规模由方法数量决定（有界） |
| D-08 | 切面补 6 类回归用例；`@within` 对接口注解的命中以实测为准，不命中则文档明确"仅方法级可靠"，不扩张 pointcut | 扩张 pointcut 属契约变更；先度量再决定 |
| D-09 | `METHOD_SIGNATURE_CACHE` 键改为 `Method`，值格式保持 `声明类型#方法名` | 移除缓存会造成异常消息抖动；改用 `Method` 键使重复键与实际方法一一对应 |
| D-10 | 新增 `additional-spring-configuration-metadata.json`、`docs/leaf-base-feign开发手册.md`，并把 `AGENTS.md` 的"错误解码器默认装配"改为"需显式开启" | 宪法 I/IV 的交付集要求；契约漂移必须双向对齐 |
| D-11 | 不做 `@GXFeignHeader` 类型级扩展，仅在 Javadoc/手册中明确"仅方法级、仅 Servlet 上下文" | 扩展注解目标属契约变更且无需求支撑（宪法 II、附加约束 4） |
| D-12 | 包级继续 `@NullUnmarked`，仅对可空返回值补 `@Nullable` | 与仓库现状一致，逐步收敛；避免一次性切换影响面失控 |

## Phase 1 设计产物

- [research.md](./research.md)：12 条决策（决策 / 理由 / 备选方案 / 影响面 / 验证方式）。
- [data-model.md](./data-model.md)：审查产物模型、矩阵结构、配置键模型与新增元数据字段。
- [contracts/header-propagation-contract.md](./contracts/header-propagation-contract.md)：头类别 × 来源 × 覆盖规则 × 无上下文行为。
- [contracts/error-decoding-contract.md](./contracts/error-decoding-contract.md)：状态码 × 异常类型 × `code` × `msg` × 响应体保留 × fallback 交互。
- [contracts/configuration-contract.md](./contracts/configuration-contract.md)：7 个配置键的默认值、生效条件、元数据与迁移说明。
- [contracts/auth-spi-contract.md](./contracts/auth-spi-contract.md)：`GXFeignService` 出站生成与 `@GXHttpInvokerAuthToken` 入站校验的语义边界。
- [quickstart.md](./quickstart.md)：单元 / 装配 / 真实往返 / 性能复现四类验证命令与期望结果。

## 实施顺序（供 /speckit.tasks 使用）

1. **基线固化**（无生产改动）：跑 `mvn -pl leaf-base-feign test` 记录 25/0 基线；产出 `audit-report.md` 骨架与证据索引（E-01 ~ E-22）。
2. **装配与配置（F-06 / F-12 / FR-018）**：四个环境模板改键 + `additional-spring-configuration-metadata.json` + `AGENTS.md`/手册对齐；用例断言"旧键不生效、新键生效"。
3. **请求头与上下文（F-07 / F-08 / F-10 / FR-004）**：公共头收敛、`User-Agent` 不覆盖、appName/os 缓存、traceId 回写与恢复；补头矩阵用例。
4. **token 与切面（F-01 / F-02 / F-03 / F-09 / FR-017）**：新增 `auth-token.enabled`；补 `GXFeignAuthTokenAspectTest` 6 类场景；`@within` 命中实测。
5. **错误语义（F-04 / FR-021）**：`code` 保真、远端 `code/msg/data` 解析、降级规则、新增状态码；补 10 状态码矩阵用例。
6. **重试与熔断（F-05 / FR-006）**：`maxRetries` 默认 0、业务异常排除、GET/POST 实际请求次数实测用例。
7. **装配收敛与性能（F-11 / F-13 / F-14 / F-15 / F-16 / FR-008 / FR-010）**：去掉 `Encoder` 声明、`@Nullable` 补齐、`apply()` 基准与固定开销清单。
8. **真实往返与报告收口（FR-004 / FR-005 / FR-013）**：`GXFeignHttpRoundTripTest`、报告与 spec 一致性校验。

## Complexity Tracking

无宪法违反项，无需豁免记录。受控行为变更 1 项（`maxRetries` 默认 `3 → 0`）已在 Constitution Check II 中说明理由与迁移要求。
