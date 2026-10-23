# leaf-base-webclient 开发手册

> 适用版本：4.3.0-SNAPSHOT（JDK 21 / Spring Boot 4.1.x / Spring Framework 7.x / Spring Cloud 2025.1.x）
> 契约基线：`specs/012-webclient-module-audit/contracts/`（bean / config / filter-chain / error-mapping / spi）

## 1. 能力概览

`leaf-base-webclient` 提供开箱即用的 `WebClient`、`WebClient.Builder`（负载均衡）与 `HttpServiceProxyFactory`，统一处理：

- 请求头注入：`X-Request-Source`、`X-Request-Start-Time`（按尝试刷新）、`User-Agent`、`X-B3-TraceId`、`platform`、`X-Auth-Token`
- 连接池与四类超时（连接 / 读 / 写 / 响应）
- 重试护栏（按 HTTP 方法 + 状态码/异常判定）
- 4xx/5xx → `GXBusinessException`（`code` 保真，`msg` 携带对端详情）
- TRACE 级别请求/响应头输出（敏感头掩码）
- 上下文快照跨线程透传
- 日志 SPI 钩子（`logRequestResponse` / `logRequestException`）与掩码 SPI（`getSensitiveHeaderFields`）

## 2. 配置键（18 个 `maple.framework.web.client.*` + 1 个统一开关）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `timeout-seconds` | 30 | 四类超时的回退基准，≥1 |
| `memory-buffer-size-mb` | 16 | 编解码缓冲上限；响应 `Content-Length` 告警阈值，≥1 |
| `max-connections` | 500 | 连接池上限，≥1 |
| `acquire-timeout-millis` | 3000 | 获取连接等待上限，≥1 |
| `pending-acquire-max-count` | 1000 | 等待队列上限，≥1 |
| `idle-timeout-millis` | 30000 | 空闲驱逐；0 = 不因空闲驱逐 |
| `max-life-time-millis` | 300000 | 最大存活；0 = 不因存活时间回收 |
| `evict-in-background-seconds` | 120 | 后台驱逐周期，≥1 |
| `retry-max-attempts` | 3 | 追加重试次数；0 = 关闭重试 |
| `retry-delay-millis` | 1000 | 固定退避；0 = 无退避 |
| `retry-methods` | `GET,HEAD,OPTIONS` | 可重试方法；空 = 不重试；`GET,HEAD,OPTIONS,POST,PUT,DELETE` 恢复旧范围 |
| `user-agent-suffix` | 空 | 追加为 `Maple-Leaf-WebClient/1.0+<suffix>`，不再暴露服务端 OS |
| `connect-timeout-millis` | 回退 `timeout-seconds×1000` | 独立连接超时，≥1 |
| `read-timeout-millis` | 回退 `timeout-seconds×1000` | 读超时；**0 = 禁用**（流式安全出口） |
| `write-timeout-millis` | 回退 `timeout-seconds×1000` | 写超时，≥1 |
| `response-timeout-millis` | 回退 `timeout-seconds×1000` | 响应整体超时；**0 = 禁用** |
| `lb-guard-enabled` | `true` | LB 守卫逃生口（**兼容别名**，统一开关优先）；`false` 完全回到旧行为（含合成 503） |
| `lb-guard-allow-hosts` | 空 | 守卫白名单（带点服务名 / 已登记 FQDN）；条目中的端口被忽略（按主机匹配），含协议或路径的非法条目在启动期 WARN 并忽略 |
| `maple.framework.enable.webclient-lb-guard` | `true` | **统一功能开关**（宪法 IV 命名）；一旦配置即覆盖 `lb-guard-enabled`。默认 `true` 属 F-01 缺陷收敛的显式豁免，关闭后回到旧行为 |

> 非法值（0/负数/非数字，除上表标注允许 0 的键）在启动期 fail-fast，异常文案包含完整键名。
> `maple.framework.web.client.token` / `secret` 由 `GXWebClientService` 消费，不计入上表。

## 3. 寻址模式

| 场景 | 使用 | 行为 |
| --- | --- | --- |
| 服务名调用（eureka / 服务发现） | 注入 `WebClient` 或 `@LoadBalanced WebClient.Builder`（bean 名 `webClientBuilder`） | 按服务实例负载均衡；主机必须匹配 `^[A-Za-z0-9_-]+$` |
| 带点服务名（K8s 等） | 同上 + `lb-guard-allow-hosts=svc.ns.svc.cluster.local` | 放行并交 LB 解析 |
| 直连外网 / IP / 端口 | `webClientDirectBuilder` | 直接出网，无 LB 过滤器 |
| 兼容旧行为 | `maple.framework.enable.webclient-lb-guard=false`（或别名 `lb-guard-enabled=false`） | 守卫关闭，直连主机将得到合成 503 |

