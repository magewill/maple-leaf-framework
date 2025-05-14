# Leaf-Base-Framework 模块开发手册

## 1. 模块简介

leaf-base-framework 是 Maple Leaf Framework 框架的基础核心模块，提供了框架运行所需的通用工具、注解、配置等基础设施，为其他模块提供支持。该模块包含了丰富的工具类、通用注解、异常处理、事件机制等功能，简化了开发人员的日常开发工作。

## 2. 主要依赖

模块主要依赖以下组件：

- Spring Boot：`3.4.x`
- Spring Framework：`6.2.x`
- Caffeine：本地缓存
- Jackson：JSON处理
- Validation API：参数验证

## 3. 核心功能

### 3.1 工具类

#### 3.1.1 常用工具类

- `GXCommonUtils`：通用工具类，提供各种常用的工具方法
- `GXResultUtils`：统一返回结果工具类，用于构建API返回结果
- `GXSpringContextUtils`：Spring上下文工具类，用于获取Bean等操作
- `GXValidatorUtils`：参数验证工具类，基于Hibernate Validator实现，提供手动验证对象的功能，支持分组验证。验证失败时会抛出 `GXBeanValidateException` 异常。
- `GXCglibUtils`：基于Cglib的Bean复制工具类。它提供了高效的Bean属性拷贝、集合拷贝以及Bean与Map之间的相互转换功能。内部使用 `GXBeanCopierCache` 缓存 `BeanCopier` 实例以提升性能，并可配合 `GXCGLibDataConvert` 实现复杂对象的深度拷贝和类型转换。
- `GXBeanCopierCache`：Bean复制器缓存，用于缓存 `BeanCopier` 实例，显著提高Bean复制操作的性能，特别是在高并发和大数据量场景下。

#### 3.1.2 缓存工具类

- `GXCacheKeysUtils`：缓存键工具类，用于统一管理系统中使用的缓存键。它通过从配置文件 (`cache-key.yml`) 加载缓存键前缀，实现了缓存键的集中管理和动态配置，有效避免了缓存键冲突和硬编码问题。内部使用 `ConcurrentHashMap` 缓存已解析的前缀以提高性能。
- `GXCaffeineCacheUtils`：基于Caffeine的本地缓存工具类，封装了对Caffeine缓存的常用操作，如获取、设置、删除缓存等。

#### 3.1.3 上下文工具类

- `GXSpringContextUtils`：Spring上下文工具类，它提供了在非Spring管理的类中访问Spring `ApplicationContext` 的能力。通过此类，可以方便地获取Spring Bean、判断Bean的特性（如是否存在、是否单例）、获取环境配置以及动态注册单例Bean等。
- `GXCurrentRequestContextUtils`：当前请求上下文工具类，用于获取请求信息。它封装了对 `HttpServletRequest` 和 `HttpServletResponse` 对象的常用操作，使得在业务代码中获取请求信息、响应信息以及进行一些安全处理变得更加便捷。主要功能包括：获取请求/响应对象、请求参数、请求头/Cookie、客户端IP，处理Token，提供XSS/SQL注入防护，请求频率限制，IP地址校验与类型判断，以及判断HTTP/RPC调用等。
- `GXTraceIdContextUtils`：链路追踪ID上下文工具类。专注于分布式链路追踪场景，核心功能是生成、管理和传递 TraceId。通过与日志框架（如SLF4J MDC）集成，可以将TraceId自动打印到日志中。支持TraceId的跨线程传递，并在RPC/HTTP调用时传递TraceId，从而串联整个调用链，方便问题排查和系统监控。
- `GXMdcThreadUtils`：MDC（Mapped Diagnostic Context）线程工具类。它主要用于在多线程环境下，特别是使用线程池异步执行任务时，能够安全、正确地传递和管理SLF4J MDC中的上下文信息（如TraceId）。通过包装 `Runnable`、`Callable`、`Supplier` 等任务，确保子线程能够继承父线程的MDC上下文，并在任务执行完毕后清理，防止上下文信息在线程复用时发生错乱或内存泄漏。这对于保持分布式链路追踪的连续性和日志的准确性至关重要。

#### 3.1.4 安全工具类

- `GXSignatureUtils`：签名工具类，用于生成和验证签名
- `GXAuthCodeUtils`：认证码工具类
- `GXTokenManagerUtils`：Token管理工具类
- `GXDBStringEscapeUtils`：数据库字符串转义工具类，防止SQL注入

#### 3.1.5 其他工具类

- `GXEventPublisherUtils`：事件发布工具类，用于发布应用事件
- `GXLoggerUtils`：日志工具类，提供统一的日志记录方法
- `GXCookieHelperUtil`：Cookie辅助工具类
- `GXSpELToolUtils`：Spring表达式语言工具类
- `GXConcurrentToolsUtils`：并发工具类

### 3.2 注解

#### 3.2.7 缓存配置类

- `GXCaffeineCacheConfig`：Caffeine 缓存配置类。负责初始化和配置 `CaffeineCacheManager`。它会读取 `GXCaffeineCacheManagerProperties` 中定义的各个缓存实例的配置信息（如初始容量、过期策略、最大大小等），并注册这些自定义缓存。同时，它也会配置一些默认的缓存实例，并可以设置是否允许动态创建未预定义的缓存。
- `GXCaffeineCacheManagerProperties`：Caffeine 缓存管理器配置属性类。通过 `@ConfigurationProperties(prefix = "caffeine")` 从配置文件（如 `caffeine-cache-config.yml`）中加载所有命名缓存的配置。其内部维护一个 `Map<String, GXCaffeineCacheProperties>` 结构，其中键是缓存名称，值是对应缓存的详细配置。
- `GXCaffeineCacheProperties`：单个 Caffeine 缓存配置属性类。定义了单个缓存实例的具体配置参数，例如：
    - `initialCapacity`：初始缓存空间大小
    - `maximumSize`：缓存的最大条目数
    - `expireAfterAccess`：最后一次访问后经过固定时间过期（秒）
    - `expireAfterWrite`：最后一次写入后经过固定时间过期（秒）
    - `refreshAfterWrite`：写入后固定时间间隔刷新缓存（秒）
    - `weakKeys`：是否使用弱引用键
    - `softValues`：是否使用软引用值
    - `recordStats`：是否开启统计功能

