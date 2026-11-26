# Implementation Plan: leaf-base-sso 正确性、线程安全与性能审查

**Branch**: `013-sso-module-audit`（`before_plan` 未注册 hook，未创建 git 分支） | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-sso-module-audit/spec.md`

## Summary

把 37 条审查发现（高 4 / 中 19 / 低 14）与 10 条既定契约（P-01 ~ P-10）落成"可实测、可回归、可迁移"的交付：**审查报告 + 权限判定闭环 + 登录态安全默认值 + 缓存/装配显式化 + 并发与实例安全 + 性能口径与可观测性 + 契约与文档重写 + 测试补强**。

技术路径（Phase 0 已逐条确认，详见 [research.md](./research.md)）：

1. **权限判定闭环**（F-01、F-14，FR-006）：把 `GXSSOPermissionInterceptor` 的判定重构为**唯一优先级链**（`@GXIgnoreLoginIntercept` → `@GXPermissionAnnotation(Skip)` → URI 模式 → 注解模式 → `nothingAnnotationPass`），URI 模式一旦启用即**自行决断**，不再回落到 `nothingAnnotationPass`；`uri == null` 按 fail-closed 处理；白名单改用 `UrlPathHelper` 剥离 context-path 后再匹配。
2. **安全默认值**（F-02、F-03、F-04、F-05，FR-007、FR-008）：`GXSSOProperties` 删除 RSA 口令与 `signKey` 硬编码（改为 `null` + 启动校验）；默认 Token 密钥缺失时**启动失败**（`production`/`prod` profile）或 ERROR 告警；`cacheExpires` 默认值由 `-1`（永不过期）改为 `7200` 秒，显式配 `-1` 仍保留旧行为但打 ERROR；新增 `cacheFallbackEnabled`（默认 `false`），缓存不可用时**默认登录失败**。
3. **登录态语义统一**（F-09、F-10、F-11、F-13，FR-005）：`GXSSOCache.get` 不再抛 `GXTokenInvalidException`（改为返回空 `Dict` + 原因码请求属性），`delete`/`kickLogin` 改为幂等并返回布尔，未登录请求不再产生缓存删除与插件副作用。
4. **并发安全**（F-08、F-20、F-21、FR-010、FR-018）：登录写 Cookie 收敛为"**后写优先 + 每次写入自包含**"契约（`GXBaseCacheService` 无 CAS，回读重试会破坏既有用例，见 research D-04 实施期修订）；注解缓存由"存 `Annotation` 实例 + 超限全清"改为"存判定结果 + 键为字符串 + FIFO 淘汰"；验证码实例改为**首次渲染后冻结**，`getAlpha` 除零修正。
5. **性能与可观测**（F-25、F-26、F-27，FR-011、FR-024）：`getBean` 结果按配置生命周期缓存到 `AtomicReference`（`setSsoConfig` 时失效）；`GXIpHelperUtil` 去掉热路径的 `InetAddress.getLocalHost()`；新增 `GXSSOMetrics`（`LongAdder` + `record` 快照）提供 **9 项指标**（`parseSuccess`/`parseFailure`/`cacheHit`/`cacheMiss`/`denied`/`loginFailure`/`cacheFallback`/`invalidToken`/`activeTokens`），性能口径另列 6 项（MD5/RC4/JSON/`getBean`/日志/`InetAddress`）；未登录 INFO 降为 DEBUG、按原因计数。
6. **契约与文档**（F-32、F-33、F-34、F-36、F-37，FR-013、FR-016）：新增 `META-INF/additional-spring-configuration-metadata.json`；`@Nullable` 收敛；接口补齐 `logout`/`attrSSOToken`；GIF 编码器补来源与许可声明；手册第 3.5/4/5/6.2/7 节按实现重写。
7. **测试补强**（F-35，FR-020 ~ FR-023）：新增 7 个测试类（真实拦截链 `@SpringBootTest`、并发、缓存契约、配置装配、验证码真实渲染、`GXSSOHelperUtil`、审查复现 `GXSSOAuditReproTest`）+ 3 个测试支撑类（`GXSSOTestApplication`、`GXSSOTestDoubles`、`GXHandlerMethodAnnotationUtilsTestAccess`），修改 5 个既有测试类；既有 40 条断言全部保留。**实施期修订**：用例总数 40 → 123（见 T096）。

## Technical Context

**Language/Version**: Java 21（根 POM `<java.version>21</java.version>`、`maven-compiler-plugin` `<release>21</release>`；ErrorProne + `-Xep:NullAway:ERROR -XepOpt:NullAway:AnnotatedPackages=cn.maple -XepOpt:NullAway:JSpecifyMode=true`）；Windows 本机实测可离线编译与测试

**Primary Dependencies**: Spring Boot 4.1.1 / Spring Framework 7.0.9（`ResponseCookie`、`AntPathMatcher`、`UrlPathHelper`、`AnnotatedElementUtils`、`MockMvc`，`spring-boot-starter-web` 经 `leaf-base-framework` 传递）；`leaf-base-framework 4.3.0-SNAPSHOT`（`GXAuthCodeUtils`、`GXCookieHelperUtil`、`GXSpringContextUtils`、`GXCurrentRequestContextUtils`、`GXBusinessException`、`GXBaseCacheService`、`GXResultUtils`；Caffeine 经其传递可用）；`leaf-base-nacos 4.3.0-SNAPSHOT`（传递 `nacos-client 3.1.1`，compile）；Hutool 5.8.x（`Dict`/`JSONUtil`/`CharSequenceUtil`/`SecureUtil`）；jspecify 1.0.1；Lombok。**零新增依赖**

**Storage**: N/A（库模块自身不持久化）；登录态存于下游提供的 `GXBaseCacheService`（内存/Redis 均可不感知）；验证码存 `HttpSession`

**Testing**: JUnit 5（Jupiter）+ `spring-boot-starter-test`；`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `MockMvc` 验证真实拦截链与自动注册；并发用 `ExecutorService` + `CountDownLatch`；缓存/插件/用户服务全部用测试实现（`GXSSOCache`、`GXTokenConfigService`、`GXSSOPlugin`、`GXUUserService`、`GXBaseCacheService`），不依赖 Redis/Nacos；**必须** `spring.cloud.nacos.config.enabled=false`、`spring.cloud.nacos.discovery.enabled=false` 避免 Nacos 自动配置发起连接

