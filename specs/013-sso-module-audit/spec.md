# Feature Specification: leaf-base-sso 正确性、线程安全与性能审查

**Feature Branch**: `013-sso-module-audit`（本次未通过 hook 创建 git 分支；`before_specify` 无 hook 注册）

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "审查 @e:\project\java\maple-leaf-framework\leaf-base-sso 模块现有实现的正确性、线程安全与性能"

## 背景与审查结论摘要

本规格把"`leaf-base-sso` 现有实现是否正确、是否线程安全、是否存在性能结构问题"转化为可验收的交付要求，并把"改动后必须做完备测试"固化为硬门槛。审查对象与证据来源：

1. `leaf-base-sso` 全部 **62 个生产源码文件**（45 个功能类 + 17 个 `package-info`）与 **6 个既有测试类（实测 40 条用例）**，逐文件覆盖，无跳过；
2. 契约来源：`leaf-base-sso/AGENTS.md`、`docs/leaf-base-sso开发手册.md`（第 394–499、722–757 行）、`.specify/memory/constitution.md`（重点为 II 稳定契约、III 统一响应、IV 显式配置与双配置源、V 空值安全与资源释放、VI 测试门槛、VII 命名后缀表）；
3. 上游语义核对：直接读取本仓 `leaf-base-framework` 的 `GXAuthCodeUtils`（RC4 + MD5 自研签名）、`GXCookieHelperUtil`（`authJSESSIONID` / `clearCookieByName`）、`GXCurrentRequestContextUtils`（`isRPC` / `getLoginCredentials` / `getClientIP`）、`GXTokenConstant`（默认密钥常量）、`GXWebMvcConfigurer`（拦截器自动注册与顺序）逐条比对，不凭类名或经验推断；
4. 依赖基线：`leaf-base-sso` 依赖 `leaf-base-framework` + `leaf-base-nacos`（compile 作用域）+ `spring-boot-starter-test`（test）；`leaf-base-nacos` 又传递 `nacos-client`（非 optional），直接决定双配置源的分支收敛结果；
5. 既有测试实测基线：`mvn -o -pl leaf-base-sso test` → **Tests run: 40, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS**（`CaptchaBehaviorTest` 5、`GXEnumBehaviorTest` 3、`GXSSOServiceBehaviorTest` 15、`GXSSOUtilsBehaviorTest` 6、`GXSSOInterceptorBehaviorTest` 9、`GXLoginUserHandlerMethodArgumentResolverTest` 2）；
6. 装配事实核对：模块**无** `src/main/resources`（无 `spring.factories`、无 `additional-spring-configuration-metadata.json`、无默认 `sso.yml`），拦截器与参数解析器全部以 `@Component` 暴露，由 `leaf-base-framework` 的 `GXWebMvcConfigurer.addInterceptors` 统一注册。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 逻辑正确性 | 登录写 Cookie、Token 解析、缓存校验、登出清理的主链路自洽，既有 40 条用例全绿；但**权限拦截在 URI 模式下被默认值绕过**、**登录插件返回失败被吞掉**、**登出返回值把"Cookie 不存在"当作失败**、**Nacos 配置热更新口径不一致** | 存在高风险缺陷 |
| 安全（契约） | **默认密钥硬编码**、**缓存不可用即降级为"自包含 Cookie"本地模式**、**默认 `cacheExpires=-1` 使 Token 永不过期**三者叠加，可在拿到合法 Cookie 后长期复用/伪造身份 | 高危 |
| 线程安全 | 无致命数据竞争：配置读取用 volatile + DCL、`GXSSOHandler` 用 CAS、`AntPathMatcher` 无状态、`SecureRandom` 线程安全；风险集中在**全局可变静态配置（`GXSSOHelperUtil`）**、**注解缓存超限整体清空**、**验证码实例可变懒初始化**、**测试通过全局静态状态互相污染** | 需补约束 |
| 性能 | 每请求固定付出：Base64 解码 + 3–4 次 MD5 + 2 次 256 轮 RC4 密钥表 + JSON 解析 + 2 次 `getBean` 查找 + 插件循环；无解析结果缓存；`GXIpHelperUtil` 在 localhost 分支每请求触发 `InetAddress.getLocalHost()` | 结构性开销，非阻断 |

关键证据（均可按位置复核）：

