# Quickstart: 修复验证运行指南

**Feature**: `specs/012-webclient-module-audit` | **Plan**: [plan.md](./plan.md) | **Contracts**: [contracts/](./contracts/)

本指南是**验证/运行手册**：实现完成后按序执行即可证明修复端到端生效。不含实现代码；实现拆解见 `tasks.md`（含 Phase 8.5 复审补充任务 T047–T050）。

## 前置条件

- JDK 21+（构建基线；本仓实测 JDK 25 可编译 `-target 21`）
- Maven 可访问仓库（surefire 3.5.4 需在线拉取一次）
- 无外部中间件需求（测试自带 `ApplicationContextRunner`/`@SpringBootTest`/JDK `HttpServer`）

## 命令总览

```powershell
# 0) 基线（修复前应为 17/0/0；修复后单调增长且全绿）
mvn -pl leaf-base-webclient test

# 1) 编译期静态检查（ErrorProne + NullAway，失败即不合格）
mvn -pl leaf-base-webclient clean install -DskipTests

# 2) 下游契约回归（SC-007：不得受影响）
mvn -pl leaf-base-eureka-client test

# 3) 单用例定位（示例）
mvn -pl leaf-base-webclient test -Dtest=GXWebClientConfigTest
```

## 验证场景（按 spec 验收点逐条对应）

### V1 错误保真（F-02 / FR-011 / SC-004）

1. 起本地 `HttpServer` 返回 500 + 框架统一响应体 `{"code":500,"msg":"remote detail"}`（测试内联，无需手工起停）。
2. 经直连 builder 调用，捕获 `GXBusinessException`。
3. **期望**：`msg` 包含 `remote detail`（修复前为通用文案 "服务器处理错误(5xx): 服务器内部错误"）。
4. 回归用例：`GXWebClientConfigTest`（或 `GXWebClientSpringBootTest`）中"统一响应形状/Spring 形状/纯文本/非法 JSON"四条断言。

### V2 LB 寻址守卫（F-01 / FR-011 / SC-003）

1. 默认（`lbMode`）builder 请求 `https://api.example.com`。
2. **期望**：0 次网络尝试、0 次重试、`GXBusinessException` code=400、消息含"服务名寻址"与主机名（修复前：4 次假尝试 + "服务不可用"）。
3. 服务名（如 `my-service`）请求放行至 LB 解析（`@SpringBootTest` 断言 LB 过滤器在链上）；`webClientDirectBuilder` 直连放行。
4. 带点服务名（`svc.ns.svc.cluster.local`）登记到 `maple.framework.web.client.lb-guard-allow-hosts` 后放行。
5. `maple.framework.enable.webclient-lb-guard=false`（统一开关，优先于别名 `lb-guard-enabled`）时回到旧行为（直连主机得到合成 503，便于兼容期验证）。


### V3 上下文透传（F-03 / FR-010 / SC-004）

1. 请求线程调用：三个上下文头齐全（既有行为，回归锁定）。
2. `@Async` 线程池线程调用（无快照）：**期望**出现限流 WARN `HTTP request dropped context`；同线程内用 `contextWrite(ctx -> ctx.put(GXWebClientContextSnapshot.ATTRIBUTE_KEY, GXWebClientContextSnapshot.capture()))` 挂快照后三头齐全。虚拟线程 executor 为补充形态。
3. 限流窗口：同包覆写 `CONTEXT_WARNING_INTERVAL_MILLIS`（实例字段，非反射）后窗口内连发 5 次仅 1 条 WARN。
4. 切面在无线程上下文的线程校验：结果可区分"无上下文"与"token 无效"。

### V4 资源释放（F-04 / FR-013 / SC-004）

1. `ApplicationContextRunner`/`@SpringBootTest` 启动后关闭上下文。
2. **期望**：`mapleWebClientConnectionProvider` 的 `dispose()` 被调用（探针/包装断言），后台驱逐线程退出。
3. 无池环境连续 10 次 `new GXWebClientConfig().webClientBuilder()`：活跃池 = 1（修复前每次 1 个，泄漏 9 个）。
4. 重复销毁幂等：第二次 `dispose()` 无异常。
5. 销毁后再取 builder：**期望**抛 `GXBusinessException` 且不新建连接池（`data-model §5` 销毁后语义）。

