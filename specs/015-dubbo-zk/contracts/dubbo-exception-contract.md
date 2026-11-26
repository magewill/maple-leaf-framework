# Contract: Dubbo 异常处理（leaf-base-dubbo-zk）

**Scope**: provider/consumer 双侧 `GXDubboExceptionFilter` 与 Web 层 `GXDubboCallExceptionHandler`
**Consumers**: 接入本模块的下游业务工程、provider 服务实现方、consumer 调用方
**Stability**: 公开签名、SPI 扩展名、响应结构不得变更；行为变更须列入 §5

## 1. Provider 侧异常分类（`GXDubboExceptionFilter.onResponse`）

前置条件：`dubbo.provider.filter` 必须排除内置 `exception`（见 [dubbo-config-observability-contract.md](./dubbo-config-observability-contract.md) §1）。

判定顺序（**顺序即契约**，实现不得重排）：

1. `Result.hasException() == false` → 不处理。
2. `GenericService` 接口 → 仅附加 Result 附件，不改变异常。
3. `getException() == null` → 不处理。
4. `GXSentinelFlowException` → **原样透传**（保留 `code/msg/data`），仅附加 traceId。
5. Sentinel 阻断异常（`com.alibaba.csp.sentinel.slots.block.` 前缀 / `BlockException` 可赋值 / 消息含 `SentinelBlockException: FlowException`）→ 构造 `GXSentinelFlowException(406)`，`data` 含 `X-B3-TraceId`、`errorType=sentinelFlow`、`interfaceName`、`methodName`、`argumentCount`、`parameterTypes`。
   - 该判定会遍历 `cause` 链且**先于**第 8 条的 `GXBusinessException` 透传：若 `GXBusinessException` 的 `cause` 链中出现 `BlockException` 子类，最终会被转换为限流异常并丢弃业务 `code/data`。该顺序为既定契约，由用例 `convertsBusinessExceptionWhoseCauseIsBlockException` 锁定。
6. 非 `RuntimeException` 的 `Exception`（checked）→ 透传。
7. `org.mybatis.spring.MyBatisSystemException` → 透传。
8. `GXBusinessException` → 透传（`data` 非空时原地注入 traceId；`data` 为空时按 §3 处理）。
9. 方法声明异常（`getExceptionTypes()` 可赋值）→ 透传。
10. 其余：记录 ERROR（含 remoteHost / 接口 / 方法 / 异常类型与消息）后：
    - `java.` / `jakarta.` 前缀 → 透传；
    - `RpcException` → 透传；
    - 其他 `RuntimeException` → 包装为 `GXBusinessException(500)`，`data` 含 `errorType=providerRuntime`。
11. 方法签名不可解析（`NoSuchMethodException`）→ **不得提前 return**，按第 10 步处理（fail-closed）。

不变量：

- `data` 中**永不**出现参数值（只有 `argumentCount` 与 `parameterTypes`）。
- filter 自身逻辑异常不得改变已设置的异常语义；只允许 WARN + 计数。

## 2. Consumer 侧（同一实现，显式装配）

`dubbo.consumer.filter` 显式列出 `dubboExceptionFilter` 时，同一实现参与 consumer 链，其效果为"兜底二次分类"：

| 输入 | 期望 |
| --- | --- |
| provider 已分类的 `GXBusinessException` | 原对象返回（不重复包装） |
| provider 已转换的 `GXSentinelFlowException` | 原对象返回 |
| 反序列化得到的未声明 `RuntimeException` | 包装为 `GXBusinessException(500)` |
| provider 侧被内置 filter 包装后的 `RuntimeException` | 包装为 `GXBusinessException(500)`（业务码已在 provider 丢失） |

## 3. `GXBusinessException.data == null` 的已接受限制

`GXBusinessException.data` 为 `final`，无法替换引用。当 `data == null` 且 traceId 非空时：

- 允许构造 `new GXBusinessException(msg, code, Dict{traceId}, original)`，异常类型降级为基类；
- 必须有用例锁定该行为（`wrapsBusinessExceptionWhenDataIsNullToExposeTraceId`）；
- 必须在开发手册登记为已知限制，并注明"如需保真需在 `leaf-base-framework` 增加可写 `data` 的构造协议"（跨模块，另行提案）。

## 4. Web 层 Handler 契约（`GXDubboCallExceptionHandler`）

- 必须声明 `@Order(Ordered.HIGHEST_PRECEDENCE)`，确保优先于基础 `GXExceptionHandler` 的 `@ExceptionHandler(Exception.class)`。
- 必须与基础 advice 同时注册时仍生效（真实 `@SpringBootTest` + `MockMvc` 验证，不得只用 `standaloneSetup` 单 advice）。

| 异常 | `code` | `msg` | `data` |
| --- | --- | --- | --- |
| `RpcException` | 500 | `Dubbo provider error, please contact operations.` | `X-B3-TraceId`、`errorType`、`rpcCode`、`exceptionClass`、`rpcMessage` |
| `GXSentinelFlowException` | 沿用 `e.getCode()`（如 406） | 沿用 `e.getMsg()` | `e.getData()` 的**副本** + `X-B3-TraceId` |

`RpcException` 的 `errorType` 映射（顺序即优先级）：`timeout` → `network` → `serialization` → `forbidden` → `authorization` → `limitExceed` → `noInvoker` → `validation` → `business` → `unknown`；单次处理只计算一次。

## 5. 本次变更清单

| 变更 | 旧行为 | 新行为 | 兼容性 |
| --- | --- | --- | --- |
| `GXSentinelFlowException` 透传 | 被重建为限流异常，`code/data` 丢失或重复转换 | 原对象返回，406/data 保留 | 修复，无调用方需改 |
| 删除裸 `"FlowException"` 消息判定 | 业务消息含该字样即被判 406 | 仅官方包裹消息/BlockException 家族命中 | 修复误判 |
| 反射失败 fail-closed | 静默透传且无日志 | 记录 ERROR 并包装为 500 | 修复，异常从"原始运行时异常"变为结构化业务异常 |
| filter 自身异常计数 | 仅 WARN | WARN + `filterFailureCount()` 递增 | 纯增量 |
| `@Order(HIGHEST_PRECEDENCE)` | `RpcException` 可能被兜底 handler 处理 | 稳定由专用 handler 处理 | 修复 |
| `enrichTraceId` 复制 `Dict` | 原地修改异常 `data` | 返回副本 | 响应内容等价 |
