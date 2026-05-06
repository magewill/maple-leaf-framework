# AGENTS.md - leaf-base-rabbitmq

本文档用于指导 `leaf-base-rabbitmq` 模块的后续迭代开发。修改本模块时，优先保持 RabbitMQ 配置装配、消息发送语义、动态声明资源能力和对外服务契约的兼容性，避免做与当前职责无关的重构。

## 1. 模块定位

`leaf-base-rabbitmq` 是 Maple Leaf Framework 的 RabbitMQ 基础能力模块，当前负责：

1. 基于 Spring AMQP 和 Spring Boot 自动装配 RabbitMQ 相关 Bean，包括 `RabbitTemplate`、`AsyncRabbitTemplate`、`RabbitAdmin`、`RabbitMessagingTemplate` 和异步 `TaskScheduler`。
2. 统一承载 RabbitMQ 配置绑定，支持本地 `rabbit.yml` 与 Nacos `rabbit.yml` 两种配置来源，并扩展连接缓存、连接限制、默认队列等配置项。
3. 提供 `GXSendRabbitMQService` 对外发送与资源管理能力，包括普通消息发送、动态声明队列、交换机、绑定关系，以及一站式消息通道创建。
4. 提供发送回调扩展点，包括 `GXConfirmCallback`、`GXReturnsCallback`、`GXRecoveryCallback`，便于业务侧接管确认、退回和重试恢复逻辑。
5. 提供默认消息监听实现 `GXDefaultRabbitMQQueueListenerImpl` 与监听器接口 `GXRabbitMQQueueListener`，用于默认队列消费与扩展接入。
6. 在发送链路中补充追踪信息，包括 `X-Trace-Id`、时间戳、来源服务名和消息 ID。

本模块当前不承载复杂消费编排、死信治理框架、幂等框架、消息事务编排或独立运维工具。如需新增这些能力，必须先重新评估模块边界，并同步更新本文档。

## 2. 目录职责

1. `config`：RabbitMQ 相关 Spring Bean 装配、模板配置、消息转换器、异步调度器和连接工厂参数应用。
2. `service`：对外暴露的发送与资源管理服务契约。
3. `service/impl`：消息发送、通道创建、动态声明 RabbitMQ 资源、追踪头补充、异步执行器管理等核心实现。
4. `properties`：RabbitMQ 配置模型基类及扩展配置项。
5. `properties/local`：本地 `rabbit.yml` 配置装配入口。
6. `properties/nacos`：Nacos `rabbit.yml` 配置装配入口。
7. `callback`：发送确认、消息退回、重试恢复等回调扩展接口。
8. `listener`：RabbitMQ 消费监听扩展契约。
9. `listener/impl`：默认监听器实现，负责默认队列的消息接收、JSON 解析和基础日志输出。
10. `dto/inner`：消息发送请求 DTO，承载 exchange、routingKey、payload、消息属性和确认上下文。
11. `src/test`：模块测试目录。当前模块尚未建立测试基线，后续所有功能迭代必须同步补齐。

## 3. 强制开发规则

1. 所有 `log` 日志中的内容都必须使用 ASCII 字符；禁止新增或保留非 ASCII 日志文案。
2. 涉及改动的点，必须同步检查对应测试用例是否完整覆盖；覆盖不足时必须补充测试。
3. 不要随意将已存在代码抽取成单独的方法或者类；只有当抽取后的逻辑能被两个及以上位置复用，或能明显降低复杂度且不破坏可读性时，才允许抽取。
4. 删除方法、类、变量上的冗余注释，避免注释重复描述代码字面含义。
5. 对复杂的方法、类和对外扩展点，适当补充 Javadoc；必要时增加简短使用示例，便于后续使用者参考。
6. 修改消息发送入口时，必须保持 `GXSendRabbitMQService` 现有契约语义稳定；若需要调整参数、返回值、异常语义或副作用，必须先评估兼容性并更新本文档。
7. 修改默认监听器、回调接口或配置装配行为时，必须确认不会破坏已有 Spring Boot 自动装配、生效条件和扩展接入方式。
8. 修改消息追踪字段写入逻辑时，必须确认 `X-Trace-Id`、`X-Timestamp`、`X-Source-Service` 和 `messageId` 的兼容性不被意外破坏。
9. 修改动态声明资源逻辑时，必须确认队列缓存、加锁策略、交换机声明、绑定关系声明与删除路径的行为一致性。

