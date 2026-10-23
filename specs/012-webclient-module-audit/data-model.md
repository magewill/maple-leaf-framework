# Data Model: leaf-base-webclient 修复领域模型（Phase 1）

**Date**: 2026-10-23 | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

本文件定义修复方案涉及的领域模型（配置、请求头、错误映射、重试、资源生命周期、上下文快照、测试矩阵）。坐标引用：`GXWebClientConfig` = `leaf-base-webclient/src/main/java/cn/maple/webclient/config/GXWebClientConfig.java`。

---

## 1. GXWebClientProperties（配置属性模型）

**职责**：全部 `maple.framework.web.client.*` 键的类型安全绑定（R-08）。键名/默认值与现状 1:1 冻结；新增键可选。

| 字段 | 配置键 | 类型 | 默认值 | 校验规则 | 来源 |
| --- | --- | --- | --- | --- | --- |
| timeoutSeconds | `...timeout-seconds` | int | 30 | ≥1，违规 fail-fast | 冻结（E-23 口径） |
| memoryBufferSizeMb | `...memory-buffer-size-mb` | int | 16 | ≥1 | 冻结 |
| maxConnections | `...max-connections` | int | 500 | ≥1（0/负数拒绝，F-17） | 冻结 |
| acquireTimeoutMillis | `...acquire-timeout-millis` | int | 3000 | ≥1 | 冻结 |
| pendingAcquireMaxCount | `...pending-acquire-max-count` | int | 1000 | ≥1 | 冻结 |
| idleTimeoutMillis | `...idle-timeout-millis` | int | 30000 | ≥0 | 冻结 |
| maxLifeTimeMillis | `...max-life-time-millis` | int | 300000 | ≥0 | 冻结 |
| evictInBackgroundSeconds | `...evict-in-background-seconds` | int | 120 | ≥1 | 冻结 |
| retryMaxAttempts | `...retry-max-attempts` | int | 3 | ≥0（0=关闭重试） | 冻结 |
| retryDelayMillis | `...retry-delay-millis` | int | 1000 | ≥0 | 冻结 |
| retryMethods | `...retry-methods` | Set\<HttpMethod> | `GET,HEAD,OPTIONS` | 解析失败 fail-fast；空=不重试 | **新增**（R-04，行为收敛见 Complexity ①） |
| userAgentSuffix | `...user-agent-suffix` | String | `""` | 可空 | **新增**（R-06） |
| connectTimeoutMillis | `...connect-timeout-millis` | Integer | null→回退 timeoutSeconds×1000 | ≥1 | **新增**（R-08 可选拆分） |
| readTimeoutMillis | `...read-timeout-millis` | Integer | null→回退 timeoutSeconds×1000 | ≥0（0=禁用读超时，流式安全） | **新增** |
| writeTimeoutMillis | `...write-timeout-millis` | Integer | null→回退 timeoutSeconds×1000 | ≥1 | **新增** |
| responseTimeoutMillis | `...response-timeout-millis` | Integer | null→回退 timeoutSeconds×1000 | ≥0（0=禁用） | **新增** |
| lbGuardEnabled | `...lb-guard-enabled` | Boolean | `true` | 不可为 null | **新增**（F-01 逃生口，Complexity ⑤；默认开启属缺陷收敛；为 `enable.*` 统一开关的兼容别名） |
| lbGuardAllowHosts | `...lb-guard-allow-hosts` | Set\<String> | 空集合 | 空白项忽略并按集合去重 | **新增**（带点服务名白名单，如 `svc.ns.svc.cluster.local`） |
| frameworkLbGuardEnabled | `maple.framework.enable.webclient-lb-guard`（不在 `web.client` 前缀下） | Boolean | `true` | 可缺省；配置后<b>覆盖</b> `lbGuardEnabled` | **新增**（宪法 IV 统一开关，Converge T051） |

**键总数 = 18（10 冻结 + 8 新增）+ 1 个统一开关**：`GXWebClientProperties` 字段数（含 `frameworkLbGuardEnabled`）、`additional-spring-configuration-metadata.json` 条目数（**19**）、本表行数三者必须相等（T004/T051 机械核对）。

**"0" 的语义按键定义（替代笼统的"0/负数 fail-fast"）**：

