package cn.maple.core.framework.config.web;

import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.api.dto.res.GXErrorApiResDto;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.filter.GXBaseRequestLoggingFilter;
import cn.maple.core.framework.service.GXWebClientService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

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
 * </ul>
 *
 * <p><strong>线程安全性：</strong></p>
 * <ul>
 *   <li>所有Bean都是线程安全的，适合在多线程环境中使用</li>
 *   <li>WebClient实例是线程安全的，可以在多个线程间共享</li>
 *   <li>使用不可变对象和函数式编程风格，减少状态共享</li>
 *   <li>依赖的GXWebClientService实现应确保线程安全</li>
 * </ul>
 *
 * <p><strong>性能优化：</strong></p>
 * <ul>
 *   <li>WebClient使用非阻塞响应式编程模型，提高并发处理能力</li>
 *   <li>配置合理的内存限制，避免大请求导致内存溢出</li>
 *   <li>设置适当的超时时间，防止请求长时间挂起</li>
 *   <li>使用连接池管理HTTP连接，提高连接复用效率</li>
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
     * </p>
     * <p>
     * 线程安全说明：
     * - WebClient实例是线程安全的，可以在多个线程间共享使用
     * - 使用响应式编程模型，支持高并发非阻塞操作
     * - 日志记录使用响应式流处理，不会阻塞请求线程
     * </p>
     * <p>
     * 性能优化：
     * - 使用响应式非阻塞模型，提高并发处理能力
     * - 配置合理的内存限制，避免大响应导致内存溢出
     * - 设置适当的超时时间，防止请求长时间挂起
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

        // 创建请求日志过滤器，记录请求信息和请求体
        ExchangeFilterFunction requestLoggingFilter = ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            // 记录请求基本信息
            LOGGER.debug("HTTP请求: {} {}", clientRequest.method(), clientRequest.url());

            // 记录请求头信息（仅在TRACE级别）
            if (LOGGER.isTraceEnabled()) {
                clientRequest.headers().forEach((name, values) ->
                        LOGGER.trace("请求头: {}={}", name, String.join(", ", values)));
            }

            // 记录请求开始时间，用于计算请求耗时
            List<String> requestStartTime = clientRequest.headers().get("requestStartTime");
            assert requestStartTime != null;
            LOGGER.trace("请求开始时间: {}", requestStartTime.getFirst());
            return Mono.just(clientRequest);
        });

        // 创建响应日志过滤器，记录响应信息和响应体
        ExchangeFilterFunction responseLoggingFilter = ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            long endTime = System.currentTimeMillis();
            HttpStatusCode statusCode = clientResponse.statusCode();

            // 获取请求开始时间，计算请求耗时
            List<String> requestStartTimeHeaders = clientResponse.headers().header("requestStartTime");
            if (!requestStartTimeHeaders.isEmpty()) {
                try {
                    long startTime = Long.parseLong(requestStartTimeHeaders.getFirst());
                    long duration = endTime - startTime;
                    LOGGER.debug("HTTP响应: 状态码={}, 耗时={}ms", statusCode.value(), duration);
                } catch (NumberFormatException e) {
                    LOGGER.debug("HTTP响应: 状态码={}", statusCode.value());
                }
            } else {
                LOGGER.debug("HTTP响应: 状态码={}", statusCode.value());
            }

            // 记录响应头信息（仅在TRACE级别）
            if (LOGGER.isTraceEnabled()) {
                clientResponse.headers().asHttpHeaders().forEach((name, values) ->
                        LOGGER.trace("响应头: {}={}", name, String.join(", ", values)));
            }

            // 对于成功响应，可以选择记录响应体（需要谨慎使用，可能影响性能）
            if (LOGGER.isTraceEnabled() && statusCode.is2xxSuccessful()) {
                return clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("<空响应体>")
                        .doOnNext(body -> LOGGER.trace("响应体: {}", body))
                        .map(body -> clientResponse.mutate().body(body).build());
            }

            return Mono.just(clientResponse);
        });

        // 创建处理错误过滤器
        ExchangeFilterFunction errorHandlingFilter = ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            HttpStatusCode httpStatusCode = clientResponse.statusCode();
            if (httpStatusCode.isError()) {
                LOGGER.warn("HTTP请求失败: {} {}",
                        httpStatusCode.value(),
                        httpStatusCode);
            }
            if (httpStatusCode.is4xxClientError()) {
                return clientResponse.bodyToMono(GXErrorApiResDto.class)
                        .flatMap(errorBody -> Mono.error(new GXBusinessException("Server Error: " + errorBody.getMessage(), httpStatusCode.value())));
            } else if (httpStatusCode.is5xxServerError()) {
                return clientResponse.bodyToMono(GXErrorApiResDto.class)
                        .flatMap(body -> Mono.error(new GXBusinessException("Server Error: " + JSONUtil.toJsonStr(body), httpStatusCode.value())));
            }
            return Mono.just(clientResponse);
        });

        // 构建WebClient，配置默认请求头、超时设置等
        return WebClient.builder()
                .exchangeStrategies(strategies)
                .filter(requestLoggingFilter)
                .filter(responseLoggingFilter)
                .filter(errorHandlingFilter)
                .defaultHeaders(headers -> {
                    GXWebClientService webClientService = GXSpringContextUtils.getBean(GXWebClientService.class);
                    if (Objects.nonNull(webClientService)) {
                        String token = webClientService.generateHttpAuthToken();
                        if (Objects.nonNull(token) && !token.isEmpty()) {
                            headers.set(GXCommonConstant.WEB_CLIENT_AUTH_TOKEN, token);
                            headers.set("requestStartTime", String.valueOf(System.currentTimeMillis()));
                            LOGGER.debug("已添加WebClient认证Token到请求头");
                        }
                    } else {
                        LOGGER.warn("未找到GXWebClientService实现，HTTP请求将不包含认证Token");
                    }
                })
                // 设置连接超时和响应超时
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_SECONDS * 1000)
                        .responseTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))))
                .build();
    }
}