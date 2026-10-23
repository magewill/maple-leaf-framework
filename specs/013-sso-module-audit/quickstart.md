# Quickstart：leaf-base-sso 审查结论的复现与验收

**Feature**: 013-sso-module-audit | **Date**: 2026-10-23
**用途**：让维护者在 15 分钟内复现任一审查结论（SC-001），并在修复后验证目标语义（SC-009）。
**前置条件**：Windows PowerShell / JDK 21+ / Maven 离线仓库可用；在仓库根目录执行所有命令。

---

## 1. 基线（修复前必须先跑）

```powershell
mvn -o -pl leaf-base-sso test
```

- 修复前期望：`Tests run: 40, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`；
- 修复后期望：`Tests run: 123, Failures: 0, Errors: 0, Skipped: 0`（40 → 123，只增不减）。
  偶发 `ClassFormatError: Method refreshActiveTokens ... illegal modifiers: 0x8` 系 IDE 增量编译产物所致，用 `mvn -o -pl leaf-base-sso clean test` 全量重建即可（见 `audit-report.md` §7.5）。
  该数字的唯一权威来源是 `audit-report.md` §验证记录；`tasks.md` 中各收敛轮次记录的是**当时值**（95 / 103），随批次递增，不得用于判定回归（T096）。

若数量少于对应基线，说明用例被跳过或环境不一致，先解决再继续（FR-012）。

按类运行（排查用）：

```powershell
mvn -o -pl leaf-base-sso test "-Dtest=GXSSOAuditReproTest,GXSSOCacheContractTest,GXSSOServiceBehaviorTest"
mvn -o -pl leaf-base-sso test "-Dtest=GXSSOAuthFlowSpringBootTest"      # 真实拦截链
mvn -o -pl leaf-base-sso test "-Dtest=GXSSOConcurrencyTest,CaptchaRenderTest"
mvn -o -pl leaf-base-sso test "-Dtest=GXSSOConfigSourceTest,GXSSOHelperUtilTest,GXSSOUtilsBehaviorTest"
```

## 2. 一条命令看清依赖与配置源真相

```powershell
mvn -o -pl leaf-base-sso dependency:tree "-Dincludes=com.alibaba.nacos"
```

期望输出含：`leaf-base-nacos:jar:4.3.0-SNAPSHOT:compile` → `nacos-client:jar:3.1.1:compile`。
结论：`@ConditionalOnMissingClass` 的 local 配置变体不可能生效（F-06 / R-01）。

```powershell
Get-ChildItem -Recurse -Filter "*.java" leaf-base-sso/src/main | Select-String "implements GXSSOCache"
```

期望：**无输出**（生产代码无缓存实现）→ 默认运行在"Cookie 即身份"模式（F-02 / R-02）。

## 3. 逐条复现高危结论

| 结论 | 复现方式 | 判定标准 |
| --- | --- | --- |
| F-01 URI 权限被默认值绕过 | 写一条临时用例：`GXSSOHelperUtil.setSsoConfig(new GXSSOProperties().setPermissionUri(true))`；构造 `permissionUri=true`、方法无 `@GXPermissionAnnotation`、`getAuthorization()` 恒返回 false，调用 `preHandle` | 现状返回 `true`（放行）；修复后返回 `false` 且响应 403 |
| F-02 默认无缓存 → 无服务端校验 | 不注册 `GXSSOCache`，用 `GXAuthCodeUtils.authCodeEncode` 手工构造一个含 `userId` 的 Cookie，发起受保护请求 | 现状：被判定为已登录；修复后：401 + 原因码 `cache-unavailable`（与是否开启降级无关） |
| F-03 默认密钥可伪造 | 用 `GXTokenConstant.TOKEN_SECRET_KEY` 作为密钥编码 `{"userId":1}`，作为 Cookie 发送 | 现状：通过校验；修复后：`prod` profile 启动失败，开发 profile 打 ERROR |
| F-04 Token 永不过期 | 默认配置签发 Token，`Thread.sleep` 后解析，或直接把 `cacheExpires` 设为 `-1` 检查编码前缀 | 现状：`0000000000` 前缀（永不过期）；修复后：默认 `7200` 秒有效期 |
| F-15 拦截器顺序依赖 bean 名 | 把 `GXSSOPermissionInterceptor` 的 bean 名改为 `aPermissionInterceptor` 后启动 | 现状：权限拦截器先执行；修复后：由 `@Order` 决定，不受 bean 名影响 |
| F-22 验证码 `length=1` 崩溃 | `ImageCaptcha.getInstance().setGif(true).setLength(1).generate(req, out, "t")` | 现状：抛 `IllegalArgumentException`；修复后：正常输出 1 帧 GIF |
| F-35 测试全局状态污染 | 在两个测试类中各自 `setSsoConfig`，开启 `junit.jupiter.execution.parallel.enabled=true` | 现状：随机失败；修复后：全部通过（`@ResourceLock` + `@AfterEach` 复原） |