**Target Platform**: JDK 21 库（jar，随 `leaf-framework` 聚合门面下发）；Windows（CRLF）与 Linux（LF）行为一致

**Project Type**: library（Maven 多模块仓库中的单个 `leaf-base-*` 模块）

**Performance Goals**: 不设吞吐/时延验收门禁（SC-005），只收敛可复算口径：每请求 `getBean` 次数 **3 → 0**（预热后）、每请求未登录 INFO 日志 **1 → 0**、热路径 `InetAddress.getLocalHost()` **1 → 0**、每请求 MD5 次数保持 4（上游 `GXAuthCodeUtils` 不改，仅记录口径）、注解解析未命中时的反射调用次数 ≤ 1 且无整体清空。**工程目标（非门禁）**：既有 40 条用例墙钟 ≤ 5s，单类 ≤ 3s。**实施期修订**：原定"新增用例总数 ≤ 30"已被推翻——实际新增 83 条（40 → 123），原因是 FR-020/FR-021 对每个修复项强制"成功/失败/异常/空值/边界 + 并发 + 真实 Spring Boot 链"五类用例；该目标不再作为门禁（T092）。

**Constraints**: 只改 `leaf-base-sso` 模块 + **1 处框架级改动（约 22 行）**（`GXWebMvcConfigurer` 拦截器排序 + null-safe 顺序解析，见 Complexity Tracking）+ 2 份文档（`docs/leaf-base-sso开发手册.md`、`leaf-base-sso/AGENTS.md`）；零新增依赖；默认值按 FR-019 保持（仅 `cacheExpires` 默认值变更，属安全强制修正）；ErrorProne + NullAway 零错误；日志与断言消息 ASCII

