# leaf-base-dubbo-nacos 开发手册

## 1. 模块定位

`leaf-base-dubbo-nacos` 是 Maple Leaf Framework 中基于 Dubbo 3.3.6 + Nacos 的 RPC 基础支持模块，提供：

| 能力 | 类型 | 说明 |
| --- | --- | --- |
| Consumer 侧 TraceId 透传 | `GXDubboClientTraceIdFilter` | 把当前 TraceId 写入 `Invocation` 与 attachment |
| Provider 侧 TraceId 提取/恢复 | `GXDubboServerTraceIdFilter` | 从 attachment 恢复 MDC，调用结束后还原 |
| 附件透传选择 | `GXPenetrateAttachmentSelector` | 请求/响应阶段只透传 TraceId |
| Provider 异常分类 | `GXDubboExceptionFilter` | 业务异常保真、Sentinel 转 `GXSentinelFlowException`、其余包装 |
| Web 层异常兜底 | `GXDubboCallExceptionHandler` | `RpcException` / `GXSentinelFlowException` 结构化返回 |
| 启动期装配校验 | `GXDubboProviderFilterValidator` | 检查 provider filter 是否排除内置 `ExceptionFilter` |

本模块**不承载**：具体业务 RPC 实现、业务编排、Sentinel 控制台规则管理、Dubbo + Sentinel 适配（后者由 `leaf-base-dubbo-nacos-sentinel` 负责）。

## 2. 依赖与装配

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-dubbo-nacos</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

- 传递依赖：`dubbo-spring-boot-starter:3.3.6`、`dubbo-nacos-spring-boot-starter:3.3.6`、`leaf-base-nacos`（含 `nacos-client`）。
- Filter / Selector 通过 `@Activate` + `META-INF/dubbo/*` SPI 装配，无需手工注册。
- `GXDubboRpcApiBeanPostProcessor`、`GXDubboCallExceptionHandler`、`GXDubboProviderFilterValidator` 通过组件扫描（`cn.maple`）装配。

## 3. 强制装配配置（务必配置）

Dubbo 内置 `org.apache.dubbo.rpc.filter.ExceptionFilter` 与 `GXDubboExceptionFilter` 都是 `@Activate(group = provider)`。**未排除内置 filter 时**，内置 filter 会把不在接口方法 `throws` 中声明的运行时异常（包括 `GXBusinessException`）包装成普通 `RuntimeException`，业务 `code` / `msg` / `data` 会丢失。

**必须配置**：

```yaml
dubbo:
  provider:
    filter: dubboExceptionFilter,-exception
```

- `-exception`：移除内置 `ExceptionFilter`。
- `dubboExceptionFilter`：启用本模块的异常 filter。

模块启动时 `GXDubboProviderFilterValidator` 会读取 `dubbo.provider.filter`；缺失或未包含 `-exception` 时输出 **ERROR**（不阻断启动）：

```text
DUBBO FILTER: built-in ExceptionFilter is active; GXBusinessException code/msg/data may be lost.
Configure dubbo.provider.filter=dubboExceptionFilter,-exception (current=...)
```

## 4. SPI 扩展名契约（不得重命名）

| 资源文件 | 扩展名 | 实现类 | 分组 |
| --- | --- | --- | --- |
| `META-INF/dubbo/org.apache.dubbo.rpc.Filter` | `dubboExceptionFilter` | `GXDubboExceptionFilter` | provider |
| 同上 | `dubboClientTraceIdFilter` | `GXDubboClientTraceIdFilter` | consumer |
| 同上 | `dubboServerTraceIdFilter` | `GXDubboServerTraceIdFilter` | provider |
| `META-INF/dubbo/org.apache.dubbo.rpc.PenetrateAttachmentSelector` | `dubboPenetrateAttachmentSelector` | `GXPenetrateAttachmentSelector` | provider + consumer |

> 修改扩展名、实现类路径或资源位置属于兼容性变更，必须同步更新本手册与 `AGENTS.md`。

## 5. TraceId 优先级

| 阶段 | 优先级（高 → 低） |
| --- | --- |
| Consumer 请求 | `Invocation` attachment → client attachment → server attachment → MDC → `generateTraceId()` |
| Provider 请求 | `Invocation` attachment → **server** attachment → client attachment → MDC → `generateTraceId()` |
| Selector `select`（请求） | `Invocation` attachment → client attachment → server attachment → MDC → `generateTraceId()` |
| Selector `selectReverse`（响应） | `Invocation` attachment → **server** attachment → client attachment → MDC → `generateTraceId()` |

