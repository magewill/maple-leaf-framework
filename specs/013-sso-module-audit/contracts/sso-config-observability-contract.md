# 契约 3：配置、装配与可观测性（sso-config-observability-contract）

**适用范围**：`GXSSOProperties` 及其子类（local/nacos）、`GXSSOConfigProperties`、`GXUrlWhiteListsConfigProperties`、`GXTokenConfigService`、`GXSSOMetrics`、日志与响应构造。
**稳定性**：本契约描述修复后的目标语义；`[变更]`/`[新增]` 条目必须同步写入 PR 与手册。

## 1. 配置源选择（`[变更]`）

| `sso.config.source` | 生效 Bean | 数据来源 | 说明 |
| --- | --- | --- | --- |
| `nacos`（默认，`matchIfMissing`） | `GXNacosSSOConfigProperties` | Nacos `sso.yml`，`autoRefreshed=true` | 与仓库现状一致；Nacos 不可用时**不得静默使用空配置**，必须 ERROR 日志 |
| `local` | `GXLocalSSOConfigProperties` | `classpath:/${spring.profiles.active}/sso.yml` | 文件缺失：WARN + 使用默认值（`ignoreResourceNotFound=true`），不再启动失败 |

**不变式**：
1. 两个变体必须**互斥**，且由显式开关控制，不再依赖"类路径上是否有 Nacos 类"（现状恒为 Nacos，F-06）。
2. 白名单配置（`url.*`）必须与主配置使用**同一来源**（同一开关、同一 profile），不得一半 Nacos 一半本地。
3. 两处 `autoRefreshed` 必须一致（`[变更]`，F-07）；启动期生效的键（`source`、`token-name`、`cookie-name`）热更新后必须 WARN"需重启生效"。

## 2. 配置键清单与默认值

见 [data-model.md §6](../data-model.md)。本契约只补充约束：

| 约束 | 内容 |
| --- | --- |
| 键名风格 | kebab-case（`cache-expires`）；手册与代码必须一致 |
| 前缀 | 主配置 `sso.config.*`；白名单 `url.white-lists` |
| 新增键（3 个） | `sso.config.source`、`sso.config.cache-fallback-enabled`、`sso.config.capacity`（注解缓存上限，默认 4096） |
| 元数据 | 全部键必须登记到 `META-INF/additional-spring-configuration-metadata.json`（`[新增]` 文件，F-32） |
| 敏感值 | `sign-key`、`rsa-*` 不得有源码默认值；`${ENV:}` 注入，缺失时按 profile 决定启动失败或 ERROR |
| 保留但无效的键 | `sign-algorithm`、`rsa-jks-store`、`rsa-cert-store`、`rsa-alias`：手册必须标注"保留、当前无效"，或从手册删除 |

**不变式**：任何 `GXSSOProperties` 的 getter 不得抛 `GXBusinessException`（现状 `getSignKey`/`getRsaJksStore`/`getRsaCertStore` 会抛）；校验必须在启动期完成，不得延后到请求期（F-05）。

## 3. 启动期校验（`[新增]`）

| 校验项 | 条件 | 结果 |
| --- | --- | --- |
| Token 密钥 | `GXTokenConfigService.getTokenSecret()` 返回空 | `prod`/`production` → 抛 `GXBusinessException` 拒绝启动；其它 → ERROR |
| 默认密钥 | 返回公开常量 `GXTokenDefaultSecret` | ERROR（明确提示"仅限开发"） |
| RSA 口令 | `rsa-keypass`/`rsa-storepass` 非空 | WARN（无效配置，建议删除） |
| Cookie 组合 | `cookie-same-site=None && !cookie-secure` | ERROR，并在写 Cookie 时拒绝 |
| 缓存 | 无 `GXSSOCache` Bean | WARN（说明进入本地模式或 fail-closed，取决于开关） |
| 生效值 | 任何启动 | INFO 打印 `source`、`cacheExpires`、`cookieName`、白名单条数（不含密钥） |

