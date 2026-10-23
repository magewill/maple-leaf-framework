# Contract: 可观测性（指标与日志）

**Feature**: `011-sse-module-audit` | **Requirements**: FR-024

## 1. 指标快照 `GXSseMetrics`

获取方式：`GXSseEmitterService#getMetrics()`（接口 `default` 方法，默认全 0）。

| 字段 | 含义 | 类型 | 单调性 | 采集点 |
| --- | --- | --- | --- | --- |
| `attempts` | 发送尝试次数（含首投与每次重试；**不含**握手帧，握手由 `createSseConnect` 直接写入） | `long` | 递增 | 每次进入实际写操作前 |
| `successes` | 成功写入次数 | `long` | 递增 | `send` 无异常返回 |
| `failures` | 最终失败并关闭连接的次数 | `long` | 递增 | 重试耗尽分支 |
| `retries` | 实际执行的重试次数 | `long` | 递增 | drain 循环内每次重试前 |
| `drops` | 丢弃消息数（详见投递契约 §3） | `long` | 递增 | 每个丢弃分支 |
| `connections` | 当前活跃连接数 | `int` | 可增可减 | 取缓存 `size()` |

**实现约束**

1. 计数用 `LongAdder`（高并发写优于 `AtomicLong`），`connections` 直接读 `ConcurrentHashMap#size()`；
2. 快照为弱一致视图，字段间不做原子协调，不得据此做"守恒"断言；
3. 不引入 Micrometer / Dropwizard 等依赖；下游如需对接监控系统，自行在 `getMetrics()` 上做桥接。

## 2. 日志事件表

| 事件 | 级别 | 触发点 | 必含字段 | 备注 |
| --- | --- | --- | --- | --- |
| `Created SSE connection` | INFO | `createSseConnect` 成功后 | `clientId`、`activeCount` | 每连接每次生命周期 1 条 |
| `Closed SSE connection` | INFO | `closeConnect` / 主动关闭 | `clientId`、`activeCount` | 每连接每次生命周期 1 条 |
| `SSE connection completed/timed out/error` | INFO / INFO / WARN | 容器回调 | `clientId`、（错误原因） | 错误不打印堆栈，仅 `getMessage()` |
| `Removed SSE connection` | **DEBUG** | 条件删除成功 | `clientId`、`activeCount` | 原为 INFO，本次降级 |
| `Skip sending SSE message to inactive connection` | DEBUG | 发送前发现已被替换 | `clientId` | — |
| `Drop SSE message` | DEBUG / WARN | 丢弃消息 | `clientId`、`reason`、`msgId` | 空负载/队列超限用 WARN，其余 DEBUG |
| `SSE message send failed` | **WARN**（首投）/ **WARN**（重试中） | 写失败 | `clientId`、`attempt`、`reason` | 由 ERROR 降为 WARN：失败可恢复 |
| `SSE message dropped after retries` | **ERROR** | 重试耗尽 | `clientId`、`attempts`、`msgId` | 唯一 ERROR 路径，1 条 |
| `SSE heartbeat batch finished` | DEBUG | 每轮心跳结束 | `scanned`、`removed`、`elapsedMs` | 便于定位心跳耗时 |
| `SSE service destroyed` | INFO | `destroy` 完成 | `closedConnections`、`awaitMs` | 停机可观测 |

**日志硬性规则**

1. 所有日志文本使用 ASCII（模块既有红线，`AGENTS.md` §3.1）；
2. DEBUG 级日志必须用 `isDebugEnabled()` 守卫后再拼接字符串（尤其是 `splitMessage` 的分片循环）；
3. 单连接单次消息生命周期内 INFO 条数 ≤ 2（创建 + 关闭），移除/清理一律 DEBUG；
4. 不打印消息体全文（避免敏感数据与日志膨胀），最多打印 `msgId` 与负载长度；
5. 不使用 `System.out`/`System.err`。

## 3. 禁止事项

- 禁止为统计连接数在日志里调用 `sseClientCache.size()` 之外的全量遍历；
- 禁止在高频路径（每条消息）输出 INFO/WARN（空消息/缺失连接两类输入告警除外）；
- 禁止引入新的日志开关配置键（本次不新增配置，见服务面契约 §5）。
