---
description: "Task list for leaf-base-sso 正确性、线程安全与性能审查"
---

# Tasks: leaf-base-sso 正确性、线程安全与性能审查

**Input**: Design documents from `/specs/013-sso-module-audit/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 本特性**强制测试**（`spec.md` FR-020 ~ FR-023、`leaf-base-sso/AGENTS.md` §4）。每个修复项必须先写"旧代码上失败"的用例（FR-021），再实现；不得通过删除或跳过既有用例来"通过"。

**Organization**: 按 `spec.md` 的 6 个用户故事分组，每个故事可独立验证。任务描述中的 `B1~B6` 对应 `plan.md` Phase 2 预告的修复批次。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与同批次其它任务并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1 ~ US6）
- 每个任务均给出精确文件路径

## Path Conventions

生产代码：`leaf-base-sso/src/main/java/cn/maple/sso/...`
测试代码：`leaf-base-sso/src/test/java/cn/maple/sso/...`
资源：`leaf-base-sso/src/main/resources/META-INF/...`
框架侧唯一改动：`leaf-base-framework/src/main/java/cn/maple/core/framework/web/config/GXWebMvcConfigurer.java`
文档：`docs/leaf-base-sso开发手册.md`、`leaf-base-sso/AGENTS.md`
规格产物：`specs/013-sso-module-audit/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 固定基线与上游事实，建立报告骨架，保证后续所有结论可复核

- [X] T001 在 `specs/013-sso-module-audit/audit-report.md` 建立骨架（章节：依赖基线 / E 序列 / F 八要素 / 四张矩阵 / P 序列 / 故障现场 / 性能口径 / 兼容声明），并记录本次实测事实：`nacos-client:3.1.1:compile` 在类路径、全仓无 `implements GXSSOCache` 生产实现、`mvn -o -pl leaf-base-sso test` = 40 执行 / 0 失败、零新增依赖决定
- [X] T002 [P] 在 `specs/013-sso-module-audit/audit-report.md` 写出附录 A「上游语义摘录」：`GXAuthCodeUtils`（第 95–114、128–150、141、147 行）、`GXCookieHelperUtil`（第 11、18–29、81–96 行）、`GXCurrentRequestContextUtils`（第 267–341、417–420 行）、`GXSpringContextUtils`（第 45–62 行）、`GXWebMvcConfigurer`（第 30–48 行）、`GXTokenConstant`（第 30 行），每条附"该事实支撑哪个 E/F"
- [X] T003 [P] 新建 `leaf-base-sso/src/main/resources/META-INF/additional-spring-configuration-metadata.json`，登记现有全部键（`sso.config.encoding`…`sso.config.permission-uri`、`url.white-lists`）并预留 3 个新键（`sso.config.source`、`sso.config.cache-fallback-enabled`、`sso.config.capacity`），字段格式照抄 `leaf-base-framework/src/main/resources/META-INF/additional-spring-configuration-metadata.json`（宪法 IV，FR-013）
- [X] T004 [P] 在 `specs/013-sso-module-audit/audit-report.md` 写出附录 B「保留断言清单」：把 P-01 ~ P-10 映射到 `leaf-base-sso/src/test/java/cn/maple/sso/` 下具体测试类与方法名（至少 40 条中的 12 条关键断言），作为后续"一条不删"的核对基线（FR-012）
- [X] T005 运行 `mvn -o -pl leaf-base-sso dependency:tree "-Dincludes=com.alibaba.nacos"` 与 `mvn -o -pl leaf-base-sso test`，把原始输出片段归档到 `specs/013-sso-module-audit/audit-report.md` 附录 C「命令与输出」（R-01、R-11）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 提供所有故事共用的类型、测试替身与拦截器顺序保证；除排序外不改变任何默认行为

**⚠️ CRITICAL**: 未完成前不得开始任何用户故事

- [X] T006 新增 `leaf-base-sso/src/main/java/cn/maple/sso/enums/GXSSOTokenStateEnums.java`：8 个枚举值 `ABSENT / ACTIVE / CACHE_MISS / EXPIRED / KICKED / CACHE_UNAVAILABLE / PLUGIN_REJECTED / LOCAL_ONLY`，每值带 `code`（ASCII）与 `desc`，提供 `fromCode(String)` 兜底返回 `ABSENT`（按 `specs/013-sso-module-audit/data-model.md` §3）
- [X] T007 [P] 新增 `leaf-base-sso/src/main/java/cn/maple/sso/metrics/GXSSOMetrics.java`：`@Component` + 8 个 `LongAdder`（`parseSuccess`/`parseFailure`/`cacheHit`/`cacheMiss`/`denied`/`loginFailure`/`cacheFallback`/`invalidToken`）+ `activeTokens` 刷新型计数 + `record Snapshot(...)` 不可变快照 + `incrementXxx()`/`snapshot()`，**最终 9 项**字段与 `contracts/sso-config-observability-contract.md` §4 完全一致（**实施期修订**：原计划的 `concurrentLoginConflict` 已随 T076/T086 移除，见 T097）
- [X] T008 [P] 在既有 `leaf-base-sso/src/main/java/cn/maple/sso/constant/GXSSOConstant.java` 内新增 `AUTHORIZATION_ORDER = 100`、`PERMISSION_ORDER = 200`（`contracts/sso-authorization-contract.md` §1）（**实施期修订**：原计划新建 `GXSSOInterceptorOrder`，T102 按宪法 VII"顺序常量并入既有 `GX<范围>Constant`"改为并入常量类）
- [X] T009 在 `leaf-base-framework/src/main/java/cn/maple/core/framework/web/config/GXWebMvcConfigurer.java` 把 `addInterceptors` 的排序改为 `Comparator.comparingInt(entry -> AnnotationAwareOrderComparator.INSTANCE.getOrder(entry.getValue()))`（稳定排序，未标注者沿用原字典序）；同步在 `leaf-base-sso/src/main/java/cn/maple/sso/web/interceptor/GXSSOAuthorizationInterceptor.java` 加 `@Order(GXSSOConstant.AUTHORIZATION_ORDER)`、在 `leaf-base-sso/src/main/java/cn/maple/sso/web/interceptor/GXSSOPermissionInterceptor.java` 加 `@Order(GXSSOConstant.PERMISSION_ORDER)`；运行 `mvn -o -pl leaf-base-sso test` 确认 40 条仍绿（R-12，D-07）
- [X] T010 [P] 新增 `leaf-base-sso/src/test/java/cn/maple/sso/support/GXSSOTestApplication.java`：`@SpringBootApplication(scanBasePackages = "cn.maple.sso")` + 注册 `GXSSOCache`/`GXTokenConfigService`/`GXSSOAuthorization`/`GXBaseCacheService`/`GXUUserService` 测试实现，类级 `properties = {"spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.discovery.enabled=false"}`（R-14）
- [X] T011 [P] 新增 `leaf-base-sso/src/test/java/cn/maple/sso/support/GXSSOTestDoubles.java`：`InMemoryCacheService`（`GXBaseCacheService` 全接口方法）、`CountingSsoCache`（记录 get/set/delete 次数并可注入失败）、`FixedTokenConfigService`（可配 secret/checkLoginStatus/verifyTokenEffectiveness）、`RecordingAuthorization`（可配 `isPermitted` 返回值）、`RecordingPlugin`（记录 login/validateToken/logout 调用次数）、`StubUserService`（FR-023 禁止反射构造现场）
- [X] T012 运行 `mvn -o -pl leaf-base-sso test` 与 `mvn -o -pl leaf-base-sso -am clean install -DskipTests`，确认：既有 40 条全绿、新增元数据 JSON 语法合法、NullAway 零错误（证明本阶段无行为变化）

**Checkpoint**: 原因码/指标/顺序常量就绪、测试替身可用、基线未破。

---

## Phase 3: User Story 1 - 审查结论可复核 (Priority: P1) 🎯 MVP

**Goal**: 交付独立报告，且每条高危结论都能用一条可运行的复现用例支撑（对应 B1）。

**Independent Test**: 随机抽取报告中的任一 F，按其"回归设计"运行对应用例，观察当前（旧）代码的失败输出与报告一致。

### Tests（先写，必须在旧代码上失败）

- [X] T013 [P] [US1] 新增 `leaf-base-sso/src/test/java/cn/maple/sso/web/interceptor/GXSSOAuditReproTest.java` 并实现 `f01UriPermissionModeIsBypassedByDefault`：`permissionUri=true` + 方法无 `@GXPermissionAnnotation` + 授权器恒 false → 期望 `preHandle` 返回 false（旧代码返回 true）
- [X] T014 [P] [US1] 在同文件实现 `f02NoCacheImplMeansNoServerSideValidation`：不注册 `GXSSOCache`，用 `GXAuthCodeUtils.authCodeEncode` 构造含 `userId` 的 Cookie → 期望判定为未登录/缓存不可用（旧代码判定为已登录）
- [X] T015 [P] [US1] 在同文件实现 `f03DefaultSecretAllowsForgedToken`：以 `GXTokenConstant.TOKEN_SECRET_KEY` 为密钥编码任意 `userId` → 期望默认配置下校验失败（旧代码通过）
- [X] T016 [P] [US1] 在同文件实现 `f04DefaultConfigIssuesNonExpiringToken`：默认 `cacheExpires` 下签发 Token 并检查编码前缀 → 期望不含 `0000000000`（旧代码含）
- [X] T017 [P] [US1] 在同文件实现 `f15InterceptorChainOrderIsExplicit`：断言两个拦截器类具备 `@Order` 且值分别为 100/200、`GXWebMvcConfigurer` 按 `AnnotationAwareOrderComparator` 排序（旧代码无注解、按 bean 名排序）
- [X] T018 [US1] 运行 `mvn -o -pl leaf-base-sso test -Dtest=GXSSOAuditReproTest`，把 5 条用例的失败输出贴入 `specs/013-sso-module-audit/audit-report.md` §故障现场（证明 FR-021）

