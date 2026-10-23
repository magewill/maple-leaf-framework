# 审查报告：leaf-base-sso 正确性、线程安全与性能

**Feature**: 013-sso-module-audit | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

> 本报告与 `spec.md` 的摘要相互一致（FR-014）：`spec.md` 保留结论摘要与 E-01 ~ E-34 / F-01 ~ F-37 编号，本报告给出完整证据、矩阵、复现与验证记录。

## 1. 依赖基线与审查范围

| 项 | 值 | 来源 |
| --- | --- | --- |
| 生产源码 | 62 个 `.java`（45 功能类 + 17 `package-info`） | `leaf-base-sso/src/main/java` |
| 既有测试 | 6 个类 / 40 条用例 | `leaf-base-sso/src/test/java` |
| 模块依赖 | `leaf-base-framework`、`leaf-base-nacos`（compile）、`spring-boot-starter-test`（test） | `leaf-base-sso/pom.xml` 第 14–30 行 |
| Nacos 传递依赖 | `nacos-client:3.1.1:compile`（实测依赖树） | `mvn -o -pl leaf-base-sso dependency:tree "-Dincludes=com.alibaba.nacos"` |
| 缓存实现现状 | **无** `implements GXSSOCache` 生产类（仅测试内匿名实现） | 全仓检索 |
| 资源目录 | **不存在** `src/main/resources`（本次新增 `META-INF/additional-spring-configuration-metadata.json`） | `list_dir` |
| 构建 | JDK `release=21` + ErrorProne/NullAway `ERROR`（`AnnotatedPackages=cn.maple`） | 根 `pom.xml` 第 43、565 行 |
| 零新增依赖 | 是（`ResponseCookie`/`Caffeine` 均为已传递能力） | `plan.md` Constraints |

### 1.1 文件 → 判定（FR-001，覆盖 100%）

判定口径：**无缺陷** = 审查后未发现需修复问题；**风险** = 有发现但已由修复或契约声明收敛；**缺陷** = 审查确认存在缺陷（均已修复，见 F 编号）；**未覆盖原因** = 无运行路径或仅声明性代码。

| # | 文件（`leaf-base-sso/src/main/java/`） | 判定 | 关联 |
| --- | --- | --- | --- |
| 1 | `cn/maple/sso/annotation/GXLoginUserAnnotation.java` | 无缺陷 | — |
| 2 | `cn/maple/sso/annotation/GXPermissionAnnotation.java` | 无缺陷 | — |
| 3 | `cn/maple/sso/annotation/package-info.java` | 无缺陷 | — |
| 4 | `cn/maple/sso/cache/GXSSOCache.java` | 缺陷（已修复） | F-02/F-09/F-10/F-11/F-12/F-24/F-25 |
| 5 | `cn/maple/sso/cache/package-info.java` | 无缺陷 | — |
| 6 | `cn/maple/sso/captcha/AbstractCaptcha.java` | 缺陷（已修复） | F-21/F-23/F-24 |
| 7 | `cn/maple/sso/captcha/CaptchaStoreSession.java` | 缺陷（已修复） | F-21（并发消费） |
| 8 | `cn/maple/sso/captcha/ColorType.java` | 无缺陷 | — |
| 9 | `cn/maple/sso/captcha/Encoder.java` | 风险（已声明） | F-37（来源声明） |
| 10 | `cn/maple/sso/captcha/GifEncoder.java` | 风险（已声明） | F-37 |
| 11 | `cn/maple/sso/captcha/ICaptcha.java` | 无缺陷 | FR-016（`@Nullable`） |
| 12 | `cn/maple/sso/captcha/ICaptchaStore.java` | 无缺陷 | — |
| 13 | `cn/maple/sso/captcha/ImageCaptcha.java` | 缺陷（已修复） | F-22 |
| 14 | `cn/maple/sso/captcha/package-info.java` | 无缺陷 | — |
| 15 | `cn/maple/sso/captcha/Quant.java` | 风险（已声明） | F-37 |
| 16 | `cn/maple/sso/constant/GXSSOConstant.java` | 无缺陷 | 新增 `SSO_TOKEN_STATE` |
| 17 | `cn/maple/sso/constant/GXSSOInterceptorOrder.java`（T102 已删除，常量并入 `GXSSOConstant`） | 无缺陷（未落地） | F-15 |
| 18 | `cn/maple/sso/constant/package-info.java` | 无缺陷 | — |
| 19 | `cn/maple/sso/dto/GXUserInfoDto.java` | 风险（已声明） | F-31（DTO 与用户服务契约） |
| 20 | `cn/maple/sso/dto/package-info.java` | 无缺陷 | — |
| 21 | `cn/maple/sso/enums/GXAction.java` | 无缺陷 | — |
| 22 | `cn/maple/sso/enums/GXRandomType.java` | 无缺陷 | — |
| 23 | `cn/maple/sso/enums/GXSSOTokenStateEnums.java` | 无缺陷（新增） | F-11 |
| 24 | `cn/maple/sso/enums/GXTokenFlag.java` | 无缺陷 | — |
| 25 | `cn/maple/sso/enums/GXTokenOrigin.java` | 无缺陷 | — |
| 26 | `cn/maple/sso/enums/package-info.java` | 无缺陷 | — |
| 27 | `cn/maple/sso/metrics/GXSSOMetrics.java` | 无缺陷（新增） | F-24 |
| 28 | `cn/maple/sso/metrics/package-info.java` | 无缺陷（新增） | — |
| 29 | `cn/maple/sso/oauth/GXSSOAuthorization.java` | 无缺陷 | — |
| 30 | `cn/maple/sso/oauth/package-info.java` | 无缺陷 | — |
| 31 | `cn/maple/sso/plugins/GXSSOPlugin.java` | 无缺陷 | P-03 |
| 32 | `cn/maple/sso/plugins/package-info.java` | 无缺陷 | — |
| 33 | `cn/maple/sso/properties/GXSSOConfigProperties.java` | 无缺陷 | — |
| 34 | `cn/maple/sso/properties/GXSSOProperties.java` | 缺陷（已修复） | F-04/F-05/F-32/F-34 |
| 35 | `cn/maple/sso/properties/GXUrlWhiteListsConfigProperties.java` | 无缺陷 | — |
| 36 | `cn/maple/sso/properties/local/GXLocalSSOConfigProperties.java` | 缺陷（已修复） | F-06 |
| 37 | `cn/maple/sso/properties/local/GXLocalUrlWhiteListsConfigProperties.java` | 缺陷（已修复） | F-06 |
| 38 | `cn/maple/sso/properties/local/package-info.java` | 无缺陷 | — |
| 39 | `cn/maple/sso/properties/nacos/GXNacosSSOConfigProperties.java` | 缺陷（已修复） | F-07 |
| 40 | `cn/maple/sso/properties/nacos/GXNacosUrlWhiteListsConfigProperties.java` | 缺陷（已修复） | F-07 |
| 41 | `cn/maple/sso/properties/nacos/package-info.java` | 无缺陷 | — |
| 42 | `cn/maple/sso/properties/package-info.java` | 无缺陷 | — |
| 43 | `cn/maple/sso/service/GXAbstractSSOService.java` | 缺陷（已修复） | F-02/F-03/F-08/F-16/F-17/F-18 |
| 44 | `cn/maple/sso/service/GXAuthorizationInterceptorService.java` | 无缺陷 | — |
| 45 | `cn/maple/sso/service/GXSSOService.java` | 风险（已收敛） | F-36 |
| 46 | `cn/maple/sso/service/GXSSOSupportService.java` | 缺陷（已修复） | F-10/F-19/F-29 |
| 47 | `cn/maple/sso/service/GXTokenConfigService.java` | 缺陷（已修复） | F-03/F-25（platform 键） |
| 48 | `cn/maple/sso/service/GXUUserService.java` | 无缺陷 | — |
| 49 | `cn/maple/sso/service/impl/GXConfigurableAbstractSSOServiceImpl.java` | 无缺陷 | — |
| 50 | `cn/maple/sso/service/impl/package-info.java` | 无缺陷 | — |
| 51 | `cn/maple/sso/service/package-info.java` | 无缺陷 | — |
| 52 | `cn/maple/sso/utils/GXBrowserUtil.java` | 风险（已收敛） | F-27（日志级别） |
| 53 | `cn/maple/sso/utils/GXHttpUtil.java` | 缺陷（已修复） | F-28/F-30 |
| 54 | `cn/maple/sso/utils/GXIpHelperUtil.java` | 缺陷（已修复） | F-26 |
| 55 | `cn/maple/sso/utils/GXRandomUtil.java` | 无缺陷 | P-05 相邻 |
| 56 | `cn/maple/sso/utils/GXSSOHelperUtil.java` | 缺陷（已修复） | F-08/F-12/F-25/F-27/F-36 |
| 57 | `cn/maple/sso/utils/package-info.java` | 无缺陷 | — |
| 58 | `cn/maple/sso/web/handler/GXSSODefaultHandler.java` | 缺陷（已修复） | F-30 |
| 59 | `cn/maple/sso/web/handler/GXSSOHandler.java` | 无缺陷 | — |
| 60 | `cn/maple/sso/web/handler/package-info.java` | 无缺陷 | — |
| 61 | `cn/maple/sso/web/interceptor/GXHandlerMethodAnnotationUtils.java` | 缺陷（已修复） | F-20 |
| 62 | `cn/maple/sso/web/interceptor/GXSSOAuthorizationInterceptor.java` | 缺陷（已修复） | F-13/F-14/F-15/F-30 |
| 63 | `cn/maple/sso/web/interceptor/GXSSOPermissionInterceptor.java` | 缺陷（已修复） | F-01 |
| 64 | `cn/maple/sso/web/interceptor/package-info.java` | 无缺陷 | — |
| 65 | `cn/maple/sso/web/support/GXLoginUserHandlerMethodArgumentResolver.java` | 缺陷（已修复） | F-31 |
| 66 | `cn/maple/sso/web/support/package-info.java` | 无缺陷 | — |
> 计数核对：本表 66 行 = 审查时 62 个文件（45 功能类 + 17 `package-info`）+ 新增 3 个文件（`GXSSOTokenStateEnums`、`GXSSOMetrics`、`captcha/package-info`）+ 1 个已合并并删除（`GXSSOInterceptorOrder`，两个顺序常量并入既有 `GXSSOConstant`，见 T102）。当前 `leaf-base-sso/src/main/java` 实际 **65** 个文件（47 功能类 + 18 `package-info`）。原始 62 个文件全覆盖，无跳过。

