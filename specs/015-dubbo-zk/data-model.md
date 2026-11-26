# Phase 1 Data Model: leaf-base-dubbo-zk 审查领域模型

**Date**: 2026-10-25 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

本模块不持久化数据，本文件描述的是**审查与契约的领域模型**：发现、异常分类、TraceId 决策链、共享状态、装配契约、性能口径。所有"期望值"即实现阶段的验收口径。

## 1. 实体：审查发现（Finding）

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `id` | `F-01`～`F-23` | 唯一，与 `spec.md` 一致 |
| `severity` | `高` / `中` / `低` | 高危必须有用例证据 |
| `dimension` | `正确性` / `线程安全` / `性能` / `契约` | 单值 |
| `location` | `文件:行区间` | 必须可直接跳转 |
| `trigger` | 文本 | 触发条件（输入形态/装配形态） |
| `impact` | 文本 | 对外可见后果 |
| `evidence` | 文本 | 上游源码行 / Dubbo 源码行 / 实测输出 |
| `verified` | `true` / `false` | `false` 必须写"如何验证" |
| `remediation` | `D-xx` | 指向 plan 决策 |
| `regression` | 用例名 | 旧代码失败、新代码通过 |

### 1.1 发现状态迁移

```text
Open（已定位）→ Verified（有源码/实测证据）→ Remediated（改动 + 用例通过）
                      ↘ Accepted（登记为契约，不改代码，如 F-11/F-12/F-17/F-21）
```

规则：`Accepted` 必须在 `docs/leaf-base-dubbo-zk开发手册.md` 与 `AGENTS.md` 中有对应条目；`Remediated` 必须有能失败的旧代码证据。

### 1.2 本次发现的最终状态（计划）

| 发现 | 目标状态 | 依据 |
| --- | --- | --- |
| F-01 内置 filter 双重激活 | Remediated（启动告警 + 文档） | D-01 / R-01 |
| F-02 advice 被遮蔽 | Remediated（`@Order`） | D-09 / R-08 |
| F-03 `GXSentinelFlowException` 二次包装 | Remediated（优先透传） | D-02 / R-06 |
| F-04 零测试 | Remediated（≥28 用例） | D-15 |
| F-05 Sentinel 误判/漏判 | Remediated（判定收敛） | D-03 / R-07 |
| F-06 反射失败 fail-open | Remediated（fail-closed） | D-04 |
| F-07 filter 自身异常静默 | Remediated（计数 + WARN） | D-05 / R-14 |
| F-08 consumer 挂 provider filter | Accepted（登记 + 用例锁定） | D-12 / R-03 |
| F-09 `onResponse` 无 traceId | Remediated（invocation 回落） | D-06 / R-02 |
| F-10 静态 appName 缓存 | Remediated（共享 + 可重置） | D-08 / R-10 |
| F-11 异常 `Dict` 原地修改 | Accepted（Javadoc + 用例） | D-11 / R-09 |
| F-12 consumer 写 serverAttachment | Accepted（用例锁定 + 登记） | D-12 / R-03 |
| F-13 潜在双 traceId | Accepted（用例证明不成立） | D-13 / R-05 |
| F-14 热路径开销 | Remediated（缓存 + DEBUG） | D-08 / R-15 |
| F-15 `AGENTS.md` 漂移 | Remediated（重写相关章节） | D-07 |
| F-16 绑定失败不可观测 | Remediated（回读校验 WARN） | D-10 |
| F-17 子类降级 | Accepted（同 014） | D-11 / R-09 |
| F-18 多路双读 | Remediated（单次解析） | D-06 |
| F-19 错误类型重复计算 | Remediated（单次） | D-09 |
| F-20 缺开发手册 | Remediated（新增） | D-07 |
| F-21 样例配置 `WRAN` | Accepted（登记，不改） | D-14 / R-13 |
| F-22 `@Log4j2` | Remediated（`@Slf4j`） | D-10 |
| F-23 空值零标注 | Remediated（补 `@Nullable`） | R-11 |

## 2. 实体：异常分类矩阵

观测点：① 最终异常类型 ② 业务字段（`code/msg/data`）是否保留 ③ 是否记录日志 ④ `data` 是否含 traceId ⑤ consumer/Web 侧表现。