- consumer/provider 与 select/selectReverse 的顺序差异是**有意镜像**，不得统一。
- 进入时保存 `originalTraceId`，`finally` 中 `restoreTraceId` 恢复；异步结果在回调线程二次保存/恢复。
- `spring.application.name` 由 `GXPenetrateAttachmentSelector.getCachedAppName()` 统一缓存；测试/多上下文可用 `resetAppNameCache()` 隔离。

## 6. 异常分类（Provider）

| 异常 | 处置 |
| --- | --- |
| `GenericService` 调用 | 跳过分类，仅附 TraceId |
| `GXBusinessException` | 原样透传；`data` 非空写入 `traceId`，为 null 时新建同 code/msg 的异常（cause=原） |
| 方法签名已声明的异常 | 原样透传 |
| checked 异常（非 RuntimeException） | 原样透传 |
| `RpcException` | 原样透传 |
| `java.*` / `jakarta.*` 异常 | 原样透传，记 ERROR |
| `org.mybatis.spring.MyBatisSystemException` | 原样透传（类名字面量判定，不引入 MyBatis 依赖） |
| Sentinel `com.alibaba.csp.sentinel.slots.block.*Exception` | 转换为 `GXSentinelFlowException(msg, 406, data)` |
| 其余未声明运行时异常 | 包装为 `GXBusinessException(500, data.errorType=providerRuntime)` |
| 方法签名无法反射解析 | **仍包装**（fail-closed），不得透传 |

> 错误 `data` 只含 `traceId` / `errorType` / `interfaceName` / `methodName` / `argumentCount` / `parameterTypes`，**不含参数值**。

Web 层：`GXDubboCallExceptionHandler` 标注 `@Order(Ordered.HIGHEST_PRECEDENCE)`，确保 `RpcException` / `GXSentinelFlowException` 不被基础 `GXExceptionHandler` 的兜底 `Exception` handler 抢先。

## 7. 配置键

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `dubbo.provider.filter` | 未配置 | **必须** `dubboExceptionFilter,-exception` |
| `dubbo.consumer.filter` | 未配置 | 可选；未配置时使用默认 `@Activate` 扩展 |
| `spring.application.name` | — | TraceId 前缀与日志 appName（模块只缓存读取） |

## 8. 序列化安全白名单

`src/main/resources/security/serialize.allowlist` 当前仅一行：

```text
com.google.common.collect.HashBasedTable
```

- 用途：Dubbo 序列化安全校验白名单，允许该类型跨进程传输。
- 维护要求：新增跨进程传输对象若触发安全校验，需评估后在此追加，并在 PR 中说明来源、用途与安全影响；**不得**为绕过校验而放宽为通配。

## 9. 不支持范围

| 能力 | 说明 |
| --- | --- |
| Sentinel 原生指标/控制台规则管理 | 由 `leaf-base-dubbo-nacos-sentinel` 负责 |
| 注册中心服务发现端到端验证 | 需真实 Nacos 环境；单元/集成测试默认离线（`injvm`/直连 + 关闭 Nacos 自动配置） |
| 非 HTTP 上下文（异步线程、消息消费）的 TraceId 自动生成 | 由调用方显式设置或依赖 Filter 的兜底生成 |

## 10. 测试

```powershell
mvn -o -pl leaf-base-dubbo-nacos test
```

- 默认离线：`spring.cloud.nacos.config.import-check.enabled=false`、`spring.cloud.nacos.discovery.enabled=false`。
- 真实 Dubbo 链用 `injvm`/直连；异常/上下文现场用可注入的 `Invoker`/`Invocation`/`Result` 替身。
- 需要真实 Nacos（注册中心服务发现端到端）时，先向维护者申请连接信息；缺环境用例须优雅跳过。
- 既有测试基线：9 个测试类 / ≥31 条用例，只增不减。

## 11. 兼容性

| 变更 | 迁移 |
| --- | --- |
| 启动 ERROR（未排除内置 filter） | 配置 `dubbo.provider.filter=dubboExceptionFilter,-exception` |
| `GXDubboCallExceptionHandler` 增加 `@Order` | `RpcException` 改由专用 handler 返回 `data.errorType` |
| TraceId 日志 INFO → DEBUG | 需要时开 DEBUG |
| Sentinel 识别扩大到全部 `BlockException` 子类 | 返回 `GXSentinelFlowException(406)` |
| `@Log4j2` → `@Slf4j` | 日志门面切换，输出目标不变 |