### V5 重试护栏（F-05 / FR-012 / P-02 保持）

1. `HttpServer` 计数：503 + GET → 请求数 4（1+3，P-02 保持）；503 + POST → 请求数 1（修复前 4）。
2. `retry-methods=GET,HEAD,OPTIONS,POST` 恢复旧行为 → POST 请求数回到 4（兼容路径验证）。
3. 耗尽后异常为**原始**失败（非包装）。

### V6 耗时口径与日志（F-07/F-08/F-16 / FR-014）

1. 构造 2 次重试后成功的请求：日志 `durationMs` ≈ 3 次尝试网络耗时之和（不含 2×1s 退避）；`attempts=3`、`retryDelayMs≈2000`。
2. 终态失败请求必有 `HTTP request failed:` ERROR 行（修复前无耗时日志）。
3. TRACE 级别输出的 `X-Auth-Token`/`Cookie` 值为 `******`（掩码映射生效）；User-Agent 无 `os.name`。
4. `LogMessageAsciiTest` 保持绿（日志 ASCII 红线）。

### V7 SPI 接线（F-09 / FR-015）

1. 覆盖 `GXWebClientService.logRequestResponse/logRequestException` 的测试实现计数器。
2. **期望**：成功调用各 +1（全参+简版按契约）、失败调用 `logRequestException` +1；实现抛异常不影响主链路。

### V8 兼容回归（FR-009 / SC-005 / SC-007）

1. 既有 17 条用例全部通过且未删改断言（P-01/P-02/P-04/P-06/P-07 清单核对）。
2. `mvn -pl leaf-base-eureka-client test` 绿（装配顺序与 Bean 契约未破坏）。
3. 旧配置文件（仅冻结键）启动行为与修复前一致（默认值快照断言）。
4. `webClientBuilder(` 4 参方法签名保留、既有 `GXWebClientConfigTest` 直接调用仍编译通过（T050）。

### V9 token 缓存与配置健壮性（F-06/F-14/F-17 / FR-018、FR-025）

1. 同一 `source+secret` 连续 5 次取 token：`authCodeEncode` 调用次数 = 1（60s 窗口内），`source` 或 `secret` 变更后计数 +1（150s 有效期内 ≤3 次）。
2. `max-connections=0` / `timeout-seconds=-1` 启动 fail-fast 且文案含键名；`read-timeout-millis=0` 启动成功（禁用读超时）。
3. `checkTokenValidity` 解码异常记 WARN（仅 `exceptionType`）后返回 false，日志不含敏感值。

### V10 异常处理器文案与可达性（F-13 / FR-026）

1. `WebClientRequestException` → `500` + `"WebClient网络请求异常"`（冻结文案）。
2. `WebClientResponseException` → 远端状态 + 中文文案（原英文已收敛）。
3. 手册声明：本模块客户端的 4xx/5xx 在过滤器内已转 `GXBusinessException`，该分支仅对外来 `WebClient` 生效。

## 旧代码失败证据表（实现期填写，随交付归档）

