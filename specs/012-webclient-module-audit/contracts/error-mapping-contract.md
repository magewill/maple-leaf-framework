# Contract: 错误映射契约

**Scope**: `errorResponseHandleFilter` + `GXWebClientExceptionHandler`；目标异常类型 `GXBusinessException`（`code`/`msg`/`data`）与 `GXWebClientAuthTokenException` 结构**冻结**（宪法 III）。

> DTO 取舍：解析使用模块内 `GXWebClientErrorResDto`（双形状 `msg`/`message`/`code`/`status`），理由与替代方案见 plan Complexity ⑥；该类型**限定单一用途**，不得作为通用 DTO 扩散（宪法附加约束 4）。

## 消息公式（前缀冻结，detail 取值修复）

| 场景 | code | msg | 变化 |
| --- | --- | --- | --- |
| 4xx | 远端 HTTP 状态 | `"客户端请求错误(4xx): {detail}"` | `{detail}` 保真 |
| 5xx | 远端 HTTP 状态 | `"服务器处理错误(5xx): {detail}"` | 同上 |
| LB 守卫拦截 | 400（不重试段） | `"负载均衡客户端仅支持服务名寻址（host={host}）；直连请使用 webClientDirectBuilder；带点服务名请登记 lb-guard-allow-hosts"` | 新增（F-01） |
| token 生成失败 | 500（现状） | `"Generate WebClient auth token failed"`（英文，现状冻结） | 不变（P-01） |

## {detail} 解析优先级（双形状，R-03）

1. 错误体 JSON 有 `msg`（框架统一响应形状）→ 用 `msg`；**修复 F-02 的核心**。
2. 有 `message`（Spring 默认错误体形状）→ 用 `message`（现状）。
3. body 非 JSON / 空 / 解析失败 → `getHttpStatusDescription(status)`（现状）。
4. 错误体 `code` 字段存在 → 覆盖 `code` 取值（缺省 HTTP 状态）。

## 调用方可见异常矩阵（验收基准）

| 触发 | 调用方捕获 | 网络尝试次数 | 重试次数 |
| --- | --- | --- | --- |
| 远端 503 + 框架错误体 | `GXBusinessException`，`code=503`，`msg` 含对端 `msg` | 1 | 3（GET）/0（POST 默认） |
| 远端 404 + 空 body | `GXBusinessException`，`code=404`，通用文案 | 1 | 0 |
| 直连 URL + 默认 builder | `GXBusinessException`，`code=400`，寻址指引文案 | **0** | 0 |
| 连接重置 + GET | 耗尽后原始 `WebClientRequestException`（P-02 透传） | 4 | 3 |
| 连接重置 + POST（默认） | `WebClientRequestException` | 1 | 0 |
| token 配置缺失 | `GXBusinessException("Generate WebClient auth token failed")` | 0 | 0 |
| 注解校验失败 | `GXWebClientAuthTokenException`（结构不变） | — | — |

## `GXWebClientExceptionHandler`（`@RestControllerAdvice`）

| 处理器 | 返回 | 变化 |
| --- | --- | --- |
| `WebClientRequestException` | `GXResultUtils.error(500, "WebClient网络请求异常")` | **冻结**（文案与返回码均不得改动，T036 断言原文保留） |
| `WebClientResponseException` | `GXResultUtils.error(远端状态, 统一中文文案)` | 文案由英文改为中文（FR-026 允许收敛，返回码语义不变）；手册声明：该分支面向**未经本模块过滤器**的外来 `WebClient` 异常——本模块客户端的 4xx/5xx 已在过滤器内转 `GXBusinessException`（T036 直接构造异常类锁定可达性） |

**日志约束**：异常日志 ASCII（AGENTS 1）；`responseLength` 等诊断字段保留；`checkTokenValidity` 内部异常改记 WARN（`exceptionType` only，不含敏感值）后返回 false（F-14）。
