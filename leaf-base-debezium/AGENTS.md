# AGENTS.md - leaf-base-debezium

本文档用于指导 `leaf-base-debezium` 模块的后续迭代开发。修改本模块时，优先保证 Debezium 配置装配、单实例初始化锁、引擎生命周期管理、CDC 事件分发、线程模型和 Spring Boot 自动装配语义的兼容性、可验证性、性能与线程安全。

## 1. 模块定位

`leaf-base-debezium` 是 Maple Leaf Framework 中承载 Debezium Embedded CDC 能力的基础模块，当前主要负责：

1. 提供 Debezium 依赖与基础装配，包括 `debezium-api`、`debezium-embedded`、`debezium-connector-mysql`。
2. 通过 `GXDebeziumProperties` 抽象统一 Debezium 配置读取入口，并支持本地 `debezium.yml` 与 Nacos `debezium.yml` 两种配置来源。
3. 通过 `GXDebeziumEngineConfig` 在 Spring Boot 容器启动后初始化 Debezium Engine，并在应用关闭时释放资源。
4. 通过 `GXDebeziumService` 暴露业务侧扩展点，由业务方实现 CDC 事件处理逻辑。
5. 通过 Redisson 分布式锁保证同一应用名与 profile 组合下仅有一个服务实例完成 Debezium 引擎初始化与运行。
6. 对 Debezium 回调事件做 JSON 解析、`payload` 提取和异步分发，并交由业务实现处理。

本模块当前不负责数据库建表、binlog 开启、Debezium Connector 外部运维、业务数据落库策略、事件幂等策略、消费重试编排或 CDC 下游业务路由设计。如需新增这些能力，必须先重新评估模块边界，并同步更新本文档。

## 2. 目录职责

1. `config`：Debezium Engine 生命周期管理、条件装配、配置校验、线程执行与关闭逻辑。
2. `properties`：Debezium 配置抽象定义。
3. `properties/local`：本地 `debezium.yml` 配置装配入口。
4. `properties/nacos`：Nacos `debezium.yml` 配置装配入口。
5. `services`：业务扩展接口与默认分布式锁行为。
6. `exception`：Debezium 初始化相关业务异常类型。
7. `src/main/resources`：模块级默认资源文件。
8. `src/test`：测试目录。当前模块尚未建立有效测试基线；后续任何涉及行为修改的开发，都要优先补齐测试骨架与关键覆盖。

## 3. 模块边界与兼容性

1. 每次修改都只能修改当前 Maven 模块 `leaf-base-debezium`；除非用户明确要求或跨模块编译契约必须同步调整，不得修改其它模块代码、配置或测试。
2. 现有代码已经在生产环境使用，不得随意更改对外接口规范、方法签名、默认行为、配置前缀、配置键、锁名称规则、事件载荷语义或异常语义。
3. 对外扩展点默认视为稳定契约，尤其是 `GXDebeziumService.processCaptureDataChange(Dict data)` 的入参结构语义，以及 `tryInitialEngineLock`、`initialEngineUnLock`、`isEngineInitialized` 的锁语义。
4. 在确保原有功能正确性的条件下进行完善。修改已经存在的逻辑时，不得改变现有逻辑的正确性；确需调整行为时，必须说明兼容性影响并补充回归测试。
5. 本模块作为基础框架，性能、线程安全性和资源释放路径非常重要。涉及异步执行、锁、关闭流程、配置刷新、事件分发时，必须重点审查并发安全、内存可见性、异常隔离和热点路径开销。
6. 在线程池与虚拟线程都能满足需求的场景中，优先使用虚拟线程；只有需要固定线程资源、复用线程本地状态、绑定专用调度器或受第三方组件限制时，才优先选择传统线程池。

## 4. 强制开发规则