| 键 | 0 的含义 |
| --- | --- |
| `timeout-seconds` / `memory-buffer-size-mb` / `max-connections` / `acquire-timeout-millis` / `pending-acquire-max-count` / `evict-in-background-seconds` / `connect-timeout-millis` / `write-timeout-millis` | 非法 → 启动期 fail-fast（文案含键名；非数字同样 fail-fast） |
| `idle-timeout-millis` / `max-life-time-millis` | 合法：0 = 连接不因该条件被驱逐/回收 |
| `read-timeout-millis` / `response-timeout-millis` | 合法：0 = 禁用该超时（流式/长响应安全出口） |
| `retry-max-attempts` / `retry-delay-millis` | 合法：0 = 关闭重试 / 无退避 |
| 任何键的负数值 | 启动期 fail-fast（`retry-methods` 为空集合除外，表示不重试） |

**关系**：`GXWebClientConfig` 以构造/方法参数注入；`GXWebClientResourceHolder` 复用同一实例；`lbGuardEnabled`/`lbGuardAllowHosts` 只被 LB 守卫过滤器消费。

**状态转换**：无（值对象）。**兼容性**：旧配置文件零迁移；新增键全部可缺省。

---

## 2. 请求头契约（Header Model）

| 头 | 来源 | 注入条件 | 空值规则 | 敏感 | 变化 |
| --- | --- | --- | --- | --- | --- |
| `X-Request-Source` | `spring.application.name` | 非空白才注入 | 空白跳过 | 否 | 不变 |
| `X-Request-Start-Time` | 当前毫秒时间戳 | 恒注入 | — | 否 | **移至内层过滤器逐尝试刷新**（E-14） |
| `User-Agent` | `Maple-Leaf-WebClient/1.0[+suffix]` | 恒注入 | — | 否 | **移除 os.name**（F-16） |
| `X-Trace-Id`（`GXTraceIdContextUtils.TRACE_ID_KEY`） | 快照 > 请求属性 > MDC | 非空白才注入 | 全空 → 限流 WARN + 跳过（F-03） | 否 | **取值顺序与缺失语义变化** |
| `X-Auth-Token`（`GXCommonConstant.X_AUTH_TOKEN`） | `generateHttpAuthToken()` 或快照 | 非空白才注入；生成失败 fail-fast 不重试（P-01） | — | **是** | 生成结果可按快照携带 |
| `PLATFORM`（`GXTokenConstant.PLATFORM`） | 快照 > 请求头透传 | 非空白才注入 | 空白跳过 | 否 | 同 TraceId 取值顺序 |

**校验**：所有头值单行化（拒绝 CR/LF 注入）；TRACE 日志输出前按掩码映射脱敏（R-06）。

---

## 3. 错误映射模型（Error Mapping）

**远端错误体形状 → 中间表示 → `GXBusinessException`**：

| 形状 | 识别 | code 取值 | message 取值 | 终态 |
| --- | --- | --- | --- | --- |
| 框架统一响应 `{"code","msg","data"}` | `msg` 字段存在 | body.code（缺省 HTTP 状态） | `msg`（F-02 修复） | `GXBusinessException(message, code)` |
| Spring 默认 `{"status","message","path",...}` | `message` 字段存在 | body.status（缺省 HTTP 状态） | `message`（现状保持） | 同上 |
| 纯文本 / 空 / 非法 JSON | 其余 | HTTP 状态 | `getHttpStatusDescription`（现状） | 同上 |
| **LB 守卫拦截**（主机非服务名形态且 lbMode） | 请求期主机形态判定（白名单优先，见下） | 400 段（不重试） | "负载均衡客户端仅支持服务名寻址…"（ASCII 日志 + 中文用户消息） | `Mono.error`，**0 次网络尝试、0 次重试** |

**守卫判定顺序（唯一口径）**：①`lbGuardAllowHosts` 命中该 host → 放行（含带点服务名）→ ②`lbGuardEnabled=false` → 跳过守卫（交 LB 过滤器，复现旧行为）→ ③host 为空 / IP 字面量 / `localhost` / 含 `.` / 带端口字面量 → 400 拦截 → ④其余简单服务名 `^[A-Za-z0-9_-]+$` → 放行。

**消息前缀（冻结）**：4xx → `"客户端请求错误(4xx): {detail}"`；5xx → `"服务器处理错误(5xx): {detail}"`。`{detail}` 按上表取值。

**状态码 × 重试决策表**（`isRetryableException` + `retryMethods` 合并判定）：

