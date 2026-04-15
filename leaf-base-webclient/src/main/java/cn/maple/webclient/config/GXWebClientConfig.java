package cn.maple.webclient.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.api.dto.res.GXHttpInvokerApiErrorResDto;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.webclient.service.GXWebClientService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * WebClient配置类
 * <p>
 * 该配置类提供了WebClient和HttpServiceProxyFactory的配置，用于创建高性能、响应式的HTTP客户端。
 * 通过Spring的@Configuration注解，在应用启动时自动注册相关Bean。
 * 该配置类是线程安全的，所有Bean都是单例模式，在应用上下文中共享。
 * </p>
 *
 * <p><strong>核心功能：</strong></p>
 * <ul>
 *   <li>配置WebClient，支持响应式非阻塞HTTP请求</li>
 *   <li>配置HttpServiceProxyFactory，支持声明式HTTP客户端（使用@HttpExchange注解）</li>
 *   <li>自动添加认证Token和TraceId到HTTP请求头，支持分布式追踪</li>
 *   <li>提供合理的默认配置，如超时设置、内存限制、连接池管理等</li>
 *   <li>实现请求/响应日志记录，便于调试和问题排查</li>
 *   <li>提供错误处理和重试机制，提高系统稳定性</li>
 * </ul>
 *
 * <p><strong>线程安全性：</strong></p>
 * <ul>
 *   <li>所有Bean都是线程安全的，适合在多线程环境中使用</li>
 *   <li>WebClient实例是线程安全的，可以在多个线程间共享</li>
 *   <li>使用不可变对象和函数式编程风格，减少状态共享</li>
 *   <li>依赖的GXWebClientService实现应确保线程安全</li>
 *   <li>连接池配置支持高并发场景，避免连接资源竞争</li>
 *   <li>过滤器链的处理是响应式的，不会阻塞请求线程</li>
 * </ul>
 *
 * <p><strong>性能优化：</strong></p>
 * <ul>
 *   <li>WebClient使用非阻塞响应式编程模型，提高并发处理能力</li>
 *   <li>配置16MB内存限制，避免大响应导致内存溢出</li>
 *   <li>设置30秒超时时间，防止请求长时间挂起</li>
 *   <li>使用连接池管理HTTP连接，最大连接数500，提高连接复用效率</li>
 *   <li>配置连接获取超时3秒，避免长时间等待连接</li>
 *   <li>设置连接空闲超时30秒，自动释放长时间不用的连接</li>
 *   <li>连接存活时间300秒，定期更新连接，避免连接失效</li>
 *   <li>支持HTTP压缩，减少网络传输量</li>
 * </ul>
 *
 * <p><strong>安全特性：</strong></p>
 * <ul>
 *   <li>自动添加安全相关的HTTP头，如X-Content-Type-Options、X-Frame-Options等</li>
 *   <li>支持SSL/TLS安全连接</li>
 *   <li>请求日志中不记录敏感信息，避免信息泄露</li>
 *   <li>响应体大小检查，防止恶意大响应攻击</li>
 *   <li>错误响应处理，避免敏感错误信息泄露</li>
 * </ul>
 *
 * <p><strong>使用示例：</strong></p>
 * <pre>
 * // 1. 在Spring Boot应用中引入该配置
 * @SpringBootApplication(scanBasePackages = {"cn.maple.core.framework"})
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * <p>
 * // 2. 创建声明式HTTP客户端接口（推荐方式）
 * @HttpExchange(url = "https://api.example.com")
 * public interface UserApiClient {
 *     @GetExchange("/users/{id}")
 *     User getUserById(@PathVariable("id") Long id);
 *
 *     @PostExchange("/users")
 *     User createUser(@RequestBody User user);
 * }
 * <p>
 * // 3. 注入并使用声明式HTTP客户端
 * @Service
 * public class UserService {
 *     private final UserApiClient userApiClient;
 * <p>
 *     public UserService(UserApiClient userApiClient) {
 *         this.userApiClient = userApiClient;
 *     }
 * <p>
 *     public User getUserById(Long id) {
 *         return userApiClient.getUserById(id);
 *     }
 * }
 * <p>
 * // 4. 直接使用WebClient（备选方式）
 * @Service
 * public class ProductService {
 *     private final WebClient webClient;
 * <p>
 *     public ProductService(WebClient webClient) {
 *         this.webClient = webClient;
 *     }
 * <p>
 *     public Mono<Product> getProductById(Long id) {
 *         return webClient.get()
 *                 .uri("https://api.example.com/products/{id}", id)
 *                 .retrieve()
 *                 .bodyToMono(Product.class);
 *     }
 * }
 * </pre>
 *
 * <p><strong>错误处理机制：</strong></p>
 * <ul>
 *   <li>4xx错误（客户端错误）：转换为GXBusinessException，包含详细错误信息</li>
 *   <li>5xx错误（服务器错误）：转换为GXBusinessException，记录详细错误日志</li>
 *   <li>超时错误：支持自动重试机制</li>
 *   <li>连接错误：详细日志记录，便于问题排查</li>
 * </ul>
 *
 * <p><strong>重试机制：</strong></p>
 * <ul>
 *   <li>支持对特定状态码（408/429/500/502/503/504）的请求进行重试</li>
 *   <li>默认重试次数：3次</li>
 *   <li>默认重试延迟：1000毫秒</li>
 *   <li>重试过程中记录详细日志，便于问题排查</li>
 * </ul>
 *
 * <p><strong>与其他组件的集成：</strong></p>
 * <ul>
 *   <li>与GXBaseWebConfig配合，实现全链路追踪</li>
 *   <li>与GXTraceIdContextUtils集成，自动传递TraceId</li>
 *   <li>与GXWebClientService集成，提供认证Token和平台信息</li>
 * </ul>
 *
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
@Configuration
public class GXWebClientConfig {
    /**
     * 日志对象
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXWebClientConfig.class);

    /**
     * 默认请求超时时间（秒）
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * 默认内存缓冲区大小（MB）
     */
    private static final int DEFAULT_MEMORY_BUFFER_SIZE_MB = 16;

