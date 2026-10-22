# Phase 1 Data Model: 审查产物与配置模型

**Feature**: `008-eureka-server-module-audit` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

本特性不引入运行时数据实体。本文件定义的是**审查产物模型**（报告结构）、**验证矩阵模型**（可断言的表格结构）与**配置键模型**（开关与示例配置）。所有模型用于 `audit-report.md`、测试断言与手册，不产生新的生产类型。

---

## 1. 审查发现（Finding）

报告与规格之间的一等实体，一条发现一行。

| 字段 | 类型 | 约束 | 来源 |
| --- | --- | --- | --- |
| `id` | 字符串 | `F-NN`，全报告唯一，与 `spec.md` 编号一致 | FR-002 |
| `level` | 枚举 | `高 / 中 / 低`；`高` 必须能构造故障现场 | FR-002、SC-010 |
| `dimension` | 枚举 | `装配边界 / 配置语义 / 依赖治理 / 资源与启动成本 / 契约一致性` | FR-002 |
| `location` | 字符串 | `文件:行区间`（可多段）；文档类发现可为 `章节标题` | FR-002 |
| `trigger` | 字符串 | 可复现的触发条件（配置组合、类路径形态或操作步骤） | FR-002、FR-003 |
| `impact` | 字符串 | 受影响范围，须可判定（如"引入聚合门面的全部应用"） | FR-018 |
| `evidence` | 字符串列表 | 证据编号 `E-NN`，至少 1 条 | FR-003 |
| `verification` | 枚举 | `已实测 / 既有用例 / 未验证假设`；`未验证假设` 不得计入已确认缺陷 | FR-003、SC-002 |
| `repro` | 字符串 | 复现命令或步骤（无外网、无外部注册中心） | FR-013 |
| `regression` | 字符串 | 回归设计：输入 → 断言点 → 期望差异（旧失败/新通过） | FR-013 |
| `fix` | 字符串 | 最小修复动作（可为"仅文档"） | FR-018 |

**校验规则**

1. `level=高` ⇒ `repro` 与 `regression` 均非空。
2. `verification=未验证假设` ⇒ `impact` 必须写明"结论待实测"。
3. `id` 必须存在于 `spec.md` 的 F 列表中，不允许报告新增未登记的发现编号（新增需回填 `spec.md`）。
4. `evidence` 引用的 `E-NN` 必须在报告证据索引中有对应条目。

**状态流转**

```text
未验证假设 --(实测复现)--> 已实测
未验证假设 --(实测否定)--> 已否定（保留条目，标注"假设不成立"，同步修正 spec 结论）
既有用例 --(用例失效/被误导)--> 未验证假设（降级并要求补测）
```

---

## 2. 启动语义矩阵（Startup Semantics Matrix）

| 字段 | 取值 |
| --- | --- |
| `key` | `register-with-eureka` / `fetch-registry` / `service-url.defaultZone` / `instance.hostname` / `instance.ip-address` |
| `mode` | `单机` / `集群` |
| `expected` | 该形态下的应取值（来自 `AGENTS.md` 基线与 Eureka 语义） |
| `actual` | 实测的生效值（从环境端点或启动日志读取） |
| `conclusion` | `一致 / 矛盾 / 不可用` |

**校验规则**：5 键 × 2 形态 = 10 行必须全部填写，不得留空；`mode=集群` 必须额外产出 1 条"对等复制是否发生"的布尔结论（`replicated=true/false` + 观测方式）。

---

## 3. 装配矩阵（Assembly Matrix）

| 字段 | 取值 |
| --- | --- |
| `form` | `仅本模块（开关缺省）` / `开关=true` / `开关=false` / `聚合门面消费者` / `类缺失（FilteredClassLoader）` |
| `webType` | `SERVLET` / `REACTIVE`（契约级判定） |
| `serverBean` | `present / absent`（是否存在 Eureka Server 相关 Bean） |
| `servletContainer` | `present / absent`（是否创建 `ServletWebServerApplicationContext`/Tomcat） |
| `startupMillis` | 上下文启动耗时（毫秒，同机同 JDK，两次运行） |
| `conclusion` | `符合预期 / 缺陷` |

**校验规则**

1. `form=仅本模块（开关缺省）` 与 `form=开关=false` 的 `serverBean` 必须为 `absent`。
2. `form=开关=true` 的 `serverBean` 必须为 `present` 且 `servletContainer=present`。
3. `webType=REACTIVE` 不产生运行期行（D-12），以 `contracts/assembly-contract.md` 的契约断言代替。

---

## 4. 依赖矩阵（Dependency Matrix）

| 字段 | 取值 |
| --- | --- |
| `coordinate` | `spring-boot-devtools` / `hk2-api` / `hk2-utils` / `woodstox-core` / `httpclient` / `httpclient5` / `jersey-apache-connector`（保留） / `jersey-server` / `jersey-hk2` / `spring-cloud-starter-netflix-eureka-server` |
| `resolvedVersion` | 解析版本（变更前后各一列） |
| `scope` | `compile / runtime / test / provided / absent` |
| `transitive` | `是 / 否`（是否随本模块传递给下游） |
| `verdict` | `缺陷 / 冗余 / 无风险` |
| `action` | `移除 / 对齐 4.0.2 / 删除重复声明 / 保留` |

**校验规则**

1. 变更后 `devtools` 行 `scope=absent`。
2. 变更后 Jersey 相关坐标全部 `4.0.x`。
3. 除 `jersey-apache-connector` 与 `httpclient` 外，其余坐标 `resolvedVersion` 前后必须一致（D-08 的 diff 断言）。

---

## 5. 配置键模型（Configuration Key）