| F 编号 | 暴露用例（先写） | 旧代码失败断言（记录原文） | 修复后 |
| --- | --- | --- | --- |
| F-01 | `rejectsDirectHostWithClearError` | **实测（HEAD 90fbb137 探针）**：`Expecting AtomicInteger(1) to have value: 0`（旧代码无守卫，请求被放行到下一层） | 绿 |
| F-02 | `preservesUnifiedResponseMsgField` | **实测（HEAD 90fbb137 探针）**：`Expecting throwable message: "服务器处理错误(5xx): 服务器内部错误" to contain: "remote detail" but did not.` | 绿 |
| F-03 | `warnsWhenContextDroppedOnAsyncThread` | **实测（HEAD 90fbb137 探针）**：`expected: 1L but was: 0L`（旧代码无任何上下文丢失告警） | 绿 |
| F-04 | `disposesConnectionProviderOnContextClose` / `reusesSinglePoolForManualBuilder` | **实测（HEAD 90fbb137 探针）**：`destroyMethod` `expected: "dispose" but was: "(inferred)"`；手工路径每次新建池 | 绿 |
| F-05 | `doesNotRetryNonIdempotentMethodByDefault` | **实测（HEAD 90fbb137 探针）**：`Expecting AtomicInteger(4) to have value: 1 but was: 4` | 绿 |
| F-06 | `reusesGeneratedTokenWithinCacheWindow` | **实测（HEAD 90fbb137 探针）**：两次 `generateHttpAuthToken("probe-source",150)` 返回值不同（`expected: "amc2NFZud3lLaFFmd093..."` vs `but was: "aGMxNWtmZFZXQ3laWUtHTmJj..."`） | 绿 |
| F-07 | `durationExcludesRetryBackoff` / `logsFailureWithDurationAndAttempts` | **实测（HEAD 90fbb137 探针）**：2×300ms 退避后 `HTTP response: ... durationMs=694`（含退避）；失败无耗时日志 | 绿 |
| F-08 | `masksSensitiveHeadersInTraceLogs` | **实测（HEAD 90fbb137 探针）**：`Expecting no elements of: ["Request header: X-Auth-Token=probe-secret"] to contain "probe-secret"` | 绿 |
| F-09 | `invokesSpiLoggingHooksOnSuccessAndFailure` | **实测（HEAD 90fbb137 探针）**：`Expecting actual: 0`（成功请求后 `logRequestResponse` 调用次数为 0） | 绿 |
| F-10 | `directBuilderAllowsDirectHost` / `reusesSinglePoolForManualBuilder` | **实测（HEAD 90fbb137 探针）**：`NoSuchMethodException: GXWebClientConfig.webClientDirectBuilder(...)` 与 `loadBalancedWebClientBuilder(...)`（旧代码无直连出口、无显式 LB 入口）；"手工路径泄漏池"为**源码核对**结论（未做线程计数实测） | 绿 |
| F-11 | `readTimeoutInterruptsSlowResponse` / `zeroReadAndResponseTimeoutKeepsLongResponseUsable` | 新增能力（标注"新增"） | 绿 |
| F-12 | —（覆盖缺口本身，由 17 → 65 条用例体现） | — | 绿 |
| F-13 | `handleWebClientResponseExceptionReturnsRemoteStatusResult` | **实测（HEAD 90fbb137 探针）**：`Expecting actual: "WebClient remote service response exception" to contain: "响应异常"` | 绿 |
| F-14 | `checkTokenValidityLogsWarningWithoutSecret` | 新增能力（标注"新增"） | 绿 |
| F-15 | —（治理项，由根 POM 属性、AGENTS.md 与手册体现） | — | 绿 |
| F-16 | `userAgentExcludesServerOperatingSystem` | **实测（HEAD 90fbb137 探针）**：`expected: "Maple-Leaf-WebClient/1.0" but was: "Maple-Leaf-WebClient/1.0 (Windows 10)"` | 绿 |
| F-17 | `rejectsIllegalTimeoutValue` | 新增能力（标注"新增"：原值直入连接池） | 绿 |
| F-18 | `exposesFrozenBeanContract` / `usesFirstServiceBeanWhenMultipleImplementationsExist` | 源码核对：旧代码 `webClient`/`HttpServiceProxyFactory*` 无 `@ConditionalOnMissingBean`；`ApplicationContextRunner` 默认允许 Bean 覆盖，探针未复现（生产 `SpringApplication` 关闭覆盖 → `BeanDefinitionOverrideException`） | 绿 |
| F-19 | —（决策项：不加开关，由手册 §2/§5.4 声明体现） | — | 绿 |

## 预期最终基线

- `mvn -pl leaf-base-webclient test`：**全绿**，用例数 **70**（从 17 单调增长，0 删除/跳过）。
- `mvn -pl leaf-base-webclient clean install -DskipTests`：ErrorProne + NullAway 零错误。
- `mvn -pl leaf-base-eureka-client test`：全绿（SC-007）。
- 交付清单：`spec.md` / `plan.md` / `research.md` / `data-model.md` / `contracts/*` / `quickstart.md` / `audit-report.md` / 证据表（F-01～F-19 全量）/ 兼容说明六处（`retry-methods`、`durationMs` 口径、UA、过滤器链增量、LB 守卫默认开启、错误体 DTO）。