### Implementation（报告本体）

- [X] T019 [US1] 在 `specs/013-sso-module-audit/audit-report.md` 写入 E-01 ~ E-34 表（字段：`id`/`kind`/`locator`/`fact`/`verified`，按 `specs/013-sso-module-audit/data-model.md` §2 校验）
- [X] T020 [P] [US1] 在 `specs/013-sso-module-audit/audit-report.md` 写入 F-01 ~ F-37 八要素表（级别/维度/位置/触发/影响/证据/复现/回归设计 + `fixBatch` B1~B6，按 `data-model.md` §1 校验）
- [X] T021 [P] [US1] 在 `specs/013-sso-module-audit/audit-report.md` 写入四张矩阵与 P 序列：终止矩阵（7 类终止 × 5 观测点）、权限矩阵（4 注解 × 4 配置）、共享状态清单（9 个对象）、性能口径表（5 项可复算）、P-01 ~ P-10 既定契约（按 `data-model.md` §3~§8）

**Checkpoint**: 报告八要素完整、5 条复现用例在旧代码上失败、结论可逐条定位。

---

## Phase 4: User Story 2 - 登录、登出与失败语义可判定 (Priority: P1)

**Goal**: 登录、降级、登出、被踢、未登录、无权限六类语义各自唯一且可观测（对应 B1 的安全默认值 + B2 的登出语义 + B3 的权限链）。

**Independent Test**: 对"正常登录 / 缓存不可用 / 插件拒绝 / 重复登出 / 被踢 / 匿名访问受保护接口 / URI 权限拒绝"七类场景各构造一次，记录返回码、Cookie、缓存状态、指标与插件调用次数。

### Tests（先写）

- [X] T022 [P] [US2] 新增 `leaf-base-sso/src/test/java/cn/maple/sso/cache/GXSSOCacheContractTest.java`：`getNeverThrowsAndReturnsEmptyDictWithReason`（`verifyTokenEffectiveness=false` → 空 Dict + 请求属性 `GXSSOTokenStateEnums.EXPIRED`）、`deleteIsIdempotent`（键不存在返回 true）、`kickLoginWithNullReturnsFalse`、`setPassesTtlToCacheService`（TTL=7200/`-1` 两种）
- [X] T023 [P] [US2] 在 `leaf-base-sso/src/test/java/cn/maple/sso/service/GXSSOServiceBehaviorTest.java` 追加：`loginFailsWhenCacheUnavailable`、`loginDegradesWithBoundedTtlWhenFallbackEnabled`、`pluginLoginFailureRollsBackCacheAndThrows`、`secondLogoutReturnsTrueWithoutWarn`、`defaultConfigTokenCarriesAbsoluteExpiry`
- [X] T024 [P] [US2] 在 `leaf-base-sso/src/test/java/cn/maple/sso/web/interceptor/GXSSOInterceptorBehaviorTest.java` 覆盖权限矩阵 16 个单元格（`data-model.md` §5 的 4 行 × 4 列）：`permissionMatrixIsDecidedBySingleChain` 参数化覆盖"无注解 / Normal / Skip" × 4 配置 = **12 行**；`@GXIgnoreLoginIntercept` 行由 `ignoreLoginAnnotationWithoutPermissionAllowsAnonymousRequest`、`ignoreLoginAnnotationAllowsAnonymousRequestWhenUriPermissionModeEnabled`、`ignoreLoginAnnotationBypassesPermissionTokenValidation` 覆盖（短路先于配置读取）；`permissionUri=true` 的空 URI 由 `blankUriIsDeniedWhenUriPermissionModeEnabled` 锁定为拒绝（**实施期修订**：原描述中的 `uriModeDoesNotFallBackToNothingAnnotationPass`、`nullUriIsDeniedInUriMode` 未单独建用例，其语义分别由参数化矩阵的 `none,true,*` 两行与 blank-uri 用例承担）
- [X] T025 [P] [US2] 新增 `leaf-base-sso/src/test/java/cn/maple/sso/service/GXSSOAuthFlowSpringBootTest.java`：`@SpringBootTest(classes = GXSSOTestApplication.class, webEnvironment = RANDOM_PORT)` + `MockMvc`，覆盖「匿名访问受保护 URL → 401/302」「登录后放行」「第二次登录顶掉第一个会话（401 + KICKED）」「登出后 401」，并断言插件 logout 调用次数在匿名路径为 0（FR-013、F-13）
- [X] T026 [US2] 运行上述四类用例，把失败输出归档到 `specs/013-sso-module-audit/audit-report.md` §故障现场（证明"旧代码失败"）

### Implementation

