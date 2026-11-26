# Phase 1 数据模型：审查产物与契约数据结构

**Feature**: `007-feign-module-audit` | **Date**: 2026-10-22

本特性不引入持久化实体，也不新增业务 DTO。"数据模型"指**审查产物结构**（报告与矩阵）与**本模块对外暴露的配置/契约数据结构**。所有标识符与 spec.md、plan.md 保持一致。

---

## 1. 审查产物模型

### 1.1 Finding（审查发现）

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `id` | `F-XX` | 全局唯一，两位数字，按发现顺序分配 |
| `severity` | `HIGH` / `MEDIUM` / `LOW` | 依据 spec 的风险排序口径（影响面 × 触发概率 × 可观测性） |
| `dimension` | `CORRECTNESS` / `THREAD_SAFETY` / `PERFORMANCE` / `CONTRACT` | 一条发现只归一个主维度 |
| `location` | `文件:起止行` | 必须能在当前代码上定位到具体行区间 |
| `trigger` | 文本 | 触发条件，需可构造 |
| `impact` | 文本 | 影响面，需写明"哪些应用/哪些调用方" |
| `evidence` | `E-XX[]` | 至少 1 条，且 E 编号在证据表中存在 |
| `reproduction` | 文本 | 既有用例 / 新增用例设计 / 构造步骤，三选一 |
| `regressionDesign` | 文本 | "旧代码失败、新代码通过"的输入、断言点、期望差异 |
| `status` | `CONFIRMED` / `UNVERIFIED_HYPOTHESIS` / `CONTRACT` | `UNVERIFIED_HYPOTHESIS` 不得计入缺陷统计 |

**不变量**：

- `severity=HIGH` 的发现必须存在可构造故障现场（`reproduction` 非空且可执行）。
- `status=CONTRACT`（既定契约）不得出现在"缺陷"计数中，只允许出现在"契约对齐"清单。
- 同一 `location` 可承载多条发现，但同一 `evidence` 不得被用来重复计数同一根因。

### 1.2 Evidence（证据）

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `id` | `E-XX` | 与 spec.md 中的 E 编号一致（E-01 ~ E-22） |
| `kind` | `SOURCE` / `TEST_BASELINE` / `DEPENDENCY` / `RUNTIME` | 运行时证据需给命令与输出摘要 |
| `summary` | 文本 | 一句话结论，禁止只写"见代码" |
| `verifiedBy` | 文本 | 复核命令或文件位置 |

## 2. 验证矩阵模型

### 2.1 请求头透传矩阵

维度：`headerCategory (5) × context (4)`

| headerCategory | 取值 |
| --- | --- |
| `AUTH_TOKEN` | `X-Auth-Token` |
| `PLATFORM` | platform 头（`GXTokenConstant.PLATFORM`） |
| `TRACE_ID` | `X-B3-TraceId` |
| `ANNOTATED` | `@GXFeignHeader` 声明头 |
| `COMMON` | `X-Request-Source` / `X-Request-Start-Time` / `User-Agent` |

| context | 取值 |
| --- | --- |
| `WEB_CTX + SPI_BEAN` | 有 `HttpServletRequest`，有 `GXFeignService` |
| `WEB_CTX + NO_SPI` | 有请求，无 Bean |
| `NO_WEB + SPI_BEAN` | 无请求（非 Web 线程），有 Bean |
| `NO_WEB + NO_SPI` | 无请求，无 Bean |

每个格子输出：`present (boolean)` / `valueSource (REQUEST|GENERATED|CONFIG|NONE)` / `duplicated (boolean)`。

### 2.2 错误语义矩阵

维度：`status × decoderEnabled`

| 字段 | 约束 |
| --- | --- |
| `status` | 7 个原有自定义码 + 3 个新增（409/422/429）+ 3 个未处理码（如 418/204/301） |
| `exceptionType` | 实际类型全名 |
| `code` | 异常携带的 `code`（`GXBusinessException#getCode`） |
| `msgSource` | `REMOTE_BODY` / `FIXED_TEXT` / `DECODER_DEFAULT` |
| `bodyPreserved` | 远端响应体内容是否进入异常消息 |
| `fallbackInvolved` | 在配置了 `fallback` 的客户端上是否被 fallback 吞掉 |

