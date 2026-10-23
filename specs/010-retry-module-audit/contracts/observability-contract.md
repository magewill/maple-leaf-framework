# 契约：重试可观测性（日志与开关）

**Feature**: `010-retry-module-audit` | **依据**: FR-019、FR-021、FR-022；research D-08；宪法 IV/V；`AGENTS.md` §3.1（日志必须 ASCII）

## 1. 日志事件表（`GXRetryListener`）

| 事件 | 回调 | 级别 | 必含字段 | 触发条件 | 守卫 |
| --- | --- | --- | --- | --- | --- |
| 重试开始 | `beforeRetry` | INFO | `retryable`, `retryCount` | **首次重试**（`retryCount == 1`，D-08） | `log.isInfoEnabled() && shouldLog("INFO")` |
| 尝试失败 | `onRetryableExecution` | WARN | `retryable`, `retryCount`, `exceptionType`, `exceptionMessage`（verbose 时增加 `exceptionCount`） | 任一次尝试失败（含"不会重试"的首次失败），文案为 `retryable execution failed` | `log.isWarnEnabled() && shouldLog("WARN")` |
| 尝试失败堆栈 | 同上 | DEBUG | 完整 `Throwable` | 同事件 + `verbose=true` | `log.isDebugEnabled()` |
| 重试成功 | `onRetrySuccess` | INFO | `retryable` | 重试后成功（首次即成功不触发） | `log.isInfoEnabled() && shouldLog("INFO")` |
| 策略耗尽 | `onRetryPolicyExhaustion` | WARN | `retryable`, `retryCount`, `lastExceptionType`, `lastExceptionMessage` | 重试预算用完 | `log.isWarnEnabled()` |
| 中断 | `onRetryPolicyInterruption` | WARN | 同上 | 退避期线程被中断 | 同上 |
| 超时 | `onRetryPolicyTimeout` | WARN | 同上 | 配置了 `timeout` 且将超时 | 同上 |
| 终止堆栈 | 三个终止回调 | DEBUG | 完整 `RetryException` | `verbose=true` | `log.isDebugEnabled()` |

**禁止**：新增非 ASCII 日志文案；在 `finally`/循环内无条件打日志；在监听器里做阻塞 I/O 或获取容器 Bean。

## 2. `retryable` 描述符

- 名称非空白 → 直接使用（`GXRetryUtil` 恒为 `"GXRetryUtil"`，**不允许**为每条业务操作单独取名，保持既有契约）。
- 名称为空白 → 使用类简名（删除缓存后每次 `getSimpleName()`，无分配压力）。
- **不再**做 lambda 检测与类型缓存（D-08；原实现永不生效）。

## 3. 级别开关（系统属性，沿用现状通道）

| 属性 | 默认 | 语义（修复后） | 变更 |
| --- | --- | --- | --- |
| `maple.framework.retry.log.level` | `INFO` | 阈值：`OFF` < `ERROR` < `WARN` < `INFO` < `DEBUG`；非法值按 `INFO` | **修复**：原实现 `OFF`/`TRACE` 落入默认分支导致"全开"；改为阈值后 `OFF` 全关 |
| `maple.framework.retry.log.verbose` | `false` | `true` 时输出 DEBUG 级堆栈与 `exceptionCount` | 不变 |

- 读取时机由"类初始化时快照"改为**每次调用重读**（`volatile` + `System.getProperty`），从而支持测试与运维在运行期调整；代价为每个事件一次 `System.getProperty`（字符串常量键，开销可忽略）。
- 该通道**不引入 Spring 配置键**（FR-018/Q4），偏差已登记于本契约，不再声称"可用 yml 配置"。

## 4. 已知偏差登记（宪法 IV）

- 宪法 IV 要求"新增配置项必须 `@ConfigurationProperties` + 元数据"。本模块的日志级别/verbose **不是新增项**，且本计划明确否决新增配置属性，因此不做属性化；手册必须如实写"只能通过 `-D` 或 `System.setProperty` 配置，无法按 profile 区分"。
- 宪法 IV 要求"装配过程输出明确日志"。本模块保持组件扫描装配，**不新增装配日志**（无开关可降级，见 Q4），偏差同样写入手册"模块如何被装配"一节。

## 5. 文档必须同步的三张表

1. 本文件 §1 的日志事件表（手册"日志与排障"节）。
2. `retry-api-contract.md` §3 的 `retryCount` 三入口对照表（手册"上下文与计数"节）——对应 FR-023 的兼容说明。
3. 默认异常集与排除示例（手册"默认异常集与风险"节）——对应 FR-022，示例必须可直接粘贴编译：

```java
Map<Class<? extends Throwable>, Boolean> retryExceptions = Map.of(
        Exception.class, true,
        GXBusinessException.class, false,
        InterruptedException.class, false);
GXRetryUtil.retryOperation(callback, null, retryExceptions);
```
