# 契约：事件类型与载荷

**类型**：公共事件（下游可监听）与回调载荷结构
**稳定性**：事件类型只追加；载荷字段只增不删，新增字段必须有默认语义。

## 事件类型

| 事件标识 | 可读名 | 运行模式 | 触发操作 | 载荷要求 |
| --- | --- | --- | --- | --- |
| `sync_save_entity` | `syncSaveEntity` | 同步 | 单条插入 | 实体快照 |
| `sync_update_entity` | `syncUpdateEntity` | 同步 | 按条件/按主键更新 | 实体、条件字段、操作符、原始条件 |
| `sync_update_field` | `syncUpdateField` | 同步 | 指定字段更新 | 更新字段、条件字段 |
| `sync_delete_soft` | `syncDeleteSoft` | 同步 | 条件软删除 | 条件字段、更新字段（仅调用方显式传入） |
| `sync_delete` | `syncDelete` | 同步 | 条件物理删除 | 条件字段、原始条件 |
| `sync_save_batch_entity` | `syncSaveBatchEntity` | 同步 | 批量保存 | 批次实体列表 |
| `sync_batch_change` | `syncBatchChange` | 同步 | 批量新增或更新 | 批次实体列表 |
| `async_save_entity` | `asyncSaveEntity` | 异步 | 单条插入 | 同上 |
| `async_update_entity` | `asyncUpdateEntity` | 异步 | 按条件/按主键更新 | 同上 |
| `async_update_field` | `asyncUpdateField` | 异步 | 指定字段更新 | 同上 |
| `async_delete_soft` | `asyncDeleteSoft` | 异步 | 条件软删除 | 同上 |
| `async_delete` | `asyncDelete` | 异步 | 条件物理删除 | 同上 |
| `async_save_batch_entity` | `asyncSaveBatchEntity` | 异步 | 批量保存 | 同上 |
| `async_batch_change` | `asyncBatchChange` | 异步 | 批量新增或更新 | 同上 |

**约束**：事件标识字符串为对外契约，不得修改；新增操作类型时同时追加同步与异步标识各一个。

## 载荷字段

| 字段 | 实现键 | 类型 | 空值语义 | 说明 |
| --- | --- | --- | --- | --- |
| `entityData` | `entityData` | 键值映射 | 空映射 | 实体字段快照；键为驼峰属性名 |
| `updateFields` | `updateFieldData` | 键值映射 | 空映射 | 被修改字段及新值 |
| `conditionFields` | `conditionFieldData` / `keyValuePairs` | 键值映射 | 空映射 | 条件字段及取值；`in`/`between` 为列表，`is null` 为 `null`；更新回调沿用历史参数名 `keyValuePairs` |
| `conditionOperators` | `keyOperatorPairs` | 键值映射 | 空映射 | 与条件字段键集合一致；更新回调沿用历史参数名 |
| `rawConditionSql` | `rawConditionSql` / `rawWhereSql` | 字符串 | 空串 | 原始条件文本；降级时唯一可靠信息；更新回调沿用历史参数名 |
| `conditionLossless` | `conditionLossless` | 布尔 | `false` | 结构化条件是否完整 |
| `degradeReasons` | `degradeReasons` | 枚举集合 | 空集合 | 见下 |
| `batchEntities` | `batchEntities` | 列表 | 空列表 | 批次载荷，保持原顺序 |
| `snapshotDegraded` | `snapshotDegraded` | 布尔 | `false` | 是否保留了未能复制的引用：不可克隆的值，或超出批量载荷分离走查深度的嵌套值 |
| `snapshotScanTruncated` | `snapshotScanTruncated` | 布尔 | `false` | 严格快照扫描是否因深度/节点预算耗尽而提前停止（此时"未检查完"不等于"干净"） |

**命名说明**：`实现键` 列为载荷中的实际键名。历史回调参数（`keyValuePairs`、`keyOperatorPairs`、`rawWhereSql`）属于已发布契约，保持不变；新代码统一按 `实现键` 读取，语义与左侧契约字段一一对应。

**软删除的更新字段口径**：`sync_delete_soft` / `async_delete_soft` 的 `updateFields` 只包含调用方通过 `updateFieldList` 显式传入的字段。框架会按 `@TableLogic` 为软删除语句追加逻辑删除列（并可能追加 `deletedBy`），这些列由 SQL 构建器决定、不出现在载荷中；监听方应按"软删除已发生"的语义处理，不要依赖载荷中出现删除标记列。

