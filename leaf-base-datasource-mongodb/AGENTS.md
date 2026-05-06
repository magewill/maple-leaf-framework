# AGENTS.md - leaf-base-datasource-mongodb

本文档用于指导 `leaf-base-datasource-mongodb` 模块的后续迭代开发。所有修改都应优先保持现有模块边界、装配方式和对外行为稳定，避免与当前实现无关的重构。

## 1. 模块定位

`leaf-base-datasource-mongodb` 是 Maple Leaf Framework 中负责 MongoDB 基础设施接入的模块，核心职责如下：

1. 基于 `mongodb.datasource.*` 配置动态注册多个 `MongoClient`、`MongoDatabaseFactory`、`MongoTemplate`。
2. 兼容本地配置与 Nacos 场景，自动选择 `GXLocalMongoDynamicDataSourceProperties` 或 `GXNacosMongoDynamicDataSourceProperties`。
3. 提供动态主数据源路由能力，使 `mongoTemplate`、`mongoOperations`、`mongoDatabaseFactory` 等标准注入点始终指向当前激活的数据源。
4. 通过 `GXMongoTemplateContext` 提供线程内、可嵌套的 `MongoTemplate` 上下文切换与快照恢复能力。
5. 提供 MongoDB Repository/Service 抽象，统一查询条件、分页、字段更新、软删除、上下文透传等通用能力。

该模块不负责业务领域建模，不承载具体业务流程，也不应在此模块内引入与 MongoDB 基础设施无关的应用层逻辑。

## 2. 技术基线

| 项目 | 当前约束 |
| --- | --- |
| JDK | 21 |
| Spring Boot | 跟随父工程 |
| Spring Data MongoDB | 由 `spring-boot-starter-data-mongodb` 管理 |
| 测试框架 | JUnit 5、Spring Boot Test、AssertJ、Mockito |

## 3. 关键文件

核心装配与动态路由：

1. `src/main/java/cn/maple/mongodb/datasource/config/GXMongoBeanDefinitionRegistryPostProcessor.java`
2. `src/main/java/cn/maple/mongodb/datasource/config/GXDynamicMongoDatabaseFactory.java`
3. `src/main/resources/application.yml`

上下文与属性：

1. `src/main/java/cn/maple/mongodb/datasource/context/GXMongoTemplateContext.java`
2. `src/main/java/cn/maple/mongodb/datasource/properties/GXMongoDataSourceProperties.java`
3. `src/main/java/cn/maple/mongodb/datasource/properties/GXMongoDynamicDataSourceProperties.java`
4. `src/main/java/cn/maple/mongodb/datasource/properties/local/GXLocalMongoDynamicDataSourceProperties.java`
5. `src/main/java/cn/maple/mongodb/datasource/properties/nacos/GXNacosMongoDynamicDataSourceProperties.java`

Repository/Service 抽象：

1. `src/main/java/cn/maple/mongodb/datasource/repository/GXMongoRepository.java`
2. `src/main/java/cn/maple/mongodb/datasource/service/GXMongoService.java`
3. `src/main/java/cn/maple/mongodb/datasource/service/impl/GXMongoServiceImpl.java`
4. `src/main/java/cn/maple/mongodb/datasource/dao/GXMongoDao.java`
5. `src/main/java/cn/maple/mongodb/datasource/model/GXMongoModel.java`

现有测试：

1. `src/test/java/cn/maple/mongodb/datasource/config/GXMongoBeanDefinitionRegistryPostProcessorTest.java`
2. `src/test/java/cn/maple/mongodb/datasource/config/GXDynamicMongoDatabaseFactoryTest.java`
3. `src/test/java/cn/maple/mongodb/datasource/context/GXMongoTemplateContextTest.java`
4. `src/test/java/cn/maple/mongodb/datasource/repository/GXMongoRepositoryTest.java`
5. `src/test/java/cn/maple/mongodb/datasource/service/impl/GXMongoServiceImplTest.java`

## 4. 强制行为约束

1. `GXMongoBeanDefinitionRegistryPostProcessor` 必须继续保证“有且仅有一个 primary 数据源”，缺失或重复 primary 时必须快速失败。
2. 标准 Spring Mongo 注入点 `mongoClient`、`mongoDatabaseFactory`、`mongoTemplate`、`mongoOperations` 必须继续可用，并指向动态主数据源。
3. `GXDynamicMongoDatabaseFactory` 必须根据 `GXMongoTemplateContext` 决定目标 `MongoDatabaseFactory`，不能退化为固定单数据源实现。
4. `GXMongoTemplateContext` 必须保持栈语义，`push` 与 `pop` 必须成对使用，禁止改成覆盖式单值上下文。
5. 上下文快照恢复逻辑必须保证线程池、虚拟线程、异常分支下不会泄漏旧的 `MongoTemplate` 名称。
6. 同一个 `ClientSession` 绑定到某个数据源后，必须继续阻止在相同 session 中切换到其他数据源。
7. `GXMongoRepository` 中字段名规范化规则必须保持兼容：`id -> _id`，驼峰字段默认转下划线。
8. `GXMongoRepository` 与 `GXMongoServiceImpl` 当前对空条件的保护不能弱化，涉及更新、删除、存在性校验的操作必须继续保留非空条件约束。
9. 现有不支持的能力，如 union 查询、union 分页、直接调用 mapper 方法，必须继续显式抛出异常，除非本模块明确新增对应能力并补齐测试。
10. 模块级日志文案必须全部使用 ASCII 字符。新增或修改日志时，日志消息、占位文本、固定字符串中禁止出现非 ASCII 字符。

