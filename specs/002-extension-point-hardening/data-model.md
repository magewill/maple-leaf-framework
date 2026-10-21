# Phase 1 Data Model: 扩展点模块审查加固

**Feature**: 002-extension-point-hardening | **Date**: 2026-10-21

本模块无持久化实体，全部为进程内不可变值对象 + 一个并发映射。以下模型用于固定"什么算同一个东西""何时可变""非法输入如何失败"。

---

## 1. 业务场景（Scenario）

**对应类型**：`GXBizScenario`（不可变、`Serializable`）

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `bizId` | String | 非空、非空白、不含 `.` |
| `useCase` | String | 非空、非空白、不含 `.` |
| `scenario` | String | 非空、非空白、不含 `.` |

**派生值**（构造期一次性计算，供热路径直接使用，不在解析时拼接）：

| 派生值 | 组合 | 用途 |
| --- | --- | --- |
| `uniqueIdentity` | `bizId.useCase.scenario` | 精确查找键 |
| `identityWithDefaultScenario` | `bizId.useCase.#defaultScenario#` | 第二层兜底 |
| `identityWithDefaultUseCase` | `bizId.#defaultUseCase#.#defaultScenario#` | 第三层兜底 |

**系统默认场景**：`#defaultBizId#.#defaultUseCase#.#defaultScenario#`（全局默认，第四层兜底）。

**新增工厂**：`fromUniqueIdentity(String identity)`
- 输入必须恰为三段、以 `.` 分隔、每段非空；
- 输出等价场景对象；不合规立即 `IllegalArgumentException`，信息包含实际段数与非法段；
- 用途：让字符串坐标入口与类坐标入口共用同一条兜底流程。

**身份与唯一性**：`equals`/`hashCode` 基于 `bizId + useCase + scenario` 三元组；两个场景对象只要三段相同即视为同一场景。

**校验时机**：构造期（`valueOf`/`fromUniqueIdentity`），不存在"半合法"状态。

---

## 2. 扩展坐标（Coordinate）

**对应类型**：`GXExtensionCoordinate`（不可变、`Serializable`）

| 字段 | 类型 | 可空 | 约束 |
| --- | --- | --- | --- |
| `extensionPointName` | String | 否 | 非空、非空白；类型入口取接口全限定名 |
| `bizScenarioUniqueIdentity` | String | 否 | 恰为三段点分、每段非空 |
| `extensionPointClass` | Class<?> | **是** | 仅类坐标构造时提供；字符串坐标构造为 `null` |
| `bizScenario` | GXBizScenario | **是** | 仅类坐标构造时提供；字符串坐标构造为 `null` |
| `hashCode` | int | 否 | 构造期预计算，避免每次查找重算 |

**两个构造入口**：

| 入口 | 校验 | 适用 |
| --- | --- | --- |
| `(Class<?> extPtClass, GXBizScenario)` | 类必须是非 `GXExtensionPoint` 本身的子接口 | 类型坐标（执行器主路径） |
| `(String extensionPoint, String bizScenario)` | 两者非空白 + 场景标识三段点分（本次新增） | 跨进程/配置化调用 |

**身份与唯一性**：仅由 `extensionPointName + bizScenarioUniqueIdentity` 决定，因此类坐标与字符串坐标只要描述同一接口与场景即相等——这是"两个入口必须返回同一实现"的模型基础。`extensionPointClass` / `bizScenario` 为 `null` 只影响"能否走兜底"，不影响相等性，**这正是缺陷 2 的成因**，修复后不可再依此分叉执行路径。

---

## 3. 注册声明（Declaration）

**对应来源**：`@GXExtension`（可重复）/ `@GXExtensions`（容器）；由实现类上的合并注解解析得到。

