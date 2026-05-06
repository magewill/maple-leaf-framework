# AGENTS.md - leaf-base-debezium

本文档用于指导 `leaf-base-debezium` 模块的后续迭代开发。修改本模块时，优先保证 Debezium 配置装配、单实例初始化锁、引擎生命周期管理、CDC 事件分发和 Spring Boot 自动装配语义的兼容性与可验证性，避免做与当前职责无关的重构。

## 1. 模块定位

`leaf-base-debezium` 是 Maple Leaf Framework 中承载 Debezium Embedded CDC 能力的基础模块，当前主要负责：

1. 提供 Debezium 依赖与基础装配，包括 `debezium-api`、`debezium-embedded`、`debezium-connector-mysql`。
2. 通过 `GXDebeziumProperties` 抽象统一 Debezium 配置读取入口，并支持本地 `debezium.yml` 与 Nacos `debezium.yml` 两种配置来源。
3. 通过 `GXDebeziumEngineConfig` 在 Spring Boot 容器启动后初始化 Debezium Engine，并在应用关闭时执行资源释放。
4. 通过 `GXDebeziumService` 暴露业务侧扩展点，要求业务方实现 CDC 事件处理逻辑。
5. 通过 Redisson 分布式锁保证同一应用名与 profile 组合下仅有一个服务实例完成 Debezium 引擎初始化与运行。
6. 对 Debezium 回调事件做 JSON 解析、`payload` 提取和异步分发，并交由业务实现处理。

本模块当前不负责数据库建表、binlog 开启、Debezium Connector 外部运维、业务数据落库策略、事件幂等策略、消费重试编排或 CDC 下游业务路由设计。如需新增这些能力，必须先重新评估模块边界并同步更新本文档。

## 2. 目录职责

1. `config`：Debezium Engine 生命周期管理、条件装配、配置校验、线程执行与关闭逻辑。
2. `properties`：Debezium 配置抽象定义。
3. `properties/local`：本地 `debezium.yml` 配置装配入口。
4. `properties/nacos`：Nacos `debezium.yml` 配置装配入口。
5. `services`：业务扩展接口与默认分布式锁行为。
6. `exception`：Debezium 初始化相关业务异常类型。
7. `src/main/resources`：模块级默认资源文件。
8. `src/test`：测试目录。当前模块尚未建立有效测试基线；后续任何涉及行为修改的开发，都要优先补齐测试骨架与关键覆盖。

## 3. 强制开发规则

1. 所有 `log` 日志中的内容都必须使用 ASCII 字符。禁止新增或保留非 ASCII 日志文案。
2. 涉及改动的点，都必须先检查对应测试用例是否完整；如果覆盖不足、失效或缺失，必须同步补齐。
3. 不要随便将已存在的代码抽取成单独的方法或者类；只有当抽取后的逻辑可被两个以上位置复用，或能显著降低复杂度且不破坏可读性时，才允许抽取。
4. 测试用例要覆盖全面；如果某项功能依赖 Spring Boot 应用启动后的 Bean 装配、条件注解、配置绑定、生命周期回调或上下文取 Bean 行为，测试就必须模拟真实 Spring Boot 应用启动后的场景。
5. 将方法、类、变量上的冗余注释去掉，避免注释重复描述代码字面含义。
6. 在复杂的方法、类和对外扩展点上适当补充 Javadoc；必要时增加简短使用示例，便于后续使用者参考。
7. 修改 `GXDebeziumEngineConfig` 时，必须保留以下关键语义，除非需求明确要求变更并同步更新本文档：
   - `maple.framework.enable.debezium` 为总开关。
   - Debezium Engine 只在获取到初始化锁后才启动。
   - 初始化失败时必须尝试释放锁。
   - 应用关闭时必须关闭 Engine、关闭线程池并释放锁。
   - `record.value()` 为空、`payload` 为空和配置为空时要有明确防御处理。
   - CDC 事件最终通过 `GXDebeziumService.processCaptureDataChange` 分发给业务实现。
8. 修改 `GXDebeziumService` 时，必须评估是否影响业务侧扩展契约，尤其是：
   - `processCaptureDataChange(Dict data)` 的入参结构语义。
   - `tryInitialEngineLock`、`initialEngineUnLock`、`isEngineInitialized` 的锁语义。
   - `LOCK_NAME_FORMAT` 与锁作用域规则。