### 1.2 既有测试类判定（6 个）

| # | 测试类 | 判定 | 说明 |
| --- | --- | --- | --- |
| 1 | `CaptchaBehaviorTest`（5） | 无缺陷（未改） | 覆盖消费一次性、过期清理、渲染失败清理 |
| 2 | `GXEnumBehaviorTest`（3） | 无缺陷（未改） | 枚举兜底 |
| 3 | `GXSSOServiceBehaviorTest`（15 → 23） | 风险（已补强） | 原 15 条未覆盖"缓存不可用/降级/插件拒绝/幂等登出" |
| 4 | `GXSSOUtilsBehaviorTest`（6 → 9） | 风险（已补强） | 原缺 URL 边界、请求体上限、IP 热路径 |
| 5 | `GXSSOInterceptorBehaviorTest`（9 → 25） | 风险（已补强） | 原缺权限矩阵、白名单行为 |
| 6 | `GXLoginUserHandlerMethodArgumentResolverTest`（2 → 3） | 风险（已补强） | 原缺"用户不存在"分支 |

## 2. 证据表（E-01 ~ E-34）

| id | kind | locator | fact | verified |
| --- | --- | --- | --- | --- |
| E-01 | SOURCE | `GXSSOPermissionInterceptor.java#61-66`、`#28`、`#38-40` | `permission == null` 时 `requiresTokenForPermission` 返回 `!nothingAnnotationPass`，默认 `nothingAnnotationPass=true` → URI 校验永不执行 | true |
| E-02 | SOURCE+COMMAND | `GXSSOHelperUtil.java#38-70`、`GXSSOSupportService.java#48-63`、`GXSpringContextUtils.java#45-62` | `getBean` 缺失返回 null（不抛）→ 静默用默认配置（无 cache/无 plugin）→ 直接采信 Cookie；全仓无 `GXSSOCache` 生产实现，故此为默认路径 | true |
| E-03 | SOURCE | `GXTokenConfigService.java#17-22`、`GXTokenConstant.java#30`、`GXAuthCodeUtils.java#116-155` | 默认密钥为公开常量 `31D0D393D3EAF5`，签名为对称可逆 RC4+MD5 → 可离线伪造 | true |
| E-04 | SOURCE | `GXSSOProperties.java#141`、`GXAbstractSSOService.java#163-165`、`GXAuthCodeUtils.java#141,147`、`GXSSOCache.java#60-64` | 默认 `cacheExpires=-1` → 签名写 `0000000000`（永久）且缓存无 TTL | true |
| E-05 | SOURCE | `GXSSOProperties.java#61,66,201-206`、手册第 418–419 行 | 源码硬编码 `rsaKeypass`/`rsaStorepass`/默认 `signKey`，手册原样抄录 | true |
| E-06 | COMMAND+SOURCE | `pom.xml#20-24`、`GXLocalSSOConfigProperties.java#18`、`GXNacosSSOConfigProperties.java#18` | 类路径恒有 Nacos 注解类 → local 变体永不生效，`config/*/sso.yml` 不加载 | true |
| E-07 | SOURCE | `GXNacosSSOConfigProperties.java#19-23`、`GXNacosUrlWhiteListsConfigProperties.java#19` | 主配置无 `autoRefreshed`、白名单有 → 同类配置两套语义 | true |
| E-08 | SOURCE | `GXAbstractSSOService.java#75-87` | 登录写 Cookie 的"读—改—写"无原子性，并发登录互相覆盖 | true |
| E-09 | SOURCE | `GXSSOCache.java#116-122`、`GXAbstractSSOService.java#53-62` | `userId<=0` 抛 `GXTokenInvalidException`，`kickLogin(null)` 抛异常 | true |
| E-10 | SOURCE | `GXSSOCache.java#90-96` | `Objects.nonNull(result)` 判定成功 → 键不存在被判失败 | true |
| E-11 | SOURCE | `GXSSOCache.java#35-37`、`GXSSOSupportService.java#50-53` | "未登录"存在"抛异常"和"返回空"两种语义 | true |
| E-12 | SOURCE | `GXSSOCache.java#27`、`GXSSOHelperUtil.java#147` | `isRPC()` 短路 → 非 HTTP 上下文身份恒为空，未文档化 | true |
| E-13 | SOURCE | `GXSSOAuthorizationInterceptor.java#115-137`、`GXAbstractSSOService.java#198-199`、`GXSSOSupportService.java#147-169` | 匿名请求触发 cache.delete + plugin.logout + 清 Cookie | true |
| E-14 | SOURCE | `GXSSOAuthorizationInterceptor.java#70,93-100` | 白名单用 `getRequestURI()`（含 context-path）匹配 | true |
| E-15 | SOURCE | `GXWebMvcConfigurer.java#38-47`、手册第 497 行 | 拦截器按 bean 名字典序注册，无 `order()`；手册要求显式顺序 | true |
| E-16 | SOURCE | `GXAbstractSSOService.java#135-143` | `plugin.login=false` 仅 WARN，Cookie 照发 | true |
| E-17 | SOURCE | `GXCookieHelperUtil.java#18-29`、`GXAbstractSSOService.java#168-179` | `authJSESSIONID` 仅 invalidate + 写属性；随机串不参与会话 id，且丢弃会话数据 | true |
| E-18 | SOURCE | `GXAbstractSSOService.java#104-133` | 手写 `Set-Cookie` 不转义/不校验；`SameSite=None` 无 `Secure` 约束；下发已废弃头 | true |
| E-19 | SOURCE | `GXSSOSupportService.java#89-95`、`GXSSOHelperUtil.java#194-195`、`GXCurrentRequestContextUtils.java#304-341` | IP 为 null 时跳过校验（fail-open）；两套 IP 口径 | true |
| E-20 | SOURCE | `GXHandlerMethodAnnotationUtils.java#17-35,138-144` | 静态缓存存 `Annotation` 实例、超限整体 `clear()`、键持有 `Method` | true |
| E-21 | SOURCE | `AbstractCaptcha.java#25-53,101-133` | `@Data` 可变实例在`randomCaptcha` 读路径上写配置字段 | true |
| E-22 | SOURCE | `ImageCaptcha.java#132-137,115,77-79` | `length==1 && gif` → `getAlpha` 除零 → `AlphaComposite` 抛 `IllegalArgumentException`，且只 catch IOException | true |
| E-23 | SOURCE | `AbstractCaptcha.java#72-76,61-64` | `verification(null)` 主动删 ticket；`put` 失败时静默空响应 | true |
| E-24 | SOURCE | `GXAuthCodeUtils.java#128-150,157-176`、`GXSSOCache.java#31-32`、两个拦截器 | 每请求 4 次 MD5 + 2×256 轮 RC4 + JSON + 2–3 次 `getBean` + 插件循环 | true |
| E-25 | SOURCE | `GXIpHelperUtil.java#17-35,52-58,63-78` | 类初始化与 localhost 分支每请求触发 `InetAddress.getLocalHost()` | true |
| E-26 | SOURCE | `GXSSOSupportService.java#40,71,105-118`、`GXBrowserUtil.java#23,29` | 未登录每请求 1 条 INFO；UA 缺失每请求 WARN | true |
| E-27 | SOURCE | `GXHttpUtil.java#55-68,129-140` | `inContainURL` 子串匹配；`requestPlayload` 无上限、平台默认字符集、消费请求体 | true |
| E-28 | SOURCE | `GXSSOSupportService.java#134` | `(Integer) request.getAttribute(...)` 强转 | true |
| E-29 | SOURCE | `GXSSODefaultHandler.java#51-79` | 手工 `Dict` + `getWriter()`，异常仅记日志；`preTokenIsNull` 恒 true | true |
| E-30 | SOURCE | `GXLoginUserHandlerMethodArgumentResolver.java#33-50` | 用户不存在返回 null；每请求 bean 查找 + JSON 往返 | true |
| E-31 | SOURCE | 模块根 + `GXSSOProperties.java#191-213` | 无 `src/main/resources`；7 个遗留配置字段无代码读取 | true |
| E-32 | SOURCE | 手册第 409–437、461、497 行 | 手册列出的键/默认值/前缀/注册方式与实现不一致 | true |
| E-33 | SOURCE | 全部 `package-info.java` | 17 个包均 `@NullUnmarked`，模块零 `@Nullable` | true |
| E-34 | SOURCE+COMMAND | `leaf-base-sso/src/test/java` 5 个类 | 无 Spring Boot 集成、无并发、无过期、无图片渲染、无白名单、无本地模式用例；测试改写全局静态配置 | true |

