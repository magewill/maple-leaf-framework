# Phase 1 Data Model: Seata 与动态数据源协作正确性审查加固

**Feature**: `003-seata-datasource-audit` | **Date**: 2026-10-21

本特性不拥有数据库 schema。这里的"数据模型"指**审查产物与装配契约的结构化定义**，用于约束审查报告、测试断言与后续任务拆分的一致性。

---

## 1. 审查结论（Finding）

**用途**: 审查报告的最小单元，一条对应一个可独立验证的判断。

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `id` | `F-NN` | 是 | 全报告唯一，按严重度排序后编号 |
| `level` | `BLOCKER` / `RISK` / `CLEAN` | 是 | 对应 FR-001 的"缺陷 / 风险 / 无缺陷" |
| `title` | 短句 | 是 | 一句话描述现象，不含推测 |
| `location` | `模块/文件:行号` 或 `依赖坐标` | 是 | 必须可直接跳转或可直接执行的坐标 |
| `trigger` | 条件描述 | 是 | 触发该现象的前提（配置、类路径、bean 形态） |
| `impact` | 影响面 | 是 | 说明对事务正确性、启动行为或数据源行为的后果 |
| `evidence` | 证据列表 | 是 | 代码片段位置、jar 类清单、源码路径、日志片段 |
| `reproduce` | 命令或测试标识 | 是 | 满足 SC-006 的 15 分钟复现要求 |
| `recommendation` | 建议 | 是 | 与 FR/任务对应；不得为空 |
| `status` | `OPEN` / `FIXED` / `ACCEPTED` | 是 | 交付时 `BLOCKER` 不得为 `OPEN` |

**校验规则**:

1. `level=BLOCKER` 必须同时有 `reproduce` 与 `recommendation`，且交付时 `status=FIXED`。
2. `reproduce` 为测试时，必须给出可直接运行的测试全名；为命令时，必须是仓库内可执行命令。
3. 同一 `location` 允许多条 Finding，但不得出现结论互相矛盾的两条。

**状态流转**: `OPEN` →（修复并回归）→ `FIXED`；`OPEN` →（评审接受风险）→ `ACCEPTED`（仅 `RISK`/`CLEAN` 允许）。

---

## 2. 协作场景矩阵（Scenario Matrix）

**用途**: 保证"每个跨模块交互点都有明确责任方与守护测试"，避免只测单侧。

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `scenario` | 枚举 | 是 | `PROXY_APPLIED` / `PROXY_MISSING` / `ASSEMBLY_ABSENT` / `BEHAVIOR_PARITY` / `CONFIG_EXCLUSIVE` |
| `responsibility` | `seata` / `datasource` / `both` | 是 | 谁是权威判定方；`both` 必须说明各自职责边界 |
| `expected` | 可断言的结果 | 是 | 必须是日志、异常或类型断言，不接受"行为正常" |
| `guardedBy` | 测试类名 | 是 | 至少一个，且属于本特性测试基线 |
| `failureMode` | 失败时表现 | 是 | `warn` / `exception` / `type-mismatch` |

**取值覆盖要求**: 5 个 `scenario` 各至少 1 行；`responsibility` 不得全部为 `seata`（跨模块校验必须有 datasource 侧断言）。其中 `BEHAVIOR_PARITY` 必须同时覆盖两项：①路由与上下文逐项对照；②分页/租户/防全表更新拦截器与自动填充的存在性冒烟（对应 FR-009 的收窄范围）。

---

## 3. 数据源包装契约（Wrapper Contract）

**用途**: 书面化"被包装后类型契约变了什么"，回答上游"能不能强转 / 怎么拿底层连接池"。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `wrapperType` | 全限定类名 | 是 | `org.apache.seata.rm.datasource.DataSourceProxy` |
| `wrapperInterface` | 全限定类名 | 是 | `org.apache.seata.rm.datasource.SeataDataSourceProxy` |
| `targetAccessor` | 方法签名 | 是 | `getTargetDataSource()`（接口方法，稳定） |
| `resourceIdSource` | 描述 | 是 | 由目标数据源的 JDBC URL + 用户名推导，同 URL 多逻辑源会合并为同一资源 |
| `closeResponsibility` | `owner` / `wrapper` | 是 | `owner`：路由数据源负责关闭底层 `DruidDataSource`；包装层不持有需关闭资源 |
| `castableTypes` | 列表 | 是 | 包装后的目标源可安全断言为 `DataSource` / `SeataDataSourceProxy`；生产环境的实际类是 `org.apache.seata.rm.datasource.DataSourceProxy`（书面事实，不通过 `getClass()` 断言之） |
| `unwrapGuidance` | 文本 | 是 | 需要连接池能力时经 `getTargetDataSource()` 获取；测试通过断言该访问器返回原始实例来固定能力，不使用"不再是某类型"式负向断言 |

