# 契约 1：登录态与 Token（sso-auth-contract）

**适用范围**：`GXSSOService` / `GXAbstractSSOService` / `GXSSOSupportService` / `GXSSOCache` / `GXTokenConfigService` / `GXSSOPlugin` / `GXUUserService` 的对外语义。
**稳定性**：本契约描述的是**修复后的目标语义**；带 `[变更]` 标记的条目属破坏性调整，需在 PR 与手册中同步声明。

## 1. 公开方法签名（只增不删）

| 类型 | 方法 | 状态 |
| --- | --- | --- |
| `GXSSOService` | `Dict getSSOToken(HttpServletRequest)` | 保留 |
| `GXSSOService` | `boolean kickLogin(Object userId)` | 保留（语义变更见 §5） |
| `GXSSOService` | `void setCookie(req, resp, Dict)` | 保留 |
| `GXSSOService` | `boolean clearLogin(req, resp)` | 保留 |
| `GXSSOService` | `void clearRedirectLogin(req, resp) throws IOException` | 保留 |
| `GXSSOService` | `default void logout(HttpServletRequest, HttpServletResponse) throws IOException` | **新增**（原仅在实现类，F-36） |
| `GXSSOService` | `default Dict attrSSOToken(HttpServletRequest)` | **新增**（原仅在 `GXSSOSupportService`，F-36） |
| `GXSSOCache` | `default Dict get(int expires, Dict requestToken)` | 保留（`[变更]` 不再抛异常，见 §4） |
| `GXSSOCache` | `default boolean set(Dict, int)` / `default boolean delete(Dict)` | 保留（`delete` 语义见 §5） |
| `GXSSOCache` | `default boolean verifyTokenConsistency(Dict, Dict)` | 保留（P-02 锁定） |

**不变式**：新增方法必须是 `default`（不破坏下游已有实现）；不得新增抽象方法。

## 2. Token 内容与序列化

| 字段 | 来源 | 必需 | 说明 |
| --- | --- | --- | --- |
| `userId` | 调用方写入 | 是（`>0`） | 缺失或 `<=0`：登录失败（`invalidToken`），校验视为未登录 |
| `loginAt` | `fillTokenValueIfNecessary` 写入（秒） | 是 | 唯一"新旧 Token"判据（P-02）；同一秒内两次登录视为同一代（残余风险，需在手册声明） |
| `createTime` | `setCookie` 写入（毫秒） | 是 | 便于观测 |
| `userAgent` | `GXBrowserUtil` 指纹 | 条件 | `cookieBrowser=true` 时必需（SHA-256）；兼容 5 位 MD5（P-05） |
| `ip` | `GXIpHelperUtil.getIpAddr` | 条件 | `cookieCheckIp=true` 时必需；解析为 null 时**拒绝**（`[变更]` fail-closed） |
| `flag` | `GXTokenFlag` | 否 | `CACHE_SHUT` 仅用于 `LOCAL_ONLY` |
| 其它业务字段 | 调用方 | 否 | 原样往返，不做白名单过滤（现状保持） |

**编码约束**：`authCodeEncode(payload, secret, ttl)` 输出 URL-safe Base64，不得含 `;`、`,`、空格、换行（保证可安全放入 Cookie 头）。`ttl<=0` 表示"不过期"，仅在显式配置 `cache-expires: -1` 时出现，且必须打 ERROR。

## 3. 签发（登录）语义

1. Cookie 名取 `cookie-name`（默认 `uid`），Token 字段名/Header 名取 `token-name`（默认 `token`）——两者**不是同一个配置**，禁止互相推断。
2. 签发顺序固定：合并既有 Token → 补 `loginAt`/`createTime`/`userAgent`/`ip` → 写缓存 → 写 Cookie → 对齐响应头 → 调用插件 `login`。
3. **插件 `login=false` 视为登录失败**（`[变更]`，FR-016）：必须回滚已写缓存（`delete`）并抛 `GXBusinessException`，不得下发 Cookie。
4. **缓存不可用且未开启降级** → 登录失败（`[变更]`，D-03），抛 `GXBusinessException`，不得下发"伪登录"Cookie。
5. **缓存不可用且已开启降级** → 写本地 Token：`flag=CACHE_SHUT`、TTL `min(cache-expires, 900)`、ERROR 日志 + `cacheFallback` 指标。
6. Cookie 属性：`Path`、`Domain`、`Max-Age`、`Secure`、`HttpOnly`、`SameSite` 必须经 `ResponseCookie` 构建（`[变更]`，D-13）；`SameSite=None` 且 `secure=false` 时拒绝构建并 ERROR。
7. 并发写：同一用户的并发登录采用"**后写优先 + 完整覆盖**"契约（`GXBaseCacheService` 无 CAS，无法原子合并）——每次写入的 Token 都必须自包含 `userId`/`loginAt`/`createTime`/`userAgent`/`ip`，因此后写不会产生字段残缺；由 `GXSSOConcurrencyTest#concurrentLoginsKeepMergedFields` 锁定。
8. 不得修改调用方传入的 `Dict`（P-01）。

