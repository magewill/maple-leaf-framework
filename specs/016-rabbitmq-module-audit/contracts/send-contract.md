# 契约：发送与资源管理 API（GXSendRabbitMQService）

**Feature**: `016-rabbitmq-module-audit` | **Date**: 2026-10-25 | **Spec**: [../spec.md](../spec.md)

方法签名不变。下表"失败"列是修复后的**唯一**表达方式；同一方法不再混用 `null`/`false`/异常。

## 方法矩阵

| 方法 | 返回 | 成功 | 失败 | 空值契约 |
| --- | --- | --- | --- | --- |
| `sendNormalMessage(GXRabbitMQMessageReqDto)` | `Object`（保持 `null`，不承诺消息 ID） | 已提交到 `RabbitTemplate`（不等于 Broker 确认） | 参数非法 → `GXBusinessException`；连接/协议错误 → `AmqpException`（保留原类型） | DTO/`exchange`/`routingKey`/`data`/`messageProperties` 必须非空；不修改调用方 `MessageProperties` |
| `createQueue(name, durable, exclusive, autoDelete, args)` | `Queue` | Broker 声明成功，返回的属性与实际声明一致 | `GXBusinessException`（参数）或 `GXRabbitMQOperationException`（Broker 冲突/失败） | 参数非空；`args` 可为 `null` |
| `createDurableQueue(name)` | `Queue` | 同上（`durable=true, exclusive=false, autoDelete=false`） | 同上 | 同上 |
| `createTemporaryQueue(name)` | `Queue` | `durable=false, exclusive=false, autoDelete=true` 声明成功 | 同上 | 同上；**排他队列不在支持范围** |
| `deleteQueue(name)` | `boolean` | `true`=已删除；`false`=Broker 明确不存在 | `GXRabbitMQOperationException`（传输/权限） | 参数非空 |
| `queueExists(name)` | `boolean` | 以 `RabbitAdmin.getQueueProperties` 为准 | `GXRabbitMQOperationException`（传输失败，不再返回 `false` 掩盖） | 参数非空 |
| `purgeQueue(name)` | `boolean` | `true`=已清空；**不会返回 `false`**（队列不存在由 Broker 返回 404，包装为异常） | `GXRabbitMQOperationException` | 参数非空 |
| `createExchange(exchange)` | `boolean` | `true`=声明成功或已存在且属性一致（幂等） | `GXRabbitMQOperationException`（传输失败或"已存在但类型不同"的 `PRECONDITION_FAILED`） | 参数非空；`getName()` 非空白；**不会返回 `false`**（保留返回类型仅为签名兼容） |
| `bindQueueToExchange(q, ex, rk)` | `boolean` | `true`=绑定成功或已存在（幂等） | `GXRabbitMQOperationException` | 三个参数非空/非空白；**不会返回 `false`** |
| `unbindQueueFromExchange(q, ex, rk)` | `boolean` | `true`=解绑完成（`removeBinding` 幂等，**不返回 `false`**） | `GXRabbitMQOperationException` | 同上 |
| `setupMessageChannel(...)` | `boolean` | `true`=交换机+队列+绑定全部成功；**不会返回 `false`** | `GXRabbitMQOperationException`（`detail` 含已完成步骤与失败步骤） | `exchange`/`routingKey`/`queueName` 非空 |
| `setupDurableMessageChannel(...)` | `boolean` | 同上（持久化参数） | 同上 | 同上 |
| `setupMessageChannelAsync(...)` | `CompletableFuture<Boolean>` | 成功 `true` | `failedFuture(GXRabbitMQOperationException)`；参数非法同步抛 `GXBusinessException`；线程池拒绝 → `failedFuture` | 同上 |

## 消息头契约（保持不变）

| 头 | 值 | 说明 |
| --- | --- | --- |
| `X-Trace-Id` | 调用方 MDC traceId，缺失时 `appName:uuid` | 每条消息一个快照值，不复用可变对象 |
| `X-Timestamp` | epoch millis | 与 `MessageProperties.timestamp` 同源 |
| `X-Source-Service` | `spring.application.name`，缺省 `unknown` | 装配期注入，不逐条查询 Environment |
| `messageId` | 调用方已设置则保留，否则等于 `X-Trace-Id` | 不改写已有值 |
| `__TypeId__` | `cn.hutool.core.lang.Dict` | 由 `DefaultClassMapper` 白名单解析 |
| `contentType` / `contentEncoding` | `application/json` / `UTF-8` | 写入快照，不写调用方对象 |

## 序列化契约

- 出站载荷**继续使用 Hutool**（`java.util.Date` / `LocalDateTime` 均为毫秒时间戳），与修复前 wire format 完全一致；不因"统一序列化栈"而改变时间类型格式。
- 模板装配的 `JacksonJsonMessageConverter` 使用模块共享 `JsonMapper`（`JavaTimeModule`、`NON_NULL`），负责入站类型化转换；必须能读取 Hutool 输出（用例 `GXMessageSerializationTest` 锁定）。
- 发送写入 `__TypeId__=cn.hutool.core.lang.Dict`；`DefaultClassMapper` 信任包必须包含 `cn.hutool.core.lang`（`"cn.hutool.core"` 不覆盖子包）。
- `consumerTag` 不再写入发送消息；`tag` 字段保留但发送侧忽略。

## 兼容性

- 方法签名、异常类型（`AmqpException`/`GXBusinessException`）、消息头名称与语义不变。
- 行为变化：创建/通道类失败由 `null`/`false` 改为异常；`queueExists` 传输失败不再返回 `false`；`sendNormalMessage` 不再改写调用方 `MessageProperties`。均登记在 [../plan.md](../plan.md) 的"契约变更与迁移"表。

## 验证要点

1. 13 个方法各有成功、失败、空值/边界用例（`spec.md` FR-025）。
2. 发送后断言调用方 `MessageProperties` 未被改写（FR-010）。
3. MDC 有 traceId 时断言复用、无时断言生成且格式合法（FR-009）。
4. `setupMessageChannelAsync` 在线程池拒绝与 Broker 失败两种情况下都返回失败 Future（FR-011、FR-017）。