- **E-01**：`GXSSOPermissionInterceptor.requiresTokenForPermission`（第 61–66 行）：`permission == null` 时返回 `!nothingAnnotationPass`，而 `nothingAnnotationPass` 默认 `true`（第 28 行）→ `preHandle` 在第 38–40 行直接 `return true`，**永不进入 `isVerification` 的 URI 校验**。即 `permissionUri=true` 且方法无注解时，`GXSSOAuthorization` 的 URI 授权形同失效。
- **E-02**：`GXSSOHelperUtil.getSSOConfig`（第 38–70 行）在 `GXSpringContextUtils.getBean(GXSSOConfigProperties.class)` 返回 null（该方法对缺失/重复/创建失败一律捕获异常并返回 null，`GXSpringContextUtils` 第 45–62 行）或 `getConfig()` 为 null 时，**静默** `new GXSSOProperties()`（第 46–48、60–65 行），此时 `cache == null`、`pluginList == null`；`GXSSOSupportService.cacheSSOToken`（第 48–63 行）在 `cache == null` 时**直接返回 Cookie 中的 token**，不做任何服务端校验。更关键的是实测：全仓检索 `implements GXSSOCache` **没有任何生产实现**（仅测试内匿名实现），因此"`cache == null`"不是异常路径而是**默认路径**，即所有下游默认运行在"自包含 Cookie、零服务端校验"模式。宪法 IV 明确"禁止静默降级"。
- **E-03**：`GXTokenConfigService.getTokenSecret`（第 17–22 行）默认返回 `GXTokenConstant.TOKEN_SECRET_KEY`（`leaf-base-framework` 中为常量 `"31D0D393D3EAF5"`，第 30 行），仅在首次调用时 WARN 一次；`GXAuthCodeUtils` 的签名是 RC4 + MD5 的对称可逆编码，密钥是公开常量 → 默认配置下 Token 可被离线伪造。
- **E-04**：`GXSSOProperties.cacheExpires` 默认等于 `GXCookieHelperUtil.CLEAR_BROWSER_IS_CLOSED`（`-1`，第 141 行）；`GXAbstractSSOService.fillTokenValueIfNecessary` 以 `Math.max(cacheExpires, 0)` 作为签名过期秒数（第 163–165 行）→ `authCode` 的 `expiry<=0` 分支写入 `0000000000`，解码侧判定为**永不过期**（`GXAuthCodeUtils` 第 141、147 行）；同时 `GXSSOCache.set` 的 `expires > 0` 不成立（第 60–64 行）→ 缓存写入**不带 TTL**。
- **E-05**：`GXSSOProperties` 源码内硬编码凭据：`rsaKeypass = "llTs1p68K"`（第 61 行）、`rsaStorepass = "lLt66Y8L321"`（第 66 行）、`getSignKey()` 缺省返回 88 字符密钥（第 201–206 行）；同一份口令被手册原样抄录（手册第 418–419 行）。
- **E-06**：`GXLocalSSOConfigProperties` 用 `@ConditionalOnMissingClass("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties")`（第 18 行）互斥 `GXNacosSSOConfigProperties` 的 `@ConditionalOnClass`（第 18 行）；但本模块 `pom.xml` 以 compile 依赖引入 `leaf-base-nacos`（第 20–24 行），后者传递 `nacos-client` → **local 变体在任何场景下都不生效**，`sso.yml` / `url-white-lists.yml` 本地文件不会再被加载。
- **E-07**：`GXNacosSSOConfigProperties` 的 `@NacosConfigurationProperties`（第 19–23 行）**未设置 `autoRefreshed`**（默认 false），而同模块 `GXNacosUrlWhiteListsConfigProperties`（第 19 行）设置了 `autoRefreshed = true` → 白名单可热更新、SSO 主配置不可以，同类配置两套语义。
- **E-08**：`GXAbstractSSOService.setCookie`（第 64–148 行）先 `getSSOTokenFromCookie`（第 75 行）再 `cache.set`（第 87 行）做"读—改—写"合并，无任何锁或版本校验；同一用户并发两次登录请求会互相覆盖（后写覆盖先写的 `createTime` / `userAgent` / `ip` / 自定义字段）。
- **E-09**：`GXSSOCache.getValidUserId`（第 116–122 行）在 `userId<=0` 时抛 `GXTokenInvalidException`，被 `set`（第 55 行）、`delete`（第 86 行）、`get`（第 39 行）共用 → `kickLogin(null)`（`GXAbstractSSOService` 第 53–62 行）抛异常而不是返回 `false`。
- **E-10**：`GXSSOCache.delete`（第 90–96 行）以 `Objects.nonNull(result)` 判定成功 → 缓存键已不存在（重复登出、已过期）时 `deleteCache` 返回 null，被判为**失败**并打 WARN，语义与"删除成功"混淆。
- **E-11**：`GXSSOCache.get`（第 35–37 行）在 `checkLoginStatus()` 或 `verifyTokenEffectiveness()` 为 false 时**抛** `GXTokenInvalidException`；而 `GXSSOSupportService.cacheSSOToken`（第 50–53 行）对"缓存未命中"返回空 `Dict`（走 401 重定向）。同一个"未登录"存在"抛异常"和"返回空"两种对外语义。
- **E-12**：`GXSSOCache.get` 第 27 行与 `GXSSOHelperUtil.parser` 第 147 行都以 `GXCurrentRequestContextUtils.isRPC()` 短路返回空 → **非 HTTP 上下文（异步线程、消息消费、RPC 提供方）中身份恒为空**，该语义未在手册与 Javadoc 中声明。
- **E-13**：`GXSSOAuthorizationInterceptor.handleEmptyToken`（第 115–137 行）在未登录时：AJAX 分支调 `getHandler().preTokenIsNullAjax` 后执行 `ssoCache.delete(Dict.create())`（第 121–123 行）；普通分支调 `clearRedirectLogin`（第 132 行）→ `GXAbstractSSOService.clearRedirectLogin`（第 198–199 行）→ `clearLogin` → 保护版 `logout`（`GXSSOSupportService` 第 147–169 行）→ **依次执行 cache.delete、全部插件 logout、清 Cookie**。匿名请求因此产生写操作与插件副作用。
- **E-14**：`GXSSOAuthorizationInterceptor.isWhiteListUrl`（第 93–100 行）用 `pathMatcher.match(pattern, request.getRequestURI())`；`getRequestURI()` 含 context-path 而白名单通常是应用内路径 → 部署在 `server.servlet.context-path` 下时白名单**全部失效**（登录拦截误拦公开接口）。同时 `urlWhiteListsConfigProperties` 为空时返回 `false`（fail-closed，方向正确）。
- **E-15**：`GXWebMvcConfigurer.addInterceptors`（第 38–47 行）按 **bean 名称字典序**注册所有 `GXAuthorizationInterceptor` 子类，无显式 `order()`；手册第 497 行却要求"`GXSSOAuthorizationInterceptor` 必须在 `GXSSOPermissionInterceptor` 之前执行"。当前顺序依赖 `gxSSOAuthorizationInterceptor` < `gxSSOPermissionInterceptor` 的字母序巧合。
- **E-16**：`GXAbstractSSOService.setCookie` 的插件登录循环（第 135–143 行）在 `plugin.login()` 返回 `false` 时只 `log.warn`，Cookie 照常下发 → 与 `AGENTS.md` 第 33 行"插件扩展点不能吞掉关键失败"相悖（`validateToken` 分支则会拒绝，两处策略不一致）。
- **E-17**：`GXAbstractSSOService.authCookie`（第 168–179 行）生成 32 位随机 `sessionId` 后调用 `GXCookieHelperUtil.authJSESSIONID`（`leaf-base-framework` 第 18–29 行），后者只做 `request.getSession().invalidate()` + `setAttribute("MAPLE-" + value, true)`：**随机串不参与会话标识**；`invalidate` 会连带丢弃 session 中的验证码（`CaptchaStoreSession` 基于 `HttpSession`）与业务会话数据。
- **E-18**：`GXAbstractSSOService.setCookie` 手写 `Set-Cookie`（第 104–130 行）：`cookieBuilder` 直接拼接 `name/value/path/maxAge/domain/SameSite`，不转义、不校验；`cookieDomain`、`cookiePath`、`cookieSameSite` 来自外部配置（Nacos 可远程改写）→ 配置侧一旦被改写即可注入任意 Cookie 属性（如 `Path=/` + 叠加域）。第 126–128 行对 `SameSite=None` 无 `Secure` 校验。第 132–133 行下发已废弃的 `X-XSS-Protection`。
- **E-19**：`GXSSOSupportService.checkIpBrowser`（第 89–95 行）仅在 `ip != null` 时比对 → 请求 IP 解析为 null 时**跳过校验**（fail-open）；IP 来源存在两套口径：`GXIpHelperUtil.getIpAddr` 受 `trustForwardedIpHeaders` 控制（默认 false），而 `GXSSOHelperUtil.parser` 第 194–195 行用 `GXCurrentRequestContextUtils.getClientIP()`（**无条件**信任 `X-Forwarded-For` 等头）写回 token 的 `ip` 字段。
- **E-20**：`GXHandlerMethodAnnotationUtils` 的 `ANNOTATION_CACHE` 为**静态** `ConcurrentHashMap`（第 19 行），`size() >= 4096` 时整体 `clear()`（第 29–31 行）→ 缓存雪崩式回源；键 `CacheKey` 持有 `Method`/`Class`（第 138–144 行）→ 静态强引用阻止类加载器回收（devtools / 热部署下泄漏）；`clear()` 与 `putIfAbsent` 之间存在竞态。
- **E-21**：`AbstractCaptcha` 是 `@Data` 可变对象，`randomCaptcha()`（第 101–133 行）在**读路径**上懒写入 `font/rgbArr/suffix/width/height/length`；类注释第 18–20 行要求"不要跨端点共享"，但没有不可变化或构造期收敛 → 共享实例在并发下互相污染参数（题面/尺寸/字号漂移）。
- **E-22**：`ImageCaptcha.getAlpha`（第 132–137 行）以 `length - 1` 为除数，`length == 1` 且 `gif=true` 时得到 `Infinity/NaN`，第 115 行 `AlphaComposite.getInstance` 抛 `IllegalArgumentException`；`writeImage` 只 `catch IOException`（第 77–79 行）→ 运行时异常直出。`length` 下限在 `AbstractCaptcha` 第 119–123 行只兜到 1，未拒绝 1。
- **E-23**：`AbstractCaptcha.verification`（第 72–76 行）在 `captcha == null` 时**直接 `remove(ticket)`** → 同一会话内可被第三方请求主动作废用户验证码（若 ticket 可预测/可枚举）；`generate` 在 `store.put` 返回 false 时（第 61–64 行）不写响应体也不报错 → 客户端收到 200 空图。
- **E-24**：每请求固定开销可逐项列出：`GXSSOHelperUtil.parser` → `Base64` 解码 + `md5(key)` + `md5/子串` ×2 + `getKey` 256 轮 + RC4 + `JSONUtil.toBean`（`GXAuthCodeUtils` 第 128–150、157–176 行）；`GXSSOCache.get` → 2 次 `GXSpringContextUtils.getBean`（第 31–32 行）+ 缓存反序列化（第 124–135 行）；`GXSSOAuthorizationInterceptor` → 1 次 `getBean(GXAuthorizationInterceptorService)`（第 103–104 行）；`GXSSOPermissionInterceptor` → 1 次 `getBean(GXSSOAuthorization)`（第 102 行）。以上均无解析结果缓存。
- **E-25**：`GXIpHelperUtil` 静态块在类初始化时执行 `InetAddress.getLocalHost()`（第 17–35 行）；`getIpAddr` 在来源为本地回环时**每请求**再执行一次 `InetAddress.getLocalHost().getHostAddress()`（第 52–58 行）；`isLocalIp` 每次调用 `getAllByName`（第 63–78 行）→ 热路径上引入 DNS/主机名解析延迟。
- **E-26**：日志成本：未登录路径每请求 1 条 INFO（`GXSSOSupportService` 第 40 行、第 71 行）；`GXBrowserUtil` 在缺失 UA 时每请求 WARN（第 23、29 行）；DEBUG 打开时每请求拼装 `summarizeToken`（第 105–118 行）；`GXSSOPlugin` 失败各自 WARN，无 `isEnabled` 守卫。
- **E-27**：`GXHttpUtil.inContainURL`（第 55–68 行）用 `reqUrl.indexOf(s) > 1` 做**子串**匹配：`/api/user` 会命中 `/api/user-admin`，且匹配位置为 0/1 时漏判；`requestPlayload`（第 129–140 行）无长度上限、使用平台默认字符集、读完即消费请求体（下游再读为空）。
- **E-28**：`GXSSOSupportService.generateCookie`（第 134 行）对请求属性做 `(Integer) request.getAttribute(...)` 强转 → 外部若写入 `Long/String` 会抛 `ClassCastException`，再被外层包成 `GXBusinessException`（语义污染）。
- **E-29**：`GXSSODefaultHandler.preTokenIsNullAjax`（第 51–71 行）以手工 `Dict` 拼 `code/msg/data` 并 `getWriter().write`，未走 `GXResultUtils`（宪法 III）；异常仅记录日志后返回，响应可能为空体。`preTokenIsNull`（第 74–79 行）恒返回 true。
- **E-30**：`GXLoginUserHandlerMethodArgumentResolver.resolveArgument`（第 33–50 行）在用户不存在时返回 `null` → 控制器拿到 null 入参而非明确失败；且每请求 `getBean(GXUUserService)` + `JSONUtil.toJsonStr` → `toBean` 往返（第 40–49 行），无缓存（宪法 III"业务失败必须抛异常"）。
- **E-31**：模块**无** `src/main/resources`：无 `additional-spring-configuration-metadata.json`（宪法 IV 要求新增开关登记）、无自动装配清单；`GXSSOProperties` 中 `signKey`/`signAlgorithm`/`rsaJksStore`/`rsaCertStore`/`rsaAlias`/`rsaKeypass`/`rsaStorepass` 七个字段**无任何生产代码读取**（仅 getter 抛 `GXBusinessException`，第 191–213 行），属"看起来可配、实际无效"的遗留契约。
- **E-32**：手册与实现大面积漂移：手册第 409–437 行列出 `tokenTimeout`/`tokenActivityTimeout`/`aesKey`/`multiLogin`/`tokenPrefix`/`userPermissionCachePrefix`/`cacheCaptchaCodePrefix`/`urlWhiteLists`/`illegalUrl`/`isNothingAnnotationPass`/`captchaTimeout`，**这些字段在 `GXSSOProperties` 中均不存在**；`tokenName` 手册写默认 `ssoToken`、实现为 `token`；`signKey` 手册写 `maple-leaf-framework`、实现为 88 字符密钥；配置前缀手册写 `maple.sso.*`、实现为 `sso.*` 与 `url.*`；手册第 461 行要求"手工注册拦截器"，实现已由 `GXWebMvcConfigurer` 自动注册。
- **E-33**：空值契约缺失：全部 17 个 `package-info` 均为 `@NullUnmarked`，模块**零 `@Nullable`**；而实际存在可空返回（`GXSSOHelperUtil.parser` 失败路径、`getSSOTokenFromCookie`、`GXSSOAuthorizationInterceptor` 返回 null、`GXBrowserUtil.getUserAgent`、resolver 返回 null）与可空入参（`kickLogin(null)`、`verification(null)`、`setSsoConfig` 之外的所有 `Dict` 入参）。
- **E-34**：测试缺口：无 `@SpringBootTest`（无真实 MVC 拦截链与自动注册验证）、无并发用例、无 Token 过期/TTL 用例、无 `ImageCaptcha.writeImage` 用例（E-22 因此不会被发现）、无白名单用例、无本地模式（`flag=CACHE_SHUT`）端到端用例；且 5 个测试类通过 `GXSSOHelperUtil.setSsoConfig(...)` 改写**全局静态**配置（`GXSSOServiceBehaviorTest` 第 50 行等）→ 用例不可并行、顺序敏感。

