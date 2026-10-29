# AGENTS.md - leaf-base-datasource

本文用于指导 `leaf-base-datasource` 模块维护。修改本模块时，优先保持多数据源、MyBatis-Plus 增强、数据权限、租户、基础 Repository/Service 和 SQL 构建行为稳定。

## 模块职责

`leaf-base-datasource` 负责：

1. 数据源、连接池和动态数据源上下文管理。
2. MyBatis-Plus 配置、分页、防全表更新、自动填充和 SQL 构建增强。
3. 数据权限、租户、数据源注解和异步上下文传递。
4. 基础 Repository/Service 支撑能力。

本模块不承载具体业务流程编排、业务表模型规则或业务 SQL 策略。

## 实现原则

1. 不要随意复杂化任何逻辑实现。能沿用现有 MyBatis-Plus、Spring 和本模块工具完成时，不要引入额外抽象、兜底分支、缓存、反射链路或生命周期钩子。
2. 遵循单一职责原则。禁止一个类型包揽数据源切换、SQL 构建、权限判断、租户处理、异步传播等多类职责；职责膨胀时优先收窄边界，而不是继续堆功能。
3. 不要随意改变公开类、接口、方法签名、泛型约束、配置键、异常语义、默认行为和 SQL 兼容语义。
4. 修改既有逻辑前，先确认旧行为的成功路径、失败路径、空值、边界输入和回归路径。修复缺陷时，必须补一个能复现旧问题并在新代码下通过的测试。
5. 只有逻辑确实被两个以上位置复用，或能明显降低重复复杂度时，才抽取新方法或新类。不要为了局部整理做无关重构。
6. 日志、异常消息和断言消息优先使用 ASCII 文本，避免运行环境、终端编码或日志采集链路出现乱码。

## 关键行为

1. `GXDynamicContextHolder` 必须保持栈语义，`push` 与 `poll` 必须成对使用。
2. `GXDataSourceAspect` 必须在 `finally` 中恢复数据源上下文，避免 `ThreadLocal` 泄漏。
3. `GXDataFilterAspect` 必须恢复旧的 `GXDataFilterInnerDto`，支持嵌套调用。
4. Druid filters、`BlockAttackInnerInterceptor` 和分页插件的默认启用行为不得随意移除。
5. 开启租户或数据权限时，缺少必要服务必须明确失败，不能静默降级为放行。
6. 自动填充字段 `createdBy`、`updatedBy`、`createdAt`、`updatedAt`、`tenantId` 的兼容行为不得破坏。
7. 异步监听任务必须传递数据源与数据权限上下文，继续使用现有 wrap 能力。
8. 批量更新、批量删除和原始 SQL 必须保留条件非空保护。
9. MyBatis 写操作监听必须委托统一发布组件：一次写操作对同一监听器只投递一次；新增切面禁止复制发布、去重与计数逻辑。
10. 同步监听回调在业务线程、事务提交前执行，其异常默认被隔离并计入失败计数；修改该语义必须同步更新开关默认值与开发手册。
11. 异步队列饱和必须显式拒绝并计入 `rejected`，拒绝日志必须携带 `operationId`；不得改为在调用方执行或静默丢弃；停机时必须记录未执行任务数。
12. 监听声明解析缓存必须有容量上限，无声明的写操作不得构造事件载荷或解析条件。
13. 监听回调内的写操作会再次触发同一投递链，投递深度上限（`GXMyBatisListenerEventPublisher.MAX_DELIVERY_DEPTH`）与跨任务深度传播不得移除或放开；超限必须计入 `failed` 并输出 `reason=recursion-limit` 的结构化告警。
14. `strict-snapshot` 的载荷扫描必须有界，超出预算必须以 `snapshotScanTruncated` 与 `degraded` 显式暴露，不得静默按"干净载荷"处理。
15. 载荷构建属于投递路径：条件解析、字段校验或事件快照失败必须计入 `failed` 并隔离，不得让已成功的写操作向调用方抛异常。事务后投递（异步或 `sync-after-commit`）的回调不会被业务调用方看到，其异常一律隔离。
16. 批量监听覆盖边界：只拦截通用服务层的 `saveBatch`/`saveOrUpdateBatch`；MyBatis-Plus 官方 `BaseMapper.insert(Collection)`、`updateById(Collection)`、`insertOrUpdate(Collection, ...)` 经 `BatchSqlSession` 直接执行语句，不产生事件。该缺口必须保留在开发手册的能力边界表中，不得悄悄删除说明，也不得声称已覆盖。
17. 批量载荷由切面一次构建后直接投递，不再二次快照；因此切面必须把实体自有的可变值（`Date`、`Calendar`、数组、集合、`Map`）复制为载荷私有副本，保证监听方修改载荷不影响业务实体。分离走查深度上限 32，超限必须置 `snapshotDegraded` 与 `UNSNAPSHOTABLE_VALUE`（计入 `degraded`），不得静默；也不得就地改写调用方传入的 `Dict`/`Map` 行（`Convert` 在这类入参下返回调用方实例）。

## 测试要求

1. 需要完整的单元测试。涉及行为变更时，测试必须覆盖成功、失败、异常、空值、边界和回归场景。
2. 涉及线程并发、`ThreadLocal`、异步上下文传播、SQL builder、SQL 拦截、连接池、缓存、租户或数据权限等关键功能点时，必须补充冒烟测试或集成测试。
3. 涉及性能关键节点时，至少要有可执行的冒烟验证，覆盖重复执行、批量输入、空输入和异常输入；无法自动化时，在交付说明中写清手工验证命令、样本规模和观察指标。
4. 涉及 Spring Boot 装配、切面、拦截器、配置加载、租户、数据权限或异步上下文传播时，必须补充接近真实运行形态的 Spring Boot 测试。
5. 重点覆盖数据源上下文、数据权限上下文、租户开关、分页、防全表更新、Druid 初始化失败清理、自动填充和 SQL builder 行为。

## 变更检查

提交或交付前确认：

1. 是否只修改了 `leaf-base-datasource` 模块；确需跨模块时，先说明原因和最小影响范围。
2. 是否影响 `@GXDataSource`、`@GXDataFilter`、租户、数据权限、分页、防全表更新或异步上下文传播。
3. 是否符合 MyBatis-Plus 多数据库兼容约定。
4. 是否引入了不必要的复杂分支、抽象层、缓存、反射或跨职责类型。
5. 是否补充并运行了与风险相匹配的单元测试、冒烟测试或集成测试。

## 推荐命令

```powershell
mvn -pl leaf-base-datasource -am test
```
