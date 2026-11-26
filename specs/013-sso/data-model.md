# Phase 1 数据模型：leaf-base-sso 审查

**Feature**: 013-sso-module-audit | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

本文件定义审查交付物与修复后行为所需的**领域对象**（不含实现代码）。所有实体都服务于两个用途：让结论可复核（Finding / Evidence / 各矩阵），让修复可验收（Token 生命周期状态机 / 配置契约 / 指标 / 测试矩阵）。

---

## 1. Finding（审查发现）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `F-nn` | 唯一、稳定（修复后不复用编号） | 与 spec 的 F-01 ~ F-37 一一对应 |
| `severity` | `HIGH / MEDIUM / LOW` | 必填 | 高 4 / 中 19 / 低 14 |
| `dimension` | `CORRECTNESS / THREAD_SAFETY / PERFORMANCE / SECURITY_CONTRACT` | 必填、可多值 | 与 spec 的维度表一致 |
| `location` | `文件#行区间` | 必填、可多条 | 只允许指向**当前**源码 |
| `trigger` | 文本 | 必填 | 触发条件（配置组合 / 并发 / 输入） |
| `impact` | 文本 | 必填 | 影响面（安全 / 可用性 / 性能 / 契约） |
| `evidenceRefs` | `E-nn[]` | 至少 1 条 | 指向 Evidence |
| `repro` | 文本 | 必填 | 既有用例 / 新增用例设计 / 构造步骤 |
| `regressionTest` | 文本 | 必填 | "旧代码失败、新代码通过"的用例名与断言点 |
| `fixBatch` | `B1..B6` | 必填 | 归属批次（供 `/speckit.tasks` 拆解） |
| `status` | `OPEN / FIXED / ACCEPTED_RISK` | 初始 `OPEN` | `ACCEPTED_RISK` 必须在报告中给出理由与到期版本 |

**校验规则**：`severity=HIGH` 必须同时具备 `repro` 与 `regressionTest`（SC-007）；`status=FIXED` 必须存在通过的同名用例（FR-021）。

## 2. Evidence（证据）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `E-nn` | 唯一 | spec 中 E-01 ~ E-34 |
| `kind` | `SOURCE / COMMAND / UPSTREAM` | 必填 | 本模块源码 / 实测命令输出 / framework 源码 |
| `locator` | `文件#行区间` 或 命令行 | 必填 | `COMMAND` 型必须给出完整命令与关键输出片段 |
| `fact` | 文本 | 必填、单句、可判定真伪 | 不允许"可能/大概" |
| `verified` | `boolean` | 必填 | `false` 时该发现的判定必须降级为"未验证假设"（FR-003） |

## 3. TokenLifecycle（登录态状态机）

修复后的唯一状态集合与迁移（`GXSSOTokenStateEnums` 与之对应）：

| 状态 | 含义 | 进入条件 | 退出（迁移） |
| --- | --- | --- | --- |
| `ABSENT` | 请求未携带 Token（无 Header / 无 Cookie / 无请求属性） | 解析结果为空 | 登录成功 → `ACTIVE` |
| `ACTIVE` | 服务端缓存命中且 `loginAt` 与 Token 一致 | `cache.get` 命中 + `verifyTokenConsistency` 通过 | 过期 / 被踢 / 登出 / 缓存不可用 |
| `CACHE_MISS` | 缓存中不存在该键（重启、被清、TTL 到期） | `cache.get` 返回空 | 重新登录 → `ACTIVE` |
| `EXPIRED` | 签名过期或缓存 TTL 到期 | `authCode` 校验失败且解码成功 / `checkLoginStatus=false` | 重新登录 |
| `KICKED` | 同用户被新登录顶掉（`loginAt` 不一致） | `verifyTokenConsistency=false` | 重新登录（设置 `SSO_KICK_FLAG`） |
| `CACHE_UNAVAILABLE` | 未装配缓存且未开启降级 | `cache == null && !cacheFallbackEnabled` | 实现缓存或开启降级（对外统一 401，原因码可观测） |
| `PLUGIN_REJECTED` | 插件 `validateToken=false` | 插件返回 false | 插件放行后重新校验 |
| `LOCAL_ONLY` | 显式开启降级后的本地 Token（无服务端校验） | `cacheFallbackEnabled=true && cache == null` | 缓存恢复后回 `ACTIVE` |