**Scale/Scope**: 62 个生产源码文件（审查覆盖 100%）；37 条发现；修改 24 个生产文件、新增 2 个生产类型（`GXSSOMetrics`、`GXSSOTokenStateEnums`）+ 1 个 `captcha/package-info.java` + 在既有 `GXSSOConstant` 内新增 2 个顺序常量（原定第 4 个类型 `GXSSOCaptchaValidator` 实施期并入 `AbstractCaptcha`/`CaptchaStoreSession`，见 T094；原计划新建的 `GXSSOInterceptorOrder` 已按宪法 VII 归并，见 T102）；新增 7 个测试类 + 3 个测试支撑类、修改 5 个测试类（`CaptchaBehaviorTest` 不改）；新增 1 个资源文件（配置元数据）；框架侧 1 个文件 5 行；重写 1 份手册 + 修改 `AGENTS.md`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 依据 / 约束 |
| --- | --- | --- |
| I. 模块自治与按需装配 | PASS（附说明） | 仍只依赖 `leaf-base-framework` + `leaf-base-nacos`；不新增中间件依赖；新增能力用 JDK/Spring 既有 API（`ResponseCookie`、Caffeine 已传递）；不迁自动装配清单（偏差与既有模块一致） |
| II. 稳定契约优先（NON-NEGOTIABLE） | PASS（附兼容声明） | 公开方法**只增不删**（接口补 `default logout`/`attrSSOToken`）；配置键全部保留，新增键只增不改；**两处行为变更**必须显式声明：① `cacheExpires` 默认值 `-1 → 7200`；② `GXSSOCache.get` 由"抛异常"改为"返回空 + 原因码"。逐条见下方兼容性声明表 |
| III. 统一响应与统一异常 | PASS（附边界说明） | 未登录响应改用 `GXResultUtils.error(...)`（替换手工 `Dict` + `getWriter()`）；权限拒绝仍用 403，语义不变；`@GXLoginUserAnnotation` 解析失败改为抛 `GXBusinessException`（不再返回 null）；不新增异常类型。**边界说明**：`GXSSOCache.get/delete/kickLogin` 属"内部判定型 API"（返回空 `Dict`/布尔表示判定结果），不产出对外响应，仍由拦截器统一映射为 `GXResultUtils`，不违反 III；该边界已由 T093 登记，`AGENTS.md` §3 的对应红线见 T063 |
| IV. 显式配置与双配置源 | **PASS（经宪法 v1.1.0 修订，T102）** | 新增 3 个配置键（`sso.config.source`、`sso.config.cache-fallback-enabled`、`sso.config.capacity`）并同步 `additional-spring-configuration-metadata.json`；`autoRefreshed` 两处统一为 `true`（F-07）；敏感值改为 `${ENV:}` 注入或启动失败（F-05）。**冲突已消解**：宪法 v1.1.0 §IV 将"显式开关"列为类路径条件不可达时的等价互斥方式，并附四条护栏（默认值保持既有行为、启动 INFO 打印生效源、互斥与默认值测试、元数据与手册登记）——本实现的 `@ConditionalOnProperty(sso.config.source, matchIfMissing=true)` 与 `GXSSOConfigSourceTest` 逐条满足；宪法同版澄清"单模块内部开关沿用模块既有前缀"，故 `cacheFallbackEnabled` 位于 `sso.config.*` 合规 |
| V. 空值安全与上下文纪律 | PASS | 可空返回/入参补 `@Nullable`（FR-016）；`ThreadLocal` 未新增；新增的指标计数器为无状态 `LongAdder`；`GXSSOProperties` 无资源需要释放；验证码 `ByteArrayOutputStream` 仍 try-with-resources |
| VI. 测试与静态检查门槛 | PASS | 每个修复项先给"旧代码失败"用例（FR-021）；新增 `@SpringBootTest` 真实拦截链用例（FR-020）；并发/配置装配/验证码渲染各 1 类；不改任何编译参数；`mvn -pl leaf-base-sso test` 须全绿且用例数从 40 单调增长 |
| VII. 命名与包结构一致性 | **PASS（已按 T102 收敛）** | 新增 `GXSSOMetrics`（`GX<概念>` 快照类型）、`GXSSOTokenStateEnums`（枚举，符合 `GX<概念>Enums`）；顺序常量 `AUTHORIZATION_ORDER`/`PERMISSION_ORDER` 并入既有 `GXSSOConstant`（符合 `GX<范围>Constant`），原 `GXSSOInterceptorOrder` 已删除；`GX` 前缀、不重命名历史类型、日志 ASCII 均合规。`Validator` 后缀由宪法 v1.1.0 §VII 新增角色认可（与仓库既有 `GXRocketMQConfigSourceValidator` 等 3 个类型对齐），本特性实际未新增该类型 |
| 附加约束 2/3（依赖治理） | PASS | 零新增依赖：`ResponseCookie`/`AntPathMatcher`/`UrlPathHelper` 来自已依赖的 spring-web，缓存上限用已传递的 Caffeine；不引入 JWT 库或新密码学实现 |

