# AGENTS.md - leaf-base-data-sync

本文档用于指导 `leaf-base-data-sync` 模块的后续迭代开发。修改本模块时，优先保证 Canal 消息接入、RabbitMQ 监听装配、消息解析分发、默认兜底处理和配置来源切换的兼容性与可验证性，避免做与当前职责无关的重构。

## 1. 模块定位

`leaf-base-data-sync` 是 Maple Leaf Framework 中承接 Canal 数据同步能力的基础模块，当前主要负责：

1. 基于 `leaf-base-rabbitmq` 提供 Canal 变更消息的 RabbitMQ 交换机、队列和绑定声明。
2. 通过 `GXCanalRabbitMQListener` 监听 Canal 投递到 RabbitMQ 的消息，并交给解析服务处理。
3. 通过 `GXCanalMessageParseServiceImpl` 校验 JSON 消息、反序列化 `GXCanalDataDto`、按 `database + table` 规则定位处理 Bean，并根据 `INSERT`、`UPDATE`、`DELETE` 分发到对应处理方法。
4. 通过 `GXProcessCanalDataService` 暴露面向业务侧的表级扩展点，允许业务方为特定库表提供自定义处理器。
5. 在未找到表级处理 Bean 时，回退到 `defaultProcessCanalDataService` 作为默认兜底处理器。
6. 支持本地 `canal.yml` 与 Nacos `canal.yml` 两种配置来源，并通过条件注解在 Spring Boot 启动时自动选择。

本模块当前不负责 Canal Server 的运维、MQ 消息重试框架、死信治理、业务幂等实现、跨系统编排或具体下游同步逻辑。

## 2. 目录职责

1. `config`：RabbitMQ 相关 Bean 声明，负责交换机、队列、绑定的创建。
2. `constant`：Canal 与 RabbitMQ 的默认常量定义。
3. `dto`：Canal 变更事件载体，目前核心对象为 `GXCanalDataDto`。
4. `listener`：RabbitMQ 消息监听入口。
5. `properties`：Canal 通用配置模型。
6. `properties/local`：本地 `canal.yml` 配置装配入口。
7. `properties/nacos`：Nacos `canal.yml` 配置装配入口。
8. `service`：Canal 消息解析与表级处理扩展契约。
9. `service/impl`：默认消息解析逻辑与默认兜底处理逻辑。
10. `src/test`：模块测试目录。当前模块尚未建立有效测试基线，后续涉及改动时必须同步补齐。

## 3. 强制开发规则

1. 所有 `log` 日志中的内容都必须使用 ASCII 字符。禁止新增或保留非 ASCII 日志文案。
2. 涉及改动的点，都要先检查对应测试用例是否完整；如果覆盖不足、失效或缺失，必须先补齐再提交。
3. 不要随便将已存在的代码抽取成单独的方法或者类；只有当抽取后的逻辑可以被两个以上位置复用，或者能显著降低复杂度且不破坏可读性时，才允许抽取。
4. 将方法、类、变量上的冗余注释去掉，避免注释重复描述代码字面含义。
5. 在复杂的方法、类和扩展点上适当补充 Javadoc；必要时提供简短使用示例，便于后续使用者参考。
6. 测试用例要覆盖全面；如果某项功能必须依赖 Spring Boot 应用启动后的 Bean 装配、条件注解、配置绑定、`@RabbitListener` 注册或上下文查 Bean 行为，测试就必须模拟真实 Spring Boot 启动后的场景。
7. 修改 `GXCanalMessageParseServiceImpl` 时，必须保留以下关键语义，除非需求明确要求改变并同步更新本文档：
   - 空消息与非法 JSON 的防御性处理。
   - `database + table + "_Service"` 转 Camel Case 后查找 Bean 的约定。
   - 找不到表级处理器时回退到 `defaultProcessCanalDataService`。
   - 按 `INSERT`、`UPDATE`、`DELETE` 分发到 `GXProcessCanalDataService`。
8. 修改 `GXCanalRabbitMQListener` 或 `GXCanalConfig` 时，必须评估是否影响队列名、交换机名、路由键、并发消费者配置、注解绑定方式以及 Spring Boot 自动装配行为。
9. 修改 `GXLocalCanalProperties` 或 `GXNacosCanalProperties` 时，必须确认本地配置与 Nacos 配置的切换条件、配置前缀、数据 ID、组名和自动刷新语义没有被意外破坏。
10. 修改 `GXCanalDataDto`、`GXProcessCanalDataService` 或 `GXCanalMessageParseService` 这类对外契约时，必须优先考虑兼容性，避免静默改变字段语义、方法入参/返回值语义和扩展接入方式。