#### 3.2.1 框架注解

- `@GXEnableLeafFramework`：启用Maple Leaf Framework框架的注解。通过在Spring Boot应用的主配置类上添加此注解，可以自动导入框架的核心配置 `GXFrameworkConfig`，并确保其具有最高加载优先级。

#### 3.2.2 缓存注解

- `@GXCacheable`：自定义缓存注解，用于方法结果缓存
- `@GXCacheEvict`：自定义缓存清除注解，用于清除缓存

#### 3.2.3 安全注解

- `@GXPermission`：权限注解，用于方法级别的权限控制
- `@GXPermissionCtl`：权限控制注解，用于控制器级别的权限控制
- `@GXIgnoreLoginIntercept`：忽略登录拦截注解，用于跳过登录验证
- `@GXValidateCRS`：CSRF验证注解，用于防止跨站请求伪造

#### 3.2.4 日志注解

- `@GXBusinessLog`：业务日志注解，用于记录业务操作日志
- `@GXStopWatch`：计时注解，用于方法执行时间统计

#### 3.2.5 数据处理注解

- `@GXIgnoreLoginIntercept`：忽略登录拦截注解，用于标记不需要进行登录验证的接口或控制器，允许未登录用户直接访问。常用于登录接口、注册接口、公开API等场景。
- `@GXFeignHeader`：Feign请求头注解，用于在微服务间调用时，将指定的HTTP请求头信息从调用方透传到被调用方。常用于传递用户认证信息、租户ID、追踪ID等上下文信息。

- `@GXSensitiveData`：敏感数据注解，用于标记敏感数据类
- `@GXSensitiveField`：敏感字段注解，用于标记敏感字段

#### 3.2.6 微服务注解

### 3.3 异常处理

#### 3.3.1 异常类

- 框架定义了一系列业务异常类，用于规范化异常处理
- 提供了全局异常处理机制，统一处理和响应异常

### 3.4 事件机制

#### 3.4.1 事件类

- 框架定义了一系列应用事件，用于解耦业务逻辑
- 提供了事件发布和监听机制，支持同步和异步事件处理

### 3.5 DDD支持

### 3.6 API层定义

### 3.7 切面编程 (AOP)

### 3.8 配置类

本章节将详细介绍 `leaf-base-framework` 框架中 `config` 包下的核心配置类及其功能。

#### 3.8.1 `GXAsyncConfig`

- **功能**: Spring异步任务线程池配置。
- **描述**: 实现 `AsyncConfigurer` 接口，为 `@Async` 注解提供默认的线程池配置。线程池参数经过优化，可以应对高负载场景，防止线程池饱和导致系统不稳定。
- **主要特性**:
    - 动态线程池大小：根据CPU核心数自动调整线程池大小，支持通过配置文件覆盖默认配置。
    - 智能拒绝策略：根据系统负载动态选择拒绝策略，保护系统稳定性。
    - 完善的异常处理：捕获并记录所有异步任务异常，提供详细的诊断信息。
    - 任务执行监控：监控长时间运行的任务和超时任务，便于性能分析和问题排查。
    - 优雅关闭：确保应用关闭时所有任务都能完成，避免数据不一致。
    - 线程池预热：预先创建核心线程，避免首次任务执行延迟。
    - 自动资源释放：非核心线程在空闲时自动释放，减少资源占用。
- **使用方法**: 通过 `@EnableAsync` 注解启用异步支持，在需要异步执行的方法上添加 `@Async` 注解即可。可以通过配置文件 `maple.framework.async.*` 相关属性进行自定义配置。

#### 3.8.2 `GXCORSConfig`

- **功能**: 跨域资源共享(CORS)配置。
- **描述**: 提供Web应用的跨域资源共享(CORS)支持，允许从不同源的客户端安全地访问API。通过配置允许的源、HTTP方法、请求头以及是否允许携带凭证等参数，实现细粒度的跨域访问控制。
- **安全说明**:
    - 当 `allowCredentials` 设置为 `true` 时，不允许将 `allowOrigins` 设置为 `*`。
    - 暴露必要的响应头，确保客户端能够正确处理跨域请求。
    - 所有配置参数都可通过外部属性文件进行定制。
- **使用方法**: 通过配置文件 `cors.allow.*` 相关属性进行配置。

#### 3.8.3 `GXCommandLineRunner`

- **功能**: 应用启动后任务执行器。
- **描述**: 实现Spring Boot的 `CommandLineRunner` 接口，用于在应用启动完成后执行一系列初始化任务。它会自动收集所有实现了 `GXCommandLineRunnerService` 接口的Bean，并按顺序执行它们的 `run` 方法。
- **线程安全**: 任务执行在应用启动线程中串行进行，不存在并发问题。使用 `AtomicInteger` 计数器安全地跟踪任务执行情况。每个任务的异常都被单独捕获和处理，不会影响其他任务的执行。
- **使用方法**: 创建自定义的启动任务类并实现 `GXCommandLineRunnerService` 接口，框架会自动执行。

#### 3.8.4 `GXFilterConfig`

