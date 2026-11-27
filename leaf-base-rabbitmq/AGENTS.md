# AGENTS.md - leaf-base-rabbitmq

本文档用于指导 `leaf-base-rabbitmq` 模块的后续迭代开发。模块已于 2026-11-27 从经典 AMQP 0-9-1 迁移到
**AMQP 1.0**（Spring Boot 4.2 + `spring-amqp-client` + Qpid ProtonJ2），修改本模块时优先保持发送语义、
拓扑契约与对外服务契约的兼容性，不要重新引入经典协议 API。

## 通用硬性规则

1. 每次修改只能触碰当前 Maven 模块；确需跨模块时，先说明触发原因、受影响模块、替代方案和最小改动集合。
2. 现有代码已经在生产环境使用，不得随意更改对外接口规范、配置语义、默认行为、异常语义或序列化契约；必须变更时，同步补充兼容说明、迁移路径和回归测试。
3. 在确保原有功能正确的前提下完善实现；修改既有逻辑前，先用测试锁定当前成功、失败、空值、边界和回归路径。
4. 基础框架代码必须优先审查性能、线程安全、资源释放和并发可见性；线程池与虚拟线程都能满足需求时，优先使用虚拟线程。
5. 空值处理必须支持 JSpecify 规范：新增或修改 API、DTO、配置属性、回调、集合元素和异步结果时，明确 `@NullMarked`、`@Nullable` 或等价约束。
6. 严格遵循测试驱动最佳实践：先写或调整能暴露问题的测试，再实现最小代码变更，最后运行模块级验证命令。
7. 涉及改动的点都要检查测试用例是否完整；覆盖不足时补齐成功、失败、异常、空值、边界和回归场景。
8. 功能只有在 Spring Boot 应用运行后才能验证时，使用 `@SpringBootTest` 或等价方式模拟真实启动后的 Bean 装配、条件配置、监听器、异步流程和配置绑定。
9. 修改线程安全或性能相关代码时，必须补充严格测试；无法自动化时在提交说明写清手工压测命令、样本规模和观察指标。
10. 所有 `log` 日志内容必须使用 ASCII 字符；面向用户的异常消息可以使用中文。
11. 不要随便把已存在代码抽取成单独方法或类；只有抽取后的逻辑至少被两个位置复用，或能显著降低复杂度且不破坏语义时，才允许抽取。
12. 删除方法、类、变量上的冗余注释；只保留语义说明、边界条件、异常约定等必要内容。
13. 复杂方法、复杂类、公共扩展点或使用方式不直观的能力，应补充必要 Javadoc；使用门槛较高时给出简短示例。
14. 精准命令胜过陈词滥调：直接给出 `mvn -o -B -pl leaf-base-rabbitmq test`、`rg "类名|方法名" leaf-base-rabbitmq/src/test` 这类具体步骤。
15. 控制 `AGENTS.md` 长度与结构，建议保持在 200 行以内。
16. 持续优化迭代：当 Agent 的实际输出与预期目标出现偏差时，把偏差改写成下一次可执行、可检查的规则。

## 1. 模块定位

`leaf-base-rabbitmq` 是 Maple Leaf Framework 的 RabbitMQ 基础能力模块（AMQP 1.0），当前负责：

1. 基于 Spring Boot 4.2 AMQP 1.0 自动装配补齐 Maple Leaf 约定：连接细节、vhost、消息转换器、
   发送器、拓扑管理客户端、监听容器工厂。
2. 统一承载 RabbitMQ 配置绑定（`spring.rabbitmq` 前缀，`spring.amqp.*` 优先），支持本地
   `rabbit.yml` 与 Nacos `rabbit.yml` 两种来源，由 `maple.framework.rabbitmq.config-source` 互斥选择。
3. 提供 `GXSendRabbitMQService`：普通消息发送（异步结算 / 同步等待）、动态声明队列/交换机/绑定、
   一站式消息通道创建（同步/异步）。
