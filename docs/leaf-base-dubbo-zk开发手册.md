# leaf-base-dubbo-zk 开发手册

## 1. 模块定位

`leaf-base-dubbo-zk` 提供 Dubbo 3.3.6 + Zookeeper 场景下的基础 RPC 支撑：

| 能力 | 实现类 | 分组 |
| --- | --- | --- |
| 异常分类与包装 | `GXDubboExceptionFilter` | PROVIDER（显式配置可挂 CONSUMER） |
| Consumer TraceId 透传 | `GXDubboClientTraceIdFilter` | CONSUMER |
| Provider TraceId 恢复 | `GXDubboServerTraceIdFilter` | PROVIDER |
| 跨边界附件选择 | `GXPenetrateAttachmentSelector` | PROVIDER + CONSUMER |
| Web 层异常兜底 | `GXDubboCallExceptionHandler` | Spring MVC Advice |
| ServiceBean 绑定增强 | `GXDubboRpcApiBeanPostProcessor` | Spring BeanPostProcessor |
| Provider filter 装配自检 | `GXDubboProviderFilterValidator` | Spring SmartInitializingSingleton |

依赖：`leaf-base-framework`、`leaf-base-nacos`、`dubbo-spring-boot-starter`、`dubbo-zookeeper-curator5-spring-boot-starter`。
`leaf-base-dubbo-zk` 与 `leaf-base-dubbo-nacos` 是两套互斥注册中心实现，**不得同时进入同一应用类路径**（两者 SPI 扩展名相同）。

## 2. 强制装配配置

```yaml
dubbo:
  provider:
    filter: dubboServerTraceIdFilter,dubboExceptionFilter,-exception   # 强制
  consumer:
    filter: dubboClientTraceIdFilter,dubboExceptionFilter,-exception   # 推荐
```

- `-exception` 用于排除 Dubbo 内置 `ExceptionFilter`。不排除时，内置 filter 会把跨 jar 的 `GXBusinessException` 包装成普通 `RuntimeException`，业务 `code/msg/data` 全部丢失，本模块的透传逻辑失效。
- 未配置 `-exception` 时应用仍可启动，但 `GXDubboProviderFilterValidator` 会输出 ERROR：

  ```text
  DUBBO FILTER: built-in ExceptionFilter is active; GXBusinessException code/msg/data may be lost.
  Configure dubbo.provider.filter=dubboServerTraceIdFilter,dubboExceptionFilter,-exception (current=...)
  ```

- consumer 侧显式挂 `dubboExceptionFilter` 是既定行为（兜底二次分类）：provider 已分类的异常原样返回，未分类的运行时异常包装为 `GXBusinessException(500)`。

## 3. SPI 扩展

| 资源文件 | 扩展名 |
| --- | --- |
| `META-INF/dubbo/org.apache.dubbo.rpc.Filter` | `dubboExceptionFilter`、`dubboClientTraceIdFilter`、`dubboServerTraceIdFilter` |
| `META-INF/dubbo/org.apache.dubbo.rpc.PenetrateAttachmentSelector` | `dubboPenetrateAttachmentSelector` |
| `security/serialize.allowlist` | `com.google.common.collect.HashBasedTable`（`GXBaseServeApi#count(Table)` 入参类型需要） |

扩展名与资源路径属于公共契约，修改视为破坏性变更。

## 4. TraceId 优先级

| 组件 | 优先级（左高右低） |
| --- | --- |
| selector `select`（consumer 请求） | invocation 附件 → clientAttachment → serverAttachment → MDC → 生成 |
| selector `selectReverse`（provider 响应） | invocation 附件 → serverResponse → clientResponse → MDC → 生成 |
| client filter | invocation 附件 → clientAttachment → serverAttachment → MDC → 生成 |
| server filter | invocation 附件 → serverAttachment → clientAttachment → MDC → 生成 |
| exception filter（异常响应） | **invocation 附件 → MDC** |

要点：

- `X-B3-TraceId` 是唯一透传键，selector 只返回该键。
- 生成值形如 `<spring.application.name>:<uuid>`。
- 两个 filter 都在 `finally` 恢复 MDC；异常 filter 运行在 Dubbo `Filter.Listener` 阶段（此时 MDC 已恢复），因此必须从 invocation 附件取 traceId。
- `appName` 通过 `GXPenetrateAttachmentSelector.getCachedAppName()` 读取（JVM 级缓存，`UnknownApp` 不缓存），需要重置时调用 `resetAppNameCache()`。
- consumer 与 provider 均写 `RpcContext` 的 `clientAttachment`/`serverAttachment`，属既定契约（下游业务代码可能读取）；`RpcContext` 由 Dubbo 在调用结束时清理。