**迁移约束**：
1. `ACTIVE → KICKED` 必须由**服务端**判定，不允许客户端自证（现状满足，P-02）。
2. `LOCAL_ONLY` 的 Token 必须带绝对过期（`min(cacheExpires, 900)`），且不得进入 `ACTIVE`（不允许"降级后又被缓存认领"）。
3. `CACHE_UNAVAILABLE` 与 `ABSENT` 的对外响应均为 **401**（不泄露内部状态），但必须通过原因码（`cache-unavailable` vs `absent`）与指标（`cacheMiss`/`cacheFallback`）区分（D-09、D-15）。

## 4. TerminationMatrix（终止矩阵）

行 = 终止原因，列 = 观测点。每格必须给出修正后的期望值，并在报告中标注现状值。

| 终止原因 | 缓存变化 | Cookie 变化 | 返回值 / 原因码 | 客户端表现 |
| --- | --- | --- | --- | --- |
| 正常登出 | 删除该键（幂等） | 清除（`Max-Age=0`，带 domain/path） | `true` | 跳转 `logoutUrl` 或 401 JSON |
| 被踢（新登录） | 保留新键，旧 `loginAt` 失配 | 本次不清除（由客户端重登时覆盖） | `false` + `KICKED` | 401 / 跳登录页 |
| 缓存未命中 | 无变化 | 无变化 | `false` + `CACHE_MISS` | 401 / 跳登录页 |
| 缓存不可用（未降级） | 不写 | 不写 | `false` + `CACHE_UNAVAILABLE` | 401 JSON，原因码 `cache-unavailable` |
| 缓存不可用（已降级） | 不写 | 写入，TTL ≤ 900s | `true`（登录）/ `LOCAL_ONLY`（校验） | 正常但可观测到降级 |
| Token 过期 | 键可能已随 TTL 消失 | 无变化（浏览器侧可能仍持有） | `false` + `EXPIRED` | 401 / 跳登录页 |
| 插件拒绝 | 不写 | 不写 | `false` + `PLUGIN_REJECTED` | 401 / 明确业务提示 |

**校验规则**：任意两行的"返回值 / 原因码"不得相同（否则无法区分，违反 FR-005）；"正常登出"的缓存删除必须幂等（重复调用返回 `true`，F-10）。

## 5. PermissionMatrix（权限判定矩阵）

行 = 注解组合，列 = 配置组合；单元格 = `ALLOW` / `DENY` / `ALLOW_IF_PERMITTED`（需要授权器判定）。

| 注解组合 | `permissionUri=false, nothingAnnotationPass=true`（默认） | `permissionUri=false, nothingAnnotationPass=false` | `permissionUri=true, nothingAnnotationPass=true` | `permissionUri=true, nothingAnnotationPass=false` |
| --- | --- | --- | --- | --- |
| 无注解 | `ALLOW` | `DENY` | `ALLOW_IF_PERMITTED(uri)` | `ALLOW_IF_PERMITTED(uri)` |
| `@GXPermissionAnnotation("p")` | `ALLOW_IF_PERMITTED("p")` | 同左 | `ALLOW_IF_PERMITTED(uri)` **优先**，URI 不过即 `DENY` | 同左 |
| `@GXPermissionAnnotation(action=Skip)` | `ALLOW` | `ALLOW` | `ALLOW` | `ALLOW` |
| `@GXIgnoreLoginIntercept` | `ALLOW` | `ALLOW` | `ALLOW` | `ALLOW` |

**关键修正点**：第 1 行第 3/4 列——现状为 `ALLOW`（`nothingAnnotationPass=true` 短路，F-01），修正后必须为 `ALLOW_IF_PERMITTED(uri)`；第 2 行第 3/4 列——现状 URI 不过会回落到注解/默认值，修正后 URI 判定为**终局**。

**校验规则**：`@GXIgnoreLoginIntercept` 与 `Skip` 在 4 种配置下恒为 `ALLOW`（既有 4 条用例锁定，P-07 相邻契约不得破坏）。

