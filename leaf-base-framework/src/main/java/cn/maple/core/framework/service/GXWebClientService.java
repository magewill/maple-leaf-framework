package cn.maple.core.framework.service;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

import java.util.Map;

/**
 * WebClient服务接口
 * <p>
 * 该接口定义了WebClient相关的服务方法，主要用于提供HTTP请求所需的认证信息和配置。
 * 在使用Spring WebClient或HttpExchange进行HTTP请求时，通常需要添加认证Token等信息，
 * 该接口的实现类负责提供这些信息。
 * </p>
 *
 * <p><strong>核心功能：</strong></p>
 * <ul>
 *   <li>提供HTTP请求认证Token</li>
 *   <li>支持WebClient和HttpExchange的认证机制</li>
 *   <li>与Spring的WebClient和HttpServiceProxyFactory集成</li>
 *   <li>记录HTTP请求和响应信息，便于调试和监控</li>
 * </ul>
 *
 * <p><strong>线程安全性：</strong></p>
 * <ul>
 *   <li>该接口的实现类应确保线程安全，因为它会在多线程环境中被调用</li>
 *   <li>实现类中的Token生成或获取逻辑应考虑并发访问情况</li>
 *   <li>建议使用线程安全的缓存机制，如ConcurrentHashMap或Caffeine</li>
 *   <li>对于需要定期刷新的Token，建议使用原子引用(AtomicReference)或读写锁(ReentrantReadWriteLock)</li>
 *   <li>避免使用同步块获取Token，可考虑使用双重检查锁定模式或CAS操作</li>
 *   <li>日志记录方法应避免阻塞主线程，可考虑使用异步日志或缓冲区</li>
 * </ul>
 *
 * <p><strong>性能优化：</strong></p>
 * <ul>
 *   <li>Token获取应尽可能高效，避免每次请求都重新生成Token</li>
 *   <li>建议使用本地缓存或分布式缓存存储Token，减少生成开销</li>
 *   <li>可以实现Token预生成机制，在当前Token即将过期前提前生成新Token</li>
 *   <li>考虑使用异步方式刷新Token，避免阻塞请求处理线程</li>
 *   <li>对于高并发场景，可以使用令牌桶或漏桶算法限制Token生成频率</li>
 *   <li>日志记录应考虑采样策略，避免在高流量场景下产生过多日志</li>
 * </ul>
 *
 * <p><strong>使用示例：</strong></p>
 * <pre>
 * // 1. 创建接口实现类
 * @Service
 * public class CustomWebClientService implements GXWebClientService {
 *     // 使用线程安全的缓存存储Token
 *     private final LoadingCache<String, String> tokenCache;
 *     private final Logger logger = LoggerFactory.getLogger(CustomWebClientService.class);
 * <p>
 *     public CustomWebClientService() {
 *         // 创建带有过期时间的缓存
 *         this.tokenCache = Caffeine.newBuilder()
 *             .expireAfterWrite(GXTokenConstant.WEB_CLIENT_TOKEN_EXPIRE - 60, TimeUnit.SECONDS) // 提前60秒过期，便于刷新
 *             .build(this::generateFreshToken); // 当缓存未命中时自动生成新Token
 *     }
 *
 *     @Override
 *     public String generateHttpAuthToken() {
 *         // 从缓存中获取Token，如果不存在或已过期，会自动调用generateFreshToken生成新Token
 *         return tokenCache.get("webClientToken");
 *     }
 * <p>
 *     private String generateFreshToken(String key) {
 *         // 实际的Token生成逻辑
 *         String tokenSource = GXCommonUtils.getEnvironmentValue("maple.framework.web.client.token", String.class);
 *         if (CharSequenceUtil.isBlank(tokenSource)) {
 *             throw new GXBusinessException("请配置maple.framework.web.client.token");
 *         }
 *         return generateHttpAuthToken(tokenSource, GXTokenConstant.WEB_CLIENT_TOKEN_EXPIRE);
 *     }
 *
 *     @Override
 *     public void logRequestResponse(HttpMethod method, String url, HttpHeaders requestHeaders,
 *                                  Object requestBody, HttpStatusCode statusCode,
 *                                  HttpHeaders responseHeaders, Object responseBody, long durationMs) {
 *         // 使用MDC添加请求标识，便于日志关联
 *         String requestId = UUID.randomUUID().toString();
 *         MDC.put("requestId", requestId);
 *
 *         try {
 *             // 记录请求信息
 *             if (logger.isDebugEnabled()) {
 *                 logger.debug("HTTP请求: {} {} [请求ID: {}]", method, url, requestId);
 *                 logger.debug("请求头: {}", requestHeaders);
 *                 if (requestBody != null) {
 *                     logger.debug("请求体: {}", requestBody);
 *                 }
 *             }
 *
 *             // 记录响应信息
 *             if (logger.isDebugEnabled()) {
 *                 logger.debug("HTTP响应: 状态码={}, 耗时={}ms [请求ID: {}]",
 *                              statusCode.value(), durationMs, requestId);
 *                 logger.debug("响应头: {}", responseHeaders);
 *                 if (responseBody != null) {
 *                     logger.debug("响应体: {}", responseBody);
 *                 }
 *             }
 *         } finally {
 *             // 清理MDC上下文
 *             MDC.remove("requestId");
 *         }
 *     }
 * }
 * <p>
 * // 2. 在WebClient配置中使用
 * @Configuration
 * public class WebClientConfig {
 *     @Autowired
 *     private GXWebClientService webClientService;
 *
 *     @Bean
 *     public WebClient webClient() {
 *         return WebClient.builder()
 *             .defaultHeader(GXCommonConstant.WEB_CLIENT_AUTH_TOKEN, webClientService.generateHttpAuthToken())
 *             .filter((request, next) -> {
 *                 // 记录请求开始时间
 *                 long startTime = System.currentTimeMillis();
 *
 *                 // 获取请求信息
 *                 HttpMethod method = request.method();
 *                 String url = request.url().toString();
 *                 HttpHeaders requestHeaders = request.headers();
 *
 *                 // 执行请求并记录响应
 *                 return next.exchange(request)
 *                     .doOnSuccess(response -> {
 *                         long endTime = System.currentTimeMillis();
 *                         long duration = endTime - startTime;
 *
 *                         // 获取响应信息
 *                         HttpStatusCode statusCode = response.statusCode();
 *                         HttpHeaders responseHeaders = response.headers().asHttpHeaders();
 *
 *                         // 记录请求和响应信息
 *                         response.bodyToMono(String.class)
 *                             .defaultIfEmpty("<空响应体>")
 *                             .subscribe(responseBody ->
 *                                 webClientService.logRequestResponse(
 *                                     method, url, requestHeaders, null,
 *                                     statusCode, responseHeaders, responseBody, duration
 *                                 )
 *                             );
 *                     });
 *             })
 *             .build();
 *     }
 * }
 * <p>
 * // 3. 在HttpExchange接口中使用
 * @HttpExchange(url = "https://api.example.com")
 * public interface ApiClient {
 *     @GetExchange("/users")
 *     List<User> getUsers();
 * }
 * <p>
 * // 4. 配置HttpExchange客户端
 * @Configuration
 * public class HttpExchangeConfig {
 *     @Autowired
 *     private GXWebClientService webClientService;
 *
 *     @Bean
 *     public HttpServiceProxyFactory httpServiceProxyFactory(WebClient.Builder webClientBuilder) {
 *         WebClient webClient = webClientBuilder
 *             .defaultHeader(GXCommonConstant.WEB_CLIENT_AUTH_TOKEN, webClientService.generateHttpAuthToken())
 *             .build();
 * <p>
 *         return HttpServiceProxyFactory.builder(WebClientAdapter.forClient(webClient)).build();
 *     }
 *
 *     @Bean
 *     public ApiClient apiClient(HttpServiceProxyFactory factory) {
 *         return factory.createClient(ApiClient.class);
 *     }
 * }
 * </pre>
 *
 * @author britton
 * @since 1.0.0
 */
