# Quickstart：leaf-base-feign 审查与修复的端到端验证

**Feature**: `007-feign-module-audit` | **Date**: 2026-10-22

本指南给出可复现的验证步骤与期望结果，用于证明 spec 的 SC-001 ~ SC-010 达成。契约细节见 [contracts/](./contracts/)，数据口径见 [data-model.md](./data-model.md)。

## 0. 前置条件

- JDK 21（`mvn -v` 显示 Java 21；本机默认 JDK 25 也可构建，但验收以 21 为准）
- Maven 3.10+，可访问本地仓库 `F:\repository`
- 无需任何外部中间件、无需外网（真实往返使用 JDK `com.sun.net.httpserver.HttpServer`）

## 1. 基线（审查阶段，无生产改动）

```powershell
cd e:/project/java/maple-leaf-framework
mvn -pl leaf-base-feign test
```

期望：`Tests run: 25, Failures: 0, Errors: 0`，`BUILD SUCCESS`。该结果必须原样写入 `audit-report.md`（SC-008）。

## 2. 头透传矩阵（FR-004 / SC-003）

```powershell
mvn -pl leaf-base-feign test -Dtest=GXFeignRequestInterceptorTest
```

期望断言（详见 `contracts/header-propagation-contract.md`）：

| 场景 | 期望 |
| --- | --- |
| 连续 `apply()` 两次 | 每个头恰好 1 个值 |
| `@GXFeignHeader` 声明 `Authorization` / `Cookie` / `Connection` | 三者均不出现在出站请求中 |
| 显式设置 `User-Agent` | 保持调用方值 |
| 无 `HttpServletRequest` | 不抛异常，`@GXFeignHeader` 头不出现 |
| `auth-token.enabled=false` 且无 token 配置 | 调用成功，日志 1 条 warn |
| 六个响应类安全头 | 断言全部不存在 |

## 3. 错误语义矩阵（FR-005 / SC-004）

```powershell
mvn -pl leaf-base-feign test -Dtest=GXFeignCustomErrorDecoderTest
```

期望（详见 `contracts/error-decoding-contract.md`）：

| 输入 | 期望 |
| --- | --- |
| 400 + `{"code":1001,"msg":"库存不足","data":{}}` | `GXBusinessException`，`code=1001`，消息含"库存不足" |
| 401 + 敏感 body | `code=401`，消息**不含** body 内容 |
| 404 / 409 / 422 / 429 / 500 / 503 / 504 | `code` 等于状态码 |
| 418 | `FeignException`（默认解码器），非 `GXBusinessException` |
| body 空 / 非 JSON / 9000 字符 | 不抛解析异常；消息含固定文案或 `...[truncated]` |

## 4. 熔断重试实测（FR-006 / SC-005）

```powershell
mvn -pl leaf-base-feign test -Dtest=GXCircuitBreakerConfigTest
```

期望：

| 用例 | 期望 |
| --- | --- |
| `max-retries` 默认 | 服务端只收到 **1** 次请求 |
| `max-retries=2` + 远端 500 | 服务端收到 **3** 次请求，耗时约 `100ms + 200ms` |
| `max-retries=2` + 远端 400 | 服务端只收到 **1** 次请求（业务异常不重试） |
| `max-retries=-1` | 启动失败，`IllegalArgumentException` |
| 配置 `fallback` + 远端 400 | 返回 fallback 值（记录为已知行为，写入手册） |

## 5. 装配矩阵（FR-008 / SC-006）

```powershell
mvn -pl leaf-base-feign test "-Dtest=GXFeignConfigTest,GXFeignCircuitBreakerFallbackTest,GXFeignAuthTokenAspectTest"
```

期望：

