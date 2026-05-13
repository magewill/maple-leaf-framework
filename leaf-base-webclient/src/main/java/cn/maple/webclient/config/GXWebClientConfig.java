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
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
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
import reactor.util.retry.Retry;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spring configuration for the framework WebClient and HttpServiceProxyFactory.
 * <p>The configured client adds framework headers, timeouts, bounded connection
 * pooling, retry handling and 4xx/5xx conversion to {@link GXBusinessException}.</p>
 *
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class GXWebClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXWebClientConfig.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private static final int DEFAULT_MEMORY_BUFFER_SIZE_MB = 16;

    private static final int DEFAULT_MAX_CONNECTIONS = 500;

    private static final int DEFAULT_ACQUIRE_TIMEOUT_MILLIS = 3000;

    private static final int DEFAULT_PENDING_ACQUIRE_MAX_COUNT = 1000;

    private static final int DEFAULT_IDLE_TIMEOUT_MILLIS = 30000;

    private static final int DEFAULT_MAX_LIFE_TIME_MILLIS = 300000;

    private static final int DEFAULT_RETRY_MAX_ATTEMPTS = 3;

    private static final int DEFAULT_RETRY_DELAY_MILLIS = 1000;

    private static final String TIMEOUT_SECONDS_KEY = "maple.framework.web.client.timeout-seconds";

    private static final String MEMORY_BUFFER_SIZE_MB_KEY = "maple.framework.web.client.memory-buffer-size-mb";

    private static final String MAX_CONNECTIONS_KEY = "maple.framework.web.client.max-connections";

    private static final String ACQUIRE_TIMEOUT_MILLIS_KEY = "maple.framework.web.client.acquire-timeout-millis";

    private static final String PENDING_ACQUIRE_MAX_COUNT_KEY = "maple.framework.web.client.pending-acquire-max-count";

    private static final String IDLE_TIMEOUT_MILLIS_KEY = "maple.framework.web.client.idle-timeout-millis";

    private static final String MAX_LIFE_TIME_MILLIS_KEY = "maple.framework.web.client.max-life-time-millis";

    private static final String EVICT_IN_BACKGROUND_SECONDS_KEY = "maple.framework.web.client.evict-in-background-seconds";

    private static final String RETRY_MAX_ATTEMPTS_KEY = "maple.framework.web.client.retry-max-attempts";

    private static final String RETRY_DELAY_MILLIS_KEY = "maple.framework.web.client.retry-delay-millis";

    @Bean
    @ConditionalOnMissingBean(name = "mapleWebClientConnectionProvider")
    public ConnectionProvider mapleWebClientConnectionProvider(Environment environment) {
        int maxConnections = getConfiguredInteger(environment, MAX_CONNECTIONS_KEY, DEFAULT_MAX_CONNECTIONS);
        int acquireTimeoutMillis = getConfiguredInteger(environment, ACQUIRE_TIMEOUT_MILLIS_KEY, DEFAULT_ACQUIRE_TIMEOUT_MILLIS);
        int pendingAcquireMaxCount = getConfiguredInteger(environment, PENDING_ACQUIRE_MAX_COUNT_KEY, DEFAULT_PENDING_ACQUIRE_MAX_COUNT);
        int idleTimeoutMillis = getConfiguredInteger(environment, IDLE_TIMEOUT_MILLIS_KEY, DEFAULT_IDLE_TIMEOUT_MILLIS);
        int maxLifeTimeMillis = getConfiguredInteger(environment, MAX_LIFE_TIME_MILLIS_KEY, DEFAULT_MAX_LIFE_TIME_MILLIS);
        int evictInBackgroundSeconds = getConfiguredInteger(environment, EVICT_IN_BACKGROUND_SECONDS_KEY, 120);
        return ConnectionProvider.builder("maple-leaf-webclient-connection-pool")
                .maxConnections(maxConnections)
                .pendingAcquireTimeout(Duration.ofMillis(acquireTimeoutMillis))
                .maxIdleTime(Duration.ofMillis(idleTimeoutMillis))
                .maxLifeTime(Duration.ofMillis(maxLifeTimeMillis))
                .pendingAcquireMaxCount(pendingAcquireMaxCount)
                .evictInBackground(Duration.ofSeconds(evictInBackgroundSeconds))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "mapleWebClientConnector")
    public ReactorClientHttpConnector mapleWebClientConnector(
            @Qualifier("mapleWebClientConnectionProvider") ConnectionProvider connectionProvider,
            Environment environment) {
        int timeoutSeconds = getConfiguredInteger(environment, TIMEOUT_SECONDS_KEY, DEFAULT_TIMEOUT_SECONDS);
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000)
                .compress(true)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS)))
                .responseTimeout(Duration.ofSeconds(timeoutSeconds));
        return new ReactorClientHttpConnector(httpClient);
    }

    /**
     * Creates a builder for declarative HTTP clients.
     *
     * @param webClient configured WebClient
     * @return proxy factory builder
     */
    @Bean
    public HttpServiceProxyFactory.Builder httpServiceProxyFactoryBuilder(WebClient webClient) {
        WebClientAdapter adapter = WebClientAdapter.create(webClient);
        return HttpServiceProxyFactory.builder()
                .exchangeAdapter(adapter);
    }

    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory(HttpServiceProxyFactory.Builder httpServiceProxyFactoryBuilder) {
        return httpServiceProxyFactoryBuilder.build();
    }

    /**
     * Creates a new WebClient builder. The Bean is prototype scoped because
     * {@link WebClient.Builder} is mutable.
     *
     * @return configured WebClient builder
     */
    @Bean
    @LoadBalanced
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public WebClient.Builder webClientBuilder(ObjectProvider<@NonNull GXWebClientService> webClientServiceProvider,
                                              ObjectProvider<@NonNull JsonMapper> jsonMapperProvider,
                                              @Qualifier("mapleWebClientConnector")
                                              ReactorClientHttpConnector clientHttpConnector,
                                              Environment environment) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(getConfiguredInteger(environment, MEMORY_BUFFER_SIZE_MB_KEY, DEFAULT_MEMORY_BUFFER_SIZE_MB) * 1024 * 1024))
                .build();

        ExchangeFilterFunction requestFilter = ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            LOGGER.debug("HTTP request: {} {}", clientRequest.method(), clientRequest.url());

            if (LOGGER.isTraceEnabled()) {
                clientRequest.headers().forEach((name, values) ->
                        LOGGER.trace("Request header: {}={}", name, String.join(", ", values)));
            }

            ClientRequest.Builder requestBuilder = ClientRequest.from(clientRequest);

            String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
            if (CharSequenceUtil.isNotBlank(appName)) {
                requestBuilder.header("X-Request-Source", appName);
            }

            requestBuilder.header("X-Request-Start-Time", String.valueOf(System.currentTimeMillis()));

            String userAgent = String.format("Maple-Leaf-WebClient/1.0 (%s)",
                    System.getProperty("os.name", "Unknown"));
            requestBuilder.header("User-Agent", userAgent);

            GXWebClientService webClientService = webClientServiceProvider.getIfAvailable();
            if (ObjectUtil.isNotNull(webClientService)) {
                String traceId = webClientService.getTraceId();
                if (CharSequenceUtil.isNotBlank(traceId)) {
                    requestBuilder.header(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
                }

                try {
                    String token = webClientService.generateHttpAuthToken();
                    if (CharSequenceUtil.isNotBlank(token)) {
                        requestBuilder.header(GXCommonConstant.X_AUTH_TOKEN, token);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Generate WebClient auth token failed: exceptionType={}", e.getClass().getName());
                    return Mono.error(new GXBusinessException("Generate WebClient auth token failed", e));
                }

                String platform = webClientService.getPlatform();
                if (CharSequenceUtil.isNotBlank(platform)) {
                    requestBuilder.header(GXTokenConstant.PLATFORM, platform);
                }
            }

            return Mono.just(requestBuilder.build());
        });

        ExchangeFilterFunction responseFilter = (clientRequest, next) -> {
            long startTime = System.currentTimeMillis();
            return next.exchange(clientRequest).flatMap(clientResponse -> {
                HttpStatusCode statusCode = clientResponse.statusCode();
                long duration = System.currentTimeMillis() - startTime;

                if (duration > 5000) {
                    LOGGER.warn("HTTP response: statusCode={}, durationMs={} (slow)", statusCode.value(), duration);
                } else if (duration > 2000) {
                    LOGGER.info("HTTP response: statusCode={}, durationMs={} (delayed)", statusCode.value(), duration);
                } else {
                    LOGGER.debug("HTTP response: statusCode={}, durationMs={}", statusCode.value(), duration);
                }

                List<String> contentTypeHeaders = clientResponse.headers().header("Content-Type");
                if (!contentTypeHeaders.isEmpty()) {
                    String contentType = contentTypeHeaders.getFirst();
                    if (contentType.contains("text/html") || contentType.contains("application/javascript")) {
                        LOGGER.debug("Potential script response content type: {}", contentType);
                    }
                }

                List<String> contentLengthHeaders = clientResponse.headers().header("Content-Length");
                if (!contentLengthHeaders.isEmpty()) {
                    try {
                        long contentLength = Long.parseLong(contentLengthHeaders.getFirst());
                        if (contentLength > DEFAULT_MEMORY_BUFFER_SIZE_MB * 1024 * 1024) {
                            LOGGER.warn("Response body size exceeds configured limit: {}MB > {}MB",
                                    contentLength / (1024 * 1024), DEFAULT_MEMORY_BUFFER_SIZE_MB);
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.trace("Parse Content-Length failed: {}", contentLengthHeaders.getFirst());
                    }
                }

                if (LOGGER.isTraceEnabled()) {
                    clientResponse.headers().asHttpHeaders().forEach((name, values) ->
                            LOGGER.trace("Response header: {}={}", name, String.join(", ", values)));
                }

                return Mono.just(clientResponse);
            });
        };

        ExchangeFilterFunction errorResponseHandleFilter = ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            HttpStatusCode httpStatusCode = clientResponse.statusCode();

            if (httpStatusCode.isError()) {
                LOGGER.warn("HTTP request failed: statusCode={}", httpStatusCode.value());
            }

            if (httpStatusCode.is4xxClientError()) {
                return handle4xxError(clientResponse, httpStatusCode);
            }
            else if (httpStatusCode.is5xxServerError()) {
                return handle5xxError(clientResponse, httpStatusCode);
            }

            return Mono.just(clientResponse);
        });

        ExchangeFilterFunction retryFilter = (clientRequest, next) -> next.exchange(clientRequest)
                .retryWhen(Retry.fixedDelay(
                                getConfiguredInteger(environment, RETRY_MAX_ATTEMPTS_KEY, DEFAULT_RETRY_MAX_ATTEMPTS),
                                Duration.ofMillis(getConfiguredInteger(environment, RETRY_DELAY_MILLIS_KEY, DEFAULT_RETRY_DELAY_MILLIS)))
                        .filter(this::isRetryableException)
                        .doBeforeRetry(retrySignal ->
                                LOGGER.debug("Retry HTTP request: attempt={}, exceptionType={}",
                                        retrySignal.totalRetries() + 1, retrySignal.failure().getClass().getName()))
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> retrySignal.failure()));

        JsonMapper jsonMapper = jsonMapperProvider.getIfAvailable();
        return WebClient.builder()
                .exchangeStrategies(strategies)
                .filter(requestFilter)
                .filter(responseFilter)
                .filter(retryFilter)
                .filter(errorResponseHandleFilter)
                .clientConnector(clientHttpConnector)
                .codecs(configurer -> {
                    if (ObjectUtil.isNotNull(jsonMapper)) {
                        configurer.defaultCodecs().jacksonJsonEncoder(
                                new JacksonJsonEncoder(jsonMapper)
                        );
                        configurer.defaultCodecs().jacksonJsonDecoder(
                                new JacksonJsonDecoder(jsonMapper)
                        );
                    }
                });
    }

    public WebClient.Builder webClientBuilder() {
        Environment environment = GXSpringContextUtils.getBean(Environment.class);
        ReactorClientHttpConnector clientHttpConnector = GXSpringContextUtils.getBean(ReactorClientHttpConnector.class);
        if (ObjectUtil.isNull(clientHttpConnector)) {
            clientHttpConnector = mapleWebClientConnector(mapleWebClientConnectionProvider(environment), environment);
        }
        return webClientBuilder(new ObjectProvider<>() {
            @Override
            public GXWebClientService getIfAvailable() {
                return GXSpringContextUtils.getBean(GXWebClientService.class);
            }
        }, new ObjectProvider<>() {
            @Override
            public JsonMapper getIfAvailable() {
                return GXSpringContextUtils.getBean(JsonMapper.class);
            }
        }, clientHttpConnector, environment);
    }

    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }

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

    private int getConfiguredInteger(Environment environment, String key, int defaultValue) {
        if (ObjectUtil.isNull(environment)) {
            return defaultValue;
        }
        return environment.getProperty(key, Integer.class, defaultValue);
    }

    private Mono<@NonNull ClientResponse> handle4xxError(ClientResponse clientResponse, HttpStatusCode httpStatusCode) {
        GXHttpInvokerApiErrorResDto errorApiResDto = new GXHttpInvokerApiErrorResDto();
        errorApiResDto.setMessage(getHttpStatusDescription(httpStatusCode));
        errorApiResDto.setCode(httpStatusCode.value());
        return clientResponse.bodyToMono(GXHttpInvokerApiErrorResDto.class)
                .onErrorReturn(errorApiResDto)
                .defaultIfEmpty(errorApiResDto)
                .flatMap(errorBody -> {
                    String errorMessage = String.format("客户端请求错误(4xx): %s",
                            CharSequenceUtil.isNotBlank(errorBody.getMessage()) ?
                                    errorBody.getMessage() : getHttpStatusDescription(httpStatusCode));
                    LOGGER.error("HTTP 4xx error detail: statusCode={}", httpStatusCode.value());
                    return Mono.error(new GXBusinessException(errorMessage, httpStatusCode.value()));
                });
    }

    private Mono<@NonNull ClientResponse> handle5xxError(ClientResponse clientResponse, HttpStatusCode httpStatusCode) {
        GXHttpInvokerApiErrorResDto errorApiResDto = new GXHttpInvokerApiErrorResDto();
        errorApiResDto.setMessage(getHttpStatusDescription(httpStatusCode));
        errorApiResDto.setCode(httpStatusCode.value());
        return clientResponse.bodyToMono(GXHttpInvokerApiErrorResDto.class)
                .onErrorReturn(errorApiResDto)
                .defaultIfEmpty(errorApiResDto)
                .flatMap(errorBody -> {
                    String errorMessage = String.format("服务器处理错误(5xx): %s",
                            CharSequenceUtil.isNotBlank(errorBody.getMessage()) ?
                                    errorBody.getMessage() : getHttpStatusDescription(httpStatusCode));
                    LOGGER.error("HTTP 5xx error detail: statusCode={}, responseLength={}",
                            httpStatusCode.value(), JSONUtil.toJsonStr(errorBody).length());
                    return Mono.error(new GXBusinessException(errorMessage, httpStatusCode.value()));
                });
    }

    private boolean shouldRetry(HttpStatusCode statusCode) {
        return statusCode.value() == 408 ||
                statusCode.value() == 429 ||
                statusCode.value() == 500 ||
                statusCode.value() == 502 ||
                statusCode.value() == 503 ||
                statusCode.value() == 504;
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof GXBusinessException businessException) {
            return shouldRetry(HttpStatusCode.valueOf(businessException.getCode()));
        }
        return throwable instanceof WebClientRequestException;
    }
}