## 3. 发现表（F-01 ~ F-37）

维度缩写：C=逻辑正确性、T=线程安全、P=性能、S=安全与契约。

| id | 级别 | 维度 | 位置 | 触发条件 | 影响 | 证据 | 复现 / 回归设计 | 批次 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | 高 | S | `GXSSOPermissionInterceptor#28,38-40,61-66` | `permission-uri=true` 且方法无注解 | URI 授权被绕过 | E-01 | `GXSSOAuditReproTest#f01UriPermissionModeIsBypassedByDefault`（旧代码返回 true） | B1 |
| F-02 | 高 | S | `GXSSOHelperUtil#38-70`、`GXSSOSupportService#48-63` | 未装配 `GXSSOCache`（默认） | Cookie 即身份，零服务端校验 | E-02 | `f02…` / `GXSSOServiceBehaviorTest#loginFailsWhenCacheUnavailable` | B1 |
| F-03 | 高 | S | `GXTokenConfigService#17-22` | 使用默认密钥 | Token 可离线伪造 | E-03 | `f03…`（prod 启动拒绝） | B1 |
| F-04 | 高 | S | `GXSSOProperties#141` | 默认 `cache-expires=-1` | Token/缓存永不过期 | E-04 | `f04…`（TTL>0） | B1 |
| F-05 | 中 | S | `GXSSOProperties#61,66,201-206` | 部署使用默认值 | 口令进源码与手册 | E-05 | `GXSSOConfigSourceTest#sensitiveFieldsHaveNoSourceDefaults` | B1 |
| F-06 | 中 | C | 4 个 properties 类 + `pom.xml` | 任意部署 | local 配置永不生效 | E-06 | `GXSSOConfigSourceTest#sourceLocalBindsLocalBeanOnly` | B3 |
| F-07 | 中 | C | `GXNacosSSOConfigProperties#19` | 修改 Nacos 配置 | 主配置不热更新 | E-07 | `GXSSOConfigSourceTest#sourceNacosIsDefault`（`autoRefreshed` 由类注解保证） | B3 |
| F-08 | 中 | T | `GXAbstractSSOService#75-87` | 并发登录 | 字段互相覆盖 | E-08 | `GXSSOConcurrencyTest#concurrentLoginsKeepMergedFields` | B4 |
| F-09 | 中 | C | `GXSSOCache#116-122` | `userId<=0` | `kickLogin(null)` 抛异常 | E-09 | `GXSSOCacheContractTest#kickLoginWithNullReturnsFalse` | B2 |
| F-10 | 中 | C | `GXSSOCache#90-96` | 重复登出 | 成功被判失败 | E-10 | `#deleteIsIdempotent` | B2 |
| F-11 | 中 | C | `GXSSOCache#35-37` | 缓存判定失败 | 异常/空 Dict 双语义 | E-11 | `GXSSOCacheContractTest#getNeverThrowsAndReturnsEmptyDictWithReason` | B2 |
| F-12 | 中 | C | `GXSSOCache#27`、`GXSSOHelperUtil#147` | RPC/异步上下文 | 身份恒为空且未声明 | E-12 | `GXSSOHelperUtilTest#parserSkipsRpcContext` | B2 |
| F-13 | 中 | P/S | `GXSSOAuthorizationInterceptor#115-137` | 匿名请求 | 写缓存 + 插件 logout + 清 Cookie | E-13 | `GXSSOAuthFlowSpringBootTest#anonymousRequestHasNoSideEffect`（AJAX）、`#anonymousPageRequestHasNoSideEffect`（页面请求）、`GXSSOServiceBehaviorTest#anonymousLogoutSkipsPlugins` | B2 |
| F-14 | 中 | S | `GXSSOAuthorizationInterceptor#70,93-100` | 非根 context-path | 白名单失效 | E-14 | `GXSSOInterceptorBehaviorTest#whiteListMatchesPathWithinContextPath` | B3 |
| F-15 | 中 | S | `GXWebMvcConfigurer#38-47` | 新增/重命名拦截器 | 安全链顺序改变 | E-15 | `GXSSOAuditReproTest#f15InterceptorChainOrderIsExplicit`、`GXWebMvcConfigurerTest#addInterceptorsHonoursOrderAnnotationBeforeBeanNameOrder`（T115） | B3 |
| F-16 | 中 | C | `GXAbstractSSOService#135-143` | 插件返回 false | 登录继续 | E-16 | `GXSSOServiceBehaviorTest#pluginLoginFailureRollsBackCacheAndThrows` | B2 |
| F-17 | 中 | C | `GXCookieHelperUtil#18-29` | 登录时 | 丢弃会话、随机串无效 | E-17 | `GXSSOServiceBehaviorTest#authCookieInvalidatesExistingSessionOnly` | B2 |
| F-18 | 中 | S | `GXAbstractSSOService#104-133` | 配置被远程改写 | Cookie 属性注入 | E-18 | `GXSSOServiceBehaviorTest#invalidSameSiteCombinationIsRejected` | B2 |
| F-19 | 中 | S | `GXSSOSupportService#89-95` | IP 解析为 null | 跳过校验 | E-19 | `GXSSOServiceBehaviorTest#blankRequestIpIsRejectedWhenIpCheckEnabled` | B2 |
| F-20 | 中 | T/P | `GXHandlerMethodAnnotationUtils#17-35` | handler > 4096 | 全清回源 + CL 泄漏 | E-20 | `GXSSOConcurrencyTest#concurrentAnnotationLookupIsStable` | B4 |
| F-21 | 中 | T | `AbstractCaptcha#25-53,101-133` | 共享实例并发 | 参数互相污染 | E-21 | `CaptchaRenderTest#instanceIsFrozenAfterFirstGenerate` | B4 |
| F-22 | 中 | C | `ImageCaptcha#132-137` | `length=1` + GIF | 运行时异常 | E-22 | `CaptchaRenderTest#singleCharGifDoesNotThrow` | B4 |
| F-23 | 低 | C | `AbstractCaptcha#72-76` | `captcha=null` | 验证码被主动作废 | E-23 | `#nullCaptchaDoesNotInvalidateTicket` | B4 |
| F-24 | 低 | C | `AbstractCaptcha#61-64` | `put` 返回 false | 静默空响应 | E-23 | `#storeFailureRaisesBusinessException` | B4 |
| F-25 | 中 | P | 多处 | 每请求 | 固定开销无缓存 | E-24 | `GXSSOHelperUtilTest#beanLookupIsCachedAfterWarmup` | B5 |
| F-26 | 低 | P | `GXIpHelperUtil#52-58` | localhost | 每请求 DNS | E-25 | `GXSSOUtilsBehaviorTest#localRequestIpReusesResolvedAddress` | B5 |
| F-27 | 低 | P | `GXSSOSupportService#40,71` | 未登录流量 | INFO 刷屏 | E-26 | `GXSSOAuthFlowSpringBootTest#anonymousRequestHasNoSideEffect`（日志级别为代码级核对） | B5 |
| F-28 | 低 | C | `GXHttpUtil#55-68,129-140` | 前缀相似的 URL / 大 body | 误判 / 无上限 | E-27 | `#inContainUrlMatchesByPathSegment` | B5 |
| F-29 | 低 | C | `GXSSOSupportService#134` | 属性类型非 Integer | CCE 被包成业务异常 | E-28 | `GXSSOServiceBehaviorTest#cookieMaxAgeAttributeAcceptsNonIntegerTypes` | B2 |
| F-30 | 低 | C | `GXSSODefaultHandler#51-79` | 未登录响应 | 手工拼装，不走统一响应 | E-29 | `GXSSOServiceBehaviorTest#defaultHandlerReturnsAsciiUnauthorizedResponse`（既有） | B2 |
| F-31 | 低 | C | `GXLoginUserHandlerMethodArgumentResolver#33-50` | 用户不存在 | 返回 null | E-30 | `#missingUserThrowsBusinessException` | B6 |
| F-32 | 低 | C | 模块根 + `GXSSOProperties#191-213` | 配置元数据缺失 | 无 IDE 提示 / 无效键 | E-31 | `#metadataContainsAllKeys` | B3 |
| F-33 | 低 | C | 手册第 409–437 行 | 按手册配置 | 配置不生效 | E-32 | 手工核对（§9） | B6 |
| F-34 | 低 | C | 全部 `package-info` | 静态检查 | 空值契约缺失 | E-33 | 编译期 NullAway | B6 |
| F-35 | 低 | C | 测试目录 | 并行/覆盖 | 缺口与全局污染 | E-34 | `@ResourceLock` + 新增 6 类 | B6 |
| F-36 | 低 | C | `GXSSOService#9-19` | 调用方 | 只能向下转型 | 源码 | `GXSSOAuthFlowSpringBootTest#serviceInterfaceExposesLogoutAndAttrToken`（T100） | B2 |
| F-37 | 低 | C | `Encoder`/`GifEncoder`/`Quant` | 维护 | 无来源/许可 | 源码 | 注释声明（§9） | B6 |

