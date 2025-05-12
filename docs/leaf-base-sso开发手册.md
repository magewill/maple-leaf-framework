# Maple Leaf Framework SSO模块开发手册完善版

## 1. 模块概述

`leaf-base-sso`模块是Maple Leaf Framework中提供的单点登录(SSO)解决方案，为应用系统提供了完整的用户认证、授权和会话管理功能。该模块设计灵活，支持多种认证方式和权限控制策略，可以满足各种复杂业务场景的安全需求。

### 1.1 主要特性

- **完整的认证流程**：支持用户登录、注销、会话管理等核心功能
- **灵活的权限控制**：提供基于URL和注解两种权限验证方式
- **安全防护机制**：内置多种安全措施，防止常见的Web攻击
- **可扩展的插件系统**：通过插件机制支持自定义认证和授权逻辑
- **分布式会话支持**：适用于集群环境的会话同步和管理
- **验证码功能**：内置多种验证码生成和验证机制
- **防会话固定攻击**：支持登录时重新生成会话标识
- **Token加密存储**：确保敏感信息不被窃取
- **多重验证机制**：结合IP、浏览器信息和缓存验证

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
    </dependency>
</dependencies>
```

## 2. 核心功能

### 2.1 用户认证

#### 2.1.1 登录流程

模块提供了完整的用户登录流程实现，包括：

- Token生成与验证
- Cookie设置与管理
- 会话状态维护
- 安全校验（IP绑定、浏览器指纹等）
- 防会话固定攻击

**示例代码**：

```java
// 创建包含用户信息的Token
Dict userInfo = Dict.create()
    .set("userId", 10001L)
    .set("username", "张三")
    .set("roles", "admin,user");

// 设置登录Cookie并防止会话固定攻击
ssoService.setCookie(request, response, userInfo);
```

#### 2.1.2 注销流程

支持用户主动注销和管理员强制注销两种方式：

- 清除客户端Cookie
- 删除服务端缓存
- 执行插件注销逻辑
- 支持单设备登出和全部设备登出

**示例代码**：

```java
// 用户主动注销
ssoService.clearLogin(request, response);

// 管理员强制某用户下线
ssoService.kickLogin(10001L);
```

### 2.2 权限控制

#### 2.2.1 注解式权限控制

通过`@GXPermissionAnnotation`注解实现方法级别的权限控制：

```java
// 要求用户拥有"user:view"权限
@GetMapping("/users")
@GXPermissionAnnotation("user:view")
public List<User> listUsers() {
    return userService.findAll();
}

