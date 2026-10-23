# Phase 0 研究与决策：leaf-base-sso 审查

**Feature**: 013-sso-module-audit | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

本文件记录两件事：**R 系列 = 已核对的事实**（可复核，不做假设）；**D 系列 = 设计决策**（决策 / 理由 / 被否决的替代方案）。所有事实均来自本仓源码或本次实测命令，不引用外部经验。

---

## 一、事实核对（R-01 ~ R-14）

### R-01 双配置源：local 变体永不生效（实测）

- 命令：`mvn -o -pl leaf-base-sso dependency:tree "-Dincludes=com.alibaba.nacos"`
- 结果：`cn.maple.framework:leaf-base-nacos:jar:4.3.0-SNAPSHOT:compile` → `com.alibaba.nacos:nacos-client:jar:3.1.1:compile`（含 `nacos-client-basic`、`nacos-auth-plugin` 等传递项）。
- 推论：`GXLocalSSOConfigProperties`（第 18 行）的 `@ConditionalOnMissingClass("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties")` **恒为假**；`GXNacosSSOConfigProperties`（第 18 行）恒为真。仓库内 `config/{dev,pre,prod,test}/sso.yml`（内容仅 `sso.config.loginUrl` / `logoutUrl`）**从不被加载**。
- 影响：`sso.config.loginUrl` 在未接 Nacos 的环境为空 → `clearRedirectLogin` 走 401 JSON 分支而非跳转登录页（`GXAbstractSSOService` 第 205–215 行）。这是"配置存在但不生效"的可观测故障。

### R-02 默认没有缓存实现：无服务端校验是默认路径

- 全仓 `search_content "implements GXSSOCache"` 仅命中 `GXSSOServiceBehaviorTest` 内的匿名实现，**无生产实现**。
- `GXSpringContextUtils.getBean(Class)`（第 45–62 行）对 `NoSuchBeanDefinitionException` / `NoUniqueBeanDefinitionException` / `BeanCreationException` 一律 `catch` 后 `return null`，**不抛异常**。
- 因此 `GXSSOHelperUtil.getSSOConfig()` 第 43–48 行取到 `null` → `new GXSSOProperties()`（`cache == null`、`pluginList == null`）；`GXSSOSupportService.cacheSSOToken`（第 48–63 行）直接返回 Cookie 中的 Token。
- 影响：默认下游 = "Cookie 即身份"，仅靠 RC4 编码与默认公开密钥保护（与 R-04 叠加为高危）。

### R-03 Token 过期语义与编解码降级

- `GXAuthCodeUtils.authCodeEncode(source, key, expiry)` → `authCode(..., ENCODE, expiry)`；`expiry > 0` 时写入 `now + expiry`，否则写 `0000000000`（第 147–148 行）。
- 解码侧 `(result.indexOf("0000000000") == 0 || Long.parseLong(result.substring(0,10)) - now > 0) && 校验位匹配` 才通过（第 141–142 行）→ `0` 即"永不过期"。
- 外层 `authCodeEncode` 用 `Base64Encoder.encodeUrlSafe`（第 97 行，Cookie 安全字符集）；`authCodeDecode` 任何异常都返回 `"{}"`（第 104–114 行，fail-closed）。
- 影响：`cacheExpires=-1`（默认）同时意味着"签名不过期"与"缓存不过期"（`GXSSOCache.set` 第 60–64 行）。

### R-04 默认密钥为公开常量

- `GXTokenConstant.TOKEN_SECRET_KEY = "31D0D393D3EAF5"`（`leaf-base-framework` 第 30 行）。
- `GXTokenConfigService.getTokenSecret()` 默认返回该常量，`DEFAULT_TOKEN_SECRET_WARNED` 只 WARN 一次（第 15–22 行）。
- 影响：RC4 + MD5 是对称可逆编码，不是签名；密钥公开 = Token 可离线构造任意 `userId`。

