# AGENTS.md - leaf-base-data-sync

本文档用于指导 `leaf-base-data-sync` 模块的后续迭代开发。修改本模块时，优先保证 Canal 消息接入、RabbitMQ
监听装配、消息解析分发、默认兜底处理和配置来源切换的兼容性、可验证性、性能与线程安全，不做与当前模块职责无关的改动。

## 通用硬性规则

1. 每次修改只能触碰当前 Maven 模块；确需跨模块时，先说明触发原因、受影响模块、替代方案和最小改动集合。
2. 现有代码已经在生产环境使用，不得随意更改对外接口规范、配置语义、默认行为、异常语义或序列化契约；必须变更时，同步补充兼容说明、迁移路径和回归测试。
3. 在确保原有功能正确的前提下完善实现；修改既有逻辑前，先用测试锁定当前成功、失败、空值、边界和回归路径。
4. 基础框架代码必须优先审查性能、线程安全、资源释放和并发可见性；线程池与虚拟线程都能满足需求时，优先使用虚拟线程。
5. 空值处理必须支持 JSpecify 规范：新增或修改 API、DTO、配置属性、回调、集合元素和异步结果时，明确 `@NullMarked`、`@Nullable` 或等价约束，让空值语义可被 IDE、编译检查和测试验证。
6. 严格遵循测试驱动最佳实践：先写或调整能暴露问题的测试，再实现最小代码变更，最后运行模块级验证命令。
7. 涉及改动的点都要检查测试用例是否完整；覆盖不足时补齐成功、失败、异常、空值、边界和回归场景。
8. 功能只有在 Spring Boot 应用运行后才能验证时，使用 `@SpringBootTest` 或等价方式模拟真实启动后的 Bean 装配、条件配置、AOP、监听器、异步流程和配置绑定。
9. 修改线程安全或性能相关代码时，必须补充严格测试；覆盖并发访问、重复执行、超时、取消、资源释放和关键指标，无法自动化时在提交说明写清手工压测命令、样本规模和观察指标。
10. 所有 `log` 日志内容必须使用 ASCII 字符，避免不同运行环境、终端编码或日志采集链路出现乱码。
11. 不要随便把已存在代码抽取成单独方法或类；只有抽取后的逻辑至少被两个位置复用，或能显著降低复杂度且不破坏语义时，才允许抽取。
12. 删除方法、类、变量上的冗余注释；清理 Javadoc 中重复、空泛或仅复述签名的信息，只保留语义说明、边界条件、异常约定等必要内容。
13. 复杂方法、复杂类、公共扩展点或使用方式不直观的能力，应补充必要 Javadoc；使用门槛较高时，给出简短使用示例。
14. 精准命令胜过陈词滥调：直接给出 `mvn -pl 当前模块 test`、`rg "类名|方法名" 当前模块/src/test` 这类具体步骤，避免“确保代码质量”“提升项目可维护性”等抽象套话。
15. 控制 `AGENTS.md` 长度与结构，建议保持在 200 行以内；规范继续增多时，在子目录创建领域指南，本文件只保留入口链接和必须遵守的红线。
16. 持续优化迭代：`AGENTS.md` 是动态文档；当 Agent 的实际输出与预期目标出现偏差时，把偏差改写成下一次可执行、可检查的规则。

## 1. 模块定位

`leaf-base-data-sync` 是 Maple Leaf Framework 中承接 Canal 数据同步能力的基础模块，当前主要负责：

1. 消息接入：`canal.mq-type` 在 RabbitMQ（默认）与 RocketMQ 之间互斥选择；RabbitMQ 由本模块声明交换机、队列、绑定与死信队列，RocketMQ 经可选依赖 `leaf-base-rocketmq` 接入并由 `rocketmq-spring` 提供消费容器。
2. 消费：`GXCanalRabbitMQListener`（`@RabbitListener`）与 `GXCanalRocketMQListener`（`RocketMQListener<String>`）都委派同一个解析链路；两者必须保持 eager，不得加 `@Lazy`。
3. 解析与分发：`GXCanalMessageParseServiceImpl#parse` 单次解析载荷（支持单个 JSON 对象与 JSON 数组），DDL 事件跳过，其余按 `database + table` 定位处理 Bean 并按 `INSERT`/`UPDATE`/`DELETE` 分发。
4. 结局判定：每条事件落到 `成功 / 跳过 / 可重试失败 / 不可重试失败` 之一，由 `GXCanalFailureHandler` 决定重投或死信；处理器异常不得被吞掉——单个对象载荷上抛，数组载荷按元素转为可重试失败（见 §9 红线 1/10）。
5. 扩展点：`GXProcessCanalDataService` 面向业务，命名约定为 `database_table_Service` 驼峰 Bean 名，未命中回退 `defaultProcessCanalDataService`。
6. 配置：`canal.*` 统一由标准 `@ConfigurationProperties` 绑定；配置来源由 `canal.config-source=local|nacos` 显式选择，缺省保持既有类路径探测行为。
7. 可观测：`GXCanalConsumeStatistics` 提供线程安全计数，有 `MeterRegistry` 时暴露 `canal.consumer.*` Gauge。