发现清单（分级）：

- **F-01（高）URI 权限模式被默认值绕过**：`permissionUri=true` 且方法无 `@GXPermissionAnnotation` 时直接放行（E-01）。
- **F-02（高）默认无缓存实现 + 缺失即静默降级，实际默认运行在"自包含 Cookie"模式**：全仓无 `GXSSOCache` 生产实现 → 无缓存、无插件、零服务端校验（E-02）。
- **F-03（高）默认密钥硬编码且可离线伪造 Token**：`TOKEN_SECRET_KEY` 公开常量 + RC4 对称编码（E-03）。
- **F-04（高）默认配置下 Token 永不过期**：`cacheExpires=-1` 同时关闭签名过期与缓存 TTL（E-04）。
- **F-05（中）源码硬编码 RSA/签名口令**：与宪法 IV"敏感值只能 `${ENV_VAR:default}`"冲突（E-05）。
- **F-06（中）双配置源实际不可达**：compile 依赖 `leaf-base-nacos` → local 变体恒不生效（E-06）。
- **F-07（中）同类配置热更新语义不一致**：SSO 主配置无 `autoRefreshed`，白名单有（E-07）。
- **F-08（中）登录写 Cookie 的读改写无原子性**：并发登录互相覆盖 Token 字段（E-08）。
- **F-09（中）`userId` 非法时抛异常而非明确失败**：`kickLogin(null)` / `set(task 无 userId)` 抛 `GXTokenInvalidException`（E-09）。
- **F-10（中）"删除成功"判定错误**：键不存在被当作失败并 WARN（E-10）。
- **F-11（中）"未登录"两种对外语义**：抛 `GXTokenInvalidException` vs 返回空 `Dict`（E-11）。
- **F-12（中）非 HTTP 上下文身份恒为空且未声明**：`isRPC()` 短路（E-12）。
- **F-13（中）匿名请求产生写副作用**：cache.delete + 插件 logout + 清 Cookie（E-13）。
- **F-14（中）白名单不剥离 context-path**：非根 context-path 下公开接口被误拦（E-14）。
- **F-15（中）拦截器链顺序隐式依赖 bean 名**：与手册的显式顺序要求不符（E-15）。
- **F-16（中）登录插件失败被吞**：`plugin.login=false` 仅告警，登录继续（E-16）。
- **F-17（中）会话固定防护名不副实且误伤会话状态**：随机串不参与会话 id，`invalidate` 丢弃验证码（E-17）。
- **F-18（中）手写 `Set-Cookie` 无转义/无校验**：配置可注入 Cookie 属性，`SameSite=None` 无 `Secure` 约束（E-18）。
- **F-19（中）IP 校验 fail-open 且两套 IP 口径**：null 跳过校验；XFF 默认信任与否不一致（E-19）。
- **F-20（中）注解缓存超限整体清空 + 静态持有 `Method`**：反射风暴与类加载器泄漏（E-20）。
- **F-21（中）验证码实例可变且读路径写字段**：共享实例并发污染（E-21）。
- **F-22（中）`length=1` + GIF 抛 `IllegalArgumentException`**：除零未覆盖（E-22）。
- **F-23（低）`verification(null)` 可主动作废验证码**（E-23）。
- **F-24（低）验证码存储失败时静默空响应**（E-23）。
- **F-25（中）每请求固定密码学 + JSON + Bean 查找开销，无缓存**（E-24）。
- **F-26（低）IP 解析在热路径触发主机名解析**（E-25）。
- **F-27（低）未登录路径 INFO 日志刷屏、缺失 UA 每请求 WARN**（E-26）。
- **F-28（低）`inContainURL` 子串匹配误判；`requestPlayload` 无上限且消费请求体**（E-27）。
- **F-29（低）请求属性强转 `ClassCastException` 被包成业务异常**（E-28）。
- **F-30（低）未登录响应未走统一响应构造**（E-29）。
- **F-31（低）`@GXLoginUserAnnotation` 解析失败返回 null 而非业务异常，且每请求 bean 查找 + JSON 往返**（E-30）。
- **F-32（低）七个遗留配置字段无效且无可配置元数据**：无 `additional-spring-configuration-metadata.json`（E-31）。
- **F-33（低）手册与实现大面积漂移**（E-32）。
- **F-34（低）空值契约零标注**（E-33）。
- **F-35（低）测试缺口与全局静态污染**（E-34）。
- **F-36（低）接口/实现不对称**：`logout`、`authCookie`、`attrSSOToken` 不在 `GXSSOService` 接口，只能向下转型调用（`GXSSOService` 第 9–19 行 vs `GXAbstractSSOService` 第 168、223 行）。
- **F-37（低）验证码 GIF 编码器为内联第三方实现**（`Encoder` 8.9KB / `GifEncoder` 15.6KB / `Quant` 14.1KB），无来源与许可声明，无可维护性说明。

