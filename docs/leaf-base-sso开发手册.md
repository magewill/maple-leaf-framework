# Maple Leaf Framework SSO模块开发手册（增强版）

## 1. 模块概述

`leaf-base-sso`模块是Maple Leaf Framework中提供的单点登录(SSO)解决方案，为应用系统提供了全面、安全、可扩展的用户认证、授权和会话管理功能。该模块旨在简化SSO集成，提供灵活的配置选项和强大的安全保障，以适应多样化的业务需求。

### 1.1 主要特性

- **全面的认证与会话管理**：支持用户登录、注销、Token生成与验证、Cookie管理、会话状态维护、分布式会话支持。
- **灵活的权限控制**：提供基于URL路径和方法注解（`@GXPermissionAnnotation`）两种精细化的权限验证方式。
- **强大的安全防护**：内置多种安全机制，如Token加密、防会话固定攻击、CSRF防护（需配合前端）、IP绑定、浏览器指纹识别等。
- **可扩展的插件系统**：通过`GXSSOPlugin`接口，允许开发者在登录、Token验证、登出等关键节点注入自定义逻辑。
- **验证码支持**：集成图片验证码（静态/GIF动态）功能，增强登录安全性，支持多种字符类型和干扰元素。
- **用户信息注入**：通过`@GXLoginUserAnnotation`注解，方便地在Controller方法中获取当前登录用户信息。
- **配置驱动**：多数功能行为可通过`GXSSOProperties`进行配置，易于调整和维护。
- **详细的日志记录**：关键操作均有日志输出，便于问题排查和安全审计。

### 1.2 模块依赖

```xml
<dependencies>
    <dependency>
        <groupId>cn.maple.framework</groupId>
        <artifactId>leaf-base-framework</artifactId>
        <version>${project.parent.version}</version>
    </dependency>
    <dependency>
        <groupId>cn.maple.framework</groupId>
        <artifactId>leaf-base-nacos</artifactId>
        <version>${project.parent.version}</version>
        <!-- Nacos主要用于配置管理和服务发现，如果SSO配置不依赖Nacos，此依赖可能为可选 -->
    </dependency>
</dependencies>
```

## 2. 核心组件与工作流程

### 2.1 核心类概览

- **`GXSSOHelperUtil`**: SSO功能的统一入口（门面类），提供静态方法进行登录、注销、获取Token等操作。它负责协调其他核心组件。
- **`GXAbstractSSOService` (及其实现如 `GXConfigurableAbstractSSOServiceImpl`)**: SSO核心服务类，处理登录、注销、Token生成与解析、Cookie设置等具体逻辑。
- **`GXSSOProperties`**: SSO配置属性类，集中管理所有SSO相关的配置项。
- **`GXSSOCache`**: Token缓存接口，负责Token的存储、获取和删除。默认实现可能基于内存，可替换为Redis等分布式缓存。
- **`GXTokenConfigService`**: Token配置服务接口，用于定义Token的缓存键生成规则、缓存桶名称以及用户登录状态的业务校验逻辑。
- **`GXSSOAuthorizationInterceptor`**: 登录授权拦截器，校验用户是否登录，处理白名单、`@GXIgnoreLoginIntercept`注解等。
- **`GXSSOPermissionInterceptor`**: 权限拦截器，校验已登录用户是否拥有访问目标资源的权限。
- **`GXSSOAuthorization`**: 权限验证接口，由业务系统实现，判断用户是否拥有特定权限。
- **`GXSSOPlugin`**: SSO插件接口，允许在SSO流程的关键节点扩展自定义逻辑。
- **`ImageCaptcha` (继承自 `AbstractCaptcha`)**: 图片验证码生成和校验工具。
- **`GXLoginUserAnnotation`**: 用于在Controller方法参数上标记，以便自动注入当前登录用户信息。
- **`GXPermissionAnnotation`**: 用于在Controller方法上标记，声明访问该方法所需的权限。

### 2.2 认证流程详解

1.  **用户请求登录**：用户提交用户名、密码（可能还有验证码）到登录接口。
2.  **登录接口处理**：
    *   （可选）校验验证码（使用`ImageCaptcha`）。
    *   校验用户凭证（用户名、密码）。
    *   如果凭证有效，调用`GXAbstractSSOService`的登录方法（如`login`或通过`GXSSOHelperUtil.setCookie`间接触发）。
3.  **`GXAbstractSSOService`处理登录**：
    *   执行`GXSSOPlugin`的`login`前置插件。
    *   生成SSO Token（一个包含用户ID、用户名、角色、登录时间等信息的`Dict`对象）。
    *   调用`GXTokenConfigService`生成Token缓存键。
    *   将Token信息通过`GXSSOCache`存入缓存，设置活动超时和绝对超时时间。
    *   将加密后的Token字符串设置到客户端Cookie中（通过`GXHttpUtil`和`GXAuthCodeUtils`）。
    *   执行`GXSSOPlugin`的`login`后置插件（如果设计有）。
4.  **后续请求验证**：
    *   客户端请求携带SSO Token的Cookie访问受保护资源。
    *   `GXSSOAuthorizationInterceptor`拦截请求。
        *   检查是否为白名单URL或标记了`@GXIgnoreLoginIntercept`，若是则放行。
        *   尝试从Cookie中获取Token字符串（通过`GXHttpUtil`）。
        *   若Token字符串存在，解密并解析为`Dict`对象（通过`GXAuthCodeUtils`）。
        *   调用`GXSSOCache.get()`（内部会调用`GXTokenConfigService.checkLoginStatus()`和`GXTokenConfigService.getEfficaciousToken()`）验证Token的有效性（包括缓存是否存在、是否过期、用户状态是否正常等）。
        *   执行`GXSSOPlugin`的`validateToken`插件。
        *   如果验证通过，将解析后的Token对象存入`HttpServletRequest`的属性中，供后续使用，然后放行到`GXSSOPermissionInterceptor`或业务处理器。
        *   如果验证失败（Token无效、过期、用户被禁用等），根据请求类型（AJAX/普通）通过`GXSSOHandler`（默认为`GXSSODefaultHandler`）返回未授权响应或重定向到登录页。