| # | 输入异常 | ① 期望类型 | ② 业务字段 | ③ 日志 | ④ traceId | ⑤ Web 表现 |
| --- | --- | --- | --- | --- | --- | --- |
| E-1 | `GXBusinessException`（`data` 非空，cause 链不含阻断异常） | 原对象 | 全保留 | 否 | 注入（原地） | `code/msg/data` 原样 |
| E-2 | `GXBusinessException`（`data == null`） | 新基类实例（**已知限制**） | `code/msg` 保留，`data` 变为仅含 traceId | 否 | 注入 | 同上 |
| E-3 | `GXBusinessException` 子类（`data` 非空） | 原对象（子类） | 全保留 | 否 | 注入 | 同上 |
| E-4 | `GXSentinelFlowException` | **原对象**（本次修复） | `code=406/msg/data` 全保留 | 否 | 注入 | advice 返回 406 + data |
| E-5 | 声明异常（`throws` 中） | 原对象 | 不适用 | 否 | 附件注入 | 由框架 handler 处理 |
| E-6 | checked 非声明异常 | 原对象 | 不适用 | 否 | 附件注入 | 同上 |
| E-7 | `RpcException` | 原对象 | 不适用 | 是（ERROR） | 附件注入 | advice 返回 500 + `data.errorType` |
| E-8 | JDK / `jakarta.` 异常 | 原对象 | 不适用 | 是（ERROR） | 附件注入 | 由框架 handler 处理 |
| E-9 | MyBatis `MyBatisSystemException` | 原对象 | 不适用 | 否 | 附件注入 | 由框架 handler 处理 |
| E-10 | Sentinel `Flow/Degrade/Authority/System/ParamFlow` | `GXSentinelFlowException` | 新建（`errorType=sentinelFlow`、方法/接口/参数类型，无参数值） | 否 | 含 traceId | 406 + data |
| E-11 | 未声明运行时异常（签名可解析） | `GXBusinessException(500)` | 新建（`errorType=providerRuntime`） | 是（ERROR） | 含 traceId | 500 + data |
| E-12 | 未声明运行时异常（签名不可解析） | `GXBusinessException(500)`（本次修复 fail-closed） | 同 E-11 | 是（ERROR） | 含 traceId | 500 + data |
| E-13 | `GenericService` 调用异常 | 原对象 | 不适用 | 否 | 附件注入 | 不改变 |
| E-14 | 业务异常消息含 `FlowException`（cause 链不含阻断异常） | 不被误转（本次修复） | 全保留 | 视类型 | 注入 | 按 E-1 处理 |
| E-15 | `GXBusinessException` 的 cause 链含 `BlockException` 子类 | 按 E-10 转换为 `GXSentinelFlowException(406)` | 业务码丢失（`data` 仅含 `errorType=sentinelFlow` + traceId） | 否 | 含 traceId | 406 + data |

校验规则：

- Sentinel 判定会**遍历 `cause` 链**且先于业务异常分支：`GXBusinessException` 的 cause 链含 `BlockException` 子类时按 E-15 处理（业务码丢失）。该顺序由用例 `convertsBusinessExceptionWhoseCauseIsBlockException` 锁定，并在 `contracts/dubbo-exception-contract.md` §1 登记。

- 矩阵中"新建"的异常只能由 `buildErrorData` 构造，**不得**包含参数值（P-OK-02）；
- `E-2` 的降级行为是已接受限制，必须有专门用例（`wrapsBusinessExceptionWhenDataIsNullToExposeTraceId`）与手册条目；
- `E-10` 的识别不得使用裸 `"FlowException"` 子串（R-07）。

## 3. 实体：TraceId 决策链

来源优先级（命中即返回）：

| 组件 | 方向 | 优先级顺序 | 命中后写回 |
| --- | --- | --- | --- |
| `GXPenetrateAttachmentSelector.select` | consumer 请求 | invocation 附件 → clientAttachment → serverAttachment → MDC → 生成 | 返回 map（由 `ConsumerContextFilter` 写入 invocation） |
| `GXPenetrateAttachmentSelector.selectReverse` | provider 响应 | invocation 附件 → serverResponse → clientResponse → MDC → 生成 | 返回 map（写入 response 附件） |
| `GXDubboClientTraceIdFilter` | consumer | invocation 附件 → clientAttachment → serverAttachment → MDC → 生成 | MDC + invocation + client/serverAttachment + Result |
| `GXDubboServerTraceIdFilter` | provider | invocation 附件 → serverAttachment → clientAttachment → MDC → 生成 | MDC + invocation + client/serverAttachment + Result |
| `GXDubboExceptionFilter` | provider/consumer 异常 | **invocation 附件 → MDC**（本次新增，R-02） | Result 附件 + `GXBusinessException.data` |

恢复语义：

| 场景 | 期望 |
| --- | --- |
| 正常返回 | `finally` 中 `restoreTraceId(original)`；进入前为 null → 清除 |
| 抛异常 | 同上（`finally` 保证） |
| 异步结果回调 | `whenCompleteWithContext` 内二次 `put`/`restore` |
| listener 阶段（异常 filter） | 不依赖 MDC，改读 invocation 附件 |
| 并发 | MDC 为线程私有；静态 appName 用 `volatile` + 可重置 |