**Post-Phase 1 复评**：`contracts/` 三份契约（登录态与 Token、权限判定、配置与可观测）与 `data-model.md` 未引入新的原则冲突；`GXSSOMetrics`/`GXSSOTokenStateEnums` 的必要性与 `GXWebMvcConfigurer` 跨模块改动已在 Complexity Tracking 说明。**Post-Phase 2 复评（2026-10-23，`/speckit.converge` 第五轮）**：原判 IV / VII 为 CONDITIONAL（配置源装配机制、新增类型后缀与宪法文本冲突）。**T102 已裁决并消解**：IV 走"宪法修订"（`.specify/memory/constitution.md` v1.1.0，MINOR，见其 §修订记录）；VII 走"实现回退"（`GXSSOTokenState` → `GXSSOTokenStateEnums`、`GXSSOInterceptorOrder` 常量并入 `GXSSOConstant` 并删除该类）。复评结论：**全部原则 PASS**；消解后 `mvn -o -pl leaf-base-sso clean install -DskipTests` 与 `mvn -o -pl leaf-base-sso clean test` 均通过（123 用例 / 0 失败）。**第六轮收敛（T104–T109）追加的决策记录**：`GXSSOProperties` 的"冻结标记"经评估不实施，共享配置的保护手段收敛为"`GXSSOHelperUtil#setSsoConfig` 唯一发布入口 + 请求路径只读"（理由见 `data-model.md` §7 注记）；`GXLoginUserHandlerMethodArgumentResolver` 的失败语义统一为抛 `GXBusinessException`（不再返回 `null`），并同步手册 §3.4。

**兼容性声明（非违反项，需在 PR 与手册中体现）**：

| 变更 | 影响面 | 迁移路径 |
| --- | --- | --- |
| `cacheExpires` 默认值 `-1`（永不过期）→ `7200` 秒 | 依赖"默认永不过期"的下游（安全上不可接受） | 显式配置 `sso.config.cache-expires: -1` 可恢复旧行为（启动打 ERROR 日志）；建议配合 `cookie-max-age` 调整客户端会话时长 |
| 缓存不可用时默认登录失败（`cacheFallbackEnabled=false`） | 未实现 `GXSSOCache` 的下游（**默认全部下游**） | 实现 `GXSSOCache`（推荐），或显式 `sso.config.cache-fallback-enabled: true` 使用短过期本地 Token（ERROR 日志 + 指标可观测） |
| `GXSSOCache.get` 不再抛 `GXTokenInvalidException` | 依赖该异常做全局异常映射的下游 | 改为读 `request` 属性 `sso_token_state`（新增枚举）或 `GXSSOMetrics`；显式 API 场景仍可抛 |
| `delete` / `kickLogin` 对 `userId<=0` 返回 `false` 而非抛异常 | 依赖异常做参数校验的下游 | 判断返回值 + `GXSSOMetrics` 的 `invalidToken` 计数 |
| 未登录请求不再触发插件 `logout` 与缓存删除 | 依赖"未登录即清场"这一副作用的下游（该副作用从未被文档承诺） | 显式调用 `clearLogin` / `logout` |
| 登录插件 `login=false` 由"告警继续"改为"登录失败" | 实现了"软拒绝"语义插件的下游 | 插件返回 `true` 并在内部记录；需要软拒绝时改用 `validateToken` |
| 拦截器注册改为按 `@Order` 排序（框架约 22 行） | 其它模块自定义 `GXAuthorizationInterceptor` 的执行顺序 | 未标注 `@Order` 的拦截器沿用 bean 名字典序（`Ordered.LOWEST_PRECEDENCE` 稳定排序） |
| 未登录 AJAX 响应体由手工 `Dict` 改为 `GXResultUtils.error` | 前端按旧字段名解析的下游 | 字段名保持 `code`/`msg`/`data`，JSON 结构不变，仅构造方式统一 |
| `@GXLoginUserAnnotation` 解析失败抛 `GXBusinessException` | 依赖 `null` 入参做匿名逻辑的控制器 | 改用 `@GXIgnoreLoginIntercept` + 显式判空，或捕获业务异常 |
| `ImageCaptcha.getInstance()` 返回构造期定型的不可变实例 | 先取实例再 `setLength()` 等链式配置的下游 | 改用 `ImageCaptcha.of(length, width, height, gif)`；`@Data` setter 保留但仅在未使用前生效（文档声明） |

## Project Structure

### Documentation (this feature)

