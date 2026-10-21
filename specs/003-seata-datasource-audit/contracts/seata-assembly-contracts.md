# Contract: Seata 与动态数据源装配契约

**Feature**: `003-seata-datasource-audit` | **Version**: 1（本次交付后生效）

本契约面向框架维护者与下游使用方，描述"启用 Seata 后哪些行为是承诺、哪些是禁止、失败长什么样"。实现可在不破坏本契约的前提下调整内部方式。

---

## 1. 装配条件

| 条件 | 承诺 |
| --- | --- |
| `leaf-base-seata` 配置类被扫描/显式导入 | 才参与装配；本模块**不提供** `AutoConfiguration.imports`，下游必须扫描 `cn.maple` 或显式 `@Import` |
| 类路径存在 `com.alibaba.druid.pool.DruidDataSource`、`cn.maple.core.datasource.config.GXDynamicDataSourceConfig`、`org.apache.seata.rm.datasource.SeataDataSourceProxy` | Seata 集成配置生效（三者缺一即整类跳过） |
| 容器存在 `GXDynamicDataSource` bean | 才把该数据源注入 MyBatis-Plus；缺失时**跳过并 warn**，不得启动失败 |
| 使用方自定义 `SqlSessionFactory` | 框架不覆盖；定制器不会被调用 |

**可断言事实（由类型断言测试固定）**: 路由数据源不会被 Seata 代理包裹；每个物理数据源最多一层 Seata 代理。

**不承诺**: 检测应用内其它 `DataSource` bean 是否被 Seata 自动代理（属使用方配置边界，见 §6）。

---

## 2. 配置键契约

| 键 | 类型 | 默认 | 语义 | 变更约束 |
| --- | --- | --- | --- | --- |
| `seata.enabled` | boolean | `true`（Seata 官方默认） | 是否启用 Seata 客户端 | 官方键，不得重命名 |
| `seata.enable-auto-data-source-proxy` | boolean | `true`（官方默认） | Seata 自动代理开关；本框架建议置 `false` | 官方键；置 `true` 时 Seata 仍会跳过 `AbstractRoutingDataSource`，不会二次代理路由数据源 |
| `maple.framework.enable.seata-strict` | boolean | `false` | `true` 时：检测到目标数据源未被 Seata 代理即启动失败；`false` 时：warn 后继续 | **本次新增**；仅可新增，不得改变默认值语义 |

**配置来源（二选一，互斥）**:

| 来源 | 生效条件 | 资源 |
| --- | --- | --- |
| local | 类路径**无** `com.alibaba.cloud.nacos.NacosConfigManager` | `classpath:/${spring.profiles.active}/seata.yml`，`ignoreResourceNotFound=false` |
| nacos | 类路径**有** `NacosConfigManager` 且**有** `com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties` | `dataId=seata.yml`，`type=YAML`，`autoRefreshed=true` |

**失败语义（不可放宽）**: profile 未设置、或对应资源不存在 → 启动失败，消息必须包含 profile 与期望路径/来源；不得静默跳过 Seata 集成。

**敏感值**: 只允许 `${ENV_VAR:default}` 形式；契约禁止在示例、测试、`config/` 模板中写真实密钥。

---

## 3. 日志契约（ASCII，可检索）

| 时机 | 级别 | 必须包含 |
| --- | --- | --- |
| 未启用 Seata（类路径无代理类型） | DEBUG（datasource 侧） | 固定文案 `Seata DataSourceProxy not found, using original DataSource`，不得升为 WARN |
| 装配完成、全部目标源已代理 | INFO | `[Seata] enabled=true, config-source=local\|nacos, datasources=[...], proxied=[...]` |
| 存在未代理目标源且非严格模式 | WARN | `[Seata] datasource '<name>' is NOT wrapped by Seata` + 后果说明（分支不会加入全局事务） |
| 无 `GXDynamicDataSource` bean | WARN | `[Seata] GXDynamicDataSource is absent, skip MyBatis-Plus datasource customization` |
| 两套配置入口同时命中（异常态） | WARN | 指明同时生效的来源，并按 nacos 记录 `config-source=nacos` |
| 严格模式失败 | ERROR（由异常处理链路记录） | 未代理数据源名称集合 + `org.apache.seata.rm.datasource.SeataDataSourceProxy` |

**禁止**: 用 `debug` 记录"Seata 已装配但目标源未代理"；也不得把"未启用 Seata"升为 `warn`（会对纯数据源用户产生每数据源一条噪声）。日志中不得输出连接串、用户名、密码。

---

## 4. 异常契约

| 场景 | 异常 | 消息必须包含 |
| --- | --- | --- |
| 严格模式下存在未代理目标源 | `cn.maple.core.framework.exception.GXBusinessException` | 未代理数据源名称集合、缺失契约类型、关闭严格模式的键名 |
| 代理类存在但实例化失败（构造器不匹配等） | `GXBusinessException`（沿用现状） | 数据源名称、被反射的类名 |
| 配置资源缺失 | Spring 资源解析异常（保持现状） | profile、期望路径、来源（local/nacos） |

契约要求：任何新增异常必须继承 `GXBusinessException`，不得新增"静默返回 null/false"的失败表达。

---

## 5. 数据源类型契约（包装后）

| 承诺 | 说明 |
| --- | --- |
| 仍可断言为 `javax.sql.DataSource` | 路由数据源与目标数据源都成立 |
| 仍可断言为 `org.apache.seata.rm.datasource.SeataDataSourceProxy` | 目标数据源成立；路由数据源**不成立**（路由层不代理） |
| 目标数据源在生产环境的实际类型是 `org.apache.seata.rm.datasource.DataSourceProxy` | 书面事实；不作为单测断言（该构造器会 `getConnection()` 并校验 `undo_log`，无库环境不可构造） |
| 获取连接池能力的方式 | `SeataDataSourceProxy#getTargetDataSource()`，测试断言其返回构建时传入的原始实例 |
| 关闭责任 | `GXDynamicDataSource#destroy` 关闭其管理的连接池；包装层不持有需关闭资源 |

**兼容性说明**: 本节列出的是**既存事实**（当前实现已如此），本次不改变行为，只是把隐式事实变成书面契约并用测试固定。下游若存在 `instanceof DruidDataSource` 判断，需改为经 `getTargetDataSource()` 获取连接池实例。

---

## 6. 非目标（本次不承诺）

- AT 模式第二阶段（回滚日志写入/回滚）正确性；业务库 `undo_log` 建表。
- TCC / Saga / XA 模式。
- 真实 Seata Server、Nacos 的连通性与故障转移。
- `leaf-base-seata` 迁移到 `@AutoConfiguration` 装配模型。
- 检测"自动数据源代理与手工代理并存"（应用内其它数据源 bean 属使用方配置边界，仅文档说明）。
- 为旧命名空间 `io.seata.*` 保留回退分支（本次只锚定新命名空间）。
