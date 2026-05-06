# AGENTS.md - leaf-base-elasticsearch

本文档用于指导 `leaf-base-elasticsearch` 模块后续迭代开发。修改本模块时，优先保持 Elasticsearch 多数据源装配、动态模板切换、仓储默认行为、查询条件转换和 Spring Data Elasticsearch 兼容性稳定，避免无关重构。

## 1. 模块定位

`leaf-base-elasticsearch` 是 Maple Leaf Framework 的 Elasticsearch 基础能力模块，负责：

1. Elasticsearch 多数据源配置解析与 Bean 注册，支持本地配置和 Nacos 配置来源。
2. `ElasticsearchClient`、`ElasticsearchTemplate`、`ElasticsearchOperations`、转换基础设施的统一装配。
3. 基于 `GXElasticsearchTemplateContext` 的动态模板切换能力，支持按线程在多个 `ElasticsearchTemplate` 之间路由。
4. `GXElasticsearchDao`、`GXElasticsearchRepository`、`GXElasticsearchService` 这一套基础仓储与服务抽象，提供保存、查询、分页、更新、删除、软删除、存在性校验等通用能力。
5. 将框架内部的 `GXCondition`、`GXUpdateField`、`GXBaseQueryParamInnerDto` 转换为 Spring Data Elasticsearch 查询对象与更新脚本。
6. 对 Spring Data `ElasticsearchRepositoryFactoryBean` 的后处理，确保 Repository 代理和框架默认方法都走同一套动态 `ElasticsearchOperations`。

本模块不承载具体业务索引设计、业务聚合查询 DSL 编排、业务侧搜索策略和业务域规则。

## 2. 目录职责

1. `config`：多数据源 Bean 注册、主模板别名处理、动态 `ElasticsearchOperations` 装配、Repository 后处理器注册。
2. `properties`：Elasticsearch 数据源属性模型定义，以及 `local`、`nacos` 配置来源抽象。
3. `support`：模板上下文切换、动态代理、Repository 工厂后处理等运行支撑组件。
4. `dao`：基于 Spring Data Elasticsearch 的底层默认实现，负责 CRUD、条件构造、分页、排序、批量删除、脚本更新。
5. `repository`：面向框架仓储层的适配封装，负责索引名推导、存在性校验、软删除字段转换等。
6. `service` / `service.impl`：面向业务模块的通用 Elasticsearch 服务抽象与默认实现。
7. `model`：Elasticsearch 实体基类。
8. `constant`：查询操作符与 Criteria 方法映射常量。

## 3. 模块功能基线

开发前必须理解并保持以下行为基线稳定：

1. 数据源配置从 `elasticsearch.datasource.*` 读取，且必须且只能存在一个 `primary=true` 的主数据源。
2. 主数据源会暴露标准 Bean 名称和别名，包括 `elasticsearchClient`、`elasticsearchTemplate`、`primaryElasticsearchTemplate`、`elasticsearchOperations`。
3. 注入 `ElasticsearchOperations` 时应优先获得动态代理对象，而不是固定绑定某一个 `ElasticsearchTemplate`。
4. `GXElasticsearchTemplateContext` 使用 `ThreadLocal` 保存当前模板名；`useElasticsearchTemplate`、`wrapElasticsearchTemplate` 负责在当前线程或延后执行时保持模板上下文。
5. `GXElasticsearchDao#getElasticsearchTemplate()` 默认回落到 `primaryElasticsearchTemplate`，若上下文存在模板名则优先使用上下文模板。
6. `GXElasticsearchRepository#getTableName()` 依赖实体类上的 `@Document(indexName=...)` 推导索引名；未配置时视为错误。
7. 条件查询当前主要通过 `CriteriaQuery` 路径构造，支持 `=`、`!=`、`in`、`not in`、`>`、`<`、`>=`、`<=`、`like`、`between`、`is`、`is not` 等操作。
8. `updateFieldByCondition` 通过 painless 脚本做批量字段更新；`deleteSoftCondition` 本质上是将 `extraData` 和补充字段转成 `GXUpdateField` 后走更新流程。
9. `GXElasticsearchServiceImpl` 中的 union 查询、Mapper 方法查询当前明确不支持，必须维持抛出异常这一语义，除非有完整设计和回归测试支撑。

## 4. 强制开发规则

1. 所有 `log` 日志内容必须使用 ASCII 字符，禁止新增或保留非 ASCII 日志文案、异常消息字符串。
2. 涉及改动的点，必须同步检查对应测试用例是否完整覆盖；覆盖不足时必须补充测试。
3. 不要随意把已存在代码抽取成单独方法或类；仅当抽取后的逻辑能被两个及以上位置复用，或能显著降低重复复杂度时才允许抽取。
4. 删除方法、类、变量上的冗余注释，避免注释重复描述代码表面含义。
5. 在复杂方法、复杂类、对外扩展点上，适当补充 Javadoc，并在必要时加入简短使用示例。
6. 任何涉及模板路由、主模板别名、Bean 替换顺序、Repository 注入方式的改动，都必须优先评估兼容性影响。
7. 任何涉及查询条件到 Elasticsearch `Criteria` 的映射、分页规则、排序规则、脚本更新语义的改动，都必须补充行为级测试。
8. 任何涉及 `ThreadLocal` 上下文传递的改动，都必须确认存在清理路径，避免模板串用和线程复用污染。