- [X] T027 [US2] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/cache/GXSSOCache.java`：`get` 不再抛 `GXTokenInvalidException`（返回空 `Dict` + 写 `GXSSOConstant.SSO_TOKEN_STATE`）、`delete` 改为"键不存在也算成功"并返回布尔、`getValidUserId` 非法时返回 `false` 而非抛异常、`set` 显式传 TTL；埋入 `cacheHit`/`cacheMiss`/`invalidToken` 指标（`contracts/sso-auth-contract.md` §4/§5）
- [X] T028 [US2] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/service/GXTokenConfigService.java`：默认 `getTokenSecret()` 返回 `null`（不再回退公开常量）、`getTokenCacheKey` 把 `extraData` 的 `platform` 纳入键（默认实现改为 `prefix + userId + ":" + platform` 且保持空 platform 时与旧键完全一致）、补 Javadoc 说明（FR-007、F-32）
- [X] T029 [US2] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/properties/GXSSOProperties.java`：删除 `rsaKeypass`/`rsaStorepass` 硬编码与 `signKey` 默认值（改 `null`）、`cacheExpires` 默认 `7200`、新增 `cacheFallbackEnabled`（false）/`capacity`（4096）/`source`（`nacos`）、七个遗留 getter 不再抛 `GXBusinessException`、可空字段补 `@Nullable`（FR-007、FR-016、D-02）
- [X] T030 [US2] 在 `leaf-base-sso/src/main/java/cn/maple/sso/service/GXAbstractSSOService.java` 新增 `@PostConstruct` 启动校验（密钥缺失 → `prod` 抛 `GXBusinessException`、其它 ERROR；`SameSite=None && !secure` → ERROR），并按 `contracts/sso-auth-contract.md` §3 实现：插件 `login=false` 回滚缓存并抛异常、缓存不可用 fail-closed、降级 TTL ≤ 900s、写后回读 `loginAt` 乐观并发重试（D-03、D-04、F-02、F-08、F-16）
- [X] T031 [US2] 把 `leaf-base-sso/src/main/java/cn/maple/sso/service/GXAbstractSSOService.java` 的 `setCookie` 改为 `ResponseCookie` 构建（`path/domain/max-age/secure/httpOnly/sameSite` 全走 API，`SameSite=None` 强制 `Secure`），删除 `X-XSS-Protection`，去掉三次重复的 `fillTokenValueIfNecessary` 调用（D-13、F-18）
- [X] T032 [US2] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/web/interceptor/GXSSOPermissionInterceptor.java`：按 `contracts/sso-authorization-contract.md` §3 实现唯一优先级链（Ignore → Skip → URI 终局 → 注解 → `nothingAnnotationPass`）、`requiresTokenForPermission` 与判定共用同一方法、启动时 INFO 打印生效配置；白名单改用 `UrlPathHelper.getPathWithinApplication` + 双写法兼容（D-06、D-08、F-01、F-14）
- [X] T033 [US2] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/web/interceptor/GXSSOAuthorizationInterceptor.java`：`handleEmptyToken` 删除 `ssoCache.delete(Dict.create())`、未登录响应改 `GXResultUtils.error`、`isAjax` 归一（`X-Requested-With` + `X-RESPONSE-TYPE`）、未登录日志降 DEBUG 并带 `GXSSOTokenStateEnums`（D-10、F-13、F-30）
- [X] T034 [US2] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/service/GXSSOSupportService.java`：`checkIpBrowser` 的 null IP 改为拒绝（fail-closed）、`generateCookie` 去掉 `(Integer)` 强转（用 `Number` 取值）、`logout` 返回值改为"缓存删除成功或键不存在"、`attrSSOToken` 返回非 null、可空参数补 `@Nullable`（F-19、F-29、F-10）
- [X] T035 [US2] 在 `leaf-base-sso/src/main/java/cn/maple/sso/service/GXSSOService.java` 补 `default void logout(...)` 与 `default Dict attrSSOToken(...)`（实现委托 `GXAbstractSSOService`/`GXSSOSupportService`），并在 `leaf-base-sso/src/main/java/cn/maple/sso/utils/GXSSOHelperUtil.java` 改为面向接口调用（F-36）
- [X] T036 [US2] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/web/handler/GXSSODefaultHandler.java` 与 `leaf-base-sso/src/main/java/cn/maple/sso/utils/GXHttpUtil.java`：`preTokenIsNullAjax`/`ajaxStatus` 改为委托 `GXResultUtils.error`（字段仍是 `code`/`msg`/`data`），保留方法签名兼容（F-30）
- [X] T037 [US2] 运行 `mvn -o -pl leaf-base-sso test`，确认 T022 ~ T025 由红转绿、既有 40 条仍绿，并把全绿输出归档到 `specs/013-sso-module-audit/audit-report.md`

**Checkpoint**: 七类登录/登出/权限语义各自唯一、默认安全（fail-closed）、匿名请求零副作用。

---

## Phase 5: User Story 3 - 并发与共享状态安全可验证 (Priority: P1)

**Goal**: 全局静态配置、注解缓存、验证码实例、测试夹具在并发与热部署下安全（对应 B4）。

**Independent Test**: 并发登录不丢字段、注解缓存超限不回源抖动、验证码实例不互相污染、`length=1` 不崩溃、测试可并行执行。

### Tests（先写）

- [X] T038 [P] [US3] 新增 `leaf-base-sso/src/test/java/cn/maple/sso/service/GXSSOConcurrencyTest.java`：`concurrentLoginsKeepMergedFields`（20 线程同 `userId`，断言最终 Token 含 `userAgent`/`ip`/`createTime`）、`concurrentAnnotationLookupIsStable`（50 线程 × 200 个 handler，断言判定结果一致且 `metrics` 无异常）、`concurrentKickAndValidateDoesNotThrow`、`concurrentCaptchaVerificationConsumesOnce`
- [X] T039 [P] [US3] 新增 `leaf-base-sso/src/test/java/cn/maple/sso/captcha/CaptchaRenderTest.java`：`pngStartsWithMagicBytes`（`89 50 4E 47`）、`gifStartsWithGif89a`、`singleCharGifDoesNotThrow`（`length=1`，旧代码抛 `IllegalArgumentException`）、`widthSmallerThanLengthIsHandled`、`nullCaptchaDoesNotInvalidateTicket`、`storeFailureRaisesBusinessException`
- [X] T040 [US3] 运行 `mvn -o -pl leaf-base-sso test -Dtest=GXSSOConcurrencyTest,CaptchaRenderTest`，把失败输出归档到 `specs/013-sso-module-audit/audit-report.md` §故障现场

### Implementation

- [X] T041 [US3] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/web/interceptor/GXHandlerMethodAnnotationUtils.java`：缓存内容改为判定结果（是否存在 / `action` / `value` 字符串），键改为 `beanType.getName()#method.toGenericString()#annotationType.getName()`，容量取 `GXSSOProperties.capacity`，超限按 FIFO 淘汰（`ConcurrentLinkedQueue` + `poll`），删除 `clear()` 全清逻辑（D-11、F-20）
- [X] T042 [US3] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/captcha/AbstractCaptcha.java`：配置字段构造期定型 + 首次 `generate` 后冻结（再次 `setXxx` 抛 `GXBusinessException`）、`randomCaptcha()` 不再懒写字段、`verification(null)` 不删除 ticket 直接返回 false、`store.put` 返回 false 时抛业务异常而非静默空响应（D-12、F-21、F-23、F-24）
- [X] T043 [US3] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/captcha/ImageCaptcha.java`：新增 `of(int length, int width, int height, boolean gif)` 工厂；`getAlpha` 在 `length <= 1` 时返回 `1.0f`（修 F-22 除零）；`writeImage` 补 `ImageIO.write` 返回 false 的失败语义；`width/length` 为 0 时的字符定位改为安全除法
- [X] T044 [US3] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/captcha/CaptchaStoreSession.java`：`getSession()` 延迟到首次 `put`/`get`，`consume` 加同步保护（同 ticket 并发只允许一次成功），过期清理保持现状（P-08 不破坏）
- [X] T045 [US3] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/utils/GXSSOHelperUtil.java` 的共享状态部分：`ssoConfig`/`ssoService` 保持 `volatile` + DCL，新增"已冻结"标记（`getSSOConfig` 返回的实例不允许在请求路径上改写），`setSsoConfig(null)` 抛 `IllegalArgumentException` 而非 NPE，`parser` 的异常类型收敛（只抛 `GXBusinessException`）
- [X] T046 [US3] 为既有 5 个测试类补测试隔离：`GXSSOServiceBehaviorTest`、`GXSSOInterceptorBehaviorTest`、`GXSSOUtilsBehaviorTest`、`GXLoginUserHandlerMethodArgumentResolverTest`、`GXEnumBehaviorTest` 的 `@AfterEach` 复原 `GXSSOHelperUtil.setSsoConfig` 与 `GXApplicationContextSingleton`，并加 `@ResourceLock("sso-global-config")`；在 `leaf-base-sso/pom.xml`（不改依赖）之外通过 `-Djunit.jupiter.execution.parallel.enabled=true` 手工验证可并行
- [X] T047 [US3] 运行 `mvn -o -pl leaf-base-sso test "-Djunit.jupiter.execution.parallel.enabled=true" "-Djunit.jupiter.execution.parallel.mode.default=same_thread"`，确认全绿且并发类耗时可控，把结果归档到 `specs/013-sso-module-audit/audit-report.md`

**Checkpoint**: 并发登录不丢字段、缓存无雪崩式回源、验证码实例与 `length=1` 语义确定、测试可并行。

---

## Phase 6: User Story 4 - 性能开销定量化 (Priority: P2)

**Goal**: 消除"每请求固定成本"中的可消除项，并给出可复算口径（对应 B5）。

**Independent Test**: 预热后每请求 `getBean` 次数为 0、未登录不产生 INFO 日志、localhost 分支不再触发 `InetAddress.getLocalHost()`；指标 10 项在对应操作后单调递增。

### Tests（先写）

- [X] T048 [P] [US4] 新增 `leaf-base-sso/src/test/java/cn/maple/sso/utils/GXSSOHelperUtilTest.java`：`beanLookupIsCachedAfterWarmup`（用 `CountingSsoCache`/计数版 `GXTokenConfigService` 断言第二次调用不再走 `GXSpringContextUtils`，实现方式为可注入的查找器计数）、`setSsoConfigInvalidatesBeanCache`、`metricsIncrementOnParseAndCache`（解析成功/失败/命中/未命中各 +1）
- [X] T049 [P] [US4] 在 `leaf-base-sso/src/test/java/cn/maple/sso/utils/GXSSOUtilsBehaviorTest.java` 追加：`ipHelperDoesNotResolveLocalHostOnEachCall`（连续 100 次调用后断言主机名解析次数为 1，用计数封装或静态字段快照）、`inContainUrlMatchesByPathSegment`（`/api/user` 不命中 `/api/user-admin`）、`requestPlayloadRejectsOversizedBody`（超过 64KB 抛 `GXBusinessException`）
- [X] T050 [US4] 运行上述用例，把失败输出归档到 `specs/013-sso-module-audit/audit-report.md` §故障现场

### Implementation

- [X] T051 [US4] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/utils/GXIpHelperUtil.java`：localhost 分支直接返回静态 `LOCAL_IP`（去掉每请求 `InetAddress.getLocalHost()`）、`isLocalIp` 结果按主机名缓存、`LOCAL_IP`/`HOST_NAME` 改为 private + getter（消除 public 可变静态字段）
- [X] T052 [US4] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/utils/GXHttpUtil.java`：`inContainURL` 改为按 `/` 分段前缀匹配（不再子串 `indexOf`）、`requestPlayload` 加 64KB 上限与显式 `UTF-8`、`isAjax` 归一为 `X-Requested-With` 与 `X-RESPONSE-TYPE` 的统一判定（F-28、F-30）
- [X] T053 [US4] 按 `contracts/sso-config-observability-contract.md` §5 统一日志级别：未登录 `/` 解析失败 `/` UA 缺失 → DEBUG 并带 `event` 前缀；登录成功 INFO、失败 WARN、降级/密钥缺失 ERROR；所有日志加 `isEnabled` 守卫、ASCII 且不输出 Token 原文（F-27）
- [X] T054 [US4] 在 `leaf-base-sso/src/main/java/cn/maple/sso/cache/GXSSOCache.java`、`leaf-base-sso/src/main/java/cn/maple/sso/service/GXAbstractSSOService.java`、`leaf-base-sso/src/main/java/cn/maple/sso/web/interceptor/GXSSOAuthorizationInterceptor.java`、`leaf-base-sso/src/main/java/cn/maple/sso/web/interceptor/GXSSOPermissionInterceptor.java` 埋入剩余指标（`loginFailure`/`denied`/`concurrentLoginConflict`），并确保每请求路径无 INFO
- [X] T055 [US4] 在 `specs/013-sso-module-audit/audit-report.md` 写入性能口径表实测值：每请求 MD5 次数（4）、RC4 密钥表轮数（2×256）、JSON 次数（1+x）、`getBean` 次数（预热后 0）、INFO 条数（0）、`InetAddress` 触发（0）、插件循环次数（N），并附复算命令（SC-005）

**Checkpoint**: 可消除的固定成本已消除、口径可独立复算、日志级别合规。

---

## Phase 7: User Story 5 - 对外契约与文档一致 (Priority: P3)

**Goal**: 空值与配置契约显式化，手册与 `AGENTS.md` 零漂移，双配置源真实可达（对应 B6 + B3 的配置源部分）。

**Independent Test**: `sso.config.source` 可切换且互斥；`rg` 检查手册不存在已不成立的键与机制描述；NullAway 零错误；`verification(null)` 返回 false 且不清 ticket。

### Tests（先写）

