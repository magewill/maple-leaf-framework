# Contract: GXWebClientService SPI 契约

**Type**: `cn.maple.webclient.service.GXWebClientService`（接口 + default 方法；可被下游 `@Bean` 实现覆盖）
**原则**: 方法签名冻结（宪法 II）；本次仅接线死扩展点、补空值标注、token 短窗缓存（默认实现内部），不增删方法。

## 方法语义与本次变化

| 方法 | 语义 | 冻结/变化 |
| --- | --- | --- |
| `generateHttpAuthToken()` | 读 `...token` → `generateHttpAuthToken(source, WEB_CLIENT_TOKEN_EXPIRE=150s)`；缺配置抛 `GXBusinessException`（中文消息，冻结） | **默认实现增加短窗缓存**（窗口 ≤60s 且 ≤expiry/2，按 source+secret 失效；F-06/FR-025；口径：60s 窗口内 1 次、150s 内 ≤3 次）；签名与异常语义不变 |
| `generateHttpAuthToken(String, int)` | blank source / expiry≤0 → `GXBusinessException`（英文消息，冻结）；委托 `GXAuthCodeUtils.authCodeEncode` | 不变 |
| `checkTokenValidity()` | 取 `X-Auth-Token` 头 → 解码 → 非空且 ≠`"{}"`（P-03 语义保持）；异常改 WARN 后 `false` | 日志增强（F-14） |
| `getAuthTokenSecret()` | 读 `...secret`；缺配置抛（冻结） | 不变 |
| `logRequestResponse(...)`×2 / `logRequestException(...)` | **本次接入过滤器链**（成功/失败路径均调用，R-07）；回调异常 try-catch 隔离 | 从死扩展点 → 活契约（兼容说明：实现类将开始收到回调） |
| `getSensitiveHeaderFields()` | 掩码映射（默认含 Authorization/Cookie/X-Auth-Token/X-HMAC-Signature/X-API-Key）；**本次起被 TRACE 输出消费**（R-06） | 从死契约 → 活契约 |
| `getPlatform()` | 请求头 `PLATFORM`；`@Nullable` 返回 | 补 `@Nullable`（宪法 V） |
| `generateHmac` / `checkHmac` | 委托 `GXCommonUtils`（工具透传） | 不变 |
| `getTraceId()` | 请求属性 > MDC 回落（P-07）；无上下文返回空串（非 null） | 不变 |

## 调用时机（接线后，R-07）

| SPI | 触发点 | 参数完备性 |
| --- | --- | --- |
| `logRequestResponse`（全参） | 响应成功、body 可消费时 | method/url/请求头(掩码)/请求体/状态/响应头(掩码)/响应体/`durationMs`（纯网络） |
| `logRequestResponse`（简版） | 响应成功（恒调，作为兜底） | method/url/status/`durationMs`/`requestId`（取 TraceId） |
| `logRequestException` | 任何终态失败（含重试耗尽、LB 守卫拒绝、token 失败） | method/url/exception/`requestId` |

> 对外消息口径区分：SPI 方法自身抛出的语言按上表（中文/英文各自冻结）；`requestFilter` 捕获后对外抛出的 `GXBusinessException("Generate WebClient auth token failed", e)` 是**另一层**消息，两者都不得单方面改动（P-01 + 宪法 II）。

## 空值标注计划（触碰即补，JSpecify）

- `@Nullable`：`getPlatform()` 返回、`logRequestResponse`/`logRequestException` 的可空参数（requestBody/responseBody/requestId）、`GXWebClientContextSnapshot` 全部字段。
- 包级 `@NullUnmarked` 现状保留（宪法现状约定）；公共 API 可空处显式 `@Nullable`，NullAway `JSpecifyMode` 构建通过为完成定义。

## 多实现语义（F-18）

- 容器内多个 `GXWebClientService` Bean：`getBean(Class)` 现状静默 null → 报错文案误导。修复：切面/过滤器改用 `ObjectProvider` 语义（0 或 1 可用，多于 1 时 WARN 并取第一个），报错文案区分"未实现"与"实现多于一个"。