已确认为**正确实现**（不得在修复中被破坏）：

- **P-01**：`setCookie` 以 `new Dict(ssoToken)` 复制入参（第 67 行），不修改调用方对象；`SameSite` 可配置且默认 `Lax`（既有 2 条用例锁定）。
- **P-02**：`verifyTokenConsistency` 仅比对 `loginAt`，且缓存侧缺省值取 `1L`（`GXSSOCache` 第 105 行）避免"双方都缺字段即相等"；被踢下线的旧 Cookie 不会覆盖新登录。
- **P-03**：插件异常不被吞掉（`setCookie` 的 `catch (Exception)` 在插件循环之外，`logout` 与 `validateToken` 均不 try-catch 插件）——与 `AGENTS.md` 第 33 行一致。
- **P-04**：白名单为空或未装配时 `isWhiteListUrl` 返回 `false`，方向为 fail-closed（第 94–96 行）。
- **P-05**：浏览器指纹升级为 SHA-256 并保留 5 位 MD5 兼容；`trustForwardedIpHeaders` 默认 false，XFF 默认不被信任（2 条用例锁定）。
- **P-06**：注解查找对"无注解"做负缓存（`Optional.empty()`）、`putIfAbsent` 保证幂等，且覆盖桥接方法/接口默认方法/父类（2 条用例锁定）。
- **P-07**：`GXSSOAuthorizationInterceptor.getHandler` 用 CAS 初始化默认处理器、`setHandler` 非空校验；`isErrorDispatch` 避免错误页被二次拦截（2 条用例锁定）。
- **P-08**：验证码一次性消费（`consume` = get + remove），且不覆盖 `HttpSession` 的 `maxInactiveInterval`（2 条用例锁定）。
- **P-09**：`GXAuthCodeUtils.authCodeEncode` 输出 URL-safe Base64（Cookie 安全字符集），解码失败统一返回 `{}`（fail-closed）。
- **P-10**：既有测试 40 条全部通过（实测），其中 15 条覆盖默认缓存实现的读/写/删与有效性钩子、3 条覆盖枚举兜底、5 条覆盖验证码、6 条覆盖工具类安全默认值。

## Clarifications

### Session 2026-10-23