## 载荷可变性

| 场景 | 载荷可变性 | 说明 |
| --- | --- | --- |
| 单条事件 | 可变副本 | 每个监听方拿到独立的防御性快照，修改只影响自身 |
| 批量事件（1 个监听方） | 可变副本 | 复用切面构建的载荷，仅该监听方可写 |
| 批量事件（≥2 个监听方） | **只读共享快照** | 所有监听方共享同一份载荷；顶层与嵌套的 `put`/`remove`/`compute`/视图修改均抛 `UnsupportedOperationException` |

**批量载荷的实体隔离**：四种批量事件类型一律由 `GXMyBatisModelSaveBatchEntitySnapshotEvent`（`GXMyBatisModelSaveBatchEntityEvent` 的子类）承载，发布组件按"整批一次快照"处理。为保证"修改载荷不影响业务对象"在批量路径同样成立，切面在构建载荷时把实体自有的可变值（`Date`、`Calendar`、数组、集合、`Map`，如 `ext`）复制为载荷私有副本（一对一复制、共享引用只复制一次、最大深度 32）。切面不得改写调用方传入的 `Dict`/`Map` 行（`Convert` 在这类入参下会返回调用方实例），必须为载荷新建行。走查超出深度 32 时保留实体引用，并置 `snapshotDegraded=true` 与 `UNSNAPSHOTABLE_VALUE` 降级原因（计入 `degraded`），不得按干净快照发布。

**迁移方式**：需要在批量回调中加工数据的监听器，请先复制为新的 `Dict`（如 `Dict.create().putAll(source)` 或 `Convert.convert`）再修改。受影响场景为"同一次批量写操作声明了两个及以上监听器"。

## 快照规则

- 支持防御性复制的类型：映射、集合、数组、日期、日历；复制后监听方修改无效。
- 不支持的类型：保留引用，并置 `snapshotDegraded=true`、追加 `UNSNAPSHOTABLE_VALUE` 降级原因。批量载荷超出分离走查深度（32）时同样保留引用并追加该原因。
- 检测时机：`maple.framework.mybatis.listener.strict-snapshot=true` 时，发布前扫描载荷并对不可克隆值置位、计数与告警；关闭时保持历史行为（保留引用但不标记），以维持无监听路径的零扫描开销。
- 扫描为有界尽力而为：最大深度 8、最多 4096 个节点。超出预算即停止扫描，置 `snapshotScanTruncated=true`、计入一次 `degraded` 并输出 `reason=scan-truncated` 的结构化告警；不得把"未检查完"判定为"干净"。
- 明确禁止：依赖 Java 序列化或反射克隆任意对象（见项目宪法 V）。
- 批量事件对整个批次只做一次快照，不重复复制。

## 降级规则

| 场景 | `conditionLossless` | `rawConditionSql` | `degradeReasons` |
| --- | --- | --- | --- |
| 条件全部为列与常量的 `AND` 组合 | `true` | 原文 | 空 |
| 条件为空（WHERE 段为空，如只带 `set(...)` 的 `UpdateWrapper`） | `true` | 空串 | 空 |
| 含 `OR` | `false` | 原文 | `OR_EXPRESSION` |
| 操作符左侧为函数/表达式/子查询 | `false` | 原文 | `NON_COLUMN_LEFT` |
| 同一字段重复出现 | `false` | 原文 | `DUPLICATE_FIELD` |
| 占位符数量或名称与解析结果不符 | `false` | 原文 | `PARAM_MISMATCH` |
| 条件文本解析失败 | `false` | 原文 | `PARSE_ERROR` |
| 条件对象非标准包装器 | `false` | 可取则取，否则空串 | `NON_ABSTRACT_WRAPPER` |

**铁律**：降级时不得伪造字段值，不得把值填到错误的字段上；宁可只给原始条件。

## 投递与顺序

- 同一写操作对同一监听器最多投递一次。
- 多个监听器按"接口全限定名 → 监听器类型全名"排序投递，顺序可复现。
- 异步模式下，同一批次的 1 个事件整体投递，不拆分。
- 事件不得携带 `null` 载荷字段；未使用字段统一为空集合/空映射。

## 兼容性规则

- 新增字段必须提供空值默认语义（空集合/空映射/`false`/空串），旧监听器无需改动。
- 不允许把既有字段的类型由单值改为列表；需要列表时新增字段。
- 事件标识、事件名不得重命名。