- [X] T056 [P] [US5] 新增 `leaf-base-sso/src/test/java/cn/maple/sso/properties/GXSSOConfigSourceTest.java`：`sourceLocalBindsLocalBeanOnly`、`sourceNacosIsDefault`（`matchIfMissing`）、`defaultValuesMatchContract`（`tokenName=token`/`cookieName=uid`/`cookieSameSite=Lax`/`cacheExpires=7200`/`cacheFallbackEnabled=false`/`capacity=4096`）、`prodProfileRejectsMissingSecret`（`AbstractApplicationContext` 启动失败）、`metadataContainsAllKeys`（读 `META-INF/additional-spring-configuration-metadata.json` 断言键集合）
- [X] T057 [US5] 运行 `mvn -o -pl leaf-base-sso test -Dtest=GXSSOConfigSourceTest`，把失败输出归档（旧代码：`source` 键不存在、`cacheExpires=-1`）

### Implementation

- [X] T058 [US5] 改造 4 个配置类：`leaf-base-sso/src/main/java/cn/maple/sso/properties/local/GXLocalSSOConfigProperties.java` 与 `GXLocalUrlWhiteListsConfigProperties.java` 的条件改为 `@ConditionalOnProperty(name="sso.config.source", havingValue="local")` 且 `ignoreResourceNotFound=true`（缺失打 WARN）；`leaf-base-sso/src/main/java/cn/maple/sso/properties/nacos/GXNacosSSOConfigProperties.java` 与 `GXNacosUrlWhiteListsConfigProperties.java` 改为 `havingValue="nacos", matchIfMissing=true`，并为 `GXNacosSSOConfigProperties` 补 `autoRefreshed = true`（D-05、F-06、F-07）
- [X] T059 [US5] 按 `contracts/sso-auth-contract.md` §6 补全 `@Nullable`：`leaf-base-sso/src/main/java/cn/maple/sso/service/GXSSOService.java`、`GXSSOSupportService.java`、`GXAbstractSSOService.java`、`leaf-base-sso/src/main/java/cn/maple/sso/utils/GXSSOHelperUtil.java`、`GXBrowserUtil.java`、`leaf-base-sso/src/main/java/cn/maple/sso/captcha/ICaptcha.java`；确保 `mvn -o -pl leaf-base-sso -am clean install -DskipTests` 通过（NullAway ERROR）
- [X] T060 [US5] 改造 `leaf-base-sso/src/main/java/cn/maple/sso/web/support/GXLoginUserHandlerMethodArgumentResolver.java`：用户不存在时抛 `GXBusinessException`（不再返回 null）、`@GXLoginUserAnnotation` 的 Javadoc 声明失败语义、`userId` 非法时抛业务异常（FR-017、F-31）
- [X] T061 [US5] 在 `leaf-base-sso/src/main/java/cn/maple/sso/captcha/Encoder.java`、`GifEncoder.java`、`Quant.java` 的类注释补来源与许可声明（如无法确认来源，则在注释中标注"内联第三方实现，来源待确认，禁止扩散使用"），并新增 `leaf-base-sso/src/main/java/cn/maple/sso/captcha/package-info.java` 说明"仅用于验证码渲染"（FR-026、F-37）
- [X] T062 [US5] 重写 `docs/leaf-base-sso开发手册.md` 的 3.5 / 4 / 5 / 6.2 / 7 章节：Token 真实编码与过期语义、配置键表（含 3 个新键与"保留但无效"标注）、拦截链与 `@Order`、`GXSSOCache` 实现要求（默认 fail-closed）、安全配置（密钥注入、`SameSite`）；删除 `tokenTimeout`/`tokenActivityTimeout`/`aesKey`/`multiLogin`/`tokenPrefix`/`validateCacheToken` 等不成立内容（FR-013、F-32、F-33）
- [X] T063 [US5] 更新 `leaf-base-sso/AGENTS.md` §3 关键行为与 §4 测试要求：新增"默认 fail-closed（无缓存不得静默放行）""Token 密钥不得有源码默认值""匿名请求零副作用""URI 权限模式不得回落到默认放行"四条红线 + 对应测试类清单

**Checkpoint**: 配置源可切换、契约显式、手册零漂移、NullAway 零错误。

---

## Phase 8: User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

**Goal**: 每条发现可直接转任务，既定契约被显式保护，报告与任务双向可追溯。

**Independent Test**: 抽取任一 F，报告已含编号/级别/维度/位置/触发/影响/证据/回归设计/`fixBatch`/任务 ID；抽取任一 P，能在用例集中找到对应断言。

- [X] T064 [P] [US6] 在 `specs/013-sso-module-audit/audit-report.md` 追加「F → `fixBatch` → 用例类 → 任务 ID」追溯矩阵，任务 ID 与 `specs/013-sso-module-audit/tasks.md` 严格对齐（37 行，无缺项）
- [X] T065 [P] [US6] 在 `specs/013-sso-module-audit/audit-report.md` 追加「P-01 ~ P-10 → 保留用例名」对照表，并用 `rg -n "@Test" leaf-base-sso/src/test/java` 核对 40 条用例一条未删、无 `@Disabled`
- [X] T066 [US6] 校验 `specs/013-sso-module-audit/quickstart.md` 的 7 条复现项与 11 个验收场景与 `tasks.md` 一一对应，补齐缺失命令与期望输出（若实现期发现口径变化，以实测为准更新）

**Checkpoint**: 报告与任务双向可追溯，P 序列有断言保护。

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T067 运行 `mvn -o -pl leaf-base-sso -am clean install -DskipTests` 与 `mvn -o -pl leaf-base-sso test`，把全绿输出（用例总数、各类耗时）与 NullAway 结果归档到 `specs/013-sso-module-audit/audit-report.md` §验证记录（FR-022）
- [X] T068 [P] 检查 `leaf-base-sso/src/main/java/**/*.java` 全部日志为 ASCII 且无 `System.out`：`rg -n "System\.out|printStackTrace|[\u4e00-\u9fa5]" leaf-base-sso/src/main/java --glob "*.java"`，逐条确认仅出现在注释/Javadoc
- [X] T069 [P] 复核三份契约与实现一致：`specs/013-sso-module-audit/contracts/sso-auth-contract.md` §4/§5、`sso-authorization-contract.md` §3~§6、`sso-config-observability-contract.md` §2~§6，逐条打勾并把偏差写回 `audit-report.md`
- [X] T070 [P] 清理实现期临时用例与冗余代码，确认 `git status` 仅包含 `leaf-base-sso/`、`leaf-base-framework/src/main/java/cn/maple/core/framework/web/config/GXWebMvcConfigurer.java`、`docs/leaf-base-sso开发手册.md`、`specs/013-sso-module-audit/`
- [X] T071 在 `specs/013-sso-module-audit/audit-report.md` 产出 PR 说明素材：10 条兼容声明（`plan.md`）、2 条破坏性变更（`cacheExpires` 默认值、`get` 不再抛异常）、验证命令与结果、遗留风险（同秒登录无法区分新旧、RPC 上下文身份为空、`GXAuthCodeUtils` 未替换）
- [X] T072 逐条更新 `specs/013-sso-module-audit/audit-report.md` 中 F-01 ~ F-37 的 `status`（`FIXED` / `ACCEPTED_RISK`），`ACCEPTED_RISK` 必须写明理由与到期版本（`data-model.md` §1 校验）

---

## Dependencies

```text
Phase 1 (T001-T005)
   └─▶ Phase 2 (T006-T012)  ← 阻塞所有故事（顺序常量/指标/测试替身）
          ├─▶ US1 (T013-T021)  ← 报告 + 5 条复现用例（旧代码失败）
          │      └─▶ US2 (T022-T037)  ← 安全默认值 + 登录态语义 + 权限链，让 F-01~F-04/F-13~F-16 复现用例转绿
          │             ├─▶ US3 (T038-T047)  ← 并发与实例安全（依赖 T027/T029 的语义）
          │             │      └─▶ US4 (T048-T055)  ← 性能与指标（依赖 T007 指标 + T032/T033 埋点位置）
          │             └─▶ US5 (T056-T063)  ← 契约与文档（依赖 T029 的配置字段 + T027 的方法语义）
          └─▶ US6 (T064-T066)  ← 仅依赖 US1 报告骨架与 US2~US5 的用例名
                                     └─▶ Phase 9 (T067-T072)  ← 全部故事完成后收尾
```

**Story 完成顺序**：US1 → US2 →（US3 → US4）∥ US5 ∥ US6 → Polish。

## Parallel Example: User Story 1

```bash
# 5 条复现用例写在同一测试类的不同方法，互不依赖
Task: "T013 f01UriPermissionModeIsBypassedByDefault in GXSSOAuditReproTest.java"
Task: "T014 f02NoCacheImplMeansNoServerSideValidation in GXSSOAuditReproTest.java"
Task: "T015 f03DefaultSecretAllowsForgedToken in GXSSOAuditReproTest.java"
Task: "T016 f04DefaultConfigIssuesNonExpiringToken in GXSSOAuditReproTest.java"
Task: "T017 f15InterceptorChainOrderIsExplicit in GXSSOAuditReproTest.java"

# 报告三类表可分头撰写
Task: "T019 E-01~E-34 表 in specs/013-sso-module-audit/audit-report.md"
Task: "T020 F-01~F-37 八要素表 in specs/013-sso-module-audit/audit-report.md"
Task: "T021 四张矩阵 + P 序列 in specs/013-sso-module-audit/audit-report.md"
```

## Parallel Example: User Story 5

```bash
Task: "T056 配置源/默认值/元数据用例 in GXSSOConfigSourceTest.java"
Task: "T058 local/nacos 四类条件改造 in properties/**"
Task: "T062 手册重写 in docs/leaf-base-sso开发手册.md"
Task: "T063 AGENTS 红线更新 in leaf-base-sso/AGENTS.md"
```