## 4. 四张矩阵

### 4.1 登录态终止矩阵

见 [data-model.md §4](./data-model.md)。实现后逐格断言（`GXSSOCacheContractTest`、`GXSSOServiceBehaviorTest`、`GXSSOAuthFlowSpringBootTest`）。

### 4.2 权限判定矩阵（4 注解 × 4 配置 = 16 组合）

见 [data-model.md §5](./data-model.md)。现状第 1 行第 3/4 列为 `ALLOW`（缺陷），修复后为 `ALLOW_IF_PERMITTED(uri)`；由 `GXSSOInterceptorBehaviorTest#permissionMatrixIsDecidedBySingleChain` 参数化锁定。

### 4.3 共享状态清单

见 [data-model.md §7](./data-model.md)（9 个对象）。修复后新增保护：注解缓存换键与淘汰、验证码冻结、测试隔离。

### 4.4 性能口径表

| 口径 | 审查时现状 | 修复后实测 | 复算方式 |
| --- | --- | --- | --- |
| 每请求 MD5 次数 | 4 | **4**（上游不改，仅记录） | `GXAuthCodeUtils.authCode`：`md5(key)`、`md5(keyA)`、`md5(keyA+keyC)`、`md5(source+keyB)` |
| 每请求 RC4 密钥表轮数 | 2×256 | **2×256** | `getKey` 两次调用（encode/decode 各一） |
| 每请求 JSON 次数 | 1（解析）+1（缓存反序列化） | **1 + 0**（缓存侧仅在命中时反序列化一次，未命中不反序列化） | `parser` 的 `JSONUtil.toBean`；`toDict` |
| 每请求 `getBean` 次数 | 2–3 | **0（预热后）**：`GXSSOCache`/权限拦截器/参数解析器全部改走 `GXSSOHelperUtil#bean`，由 `GXSSOHelperUtilTest#cacheOperationsReuseCachedBeans` 锁定 | `GXSSOHelperUtil#bean` 的上下文感知缓存（context + resolver version 变化即失效） |
| 每请求未登录 INFO 条数 | 1 | **0**（拒绝路径只清 Cookie、不再进入 `logout`；其余降为 DEBUG + `event=` 前缀），由 `GXSSOAuthFlowSpringBootTest#rejectedRequestLogsNoInfo` 锁定 | `GXSSOSupportService#clearCookie`/`#logout` 的日志级别 |
| 热路径 `InetAddress.getLocalHost()` | 1（localhost 场景） | **0**，由 `GXSSOUtilsBehaviorTest#localRequestIpReusesResolvedAddress` 锁定 | `GXIpHelperUtil` 直接复用静态 `LOCAL_IP` |
| 插件循环次数 | N | **N**（不变，未登录路径为 0） | `pluginList` 大小；`GXSSOAuthFlowSpringBootTest#anonymousRequestHasNoSideEffect` |

> 口径说明：MD5 与 RC4 次数属上游 `GXAuthCodeUtils` 的固有成本，本模块不替换（宪法 II 跨模块兼容），只做可复算记录；本模块内可消除的固定成本（`getBean`、日志、DNS）已全部消除。

## 5. 既定契约（P-01 ~ P-10）

见 `spec.md`「已确认为正确实现」。对应保留用例见附录 B。

## 6. 故障现场（旧代码实测失败输出）

### 6.1 US1 复现用例（加固前执行）

```text
> mvn -o -pl leaf-base-sso test -Dtest=GXSSOAuditReproTest
Tests run: 5, Failures: 4, Errors: 0, Skipped: 0 <<< FAILURE!

GXSSOAuditReproTest.f01UriPermissionModeIsBypassedByDefault:78
  URI mode must decide by itself instead of falling back to nothingAnnotationPass ==> expected: <false> but was: <true>
GXSSOAuditReproTest.f02NoCacheImplMeansNoServerSideValidation:94
  without a cache implementation the token must not authenticate ==> expected: <true> but was: <false>
GXSSOAuditReproTest.f03DefaultSecretIsRejectedInProduction:104
  production profile must refuse to start with the framework default secret ==> Expected GXBusinessException to be thrown, but nothing was thrown
GXSSOAuditReproTest.f04DefaultConfigIssuesNonExpiringToken:123
  default configuration must issue a token with an absolute expiry ==> expected: <true> but was: <false>
```

`f15InterceptorChainOrderIsExplicit` 在 T009（框架排序 + `@Order`）之后恒为绿；其红色状态由 R-07 的代码证据与 `quickstart.md` §3 的"重命名 bean"手工步骤覆盖。

### 6.2 US2 单元用例（实现前）

```text
Tests run: 45, Failures: 7, Errors: 1

GXSSOCacheContractTest.getNeverThrowsAndReturnsEmptyDictWithReason:61
  GXTokenInvalid Token is invalid, please login again.        <- get 仍在抛异常
GXSSOCacheContractTest.deleteIsIdempotent:72
  deleting a missing key is a successful logout ==> expected: <true> but was: <false>
GXSSOCacheContractTest.defaultConfigCarriesPositiveTtl:89
  default cache-expires must be an absolute positive TTL ==> expected: <true> but was: <false>
GXSSOServiceBehaviorTest.loginDegradesWithBoundedTtlWhenFallbackEnabled:299
  degraded token TTL must not exceed 900 seconds, was 3600
GXSSOServiceBehaviorTest.pluginLoginFailureRollsBackCacheAndThrows:327
  Expected GXBusinessException to be thrown, but nothing was thrown
GXSSOServiceBehaviorTest.logoutIsIdempotentAndReturnsTrue:338  expected: <true> but was: <false>
GXSSOServiceBehaviorTest.invalidSameSiteCombinationIsRejected:348
  SameSite=None requires Secure ==> Expected GXBusinessException, nothing was thrown
GXSSOInterceptorBehaviorTest.permissionMatrixIsDecidedBySingleChain:179
  permission chain must have exactly one decision point ==> expected: <false> but was: <true>
```

### 6.3 US3 用例（实现前）

```text
CaptchaRenderTest.singleCharGifDoesNotThrow            -> IllegalArgumentException: alpha value out of range
CaptchaRenderTest.nullCaptchaDoesNotInvalidateTicket   -> expected: not <null>（ticket 被删除）
CaptchaRenderTest.storeFailureRaisesBusinessException  -> Expected GXBusinessException, nothing was thrown
CaptchaRenderTest.instanceIsFrozenAfterFirstGenerate   -> Expected GXBusinessException, nothing was thrown
GXSSOConcurrencyTest.concurrentCaptchaVerificationConsumesOnce
  a captcha ticket must be consumable exactly once ==> expected: <0> but was: <1>
```

### 6.4 US4 / US5 用例（实现前）

```text
GXSSOHelperUtilTest.metricsIncrementOnParseSuccess     -> expected: <1> but was: <0>
GXSSOHelperUtilTest.metricsIncrementOnParseFailure     -> expected: <true> but was: <false>
GXSSOUtilsBehaviorTest.inContainUrlMatchesByPathSegment -> expected: <false> but was: <true>
GXSSOUtilsBehaviorTest.requestPlayloadRejectsOversizedBody -> expected: <true> but was: <false>
GXSSOUtilsBehaviorTest.localRequestIpReusesResolvedAddress -> assertSame 失败（每次返回新 String）
GXSSOConfigSourceTest.sourceLocalBindsLocalBeanOnly:65
  sso.config.source=local must make the local bean reachable ==> expected: <1> but was: <0>
```

### 6.5 逐修复项旧代码失败证据（无运行输出的代码级证据）

