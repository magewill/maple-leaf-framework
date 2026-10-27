# 契约：默认队列监听器

**Feature**: `016-rabbitmq-module-audit` | **Date**: 2026-10-25

## 启停与队列名

| 项 | 规则 |
| --- | --- |
| 启用条件 | `@ConditionalOnProperty(prefix="maple.framework.mq.rabbitmq", name="enable", havingValue="true")`（替换 `@ConditionalOnExpression`，避免 SpEL 解析期失败） |
| 类路径条件 | `@ConditionalOnClass(ConnectionFactory)`（不变） |
| 队列名解析 | `${maple.framework.mq.rabbitmq.default-queue-name:#{@gxRabbitMQProperties.defaultQueueName}}` |
| 默认值 | `maple.default.queue`（`GXRabbitMQProperties#defaultQueueName`） |
| 行为 | 队列不存在时由容器周期性重试并输出容器日志；模块不自动声明该队列（避免隐式资源创建） |

`GXRabbitMQProperties.defaultQueueName` 由此真正生效：`spring.rabbitmq.default-queue-name` 与 `defaultQueueName` 两种写法都通过 relaxed binding 绑定到该字段。

## 结果矩阵

| 输入/事件 | 处理 | 抛出 | 计数（无 MeterRegistry 时降级为日志） |
| --- | --- | --- | --- |
| 正常 JSON | 解析为 `Dict` 并 debug 记录 | 无 | `rabbitmq.listener.messages.processed` |
| 正常非 JSON | debug 记录原文 | 无 | 同上 |
| 空消息体（空白） | warn（ASCII） | `AmqpRejectAndDontRequeueException` | `rabbitmq.listener.messages.rejected` |
| 非 UTF-8 / JSON 解析失败 | warn（ASCII，含长度与异常类名，不打印完整报文） | `AmqpRejectAndDontRequeueException` | 同上 |
| 业务/运行时异常 | error（ASCII） | 原样抛出，由容器 `default-requeue-rejected` 与重试策略决定 | `rabbitmq.listener.messages.failed` |

**不变量**：不存在"只记日志并正常返回"的路径；`AmqpException` 不再单独吞掉。

## 日志规范

- 全部 ASCII；关键字固定便于检索：`RABBITMQ_LISTENER_REJECTED`、`RABBITMQ_LISTENER_FAILED`。
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

- 默认监听器此前永不抛异常，修复后会抛出 `AmqpRejectAndDontRequeueException`；若下游依赖"永不失败"的行为，需要在容器层设置 `default-requeue-rejected=false` 并配置死信，登记在 `AGENTS.md`。
- 队列名解析从纯占位符链改为"占位符 + Properties Bean 回退"，`maple.framework.mq.rabbitmq.default-queue-name` 仍为最高优先级。

## 验证要点

1. 空消息、非 JSON、JSON 非法、业务异常四类用例分别断言抛出的异常类型。
2. `MeterRegistry` 存在/缺失两种情况下都不断言失败。
3. `enable=false`（默认）时 Bean 不存在；`enable=true` 时 Bean 存在且队列名解析正确。
4. 日志断言：无中文文案；不含完整消息体。
