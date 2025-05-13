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
4.  **（可选）URL级权限验证**：如果`GXSSOProperties.isPermissionUri()`为`true`，还会调用`GXSSOAuthorization.isPermitted(token, requestURI)`进行基于请求URI的权限验证。
5.  **处理结果**：
    *   如果权限验证通过，则放行到业务处理器。
    *   如果权限验证失败，调用`GXSSOPermissionInterceptor.unauthorizedAccess()`方法，根据请求类型（AJAX/普通）返回403错误或重定向到配置的`illegalUrl`。

### 2.4 注销流程

1.  用户请求注销接口，或管理员触发强制下线。
2.  调用`GXAbstractSSOService.clearLogin(request, response)`或`GXAbstractSSOService.kickLogin(userId)`。
3.  **`GXAbstractSSOService`处理注销**：
    *   从Cookie或参数中获取Token/用户ID。
    *   调用`GXSSOCache.delete()`从缓存中删除对应的Token。
    *   清除客户端Cookie（通过`GXHttpUtil`）。
    *   执行`GXSSOPlugin`的`logout`插件。

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
        // 注意：框架内部会将 ticket 和 generatedCode 存入 GXSSOCache (如果配置了)
        // 或者你可以自己处理存储，例如存入Session: request.getSession().setAttribute("captcha_" + ticket, generatedCode);
    }

    // 校验验证码 (通常在登录或注册接口内部调用)
    public boolean verifyCaptcha(HttpServletRequest request, String userSubmittedCode) {
        String ticket = request.getSession().getId();
        return ImageCaptcha.getInstance().verification(request, ticket, userSubmittedCode);
    }
}
```
**注意**：`ImageCaptcha.generate`方法内部会尝试使用`GXSSOHelperUtil.getSSOCache()`将验证码存入缓存，键为`GXSSOConstant.CACHE_CAPTCHA_CODE_PREFIX + ticket`。校验时`ImageCaptcha.verification`也会从该缓存读取。确保`GXSSOCache`已正确配置和工作。

### 3.4 用户信息注入 (`@GXLoginUserAnnotation`)

在Controller方法参数上使用`@GXLoginUserAnnotation`，可以将当前登录用户的Token信息（`Dict`对象）或其特定字段注入。

```java
@RestController
@RequestMapping("/api/me")
public class UserProfileController {

    // 注入整个Token Dict对象
    @GetMapping("/profile")
    public Result<Dict> getUserProfile(@GXLoginUserAnnotation Dict currentUser) {
        if (currentUser == null) {
            return Result.failed("用户未登录");
        }
        // currentUser 包含登录时存入的所有信息
        return Result.success(currentUser);
    }

    // 注入Token中的特定字段 (假设LoginUser是你的用户模型类)
    // 需要自定义一个 HandlerMethodArgumentResolver 来实现将 Dict 转换为 LoginUser
    // 或者直接注入Long类型的用户ID
    @GetMapping("/id")
    public Result<Long> getCurrentUserId(@GXLoginUserAnnotation(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME) Long userId) {
        if (userId == null) {
            return Result.failed("用户未登录");
        }
        return Result.success(userId);
    }
    