### R-05 会话固定防护的真实效果

- `GXCookieHelperUtil.authJSESSIONID(request, value)`（第 18–29 行）：`request.getSession().invalidate()` 后 `request.getSession().setAttribute("MAPLE-" + value, true)`。
- 容器行为：`invalidate` 后新建会话会得到新 ID（该点对会话固定有效），但传进来的 32 位随机串**只作为属性名**，不参与会话标识。
- 副作用：`invalidate` 会丢弃 `CaptchaStoreSession` 依赖的 `HttpSession` 数据与下游业务会话数据（登录成功后验证码自然失效，但同一请求链路上的其它会话态也会丢）。

### R-06 IP 解析存在两套口径

- `GXCurrentRequestContextUtils.getClientIP(HttpServletRequest)`（第 304–341 行）**无条件**读取 `X-Forwarded-For` / `CF-Connecting-IP` / `X-Real-IP` / `Proxy-Client-IP` / `WL-Proxy-Client-IP` / `HTTP_CLIENT_IP` / `HTTP_X_FORWARDED_FOR` / `X-Cluster-Client-IP` / `Fastly-Client-IP` / `True-Client-IP`，取第一个合法 IP。
- `GXIpHelperUtil.getIpAddr`（第 40–61 行）只在 `trustForwardedIpHeaders=true` 时读 `x-forwarded-for` / `Proxy-Client-IP` / `WL-Proxy-Client-IP`，否则用 `getRemoteAddr()`。
- `GXSSOHelperUtil.parser` 第 194–195 行用前者写入 token 的 `ip`；`GXSSOSupportService.checkIpBrowser` 第 90–93 行用后者比对 → 两者不一致时 `cookieCheckIp=true` 会随机失败/放行。

### R-07 拦截器注册顺序 = bean 名字典序

- `GXWebMvcConfigurer.addInterceptors`（第 30–48 行）：`getBeans(GXAuthorizationInterceptor.class)` → `stream().sorted(Comparator.comparing(Map.Entry::getKey))` → `registry.addInterceptor(interceptor)`，无 `.order()`、无 `@Order` 读取。
- `InterceptorRegistry` 的执行顺序 = 添加顺序，因此当前顺序是 `gxSSOAuthorizationInterceptor` < `gxSSOPermissionInterceptor` 的字母序结果。
- 影响：新增任意 `GXAuthorizationInterceptor` 子类（例如审计拦截器 `gxAuditInterceptor`）都会插到登录链之前执行。

### R-08 缓存 API 无 CAS / putIfAbsent

- `GXBaseCacheService`（framework）：`setCache(...)`（含 TTL 与不带 TTL）、`getCache(bucket, key)`、`deleteCache`、`getCacheRemainTimeToLive`、`updateCacheExpiredTime`、`clear`、`size`、`exists`。
- 无 `putIfAbsent` / `compareAndSet` / 原子递增。
- 影响：并发登录的"合并写"无法一次性原子化，只能用"写后回读 + 一次重试"的乐观策略（D-04）。

### R-09 模块无资源目录，但仓库有元数据范式

- `leaf-base-sso/src/main/resources` 不存在。
- 仓库内已有 8 份 `META-INF/additional-spring-configuration-metadata.json`（framework / datasource / eureka-client / eureka-server / feign / rocketmq / seata / webclient，另有 `config/META-INF/`）。
- 影响：新增配置键必须补本模块的元数据文件（宪法 IV 明文要求），且已有可照抄的字段结构。

### R-10 验证码图片渲染从未被测试执行

- `CaptchaBehaviorTest` 的 5 条用例全部使用 `TestCaptcha extends AbstractCaptcha`（覆盖 `writeImage` 为 `out.write(captcha.getBytes())`）。
- `ImageCaptcha.writeImage`（第 59–80 行）在任何测试中均未执行；`getAlpha`（第 132–137 行）在 `length == 1 && gif == true` 时返回 `NaN`，`AlphaComposite.getInstance(SRC_OVER, NaN)`（第 115 行）抛 `IllegalArgumentException`；`writeImage` 只 `catch IOException`。
- 影响：F-22 是"未被覆盖的真实崩溃路径"，不是理论风险。

