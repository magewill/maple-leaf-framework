# AGENTS.md - leaf-base-seata

本文档用于指导 `leaf-base-seata` 模块的后续迭代开发。修改本模块时，优先保证 Seata 配置加载策略、动态数据源整合行为、MyBatis-Plus 接入方式、生产兼容性、性能和线程安全稳定，不做与当前职责无关的重构。

## 1. 模块定位

`leaf-base-seata` 是 Maple Leaf Framework 中面向 Seata 分布式事务集成的基础模块，当前主要负责：

1. 基于 `seata-spring-boot-starter` 提供 Seata 客户端接入能力。
2. 将框架内的动态数据源能力与 Seata 整合，确保 MyBatis-Plus 使用 `GXDynamicDataSource`。
3. 提供两套 Seata 配置加载入口：
   - 无 Spring Cloud Alibaba Nacos Config 时，从 `classpath:/${spring.profiles.active}/seata.yml` 加载本地配置。
   - 存在 Spring Cloud Alibaba Nacos Config 时，从 Nacos 的 `seata.yml` 加载配置并开启自动刷新。
4. 通过条件装配控制 Seata 集成只在满足依赖条件时生效，避免无效装配或重复代理。

本模块当前不承担以下职责：

1. 不实现 `@GlobalTransactional` 业务编排逻辑。
2. 不负责 Seata Server 部署、注册中心运维或远程事务链路排障工具。
3. 不额外封装一套独立的数据源代理体系。
4. 不提供事务测试基础设施之外的业务样例工程。

## 2. 目录职责

1. `config/GXSeataDynamicDataSourceConfig`
   负责将 `GXDynamicDataSource` 注入 MyBatis-Plus 的 `SqlSessionFactoryBean` 定制流程，确保 MyBatis-Plus 运行在已由上游动态数据源模块处理过的 Seata 环境中。

2. `properties/GXLocalSeataProperties`
   负责在未接入 Nacos Config 时加载本地 `seata.yml` 配置。

3. `properties/GXNacosSeataProperties`
   负责在接入 Nacos Config 时加载 Nacos 中的 `seata.yml` 配置，并启用自动刷新。

4. `src/test`
   当前模块尚未形成稳定测试基线；后续涉及改动时，必须按变更点补齐单元测试或 Spring Boot 集成测试。

## 3. 强制开发规则

1. 所有 `log` 日志内容必须使用 ASCII 字符，禁止新增或保留非 ASCII 日志文案。
2. 每个涉及改动的点都必须同步检查测试用例是否完整覆盖；覆盖不足时必须补充或更新测试。
3. 每次修改都只能修改 `leaf-base-seata` 当前 Maven 模块，不得修改其它模块的代码、配置、测试或文档，除非用户明确要求跨模块调整。
4. 现有代码已经在生产环境使用，不得随意更改对外接口规范、默认行为、Bean 装配语义、配置入口、异常传播语义或兼容性约定。
5. 在确保原有功能正确性的前提下进行完善；修改已存在逻辑时，不得改变现有逻辑的正确性。
6. 本模块作为基础框架能力，代码性能、并发正确性和线程安全性必须优先评估。
7. 在线程池与虚拟线程都能满足需求的场景中，优先使用虚拟线程；确需传统线程池时，应说明固定线程资源、调度器、ThreadLocal 或第三方组件限制等原因。
8. 严格遵循测试驱动最佳实践：先用测试描述目标行为或缺陷，再进行最小实现，最后通过回归测试确认既有正确行为没有被破坏。
9. 不要随意将已存在的代码抽取成单独的方法或类；只有抽取后的逻辑能被两个以上地方复用，或能显著降低复杂度时，才允许抽取。
10. 删除方法、类、变量上的冗余注释；能通过命名、类型和局部代码直接理解的内容，不再添加或保留重复解释。
11. 清理 Javadoc 中的冗余信息，只保留必要文档；复杂方法、复杂类、条件装配入口和对外约束点可适当补充 Javadoc、边界说明和简短使用示例。
12. 变更 `GXSeataDynamicDataSourceConfig` 时，必须保持“由 `GXDynamicDataSourceConfig` 处理物理数据源与 Seata 代理，本类只负责把 `GXDynamicDataSource` 接入 MyBatis-Plus”的职责边界，不要在这里重复包装 `DataSourceProxy`。
13. 变更 `GXLocalSeataProperties` 或 `GXNacosSeataProperties` 时，必须维持二选一的配置加载语义，避免本地配置与 Nacos 配置同时生效。
14. 变更 Nacos 配置装配时，必须确认 `dataId`、`groupId`、`serverAddr`、`namespace`、`username`、`password` 与 `autoRefreshed` 行为没有被意外破坏。
15. 变更本地配置装配时，必须确认 `spring.profiles.active` 对应目录下的 `seata.yml` 仍为唯一入口，并明确评估 `ignoreResourceNotFound = false` 的启动影响。
16. 涉及 MyBatis-Plus、动态数据源或 Seata 条件装配的改动，不要为了风格统一重写整套自动配置链路，应优先复用现有装配点。