## 4. 测试要求

1. 任意功能改动都要覆盖成功路径、失败路径、异常路径、空值路径和边界条件。
2. `GXCanalMessageParseServiceImpl` 相关改动至少要覆盖：
   - 空消息。
   - 非 JSON 消息。
   - JSON 反序列化失败。
   - `database` 或 `table` 缺失。
   - 命中表级处理器。
   - 表级处理器缺失后回退默认处理器。
   - 默认处理器也缺失。
   - Bean 类型不匹配。
   - `INSERT`、`UPDATE`、`DELETE`、未知类型四类分支。
   - 处理器返回 `null` 和处理器抛异常。
3. `GXCanalRabbitMQListener`、`GXCanalConfig`、`GXLocalCanalProperties`、`GXNacosCanalProperties` 相关改动，如果涉及 Bean 装配、配置绑定、条件装配、监听注解行为或上下文获取，必须补充 `@SpringBootTest` 或同级别的集成测试。
4. 自定义 `GXProcessCanalDataService` 扩展点相关改动，至少要验证 Bean 命名约定是否仍能被正确解析到。
5. 当前模块没有成熟测试基线。后续第一次修改运行时行为时，应优先建立最小可运行测试骨架，而不是继续无测试迭代。
6. 提交前至少保证模块级测试命令可运行：`mvn -pl leaf-base-data-sync test`。

## 5. 变更前检查清单

1. 是否影响了 RabbitMQ 队列、交换机、路由键、并发消费者或监听绑定关系？
2. 是否影响了本地配置与 Nacos 配置之间的选择逻辑？
3. 是否影响了 `database_table_Service` 到 Camel Case Bean 名称的解析规则？
4. 是否影响了默认兜底处理器 `defaultProcessCanalDataService` 的回退行为？
5. 是否引入了非 ASCII 日志文本？
6. 是否做了没有复用价值的方法或类抽取？
7. 是否删除了冗余注释，并为复杂逻辑补充了必要 Javadoc 或示例？
8. 是否检查并补齐了本次改动所需的测试覆盖？
9. 如果改动依赖 Spring Boot 运行时行为，是否提供了真实启动上下文下的测试？

## 6. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一个 PR 或提交中同步更新本文档：

1. 模块职责边界发生变化，例如新增新的消息接入方式、引入新的核心扩展点，或不再只围绕 Canal + RabbitMQ 同步。
2. 目录结构发生变化，例如新增关键 package、迁移核心类、拆分或合并 `config`、`listener`、`properties`、`service` 等目录职责。
3. 对外契约发生变化，例如 `GXCanalDataDto` 字段语义、`GXCanalMessageParseService` 方法语义、`GXProcessCanalDataService` 扩展约定或 Bean 命名规范发生变化。
4. 默认运行行为发生变化，例如消息校验策略、Bean 路由规则、默认回退策略、`INSERT/UPDATE/DELETE` 分发规则、异常处理策略发生变化。
5. 配置装配策略发生变化，例如本地/Nacos 切换条件、配置前缀、Data ID、Group、自动刷新语义、配置文件路径或默认值发生变化。
6. 测试基线发生变化，例如新增必须遵守的 Spring Boot 集成测试要求、建立了模块测试骨架、或者模块级验证命令发生变化。
7. 团队沉淀出新的长期规则，并且这些规则需要被此模块后续迭代持续遵守，例如日志、测试、注释、文档、兼容性或重构边界要求。
8. 修复线上或高风险问题后，如果该问题暴露出可复用的开发约束、排查规则或回归测试要求，也必须回写到本文档。

## 7. 提交说明建议

1. 涉及监听、解析、Bean 路由、默认回退或配置来源切换的改动，提交说明中要写清行为变化和兼容性影响。
2. 涉及日志调整时，提交前确认新增和修改后的日志文本全部为 ASCII。
3. 涉及运行时装配行为的改动，提交说明中要明确本次验证采用的是单元测试、Spring Boot 集成测试，还是两者都有。
4. 如果本次改动理论上应补测试但暂时无法补齐，必须在提交说明中明确风险、缺口和后续补齐计划。