**覆盖方式（`GXSSOInterceptorBehaviorTest`）**：`permissionMatrixIsDecidedBySingleChain` 参数化覆盖"无注解 / Normal / Skip" × 4 种配置 = **12 个单元格**；`@GXIgnoreLoginIntercept` 行在 `preHandle` 第 52 行短路（不读取任何配置），由 `ignoreLoginAnnotationWithoutPermissionAllowsAnonymousRequest`、`ignoreLoginAnnotationAllowsAnonymousRequestWhenUriPermissionModeEnabled`、`ignoreLoginAnnotationBypassesPermissionTokenValidation` 三条专测覆盖（两个 `nothingAnnotationPass` 取值因短路而等价）；`permissionUri=true` 且 `requestURI` 为空（`null`/空白）时由 `blankUriIsDeniedWhenUriPermissionModeEnabled` 锁定为 **`DENY`**（fail-closed，即"第 1 行第 3/4 列"的空值分支）。

## 6. ConfigContract（配置契约表）

| 配置键 | 绑定类 | 默认值（修正后） | 是否被代码读取 | 生效时机 | 手册现状 |
| --- | --- | --- | --- | --- | --- |
| `sso.config.encoding` | `GXSSOProperties` | `UTF-8` | 是 | 请求处理时 | 一致 |
| `sso.config.token-name` | 同上 | `token` | 是（Header 名 + Token 字段名） | 请求处理时 | **不一致**（手册写 `ssoToken`） |
| `sso.config.cookie-name` | 同上 | `uid` | 是（Cookie 名） | 请求处理时 | **不一致**（手册称"已废弃"） |
| `sso.config.cookie-domain` | 同上 | `null` | 是 | 写 Cookie 时 | 一致 |
| `sso.config.cookie-path` | 同上 | `/` | 是 | 写 Cookie 时 | 一致 |
| `sso.config.cookie-secure` | 同上 | `false` | 是 | 写 Cookie 时 | 一致 |
| `sso.config.cookie-http-only` | 同上 | `true` | 是 | 写 Cookie 时 | 一致 |
| `sso.config.cookie-same-site` | 同上 | `Lax` | 是 | 写 Cookie 时（`None` 强制 `Secure`） | **未覆盖** |
| `sso.config.cookie-max-age` | 同上 | `-1` | 是 | 写 Cookie 时 | 一致 |
| `sso.config.cookie-browser` | 同上 | `false` | 是 | 请求校验时 | 一致 |
| `sso.config.cookie-check-ip` | 同上 | `false` | 是 | 请求校验时（null IP → 拒绝） | 一致 |
| `sso.config.trust-forwarded-ip-headers` | 同上 | `false` | 是 | IP 解析时 | **未覆盖** |
| `sso.config.login-url` | 同上 | `""` | 是 | 未登录重定向时 | 一致 |
| `sso.config.logout-url` | 同上 | `""` | 是 | 登出后跳转 | 一致 |
| `sso.config.param-return-url` | 同上 | `ReturnURL` | 是 | 登录回跳参数 | 一致 |
| `sso.config.cache-expires` | 同上 | **`7200`**（原 `-1`） | 是 | 签发 + 缓存 TTL | **不一致**（手册无此键） |
| `sso.config.permission-uri` | 同上 | `false` | 是 | 权限判定 | 一致（手册键名 `isPermissionUri`） |
| `sso.config.source` | 同上 | `nacos` | **新增**：配置源选择 | 启动期 | **不存在** |
| `sso.config.cache-fallback-enabled` | 同上 | `false` | **新增**：降级开关 | 登录/校验时 | **不存在** |
| `sso.config.capacity` | 同上 | `4096` | **新增**：注解缓存上限（FIFO 淘汰） | 首次解析时 | **不存在** |
| `sso.config.sign-key` / `sign-algorithm` / `rsa-*` | 同上 | `null` / 保留但无读取 | **否**（`getSignKey()` 返回 `null` 并带 Javadoc 说明"保留字段、当前实现未读取"；其余字段由 `@Data` 原样生成） | 无效 | **不一致**（手册列为可用；T109 已改为"保留但无效"） |
| `url.white-lists` | `GXUrlWhiteListsConfigProperties` | 空列表 | 是 | 拦截时 | **不一致**（手册写 `maple.sso.url-white-lists`） |
| `sso.config.illegal-url` / `sso.config.nothing-annotation-pass` | `GXSSOProperties`（绑定）+ `GXSSOPermissionInterceptor` 显式覆盖 | `null` / `true` | 是（显式 setter 优先于配置） | 权限判定 | 一致（T112 起绑定为配置项） |

**校验规则**：`是否被代码读取 = 否` 的键必须在手册中标注"保留但无效"或删除（FR-013）；新增键必须在 `additional-spring-configuration-metadata.json` 中登记（宪法 IV）。

