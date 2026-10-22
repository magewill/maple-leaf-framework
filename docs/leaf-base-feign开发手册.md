# leaf-base-feign 开发手册

`leaf-base-feign` 是 Maple Leaf Framework 对 OpenFeign 的封装模块，负责四件事：

1. 出站请求头透传（认证 token、platform、traceId、`@GXFeignHeader` 声明头、公共头）；
2. 入站注解鉴权（`@GXHttpInvokerAuthToken`）；
3. 远端错误解码（可选的 `GXFeignCustomErrorDecoder`）；
4. 熔断与重试的默认参数（Spring Cloud CircuitBreaker Retry）。

---

## 1. 接入

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-feign</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

模块通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动装配：

- `cn.maple.feign.config.GXFeignConfig`
- `cn.maple.feign.config.GXCircuitBreakerConfig`

下游只需使用 `@EnableFeignClients`：

```java
@EnableFeignClients(basePackages = "com.example.client")
@SpringBootApplication
public class DemoApplication {
}
```

## 2. 认证 token

### 2.1 出站：生成并注入

框架通过 `GXFeignService` 的 SPI 生成 token：

```java
@Configuration
public class DemoFeignConfig {
    @Bean
    public GXFeignService gxFeignService() {
        return new GXFeignService() {
        };
    }
}
```

默认实现依赖两个配置键：

| 键 | 说明 |
| --- | --- |
| `maple.framework.web.feign.token` | 参与签名/编码的源值，缺失时抛 `GXBusinessException` |
| `maple.framework.web.feign.secret` | 加解密密钥，缺失时抛 `GXBusinessException` |

**缺配置时的行为**：默认 fail-fast——Feign 调用在发出请求前失败（异常来自 `RequestInterceptor`）。若目标服务不需要鉴权，可显式关闭 token 透传：

```yaml
maple:
  framework:
    feign:
      auth-token:
        enabled: false   # 跳过生成并输出 warn，traceId/platform 照常透传
```

> 注意：关闭后调用方不再携带 `X-Auth-Token`，下游若启用了鉴权会返回 401。

### 2.2 入站：`@GXHttpInvokerAuthToken`

```java
@RestController
public class OrderController {
    @GXHttpInvokerAuthToken
    @GetMapping("/inner/orders")
    public GXResultUtils<List<OrderResDto>> innerOrders() {
        // 仅当请求携带有效 X-Auth-Token 时才执行
    }
}
```

| 场景 | 行为 |
| --- | --- |
| 注解 `value` 不是 `feign`（大小写不敏感） | 直接放行（例如 `webClient` 交给 WebClient 切面） |
| `value=feign` 且容器无 `GXFeignService` Bean | 抛 `GXBusinessException`，提示实现该接口 |
| `value=feign` 且 token 缺失/无效 | 抛 `GXFeignAuthTokenException` |
| 方法级注解与类级注解同时存在 | 方法级优先 |

**已知边界**：类级注解在"接口上标注、实现类未标注"时不会被拦截（`@within` 只匹配目标类）。需要强约束时请标注在方法或实现类上。

## 3. 请求头透传规则

| 头 | 来源 | 缺失时 |
| --- | --- | --- |
| `X-Auth-Token` | `GXFeignService#generateHttpAuthToken()` | 见 §2.1 |
| `platform` | 当前请求头（经 `GXFeignService#getPlatform()`） | 不写 |
| `X-B3-TraceId` | 请求属性 → 请求头 → MDC → 新生成 | 新生成并回写 MDC（调用结束恢复） |
| `@GXFeignHeader` 声明头 | 当前 `HttpServletRequest` | 跳过 |
| `X-Request-Source` | `spring.application.name` | 不写 |
| `X-Request-Start-Time` | 当前毫秒时间戳 | 恒写 |
| `User-Agent` | 调用方显式值优先 | 补 `Maple-Leaf-Feign/1.0 (<os>)` |

**不会透传**：`Authorization`、`Cookie`、`Set-Cookie`、`X-Auth-Token`、`X-HMAC-Signature`、`X-API-Key`、`Host`、`Content-Length`、`Connection`、`Keep-Alive`、`Proxy-Authenticate`、`Proxy-Authorization`、`TE`、`Trailer`、`Transfer-Encoding`、`Upgrade`（大小写不敏感）。即使通过 `@GXFeignHeader` 声明也会被阻断。

