# AGENTS.md - leaf-base-elasticsearch

本文用于指导 `leaf-base-elasticsearch` 模块的后续维护。修改本模块时，优先保持 Elasticsearch 多数据源装配、动态模板切换、仓储默认行为、查询条件转换和 Spring Data Elasticsearch 兼容性稳定。

## 模块职责

`leaf-base-elasticsearch` 是框架级 Elasticsearch 基础能力模块，负责：

1. 从已加载到 Spring `Environment` 的 `elasticsearch.datasource.*` 配置中装配多数据源。
2. 注册 `ElasticsearchClient`、`ElasticsearchTemplate`、`ElasticsearchOperations`、转换器和 mapping context。
3. 通过 `GXElasticsearchTemplateContext` 支持线程内动态切换 `ElasticsearchTemplate`。
4. 提供 `GXElasticsearchDao`、`GXElasticsearchRepository`、`GXElasticsearchService` 的通用 CRUD、分页、条件查询、批量更新和删除能力。
5. 将框架内的 `GXCondition`、`GXUpdateField`、`GXBaseQueryParamInnerDto` 转换为 Spring Data Elasticsearch 查询对象和更新脚本。

本模块不承载具体业务索引设计、业务 DSL 编排、业务搜索策略或业务规则。

## 实现原则

1. 不要随意复杂化任何逻辑实现。优先采用当前代码已经使用的模式；能用直接、清晰、可测试的实现解决时，不要引入额外抽象、兜底分支、反射链路、缓存或生命周期钩子。
2. 不要把职责放错位置。配置加载由 `GXLocalElasticsearchProperties` 和 `GXNacosElasticsearchProperties` 负责；`GXElasticsearchBeanDefinitionRegistryPostProcessor` 默认配置已经进入 `Environment`，如果配置缺失应直接失败并中止启动。
3. 不要随意改变对外契约，包括公开类名、方法签名、bean 名称、配置属性名、异常语义、默认模板名称和序列化行为。
4. 修改既有逻辑前，先确认旧行为的成功路径、失败路径、空值、边界输入和兼容语义。修复缺陷时，应补一个能复现旧问题并在新代码下通过的回归测试。
5. 只在确实降低重复复杂度、或至少被两个位置复用时抽取新方法或新类。不要为了“看起来更整洁”做无关重构。
6. 日志、异常消息和断言消息优先使用 ASCII 文本，避免运行环境、终端编码或日志采集链路出现乱码。

## 关键行为

1. 数据源配置必须来自 `elasticsearch.datasource.*`，并且必须且只能有一个 `primary=true` 的主数据源。
2. 主数据源必须暴露 `elasticsearchClient`、`elasticsearchTemplate`、`primaryElasticsearchTemplate`、`elasticsearchOperations` 等框架默认 bean 名或别名。
3. 注入 `ElasticsearchOperations` 时应优先获得动态代理对象，而不是固定绑定某一个 `ElasticsearchTemplate`。
4. `GXElasticsearchDao#getElasticsearchTemplate()` 默认回退到 `primaryElasticsearchTemplate`；如果 `GXElasticsearchTemplateContext` 中存在模板名，则优先使用上下文模板。
5. `GXElasticsearchTemplateContext` 使用 `ThreadLocal` 保存当前模板名；任何改动都必须确认上下文恢复和清理路径，避免线程复用污染。
6. `GXElasticsearchRepository#getTableName()` 依赖实体类上的 `@Document(indexName=...)` 推导索引名，未配置时视为错误。
7. 条件查询当前主要走 `CriteriaQuery` 路径，支持的操作符必须与 `GXEsCriteriaMethodMappingConstant` 保持一致。
8. `updateFieldByCondition` 使用 painless 脚本批量更新字段；脚本内容、参数结构和字段名校验直接影响性能与安全，不要随意改动。
9. `GXElasticsearchServiceImpl` 中的 union 查询和 mapper 方法查询当前明确不支持，除非有完整设计和回归测试，否则必须继续抛出异常。

## 测试要求

1. 修改普通行为时，至少运行模块级测试：`mvn -pl leaf-base-elasticsearch -am test`。
2. 涉及线程并发、`ThreadLocal`、动态模板切换、动态代理、批量操作、painless 脚本、缓存、bean 注册顺序、配置绑定、连接创建等关键功能点时，必须补充对应的冒烟测试或集成测试。
3. 涉及性能关键节点时，至少要有可执行的冒烟验证，覆盖重复执行、批量输入、空输入和异常输入；如果无法自动化，需在交付说明中写清手工验证命令、样本规模和观察指标。
4. 改动 `GXElasticsearchBeanDefinitionRegistryPostProcessor` 时，重点覆盖单主数据源、多数据源、缺少主数据源、多个主数据源、空配置、URI 为空、动态 `ElasticsearchOperations` 注入、`ElasticsearchCustomConversions` 复用等路径。
5. 改动 `GXElasticsearchTemplateContext`、`GXDynamicElasticsearchOperations`、`GXElasticsearchRepositoryFactoryBeanPostProcessor` 时，重点覆盖模板上下文设置、恢复、清理、延后执行保持上下文，以及代理分支仍指向正确模板。
6. 改动 `GXElasticsearchDao`、`GXElasticsearchRepository`、`GXElasticsearchServiceImpl` 时，重点覆盖 CRUD、分页、排序、条件操作符映射、`between` 值个数、`in/not in` 集合校验、脚本更新、软删除和默认索引名回填。

## 变更检查

提交或交付前确认：

1. 是否只修改了 `leaf-base-elasticsearch` 模块内的文件；确需跨模块时，先说明原因和最小影响范围。
2. 是否影响默认 bean 名、主模板别名、动态 `ElasticsearchOperations` 或 repository 注入语义。
3. 是否引入了不必要的复杂分支、兜底逻辑、抽象层、缓存或反射。
4. 是否改变配置加载职责，特别是 local/nacos properties 与 bean definition registry post processor 的边界。
5. 是否补充并运行了与风险相匹配的测试。

## 推荐命令

```powershell
mvn -pl leaf-base-elasticsearch -am test
```
