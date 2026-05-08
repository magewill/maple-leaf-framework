# AGENTS.md - leaf-base-elasticsearch

本文档用于指导 `leaf-base-elasticsearch` 模块后续迭代开发。修改本模块时，只处理当前 Maven 模块范围内的文件，优先保持 Elasticsearch 多数据源装配、动态模板切换、仓储默认行为、查询条件转换和 Spring Data Elasticsearch 兼容性稳定，避免无关重构。

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

### 4.1 修改范围

1. 每次修改都只能修改 `leaf-base-elasticsearch` 当前 Maven 模块内的文件，不得修改其它 Maven 模块。
2. 如发现问题根因位于其它模块，只能在本模块内做兼容处理或记录风险，不得顺手跨模块修改。
3. 不要引入与本模块职责无关的业务索引、业务 DSL、业务规则或业务搜索策略。

### 4.2 兼容性与行为稳定

1. 现有代码已经在生产环境使用，不得随意更改对外接口规范、公开类名、方法签名、Bean 名称、配置属性名、异常语义和默认行为。
2. 在确保原有功能正确性的前提下进行完善；修改已存在逻辑时，不得改变现有逻辑的正确性和兼容性。
3. 任何涉及模板路由、主模板别名、Bean 替换顺序、Repository 注入方式的改动，都必须优先评估兼容性影响。
4. 任何涉及查询条件到 Elasticsearch `Criteria` 的映射、分页规则、排序规则、脚本更新语义的改动，都必须补充行为级测试。
5. 任何涉及 `ThreadLocal` 上下文传递的改动，都必须确认存在清理路径，避免模板串用和线程复用污染。

### 4.3 性能与并发

1. 本模块作为基础框架，性能、线程安全性和资源释放路径非常重要，禁止引入明显的全局锁竞争、重复反射、重复 Bean 查找或无界缓存。
2. 涉及缓存、静态状态、`ThreadLocal`、异步执行、批量操作、脚本更新和动态代理时，必须评估并发访问下的正确性。
3. 在线程池与虚拟线程都能满足需求的场景中，优先使用虚拟线程；若继续使用线程池，必须有明确原因，例如复用现有执行器、限制并发资源或兼容当前运行环境。

### 4.4 日志与异常文本

1. 所有 `log` 日志内容必须使用 ASCII 字符，禁止新增或保留非 ASCII 日志文案。
2. 新增异常消息、断言消息和测试失败消息也应优先使用 ASCII，除非对外协议已经明确要求中文文本。

### 4.5 重构约束

1. 不要随意把已存在代码抽取成单独方法或类。
2. 仅当抽取后的逻辑能被两个及以上位置复用，或能显著降低重复复杂度时，才允许抽取方法或类。
3. 不做与当前需求无关的格式化、重命名、包迁移、抽象层新增或风格调整。

### 4.6 注释与 Javadoc

1. 删除方法、类、变量上的冗余注释，避免注释重复描述代码表面含义。
2. 清理 Javadoc 的冗余信息，只保留必要文档。
3. 在复杂方法、复杂类、对外扩展点、容易误用的上下文切换 API 上，适当补充 Javadoc。
4. 如果使用方式比较复杂，应在 Javadoc 中补充简短使用示例，便于使用者参考。

## 5. 测试要求

1. 严格遵循测试驱动最佳实践：先明确失败用例或行为断言，再实现代码，最后通过回归测试确认行为稳定。
2. 涉及改动的点，必须同步检查对应测试用例是否完整覆盖；覆盖不足时必须补充测试。
3. 任何功能改动都必须检查现有测试是否覆盖成功路径、失败路径、异常路径和边界条件。
4. 测试用例要覆盖全面；如果功能依赖 Spring Boot 应用启动后的真实运行形态，例如 Spring 容器、自动配置、BeanDefinitionRegistryPostProcessor、BeanPostProcessor、`ElasticsearchTemplate` 注入、应用启动过程或多数据源装配，测试必须模拟真实 Spring Boot 应用启动之后再测试，不能只做纯单元测试。
5. 变更 `GXElasticsearchBeanDefinitionRegistryPostProcessor` 时，至少覆盖：
   - 单主数据源和多数据源注册成功路径。
   - 缺少主数据源、存在多个主数据源、配置为空、URI 为空等异常路径。
   - `elasticsearchOperations` 动态代理注入优先级。
   - `elasticsearchCustomConversions` 和 `SimpleTypeHolder` 的复用与替换逻辑。
6. 变更 `GXElasticsearchTemplateContext`、`GXDynamicElasticsearchOperations`、`GXElasticsearchRepositoryFactoryBeanPostProcessor` 时，至少覆盖：
   - 模板上下文设置、恢复、清理。
   - `wrap` 后异步或延后执行时上下文保持。
   - `withRefreshPolicy`、`withRouting`、`indexOps` 等代理分支仍指向正确模板。
   - Repository 代理和直接注入的 `ElasticsearchOperations` 行为一致。
7. 变更 `GXElasticsearchDao`、`GXElasticsearchRepository`、`GXElasticsearchServiceImpl` 时，至少覆盖：
   - 正常 CRUD 与分页路径。
   - 空条件、空列、空索引名、空目标类型、默认索引名回填等边界输入。
   - 条件操作符映射、排序非法值忽略、`between` 值个数校验、`in/not in` 集合校验。
   - `deleteSoftCondition`、`updateFieldByCondition`、`countByCondition`、`findSingleFieldByCondition` 等关键分支。
   - 当前不支持的 union 查询和 Mapper 查询异常语义。
8. 修复缺陷时，必须增加一个能复现旧问题且在新代码下通过的回归测试。
9. 提交前至少保证模块级测试命令可执行：`mvn -pl leaf-base-elasticsearch -am test`。

## 6. 变更前检查清单

1. 是否只修改了 `leaf-base-elasticsearch` 当前 Maven 模块。
2. 是否影响 `primaryElasticsearchTemplate`、`elasticsearchTemplate`、`elasticsearchOperations` 的装配语义。
3. 是否影响动态模板切换的线程隔离、上下文回退和清理逻辑。
4. 是否影响实体 `@Document(indexName)` 的索引名推导规则。
5. 是否影响条件操作符到 `Criteria` 的映射关系，或分页、排序、总数统计行为。
6. 是否影响脚本更新、软删除、批量删除的执行语义。
7. 是否改变了任何对外接口规范、配置属性、Bean 名称、异常语义或默认行为。
8. 是否新增或保留了非 ASCII 日志文本。
9. 是否引入了不必要的方法/类抽取。
10. 是否评估了性能、线程安全性和资源释放路径。
11. 是否清理了冗余注释，并为复杂逻辑补齐了必要 Javadoc 与示例。
12. 是否已经补充并运行了与改动风险相匹配的测试。

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
9. 对外接口规范、兼容性要求、性能要求、线程安全要求或虚拟线程使用策略发生变化。

如果本次代码变更不触发以上条件，则可以不更新本文件。

## 8. 提交说明建议

1. 涉及多数据源装配、主模板切换、动态代理注入的变更，PR 描述必须说明行为变化和兼容性影响。
2. 涉及条件映射、分页排序、脚本更新、软删除语义的变更，PR 描述应说明旧行为、新行为和风险点。
3. 提交前确认新增或修改的日志文本全部为 ASCII。
4. 提交前确认测试已覆盖改动点，并在 PR 中说明关键验证结果。
5. 提交前确认本次改动没有跨出 `leaf-base-elasticsearch` 当前 Maven 模块。

## 9. 推荐执行命令

```powershell
mvn -pl leaf-base-elasticsearch -am test
```