## 7. SharedStateInventory（共享状态清单）

| 对象 | 共享范围 | 现状保护 | 修正后 | 对应发现 |
| --- | --- | --- | --- | --- |
| `GXSSOHelperUtil.ssoConfig` | 全 JVM 静态 | `volatile` + DCL | 保留 + 写入需 `setSsoConfig` 且指标记录替换次数 | F-08、F-20 |
| `GXSSOProperties` 实例 | 全 JVM | 无可变保护（`@Data`） | 约定只读：`GXSSOHelperUtil#setSsoConfig` 是唯一受支持的写入口（`volatile` + DCL 发布），请求路径只读取；**冻结标记经评估放弃**（见下方注记） | F-08 |
| `GXHandlerMethodAnnotationUtils.ANNOTATION_CACHE` | 静态 | `ConcurrentHashMap` + 超限 `clear()` | 判定结果 + 字符串键 + FIFO 淘汰（D-11） | F-20 |
| `AbstractCaptcha` 配置字段 | 实例（可跨请求共享） | 无（读路径写字段） | 构造期定型 + 首次生成后冻结（D-12） | F-21、F-22 |
| `GXSSOAuthorizationInterceptor.handlerRef` | 单实例 | `AtomicReference` + CAS | 不变（`P-07`） | — |
| `GXSSOPermissionInterceptor.illegalUrl/nothingAnnotationPass` | 单实例 | `volatile` | 不变 + 启动日志打印生效值 | F-15 |
| `GXSSODefaultHandler.JSON_CONFIG_REF` | 静态 | `AtomicReference` + CAS | 不变 | — |
| `GXRandomUtil.SECURE_RANDOM` | 静态 | `SecureRandom` 线程安全 | 不变（仅记录竞争口径） | F-25 |
| 测试内 `GXSSOHelperUtil.setSsoConfig` | 全测试 JVM | 无 | `@AfterEach` 复原 + `@ResourceLock`（D-16） | F-35 |

**注记（T106，冻结标记不实施）**：`GXSSOProperties` 的"冻结后 setter 抛异常"经评估不实施，理由：① 该类为 Lombok `@Data`，加逐 setter 护栏需手写约 30 个方法，收益仅为文档级约定，违反"复杂度必须被证明"；② 配置热更新按"整体替换实例"生效，冻结与替换路径语义冲突；③ 现有保护已足够——`GXSSOHelperUtil#setSsoConfig` 是唯一受支持的发布入口（`volatile` + DCL）、请求路径只读、且 `GXSSOHelperUtil.bean` 的缓存以"上下文身份 + 版本"为键，替换后自然失效。若后续需要强约束，优先考虑"不可变快照 + Builder"，而非逐 setter 守卫。

## 8. Metrics（可观测口径）

| 指标 | 类型 | 递增点 | 用途 |
| --- | --- | --- | --- |
| `parseSuccess` | `LongAdder` | Token 解析成功 | 基线吞吐 |
| `parseFailure` | `LongAdder` | 解析失败（非空但无效） | 攻击/配置错误探测 |
| `cacheHit` | `LongAdder` | `cache.get` 命中 | 缓存有效性 |
| `cacheMiss` | `LongAdder` | `cache.get` 未命中 | 掉线率 |
| `denied` | `LongAdder` | 权限拒绝 | 授权配置验证 |
| `loginFailure` | `LongAdder` | 登录失败（含插件拒绝） | 登录成功率 |
| `cacheFallback` | `LongAdder` | 进入 `LOCAL_ONLY` | 降级告警 |
| `activeTokens` | `AtomicLong` 快照 | `set`/`delete` 后按缓存桶大小刷新 | 在线规模 |
| `invalidToken` | `LongAdder` | `userId<=0` 等参数非法 | 客户端错误 |

**校验规则**：快照必须不可变（`record`）；不得以 INFO 级别逐请求输出（FR-024）；指标名 ASCII 且与日志事件名一致。

## 9. TestMatrix（测试覆盖矩阵）

