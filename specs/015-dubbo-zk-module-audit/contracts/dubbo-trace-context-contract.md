# Contract: TraceId 上下文（leaf-base-dubbo-zk）

**Scope**: `GXPenetrateAttachmentSelector`、`GXDubboClientTraceIdFilter`、`GXDubboServerTraceIdFilter`、`GXDubboExceptionFilter`
**Key**: `X-B3-TraceId`（`GXTraceIdContextUtils.TRACE_ID_KEY`）

## 1. 来源优先级

| 组件 | 优先级（左高右低） |
| --- | --- |
| selector `select`（consumer 请求） | invocation 附件 → clientAttachment → serverAttachment → MDC → 生成 |
| selector `selectReverse`（provider 响应） | invocation 附件 → serverResponse → clientResponse → MDC → 生成 |
| client filter（consumer） | invocation 附件 → clientAttachment → serverAttachment → MDC → 生成 |
| server filter（provider） | invocation 附件 → serverAttachment → clientAttachment → MDC → 生成 |
| exception filter（双侧，异常路径） | **invocation 附件 → MDC**（新增，不得只读 MDC） |

规则：

- "空/空白"值等价于不存在（`CharSequenceUtil.isBlank`）。
- 每次解析**只生成一次**；同一组件内不得重复生成。
- 生成结果形如 `<spring.application.name>:<uuid>`（由 `GXTraceIdContextUtils.generateTraceId` 决定，模块不重复实现）。

## 2. 写回位置

| 组件 | MDC | invocation 附件 | clientAttachment | serverAttachment | Result 附件 | 异常 `data` |
| --- | --- | --- | --- | --- | --- | --- |
| client filter | 是（`finally` 恢复） | 是 | 是 | 是 | 是（异步走回调） | — |
| server filter | 是（`finally` 恢复） | 是 | 是 | 是 | 是（异步走回调） | — |
| selector | 否 | 否（由 `ConsumerContextFilter` 写入） | 否 | 否 | 否（由 `ContextFilter` 写入） | — |
| exception filter | 否 | 否 | 否 | 否 | 是 | 是（`GXBusinessException` 路径） |

说明：

- client filter 对 `serverAttachment` 的写入、server filter 对 `clientAttachment` 的写入**保留**（下游业务代码可能读取），以用例锁定并在手册登记；不得以"语义更干净"为由移除。
- `RpcContext` 附件由 Dubbo 的 `ContextFilter` / `ConsumerContextFilter` 在每次调用结束时清理，模块不额外清理（避免与 Dubbo 生命周期冲突）。

## 3. 恢复语义（不得破坏）

| 场景 | 要求 |
| --- | --- |
| 正常返回 | `finally` 中 `restoreTraceId(original)`；进入前为 null → 清除 |
| `invoker.invoke` 抛异常 | 同上（`finally` 保证），MDC 不得残留 |
| `Result` 为 `AsyncRpcResult` | `whenCompleteWithContext` 回调内 `put` + `finally restore`，不得跨线程泄漏 |
| `Result` 为 null / 普通 `Result` | 直接 `setAttachment` 或跳过，不得 NPE |
| listener 阶段（异常 filter） | 读 invocation 附件；MDC 可能已被 server filter 恢复（已由 Dubbo `FilterChainBuilder` 源码确认） |

## 4. 一致性契约

- **同一次调用一个 id**：selector 返回的 id、client filter 写入 MDC 的 id、server filter 读到的 id、异常响应 `data` 中的 id 必须相同（用例 `oneTraceIdAcrossConsumerAndProvider`）。
- 异常响应的 `Result` 附件必须带 traceId（即使 MDC 已恢复）。
- `GXBusinessException.data` 的 traceId 注入只允许在 `data` 非空时原地写；`data` 为空时按 [dubbo-exception-contract.md](./dubbo-exception-contract.md) §3 处理。

## 5. 性能契约

- `spring.application.name` 通过 `GXPenetrateAttachmentSelector.getCachedAppName()` 读取：首次查 `Environment`，之后命中静态缓存；`UnknownApp` 不缓存。
- 核心链路（filter / selector）不得输出 INFO 日志；诊断日志使用 DEBUG。
- 单次调用 `Environment` 查询次数上限：有入站 traceId ≤1，无入站 ≤2。

## 6. 本次变更清单

| 变更 | 旧行为 | 新行为 |
| --- | --- | --- |
| exception filter 的 traceId 来源 | 仅 MDC（listener 阶段已被清空） | invocation 附件优先，MDC 回落 |
| 单次解析次数 | 3 次 | 1 次 |
| appName 读取 | 两个 filter 各自每请求查 `Environment` | 共享静态缓存 + `resetAppNameCache()` |
| 日志级别 | INFO（每请求 2 条） | DEBUG |
