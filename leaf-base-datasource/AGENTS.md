# AGENTS.md - leaf-base-datasource

## 1. 模块定位
- 模块名称：`leaf-base-datasource`
- 职责边界：提供数据源基础设施能力，不承载业务流程编排。
- 核心能力：
1. 多数据源动态路由（`@GXDataSource` + `GXDynamicContextHolder` + `GXDynamicDataSource`）
2. Druid 数据源构建与生命周期管理（`GXDynamicDataSourceFactory` / `GXDynamicDataSourceConfig`）
3. MyBatis-Plus 插件编排（分页、防全表更新、可选租户、可选数据权限）
4. 数据权限切面与 SQL 过滤上下文（`GXDataFilterAspect` / `GXDataFilterInterceptor`）
5. 审计字段自动填充（`GXAutoFillMetaObjectHandler`）
6. Repository/Service 基类抽象（`GXMyBatisRepository` / `GXMyBatisBaseServiceImpl`）
7. MyBatis 事件异步执行器与上下文透传（数据源上下文 + 数据权限上下文）

## 2. 技术基线
- JDK：21
- Spring Boot：4.x
- MyBatis-Plus：`3.5.16`
- Druid：`1.2.28`
- MySQL Connector/J：`9.7.0`
- P6Spy：`3.9.1`

## 3. 目录与关键实现
- 关键配置：
1. `src/main/java/cn/maple/core/datasource/config/GXDynamicDataSourceConfig.java`
2. `src/main/java/cn/maple/core/datasource/config/GXMyBatisPlusConfig.java`
3. `src/main/java/cn/maple/core/datasource/config/GXMyBatisAsyncListenerExecutorConfig.java`
- 关键切面/拦截器：
1. `aspect/GXDataSourceAspect.java`
2. `aspect/GXDataFilterAspect.java`
3. `interceptor/GXDataFilterInterceptor.java`
- 关键抽象：
1. `repository/GXMyBatisRepository.java`
2. `service/impl/GXMyBatisBaseServiceImpl.java`
3. `handler/GXAutoFillMetaObjectHandler.java`
- 配置源：
1. 本地：`properties/local/GXLocalDynamicDataSourceProperties.java`
2. Nacos：`properties/nacos/GXNacosDynamicDataSourceProperties.java`

## 4. 不可破坏的行为约束（迭代红线）
1. `GXDynamicContextHolder` 必须维持栈语义（push/poll 成对），禁止改成单值覆盖。
2. `GXDataSourceAspect` 的清理逻辑必须在 `finally` 中执行，禁止提前 return 导致上下文泄漏。
3. `GXDataFilterAspect` 必须恢复旧的 `GXDataFilterInnerDto`，禁止只清空不恢复嵌套上下文。
4. Druid 过滤器必须至少包含 `stat,wall,slf4j`（工厂已有兜底），禁止删除兜底逻辑。
5. `GXMyBatisPlusConfig` 中 `BlockAttackInnerInterceptor`、分页插件默认启用行为不得移除。
6. 当 `maple.framework.enable.tenant=true` 时，缺失 `GXTenantIdService` 必须抛错，禁止静默降级。
7. 当 `maple.framework.enable.data-permission=true` 时，缺失 `DataPermissionHandler` 必须抛错，禁止静默降级。
8. 自动填充字段行为（`createdBy/updatedBy/createdAt/updatedAt/tenantId`）不得破坏兼容性。
9. 异步监听任务必须透传数据源与数据权限上下文（`GXDynamicContextHolder.wrap` + `GXDataFilterThreadLocalUtils.wrap`）。

## 5. 扩展规范
### 5.1 新增数据源相关能力
1. 优先扩展 `GXDataSourceProperties` 与 `GXDynamicDataSourceFactory`，避免在业务层直接创建连接池。
2. 所有可调参数必须配置化，禁止硬编码环境敏感值。
3. 初始化失败必须可回滚（关闭已创建数据源），保持当前失败清理策略。

### 5.2 新增 MyBatis 插件能力
1. 统一在 `GXMyBatisPlusConfig` 注册，按拦截顺序评估与已有插件冲突。
2. 新插件默认应可开关化（环境变量/配置开关），避免强绑定上线。
3. 若引入 SQL 改写，需验证与 `GXDataFilterInterceptor`、租户插件共存行为。

### 5.3 新增基础 Service/Repository 能力
1. 通用能力优先下沉到 `GXMyBatisRepository` 或 `GXMyBatisBaseServiceImpl`。
2. 涉及批量更新/删除时必须保留“条件非空”保护，禁止无条件写操作。
3. 保持泛型签名兼容，避免破坏下游模块编译。

## 6. 代码风格约定（本模块）
1. Spring 注入优先使用 `jakarta.annotation.Resource`；仅在必要场景使用 `@Autowired`（当前存量允许渐进治理）。
2. 异常类型优先使用框架统一异常（如 `GXBusinessException`），避免抛裸 `RuntimeException`。
3. 日志要求：
- 初始化/关键开关：`info`
- 诊断细节：`debug/trace`
- 可恢复异常：`warn`
- 功能失败：`error`
4. 新增公共类需保持无状态或线程安全。

## 7. 配置约定
1. 本地模式读取 `classpath:/${spring.profiles.active:dev}/datasource.yml`。
2. Nacos 模式读取 `dataId=datasource.yml`，group/namespace 按配置解析。
3. 模块默认排除 `DataSourceAutoConfiguration`（见 `src/main/resources/application.yml`），禁止随意移除。

## 8. 变更前检查清单
1. 是否影响 `@GXDataSource` 切库与嵌套调用恢复？
2. 是否影响 `@GXDataFilter` 的上下文设置与恢复？
3. 是否影响租户/数据权限开关的必需依赖校验？
4. 是否影响 Druid 连接池初始化失败时的清理流程？
5. 是否影响分页、防全表更新、乐观锁等插件顺序？
6. 是否影响异步监听任务的上下文透传？
7. 是否引入破坏性 API 变更（泛型、方法签名、异常语义）？

## 9. 建议测试策略（提交前）
- 单元测试优先覆盖：
1. 数据源上下文 push/poll/嵌套恢复
2. 切面命中与注解缓存逻辑
3. 过滤器 SQL 注入基础防护分支
4. Druid 工厂参数解析与 filters 兜底
5. 自动填充在 insert/update 下的字段行为
- 集成测试优先覆盖：
1. 本地配置 vs Nacos 配置的装配分支
2. 租户开关与数据权限开关在有/无依赖 Bean 时的行为
3. 异步监听任务中的数据源与数据权限上下文继承

## 10. 提交规范建议
1. 对配置、切面、拦截器的修改需在 PR 描述中明确“行为变化点”。
2. 涉及默认值变更时，必须给出向后兼容说明与回滚方案。
3. 任何可能导致全局 SQL 行为改变的改动，必须附最小复现用例或测试说明。
