# Phase 1 数据模型：leaf-base-rabbitmq 修复涉及的运行期模型

**Feature**: `016-rabbitmq-module-audit` | **Date**: 2026-10-25

本模块不持有业务数据，模型描述的是**运行期状态对象**与**审查产物**。字段与规则均来自 `spec.md` 的功能需求，不引入持久化。

## 1. 队列声明请求（QueueDeclarationRequest）

| 字段 | 类型 | 约束 | 来源 |
| --- | --- | --- | --- |
| `name` | `String` | 非空白；长度 ≤ 255（AMQP 限制）；不得包含 `\0` | FR-006、FR-021（参数校验） |
| `durable` | `boolean` | 与 Broker 已存在声明一致，否则冲突 | `createQueue` 入参 |
| `exclusive` | `boolean` | 临时队列固定 `false`（D-04） | `createQueue` 入参 |
| `autoDelete` | `boolean` | 与 `durable` 组合决定生命周期 | `createQueue` 入参 |
| `arguments` | `Map<String,Object>`（可空） | 不可变拷贝；`null` 值剔除；用于指纹 | 入参 |
| `fingerprint` | `String` | 由上述 5 项规范化后的稳定摘要（键排序 + `Objects.toString`） | 派生 |

**不变量**：
- 指纹只用于"单飞键 + 冲突检测"，**不作为"已存在"的长期判据**（D-02）。
- 同一 `name` 的并发声明共享同一个进行中条目，完成后条目移除。

**状态迁移**（单飞条目）：

```text
ABSENT --computeIfAbsent--> IN_FLIGHT --declare ok--> COMPLETED(Queue) --remove--> ABSENT
                                     --declare fail--> FAILED(exception) --remove--> ABSENT
```

## 2. 进行中声明条目（InFlightDeclaration）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | `String` | `name + "#" + fingerprint` |
| `future` | `CompletableFuture<Queue>` | 供并发调用方共享 |
| `startedAt` | `long`（epoch millis） | 仅用于日志与超时诊断 |

**约束**：条目必须在 `whenComplete` 中移除；异常完成同样移除；不复用已完成的 Future 作为缓存。

## 3. 消息快照（MessageSnapshot）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `body` | `byte[]` | UTF-8 JSON，序列化栈统一为 `JsonMapper`（D-08） |
| `properties` | `MessageProperties` | `fromClonedProperties` 深拷贝，调用方对象只读（D-05） |
| `traceId` | `String` | 优先 MDC，缺失时新生成（D-06） |
| `messageId` | `String` | 调用方已设置则保留，否则取 `traceId` |
| `timestamp` | `long` | `X-Timestamp` 与 AMQP `timestamp` 同源 |
| `sourceService` | `String` | 装配期注入的 `spring.application.name`，缺省 `unknown` |

**不变量**：
- 快照创建后不再读取 DTO 字段（避免调用方并发修改）。
- 不写 `consumerTag`（消费侧属性）；`tag` 字段保留但发送侧忽略（兼容表）。

## 4. 配置源状态（ConfigSourceState）

| 字段 | 取值 | 说明 |
| --- | --- | --- |
| `requested` | `local` / `nacos` | `maple.framework.rabbitmq.config-source`，默认 `nacos` |
| `activeBean` | `GXLocalRabbitMQProperties` / `GXNacosRabbitMQProperties` | 由 `@ConditionalOnProperty` 决定，二者互斥 |
| `nacosLoaded` | `boolean` | 启动期是否成功拉取 `rabbit.yml` |
| `nacosKeys` | `int` | 拉取到的键数量（仅日志，不打印值） |
| `localResources` | `List<String>` | 本地 `@PropertySource` 实际命中的路径 |
| `precedence` | `enum` | `APPLICATION > NACOS > LOCAL_FILE`（`CommandLine > application.yml > nacos-rabbit.yml > local yml`） |

**状态迁移**：

```text
STARTUP -> resolve config-source -> [local] load classpath/file yml -> log source=local
                                 -> [nacos] server-addr blank? -> log source=nacos, loaded=false, reason=no-server-addr
                                            server-addr present -> pull(2s) -> ok? log loaded=true : warn + log loaded=false
```

