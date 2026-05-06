# AGENTS.md - leaf-base-seata

本文档用于指导 `leaf-base-seata` 模块的后续迭代开发。修改本模块时，优先保证 Seata 配置加载策略、动态数据源整合行为以及 MyBatis-Plus 接入方式的稳定性，避免与当前职责无关的重构。

## 1. 模块定位

`leaf-base-seata` 是 Maple Leaf Framework 中面向 Seata 分布式事务集成的基础模块，当前主要负责：

1. 基于 `seata-spring-boot-starter` 提供 Seata 客户端接入能力。
2. 将框架内的动态数据源能力与 Seata 整合，确保 MyBatis-Plus 使用 `GXDynamicDataSource`。
3. 提供两套 Seata 配置加载入口：
   - 无 Nacos 时，从 `classpath:/${spring.profiles.active}/seata.yml` 加载本地配置。
   - 存在 Spring Cloud Alibaba Nacos Config 时，从 Nacos 的 `seata.yml` 加载配置并开启自动刷新。
4. 通过条件装配控制 Seata 集成仅在满足依赖条件时生效，避免无效装配或重复代理。

本模块当前不直接承载以下职责：

1. 不负责实现 `@GlobalTransactional` 业务编排逻辑。
2. 不负责 Seata Server 部署、注册中心运维和远程事务链路排障工具。
3. 不负责额外封装一套独立的数据源代理体系。
4. 不负责事务测试基建之外的业务样例工程。

## 2. 目录职责

1. `config/GXSeataDynamicDataSourceConfig`
   负责将 `GXDynamicDataSource` 注入 MyBatis-Plus 的 `SqlSessionFactoryBean` 定制流程，确保 MyBatis-Plus 运行在已由上游动态数据源模块处理过的 Seata 环境中。
2. `properties/GXLocalSeataProperties`
   负责在未接入 Nacos Config 时加载本地 `seata.yml` 配置。
3. `properties/GXNacosSeataProperties`
   负责在接入 Nacos Config 时加载 Nacos 中的 `seata.yml` 配置，并启用自动刷新。
4. `src/test`
   当前模块尚未形成稳定测试基线；后续涉及改动时，需要按变更点补齐单元测试或 Spring Boot 集成测试。

## 3. 强制开发规则

1. 所有 `log` 日志中的内容都必须使用 ASCII 字符，禁止新增或保留非 ASCII 日志文案。
2. 涉及到改动的点，都必须检查测试用例是否完整覆盖；覆盖不足时必须补充测试。
3. 不要随便将已存在的代码抽取成单独的方法或者类；只有当抽取后的逻辑可以被两个及以上位置复用，或能显著降低复杂度时才允许抽取。
4. 删除方法、类、变量上的冗余注释，避免注释重复描述代码字面含义。
5. 对复杂的方法、类、条件装配入口和对外约束点，适当补充 Javadoc；必要时加入简短使用示例，方便后续使用者参考。
6. 变更 `GXSeataDynamicDataSourceConfig` 时，必须保持“由 `GXDynamicDataSourceConfig` 处理物理数据源与 Seata 代理，本类只负责把 `GXDynamicDataSource` 接入 MyBatis-Plus”这一职责边界，不要在这里重复包裹 `DataSourceProxy`。
7. 变更 `GXLocalSeataProperties` 或 `GXNacosSeataProperties` 时，必须维持二选一的配置加载语义，避免本地配置与 Nacos 配置同时生效。
8. 变更 Nacos 配置装配时，必须确认 `dataId`、`groupId`、`serverAddr`、`namespace`、`username`、`password` 与 `autoRefreshed` 行为没有被意外破坏。
9. 变更本地配置装配时，必须确认 `spring.profiles.active` 对应目录下的 `seata.yml` 仍为唯一入口，并明确评估 `ignoreResourceNotFound = false` 的启动影响。
10. 涉及 MyBatis-Plus、动态数据源或 Seata 条件装配的改动，不要为了“风格统一”重写整套自动配置链路，应优先复用现有装配点。

## 4. 测试要求

1. 任何功能改动都必须覆盖成功路径、失败路径、异常路径和边界条件。
2. 如果改动影响以下内容，至少补充对应测试：
   - `GXSeataDynamicDataSourceConfig` 的条件装配是否生效。
   - `seataDynamicDataSourceCustomizer` 是否将 `GXDynamicDataSource` 注入到 `SqlSessionFactoryBean`。
   - 本地配置与 Nacos 配置的互斥装配是否正确。
   - Nacos 配置项表达式是否仍能正确绑定到 `seata.yml`。
3. 如果该功能需要在 Spring Boot 应用真实启动后才能验证，例如条件注解、自动配置、Bean 注入、配置源切换、MyBatis-Plus 集成行为，那么测试用例必须采用接近真实运行形态的 Spring Boot 启动测试，例如 `@SpringBootTest`、`ApplicationContextRunner` 或等价方式，而不是只写纯单元测试。
4. 当前模块没有现成测试时，触及相关能力的修改不能以“暂无测试”为理由跳过验证，必须为本次改动建立最小可维护测试基线。
5. 模块级最小验证命令为 `mvn -pl leaf-base-seata test`。

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
8. 是否清理了冗余注释，并为复杂逻辑补充了必要的 Javadoc 或示例。
9. 是否检查并补齐了与改动点匹配的测试。

## 6. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一 PR 或提交中同步更新本文件：

1. 模块职责边界变化，例如新增 Seata 事务辅助能力、扩展新的配置加载源、改变与动态数据源模块的协作方式。
2. 目录结构变化，例如新增核心包、迁移核心类、删除关键装配类。
3. 对外契约变化，例如本模块公开的配置入口、条件装配前提、Bean 装配方式或依赖前提发生变化。
4. `GXSeataDynamicDataSourceConfig` 的职责变化，例如不再只做 MyBatis-Plus 数据源注入，或新增额外代理逻辑。
5. `GXLocalSeataProperties`、`GXNacosSeataProperties` 的条件注解、配置路径、Nacos 绑定参数、自动刷新语义发生变化。
6. 测试策略变化，例如新增必须遵守的 Spring Boot 集成测试基线、调整模块最小验证命令、引入新的测试夹具规范。
7. 团队新增了需要长期遵守且可复用的编码、日志、测试、注释、文档或兼容性规则。

## 7. 提交说明建议

1. 涉及 Seata 配置加载策略、条件装配或动态数据源整合行为变更时，提交说明中必须写清行为变化和兼容性影响。
2. 涉及 Spring Boot 自动配置行为的改动时，提交说明中应明确说明新增测试如何覆盖装配结果。
3. 提交前确认新增或修改的日志均为 ASCII。
4. 提交前确认测试已覆盖改动点；如果暂时无法补齐测试，需要在提交说明中明确风险和缺口。