public interface GXWebClientService {
    /**
     * 生成HTTP客户端认证令牌
     * <p>
     * 该方法返回用于HTTP请求认证的Token值，通常会被设置在请求头中。
     * 默认实现从环境配置中获取基础令牌源，并使用默认过期时间生成Token。
     * </p>
     * <p>
     * 实现类可以覆盖此方法，提供更高效的Token生成和缓存策略，例如：
     * 1. 使用本地缓存存储Token，避免频繁生成
     * 2. 实现Token预生成机制，在当前Token即将过期前提前生成新Token
     * 3. 使用分布式缓存在多实例环境中共享Token
     * </p>
     *
     * @return 生成的认证令牌字符串
     * @throws GXBusinessException 当未配置基础令牌源时抛出
     */
    default String generateHttpAuthToken() {
        String tokenSource = GXCommonUtils.getEnvironmentValue("maple.framework.web.client.token", String.class);
        // 校验基础令牌配置有效性，为空时抛出业务异常
        if (CharSequenceUtil.isBlank(tokenSource)) {
            throw new GXBusinessException("请配置maple.framework.web.client.token");
        }
        return generateHttpAuthToken(tokenSource, GXTokenConstant.WEB_CLIENT_TOKEN_EXPIRE);
    }

    /**
     * 生成Web Client调用的HTTP请求认证Token
     * <p>
     * 该方法根据提供的源字符串和过期时间生成认证Token。
     * 默认实现使用GXAuthCodeUtils进行Token的编码生成。
     * </p>
     * <p>
     * 实现注意事项：
     * <ul>
     *   <li>Token生成应考虑性能影响，避免每次调用都重新生成</li>
     *   <li>对于有过期时间的Token，应实现自动刷新机制</li>
     *   <li>应处理Token获取失败的情况，提供合理的降级策略</li>
     *   <li>可以考虑使用缓存机制存储Token，提高性能</li>
     * </ul>
     * </p>
     *
     * @param source 源字符串，用于生成Token的基础数据
     * @param expiry Token的过期时间，单位为秒
     * @return HTTP认证Token字符串，不应返回null
     * @throws GXBusinessException 当Token生成失败时抛出
     */
    default String generateHttpAuthToken(String source, int expiry) {
        String authTokenSecret = getAuthTokenSecret();
        return GXAuthCodeUtils.authCodeEncode(source, authTokenSecret, expiry);
    }