## 5. 代码演进规则

1. 优先沿用现有抽象层级和命名方式，避免仅为局部改动引入新的基础设施层、helper 层或包装器。
2. 不要随便将已存在的代码抽取成单独的方法或者类，除非抽取后的方法或类能够被两个以上的位置复用，并且确实提升可维护性。
3. 修改 Repository/Service 通用能力时，优先在 `GXMongoRepository` 或 `GXMongoServiceImpl` 内完成，不要把通用逻辑散落到下游业务模块。
4. 新增保留字、默认 Bean 名、别名替换逻辑时，必须同步评估是否会破坏 Spring 默认装配。
5. 新增配置项时，必须提供清晰的默认行为或失败语义，不能让启动流程依赖隐式空值。
6. 与连接串、用户名、密码、认证库相关的处理，必须保持 `GXCommonUtils.decodeConnectStr(...)` 的解码兼容性。
7. 日志输出应聚焦可诊断信息，避免输出敏感信息明文，尤其是 URI 中可能包含的凭据、用户名、密码等内容。
8. 将方法、类、变量上的冗余注释去掉。对“注释只是在复述命名本身”的内容应直接删除。
9. 对复杂的方法、复杂的类、对外扩展点或容易误用的能力，适当补充 Javadoc，并在必要时提供简短使用示例，帮助调用方理解适用场景、关键参数和行为边界。

## 6. 测试要求

1. 涉及到改动的点，都要先检查现有测试用例是否完整；如果现有测试没有覆盖改动影响面，必须同步补充。
2. 测试用例要覆盖正常路径、边界条件、异常路径和回归风险，不能只验证 happy path。
3. 修改动态数据源注册逻辑时，至少覆盖：
   - primary 数据源缺失、重复、命名冲突
   - 标准注入点与别名装配
   - 多个 `MongoTemplate` 同时存在时的查找与切换
4. 修改 `GXMongoTemplateContext` 或上下文透传逻辑时，至少覆盖：
   - 嵌套 `push/pop`
   - snapshot capture/replace
   - 异常恢复
   - 线程池或虚拟线程中的上下文不泄漏
5. 修改 `GXDynamicMongoDatabaseFactory` 时，至少覆盖：
   - 默认工厂选择
   - 基于上下文的数据源切换
   - session 绑定后禁止跨数据源切换
6. 修改 `GXMongoRepository`/`GXMongoServiceImpl` 时，至少覆盖：
   - 字段名转换和 `_id` 映射
   - 条件构造与操作符兼容性
   - 空条件保护
   - 分页、单字段查询、更新、删除、复制等行为
7. 如果某个功能必须在 Spring Boot 应用真实启动后才能验证，那么测试必须使用接近真实启动方式的集成测试，例如 `@SpringBootTest`、`ApplicationContextRunner` 或其他真实装配测试手段，而不是只做脱离容器的伪单元测试。
8. 涉及自动配置、Bean 注册、条件装配、配置绑定的改动，优先补 Spring 容器级测试，而不是只补纯方法级测试。

## 7. 变更前检查清单

1. 是否影响 `mongodb.datasource.*` 的配置结构、默认值或兼容性。
2. 是否影响 primary 数据源判定、标准注入点别名或 Bean 名保留规则。
3. 是否影响 `GXMongoTemplateContext` 的栈恢复、快照恢复或跨线程透传。
4. 是否影响 `GXDynamicMongoDatabaseFactory` 的动态路由和 session 绑定约束。
5. 是否影响 `id`/`_id` 映射、字段驼峰转下划线规则或条件操作符兼容性。
6. 是否引入了新的日志文案，且这些日志文案是否全部为 ASCII。
7. 是否删除了冗余注释，并为新增复杂逻辑补充了必要的 Javadoc 或示例。
8. 是否已经检查并补齐相关测试用例。

## 8. AGENTS.md 自动更新时机

出现以下任一情况时，应同步更新本文件：

1. 模块职责边界发生变化，例如从“仅提供 MongoDB 基础设施”扩展到新的能力域。
2. 动态数据源装配方式、默认 Bean 名、保留字、别名策略或 primary 规则发生变化。
3. `GXMongoTemplateContext` 的上下文传播模型、线程模型或恢复语义发生变化。
4. `GXDynamicMongoDatabaseFactory` 的路由规则、session 约束或事务相关行为发生变化。
5. `GXMongoRepository` 或 `GXMongoServiceImpl` 的公共行为、异常语义、字段映射规则、查询能力边界发生变化。
6. 测试策略发生变化，例如新增必须遵守的集成测试方式、测试基线或覆盖要求。
7. 团队新增了会持续影响后续开发的编码约束、注释规范、日志规范或提交规范。
8. 模块关键依赖、Spring Boot 版本基线、JDK 基线或启动装配方式发生重要变化。

## 9. 提交与评审建议

1. 涉及配置绑定、动态注册、上下文切换或统一查询行为的修改，PR 描述中应明确说明行为变化和兼容性影响。
2. 涉及日志调整时，提交前再次检查日志文本是否全部为 ASCII，并确认未暴露敏感配置。
3. 涉及测试调整时，评审重点应放在覆盖范围是否与改动风险匹配，而不是只看测试是否通过。
4. 如果改动会触发本文件第 8 节中的任一条件，应将 `AGENTS.md` 的更新与功能改动一并提交。