| 状态/异常 | 方法 ∈ retryMethods | 判定 |
| --- | --- | --- |
| 408 / 429 / 500 / 502 / 503 / 504 | 是 | 重试（P-02 保持） |
| 408 / 429 / 500 / 502 / 503 / 504 | 否（默认 POST/PUT/DELETE/PATCH） | **不重试**（F-05 收敛） |
| 其余 4xx（400/401/403/404…） | 任意 | 不重试 |
| `WebClientRequestException`（连接/超时类） | 是 | 重试 |
| `WebClientRequestException` | 否 | 不重试 |
| token 生成失败（requestFilter 短路） | 任意 | 不重试（P-01，链路位置保证） |
| LB 守卫错误（400 段） | 任意 | 不重试 |

---

## 4. 重试策略模型（RetryPolicy）

```
attempt = 0
订阅失败:
  if 方法 ∉ retryMethods            → 终态失败
  if 异常/状态 不可重试               → 终态失败
  if attempt ≥ retryMaxAttempts      → 终态失败（透传原始异常，P-02）
  attempt += 1; 等待 retryDelayMillis（固定延迟）→ 重订阅（重建 X-Request-Start-Time）
```

**字段**：`retryMaxAttempts`（默认 3）、`retryDelayMillis`（默认 1000）、`retryMethods`（默认安全方法集）。**不变量**：耗尽后抛原始失败；`durationMs` 统计不含退避等待（R-06）；每次尝试独立刷新时间戳头。

**组合语义（`attempts` / `retryDelayMs` 计数口径，唯一口径）**：

| 场景 | 网络尝试次数 | `attempts` 日志值 | `retryDelayMs` |
| --- | --- | --- | --- |
| `retryMaxAttempts=0` | 1 | 1 | 0 |
| `retryMethods` 为空集合 | 1 | 1 | 0 |
| 方法 ∉ `retryMethods`（如默认 POST） | 1 | 1 | 0 |
| token 生成失败 / LB 守卫拦截 | **0** | 0 | 0 |
| GET + 503 耗尽（默认） | 4 | 4 | 3×`retryDelayMillis` |
| GET + 503 第 3 次成功 | 3 | 3 | 2×`retryDelayMillis` |

> `attempts` 语义固定为"含首次的网络尝试次数"，与 `retryMaxAttempts`（追加重试次数）严格区分，测试断言以本表为准。

---

## 5. 资源生命周期模型（ResourceLifecycle）

**连接池状态机**（`ConnectionProvider`）：

```
CREATED ──(注册 destroyMethod="dispose" 或 holder 持有)──► MANAGED
MANAGED ──(ContextClosedEvent / JVM shutdown / holder.dispose())──► DISPOSING ──► DISPOSED
DISPOSED ──(再次 dispose)──► DISPOSED（幂等，见 FR-013 与 US4 验收场景 3）
```

**获取路径 × 释放责任**：

| 路径 | 获取 | 释放责任 | 修复后行为 |
| --- | --- | --- | --- |
| Bean 注入（`mapleWebClientConnectionProvider`） | Spring 容器 | 容器关闭自动 `dispose()` | 显式 `destroyMethod`（F-04） |
| no-arg `webClientBuilder()` 且容器有池 | 复用容器 Bean | 容器 | **不再自建**（E-09 闭合） |
| no-arg `webClientBuilder()` 且容器无池 | `GXWebClientResourceHolder`（全局至多 1 个池） | holder（ContextClosedEvent / shutdown hook） | 连续调用复用，禁止每调用新建 |

**计数不变量**：10 次 no-arg 调用 → 活跃池数 = 1；上下文关闭后 → `dispose()` 调用次数 ≥1 且驱逐线程退出。

**销毁后语义（定稿，替代"重建或抛出"二选一）**：holder 进入 `DISPOSED` 后再调用 no-arg `webClientBuilder()` MUST 抛出 `GXBusinessException`（用户消息中文、含 `webClientBuilder` 与 `disposed` 语义；日志 ASCII），**不自动重建连接池**——自动重建会在热部署/上下文关闭后再次静默泄漏，与 F-04 的修复目标相反。需要继续出网的应用必须重新初始化 Spring 上下文。

---

## 6. 上下文快照模型（ContextSnapshot）

**字段**：`traceId`（String, nullable）、`platform`（String, nullable）、`authToken`（String, nullable，**仅由调用方显式提供**，不由 `capture()` 生成）、`capturedAtMillis`（long）。