## 4. 指标（`GXSSOMetrics`）

| 指标 | 递增点 | 级别 |
| --- | --- | --- |
| `parseSuccess` / `parseFailure` | Token 解析 | — |
| `cacheHit` / `cacheMiss` | 缓存读取 | — |
| `denied` | 权限拒绝 | — |
| `loginFailure` | 登录失败（含插件拒绝） | — |
| `cacheFallback` | 进入 `LOCAL_ONLY` | 同时 ERROR 日志（每进程首次 + 每分钟限流） |
| `activeTokens` | `set`/`delete` 成功后按 `GXBaseCacheService#size(bucket)` 刷新 | — |
| `invalidToken` | `userId<=0` 等 | DEBUG |

**不变式**：指标必须可通过公共 API 读取（`GXSSOMetrics.snapshot()` 返回不可变 `record`）；不得依赖反射读取；不得引入 Micrometer 等新依赖。

## 5. 日志事件（ASCII，键值对）

| 事件 | 级别 | 字段 | 备注 |
| --- | --- | --- | --- |
| `event=sso.login.success` | INFO | `userId`, `source=local|cache`, `profile` | 每登录一次 1 条 |
| `event=sso.login.failure` | WARN | `reason`, `userId?` | 插件拒绝、缓存不可用、参数非法 |
| `event=sso.login.degraded` | ERROR | `ttlSeconds`, `reason=cache-unavailable` | 降级唯一入口 |
| `event=sso.token.reject` | DEBUG | `reason`, `path` | 未登录/过期/被踢/缓存不可用 |
| `event=sso.permission.denied` | INFO | `userId?`, `resource`, `mode=uri|annotation` | 403 时 1 条 |
| `event=sso.logout` | INFO | `userId?`, `result` | 幂等登出也记录 |
| `event=sso.config.invalid` | ERROR | `key`, `reason` | 启动校验失败/无效配置 |

**不变式**：
1. 日志**不得**输出 Token 原文、密钥、Cookie 值（只允许 `userId`/`loginAt`/`flag`）。
2. 未登录路径不得输出 INFO（现状每请求 1 条，F-27）。
3. 所有消息 ASCII；异常消息面向用户时可中文，但必须经 `GXBusinessException` 统一承载。

## 6. 响应构造（宪法 III）

| 场景 | 构造方式 | 字段 |
| --- | --- | --- |
| 未登录 AJAX | `GXResultUtils.error(status, msg)` | `code`/`msg`/`data=null` |
| 无权限 AJAX | 同上（403） | 同上 |
| 缓存不可用 | 同上（401），原因码 `cache-unavailable` | `msg` 不含内部细节 |
| 登出跳转 | `sendRedirect` / 401 JSON | — |

**不变式**：不得再用 `Dict.create().set("code", ...)` + `getWriter().write(...)` 手工拼装（现状 `GXSSODefaultHandler`、`GXHttpUtil.ajaxStatus`，F-30）；`GXHttpUtil.ajaxStatus` 保留为兼容壳，内部委托统一构造。

## 7. 兼容性条目汇总

| 变更 | 触发条件 | 迁移 |
| --- | --- | --- |
| 配置源改为显式开关 | 依赖"有 Nacos 类就走 Nacos"的部署 | 无需配置（默认即 Nacos）；本地开发设 `sso.config.source: local` |
| 本地文件缺失不再启动失败 | 依赖"缺配置即失败"做环境校验的运维 | 由启动校验的 ERROR 日志替代 |
| 新增 3 个配置键 | — | 全部有默认值，零迁移 |
| `GXSSOProperties` getter 不再抛异常 | 依赖 getter 抛异常做存在性判断的下游 | 启动校验已前置 |
| 未登录改为 `GXResultUtils` | 按字段名解析的前端 | 字段名不变 |