**校验规则**:

1. `castableTypes` 必须与测试断言一一对应，且每条断言必须是正向陈述（类型 `isInstance` 为真、访问器返回同一实例），禁止只写"不再保证为 X"；`getClass()` 断言不可用——真实代理构造会 `getConnection()` 并校验 `undo_log`，无库环境无法构造。
2. `closeResponsibility` 与 `GXDynamicDataSource#destroy` 的实际行为必须一致，且有用例覆盖"容器关闭后连接池已关闭"。
3. `resourceIdSource` 冲突场景必须作为边界用例记录（同一物理库多逻辑源），不要求修复，但必须书面声明。

---

## 4. 配置入口契约（Config Entry Contract）

**用途**: 固定"哪套配置在什么条件下生效、缺了会怎样"，供文档与测试共用一套事实。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `source` | `local` / `nacos` | 是 | 两条互斥入口 |
| `condition` | 类路径条件 | 是 | `local`：`com.alibaba.cloud.nacos.NacosConfigManager` 缺席；`nacos`：该类型存在且 `NacosConfigurationProperties` 存在 |
| `resource` | 路径 / dataId | 是 | `local`：`classpath:/${spring.profiles.active}/seata.yml`；`nacos`：`dataId=seata.yml`、`type=YAML` |
| `requiredOnMissing` | 布尔 | 是 | 固定 `true`：资源缺失必须启动失败（澄清 4） |
| `failureMessage` | 必须包含的要素 | 是 | 当前 profile、期望路径/来源、缺失原因 |
| `autoRefresh` | 布尔 | 是 | `local=false`，`nacos=true` |
| `observableSignal` | 日志键值 | 是 | `config-source=local|nacos` |

**校验规则**:

1. 两条入口不得同时生效；同时满足条件时必须只有 `nacos` 生效（本地入口的 `@ConditionalOnMissingClass` 保证）。
2. `failureMessage` 的要素必须有测试断言（不能只断言"抛异常"）。
3. 配置键清单必须与 `docs/leaf-base-seata开发手册.md` 一致（SC-005）。
4. 4 种组合（本地生效 / Nacos 生效 / profile 缺失 / 资源缺失）MUST 各有独立自动化用例（澄清 8），缺一即不满足 SC-005。

---

## 5. 装配校验状态机

```
[类路径无 Seata] ──> SKIPPED（seata 配置不装配；datasource 反射失败 → debug 后返回原数据源，0 告警）
[类路径有 Seata]
   │
   ├─ 无 GXDynamicDataSource bean ──> SKIPPED_WITH_WARN（定制器惰性解析失败，不注入）
   │
   └─ 有 GXDynamicDataSource bean
        ├─ 全部目标源实现 SeataDataSourceProxy ──> VERIFIED（info 日志含清单）
        └─ 存在未代理的目标源
             ├─ enable.seata-strict=false ──> DEGRADED（warn 含数据源名，启动继续）
             └─ enable.seata-strict=true  ──> FAILED（GXBusinessException，启动中断）
```

**不变量**:

- `SKIPPED` 与 `VERIFIED` 不得同时出现（同一数据源同一时刻只有一种状态）。
- `SKIPPED` 分支只允许 debug 级日志，不得产生 warn/error（澄清 7）。
- `DEGRADED`/`FAILED` 必须给出数据源名称集合，且集合非空。
- 状态判定只看"目标源是否实现接口"与"路由源是否被二次代理"，不执行任何 SQL、不建立连接，也不检测应用内其它数据源 bean 的自动代理（FR-007 收窄）。

---

## 实体关系

```text
Finding ──(reproduce)──> Scenario Matrix ──(guardedBy)──> 测试用例
   │                            │
   └──(location)──> 源码/依赖    └──(expected)──> Wrapper Contract / Config Entry Contract
                                                        │
                                                        └──(observableSignal)──> 日志契约
```

- 每条 `BLOCKER` Finding 至少映射 1 个场景行与 1 个测试用例。
- `Wrapper Contract` 与 `Config Entry Contract` 的每个字段必须至少被 1 条 `expected` 或测试断言引用，否则视为冗余字段，交付前删除。