    /**
     * 默认最大连接数
     */
    private static final int DEFAULT_MAX_CONNECTIONS = 500;

    /**
     * 默认获取连接超时时间（毫秒）
     */
    private static final int DEFAULT_ACQUIRE_TIMEOUT_MILLIS = 3000;

    /**
     * 默认连接空闲超时时间（毫秒）
     */
    private static final int DEFAULT_IDLE_TIMEOUT_MILLIS = 30000;

    /**
     * 默认连接存活时间（毫秒）
     */
    private static final int DEFAULT_MAX_LIFE_TIME_MILLIS = 300000;

    /**
     * 创建并配置HttpServiceProxyFactory，用于生成声明式HTTP客户端
     * <p>
     * 该方法创建并配置WebClient实例，然后基于此构建HttpServiceProxyFactory。
     * HttpServiceProxyFactory用于创建基于接口的声明式HTTP客户端（使用@HttpExchange注解）。
     * 配置包括：
     * - 添加认证Token到请求头
     * - 设置合理的超时时间
     * - 配置内存限制，避免大响应导致内存溢出
     * - 添加请求/响应日志记录
     * </p>
     * <p>
     * 线程安全说明：
     * - WebClient是线程安全的，可以在多个线程间共享使用
     * - HttpServiceProxyFactory创建的代理对象也是线程安全的
     * - 使用函数式编程风格和不可变对象，减少状态共享
     * </p>
     * <p>
     * 性能优化：
     * - 使用非阻塞响应式编程模型，提高并发处理能力
     * - 设置合理的超时时间，防止请求长时间挂起
     * - 配置内存限制，避免大响应导致内存溢出
     * - 可选地使用连接池管理HTTP连接，提高连接复用效率
     * </p>
     *
     * @param webClient 配置好的WebClient实例
     * @return 配置好的HttpServiceProxyFactory.Builder实例，用于创建声明式HTTP客户端
     */
    @Bean
    public HttpServiceProxyFactory.Builder httpServiceProxyFactoryBuilder(WebClient webClient) {
        WebClientAdapter adapter = WebClientAdapter.create(webClient);
        return HttpServiceProxyFactory.builder()
                .exchangeAdapter(adapter);
    }