1. 所有 `log` 日志中的内容都必须使用 ASCII 字符。禁止新增或保留非 ASCII 日志文案，避免不同运行环境、终端编码或日志采集链路出现乱码。
2. 涉及改动的点，都必须查看相关测试用例是否完整；如果覆盖不足、失效或缺失，必须同步补齐。
3. 严格遵循测试驱动最佳实践：先用测试明确问题或目标行为，再进行最小实现，最后通过回归测试确认既有正确行为没有被破坏。
4. 不要随便将已存在的代码抽取成单独的方法或者类。只有抽取后的逻辑可以被两个以上的位置复用，或能显著降低复杂度且不破坏可读性时，才允许抽取。
5. 将方法、类、变量上的冗余注释去掉。命名、类型和局部代码已经能表达清楚的内容，不再保留重复解释。
6. 清理 Javadoc 中重复、空泛或仅复述签名的信息，只保留必要文档，例如语义说明、边界条件、异常约定、线程安全说明和兼容性要求。
7. 在复杂的方法、类、对外扩展点或公共能力上适当添加 Javadoc；如果使用方式比较复杂，可以补充简短使用实例，便于使用者参考。
8. 新增能力优先复用 Spring Boot、Spring Framework、Debezium 或项目内既有机制；新增前必须判断是否与框架原生能力或已有模块能力重复。
9. 禁止提交真实密钥、令牌、生产地址、密码或其它敏感信息。

## 5. 关键代码约束

1. 修改 `GXDebeziumEngineConfig` 时，必须保留以下关键语义，除非需求明确要求变更并同步更新本文档：
   - `maple.framework.enable.debezium` 为总开关。
   - Debezium Engine 只在获取到初始化锁后启动。
   - 初始化失败时必须尝试释放锁。
   - 应用关闭时必须关闭 Engine、关闭执行资源并释放锁。
   - `record.value()` 为空、`payload` 为空和配置为空时要有明确防御处理。
   - CDC 事件最终通过 `GXDebeziumService.processCaptureDataChange` 分发给业务实现。
2. 修改 `GXDebeziumService` 时，必须评估是否影响业务侧扩展契约，尤其是：
   - `processCaptureDataChange(Dict data)` 的入参结构语义。
   - `tryInitialEngineLock`、`initialEngineUnLock`、`isEngineInitialized` 的锁语义。
   - `LOCK_NAME_FORMAT` 与锁作用域规则。
   - 默认锁必须带 TTL、owner token 与运行期续期，释放或续期时不得误操作其它实例新获得的锁。
3. 修改 `GXLocalDebeziumProperties` 或 `GXNacosDebeziumProperties` 时，必须确认本地与 Nacos 的切换条件、配置前缀、Data ID、Group、命名空间与自动刷新语义没有被意外破坏。
4. 修改线程模型、异步处理或关闭逻辑时，必须保持生命周期时序清晰，覆盖正常完成、异常完成、拒绝执行、超时、中断恢复和重复关闭场景。
5. 捕获 `InterruptedException` 后必须恢复线程中断标记。写入 ThreadLocal、MDC 或类似上下文后，必须在 `finally` 中清理或恢复。

## 6. 测试要求

1. 任意功能改动都要覆盖成功路径、失败路径、异常路径、空值路径、边界条件和回归场景。
2. 测试用例要覆盖全面。如果功能需要在 Spring Boot 应用运行后才能测试，例如 Bean 装配、条件注解、配置绑定、生命周期回调、上下文取 Bean、自动装配或配置刷新，那么测试用例就必须模拟真实 Spring Boot 应用启动后的场景，不能只写纯单元测试。
3. 涉及代码改动时，必须同步查看相关测试用例是否完整覆盖改动点；若覆盖不足，应补充或更新测试。
4. `GXDebeziumEngineConfig` 相关改动至少要评估并按需覆盖以下场景：
   - `GXDebeziumService` Bean 不存在。
   - 未获取到初始化锁。
   - `debeziumProperties.getConfig()` 为空或缺少必填项。
   - Debezium Engine 创建失败。
   - 事件记录为空。
   - `payload` 为空。
   - 业务处理抛异常。
   - 线程执行资源拒绝执行。
   - `destroy()` 重复调用。
   - 关闭过程中 `awaitTermination` 超时或被中断。