    /**
     * 检查WebClient的Token是否有效
     * <p>
     * 该方法从当前请求上下文中获取WebClient认证Token，并验证其有效性。
     * 默认实现使用GXAuthCodeUtils进行Token的解码验证。
     * </p>
     * <p>
     * 实现类可以覆盖此方法，提供更复杂的Token验证逻辑，例如：
     * 1. 验证Token的签发时间和过期时间
     * 2. 检查Token是否在黑名单中（已被撤销）
     * 3. 验证Token的签名是否有效
     * 4. 检查Token中的用户信息是否与当前请求匹配
     * </p>
     *
     * @return true 有效 ; false 无效
     */
    default boolean checkTokenValidity() {
        String webClientToken = GXCurrentRequestContextUtils.getHeader(GXCommonConstant.WEB_CLIENT_AUTH_TOKEN);
        if (CharSequenceUtil.isBlank(webClientToken)) {
            return false; // Token为空，直接返回无效
        }

        try {
            String authTokenSecret = getAuthTokenSecret();
            String decodedToken = GXAuthCodeUtils.authCodeDecode(webClientToken, authTokenSecret);
            return !CharSequenceUtil.equalsIgnoreCase(decodedToken, "{}");
        } catch (Exception e) {
            // Token解码过程中发生异常，视为无效
            return false;
        }
    }

    /**
     * 获取Token的密钥
     * <p>
     * 该方法从环境配置中获取用于Token加密和解密的密钥。
     * 默认实现从环境变量maple.framework.web.client.secret中获取密钥。
     * </p>
     * <p>
     * 实现类可以覆盖此方法，提供更安全的密钥管理策略，例如：
     * 1. 从安全的密钥管理系统获取密钥
     * 2. 使用环境变量或配置中心存储加密后的密钥，运行时解密
     * 3. 实现密钥轮换机制，定期更新密钥
     * </p>
     *
     * @return 密钥字符串
     * @throws GXBusinessException 当未配置密钥时抛出
     */
    default String getAuthTokenSecret() {
        String tokenSecret = GXCommonUtils.getEnvironmentValue("maple.framework.web.client.secret", String.class);
        if (CharSequenceUtil.isBlank(tokenSecret)) {
            throw new GXBusinessException("请配置maple.framework.web.client.secret");
        }
        return tokenSecret;
    }