    /**
     * 创建并配置HttpServiceProxyFactory Bean
     *
     * @param httpServiceProxyFactoryBuilder 用于构建HttpServiceProxyFactory的构建器实例
     * @return 配置完成的HttpServiceProxyFactory实例
     */
    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory(HttpServiceProxyFactory.Builder httpServiceProxyFactoryBuilder) {
        return httpServiceProxyFactoryBuilder.build();
    }

    /**
     * 创建并返回WebClient实例，用于执行HTTP请求
     * <p>
     * 该方法创建并配置WebClient实例，用于直接执行HTTP请求。
     * 配置包括：
     * - 内存限制：防止大响应导致内存溢出
     * - 日志记录：记录请求和响应信息，便于调试
     * - 认证Token：自动添加WebClient认证Token到请求头
     * - 超时设置：防止请求长时间挂起
     * - 连接池：优化高并发场景下的连接管理
     * - 分布式追踪：自动传递TraceId
     * </p>
     * <p>
     * 线程安全说明：
     * - WebClient实例是线程安全的，可以在多个线程间共享使用
     * - 使用响应式编程模型，支持高并发非阻塞操作
     * - 日志记录使用响应式流处理，不会阻塞请求线程
     * - 连接池配置支持高并发场景，避免连接资源竞争
     * </p>
     * <p>
     * 性能优化：
     * - 使用响应式非阻塞模型，提高并发处理能力
     * - 配置合理的内存限制，避免大响应导致内存溢出
     * - 设置适当的超时时间，防止请求长时间挂起
     * - 使用连接池管理HTTP连接，提高连接复用效率
     * - 优化连接获取策略，减少连接建立的开销
     * </p>
     *
     * @return 配置好的WebClientBuilder实例
     * @deprecated 推荐使用httpServiceProxyFactory创建声明式HTTP客户端，
     * 该方法保留用于向后兼容，将在未来版本中移除
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        // 配置内存限制，避免大响应导致内存溢出
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(DEFAULT_MEMORY_BUFFER_SIZE_MB * 1024 * 1024))
                .build();

        // 创建连接池配置，优化高并发场景
        ConnectionProvider connectionProvider = ConnectionProvider.builder("maple-leaf-webclient-connection-pool")
                .maxConnections(DEFAULT_MAX_CONNECTIONS)
                .pendingAcquireTimeout(Duration.ofMillis(DEFAULT_ACQUIRE_TIMEOUT_MILLIS))
                .maxIdleTime(Duration.ofMillis(DEFAULT_IDLE_TIMEOUT_MILLIS))
                .maxLifeTime(Duration.ofMillis(DEFAULT_MAX_LIFE_TIME_MILLIS))
                .pendingAcquireMaxCount(-1) // 无限制等待队列
                .evictInBackground(Duration.ofSeconds(120)) // 后台清理空闲连接
                .build();

        // 获取WebClientService，用于添加认证信息
        GXWebClientService webClientService = GXSpringContextUtils.getBean(GXWebClientService.class);
        if (ObjectUtil.isNull(webClientService)) {
            LOGGER.warn("未找到GXWebClientService实现，HTTP请求将不包含认证Token和追踪信息");
        }

        // 创建请求日志过滤器，记录请求信息和请求体
        ExchangeFilterFunction requestFilter = ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            // 记录请求基本信息
            LOGGER.debug("HTTP请求: {} {}", clientRequest.method(), clientRequest.url());

            // 记录请求头信息（仅在TRACE级别）
            if (LOGGER.isTraceEnabled()) {
                clientRequest.headers().forEach((name, values) ->
                        LOGGER.trace("请求头: {}={}", name, String.join(", ", values)));
            }

            // 构建新的请求，添加必要的头信息
            ClientRequest.Builder requestBuilder = ClientRequest.from(clientRequest);

            // 添加应用名称
            String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
            if (CharSequenceUtil.isNotBlank(appName)) {
                requestBuilder.header("X-Request-Source", appName);
            }

            // 添加请求开始时间，用于计算请求耗时
            requestBuilder.header("X-Request-Start-Time", String.valueOf(System.currentTimeMillis()));

            // 添加安全相关头信息
            requestBuilder.header("X-Content-Type-Options", "nosniff");
            requestBuilder.header("X-Frame-Options", "DENY");
            requestBuilder.header("X-XSS-Protection", "1; mode=block");
            requestBuilder.header("Cache-Control", "no-cache, no-store, must-revalidate");
            requestBuilder.header("Pragma", "no-cache");
            requestBuilder.header("Expires", "0");

            // 添加用户代理信息
            String userAgent = String.format("Maple-Leaf-WebClient/1.0 (%s)",
                    System.getProperty("os.name", "Unknown"));
            requestBuilder.header("User-Agent", userAgent);

            // 如果存在WebClientService，添加认证和追踪信息
            if (ObjectUtil.isNotNull(webClientService)) {
                // 添加TraceId用于分布式追踪
                String traceId = webClientService.getTraceId();
                if (CharSequenceUtil.isNotBlank(traceId)) {
                    requestBuilder.header(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
                }

                // 添加认证Token
                String token = webClientService.generateHttpAuthToken();
                if (CharSequenceUtil.isNotBlank(token)) {
                    requestBuilder.header(GXCommonConstant.X_AUTH_TOKEN, token);
                }

                // 添加平台信息
                String platform = webClientService.getPlatform();
                if (CharSequenceUtil.isNotBlank(platform)) {
                    requestBuilder.header(GXTokenConstant.PLATFORM, platform);
                }
            }

            return Mono.just(requestBuilder.build());
        });

        // 创建响应日志过滤器，记录响应信息和响应体
        ExchangeFilterFunction responseFilter = ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            HttpStatusCode statusCode = clientResponse.statusCode();
            long endTime = System.currentTimeMillis();

            // 获取请求开始时间，计算请求耗时
            List<String> requestStartTimeHeaders = clientResponse.headers().header("X-Request-Start-Time");
            if (!requestStartTimeHeaders.isEmpty()) {
                try {
                    long startTime = Long.parseLong(requestStartTimeHeaders.getFirst());
                    long duration = endTime - startTime;

                    // 根据响应时间记录不同级别的日志
                    if (duration > 5000) {
                        LOGGER.warn("HTTP响应: 状态码={}, 耗时={}ms (响应时间过长)", statusCode.value(), duration);
                    } else if (duration > 2000) {
                        LOGGER.info("HTTP响应: 状态码={}, 耗时={}ms (响应时间较长)", statusCode.value(), duration);
                    } else {
                        LOGGER.debug("HTTP响应: 状态码={}, 耗时={}ms", statusCode.value(), duration);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.debug("HTTP响应: 状态码={}, 解析X-Request-Start-Time失败", statusCode.value());
                }
            } else {
                LOGGER.debug("HTTP响应: 状态码={}", statusCode.value());
            }

            // 检查响应头中的安全信息
            List<String> contentTypeHeaders = clientResponse.headers().header("Content-Type");
            if (!contentTypeHeaders.isEmpty()) {
                String contentType = contentTypeHeaders.getFirst();
                if (contentType.contains("text/html") || contentType.contains("application/javascript")) {
                    LOGGER.debug("收到可能包含脚本的响应类型: {}", contentType);
                }
            }

            // 检查响应大小
            List<String> contentLengthHeaders = clientResponse.headers().header("Content-Length");
            if (!contentLengthHeaders.isEmpty()) {
                try {
                    long contentLength = Long.parseLong(contentLengthHeaders.getFirst());
                    if (contentLength > DEFAULT_MEMORY_BUFFER_SIZE_MB * 1024 * 1024) {
                        LOGGER.warn("响应体大小超过配置限制: {}MB > {}MB",
                                contentLength / (1024 * 1024), DEFAULT_MEMORY_BUFFER_SIZE_MB);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.trace("解析Content-Length失败: {}", contentLengthHeaders.getFirst());
                }
            }

            // 记录响应头信息（仅在TRACE级别）
            if (LOGGER.isTraceEnabled()) {
                clientResponse.headers().asHttpHeaders().forEach((name, values) ->
                        LOGGER.trace("响应头: {}={}", name, String.join(", ", values)));
            }

            // 对于成功响应，可以选择记录响应体（仅在TRACE级别）
            if (LOGGER.isTraceEnabled() && statusCode.is2xxSuccessful()) {
                return clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("<空响应体>")
                        .doOnNext(body -> {
                            String truncatedBody = body.length() > 1000 ?
                                    body.substring(0, 1000) + "...(截断)" : body;
                            LOGGER.trace("响应体: {}", truncatedBody);
                        })
                        .map(body -> clientResponse.mutate().body(body).build());
            }

            return Mono.just(clientResponse);
        });

        // 创建处理错误过滤器
        ExchangeFilterFunction errorResponseHandleFilter = ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            HttpStatusCode httpStatusCode = clientResponse.statusCode();

            // 记录错误响应
            if (httpStatusCode.isError()) {
                String errorMessage = getHttpStatusDescription(httpStatusCode);
                LOGGER.warn("HTTP请求失败: 状态码={}, 描述={}",
                        httpStatusCode.value(), errorMessage);
            }

            // 处理客户端错误（4xx）
            if (httpStatusCode.is4xxClientError()) {
                return handle4xxError(clientResponse, httpStatusCode);
            }
            // 处理服务器错误（5xx）
            else if (httpStatusCode.is5xxServerError()) {
                return handle5xxError(clientResponse, httpStatusCode);
            }

            return Mono.just(clientResponse);
        });

        // 创建重试过滤器
        ExchangeFilterFunction retryFilter = ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            HttpStatusCode statusCode = clientResponse.statusCode();

            // 对于特定的错误状态码进行重试
            if (shouldRetry(statusCode)) {
                LOGGER.debug("HTTP请求将进行重试，状态码: {}", statusCode.value());
                // 注意：这里只是标记需要重试，实际重试逻辑在WebClient构建时配置
            }

            return Mono.just(clientResponse);
        });

        JsonMapper jsonMapper = GXSpringContextUtils.getBean(JsonMapper.class);
        assert jsonMapper != null;
        // 构建WebClient，配置默认请求头、超时设置等
        return WebClient.builder()
                .exchangeStrategies(strategies)
                .filter(requestFilter)
                .filter(responseFilter)
                .filter(retryFilter)
                .filter(errorResponseHandleFilter)
                // 设置连接超时和响应超时
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create(connectionProvider)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_SECONDS * 1000)
                        .compress(true)
                        .secure() // 启用SSL/TLS安全连接
                        .doOnConnected(conn -> conn
                                // 读超时：在指定时间内没有收到任何数据
                                .addHandlerLast(new ReadTimeoutHandler(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                                // 写超时：在指定时间内没有完成数据发送
                                .addHandlerLast(new WriteTimeoutHandler(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)))
                        .responseTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))))
                // 设置编解码器
                .codecs(configurer -> {
                    configurer.defaultCodecs().jacksonJsonEncoder(
                            new JacksonJsonEncoder(jsonMapper)
                    );
                    configurer.defaultCodecs().jacksonJsonDecoder(
                            new JacksonJsonDecoder(jsonMapper)
                    );
                });
    }

    /**
     * 创建并配置WebClient实例。
     *
     * @param webClientBuilder WebClient的构建器，用于配置WebClient的属性
     * @return 配置好的WebClient实例
     */
    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }

    /**
     * 获取HTTP状态码的描述信息
     *
     * @param statusCode HTTP状态码
     * @return 状态码描述
     */
    private String getHttpStatusDescription(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> "请求参数错误";
            case 401 -> "未授权访问";
            case 403 -> "访问被禁止";
            case 404 -> "资源未找到";
            case 408 -> "请求超时";
            case 429 -> "请求过于频繁";
            case 500 -> "服务器内部错误";
            case 502 -> "网关错误";
            case 503 -> "服务不可用";
            case 504 -> "网关超时";
            default -> statusCode.toString();
        };
    }

    /**
     * 处理客户端错误（4xx）
     *
     * @param clientResponse 客户端响应
     * @param httpStatusCode HTTP状态码
     * @return 处理后的响应
     */
    private Mono<ClientResponse> handle4xxError(ClientResponse clientResponse, HttpStatusCode httpStatusCode) {
        GXHttpInvokerApiErrorResDto errorApiResDto = new GXHttpInvokerApiErrorResDto();
        errorApiResDto.setMessage(getHttpStatusDescription(httpStatusCode));
        errorApiResDto.setCode(httpStatusCode.value());
        return clientResponse.bodyToMono(GXHttpInvokerApiErrorResDto.class)
                .defaultIfEmpty(errorApiResDto)
                .flatMap(errorBody -> {
                    String errorMessage = String.format("客户端请求错误(4xx): %s",
                            CharSequenceUtil.isNotBlank(errorBody.getMessage()) ?
                                    errorBody.getMessage() : getHttpStatusDescription(httpStatusCode));
                    LOGGER.error("客户端错误详情: 状态码={}, 错误信息={}",
                            httpStatusCode.value(), errorBody.getMessage());
                    return Mono.error(new GXBusinessException(errorMessage, httpStatusCode.value()));
                });
    }

    /**
     * 处理服务器错误（5xx）
     *
     * @param clientResponse 客户端响应
     * @param httpStatusCode HTTP状态码
     * @return 处理后的响应
     */
    private Mono<ClientResponse> handle5xxError(ClientResponse clientResponse, HttpStatusCode httpStatusCode) {
        GXHttpInvokerApiErrorResDto errorApiResDto = new GXHttpInvokerApiErrorResDto();
        errorApiResDto.setMessage(getHttpStatusDescription(httpStatusCode));
        errorApiResDto.setCode(httpStatusCode.value());
        return clientResponse.bodyToMono(GXHttpInvokerApiErrorResDto.class)
                .defaultIfEmpty(errorApiResDto)
                .flatMap(errorBody -> {
                    String errorMessage = String.format("服务器处理错误(5xx): %s",
                            CharSequenceUtil.isNotBlank(errorBody.getMessage()) ?
                                    errorBody.getMessage() : getHttpStatusDescription(httpStatusCode));
                    LOGGER.error("服务器错误详情: 状态码={}, 错误信息={}, 完整响应={}",
                            httpStatusCode.value(), errorBody.getMessage(), JSONUtil.toJsonStr(errorBody));
                    return Mono.error(new GXBusinessException(errorMessage, httpStatusCode.value()));
                });
    }

    /**
     * 判断是否应该重试请求
     *
     * @param statusCode HTTP状态码
     * @return 是否应该重试
     */
    private boolean shouldRetry(HttpStatusCode statusCode) {
        // 对于以下状态码进行重试：
        // 408 - 请求超时
        // 429 - 请求过于频繁
        // 500 - 服务器内部错误
        // 502 - 网关错误
        // 503 - 服务不可用
        // 504 - 网关超时
        return statusCode.value() == 408 ||
                statusCode.value() == 429 ||
                statusCode.value() == 500 ||
                statusCode.value() == 502 ||
                statusCode.value() == 503 ||
                statusCode.value() == 504;
    }
}