> 复现用的临时用例在验证后必须删除或转为正式回归用例（FR-021：先失败、后通过）。

## 4. 修复后的验收场景

| 场景 | 操作 | 期望 |
| --- | --- | --- |
| 正常登录 | 调用 `setCookie`（`userId=7`，缓存可用） | 缓存有键、`Set-Cookie` 含 `SameSite=Lax; HttpOnly; Path=/`、`Max-Age` 与 `cookie-max-age` 一致 |
| 服务端校验 | 登录后手工把 Cookie 的 `loginAt` 改一位再发请求 | 401 + 原因码 `KICKED`，且 `denied` 指标 +1 |
| 登出幂等 | 连续两次 `clearLogin` | 两次都返回 `true`，第二次无 WARN |
| 缓存不可用 | 不注册 `GXSSOCache` 且不开启降级 | 登录抛 `GXBusinessException`，无 Cookie 下发 |
| 降级模式 | `cache-fallback-enabled: true` | 登录成功、TTL ≤ 900s、ERROR 日志 + `cacheFallback` 指标 |
| 插件拒绝 | `GXSSOPlugin.login` 返回 `false` | 登录失败、缓存已回滚、无 Cookie |
| 匿名请求零副作用 | 未登录访问受保护 URL，插件记录调用次数 | 插件 `logout` 调用次数 = 0，缓存无写操作 |
| URI 权限终局 | `permission-uri: true`、无注解、`isPermitted=false` | 403，不再放行 |
| 白名单 + context-path | `server.servlet.context-path=/api`，白名单配 `/public/**`，访问 `/api/public/x` | 放行 |
| 配置源切换 | `sso.config.source=local` + `classpath:dev/sso.yml` | 使用本地值（`loginUrl` 生效），日志 `source=local`；由 `GXSSOConfigSourceTest#sourceLocalBindsLocalBeanOnly` 锁定 |
| 验证码渲染 | `ImageCaptcha.of(4, 120, 48, false).generate(...)` | 输出以 PNG 魔数 `89 50 4E 47` 开头；GIF 以 `GIF89a` 开头；由 `CaptchaRenderTest` 锁定 |
| 注释缓存上限 | `sso.config.capacity=16` 后访问 100 个不同 handler | 缓存不整体清空、不抛异常；由 `GXSSOConcurrencyTest#concurrentAnnotationLookupIsStable` 覆盖 |
| 指标可读 | `GXSSOMetrics.snapshot()` | `parseSuccess`/`parseFailure`/`cacheHit`/`cacheMiss`/`denied`/`loginFailure` 等随操作单调变化 |

## 5. 构建与静态检查

```powershell
mvn -o -pl leaf-base-sso -am clean install -DskipTests
mvn -o -pl leaf-base-sso test
```

期望：ErrorProne + NullAway 零错误（构建失败即不合格，不得改编译参数）；测试全绿且用例数 **≥ 40**（只增不减）。

## 6. 交付物自检

- [ ] `specs/013-sso-module-audit/audit-report.md` 存在，37 条发现逐条含"级别/维度/位置/触发/影响/证据/复现/回归设计"
- [ ] `docs/leaf-base-sso开发手册.md` 已按实现重写（Token、配置键、拦截链、缓存实现要求、Safe Cookie）
- [ ] `leaf-base-sso/AGENTS.md` 已补"默认 fail-closed / 密钥红线 / 匿名零副作用"
- [ ] `leaf-base-sso/src/main/resources/META-INF/additional-spring-configuration-metadata.json` 已包含全部 `sso.config.*` 与 `url.white-lists`
- [ ] 新增 3 个配置键在手册与元数据中一致
- [ ] 既有 40 条断言一条未删（P-01 ~ P-10 覆盖的用例在"保留清单"中）

## 7. 常见误判

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 集成测试启动卡住/超时 | Nacos 自动配置在尝试连接 | 测试属性加 `spring.cloud.nacos.config.enabled=false`、`spring.cloud.nacos.discovery.enabled=false`（R-14） |
| 本地模式用例互相影响 | `GXSSOHelperUtil.setSsoConfig` 是全局静态 | `@AfterEach` 复原 + `@ResourceLock`（D-16） |
| 验证码用例一直是"假渲染" | 用了 `TestCaptcha` 覆盖 `writeImage` | 必须用 `ImageCaptcha` 真实渲染才能覆盖 F-22 |
| 白名单用例在本地通过、上线失效 | 用例未设 `context-path` | 用例必须覆盖带 context-path 的部署形态（D-08） |