- **功能**: Web过滤器配置。
- **描述**: 负责注册和配置应用中使用的各种Servlet过滤器，主要包括XSS防护过滤器、RequestContextFilter、ServletRequestPathFilter 和 CommonsRequestLoggingFilter。
- **主要特性**:
    - `GXXssFilter`: XSS防护过滤器，应用于所有请求路径，提供全局防护。优先级设置为最高，确保在其他过滤器之前执行。
    - `RequestContextFilter`: 用于在子线程中获取上下文对象。可以将当前请求的上下文对象存储在线程本地变量中。
    - `ServletRequestPathFilter`: 用于在子线程中获取请求路径。可以将当前请求的路径存储在线程本地变量中。
    - `CommonsRequestLoggingFilter`: 用于记录请求日志，会将请求的详细信息记录到日志中。
- **使用方法**: 过滤器默认启用或根据配置文件 `maple.framework.web.filter.*.enabled` 属性进行配置。

#### 3.8.5 `GXFrameworkConfig`

- **功能**: 框架核心配置。
- **描述**: 提供自定义JSON序列化处理（特别是对null值的处理策略）、Bean验证配置（支持快速失败模式）和组件自动扫描配置。
- **主要特性**:
    - `ObjectMapper` 自定义: 自定义null值处理策略，根据字段类型返回不同的默认值；忽略未知属性，提高反序列化的健壮性；允许序列化空Bean对象。
    - `LocalValidatorFactoryBean` 配置: 支持Bean验证的快速失败模式，在发现第一个验证错误后立即停止。
    - `@ComponentScan("cn.maple")`: 自动扫描 `cn.maple` 包下的组件。
- **使用方法**: 框架自动加载该配置。

#### 3.8.6 `aware` 包

- **`GXApplicationContextAware`**: Spring应用上下文感知类。实现 `ApplicationContextAware` 接口，在Spring容器启动时自动获取 `ApplicationContext` 并设置到 `GXApplicationContextSingleton` 单例中。通过 `@Order(Ordered.HIGHEST_PRECEDENCE)` 设置为最高优先级。
- **`GXApplicationContextSingleton`**: Spring应用上下文单例持有类。使用枚举实现单例模式，用于在应用的任何位置获取Spring的 `ApplicationContext` 对象。具有线程安全、序列化安全和反射安全的特性。

#### 3.8.7 `cache` 包

- **`GXCaffeineCacheConfig`**: Caffeine缓存配置类。提供基于Caffeine的本地缓存管理器配置。支持自定义缓存配置和默认缓存配置。Caffeine缓存是线程安全的。
    - **配置来源**: 通过注入 `GXCaffeineCacheManagerProperties` 来加载自定义缓存配置。
    - **默认缓存**: 配置了多个默认缓存实例，如 `FRAMEWORK-CACHE`, `__DEFAULT__`, `UNIVERSAL-CACHE` 等，并设置了合理的默认参数。
    - **动态创建**: 禁止自动创建缓存 (`caffeineCacheManager.dynamic=false`)，以避免缓存泄漏和内存溢出风险。
- **相关属性类 (位于 `properties` 包)**:
    - **`GXCaffeineCacheManagerProperties`**: Caffeine缓存管理器属性配置类。通过 `@PropertySource` 加载 `caffeine-cache-config.yml` 配置文件，并使用 `@ConfigurationProperties(prefix = "caffeine")` 绑定属性。包含一个 `Map<String, GXCaffeineCacheProperties>` 用于存储各个缓存的配置。
    - **`GXCaffeineCacheProperties`**: 单个Caffeine缓存配置属性类。包含初始容量、最大条数、过期策略（访问后过期、写入后过期）、刷新策略、引用类型（弱引用、软引用）和统计功能开关等配置项。

#### 3.8.8 `support` 包

- **`GXCachingConfigurerSupport`**: 解决多CacheManager配置的支持类。实现 `CachingConfigurer` 接口，用于自定义Spring缓存的配置。
    - **默认CacheManager**: 提供默认的 `CaffeineCacheManager` 作为缓存管理器。
    - **自定义错误处理器**: 提供 `LoggingCacheErrorHandler`，增强日志记录能力，记录缓存操作（获取、写入、驱逐、清空）中发生的异常，但不会阻止异常传播。

#### 3.8.9 `web` 包

- **`GXBaseWebConfig`**: Web基础配置类。提供Web应用的基础配置，例如注册 `GXBaseRequestLoggingFilter`。
    - **`GXBaseRequestLoggingFilter`**: 请求日志记录过滤器，用于记录HTTP请求的详细信息，如URL、请求头、请求参数、请求体等，便于调试和问题排查。该过滤器是线程安全的。

### 3.7 切面编程 (AOP)

#### 3.7.1 业务日志切面 (`GXBusinessLogAspect`)

- **功能**：拦截标记了 `@GXBusinessLog` 注解的方法，自动记录业务操作日志。
- **特点**：
    - 线程安全。
    - 记录信息包括：业务名称、描述、方法名、请求参数、客户端IP、执行时间、请求时间、用户名。
    - 支持在类级别和方法级别使用注解，方法级别注解会覆盖类级别。
    - 目标方法抛出的异常会被记录并继续向上抛出。
    - 日志记录过程中的异常会被捕获并记录，不影响主业务流程。
- **依赖**：需要 `GXBusinessLogService` 的实现来获取用户名和保存日志。

#### 3.7.2 缓存清除切面 (`GXCacheEvictAspect`)