### R-11 既有测试基线（实测）

- `mvn -o -pl leaf-base-sso test` → **Tests run: 40, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS**；各类耗时：`CaptchaBehaviorTest` 1.398s / `GXEnumBehaviorTest` 0.014s / `GXSSOServiceBehaviorTest` 0.470s / `GXSSOUtilsBehaviorTest` 0.016s / `GXSSOInterceptorBehaviorTest` 0.039s / `GXLoginUserHandlerMethodArgumentResolverTest` 0.028s。

### R-12 框架侧 5 行改动可行

- `GXWebMvcConfigurer` 为 `@Configuration`，`addInterceptors` 已拿到全部拦截器 Bean；把 `Comparator.comparing(Map.Entry::getKey)` 换成 `Comparator.comparingInt(entry -> AnnotationAwareOrderComparator.INSTANCE.getOrder(entry.getValue()))`（稳定排序保留同级字典序）即可支持 `@Order`。
- 未标注 `@Order` 的拦截器得到 `Ordered.LOWEST_PRECEDENCE`，全部同级 → 保持现有字典序，**零回归**。

### R-13 构建门槛（根 POM）

- `<java.version>21</java.version>`、`<release>21</release>`、`<encoding>UTF-8</encoding>`。
- `maven-compiler-plugin` 的 `compilerArgs` 含 `-Xplugin:ErrorProne -XepDisableAllChecks -Xep:NullAway:ERROR -XepOpt:NullAway:AnnotatedPackages=cn.maple -XepOpt:NullAway:JSpecifyMode=true`（第 565 行）。
- 影响：任何新增可空返回/入参必须 `@Nullable`，否则 NullAway 直接失败；不得为通过而修改编译参数。

### R-14 测试上下文约束

- `leaf-base-framework` 引 `spring-boot-starter-web`（compile）→ `MockMvc`、`RANDOM_PORT` 可用；`spring-boot-starter-test`（test）含 JUnit 5、AssertJ、MockMvc。
- `spring-cloud-starter-alibaba-nacos-config` 与 `nacos-client` 同在类路径 → `@SpringBootTest` 若不关闭 Nacos 自动配置，启动期会尝试连接 `serverAddr`（默认空）。
- 影响：新增集成测试类必须带 `properties = {"spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.discovery.enabled=false"}`，并用 `@ComponentScan("cn.maple.sso")` 的最小测试主类，避免拉起 `cn.maple` 全量自动配置。

---

## 二、设计决策（D-01 ~ D-16）

### D-01 Token 签名方案：保留 `GXAuthCodeUtils`，改为"强制外部密钥 + 启动校验"

- **Decision**: 不更换签名算法，不改 `GXAuthCodeUtils`（framework 层，多模块共用）。在本模块把"默认密钥"从"静默可用"改为"启动即失败/告警"：`GXTokenConfigService.getTokenSecret()` 默认实现改为返回 `null`；`GXSSOProperties` 增加启动校验（`InitializingBean`/`@PostConstruct`），密钥为空时：`prod`/`production` profile → 抛 `GXBusinessException` 拒绝启动；其它 profile → ERROR 日志 + 明确提示"仅限开发环境"。
- **Rationale**: 换算法会同时影响 `GXCurrentRequestContextUtils.getLoginCredentials`、`GXResponseBodyAdvice`、RPC 透传等 framework 侧路径，属跨模块破坏性变更（宪法 II）；而"密钥来源"是本模块可控的配置契约，修这里即可消除"公开密钥可离线伪造"。
- **Alternatives considered**: ① 引入 HMAC/JWT（新依赖 + 跨模块兼容问题 + Cookie 体积翻倍）→ 否决；② 在 sso 侧对 payload 追加 HMAC 再套 RC4（双重编码，客户端/其它模块解析不变但需双份实现）→ 复杂度不划算，且仍解决不了"密钥公开"；③ 仅文档提示"必须改密钥"（现状）→ 已被证明无效（手册第 412 行默认值仍是错的）。