本模块不负责 Canal Server 运维、业务幂等实现、跨系统编排、顺序/事务消息、跨 MQ 双跑，也不复用 `leaf-base-rocketmq` 的 `GXCdc*` 事件模型作为 Canal 分发主路径。

## 2. 目录职责

1. `config`：`GXCanalPropertiesConfig`（配置源互斥装配）、`GXCanalConfig`（RabbitMQ 交换/队列/绑定/死信 + 容量日志）、`GXCanalRocketMQConfig`（必填项校验）、`GXCanalRocketMQDependencyConfig`（缺依赖 fail-fast）。
2. `constant`：默认值、消息头名、操作名、结局相关常量（`GXCanalConstant`）。
3. `dto`：`GXCanalDataDto` 事件载体；`dto/inner` 存放结构化解析结果 `GXCanalParseResultInnerDto`。
4. `enums`：`GXCanalOutcomeEnums`（四类结局）、`GXCanalMqTypeEnums`（中间件类型）。
5. `exception`：`GXCanalMessageRejectedException`（RabbitMQ 拒绝进死信）、`GXCanalMessageRetryRequestedException`（RocketMQ 重投）。
6. `listener`：`GXCanalRabbitMQListener`、`GXCanalRocketMQListener`。
7. `metrics`：`GXCanalConsumeStatistics`。
8. `properties`：`GXCanalProperties` 及 `retry`/`deadLetter`/`rocketmq` 三组嵌套配置、来源校验器。
9. `properties/local`、`properties/nacos`：两种配置来源的载体类（由 `GXCanalPropertiesConfig` 创建，不再参与组件扫描）。
10. `service`：解析契约与表级处理扩展契约；`service/support` 存放 `GXCanalHandlerResolver`（路由快照 + 有界缓存）、`GXCanalFailureHandler`（失败处置端口）与 `GXCanalLogThrottle`（热路径日志节流，见 §9 红线 9）。
11. `service/impl`：解析实现与默认兜底实现；`service/impl/rabbit`、`service/impl/rocketmq` 存放两套失败处置实现。
12. `src/test`：单元 + 装配 + 端到端测试；行为改动必须同步补齐用例。

## 3. 开发边界

1. 每次修改只能修改当前 Maven 模块，不得修改其它模块。确实需要跨模块协同时，先停下来说明原因、影响面和替代方案。
2. 现有代码已经在生产环境使用，不得随意更改对外接口规范，包括公开类名、方法签名、字段语义、Bean 命名约定、配置前缀和默认行为。
3. 在确保原有功能正确性的前提下进行完善。修改已经存在的逻辑时，不得改变现有逻辑的正确性和兼容性，除非需求明确要求并同步更新测试与本文档。
4. 作为基础框架代码，性能和线程安全非常重要。新增共享状态、缓存、异步处理、监听并发或上下文访问逻辑时，必须评估并发可见性、竞态、资源释放和吞吐影响。
5. 在线程池与虚拟线程都能满足需求的场景中，优先使用虚拟线程；如果选择线程池，必须有明确理由，例如生命周期控制、队列背压、线程隔离或兼容性要求。
6. 不要随意将已存在的代码抽取成单独的方法或类。只有抽取后的逻辑能被两个以上地方复用，或能显著降低复杂度且不破坏可读性时，才允许抽取。
7. 严格遵循测试驱动最佳实践。行为变更应先明确期望与边界，优先补充或调整测试，再实现代码。

## 4. 代码与日志规范

1. 所有 `log` 日志内容都必须使用 ASCII 字符。禁止新增或保留非 ASCII 日志文案。
2. 删除方法、类、变量上的冗余注释，避免注释重复描述代码字面含义。
3. 清理 Javadoc 的冗余信息，只保留必要文档。复杂类、复杂方法、扩展点或不易理解的约定，应适当补充
   Javadoc；使用方式比较复杂时，可以提供简短使用示例。
4. 不要为了统一风格做无关格式化、重排 import 或大面积重写。修改应尽量贴近本次需求。
5. 新增或修改异常处理、日志、默认值、兜底逻辑时，必须考虑线上排障可读性，但日志文本仍需保持 ASCII。

## 5. 核心行为约束

