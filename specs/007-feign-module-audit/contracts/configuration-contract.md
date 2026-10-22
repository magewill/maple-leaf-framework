# 契约：配置键、默认值与生效条件

**Feature**: `007-feign-module-audit` | **适用版本**: `leaf-base-feign` 4.3.0-SNAPSHOT

## 1. 唯一前缀

`maple.framework.feign.*` 为唯一有效前缀。

| 写法 | 状态 | 处理 |
| --- | --- | --- |
| `maple.framework.feign.*` | 有效 | 正常绑定 |
| `maple.feign.*` | 从未生效（历史写法） | 不新增别名绑定；四个环境模板改为正确前缀；下游自有配置按手册迁移 |

## 2. 开关矩阵

| 键 | 默认 | 作用范围 | 关闭后行为 |
| --- | --- | --- | --- |
| `logger-level.enabled` | `true` | `Logger.Level` Bean | 不注册默认级别，交由 Spring Cloud 默认 |
| `error-decoder.enabled` | **`false`**（无 `matchIfMissing`） | `ErrorDecoder` Bean | 使用 Feign 默认解码器 |
| `request-interceptor.enabled` | `true` | `GXFeignRequestInterceptor` | 不注册拦截器（token/traceId/公共头都不再传播） |
| `auth-token-aspect.enabled` | `true` | `GXFeignAuthTokenAspect` | 不注册切面，`@GXHttpInvokerAuthToken` 不再校验 |
| `auth-token.enabled` | `true` | 拦截器内的 token 生成与注入 | 跳过生成、warn 后继续；traceId/platform 不受影响 |
| `fallback-config.enabled` | `true` | `default.mapleFeignFallback` 规格 | 不注册该 default 规格；此时应用必须自行提供 `Encoder` 与 `FeignHttpMessageConverters`，否则客户端创建失败 |

**不变量**：新增开关必须是"默认保持既有行为"，唯一例外是 `circuit-breaker.max-retries` 的受控变更（见 §3）。

## 3. 熔断与重试参数

| 键 | 默认（变更后） | 类型 | 校验 |
| --- | --- | --- | --- |
| `circuit-breaker.enabled` | `true` | boolean | — |
| `circuit-breaker.max-retries` | **`0`**（原 `3`） | int | `< 0` 抛 `IllegalArgumentException` |
| `circuit-breaker.backoff` | `100ms` | Duration | `null` 抛 `IllegalArgumentException` |
| `circuit-breaker.backoff-multiplier` | `2.0` | double | `<= 0` 抛 `IllegalArgumentException` |
| `circuit-breaker.open-timeout` | `20s` | Duration | `null` 抛 `IllegalArgumentException` |
| `circuit-breaker.reset-timeout` | `5s` | Duration | `null` 抛 `IllegalArgumentException` |

**重试语义**：

| `max-retries` | 行为 |
| --- | --- |
| `0`（默认） | 单次执行，不重试（对齐 OpenFeign `Retryer.NEVER_RETRY`） |
| `> 0` | 最多重试 N 次；`GXBusinessException` 且 `code < 500`（客户端错误）**不重试**，5xx 与传输异常可重试 |

## 4. 模板侧（已授权跨模块改动）

`config/{dev,test,pre,prod}/app-environment.yml`：

```yaml
maple:
  framework:
    feign:
      circuit-breaker:
        enabled: true
        max-retries: 0
        backoff: 100ms
        backoff-multiplier: 2.0
        open-timeout: 20s
        reset-timeout: 5s
```

**约束**：仅改这一段；`spring.cloud.openfeign.circuitbreaker.*` 与 `maple.framework.web.client.*` 保持原样。

## 5. 元数据与文档

- 每个键必须有 `additional-spring-configuration-metadata.json` 条目，`defaultValue` 与代码一致。
- `docs/leaf-base-feign开发手册.md` 的键表必须与本节逐行一致。
- `AGENTS.md` 必须把"错误解码器自动注册"改为"需显式开启"，并记录 `max-retries` 新默认值。

## 6. 验证断言

1. 正确前缀生效：`maple.framework.feign.circuit-breaker.max-retries=1` → `GXCircuitBreakerProperties#getMaxRetries()==1`。
2. 旧前缀不生效：`maple.feign.circuit-breaker.max-retries=1` → 属性仍为默认值。
3. 每个开关置 `false` 时对应 Bean 不存在（已有 5 条用例扩展为 6 条）。
4. 元数据 JSON 在 `target/classes/META-INF/` 下存在且可解析。