4. 提供发送回调扩展点：`GXConfirmCallback`、`GXReturnsCallback`、`GXRecoveryCallback`。
5. 提供默认监听实现 `GXDefaultRabbitMQQueueListenerImpl` 与监听契约 `GXRabbitMQQueueListener`。
6. 在发送链路补充 `X-Trace-Id`、`X-Timestamp`、`X-Source-Service` 与 `messageId`。

不承载：复杂消费编排、死信治理框架、幂等框架、消息事务编排、独立运维工具。

## 2. 目录职责

1. `config`：`GXRabbitMQConfig`——连接细节、vhost 定制、转换器、发布器、拓扑客户端、容器工厂。
2. `service` / `service/impl`：发送、声明、通道创建、结算分发、异步执行器。
3. `properties`：配置基类、`local`/`nacos` 变体、启动期 Nacos 拉取的 `EnvironmentPostProcessor`。
4. `exception`：`GXRabbitMQOperationException`，携带操作类型、资源名与已完成步骤。
5. `callback`：确认、退回、重试恢复扩展点；`GXReturnedMessage` 为退回描述。
6. `listener` / `listener/impl`：监听契约、默认实现（`@AmqpListener` + 显式 ack）、计数器与
   `GXAmqpListenerErrorHandler`（消息体转换失败直接 reject）。
7. `dto/inner`：消息发送请求 DTO 与 `GXCorrelationData`。
8. `support`：`GXAmqpAddress`（v2 地址编码）、`GXAmqpMessagePublisher`（发送 + 结算）、
   `GXAmqpManagementClient`（Management HTTP API）、`GXSendOutcome`、`GXAmqpManagementException`、
   `GXRabbitMQJsonSupport`。
9. `src/main/resources/META-INF`：`additional-spring-configuration-metadata.json`、`spring.factories`。
10. `src/test`：158 个用例（148 单元/上下文 + 10 集成）；集成用例需要 `RABBITMQ_HOST`，缺失时 `Skipped`。

## 3. 强制开发规则

1. 所有日志文案必须 ASCII；错误码/关键字保持稳定（`RABBITMQ_LISTENER_REJECTED`、`RABBITMQ_LISTENER_REQUEUE`）。
   毒消息（消息体无法转换）由 `GXAmqpListenerErrorHandler` 以 `RABBITMQ_LISTENER_REJECTED reason=unconvertible` 记录。
2. 参数非法抛 `GXBusinessException`；Broker 操作失败抛 `GXRabbitMQOperationException`。禁止用 `null`/`false` 表达需要排障的失败。
3. **禁止重新引入经典协议 API**：`RabbitTemplate`、`RabbitAdmin`、`CachingConnectionFactory`、
   `@RabbitListener`、`CorrelationData`、`spring-boot-rabbitmq`、`spring-rabbit`。
4. 发布地址必须走 `GXAmqpAddress`：默认交换机 → `/queues/<routingKey>`，命名交换机 →
   `/exchanges/<exchange>/<routingKey>`；消费源地址必须写成 `/queues/<queue>`，裸队列名会被
   RabbitMQ 以 `amqp_address_v1_not_permitted` 拒绝。
5. 拓扑只能通过 `GXAmqpManagementClient`（Management HTTP API）：AMQP 1.0 协议没有
   `queue.declare`/`exchange.declare`/`queue.bind`；不要假设 `createQueue` 会隐式声明。
6. `createQueue` 每次必须触达 Broker，同队列并发由单飞收敛；**禁止恢复"永久队列缓存"**。
7. 排他队列不支持：`createQueue(..., exclusive=true)` 必须抛 `GXBusinessException`；
   `createTemporaryQueue` 语义为 `durable=true, exclusive=false, autoDelete=true`
   （RabbitMQ 4.x 默认禁止非持久化非排他队列）。
8. 发送前必须用 `MessagePropertiesBuilder.fromClonedProperties` 做快照，禁止改写调用方
   `MessageProperties`，禁止写 `consumerTag`。