**不再写入请求的响应类头**（4.3.0 起）：`X-Content-Type-Options`、`X-Frame-Options`、`X-XSS-Protection`、`Cache-Control`、`Pragma`、`Expires`。这些是响应安全头，写入请求没有防护效果，且 `Cache-Control: no-cache` 会抑制幂等 GET 的代理缓存。

`@GXFeignHeader` 仅支持方法级：

```java
public interface OrderClient {
    @GXFeignHeader(names = {"X-Tenant-Id", "X-Request-Channel"})
    @GetMapping("/orders")
    List<OrderResDto> orders();
}
```

## 4. 错误解码

**默认关闭**。未开启时远端错误由 Feign 默认解码器处理（抛 `FeignException`）。

开启方式：

```yaml
maple:
  framework:
    feign:
      error-decoder:
        enabled: true
```

开启后的映射：

| 状态码 | 异常 | `code` | 消息来源 |
| --- | --- | --- | --- |
| 400 / 409 / 422 / 429 | `GXBusinessException` | 远端业务 `code`，缺失则为状态码 | 远端 `msg`，解析失败则为固定文案 + 原始 body |
| 401 | `GXBusinessException` | 401 | 固定文案（不回显 body） |
| 403 | `GXBusinessException` | 403 | 固定文案（不回显 body） |
| 404 | `GXBusinessException` | 404 | 固定文案 + 请求 URL |
| 500 / 503 / 504 | `GXBusinessException` | 对应状态码 | 固定文案（不回显 body） |
| 其它（204/301/418 等） | `FeignException` | N/A | 默认解码器 |

远端业务响应格式（`GXResultUtils`）会被识别：

```json
{"code": 1001, "msg": "库存不足", "data": {"sku": "A1"}}
```

此时异常 `code=1001`、`message` 含"库存不足"、`data` 保留 `{sku=A1}`。响应体读取上限 8192 字符，超出追加 `...[truncated]`。

> **fallback 交互**：若客户端配置了 `fallback` 或 `fallbackFactory`，Spring Cloud CircuitBreaker 会捕获**任何**异常（含 4xx 业务异常）并返回 fallback 结果。需要把业务异常抛给调用方时，不要为该客户端配置 fallback。

## 5. 熔断与重试

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `maple.framework.feign.circuit-breaker.enabled` | `true` | 是否注册默认 `Customizer` |
| `maple.framework.feign.circuit-breaker.max-retries` | `0` | 重试次数；`0` 表示不重试 |
| `maple.framework.feign.circuit-breaker.backoff` | `100ms` | 首次退避 |
| `maple.framework.feign.circuit-breaker.backoff-multiplier` | `2.0` | 退避倍数 |
| `maple.framework.feign.circuit-breaker.open-timeout` | `20s` | 熔断打开时长 |
| `maple.framework.feign.circuit-breaker.reset-timeout` | `5s` | 失败后状态重置窗口 |

**重试语义**：

- 默认 `0`，与 OpenFeign 原生 `Retryer.NEVER_RETRY` 一致，避免非幂等请求（POST/PUT/DELETE）被重复执行。
- 显式配置 `> 0` 时，仅重试传输异常与 5xx；`GXBusinessException` 且 `code < 500`（客户端错误）**不重试**。
- 熔断器对每种客户端 id 独立计数，可按 `@FeignClient(name=...)` 观察。

## 6. 配置键总表

| 键 | 默认 | 作用 |
| --- | --- | --- |
| `maple.framework.feign.logger-level.enabled` | `true` | 注册 `Logger.Level.BASIC` |
| `maple.framework.feign.error-decoder.enabled` | `false` | 注册错误解码器 |
| `maple.framework.feign.request-interceptor.enabled` | `true` | 注册请求头拦截器 |
| `maple.framework.feign.auth-token-aspect.enabled` | `true` | 注册入站鉴权切面 |
| `maple.framework.feign.auth-token.enabled` | `true` | 出站 token 生成与注入 |
| `maple.framework.feign.fallback-config.enabled` | `true` | 注册默认 Feign 规格（提供 `Encoder` 与消息转换器） |
| `maple.framework.feign.circuit-breaker.*` | 见 §5 | 熔断与重试参数 |
| `maple.framework.web.feign.token` | 无 | token 生成源值 |
| `maple.framework.web.feign.secret` | 无 | token 密钥 |