    // 推荐：注入自定义的用户对象，需要配合 ArgumentResolver
    // 假设你有一个 LoginUserArgumentResolver 实现了 HandlerMethodArgumentResolver
    @GetMapping("/info")
    public Result<LoginUser> getCurrentUserInfo(@GXLoginUserAnnotation LoginUser loginUser) {
        if (loginUser == null) {
            return Result.failed("用户未登录");
        }
        return Result.success(loginUser);
    }
}
```
要实现将`Dict`自动转换为自定义的`LoginUser`对象，你需要创建一个`HandlerMethodArgumentResolver`：

```java
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(GXLoginUserAnnotation.class) &&
               parameter.getParameterType().isAssignableFrom(LoginUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }
        Dict tokenDict = (Dict) request.getAttribute(GXSSOConstant.SSO_TOKEN_ATTR);
        if (tokenDict == null) {
            tokenDict = GXSSOHelperUtil.getSSOToken(request); // 尝试再次获取
        }

        if (tokenDict == null || tokenDict.isEmpty()) {
            return null;
        }

        // 将Dict转换为LoginUser对象
        LoginUser loginUser = new LoginUser();
        loginUser.setId(tokenDict.getLong(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME));
        loginUser.setUsername(tokenDict.getStr("username"));
        // ... 填充其他字段
        return loginUser;
    }
}
```
然后注册这个Resolver到Spring MVC配置中：
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

- **Token内容**：Token是一个`Dict`对象，可以存储任意键值对信息。核心是`GXTokenConstant.TOKEN_USER_ID_FIELD_NAME`。
- **Token加密**：Token在存入Cookie前会通过`GXAuthCodeUtils.encode()`进行加密（默认使用AES），密钥和算法在`GXSSOProperties`中配置。
- **Token有效期**：
    - `tokenTimeout`：Token的绝对超时时间（秒）。即使有活动，超过此时间Token也会失效。
    - `tokenActivityTimeout`：Token的活动超时时间（秒）。如果用户在此时间内有活动，Token会自动续期（重新设置缓存的过期时间）。
- **Token缓存**：Token信息（解密后的`Dict`对象）存储在`GXSSOCache`中，用于快速验证和获取用户信息。

## 4. 配置说明 (`GXSSOProperties`)

`GXSSOProperties`类定义了SSO系统的所有可配置项。可以通过`application.yml`或Java Bean配置。

**主要配置项**：

| 配置项                      | 类型          | 说明                                                                 | 默认值                               |
| --------------------------- | ------------- | -------------------------------------------------------------------- | ------------------------------------ |
| `encoding`                  | `String`      | 字符编码                                                               | `UTF-8`                              |
| `signKey`                   | `String`      | Token签名密钥（用于JWT等场景，当前模块主要用AES加密Cookie内容）          | `maple-leaf-framework`               |
| `signAlgorithm`             | `String`      | 签名算法 (如 HS256, HS512)                                             | `HS512`                              |
| `aesKey`                    | `String`      | AES加密密钥 (用于Cookie内容加密, `GXAuthCodeUtils`)                      | `www.maple.leaf.framework.cn`        |
| `rsaJksStore`               | `String`      | RSA JKS密钥库路径                                                        | `key.jks`                            |
| `rsaCertStore`              | `String`      | RSA公钥证书路径                                                        | `public.cert`                        |
| `rsaAlias`                  | `String`      | RSA密钥别名                                                            | `jwtkey`                             |
| `rsaKeypass`                | `String`      | RSA私钥密码                                                            | `llTs1p68K`                          |
| `rsaStorepass`              | `String`      | RSA密钥库密码                                                          | `lLt66Y8L321`                        |
| `tokenName`                 | `String`      | Token在Cookie中的名称                                                    | `ssoToken`                           |
| `cookieName`                | `String`      | （已废弃，请使用`tokenName`）旧版Cookie名称                              | `uid`                                |
| `cookieDomain`              | `String`      | Cookie的有效域名 (例如 `.example.com`)                                 | 当前访问域名                         |
| `cookiePath`                | `String`      | Cookie的有效路径                                                         | `/`                                  |
| `cookieSecure`              | `boolean`     | Cookie是否仅通过HTTPS传输                                                | `false`                              |
| `cookieHttpOnly`            | `boolean`     | Cookie是否禁止客户端脚本访问                                             | `true`                               |
| `cookieMaxAge`              | `int`         | Cookie在客户端的存活时间（秒），-1表示会话级Cookie                       | `-1`                                 |
| `tokenTimeout`              | `int`         | Token绝对超时时间（秒），缓存在此时间后失效                               | `2592000` (30天)                     |
| `tokenActivityTimeout`      | `int`         | Token活动超时时间（秒），用户在此时间内无活动则Token失效，有活动则续期 | `1800` (30分钟)                      |
| `isPermissionUri`           | `boolean`     | 是否开启基于URI的权限验证                                                | `false`                              |
| `isNothingAnnotationPass`   | `boolean`     | Controller方法无`@GXPermissionAnnotation`时是否默认放行权限检查        | `true`                               |
| `illegalUrl`                | `String`      | 无权限访问时重定向的URL (普通请求)                                       | `null` (返回403)                     |
| `urlWhiteLists`             | `List<String>`| URL白名单列表，这些URL不进行登录拦截                                   | 空列表                               |
| `captchaTimeout`            | `int`         | 验证码超时时间（秒）                                                     | `300` (5分钟)                        |
| `multiLogin`                | `boolean`     | 是否允许多端登录，`false`表示后登录会踢掉前一个登录（基于相同用户ID）    | `true`                               |
| `tokenPrefix`               | `String`      | Token缓存键前缀                                                        | `sso:token:`                         |
| `userPermissionCachePrefix` | `String`      | 用户权限缓存键前缀 (如果业务实现了权限缓存)                              | `sso:user_permission:`               |
| `cacheCaptchaCodePrefix`    | `String`      | 验证码缓存键前缀                                                       | `sso:captcha:`                       |

**配置示例 (`application.yml`)**：

```yaml
maple:
  sso:
    token-name: "MY_APP_TOKEN"
    cookie-domain: ".myapp.com"
    cookie-http-only: true
    token-timeout: 86400 # 1天
    token-activity-timeout: 1800 # 30分钟
    aes-key: "a-very-strong-and-secret-aes-key" # 务必修改为强密钥
    url-white-lists:
      - /api/public/**
      - /api/auth/login
      - /api/auth/captcha
    is-nothing-annotation-pass: false # 建议生产环境设为false
    illegal-url: "/error/403"
    multi-login: false # 只允许单端登录
```

## 5. 拦截器配置

你需要将`GXSSOAuthorizationInterceptor`和`GXSSOPermissionInterceptor`注册到Spring MVC的拦截器链中。

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private GXSSOAuthorizationInterceptor ssoAuthorizationInterceptor;

    @Resource
    private GXSSOPermissionInterceptor ssoPermissionInterceptor;
    
    @Autowired // 如果你实现了自定义的ArgumentResolver
    private LoginUserArgumentResolver loginUserArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器 (先执行)
        registry.addInterceptor(ssoAuthorizationInterceptor)
                .addPathPatterns("/**") // 拦截所有请求
                .excludePathPatterns("/static/**", "/public/**", "/error", "/favicon.ico") // 排除静态资源和公共路径
                .order(1); // 设置执行顺序

        // 权限拦截器 (后执行)
        registry.addInterceptor(ssoPermissionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/public/**", "/error", "/favicon.ico")
                .order(2); // 设置执行顺序
    }
    
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver); // 注册自定义的参数解析器
    }
}
```
**注意拦截器顺序**：`GXSSOAuthorizationInterceptor`必须在`GXSSOPermissionInterceptor`之前执行。

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

默认情况下，框架可能使用基于内存的缓存或尝试从Spring上下文中获取`GXBaseCacheService`的实现。你可以提供自己的`GXSSOCache`实现（例如基于Redis），并注册为Spring Bean，SSO模块会自动使用它。

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
**重要**：`GXSSOCache`的`get`方法在框架中的实际调用链是 `GXSSOHelperUtil.getSSOToken` -> `GXAbstractSSOService.getSSOToken` -> `GXSSOCache.get`。而`GXSSOCache.get`的默认实现会进一步调用`GXTokenConfigService.checkLoginStatus()`和`GXTokenConfigService.getEfficaciousToken()`。因此，自定义`GXSSOCache`时，需要理解这个调用关系，或者同时自定义`GXTokenConfigService`。

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

- **强密钥**：务必为`GXSSOProperties.aesKey`（以及RSA相关配置，如果使用）设置强大且唯一的密钥，并妥善保管。
- **HTTPS**：生产环境必须使用HTTPS，并设置`GXSSOProperties.cookieSecure = true`。
- **HttpOnly Cookie**：保持`GXSSOProperties.cookieHttpOnly = true`，防止客户端脚本窃取Token Cookie。
- **Token有效期**：根据业务需求设置合理的`tokenTimeout`和`tokenActivityTimeout`。过长的有效期会增加安全风险。
- **CSRF防护**：SSO本身不直接处理CSRF，但HttpOnly Cookie有助于缓解。业务系统应配合使用标准的CSRF Token机制。
- **输入验证**：所有用户输入（如登录参数、权限字符串）都应进行严格验证。
- **日志审计**：定期审查SSO相关的日志（登录、登出、权限失败等），监控异常活动。
- **`isNothingAnnotationPass`**: 生产环境建议设置为`false`，采用白名单模式管理权限，即默认所有接口都需要权限注解。
- **`multiLogin`**: 根据业务需求决定是否允许多端登录。禁止多端登录可以提高账户安全性。

### 7.2 性能优化

- **分布式缓存**：在高并发或集群环境下，使用Redis等分布式缓存实现`GXSSOCache`和`GXBaseCacheService`。
- **权限缓存**：对于复杂或查询频繁的权限验证逻辑（`GXSSOAuthorization.isPermitted`），考虑在业务层面增加缓存。
- **异步处理**：对于非核心流程（如登录日志记录），可以考虑使用异步方式处理，避免阻塞主线程。
- **验证码优化**：如果验证码生成消耗较大，可以考虑预生成或使用更轻量级的验证码方案。

### 7.3 部署与维护

- **配置分离**：将敏感配置（如密钥）通过环境变量或外部配置文件注入，而不是硬编码在代码或打包的配置文件中。
- **版本控制**：SSO模块的配置和自定义实现应纳入版本控制。
- **监控告警**：对SSO服务的关键指标（如Token生成速率、缓存命中率、认证失败率）进行监控，并设置告警。

## 8. 故障排查

- **日志级别**：调整相关类的日志级别（如`cn.maple.sso`包）到DEBUG，获取更详细的执行信息。
- **Cookie检查**：使用浏览器开发者工具检查Token Cookie是否正确设置（名称、域、路径、HttpOnly、Secure等）。
- **缓存检查**：直接连接缓存服务器（如Redis客户端）检查Token是否按预期存储、更新和删除。
- **网络问题**：确认客户端与服务器之间、服务器与缓存服务器之间的网络通畅。
- **配置核对**：仔细核对`GXSSOProperties`的各项配置是否正确，特别是密钥、超时时间、白名单等。
- **插件冲突**：如果使用了自定义插件，尝试逐个禁用插件，排查是否由插件引起的问题。

通过本手册，希望能帮助您更好地理解和使用`leaf-base-sso`模块，构建安全可靠的应用系统。