1. 修改 `GXCanalMessageParseServiceImpl` 时，必须保留以下关键语义，除非需求明确要求改变并同步更新本文档：
    - 空消息、非法 JSON、标量 JSON 的防御性处理（均为不可重试失败）。
    - `database + table + "_Service"` 转 Camel Case 后查找 Bean 的约定（经 `GXCanalHandlerResolver` 快照查找，热路径禁止 `getBean`）。
    - 找不到表级处理器时回退到 `defaultProcessCanalDataService`；两者都没有则判定不可重试失败。
    - DDL 事件（`isDdl=true` 或 DDL 类型）判定为「跳过」，不得记为错误。
    - 按 `INSERT`、`UPDATE`、`DELETE` 分发到 `GXProcessCanalDataService`；处理器异常不得被吞掉：单个对象载荷上抛，数组载荷按元素转为 `RETRYABLE_FAILURE`（见 §9 红线 1/10）。
    - 载荷只解析一次；数组载荷按元素逐个产出结局。
2. 修改 `GXCanalRabbitMQListener`、`GXCanalRocketMQListener` 或 `GXCanalConfig` 时，必须评估是否影响队列名、交换机名、路由键、
   并发消费者、死信目的地、注解绑定方式与条件装配；**监听器 Bean 不得加 `@Lazy`**（会导致端点不注册）。
3. 修改 `GXLocalCanalProperties` / `GXNacosCanalProperties` / `GXCanalPropertiesConfig` 时，必须确认 `canal.config-source`
   的四种组合（有/无 Nacos 类路径 × 默认/显式）装配结果与启动日志一致。
4. 修改 `GXCanalDataDto`、`GXProcessCanalDataService` 或 `GXCanalMessageParseService`
   这类对外契约时，必须优先考虑兼容性：`parseMessage(String)` 签名与 `Dict` 返回语义不得改变，新增能力通过 `parse` 提供。
5. 修改失败处置链路（`GXCanalFailureHandler` 及两个实现）时，必须保证：重投有上限、不可重试失败必须落到死信或可检索记录、
   任何结局都必须更新计数。禁止把失败转换成成功消费。
6. 依赖治理：RocketMQ 相关坐标只能通过 `leaf-base-rocketmq` 引入（`optional`），禁止在本模块直接声明 `rocketmq-spring-boot-starter`
   或 `rocketmq-client`；版本沿用该模块与根 POM。

## 6. 测试要求

1. 涉及改动的点，都要检查测试用例是否完整。如果覆盖不足、失效或缺失，必须先补齐再提交。
2. 测试用例要覆盖全面，至少覆盖成功路径、失败路径、异常路径、空值路径和边界条件。
3. 如果功能需要在 Spring Boot 应用运行后才能测试，例如 Bean 装配、条件注解、配置绑定、`@RabbitListener` 注册或上下文查找
   Bean 行为，测试用例必须模拟真实 Spring Boot 应用启动后的场景，使用 `@SpringBootTest` 或同等级别的集成测试。
4. `GXCanalMessageParseServiceImpl` 相关改动至少要覆盖：
    - 空消息。
    - 非 JSON 消息。
    - JSON 反序列化失败。
    - `database` 或 `table` 缺失。
    - 命中表级处理器。
    - 表级处理器缺失后回退默认处理器。
    - 默认处理器也缺失。
    - Bean 类型不匹配。
    - `INSERT`、`UPDATE`、`DELETE` 和未知类型分支。
    - 处理器返回 `null` 和处理器抛异常。
5. `GXCanalRabbitMQListener`、`GXCanalConfig`、`GXLocalCanalProperties`、`GXNacosCanalProperties` 相关改动，如果涉及 Bean
   装配、配置绑定、条件装配、监听注解行为或上下文获取，必须补充 Spring Boot 集成测试。
6. 自定义 `GXProcessCanalDataService` 扩展点相关改动，至少要验证 Bean 命名约定是否仍能被正确解析到。
7. 当前模块没有成熟测试基线。后续第一次修改运行时行为时，应优先建立最小可运行测试骨架，而不是继续无测试迭代。
8. 提交前至少保证模块级测试命令可运行：`mvn -pl leaf-base-data-sync test`。

## 7. 变更前检查清单

1. 是否只修改了 `leaf-base-data-sync` 当前模块？
2. 是否影响 RabbitMQ 队列、交换机、路由键、并发消费者或监听绑定关系？
3. 是否影响本地配置与 Nacos 配置之间的选择逻辑？
4. 是否影响 `database_table_Service` 到 Camel Case Bean 名称的解析规则？
5. 是否影响默认兜底处理器 `defaultProcessCanalDataService` 的回退行为？
6. 是否引入了非 ASCII 日志文本？
7. 是否做了没有复用价值的方法或类抽取？
8. 是否删除了冗余注释，并为复杂逻辑补充了必要 Javadoc 或示例？
9. 是否检查并补齐了本次改动所需的测试覆盖？
10. 如果改动依赖 Spring Boot 运行时行为，是否提供了真实启动上下文下的测试？
11. 是否评估了性能、线程安全、资源释放和并发场景？
12. 是否确认没有破坏生产环境已使用的对外接口规范？