- **功能**：拦截标记了 `@GXCacheEvict` 注解的方法，在方法成功执行后清除指定的缓存。
- **特点**：
    - 线程安全，使用分布式锁（依赖 `GXBaseCacheLockService`）确保缓存清除的原子性。
    - 支持动态生成缓存键，可引用方法参数及其属性。
    - 如果未指定 `cacheKey`，默认使用 `类名:方法名` 作为缓存键。
    - 目标类需要实现 `evictCacheData(String cacheKey, Object... args)` 方法来执行实际的缓存清除操作。
    - 只有当方法执行成功且返回结果不为null时才会触发缓存清除。
    - 缓存清除异常不影响主业务流程。

#### 3.7.3 缓存读取切面 (`GXCacheableAspect`)

- **功能**：拦截标记了 `@GXCacheable` 注解的方法，实现方法返回值的缓存。
- **特点**：
    - 线程安全。
    - 在方法执行前检查缓存，若命中则直接返回缓存数据，否则执行方法并将结果存入缓存。
    - 支持动态生成缓存键，可引用方法参数及其属性。
    - 如果未指定 `cacheKey`，默认使用 `类名:方法名` 作为缓存键。
    - 目标类需要实现 `getDataFromCache(String cacheKey, Object... args)` 方法从缓存获取数据，以及 `setCacheData(String cacheKey, Object result, Object... args)` 方法将数据存入缓存。
    - 支持通过 `@GXCacheable` 的 `retType` 和 `methodName` 属性对缓存结果进行类型转换。
    - 缓存操作异常不影响主业务流程。

#### 3.7.4 方法耗时监控切面 (`GXStopWatchAspect`)

- **功能**：拦截标记了 `@GXStopWatch` 注解的方法或类中的所有方法，监控其执行时间。
- **特点**：
    - 记录方法的调用参数、返回结果和执行耗时。
    - 集成 `GXTraceIdContextUtils`，日志输出会包含分布式追踪ID。
    - 通过 `@Order(Ordered.HIGHEST_PRECEDENCE)` 确保切面最先执行，以准确测量方法耗时。
    - 目标方法异常不会被吞噬。

#### 3.7.5 请求参数验证切面 (`GXValidateRequestParamAspect`)

- **功能**：拦截标记了 `@GetMapping` 注解的方法，自动对请求参数进行JSR 303/JSR 380 (Bean Validation) 验证。
- **特点**：
    - 线程安全，使用标准的 `jakarta.validation.Validator`。
    - 验证失败时抛出 `GXBeanValidateException`，包含详细的验证错误信息。
    - 默认优先级较高 (`Ordered.HIGHEST_PRECEDENCE + 100`)，确保在业务逻辑执行前进行参数验证。
    - 可以扩展到其他HTTP请求方法注解如 `@PostMapping` 等。

#### 3.6.1 API接口

- `GXBaseServeApi`：一个基础 API 接口，定义了通用的数据操作方法，支持条件查询、分页、更新、删除等操作，设计上遵循线程安全和内存安全的最佳实践。
- `GXBaseServeApiImpl`：`GXBaseServeApi` 接口的基础实现，封装了常用的数据操作方法，通过反射机制调用底层服务类的方法，支持静态绑定和动态绑定两种方式指定目标服务类，并实现了线程安全和内存安全的最佳实践。

#### 3.6.2 API数据传输对象 (DTO)

- `GXBaseApiReqDto`：基础的API请求DTO，继承自 `GXBaseReqDto`，作为所有API请求DTO的基类。
- `GXQueryParamApiReqDto`：通用的查询参数API请求DTO，继承自 `GXBaseApiReqDto`。它封装了进行复杂查询所需的各种参数，例如：
    - `tableName` 和 `tableNameAlias`：主表名及其别名，用于JOIN查询。
    - `page` 和 `pageSize`：分页参数，默认为第一页，每页默认大小。
    - `condition`：查询条件列表，使用 `GXCondition` 对象定义复杂的过滤逻辑。
    - `columns`：需要查询的数据列集合。
    - `orderByField`：排序字段，一个Map，键为字段名，值为排序方向（asc/desc）。
    - `groupByField`：分组字段集合。
    - `methodName`：`GXBaseData` 及其子类中的方法名，用于在查询后自动调用额外处理逻辑。
    - `having`：SQL中的HAVING条件集合。
    - `copyOptions`：Hutool `CopyOptions`，用于对象复制时的额外配置。
    - `limit`：限制查询结果的条数。
    - `joins`：JOIN连接信息列表，使用 `GXJoinDto` 定义连接表、类型和条件。
    - `extraData`：额外参数，配合 `methodName` 使用。
    - `rawSQL`：原始SQL语句，如果提供此字段，框架将直接执行此SQL，不再自动拼接。
    - `ignoreDataFilter`：是否忽略数据权限，默认为false。
- `GXBaseApiResDto`：基础的API响应DTO，继承自 `GXBaseResDto`，作为所有API响应DTO的基类。

#### 3.5.1 领域驱动设计

- 提供了DDD相关的基础设施，支持领域驱动设计开发
- 包含实体、值对象、仓储、领域服务等DDD概念的基础实现

## 4. 使用方法

### 4.1 启用框架

在Spring Boot应用的主类上添加 `@GXEnableLeafFramework` 注解即可启用 Maple Leaf Framework 的核心功能。此注解会自动导入框架的基础配置，并赋予最高加载优先级，确保框架组件正确初始化。

**示例：**

```java
@SpringBootApplication
@GXEnableLeafFramework
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 4.2 使用工具类

**GXCacheKeysUtils 示例：**

首先，在 `src/main/resources/dev/cache-key.yml` (或其他对应环境的) 文件中配置缓存键：

```yaml
cache-key:
  keys:
    - sys.config: "maple:framework:config"
    - sys.captcha: "maple:framework:captcha"
    - user.info: "maple:biz:user:info"
```

然后，在代码中使用 `GXCacheKeysUtils`：

```java
// 注入 GXCacheKeysUtils (通常由Spring自动注入)
@Autowired
private GXCacheKeysUtils cacheKeysUtils;

