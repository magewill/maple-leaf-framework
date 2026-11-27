# 契约：默认队列监听器（AMQP 1.0）

**Feature**: `016-rabbitmq-module-audit` | **Date**: 2026-10-25（2026-11-27 随 AMQP 1.0 迁移更新）

## 启停与地址

| 项 | 规则 |
| --- | --- |
| 启用条件 | `@ConditionalOnProperty(prefix="maple.framework.mq.rabbitmq", name="enable", havingValue="true")` |
| 类路径条件 | `@ConditionalOnClass(name="org.springframework.amqp.client.annotation.AmqpListener")` |
| 地址解析 | `/queues/${maple.framework.mq.rabbitmq.default-queue-name:${spring.rabbitmq.default-queue-name:${spring.rabbitmq.defaultQueueName:maple.default.queue}}}` |
| 默认值 | `maple.default.queue`（`GXRabbitMQProperties#defaultQueueName`） |
| 初始化 | 饿汉：`@Lazy` 会让 `@AmqpListener` 端点永不注册 |
| 行为 | 队列不存在时链路 attach 被拒，容器按 `recovery-backoff` 周期性重试；模块不自动声明该队列 |

> RabbitMQ 只接受 AMQP 1.0 v2 地址，消费源地址必须是 `/queues/<queue>`；裸队列名会以
> `amqp_address_v1_not_permitted` 被拒绝。

## 结果矩阵

容器以 `autoAccept=false` 运行，`onMessage(Message, AmqpAcknowledgment)` 显式结算：

| 输入/事件 | 处理 | 结算 | 计数（无 MeterRegistry 时降级为日志） |
| --- | --- | --- | --- |
| 正常 JSON | 解析为 `Dict` 并 debug 记录 | `ACCEPT` | `rabbitmq.listener.messages.processed` |
| 正常非 JSON | debug 记录原文 | `ACCEPT` | 同上 |
| 空消息体（空白） | warn（ASCII） | `REJECT` | `rabbitmq.listener.messages.rejected` |
| JSON 解析失败 | warn（ASCII，含长度与异常类名，不打印完整报文） | `REJECT` | 同上 |
| 业务/运行时异常 | error（ASCII） | `REQUEUE` | `rabbitmq.listener.messages.failed` |
| 消息体无法被转换器解析（监听器方法未执行） | warn（`reason=unconvertible`） | `REJECT`（由 `GXAmqpListenerErrorHandler`） | 不计数 |

**不变量**：不存在"只记日志并正常返回"的路径；每条 delivery 都必须被显式结算；
结算调用位于 `try` 之外，结算失败不得被当作业务失败再触发一次 `REQUEUE`。

## 日志规范

- 全部 ASCII；关键字固定便于检索：`RABBITMQ_LISTENER_REJECTED`、`RABBITMQ_LISTENER_REQUEUE`。
- 毒消息由 `GXAmqpListenerErrorHandler` 记录 `RABBITMQ_LISTENER_REJECTED reason=unconvertible`。
- 不打印完整消息体（仅长度与摘要前缀 ≤64 字符），避免敏感数据与日志膨胀。
- 正常消息仅 `debug`，不产生每消息 `info`。

## 指标规范

| 指标 | 类型 | 标签 |
| --- | --- | --- |
| `rabbitmq.listener.messages.processed` | Counter | 无（保持低基数） |
| `rabbitmq.listener.messages.rejected` | Counter | `reason=empty|unparseable` |
| `rabbitmq.listener.messages.failed` | Counter | `type=<异常简单类名>`（受控白名单，未知归 `other`） |

`MeterRegistry` 通过 `ObjectProvider` 可选获取；缺失时仅日志，不新增依赖。

## 兼容性

- 0-9-1 时代通过抛 `AmqpRejectAndDontRequeueException` 让容器拒绝；AMQP 1.0 改为显式
  `REJECT` 结算。自定义监听器若仍依赖异常语义，需要自己结算或实现 `AmqpAcknowledgment` 参数。
- 队列名解析保持"属性占位符链"，`maple.framework.mq.rabbitmq.default-queue-name` 仍为最高优先级。
- 容器不再"先 ack 后处理"：`autoAccept=false` 是默认监听器正确工作的前提。

## 验证要点

1. 空消息、非 JSON、JSON 非法、业务异常四类用例分别断言结算状态（ACCEPT/REJECT/REQUEUE）。
2. `GXAmqpListenerErrorHandler`：转换失败 → `rejected` disposition；其他异常原样抛出。
3. `MeterRegistry` 存在/缺失两种情况下都不断言失败。
4. `enable=false`（默认）时 Bean 不存在；`enable=true` 时 Bean 存在且地址以 `/queues/` 开头。
5. 日志断言：无中文文案；不含完整消息体。