    /**
     * 记录HTTP请求和响应信息
     * <p>
     * 该方法用于记录HTTP请求和响应的详细信息，包括请求方法、URL、请求头、请求体、
     * 响应状态码、响应头、响应体和请求耗时等。这些信息对于调试和监控非常有用。
     * </p>
     * <p>
     * 实现类应根据实际需求决定日志记录的详细程度和格式，例如：
     * 1. 在开发环境记录完整的请求和响应信息，在生产环境只记录基本信息
     * 2. 对敏感信息（如密码、Token等）进行脱敏处理
     * 3. 使用结构化日志格式，便于日志分析和检索
     * 4. 添加请求ID，便于关联同一请求的多条日志
     * </p>
     * <p>
     * 性能和安全注意事项：
     * <ul>
     *   <li>避免在高流量场景下记录过多日志，可以采用采样策略</li>
     *   <li>考虑使用异步日志框架，避免日志记录阻塞请求处理线程</li>
     *   <li>大型响应体应考虑截断或摘要记录，避免日志过大</li>
     *   <li>敏感信息应进行脱敏处理，避免安全风险</li>
     *   <li>考虑使用MDC（Mapped Diagnostic Context）添加请求上下文信息</li>
     * </ul>
     * </p>
     *
     * @param method          HTTP请求方法
     * @param url             请求URL
     * @param requestHeaders  请求头信息
     * @param requestBody     请求体（可能为null）
     * @param statusCode      响应状态码
     * @param responseHeaders 响应头信息
     * @param responseBody    响应体（可能为null）
     * @param durationMs      请求耗时（毫秒）
     */
    default void logRequestResponse(HttpMethod method, String url, HttpHeaders requestHeaders,
                                    Object requestBody, HttpStatusCode statusCode,
                                    HttpHeaders responseHeaders, Object responseBody, long durationMs) {
        // 默认实现为空，由具体实现类根据需求实现
    }

    /**
     * 记录HTTP请求和响应信息（简化版）
     * <p>
     * 该方法是{@link #logRequestResponse}的简化版本，适用于只需记录基本请求和响应信息的场景。
     * 实现类可以根据需要选择实现此方法或完整版本。
     * </p>
     *
     * @param method     HTTP请求方法
     * @param url        请求URL
     * @param statusCode 响应状态码
     * @param durationMs 请求耗时（毫秒）
     * @param requestId  请求ID，用于关联同一请求的多条日志（可选）
     */
    default void logRequestResponse(HttpMethod method, String url, HttpStatusCode statusCode,
                                    long durationMs, String requestId) {
        // 默认实现为空，由具体实现类根据需求实现
    }

    /**
     * 记录HTTP请求异常信息
     * <p>
     * 该方法用于记录HTTP请求过程中发生的异常信息，包括请求方法、URL、异常信息等。
     * 这些信息对于排查问题非常有用。
     * </p>
     *
     * @param method    HTTP请求方法
     * @param url       请求URL
     * @param exception 异常信息
     * @param requestId 请求ID，用于关联同一请求的多条日志（可选）
     */
    default void logRequestException(HttpMethod method, String url, Throwable exception, String requestId) {
        // 默认实现为空，由具体实现类根据需求实现
    }

    /**
     * 获取需要脱敏的请求头字段集合
     * <p>
     * 该方法返回需要在日志记录中进行脱敏处理的请求头字段名称集合。
     * 默认实现包含常见的敏感字段，如Authorization、Cookie等。
     * 实现类可以覆盖此方法，根据实际需求添加或移除字段。
     * </p>
     *
     * @return 需要脱敏的请求头字段名称集合
     */
    default Map<String, String> getSensitiveHeaderFields() {
        // 返回默认的敏感字段映射，键为字段名，值为脱敏后的显示值
        return Map.of(
                "Authorization", "******",
                "Cookie", "******",
                "Web-Client-Auth-Token", "******",
                "X-Auth-Token", "******",
                "X-API-Key", "******"
        );
    }

    /**
     * 获取当前请求的平台信息
     * 从HTTP请求头中提取平台标识字段
     *
     * @return 平台标识字符串
     * 可能返回null或空字符串（当请求头未设置时）
     * 典型值如：WEB/APP/ADMIN等
     */
    default String getPlatform() {
        return GXCurrentRequestContextUtils.getHeader(GXTokenConstant.PLATFORM);
    }
}