### D-02 默认过期：`cacheExpires` 默认 `-1 → 7200`，`-1` 保留为逃生口

- **Decision**: `GXSSOProperties.cacheExpires` 默认值改为 `7200`（2 小时）；显式配置 `-1` 仍表示"签名与缓存都不过期"，但启动时 ERROR 日志；`cookieMaxAge` 默认保持 `-1`（浏览器会话级）。新增只读派生：`tokenTtlSeconds` 不单列字段，复用 `cacheExpires`。
- **Rationale**: 一个字段同时表达"Cookie 客户端生命周期"与"Token 服务端有效期"是现状的设计缺陷；拆成两个字段会引入新键与迁移成本（FR-019 要求默认值尽量不变）。只把**默认值**从"永不过期"改为"2 小时"是最小破坏面，且 `-1` 逃生口保证既有显式配置者行为不变。
- **Alternatives considered**: ① 新增 `tokenTtlSeconds` 独立键（更清晰，但两键并存会产生"谁是权威"的歧义，且手册已有一堆不存在的键，再加键会加剧漂移）→ 留待 v5 主版本；② 直接禁止 `-1`（破坏显式配置者，违反 FR-019）→ 否决。

### D-03 缓存不可用：默认 fail-closed + 显式降级开关

- **Decision**: 新增 `sso.config.cache-fallback-enabled`（默认 `false`）。`cache == null` 时：未开启 → 登录抛 `GXBusinessException("SSO cache unavailable...")`，校验侧返回"未登录（原因 `CACHE_UNAVAILABLE`）"；开启 → 走本地 Token，但 TTL 强制取 `min(cacheExpires, 900)`，并打 ERROR + 指标 `cacheFallback`。
- **Rationale**: 现状 `cache == null` 是默认路径（R-02），等于"框架默认不做服务端校验"；fail-closed 与 `AGENTS.md` §2.3"安全默认值必须保守"一致。保留开关是为了不把"只用 Cookie 的单体应用"直接锁死（宪法 II 兼容性）。
- **Alternatives considered**: ① 提供内置内存缓存实现（`ConcurrentHashMap`）作为默认 → 多实例部署下会造成"随机掉线"，比 fail-closed 更难排查，且悄悄引入状态；② 强制要求实现 `GXSSOCache`（无开关）→ 破坏性过大。

### D-04 并发登录写：写后回读 + 一次重试（乐观并发）

- **Decision**（实施期修订）：不引入"写后回读重试"。落地契约为"**后写优先 + 每次写入自包含**"：合并仍基于 Cookie 中的既有 Token，且每次写入都强制补齐 `userId`/`loginAt`/`createTime`/`userAgent`/`ip`，因此并发覆盖只会丢掉"上一次写入的旧值"，不会产生字段残缺；由 `GXSSOConcurrencyTest#concurrentLoginsKeepMergedFields` 锁定。回读方案被否决的原因见下方 Alternatives。
- **Rationale**: `GXBaseCacheService` 无 CAS（R-08），无法做真正的原子合并；回读校验不新增依赖、不改变公开方法签名，且能覆盖"并发登录丢字段"的绝大多数场景。
- **Alternatives considered**: ① 缓存层新增 `putIfAbsent`（需改 framework 接口 + 所有实现，跨模块破坏）→ 否决；② 写后回读 `loginAt` 再重试（实施期否决：`GXSSOCache#get` 在"仅覆盖 `set`"的既有测试实现下会抛异常并破坏 P-01 相关用例，且多一次缓存往返）；③ 加 JVM 内 `synchronized` 按 userId 分段（单机有效、集群无效）→ 不纳入；④ 后写整条覆盖且不自包含（会丢字段）→ 否决，改为"后写优先 + 自包含"。