## 4. 测试要求

1. 任何功能改动都必须覆盖成功路径、失败路径、异常路径和边界条件。
2. 如果改动影响以下内容，至少补充对应测试：
   - `GXSeataDynamicDataSourceConfig` 的条件装配是否生效。
   - `seataDynamicDataSourceCustomizer` 是否将 `GXDynamicDataSource` 注入到 `SqlSessionFactoryBean`。
   - 本地配置与 Nacos 配置的互斥装配是否正确。
   - Nacos 配置项表达式是否仍能正确绑定到 `seata.yml`。
   - 日志内容是否保持 ASCII。
3. 如果功能需要在 Spring Boot 应用真实启动后才能验证，例如条件注解、自动配置、Bean 注入、配置源切换、MyBatis-Plus 集成行为，测试用例必须模拟真实 Spring Boot 应用启动后的运行形态。优先使用 `@SpringBootTest`、`ApplicationContextRunner` 或等价方式，不能只写纯单元测试。
4. 当前模块没有现成测试时，触及相关能力的修改不能以“暂无测试”为理由跳过验证，必须为本次改动建立最小可维护测试基线。
5. 测试应尽量避免依赖真实外部 Seata Server 或 Nacos Server；除非变更目标就是外部集成验证，否则优先通过 Spring 上下文、条件装配和配置绑定测试验证模块契约。
6. 模块级最小验证命令为 `mvn -pl leaf-base-seata test`。

## 5. 变更前检查清单

1. 是否影响 `GXSeataDynamicDataSourceConfig` 的条件装配前提：
   - `DruidDataSource`
   - `GXDynamicDataSourceConfig`
   - `org.apache.seata.rm.datasource.SeataDataSourceProxy`
2. 是否影响 MyBatis-Plus 最终拿到的数据源类型，导致不再使用 `GXDynamicDataSource`。
3. 是否引入了对 `DataSourceProxy` 的重复代理风险。
4. 是否影响本地 `seata.yml` 与 Nacos `seata.yml` 的加载优先级或互斥关系。
5. 是否影响 Nacos 自动刷新行为。
6. 是否引入了非 ASCII 日志文本。
7. 是否进行了不必要的方法或类抽取。
8. 是否清理了冗余注释和冗余 Javadoc，并仅为复杂逻辑补充了必要 Javadoc 或示例。
9. 是否检查并补齐了与改动点匹配的测试。
10. 是否只修改了 `leaf-base-seata` 当前 Maven 模块内的文件。
11. 是否保持生产环境已使用的对外接口规范、默认行为和异常传播语义稳定。
12. 是否评估了性能、并发正确性和线程安全影响。
13. 是否在适合的异步或并发场景中优先考虑了虚拟线程。
14. 是否遵循测试驱动最佳实践，先用测试描述目标行为再调整实现。

## 6. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一 PR 或提交中同步更新本文件：

1. 模块职责边界变化，例如新增 Seata 事务辅助能力、扩展新的配置加载源、改变与动态数据源模块的协作方式。
2. 目录结构变化，例如新增核心包、迁移核心类、删除关键装配类。
3. 对外契约变化，例如本模块公开的配置入口、条件装配前提、Bean 装配方式、依赖前提或异常语义发生变化。
4. `GXSeataDynamicDataSourceConfig` 的职责变化，例如不再只做 MyBatis-Plus 数据源注入，或新增额外代理逻辑。
5. `GXLocalSeataProperties`、`GXNacosSeataProperties` 的条件注解、配置路径、Nacos 绑定参数或自动刷新语义发生变化。
6. 测试策略变化，例如新增必须遵守的 Spring Boot 集成测试基线、调整模块最小验证命令、引入新的测试夹具规范。
7. 团队新增需要长期遵守且可复用的编码、日志、测试、注释、文档、性能、线程安全或兼容性规则。
8. 修复的问题暴露出新的长期风险、红线或回归测试要求。

## 7. 文档与注释规范

1. 注释应解释意图、边界、兼容性约束和非显而易见的选择，不应复述代码字面含义。
2. 对复杂类、复杂方法、公共扩展点、条件装配入口和容易误用的配置契约，可以保留必要 Javadoc。
3. 使用方式比较复杂时，可以补充简短示例；示例应聚焦真实调用方式和关键边界，不要堆砌无关信息。
4. 清理 Javadoc 时优先删除重复、空泛、过时或只复述方法签名的信息。

## 8. 提交说明建议

1. 涉及 Seata 配置加载策略、条件装配或动态数据源整合行为变更时，提交说明中必须写清行为变化、兼容性影响和回滚方式。
2. 涉及 Spring Boot 自动配置行为的改动时，提交说明中应明确说明新增测试如何覆盖装配结果。
3. 提交前确认新增或修改的日志均为 ASCII。
4. 提交前确认测试已覆盖改动点，并在 PR 中说明关键验证结果。
