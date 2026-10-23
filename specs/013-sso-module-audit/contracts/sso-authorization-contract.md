# 契约 2：拦截链与权限判定（sso-authorization-contract）

**适用范围**：`GXSSOAuthorizationInterceptor`、`GXSSOPermissionInterceptor`、`GXHandlerMethodAnnotationUtils`、`GXUrlWhiteListsConfigProperties`、`GXWebMvcConfigurer`（注册顺序）。
**稳定性**：本契约描述修复后的目标语义；`[变更]` 条目属安全修正，需在 PR 与手册中声明。

## 1. 拦截链顺序（硬约束）

| 顺序 | 拦截器 | `@Order` | 职责 |
| --- | --- | --- | --- |
| 1 | `GXSSOAuthorizationInterceptor` | `100` | 登录态（未登录 → 401 / 跳登录页） |
| 2 | `GXSSOPermissionInterceptor` | `200` | 权限（无权限 → 403 / 跳 `illegal-url`） |

**不变式**：
1. 顺序必须由 `@Order` 显式声明，注册端按 `AnnotationAwareOrderComparator` 排序（`[变更]`，D-07，框架 5 行）。
2. 未标注 `@Order` 的第三方 `GXAuthorizationInterceptor` 必须排在已标注者之后，且相互之间保持原 bean 名字典序（稳定排序）。
3. 权限拦截器**不得**在登录拦截器之前执行（否则未登录请求会得到 403 而非 401）。

## 2. 登录拦截器判定顺序

| 步 | 条件 | 结果 |
| --- | --- | --- |
| 1 | `DispatcherType.ERROR` 或存在 `ERROR_REQUEST_URI` | 放行（P-07） |
| 2 | `OPTIONS` 或 handler 非 `HandlerMethod` | 放行 |
| 3 | URL 命中白名单 | 放行 |
| 4 | 方法/类上有 `@GXIgnoreLoginIntercept` | 放行 |
| 5 | 方法/类上有 `@GXHttpInvokerAuthToken` | 放行 |
| 6 | 自定义 `GXAuthorizationInterceptorService.interceptor()` 返回 `true` | 放行 |
| 7 | Token 状态为 `ACTIVE` / `LOCAL_ONLY` | 放行，写入请求属性 |
| 8 | 其它状态 | 拒绝：AJAX → 401 JSON；普通 → 跳 `login-url`（空则 401 JSON） |

**不变式**：
- 步骤 8 必须**零副作用**：不得写缓存、不得删除会话键、不得调用插件 `logout`、不得写 INFO 日志、不得清除**有效**登录 Cookie（`[变更]`，F-13/RV-04/RV-05）；仅允许对 Cookie 做幂等清理，请求未携带 Cookie 时不得写出 `Set-Cookie`。该路径由 `GXSSOServiceBehaviorTest#anonymousLogoutWithoutCookieWritesNoSetCookie`、`GXSSOAuthFlowSpringBootTest#rejectedParseableTokenPageRequestHasNoSideEffect`（可解析但校验失败的 Token）与 `#rejectedRequestLogsNoInfo` 锁定。
- 响应体必须由 `GXResultUtils.error(...)` 构造，字段固定 `code`/`msg`/`data`（`[变更]`，F-30）。
- 未登录的日志为 DEBUG（按 reason 区分），`denied`/`cacheMiss` 走指标（`[变更]`，F-27）。

## 3. 权限拦截器判定顺序（唯一优先级链）

| 优先级 | 条件 | 是否需要 Token | 结果 |
| --- | --- | --- | --- |
| 1 | `@GXIgnoreLoginIntercept` | 否 | 放行 |
| 2 | `@GXPermissionAnnotation(action = Skip)` | 否 | 放行 |
| 3 | `permission-uri = true` | 是 | `isPermitted(token, uri)` **终局判定**（`uri == null` → 拒绝） |
| 4 | 有注解且 `action = Normal` 且 `value` 非空 | 是 | `isPermitted(token, value)` 终局判定 |
| 5 | 无注解 | 由 `nothing-annotation-pass` 决定 | `true` → 放行；`false` → 拒绝 |

