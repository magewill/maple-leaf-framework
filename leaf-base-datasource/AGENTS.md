# AGENTS.md - leaf-base-datasource

本文件用于指导 `leaf-base-datasource` 模块的后续迭代开发。修改本模块时，优先遵循当前代码结构和既有行为，避免无关重构。

## 1. 模块定位

`leaf-base-datasource` 是 Maple Leaf Framework 的数据源基础设施模块，只负责数据源、连接池、MyBatis-Plus 增强、数据权限、租户和基础 Repository/Service 支撑，不承载业务流程编排。

核心能力包括：

1. 多数据源动态路由：`@GXDataSource`、`GXDynamicContextHolder`、`GXDynamicDataSource`。
2. Druid 数据源构建与生命周期管理：`GXDynamicDataSourceFactory`、`GXDynamicDataSourceConfig`。
3. MyBatis-Plus 插件编排：分页、防全表更新、可选租户、可选数据权限。
4. 数据权限切面与 SQL 过滤上下文：`GXDataFilterAspect`、`GXDataFilterInterceptor`。
5. 审计字段自动填充：`GXAutoFillMetaObjectHandler`。
6. Repository/Service 基类抽象：`GXMyBatisRepository`、`GXMyBatisBaseServiceImpl`。
7. MyBatis 事件异步执行器与上下文透传：数据源上下文和数据权限上下文。

## 2. 技术基线

| 技术 | 当前版本/约束 |
| --- | --- |
| JDK | 21 |
| Spring Boot | 4.x |
| MyBatis-Plus | `3.5.16` |
| Druid | `1.2.28` |
| MySQL Connector/J | `9.7.0` |
| P6Spy | `3.9.1` |

## 3. 关键文件

关键配置文件：

1. `src/main/java/cn/maple/core/datasource/config/GXDynamicDataSourceConfig.java`
2. `src/main/java/cn/maple/core/datasource/config/GXMyBatisPlusConfig.java`
3. `src/main/java/cn/maple/core/datasource/config/GXMyBatisAsyncListenerExecutorConfig.java`

关键切面与拦截器：

1. `src/main/java/cn/maple/core/datasource/aspect/GXDataSourceAspect.java`
2. `src/main/java/cn/maple/core/datasource/aspect/GXDataFilterAspect.java`
3. `src/main/java/cn/maple/core/datasource/interceptor/GXDataFilterInterceptor.java`

关键基础抽象：

1. `src/main/java/cn/maple/core/datasource/repository/GXMyBatisRepository.java`
2. `src/main/java/cn/maple/core/datasource/service/impl/GXMyBatisBaseServiceImpl.java`
3. `src/main/java/cn/maple/core/datasource/handler/GXAutoFillMetaObjectHandler.java`

配置源：

1. 本地配置：`src/main/java/cn/maple/core/datasource/properties/local/GXLocalDynamicDataSourceProperties.java`
2. Nacos 配置：`src/main/java/cn/maple/core/datasource/properties/nacos/GXNacosDynamicDataSourceProperties.java`

## 4. 强制行为约束

1. `GXDynamicContextHolder` 必须维持栈语义，`push` 与 `poll` 必须成对使用，禁止改成单值覆盖。
2. `GXDataSourceAspect` 必须在 `finally` 中恢复数据源上下文，禁止造成 ThreadLocal 泄漏。
3. `GXDataFilterAspect` 必须恢复旧的 `GXDataFilterInnerDto`，支持嵌套调用场景。
4. Druid filters 必须至少包含 `stat,wall,slf4j`，不得删除 `GXDynamicDataSourceFactory` 中的兜底逻辑。
5. `GXMyBatisPlusConfig` 中 `BlockAttackInnerInterceptor` 和分页插件的默认启用行为不得移除。
6. `maple.framework.enable.tenant=true` 时，缺失 `GXTenantIdService` 必须抛出异常。
7. `maple.framework.enable.data-permission=true` 时，缺失 `DataPermissionHandler` 必须抛出异常。
8. 自动填充字段 `createdBy`、`updatedBy`、`createdAt`、`updatedAt`、`tenantId` 的兼容行为不得破坏。
9. 异步监听任务必须透传数据源与数据权限上下文，即继续使用 `GXDynamicContextHolder.wrap` 和 `GXDataFilterThreadLocalUtils.wrap`。
10. 所有日志输出内容必须使用 ASCII 字符，新增或修改日志时禁止写入非 ASCII 日志文案。

## 5. 代码演进规则