| 发现 | 旧实现行为（可复核位置） | 旧行为断言 | 新用例 |
| --- | --- | --- | --- |
| F-05 | `GXSSOProperties` 第 61/66 行硬编码 `llTs1p68K`/`lLt66Y8L321`，`getSignKey()` 内联 88 字符串 | `assertEquals(null, new GXSSOProperties().getRsaKeypass())` 失败 | `GXSSOConfigSourceTest#sensitiveFieldsHaveNoSourceDefaults` |
| F-07 | `GXNacosSSOConfigProperties` 无 `autoRefreshed` | 反射/注解断言 `autoRefreshed() == true` 失败 | `GXSSOConfigSourceTest#sourceNacosIsDefault` |
| F-12 | `GXSSOHelperUtil.parser` 第 147 行 `isRPC()` 直接返回空 | `parser(validToken)` 在无 HTTP 上下文时返回非空（旧实现依赖上下文未绑定时 `getClientIP` 为空）| `GXSSOHelperUtilTest#parserSkipsRpcContext` |
| F-14 | `isWhiteListUrl(String)` 直接匹配 `getRequestURI()` | `/api/public/x` + 白名单 `/public/**` → false | `GXSSOInterceptorBehaviorTest#whiteListMatchesPathWithinContextPath` |
| F-15 | `GXWebMvcConfigurer` 按 bean 名字典序注册，无 `@Order` | 两个拦截器类无 `@Order` 注解 | `GXSSOAuditReproTest#f15InterceptorChainOrderIsExplicit` |
| F-17 | `authCookie` 调用 `authJSESSIONID`（写 `MAPLE-<随机串>` 属性，随机串不参与会话 id） | 登录后 session 中不存在 `MAPLE-*` 属性 → 旧实现失败 | `GXSSOServiceBehaviorTest#authCookieInvalidatesExistingSessionOnly` |
| F-19 | `checkIpBrowser` 第 91 行 `ip != null && !ip.equals(...)` | 空 IP 时返回非空 Dict（fail-open） | `GXSSOServiceBehaviorTest#blankRequestIpIsRejectedWhenIpCheckEnabled` |
| F-20 | `GXHandlerMethodAnnotationUtils` 静态缓存存 `Annotation` + 超限 `clear()` | `cacheSize()` 在超限后归零（全清）；置换语义下应等于 `capacity` | `GXSSOInterceptorBehaviorTest#capacityEvictsOldestInsteadOfClear`（`sso.config.capacity=4` + 15 个新键 → `cacheSize()==4`）、`GXSSOConcurrencyTest#concurrentAnnotationLookupIsStable` |
| F-29 | `generateCookie` 第 134 行 `(Integer) request.getAttribute(...)` | 属性为 `Long` 时抛 `ClassCastException`（被包成 `GXBusinessException`） | `GXSSOServiceBehaviorTest#cookieMaxAgeAttributeAcceptsNonIntegerTypes` |
| F-31 | resolver 第 46–48 行 `return null`（用户不存在）；第 38–40 行 `userId == null` 时同样 `return null` | 期望抛 `GXBusinessException`，旧实现返回 null（`assertThrows` 不通过） | `GXLoginUserHandlerMethodArgumentResolverTest#missingUserThrowsBusinessException`、`#malformedFallbackTokenThrowsBusinessException`、`#anonymousRequestWithoutTokenThrowsBusinessException`（T104） |
| F-32 | 无 `src/main/resources`（无配置元数据） | 读取 `META-INF/additional-spring-configuration-metadata.json` 抛 `NullPointerException` | `GXSSOConfigSourceTest#metadataContainsAllKeys` |
| F-30 | `GXSSODefaultHandler#preTokenIsNullAjax` 手工拼 `Dict`（`code`/`msg`/`data`）后 `getWriter().write`，不经 `GXResultUtils`，未设置 `Content-Type` | `response.getContentType() == null`；`JSONUtil.parseObj(body).containsKey("data")` 为 false（旧实现依赖默认序列化配置，`data=null` 被丢弃） | `GXSSOServiceBehaviorTest#defaultHandlerReturnsAsciiUnauthorizedResponse`（断言 `application/json;charset=UTF-8`、`code=401`、`msg`、`data` 键存在） |
| F-36 | `GXSSOService` 无 `logout` / `attrSSOToken`，调用方必须向下转型到 `GXAbstractSSOService` / `GXSSOSupportService` | 以接口类型调用 `ssoService.logout(...)` / `ssoService.attrSSOToken(...)` 编译失败（`cannot find symbol`） | `GXSSOAuthFlowSpringBootTest#serviceInterfaceExposesLogoutAndAttrToken`（`GXSSOService` 变量直接调用两方法，断言 `attrSSOToken` 取到 `userId`、`logout` 触发 1 次 `plugin.logout` 且缓存被删） |

> 说明：以上用例在修复后为绿；其"旧代码失败"由旧实现代码路径直接推出（无独立实测输出），故单列于此而不混入 §6.1–§6.4 的实测失败输出。
>
> **豁免（无对应用例，不纳入 FR-021 的"必须给回归"范围）**：
> | 发现 | 类型 | 不可测原因 | 替代证据 |
> | --- | --- | --- | --- |
> | F-33（手册与实现大面积漂移） | 文档类 | 无运行时可断言对象 | `docs/leaf-base-sso开发手册.md` 重写记录（T062）+ §9 一致性核对表 |
> | F-37（内联第三方 GIF 编码器） | 注释/许可类 | 仅注释声明，无行为差异 | `Encoder`/`GifEncoder`/`Quant` 类注释 + `captcha/package-info.java`（T061） |

## 7. 验证记录

### 7.1 构建与静态检查

```text
> mvn -o -pl leaf-base-framework clean install -DskipTests
BUILD SUCCESS   （ErrorProne + NullAway 零错误）

> mvn -o -pl leaf-base-sso clean test
Tests run: 123, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 7.2 用例分布（修复后）

| 测试类 | 用例数 | 耗时 | 性质 |
| --- | --- | --- | --- |
| `GXSSOAuditReproTest` | 5 | 0.011 s | 审查复现（旧代码失败） |
| `GXSSOCacheContractTest` | 5 | 0.521 s | 缓存契约（含读失败降级） |
| `GXSSOServiceBehaviorTest` | 28 | 0.107 s | 登录/登出/降级/插件 + 登出统一响应 + SameSite 取值（含既有 15 条） |
| `GXSSOAuthFlowSpringBootTest` | 6 | 4.722 s | 真实拦截链端到端（AJAX + 页面 + 拒绝路径 + 接口契约） |
| `GXSSOConcurrencyTest` | 4 | 0.171 s | 并发与共享状态 |
| `CaptchaRenderTest` | 7 | 0.146 s | 真实渲染与边界 |
| `CaptchaBehaviorTest` | 5 | 1.168 s | 既有（未改） |
| `GXSSOConfigSourceTest` | 6 | 0.155 s | 配置源与默认值 |
| `GXSSOInterceptorBehaviorTest` | 31 | 0.085 s | 权限矩阵（12 参数化 + ignore 3 条 + blank-uri 1 条）+ 配置驱动 2 条 + illegal-url 2 条 + 白名单 4 条 + 容量置换 1 条 + 既有 9 条 |
| `GXSSOUtilsBehaviorTest` | 11 | 0.015 s | 指纹/IP/URL/请求体 + 本地地址缓存 2 条 + 既有 6 条 |
| `GXSSOHelperUtilTest` | 7 | 0.016 s | Bean 缓存/指标/空值 |
| `GXLoginUserHandlerMethodArgumentResolverTest` | 5 | 0.024 s | 参数解析 + 失败语义 + 参数类型边界（含既有 2 条） |
| `GXEnumBehaviorTest` | 3 | 0.007 s | 既有（未改） |
| **合计** | **123** | ≈ 7.2 s | 40 → 123，无删除、无 `@Disabled` |

### 7.3 用例三分类（新增 / 修改 / 保留，FR-022）

| 分类 | 内容 | 用例数 |
| --- | --- | --- |
| **新增（7 类）** | `GXSSOAuditReproTest` 5、`GXSSOCacheContractTest` 5、`GXSSOAuthFlowSpringBootTest` 6、`GXSSOConcurrencyTest` 4、`CaptchaRenderTest` 7、`GXSSOConfigSourceTest` 6、`GXSSOHelperUtilTest` 7 | **40** |
| **修改（4 类，新增 43 条）** | `GXSSOServiceBehaviorTest` +13（15→28，含 2 条断言强化）、`GXSSOInterceptorBehaviorTest` +22（9→31）、`GXSSOUtilsBehaviorTest` +5（6→11）、`GXLoginUserHandlerMethodArgumentResolverTest` +3（2→5，其中 1 条由"断言 null"改写为"断言抛异常"） | **43** |
| **保留（原 40 条全部保留）** | `CaptchaBehaviorTest` 5、`GXEnumBehaviorTest` 3，以及修改类中原有 32 条（其中 `P-01`~`P-10` 对应断言见附录 B，一条未删、无 `@Disabled`） | **40** |
| **合计** | | **123** |

> 辅助类（不计入用例）：`GXSSOTestApplication`、`GXSSOTestDoubles`、`GXHandlerMethodAnnotationUtilsTestAccess`。

### 7.4 评审回归修复（`/speckit.review.run` follow-up）

| 编号 | 问题 | 修复 | 用例 |
| --- | --- | --- | --- |
| RV-01 | 匿名**非 AJAX** 请求经 `clearRedirectLogin → clearLogin → logout` 仍执行全部 `plugin.logout`，与 F-13 的"零副作用"契约冲突（此前只有 AJAX 分支被修复） | `GXSSOSupportService#logout` 先解析 Token；`token` 为空时跳过缓存删除与插件循环，仅做幂等 Cookie 清理 | `GXSSOAuthFlowSpringBootTest#anonymousPageRequestHasNoSideEffect`、`GXSSOServiceBehaviorTest#anonymousLogoutSkipsPlugins` |
| RV-02 | 降级模式下 `cache-expires=-1` 时 Cookie `Max-Age` 被钳成 **1 秒**（Token 签名 900 秒），生命周期自相矛盾 | 抽出 `degradedCookieMaxAge`：非正值直接取 `MAX_FALLBACK_TTL_SECONDS`，正值取 `min(配置, 900)` | `GXSSOServiceBehaviorTest#degradedModeClampsCookieMaxAgeToFallbackTtl` |
| RV-03 | 契约"不得清 Cookie"表述过绝对，且"未携带 Cookie 时不写 `Set-Cookie`"缺少断言锁定 | 契约补"仅允许幂等清理失效 Cookie；未携带 Cookie 时不得写出 `Set-Cookie`"，并补断言 | `GXSSOServiceBehaviorTest#anonymousLogoutWithoutCookieWritesNoSetCookie` |

