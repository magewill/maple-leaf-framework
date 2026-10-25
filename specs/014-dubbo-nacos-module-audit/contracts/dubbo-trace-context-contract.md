# Contract: TraceId 透传与上下文恢复

**Feature**: 014-dubbo-nacos-module-audit | **Date**: 2026-10-25
**范围**: `GXDubboClientTraceIdFilter`、`GXDubboServerTraceIdFilter`、`GXPenetrateAttachmentSelector`

## 1. TraceId 优先级（不可随意调整，AGENTS.md §3.1）

| 阶段 | 优先级（高 → 低） |
| --- | --- |
| Consumer 请求 | `Invocation` attachment → client attachment → server attachment → MDC → `generateTraceId()` |
| Provider 请求 | `Invocation` attachment → **server** attachment → client attachment → MDC → `generateTraceId()` |
| Selector `select`（请求） | `Invocation` attachment → client attachment → server attachment → MDC → `generateTraceId()` |
| Selector `selectReverse`（响应） | `Invocation` attachment → **server** attachment → client attachment → MDC → `generateTraceId()` |

- consumer 与 provider、select 与 selectReverse 的差异是**有意镜像**（请求方向与响应方向的 attachment 归属不同），不得"统一成一版"。
- 每一次读取都先 `getObjectAttachment`，为 null 时才 `getAttachment`（保持既有行为）。

## 2. 写回与恢复（P-OK-01，不可破坏）

- 进入时保存 `originalTraceId = GXTraceIdContextUtils.getNullableTraceId()`（可能为 null）。
- 解析出 TraceId 后：
  - `GXTraceIdContextUtils.putTraceId(traceId)`；
  - `invocation.setAttachment(TRACE_ID_KEY, traceId)`；
  - `RpcContext.getServerAttachment().setAttachment(...)`；
  - `RpcContext.getClientAttachment().setAttachment(...)`。
- `finally` 中 `GXTraceIdContextUtils.restoreTraceId(originalTraceId)`（blank → remove，保证"进入前为空"时清除）。
- 异步结果（`AsyncRpcResult`）：`whenCompleteWithContext` 回调中**再次**保存/设置/恢复 MDC，禁止跨线程泄漏。

## 3. Selector 透传范围（P-OK-04）

- `select` / `selectReverse` **只返回** `{X-B3-TraceId: traceId}`（1 项），不得透传其它 attachment（如 `author`、`SPC`）。
- 入参 `invocation` 允许为 null（返回其它来源）；attachment 允许为 null。
- 扩展名 `dubboPenetrateAttachmentSelector` 不得重命名。

## 4. 共享 `appName` 缓存（F-08/F-09）

- `spring.application.name` 只解析一次并缓存（`volatile`），消费者：两个 TraceId Filter 与 Selector 统一读取同一缓存。
- 提供 `resetAppNameCache()`：测试隔离、上下文刷新时调用。
- 热路径（每请求）**不得**再调用 `GXCommonUtils.getEnvironmentValue` / `GXSpringContextUtils.getEnvironment`。

## 5. 日志（F-10）

- TraceId Filter 的"propagate/receive TraceId"日志为 **DEBUG**（热路径，每请求一次）。
- `generateTraceId` 兜底分支日志为 DEBUG。
- 日志文案必须为 ASCII。