## Implementation Strategy

### MVP First（US1）

1. Phase 1 + Phase 2 完成 → 基线 40/0、类型与测试替身就绪、拦截器顺序显式；
2. 完成 US1 → `specs/013-sso-module-audit/audit-report.md` 可交付，5 条复现用例在旧代码上稳定失败；
3. **STOP and VALIDATE**：按 `specs/013-sso-module-audit/quickstart.md` §3 逐条核对 7 个高危现场。

> 仅 US1 交付"可复核结论 + 失败用例"，不含生产修复。**最小可发布修复集 = US1 + US2**（安全默认值 + 登录态语义 + 权限链），US3/US4/US5 为增强项，US6 为追溯收尾。

### Incremental Delivery

1. US1 → 报告可复核（评审通过门槛）
2. US2 → 权限绕过、默认密钥、永不过期、静默降级四项高危消除（线上风险最高）
3. US3 → 并发与实例安全（登录覆盖、缓存雪崩、验证码崩溃）
4. US4 → 固定成本可消除项 + 指标可读
5. US5 → 契约与文档（可独立合入）
6. US6 + Polish → 追溯矩阵、端到端验证、PR 素材

### 每个任务的完成定义

1. 用例先行且在**改动前失败**（失败输出保存在 `specs/013-sso-module-audit/audit-report.md` §故障现场或 PR 描述）；
2. 实现最小化，不改变 `plan.md` 承诺的默认值（`tokenName`/`cookieName`/`cookieSameSite`/`cookieMaxAge`/`permissionUri`/`nothingAnnotationPass`/`trustForwardedIpHeaders`）；
3. `mvn -o -pl leaf-base-sso test` 全绿且用例数单调增长（≥ 40）；
4. 日志 ASCII、无 `System.out`、无新增依赖、无跨模块改动（框架侧仅 T009 的 5 行）。

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖，可并行
- 每个用户故事结束时都有独立 Checkpoint，可停止并交付
- 既有 40 条用例**一条不删**；`P-01 ~ P-10`（见 `audit-report.md` 附录 B）为保护对象
- 禁止用反射读写私有字段构造故障现场（FR-023）；一律使用 `leaf-base-sso/src/test/java/cn/maple/sso/support/GXSSOTestDoubles.java`
- 单条最小验证命令：`mvn -o -pl leaf-base-sso test`
- 唯一跨模块改动是 T009（`GXWebMvcConfigurer` 5 行排序），必须在 PR 说明原因（`plan.md` Complexity Tracking）

## 格式校验

- 全部 **103** 个任务（T001-T103，含 Phase 10~14 五轮收敛批次）均以 `- [X] T0xx`（已完成）或 `- [ ] T0xx`（待决，见 Phase 14）形式记录，含 `[P]`（适用时）与 `[USn]`（仅故事阶段）标记，并给出精确文件路径；
- Setup（T001-T005）、Foundational（T006-T012）、Polish（T067-T072）、Convergence（T073-T103）阶段无 `[Story]` 标记，符合模板约定；
- 任务 ID 从 T001 严格递增到 T103，无重复、无缺号；
- 每个用户故事均含"Tests（先写）→ Implementation → Checkpoint"三段，满足 FR-021 的"旧代码失败优先"顺序。

---

## Phase 10: Convergence

> 由 `/speckit.converge` 于 2026-10-23 追加。仅追加，不改写既有任务。实施前请先读 `specs/013-sso-module-audit/audit-report.md` §4.4/§6/§7 与三份契约。

- [X] T073 在 `specs/013-sso-module-audit/audit-report.md` 新增"文件 → 判定"覆盖表：逐行列出 `leaf-base-sso/src/main/java` 下全部 62 个文件（45 功能类 + 17 `package-info`）与 6 个既有测试类，每行给出判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因）并交叉引用 E/F 编号 per FR-001 (missing)
- [X] T074 消除请求路径上的直连 `getBean`：把 `leaf-base-sso/src/main/java/cn/maple/sso/cache/GXSSOCache.java` 三处 `GXSpringContextUtils.getBean(...)`、`leaf-base-sso/src/main/java/cn/maple/sso/web/interceptor/GXSSOPermissionInterceptor.java#getAuthorization`、`leaf-base-sso/src/main/java/cn/maple/sso/web/support/GXLoginUserHandlerMethodArgumentResolver.java` 的 `getBean` 统一改为 `GXSSOHelperUtil.bean(...)`；在 `leaf-base-sso/src/test/java/cn/maple/sso/utils/GXSSOHelperUtilTest.java` 增一条"配置缓存后，完整校验链共发生 0 次 bean 解析"的用例，并回写 `specs/013-sso-module-audit/audit-report.md` §4.4 与 `docs/leaf-base-sso开发手册.md` §7.2 的口径 per FR-011 (contradicts)
- [X] T075 在 `leaf-base-sso/src/test/java/cn/maple/sso/web/interceptor/GXSSOInterceptorBehaviorTest.java` 新增白名单用例：`whiteListMatchesPathWithinContextPath`（`UrlPathHelper` 剥离 context-path）、`whiteListAcceptsRawUriForm`（带 context-path 的写法同样命中）、`emptyWhiteListIsFailClosed`、`trailingSlashPatternIsHonoured`；必要时通过 `ReflectionTestUtils` 之外的构造方式注入 `GXUrlWhiteListsConfigProperties` per FR-020 (partial)
- [X] T076 处理 `leaf-base-sso/src/main/java/cn/maple/sso/metrics/GXSSOMetrics.java` 的死指标：为 `activeTokens` 在登录成功/登出后调用 `updateActiveTokens(...)`（来源可为 `GXBaseCacheService#size(bucket)`，不可用则跳过），并删除 `concurrentLoginConflict` 或补调用点；同步更新 `specs/013-sso-module-audit/contracts/sso-config-observability-contract.md` §4 与 `audit-report.md` §7.2 per contracts: observability §4 (contradicts)
- [X] T077 在 `specs/013-sso-module-audit/audit-report.md` §6 新增"逐修复项旧代码失败证据"表：为 F-05、F-07、F-12、F-14、F-15、F-17、F-19、F-20、F-29、F-31、F-32 各给出旧实现位置、旧行为断言与对应新用例名（无法复跑者标注"代码证据"）per FR-021 (partial)
- [X] T078 在 `leaf-base-sso/src/test/java/cn/maple/sso/service/GXSSOServiceBehaviorTest.java` 新增 `authCookieInvalidatesExistingSessionOnly`：携带已存在 session 时断言 `invalidate` 生效且 `MAPLE-*` 属性不再写入；无 session 时断言不新建会话（`request.getSession(false) == null`）per FR-020 (partial)
- [X] T079 在 `leaf-base-sso/src/test/java/cn/maple/sso/service/GXSSOServiceBehaviorTest.java` 中通过 `TestSSOService` 暴露 `checkIpBrowser`，新增 `blankRequestIpIsRejectedWhenIpCheckEnabled`（`cookie-check-ip=true` + 空 IP → 空 `Dict`）per FR-020 (partial)
- [X] T080 在 `leaf-base-sso/src/test/java/cn/maple/sso/service/GXSSOServiceBehaviorTest.java` 新增 `cookieMaxAgeAttributeAcceptsNonIntegerTypes`：请求属性为 `Long`/`String` 时分别断言正常取值与 WARN 降级（不抛 `ClassCastException`）per FR-020 (partial)
- [X] T081 在 `specs/013-sso-module-audit/audit-report.md` §7.2 增加"新增 / 修改 / 保留"三分类用例清单，保留项逐条对齐 P-01 ~ P-10 与附录 B per FR-022 (partial)
- [X] T082 在 `docs/leaf-base-sso开发手册.md` 新增"可观测性"小节：列出 `GXSSOMetrics.snapshot()` 全部指标名与含义、`event=sso.*` 事件名与级别、以及"不得逐请求输出 INFO"的约束 per FR-024 (partial)
- [X] T083 清理未使用导入：`leaf-base-sso/src/main/java/cn/maple/sso/web/interceptor/GXSSOAuthorizationInterceptor.java` 的 `GXSpringContextUtils`、`leaf-base-sso/src/test/java/cn/maple/sso/service/GXSSOServiceBehaviorTest.java` 的 `GXTokenInvalidException`；运行 `mvn -o -pl leaf-base-sso test` 确认 95 条仍全绿（**当时值 95**；现值以 `audit-report.md` §7.1 为唯一权威，见 T096/T119）per plan: constraints (partial)
---

## Phase 11: Convergence

> 由 `/speckit.converge` 于 2026-10-23（第二轮）追加。仅追加，不改写既有任务。