### 2.3 装配矩阵

维度：`configuration × switch × userBean`

| 行 | 断言点 |
| --- | --- |
| `GXFeignConfig` | `Logger.Level`、`ErrorDecoder`、`RequestInterceptor`、`GXFeignAuthTokenAspect`、`FeignClientSpecification` 是否存在 |
| `GXCircuitBreakerConfig` | `Customizer` 是否存在、`maxRetries` 实际生效值 |
| `GXFeignFallbackConfig` | default 规格是否注册、子上下文能否取得 `Encoder` 与 `FeignHttpMessageConverters`、关闭规格且无用户 `Encoder` 时客户端能否创建 |

### 2.4 配置键矩阵

| 键 | 默认值 | 生效条件 | 迁移状态 |
| --- | --- | --- | --- |
| `maple.framework.feign.logger-level.enabled` | `true` | `Logger.Level` Bean 未由用户定义 | 不变 |
| `maple.framework.feign.error-decoder.enabled` | `false`（无 `matchIfMissing`） | 显式 `true` | 不变（文档改为"需显式开启"） |
| `maple.framework.feign.request-interceptor.enabled` | `true` | 未关闭 | 不变 |
| `maple.framework.feign.auth-token-aspect.enabled` | `true` | 未关闭 | 不变 |
| `maple.framework.feign.auth-token.enabled` | `true` | 控制 token 生成与注入；`false` 时跳过并 warn | **新增** |
| `maple.framework.feign.fallback-config.enabled` | `true` | 控制 `default.mapleFeignFallback` 规格注册 | **新增** |
| `maple.framework.feign.circuit-breaker.max-retries` | `0`（原 `3`） | 显式 >0 才重试 | **默认值变更** |
| `maple.framework.feign.circuit-breaker.{backoff,backoff-multiplier,open-timeout,reset-timeout}` | `100ms` / `2.0` / `20s` / `5s` | 无变化 | 不变 |

**模板侧**：`config/{dev,test,pre,prod}/app-environment.yml` 的键由 `maple.feign.circuit-breaker.*` 改为 `maple.framework.feign.circuit-breaker.*`（已授权）。

## 3. 配置元数据结构

`src/main/resources/META-INF/additional-spring-configuration-metadata.json` 的最小结构：

```json
{
  "properties": [
    { "name": "maple.framework.feign.auth-token.enabled", "type": "java.lang.Boolean", "defaultValue": true,
      "description": "Generate and inject the Feign auth token. When false, token generation is skipped and a warning is logged." },
    { "name": "maple.framework.feign.fallback-config.enabled", "type": "java.lang.Boolean", "defaultValue": true,
      "description": "Register the default Feign fallback specification." },
    { "name": "maple.framework.feign.circuit-breaker.max-retries", "type": "java.lang.Integer", "defaultValue": 0,
      "description": "Maximum retries performed by the framework retry circuit breaker. 0 disables retries." }
  ]
}
```

**不变量**：

- 每条 `name` 必须在代码中存在对应的 `@ConditionalOnProperty` 或 `@ConfigurationProperties` 前缀拼接。
- `defaultValue` 必须与代码默认值一致；`error-decoder.enabled` 因无 `matchIfMissing`，元数据中必须写 `false`。
- 描述使用 ASCII（与日志规则一致，避免编码差异）。

## 4. 契约对象的结构变化（对外可见）

| 对象 | 变化 | 兼容性 |
| --- | --- | --- |
| `GXBusinessException`（使用方式） | `code` 由固定 500 改为远端状态码；`data` 可承载远端 `data` | 异常类型不变；`code` 语义变化需 PR 迁移说明 |
| `GXFeignService` | 仅新增 `@Nullable` 注解与 Javadoc | 签名不变，二进制兼容 |
| `GXFeignHeader` | 无结构变化 | 不变 |
| `RequestTemplate` 出站头集合 | 移除 6 个响应类头；`User-Agent` 仅在缺失时补充 | 头集合变化属可观测契约，写入手册 |
| `GXCircuitBreakerProperties` | `maxRetries` 默认 0 | 受控行为变更 |
