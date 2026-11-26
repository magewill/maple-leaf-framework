# 契约：出站请求头传播（`GXFeignRequestInterceptor`）

**Feature**: `007-feign-module-audit` | **适用版本**: `leaf-base-feign` 4.3.0-SNAPSHOT

## 1. 头类别与来源

| 类别 | 头名 | 来源 | 覆盖规则 | 缺失时 |
| --- | --- | --- | --- | --- |
| 认证 | `X-Auth-Token` | `GXFeignService#generateHttpAuthToken()` | 先 `removeHeader` 再写入，保证单值幂等 | 见 §2 |
| 平台 | `GXTokenConstant.PLATFORM` | `GXFeignService#getPlatform()`（读当前请求头） | 同上 | 跳过，不写空值 |
| 链路 | `X-B3-TraceId` | 请求属性 → 请求头 → MDC → 新生成 | 同上 | 新生成（见 §3） |
| 声明 | `@GXFeignHeader(names=...)` 声明的头 | 当前 `HttpServletRequest` | 多值全部透传；命中黑名单则跳过 | 源头不存在则跳过 |
| 公共 | `X-Request-Source` | `spring.application.name` | 覆盖 | 不写 |
| 公共 | `X-Request-Start-Time` | `System.currentTimeMillis()` | 覆盖 | 恒写 |
| 公共 | `User-Agent` | 调用方显式值优先，否则 `Maple-Leaf-Feign/1.0 (<os>)` | **不覆盖已有值** | 补默认 |

## 2. 认证 token 行为矩阵

| 开关 `auth-token.enabled` | `GXFeignService` Bean | 配置 `maple.framework.web.feign.token` / `secret` | 行为 |
| --- | --- | --- | --- |
| `true`（默认） | 存在 | 齐全 | 注入 token；debug 日志 |
| `true` | 存在 | 缺失 | 抛 `GXBusinessException`（"请配置maple.framework.web.feign.token"），调用在发出前失败 |
| `true` | 不存在 | N/A | warn（"Failed to get FeignService instance, ..."）后跳过，其余头照常 |
| `false` | 任意 | 任意 | 跳过生成与注入，warn 后继续；traceId / platform 传播不受影响 |

**不变量**：开关只控制"是否生成并注入 token"，不控制 platform / traceId / 注解头 / 公共头。

## 3. traceId 解析顺序与上下文

1. `HttpServletRequest#getAttribute("X-B3-TraceId")`（非空白）；
2. `HttpServletRequest#getHeader("X-B3-TraceId")`（非空白）；
3. `GXTraceIdContextUtils#getTraceId()`（MDC）；
4. `GXTraceIdContextUtils#generateTraceId()`（新生成）。

新生成分支必须：写入请求头 → `putTraceId` → `finally` 中 `restoreTraceId(previous)`。**不得**在无请求上下文时抛出异常。

## 4. 阻断列表（不可通过 `@GXFeignHeader` 透传）

`authorization`、`cookie`、`set-cookie`、`x-auth-token`、`x-hmac-signature`、`x-api-key`、`host`、`content-length`、`connection`、`keep-alive`、`proxy-authenticate`、`proxy-authorization`、`te`、`trailer`、`transfer-encoding`、`upgrade`；匹配时大小写不敏感。

## 5. 已移除的头（行为变更）

| 头 | 移除理由 |
| --- | --- |
| `X-Content-Type-Options` | 响应语义，写入请求无效果 |
| `X-Frame-Options` | 同上 |
| `X-XSS-Protection` | 同上 |
| `Cache-Control` | `no-cache` 抑制幂等 GET 的代理缓存 |
| `Pragma` | 同上，HTTP/1.0 遗留 |
| `Expires` | 同上 |

## 6. 验证断言

1. 连续 `apply()` 两次，每个头只有一个值（幂等）。
2. 六个已移除头在出站请求中不存在。
3. 显式设置 `User-Agent` 时保持调用方值。
4. 无 `HttpServletRequest` 时不抛异常，`@GXFeignHeader` 头不出现。
5. 敏感头即使被 `@GXFeignHeader` 声明也不透传。