1. 优先沿用本模块既有抽象和命名风格，不为局部需求引入新的架构层。
2. 不要随意将已存在代码抽取成独立方法或类；仅当抽取后的方法或类能被两个以上调用点复用，且能明显提升可维护性时才允许抽取。
3. 通用能力优先下沉到 `GXMyBatisRepository` 或 `GXMyBatisBaseServiceImpl`，不要散落在业务模块中。
4. 涉及批量更新、批量删除或原始 SQL 的改动，必须保留条件非空保护。
5. 新增配置项必须有默认值或清晰的失败语义，避免启动时出现隐式空值行为。
6. Spring 注入优先使用 `jakarta.annotation.Resource`；仅在集合注入、泛型注入或必须配合 Spring 语义时使用 `@Autowired`。
7. 异常类型优先使用框架统一异常，例如 `GXBusinessException`，避免裸抛 `RuntimeException`。

## 6. 扩展规范

新增数据源能力时：

1. 优先扩展 `GXDataSourceProperties` 与 `GXDynamicDataSourceFactory`。
2. 所有连接池参数必须配置化，禁止硬编码环境敏感值。
3. 初始化失败时必须关闭已创建的数据源，保持当前失败清理策略。

新增 MyBatis 插件能力时：

1. 统一在 `GXMyBatisPlusConfig` 注册。
2. 必须评估插件顺序与 `GXDataFilterInterceptor`、租户插件、分页插件之间的影响。
3. 默认应提供配置开关，避免新增插件直接改变全局 SQL 行为。

新增 Repository/Service 能力时：

1. 保持泛型签名兼容，避免破坏下游模块编译。
2. 需要返回 DTO/Dict 的转换逻辑应沿用 `GXCommonUtils` 等现有工具。
3. 对外可见方法的异常语义必须明确，不能吞掉关键失败。

## 7. 配置约定

1. 本地模式读取 `classpath:/${spring.profiles.active:dev}/datasource.yml`。
2. Nacos 模式读取 `dataId=datasource.yml`，group 和 namespace 按配置解析。
3. 模块默认排除 `DataSourceAutoConfiguration`，见 `src/main/resources/application.yml`，禁止随意移除。
4. 使用 Spring Cloud Nacos 配置时，需要注意 `leaf-base-nacos` 中 Nacos 配置依赖的排除关系。

## 8. 测试要求

任何涉及功能行为的改动，都必须同步检查测试用例是否完整。

单元测试优先覆盖：

1. 数据源上下文 `push`、`poll`、嵌套恢复和异常恢复。
2. `@GXDataSource`、`@GXDataFilter` 切面命中与注解缓存逻辑。
3. Druid 工厂参数解析、filters 兜底和初始化失败清理。
4. 自动填充在 insert/update 下的字段行为。
5. 数据权限和租户开关的异常分支。

集成测试优先覆盖：

1. 本地配置与 Nacos 配置的装配分支。
2. 租户开关与数据权限开关在有/无依赖 Bean 时的启动行为。
3. 异步监听任务中的数据源与数据权限上下文继承。
4. 需要 Spring Boot 应用运行态才能验证的功能，必须使用真实 Spring Boot 启动上下文测试，例如 `@SpringBootTest`。

## 9. 变更前检查清单

1. 是否影响 `@GXDataSource` 切库与嵌套调用恢复？
2. 是否影响 `@GXDataFilter` 的上下文设置与恢复？
3. 是否影响租户或数据权限开关的必需依赖校验？
4. 是否影响 Druid 连接池初始化失败时的清理流程？
5. 是否影响分页、防全表更新、乐观锁等插件顺序？
6. 是否影响异步监听任务的上下文透传？
7. 是否引入破坏性 API 变更，例如泛型、方法签名或异常语义变化？
8. 是否已经补充或更新对应测试用例？

## 10. AGENTS.md 更新时机

当出现以下情况时，应同步更新本文件：

1. 模块职责边界发生变化。
2. 核心配置、切面、拦截器或上下文传递机制发生行为变化。
3. 测试策略、提交流程或开发红线发生变化。
4. 新增跨团队约束，且会影响后续迭代开发。
5. 依赖版本、基础技术栈或启动装配方式发生重要变化。

## 11. 提交规范建议

1. 对配置、切面、拦截器的修改，需要在 PR 描述中明确行为变化点。
2. 涉及默认值变更时，必须说明向后兼容影响与回滚方案。
3. 任何可能导致全局 SQL 行为改变的改动，必须附最小复现用例或测试说明。
4. 提交前确认新增或修改的日志文案均为 ASCII。