一致性规则：同一次调用中 selector 返回的 id、client filter 写入 MDC 的 id、server filter 读到的 id、异常响应 `data` 中的 id **必须相同**（用例 `oneTraceIdAcrossConsumerAndProvider` 锁定）。

## 4. 实体：共享状态清单

| 对象 | 共享范围 | 现状保护 | 收敛后 |
| --- | --- | --- | --- |
| `GXPenetrateAttachmentSelector.appName` | JVM（静态） | `volatile`，读改写非原子 | 保持 `volatile` + `resetAppNameCache()`；`UnknownApp` 不缓存；3 处复用 |
| `GXBusinessException.data`（`Dict`） | 异常实例 | 无（`HashMap`） | 保持原地注入（data 非空）；Javadoc 声明"异常实例不得跨线程共享"；Web 层 enrich 改为复制 |
| `GXSentinelFlowException.data` | 异常实例 | 无 | Web 层 `enrichTraceId` 复制后返回，不改原对象 |
| `RpcContext` 附件 | 线程（每次调用） | Dubbo 在 `ContextFilter`/`ConsumerContextFilter` 结束时清理 | 保持双向写入（Accepted，用例锁定） |
| MDC | 线程 | filter `finally` 成对恢复 | 不变 |
| `FILTER_FAILURE_COUNT` | JVM（静态） | `AtomicLong` | 新增，包级只读访问器 |
| `GXDubboExceptionFilter.SENTINEL_BLOCK_EXCEPTION` | JVM（静态 final） | `Class.forName(..., false, loader)` 一次解析 | 新增，缺失为 null 时跳过该条件 |

## 5. 实体：装配契约

| 配置键 | 必需值 | 缺失后果 | 检测手段 |
| --- | --- | --- | --- |
| `dubbo.provider.filter` | 含 `dubboServerTraceIdFilter`、`dubboExceptionFilter`、`-exception` | 内置 `ExceptionFilter` 包装业务异常，`code/msg/data` 丢失；无 TraceId 写入 MDC | `GXDubboProviderFilterValidator` 启动期 ERROR（仅校验 `-exception`） |
| `dubbo.consumer.filter` | 含 `dubboClientTraceIdFilter`、`dubboExceptionFilter`、`-exception` | 无 TraceId 透传；consumer 侧异常不做兜底分类 | 手册登记 + 用例锁定（不做启动校验，避免与 014 口径分叉） |
| `dubbo.application.serialize-check-status` | `DISABLE` / `WARN` / `STRICT` | 非法值（样例中的 `WRAN`）触发 `IllegalArgumentException` | 手册"已知问题"登记（跨目录，本次不改） |
| `security/serialize.allowlist` | 含 `com.google.common.collect.HashBasedTable` | `WARN` 模式告警、`STRICT` 模式拒绝反序列化 | 手册登记用途（`GXBaseServeApi#count(Table)` 需要） |

## 6. 实体：性能口径

| 指标 | 口径定义 | 修复前 | 修复后上限 |
| --- | --- | --- | --- |
| `envLookups` | 单次调用中 `GXCommonUtils.getEnvironmentValue` 调用次数（含 `generateTraceId` 内部） | 2（有入站）/ 3（无入站） | 1 / 2 |
| `infoLogs` | 单次调用 INFO 级日志条数 | 2 | 0 |
| `attachmentReads` | 单次 filter 调用中 `getObjectAttachment` + `getAttachment` 次数 | 4–6 | ≤4 |
| `traceIdResolutions` | 单次异常 filter 调用中的 traceId 解析次数 | 3 | 1 |
| `errorTypeComputations` | 单次 `RpcException` 处理中 `resolveRpcErrorType` 次数 | 2 | 1 |
| `dictAllocations` | 正常路径 `Dict` 分配次数 | 1 | 1 |

采集方式：用例内用计数替身/`resetAppNameCache()` 前后对照，不引入基准测试框架（宪法附加约束 §3）。

## 7. 校验规则汇总（实现阶段必须同时满足）

1. `GXBusinessException` 在排除内置 filter 时 `code/msg/data` 无损（E-1/E-3）。
2. `GXSentinelFlowException` 在双侧均不被重建（E-4）。
3. 未声明运行时异常 100% fail-closed（E-11/E-12）。
4. 异常响应 `data.X-B3-TraceId` 必有值（R-02 修复后）。
5. 同一次调用零双 traceId。
6. 所有新增/修改日志 ASCII，且核心链路无 INFO。
7. 公开签名、SPI 扩展名、资源路径零变更。
8. NullAway 零错误。