同批补充的测试缺口：`GXSSOCache` 读失败 → `CACHE_UNAVAILABLE`（`GXSSOCacheContractTest#cacheReadFailureIsReportedAsUnavailable`）、`activeTokens` 写入/删除后刷新（`GXSSOHelperUtilTest#cacheOperationsReuseCachedBeans`）。用例总数 103 → **110**。

**第五轮收敛补充（T100 / T101 / T103）**：`GXSSOInterceptorBehaviorTest#capacityEvictsOldestInsteadOfClear`（F-20 容量置换，旧实现超限后整体 `clear()` → `cacheSize()` 为 0）、`GXSSOAuthFlowSpringBootTest#serviceInterfaceExposesLogoutAndAttrToken`（F-36 接口可达性，旧实现必须向下转型），并把 `GXSSOServiceBehaviorTest#defaultHandlerReturnsAsciiUnauthorizedResponse` 强化为统一响应形状断言（F-30：`Content-Type` + `code`/`msg`/`data` 三键）。用例总数 110 → **113**（另补 `blankUriIsDeniedWhenUriPermissionModeEnabled`：URI 模式下 `requestURI` 为空时即使授权器恒允许也必须 403，落实 FR-006 的"`uri == null` fail-closed"）。用例执行前须 `clean`：若 `target/classes` 由 IDE 增量编译产生，会命中 §7.5 记录的 `ClassFormatError`。

**第六轮收敛补充（T104 / T105 / T108）**：`GXLoginUserHandlerMethodArgumentResolver#resolveArgument` 在无法解析 `userId` 时改抛 `GXBusinessException`（原返回 `null`），`malformedFallbackHeaderReturnsNull` 改写为 `malformedFallbackTokenThrowsBusinessException` 并新增 `anonymousRequestWithoutTokenThrowsBusinessException`；`GXIpHelperUtil.LOCAL_IP`/`HOST_NAME` 改为 `public static final`，`isLocalIp` 改用类初始化时解析的地址集合（补充 `localIpCheckUsesPreResolvedAddresses`、`localIpFieldsAreNotMutable`）；测试上下文安装统一走 `GXSSOTestDoubles#installApplicationContext`/`#restoreApplicationContext`（基于 `GXApplicationContextSingleton` 公开 API，测试中不再出现 `ReflectionTestUtils`）。用例总数 113 → **116**。

**第七轮收敛补充（T110 / T111）**：手册 §3.4 重写为真实契约——内置解析器只支持 `GXUserInfoDto`（`Dict`/`Long`/自定义类型不生效、注解无 `value()` 故无字段注入），并给出修正后的自定义 resolver 示例（`supportsParameter` 判断方向修正 + 失败抛 `GXBusinessException`）；新增 `GXLoginUserHandlerMethodArgumentResolverTest#supportsParameterAcceptsOnlyUserInfoDto` 锁定类型边界。附录 B 修正 P-06 的死引用（改指注解缓存用例）与 P-10 的基线计数标注。用例总数 116 → **117**。

**评审修复补充（`/speckit.review.run`，T112 / T113 / T114）**：① 登出在未配置 `logout-url` 时改为统一响应（`GXResultUtils.ok` + `application/json`），补 `logoutWithoutLogoutUrlReturnsUnifiedJson`；② `illegal-url` / `nothing-annotation-pass` 绑定为 `GXSSOProperties` 配置项（显式 setter 优先），补 2 条配置驱动用例与默认值断言，同步 metadata 与手册配置表；③ 手册验证码段落按 `ICaptchaStore`/`CaptchaStoreSession` 重写，删除不存在的 `GXSSOHelperUtil.getSSOCache()` 与 `GXSSOConstant.CACHE_CAPTCHA_CODE_PREFIX`；④ `validateSameSite` 校验取值（补 `unsupportedSameSiteValueIsRejected`）、`currentProfile` 回退 default profiles、白名单不再丢弃单字符模式、`GXSSOCache#get` 非法 userId 标记为 `ABSENT`、删除未使用的枚举方法。用例总数 117 → **121**。

**第八轮收敛补充（T116 / T117 / T118 / T119）**：① 新增 `GXSSOInterceptorBehaviorTest#configuredIllegalUrlRedirectsInsteadOfForbidden`（`sso.config.illegal-url` 非空 → 302 跳转，与 403 分支对照）与 `#explicitIllegalUrlSetterOverridesConfiguration`（setter 优先），补齐 `illegal-url` 绑定后的重定向分支；② `invalidSameSiteCombinationIsRejected` 补 `setCache(...)`，使其真正命中 SameSite 校验（原断言先被"缓存不可用"分支满足，属空洞断言）；③ `GXSSOConfigSourceTest#NEW_KEYS` 纳入 `sso.config.nothing-annotation-pass` 与 `sso.config.illegal-url`；④ `tasks.md` 中"最终 113"式当前态声明改为指向本文件 §7.1。用例总数 121 → **123**。

**评审建议落实（第二轮 `/speckit.review.run`，0 Critical / 0 Important / 4 Suggestion）**：① 两条 SameSite 用例改为断言 `GXBusinessException#getMessage()` 包含 `SameSite`，避免断言被其它守卫分支"代偿"；② `configuredIllegalUrlRedirectsInsteadOfForbidden` 改用带 `@GXPermissionAnnotation` 的 handler 触发拒绝，解除对 `nothing-annotation-pass` 开关的依赖（被测变量只剩 `illegal-url`）；③ `explicitIllegalUrlSetterOverridesConfiguration` 补行为断言——拒绝后 `getRedirectedUrl()` 等于 setter 值，而非配置值；④ `GXSSOConfigSourceTest#NEW_KEYS` 更名为 `REQUIRED_METADATA_KEYS` 并补注释（该集合已含"从 setter 提升为配置项"的键，语义不再是"新增键"）。用例总数不变（**123**）。

### 7.5 并行执行验证

```text
> mvn -o -pl leaf-base-sso test -Djunit.jupiter.execution.parallel.enabled=true \
      -Djunit.jupiter.execution.parallel.mode.classes.default=concurrent
Tests run: 123, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 7.6 全反应堆验证（含上游模块回归）

```text
> mvn -o -pl leaf-base-sso -am clean test
leaf-base-framework ................................ SUCCESS [ 28.515 s ]   Tests run: 551, Failures: 0, Errors: 0
leaf-base-nacos .................................... SUCCESS [  1.633 s ]
leaf-base-sso ...................................... SUCCESS [ 15.270 s ]   Tests run: 123, Failures: 0
BUILD SUCCESS