- Q: 本次交付是"只给审查结论与验证基线"，还是"结论 + 直接修复已确认缺陷"？ → A: 按 `/speckit.specify` 的职责边界，本次交付为**可验收规格 + 可复核结论**；生产代码修复与配套测试在 `/speckit.plan` → `/speckit.implement` 阶段执行。用户"改完代码必须做完备测试"的要求固化为 FR-020～FR-023 的硬门槛，而不是在本阶段改代码。
- Q: 审查是否需要外部系统（Redis、Nacos、浏览器）？ → A: 不需要真实中间件。缓存用内存实现（既有 `InMemoryCacheService` 模式），配置装配用 `GenericApplicationContext`，Web 链用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `MockMvc` 异步分派即可覆盖。
- Q: 性能结论按什么口径判定？ → A: 不设绝对吞吐/时延门禁；只保留可复算口径（每请求 MD5 次数、RC4 密钥表轮数、JSON 次数、`getBean` 次数、日志条数、IP 解析的 DNS 触发点）与"同环境重跑同量级"的复现要求。
- Q: 默认值是否允许变更（`tokenName`、`cookieName`、`cookieSameSite`、`cacheExpires`、`permissionUri`、`nothingAnnotationPass`）？ → A: **允许，且必须变更的部分单独声明**：`cacheExpires` 的"无过期"必须改为有默认过期（安全问题，属破坏性行为修正，需给出迁移说明）；`tokenName`/`cookieName`/`cookieSameSite`/`permissionUri` 默认值保持不变；`nothingAnnotationPass` 保持默认 `true` 但在 URI 模式下必须**不再短路**（见 FR-006）。
- Q: 硬编码默认密钥与 RSA 口令如何处置？ → A: 默认密钥只作为"开发态兜底"保留但必须满足三条：启动时显式告警升级为 ERROR 级、`production` profile 下**拒绝启动**、源码中删除 RSA 口令与 `signKey` 硬编码（改为 `null` + 启动校验）。兼容策略：旧 Token 用旧密钥仍可解析一次（灰度期），并有明确下线版本。
- Q: 本地模式（`flag=CACHE_SHUT`，缓存不可用时用自包含 Cookie）应保留还是移除？ → A: **保留能力但必须显式开启**：默认"缓存不可用 = 登录失败"（fail-closed）；显式配置 `cacheFallbackEnabled=true` 才允许降级，且降级时 Cookie 必须带短过期、日志 ERROR 级记录，并在响应中可观测。
- Q: 工作区里 local 配置源实际不可达（compile 依赖 nacos-client），应如何收敛？ → A: 二选一由 `/speckit.plan` 决定：把 `leaf-base-nacos` 改为 `optional`/`provided` 使 local 变体可生效，或删除 local 变体并在文档中声明"仅支持 Nacos"。无论哪种，**不允许保留"文档说支持两套、实际只有一套"的状态**。
- Q: 权限模型在 URI 模式与注解模式下应如何收敛？ → A: 定义唯一优先级：`@GXIgnoreLoginIntercept` → `@GXPermissionAnnotation(Skip)` → URI 模式（`permissionUri=true` 时 `isPermitted(token, uri)` 必须决断，不再回落到 `nothingAnnotationPass`）→ 注解模式 → `nothingAnnotationPass`。任何分支都不允许"校验失败后再被默认值放行"。
- Q: 测试完备性的最低标准是什么？ → A: 任何生产代码改动必须在同一次变更中给出：受影响路径的成功/失败/异常/空值/边界用例、并发用例、`mvn -pl leaf-base-sso test` 全绿输出、以及至少一个 `@SpringBootTest` 真实拦截链用例；不得通过删除或跳过既有用例来"通过"（P-01～P-10 为必须保留的既有断言）。

### Session 2026-10-23（`/speckit.converge` 第五轮补记）

- Q: T102 的宪法 IV / VII 冲突如何裁决？ → A: **两组冲突的最优解不同，分别裁决**：**IV 选"宪法修订"**——`leaf-base-nacos` 是 compile 依赖，类路径条件在本模块恒不可达，"显式开关"是唯一能让 local 变体对真实下游可达且可测的方式（改依赖 scope 后下游仍因聚合门面持有 nacos 而只能用 nacos，F-06 不会真正解决），故以宪法 v1.1.0（MINOR）正式认可该机制并附四条护栏；**VII 选"实现回退"**——`GXSSOTokenState` 重命名为 `GXSSOTokenStateEnums`（符合既有"枚举 `GX<概念>Enums`"行），`GXSSOInterceptorOrder` 的两个常量并入既有 `GXSSOConstant`（`AUTHORIZATION_ORDER`/`PERMISSION_ORDER`）并删除该类；`GXSSOCaptchaValidator` 未落地（改为在宪法 VII 补"校验器 `GX<概念>Validator`"角色，与仓库既有三个 `Validator` 类型对齐）。裁决后 `mvn -o -pl leaf-base-sso test` 仍为 110 通过 / 0 失败。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 审查结论可复核 (Priority: P1)

框架维护者要判断"能否把该模块直接用于生产环境做统一登录"，需要按维度分级、可逐条跳到代码位置的结论，并能用模块自带测试与最小复现用例验证。

**Why this priority**: 审查的价值取决于结论可信度；F-01～F-04 任一条成立都意味着线上可被绕过或长期冒用身份。

**Independent Test**: 随机抽取一条结论，按报告给出的文件与行区间定位，用报告给出的命令或用例复现同一现象。

**Acceptance Scenarios**:

1. **Given** 结论"URI 权限模式被默认值绕过"，**When** 配置 `permissionUri=true`、构造一个无 `@GXPermissionAnnotation` 的方法并让 `GXSSOAuthorization.isPermitted` 恒返回 false，**Then** 拦截器仍返回 true（放行），并可在报告中指出决策点为 `requiresTokenForPermission`。
2. **Given** 结论"缓存不可用即降级为本地 Cookie 模式"，**When** 让 `GXSSOConfigProperties` 的装配失败并携带一个合法 Cookie 发起请求，**Then** 请求被判定为已登录（无服务端校验），且日志无 ERROR。
3. **Given** 结论"默认配置下 Token 永不过期"，**When** 使用默认 `cacheExpires=-1` 生成 Token 并跨时间点解析，**Then** 解码侧始终认为有效。
4. **Given** 结论"拦截器顺序依赖 bean 名"，**When** 重命名任一拦截器 bean，**Then** 注册顺序随之改变。

---

### User Story 2 - 登录、登出与失败语义可判定 (Priority: P1)

接入方需要明确知道：登录成功后哪些失败会让登录失败、登出失败如何判定、缓存不可用时会怎样、未登录请求会不会有副作用。

**Why this priority**: 登录态语义判断错误会直接放大为"无法登录"或"登出不掉"。

**Independent Test**: 对"正常登录、缓存失败、插件失败、重复登出、未登录访问受保护接口"五类场景各构造一次，记录返回码、Cookie、缓存状态与插件调用次数。

**Acceptance Scenarios**:

