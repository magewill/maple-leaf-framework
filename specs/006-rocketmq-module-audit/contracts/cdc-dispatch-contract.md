# 契约：CDC 分发（`GXCdcMessageDispatcher`）

**范围**：`dispatch(String message, String bizType)`。**兼容性**：方法签名不变；异常语义由"不抛"调整为"仅监听器异常汇总后抛"，属于收紧而非破坏（原实现无调用方可依赖抛错）。

## 1. 开关契约

| 键 | 作用 | 缺省 | 优先级 |
| --- | --- | --- | --- |
| `maple.framework.cdc.enable` | Bean 是否装配 | `false`（不装配） | 装配期 |
| `maple.framework.cdc.active-status` | 运行期开关 | 回退到旧键 | 调用期（每消息读取一次） |
| `cdc.active.status` | 旧键，兼容保留 | `disabled` | 低于新键 |

**规则**：新键存在时以新键为准；旧键命中时按 `debug` 输出一次迁移提示（每个上下文只提示一次，避免刷屏）。开关关闭时**必须** `warn`（当前为 `debug`），消息内容不打印，只打印 `bizType`。

## 2. 失败分层契约

| 场景 | 日志级别 | 计数键 | 是否抛给消费方 | 死信 |
| --- | --- | --- | --- | --- |
| 开关关闭 | `warn` | `skipped` | 否 | 否 |
| message 为 null/空白 | `warn` | `parseFailure` | 否 | 可选 |
| 非 JSON / 结构不可读 | `warn` | `parseFailure` | 否 | 可选 |
| payload 为空 | `warn` | `parseFailure` | 否 | 可选 |
| `op` 缺失或未知 | `warn` | `unknownOperation` | 否 | 可选 |
| 无监听器匹配 | `debug` | `noTarget` | 否 | 否 |
| 监听器异常（≥1） | `error`（含监听器类名） | `listenerFailure` | **是**，抛 `GXCdcDispatchException` | 否 |

**死信契约**：`maple.framework.cdc.dead-letter-topic` 非空且死信转发成功 → `deadLetter` 计数 +1 且返回；转发失败 → `error` 日志且**不得**因此抛出（避免"死信不可用导致正常消费中断"）。

## 3. 监听器执行契约

1. 遍历顺序：按 Spring 注入顺序（`@Order` 生效）；结果不得依赖顺序。
2. 隔离：每个监听器独立 `try-catch`；异常累积，不中断遍历。
3. 汇总：全部执行完后若存在异常 → 构造 `GXCdcDispatchException`，`failedListenerCount` = 失败监听器数，原始异常逐个 `addSuppressed`。
4. 事件不可变：分发给监听器的 `GXCdcEvent` 为**只读视图**（无 setter，集合字段不可变）；监听器无法修改后续监听器可见的内容。
5. 无重试：模块内部不重试；重试由消费方（业务模块）决定。

## 4. 事件契约（`GXCdcEvent`）

- `before` / `after` / `source` / `transaction`：不可变 Map（缺失时为**空 Map**，保持现状语义）。
- `op`：原始 op 字符串；`getOperation()` 返回枚举或 `null`。
- `tsMs`：字符串，缺失为 `""`。
- `rawMessage`：原始消息全文，仅用于排障；**不得**写入日志或异常消息。
- `isOnlyChanged(field)`：语义不变（`Maps.difference` 结果为单键差异），但计算次数由"每监听器一次"降为"每事件一次"（缓存结果）。

## 5. 回归断言点

1. 非 JSON / `op=x` / 空白消息 → 不抛异常，计数相应 +1，日志为 `warn`。
2. 两个监听器，前者抛 `RuntimeException` → 后者仍被调用一次，最终抛 `GXCdcDispatchException` 且 `getSuppressed().length == 1`。
3. 监听器尝试修改事件（若存在 setter）→ 编译期不可行 / 运行期抛 `UnsupportedOperationException`。
4. 开关关闭 → 消息不被任何监听器看到，且日志级别为 `warn`。
5. 死信 topic 已配置且转发失败 → 不抛异常，`error` 日志可检索。
