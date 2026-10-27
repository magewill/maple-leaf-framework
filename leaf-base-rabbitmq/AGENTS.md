# AGENTS.md - leaf-base-rabbitmq

本文档用于指导 `leaf-base-rabbitmq` 模块的后续迭代开发。修改本模块时，优先保持 RabbitMQ 配置装配、消息发送语义、动态声明资源能力和对外服务契约的兼容性，避免做与当前职责无关的重构。

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

`leaf-base-rabbitmq` 是 Maple Leaf Framework 的 RabbitMQ 基础能力模块，当前负责：

1. 基于 Spring AMQP 自动装配 `RabbitTemplate`、`AsyncRabbitTemplate`、`RabbitAdmin`、`RabbitMessagingTemplate` 与 `ThreadPoolTaskScheduler`。
2. 统一承载 RabbitMQ 配置绑定（`spring.rabbitmq` 前缀），支持本地 `rabbit.yml` 与 Nacos `rabbit.yml` 两种来源，由 `maple.framework.rabbitmq.config-source` 互斥选择。
3. 提供 `GXSendRabbitMQService`：普通消息发送、动态声明队列/交换机/绑定、一站式消息通道创建（同步/异步）。
4. 提供发送回调扩展点：`GXConfirmCallback`、`GXReturnsCallback`、`GXRecoveryCallback`。
5. 提供默认监听实现 `GXDefaultRabbitMQQueueListenerImpl` 与监听契约 `GXRabbitMQQueueListener`。
6. 在发送链路补充 `X-Trace-Id`、`X-Timestamp`、`X-Source-Service` 与 `messageId`。

不承载：复杂消费编排、死信治理框架、幂等框架、消息事务编排、独立运维工具。

## 2. 目录职责

1. `config`：Bean 装配、转换器、回调、调度器与连接工厂参数应用。
2. `service` / `service/impl`：发送、声明、通道创建、追踪与异步执行器。
3. `properties`：配置基类、`local`/`nacos` 变体、启动期 Nacos 拉取的 `EnvironmentPostProcessor`。
4. `exception`：`GXRabbitMQOperationException`，携带操作类型、资源名与已完成步骤。
5. `callback`：确认、退回、重试恢复扩展点。
6. `listener` / `listener/impl`：监听契约、默认实现与计数器。
7. `dto/inner`：消息发送请求 DTO。
8. `support`：共享 `JsonMapper` 与类型头常量。
9. `src/main/resources/META-INF`：`additional-spring-configuration-metadata.json`、`spring.factories`。
10. `src/test`：125 个用例（120 执行 + 5 集成跳过），覆盖单元 / 上下文 / 并发；集成用例默认参与构建，无 Broker 时以 `Skipped` 呈现，`-Dgroups=integration` 单独执行。

## 3. 强制开发规则

1. 所有日志文案必须 ASCII；错误码/关键字保持稳定（`RABBITMQ_LISTENER_REJECTED`、`RABBITMQ_LISTENER_FAILED`）。
2. 参数非法抛 `GXBusinessException`；Broker 操作失败抛 `GXRabbitMQOperationException`。禁止用 `null`/`false` 表达需要排障的失败。
3. `createQueue` 每次必须触达 Broker，同队列并发由单飞收敛；**禁止恢复"永久队列缓存"**（无法感知 Broker 侧删除）。
4. 不再声明排他队列；`createTemporaryQueue` 语义为 `durable=false, exclusive=false, autoDelete=true`。
5. 发送前必须用 `MessagePropertiesBuilder.fromClonedProperties` 做快照，禁止改写调用方 `MessageProperties`，禁止写 `consumerTag`。
6. `X-Trace-Id` 必须优先复用 MDC 中的 traceId，缺失才生成；禁止逐条查询 `Environment`。
7. 出站载荷继续使用 Hutool 序列化（`Date`/`LocalDateTime` 为毫秒时间戳），改变即视为破坏 wire format；`JacksonJsonMessageConverter` 只负责类型化转换。
8. `DefaultClassMapper.setTrustedPackages` 必须包含 `cn.hutool.core.lang`（`"cn.hutool.core"` 不覆盖子包）。
9. 监听器禁止"捕获异常后正常返回"：不可重试抛 `AmqpRejectAndDontRequeueException`，可重试原样抛出。
10. 线程池/调度器必须有 `@PreDestroy` 或容器销毁路径；异步执行器必须使用 `GXMdcWrapperThreadPoolExecutor`（自动传播 MDC），禁止手工拷贝 MDC 或把任务丢给裸 `Thread`；禁止在调度线程上阻塞等待子任务。
11. 修改消息头、失败语义、临时队列语义或配置键时，必须同步更新本文件、`docs/leaf-base-rabbitmq开发手册.md` 与 `specs/016-rabbitmq-module-audit/contracts/`。
12. `leaf-base-retry` 与 `jackson-datatype-jsr353` 已移除，不得重新引入；`GXRecoveryCallback` 仅在调用方为模板设置 `RetryTemplate` 时触发。