```text
specs/013-sso-module-audit/
├── plan.md                          # 本文件
├── research.md                      # Phase 0：D-01 ~ D-16 决策 + R-01 ~ R-14 事实核对
├── data-model.md                    # Phase 1：发现/证据/终止矩阵/权限矩阵/配置契约/指标/测试矩阵
├── quickstart.md                    # Phase 1：可执行验证指南（含"旧代码失败"复现步骤）
├── contracts/
│   ├── sso-auth-contract.md         # 登录态与 Token 契约（签发/过期/校验/登出/踢人/降级/返回值）
│   ├── sso-authorization-contract.md# 权限判定优先级链、白名单、拦截链顺序、未登录响应
│   └── sso-config-observability-contract.md # 配置键表、生效时机、指标与日志事件、兼容声明
├── audit-report.md                  # 实施期产出：审查报告（与 spec 摘要互引，FR-014）
└── checklists/requirements.md       # 规格质量清单（16/16 通过）
```

### Source Code (repository root)

```text
leaf-base-sso/
├── pom.xml                                                       # 不改（零新增依赖）
└── src/
    ├── main/java/cn/maple/sso/
    │   ├── cache/
    │   │   └── GXSSOCache.java                                   # 改：get 不抛异常+原因码、delete/kickLogin 幂等布尔、set 显式 TTL、切面口径注释
    │   ├── captcha/
    │   │   ├── AbstractCaptcha.java                              # 改：构造期定型、字段 final、getAlpha 除零修正、verification(null) 不删除
    │   │   ├── ImageCaptcha.java                                 # 改：of(...) 工厂、length 下限校验、渲染失败语义
    │   │   ├── (GXSSOCaptchaValidator.java)                       # 实施期修订：未单独建类型，校验语义内联于 AbstractCaptcha.verification + CaptchaStoreSession.consume（T094）
    │   │   ├── Encoder.java / GifEncoder.java / Quant.java       # 改：补来源与许可声明（不重构算法）
    │   │   └── CaptchaStoreSession.java                          # 改：session 创建延迟到 put、并发消费保护
    │   ├── properties/
    │   │   ├── GXSSOProperties.java                              # 改：删除硬编码口令/signKey、cacheExpires 默认 7200、cacheFallbackEnabled、tokenTtlSeconds、source、@Nullable
    │   │   ├── local/GXLocalSSOConfigProperties.java             # 改：条件由"缺类"改为 sso.config.source=local
    │   │   ├── local/GXLocalUrlWhiteListsConfigProperties.java   # 改：同上 + ignoreResourceNotFound=true + ERROR 日志
    │   │   ├── nacos/GXNacosSSOConfigProperties.java             # 改：autoRefreshed=true + 条件 sso.config.source=nacos(matchIfMissing)
    │   │   └── nacos/GXNacosUrlWhiteListsConfigProperties.java   # 改：条件统一
    │   ├── service/
    │   │   ├── GXSSOService.java                                 # 改：补 default logout/attrSSOToken、@Nullable
    │   │   ├── GXSSOSupportService.java                          # 改：checkIpBrowser fail-closed、generateCookie 去强转、logout 返回值语义、@Nullable
    │   │   ├── GXAbstractSSOService.java                         # 改：ResponseCookie 构建、插件失败即失败、authCookie 不再丢会话、clearRedirectLogin 统一 GXResultUtils
    │   │   ├── GXTokenConfigService.java                         # 改：默认密钥启动校验、tokenCacheKey 纳入 platform、缓存键前缀统一
    │   │   └── GXSSOServiceImpl? (无新增实现)                     # 保持 GXConfigurableAbstractSSOServiceImpl
    │   ├── enums/
    │   │   └── GXSSOTokenStateEnums.java                              # 新增：ABSENT/CACHE_MISS/EXPIRED/KICKED/CACHE_UNAVAILABLE/PLUGIN_REJECTED
    │   ├── metrics/
    │   │   └── GXSSOMetrics.java                                 # 新增：LongAdder 六项口径 + record 快照
    │   ├── constant/
    │   │   └── GXSSOConstant.java                        # 改：新增 AUTHORIZATION_ORDER=100 / PERMISSION_ORDER=200（吸收原 GXSSOInterceptorOrder，T102）
    │   ├── utils/
    │   │   ├── GXSSOHelperUtil.java                              # 改：bean 引用缓存、parser 空值契约、日志级别、指标埋点
    │   │   ├── GXIpHelperUtil.java                               # 改：去掉热路径 getLocalHost、isLocalIp 缓存
    │   │   └── GXHttpUtil.java                                   # 改：isAjax 兼容 X-RESPONSE-TYPE、inContainURL 改为分段匹配、requestPlayload 上限与字符集
    │   └── web/
    │       ├── interceptor/GXSSOPermissionInterceptor.java       # 改：优先级链重构、@Order、白名单 context-path、URI 模式决断
    │       ├── interceptor/GXSSOAuthorizationInterceptor.java    # 改：@Order、匿名请求零副作用、白名单 context-path、GXResultUtils 响应
    │       ├── interceptor/GXHandlerMethodAnnotationUtils.java   # 改：缓存判定结果 + 字符串键 + FIFO 淘汰
    │       ├── handler/GXSSODefaultHandler.java                  # 改：GXResultUtils、@Nullable
    │       └── support/GXLoginUserHandlerMethodArgumentResolver.java # 改：失败抛业务异常、用户缓存钩子、@Nullable
    ├── main/resources/META-INF/
    │   └── additional-spring-configuration-metadata.json          # 新增：sso.* 与 url.* 全部键 + 新增 3 键
    └── test/java/cn/maple/sso/
        ├── service/GXSSOServiceBehaviorTest.java                  # 改：保留 15 条 + 过期/降级/幂等登出
        ├── service/GXSSOAuthFlowSpringBootTest.java               # 新增：真实拦截链 + 登录→校验→登出 端到端
        ├── service/GXSSOConcurrencyTest.java                      # 新增：并发登录覆盖、并发注解解析、并发踢人
        ├── cache/GXSSOCacheContractTest.java                      # 新增：get 不抛异常、delete 幂等、TTL 传参
        ├── properties/GXSSOConfigSourceTest.java                  # 新增：source 开关互斥、默认值、口令校验
        ├── captcha/CaptchaRenderTest.java                         # 新增：ImageCaptcha 真实渲染（PNG/GIF/length=1）
        ├── web/interceptor/GXSSOAuditReproTest.java               # 新增：F-01/F-02/F-03/F-04/F-15 旧代码复现（T013~T017）
        ├── support/GXSSOTestApplication.java                      # 新增：测试用 @SpringBootApplication（关闭 Nacos 自动配置）
        ├── support/GXSSOTestDoubles.java                          # 新增：缓存/插件/用户服务/授权器测试替身（FR-023）
        ├── web/interceptor/GXHandlerMethodAnnotationUtilsTestAccess.java # 新增：注解缓存的包内测试通道（不反射）
        ├── web/interceptor/GXSSOInterceptorBehaviorTest.java      # 改：保留 9 条 + 权限矩阵 16 组合
        ├── web/support/GXLoginUserHandlerMethodArgumentResolverTest.java # 改：保留 2 条 + 失败抛异常
        ├── utils/GXSSOUtilsBehaviorTest.java                      # 改：保留 6 条 + 白名单/URL/请求体
        ├── utils/GXSSOHelperUtilTest.java                         # 新增：bean 缓存失效、parser 空值、指标
        └── enums/GXEnumBehaviorTest.java                          # 改：保留 3 条 + GXSSOTokenStateEnums

leaf-base-framework/src/main/java/cn/maple/core/framework/web/config/GXWebMvcConfigurer.java # 改（约 22 行）：按 @Order/Ordered 排序后注册（null-safe resolveOrder）

docs/leaf-base-sso开发手册.md                                      # 重写 3.5/4/5/6.2/7：Token 真相、配置键表、拦截链、缓存实现要求
leaf-base-sso/AGENTS.md                                            # 改：§3 补"默认 fail-closed/密钥红线/匿名零副作用"，§4 补测试清单
```

