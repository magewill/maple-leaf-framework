# 契约：事件类型与载荷

**类型**：公共事件（下游可监听）与回调载荷结构
**稳定性**：事件类型只追加；载荷字段只增不删，新增字段必须有默认语义。

## 事件类型

| 事件标识 | 可读名 | 运行模式 | 触发操作 | 载荷要求 |
| --- | --- | --- | --- | --- |
| `sync_save_entity` | `syncSaveEntity` | 同步 | 单条插入 | 实体快照 |
| `sync_update_entity` | `syncUpdateEntity` | 同步 | 按条件/按主键更新 | 实体、条件字段、操作符、原始条件 |
| `sync_update_field` | `syncUpdateField` | 同步 | 指定字段更新 | 更新字段、条件字段 |
| `sync_delete_soft` | `syncDeleteSoft` | 同步 | 条件软删除 | 条件字段、更新字段 |
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

| 字段 | 类型 | 空值语义 | 说明 |
| --- | --- | --- | --- |
| `entityData` | 键值映射 | 空映射 | 实体字段快照；键为驼峰属性名 |
| `updateFields` | 键值映射 | 空映射 | 被修改字段及新值 |
| `conditionFields` | 键值映射 | 空映射 | 条件字段及取值；`in`/`between` 为列表，`is null` 为 `null` |
| `conditionOperators` | 键值映射 | 空映射 | 与 `conditionFields` 键集合一致 |
| `rawConditionSql` | 字符串 | 空串 | 原始条件文本；降级时唯一可靠信息 |
| `conditionLossless` | 布尔 | `false` | 结构化条件是否完整 |
| `degradeReasons` | 枚举集合 | 空集合 | 见下 |
| `batchEntities` | 列表 | 空列表 | 批次载荷，保持原顺序 |
| `snapshotDegraded` | 布尔 | `false` | 是否保留了不可克隆值的引用 |

## 快照规则

- 支持防御性复制的类型：映射、集合、数组、日期、日历；复制后监听方修改无效。
- 不支持的类型：保留引用，并置 `snapshotDegraded=true`、追加 `UNSNAPSHOTABLE_VALUE` 降级原因。
- 明确禁止：依赖 Java 序列化或反射克隆任意对象（见项目宪法 V）。
- 批量事件对整个批次只做一次快照，不重复复制。

## 降级规则

| 场景 | `conditionLossless` | `rawConditionSql` | `degradeReasons` |
| --- | --- | --- | --- |
| 条件全部为列与常量的 `AND` 组合 | `true` | 原文 | 空 |
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