9. `X-Trace-Id` 必须优先复用 MDC 中的 traceId，缺失才生成；禁止逐条查询 `Environment`。
10. 出站载荷继续使用 Hutool 序列化（`Date`/`LocalDateTime` 为毫秒时间戳），改变即视为破坏 wire format；
    `JacksonJsonMessageConverter` 只负责类型化转换；header 通过 AMQP 1.0 application properties 传输。
11. `DefaultClassMapper.setTrustedPackages` 必须包含 `cn.hutool.core.lang`（`"cn.hutool.core"` 不覆盖子包）。
12. 监听器禁止"捕获异常后正常返回"：容器以 `autoAccept=false` 运行，监听器必须显式结算——
    成功 `ACCEPT`、`AmqpRejectAndDontRequeueException` → `REJECT`、其他异常 → `REQUEUE`。
13. 监听器 Bean 必须保持饿汉初始化；`@Lazy` 会导致 `@AmqpListener` 端点永不注册。
14. 消息体无法转换（`MessageConversionException`）必须由 `GXAmqpListenerErrorHandler` 结算为 `REJECT`，
    禁止让容器 release 后无限重投；该处理器是唯一 Bean，Boot 的容器工厂配置器会自动应用。
15. 线程池/调度器必须有 `@PreDestroy` 或容器销毁路径；异步执行器必须使用
    `GXMdcWrapperThreadPoolExecutor`（自动传播 MDC），禁止手工拷贝 MDC 或把任务丢给裸 `Thread`。
16. 修改消息头、失败语义、临时队列语义或配置键时，必须同步更新本文件、
    `docs/leaf-base-rabbitmq开发手册.md` 与 `../specs/016-rabbitmq`。
17. `leaf-base-retry` 与 `jackson-datatype-jsr353` 已移除，不得重新引入；`GXRecoveryCallback`
    在结算失败（连接中断、链路关闭、结算超时）时触发。

## 4. 配置键

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.amqp.host/port/username/password` | Boot 默认 | AMQP 1.0 原生前缀，显式配置时优先 |
| `spring.rabbitmq.host/port/username/password` | `localhost` / `5672` | 历史前缀，作为回退来源 |
| `spring.rabbitmq.addresses` | 无 | 历史写法 `host1:5672,host2:5672`，取第一个作为连接端点（优先于 host/port） |
| `spring.rabbitmq.virtual-host` | `/` | vhost，经 `ConnectionOptionsCustomizer` 注入 open 帧 |
| `spring.amqp.listener.*` | Boot 默认 | 并发、credits、receive-timeout、graceful-shutdown |
| `maple.framework.rabbitmq.config-source` | `nacos` | `local` / `nacos` 互斥 |
| `maple.framework.rabbitmq.confirm-timeout` | `5000` | 异步发送等待结算的毫秒数（由 `@Value` 读取，**不**在 `spring.rabbitmq` 绑定类里） |
| `maple.framework.rabbitmq.management.enabled` | `true` | 关闭后拓扑方法抛异常，发送与消费不受影响 |
| `maple.framework.rabbitmq.management.url` | `http://<host>:15672` | Management API 基地址 |
| `maple.framework.rabbitmq.management.username/password` | 回退连接凭据 | 管理 API 凭据 |
| `maple.framework.rabbitmq.management.virtual-host` | 回退连接 vhost | 管理 API vhost |
| `maple.framework.rabbitmq.thread-pool.core-size` | CPU | 异步声明执行器核心线程 |
| `maple.framework.rabbitmq.thread-pool.max-size` | CPU×2 | 仅队列满时生效；`queue-capacity<=0` 时立即生效 |
| `maple.framework.rabbitmq.thread-pool.queue-capacity` | `1000` | `<=0` 使用 `SynchronousQueue` |
| `maple.framework.mq.rabbitmq.enable` | `false` | 默认监听器启停 |
| `maple.framework.mq.rabbitmq.default-queue-name` | `maple.default.queue` | 监听队列名（最高优先级） |

已移除：`maple.framework.rabbitmq.scheduler.expose`（经典 `AsyncRabbitTemplate` 调度器随之删除）、
`spring.rabbitmq.connection-limit` / `channel-cache-size` / `cache-mode` / `channel-checkout-timeout`
（经典连接工厂概念，AMQP 1.0 无对应语义）。