1. **Given** 一个 `login()` 返回 false 的插件，**When** 完成一次登录，**Then** 登录被明确拒绝（抛业务异常或返回失败），而不是仅记录 WARN 后继续下发 Cookie。
2. **Given** 缓存服务不可用，**When** 完成登录，**Then** 默认按失败处理；仅在显式开启降级时使用短过期本地 Token 并记录 ERROR。
3. **Given** 同一用户重复登出两次，**When** 第二次登出时 Cookie 已不存在，**Then** 返回"已登出"（成功语义），不产生 WARN 级"删除失败"。
4. **Given** 一个未登录的匿名请求访问受保护页面，**When** 拦截器处理完成，**Then** 不产生缓存删除与插件 logout 调用（副作用为零）。

---

### User Story 3 - 并发与共享状态安全可验证 (Priority: P1)

维护者需要知道：全局配置、注解缓存、验证码实例、测试夹具这些共享状态在并发与热部署下是否安全。

**Why this priority**: 并发问题在单线程用例中不暴露，线上表现为"个别请求参数错乱""重启后内存上涨"。

**Independent Test**: 并发改写全局配置、并发访问大量不同 handler、并发复用同一验证码实例，观察结果一致性与内存占用。

**Acceptance Scenarios**:

1. **Given** 同一用户并发两次登录，**When** 两次请求都完成，**Then** 缓存中最终 Token 的字段集完整（不出现 `userAgent` 或自定义字段丢失），或明确记录"后写覆盖"契约。
2. **Given** 注解缓存达到上限（>4096 项），**When** 继续出现新的 handler 组合，**Then** 不发生整体清空导致的回源抖动（置换而非全清）。
3. **Given** 一个验证码实例被两个线程同时使用，**When** 各自生成验证码，**Then** 两者尺寸/长度/字体互不干扰（或实例不可共享被类型系统强制）。
4. **Given** `@SpringBootTest` 全量用例，**When** 以 `junit.jupiter.execution.parallel.enabled=true` 运行，**Then** 用例间不通过全局静态配置互相污染。

---

### User Story 4 - 性能开销定量化 (Priority: P2)

维护者需要把"每次请求的身份校验"和"每次登录"折算成可复算口径，判断它能否承受高 QPS。

**Why this priority**: 该模块位于每个请求的关键路径上，无口径的"有点慢"无法转化为改造计划。

**Independent Test**: 按报告口径统计每请求的 MD5 次数、RC4 密钥表轮数、JSON 解析次数、`getBean` 次数、日志条数、DNS 触发点。

**Acceptance Scenarios**:

1. **Given** 一次带 Cookie 的受保护请求，**When** 统计身份校验开销，**Then** 报告给出"Base64 解码 1 次 / MD5 3–4 次 / RC4 密钥表 2 次×256 轮 / JSON 1 次 / getBean 2–3 次 / 插件循环 N 次"的可复算清单。
2. **Given** 1000 个并发请求来自 localhost，**When** 观察 `GXIpHelperUtil.getIpAddr` 调用，**Then** 报告给出"每请求一次 `InetAddress.getLocalHost()`"的结论与实测耗时量级。
3. **Given** 未登录流量（如健康检查误配），**When** 统计日志量，**Then** 报告给出"每请求 1 条 INFO"的结论与按 QPS 换算的日志条数。

---

### User Story 5 - 对外契约与文档一致 (Priority: P3)

使用方依赖 `AGENTS.md`、开发手册、配置键与默认值判断行为；不一致处必须单列为发现，而不是顺手改掉。

**Why this priority**: 契约漂移不直接报错，但会让接入方按错误前提设计（例如按手册配 `maple.sso.token-timeout`）。

**Independent Test**: 逐条核对文档声明的字段、方法、默认值、配置前缀与实现，给出"一致 / 不一致 / 未覆盖"判定。

**Acceptance Scenarios**:

1. **Given** 手册称存在 `tokenTimeout` 与 `tokenActivityTimeout`，**When** 核对 `GXSSOProperties`，**Then** 判为不一致并给出证据。
2. **Given** 手册称 `tokenName` 默认 `ssoToken`，**When** 核对实现，**Then** 判为不一致（实际 `token`）。
3. **Given** 手册要求手工注册拦截器，**When** 核对 `GXWebMvcConfigurer`，**Then** 判为"已自动注册"并说明顺序来源。

---

### User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

计划阶段需要把发现直接转成任务：每条发现具备编号、级别、维度、位置、触发条件、影响、证据、回归设计。

**Why this priority**: 审查不是终点；不能落到可执行任务的结论等于没有结论。

**Independent Test**: 抽取任一发现，检查八项要素是否齐备；抽取任一"既定契约"检查是否被明确标注，避免被误改。

**Acceptance Scenarios**:

1. **Given** F-01，**When** 交由计划阶段，**Then** 无需补充上下文即可拆出"先写暴露放行的用例、再收敛判定优先级"的任务。
2. **Given** P-01/P-02/P-08 为既有断言，**When** 任何修复改动 Cookie 写入、一致性校验或验证码消费逻辑，**Then** 这些断言必须保持通过。

---

### Edge Cases