误用示例（默认 builder 请求 `https://api.example.com`）：0 次网络尝试，抛 `GXBusinessException`（`code=400`，消息含"服务名寻址"与主机名）。

## 4. 错误映射

| 远端返回 | 调用方异常 | `code` |
| --- | --- | --- |
| `{"code":500,"msg":"detail"}` | `GXBusinessException("服务器处理错误(5xx): detail")` | body `code`（缺省 = HTTP 状态） |
| `{"status":503,"message":"detail"}` | `GXBusinessException`，detail 取 `message` | body `status`（缺省 = HTTP 状态） |
| 纯文本 / 空 / 非法 JSON | 通用文案 `getHttpStatusDescription` | HTTP 状态 |
| 直连主机被守卫拦截 | `GXBusinessException`，含寻址指引 | 400 |

detail 优先级：`msg` → `message` → 状态描述。

## 5. 重试语义

```
仅在 method ∈ retry-methods 且（状态 ∈ {408,429,500,502,503,504} 或 WebClientRequestException）时重试
retry-max-attempts=0 或 retry-methods 为空 → 不重试（attempts=1）
耗尽后抛原始失败（不包装）
token 生成失败 / LB 守卫拒绝 → 0 次尝试、0 次重试
```

- `attempts` = 含首次的网络尝试次数；`durationMs` = 各次尝试网络耗时之和（**不含退避**）；`retryDelayMs` = 退避累计；总墙钟 = 两者之和。
- 非可重放请求体（流 / 表单流）：重试将重新订阅请求，流已被消费时无法重放；需要重放语义请自行缓冲为 `byte[]` / `String`。

## 6. 上下文快照（跨线程透传）

```java
GXWebClientContextSnapshot snapshot = GXWebClientContextSnapshot.capture(); // 调用线程
webClient.get().uri("/orders")
        .retrieve().bodyToMono(Order.class)
        .contextWrite(ctx -> ctx.put(GXWebClientContextSnapshot.ATTRIBUTE_KEY, snapshot))
        .subscribe();
```

- `capture()`：读取请求属性 / MDC 的 traceId 与 `platform` 头；**不生成 token**（token 由请求过滤器在订阅线程生成，token 依赖 Environment 而非 ThreadLocal）。
- 需要携带特定 token 时：`GXWebClientContextSnapshot.capture(token)`。
- 无快照且无 ThreadLocal 上下文：按 30s 窗口限流输出 WARN `HTTP request dropped context`，并跳过上下文头（不再静默）。
- 切面失败原因可区分：`reason=no request context` vs `reason=token invalid`。

## 7. 资源生命周期

| 组件 | 创建方式 | 释放 |
| --- | --- | --- |
| `mapleWebClientConnectionProvider` | 容器 Bean（`@Bean(destroyMethod = "dispose")`） | 容器关闭自动 `dispose()`，后台驱逐线程随之退出 |
| 手工 `new GXWebClientConfig().webClientBuilder()` | 复用容器 connector；缺失时经 `GXWebClientResourceHolder` 全局单池 | 容器关闭（`DisposableBean`）或显式 `holder.dispose()`；**dispose 后再取 builder 抛 `GXBusinessException`，不自动重建** |

## 8. 日志与 SPI

- TRACE 级别输出请求/响应头，敏感头按 `getSensitiveHeaderFields()` 掩码（默认含 `Authorization`/`Cookie`/`Set-Cookie`/`X-Auth-Token`/`X-HMAC-Signature`/`X-API-Key`，大小写不敏感）。
- 成功路径调用 `logRequestResponse`（全参 + 简版各一次）；任何终态失败调用 `logRequestException`；回调异常隔离，不影响主链路。
- 日志文本一律 ASCII；用户可见异常消息使用中文。

## 9. 异常处理器

`GXWebClientExceptionHandler` 两条文案统一中文，返回码保持（`WebClientRequestException` → 500；`WebClientResponseException` → 远端状态）。
注意：本模块客户端的 4xx/5xx 已在过滤器链内转为 `GXBusinessException`，`WebClientResponseException` 分支只对**未经本模块过滤器**的外来 `WebClient` 生效。

## 10. 验证命令

```powershell
mvn -pl leaf-base-webclient clean install -DskipTests   # ErrorProne + NullAway
mvn -pl leaf-base-webclient test                        # 70 条用例
mvn -pl leaf-base-eureka-client test                    # 下游契约回归
```