## 5. 测试要求

1. 任何功能改动都必须先检查现有测试是否覆盖成功路径、失败路径、异常路径和边界条件。
2. 测试用例要覆盖全面；如果功能依赖 Spring 容器、自动配置、BeanDefinitionRegistryPostProcessor、BeanPostProcessor、`ElasticsearchTemplate` 注入、应用启动过程或多数据源装配，测试必须使用接近真实运行形态的 Spring Boot 或 Spring 上下文启动测试，不能只做纯单元测试。
3. 变更 `GXElasticsearchBeanDefinitionRegistryPostProcessor` 时，至少覆盖：
   - 单主数据源和多数据源注册成功路径。
   - 缺少主数据源、存在多个主数据源、配置为空、URI 为空等异常路径。
   - `elasticsearchOperations` 动态代理注入优先级。
   - `elasticsearchCustomConversions` 和 `SimpleTypeHolder` 的复用与替换逻辑。
4. 变更 `GXElasticsearchTemplateContext`、`GXDynamicElasticsearchOperations`、`GXElasticsearchRepositoryFactoryBeanPostProcessor` 时，至少覆盖：
   - 模板上下文设置、恢复、清理。
   - `wrap` 后异步或延后执行时上下文保持。
   - `withRefreshPolicy`、`withRouting`、`indexOps` 等代理分支仍指向正确模板。
   - Repository 代理和直接注入的 `ElasticsearchOperations` 行为一致。
5. 变更 `GXElasticsearchDao`、`GXElasticsearchRepository`、`GXElasticsearchServiceImpl` 时，至少覆盖：
   - 正常 CRUD 与分页路径。
   - 空条件、空列、空索引名、空目标类型、默认索引名回填等边界输入。
   - 条件操作符映射、排序非法值忽略、`between` 值个数校验、`in/not in` 集合校验。
   - `deleteSoftCondition`、`updateFieldByCondition`、`countByCondition`、`findSingleFieldByCondition` 等关键分支。
   - 当前不支持的 union 查询和 Mapper 查询异常语义。
6. 修复缺陷时，必须增加一个能复现旧问题且在新代码下通过的回归测试。
7. 提交前至少保证模块级测试命令可执行：`mvn -pl leaf-base-elasticsearch -am test`。

## 6. 变更前检查清单

1. 是否影响 `primaryElasticsearchTemplate`、`elasticsearchTemplate`、`elasticsearchOperations` 的装配语义。
2. 是否影响动态模板切换的线程隔离、上下文回退和清理逻辑。
3. 是否影响实体 `@Document(indexName)` 的索引名推导规则。
4. 是否影响条件操作符到 `Criteria` 的映射关系，或分页、排序、总数统计行为。
5. 是否影响脚本更新、软删除、批量删除的执行语义。
6. 是否新增或保留了非 ASCII 日志文本。
7. 是否引入了不必要的方法/类抽取。
8. 是否清理了冗余注释，并为复杂逻辑补齐了必要 Javadoc 与示例。
9. 是否已经补充并运行了与改动风险相匹配的测试。

## 7. AGENTS.md 自动触发更新时机

出现以下任一情况时，必须在同一 PR 或提交中同步更新本文件：

1. 模块职责边界变化，例如新增或移除了核心 Elasticsearch 能力。
2. 目录结构变化，例如新增关键包、迁移核心类、删除关键组件。
3. 多数据源配置模型、配置来源、主数据源判定规则发生变化。
4. 主模板别名、动态 `ElasticsearchOperations` 注入规则、Repository 代理注入方式发生变化。
5. 查询条件支持范围、排序分页规则、更新脚本策略、软删除语义发生变化。
6. `GXElasticsearchTemplateContext` 的上下文传递或清理规则发生变化。
7. 测试策略发生变化，例如新增必须的 Spring Boot 集成测试形态、模块最低测试门槛调整。
8. 团队新增、删除或调整了需要长期遵守的开发规则，例如日志、测试、注释、Javadoc、重构约束。

如果本次代码变更不触发以上条件，则可以不更新本文件。

## 8. 提交说明建议

1. 涉及多数据源装配、主模板切换、动态代理注入的变更，PR 描述必须说明行为变化和兼容性影响。
2. 涉及条件映射、分页排序、脚本更新、软删除语义的变更，PR 描述应说明旧行为、新行为和风险点。
3. 提交前确认新增或修改的日志文本全部为 ASCII。
4. 提交前确认测试已覆盖改动点，并在 PR 中说明关键验证结果。

## 9. 推荐执行命令

```powershell
mvn -pl leaf-base-elasticsearch -am test
```