**Structure Decision**: 保持"单模块 + 包内分层"现状，不新增子工程；生产侧新增 2 个类型（`GXSSOMetrics` 对外快照、`GXSSOTokenStateEnums` 原因码）+ 在既有 `GXSSOConstant` 内新增 2 个顺序常量，修改 24 个既有类型，新增 1 个资源文件（配置元数据）+ 1 个 `captcha/package-info.java`；测试侧新增 7 个测试类 + 3 个测试支撑类、修改 5 个测试类；文档改 2 份；**框架侧仅一处排序改动（约 22 行，含 null-safe 顺序解析）**。`pom.xml`、根 POM、其它模块不改。（原定第 4 个生产类型 `GXSSOCaptchaValidator` 未单独落地，见 T094 的实施期修订记录。）

## Phase 0 → Phase 1 结论摘要

- **R-01（配置源实际只有 Nacos）**：`mvn -o -pl leaf-base-sso dependency:tree -Dincludes=com.alibaba.nacos` 实测：`leaf-base-nacos:compile` → `nacos-client:3.1.1:compile`。因此 `@ConditionalOnMissingClass("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties")` 恒为假，local 变体**永不生效**；`config/<profile>/sso.yml` 从不被加载（F-06 成立）。
- **R-02（默认没有缓存实现）**：全仓检索 `implements GXSSOCache` 仅命中测试内匿名类，**无生产实现**；`GXSpringContextUtils.getBean` 对缺失 Bean 返回 `null` 且不抛异常（第 45–62 行）。因此"无缓存 → 无服务端校验"是**默认路径**而非异常路径（F-02 由"降级风险"升级为"默认不安全"）。
- **R-03（Token 过期语义）**：`GXAuthCodeUtils.authCode` 在 `expiry<=0` 时写入 `0000000000`，解码侧 `result.indexOf("0000000000")==0` 视为永久有效（第 141、147 行）；编码输出 URL-safe Base64、解码失败统一返回 `{}`（P-09）。
- **R-04（默认密钥是公开常量）**：`GXTokenConstant.TOKEN_SECRET_KEY = "31D0D393D3EAF5"`（第 30 行），`GXTokenConfigService` 默认返回它并只 WARN 一次（第 17–22 行）；RC4 + MD5 为对称可逆编码 → 默认配置下 Token 可离线伪造（F-03 成立）。
- **R-05（会话固定防护的真实效果）**：`GXCookieHelperUtil.authJSESSIONID` 仅 `request.getSession().invalidate()` + `setAttribute("MAPLE-"+value, true)`（第 18–29 行）；容器会因 `invalidate` 生成新会话（该点有效），但随机串不参与会话标识，且 `invalidate` 会丢弃 `CaptchaStoreSession` 依赖的会话数据（F-17 成立）。
- **R-06（IP 口径两套）**：`GXCurrentRequestContextUtils.getClientIP(request)` 无条件遍历 `X-Forwarded-For`/`CF-Connecting-IP`/`X-Real-IP` 等头（第 304–341 行）；`GXIpHelperUtil.getIpAddr` 受 `trustForwardedIpHeaders` 控制（默认 false）。`GXSSOHelperUtil.parser` 用前者写入 token 的 `ip`，`checkIpBrowser` 用后者比对 → 口径不一致（F-19 成立）。
- **R-07（拦截器顺序来源）**：`GXWebMvcConfigurer.addInterceptors` 用 `authorizationInterceptors.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))` 注册（第 38–47 行），**顺序 = bean 名字典序**，无 `order()`；手册第 497 行的强制顺序要求仅靠字母序巧合成立（F-15 成立）。
- **R-08（缓存 API 无 CAS）**：`GXBaseCacheService` 提供 `setCache`/`getCache`/`deleteCache`/`updateCacheExpiredTime`，无 `putIfAbsent`/CAS → 并发登录的原子性只能用"写后回读校验 + 一次重试"实现（D-03 的依据）。
- **R-09（模块无资源目录）**：`leaf-base-sso/src/main/resources` 不存在；仓库内已有 8 个模块提供 `META-INF/additional-spring-configuration-metadata.json`（framework/datasource/feign/...）→ 本模块缺失属偏差而非约定（F-32 成立，修复有现成范式）。
- **R-10（验证码渲染路径无测试）**：`ImageCaptcha.writeImage` 在任何测试中均未被执行（`CaptchaBehaviorTest` 用 `TestCaptcha` 覆盖 `writeImage`），`AlphaComposite.getInstance(SRC_OVER, NaN)` 会抛 `IllegalArgumentException`（F-22 因此不被发现）。
- **R-11（既有基线）**：`mvn -o -pl leaf-base-sso test` = 40 执行 / 0 失败 / BUILD SUCCESS（6 个测试类）。
- **R-12（框架侧 5 行改动的可行性）**：`GXWebMvcConfigurer` 是 `@Configuration`，`addInterceptors` 已收集全部 `GXAuthorizationInterceptor`；改为 `sorted(Comparator.comparing(AnnotationAwareOrderComparator::getOrder))` 即可支持 `@Order`，未标注者沿用原字典序（稳定排序），零行为回归。
- **R-13（构建门槛）**：根 POM `release=21`、ErrorProne + NullAway `ERROR`、`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`；任何新增可空 API 必须标注，否则构建失败。
- **R-14（测试上下文约束）**：`spring-boot-starter-web` 经 framework 传递（`pom.xml` 第 15–18 行），`@SpringBootTest(RANDOM_PORT)` 可用；但 `spring-cloud-starter-alibaba-nacos-config` 与 `nacos-client` 同在类路径，测试必须显式关闭 Nacos 自动配置，否则启动期尝试连接。
- 其余设计决策（密钥处置、默认过期、降级开关、权限链、配置源开关、并发写、缓存淘汰、验证码定型、Cookie 构建、指标、文档）见 [research.md](./research.md)。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| 跨模块改 `GXWebMvcConfigurer`（约 22 行，加 null-safe 的 `@Order` 排序） | FR-006/FR-015 要求拦截链顺序**可保证**；`InterceptorRegistry` 的注册顺序即执行顺序，而顺序由该框架类决定，本模块无法覆盖 | 在 sso 模块内合并两个拦截器为一个会破坏公开类型与"登录链/权限链分离"的语义（AGENTS §3.3）；自建 `WebMvcConfigurer` 会与框架重复注册同一实例（`registry.addInterceptor` 去重不可靠，且 `addPathPatterns` 不同会二次执行） |
| 新增 `GXSSOMetrics` 类型 | FR-024 要求 6 项可获取口径，且禁止用高频 INFO 承载 | 返回 `Map<String, Long>` 违反宪法 III 的"禁止裸 Map"风格；在 6 个类里各加计数器会散落状态；引入 Micrometer 需新增依赖 |
| 新增 `GXSSOTokenStateEnums` 枚举 | F-11 要求"未登录"只有一种表达；现有"抛异常 vs 返回空"必须收敛为可承载原因的结构化结果 | 继续抛 `GXTokenInvalidException` 会让拦截器无法区分 401/重定向/被踢三种响应；用魔法字符串会违反宪法 VII |
| 新增 3 个配置键 | FR-007/FR-008/FR-009 分别要求"默认过期可配""降级可开关""配置源可选" | 复用 `cacheExpires=-1` 表达"永不过期"无法与"未配置"区分；用约定式 bean 名（如 `ssoCache`）选择实现依赖字符串约定，不可测 |
| 新增 `GXSSOCaptchaValidator`（**实施期未落地**，见 T094） | F-23/F-24 要求"验证码不可被第三方作废""存储失败不静默"，需要把校验语义从可变状态机里剥离 | 直接在 `AbstractCaptcha.verification` 里加分支会让"实例可变"与"校验语义"继续耦合，且无法为 `verification(null)` 提供独立回归点。**实施期修订**：实际由 `AbstractCaptcha.verification`（`null` 不删 ticket、`store.put=false` 抛业务异常）+ `CaptchaStoreSession.consume`（同步一次性消费）落地，未单独建类型；`GXSSOCaptchaValidator` 从交付范围移除，回归点由 `CaptchaRenderTest#nullCaptchaDoesNotInvalidateTicket`、`#storeFailureRaisesBusinessException` 与 `GXSSOConcurrencyTest#concurrentCaptchaVerificationConsumesOnce` 覆盖 |
| 手写 `Set-Cookie` 改为 `ResponseCookie` | FR-007 要求 Cookie 属性不可被配置注入，`SameSite=None` 必须配 `Secure` | 继续手写需自行实现转义与校验（重复造轮子，违反附加约束 4）；`jakarta.servlet.http.Cookie` 不支持 `SameSite`，只能手写头 |
| `cacheExpires` 默认值变更（破坏性） | FR-007 要求默认配置下 Token 必须有绝对过期 | 保留 `-1` 并靠文档提醒等于把安全决策交给下游；改为"默认过期 + 显式 `-1` 恢复"是唯一兼顾兼容的路径 |

## Phase 2 预告

`/speckit.tasks` 将按"测试先行"拆分为 6 个批次：B1 权限与安全默认值（F-01/F-02/F-03/F-04/F-05）→ B2 登录态与登出语义（F-09/F-10/F-11/F-13/F-16/F-17）→ B3 装配与配置源（F-06/F-07/F-14/F-15/F-32）→ B4 并发与实例安全（F-08/F-20/F-21/F-22）→ B5 性能与可观测（F-25/F-26/F-27/F-28/F-29/F-30/F-31）→ B6 契约与文档（F-33/F-34/F-35/F-36/F-37），每批次内顺序为"先用例（旧代码失败）→ 最小实现 → `mvn -pl leaf-base-sso test` 全绿"。
