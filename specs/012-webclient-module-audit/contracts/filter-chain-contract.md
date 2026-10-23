# Contract: 过滤器链与请求语义契约

**Scope**: `webClientBuilder` 构建的过滤器链（默认与直连 builder 共享；LB 守卫仅 `lbMode`）。

## 过滤器顺序（注册序 = 请求方向；响应方向逆序）

| # | 过滤器 | 请求期职责 | 响应期职责 | 变化 |
| --- | --- | --- | --- | --- |
| 1 | `requestFilter` | 头注入（见 [data-model §2](../data-model.md)）；token fail-fast（P-01）；快照优先取值（R-01，Reactor Context > `ClientRequest.attribute`）；TRACE 掩码输出 | — | 取值顺序/掩码/WARN 增量 |
| 2 | `retryFilter`（合并指标与日志） | 方法集合护栏（R-04）；**逐尝试**重建 `X-Request-Start-Time`（E-14 闭合） | `durationMs`（纯网络）/`attempts`/`retryDelayMs` 日志；Content-Length 阈值=配置值；TRACE 掩码；终态失败补 ERROR 日志；调用 `logRequestResponse`/`logRequestException` SPI；耗尽透传原始失败（P-02） | 口径分离（F-07）+ SPI 接线（F-09）+ 时间戳迁移 |
| 3 | `errorResponseHandleFilter` | — | 4xx/5xx → `GXBusinessException`（消息保真 R-03） | 解析双形状 |
| 4 | `lbGuardFilter`（新增，`lbMode` 时启用） | 主机形态判定（白名单优先）→ 非服务名即 `Mono.error`（400 段，不重试） | — | 新增（F-01） |
| 5 | LB 过滤器（spring-cloud 自动追加，最内层） | 服务名解析 | 合成 503（被 #4 前置拦截，不再到达） | 不受本模块控制 |

> 顺序冻结依据：既有 #1–#4 的相对顺序不得改变（P-01 短路语义、P-02 重试捕获语义依赖它）；新增过滤器只能插入内侧或外侧并说明。
>
> 层级口径：按"由外到内"排序为 **#1 requestFilter → #2 retryFilter → #3 errorResponseHandleFilter → #4 lbGuardFilter → #5 LB 过滤器**。`lbGuardFilter` 在 `retryFilter` 内侧，保证守卫错误（code=400）可被重试判定看到但因不可重试而 0 次尝试；`X-Request-Start-Time` 在 `retryFilter` 的 `Mono.defer` 中按尝试重建（不再需要独立过滤器）。
> 实现说明（`/speckit.implement` 偏差记录）：原计划的独立 `responseFilter` 已并入 `#2`，原因是 Spring Framework 7 的 `WebClient` 不会把 `RequestHeadersSpec` 的自定义属性带入 `ClientRequest`，跨过滤器共享指标不可靠；合并后语义与 FR-005 矩阵一致。
>
> 头注入的来源与取值顺序**以 [data-model §2](../data-model.md) 为唯一权威**，本表不重复定义（避免两份口径漂移）。

## 重试语义（与 data-model §4 一致）

- 默认可重试：方法 ∈ `retry-methods`（默认 `GET,HEAD,OPTIONS`）且（状态 ∈ {408,429,500,502,503,504} 或异常为 `WebClientRequestException`）。
- 耗尽：抛原始失败（`onRetryExhaustedThrow` 透传）；token 生成失败与 LB 守卫错误天然 0 重试。

## 日志字段契约（ASCII，格式可解析）

```
HTTP request: {method} {url}                          # DEBUG
HTTP response: statusCode={code}, durationMs={net}, attempts={n}, retryDelayMs={delay}   # DEBUG/INFO/WARN 分级不变（阈值 5000/2000 现在只作用于纯网络口径 durationMs）
HTTP request failed: durationMs={net}, attempts={n}, exceptionType={t}                   # ERROR（新增，失败必有日志）
HTTP request dropped context: keys={traceId,platform,token}                              # WARN（限流 30s≤1，F-03）
Load-balanced client rejects direct host: host={h}                                      # ERROR→业务异常（F-01）
```

- `durationMs` = 各次尝试网络耗时之和（**不含退避**）；`retryDelayMs` = 退避累计；总墙钟 = 两者之和（兼容说明：旧口径=总墙钟，监控口径迁移见 quickstart）。
- 头输出（TRACE）一律经掩码映射（`getSensitiveHeaderFields()`，缺 SPI 用内置映射）。

## 寻址模式契约

| 模式 | builder | 主机形态 | 行为 |
| --- | --- | --- | --- |
| 服务名（默认） | `webClient` / `webClientBuilder` | `^[A-Za-z0-9_-]+$` 简单名 | LB 解析转发（现状保持） |
| 服务名（白名单） | 默认 builder + `lb-guard-allow-hosts` | 带点服务名（`svc.ns.svc.cluster.local`）、登记在册的 FQDN | 放行并交 LB 解析（`serviceId = host`） |
| 直连 | `webClientDirectBuilder` 或 no-arg `webClientBuilder()` | 任意（FQDN/IP/端口） | 直接出网（`lbMode=false`） |
| 误用拦截 | 默认 builder | 未在白名单的 FQDN（含 `.`）/IP/`localhost`/带端口字面量/空 host | 明确业务异常 code=400，0 次尝试（F-01） |
| 守卫关闭 | 默认 builder + `lb-guard-enabled=false` | 任意 | 完全回到旧行为（交 LB 过滤器；直连主机将被合成 503，供兼容期使用） |
