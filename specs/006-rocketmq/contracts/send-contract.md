# 契约：消息发送（`GXSendRocketMQService`）

**范围**：`cn.maple.rocketmq.service.GXSendRocketMQService` 的 5 个方法。**兼容性**：方法签名、返回值类型、异常类型在本特性中**不得变更**。

## 1. 统一输入契约（`GXRocketMQMessageReqDto`）

| 字段 | 规则 | 拒绝方式 |
| --- | --- | --- |
| topic | 去首尾空格后非空；匹配 `^[%a-zA-Z0-9_-]{1,127}$` | `GXBusinessException`，消息含 `Message topic` |
| tag | 可为空；去空格；长度 ≤128；不含 `:` | `GXBusinessException`，消息含 `Message tag` |
| body | 非空；UTF-8 字节数 ≤ `rocketmq.producer.max-message-size`（默认 4MB）；**不得为字面量 `"null"`** | `GXBusinessException`，消息含 `Message body` |
| messageKey | 可为空；去空格；长度 ≤255 | `GXBusinessException`，消息含 `messageKey` |
| deliverTime | 仅 `sendDelayMessage` 校验：必须 > 0 且 `currentTimeMillis + deliverTime*1000` 不溢出 | `GXBusinessException` |

**前置条件**：以上校验全部在触达 Broker 之前完成（真实网络往返 0 次）。

**构造器约束**：`GXRocketMQMessageReqDto(Object body)` 在 `body == null` 时抛 `GXBusinessException`，不再生成 `"null"` 字符串；`(String tag, Object body)` 与 `(Object body)` 的重载歧义按 javadoc 说明处理，不新增构造器。

## 2. 方法级契约

| 方法 | 返回值 | 成功条件 | 失败表达 | 阻塞 | 投递语义 |
| --- | --- | --- | --- | --- | --- |
| `void sendNormalMessage(dto)` | 无 | `SendStatus.SEND_OK` | 抛 `GXBusinessException` | 是（1 次 RTT） | 至少一次（上游重试可致重复） |
| `boolean syncSend(dto)` | 恒 `true` | 同上 | 抛 `GXBusinessException`，**不返回 false** | 是（1 次 RTT） | 至少一次 |
| `String sendDelayMessage(dto)` | `msgId` | 同上 + `deliverTime > 0` | 抛 `GXBusinessException` | 是（1 次 RTT） | 至少一次（依赖 Broker 定时能力） |
| `boolean sendAsync(dto)` | 提交成功即 `true` | 请求已提交给生产者线程池 | 提交阶段异常抛 `GXBusinessException`；**最终失败不回传**（见 async-failure-contract） | 否 | 尽力投递 + 可选失败回调 |
| `boolean sendOneway(dto)` | 提交成功即 `true` | 请求已发出 | 提交阶段异常抛 `GXBusinessException` | 否 | 至多一次（客户端无感知丢失） |

## 3. 目的地拼装

- `tag` 空白 → destination = `topic`。
- 否则 → destination = `topic:tag`。
- `topic` 含 `:` 属非法输入（由第 1 节校验拦截），保证拼装无歧义。

## 4. 消息头

- `messageKey` 非空时写入 `RocketMQHeaders.KEYS`；空白时不写。
- 不写自定义 tag 头（tag 只通过 destination 表达）。

## 5. 日志契约（性能相关）

- 成功路径：**0 条 `info` 日志**（统一 `debug`）。
- 失败路径：`error` 日志必须包含 destination、`messageKey`、`SendStatus`（可空）与异常摘要。
- 参数校验失败：`GXBusinessException` 由调用方处理，模块本身不重复打 error。

## 6. 回归断言点

1. `SEND_OK` → 正常返回 / 返回 `true` / 返回 `msgId`。
2. `null`、`FLUSH_DISK_TIMEOUT`、`SLAVE_NOT_AVAILABLE` → 抛 `GXBusinessException`，消息含状态名。
3. 非法 topic / 超长 tag / `null` body → 抛异常且 `verifyNoInteractions(rocketMQTemplate)`。
4. 成功路径日志级别为 `debug`（用 appender 断言 `info` 计数为 0）。