- [X] T084 更新 `specs/013-sso-module-audit/quickstart.md` §1 的修复后基线：`Tests run: 95` → `Tests run: 103`（并保留"只增不减"的判定口径），同时核对该文件其他命令示例中的用例数描述 per SC-001 (contradicts)
- [X] T085 合并 `specs/013-sso-module-audit/contracts/sso-config-observability-contract.md` §4 与 `specs/013-sso-module-audit/data-model.md` §8 中重复的 `activeTokens` 行，保留一条并注明触发点（`set`/`delete` 后按 `GXBaseCacheService#size(bucket)` 刷新） per FR-024 (partial)
- [X] T086 在 `specs/013-sso-module-audit/research.md` D-15 补充"实施期修订"注记：`concurrentLoginConflict` 已随 D-04 修订移除，实际指标为 `parseSuccess`/`parseFailure`/`cacheHit`/`cacheMiss`/`denied`/`loginFailure`/`cacheFallback`/`invalidToken`/`activeTokens` per plan: D-15 (contradicts)
- [X] T087 清理 4 处未使用导入：`leaf-base-sso/src/test/java/cn/maple/sso/utils/GXSSOHelperUtilTest.java` 的 `assertNull`、`leaf-base-sso/src/test/java/cn/maple/sso/web/interceptor/GXSSOInterceptorBehaviorTest.java` 的 `java.lang.reflect.Method`、`leaf-base-sso/src/test/java/cn/maple/sso/web/support/GXLoginUserHandlerMethodArgumentResolverTest.java` 的 `HttpServletRequest` 与 `NativeWebRequest`，并运行 `mvn -o -pl leaf-base-sso test` 确认 103 条仍全绿（**当时值 103**；现值以 `audit-report.md` §7.1 为唯一权威，见 T096/T119）per FR-022 (partial)
- [X] T088 为 `leaf-base-sso/src/main/java/cn/maple/sso/utils/GXSSOHelperUtil.java#beanLookupCount()` 补使用点（在 `leaf-base-sso/src/test/java/cn/maple/sso/utils/GXSSOHelperUtilTest.java` 的 `cacheOperationsReuseCachedBeans` 中断言预热后计数不再增长），或删除该 getter 与 `BEAN_LOOKUP_COUNT` 计数 per FR-011 (unrequested)
---

## Phase 12: Convergence

> 由 `/speckit.converge` 于 2026-10-23（第三轮）追加。仅追加，不改写既有任务。

- [X] T089 修正 `specs/013-sso-module-audit/audit-report.md` §3「复现 / 回归设计」列中 11 行失效引用，改用真实存在的用例名：F-05 → `GXSSOConfigSourceTest#sensitiveFieldsHaveNoSourceDefaults`；F-07 → `GXSSOConfigSourceTest#sourceNacosIsDefault`（并注明 `autoRefreshed` 由类注解保证）；F-14 → `GXSSOInterceptorBehaviorTest#whiteListMatchesPathWithinContextPath`；F-17 → `GXSSOServiceBehaviorTest#authCookieInvalidatesExistingSessionOnly`；F-18 → `GXSSOServiceBehaviorTest#invalidSameSiteCombinationIsRejected`；F-19 → `GXSSOServiceBehaviorTest#blankRequestIpIsRejectedWhenIpCheckEnabled`；F-20 → `GXSSOConcurrencyTest#concurrentAnnotationLookupIsStable`；F-21 → `CaptchaRenderTest#instanceIsFrozenAfterFirstGenerate`；F-26 → `GXSSOUtilsBehaviorTest#localRequestIpReusesResolvedAddress`；F-27 → `GXSSOAuthFlowSpringBootTest#anonymousRequestHasNoSideEffect`（并注明日志级别为代码级核对）；F-29 → `GXSSOServiceBehaviorTest#cookieMaxAgeAttributeAcceptsNonIntegerTypes`；随后用脚本提取 `Class#method` 与裸 `#method` 引用并逐一在 `leaf-base-sso/src/test/java` 中解析，要求 0 条无法解析 per FR-015 (partial)
---

## Phase 13: Convergence

> 由 `/speckit.converge` 于 2026-10-23（第四轮）追加。仅追加，不改写既有任务。

- [X] T090 统一"缓存不可用"对外响应为 **401 + 原因码 `cache-unavailable`**：修改 `specs/013-sso-module-audit/data-model.md` 第 55 行（校验规则 3）与第 66 行（终止矩阵"缓存不可用（未降级）"行的客户端表现）、`specs/013-sso-module-audit/contracts/sso-config-observability-contract.md` 第 81 行（改为"同上（401），`msg` 不含内部细节"）、`specs/013-sso-module-audit/research.md` D-13 理由段（去掉"500"）、`specs/013-sso-module-audit/quickstart.md` 第 49 行（改为"401 + 原因码 `cache-unavailable`，与是否降级无关"），改完用 `rg -n "\b500\b" specs/013-sso-module-audit` 确认仅剩历史证据描述 per FR-005 (contradicts)
- [X] T091 明确匿名请求的 Cookie 清理边界并为它补断言：在 `specs/013-sso-module-audit/contracts/sso-authorization-contract.md` §2 步骤 8 的"不得清 Cookie"后补充"（仅允许对已失效/无法解析的 Cookie 做幂等清理；请求未携带 Cookie 时不得写出 `Set-Cookie`）"，并在 `leaf-base-sso/src/test/java/cn/maple/sso/service/GXSSOServiceBehaviorTest.java` 新增 `anonymousLogoutWithoutCookieWritesNoSetCookie`（无 Token、无 Cookie 的 `clearLogin` → `response.getHeader("Set-Cookie") == null` 且仍返回 `true`），同步 `audit-report.md` §7.4 用例清单与用例总数 per FR-013 (partial)

---

## Phase 14: Convergence

> 由 `/speckit.converge` 于 2026-10-23（第五轮）追加。仅追加，不改写既有任务；本轮只按实测校正计数与状态，不改变任何行为契约。输入为 `/speckit.analyze` 的 17 条跨产物发现（C1/C2/C3/U1/U2/U3/CG1/CG2/AM1/AM2/I1/I2/I3/I4/I5/D1）。

- [X] T092 校正 `plan.md` 与 `audit-report.md` §8 追溯矩阵的规模陈述：结构树补 `GXSSOAuditReproTest`、`GXSSOTestApplication`、`GXSSOTestDoubles`、`GXHandlerMethodAnnotationUtilsTestAccess` 四个文件；统一为"新增 7 个测试类 + 3 个测试支撑类、修改 5 个测试类""修改 24 个生产文件、新增 3 个生产类型"；Performance Goals 补"实施期修订：新增用例数远超原 ≤ 30 目标（基数 40，现值以 `audit-report.md` §7.1 为唯一权威）" per FR-001 (contradicts)
- [X] T093 校正 `plan.md` 的 Constitution Check：III 补"内部判定型 API 返回空 `Dict`/布尔不产出对外响应"的边界说明；IV 与 VII 判定由 `PASS` 改为 **`CONDITIONAL`** 并指向 T102；Post-Phase 1 复评追加第五轮结论（IV/VII 未消解） per constitution: III/IV/VII (contradicts)
- [X] T094 记录 `GXSSOCaptchaValidator` 的实施期修订：在 `plan.md` 的结构树、`Scale/Scope`、`Structure Decision` 与 Complexity Tracking 中标注"未单独落地"，说明校验语义由 `AbstractCaptcha.verification`（`null` 不删 ticket、`put` 失败抛业务异常）+ `CaptchaStoreSession.consume`（同步一次性消费）承担，回归点由 `CaptchaRenderTest#nullCaptchaDoesNotInvalidateTicket`、`#storeFailureRaisesBusinessException`、`GXSSOConcurrencyTest#concurrentCaptchaVerificationConsumesOnce` 覆盖 per FR-025 (missing)
- [X] T095 统一权限矩阵组合数为 **16**（4 行注解 × 4 列配置）：修正 `spec.md` SC-004、`tasks.md` T024、`data-model.md` §9 测试矩阵、`audit-report.md` §4.2 标题与 §7.3 说明中不一致的"12" per SC-004 (contradicts)
- [X] T096 统一用例总数口径：`quickstart.md` §1 注明"`audit-report.md` §验证记录为唯一权威，tasks 中的 95/103 为当时值"，并在 T083/T087 加"当时值"标注 per FR-022 (contradicts)
- [X] T097 统一指标清单为 **9 项**（`parseSuccess`/`parseFailure`/`cacheHit`/`cacheMiss`/`denied`/`loginFailure`/`cacheFallback`/`invalidToken`/`activeTokens`）：修正 T007 原有的"10 个 `LongAdder`（含 `concurrentLoginConflict`）"，并把 `plan.md` 技术路径第 5 条的"提供 6 项口径"改为"9 项指标 + 6 项性能口径" per contracts: observability §4 (contradicts)
- [X] T098 消除默认值冲突与终止矩阵口径偏差：`spec.md` FR-019 把 `cacheExpires` 单列为"**唯一例外项**"（默认 `-1` → `7200`，显式 `-1` 保留并打 ERROR），其余默认值一律保持；SC-003 由"6 类 × 至少 4 个观测点"改为"≥7 类（含 `LOCAL_ONLY`）× 5 个观测点" per FR-019 (contradicts)
- [X] T099 更新 `tasks.md` §格式校验：任务总数 72 → 103、状态说明由"均为 `- [ ]`"改为"已完成 `[X]` / 待决 `[ ]`"，并把 ID 区间改为 T001-T103 per tasks: format (contradicts)
- [X] T100 在 `specs/013-sso-module-audit/audit-report.md` §6.5（逐修复项旧代码失败证据）补齐 F-30、F-36 的"旧代码失败 / 新代码通过"证据（F-30：未登录 AJAX 响应由手工 `Dict` + `getWriter()` 改为 `GXResultUtils.error`；F-36：`GXSSOService` 补 `default logout/attrSSOToken` 后可面向接口调用、不再向下转型），并为 F-33/F-37 增加"文档/注释类，不做用例"的豁免表；同步强化 `GXSSOServiceBehaviorTest#defaultHandlerReturnsAsciiUnauthorizedResponse`（断言 `Content-Type` 与 `code`/`msg`/`data` 三键）与新增 `GXSSOAuthFlowSpringBootTest#serviceInterfaceExposesLogoutAndAttrToken`，并更新 §8 追溯矩阵与 F-36 在 §3 的"无独立用例"表述 per FR-021 (partial)
- [X] T101 在 `leaf-base-sso/src/test/java/cn/maple/sso/web/interceptor/GXSSOInterceptorBehaviorTest.java` 新增 `capacityEvictsOldestInsteadOfClear`：设 `sso.config.capacity=4` 后对专用 `CacheBurstController` 的 5 个方法 × 3 个 tag 共 15 个新键做解析，断言 `cacheSize()==4`（旧实现"`size() >= 4096` 整体 `clear()`"时该断言为 0 而失败），并同步 `audit-report.md` §6.5/§7.2/§7.4 与 §8 per FR-020 (missing)
- [X] T102 **宪法裁决（已裁决：IV 走修订、VII 走实现回退）**：①IV 采用**宪法修订**——`.specify/memory/constitution.md` 升至 **v1.1.0（MINOR）**，§IV 新增"显式开关"作为类路径条件不可达时的等价互斥方式并附四条护栏（默认值保持既有行为 / 启动 INFO 打印生效源 / 互斥与默认值测试 / 元数据与手册登记），同时澄清"单模块内部开关沿用模块既有前缀"，并在 §修订记录登记动机与依据；`spec.md` FR-009 与 Clarifications 同步承认第三种手段。②VII 采用**实现回退**——`GXSSOTokenState` → `GXSSOTokenStateEnums`（符合 `GX<概念>Enums`），`GXSSOInterceptorOrder` 的 `AUTHORIZATION`/`PERMISSION` 并入既有 `GXSSOConstant`（`AUTHORIZATION_ORDER`/`PERMISSION_ORDER`）并删除该类，同步 `AGENTS.md` §3、`docs/leaf-base-sso开发手册.md`、`data-model.md`、`audit-report.md` 与 `research.md` D-07；宪法 v1.1.0 §VII 亦为 `Validator` 补角色（仓库既有 3 个 `Validator` 类型）。③`plan.md` 的 IV/VII 判定改回 `PASS`；回归：`mvn -o -pl leaf-base-sso -am clean install -DskipTests` 通过、`mvn -o -pl leaf-base-sso test` = **110 通过 / 0 失败** per constitution: IV/VII (contradicts)
- [X] T103 补齐 FR-020 的判定依据：在 `specs/013-sso-module-audit/data-model.md` §9.1 新增"受影响路径 × 5 类用例（成功/失败/异常/空值/边界）"矩阵（12 条路径，逐格给出真实用例名，`—` 表示该维度不适用），覆盖 T031（`ResponseCookie` 属性）、T041/T101（容量淘汰）、T043（`width < length`）等未被显式点名的路径；并把 T070/T071 归入"宪法附加门禁（PR 说明 / 工作区整洁）"、在 `audit-report.md` §8 追溯矩阵后登记为流程类任务，消除无映射任务；同时补 `GXSSOInterceptorBehaviorTest#blankUriIsDeniedWhenUriPermissionModeEnabled`（URI 模式空 URI fail-closed，落实 FR-006），并修正 T024 中对不存在用例的引用 per FR-020 (partial)