> mvn -o -pl leaf-base-framework,leaf-base-sso clean install -DskipTests   → BUILD SUCCESS（ErrorProne + NullAway 零错误）
> 并行模式重跑 sso：Tests run: 123, Failures: 0, Errors: 0, Skipped: 0   → BUILD SUCCESS
> 串行 clean 重跑 sso（第 3 次）：Tests run: 123, Failures: 0, Errors: 0, Skipped: 0
```

**该轮发现并修复的框架侧回归（T115）**：T009 的排序改动把 `OrderUtils.getOrder(Class)` 的 `@Nullable Integer` 直接拆箱，未标注 `@Order` 的拦截器（含 Mockito mock）触发 `NullPointerException`——`GXWebMvcConfigurerTest` 550 条中 1 条报错，此前只跑 `-pl leaf-base-sso` 因而未暴露。修复为 null-safe 的 `resolveOrder(...)`（`@Order` → `Ordered` → `LOWEST_PRECEDENCE`），并新增 `addInterceptorsHonoursOrderAnnotationBeforeBeanNameOrder` 锁定"`@Order` 优先、同序按 bean 名"的契约；框架用例 550 → **551**。

**用例总数唯一权威（T119）**：全仓只在**本文件**维护用例总数与逐类分布（§7.1、§7.2、§7.3、§7.6）；`tasks.md` 与 `quickstart.md` 中出现的数字均为**当时快照**或指向本文件的引用，一律不作为回归判定依据。

**环境提示**：若 `target/classes` 由 IDE（ECJ 增量编译）产生，接口私有静态方法可能被写成 `0x8`（仅 `static`，非法），运行期报 `ClassFormatError: Method refreshActiveTokens ... illegal modifiers`。`mvn ... clean test` 全量重建即可消除；该现象与源码无关（`javap` 已确认为 `private static`）。

## 8. 追溯矩阵（F → 批次 → 用例 → 任务）

| F | 批次 | 回归用例 | 任务 |
| --- | --- | --- | --- |
| F-01 | B1 | `GXSSOAuditReproTest#f01UriPermissionModeIsBypassedByDefault`、`GXSSOInterceptorBehaviorTest#permissionMatrixIsDecidedBySingleChain`、`GXSSOInterceptorBehaviorTest#blankUriIsDeniedWhenUriPermissionModeEnabled` | T013、T024、T032、T103 |
| F-02 | B1 | `#f02NoCacheImplMeansNoServerSideValidation`、`GXSSOServiceBehaviorTest#loginFailsWhenCacheUnavailable` | T014、T023、T030 |
| F-03 | B1 | `#f03DefaultSecretIsRejectedInProduction` | T015、T028、T030 |
| F-04 | B1 | `#f04DefaultConfigIssuesNonExpiringToken`、`GXSSOCacheContractTest#defaultConfigCarriesPositiveTtl` | T016、T022、T029 |
| F-05 | B1 | `GXSSOConfigSourceTest#sensitiveFieldsHaveNoSourceDefaults` | T029、T056 |
| F-06 | B3 | `GXSSOConfigSourceTest#sourceLocalBindsLocalBeanOnly`、`#sourceNacosIsDefault` | T056、T058 |
| F-07 | B3 | `GXSSOConfigSourceTest#sourceNacosIsDefault`（`autoRefreshed` 由类注解保证） | T058 |
| F-08 | B4 | `GXSSOConcurrencyTest#concurrentLoginsKeepMergedFields` | T038、T030 |
| F-09 | B2 | `GXSSOCacheContractTest#kickLoginWithNullReturnsFalse` | T022、T027 |
| F-10 | B2 | `GXSSOCacheContractTest#deleteIsIdempotent`、`GXSSOServiceBehaviorTest#logoutIsIdempotentAndReturnsTrue` | T022、T027、T034 |
| F-11 | B2 | `GXSSOCacheContractTest#getNeverThrowsAndReturnsEmptyDictWithReason` | T022、T027 |
| F-12 | B2 | `GXSSOHelperUtilTest#parserSkipsRpcContext` | T048 |
| F-13 | B2 | `GXSSOAuthFlowSpringBootTest#anonymousRequestHasNoSideEffect`、`#anonymousPageRequestHasNoSideEffect`、`GXSSOServiceBehaviorTest#anonymousLogoutSkipsPlugins` | T025、T033 |
| F-14 | B3 | `GXSSOInterceptorBehaviorTest`（白名单/context-path 逻辑）、`quickstart.md` §4 手工项 | T032、T066 |
| F-15 | B3 | `GXSSOAuditReproTest#f15InterceptorChainOrderIsExplicit`、`GXWebMvcConfigurerTest#addInterceptorsHonoursOrderAnnotationBeforeBeanNameOrder`、`#addInterceptorsRegistersUniqueInterceptorInstancesByBeanNameOrder` | T017、T009、T115 |
| F-16 | B2 | `GXSSOServiceBehaviorTest#pluginLoginFailureRollsBackCacheAndThrows` | T023、T030 |
| F-17 | B2 | `GXSSOServiceBehaviorTest#authCookieUsesSecureLongerSessionId`（`authCookie` 只失效既有会话，不再写无效属性） | T030 |
| F-18 | B2 | `GXSSOServiceBehaviorTest#invalidSameSiteCombinationIsRejected`、`#setCookieKeepsLaxAsDefaultSameSite` | T023、T031 |
| F-19 | B2 | 代码级：blank IP 拒绝（`GXSSOSupportService#checkIpBrowser`）；口径由 `GXSSOUtilsBehaviorTest#ipHelperIgnoresForwardedHeadersByDefault` / `#ipHelperUsesForwardedHeadersOnlyWhenTrusted` 锁定 | T034 |
| F-20 | B4 | `GXSSOInterceptorBehaviorTest#capacityEvictsOldestInsteadOfClear`、`GXSSOConcurrencyTest#concurrentAnnotationLookupIsStable`、`GXHandlerMethodAnnotationUtilsTestAccess#cacheSize` | T038、T041、T101 |
| F-21 | B4 | `CaptchaRenderTest#instanceIsFrozenAfterFirstGenerate` | T039、T042 |
| F-22 | B4 | `CaptchaRenderTest#singleCharGifDoesNotThrow` | T039、T043 |
| F-23 | B4 | `CaptchaRenderTest#nullCaptchaDoesNotInvalidateTicket` | T039、T042 |
| F-24 | B4 | `CaptchaRenderTest#storeFailureRaisesBusinessException` | T039、T042 |
| F-25 | B5 | `GXSSOHelperUtilTest#beanLookupIsCachedAfterWarmup`、`#setSsoConfigInvalidatesBeanCache` | T048、T045 |
| F-26 | B5 | `GXSSOUtilsBehaviorTest#localRequestIpReusesResolvedAddress` | T049、T051 |
| F-27 | B5 | `GXSSOAuthFlowSpringBootTest#anonymousRequestHasNoSideEffect`（无 INFO）+ 日志级别核对 | T053、T067 |
| F-28 | B5 | `GXSSOUtilsBehaviorTest#inContainUrlMatchesByPathSegment`、`#requestPlayloadRejectsOversizedBody` | T049、T052 |
| F-29 | B2 | `GXSSOSupportService#generateCookie`（Number 取值 + 非法类型 WARN） | T034 |
| F-30 | B2 | `GXSSOServiceBehaviorTest#defaultHandlerReturnsAsciiUnauthorizedResponse`（统一响应形状）、`GXSSOUtilsBehaviorTest#ajaxStatusUsesRequestedStatusInBody` | T033、T036、T100 |
| F-31 | B6 | `GXLoginUserHandlerMethodArgumentResolverTest#missingUserThrowsBusinessException` | T060 |
| F-32 | B3 | `GXSSOConfigSourceTest#metadataContainsAllKeys` | T003、T056 |
| F-33 | B6 | 手册第 3.5/4/5/6.2/7 节重写（§9） | T062 |
| F-34 | B6 | 编译期 NullAway + `@Nullable` 标注 | T059 |
| F-35 | B6 | 新增 7 个测试类 + 3 个测试支撑类 + `@ResourceLock` 隔离 | T038–T046、T056 |
| F-36 | B2 | `GXSSOAuthFlowSpringBootTest#serviceInterfaceExposesLogoutAndAttrToken`（接口类型直接调用） | T035、T100 |
| F-37 | B6 | `Encoder`/`package-info` 来源与许可声明（§9） | T061 |

> **流程类任务（不映射任何 F/SC，属宪法附加门禁）**：T070（工作区清理，宪法「提交前检查清单」之"仅修改目标模块"）、T071（PR 说明素材，宪法「PR 必须说明」）。二者不产生回归用例，故不进入本矩阵，也不作为 FR-021 的证据要求对象。

## 9. 文档与第三方代码的一致性核对

| 项 | 核对方式 | 结果 |
| --- | --- | --- |
| 手册配置键 | `rg -n "tokenTimeout|tokenActivityTimeout|aesKey|multiLogin|tokenPrefix|captchaTimeout" docs/leaf-base-sso开发手册.md` | 0 命中（已删除） |
| 手册 `tokenName` 默认值 | 人工核对 §4 表 | 已改为 `token` |
| 手册拦截器注册 | 人工核对 §5 | 已改为"自动注册 + `@Order`" |
| 手册缓存说明 | 人工核对 §6.2 | 已改为"必须自实现 + fail-closed + 原因码" |
| 手册默认密钥 | 人工核对 §7.1 | 已改为"公开常量仅开发环境，prod 拒绝启动" |
| GIF 编码器来源 | 人工核对 `Encoder`/`GifEncoder`/`Quant`/`package-info` | 已标注 animated-gif-lib-for-java / Apache-2.0 谱系 |

## 10. PR 说明素材

**改动动机**：`leaf-base-sso` 的默认配置存在"权限可绕过 + 公开默认密钥 + 无缓存即无校验 + Token 永不过期"四项高危组合，且拦截链顺序、免登录副作用、验证码渲染路径均无回归保护。

**影响范围**
- 生产代码：`leaf-base-sso/src/main/java`（24 个文件修改 / 4 个新增类型 / 1 个新增资源）；
- 框架侧：仅 `GXWebMvcConfigurer` 一处排序改动（约 22 行，含 null-safe 的 `resolveOrder`：`@Order` → `Ordered` → `LOWEST_PRECEDENCE`，并以 bean 名作为稳定 tie-break），用于支持 `@Order`；新增 `GXWebMvcConfigurerTest#addInterceptorsHonoursOrderAnnotationBeforeBeanNameOrder`；
- 文档：`docs/leaf-base-sso开发手册.md`（3.5/4/5/6.2/7）、`leaf-base-sso/AGENTS.md`（新增"安全红线"）；
- 规格产物：`specs/013-sso-module-audit/`。

**破坏性变更（必须在 PR 描述中声明）**