## 4. 测试要求

1. 任何功能改动都必须先检查现有测试是否覆盖成功路径、失败路径、异常路径和边界条件。
2. 测试用例要覆盖全面；如果某项功能必须在 Spring Boot 应用运行后才能成立，例如自动装配、条件注解、属性绑定、监听器注册、模板 Bean 注入、回调装配和默认队列监听，那么测试必须使用接近真实启动形态的 Spring Boot 集成测试，例如 `@SpringBootTest`。
3. 不能只验证纯工具方法。凡是依赖 Bean 生命周期、配置来源切换、`@ConditionalOnClass`、`@ConditionalOnExpression`、`@ConfigurationProperties`、`@RabbitListener` 或消息模板装配的行为，都必须至少补一个 Spring Boot 上下文启动测试。
4. 当前模块还没有 `src/test` 基线。后续首次改动时，应优先建立最小可运行测试骨架，并至少覆盖以下高风险点：
5. `GXRabbitMQConfig` 的核心 Bean 装配、属性应用、JSON 消息转换器和异步调度器分支行为。
6. `GXSendRabbitMQServiceImpl` 的参数校验、消息属性设置、追踪头补充、发送异常分支、资源声明、绑定关系和异步通道创建。
7. `GXLocalRabbitMQProperties` 与 `GXNacosRabbitMQProperties` 的条件装配和配置来源切换。
8. `GXDefaultRabbitMQQueueListenerImpl` 的空消息、JSON 消息、非 JSON 消息和异常处理路径。
9. `GXRabbitMQMessageReqDto` 的默认消息属性、关联数据初始化约定和构造使用方式。
10. 模块级最小验证命令为 `mvn -pl leaf-base-rabbitmq test`。

## 5. 变更前检查清单

1. 是否影响 `GXSendRabbitMQService` 的方法契约、返回语义或异常语义。
2. 是否影响 `GXRabbitMQConfig` 中模板、管理器、调度器或消息转换器的装配结果。
3. 是否影响本地/Nacos 两种配置来源的选择逻辑与属性绑定。
4. 是否影响默认监听器的启用条件、默认队列来源或消息处理路径。
5. 是否影响 `GXConfirmCallback`、`GXReturnsCallback`、`GXRecoveryCallback` 的扩展接入方式。
6. 是否影响追踪头、消息 ID、编码、内容类型或消息属性默认值。
7. 是否引入了非 ASCII 日志文本。
8. 是否做了不必要的方法或类抽取。
9. 是否删除了冗余注释，并为复杂逻辑补齐了必要的 Javadoc 或示例。
10. 是否补充或更新了覆盖本次风险点的测试。

## 6. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一个 PR 或提交中同步更新本文档：

1. 模块职责边界变化，例如新增消费编排、死信治理、重试框架、事务消息支持或新的核心扩展能力。
2. 目录结构变化，例如新增关键包、迁移核心类、拆分实现层、删除关键组件。
3. 对外契约变化，例如 `GXSendRabbitMQService`、`GXRabbitMQQueueListener`、消息 DTO 或回调接口的方法签名、字段语义、异常语义发生变化。
4. 配置装配策略变化，例如本地/Nacos 配置来源、条件注解、默认属性名、配置文件命名或默认值发生变化。
5. 消息发送语义变化，例如默认编码、消息转换器、追踪头、发送失败处理、确认/退回/恢复回调行为发生变化。
6. 动态声明资源行为变化，例如队列缓存、并发控制、交换机声明、绑定关系或异步执行策略发生变化。
7. 默认监听器行为变化，例如启用条件、默认监听队列、JSON 解析方式、错误处理或扩展约束发生变化。
8. 测试策略变化，例如新增必须遵守的 Spring Boot 集成测试要求、模块最小验证命令变更、测试基线建立或重构。
9. 团队新增了需要长期遵守且适合在模块内复用的编码、日志、测试、文档或兼容性规则。

## 7. 提交说明建议

1. 涉及发送语义、配置来源、监听器启停条件、回调行为变更的提交，提交说明中必须写清行为变化和兼容性影响。
2. 涉及日志调整的提交，提交前确认新增和修改后的日志文本全部为 ASCII。
3. 涉及发送、监听、配置装配和异步执行的改动，提交说明中应写明新增或更新了哪些测试覆盖。
4. 如果本次改动理论上应补测试但暂时无法补齐，必须在提交说明中明确风险、缺口和后续补齐计划。
