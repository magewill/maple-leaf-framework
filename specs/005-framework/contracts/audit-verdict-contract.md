# Contract: 审查判定口径（verdict）

**Feature**: `005-framework-module-audit` | **Version**: 1.0 | **Date**: 2026-10-22

本契约定义"一个现象该被判成什么"，是报告可复核性的基础。判定口径不一致会让同一事实在不同章节得到不同结论（`004` 轮已出现过 F-05/F-06 需要修正的情况）。

---

## 1. 四档判定 + 一个隔离档

| 判定 | 定义 | 判据（需同时满足） | 处理 |
| --- | --- | --- | --- |
| `DEFECT` | 实现与**承诺**冲突，且可复现 | ① 有明确承诺来源（宪法 / `AGENTS.md` / 公开签名语义 / 代码自身注释）；② 正常路径即可触发；③ 有 `SOURCE_FACT` 或 `EXECUTABLE` 证据 | 计入 GateDecision；给出回归测试设计 |
| `RISK` | 当前可接受，条件变化即失效 | ① 触发需要特定配置/负载/调用方式；② 现状无数据错误 | 计入报告，不计入 `blockers`；给出触发条件 |
| `CONTRACT_DEVIATION` | 文档与实现不一致 | ① 文档有明确表述；② 实现行为可核对；③ 二者不同 | 单列，要求文档同步；**不得**顺手改实现 |
| `ESTABLISHED_CONTRACT` | 已被既有用例锁定的行为，即使语义可疑 | 既有测试断言了该行为 | 标注"契约"，禁止在修复中被当作缺陷推翻 |
| `UNVERIFIED_ASSUMPTION` | 无法在本环境证实 | 缺真实容器/中间件/生产数据 | 隔离，不计入统计，写清缺失条件 |

**边界规则**：

1. **"违反直觉"不等于缺陷**：`getTraceId()` 返回空串而非 `null` 属 `ESTABLISHED_CONTRACT`（`GXTraceIdContextUtilsTest` 已锁定），报告只要求文档写明。
2. **"不安全"不等于"可利用"**：动态调用无白名单是 `DEFECT`（与同模块解析侧白名单策略冲突），但报告不得直接表述为"远程代码执行"，须写明"需上游传入可控类名"这一前提。
3. **"慢"不等于性能缺陷**：必须有可复算公式与结构上限，否则只能写 `RISK`。

---

## 2. 上下文四段判定表

对每个 `ThreadLocal`/MDC/请求上下文写入点，必须四段齐全（§4 对应 FR-005）：

| 段 | 检查项 | 判定值 |
| --- | --- | --- |
| **写入** | 是否在入口写入、是否覆盖已有值、`null`/空串如何处理 | `OK` / `AMBIGUOUS` / `MISSING` |
| **传播** | 同步直传 / 执行器包装 / ForkJoin 包装 / 事件总线 / 调度器 | 每条路径 `OK` / `GAP` |
| **清理** | `finally` 清理、清理范围（单键 vs 全量）、嵌套恢复 | `RESTORED` / `PARTIAL` / `NONE` |
| **异常路径** | 抛异常时是否仍清理、任务被取消/拒绝时是否清理 | `RESTORED` / `LEAKED` / `NOT_EXERCISED` |

**整体判定合成**：

- 四段全 `OK/RESTORED` → `OK`
- 清理为 `PARTIAL`（只恢复单键） → `GAP`，且只要存在"自定义 MDC 键"使用可能，即升为 `HIGH`
- 任一传播路径 `GAP` 且该路径在生产装配中可达 → `GAP` 且 `HIGH`
- 传播路径 `GAP` 但该组件**未被装配**（如包装器未注册为 Bean） → 仍为 `DEFECT`，但影响描述必须写"当前装配下不生效，一旦接入即失效"

---

## 3. 六条性能口径公式（唯一口径）