// 获取系统配置相关的缓存键
String configCacheKey = cacheKeysUtils.getSysConfigKey("siteName"); // 生成: maple:framework:config:siteName

// 获取用户模块相关的缓存键 (假设配置文件中定义了 user.info)
String userInfoKey = cacheKeysUtils.getCacheKey("user.info", userId.toString()); // 生成: maple:biz:user:info:123

// 如果配置名未在 cache-key.yml 中找到，会使用默认前缀
String defaultKey = cacheKeysUtils.getCacheKey("unknown.config", "someValue"); // 生成: geoxus:default:someValue (如果 unknown.config 未配置)
```

**GXCaffeineCacheUtils 示例 (通常与 `@GXCacheable` 等注解配合使用，直接使用场景较少)：**

```java
// 假设 GXCaffeineCacheUtils 已经通过 Spring 注入
@Autowired
private GXCaffeineCacheUtils caffeineCacheUtils; // 注意：实际框架中可能不直接提供此类，而是通过 CacheManager 操作

// 假设有一个 CacheManager bean
@Autowired
private CacheManager cacheManager;

public void manageCacheExplicitly() {
    Cache userCache = cacheManager.getCache("userCacheName"); // 获取名为 userCacheName 的缓存实例
    
    // 存入缓存
    userCache.put("user:1", new User("John"));
    
    // 获取缓存
    User user = userCache.get("user:1", User.class);
    
    // 清除缓存
    userCache.evict("user:1");
}
```

```java
// 使用通用工具类
String uuid = GXCommonUtils.getUUID();

// 使用结果工具类构建API返回结果
GXResultUtils.ok(data);
GXResultUtils.error("操作失败");

// 使用Spring上下文工具类获取Bean
UserService userService = GXSpringContextUtils.getBean(UserService.class);

// 使用验证工具类验证参数
// GXValidatorUtils.validateEntity(object, groups...) 会在验证失败时直接抛出 GXBeanValidateException
// 如果需要获取详细的验证结果，可以自行捕获异常或使用标准的 Validator API
GXValidatorUtils.validateEntity(userDTO); // 验证所有约束
GXValidatorUtils.validateEntity(userDTO, GXAddGroup.class); // 仅验证 AddGroup 分组的约束
GXValidatorUtils.validateEntity(userDTO, "userData"); // 验证并指定JSON名称，用于错误信息展示

// 使用Bean复制工具类 (GXCglibUtils)
// 1. 简单的Bean拷贝
// 假设有 UserEntity 和 UserDTO 两个类
UserEntity userEntity = new UserEntity();
userEntity.setId(1L);
userEntity.setUsername("testUser");

UserDTO userDTO = GXCglibUtils.copy(userEntity, UserDTO.class);
System.out.println(userDTO.getUsername()); // 输出: testUser

// 2. 拷贝到已存在的对象
UserDTO existingUserDTO = new UserDTO();
GXCglibUtils.copy(userEntity, existingUserDTO);

// 3. 集合拷贝
List<UserEntity> userEntityList = Arrays.asList(userEntity);
List<UserDTO> userDTOList = GXCglibUtils.copyList(userEntityList, UserDTO::new);

// 4. 集合拷贝并使用回调进行额外处理
List<UserDTO> userDTOListWithCallback = GXCglibUtils.copyList(userEntityList, UserDTO::new, (sourceObj, targetObj) -> {
    // 可以在这里对拷贝后的 targetObj 进行额外处理
    // targetObj.setAdditionalProperty("processed"); // 假设UserDTO有additionalProperty字段
});

// 5. Bean 转 Map
Map<String, Object> beanMap = GXCglibUtils.toMap(userEntity);
System.out.println(beanMap.get("username")); // 输出: testUser

// 6. Map 转 Bean
Map<String, Object> map = new HashMap<>();
map.put("id", 2L);
map.put("username", "anotherUser");
UserEntity newUserEntity = GXCglibUtils.toBean(map, UserEntity.class);
System.out.println(newUserEntity.getUsername()); // 输出: anotherUser

// GXCglibUtils 在进行复杂类型拷贝时，会利用 GXCGLibDataConvert (如果 useConverter 为 true 时创建 BeanCopier)。
// GXCGLibDataConvert 是一个强大的 Converter，支持深度拷贝和各种类型转换，通常不需要用户显式操作。
```

**使用 `GXSpringContextUtils` 获取Bean和环境配置：**

```java
// 1. 获取指定类型的Bean
UserService userService = GXSpringContextUtils.getBean(UserService.class);
if (userService != null) {
    userService.doSomething();
}

// 2. 获取指定名称和类型的Bean
DataSource dataSource = GXSpringContextUtils.getBean("masterDataSource", DataSource.class);

// 3. 判断Bean是否存在
if (GXSpringContextUtils.containsBean("orderService")) {
    OrderService orderService = GXSpringContextUtils.getBean("orderService", OrderService.class);
    // ...
}

// 4. 获取环境配置
Environment env = GXSpringContextUtils.getEnvironment();
String serverPort = env.getProperty("server.port");

// 5. 注册单例Bean
MyCustomService customService = new MyCustomService();
GXSpringContextUtils.registerSingleton("myCustomService", customService);

// 6. 获取指定类型的所有Bean
Map<String, NotificationService> notificationServices = GXSpringContextUtils.getBeans(NotificationService.class);
notificationServices.forEach((name, service) -> {
    System.out.println("Bean名称: " + name);
    service.sendNotification("Hello");
});
```

**使用 `GXCurrentRequestContextUtils` 获取请求信息和进行安全处理：**

```java
// 获取当前 HttpServletRequest 对象
HttpServletRequest request = GXCurrentRequestContextUtils.getHttpServletRequest();