### D-05 双配置源：用显式开关替代"类路径条件"

- **Decision**: 两个变体的条件改为 `@ConditionalOnProperty(name = "sso.config.source", havingValue = "local")` 与 `havingValue = "nacos", matchIfMissing = true`；`@PropertySource` 的 `ignoreResourceNotFound` 改为 `true` 并打 ERROR（本地文件缺失不再导致启动失败）；`GXNacosSSOConfigProperties` 补 `autoRefreshed = true`（与白名单一致）。
- **Rationale**: `leaf-base-nacos` 是 compile 依赖且被下游用于服务发现，改 `optional` 会让依赖它的下游丢失传递依赖（R-01）；"类路径条件"在"依赖必然存在"的前提下等于死代码。显式开关让两套实现**真实可达**（满足 FR-009），可被测试直接覆盖，且不改变任何默认行为（默认仍是 Nacos）。
- **Alternatives considered**: ① 把 `leaf-base-nacos` 改 `provided`（破坏下游传递依赖）→ 否决；② 删除 local 变体 + 文档声明"仅 Nacos"（丢失本地开发能力，且仓库已有 `config/*/sso.yml`）→ 否决；③ 保留现状 + 文档说明（等于承认死代码）→ 违反 FR-009。

### D-06 权限判定唯一优先级链

- **Decision**: `GXSSOPermissionInterceptor` 改为显式链式判定，顺序固定：① `@GXIgnoreLoginIntercept` → 放行；② `@GXPermissionAnnotation(action = Skip)` → 放行；③ `permissionUri == true` → `isPermitted(token, uri)` **决断**（true 放行 / false 拒绝，不再回落）；④ 有注解（`Normal` + 非空 `value`）→ `isPermitted(token, value)` 决断；⑤ 无注解 → `nothingAnnotationPass`。`uri == null` 在 ③ 中按 fail-closed 拒绝。`requiresTokenForPermission` 改为按 ③④⑤ 分别判断是否需要 Token。
- **Rationale**: 现状是"③ 失败 → 落到 ⑤ 的默认 true → 放行"（E-01），把 URI 授权变成装饰品；链式判定让"是否需要 token"与"是否放行"由同一处决定，消除两处口径分叉。
- **Alternatives considered**: ① 只把 `nothingAnnotationPass` 默认改 `false`（看似修好，但会破坏"注解模式默认放行"的既有契约，且 URI 模式仍会在 ③ 失败后落到 ④/⑤ 产生歧义）→ 否决；② 拆成两个拦截器（URI 一个、注解一个）→ 改变拦截链结构（AGENTS §3.3）→ 否决。

### D-07 拦截链顺序：`@Order` + 框架 5 行排序

- **Decision**: 在既有 `GXSSOConstant` 内新增 `AUTHORIZATION_ORDER = 100`、`PERMISSION_ORDER = 200`（**实施期修订**：原计划新建 `GXSSOInterceptorOrder`，T102 按宪法 VII"顺序常量并入既有 `GX<范围>Constant`"归并）；两个拦截器加 `@Order(...)`；框架 `GXWebMvcConfigurer` 改为按 `AnnotationAwareOrderComparator` 排序后注册（未标注者同序，保持原字典序）。
- **Rationale**: `InterceptorRegistry` 顺序即执行顺序，而注册发生在框架类（R-07）；本模块无法从外部覆盖。5 行改动、稳定排序、未标注者行为不变，是"最小影响面"的最优解（`AGENTS.md` §变更检查第 1 条的例外需在 PR 说明）。
- **Alternatives considered**: ① 在 sso 模块内合并两个拦截器（破坏公开类型与职责分离）→ 否决；② 让 sso 模块实现自己的 `WebMvcConfigurer` 再注册（与框架重复注册，`addPathPatterns` 不同会导致二次执行）→ 否决；③ 靠重命名 bean 保持字母序（脆弱，且新增拦截器即失效）→ 否决。