---

## Phase 15: Convergence

> 由 `/speckit.converge` 于 2026-10-23（第六轮）追加。仅追加，不改写既有任务。本轮按"代码现状 vs spec/plan/tasks/宪法"核对（不依赖 git 差异），基线为 `mvn -o -pl leaf-base-sso clean test` = 113 通过 / 0 失败。

- [X] T104 改造 `leaf-base-sso/src/main/java/cn/maple/sso/web/support/GXLoginUserHandlerMethodArgumentResolver.java` 第 34–40 行：`resolveUserId` 返回 `null`（无 Token、无 `SSO_TOKEN_ATTR`、手写头无法解析）时不再 `return null`，改为抛 `GXBusinessException`（与"用户不存在"分支语义一致）；若坚持"返回空对象"方案，则必须返回不可变空实例并在 `@GXLoginUserAnnotation` 的 Javadoc 与 `docs/leaf-base-sso开发手册.md` §3.4 显式声明——二者择一，不得继续返回 `null`；把 `leaf-base-sso/src/test/java/cn/maple/sso/web/support/GXLoginUserHandlerMethodArgumentResolverTest.java#malformedFallbackHeaderReturnsNull` 改写为断言抛业务异常，并新增"完全无 Token 的匿名请求同样抛异常"用例（旧实现返回 null → 新实现抛异常）；同步 `specs/013-sso-module-audit/data-model.md` §9.1 第 9 行与 `audit-report.md` §6.5 的 F-31 行 per FR-017 (contradicts)
- [X] T105 改造 `leaf-base-sso/src/main/java/cn/maple/sso/utils/GXIpHelperUtil.java`：把 `LOCAL_IP`/`HOST_NAME`（第 13、15 行）由 `public static` 可变字段改为 `private static final` + 只读访问器（`localIp()`/`hostName()`），消除可被任意代码改写的全局状态；`isLocalIp`（第 61–76 行）复用类初始化时的解析结果或按主机名缓存，避免每次调用执行 `InetAddress.getLocalHost()` + `getAllByName`；同步改写 `leaf-base-sso/src/test/java/cn/maple/sso/utils/GXSSOUtilsBehaviorTest.java` 第 114 行（现直接断言 `GXIpHelperUtil.LOCAL_IP`）与任何引用该字段的代码，并确认 `localRequestIpReusesResolvedAddress` 仍锁定"热路径不再解析主机名" per FR-010 (partial)
- [X] T106 落实 `GXSSOProperties` 的"启动后只读"：在 `leaf-base-sso/src/main/java/cn/maple/sso/properties/GXSSOProperties.java` 增加冻结标记（`freeze()` + 冻结后各 `setXxx` 抛 `GXBusinessException`），由 `leaf-base-sso/src/main/java/cn/maple/sso/utils/GXSSOHelperUtil.java#setSsoConfig` 负责"解冻旧实例 / 冻结新实例"，并在 `leaf-base-sso/src/test/java/cn/maple/sso/service/GXSSOServiceBehaviorTest.java` 补两条用例（冻结后 `setXxx` 抛异常、`setSsoConfig` 仍可整体替换）；若评估后判定该设计成本过高而放弃，则必须在 `specs/013-sso-module-audit/data-model.md` §7 共享状态清单第 125 行与 `plan.md` 的 Phase 1 决策中显式记录"冻结标记已放弃及理由"，不得让文档继续声称已实现 per FR-010 (partial)
- [X] T107 在 `docs/leaf-base-sso开发手册.md` 第 89 行（URL 级权限验证段）补充：`permissionUri=true` 且 `requestURI` 为空/空白时**一律拒绝（fail-closed）**，并指向用例 `GXSSOInterceptorBehaviorTest#blankUriIsDeniedWhenUriPermissionModeEnabled`；同步 `specs/013-sso-module-audit/contracts/sso-authorization-contract.md` 的 URI 模式小节，使"`uri == null` 时 MUST 明确 fail-closed 或 fail-open 并在文档声明"可核对 per FR-006 (partial)
- [X] T108 消除测试中散落的反射搭建：当前共 18 处 `ReflectionTestUtils.setField(GXApplicationContextSingleton.INSTANCE, "applicationContext", …)`（`GXLoginUserHandlerMethodArgumentResolverTest`、`GXSSOAuditReproTest`、`GXSSOCacheContractTest`、`GXSSOServiceBehaviorTest` 等）；把反射收敛到 `leaf-base-sso/src/test/java/cn/maple/sso/support/GXSSOTestDoubles.java` 的唯一助手（如 `withApplicationContext(Object... beans)`，内部一处反射并注释原因），各测试类改为调用该助手并各自 `@AfterEach` 复原原上下文；改完运行 `mvn -o -pl leaf-base-sso clean test` 确认 113 条仍全绿 per FR-023 (partial)
- [X] T109 更新 `specs/013-sso-module-audit/data-model.md` §6 配置契约表第 114 行：`sso.config.sign-key` / `sign-algorithm` / `rsa-*` 的"仅 getter 抛异常"已不成立（`GXSSOProperties#getSignKey` 第 223–225 行返回 `null`，其余字段由 Lombok 生成），改为"保留但无生产读取；`getSignKey()` 返回 `null` 并带 Javadoc 说明"；同步核对 `audit-report.md` §9 与手册配置键表中的同名表述 per SC-008 (partial)

---

## Phase 16: Convergence

> 由 `/speckit.converge` 于 2026-10-23（第七轮）追加。仅追加，不改写既有任务。基线：`mvn -o -pl leaf-base-sso clean test` = 116 通过 / 0 失败；109 个既有任务全部 `[X]`。本轮全量核对 FR-001~FR-026、SC-001~SC-009、US1~US6 验收场景、20 项计划决策与宪法 I~VII，仅余 2 条产物内一致性缺口。