// 获取请求参数 (String 类型)
String username = GXCurrentRequestContextUtils.getHttpParam("username", String.class);

// 获取请求头
String userAgent = GXCurrentRequestContextUtils.getHeader("User-Agent");

// 获取客户端 IP 地址
String clientIp = GXCurrentRequestContextUtils.getClientIP();
System.out.println("Client IP: " + clientIp);

// 获取登录用户的ID (假设 Token 中存储了 userId)
// GXTokenConstant.TOKEN_NAME 是配置的token名, "userId"是token中存放用户id的字段名
Long userId = GXCurrentRequestContextUtils.getLoginFieldFromToken(GXTokenConstant.TOKEN_NAME, "userId", Long.class);
if (userId != null) {
    System.out.println("Logged in User ID: " + userId);
}

// XSS 过滤示例
String potentiallyMaliciousInput = "<script>alert('XSS')</script>Some safe text";
String safeInput = GXCurrentRequestContextUtils.filterXSS(potentiallyMaliciousInput);
System.out.println("Safe input: " + safeInput); // 输出: Some safe text

// 获取安全的请求参数 (自动进行 XSS 过滤和 SQL 注入检查)
try {
    String safeUsername = GXCurrentRequestContextUtils.getSafeHttpParam("username", String.class);
    System.out.println("Safe username: " + safeUsername);
} catch (GXBusinessException e) {
    System.err.println("Parameter validation failed: " + e.getMessage());
}

// 判断是否是 HTTP 调用
if (GXCurrentRequestContextUtils.isHTTP()) {
    System.out.println("Current context is HTTP request.");
}

// 检查请求频率 (示例：限制 clientId 为 "user123" 的用户每分钟最多请求 MAX_REQUESTS_PER_MINUTE 次)
String currentClientId = "user123"; // 实际应用中应获取真实客户端标识
if (GXCurrentRequestContextUtils.isRateLimited(currentClientId)) {
    System.out.println("Request rate limit exceeded for client: " + currentClientId);
    // 返回错误响应或执行相应处理
} else {
    System.out.println("Request accepted for client: " + currentClientId);
}

// 获取 IP 地址类型
String ipType = GXCurrentRequestContextUtils.getIPAddressType(clientIp);
System.out.println("IP Address Type: " + ipType);
```

**使用 `GXTraceIdContextUtils` 进行链路追踪：**

```java
// 在请求开始时 (例如在 Filter 或 Interceptor 中)
GXTraceIdContextUtils.setTraceId(); // 如果没有外部传入的 TraceId，则生成一个新的
// 或者，如果从上游服务接收到 TraceId (例如从请求头 X-Trace-Id)
// String upstreamTraceId = request.getHeader("X-Trace-Id");
// if (CharSequenceUtil.isNotBlank(upstreamTraceId)) {
//     GXTraceIdContextUtils.setTraceId(upstreamTraceId);
// } else {
//     GXTraceIdContextUtils.setTraceId();
// }

System.out.println("Current TraceId: " + GXTraceIdContextUtils.getTraceId());

// 业务逻辑代码...
// log.info("Processing user request..."); // 日志输出会自动带上 TraceId (如果日志框架配置了 MDC)

// 模拟异步操作
ExecutorService executorService = Executors.newSingleThreadExecutor();
Runnable task = () -> {
    // 在新线程中，需要手动传递 TraceId
    String parentTraceId = GXTraceIdContextUtils.getTraceId(); // 获取父线程的 TraceId
    GXTraceIdContextUtils.setTraceId(parentTraceId); // 设置到子线程
    try {
        System.out.println("Async task TraceId: " + GXTraceIdContextUtils.getTraceId());
        // log.info("Async task processing...");
    } finally {
        GXTraceIdContextUtils.removeTraceId(); // 清理子线程的 TraceId
    }
};
executorService.submit(task);
executorService.shutdown();

// 在请求结束时 (例如在 Filter 或 Interceptor 的 finally 块中)
GXTraceIdContextUtils.removeTraceId();
```

**使用 `GXMdcThreadUtils` 在异步任务中传递MDC上下文：**

```java
import org.slf4j.MDC;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

// ... (假设 GXTraceIdContextUtils 和 GXMdcThreadUtils 已正确配置和注入)