## 5. 监听结果（ListenerOutcome）

| 取值 | 触发条件 | ack 语义 | 计数 |
| --- | --- | --- | --- |
| `ACK` | 消息体非空且可解析 | 正常 ack | `rabbitmq.listener.messages.processed` |
| `REJECT_NO_REQUEUE` | 消息体为空 / 非 UTF-8 / JSON 解析失败 | `AmqpRejectAndDontRequeueException` | `rabbitmq.listener.messages.rejected` |
| `RETRY` | 业务/运行时异常 | 异常原样抛出，由容器策略决定 requeue | `rabbitmq.listener.messages.failed` |

**不变量**：不存在"记录日志后正常返回"的第四种路径（FR-008）。

## 6. 生命周期对象

| 对象 | 所有者 | 并发度 | 关闭方式 | 开关 |
| --- | --- | --- | --- | --- |
| `rabbitTaskScheduler` | Spring 容器（`expose=true`）或 `GXRabbitMQConfig`（`expose=false`） | `poolSize = max(2, CPU)`，JDK 21 虚拟线程 | `shutdown()` + 等待 30s | `maple.framework.rabbitmq.scheduler.expose`（默认 `true`） |
| `rabbitmqAsyncExecutor` | `GXSendRabbitMQServiceImpl` | `core=min(core,max)`；`max` 受队列容量约束；类型为 `GXMdcWrapperThreadPoolExecutor`（自动传播 MDC） | `@PreDestroy`：`shutdown` → 30s → `shutdownNow` | `maple.framework.rabbitmq.thread-pool.*`（既有） |
| `MDC` 上下文 | 提交线程捕获，任务内恢复并清理 | N/A | `TaskDecorator` 等价逻辑 | 无 |

**不变量**：上下文关闭后不存在模块命名的存活线程；关闭期间在途任务要么完成，要么被记录为丢弃（不得静默）。

## 7. 异常模型（GXRabbitMQOperationException）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `int` | 复用 `GXBusinessException` 的 code 机制 |
| `operation` | `enum` | `DECLARE_EXCHANGE` / `DECLARE_QUEUE` / `QUERY_QUEUE` / `BIND` / `UNBIND` / `DELETE_QUEUE` / `PURGE_QUEUE` / `SEND` |
| `resource` | `String` | 队列/交换机/routingKey 的可读标识（不含敏感值） |
| `detail` | `Dict` | `cause` 类名、Broker replyCode/replyText（可得时） |

**约束**：消息面向用户可用中文，日志文案必须 ASCII（AGENTS.md §3.1）。

## 8. 审查产物模型（Finding / FixRecord）

| 字段 | 说明 |
| --- | --- |
| `id` | `F-01` … `F-22` |
| `severity` | 高 / 中 / 低 |
| `dimension` | 正确性 / 线程安全 / 性能 |
| `location` | 文件 + 行区间 |
| `trigger` | 触发条件 |
| `impact` | 影响面 |
| `evidence` | `E-xx` 编号或实测数据 |
| `reproduction` | 命令 / 用例名 / 构造步骤 |
| `regression` | 旧代码失败、新代码通过的用例设计 |
| `fix` | 对应 FR 与提交范围 |
| `compatibility` | 无变化 / 语义修正（附迁移方式） |

## 9. 测试矩阵（TestMatrix）

| 维度 | 取值 | 最低覆盖 |
| --- | --- | --- |
| 输入类别 | 成功 / 失败 / 异常 / 空值 / 边界 | 每个公开方法各 ≥1 |
| 运行形态 | 纯单元（Mock） / Spring 上下文 / 并发 / 真实 Broker | 4 类均有 |
| 并发规模 | 发送 16 线程 × 100 条；声明 32 线程 × 1 队列；不同队列 10 × 20 | FR-027 |
| 重复性 | 全量连续 3 次 | FR-029 |
| 环境 | 默认无 Broker；`RABBITMQ_*` 存在时启用集成 | FR-028 |