### D-08 白名单与 context-path

- **Decision**: 白名单匹配前先用 `UrlPathHelper.defaultInstance.getPathWithinApplication(request)` 取应用内路径（自动剥离 context-path），再做 `AntPathMatcher.match`；同时兼容配置里带 context-path 的写法（同时匹配"应用内路径"与原 URI，命中其一即放行）。
- **Rationale**: `request.getRequestURI()` 含 context-path（E-14），而 Spring 自身匹配拦截器路径用的是应用内路径，两者必须对齐才不会"配了白名单仍被拦"。
- **Alternatives considered**: 只在文档里要求"白名单要带 context-path"→ 部署形态一变就失效，且与 Spring 惯例相反 → 否决。

### D-09 未登录语义：空 `Dict` + 原因码，不抛异常

- **Decision**: `GXSSOCache.get` 在 `checkLoginStatus()`/`verifyTokenEffectiveness()` 为 false 时返回 `Dict.create()` 并把原因写进 `request` 属性（`GXSSOConstant.SSO_TOKEN_STATE` = `GXSSOTokenStateEnums.EXPIRED/PLUGIN_REJECTED`）；`GXTokenInvalidException` 仅保留给显式业务 API（如"刷新 Token"）。
- **Rationale**: 拦截器需要区分"未登录/被踢/过期/缓存不可用/插件拒绝"五种响应（401 JSON、403、重定向登录页；缓存不可用同样走 401 + 原因码），异常类型无法承载；请求属性 + 枚举是最小侵入的可承载方案，且不改变方法签名。
- **Alternatives considered**: ① 返回 `Optional<Dict>`（签名破坏）→ 否决；② 新增结果 DTO（重）→ 否决；③ 继续抛异常并在拦截器 `catch` 分派（异常做流程控制，且 `cacheSSOToken` 之外的调用点仍会漏）→ 否决。

### D-10 匿名请求零副作用

- **Decision**: `GXSSOAuthorizationInterceptor.handleEmptyToken` 删除 `ssoCache.delete(Dict.create())` 调用；`clearRedirectLogin` 只做响应，不再间接触发 `plugin.logout` 与 Cookie 清理（Cookie 清理仅在"被踢"或显式登出时执行）。
- **Rationale**: 匿名请求（含机器人扫描）不应触发写缓存与插件回调（E-13），否则既是性能问题也是放大攻击面的路径；`SSO_KICK_FLAG` 已能区分"被踢"场景，无需靠"总是清场"兜底。
- **Alternatives considered**: 保留但加节流（仍会在缓存层产生写放大）→ 否决。

### D-11 注解缓存：缓存判定结果 + 字符串键 + FIFO 淘汰

- **Decision**: `GXHandlerMethodAnnotationUtils` 改为 `ConcurrentHashMap<String, CachedFlag>`，键 = `beanType.getName() + "#" + method.toGenericString() + "#" + annotationType.getName()`；值 = 枚举/布尔 + 注解 `value()` 字符串（不持有 `Annotation` 实例、不持有 `Method`/`Class`）；容量上限 4096，超限时按 FIFO 淘汰最旧键（`ConcurrentLinkedQueue` + `poll`）。
- **Rationale**: 现状"存 `Annotation` 实例 + 超限整体 `clear()`"同时造成反射风暴与类加载器泄漏（E-20）；不缓存注解实例即可消除强引用链，字符串键仍然命中（`HandlerMethod.getMethod()` 每次是同一个 `Method` 实例，`toGenericString()` 稳定）。
- **Alternatives considered**: ① Caffeine `maximumSize` + `weakKeys`（weakKeys 按身份比较，破坏命中）→ 否决；② `ClassValue`（只按类缓存，无法按方法区分）→ 否决；③ 完全去缓存（每次反射，性能回退）→ 否决。