## 4. 校验（每次请求）语义

1. 解析来源优先级：请求属性 `SSO_TOKEN_ATTR` → Header(`token-name`) → Cookie(`cookie-name`)。
2. 解析失败（非空但无效）返回空 `Dict` + `parseFailure`，不得抛异常（`[变更]`，D-09）。
3. 有缓存时以**缓存为准**：`cache.get` 命中 → `verifyTokenConsistency` 通过才可用；不通过 → `KICKED`（设置 `SSO_KICK_FLAG`）。
4. 缓存未命中 → `CACHE_MISS`（视为未登录），不得回落到 Cookie 自证（除非 `LOCAL_ONLY`）。
5. `cache == null` 且未开启降级 → `CACHE_UNAVAILABLE`；已开启 → `LOCAL_ONLY`（仅校验签名与过期）。
6. `isRPC()`（无 HTTP 上下文）→ 返回空 + 原因 `RPC_CONTEXT`，必须在 Javadoc 与手册中声明（F-12）。
7. 每请求必须记录 1 条 DEBUG（含来源与原因码），不得记录 token 内容（仅 `userId`/`loginAt`/`flag`）。

## 5. 登出 / 踢人语义

| 操作 | 缓存 | Cookie | 返回值 | 备注 |
| --- | --- | --- | --- | --- |
| `clearLogin` | 删除该键（幂等） | 清除 | 缓存删除成功 **或** 键本就不存在 → `true` | `[变更]` 幂等（F-10） |
| `logout` | 同上 | 同上 | 同上 | 之后按 `logout-url` 跳转；`logout-url` 为空 → 401 JSON 或明确提示 |
| `kickLogin(userId)` | 删除该用户键（幂等） | 不涉及 | `userId` 非法 → `false`（`[变更]`，不抛异常） | 无缓存 → `false` + WARN |

**不变式**：`delete`/`kickLogin` 不得因"键不存在"而报失败（重复登出是合法操作）；`plugin.logout` 只在**已登录**的显式登出路径调用（`token` 为空时 `logout` 跳过缓存删除与插件循环，仅做幂等 Cookie 清理），未登录请求不得触发（`[变更]`，F-13）。

## 6. 空值契约（JSpecify）

| 位置 | 修正 |
| --- | --- |
| `kickLogin(Object userId)` | 参数 `@Nullable`；`null` → `false` |
| `GXSSOCache.get` 返回值 | 永不为 `null`（返回空 `Dict`） |
| `GXSSOHelperUtil.parser` | 返回值 `@NonNull`（失败返回空 `Dict`） |
| `GXBrowserUtil.getUserAgent` | 返回 `@Nullable` |
| `GXSSOSupportService.attrSSOToken` | 返回 `@NonNull`（无属性时返回空 `Dict`，不再让 `Convert` 产出 null） |
| `GXLoginUserHandlerMethodArgumentResolver.resolveArgument` | 未认证（无 userId）返回 `null`；已认证但用户不存在抛 `GXBusinessException` |
| `ICaptcha.verification(request, ticket, captcha)` | `captcha` 参数 `@Nullable`，`null` → `false` 且**不删除** ticket（`[变更]`，F-23） |

## 7. 兼容性条目汇总

| 变更 | 触发条件 | 迁移 |
| --- | --- | --- |
| 缓存缺失默认失败 | 未实现 `GXSSOCache` 的下游 | 实现 `GXSSOCache` 或 `cache-fallback-enabled: true` |
| 插件 `login=false` 阻断登录 | 使用"软拒绝"插件的下游 | 插件改为 `true`；需拒绝时用 `validateToken` |
| `get` 不抛异常 | 依赖 `GXTokenInvalidException` 的下游 | 读请求属性原因码 / 指标 |
| `delete`/`kickLogin` 幂等返回布尔 | 依赖异常做校验的下游 | 判断返回值 |
| 未登录不再触发插件 `logout` | 依赖隐式清场的下游 | 显式调用 `logout` |
| `ip` 解析为 null 时拒绝 | 反向代理未配置真实 IP 的下游 | 配置 `trust-forwarded-ip-headers: true` 并确保代理写入 XFF |