- [X] T110 修正 `docs/leaf-base-sso开发手册.md` §3.4 与实现的矛盾并锁定边界：① 手册第 320 行的 `@GXLoginUserAnnotation Dict currentUser` 示例不成立——`GXLoginUserHandlerMethodArgumentResolver#supportsParameter`（第 28–32 行）只接受 `GXUserInfoDto` 及其子类，`Dict` 参数会被 Spring 当作普通模型参数绑定（注入空对象而非 Token，且不报错）；② 手册第 329 行的 `@GXLoginUserAnnotation(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME) Long userId` 无法编译——`GXLoginUserAnnotation`（`annotation/GXLoginUserAnnotation.java`）未声明任何元素（无 `value()`），所谓"注入特定字段"并不存在；③ 手册第 337 行的自定义 `LoginUser` 示例已在注释中声明需自建 resolver，保留但需与 ①②统一表述。改法：把示例统一为 `GXUserInfoDto`（或用户自定义子类），删除"特定字段注入"与 `value()` 用法，并明确"该注解只支持 `GXUserInfoDto` 子类；其他类型请在自有 `HandlerMethodArgumentResolver` 中实现"；随后在 `leaf-base-sso/src/test/java/cn/maple/sso/web/support/GXLoginUserHandlerMethodArgumentResolverTest.java` 增加 `supportsParameterAcceptsOnlyUserInfoDto`（`GXUserInfoDto` 参数返回 true，`Dict`/`Long`/无注解参数返回 false），并同步 `specs/013-sso-module-audit/data-model.md` §9.1 第 9 行 per FR-013 (contradicts)
- [X] T111 修复 `specs/013-sso-module-audit/audit-report.md` 附录 B「保留断言清单」中的失效引用与过期计数：第 511 行 P-06 指向已删除的 `GXLoginUserHandlerMethodArgumentResolverTest#malformedFallbackHeaderReturnsNull`（T104 已改名为 `#malformedFallbackTokenThrowsBusinessException`），应改为 P-06 的真实保护点（注解查找缓存：`GXSSOInterceptorBehaviorTest#annotationLookupFindsAndCachesMergedAnnotations`），或注明"P-06 与用户注入无关，原映射有误"；第 515 行 P-10 的"`GXSSOServiceBehaviorTest` 全部 15 条 / `GXSSOUtilsBehaviorTest` 6 条"应标注为"原始 40 条基线（现分别为 26 / 11 条，保留断言未删）"。改完用脚本提取 `Class#method` 引用并逐条在 `leaf-base-sso/src/test/java` 中解析，要求 0 条无法解析（当前仅此 1 条） per FR-022 (partial)

---

## Phase 17: Review Follow-up

> 由 `/speckit.review.run` 于 2026-10-23（综合评审：code / comments / tests / errors / types / simplify）追加并已执行。基线 117 用例 → 121 用例，全部 `[X]`。

- [X] T112 修复评审 Important #1 与 #3：① `leaf-base-sso/src/main/java/cn/maple/sso/service/GXAbstractSSOService.java#logout` 在 `logout-url` 为空时不再写纯文本 `"sso.yml Must include: sso.config.logout-url"`，改为 `GXResultUtils.ok("Logged out")` + `application/json;charset=UTF-8`（宪法 III），并补 `GXSSOServiceBehaviorTest#logoutWithoutLogoutUrlReturnsUnifiedJson`；② 把 `illegal-url` / `nothing-annotation-pass` 绑定为 `GXSSOProperties` 字段（默认 `null` / `true`，保持 FR-019 默认行为），`GXSSOPermissionInterceptor` 改为"显式 setter 优先、否则读配置"，同步 `META-INF/additional-spring-configuration-metadata.json`、手册配置表与 `data-model.md` §6，并补 `GXSSOInterceptorBehaviorTest#nothingAnnotationPassIsReadFromConfiguration`、`#nothingAnnotationPassPrefersExplicitSetterOverConfiguration` 与 `GXSSOConfigSourceTest#defaultValuesMatchContract` 的默认值断言 per FR-013 (contradicts)
- [X] T113 修复评审 Important #2：重写 `docs/leaf-base-sso开发手册.md` 验证码段落——删除不存在的 `GXSSOHelperUtil.getSSOCache()` 与 `GXSSOConstant.CACHE_CAPTCHA_CODE_PREFIX` 引用，改为说明默认存储为 `ICaptchaStore` → `CaptchaStoreSession`（`HttpSession`）、默认 60 秒、一次性消费、`put` 失败抛 `GXBusinessException`、不覆盖 `maxInactiveInterval`，并给出替换存储的注入方式；同步修正"行为型配置不在 Properties 中"的表述 per FR-013 (contradicts)
- [X] T114 修复评审 Suggestions：① `validateSameSite` 校验取值仅允许 `Lax/Strict/None`（启动期与登录期共用），补 `GXSSOServiceBehaviorTest#unsupportedSameSiteValueIsRejected`；② `currentProfile()` 在没有 active profile 时回退 `Environment#getDefaultProfiles()`，避免仅用 default profile 部署 `prod` 时绕过密钥红线；③ 白名单过滤改为"非空白即接受"，不再静默丢弃单字符模式（根路径 `/`）；④ `GXSSOCache#get` 在 `userId` 非法时改为标记 `ABSENT`（原 `EXPIRED` 语义不准）；⑤ 删除未被引用的 `GXSSOTokenStateEnums#isAuthenticated()`/`#isCacheRelated()` 与随之无用的常量 per FR-010 (partial)

---

## Phase 18: Comprehensive Test Follow-up

> 由「全面测试」于 2026-10-23 追加并已执行。触发点：`mvn -o -pl leaf-base-sso -am clean test` 首次把上游 `leaf-base-framework` 一并纳入回归。

- [X] T115 修复 T009 引入的框架侧回归：`leaf-base-framework/src/main/java/cn/maple/core/framework/web/config/GXWebMvcConfigurer.java#addInterceptors` 把 `OrderUtils.getOrder(Class)` 的 `@Nullable Integer` 直接拆箱，未标注 `@Order` 的拦截器（含 Mockito mock）触发 `NullPointerException`（`GXWebMvcConfigurerTest#addInterceptorsRegistersUniqueInterceptorInstancesByBeanNameOrder` 报错，550 条中 1 条失败；此前只跑 `-pl leaf-base-sso` 未暴露）。修复为 null-safe 的 `resolveOrder(...)`：`@Order` → `Ordered` → `Ordered.LOWEST_PRECEDENCE`，并以 bean 名作为稳定 tie-break；新增 `GXWebMvcConfigurerTest#addInterceptorsHonoursOrderAnnotationBeforeBeanNameOrder`（`@Order` 优先于 bean 名排序）与两个内部测试拦截器；验证：`mvn -o -pl leaf-base-sso -am clean test` → framework 551 通过 / sso 121 通过 / BUILD SUCCESS，`mvn -o -pl leaf-base-framework,leaf-base-sso clean install -DskipTests` 通过 per FR-015 (contradicts)

---

## Phase 19: Convergence

> 由 `/speckit.converge` 于 2026-10-23（第八轮）追加。仅追加，不改写既有任务。基线：`mvn -o -pl leaf-base-sso -am clean test` = framework 551 / sso 121 全绿，115 个既有任务全部 `[X]`。本轮全量核对 FR-001~FR-026、SC-001~SC-009、US1~US6 验收场景、20 项计划决策与宪法 v1.1.0，余 4 条产物内缺口（1 MEDIUM + 3 LOW），无功能性与宪法级缺口。

- [X] T116 补齐 `illegal-url` 重定向分支的用例：在 `leaf-base-sso/src/test/java/cn/maple/sso/web/interceptor/GXSSOInterceptorBehaviorTest.java` 新增 `configuredIllegalUrlRedirectsInsteadOfForbidden`——用 `new GXSSOProperties().setIllegalUrl("https://sso.example.com/403")` 走**配置绑定**（不调 setter），对无 Token 的未注解 handler 执行 `GXSSOPermissionInterceptor#preHandle`，断言 `response.getStatus()==302` 且 `response.getRedirectedUrl()` 为该地址（与 `permissionInterceptorReturnsAsciiForbiddenMessage` 的 403 分支形成对照）；再补一条"显式 `setIllegalUrl(...)` 覆盖配置值"的断言，锁定与 `nothingAnnotationPass` 一致的"setter 优先"优先级；同步 `data-model.md` §9.1 第 3 行（权限判定路径的"边界"列）、`audit-report.md` §7.2/§7.3 的用例数与 §9.1 说明 per FR-020 (partial)
- [X] T117 修正空洞断言：`leaf-base-sso/src/test/java/cn/maple/sso/service/GXSSOServiceBehaviorTest.java#invalidSameSiteCombinationIsRejected`（第 411–419 行）只设 `cookieSameSite=None` + `cookieSecure=false` 而未给 `GXSSOProperties` 设 `cache`，`setCookie` 会先在"缓存不可用"分支抛 `GXBusinessException`，**从未执行到 SameSite 校验**，断言因此恒成立；改为在配置上补 `setCache(new GXSSOCache() { })`（或断言异常消息包含 `SameSite`），使其真正锁定 `None` + `!Secure` 被拒绝，并与 `unsupportedSameSiteValueIsRejected` 形成"组合非法 / 取值非法"两条互补断言 per FR-022 (partial)
- [X] T118 把 T112 新增的两个配置键纳入元数据守护：`leaf-base-sso/src/test/java/cn/maple/sso/properties/GXSSOConfigSourceTest.java` 的 `NEW_KEYS`（第 30–33 行）增补 `sso.config.nothing-annotation-pass`、`sso.config.illegal-url`，使宪法 IV「新增键必须登记 `additional-spring-configuration-metadata.json`」对这两个键同样有断言保护（当前仅覆盖 `source`/`cache-fallback-enabled`/`capacity`） per SC-008 (partial)
- [X] T119 消除用例总数的二次漂移：`specs/013-sso-module-audit/tasks.md` 中 T083/T087/T092 仍写"最终 113"（现为 121），而该表述是**当前态**声明、会被每轮新增用例推翻；改为"当时值 X；现值以 `audit-report.md` §7.1 为唯一权威"，并在 `audit-report.md` §7.4/§7.6 注明"用例总数只在本文件维护"，避免历史任务被反复回改 per FR-022 (partial)