**生命周期**：`capture()`（调用线程，读请求属性/MDC）→ 通过 **Reactor Context** 挂载：`...retrieve()...contextWrite(ctx -> ctx.put(ATTRIBUTE_KEY, snapshot))`（同时兼容 `ClientRequest.attribute` 读取）→ `requestFilter` 在订阅线程读取并 `apply(builder)` 注入头。
> 实现说明：`RequestHeadersSpec#attribute` / `#attributes` 在 Spring Framework 7 中不会把自定义属性带入 `ClientRequest`（实测 attributes 仅含框架内部键），因此以 Reactor Context 作为主通道。

**token 语义（定稿）**：token 依赖 `Environment`（非 ThreadLocal），本就不会随线程丢失 → `capture()` **不生成** token；`authToken` 为空时仍由 `requestFilter` 在订阅线程生成并沿用 P-01 fail-fast（生成失败即 `Mono.error`，不重试）。快照只为"需要携带特定 token（如跨服务委派）"的场景提供可选入口。

**空值规则（JSpecify）**：全部字段 `@Nullable`；`apply` 对 null 字段跳过注入；`capture()` 在无上下文环境返回全空快照（非 null）。

**缺失语义**：无快照且无 ThreadLocal/MDC → 限流 WARN（每 30s ≤1 条）+ 跳过三个上下文头（F-03：不再静默）。限流状态为 `GXWebClientConfig` **实例字段**（`AtomicLong lastContextWarningAtMillis`，单例 Bean 按上下文隔离），窗口常量 `CONTEXT_WARNING_INTERVAL_MILLIS = 30_000` 同包可覆写；不新增配置键，测试不使用反射。

---

## 7. 测试矩阵（Test Matrix）

| 修复项 | 成功 | 失败/异常 | 空值 | 边界 | 并发/生命周期 | 集成形态 | 旧代码失败证据 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 LB 守卫 | 简单服务名放行 + 白名单带点服务名放行 | FQDN/IP/localhost 拦截（code=400、0 次尝试） | 无 host / 空 host | `lb-guard-enabled=false` 回退旧行为 | 并发混合寻址 | `@SpringBootTest` 断言 LB 过滤器存在 | 直连 URL 现状=假 503×4 |
| F-02 错误保真 | `msg`/`message`/文本三形状 | 非法 JSON/空 body | 缺字段 | 超大 body | — | HttpServer 返回统一响应 | 现状丢失 `msg` |
| F-03 上下文 | 请求线程头齐全 | 异步线程 WARN+可选快照 | 全空快照 | 限流窗口 | 4 线程形态×并发 | `@SpringBootTest` | 现状异步丢头 |
| F-04 资源释放 | 关闭即 dispose | 多 connector/缺 Bean | 重复销毁 | holder 复用 | 10×10 创建关闭 | ContextRunner | 现状池不释放 |
| F-05 重试护栏 | GET 重试 4 次（P-02） | POST 不重试 | retryMethods 空 | `retryMaxAttempts=0` 与空集合的 attempts=1 口径（§4 组合语义表） | 并发重试 | HttpServer 计数 | POST 现状重放 |
| F-06 token 缓存 | 60s 窗口内复用（生成 1 次） | `source`/`secret` 变更后失效重生成 | token 源缺失仍 fail-fast（P-01） | 窗口边界（59s / 61s）、`expiry/2` 下限 | 并发请求同窗口生成次数=1 | — | 现状每请求 1 次 |
| F-07 耗时口径 | durationMs 不含退避 | 失败有耗时日志 | — | 阈值=配置值 | — | — | 现状含退避 |
| F-08 掩码 | TRACE 输出脱敏 | SPI 缺失用内置映射 | 空映射 | 大小写头名 | — | — | 现状明文 token |
| F-11 超时口径 | 四键（connect/read/write/response）独立生效 | 非法值 fail-fast（文案含键名） | null → 回退 `timeoutSeconds×1000` | `read/response=0` 禁用、流式持续 > timeout 不断连 | 长轮询/SSE 超基线仍可消费 | HttpServer 慢响应端到端 | 现状四类超时共用 `timeout-seconds` |
| P-01/02/04/06/07 | 既有 17 条全保留 | — | — | — | — | — | 回归锁定 |

> 矩阵只列"有专属用例设计约束"的 **9 个修复项**（F-01～F-08 + F-11）。其余发现（F-09/F-10/F-13/F-14/F-15/F-16/F-17/F-18）的用例归属见 `quickstart.md` 的 F-01～F-19 证据表，避免矩阵膨胀。

**完成定义**：`mvn -pl leaf-base-webclient test` 全绿、用例数从 17 单调增长至 40～55、ErrorProne/NullAway 零错误、每修复项证据行齐备。