5. `GXDebeziumProperties`、`GXLocalDebeziumProperties`、`GXNacosDebeziumProperties` 相关改动，如果涉及配置绑定、条件装配或配置来源切换，不能只写纯单元测试，必须至少补充一个 Spring Boot 上下文启动测试。
6. `GXDebeziumService` 默认锁实现相关改动，至少要验证锁获取、锁释放和已初始化判断的行为是否仍然一致；如依赖真实 Redisson Bean 装配，测试应在接近真实 Spring Boot 启动的环境下执行。
7. 当前模块没有成熟测试基线。后续第一次修改运行时行为时，应优先建立最小可运行测试骨架，而不是继续无测试迭代。
8. 提交前至少保证模块级测试命令可运行：`mvn -pl leaf-base-debezium test`。

## 7. 变更前检查清单

1. 是否只修改了 `leaf-base-debezium` 当前 Maven 模块？
2. 是否影响 Debezium Engine 的初始化、运行、关闭或锁释放时序？
3. 是否影响 `GXDebeziumService` 作为业务扩展点的入参语义、调用时机或锁语义？
4. 是否影响本地配置与 Nacos 配置之间的装配切换逻辑？
5. 是否影响 Debezium 必填配置项校验规则？
6. 是否引入了非 ASCII 日志文本？
7. 是否做了没有复用价值的方法或类抽取？
8. 是否删除了冗余注释，并为复杂逻辑补充了必要 Javadoc 或使用示例？
9. 是否清理了 Javadoc 冗余信息，只保留必要文档？
10. 是否检查并补齐了本次改动所需的测试覆盖？
11. 如果改动依赖 Spring Boot 运行时行为，是否提供了真实启动上下文下的测试？
12. 是否确认没有改变生产中已使用的对外接口规范和既有正确行为？
13. 是否评估了性能、线程安全性和资源释放路径？

## 8. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一个 PR 或提交中同步更新本文档：

1. 模块职责边界发生变化，例如新增 CDC 来源、引入新的核心扩展点，或不再只围绕 Debezium Embedded 与 MySQL Connector 工作。
2. 目录结构发生变化，例如新增关键 package、迁移核心类、拆分或合并 `config`、`properties`、`services`、`exception` 等目录职责。
3. 对外契约发生变化，例如 `GXDebeziumService` 方法语义、锁命名规则、事件载荷约定、配置前缀、配置键或异常语义发生变化。
4. 默认运行行为发生变化，例如初始化锁策略、线程模型、虚拟线程使用策略、事件分发方式、异常处理策略、关闭策略或配置校验规则发生变化。
5. 配置装配策略发生变化，例如本地与 Nacos 切换条件、Data ID、Group、命名空间、配置文件路径或自动刷新语义发生变化。
6. 测试基线发生变化，例如新增模块级测试骨架、引入必须遵守的 Spring Boot 集成测试要求，或调整模块验证命令。
7. 团队沉淀出新的长期规则，并且这些规则需要被本模块后续迭代持续遵守，例如日志、测试、注释、Javadoc、兼容性、性能、线程安全或重构边界要求。
8. 修复线上或高风险问题后，如果该问题暴露出可复用的开发约束、排查规则或回归测试要求，必须回写到本文档。
9. 自动化工具、代码生成器、脚手架或仓库规则触发了本模块结构、测试、配置或约束变化时，必须检查并更新本文档。

## 9. 提交说明建议

1. 涉及 Debezium 初始化、锁语义、事件分发、线程模型或配置来源切换的改动，提交说明中要写清行为变化与兼容性影响。
2. 涉及日志调整时，提交前确认新增和修改后的日志文本全部为 ASCII。
3. 涉及运行时装配行为的改动，提交说明中要明确本次验证采用的是单元测试、Spring Boot 集成测试，还是两者都有。
4. 如果本次改动理论上应补测试但暂时无法补齐，必须在提交说明中明确风险、缺口和后续补齐计划。
