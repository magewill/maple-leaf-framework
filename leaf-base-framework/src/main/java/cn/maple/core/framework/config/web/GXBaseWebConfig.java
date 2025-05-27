package cn.maple.core.framework.config.web;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.api.dto.res.GXErrorApiResDto;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.filter.GXBaseRequestLoggingFilter;
import cn.maple.core.framework.service.GXWebClientService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Web基础配置类
 * <p>
 * 该配置类提供了Web应用的基础配置，包括请求日志记录过滤器和WebClient配置等组件。
 * 通过Spring的@Configuration注解，在应用启动时自动注册相关Bean。
 * 该配置类是线程安全的，所有Bean都是单例模式，在应用上下文中共享。
 * </p>
 *
 * <p><strong>核心功能：</strong></p>
 * <ul>
 *   <li>配置HTTP请求日志记录过滤器，便于调试和问题排查</li>
 *   <li>配置WebClient和HttpServiceProxyFactory，支持声明式HTTP客户端</li>
 *   <li>自动添加认证Token到HTTP请求头</li>
 *   <li>提供合理的默认配置，如超时设置、内存限制等</li>
 *   <li>支持分布式追踪，自动传递TraceId</li>
 *   <li>提供高性能的连接池管理，优化高并发场景</li>
 * </ul>
 *
 * <p><strong>线程安全性：</strong></p>
 * <ul>
 *   <li>所有Bean都是线程安全的，适合在多线程环境中使用</li>
 *   <li>WebClient实例是线程安全的，可以在多个线程间共享</li>
 *   <li>使用不可变对象和函数式编程风格，减少状态共享</li>
 *   <li>依赖的GXWebClientService实现应确保线程安全</li>
 *   <li>连接池配置支持高并发场景，避免连接资源竞争</li>
 * </ul>
 *
 * <p><strong>性能优化：</strong></p>
 * <ul>
 *   <li>WebClient使用非阻塞响应式编程模型，提高并发处理能力</li>
 *   <li>配置合理的内存限制，避免大请求导致内存溢出</li>
 *   <li>设置适当的超时时间，防止请求长时间挂起</li>
 *   <li>使用连接池管理HTTP连接，提高连接复用效率</li>
 *   <li>优化连接获取策略，减少连接建立的开销</li>
 *   <li>支持连接空闲超时，自动释放长时间不用的连接</li>
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
 *
 * // 2. 创建HttpExchange接口
 * @HttpExchange(url = "https://api.example.com")
 * public interface UserApiClient {
 *     @GetExchange("/users/{id}")
 *     User getUserById(@PathVariable("id") Long id);
 *
 *     @PostExchange("/users")
 *     User createUser(@RequestBody User user);
 * }
 *
 * // 3. 注入并使用HttpExchange客户端
 * @Service
 * public class UserService {
 *     private final UserApiClient userApiClient;
 *
 *     public UserService(UserApiClient userApiClient) {
 *         this.userApiClient = userApiClient;
 *     }
 *
 *     public User getUserById(Long id) {
 *         return userApiClient.getUserById(id);
 *     }
 * }
 * </pre>
 *
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
@Configuration
public class GXBaseWebConfig {
    /**
     * 日志对象
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GXBaseWebConfig.class);

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
     * 创建请求日志记录过滤器Bean
     * <p>
     * 该方法创建并配置GXBaseRequestLoggingFilter实例，用于记录HTTP请求的详细信息。
     * 过滤器会记录请求的URL、请求头、请求参数、请求体等信息，便于调试和问题排查。
     * </p>
     * <p>
     * 线程安全说明：
     * - 该过滤器是线程安全的，每个请求都有独立的处理上下文
     * - 过滤器在应用启动时创建单例，在多线程环境中共享使用
     * </p>
     *
     * @return 配置好的GXBaseRequestLoggingFilter实例
     */
    @Bean
    public GXBaseRequestLoggingFilter requestLoggingFilter() {
        return new GXBaseRequestLoggingFilter();
    }

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
     * @return 配置好的HttpServiceProxyFactory实例，用于创建声明式HTTP客户端
     */
    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory(WebClient webClient) {
        WebClientAdapter adapter = WebClientAdapter.create(webClient);
        return HttpServiceProxyFactory.builder()
                .exchangeAdapter(adapter)
                .build();
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
     * @return 配置好的WebClient实例
     * @deprecated 推荐使用httpServiceProxyFactory创建声明式HTTP客户端，
     * 该方法保留用于向后兼容，将在未来版本中移除
     */
    @Bean
    public WebClient webClient() {
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
                    LOGGER.debug("HTTP响应: 状态码={}, 耗时={}ms", statusCode.value(), duration);
                } catch (NumberFormatException e) {
                    LOGGER.debug("HTTP响应: 状态码={}, 解析X-Request-Start-Time失败", statusCode.value());
                }
            } else {
                LOGGER.debug("HTTP响应: 状态码={}", statusCode.value());
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
                        .doOnNext(body -> LOGGER.trace("响应体: {}", body))
                        .map(body -> clientResponse.mutate().body(body).build());
            }

            return Mono.just(clientResponse);
        });

        // 创建处理错误过滤器
        ExchangeFilterFunction errorResponseHandleFilter = ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            HttpStatusCode httpStatusCode = clientResponse.statusCode();

            // 记录错误响应
            if (httpStatusCode.isError()) {
                LOGGER.warn("HTTP请求失败: 状态码={}, 描述={}",
                        httpStatusCode.value(),
                        httpStatusCode);
            }

            // 处理服务端错误（4xx）
            if (httpStatusCode.is4xxClientError()) {
                GXErrorApiResDto errorApiResDto = new GXErrorApiResDto();
                errorApiResDto.setMessage("未知服务端错误");
                errorApiResDto.setCode(httpStatusCode.value());
                return clientResponse.bodyToMono(GXErrorApiResDto.class)
                        .defaultIfEmpty(errorApiResDto)
                        .flatMap(errorBody -> Mono.error(new GXBusinessException(
                                String.format("服务端请求错误(4xx): %s", errorBody.getMessage()),
                                httpStatusCode.value())));
            }
            // 处理服务器错误（5xx）
            else if (httpStatusCode.is5xxServerError()) {
                GXErrorApiResDto errorApiResDto = new GXErrorApiResDto();
                errorApiResDto.setMessage("未知服务端错误");
                errorApiResDto.setCode(httpStatusCode.value());
                return clientResponse.bodyToMono(GXErrorApiResDto.class)
                        .defaultIfEmpty(errorApiResDto)
                        .flatMap(body -> Mono.error(new GXBusinessException(
                                String.format("服务器处理错误(5xx): %s", JSONUtil.toJsonStr(body)),
                                httpStatusCode.value())));
            }

            return Mono.just(clientResponse);
        });

        // 构建WebClient，配置默认请求头、超时设置等
        return WebClient.builder()
                .exchangeStrategies(strategies)
                .filter(requestFilter)
                .filter(responseFilter)
                .filter(errorResponseHandleFilter)
                // 设置连接超时和响应超时
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create(connectionProvider)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_SECONDS * 1000)
                        .compress(true)
                        .doOnConnected(conn -> conn
                                // 读超时：在指定时间内没有收到任何数据
                                .addHandlerLast(new ReadTimeoutHandler(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                                // 写超时：在指定时间内没有完成数据发送
                                .addHandlerLast(new WriteTimeoutHandler(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)))
                        .responseTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))))
                .build();
    }
}