9. 修改 `GXLocalDebeziumProperties` 或 `GXNacosDebeziumProperties` 时，必须确认本地与 Nacos 的切换条件、配置前缀、Data ID、Group、命名空间与自动刷新语义没有被意外破坏。
10. 修改线程模型、异步处理或关闭逻辑时，优先保持现有生命周期时序清晰，避免为了形式上的“拆方法”而削弱问题定位能力。

## 4. 测试要求

1. 任意功能改动都要覆盖成功路径、失败路径、异常路径、空值路径和边界条件。
2. `GXDebeziumEngineConfig` 相关改动至少要评估并按需覆盖以下场景：
   - `GXDebeziumService` Bean 不存在。
   - 未获取到初始化锁。
   - `debeziumProperties.getConfig()` 为空或缺少必填项。
   - Debezium Engine 创建失败。
   - 事件记录为空。
   - `payload` 为空。
   - 业务处理抛异常。
   - 线程池拒绝执行。
   - `destroy()` 重复调用。
   - 关闭过程中 `awaitTermination` 超时或被中断。
3. `GXDebeziumProperties`、`GXLocalDebeziumProperties`、`GXNacosDebeziumProperties` 相关改动，如果涉及配置绑定、条件装配或配置来源切换，不能只写纯单测，必须至少补一个 Spring Boot 上下文启动测试。
4. `GXDebeziumService` 默认锁实现相关改动，至少要验证锁获取、锁释放和已初始化判断的行为是否仍然一致；如依赖真实 Redisson Bean 装配，测试应在接近真实 Spring Boot 启动的环境下执行。
5. 当前模块没有成熟测试基线。后续第一次修改运行时行为时，应优先建立最小可运行测试骨架，而不是继续无测试迭代。
6. 提交前至少保证模块级测试命令可运行：`mvn -pl leaf-base-debezium test`。

## 5. 变更前检查清单

1. 是否影响 Debezium Engine 的初始化、运行、关闭或锁释放时序？
2. 是否影响 `GXDebeziumService` 作为业务扩展点的入参语义或调用时机？
3. 是否影响本地配置与 Nacos 配置之间的装配切换逻辑？
4. 是否影响 Debezium 必填配置项校验规则？
5. 是否引入了非 ASCII 的日志文本？
6. 是否做了没有复用价值的方法或类抽取？
7. 是否删除了冗余注释，并为复杂逻辑补充了必要的 Javadoc 或使用示例？
8. 是否检查并补齐了本次改动所需的测试覆盖？
9. 如果改动依赖 Spring Boot 运行时行为，是否提供了真实启动上下文下的测试？

## 6. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一个 PR 或提交中同步更新本文档：

1. 模块职责边界发生变化，例如新增新的 CDC 来源、引入新的核心扩展点，或不再只围绕 Debezium Embedded + MySQL Connector 工作。
2. 目录结构发生变化，例如新增关键 package、迁移核心类、拆分或合并 `config`、`properties`、`services`、`exception` 等目录职责。
3. 对外契约发生变化，例如 `GXDebeziumService` 方法语义、锁命名规则、事件载荷约定或配置前缀发生变化。
4. 默认运行行为发生变化，例如初始化锁策略、线程模型、事件分发方式、异常处理策略、关闭策略或配置校验规则发生变化。
5. 配置装配策略发生变化，例如本地/Nacos 切换条件、Data ID、Group、命名空间、配置文件路径或自动刷新语义发生变化。
6. 测试基线发生变化，例如新增了模块级测试骨架、引入了必须遵守的 Spring Boot 集成测试要求、调整了模块验证命令。
7. 团队沉淀出新的长期规则，并且这些规则需要被本模块后续迭代持续遵守，例如日志、测试、注释、文档、兼容性或重构边界要求。
8. 修复线上或高风险问题后，如果该问题暴露出可复用的开发约束、排查规则或回归测试要求，也必须回写到本文档。

## 7. 提交说明建议

1. 涉及 Debezium 初始化、锁语义、事件分发、配置来源切换的改动，提交说明中要写清行为变化与兼容性影响。
2. 涉及日志调整时，提交前确认新增和修改后的日志文本全部为 ASCII。
3. 涉及运行时装配行为的改动，提交说明中要明确本次验证采用的是单元测试、Spring Boot 集成测试，还是两者都有。
4. 如果本次改动理论上应补测试但暂时无法补齐，必须在提交说明中明确风险、缺口和后续补齐计划。