- `cacheExpires` 为 `-1 / 0 / 1 / 60 / Integer.MAX_VALUE`；`cookieMaxAge` 为 `-1 / 0 / 正数`；`cookieMaxAge` 与 `cacheExpires` 不一致。
- `tokenName` 与 `cookieName` 配置相同、为空、含空格、含 `;`；`cookieDomain` 为空串 / `.example.com` / `localhost` / 含换行（注入探测）；`cookieSameSite` 为 `None` / 非法值。
- Token 为 `null` / `""` / `" "` / 非 Base64 / Base64 但 RC4 校验失败 / 合法但 `userId=0` / 合法但 `userId` 为字符串 / 超长（>8KB，接近 Cookie 上限）。
- 缓存命中但 `loginAt` 与 Cookie 不同（被踢）、缓存未命中、缓存抛异常、缓存返回非 JSON 字符串、缓存返回非 `Dict` 对象。
- 同一用户同一秒内两次登录（`loginAt` 相同）→ `verifyTokenConsistency` 无法区分新旧 Token。
- `set` / `delete` / `get` 的 `userId` 为 `null` / `0` / `-1`。
- 白名单：空列表 / 单条精确路径 / 带 `**` / 带 context-path / 大小写差异 / 尾部斜杠。
- 非根 `server.servlet.context-path`；`DispatcherType.ERROR` / `ASYNC` / `FORWARD`；`OPTIONS` 预检；静态资源（handler 非 `HandlerMethod`）。
- `permissionUri=true` 与 `nothingAnnotationPass` 的四种组合；`uri` 为 `null`；`GXSSOAuthorization` 未装配。
- 验证码：`length` 为 `1 / 10 / 11`；`width` 小于 `length`；GIF 单帧；`ticket` 为空 / 超长 / 重复；存储 `put` 返回 false；输出流写失败；并发校验同一 `ticket`。
- 匿名请求携带 `X-Requested-With: XMLHttpRequest` 与不带；`X-RESPONSE-TYPE: api` 与不带。
- 插件：`login` / `validateToken` / `logout` 返回 false、抛 `RuntimeException`、抛 `Error`；插件数为 0 / 1 / 多。
- 注解缓存：同一 `Method` 在不同 `beanType` 下解析结果不同（CGLIB 代理、接口默认方法、桥接方法）。
- 关闭 Nacos 服务、Nacos 返回空配置、Nacos 推送更新（观察是否热生效）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖模块全部 62 个生产源码文件与 6 个既有测试类；每个文件 MUST 有明确判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因），不得跳过。
- **FR-002**: 每条发现 MUST 标注维度（逻辑正确性 / 线程安全 / 性能 / 安全与契约）、严重级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件与影响面。
- **FR-003**: 每条发现 MUST 附带复现方式：既有用例、可新增的回归用例设计或明确构造步骤；仅凭阅读的判断 MUST 标注为"未验证假设"。
- **FR-004**: 审查 MUST 以**本仓上游 `leaf-base-framework` 源码**为准核对 `GXAuthCodeUtils`（过期语义、签名强度）、`GXCookieHelperUtil`（`authJSESSIONID` 实际效果）、`GXCurrentRequestContextUtils`（`isRPC` / `getClientIP` 口径）、`GXWebMvcConfigurer`（拦截器注册顺序），不得仅凭类名或经验推断。
- **FR-005**: 登录态终止语义 MUST 按"正常登出 / 被顶下线（kick）/ 缓存未命中 / 缓存不可用 / Token 过期 / 插件拒绝"六类逐条定性，说明缓存变化、Cookie 变化、返回值语义、客户端表现；"只返回空 `Dict`"与"抛异常"混用之处 MUST 列为发现（对应 F-09、F-11）。
- **FR-006**: 权限判定 MUST 收敛为唯一优先级链（`@GXIgnoreLoginIntercept` → `@GXPermissionAnnotation(Skip)` → URI 模式 → 注解模式 → `nothingAnnotationPass`），且 MUST NOT 存在"校验失败后被默认值放行"的路径（对应 F-01）；`uri == null` 时 MUST 明确 fail-closed 或 fail-open 并在文档声明。
- **FR-007**: 安全默认值 MUST 满足：默认密钥不得为公开常量（或以 ERROR 告警 + 生产 profile 拒绝启动替代）、源码内不得出现口令、默认配置下 Token MUST 有绝对过期时间、`SameSite=None` MUST 要求 `Secure`（对应 F-03、F-04、F-05、F-18）。
- **FR-008**: 缓存不可用时的行为 MUST 为显式可配置，默认 fail-closed；降级为本地 Token 时 MUST 使用短过期、ERROR 日志与可观测标记，且 MUST NOT 与正常模式共用同一过期策略（对应 F-02）。
- **FR-009**: 双配置源 MUST 与实际装配一致，三种方式择一：①依赖改为 optional/provided 使类路径条件真实可达；②删除 local 变体并声明"仅支持 Nacos"；③改用显式配置源开关（`sso.config.source=local|nacos`，默认保持既有行为）使 local 变体在任意类路径下均可生效——该方式已由宪法 v1.1.0 §IV 认可，须满足其四条护栏（默认值保持、启动 INFO 打印生效源、互斥与默认值测试、元数据与手册登记）。同类配置的 `autoRefreshed` 语义 MUST 一致（对应 F-06、F-07）。
- **FR-010**: 并发安全结论 MUST 覆盖四类共享状态：全局静态配置（`GXSSOHelperUtil`）、注解缓存（`GXHandlerMethodAnnotationUtils`）、验证码实例（`AbstractCaptcha`）、测试夹具（全局 `setSsoConfig`），并给出各对象的现状保护手段与收敛路径（对应 F-08、F-20、F-21、F-35）。
- **FR-011**: 性能结论 MUST 给出可复算口径：每请求 MD5/RC4/JSON/`getBean`/日志次数、`InetAddress` 触发点、插件循环次数；MUST NOT 以绝对吞吐或时延数值作为通过门槛（对应 F-25、F-26、F-27）。
- **FR-012**: 既有测试 MUST 全部执行并记录结果（当前基线：40 执行 / 0 失败）；MUST NOT 通过删除或跳过用例来"通过"；覆盖缺口 MUST 单列为发现（对应 F-35）。
- **FR-013**: 对外契约（`AGENTS.md`、开发手册第 394–499 与 722–757 行、公开方法签名、配置键、默认值、事件字段）MUST 与实现逐条对照，不一致处 MUST 单列为发现；审查阶段 MUST NOT 修改生产代码（对应 F-32、F-33）。
- **FR-014**: 审查报告 MUST 独立成文（`audit-report.md`），`spec.md` 只保留摘要与引用，两者在交付时 MUST 相互一致。
- **FR-015**: 每条发现 MUST 给出"能在旧代码上失败、在新代码上通过"的回归测试设计（输入、断言点、期望差异），且该测试 MUST 无需外部中间件即可运行。
- **FR-016**: 空值语义 MUST 按 JSpecify 收敛：可空返回（`parser` 失败路径、`getUserAgent`、resolver、`getSSOTokenFromCookie`）与可空入参（`kickLogin`、`verification` 的 `captcha`）MUST 显式标注 `@Nullable` 或改为不允许为空，并 MUST 与 NullAway 构建检查一致（对应 F-34）。
- **FR-017**: 用户身份注入（`@GXLoginUserAnnotation`）失败语义 MUST 唯一：要么抛业务异常，要么明确返回空对象并在文档声明；返回 `null` 给非空参数 MUST 被视为缺陷（对应 F-31）。
- **FR-018**: 任何"写 Cookie / 写缓存"的登录路径 MUST 具备原子性或明确的覆盖契约；并发登录的字段合并 MUST NOT 因后写覆盖而丢失 `userAgent` / `ip` / `createTime`（或 MUST 声明"后写优先"并给出用例）（对应 F-08）。
- **FR-019**: 默认行为 MUST 保持不变的部分：`tokenName=token`、`cookieName=uid`、`cookiePath=/`、`cookieHttpOnly=true`、`cookieSecure=false`、`cookieSameSite=Lax`、`cookieMaxAge=-1`、`permissionUri=false`、`nothingAnnotationPass=true`、`trustForwardedIpHeaders=false`；如需变更 MUST 单列兼容说明、迁移路径与受影响调用方清单。**唯一例外项**：`cacheExpires`（原默认 `-1` = 会话级/永不过期）不再属于"必须保持不变"的默认值——默认值改为 `7200` 秒（FR-007 强制修正，属破坏性变更），`-1` 语义仅在**显式配置**时保留并输出 ERROR 日志；该例外 MUST 单独列在兼容性声明表中，其余键的默认值一律保持原样。全部"必须保持"行为 MUST 有用例锁定（`GXSSOServiceBehaviorTest`、`GXSSOConfigSourceTest`）。
- **FR-020**: 任何生产代码改动 MUST 在同一变更中补齐测试：受影响路径的成功、失败、异常、空值、边界用例各至少 1 条；涉及并发/缓存/生命周期的改动 MUST 补并发用例；涉及 Web 层语义的改动 MUST 至少补 1 个真实 Spring Boot 启动 + 拦截链用例（`@SpringBootTest` + `MockMvc`）。
- **FR-021**: 每个修复项 MUST 附"先在旧代码上失败"的用例证据（用例名 + 失败断言），再给出实现改动；仅补实现不补用例的变更 MUST 视为未完成。
- **FR-022**: 交付前 MUST 提供 `mvn -pl leaf-base-sso test` 的全绿执行输出（含用例总数与耗时），并 MUST 给出"新增 / 修改 / 保留"用例清单，其中保留清单 MUST 包含 P-01～P-10 对应的既有断言。
- **FR-023**: 测试 MUST NOT 依赖反射访问私有字段/方法来构造"缓存不可用""插件失败"等现场；这些现场 MUST 通过可注入的扩展点（`GXSSOCache` / `GXTokenConfigService` / `GXSSOPlugin` / `GXUUserService` 的测试实现）构造。
- **FR-024**: 安全关键路径 MUST 具备可观测性：登录成功/失败、被顶下线、缓存降级、权限拒绝、插件拒绝 MUST 各自有可区分的日志或指标，且 MUST NOT 以 INFO 级别对每个匿名请求无条件输出（对应 F-27）。
- **FR-025**: 验证码 MUST 明确"实例不可共享"的强制手段（不可变或每次新建且类型上禁止复用），`length=1` / `width < length` / `gif` 组合 MUST 有定义行为而非抛运行时异常（对应 F-21、F-22）。
- **FR-026**: 内联第三方 GIF 编码（`Encoder` / `GifEncoder` / `Quant`）MUST 明确来源与许可，或替换为既有依赖能力；MUST NOT 保留无归属的第三方代码（对应 F-37）。