| 声明形态 | 示例 | 解析结果 |
| --- | --- | --- |
| 单场景 | `@GXExtension(bizId="a")` | 1 个坐标 |
| 重复场景 | `@GXExtension(bizId="a")` ×2 | N 个坐标（每个注解 1 个） |
| 显式多坐标 | `@GXExtensions(value={...})` | `value().length` 个坐标 |
| 笛卡尔积 | `@GXExtensions(bizId={a,b}, useCase={c}, scenario={d,e})` | `2×1×2 = 4` 个坐标 |
| 混用 | `@GXExtension(a)` + `@GXExtensions({b})` | 两处合计（a、b） |

**解析规则**（唯一实现，供两个注册入口共用）：

```
repeatable = findMergedRepeatableAnnotations(clazz, GXExtension)   // Spring 7: Set
container  = findMergedAnnotation(clazz, GXExtensions)

坐标 ← repeatable 的每一项
若 (container != null 且 container.value().length == 0)
    坐标 ← 坐标 ∪ 笛卡尔积(container.bizId × useCase × scenario)

若 坐标为空 → 抛 GXExtensionException（含实现类全名与声明形态）
```

**约束**：
- 同一实现的坐标集合必须**全部注册成功**，不允许部分成功（启动期整体失败）；
- 任两处声明落到同一坐标 → 立即冲突失败，信息含坐标、已有实现、冲突实现；
- 实现类必须恰好对应一个扩展点接口（接口名含既有约定片段），否则失败并列出候选。
- 父类与子类各自声明并落到同一坐标时，按上一条冲突规则处理，信息给出父子双方类名；`@Inherited` 只决定"子类未声明时继承父类声明"，不决定"父子都声明时如何取舍"——后者一律判冲突，禁止子类静默覆盖。
- 失败信息要素按类型分层：未命中 → 扩展点标识 + 请求场景；冲突 → 坐标 + 冲突双方实现类名；扩展点接口非法 → 实现类名 + 候选接口列表。

---

## 4. 扩展仓库（Repository）

**对应类型**：`GXExtensionRepository`

| 状态 | 写入方 | 读方 | 约束 |
| --- | --- | --- | --- |
| 注册期（容器刷新完成前） | 注册流程 / 手动注册入口 | 无 | 并发写入必须原子；同坐标仅一次成功 |
| 运行期（刷新完成后） | 无 | 全部执行线程 | 内容恒定；只对外提供只读快照 |

**状态流转**：

```
[空] --注册--> [部分注册] --注册--> [就绪]
 │                 │
 └── 任一步失败 ────┴──> 启动失败（上下文刷新中止，不进入"就绪"）

[就绪] --(无任何合法写入路径)--> [就绪]
```

**不变量**：
- 键唯一：一个 `(扩展点, 场景)` 只能对应一个实现；
- 值非空：不存在"已注册但实现为空"的条目；
- 读可见：注册成功后，任意线程的后续读取必须立即可见该实现；
- 快照只读：对外返回的视图不可修改，且不反映后续写入。

---

## 5. 解析结果（Resolution）

用于验收与排障的中间模型，不对外暴露为类型。

| 字段 | 说明 |
| --- | --- |
| `requestedIdentity` | 请求场景的 `uniqueIdentity` |
| `matchedIdentity` | 实际命中的坐标标识 |
| `fallbackStep` | 1=精确；2=默认场景；3=默认用例；4=全局默认 |
| `outcome` | 命中 / 未命中（未命中抛异常，信息含扩展点与请求场景） |

**兜底顺序（不可变更）**：

| 步骤 | 查找键 | 语义 |
| --- | --- | --- |
| 1 | `biz.useCase.scenario` | 精确场景 |
| 2 | `biz.useCase.#defaultScenario#` | 该用例的默认场景 |
| 3 | `biz.#defaultUseCase#.#defaultScenario#` | 该业务的默认用例 |
| 4 | `#defaultBizId#.#defaultUseCase#.#defaultScenario#` | 全局默认 |

**性能不变量**：每次解析的查找次数 ≤ 4，与仓库规模无关；解析路径不得出现反射、字符串拼接、全量拷贝。