| 用例 | 期望 |
| --- | --- |
| 仅框架自动配置 | `Logger.Level` / `RequestInterceptor` / `GXFeignAuthTokenAspect` 存在；`ErrorDecoder` **不存在** |
| `error-decoder.enabled=true` | `ErrorDecoder` 为 `GXFeignCustomErrorDecoder` |
| 五+一个开关分别置 `false` | 对应 Bean 不存在 |
| `@EnableFeignClients` 默认装配 | 子上下文可取得 `Encoder`（由 default 规格提供），客户端可正常创建 |
| `fallback-config.enabled=false` + 用户 `Encoder` | default 规格不注册，客户端仍可创建 |
| `fallback-config.enabled=false` 且无用户 `Encoder` | 客户端创建失败（`No bean found of type feign.codec.Encoder`），已在手册中说明 |
| 用户自定义 `Encoder` / `ErrorDecoder` | 用户 Bean 优先 |
| 切面 6 类场景 | 见 `contracts/auth-spi-contract.md` §5 |

## 6. 配置键矩阵（FR-007 / SC-006）

```powershell
mvn -pl leaf-base-feign test "-Dtest=GXCircuitBreakerConfigTest,GXFeignConfigTest"
```

期望：

| 输入 | 期望 |
| --- | --- |
| `maple.framework.feign.circuit-breaker.max-retries=1` | `getMaxRetries()==1` |
| `maple.feign.circuit-breaker.max-retries=1`（旧写法） | 属性仍为默认值 `0` |
| `config/{dev,test,pre,prod}` 模板 | 只包含 `maple.framework.feign.circuit-breaker.*`，无 `maple.feign.` 残留 |

模板核对：

```powershell
Select-String -Path "config/*/app-environment.yml" -Pattern "maple\."
```

## 7. 真实 HTTP 往返（FR-004 / FR-005 / SC-003 / SC-004）

```powershell
mvn -pl leaf-base-feign test -Dtest=GXFeignHttpRoundTripTest
```

期望：

1. 服务端侧记录到的请求头与 §2 矩阵一致（含 token / platform / traceId / 注解头）。
2. 服务端按状态码返回时，客户端异常类型与 `code` 与 §3 矩阵一致。
3. 用例不依赖本地端口以外的任何环境，不访问外网。

## 8. 性能口径（FR-010 / SC-007）

```powershell
mvn -pl leaf-base-feign test -Dtest=GXFeignRequestInterceptorTest#applyThroughputBaseline
```

期望报告内容（写入 `audit-report.md`）：

| 指标 | 期望 |
| --- | --- |
| 采样量 | ≥ 100,000 次 `apply()`（先 50,000 次预热） |
| 分位 | 给出 p50 / p95 / p99 与 ops/s |
| 重跑偏差 | 两次同量级，偏差 ≤ 30% |
| 固定开销 | 每调用环境值查询 **0** 次、注解查找缓存命中、debug 日志 ≤ 3 条、头写入次数可复算 |

**注意**：不得以绝对吞吐/时延数值作为通过门槛（受硬件影响）。

## 9. 报告验收（FR-013 / SC-001 / SC-002 / SC-010）

人工核对 `specs/007-feign-module-audit/audit-report.md`：

- [ ] 15 个生产文件 + 5 个既有测试类均有判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因）
- [ ] 每条发现含"级别 + 维度 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归设计"
- [ ] 未验证项标记为 `UNVERIFIED_HYPOTHESIS`（如 `@within` 命中、fallback 吞异常）
- [ ] 既定契约（错误解码器默认关闭、`@GXFeignHeader` 仅方法级）单列，不计缺陷
- [ ] 报告与 `spec.md` 摘要无冲突
- [ ] 基线 25/0 与 §2~§8 的实测数字可复算

## 10. 完成门槛

```powershell
mvn -pl leaf-base-feign -am test
mvn -pl leaf-base-feign -am clean install -DskipTests
```

期望：测试全绿；ErrorProne + NullAway 零错误；`target/classes/META-INF/additional-spring-configuration-metadata.json` 存在且为合法 JSON。