### D-12 验证码实例：构造期定型 + 校验语义独立

- **Decision**: `AbstractCaptcha` 的配置字段改为 `final`（构造期写入），保留 `@Data` 的 setter 但**仅在 `generate()` 前有效**（首次生成后冻结，再次 set 抛 `GXBusinessException`）；新增 `ImageCaptcha.of(length, width, height, gif)` 工厂；`getAlpha` 在 `length <= 1` 时返回 `1.0f`；`randomCaptcha()` 不再懒写字段。
- **Rationale**: 现状"读路径写字段"使共享实例互相污染（E-21），且框架文档无法用类型系统强制；"首次生成后冻结"用最小改动把可变窗口收敛到构造期，且不破坏既有 `setXxx` 链式调用者。
- **Alternatives considered**: ① 完全不可变 + 删除 setter（破坏 DOM 式配置 API）→ 否决；② 加 `synchronized`（只解决可见性，仍会让并发请求互相改写参数）→ 否决。

### D-13 Cookie 构建改用 `ResponseCookie`

- **Decision**: `GXAbstractSSOService.setCookie` 用 `ResponseCookie.from(name, value).path(...).domain(...).maxAge(...).secure(...).httpOnly(...).sameSite(...).build()` 并 `response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())`；`SameSite=None` 时强制 `secure=true`（否则拒绝并 ERROR 日志）；删除 `X-XSS-Protection` 头。
- **Rationale**: 手写头不转义、不校验（E-18），而 `ResponseCookie` 是 Spring Framework 自带能力（零新增依赖），自带属性校验与 `SameSite` 支持；`jakarta.servlet.http.Cookie` 不支持 `SameSite`，故必须用 `ResponseCookie`。
- **Alternatives considered**: ① 继续手写 + 自写转义（重复造轮子）→ 否决；② 只用 `Cookie` 放弃 `SameSite`（安全回退）→ 否决。

### D-14 性能：Bean 查找缓存 + IP 解析去热路径 + 日志分级

- **Decision**: ① `GXSSOHelperUtil` 把 `GXTokenConfigService`/`GXSSOCache`/`GXSSOAuthorization`/`JsonMapper` 的解析结果缓存到 `AtomicReference`，`setSsoConfig` 与 `@PostConstruct` 时失效重取；② `GXIpHelperUtil.getIpAddr` 的 localhost 分支直接返回静态 `LOCAL_IP`，不再每请求 `InetAddress.getLocalHost()`；`isLocalIp` 结果按主机名缓存；③ 未登录 INFO → DEBUG，并计数到 `GXSSOMetrics`；`GXBrowserUtil` 的缺失 UA 由 WARN 降 DEBUG。
- **Rationale**: 三项都是"每请求固定成本"，且不改变任何可观测语义（FR-011 只要求口径可复算）；Bean 查找缓存不改变依赖注入语义（仍由 Spring 管理生命周期，仅在 `setSsoConfig` 边界失效）。
- **Alternatives considered**: ① 缓存 Token 解析结果（有陈旧风险：`getEfficaciousToken` 可能依赖实时数据）→ 不做，只记录口径；② 改 `GXAuthCodeUtils` 的内部 MD5（跨模块）→ 不做，MD5 次数按现状记录为 4。

### D-15 可观测性：`GXSSOMetrics` + 结构化日志事件