| 测试类 | 类型 | 覆盖 | 保留断言 |
| --- | --- | --- | --- |
| `GXSSOServiceBehaviorTest` | 单元 | 缓存/登出/插件/本地模式/过期 | 既有 15 条（P-01、P-02、P-06、P-08 相邻） |
| `GXSSOAuthFlowSpringBootTest` | 集成（新增） | 真实拦截链：公开→登录→受保护→登出→被踢 | — |
| `GXSSOConcurrencyTest` | 并发（新增） | 并发登录覆盖、并发注解解析、并发踢人、并发验证码 | — |
| `GXSSOCacheContractTest` | 契约（新增） | `get` 不抛异常、`delete` 幂等、TTL 传参、原因码 | — |
| `GXSSOConfigSourceTest` | 装配（新增） | `source` 开关互斥、默认值、密钥校验、元数据存在 | — |
| `CaptchaRenderTest` | 渲染（新增） | PNG/GIF 魔数、`length=1`、`width<length`、写失败清理 | — |
| `GXSSOInterceptorBehaviorTest` | 单元 | 权限矩阵 16 组合、白名单 context-path、匿名零副作用 | 既有 9 条（P-07） |
| `GXLoginUserHandlerMethodArgumentResolverTest` | 单元 | 成功映射、用户不存在抛异常 | 既有 2 条 |
| `GXSSOUtilsBehaviorTest` | 单元 | 指纹、IP、URL、白名单分段匹配 | 既有 6 条（P-05） |
| `GXSSOHelperUtilTest` | 单元（新增） | bean 缓存失效、parser 空值、指标递增 | — |
| `GXEnumBehaviorTest` | 单元 | 枚举兜底 + `GXSSOTokenStateEnums` | 既有 3 条 |

**校验规则**：既有 40 条断言一条不得删除或跳过（FR-012、FR-022）；新增用例必须能在无 Redis/Nacos 环境下运行（FR-015）。

### 9.1 受影响路径 × 5 类用例（FR-020 判定依据）

每个生产改动路径必须能在此矩阵中定位到"成功 / 失败 / 异常 / 空值 / 边界"至少各 1 条；标 `—` 表示该维度对当次改动不适用，须在 PR 说明中写明理由。

类名简写：`Flow`=`GXSSOAuthFlowSpringBootTest`、`Svc`=`GXSSOServiceBehaviorTest`、`Itc`=`GXSSOInterceptorBehaviorTest`、`Cache`=`GXSSOCacheContractTest`、`Cap`=`CaptchaRenderTest`、`Cfg`=`GXSSOConfigSourceTest`、`Utl`=`GXSSOUtilsBehaviorTest`、`Cc`=`GXSSOConcurrencyTest`、`Res`=`GXLoginUserHandlerMethodArgumentResolverTest`、`Repro`=`GXSSOAuditReproTest`。