> 前缀只认 `maple.framework.feign.*`。历史写法 `maple.feign.*` 从未生效，请一并迁移。

> 关闭 `fallback-config.enabled` 时必须自行提供 `feign.codec.Encoder` 与 `FeignHttpMessageConverters`，否则 Feign 客户端无法创建。

### 6.1 空值契约

`cn.maple.feign.*` 包当前使用 `@NullUnmarked`（与仓库现状一致），因此空值语义以 Javadoc 与下表为准：

| 方法 | 可能为空 | 说明 |
| --- | --- | --- |
| `GXFeignService#getPlatform()` | **是** | 已标 `@Nullable`；无请求上下文或请求头缺失时返回 `null` |
| `GXFeignService#getTraceId()` | 否 | 末级会生成，恒非空 |
| `GXFeignService#getAuthTokenSecret()` | 否 | 缺失配置抛 `GXBusinessException` |
| `GXFeignService#generateHttpAuthToken(...)` | 否 | 失败以异常表达 |
| `GXFeignService#getSensitiveHeaderFields()` | 否 | `Map.of` 常量 |
| `GXFeignService#checkTokenValidity()` / `checkHmac(...)` | N/A | 返回 `boolean`，不抛出 |

后续计划：待 `leaf-base-framework` 侧统一切换到 `@NullMarked` 后，本模块一并切换；在此之前新增公开方法必须显式标注 `@Nullable`（可空）或保持非空并在 Javadoc 写明异常语义。

## 7. 迁移说明（4.3.0）

| 变更 | 影响 | 处理 |
| --- | --- | --- |
| `max-retries` 默认 `3 → 0` | 不再自动重试 | 需要重试时显式配置 `> 0` |
| 出站不再写 6 个响应类头 | 请求头变小；幂等 GET 可被代理缓存 | 依赖这些头的下游需自检 |
| `User-Agent` 不再被覆盖 | 服务端可识别真实调用方 | 需要固定 UA 时在拦截器中自行设置 |
| 错误解码 `code` 由 500 改为远端状态码 | 上游按 code 分流更准确 | 检查是否有 `code == 500` 的硬编码判断 |
| 配置前缀统一为 `maple.framework.feign.*` | 旧前缀彻底失效 | 更新本地与 Nacos 配置 |

## 8. 故障排查

| 现象 | 排查 |
| --- | --- |
| 日志出现 `Failed to get FeignService instance` | 未注册 `GXFeignService` Bean，token 未透传 |
| 启动后首个 Feign 调用即 `GXBusinessException`（提示配置 token） | 缺 `maple.framework.web.feign.token`；或按 §2.1 关闭 token 透传 |
| 下游收到 401 | 确认 `auth-token.enabled` 未被关闭、token/secret 与下游一致 |
| 业务异常被 fallback 吞掉 | 该客户端配置了 `fallback`，移除或改用 `fallbackFactory` 并在内部重抛 |
| 远端 400 缺少业务提示 | 未开启 `error-decoder.enabled` |
| 配置不生效 | 检查前缀是否为 `maple.framework.feign.*` |

## 9. 验证命令

```powershell
# 模块单测
mvn -pl leaf-base-feign test

# 指定场景
mvn -pl leaf-base-feign test -Dtest=GXFeignRequestInterceptorTest
mvn -pl leaf-base-feign test -Dtest=GXFeignCustomErrorDecoderTest
mvn -pl leaf-base-feign test -Dtest=GXCircuitBreakerConfigTest
mvn -pl leaf-base-feign test -Dtest=GXFeignAuthTokenAspectTest
mvn -pl leaf-base-feign test -Dtest=GXFeignHttpRoundTripTest

# 编译期静态检查
mvn -pl leaf-base-feign -am clean install -DskipTests
```
