# Contract: `GXSseEmitterService` 服务面

**Feature**: `011-sse-module-audit` | **Stability**: 公开契约（宪法 II） | **Violations**: 见 §5

## 1. 方法面（改造后）

| 方法 | 签名变化 | 空值契约 | 失败语义 |
| --- | --- | --- | --- |
| `createSseConnect` | 不变 | `clientId` 可为 `null`/空白 → 服务端生成 UUID | 已销毁 → 抛 `GXBusinessException`；真实首帧失败 → 抛 `GXBusinessException`；**被并发替换 → 正常返回**（D-02） |
| `getSseEmitterByClientId` | 不变 | 返回 `@Nullable SseEmitter`；`clientId` 空白/`null` → `null` | 不抛异常 |
| `sendMessageToAllClient` | 不变 | `msg` 为 `null`/空白 → WARN + 返回 | 不抛异常（已销毁 → DEBUG + 返回） |
| `sendMessageToOneClient` | 不变 | `clientId`/`msg` 空白 → WARN + 返回 | 不抛异常（已销毁 → DEBUG + 返回） |
| `closeConnect` | 不变 | `clientId` 空白 → 静默返回 | 不抛异常；幂等 |
| `splitMessage` | 不变 | `msg` 为 `null`/空白（`isBlank`）→ 空列表 | 不抛异常；`length` 钳制到 `[1, 100]` |
| `getMetrics` | **新增 `default` 方法** | 返回非 `null` 快照 | 默认实现返回全 0 + `connections=0` |

实现类额外公开（不进接口，避免破坏自定义实现）：`sendHeartbeat(@Nullable String)`、`getActiveConnectionCount()`、`closeConnections(@Nullable Function<String, Boolean>)`、`destroy()`、`getMetrics()`（覆盖 default）。

## 2. 超时与生命周期

1. `timeout` 单位为**秒**，实际值 `max(timeout, 60)`，写入 `SseEmitter` 的超时（毫秒）。
2. 超时是**绝对超时**：从请求开始计时，写入与心跳均不重置（R-04）。连接存活上限 = 实际超时值。
3. `closeConnect` 会先发 `event: close` 再 `complete()`；**不阻止浏览器按 `retry` 自动重连**（无 204，无抑制手段）。
4. `destroy()` 幂等；执行顺序为"标记销毁 → 关闭全部连接 → 清空管线 → 关闭执行器 → 等待终止（5s）"（D-06）。
5. 销毁后 `createSseConnect` 抛 `GXBusinessException`；已建立的连接不受后续 `send*` 影响（`send*` 静默降级）。

## 3. 空值契约（JSpecify）

- 包级仍为 `@NullUnmarked`（宪法 V 的现状约定），模块内**所有可能为空**的公开参数与返回值显式标注 `@Nullable`：
  - `getSseEmitterByClientId(@Nullable String) → @Nullable SseEmitter`
  - `sendHeartbeat(@Nullable String)`
  - `closeConnections(@Nullable Function<String, Boolean>)`
  - `GXSseMessageInnerReqDto.data`、`clientId`、`eventName`
- `data == null` 的消息**不入队、不发送**，计 `drops` 并记 WARN（不再抛 NPE，D-08）。
- NullAway 在 `AnnotatedPackages=cn.maple` 下编译零错误。

## 4. 事件线格式（对外可见，不得变更）

| 字段 | 现有取值 | 本次变更 |
| --- | --- | --- |
| 初始帧 | `id: 201` + `data: <clientId 的 JSON 字符串>`（无 `event:`） | 不变 |
| 业务帧 | `event: Push Msg`、`id: <msgId>`、`retry: 2000`、`data: {"message":"..."}`、注释行 `::<clientId>` | 拆分发送时注释行变为 `::chunk=<i>/<n>:<clientId>`（序号在前、clientId 在后）；同一条原始消息的 `msgId` 在分片间共享，未拆分时沿用 `@Builder` 默认值 |
| 心跳帧 | 注释行 `:heartbeat` | 不变 |
| 关闭帧 | `event: close` + `data: Connection closed by server`（`text/plain`） | 不变 |

## 5. 已知偏差（宪法 IV）

本模块**不引入** `maple.framework.enable.sse` 开关：
- 该模块为既有常开能力（`@Service` 恒装配，构造即启动调度线程），新增开关默认关闭会让既有下游在升级后静默失去 SSE 能力；
- 引入开关需同步 `additional-spring-configuration-metadata.json`，而模块当前无 `src/main/resources`，改动面会溢出"只改本模块 + 2 份文档"的边界；
- 偏差已登记：后续若需要按需装配，单独开特性处理（迁移路径：`@ConditionalOnProperty` + 默认 `true` 的兼容开关）。

## 6. 兼容性清单

| 变更 | 二进制兼容 | 源兼容 | 行为兼容 |
| --- | --- | --- | --- |
| 接口新增 `default getMetrics()` | 是 | 是 | 是 |
| 新增 `@Nullable` 标注 | 是 | 是 | 是 |
| 被并发替换方不再抛 500 | 是 | 是 | 否（见 plan.md 兼容声明） |
| 首投失败后消息改排队异步投递 | 是 | 是 | 否（`void` 方法无同步保证，未承诺过） |
| `splitMessage(" ")` 返回空列表 | 是 | 是 | 否 |
| 注释行追加分片序号 | 是 | 是 | 否（comment 无规范语义） |
