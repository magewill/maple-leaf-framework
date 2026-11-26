# Phase 1 数据模型：MyBatis-Plus 操作监听

**范围**：本能力不存在持久化实体；以下"实体"指运行期对象与契约结构，用于约束字段、取值与状态流转。
**原则**：字段只增不删；新增字段一律可为缺省值或带默认语义，保证既有监听器无需改动。

---

## 1. 写操作事件（WriteOperationEvent）

一次成功写操作的抽象载体，作为投递与日志的最小单元。

| 字段 | 类型 | 必填 | 说明 | 约束来源 |
| --- | --- | --- | --- | --- |
| `operation` | 枚举 | 是 | 保存 / 更新 / 条件更新字段 / 软删除 / 物理删除 / 批量保存 / 批量新增或更新 | FR-003、FR-011 |
| `mode` | 枚举 | 是 | `SYNC`（业务线程内）/ `ASYNC`（提交后） | FR-003 |
| `eventType` | 字符串 | 是 | 14 个稳定事件标识之一，见事件契约 | FR-003 |
| `eventName` | 字符串 | 是 | 面向日志的可读名 | FR-020 |
| `listenerType` | 类型引用 | 是 | 本次投递的目标监听器 | FR-002 |
| `payload` | 回调载荷 | 是 | 见第 3 节 | FR-008～FR-012 |
| `operationId` | 字符串 | 否 | 关联标识，格式为 `方法名#进程内自增序号`；TraceId 由 MDC 独立输出，不并入该字段 | FR-020 |
| `occurredAt` | 时间戳 | 是 | 事件构造时刻 | FR-020 |

**校验规则**

- `mode` 与 `eventType` 必须一致（异步事件不得投递给同步监听器，反之亦然）。
- 同一 `operationId` + `listenerType` + `operation` 只能出现一次（去重，FR-002）。
- `payload` 在构造后不可变；监听方修改不得回写（FR-008）。

**状态流转**

```text
CREATED → SKIPPED（无监听声明，不产生实例）
CREATED → PENDING_COMMIT → PUBLISHED（异步模式 + 有活动事务）
CREATED → PUBLISHED（同步模式，或异步模式但无活动事务）
PENDING_COMMIT → DISCARDED（事务回滚）
PUBLISHED → DELIVERED | FAILED | REJECTED
```

---

## 2. 监听声明（ListenerDeclaration）

绑定在数据访问方法或接口上的配置快照，解析后进入缓存。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `listenerType` | 类型引用 | 是 | 目标监听器实现 |
| `mode` | 枚举 | 是 | `SYNC` / `ASYNC`，由声明的运行模式决定 |
| `declaredOn` | 枚举 | 是 | `METHOD` / `TYPE` / `INHERITED` |
| `resolvedFrom` | 字符串 | 是 | 命中声明的类型全名与方法签名，用于诊断 |
| `cacheKey` | 字符串 | 是 | 接口 + 方法签名 + 目标类型 |

**校验规则**

- 方法级声明优先于类型级声明；类型级优先于父接口/父类上的声明。
- 同一操作解析出的声明集合按"监听器类型 + 事件种类"去重后投递（FR-002）。
- 解析不到声明时缓存"空结论"，不得每次回落反射（FR-018）。

---

## 3. 回调载荷（CallbackPayload）

监听方实际消费的数据。不同操作填不同子集，未使用的字段为空集合而非 `null`。

| 字段 | 类型 | 适用操作 | 说明 |
| --- | --- | --- | --- |
| `operation` | 字符串 | 更新 / 物理删除 / 批量 | 被拦截的写方法名；其它操作不写该键 |
| `entityData` | 键值映射 | 保存 / 更新 / 批量 | 实体字段快照 |
| `updateFields` | 键值映射 | 条件更新字段 / 软删除 | 被修改字段及新值 |
| `conditionFields` | 键值映射 | 更新 / 条件更新字段 / 软删除 / 物理删除 | 条件字段及取值 |
| `conditionOperators` | 键值映射 | 更新 | 条件字段与操作符（`=`、`in`、`between` 等） |
| `rawConditionSql` | 字符串 | 全部条件类操作 | 原始条件文本；降级时是唯一可靠信息 |
| `conditionLossless` | 布尔 | 全部条件类操作 | 结构化条件是否可无损还原 |
| `degradeReasons` | 枚举集合 | 全部条件类操作 | 降级原因，见第 5 节 |
| `batchEntities` | 列表 | 批量 | 本批次实体快照，保持原顺序 |
| `snapshotDegraded` | 布尔 | 全部 | 是否出现不可快照的值（引用被原样保留） |
| `snapshotScanTruncated` | 布尔 | 全部（仅 `strict-snapshot=true`） | 严格快照扫描是否因超出深度/节点预算而截断，此时载荷未被完整校验 |

**校验规则**