| `path` | 公式 | 上限来源 | 冒烟可断言 |
| --- | --- | --- | --- |
| `XSS_REWRITE` | `peak ≈ 2 × bodyBytes × concurrentRewrites`；`cpu ≈ Σ(字符串值数 × htmlPatternCount)` | `MAX_REQUEST_SIZE = 50MB`（`filter/GXXssHttpServletRequestWrapper.java:26-30`） | 改写后 `getContentLength()` ≠ `getHeader("Content-Length")` |
| `BODY_CACHE` | `alloc ≈ requestBodyBytes × 1`（每请求 1 次序列化）；`parse ≈ getHttpParam() 调用次数` | 请求体大小 | 同一请求多次读取时，属性只写一次但有 N 次解析 |
| `RATE_LIMIT` | `perCall ≈ O(windowSize) × 2`（`removeIf` + `size`）；`keys ≤ distinctClientIds`（60s 内） | 无上限（静态 Map） | N 个 `clientId` 打点后 `size() == N` |
| `CONCURRENT_TOOLS` | `formatCalls ≈ 2 × tasks`（提交 1 + 完成 1）；`atoms ≈ 6 × (1 + formatCalls)` | 池容量/队列容量 | 日志级别关闭时 `formatCalls` 仍 > 0 |
| `EVENT_DISPATCH` | `snapshotAlloc ≈ 2 × IdentityHashMap + Σ容器节点`；`submissions = subscriberCount` | 深度 64 / 节点 10 万（`event/GXBaseEvent.java:211-223`） | 发布 1 事件给 k 订阅者 → 执行器提交 k 次 |
| `REFLECTION_COPY` | `BeanCopier.create ≈ copies`（缓存不命中）；`treeCost ≈ Σ layer(n) × reflectiveCalls` | 反射缓存 4096（`util/GXCommonUtils.java:452-463`） | 连续 N 次取 `BeanCopier` → `create` 调用 > 1 |

**规则**：

1. 公式必须能代入数字得出量级；只写 O(...) 而无常数因子的，须说明"常数因子可忽略"的理由。
2. 冒烟断言只允许计数/规模/布尔关系；**禁止** `System.currentTimeMillis()` 差值阈值。
3. 若某条路径无法给出上限来源（纯业务相关），标 `UNBOUNDED_BY_DESIGN` 并说明由谁约束。

---

## 4. 缺陷 vs 既定契约 的双条件判定

```text
现象 P：
  ├─ 是否被既有测试断言？（grep: 断言该返回值/该副作用）
  │     ├─ 是 → ESTABLISHED_CONTRACT（即使与直觉冲突）
  │     │        └─ 若同时违反宪法明文条款 → CONTRACT_DEVIATION（要求文档/测试同步，不改实现）
  │     └─ 否 → 继续
  ├─ 是否有明确承诺来源（宪法/AGENTS.md/公开语义/注释）？
  │     ├─ 是 → DEFECT
  │     └─ 否 → RISK（无承诺即无违约）
  └─ 是否可复现？
        ├─ 是 → 保持上述判定
        └─ 否 → UNVERIFIED_ASSUMPTION
```

**应用示例（本轮已定）**：

- `updateCacheExpiredTime` 类"无过期时间不刷新"若被测试锁定 → `ESTABLISHED_CONTRACT`。
- 事件载荷自定义对象不快照 → `DEFECT`（`GXBaseEvent` 类注释与宪法 V"事件载荷在发布时快照化"均有承诺）。
- 缓存无 `KeyGenerator` → `RISK`（模块未承诺提供 key 隔离，但下游会踩）。
- 静态初始化读配置 → `RISK`（无承诺，且已有降级分支，见 research D3）。

---

## 5. 覆盖台账判定值域

| `verdict` | 含义 | 与报告的关系 |
| --- | --- | --- |
| `CLEAN` | 逐文件读过实现，未发现问题 | 无 `findingRefs` |
| `RISK` | 该文件存在风险项 | 必须引用 `F-NN` |
| `DEFECT` | 该文件存在缺陷 | 必须引用 `F-NN` |
| `CONTRACT_CHECKED` | 仅做契约核对（DTO/常量/注解/异常类） | `note` 必填判定依据 |

**禁止值**：`SKIPPED`、`N/A`、`IRRELEVANT`。规格 FR-001 要求"不得跳过"，因此每个文件都有判定；若确因不可抗力无法判定，必须写成 `RISK` 并在 `note` 说明。

---

## 6. 安全默认值的判定矩阵（FR-008）

| 方面 | 判 `DEFECT` 的条件 | 判 `RISK` 的条件 |
| --- | --- | --- |
| 默认密钥 | 未配置时使用**硬编码常量**且功能照常可用 | 未配置时抛异常/拒绝启动 |
| 类/方法白名单 | 无白名单且入参可来自请求/外部 | 有白名单但有绕过路径 |
| 鉴权放行策略 | 默认放行且无显式开关说明 | 默认放行但文档明确要求子类覆盖 |
| 异常响应内部信息 | 正常异常类型即回显内部 message/路径 | 仅在罕见异常类型下回显 |
| 敏感数据流向 | 敏感字段进入日志/响应/Cookie 且不可关闭 | 可关闭但默认开启 |

**证据要求**：`DEFECT` 必须给 `EXECUTABLE`（构造一次调用并观察输出）或 `SOURCE_FACT`（常量 + 使用路径同时给出）。