public class MdcAsyncExample {

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    public void processRequestWithMdc() {
        // 1. 在主线程设置 TraceId (或其他MDC信息)
        GXTraceIdContextUtils.setTraceId(); // 生成或设置一个TraceId
        MDC.put("customKey", "customValue");
        System.out.println("Main thread TraceId: " + GXTraceIdContextUtils.getTraceId() + ", CustomKey: " + MDC.get("customKey"));

        // 2. 使用 GXMdcThreadUtils 包装 Runnable
        Runnable mdcRunnable = GXMdcThreadUtils.wrap(() -> {
            System.out.println("Runnable - Current thread TraceId: " + GXTraceIdContextUtils.getTraceId() + ", CustomKey: " + MDC.get("customKey"));
            // 业务逻辑...
        });
        executor.submit(mdcRunnable);

        // 3. 使用 GXMdcThreadUtils 包装 Callable
        Future<String> futureResult = executor.submit(GXMdcThreadUtils.wrap(() -> {
            System.out.println("Callable - Current thread TraceId: " + GXTraceIdContextUtils.getTraceId() + ", CustomKey: " + MDC.get("customKey"));
            return "Callable Result with MDC";
        }));

        try {
            System.out.println(futureResult.get());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 4. 使用 GXMdcThreadUtils 包装 Supplier (常用于 CompletableFuture)
        Supplier<String> mdcSupplier = GXMdcThreadUtils.wrapSupplier(() -> {
            System.out.println("Supplier - Current thread TraceId: " + GXTraceIdContextUtils.getTraceId() + ", CustomKey: " + MDC.get("customKey"));
            return "Supplier Result with MDC";
        });
        CompletableFuture.supplyAsync(mdcSupplier, executor)
                         .thenAccept(result -> System.out.println("CompletableFuture result: " + result));
        
        // 5. 使用 GXMdcThreadUtils 提供的便捷方法 runAsync 和 supplyAsync
        GXMdcThreadUtils.runAsync(() -> {
            System.out.println("runAsync - Current thread TraceId: " + GXTraceIdContextUtils.getTraceId() + ", CustomKey: " + MDC.get("customKey"));
        }, executor);

        GXMdcThreadUtils.supplyAsync(() -> {
            System.out.println("supplyAsync - Current thread TraceId: " + GXTraceIdContextUtils.getTraceId() + ", CustomKey: " + MDC.get("customKey"));
            return "supplyAsync Result";
        }, executor).thenAccept(System.out::println);

        // 清理主线程的MDC信息 (通常在请求结束时)
        GXTraceIdContextUtils.removeTraceId();
        MDC.remove("customKey");
        MDC.clear(); 

        // 关闭线程池 (在应用关闭时)
        // executor.shutdown();
    }
}
```

### 4.3 使用缓存注解

```java
// 1. 获取指定类型的Bean
UserService userService = GXSpringContextUtils.getBean(UserService.class);
if (userService != null) {
    userService.doSomething();
}

// 2. 获取指定名称和类型的Bean
DataSource dataSource = GXSpringContextUtils.getBean("masterDataSource", DataSource.class);

// 3. 判断Bean是否存在
if (GXSpringContextUtils.containsBean("orderService")) {
    OrderService orderService = GXSpringContextUtils.getBean("orderService", OrderService.class);
    // ...
}

// 4. 获取环境配置
Environment env = GXSpringContextUtils.getEnvironment();
String serverPort = env.getProperty("server.port");

// 5. 注册单例Bean
MyCustomService customService = new MyCustomService();
GXSpringContextUtils.registerSingleton("myCustomService", customService);

// 6. 获取指定类型的所有Bean
Map<String, NotificationService> notificationServices = GXSpringContextUtils.getBeans(NotificationService.class);
notificationServices.forEach((name, service) -> {
    System.out.println("Bean名称: " + name);
    service.sendNotification("Hello");
});
```

### 4.3 使用缓存注解

```java
@Service
public class UserServiceImpl implements UserService {
    
    @Override
    @GXCacheable(name = "user", key = "#id", expire = 3600)
    public UserEntity getById(Long id) {
        return userMapper.selectById(id);
    }
    
    @Override
    @GXCacheEvict(name = "user", key = "#user.id")
    public boolean updateUser(UserEntity user) {
        return userMapper.updateById(user) > 0;
    }
}
```

### 4.4 使用权限注解

```java
@RestController
@RequestMapping("/api/users")
@GXPermissionCtl("sys:user")
public class UserController implements GXBaseController{
    
    @GetMapping("/list")
    @GXPermission("list")
    public GXResultUtils<List<UserVO>> list() {
        // 需要 sys:user:list 权限
        return GXResultUtils.ok(userService.list());
    }
    
    @PostMapping("/save")
    @GXPermission("save")
    public GXResultUtils<Boolean> save(@RequestBody UserDTO userDTO) {
        // 需要 sys:user:save 权限
        return GXResultUtils.ok(userService.save(userDTO));
    }
    
    @GetMapping("/public/info")
    @GXIgnoreLoginIntercept
    public GXResultUtils<UserVO> publicInfo() {
        // 不需要登录即可访问
        return GXResultUtils.ok(userService.getPublicInfo());
    }
}
```

### 4.5 使用业务日志注解

```java
@Service
public class UserServiceImpl implements UserService {
    
    @Override
    @GXBusinessLog(value = "创建用户", operateType = "CREATE")
    public boolean createUser(UserDTO userDTO) {
        UserEntity entity = convertToEntity(userDTO);
        return userMapper.insert(entity) > 0;
    }
    
    @Override
    @GXBusinessLog(value = "更新用户", operateType = "UPDATE")
    public boolean updateUser(UserDTO userDTO) {
        UserEntity entity = convertToEntity(userDTO);
        return userMapper.updateById(entity) > 0;
    }
}
```

### 4.6 使用计时注解

```java
@Service
public class ComplexServiceImpl implements ComplexService {
    
    @Override
    @GXStopWatch("复杂计算操作")
    public Result complexOperation() {
        // 执行耗时操作
        return result;
    }
}
```

### 4.7 使用敏感数据注解

```java
@Data
@GXSensitiveData
public class UserDTO {
    private Long id;
    private String username;
    
    @GXSensitiveField(strategy = SensitiveStrategy.PHONE)
    private String phone;
    
    @GXSensitiveField(strategy = SensitiveStrategy.EMAIL)
    private String email;
    
    @GXSensitiveField(strategy = SensitiveStrategy.ID_CARD)
    private String idCard;
}
```

### 4.8 使用事件机制

```java
// 发布事件
UserCreatedEvent event = new UserCreatedEvent(this, user);
GXEventPublisherUtils.publishEvent(event);

// 监听事件
@Component
public class UserEventListener {
    