## 8. 提交说明建议

1. 涉及监听、解析、Bean 路由、默认回退或配置来源切换的改动，提交说明中要写清行为变化和兼容性影响。
2. 涉及日志调整时，提交前确认新增和修改后的日志文本全部为 ASCII。
3. 涉及运行时装配行为的改动，提交说明中要明确本次验证采用的是单元测试、Spring Boot 集成测试，还是两者都有。
4. 如果本次改动理论上应补测试但暂时无法补齐，必须在提交说明中明确风险、缺口和后续补齐计划。

## 9. Runtime Guardrails

1. Handler failures must never be swallowed as a success. The transport of the exception depends on the payload shape: for a **single object** payload it must propagate out of `GXCanalMessageParseServiceImpl` (this keeps the legacy `parseMessage` contract), for a **JSON array** it is captured per element and returned as `RETRYABLE_FAILURE` (see 10). Do not silently convert handler failures into successful message consumption.
2. RabbitMQ exchange and queue beans must be durable by default. The exchange must not default to `autoDelete=true`, because a temporary consumer disconnect must not remove shared infrastructure.
3. The main RabbitMQ queue must keep its dead letter arguments (`x-dead-letter-exchange` / `x-dead-letter-routing-key`); a rejected message must never disappear.
4. Retry must stay bounded by `canal.retry.max-attempts` (default 3). RabbitMQ carries the counter in the `x-canal-retry-count` header; RocketMQ uses the annotation constant `maxReconsumeTimes = GXCanalConstant.DEFAULT_RETRY_MAX_ATTEMPTS` (3), which `canal.retry.max-attempts` does NOT change. Do not introduce unbounded requeueing and do not add a config key that the annotation cannot honour.
5. `canal.consumer-enabled=false` must leave the context free of any MQ infrastructure bean and without connection attempts.
6. Selecting `rocketmq` without `leaf-base-rocketmq` must fail fast with an actionable message; never fall back silently.
7. Listener beans must stay eager. A `@Lazy` listener bean is not instantiated, so its endpoint is never registered.
8. `GXCanalProperties` and its nested property classes must keep every mutable field `volatile`; consumer threads read them while Spring may rebind. Keys that influence assembly are restart-only and must be logged as such.
9. Hot path logs must go through `GXCanalLogThrottle`; never log one line per message for a repetitive failure.
10. Batch elements are isolated: a handler failure inside one element becomes a `RETRYABLE_FAILURE` result for that element and the remaining elements are still processed. Every failed element must be counted and logged; the transport action is executed once per payload, preferring the retryable failure when both kinds occur. A single object payload keeps the propagation behaviour of rule 1 — do not "unify" the two paths.

## 10. 不支持范围（能力 + 原因 + 替代方案）

与 `docs/leaf-base-data-sync开发手册.md` §6.4 保持一致，新增/删除条目必须两处同时更新。

| 不支持能力 | 原因 | 替代方案 |
| --- | --- | --- |
| 顺序消费 / 分区有序 | 与既有 `concurrency=10` 无序语义对齐，顺序保证需业务侧配合 | 按表维度拆分消费组，同一表只用单一消费者 |
| 事务消息 | 数据同步链路无事务边界，Canal 自身保证 binlog 顺序 | 业务侧幂等 + 唯一键约束 |
| 批量确认（batch ack） | 逐条判定结局是"不丢消息"的前提 | 调大 `spring.rabbitmq.listener.simple.prefetch` / RocketMQ 注解 `consumeThreadMax` |
| 跨 MQ 双跑 | 重复消费与幂等责任超出模块职责 | 部署级切换 `canal.mq-type` + 重启 |
| 跨库 routing 过滤（fanout → topic） | 会改变既有交换机类型与下游绑定语义 | 下游各自建队列；RocketMQ 侧用 `canal.rocketmq.tag` 过滤 |
| 运行期刷新装配期键 | 队列/交换机/主题/并发在容器启动时固定 | 改配置后重启（启动日志会提示） |
| 运行期新增处理器 Bean | 处理器集合在启动期快照 | 重启应用 |
| `canal.rocketmq.consume-thread-max` / `max-reconsume-times` 可配置 | 均为 `@RocketMQMessageListener` 注解属性，不接受占位符 | 需要不同取值时修改常量并重新发布 |
| RocketMQ 消费链路透传 traceId | `RocketMQListener<String>` 不暴露消息属性 | RocketMQ 侧每次投递本地生成 traceId |
| 复用 `GXCdcMessageDispatcher` / `GXCdcEventListener` 作为 Canal 分发主路径 | 事件模型不同，会要求业务改实现并放弃 `database_table_Service` 约定 | 使用 `GXProcessCanalDataService` 扩展点 |