## 5. 能力边界

| 能力 | 状态 | 替代方案 |
| --- | --- | --- |
| 排他队列声明 | 不支持 | 调用方自行持有连接并用 `openReceiver`/`openSender` 声明 |
| 拓扑管理 | 依赖 Management API | 关闭 `management.enabled` 后改用部署期声明（IaC / rabbitmqadmin） |
| 批量发送 | 不支持 | 调用方循环发送；吞吐不足时在业务侧合并消息体 |
| 延迟/优先级/TTL | 未封装 | 通过 `MessageProperties` 自行设置（TTL 映射为 AMQP 1.0 `timeToLive`） |
| 死信治理 | 未封装 | 部署方在 Broker 侧配置 DLX，并依赖监听器 `REJECT` 语义 |
| 消费端重试策略 | 交给调用方 | 监听器内重投或 `REQUEUE`；容器按 `recovery-backoff` 重建链路 |
| 消息大小前置校验 | 未实现 | 由调用方控制载荷大小，Broker 侧限制兜底 |
| 指标暴露 | 仅监听计数 | 通过 `MeterRegistry` 采集 `rabbitmq.listener.messages.*` |
| Nacos 动态刷新 | 不支持 | 重启生效；需要热更新时改用 `spring.config.import` |
| 独立关闭监听基础设施 | 不支持 | `@EnableAmqp` 随 `GXRabbitMQConfig` 一并生效；需要完全禁用时不要引入本模块 |

## 6. 测试要求

1. 模块级命令：`mvn -o -B -pl leaf-base-rabbitmq test`（包含 `*IT`，无 Broker 环境时集成用例显示为 `Skipped`）。
2. 真实 Broker：设置 `RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`/`RABBITMQ_VHOST`/`RABBITMQ_MANAGEMENT_PORT` 后执行同一条命令；凭据不得入库。
3. 当前基线：158 个用例（148 执行 + 10 集成），真实 RabbitMQ 4.4.1 全绿（2026-11-27）。
4. 新增/修改能力必须覆盖：成功、失败、异常、空值/边界、并发、Spring 上下文装配。
5. 关键并发基线：16 线程 × 100 条发送不串扰；32 线程同队列声明只声明 1 次；10 队列并发不相互阻塞。
6. 性能口径（本机 → 局域网 Broker）：顺序发送 p95 ≈ 1.4 ms；8 线程并发 ≈ 3100 msg/s；
   发送成功路径 0 条 info 日志。
7. 集成用例注意：`autoDelete=true` 队列在最后一个消费者断开后会被删除，需要多次收消息的场景使用
   `autoDelete=false`。

## 7. 变更前检查清单

1. 是否影响 `GXSendRabbitMQService` 的方法契约、失败语义或返回语义。
2. 是否影响 `GXRabbitMQConfig` 中连接细节、vhost、转换器、发布器、容器工厂的装配结果。
3. 是否影响 `config-source` 的选择逻辑与启动日志。
4. 是否影响默认监听器的启停条件、地址、ack 语义。
5. 是否影响 `GXConfirmCallback`、`GXReturnsCallback`、`GXRecoveryCallback` 的接入方式。
6. 是否影响追踪头、消息 ID、编码、内容类型或 wire format。
7. 是否引入非 ASCII 日志或直接 `System.out`。
8. 是否新增未使用依赖、死代码、无所属调用的公共 API。
9. 是否补充或更新了覆盖本次风险点的测试，并执行 3 次全量验证。

## 8. 提交说明建议

1. 涉及发送语义、配置来源、监听器启停条件、回调行为的提交，必须写清行为变化与兼容性影响。
2. 涉及日志调整的提交，提交前确认新增和修改后的日志全部为 ASCII。
3. 涉及并发、线程池、拓扑声明的提交，写明新增或更新的测试与样本规模。
4. 无法补齐的测试必须在提交说明中明确风险、缺口与后续计划。