### 2.3 权限控制流程

1.  **请求到达`GXSSOPermissionInterceptor`**（在`GXSSOAuthorizationInterceptor`之后执行）。
2.  从`HttpServletRequest`属性中获取已验证的Token对象。
3.  **检查权限注解**：
    *   获取目标Controller方法上的`@GXPermissionAnnotation`。
    *   如果注解存在：
        *   若`action`为`GXAction.Skip`，则直接放行。
        *   否则，获取注解中定义的权限字符串（`value`）。调用`GXSSOAuthorization.isPermitted(token, permissionValue)`进行验证。
    *   如果注解不存在：
        *   根据`GXSSOProperties.isNothingAnnotationPass()`的配置决定是否放行。若为`true`则放行，否则视为无权限。
4.  **（可选）URL级权限验证**：如果`GXSSOProperties.isPermissionUri()`为`true`，还会调用`GXSSOAuthorization.isPermitted(token, requestURI)`进行基于请求URI的权限验证。该模式下 URI 判定是**终局**：`requestURI` 为空/空白时一律拒绝（fail-closed），不会回落到 `nothingAnnotationPass`；对应用例 `GXSSOInterceptorBehaviorTest#blankUriIsDeniedWhenUriPermissionModeEnabled`。
5.  **处理结果**：
    *   如果权限验证通过，则放行到业务处理器。
    *   如果权限验证失败，调用`GXSSOPermissionInterceptor.unauthorizedAccess()`方法，根据请求类型（AJAX/普通）返回403错误或重定向到配置的`illegalUrl`。

### 2.4 注销流程

1.  用户请求注销接口，或管理员触发强制下线。
2.  调用`GXAbstractSSOService.clearLogin(request, response)`或`GXAbstractSSOService.kickLogin(userId)`。
3.  **`GXAbstractSSOService`处理注销**：
    *   从请求属性、Header 或 Cookie 中解析 Token（`GXSSOSupportService.getSSOTokenFromCookie`）。
    *   仅在**已登录**时执行后续步骤：调用`GXSSOCache.delete()`删除服务端 Token，并执行 `GXSSOPlugin` 的 `logout` 插件。
    *   **未登录/重复注销**（Token 为空）：跳过缓存删除与插件循环，只做幂等的 Cookie 清理并返回 `true`，不产生任何业务副作用。
    *   清除客户端 Cookie（`GXCookieHelperUtil.clearCookieByName`，Cookie 不存在时不写响应头）。

## 3. 核心功能详解

### 3.1 用户认证

#### 3.1.1 登录 (`GXSSOHelperUtil.setCookie`)

当业务系统验证用户凭证成功后，应准备一个包含用户核心信息的`Dict`对象，然后调用`GXSSOHelperUtil.setCookie`方法。此方法会触发SSO服务生成Token、存入缓存并设置到客户端Cookie。

```java
// 业务代码中处理登录逻辑
@PostMapping("/login")
public Result<String> login(HttpServletRequest request, HttpServletResponse response, @RequestBody LoginRequest loginRequest) {
    // 1. 校验验证码 (如果启用)
    // ... GXSSOHelperUtil.getCaptcha(request).verification(...)

    // 2. 校验用户名密码
    UserModel user = userService.findByUsername(loginRequest.getUsername());
    if (user == null || !passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
        return Result.failed("用户名或密码错误");
    }
    if (!user.isEnabled()) {
        return Result.failed("用户已被禁用");
    }

    // 3. 准备SSO Token信息
    Dict ssoTokenInfo = Dict.create()
        .set(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, user.getId()) // 必须包含用户ID
        .set("username", user.getUsername())
        .set("roles", userService.getUserRoles(user.getId())) // 用户角色或其他权限相关信息
        .set("loginTime", System.currentTimeMillis());

    // 4. 设置SSO Cookie，完成登录
    // 第三个参数 true 表示进行防会话固定攻击处理（重新生成会话标识，如果SSO服务支持）
    GXSSOHelperUtil.setCookie(request, response, ssoTokenInfo, true);

    return Result.success("登录成功");
}
```

#### 3.1.2 注销 (`GXSSOHelperUtil.clearLogin`)

```java
@PostMapping("/logout")
public Result<String> logout(HttpServletRequest request, HttpServletResponse response) {
    GXSSOHelperUtil.clearLogin(request, response);
    return Result.success("注销成功");
}
```

#### 3.1.3 强制下线 (`GXSSOHelperUtil.kickLogin`)

此功能通常用于管理员后台，强制某个用户下线。

```java
// 管理员操作，强制用户ID为123的用户下线
GXSSOHelperUtil.kickLogin(123L);
```

### 3.2 权限控制

#### 3.2.1 注解式权限 (`@GXPermissionAnnotation`)

`@GXPermissionAnnotation`用于Controller方法上，声明访问该方法所需的权限。