- **Decision**: 新增 `GXSSOMetrics`（静态单例 + `LongAdder` 计数 + `record Snapshot(...)`，避免在静态入口每请求 `getBean`），暴露 6 项：`parseSuccess`、`parseFailure`、`cacheHit`、`cacheMiss`、`denied`、`loginFailure`（另附 `cacheFallback`、`concurrentLoginConflict`、`invalidToken`）；日志事件统一为 ASCII 键值对（`event=sso.login.success userId=..`），级别：登录成功 INFO、失败 WARN、降级/密钥缺失 ERROR、逐请求解析 DEBUG。
- **实施期修订（T076）**：`concurrentLoginConflict` 已随 D-04 的"后写优先 + 自包含"修订失去触发点，落地时删除；实际指标为 `parseSuccess` / `parseFailure` / `cacheHit` / `cacheMiss` / `denied` / `loginFailure` / `cacheFallback` / `invalidToken` / `activeTokens`（`activeTokens` 在 `GXSSOCache#set/delete` 后按 `GXBaseCacheService#size(bucket)` 刷新）。
- **Rationale**: FR-024 要求 6 项可获取口径且禁止高频 INFO；`LongAdder` 无锁、便宜，`record` 快照不可变，符合宪法 V 与 VII。
- **Alternatives considered**: ① Micrometer（新增依赖）→ 否决；② `AtomicLong`（高并发下热点）→ 次优；③ 只靠日志统计（无法在运行中读取）→ 否决。

### D-16 测试架构：最小 Spring 上下文 + 关闭 Nacos + 并发隔离

- **Decision**: 新增 `GXSSOAuthFlowSpringBootTest`（`@SpringBootTest(classes = GXSSOTestApplication.class, webEnvironment = RANDOM_PORT, properties = {"spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.discovery.enabled=false"})` + `MockMvc`），测试主类 `@SpringBootApplication(scanBasePackages = "cn.maple.sso")` 且提供 `GXSSOCache`/`GXTokenConfigService`/`GXSSOAuthorization`/`GXBaseCacheService` 的测试实现；并发类用 `@ResourceLock` + `@AfterEach` 复原 `GXSSOHelperUtil.setSsoConfig`；验证码渲染类直接调 `ImageCaptcha.of(...).generate(...)` 输出到 `ByteArrayOutputStream` 并断言 PNG/GIF 魔数。
- **Rationale**: R-14 表明 Nacos 自动配置会干扰启动；`ImageCaptcha` 的真实渲染路径（R-10）必须被执行才能覆盖 F-22；全局静态配置是既有测试的隐式共享状态，必须显式隔离才能满足 FR-010 的"可并行"要求。
- **Alternatives considered**: ① `@WebMvcTest`（不会加载 `GXWebMvcConfigurer` 的拦截器注册，无法验证 F-15）→ 否决；② 用 `ReflectionTestUtils` 注入拦截器（FR-023 禁止反射构造现场）→ 否决。

---

## 三、决策与发现的映射

| 决策 | 覆盖发现 | 对应 FR |
| --- | --- | --- |
| D-01 | F-03、F-05 | FR-007 |
| D-02 | F-04 | FR-007、FR-019 |
| D-03 | F-02 | FR-008 |
| D-04 | F-08 | FR-018 |
| D-05 | F-06、F-07 | FR-009 |
| D-06 | F-01、F-14（部分） | FR-006 |
| D-07 | F-15 | FR-006 |
| D-08 | F-14 | FR-006 |
| D-09 | F-11、F-12 | FR-005 |
| D-10 | F-13 | FR-005、FR-024 |
| D-11 | F-20 | FR-010 |
| D-12 | F-21、F-22、F-23、F-24 | FR-025 |
| D-13 | F-18 | FR-007 |
| D-14 | F-25、F-26、F-27、F-19（部分） | FR-011、FR-024 |
| D-15 | F-24（可观测）、F-30 | FR-024 |
| D-16 | F-35 | FR-020、FR-021、FR-022、FR-023 |

未在本计划中作为独立决策、但已列在 spec 且由批次覆盖的发现：F-09/F-10（D-09 的返回值幂等化）、F-16（插件失败语义）、F-17（会话固定防护）、F-19（IP fail-closed）、F-28 ~ F-37（日志/工具类/契约/文档/命名），分别落在 B2、B3、B5、B6 批次。