// 跳过权限验证 - 适用于公开接口
@GetMapping("/public/info")
@GXPermissionAnnotation(action = GXAction.Skip)
public Dict publicInfo() {
    return Dict.create().set("version", "1.0");
}
```

#### 2.2.2 URL权限控制

支持基于URL路径的权限控制，可通过配置文件设置URL白名单：

```yaml
maple:
  sso:
    url-white-lists:
      - /api/public/**
      - /api/login
      - /api/register
```

#### 2.2.3 权限验证策略配置

可以通过配置`nothingAnnotationPass`属性控制无注解方法的默认行为：

- `true`：默认放行，适用于大部分方法都不需要权限控制的场景（黑名单模式）
- `false`：默认拦截，适用于大部分方法都需要权限控制的场景（白名单模式）

安全建议：
- 开发环境可设置为true，便于调试
- 生产环境建议设置为false，采用白名单策略，更安全

### 2.3 验证码功能

模块内置了多种验证码生成和验证机制：

- 支持静态图片和GIF动态图片
- 支持多种验证码类型（数字、字母、汉字、混合）
- 提供验证码存储和校验机制
- 支持字符扭曲和旋转，增加机器识别难度
- 支持随机干扰线和干扰点，提高安全性

**示例代码**：

```java
// 创建验证码实例
ImageCaptcha captcha = ImageCaptcha.getInstance()
    .setLength(5)         // 设置验证码长度
    .setWidth(150)        // 设置图片宽度
    .setHeight(50)        // 设置图片高度
    .setRandomType(GXRandomType.MIX)  // 设置验证码类型
    .setGif(true);        // 设置为GIF动态验证码

// 生成验证码
String ticket = UUID.randomUUID().toString();
captcha.generate(request, response.getOutputStream(), ticket);

// 验证用户输入
boolean valid = captcha.verification(request, ticket, userInput);
```

### 2.4 用户信息注入

通过`@GXLoginUserAnnotation`注解实现Controller方法参数的用户信息自动注入：

```java
// 注入当前登录用户对象
@GetMapping("/user/profile")
@GXPermissionAnnotation("user:profile")
public Dict getUserProfile(@GXLoginUserAnnotation LoginUser user) {
    return Dict.create()
        .set("id", user.getId())
        .set("username", user.getUsername())
        .set("lastLoginTime", user.getLastLoginTime());
}
```

### 2.5 Token管理

模块提供了完善的Token管理机制：

- Token生成与验证
- Token加密存储
- Token活动超时与绝对超时
- Token自动刷新
- 支持多端登录控制

## 3. 配置说明

### 3.1 SSO配置属性

`GXSSOProperties`类定义了SSO系统的核心配置项：

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| encoding | 编码格式 | UTF-8 |
| signKey | 签名密钥 | - |
| signAlgorithm | 签名算法 | HS512 |
| rsaJksStore | RSA私钥存储路径 | key.jks |
| rsaCertStore | RSA公钥存储路径 | public.cert |
| rsaAlias | RSA密钥Alias | jwtkey |
| rsaKeypass | RSA密钥keypass | llTs1p68K |
| rsaStorepass | RSA密钥storepass | lLt66Y8L321 |
| tokenName | 访问票据名 | token |
| cookieName | cookie名称 | uid |
| cookieDomain | cookie所在有效域名 | 当前访问域名 |
| cookiePath | cookie路径 | / |
| cookieSecure | cookie是否设置安全 | false |
| cookieHttpOnly | cookie是否为只读状态 | true |
| cookieMaxAge | cookie有效期 | -1（关闭浏览器失效） |
| tokenTimeout | Token超时时间 | 2592000（30天） |
| tokenActivityTimeout | Token活动超时时间 | 1800（30分钟） |

### 3.2 配置方式

可以通过以下方式配置SSO系统：

1. **配置文件方式**：在application.yml中设置

```yaml
maple:
  sso:
    cookie-name: uid
    cookie-domain: example.com
    cookie-path: /
    cookie-http-only: true
    token-timeout: 2592000
    token-activity-timeout: 1800
    url-white-lists:
      - /api/public/**
      - /api/login
```

2. **编程方式**：通过代码设置

```java
@Configuration
public class SSOConfig {
    @Bean
    public GXSSOProperties ssoProperties() {
        return new GXSSOProperties()
            .setCookieName("uid")
            .setCookieDomain("example.com")
            .setCookiePath("/")
            .setCookieHttpOnly(true)
            .setTokenTimeout(2592000)
            .setTokenActivityTimeout(1800);
    }
}
```

## 4. 拦截器说明

### 4.1 登录拦截器

`GXSSOAuthorizationInterceptor`负责验证用户是否已登录：

- 白名单URL直接放行
- 带有`@GXIgnoreLoginIntercept`注解的方法直接放行
- 支持自定义拦截规则
- 处理未登录用户的请求（AJAX和普通HTTP请求）
- 支持Token的缓存管理

**配置示例**：

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Resource
    private GXSSOAuthorizationInterceptor ssoAuthorizationInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ssoAuthorizationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**");
    }
}
```

### 4.2 权限拦截器

`GXSSOPermissionInterceptor`负责对已登录用户的权限进行验证：

- 基于URL的权限验证
- 基于注解的权限验证
- 处理无权限访问的情况
- 支持灵活的权限控制策略
- 多层次权限验证 - 同时支持URL和注解两种验证方式
- 差异化响应处理 - 区分AJAX请求和普通HTTP请求

**注意**：权限拦截器必须在登录拦截器之后执行。

**配置示例**：

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Resource
    private GXSSOPermissionInterceptor ssoPermissionInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ssoPermissionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**");
    }
}
```

## 5. 工具类说明

### 5.1 GXSSOHelperUtil

`GXSSOHelperUtil`是SSO系统的门面(Facade)，提供了一系列静态方法用于操作SSO功能：

```java
// 获取当前登录用户信息
Dict currentUser = GXSSOHelperUtil.getSSOToken(request);
Long userId = currentUser.getLong("userId");

// 设置用户登录状态
Dict userInfo = Dict.create()
    .set("userId", 10001L)
    .set("username", "张三");
GXSSOHelperUtil.setCookie(request, response, userInfo);

// 用户注销
GXSSOHelperUtil.clearLogin(request, response);

// 强制用户下线
GXSSOHelperUtil.kickLogin(10001L);
```

### 5.2 其他工具类

- **GXBrowserUtil**：浏览器工具类，用于获取和验证浏览器信息
- **GXHttpUtil**：HTTP工具类，提供HTTP请求和响应处理方法
- **GXIpHelperUtil**：IP工具类，用于获取和验证客户端IP
- **GXRandomUtil**：随机数工具类，用于生成各种类型的随机字符

## 6. 扩展机制

### 6.1 插件系统

通过实现`GXSSOPlugin`接口可以扩展SSO系统的功能：

```java
@Component
public class CustomSSOPlugin implements GXSSOPlugin {
    @Override
    public boolean validateToken(Dict token) {
        // 自定义Token验证逻辑
        return true;
    }

    @Override
    public boolean logout(HttpServletRequest request, HttpServletResponse response) {
        // 自定义注销逻辑
        return true;
    }
    
    @Override
    public boolean login(HttpServletRequest request, HttpServletResponse response) {
        // 自定义登录逻辑，如双因素认证
        return true;
    }
}
```

### 6.2 自定义缓存实现

通过实现`GXSSOCache`接口可以自定义Token的缓存策略：

```java
@Component
public class CustomSSOCache implements GXSSOCache {
    @Override
    public void set(String key, String value, int timeout) {
        // 自定义缓存设置逻辑
    }

    @Override
    public String get(String key) {
        // 自定义缓存获取逻辑
        return null;
    }

    @Override
    public boolean delete(String key) {
        // 自定义缓存删除逻辑
        return true;
    }
    
    @Override
    public boolean validateToken(Dict token, String requestToken) {
        // 自定义Token验证逻辑
        return true;
    }
}
```

### 6.3 自定义权限验证

通过实现`GXSSOAuthorization`接口可以自定义权限验证逻辑：

```java
@Component
public class CustomAuthorization implements GXSSOAuthorization {
    @Autowired
    private PermissionService permissionService;
    
    @Override
    public boolean isPermitted(Dict token, String permission) {
        // 获取用户ID
        Long userId = token.getLong("userId");
        if (userId == null) {
            return false;
        }
        
        // 超级管理员拥有所有权限
        if (permissionService.isAdmin(userId)) {
            return true;
        }
        
        // 验证用户是否拥有指定权限
        return permissionService.hasPermission(userId, permission);
    }
}
```

### 6.4 自定义处理器

通过实现`GXSSOHandler`接口可以自定义未登录情况下的处理逻辑：

```java
@Component
public class CustomSSOHandler implements GXSSOHandler {
    @Override
    public boolean preTokenIsNullAjax(HttpServletRequest request, HttpServletResponse response) {
        // 处理AJAX请求未登录情况
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        try {
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\"}");
        } catch (IOException e) {
            // 处理异常
        }
        return false; // 停止执行
    }

    @Override
    public boolean preTokenIsNull(HttpServletRequest request, HttpServletResponse response) {
        // 处理普通请求未登录情况
        try {
            response.sendRedirect("/login?redirect=" + URLEncoder.encode(request.getRequestURL().toString(), "UTF-8"));
        } catch (IOException e) {
            // 处理异常
        }
        return false; // 停止执行
    }
}
```

## 7. 最佳实践

### 7.1 安全建议

- 生产环境中启用Cookie的Secure和HttpOnly选项
- 配置合理的Token超时时间，平衡安全性和用户体验
- 对敏感操作增加额外的安全验证
- 使用HTTPS协议保护传输层安全
- 定期审计登录日志，及时发现异常登录
- 使用RSA非对称加密保护敏感信息
- 实现IP绑定和设备指纹验证，防止会话劫持

### 7.2 性能优化

- 使用分布式缓存存储Token，提高集群环境下的性能
- 合理配置Token活动超时时间，减少不必要的Token刷新
- 对高频访问的权限验证结果进行缓存
- 使用异步方式记录登录日志，避免影响主流程性能
- 优化验证码生成算法，减少CPU占用
- 使用延迟加载策略初始化SSO服务

### 7.3 集成示例

1. **配置拦截器**

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Resource
    private GXSSOAuthorizationInterceptor ssoAuthorizationInterceptor;
    
    @Resource
    private GXSSOPermissionInterceptor ssoPermissionInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器
        registry.addInterceptor(ssoAuthorizationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**");
        
        // 权限拦截器（必须在登录拦截器之后）
        registry.addInterceptor(ssoPermissionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**");
    }
}
```

2. **配置参数解析器**

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Resource
    private GXLoginUserHandlerMethodArgumentResolver loginUserResolver;
    
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserResolver);
    }
}
```

3. **登录接口实现**

```java
@RestController
@RequestMapping("/api")
public class AuthController {
    @Resource
    private GXSSOService ssoService;
    
    @PostMapping("/login")
    public Dict login(@RequestBody LoginDTO loginDTO, 
                     HttpServletRequest request, 
                     HttpServletResponse response) {
        // 验证用户名密码（略）
        
        // 设置登录信息
        Dict userInfo = Dict.create()
            .set("userId", user.getId())
            .set("username", user.getUsername())
            .set("roles", user.getRoles());
        
        // 写入Cookie并同步到缓存
        ssoService.setCookie(request, response, userInfo);
        
        return Dict.create().set("code", 0).set("msg", "登录成功");
    }
    
    @PostMapping("/logout")
    public Dict logout(HttpServletRequest request, HttpServletResponse response) {
        ssoService.clearLogin(request, response);
        return Dict.create().set("code", 0).set("msg", "注销成功");
    }
}
```

4. **验证码接口实现**

```java
@RestController
@RequestMapping("/api")
public class CaptchaController {
    
    @GetMapping("/captcha")
    public void getCaptcha(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("image/gif");
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
        
        // 创建验证码实例
        ImageCaptcha captcha = ImageCaptcha.getInstance()
            .setLength(4)
            .setWidth(120)
            .setHeight(40)
            .setRandomType(GXRandomType.MIX)
            .setGif(true);
        
        // 生成验证码并写入响应
        String ticket = UUID.randomUUID().toString();
        captcha.generate(request, response.getOutputStream(), ticket);
        
        // 将ticket返回给前端，用于后续验证
        request.getSession().setAttribute("captchaTicket", ticket);
    }
    
    @PostMapping("/verify-captcha")
    public Dict verifyCaptcha(@RequestParam String code, HttpServletRequest request) {
        String ticket = (String) request.getSession().getAttribute("captchaTicket");
        if (ticket == null) {
            return Dict.create().set("code", 1).set("msg", "验证码已过期");
        }
        
        ImageCaptcha captcha = ImageCaptcha.getInstance();
        boolean valid = captcha.verification(request, ticket, code);
        
        if (valid) {
            return Dict.create().set("code", 0).set("msg", "验证成功");
        } else {
            return Dict.create().set("code", 1).set("msg", "验证码错误");
        }
    }
}
```

## 8. 常见问题

### 8.1 Token失效问题

**问题**：用户登录后短时间内Token就失效了。

**解决方案**：
- 检查`tokenTimeout`和`tokenActivityTimeout`配置是否合理
- 确认缓存服务是否正常运行
- 检查是否有其他系统或插件强制使Token失效
- 检查是否启用了IP绑定或浏览器指纹验证，用户IP变化可能导致Token失效

### 8.2 跨域问题

**问题**：前后端分离架构下，Cookie无法正常工作。

**解决方案**：
- 确保前后端域名一致，或配置正确的`cookieDomain`
- 配置正确的跨域策略，允许凭证传递
- 考虑使用Token传递方式代替Cookie

```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin("https://example.com");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}
```

### 8.3 权限控制不生效

**问题**：配置了权限注解但没有生效。

**解决方案**：
- 确认拦截器注册顺序正确（权限拦截器在登录拦截器之后）
- 检查权限表达式是否正确
- 确认`GXSSOAuthorization`接口实现是否正确
- 检查`nothingAnnotationPass`配置是否符合预期
- 确保权限拦截器已正确注册到Spring容器中

### 8.4 验证码显示问题

**问题**：验证码图片无法正常显示或生成。

**解决方案**：
- 检查响应头设置是否正确，特别是Content-Type
- 确保输出流正确关闭，避免图片损坏
- 检查字体文件是否可访问
- 调整验证码参数，如宽度、高度和干扰元素数量

### 8.5 多设备登录问题

**问题**：如何控制用户在多设备上的登录状态。

**解决方案**：
- 在Token中添加设备标识信息
- 实现自定义的Token存储策略，支持按用户ID查询所有Token
- 提供API允许用户查看和管理自己的登录会话
- 实现单设备登录策略，新设备登录时自动踢出旧设备

## 9. 版本历史

| 版本 | 日期 | 主要变更 |
| --- | --- | --- |
| 4.1.2 | 2023-06-01 | 当前版本，增强验证码功能，优化Token管理 |
| 4.1.1 | 2023-03-15 | 增强安全性，修复已知问题 |
| 4.1.0 | 2023-01-10 | 新增插件系统，支持自定义验证逻辑 |
| 4.0.0 | 2022-10-05 | 架构重构，提升性能和可扩展性 |

## 10. 参考资料

- [Spring Security 官方文档](https://docs.spring.io/spring-security/reference/index.html)
- [OWASP 安全指南](https://owasp.org/www-project-web-security-testing-guide/)
- [JWT 官方网站](https://jwt.io/)
- [OAuth 2.0 规范](https://oauth.net/2/)
- [CSRF 防护最佳实践](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [Cookie 安全配置指南](https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#security)