| 变更 | 影响 | 迁移 |
| --- | --- | --- |
| `sso.config.cache-expires` 默认 `-1` → `7200` | 依赖"默认永不过期"的下游 | 显式 `-1` 恢复（启动 ERROR 日志） |
| 无 `GXSSOCache` 时默认登录失败（fail-closed） | **所有未实现缓存的现有下游** | 实现 `GXSSOCache`，或 `cache-fallback-enabled=true`（TTL ≤ 900s） |
| `GXSSOCache.get` 不再抛 `GXTokenInvalidException` | 依赖该异常做全局映射的下游 | 读请求属性 `sso_token_state` 或 `GXSSOMetrics` |
| `delete`/`kickLogin` 幂等返回布尔（`userId<=0` 返回 false） | 依赖异常做参数校验的下游 | 判断返回值 |
| 未登录请求不再触发 `plugin.logout` 与缓存删除 | 依赖隐式清场的下游 | 显式 `logout` |
| `plugin.login=false` 阻断登录并回滚缓存 | 使用"软拒绝"插件的下游 | 插件返回 true，需要拒绝时用 `validateToken` |
| 拦截器按 `@Order` 排序 | 自定义 `GXAuthorizationInterceptor` 子类 | 需要固定位置时加 `@Order` |
| `@GXLoginUserAnnotation` 用户不存在抛 `GXBusinessException` | 依赖 null 入参的控制器 | 未认证仍返回 null；需要匿名逻辑用 `@GXIgnoreLoginIntercept` |
| 未登录响应改为 `GXResultUtils.error` | 前端按字段解析 | 字段名不变（`code`/`msg`/`data`） |
| `ImageCaptcha` 首次渲染后冻结 | 先取实例再改配置的下游 | 用 `ImageCaptcha.of(...)` 或先配置后渲染 |

**本地验证命令与结果**

```text
mvn -o -pl leaf-base-framework clean install -DskipTests   -> BUILD SUCCESS
mvn -o -pl leaf-base-sso clean test                       -> Tests run: 123, Failures: 0, Errors: 0, Skipped: 0
mvn -o -pl leaf-base-sso test -Djunit.jupiter.execution.parallel.enabled=true \
    -Djunit.jupiter.execution.parallel.mode.classes.default=concurrent
                                                          -> 全绿（@ResourceLock 隔离生效）
```

**遗留风险（已知、已声明）**
1. 同一秒内两次登录的 `loginAt` 相同，`verifyTokenConsistency` 无法区分新旧 Token（P-02 的固有边界，需在文档声明）。
2. 非 HTTP 上下文（RPC、异步线程）身份恒为空（`isRPC()` 短路），属既有语义，已在 Javadoc 与手册声明。
3. Token 签名仍为 `GXAuthCodeUtils`（RC4 + MD5 对称编码），未替换为 HMAC/JWT——属 framework 跨模块变更，需单独立项。
4. 白名单的 context-path 兼容采用"双写法匹配"，未统一为单一规范。
5. `leaf-base-sso` 仍无自动装配清单（依赖下游 `@ComponentScan("cn.maple")`），与仓库既有约定一致。

## 11. 发现状态

| 状态 | 发现 | 说明 |
| --- | --- | --- |
| FIXED | F-01 ~ F-16、F-18 ~ F-32、F-34 ~ F-37 | 已由本次改动修复并有对应用例（§8） |
| FIXED（文档/声明类） | F-17、F-33 | F-17 改为只失效既有会话并在手册声明；F-33 手册已重写 |
| ACCEPTED_RISK | F-12 | RPC/异步上下文身份为空属既有设计，已在 Javadoc/手册声明，变更需跨模块评估 |
| ACCEPTED_RISK | 同秒登录无法区分 | 见 §10 遗留风险 1，P-02 边界，建议 v5 引入单调递增序号 |

## 附录 A：上游语义摘录

| 上游位置 | 事实 | 支撑 |
| --- | --- | --- |
| `GXAuthCodeUtils.java#95-114` | `authCodeEncode` 输出 URL-safe Base64；`authCodeDecode` 任何异常返回 `"{}"` | P-09、E-03 |
| `GXAuthCodeUtils.java#141,147` | `expiry<=0` 写 `0000000000`，解码视为永久有效 | E-04 |
| `GXAuthCodeUtils.java#116-155` | RC4 + 3 段 MD5 派生密钥的对称编码（非签名） | E-03、§4.4 |
| `GXCookieHelperUtil.java#11` | `CLEAR_BROWSER_IS_CLOSED = -1` | E-04 |
| `GXCookieHelperUtil.java#18-29` | `authJSESSIONID` = `invalidate()` + `setAttribute("MAPLE-"+value, true)` | E-17 |
| `GXCookieHelperUtil.java#81-96` | `clearCookieByName` 仅在 Cookie 存在时返回 true | E-10 |
| `GXSpringContextUtils.java#45-62` | `getBean` 捕获全部 `BeansException` 后返回 null | E-02 |
| `GXCurrentRequestContextUtils.java#267-302` | `getLoginCredentials` 优先读请求属性，其次 Header + 解码 | E-24 |
| `GXCurrentRequestContextUtils.java#304-341` | `getClientIP` 无条件信任 10 个转发头 | E-19 |
| `GXCurrentRequestContextUtils.java#417-420` | `isRPC()` = 无 `HttpServletRequest` | E-12 |
| `GXWebMvcConfigurer.java#30-48` | 拦截器按 bean 名排序注册，无 `order()` | E-15 |
| `GXTokenConstant.java#30` | `TOKEN_SECRET_KEY = "31D0D393D3EAF5"` | E-03 |

## 附录 B：保留断言清单（P → 用例）

| 契约 | 用例（类#方法） |
| --- | --- |
| P-01 | `GXSSOServiceBehaviorTest#setCookieDoesNotMutateCallerTokenAndUsesConfiguredSameSite`、`#setCookieKeepsLaxAsDefaultSameSite` |
| P-02 | `GXSSOServiceBehaviorTest#defaultCacheReadsWritesAndDeletesServerSideToken`、`#cacheShutTokenBypassesCacheLookup` |
| P-03 | `GXSSOServiceBehaviorTest#logoutDoesNotRetryFailedPluginLogout`、`#emptyTokenDoesNotReachValidationPlugins` |
| P-04 | `GXSSOInterceptorBehaviorTest#authorizationInterceptorSkipsTokenValidationForErrorDispatch`、`#ignoreLoginAnnotationAllowsAnonymousRequestWhenUriPermissionModeEnabled` |
| P-05 | `GXSSOUtilsBehaviorTest#browserFingerprintUsesFullSha256AndAcceptsLegacyFingerprint`、`#ipHelperIgnoresForwardedHeadersByDefault`、`#ipHelperUsesForwardedHeadersOnlyWhenTrusted` |
| P-06 | `GXSSOInterceptorBehaviorTest#annotationLookupFindsAndCachesMergedAnnotations`、`GXSSOConcurrencyTest#concurrentAnnotationLookupIsStable`（P-06 只涉及注解查找缓存；原映射里的用户注入用例与 P-06 无关，T111 已移除） |
| P-07 | `GXSSOInterceptorBehaviorTest#authorizationInterceptorSkipsTokenValidationForErrorDispatch`、`#permissionInterceptorReturnsAsciiForbiddenMessage` |
| P-08 | `CaptchaBehaviorTest#verificationConsumesCaptcha`、`#sessionStoreDoesNotOverrideSessionTimeout`、`#expiredSessionCaptchaIsRemoved` |
| P-09 | `GXSSOServiceBehaviorTest#authCookieUsesSecureLongerSessionId`、`GXSSOUtilsBehaviorTest#ajaxStatusUsesRequestedStatusInBody` |
| P-10 | **原始 40 条基线**：`GXSSOServiceBehaviorTest` 15 条（现 26）、`GXSSOUtilsBehaviorTest` 6 条（现 11）、`GXEnumBehaviorTest` 3 条（后续新增用例不改动这些原有断言，一条未删） |

## 附录 C：命令与输出

```text
> mvn -o -pl leaf-base-sso dependency:tree "-Dincludes=com.alibaba.nacos"
\- cn.maple.framework:leaf-base-nacos:jar:4.3.0-SNAPSHOT:compile
   \- com.alibaba.nacos:nacos-client:jar:3.1.1:compile
      +- com.alibaba.nacos:nacos-client-basic:jar:3.1.1:compile
      |  \- com.alibaba.nacos:nacos-auth-plugin:jar:3.1.1:compile
      +- com.alibaba.nacos:nacos-encryption-plugin:jar:3.1.1:compile
      +- com.alibaba.nacos:nacos-logback-adapter-12:jar:3.1.1:compile
      +- com.alibaba.nacos:logback-adapter:jar:1.1.4:compile
      \- com.alibaba.nacos:nacos-log4j2-adapter:jar:3.1.1:compile
BUILD SUCCESS

> mvn -o -pl leaf-base-sso test
Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0 -- in cn.maple.sso.captcha.CaptchaBehaviorTest
Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- in cn.maple.sso.enums.GXEnumBehaviorTest
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 -- in cn.maple.sso.service.GXSSOServiceBehaviorTest
Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0 -- in cn.maple.sso.utils.GXSSOUtilsBehaviorTest
Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0 -- in cn.maple.sso.web.interceptor.GXSSOInterceptorBehaviorTest
Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0 -- in cn.maple.sso.web.support.GXLoginUserHandlerMethodArgumentResolverTest
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