| 字段 | 约束 | 值 |
| --- | --- | --- |
| `key` | 遵循 `maple.framework.enable.<feature>` | `maple.framework.enable.eureka-server` |
| `type` | Boolean | `java.lang.Boolean` |
| `defaultValue` | 必须显式声明且与代码一致 | `false` |
| `effectWhenTrue` | 启用 `@EnableEurekaServer` 并创建注册中心端点 | — |
| `effectWhenFalse` | 不创建注册中心 Bean、不创建 Servlet 上下文相关端点，输出无告警 | — |
| `metadata` | `additional-spring-configuration-metadata.json` 中必须存在 | 名称、类型、默认值、描述 |
| `migration` | 从"引入即启用"升级的路径 | 显式设 `true` 或直接依赖本模块 |

**校验规则**：元数据 `defaultValue` 与 `@ConditionalOnProperty(matchIfMissing=false)` 语义一致；键名不得与 Eureka 原生 `eureka.*` 重复或冲突（宪法/`AGENTS.md` 第 44 条）。

### 5.1 模块施加的隐式默认键

| 字段 | 约束 | 值 |
| --- | --- | --- |
| `key` | Eureka 原生键，模块不新增命名空间 | `eureka.client.enabled` |
| `type` | Boolean | `java.lang.Boolean` |
| `imposedValue` | 由 `EnvironmentPostProcessor` 以最低优先级写入 | `false` |
| `trigger` | 应用不是 Servlet 注册中心 | 开关非 `true`，或开关为 `true` 但 `spring.main.web-application-type` 非 `servlet` |
| `override` | 应用显式配置优先 | 任意更高优先级属性源中的 `eureka.client.enabled` 覆盖本默认 |
| `observability` | 必须留痕 | 一条 ASCII INFO，说明降级原因与恢复方式 |
| `regression` | 四个客户端禁用输入组合 | `GXEurekaServerClientDefaultsPostProcessorTest`（类内共 6 个用例，其中 4 个针对客户端禁用/保留，另 2 个覆盖 §5.2 的单机默认） |
| `metadata` | 不写入模块元数据 | 属 Eureka 原生键，已有上游元数据 |

**校验规则**：施加前必须同时检查"是否已存在 `eureka.client.enabled`"与"是否为 Servlet 注册中心"；两者任一成立都不得写入该默认值。

### 5.2 模块施加的单机默认键

| 字段 | 约束 | 值 |
| --- | --- | --- |
| `keys` | Eureka 原生键，模块不新增命名空间 | `eureka.client.register-with-eureka`、`eureka.client.fetch-registry` |
| `type` | Boolean | `java.lang.Boolean` |
| `imposedValue` | 由 `EnvironmentPostProcessor` 以最低优先级逐键写入 | 两者均 `false` |
| `trigger` | 应用是 Servlet 注册中心 | 开关 `true` 且 Web 类型为 `servlet` |
| `override` | 应用显式配置优先 | 该键在任意更高优先级属性源或默认属性中已存在时不写入 |
| `observability` | 必须留痕 | 一条 ASCII INFO，列出实际写入的键 |
| `regression` | 三个用例 | `GXEurekaServerSwitchSpringBootTest#switchOnlySingleMachineDefaultsToNoRegistration`、`GXEurekaServerClientDefaultsPostProcessorTest#appliesSingleMachineDefaultsWhenServletRegistryIsEnabled`、`#keepsExplicitRegistrationSettingsWhenServletRegistryIsEnabled` |
| `metadata` | 不写入模块元数据 | 属 Eureka 原生键，已有上游元数据 |

**校验规则**：该默认只表达"单机不注册、不拉取"，不得包含任何地址、域名、端口；集群成员必须显式设 `true`，否则不参与对等复制（见 §6 与 `contracts/configuration-contract.md` §1.2）。

---

## 6. 示例配置文件（Example Config）

| 字段 | standalone | peer |
| --- | --- | --- |
| `path` | `src/main/resources/examples/eureka-server-standalone.yml` | `src/main/resources/examples/eureka-server-peer.yml` |
| `register-with-eureka` | `false` | `true` |
| `fetch-registry` | `false` | `true` |
| `service-url.defaultZone` | 不出现 | 占位符形式，至少 2 个对端 |
| `instance.hostname` | 不出现 | 占位符形式（`${...}`） |
| `instance.ip-address` | 不出现 | 不出现（由 `prefer-ip-address` 决定，缺省不写） |
| `server.port` | 不出现 | 占位符形式（`${server.port:7001}` 或显式示例值 + 注释） |
| `loadedByDefault` | `false`（不在 `classpath:/` 根） | `false` |

**校验规则**

1. 两个文件都**不得**含 `127.0.0.1`。
2. `standalone` 不得出现任何地址、主机名、端口键。
3. `classpath:/application.yml` 必须不存在（资源契约用例断言）。
4. 示例中出现的键必须全部是 Eureka 原生键，不得发明自定义键。

---

## 7. 契约核对清单（Contract Checklist）

| 字段 | 取值 |
| --- | --- |
| `source` | `AGENTS.md` / `README.md` / `docs/leaf-base-eureka-server开发手册.md` / `additional-spring-configuration-metadata.json` |
| `item` | 章节编号、测试现状表述、开关默认值、模块表描述、元数据键 |
| `expected` | 修复后应呈现的内容 |
| `actual` | 当前内容 |
| `verdict` | `一致 / 不一致` |
| `location` | 章节标题或行号 |

**校验规则**：`verdict=不一致` 的条目必须能在报告中定位到具体行号；修复后重新核对应全部为 `一致`（SC-009）。
