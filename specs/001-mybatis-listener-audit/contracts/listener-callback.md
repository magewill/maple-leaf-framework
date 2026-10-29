# 契约：监听回调接口

**类型**：公共接口（下游实现方直接实现或继承）
**稳定性**：只增不改。本次加固保留全部既有方法签名与默认实现。

## 签名

```java
public interface GXMybatisListenerService<T> {
    default void saveEntityListener(T data);
    default void updateEntityListener(T data, Dict keyValuePairs, Dict keyOperatorPairs);
    default void updateEntityListener(T data, Dict keyValuePairs, Dict keyOperatorPairs, String rawWhereSql);
    default void updateFieldListener(Dict updateFieldData, Dict conditionFieldData);
    default void deleteSoftListener(Dict data);
    default void deleteListener(Dict data);
    default void saveBatchListener(Dict data);
    default void batchChangeListener(Dict data);
}
```

## 方法语义

| 方法 | 触发操作 | 载荷要点 |
| --- | --- | --- |
| `saveEntityListener` | 单条插入成功 | 实体快照，泛型 `T` 由实现类的类型参数决定 |
| `updateEntityListener`（3 参） | 按条件更新 / 按主键更新成功 | 更新字段 + 条件字段与操作符 |
| `updateEntityListener`（4 参） | 同上 | 追加原始条件文本；默认委托给 3 参版本，保证旧实现继续可用 |
| `updateFieldListener` | 指定字段更新成功 | 更新字段 + 条件字段 |
| `deleteSoftListener` | 条件软删除成功 | 条件字段 + 调用方显式传入的更新字段；框架追加的逻辑删除列不出现在载荷中 |
| `deleteListener` | 条件物理删除成功 | 条件字段 |
| `saveBatchListener` | 批量保存成功 | 批次实体列表，保持顺序 |
| `batchChangeListener` | 批量新增或更新成功 | 同上；默认委托给 `saveBatchListener` |

## 线程与事务语义

| 模式 | 执行线程 | 事务状态 | 约束 |
| --- | --- | --- | --- |
| 同步（默认） | 业务调用方线程 | 若存在事务，则**事务尚未提交** | 回调不得假设数据已提交；默认不声明独立事务边界 |
| 异步 | 独立执行器线程 | 事务已提交（或无事务时立即执行） | 回调若写库，必须自行声明事务传播行为 |

- 同步回调 MUST NOT 声明与调用方相同的事务边界，以免回调失败连带回滚业务写入（由回归测试固化）。
- 开启 `sync-after-commit` 后，同步回调改为提交后执行，线程仍为提交动作所在线程。

## 异常语义

| 配置 | 行为 |
| --- | --- |
| 默认（`propagate-exception=false`） | 单个回调异常被捕获：记录事件类型、监听器、异常摘要，计入 `failed`，继续执行下一个监听器；业务写操作保持成功 |
| `propagate-exception=true` | 同步投递的异常按原样向业务调用方传播（旧行为），后续监听器不再执行 |

- 无论哪种配置，异常 MUST NOT 导致事件被静默丢弃而不计数：回调异常由回调分发层计入 `failed` 并记录，投递层异常由发布层计入 `failed` 并记录，二者互不重复计数。
- 异步模式下异常 MUST NOT 终止执行器线程：异步回调异常始终隔离（无论 `propagate-exception` 取值），业务调用方无法观察到异步回调异常；`propagate-exception=true` 仅对同步投递生效。
- 开启 `sync-after-commit` 且存在活动事务时，回调在事务提交后执行；此时异常被隔离并记录（事务已提交、调用方已越过提交点），`propagate-exception` 的传播与"停止后续监听器"语义不适用于该路径。无事务时 `sync-after-commit` 立即投递，传播语义与默认同步路径一致。

## 泛型与载荷

- 单条保存/更新的泛型 `T` 取自实现类声明的类型参数；无法解析时按原始类型传入，不得抛异常。
- 条件类回调的键为驼峰字段名，值可能为单值、列表（`in`/`between`）或 `null`（`is null`）。
- 载荷为快照：回调修改不得影响业务对象、数据库参数或其他监听器。批量事件的载荷由切面构建后直接投递（不再二次快照），因此切面 MUST 把实体自有的可变值（`Date`、`Calendar`、数组、集合、`Map`，如 `ext`）复制为载荷私有副本，使"修改载荷不影响业务对象"在批量路径同样成立。
- 条件无法无损还原时，结构化字段为空，`rawWhereSql`（删除类事件为 `rawConditionSql`）为唯一可靠信息。
- 条件为空（WHERE 段为空）时视为可无损还原：结构化条件与原始条件文本均为空、`degradeReasons` 为空，且不计入 `degraded`。

## 兼容性规则

- 既有方法签名保持不变；新增能力只能以 `default` 方法追加。
- 不得改变既有回调的触发时机与次数语义（本次修复的去重与隔离属于缺陷修复，见 spec 附录）。