- **`value`**: 权限字符串，例如 `"user:view"`, `"order:edit"`。具体格式和含义由`GXSSOAuthorization`的实现类定义。
- **`action`**: `GXAction`枚举，默认为`GXAction.Normal`（进行权限验证）。可设置为`GXAction.Skip`跳过权限验证，适用于公共接口。

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    // 需要 "user:list" 权限
    @GetMapping
    @GXPermissionAnnotation("user:list")
    public Result<List<UserDTO>> listUsers() {
        return Result.success(userService.getAllUsers());
    }

    // 需要 "user:create" 权限
    @PostMapping
    @GXPermissionAnnotation("user:create")
    public Result<UserDTO> createUser(@RequestBody UserCreateDTO dto) {
        return Result.success(userService.createUser(dto));
    }

    // 公开接口，跳过权限验证
    @GetMapping("/public-info")
    @GXPermissionAnnotation(action = GXAction.Skip)
    public Result<String> getPublicInfo() {
        return Result.success("This is public information.");
    }
}
```

#### 3.2.2 URL白名单与登录豁免 (`@GXIgnoreLoginIntercept`)

- **URL白名单**：在`GXSSOProperties`中配置`url-white-lists`，这些URL将不会被`GXSSOAuthorizationInterceptor`拦截，即无需登录即可访问。
- **`@GXIgnoreLoginIntercept`**: 此注解用于Controller方法上，被标记的方法将跳过`GXSSOAuthorizationInterceptor`的登录验证。

```java
// application.yml 或 GXSSOProperties 配置
maple:
  sso:
    url-white-lists:
      - /api/public/**
      - /api/auth/login
      - /api/auth/captcha
```

```java
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    // 此接口无需登录即可访问
    @GetMapping("/global")
    @GXIgnoreLoginIntercept
    public Result<String> getGlobalNotification() {
        return Result.success("Global notification: System maintenance at 2 AM.");
    }
}
```

#### 3.2.3 自定义权限验证逻辑 (`GXSSOAuthorization`)

你需要实现`GXSSOAuthorization`接口，并将其注册为Spring Bean。`GXSSOPermissionInterceptor`会自动查找并使用它。

```java
@Component
public class MyCustomAuthorization implements GXSSOAuthorization {

    @Autowired
    private UserPermissionService userPermissionService; // 你的用户权限服务

    @Override
    public boolean isPermitted(Dict token, String permission) {
        if (token == null || permission == null) {
            return false;
        }
        Long userId = token.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
        if (userId == null) {
            return false;
        }

        // 示例：检查用户是否拥有特定权限字符串
        // "user:list", "order:edit:123" (带资源ID的权限)
        // 你可以根据自己的权限模型来实现复杂的逻辑，例如解析permission字符串，查询数据库等
        return userPermissionService.hasPermission(userId, permission);
    }
}
```

### 3.3 验证码功能 (`ImageCaptcha`)

`ImageCaptcha`提供了生成和校验图片验证码的功能。

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // 获取验证码图片
    @GetMapping("/captcha")
    public void getCaptcha(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("image/gif"); // 或 image/png 如果非GIF
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);

        ImageCaptcha captcha = ImageCaptcha.getInstance()
            .setLength(4)       // 验证码长度
            .setWidth(120)      // 图片宽度
            .setHeight(40)      // 图片高度
            .setFontSize(28)    // 字体大小
            .setInterfere(5)    // 干扰元素数量
            .setRandomType(GXRandomType.DEFAULT) // 字符类型：数字+小写字母
            .setGif(true);      // 生成GIF动态验证码

        // ticket用于后续校验时关联验证码，可以是session ID或其他唯一标识
        String ticket = request.getSession().getId(); 
        String generatedCode = captcha.generate(request, response.getOutputStream(), ticket);
        // 框架默认把 ticket + generatedCode 存入 HttpSession（ICaptchaStore -> CaptchaStoreSession），
        // 默认有效期 60 秒；校验成功后立即删除（一次性消费）。如需 Redis 等共享存储，
        // 实现 ICaptchaStore 并在首次 generate 前通过 captcha.setCaptchaStore(...) 注入。
    }

    // 校验验证码 (通常在登录或注册接口内部调用)
    public boolean verifyCaptcha(HttpServletRequest request, String userSubmittedCode) {
        String ticket = request.getSession().getId();
        return ImageCaptcha.getInstance().verification(request, ticket, userSubmittedCode);
    }
}
```
**存储说明**：验证码**不使用** `GXSSOCache`。默认实现是 `CaptchaStoreSession`（存 `HttpSession`），读写都经过 `ICaptchaStore`；`generate` 写入、`verification` 消费即删除（P-08），过期时间取 `ICaptchaStore.DEFAULT_EXPIRE_SECONDS`（60 秒）或 `setExpireSeconds(...)` 的显式值，且**不会**修改 `HttpSession` 的 `maxInactiveInterval`。`ticket` 为空时 `verification` 直接返回 `false` 且不会作废他人验证码；`put` 失败会抛 `GXBusinessException` 而不是静默返回空图。

### 3.4 用户信息注入 (`@GXLoginUserAnnotation`)

在Controller方法参数上使用`@GXLoginUserAnnotation`，可以把当前登录用户注入为 **`GXUserInfoDto`**（内置解析器只支持该类型及其子类；它由 Token 与 `GXUUserService` 组装而成）。该注解**没有** `value()` 属性，因此不支持"字段级注入"。

> **失败语义（单一且显式）**：解析失败只有一种表达——抛 `GXBusinessException`。以下三种情况都会抛异常，**不会**把 `null` 注入到参数：
> 1. 请求未携带任何 Token（匿名请求）；
> 2. Token 无法解析；
> 3. Token 合法但用户记录不存在。
>
> 因此 Controller 中不需要（也不应依赖）`if (param == null)` 分支。如果某个端点允许匿名访问且需要"可选用户"，不要使用本注解：改为显式读取 `GXSSOHelperUtil.getSSOToken(request)` 并自行判空（但请注意该类端点仍需 `@GXIgnoreLoginIntercept` 才能通过登录拦截）。

```java
@RestController
@RequestMapping("/api/me")
public class UserProfileController {

    // 内置解析器唯一支持的类型：GXUserInfoDto 及其子类
    @GetMapping("/profile")
    public Result<GXUserInfoDto> getUserProfile(@GXLoginUserAnnotation GXUserInfoDto currentUser) {
        // currentUser 由 Token + GXUUserService 组装，且一定非空
        return Result.success(currentUser);
    }
}
```

**内置解析器不支持的类型**（写错不会抛异常，而是静默拿到空对象，务必对照）：

| 写法 | 是否生效 | 说明 |
| --- | --- | --- |
| `@GXLoginUserAnnotation GXUserInfoDto u` | ✅ | `GXUserInfoDto` 及其子类 |
| `@GXLoginUserAnnotation Dict d` | ❌ | `supportsParameter` 返回 `false`，Spring 按普通模型参数绑定 → 得到空 `Dict` |
| `@GXLoginUserAnnotation Long id` | ❌ | 同上；`userId` 请从 `GXUserInfoDto#getUserId()` 取 |
| `@GXLoginUserAnnotation(XXX) T t` | ❌（无法编译） | 注解没有 `value()`，不存在字段级注入 |
| `@GXLoginUserAnnotation LoginUser u` | ❌ | 自定义类型需自建 `HandlerMethodArgumentResolver`（见下） |

> 内置解析器带 `@ConditionalOnBean(GXUUserService.class)`：未提供 `GXUUserService` 实现时，该注解不会被解析。
若需要注入自定义类型（例如 `LoginUser`），请自建 `HandlerMethodArgumentResolver`。两个易错点：① `supportsParameter` 的判断方向不能写反（应为"参数类型是 `LoginUser` 或其子类"）；② 失败语义必须与内置解析器一致——**抛 `GXBusinessException`，不要返回 `null`**。

```java
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(GXLoginUserAnnotation.class)
                && LoginUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new GXBusinessException("No HTTP request available for @GXLoginUserAnnotation parameter.");
        }
        Dict tokenDict = GXSSOHelperUtil.attrToken(request);
        if (tokenDict.isEmpty()) {
            tokenDict = GXSSOHelperUtil.getSSOToken(request);
        }
        if (tokenDict.isEmpty()) {
            throw new GXBusinessException("SSO token is absent, cannot resolve @GXLoginUserAnnotation parameter.");
        }

        // 将 Dict 转换为 LoginUser 对象
        LoginUser loginUser = new LoginUser();
        loginUser.setId(tokenDict.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME));
        loginUser.setUsername(tokenDict.getStr("username"));
        // ... 填充其他字段
        return loginUser;
    }
}
```

注册自建解析器（内置的 `GXLoginUserHandlerMethodArgumentResolver` 由框架自动装配，不要重复注册）：

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Autowired
    private LoginUserArgumentResolver loginUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}
```

### 3.5 Token管理

- **Token内容**：Token 是一个 `Dict`，核心字段为 `userId`（必须 `>0`）、`loginAt`（秒，唯一的新旧 Token 判据）、`createTime`（毫秒）、`userAgent`（SHA-256 指纹，`cookie-browser=true` 时必需）、`ip`（`cookie-check-ip=true` 时必需）。其它业务字段原样往返。
- **Token编码**：Token 由 `GXAuthCodeUtils.authCodeEncode` 生成（RC4 + 多段 MD5 的**对称可逆编码**，不是签名），输出 URL-safe Base64，可安全放入 Cookie。密钥来自 `GXTokenConfigService.getTokenSecret()`；**默认密钥是框架公开常量，仅限开发环境**，`prod` profile 下启动校验会直接拒绝启动。
- **Token有效期**：有效期由 `sso.config.cache-expires` 单键控制，同时决定签名绝对过期时间与缓存 TTL。默认 **7200 秒**。显式配置 `-1` 表示"永不过期"（会打 ERROR 日志，不建议）。
- **Token缓存**：解析后的 `Dict` 存入 `GXSSOCache`，键为 `ssoTokenKey_<userId>[:<platform>]`。**缓存不可用时默认登录失败**（fail-closed）；只有显式开启 `cache-fallback-enabled` 才降级为本地 Token（TTL ≤ 900 秒、`flag=CACHE_SHUT`、ERROR 日志）。

## 4. 配置说明 (`GXSSOProperties`)

`GXSSOProperties` 定义全部可配置项，前缀为 `sso.config`（由 `GXSSOConfigProperties` 包裹，即 `sso.config.xxx`）；白名单是独立的 `url.white-lists`。

**配置项（与实现一致）**：

| 配置键 | 类型 | 说明 | 默认值 |
| --- | --- | --- | --- |
| `sso.config.source` | `String` | 配置源：`nacos`（默认）/ `local`（`classpath:${spring.profiles.active}/sso.yml`） | `nacos` |
| `sso.config.encoding` | `String` | 响应与跳转编码 | `UTF-8` |
| `sso.config.token-name` | `String` | Header 名 + Token 载荷内的字段名 | `token` |
| `sso.config.cookie-name` | `String` | 承载 Token 的 Cookie 名 | `uid` |
| `sso.config.cookie-domain` | `String` | Cookie 域，空表示当前主机 | 空 |
| `sso.config.cookie-path` | `String` | Cookie 路径 | `/` |
| `sso.config.cookie-secure` | `boolean` | Cookie `Secure`；`same-site=None` 时必须为 `true` | `false` |
| `sso.config.cookie-http-only` | `boolean` | Cookie `HttpOnly` | `true` |
| `sso.config.cookie-same-site` | `String` | `Lax` / `Strict` / `None` | `Lax` |
| `sso.config.cookie-max-age` | `int` | Cookie `Max-Age`（秒），`-1` 为浏览器会话级 | `-1` |
| `sso.config.cookie-browser` | `boolean` | 校验 Token 内的浏览器指纹 | `false` |
| `sso.config.cookie-check-ip` | `boolean` | 校验 Token 内的 IP（无法解析 IP 时拒绝） | `false` |
| `sso.config.trust-forwarded-ip-headers` | `boolean` | 是否信任 `X-Forwarded-For` 等转发头 | `false` |
| `sso.config.login-url` | `String` | 未登录跳转地址，空则返回 401 JSON | 空 |
| `sso.config.logout-url` | `String` | 登出后跳转地址 | 空 |
| `sso.config.param-return-url` | `String` | 回跳参数名 | `ReturnURL` |
| `sso.config.cache-expires` | `int` | Token 绝对有效期（秒），同时作为缓存 TTL；`-1` 为永不过期（ERROR 日志） | `7200` |
| `sso.config.cache-fallback-enabled` | `boolean` | 无 `GXSSOCache` 时是否允许降级为本地 Token | `false` |
| `sso.config.capacity` | `int` | 注解缓存上限 | `4096` |
| `sso.config.permission-uri` | `boolean` | 是否启用 URI 授权模式 | `false` |
| `sso.config.nothing-annotation-pass` | `boolean` | 无权限注解时是否放行（生产建议 `false`；URI 模式下不生效） | `true` |
| `sso.config.illegal-url` | `String` | 无权限时的跳转地址，空则返回 403 | 空 |
| `url.white-lists` | `List<String>` | Ant 风格路径白名单，命中则跳过登录拦截 | 空列表 |

**保留但当前无效的字段**（`GXSSOProperties` 中仍存在，但没有任何生产代码读取，配置不会生效）：`sign-key`、`sign-algorithm`、`rsa-jks-store`、`rsa-cert-store`、`rsa-alias`、`rsa-keypass`、`rsa-storepass`。这些字段**不得**写入真实口令（源码默认值已删除）。

**行为型配置的优先级**：`illegal-url`、`nothing-annotation-pass` 既可通过 `sso.config.*` 绑定，也可通过 `GXSSOPermissionInterceptor#setIllegalUrl(...)` / `#setNothingAnnotationPass(...)` 显式覆盖，**显式 setter 优先于配置**。
**只能通过 Bean 注入的项**：`cache`、`plugin-list`、`authorization`（类型为接口/对象，不能从 YAML 绑定）。

**配置示例（Nacos `sso.yml` 或本地 `config/<profile>/sso.yml`）**：

```yaml
sso:
  config:
    source: nacos
    cookie-domain: ".myapp.com"
    cookie-secure: true
    cookie-same-site: "Lax"
    cache-expires: 7200
    cache-fallback-enabled: false
    login-url: "https://sso.example.com/login"
    logout-url: "https://sso.example.com/logout"
    permission-uri: false
url:
  white-lists:
    - /api/public/**
    - /api/auth/login
    - /api/auth/captcha
```

> 本地文件缺失时只打 WARN 并使用默认值（`ignore-resource-not-found=true`）；Nacos 不可用时会记录 ERROR，不会静默接管。

## 5. 拦截器配置

**无需手工注册**。`GXSSOAuthorizationInterceptor` 与 `GXSSOPermissionInterceptor` 都是 `@Component`，由 `leaf-base-framework` 的 `GXWebMvcConfigurer#addInterceptors` 收集全部 `GXAuthorizationInterceptor` 子类并按 `@Order` 注册：

| 顺序 | 拦截器 | `@Order` |
| --- | --- | --- |
| 1 | `GXSSOAuthorizationInterceptor` | `GXSSOConstant.AUTHORIZATION_ORDER`（100） |
| 2 | `GXSSOPermissionInterceptor` | `GXSSOConstant.PERMISSION_ORDER`（200） |

**约束**：
1. 登录拦截器必须在权限拦截器之前执行；顺序由 `@Order` 保证，**不再依赖 bean 名称字典序**。
2. 自定义 `GXAuthorizationInterceptor` 子类若需指定位置，请自行标注 `@Order`（未标注者排在已标注者之后）。
3. `@GXLoginUserAnnotation` 参数解析器在存在 `GXUUserService` Bean 时自动装配，无需手工 `addArgumentResolvers`。

## 6. 扩展机制

### 6.1 SSO插件 (`GXSSOPlugin`)

实现`GXSSOPlugin`接口并注册为Spring Bean，可以在SSO流程的关键节点执行自定义逻辑。

- **`login(HttpServletRequest request, HttpServletResponse response)`**: 用户登录凭证验证通过后、生成Token之前调用。返回`false`可中止登录。
- **`validateToken(Dict ssoToken)`**: 获取并解析Token后，进行缓存验证之前调用。返回`false`可使Token验证失败。
- **`logout(HttpServletRequest request, HttpServletResponse response)`**: 用户注销，清除Token缓存之后调用。

```java
@Component
@Slf4j
public class MyCustomSSOPlugin implements GXSSOPlugin {

    @Override
    public boolean login(HttpServletRequest request, HttpServletResponse response) {
        log.info("CustomSSOPlugin: User {} attempting login from IP {}.", request.getParameter("username"), GXHttpUtil.getIpAddr(request));
        // 例如：检查用户是否在特定IP段，或者进行风险评估
        return true; // 返回true继续登录，false中止
    }

    @Override
    public boolean validateToken(Dict ssoToken) {
        Long userId = ssoToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
        log.info("CustomSSOPlugin: Validating token for user ID {}.", userId);
        // 例如：检查用户账户是否被临时锁定
        // if (userService.isUserLocked(userId)) { return false; }
        return true; // 返回true继续验证，false使Token失效
    }

    @Override
    public boolean logout(HttpServletRequest request, HttpServletResponse response) {
        Dict token = GXSSOHelperUtil.attrToken(request); // 尝试从请求属性获取，可能已清除
        if (token != null && !token.isEmpty()){
             log.info("CustomSSOPlugin: User ID {} logged out.", token.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME));
        } else {
            log.info("CustomSSOPlugin: A user logged out.");
        }
        // 例如：记录详细登出日志
        return true;
    }
}
```

### 6.2 自定义Token缓存 (`GXSSOCache`)

**重要**：`leaf-base-sso` 自身**不提供** `GXSSOCache` 实现。你必须实现它（例如基于 `GXBaseCacheService`/Redis）并注册为 Spring Bean，否则：

- 默认（`cache-fallback-enabled=false`）：登录直接失败、校验一律视为未登录（fail-closed）；
- 显式开启降级：退化为"自包含 Cookie"模式（本地 Token、无服务端校验、TTL ≤ 900 秒、ERROR 日志），仅用于本地开发。

Bean 按**类型**查找（`GXSpringContextUtils.getBean(GXSSOCache.class)`），不存在"优先查找名为 `ssoCache` 的 Bean"的约定。

```java
@Component("ssoCache") // Bean的名称可以自定义，但SSO模块会优先查找名为"ssoCache"的Bean
public class RedisSSOCache implements GXSSOCache {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired // 需要注入GXTokenConfigService来获取缓存键和桶名
    private GXTokenConfigService tokenConfigService;

    private String getCacheKey(Long userId, Dict ssoToken) {
        return tokenConfigService.getTokenCacheKey(userId, ssoToken);
    }

    private String getBucketName() {
        return tokenConfigService.getCacheBucketName(); // 虽然Redis本身不直接用桶名，但可以作为key的一部分
    }

    @Override
    public Dict get(int expires, Dict requestToken) {
        // 注意：此处的expires是活动续期时间，requestToken是Cookie中的原始Token
        // 实际的get逻辑应由GXTokenConfigService.getEfficaciousToken()处理，这里仅演示基础get
        Long userId = requestToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
        String cacheKey = getCacheKey(userId, requestToken);
        String jsonToken = redisTemplate.opsForValue().get(getBucketName() + ":" + cacheKey);
        if (jsonToken != null) {
            Dict token = JSONUtil.toBean(jsonToken, Dict.class);
            // 活动续期
            redisTemplate.expire(getBucketName() + ":" + cacheKey, expires, TimeUnit.SECONDS);
            return token;
        }
        return null;
    }

    @Override
    public boolean set(Dict ssoToken, int expires) {
        Long userId = ssoToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
        String cacheKey = getCacheKey(userId, ssoToken);
        try {
            redisTemplate.opsForValue().set(getBucketName() + ":" + cacheKey, JSONUtil.toJsonStr(ssoToken), expires, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean delete(Dict ssoToken) {
        Long userId = ssoToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
        String cacheKey = getCacheKey(userId, ssoToken);
        return Boolean.TRUE.equals(redisTemplate.delete(getBucketName() + ":" + cacheKey));
    }

    @Override
    public boolean validateCacheToken(Dict cacheToken, Dict requestToken) {
        // 默认实现已在GXSSOCache接口中，通常不需要覆盖
        // 用于比较缓存中的Token和请求中的Token是否一致（例如校验特定字段）
        return GXSSOCache.super.validateCacheToken(cacheToken, requestToken);
    }
}
```
**关于上面的示例代码**：示例中的 `validateCacheToken(...)` 是历史遗留写法，接口中并不存在该方法；一致性校验的实际方法名是 `verifyTokenConsistency(Dict cacheToken, Dict requestToken)`（默认实现只比较 `loginAt`，通常无需覆盖）。

**实际调用链**：`GXSSOHelperUtil.getSSOToken` → `GXAbstractSSOService.getSSOToken` → `GXSSOSupportService.cacheSSOToken` → `GXSSOCache.get`。默认 `get` 会依次调用 `GXTokenConfigService.checkLoginStatus()`、`verifyTokenEffectiveness()`、`getEfficaciousToken()`。

**失败语义（与旧版不同）**：`get` 不再抛 `GXTokenInvalidException`，而是返回空 `Dict` 并把原因写入请求属性 `GXSSOConstant.SSO_TOKEN_STATE`（取值见 `GXSSOTokenStateEnums`：`absent` / `active` / `cache-miss` / `expired` / `kicked` / `cache-unavailable` / `plugin-rejected` / `local-only`）。`delete` 是幂等的：键不存在也算成功。`kickLogin(null)` 返回 `false` 而不抛异常。

### 6.3 自定义Token配置服务 (`GXTokenConfigService`)

实现`GXTokenConfigService`接口并注册为Spring Bean，可以控制：
- **`getTokenCacheKey(Long userId, Dict ssoToken)`**: 生成Token在缓存中的唯一键。
- **`getCacheBucketName()`**: 定义缓存桶名称（用于逻辑隔离）。
- **`checkLoginStatus()`**: 校验当前用户业务状态是否允许登录（例如，用户是否被禁用、是否需要强制修改密码等）。在`GXSSOCache.get()`中被调用。
- **`getEfficaciousToken(Dict requestToken)`**: 获取有效的Token。在`GXSSOCache.get()`中被调用，用于从缓存中读取并验证Token，处理多端登录踢出逻辑等。

```java
@Component
public class MyTokenConfigService implements GXTokenConfigService {
    @Autowired
    private GXBaseCacheService gxBaseCacheService; // 假设这是你项目中的基础缓存服务
    
    @Autowired
    private UserService userService; // 你的用户服务

    @Override
    public String getTokenCacheKey(Long userId, Dict ssoToken) {
        // 可以加入设备指纹等信息，实现更细致的缓存键
        String deviceId = ssoToken.getStr("deviceId", "default");
        return GXSSOProperties.getInstance().getTokenPrefix() + userId + ":" + deviceId;
    }

    @Override
    public String getCacheBucketName() {
        return "sso_tokens";
    }

    @Override
    public boolean checkLoginStatus() {
        // 框架会从当前请求上下文中获取用户信息，这里假设已通过某种方式获取到userId
        Long currentUserId = getCurrentUserIdFromContext(); 
        if (currentUserId == null) return true; // 没有用户信息，则不校验业务状态，由Token本身决定
        
        UserModel user = userService.findById(currentUserId);
        return user != null && user.isActive() && !user.isLocked();
    }

    @Override
    public Dict getEfficaciousToken(Dict requestToken) {
        Long userId = requestToken.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
        String cacheKey = getTokenCacheKey(userId, requestToken);
        String jsonToken = gxBaseCacheService.getCache(getCacheBucketName(), cacheKey);
        if (CharSequenceUtil.isBlank(jsonToken)) {
            return null;
        }
        Dict cacheToken = JSONUtil.toBean(jsonToken, Dict.class);

        // 多端登录处理 (如果 GXSSOProperties.isMultiLogin() == false)
        if (!GXSSOProperties.getInstance().isMultiLogin()) {
            String loginIpFromCache = cacheToken.getStr(GXTokenConstant.TOKEN_LOGIN_IP_FIELD_NAME);
            String loginIpFromRequest = requestToken.getStr(GXTokenConstant.TOKEN_LOGIN_IP_FIELD_NAME);
            // 简单示例：如果IP不一致，则认为是被踢了 (实际场景可能更复杂)
            if (!CharSequenceUtil.equals(loginIpFromCache, loginIpFromRequest)) {
                return Dict.create().set(GXTokenConstant.TOKEN_FLAG_FIELD_NAME, GXTokenConstant.FLAG_KICK_OUT);
            }
        }
        
        // 续期
        gxBaseCacheService.expire(getCacheBucketName(), cacheKey, GXSSOProperties.getInstance().getTokenActivityTimeout(), TimeUnit.SECONDS);
        return cacheToken;
    }
    
    private Long getCurrentUserIdFromContext(){
        // 实现从请求上下文获取用户ID的逻辑
        // 例如: LoginUserContext.getUserId();
        Dict token = GXCurrentRequestContextUtils.getLoginCredentials();
        return token != null ? token.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME) : null;
    }
}
```

### 6.4 自定义未登录处理器 (`GXSSOHandler`)

实现`GXSSOHandler`接口并注册为Spring Bean，可以自定义当`GXSSOAuthorizationInterceptor`检测到用户未登录时的处理行为。

- **`preTokenIsNullAjax(HttpServletRequest request, HttpServletResponse response)`**: 处理AJAX请求未登录的情况。返回`false`表示已处理响应，不再继续执行。
- **`preTokenIsNull(HttpServletRequest request, HttpServletResponse response)`**: 处理普通HTTP请求未登录的情况。返回`false`表示已处理响应。

```java
@Component
public class MySSOHandler implements GXSSOHandler {
    @Override
    public boolean preTokenIsNullAjax(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        try {
            // 返回自定义的JSON错误信息
            response.getWriter().write("{\"code\": 401, \"message\": \"请先登录后再操作（Ajax）\", \"data\": null}");
        } catch (IOException e) {
            // log error
        }
        return false; // 表示已处理，不再继续
    }

    @Override
    public boolean preTokenIsNull(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 重定向到登录页面，并附带原始请求URL作为回调
            String redirectUrl = request.getContextPath() + "/login?callback=" + 
                               URLEncoder.encode(request.getRequestURL().toString(), StandardCharsets.UTF_8.name());
            response.sendRedirect(redirectUrl);
        } catch (IOException e) {
            // log error
        }
        return false; // 表示已处理，不再继续
    }
}
```

## 7. 最佳实践与安全建议

### 7.1 安全配置

- **强密钥（必须）**：`GXTokenConfigService.getTokenSecret()` 默认返回框架公开常量（`31D0D393D3EAF5`），仅限开发环境。生产环境必须覆盖该实现；`prod` profile 下启动校验会直接拒绝启动（`event=sso.config.invalid key=token-secret`）。仓库中不存在 `aesKey` 配置项，Token 编码由 `GXAuthCodeUtils` 完成。
- **HTTPS**：生产环境必须使用 HTTPS 并设置 `sso.config.cookie-secure=true`；`cookie-same-site=None` 时该值为强制要求（启动校验与登录时都会校验）。
- **HttpOnly Cookie**：保持 `sso.config.cookie-http-only=true`，防止客户端脚本读取 Token。
- **Token有效期**：`sso.config.cache-expires` 默认 7200 秒；不要配置 `-1`（永不过期会打 ERROR）。
- **服务端校验**：必须提供 `GXSSOCache` 实现；保持 `cache-fallback-enabled=false`，避免退化成"Cookie 即身份"。
- **CSRF防护**：SSO 不处理 CSRF；`SameSite=Lax` + HttpOnly 只能缓解，业务侧仍需标准 CSRF Token。
- **日志审计**：按 `event=sso.login.*`、`event=sso.token.reject`、`event=sso.permission.denied`、`event=sso.config.invalid` 检索与告警；日志中不会输出 Token 原文与密钥。
- **`nothing-annotation-pass`**：生产环境建议在配置中显式设为 `false`（`sso.config.nothing-annotation-pass: false`，白名单模式，未标注权限注解的接口一律拒绝）。注意：① 启用 `permission-uri=true` 后 URI 授权**自身决断**，不会再回落到该开关；② 代码里调用 `setNothingAnnotationPass(...)` 会覆盖配置值，两处不要给相反的值。
- **多端登录**：通过 `loginAt` 判定，后登录会使旧 Token 变为 `kicked`；不存在 `multiLogin` 配置项。

### 7.2 性能优化

- **分布式缓存**：在集群环境下必须用 Redis 等分布式缓存实现 `GXSSOCache`/`GXBaseCacheService`，否则各实例的登录态不一致。
- **权限缓存**：`GXSSOAuthorization.isPermitted` 每个请求都会调用，复杂判定建议在业务侧加缓存。
- **固定开销口径**（可复算）：每请求 4 次 MD5 + 2×256 轮 RC4 密钥表（`GXAuthCodeUtils`）、1 次 JSON 解析、**0 次** `getBean`（`GXSSOHelperUtil` 已缓存 Bean 引用）、**0 条** INFO 日志；`InetAddress` 解析只在类初始化时执行一次。
- **注解缓存**：容量由 `sso.config.capacity`（默认 4096）控制，超限按 FIFO 淘汰，不会整体清空。
- **验证码优化**：`ImageCaptcha` 单实例首次渲染后冻结；高并发场景请每请求 `ImageCaptcha.getInstance()` 或 `of(...)` 新建，避免串行化。
- **异步处理**：登录日志等非核心流程建议业务侧异步化，本模块在请求线程内不做额外 I/O。

### 7.3 部署与维护

- **配置分离**：将敏感配置（如密钥）通过环境变量或外部配置文件注入，而不是硬编码在代码或打包的配置文件中。
- **版本控制**：SSO模块的配置和自定义实现应纳入版本控制。
- **监控告警**：对SSO服务的关键指标（如Token生成速率、缓存命中率、认证失败率）进行监控，并设置告警。

### 7.4 可观测性

**指标**（`GXSSOMetrics.snapshot()`，返回不可变 `record`；`GXSSOMetrics.reset()` 仅用于测试）：

| 指标 | 含义 | 递增点 |
| --- | --- | --- |
| `parseSuccess` / `parseFailure` | Token 解析成功 / 失败（签名非法、JSON 非法、空载荷） | `GXSSOHelperUtil.parser` |
| `cacheHit` / `cacheMiss` | 服务端缓存命中 / 未命中 | `GXSSOCache.get` |
| `denied` | 权限拒绝（403） | `GXSSOPermissionInterceptor` |
| `loginFailure` | 登录失败（缓存不可用、插件拒绝、Cookie 配置非法） | `GXAbstractSSOService.setCookie` |
| `cacheFallback` | 进入本地 Token 降级模式 | 降级分支 |
| `invalidToken` | `userId` 缺失或 `<=0` | `GXSSOCache` |
| `activeTokens` | 缓存桶内 Token 数（登录/登出后刷新） | `GXSSOCache.set/delete` |

**日志事件**（ASCII 键值对，按 `event=` 检索）：

| 事件 | 级别 | 关键字段 |
| --- | --- | --- |
| `event=sso.login.success` | INFO | `userId`、`degraded` |
| `event=sso.login.failure` | WARN | `reason`（`cache-unavailable` / `cache-write-failed` / `plugin-rejected` / `invalid-cookie-config`） |
| `event=sso.login.degraded` | ERROR | `reason`、TTL ≤ 900 |
| `event=sso.token.reject` | DEBUG | `reason`（`absent` / `cache-miss` / `kicked` / `expired` / `cache-unavailable` / `plugin-rejected`） |
| `event=sso.permission.denied` | DEBUG | `uri`（同时 `denied` 指标 +1） |
| `event=sso.logout` | INFO | `result` |
| `event=sso.config.invalid` | ERROR | `key`、`reason`（启动校验失败） |

**约束**：① 日志不得输出 Token 原文、密钥或 Cookie 值（只允许 `userId`/`loginAt`/`flag`）；② 未登录路径不得输出 INFO；③ 不得引入 Micrometer 等新依赖；④ 指标与日志事件名保持 ASCII 且语义对齐。

## 8. 故障排查

- **日志级别**：调整相关类的日志级别（如`cn.maple.sso`包）到DEBUG，获取更详细的执行信息。
- **Cookie检查**：使用浏览器开发者工具检查Token Cookie是否正确设置（名称、域、路径、HttpOnly、Secure等）。
- **缓存检查**：直接连接缓存服务器（如Redis客户端）检查Token是否按预期存储、更新和删除。
- **网络问题**：确认客户端与服务器之间、服务器与缓存服务器之间的网络通畅。
- **配置核对**：仔细核对`GXSSOProperties`的各项配置是否正确，特别是密钥、超时时间、白名单等。
- **插件冲突**：如果使用了自定义插件，尝试逐个禁用插件，排查是否由插件引起的问题。

通过本手册，希望能帮助您更好地理解和使用`leaf-base-sso`模块，构建安全可靠的应用系统。