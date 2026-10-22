# 契约：认证 SPI 与切面语义边界

**Feature**: `007-feign-module-audit` | **适用版本**: `leaf-base-feign` 4.3.0-SNAPSHOT

## 1. 两个方向的职责划分

| 方向 | 承载者 | 触发时机 | 说明 |
| --- | --- | --- | --- |
| 出站（本服务 → 下游） | `GXFeignService#generateHttpAuthToken()` + `GXFeignRequestInterceptor` | Feign 请求构造阶段 | 生成并注入 `X-Auth-Token` |
| 入站（上游 → 本服务） | `GXFeignAuthTokenAspect` + `@GXHttpInvokerAuthToken` | 被注解方法执行前 | 校验当前请求中的 `X-Auth-Token` |

**约束**：切面不负责生成 token；拦截器不负责校验 token。两者不得互相调用。

## 2. `GXFeignService` 契约

| 方法 | 返回 | 可能为空 | 异常 |
| --- | --- | --- | --- |
| `generateHttpAuthToken()` | 编码后的 token | 否 | `maple.framework.web.feign.token` 缺失 → `GXBusinessException` |
| `generateHttpAuthToken(String source, int expiry)` | 编码后的 token | 否 | `maple.framework.web.feign.secret` 缺失或编码失败 → `GXBusinessException` |
| `checkTokenValidity()` | `boolean` | 否 | 不抛异常（内部捕获） |
| `getAuthTokenSecret()` | secret | 否 | `maple.framework.web.feign.secret` 缺失 → `GXBusinessException` |
| `getPlatform()` | 平台标识 | **是**（返回 `null`） | 无 |
| `getTraceId()` | traceId | 否（末级生成） | 无 |
| `getSensitiveHeaderFields()` | 头名 → 掩码 | 否 | 无 |
| `generateHmac(Object, String)` | HMAC 串 | 否 | 签名失败 → `GXBusinessException` |
| `checkHmac(String, String, Object)` | `boolean` | 否 | 无 |

**不变量**：

- 本次只新增 `@Nullable` 与 Javadoc，不改任何方法签名、默认实现逻辑与异常消息。
- 接口不提供默认实现 Bean；`GXFeignService` 由业务工程实现（`spec.md` Assumptions 已确认）。

## 3. 切面语义

| 条件 | 行为 |
| --- | --- |
| 无 `@GXHttpInvokerAuthToken`（方法级与类级都无） | 直接放行 |
| 注解 `value` 不等于 `feign`（大小写不敏感比较） | 直接放行（交给其它切面，如 WebClient 的 `webClient`） |
| 注解 `value=feign`，容器无 `GXFeignService` Bean | 抛 `GXBusinessException`，消息含待实现接口全名 |
| 注解 `value=feign`，token 缺失或解码失败 | 抛 `GXFeignAuthTokenException`，消息含方法描述 |
| 注解 `value=feign`，token 有效 | 放行 |

**注解解析优先级**：最具体方法 → 方法 → 目标类。

## 4. 已知边界（必须在手册中写明）

1. `@GXFeignHeader` 仅 `@Target(METHOD)`，且仅从当前 Servlet 请求取值；非 Web 线程静默跳过。
2. `@GXHttpInvokerAuthToken` 的类级注解在"标注在接口上、实现类未标注"时**不被 `@within` 命中**（实测：`GXFeignAuthTokenAspectTest#interfaceLevelAnnotationIsNotInterceptedOnImplementation` 断言调用不抛异常）。因此只有"方法级注解"与"实现类上的类级注解"可靠；接口级类级注解需要改标到方法或实现类。
3. 配置 `fallback` 后，切面抛出的 `GXBusinessException` / `GXFeignAuthTokenException` 在调用方侧可能被 fallback 结果替代（见 `error-decoding-contract.md` §4）。

## 5. 验证断言

1. 方法级注解生效、类级注解生效、`value` 不匹配跳过（3 条）。
2. 缺失 Bean 抛 `GXBusinessException`；token 无效抛 `GXFeignAuthTokenException`（2 条）。
3. JDK 代理 + 接口方法解析到最具体实现（1 条）。
4. 接口上的类级注解命中结论写入报告（1 条，允许结论为"不命中"）。