- `conditionLossless=false` 时 `rawConditionSql` 必须非空（FR-009）。
- `conditionFields` 的键数量必须与 `conditionOperators` 一致，键集合相同。
- `batchEntities` 为空时不得投递批量事件（等价于"无实际写入"，FR-001）。
- `entityData`、`updateFields`、`conditionFields` 不得为 `null`。

---

## 4. 投递任务（DeliveryTask）

异步模式下的执行单元。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `event` | 写操作事件 | 要投递的事件 |
| `datasourceContext` | 上下文快照 | 提交时的数据源键 |
| `dataFilterContext` | 上下文快照 | 提交时的数据权限条件 |
| `traceId` | 字符串 | 提交时的追踪标识 |
| `submittedAt` | 时间戳 | 入队时刻 |
| `startedAt` / `finishedAt` | 时间戳 | 执行起止，用于长任务告警 |

**状态流转**

```text
SUBMITTED → QUEUED → RUNNING → SUCCEEDED
                              → FAILED
QUEUED → REJECTED（容量耗尽或执行器已关闭）
QUEUED → CANCELLED（停机超时未执行）
```

**校验规则**

- 任务结束必须恢复或清理三类上下文，不得残留到同线程下一个任务（FR-014）。
- `REJECTED` 与 `CANCELLED` 必须计入计数并输出结构化日志（FR-016、FR-017）。

---

## 5. 投递结果与降级原因（DeliveryOutcome / DegradeReason）

| 结果 | 含义 | 业务影响 |
| --- | --- | --- |
| `DELIVERED` | 监听器正常返回 | 无 |
| `FAILED` | 监听器抛出异常且已被隔离 | 无（写操作保持成功） |
| `REJECTED` | 异步任务被拒绝 | 无（写操作保持成功），但事件未投递 |
| `DEGRADED` | 载荷降级（条件无法还原 / 快照不可用） | 无，但回调信息不完整 |

**降级原因枚举**

| 原因 | 触发条件 |
| --- | --- |
| `OR_EXPRESSION` | 条件下含 `OR`，无法用字段到值的单值映射表达 |
| `NON_COLUMN_LEFT` | 操作符左侧不是列（函数、表达式、子查询） |
| `DUPLICATE_FIELD` | 同一字段重复出现，无法唯一还原 |
| `PARAM_MISMATCH` | 占位符数量或名称与解析出的条件不匹配 |
| `PARSE_ERROR` | 条件文本解析失败 |
| `NON_ABSTRACT_WRAPPER` | 条件对象不是可解析的标准包装器 |
| `UNSNAPSHOTABLE_VALUE` | 值不可克隆，只能保留引用 |

---

## 6. 指标快照（MetricsSnapshot）

只读，用于告警与验收。`delivered` 至 `skipped` 六项为累计计数，进程生命周期内单调递增；`activeTasks` 与 `queueSize` 为瞬时 gauge，会随任务开始/结束与入队/出队上升和下降。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `delivered` | 长整型 | 成功投递次数 |
| `failed` | 长整型 | 监听器异常、载荷构建/投递层失败，以及递归上限抑制的投递次数（每项恰好计一次） |
| `rejected` | 长整型 | 异步拒绝次数 |
| `cancelled` | 长整型 | 停机超时未执行次数 |
| `degraded` | 长整型 | 载荷降级次数：每个载荷最多计一次，同时命中多种降级标记时仍只计一次 |
| `skipped` | 长整型 | 无监听声明的写操作次数（用于验证短路生效） |
| `activeTasks` | 整型 | 当前执行中的异步任务数 |
| `queueSize` | 整型 | 当前排队任务数 |

**校验规则**

- 累计计数（`delivered` / `failed` / `rejected` / `cancelled` / `degraded` / `skipped`）只增不减，重启后归零（不跨进程持久化）。
- `degraded` 按载荷计数：同一载荷无论命中多少种降级标记（条件不可还原、保留不可快照引用、严格扫描截断、批量分离走查截断），都只计一次。
- `activeTasks` 与 `queueSize` 为 gauge，可为任意非负瞬时值，不得按"只增"配置告警。
- 快照读取必须无锁或低竞争，不得阻塞写路径。

---

## 7. 关系图

```text
写操作（Mapper / Service 方法）
   │ 1
   ├── 命中 0..n 个监听声明 ──► 去重（监听器类型 + 事件种类）
   │                                   │
   │                                   ▼
   └── 成功(>0行) ──► 构造 1 个写操作事件 ──► 同步：立即投递
                                          └─► 异步：登记提交回调 → 提交后投递
                                                     │
                                                     ▼
                                              投递任务（继承上下文）
                                                     │
                                          ┌──────────┴──────────┐
                                          ▼                     ▼
                                     DELIVERED/FAILED      REJECTED/CANCELLED
                                          │                     │
                                          └──────► 指标快照 ◄────┘
```
