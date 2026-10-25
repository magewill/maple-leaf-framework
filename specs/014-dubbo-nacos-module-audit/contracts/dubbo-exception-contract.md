# Contract: Dubbo 异常分类与 Filter 装配

**Feature**: 014-dubbo-nacos-module-audit | **Date**: 2026-10-25
**范围**: Provider 侧 `GXDubboExceptionFilter`、Consumer 侧接收语义、Web 层 `GXDubboCallExceptionHandler`

## 1. 强制装配契约（F-01）

Dubbo provider 侧默认会激活内置 `org.apache.dubbo.rpc.filter.ExceptionFilter`（`@Activate(group=provider)`）。该 filter 会把未在接口方法 `throws` 中声明的运行时异常包装为普通 `RuntimeException`。

**契约**：使用本模块的 provider **必须**配置：

```yaml
dubbo:
  provider:
    filter: dubboExceptionFilter,-exception
```

- `-exception`：移除内置 `ExceptionFilter`。
- `dubboExceptionFilter`：启用本模块的异常 filter。
- 未配置时，模块启动 `GXDubboProviderFilterValidator` **输出 ERROR**，明确提示上述配置；`maple.framework.dubbo.filter.check-strict=true` 时启动失败。

**为什么**：否则 `GXBusinessException` 的 `code/msg/data` 在到达 consumer 前被内置 filter 丢弃（已由字节码实测确认，见 `research.md` R-02）。

## 2. 异常分类规则（Provider）

| 异常 | 处置 | 说明 |
| --- | --- | --- |
| `GenericService` 调用 | 跳过分类，仅附 TraceId | 兼容泛化调用 |
| `GXBusinessException` | 原样透传；`data` 非空时写入 `traceId`，`data==null` 时新建同 code/msg 的 `GXBusinessException`（cause=原） | 业务码保真 |
| 方法签名已声明异常（`throws`） | 原样透传 | 不包装 |
| checked 异常（非 RuntimeException） | 原样透传 | 不包装 |
| `RpcException` | 原样透传 | 不重复包装 |
| `java.*` / `jakarta.*` 异常 | 原样透传，记 ERROR | 保留原始类型 |
| `org.mybatis.spring.MyBatisSystemException` | 原样透传 | 类名字面量判定（不引入 MyBatis 依赖） |
| Sentinel `com.alibaba.csp.sentinel.slots.block.*Exception` 全子类 | 转换为 `GXSentinelFlowException(msg, 406, data, cause)` | 见 §3 |
| 其余未声明运行时异常 | 包装为 `GXBusinessException(500, data.errorType=providerRuntime)` | fail-closed |
| 参数类型无法反射解析 | **仍包装**为 `GXBusinessException(500)`（不得 return） | F-04 |

## 3. Sentinel 识别（F-03）

**必须**：
1. 遍历 cause 链；
2. `className.startsWith("com.alibaba.csp.sentinel.slots.block.")` → 命中（覆盖 Flow/Degrade/Authority/System/ParamFlow/Block）；
3. 反射兜底：类路径存在 `BlockException` 时 `isAssignableFrom` 判定；
4. **不得**仅凭消息含 `"FlowException"` 判定（避免把业务异常误判为限流）。

**输出**：`GXSentinelFlowException`，`code = 406`，`data` 含 `traceId`/`errorType=sentinelFlow`/`interfaceName`/`methodName`/`argumentCount`/`parameterTypes`（**不含**参数值）。

## 4. Web 层 Advice 契约（F-02）

- `GXDubboCallExceptionHandler` 必须标注 `@Order(Ordered.HIGHEST_PRECEDENCE)`，确保先于继承自 `GXExceptionHandler` 的兜底 `Exception` handler 被匹配。
- `RpcException` → `GXResultUtils.error(500, "Dubbo provider error, please contact operations.", data)`，`data.errorType` ∈ `{timeout, network, serialization, forbidden, authorization, limitExceed, noInvoker, validation, business, unknown}`。
- `GXSentinelFlowException` → `GXResultUtils.error(e.getCode(), e.getMsg(), data + traceId)`。
- 不得回显堆栈、内部路径、密钥（宪法 III）。

## 5. 不可破坏契约（P-OK-02 / P-OK-03）

- 错误 `data` 只含 `argumentCount`/`parameterTypes`，**不得**含 `arguments`。
- 既有 11 条异常分类用例的断言必须保持通过。
- 异常分类**只增不减**：新增分支不得改变既有分支的最终异常类型与业务字段。