**不变式**：
1. 任何分支**不得**在"判定失败"后回落到后续优先级（`[变更]`，F-01：现状 URI 失败会落到 5 并因默认 `true` 放行）。
2. 优先级 3 与 4 互斥：URI 模式启用时，注解 `value` 不参与判定（避免两套模型同时生效）。
3. `nothing-annotation-pass` 只影响优先级 5，不得影响 1–4。
4. `requestURI` 为空或空白字符串时与 `uri == null` 同等处理（fail-closed，直接 403），由 `GXSSOInterceptorBehaviorTest#blankUriIsDeniedWhenUriPermissionModeEnabled` 锁定；该行为见 `docs/leaf-base-sso开发手册.md` §3.x「URL级权限验证」。
4. 拒绝时：AJAX → 403 JSON（`GXResultUtils.error`）；普通 → `illegal-url` 非空则 302，否则 `sendError(403)`。
5. 授权器（`GXSSOAuthorization`）缺失时抛 `GXBusinessException`（现状保持），不得静默放行。

## 4. 白名单匹配

| 规则 | 目标语义 |
| --- | --- |
| 路径来源 | `UrlPathHelper.getPathWithinApplication(request)`（已剥离 context-path）（`[变更]`，F-14） |
| 匹配器 | `AntPathMatcher`，支持 `*` / `**` / `{var}` |
| 兼容 | 同时尝试"应用内路径"与"原始 `requestURI`"，命中其一即放行 |
| 空列表 / 未装配 | 不过滤（等价于"无白名单"），且启动时 INFO 提示条数 |
| 大小写 | 区分大小写（与 Spring 一致）；如需忽略大小写由配置侧统一小写 |

**不变式**：白名单只用于**登录拦截**，不得替代权限判定（白名单 URL 仍需通过权限链，除非带 `Skip` 注解或 `nothing-annotation-pass=true`）。

## 5. 注解解析缓存

| 维度 | 目标语义 |
| --- | --- |
| 缓存内容 | 判定结果（是否存在 / `action` / `value` 字符串），**不缓存 `Annotation` 实例**（`[变更]`，D-11） |
| 键 | `beanType.getName() + "#" + method.toGenericString() + "#" + annotationType.getName()`（字符串，不持有 `Class`/`Method`） |
| 容量 | 4096，超限按 FIFO 淘汰（不整体清空） |
| 覆盖范围 | 方法 → 桥接方法 → 接口方法 → 类（含父类与接口）（P-06 保持） |
| 负缓存 | 允许（`Optional.empty` 等价语义），避免重复反射 |

**不变式**：缓存不得跨类加载器泄漏；`clear` 不作为容量控制手段（`[变更]`，F-20）。

## 6. 未登录 / 无权限响应矩阵

| 场景 | 状态码 | 响应体 | 是否跳转 |
| --- | --- | --- | --- |
| 未登录 + AJAX（`X-Requested-With` 或 `X-RESPONSE-TYPE: api`） | 401 | `{"code":401,"msg":"...","data":null}` | 否 |
| 未登录 + 普通请求 + `login-url` 非空 | 302 | 空 | 是（带 `ReturnURL`） |
| 未登录 + 普通请求 + `login-url` 为空 | 401 | 同上 | 否 |
| 无权限 + AJAX | 403 | `{"code":403,"msg":"Forbidden","data":null}` | 否 |
| 无权限 + 普通 + `illegal-url` 非空 | 302 | 空 | 是 |
| 无权限 + 普通 + `illegal-url` 为空 | 403 | `{"code":403,"msg":"Forbidden","data":null}` | 否 |
| 缓存不可用（未降级） | 401 | `{"code":401,"msg":"Please login","data":null}`，原因码 `cache-unavailable` | 否 |

**不变式**：`X-Requested-With` 与 `X-RESPONSE-TYPE` 两种 AJAX 判定口径必须统一到一个内部方法（`[变更]`，F-30），不得一处用一个。

## 7. 兼容性条目汇总

| 变更 | 触发条件 | 迁移 |
| --- | --- | --- |
| 拦截链按 `@Order` 排序 | 自定义 `GXAuthorizationInterceptor` 子类 | 需要固定位置的加 `@Order` |
| URI 模式终局判定 | 同时使用 URI 授权与注解授权的下游 | 二选一；同时存在时 URI 优先 |
| `uri == null` 拒绝 | 极端容器实现（`getRequestURI()` 为 null） | 视为异常请求，拒绝是安全方向 |
| 白名单按应用内路径匹配 | 依赖"白名单必须带 context-path"的下游 | 两种写法都兼容（同时匹配） |
| 注解缓存改为结果缓存 | 依赖"缓存返回同一 `Annotation` 实例"的下游（无此契约） | 无 |