## 5. 异常分类矩阵

| 输入 | 结果 | 日志 |
| --- | --- | --- |
| `GXBusinessException` | 原对象透传；`data` 非空时原地注入 traceId | 否 |
| `GXBusinessException`（`data == null`） | 构造基类实例并注入 traceId（类型降级，见 §7） | 否 |
| `GXSentinelFlowException` | 原对象透传，保留 `code/msg/data` | 否 |
| Sentinel `Flow/Degrade/Authority/System/ParamFlow` | 转为 `GXSentinelFlowException(406)` | 否 |
| 声明异常 / checked 异常 | 透传 | 否 |
| `RpcException` | 透传 | 是（ERROR） |
| `java.` / `jakarta.` 异常 | 透传 | 是（ERROR） |
| `org.mybatis.spring.MyBatisSystemException` | 透传 | 否 |
| 其他未声明运行时异常（含签名不可解析） | 包装为 `GXBusinessException(500)` | 是（ERROR） |
| `GenericService` 调用 | 不改变异常，仅附加 Result 附件 | 否 |

`data` 中只允许出现 `X-B3-TraceId`、`errorType`、`interfaceName`、`methodName`、`argumentCount`、`parameterTypes`，**禁止**写入参数值。

Web 层：

| 异常 | 响应 |
| --- | --- |
| `RpcException` | `code=500`、`msg=Dubbo provider error, please contact operations.`、`data.errorType` 等 |
| `GXSentinelFlowException` | 沿用异常 `code/msg`，`data` 返回副本 + traceId |

`GXDubboCallExceptionHandler` 使用 `@Order(HIGHEST_PRECEDENCE)`，确保先于 `GXExceptionHandler` 的兜底 handler 命中。

## 6. 日志与可观测性

- 核心链路（filter / selector）不输出 INFO，诊断信息在 DEBUG。
- 未声明运行时异常、`RpcException`、Web 层异常记录 ERROR；filter 自身异常记录 WARN。
- `GXDubboExceptionFilter.filterFailureCount()`（包级可见）返回 filter 自身抛出异常的累计次数，供测试与后续指标接入。

## 7. 已知问题

1. `GXBusinessException.data` 是 `final` 字段，无法替换引用：当 `data == null` 且需要回传 traceId 时，会构造基类 `GXBusinessException`，子类语义在该路径上丢失（与 `leaf-base-dubbo-nacos` 行为一致）。彻底修复需在 `leaf-base-framework` 增加可写 `data` 的构造协议。
2. 异常数据 map 采用原地注入 traceId（同样因为 `data` 为 `final`），因此**同一个异常实例不得跨线程共享**。
3. `config/application-dubbo-zk.yml.conf` 中 `dubbo.application.serialize-check-status: WRAN` 为拼写错误（Dubbo 仅接受 `DISABLE` / `WARN` / `STRICT`，`SerializeCheckStatus.valueOf` 会抛 `IllegalArgumentException`）。该文件属仓库级样例配置，超出本模块边界，需由样例配置专项目统一修正。

## 8. 不支持范围

- Sentinel 控制台规则管理与原生指标上报（本模块只做异常语义转换）。
- Zookeeper 注册中心端到端服务发现（由 `dubbo-zookeeper-curator5-spring-boot-starter` 提供）。
- Dubbo 鉴权 filter、消费者路由策略。

## 9. 测试说明

```powershell
mvn -o -pl leaf-base-dubbo-zk test          # 14 个测试类 / 83 条用例，全部离线
mvn -o -pl leaf-base-dubbo-zk -am test      # 上游快照缺失时连带构建
```

- 全部用例不连接 Zookeeper/Nacos；`@SpringBootTest` 使用最小配置类 `GXDubboTestApp`，不启用自动装配。
- Sentinel 与 MyBatis 异常通过 `src/test/java` 下的桩类模拟，不引入额外依赖。
- 手工验证：删除 `-exception` 后启动应出现 §2 的 ERROR 日志；provider 抛 `GXBusinessException(4001)` 时 consumer 应收到 `code=4001` 且 `data` 含 `X-B3-TraceId`。
