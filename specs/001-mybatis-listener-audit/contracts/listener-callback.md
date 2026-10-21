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
| `deleteSoftListener` | 条件软删除成功 | 条件字段 + 被置为删除标记的字段 |
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
| `propagate-exception=true` | 异常按原样向业务调用方传播（旧行为），后续监听器不再执行 |

- 无论哪种配置，异常 MUST NOT 导致事件被静默丢弃而不计数。
- 异步模式下异常 MUST NOT 终止执行器线程。

## 泛型与载荷

- 单条保存/更新的泛型 `T` 取自实现类声明的类型参数；无法解析时按原始类型传入，不得抛异常。
- 条件类回调的键为驼峰字段名，值可能为单值、列表（`in`/`between`）或 `null`（`is null`）。
- 载荷为快照：回调修改不得影响业务对象、数据库参数或其他监听器。
- 条件无法无损还原时，结构化字段为空，`rawWhereSql` 为唯一可靠信息。

## 兼容性规则

- 既有方法签名保持不变；新增能力只能以 `default` 方法追加。
- 不得改变既有回调的触发时机与次数语义（本次修复的去重与隔离属于缺陷修复，见 spec 附录）。