## 4. 配置键

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.rabbitmq.*` | Boot 默认 | 连接/监听/模板，绑定到 `GXRabbitMQProperties` |
| `maple.framework.rabbitmq.config-source` | `nacos` | `local` / `nacos` 互斥 |
| `maple.framework.rabbitmq.scheduler.expose` | `true` | 是否把 `rabbitTaskScheduler` 暴露为 `TaskScheduler` Bean |
| `maple.framework.rabbitmq.thread-pool.core-size` | CPU | 异步声明执行器核心线程 |
| `maple.framework.rabbitmq.thread-pool.max-size` | CPU×2 | 仅队列满时生效；`queue-capacity<=0` 时立即生效 |
| `maple.framework.rabbitmq.thread-pool.queue-capacity` | `1000` | `<=0` 使用 `SynchronousQueue` |
| `maple.framework.mq.rabbitmq.enable` | `false` | 默认监听器启停 |
| `maple.framework.mq.rabbitmq.default-queue-name` | `maple.default.queue` | 监听队列名（最高优先级） |

## 5. 能力边界（宪法附加约束 §9）

| 能力 | 状态 | 替代方案 |
| --- | --- | --- |
| 排他队列声明 | 不支持 | 调用方自行持有连接并用 `QueueBuilder` 声明 |
| 批量发送 | 不支持 | 调用方循环发送；吞吐不足时在业务侧合并消息体 |
| 延迟/优先级/TTL | 未封装 | 通过 `MessageProperties` 或 `arguments` 自行设置 |
| 死信治理 | 未封装 | 部署方在 Broker 侧配置 DLX，并依赖监听器拒绝语义 |
| 消费端重试策略 | 交给容器 | 通过 `spring.rabbitmq.listener.simple.retry.*` 配置 |
| 消息大小前置校验 | 未实现 | 由调用方控制载荷大小，Broker 侧限制兜底 |
| 指标暴露 | 仅监听计数 | 通过 `MeterRegistry` 采集 `rabbitmq.listener.messages.*` |
| Nacos 动态刷新 | 不支持 | 重启生效；需要热更新时改用 `spring.config.import` |
| 独立关闭监听基础设施 | 不支持 | `@EnableRabbit` 是监听能力入口，随 `GXRabbitMQConfig` 一并生效；需要完全禁用时不要引入本模块或排除该配置类 |

## 6. 测试要求

1. 模块级命令：`mvn -o -B -pl leaf-base-rabbitmq test`（包含 `*IT`，无 Broker 环境时集成用例显示为 `Skipped`）。
2. 真实 Broker：设置 `RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`/`RABBITMQ_VHOST` 后执行 `mvn -o -B -pl leaf-base-rabbitmq test -Dgroups=integration`；凭据不得入库。
3. 当前基线：125 个用例（120 执行 + 5 集成跳过），连续 3 次执行结果一致（2026-10-25）。
4. 新增/修改能力必须覆盖：成功、失败、异常、空值/边界、并发、Spring 上下文装配。
5. 关键并发基线：16 线程 × 20 条发送不串扰；32 线程同队列声明只声明 1 次；10 队列并发不相互阻塞。
6. 性能口径：发送成功路径 0 条 info 日志；每消息固定开销 ≤4 项；调度器并发度 >1。

## 7. 变更前检查清单

1. 是否影响 `GXSendRabbitMQService` 的方法契约、失败语义或返回语义。
2. 是否影响 `GXRabbitMQConfig` 中模板、管理器、调度器或转换器的装配结果。
3. 是否影响 `config-source` 的选择逻辑与启动日志。
4. 是否影响默认监听器的启停条件、队列名来源或拒绝语义。
5. 是否影响 `GXConfirmCallback`、`GXReturnsCallback`、`GXRecoveryCallback` 的接入方式。
6. 是否影响追踪头、消息 ID、编码、内容类型或 wire format。
7. 是否引入非 ASCII 日志或直接 `System.out`。
8. 是否新增未使用依赖、死代码、无所属调用的公共 API。
9. 是否补充或更新了覆盖本次风险点的测试，并执行 3 次全量验证。

## 8. 提交说明建议

1. 涉及发送语义、配置来源、监听器启停条件、回调行为的提交，必须写清行为变化与兼容性影响。
2. 涉及日志调整的提交，提交前确认新增和修改后的日志全部为 ASCII。
3. 涉及并发、线程池、调度器的提交，写明新增或更新的测试与样本规模。
4. 无法补齐的测试必须在提交说明中明确风险、缺口与后续计划。