    @EventListener
    public void handleUserCreatedEvent(UserCreatedEvent event) {
        UserEntity user = event.getUser();
        // 处理用户创建事件
    }
}
```

### 4.9 使用忽略登录拦截注解

`@GXIgnoreLoginIntercept` 注解可以用于方法或类级别，标记后该接口或该控制器下的所有接口将跳过登录验证。

**使用场景：**

- 登录、注册、找回密码等身份认证相关接口
- 公开的API接口（如获取公共配置、公开内容等）
- 健康检查、监控接口
- 静态资源访问
- 第三方回调接口

**示例：**

```java
// 在方法上使用
@RestController
@RequestMapping("/api/user")
public class UserController implements GXBaseController{
    
    @PostMapping("/login")
    @GXIgnoreLoginIntercept
    public Result login(@RequestBody LoginDTO loginDTO) {
        // 登录逻辑
        return Result.success();
    }
}

// 在类上使用
@RestController
@RequestMapping("/api/public")
@GXIgnoreLoginIntercept
public class PublicController implements GXBaseController {
    
    @GetMapping("/config")
    public Result getPublicConfig() {
        // 获取公共配置
        return Result.success();
    }
}
```

### 4.10 使用Feign请求头注解

`@GXFeignHeader` 注解用于 Feign 客户端接口的方法上，可以指定需要从当前请求透传到目标服务的 HTTP 请求头。

**使用场景：**

- 传递认证令牌（如JWT Token）
- 传递租户标识（多租户系统）
- 传递请求追踪ID（分布式追踪）
- 传递语言偏好、时区等用户上下文

**参数说明：**

- `names`：String[] 类型，指定需要透传的请求头名称数组。如果为空，则不透传任何请求头。

**示例：**

```java
@FeignClient(name = "user-service", url = "${service.user.url}")
public interface UserFeignClient {
    
    // 透传Authorization和X-Tenant-Id两个请求头
    @GetMapping("/api/users/{id}")
    @GXFeignHeader(names = {"Authorization", "X-Tenant-Id"})
    UserVO getUserById(@PathVariable("id") Long id);
    
    // 透传所有追踪相关的请求头
    @PostMapping("/api/users")
    @GXFeignHeader(names = {"X-Request-ID", "X-Trace-ID"})
    Result createUser(@RequestBody UserDTO userDTO);
}
```

在实际使用中，通常需要配合一个 `RequestInterceptor` 来实现请求头的获取和设置逻辑。

## 5. 最佳实践

### 5.1 统一返回结果

使用`GXResultUtils`构建统一的API返回结果：

```java
@RestController
@RequestMapping("/api/users")
public class UserController implements GXBaseController {
    
    @GetMapping("/{id}")
    public GXResultUtils<UserVO> getById(@PathVariable Long id) {
        UserVO user = userService.getById(id);
        if (user == null) {
            return GXResultUtils.error("用户不存在");
        }
        return GXResultUtils.ok(user);
    }
    
    @PostMapping
    public GXResultUtils<Boolean> save(@RequestBody @Valid UserDTO userDTO) {
        boolean result = userService.save(userDTO);
        return result ? GXResultUtils.ok() : GXResultUtils.error("保存失败");
    }
}
```

### 5.2 异常处理

使用框架提供的异常类和全局异常处理机制：

```java
// 业务逻辑中抛出业务异常
public UserEntity getByUsername(String username) {
    UserEntity user = userMapper.selectByUsername(username);
    if (user == null) {
        throw new GXBusinessException("用户不存在");
    }
    return user;
}

// 全局异常处理器
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(GXBusinessException.class)
    public GXResultUtils<Object> handleBusinessException(GXBusinessException e) {
        return GXResultUtils.error(e.getMessage());
    }
    
    @ExceptionHandler(Exception.class)
    public GXResultUtils<Object> handleException(Exception e) {
        log.error("系统异常", e);
        return GXResultUtils.error("系统异常，请联系管理员");
    }
}
```

### 5.3 参数验证

结合框架的验证工具和Spring Validation进行参数验证：

```java
@Data
public class UserDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 20, message = "密码长度必须在6-20之间")
    private String password;
    
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    
    @Email(message = "邮箱格式不正确")
    private String email;
}

@RestController
@RequestMapping("/api/users")
public class UserController implements GXBaseController {
    
    @PostMapping
    public GXResultUtils<Boolean> save(@RequestBody @Valid UserDTO userDTO, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return GXResultUtils.error(bindingResult.getFieldError().getDefaultMessage());
        }
        return GXResultUtils.ok(userService.save(userDTO));
    }
}
```

## 6. 常见问题

### 6.1 Spring上下文获取问题

**问题**：在某些非Spring管理的类中无法使用`GXSpringContextUtils`获取Bean。

**解决方案**：确保在应用启动时已经初始化了Spring上下文，可以在启动类中添加以下代码：

```java
@SpringBootApplication
@GXEnableLeafFramework
public class Application implements ApplicationContextAware {
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        GXSpringContextUtils.setApplicationContext(applicationContext);
    }
    
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 6.2 缓存注解不生效

**问题**：使用`@GXCacheable`和`@GXCacheEvict`注解但缓存不生效。

**解决方案**：
1. 确保启用了框架的缓存支持
2. 检查缓存名称和键是否正确设置
3. 确保方法是通过Spring代理调用的，而不是内部调用

### 6.3 权限注解不生效

**问题**：使用`@GXPermission`和`@GXPermissionCtl`注解但权限控制不生效。

**解决方案**：
1. 确保实现了权限检查的拦截器或切面
2. 检查权限标识是否正确设置
3. 确保方法是通过Spring代理调用的，而不是内部调用

## 7. 参考资料

- [Spring Framework 官方文档](https://docs.spring.io/spring-framework/docs/current/reference/html/)
- [Spring Boot 官方文档](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Caffeine 官方文档](https://github.com/ben-manes/caffeine/wiki)
- [Jackson 官方文档](https://github.com/FasterXML/jackson-docs)