# 契约：异步发送失败扩展点

**范围**：`sendAsync` 最终失败的可补偿通道。**兼容性**：`sendAsync` 签名与返回值不变；扩展点可选，未注册时行为与现状等价（仅日志）。

## 1. 类型

```java
@FunctionalInterface
public interface GXRocketMQAsyncSendFailureHandler {
    void onFailure(GXRocketMQAsyncSendFailureContext context);
}
```

`GXRocketMQAsyncSendFailureContext`（不可变 record）字段见 [data-model.md](../data-model.md) B-1。

## 2. 调用时机

| 生产者回调 | 是否回调扩展点 | context 组成 |
| --- | --- | --- |
| `onSuccess(SEND_OK)` | 否 | — |
| `onSuccess(null)` | 是 | `sendStatus=null`、`cause=IllegalStateException("null send result")` |
| `onSuccess(非 SEND_OK)` | 是 | `sendStatus=<status>`、`msgId=<msgId>`、`cause=null` |
| `onException(t)` | 是 | `sendStatus=null`、`cause=t`（`t==null` 时用占位异常） |

## 3. 执行契约

1. 线程：在生产者的回调线程上同步执行；**不得**阻塞，实现方自行保证（契约文档注明）。
2. 隔离：每个实现单独 `try-catch`；实现抛异常只记 `error`，不影响其他实现，也不影响生产者回调线程。
3. 顺序：多个实现按 `@Order` 升序执行。
4. 幂等提示：同一消息在客户端重试后可能产生多次失败回调，实现方需自行去重（以 `messageKey` 为幂等键）。
5. 上下文快照：`context` 的 `topic/tag/messageKey` 取自发送前快照，调用方后续修改 DTO 不影响回调内容。

## 4. 兼容性

- 未注册实现：`ObjectProvider#ifAvailable` 为空 → 仅 `error` 日志，与当前行为一致。
- 注册 1 个实现：不改变 `sendAsync` 的返回值与耗时（同线程内多一次接口调用）。
- 不新增配置键；不改变失败日志级别（保持 `error`）。

## 5. 回归断言点

1. 无实现 + `onSuccess(非 SEND_OK)` → 不抛异常，`error` 日志存在。
2. 有实现 + `onException(new RuntimeException("boom"))` → 实现收到 `cause.getMessage()=="boom"`、`sendStatus==null`。
3. 两个实现，前者抛异常 → 后者仍被调用一次。
4. `sendAsync` 返回 `true`，`verify(rocketMQTemplate).asyncSend(...)` 恰好一次（不因扩展点产生额外发送）。