### Key Entities *(include if data involved)*

- **审查发现（Finding）**：编号、级别、维度、代码位置、触发条件、影响面、证据、复现方式、回归测试设计、修复建议。
- **登录态终止矩阵**：终止原因（正常登出 / 被踢 / 缓存未命中 / 缓存不可用 / Token 过期 / 插件拒绝）× 缓存变化 × Cookie 变化 × 返回值语义 × 客户端表现。
- **权限判定矩阵**：注解组合（`@GXIgnoreLoginIntercept` / `@GXPermissionAnnotation(Normal|Skip)` / 无注解）× `permissionUri` × `nothingAnnotationPass` × 最终判定。
- **共享状态清单**：对象（全局配置 / 注解缓存 / 验证码实例 / `SecureRandom` / `GXSSOHandler` 引用）× 共享范围 × 现状保护手段 × 收敛路径。
- **性能口径**：每请求 MD5 次数、RC4 密钥表轮数、JSON 次数、`getBean` 次数、日志条数、`InetAddress` 触发点、插件循环次数、测试墙钟耗时。
- **配置契约表**：配置键 × 实际生效类 × 默认值 × 是否被代码读取 × 手册描述 × 一致性判定。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查覆盖 100% 的 62 个生产源码文件与 6 个既有测试类，且 100% 的发现记录在独立报告中；新维护者可在 15 分钟内复现任一结论。
- **SC-002**: 100% 的发现具备"级别 + 维度 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归设计"；0 条仅凭阅读的结论混入已确认项。
- **SC-003**: 登录态终止矩阵覆盖 ≥7 类终止（正常登出 / 被踢 / 缓存未命中 / 缓存不可用（未降级）/ 缓存不可用（已降级 `LOCAL_ONLY`）/ Token 过期 / 插件拒绝）× 5 个观测点（缓存变化 / Cookie 变化 / 返回值语义 / 客户端表现 / 指标变化），判定率 100%，每条判定附上游源码或实测证据（口径以 `data-model.md` §4 为准）。
- **SC-004**: 权限判定矩阵覆盖 4 类注解组合（无注解 / `@GXPermissionAnnotation` / `@GXPermissionAnnotation(action=Skip)` / `@GXIgnoreLoginIntercept`）× 4 种配置组合（`permissionUri` × `nothingAnnotationPass`）= **16 种组合**，覆盖率 100%，且明确标出放行/拒绝的期望值与现状值（口径以 `data-model.md` §5 的 4 行 × 4 列为准）。
- **SC-005**: 性能结论给出可复算口径，且"每请求 MD5 次数""每请求 RC4 密钥表轮数""每请求 `getBean` 次数""每请求日志条数""每请求 `InetAddress` 触发次数"五项可被独立复算（误差 0）；MUST NOT 以绝对吞吐或时延数值作为通过门槛。
- **SC-006**: 既有测试执行率 100%（基线 40 执行 / 0 失败），测试覆盖缺口 100% 被记录为发现并对应到 `AGENTS.md` 第 4 节的具体条款。
- **SC-007**: 高危发现（F-01 / F-02 / F-03 / F-04）100% 具备可构造的故障现场与回归测试设计，计划阶段无需补充调研即可拆分为任务。
- **SC-008**: 配置契约表覆盖 `GXSSOProperties` 与 `GXUrlWhiteListsConfigProperties` 与子类声明的 100% 配置项，偏差 100% 列为发现。
- **SC-009**: 修复阶段每个改动项的"旧代码失败用例"证据覆盖率 100%（FR-021），且 `mvn -pl leaf-base-sso test` 全绿、用例数从 40 单调增长（不减少）。

## Assumptions

- 目标运行形态为 JDK 21+ / Spring Boot 4.1.x / Spring Framework 7.x，下游通过 `@GXEnableLeafFramework` 的 `@ComponentScan("cn.maple")` 装配拦截器与解析器（模块无 `spring.factories`，Windows 本机实测 JDK + Maven 离线仓库可运行模块测试）。
- 本次为审查交付，不修改生产代码；修复在 `/speckit.plan` / `/speckit.implement` 阶段执行，并受 FR-020～FR-023 的测试门槛约束。
- 模块安全边界依赖上游 `leaf-base-framework`（签名算法、Cookie 工具、请求上下文）；本规格只要求"不因本模块的默认值放大上游的弱点"，不要求重写上游密码学实现（是否更换签名算法由 `/speckit.plan` 单独立项）。
- "缓存可用"以装配到 `GXSSOCache` 与 `GXBaseCacheService` 两个 bean 为准；未装配即视为不可用，其默认行为在修复后应为 fail-closed。
- 既有用例中被锁定的行为（P-01～P-10）按"既定契约"处理，修复只能在不破坏这些断言的前提下进行。
- 性能数据以单机、单 JVM、本地回环网络为前提；结论以"可复算口径 + 同量级复现"为验收标准，不承诺生产容量。
- 手册（`docs/leaf-base-sso开发手册.md`）视为**声明**而非规范：与实现冲突时以"实现 + 宪法"为准，冲突本身单列为发现，不在本阶段直接改文档。
