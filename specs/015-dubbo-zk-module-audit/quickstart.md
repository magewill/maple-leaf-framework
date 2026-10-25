# Quickstart: leaf-base-dubbo-zk 审查结果的验证指南

本文件只描述"如何验证"，实现细节见 [plan.md](./plan.md)、契约见 [contracts/](./contracts/)。

## 1. 前置条件

- JDK 21+、Maven 3.9+（Windows PowerShell 为默认 shell）
- 本地仓库（`%USERPROFILE%\.m2\settings.xml` → `F:\repository`）已安装 `leaf-base-framework`、`leaf-base-nacos` 的 `4.3.0-SNAPSHOT`
- 无需 Zookeeper / Nacos 服务（全部用例离线）

## 2. 基线（改动前）

```powershell
cd e:\project\java\maple-leaf-framework
mvn -o -pl leaf-base-dubbo-zk test
```

预期：`Tests run: 0`（模块无 `src/test`）——这就是 F-04 的实测证据。

## 3. 实现完成后（改动后）

```powershell
# 编译 + 测试（NullAway 会在编译期报错，任何错误即不合格）
mvn -o -pl leaf-base-dubbo-zk test

# 若上游快照缺失
mvn -o -pl leaf-base-dubbo-zk -am test

# 只看结果行
mvn -o -pl leaf-base-dubbo-zk test 2>&1 | Select-String -Pattern "Tests run:|BUILD"
```

预期：`Tests run: 83, Failures: 0, Errors: 0, Skipped: 0` 与 `BUILD SUCCESS`。

## 4. 按发现逐项验证

| 发现 | 验证用例（同包） | 断言要点 |
| --- | --- | --- |
| F-01 | `GXDubboFilterChainBehaviorTest#builtInExceptionFilterActiveLosesBusinessCode` / `#builtInExceptionFilterExcludedKeepsBusinessCode` | 内置 filter 先跑 → `GXBusinessException` 消失；排除后 → 原对象 + `code` + `data` 全保留 |
| F-02 | `GXDubboCallExceptionHandlerSpringBootTest#rpcExceptionIsHandledByDedicatedAdvice` | 真实上下文 + MockMvc → `data.errorType` 存在（非"系统内部错误"） |
| F-03 | `GXDubboExceptionFilterTest#keepsSentinelFlowExceptionUnchanged` | 类型与 `code=406`、`data` 原样 |
| F-04 | 全量用例数 | 从 0 增长到 83 |
| F-05 | `#convertsDegradeAndAuthorityExceptions`、`#convertsAllSentinelBlockSubtypes`、`#doesNotMisclassifyBusinessMessageContainingFlowException`、`#convertsBusinessExceptionWhoseCauseIsBlockException` | Flow/Degrade/Authority/System/ParamFlow 五类均 → 406；业务消息含 `FlowException` → 不被转换；cause 链含阻断异常 → 按契约转换 |
| F-06 | `#wrapsUndeclaredRuntimeWhenMethodSignatureUnresolvable` | 参数类型不匹配 → 仍包装为 `GXBusinessException(500)` |
| F-07 | `#recordsFilterInternalFailure` | 构造 filter 内部失败 → 计数 +1、原异常不受影响 |
| F-08 | `GXDubboFilterChainBehaviorTest#consumerSideWrapsUnclassifiedRuntimeException` | consumer 侧只做兜底分类，provider 已分类异常原样返回 |
| F-09 | `#attachesTraceIdFromInvocationWhenMdcCleared` | 清空 MDC 后仍能取到 invocation 附件里的 traceId |
| F-10 | `GXDubboSharedStateContractTest#resetAppNameCacheAllowsSecondContextToBeRead` | 换 Spring 上下文后 `resetAppNameCache()` 能读到新值 |
| F-13 | `GXDubboTraceIdFilterTest#oneTraceIdAcrossConsumerAndProvider` | 四段 id 完全相等 |
| F-14 | `GXDubboPerformanceContractTest#readsEnvironmentOnlyOnceForCachedAppName` / `#corePathHasNoInfoLogs` / `#attachmentReadsStayWithinBudget` | 环境只读一次、热路径无 INFO、attachment 读取 ≤4 |
| F-16 | `GXDubboRpcApiBeanPostProcessorTest#keepsStartupAliveWhenBindingIsNotEffective` | `getServeServiceClass()` 与目标不一致 → WARN、返回原 bean |
| F-19 | `GXDubboCallExceptionHandlerTest#rpcExceptionSubtypesKeepStableCode` | 9 个子类 `code=500` + `data.errorType` 稳定 |
| F-22 | `AsciiSourceGuardTest` + 代码审查 | `src/main/java` 全 ASCII、无 `@Log4j2` |
| F-23 | 编译 | NullAway 零错误 |
| 装配自检 | `GXDubboProviderFilterValidatorSpringBootTest#validatorIsRegisteredAndContextStillStarts` | 真实上下文注册校验器且不阻断启动 |

## 5. 手工装配验证（可选，需要真实应用）

1. 用 `config/application-dubbo-zk.yml.conf` 启动 provider，**故意删除** `-exception`：
   - 预期日志出现 `DUBBO FILTER: built-in ExceptionFilter is active ...`（ERROR），应用仍能启动。
2. 恢复 `-exception` 后重启：不再出现该 ERROR。
3. provider 抛 `GXBusinessException(4001, "order-conflict")`，consumer 捕获：
   - 预期 `e.getCode() == 4001` 且 `e.getData()` 含原字段与 `X-B3-TraceId`。

## 6. 性能口径复算（不追求绝对吞吐）

| 指标 | 复算方式 |
| --- | --- |
| `envLookups` | `resetAppNameCache()` 后连续两次 filter 调用，用测试替身统计 `getEnvironmentValue` 调用次数 |
| `infoLogs` | 代码审查 + 日志级别断言（核心链路无 `log.info`） |
| `traceIdResolutions` | 单次 `onResponse` 内 traceId 解析次数（代码审查 + 用例覆盖） |
| `attachmentReads` | 单次 `onResponse` 中 `getObjectAttachment` + `getAttachment` 次数（用例 `attachmentReadsStayWithinBudget`，上限 4） |
| `errorTypeComputations` | 单次 `RpcException` 处理只调用一次 `resolveRpcErrorType` |

## 7. 交付检查单

- [ ] `mvn -o -pl leaf-base-dubbo-zk test` 全绿，用例 ≥28，0 跳过
- [ ] 编译期 NullAway 零错误
- [ ] `git status` 只包含 `leaf-base-dubbo-zk/**` 与 `docs/leaf-base-dubbo-zk开发手册.md`、`specs/015-*/**`
- [ ] `AGENTS.md` 与实现一致（能力清单、装配配置、Sentinel 规则、测试基线）
- [ ] 手册包含：模块定位、SPI 扩展名、TraceId 优先级表、异常矩阵、强制装配、已知问题、不支持范围
- [ ] 未改 `config/`（`WRAN` 已按 D-14 登记）
