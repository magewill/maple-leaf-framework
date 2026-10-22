# 装配契约：leaf-base-eureka-client

**Feature**: `009-eureka-client-module-audit` | **Date**: 2026-10-22 | 适用版本：`4.3.0-SNAPSHOT` 起

本契约约束"引入本模块后应用会发生什么"，是回归用例的判定依据。

## 1. 开关真值表

开关键：`maple.framework.enable.eureka-client`（`Boolean`，**默认 `true`**，`matchIfMissing=true`）。

| 开关值 | 应用显式 `eureka.client.enabled` | `GXEurekaClientConfig` | `eureka.client.enabled` 最终值 | Eureka 客户端 Bean | 注册行为 | 日志 |
| --- | --- | --- | --- | --- | --- | --- |
| 未设置（默认 `true`） | 未设置 | 生效 | 上游默认（`true`） | 存在 | 按上游默认（目标为 `localhost:8761`，若未配置 `defaultZone`） | 1 条 WARN（未配置 `defaultZone`） |
| `true` | 未设置 | 生效 | 上游默认（`true`） | 存在 | 按上游默认 | 1 条 WARN（未配置 `defaultZone`） |
| `true` | `false` | 生效 | `false`（应用值优先） | 不存在 | 无 | 无 |
| `false` | 未设置 | **不生效** | 由 PostProcessor 写为 `false`（最低优先级） | 不存在 | 无 | 1 条 INFO（禁用来源） |
| `false` | `true` | **不生效** | `true`（应用值优先） | 存在 | 应用自行负责 | 1 条 INFO（禁用来源，事件被应用覆盖） |

**硬性约束**：

- 开关为 `false` 时，模块 MUST NOT 写入除 `eureka.client.enabled=false` 之外的任何 `eureka.*` 键。
- 应用显式配置 MUST 优先于框架注入值（`EnvironmentPostProcessor` 使用"仅当缺失时写入"的最低优先级语义）。
- 开关不得改变 `@EnableFeignClients` 的开启状态（任何取值下都**不**自动扫描 Feign 客户端）。

## 2. Bean 契约（保持 P-01 ~ P-06）

| 项 | 契约 |
| --- | --- |
| 自动装配入口 | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 唯一一行 `cn.maple.eureka.client.config.GXEurekaClientConfig` |
| 注解 | `@AutoConfiguration(after = {GXFeignConfig, GXWebClientConfig, EurekaClientAutoConfiguration})` 保持不变；新增 `@ConditionalOnProperty` |
| `@Bean` | 本模块 MUST NOT 定义任何 Bean（不抢占 `WebClient.Builder`、`HttpServiceProxyFactory`、`ErrorDecoder` 等上游能力） |
| `@EnableFeignClients` | MUST NOT 出现（扫描范围归业务方，见 spec P-02） |
| 注册中心服务端能力 | MUST NOT 出现 `@EnableEurekaServer` 或相关依赖 |

## 3. 日志契约

| 场景 | 级别 | 文本（纯 ASCII） | 次数 |
| --- | --- | --- | --- |
| 开关为 `false` | INFO | `Eureka client disabled via maple.framework.enable.eureka-client=false` | 每次上下文 1 条 |
| 开关为 `true` 且未配置 `defaultZone` | WARN | `Eureka client default zone is not configured (eureka.client.service-url.defaultZone); registration will target the Spring Cloud default.` | 每次上下文 1 条 |
| 开关为 `true` 且已配置 `defaultZone` | —— | 不额外输出 | 0 |

**约束**：日志内容不得包含中文、全角符号、真实域名或凭据；不得在请求/心跳路径重复打印。

## 4. 自动装配条件元数据契约

`target/classes/META-INF/spring-autoconfigure-metadata.properties` 的实测内容为两行（`Get-Content` 于 2026-10-23 复核）：

- `GXEurekaClientConfig.AutoConfigureAfter=cn.maple.feign.config.GXFeignConfig,...`
- `GXEurekaClientConfig.ConditionalOnClass=...EurekaClientAutoConfiguration,...FeignClient,...WebClient`

**重要约束**：Spring Boot 的 autoconfigure metadata 只记录 `AutoConfigureAfter/Before` 与 `ConditionalOnClass/ConditionalOnBean` 等条件，**不记录** `@ConditionalOnProperty`。因此**不得**要求该文件出现 `ConditionalOnProperty=`（早期草案曾如此要求，属对框架能力的误判，已在 Phase 11 修正）。

开关条件的可验证载体是以下三处，缺一不可：

| 载体 | 断言点 | 回归用例 |
| --- | --- | --- |
| `@ConditionalOnProperty` 注解 | `prefix=maple.framework.enable`、`name=eureka-client`、`havingValue=true`、`matchIfMissing=true` | `GXEurekaClientContractTest#switchConditionIsDeclaredOnTheConfigurationClass` |
| `META-INF/additional-spring-configuration-metadata.json` | 同名条目、`defaultValue=true`、不含 `eureka.*`/`spring.cloud.openfeign.*` | `GXEurekaClientContractTest#metadataDeclaresOnlyTheModuleSwitchWithDefaultTrue` |
| 行为 | 开关 `false` 时无客户端 Bean 且写入 `eureka.client.enabled=false` | `GXEurekaClientSwitchTest$SwitchOff#switchOffDisablesEurekaClient` |

另外，autoconfigure metadata 必须保持上述两键，且不得包含 `eureka700`（`GXEurekaClientContractTest#autoconfigureMetadataKeepsDocumentedKeys`）。

## 5. 装配异常时的降级顺序（回滚契约）

若 `GXEurekaClientDefaultsPostProcessor` 在任一 Web 类型下引发上下文失败：

1. 回滚该 PostProcessor，保留 `@ConditionalOnProperty`（开关退化为"仅关闭本模块配置类"）；
2. 在 `audit-report.md` 登记为未解决项，写明失败上下文、异常文本与影响面；
3. MUST NOT 通过删除开关或放宽测试来"修复"。