| # | 受影响路径 | 成功 | 失败 | 异常 | 空值 | 边界 | 关联任务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 登录写 Cookie（`setCookie` → `ResponseCookie`） | `Flow#loginThenValidateThenKickThenLogout` | `Svc#loginFailsWhenCacheUnavailable` | `Svc#pluginLoginFailureRollsBackCacheAndThrows` | `Svc#cookieMaxAgeAttributeAcceptsNonIntegerTypes` | `Svc#invalidSameSiteCombinationIsRejected`、`Svc#unsupportedSameSiteValueIsRejected`、`Svc#degradedModeClampsCookieMaxAgeToFallbackTtl` | T031、T030、T114 |
| 2 | 未登录 / 拒绝路径（零副作用） | `Flow#anonymousRequestHasNoSideEffect` | `Flow#anonymousPageRequestHasNoSideEffect` | `Flow#rejectedParseableTokenPageRequestHasNoSideEffect` | `Svc#anonymousLogoutWithoutCookieWritesNoSetCookie` | `Flow#rejectedRequestLogsNoInfo`（日志级别） | T033、T091 |
| 3 | 权限判定链（Ignore / Skip / URI / 注解） | `Itc#permissionMatrixIsDecidedBySingleChain` | `Itc#permissionMatrixIsDecidedBySingleChain`（`none,true,*` 两行由旧实现的"默认放行"翻转为拒绝）、`Repro#f01UriPermissionModeIsBypassedByDefault` | `Itc#ignoreLoginAnnotationBypassesPermissionTokenValidation`、`Itc#authorizationInterceptorSkipsTokenValidationForIgnoreLoginAnnotation` | `Itc#blankUriIsDeniedWhenUriPermissionModeEnabled`（`requestURI` 为空） | `Itc#ignoreLoginAnnotationAllowsAnonymousRequestWhenUriPermissionModeEnabled`、`Itc#permissionSkipDoesNotRequireTokenWhenUriPermissionModeEnabled`；配置驱动：`Itc#nothingAnnotationPassIsReadFromConfiguration`、`Itc#nothingAnnotationPassPrefersExplicitSetterOverConfiguration`、`Itc#configuredIllegalUrlRedirectsInsteadOfForbidden`（`illegal-url` 非空 → 302）、`Itc#explicitIllegalUrlSetterOverridesConfiguration` | T032、T013、T103、T112、T116 |
| 4 | 白名单匹配（含 context-path） | `Itc#whiteListMatchesPathWithinContextPath` | `Itc#emptyWhiteListIsFailClosed` | `Itc#authorizationInterceptorSkipsTokenValidationForErrorDispatch` | `Itc#emptyWhiteListIsFailClosed` | `Itc#trailingSlashPatternIsHonoured`、`Itc#whiteListAcceptsRawUriForm` | T075、T032 |
| 5 | 缓存读写（get / set / delete / TTL） | `Svc#defaultCacheReadsWritesAndDeletesServerSideToken` | `Cache#cacheReadFailureIsReportedAsUnavailable` | `Cache#getNeverThrowsAndReturnsEmptyDictWithReason` | `Cache#kickLoginWithNullReturnsFalse` | `Cache#deleteIsIdempotent`、`Cache#defaultConfigCarriesPositiveTtl` | T027、T090 |
| 6 | 注解缓存（容量 / 并发） | `Itc#annotationLookupFindsAndCachesMergedAnnotations` | — | — | — | `Itc#capacityEvictsOldestInsteadOfClear`、`Cc#concurrentAnnotationLookupIsStable` | T041、T101 |
| 7 | 验证码渲染与消费 | `Cap#pngStartsWithMagicBytes`、`Cap#gifStartsWithGif89a` | `Cap#storeFailureRaisesBusinessException` | `Cap#instanceIsFrozenAfterFirstGenerate` | `Cap#nullCaptchaDoesNotInvalidateTicket` | `Cap#singleCharGifDoesNotThrow`、`Cap#widthSmallerThanLengthIsHandled`、`Cc#concurrentCaptchaVerificationConsumesOnce` | T042、T043、T044 |
| 8 | 配置装配（source / 默认值 / 密钥） | `Cfg#sourceLocalBindsLocalBeanOnly` | `Cfg#defaultSecretIsDetected` | `Cfg#sourceNacosIsDefault`（默认分支） | `Cfg#metadataContainsAllKeys` | `Cfg#defaultValuesMatchContract`、`Cfg#sensitiveFieldsHaveNoSourceDefaults` | T029、T058 |
| 9 | 用户身份注入（`@GXLoginUserAnnotation`） | `Res#resolvesDictUserToDeclaredDtoType`、`Res#supportsParameterAcceptsOnlyUserInfoDto`（边界：只接受 `GXUserInfoDto` 子类） | `Res#missingUserThrowsBusinessException`、`Res#malformedFallbackTokenThrowsBusinessException` | `Res#malformedFallbackTokenThrowsBusinessException`（旧实现返回 `null` → 新实现抛异常） | `Res#anonymousRequestWithoutTokenThrowsBusinessException`（无 Token），旧用例 `malformedFallbackHeaderReturnsNull` 已删除 | — | T060、T104 |
| 10 | 服务接口入口（`GXSSOService` 默认方法 / 登出响应） | `Flow#serviceInterfaceExposesLogoutAndAttrToken`、`Svc#logoutWithoutLogoutUrlReturnsUnifiedJson`（未配置 `logout-url` 时返回统一响应） | — | — | — | — | T035、T100、T112 |
| 11 | 工具类（IP / URL / 请求体 / 指纹） | `Utl#localRequestIpReusesResolvedAddress` | `Utl#requestPlayloadRejectsOversizedBody` | `Utl#inContainUrlMatchesByPathSegment` | `Utl#ipHelperIgnoresForwardedHeadersByDefault` | `Utl#browserFingerprintUsesFullSha256AndAcceptsLegacyFingerprint`、`Utl#requestUrlDoesNotTrustRawHostHeader` | T051、T052 |
| 12 | 文档 / 第三方注释（F-33 / F-37） | 豁免 | 豁免 | 豁免 | 豁免 | 豁免 | T061、T062（理由见 `audit-report.md` §6.5 豁免表） |
