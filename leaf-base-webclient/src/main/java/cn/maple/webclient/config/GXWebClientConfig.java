package cn.maple.webclient.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.webclient.service.GXWebClientContextSnapshot;
import cn.maple.webclient.service.GXWebClientService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring configuration for the framework WebClient and HttpServiceProxyFactory.
 * <p>The configured client adds framework headers, bounded connection pooling,
 * retry handling, load balancer addressing guards and 4xx/5xx conversion to
 * {@link GXBusinessException}.</p>
 *
 * @author britton chen <britton@126.com>
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class GXWebClientConfig implements DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXWebClientConfig.class);

    private static final String START_TIME_HEADER = "X-Request-Start-Time";

    private static final String SOURCE_HEADER = "X-Request-Source";

    private static final String APPLICATION_NAME_KEY = "spring.application.name";

    private static final int SLOW_THRESHOLD_MILLIS = 5000;

    private static final int DELAYED_THRESHOLD_MILLIS = 2000;

    /**
     * Default window used to throttle "context dropped" warnings.
     */
    static final long DEFAULT_CONTEXT_WARNING_INTERVAL_MILLIS = 30_000L;

    private static final Map<String, String> BUILT_IN_SENSITIVE_HEADERS = Map.of(
            "authorization", "******",
            "cookie", "******",
            "set-cookie", "******",
            "x-auth-token", "******",
            "x-hmac-signature", "******",
            "x-api-key", "******"
    );

    private final GXWebClientResourceHolder resourceHolder = new GXWebClientResourceHolder();

    private final AtomicLong lastContextWarningAtMillis = new AtomicLong(0L);

    /**
     * Throttle window in milliseconds; package visible so tests can shrink it
     * without reflection.
     */
    long contextWarningIntervalMillis = DEFAULT_CONTEXT_WARNING_INTERVAL_MILLIS;

    /**
     * Creates the bounded connection pool. The pool is released when the owning
     * application context shuts down.
     *
     * @param environment environment
     * @return connection provider
     */
    @Bean(destroyMethod = "dispose")
    @ConditionalOnMissingBean(name = "mapleWebClientConnectionProvider")
    public ConnectionProvider mapleWebClientConnectionProvider(Environment environment) {
        return createConnectionProvider(environment);
    }

    /**
     * Creates the shared reactor-netty connector with the four independent timeouts.
     *
     * @param connectionProvider connection pool
     * @param environment        environment
     * @return connector
     */
    @Bean
    @ConditionalOnMissingBean(name = "mapleWebClientConnector")
    public ReactorClientHttpConnector mapleWebClientConnector(
            @Qualifier("mapleWebClientConnectionProvider") ConnectionProvider connectionProvider,
            Environment environment) {
        GXWebClientProperties properties = GXWebClientProperties.from(environment);
        int connectTimeoutMillis = properties.effectiveConnectTimeoutMillis();
        int readTimeoutMillis = properties.effectiveReadTimeoutMillis();
        int writeTimeoutMillis = properties.effectiveWriteTimeoutMillis();
        int responseTimeoutMillis = properties.effectiveResponseTimeoutMillis();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .compress(true)
                .doOnConnected(conn -> {
                    if (readTimeoutMillis > 0) {
                        conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutMillis, TimeUnit.MILLISECONDS));
                    }
                    if (writeTimeoutMillis > 0) {
                        conn.addHandlerLast(new WriteTimeoutHandler(writeTimeoutMillis, TimeUnit.MILLISECONDS));
                    }
                });
        if (responseTimeoutMillis > 0) {
            httpClient = httpClient.responseTimeout(Duration.ofMillis(responseTimeoutMillis));
        }
        return new ReactorClientHttpConnector(httpClient);
    }

    /**
     * Creates a builder for declarative HTTP clients.
     *
     * @param webClient configured WebClient
     * @return proxy factory builder
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpServiceProxyFactory.Builder httpServiceProxyFactoryBuilder(@Qualifier("webClient") WebClient webClient) {
        WebClientAdapter adapter = WebClientAdapter.create(webClient);
        return HttpServiceProxyFactory.builder()
                .exchangeAdapter(adapter);
    }

    /**
     * Creates the HTTP service proxy factory.
     *
     * @param httpServiceProxyFactoryBuilder builder
     * @return proxy factory
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpServiceProxyFactory httpServiceProxyFactory(HttpServiceProxyFactory.Builder httpServiceProxyFactoryBuilder) {
        return httpServiceProxyFactoryBuilder.build();
    }

    /**
     * Load balanced builder used by {@link #webClient(WebClient.Builder)}. The bean
     * name remains {@code webClientBuilder}; the four parameter method signature is
     * unchanged, the load balancer mode is carried by an internal overload.
     *
     * @param webClientServiceProvider auth/trace SPI provider
     * @param jsonMapperProvider       JSON mapper provider
     * @param clientHttpConnector      connector
     * @param environment              environment
     * @return prototype scoped load balanced builder
     */
    @Bean(name = "webClientBuilder")
    @LoadBalanced
    @Primary
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public WebClient.Builder loadBalancedWebClientBuilder(
            ObjectProvider<@NonNull GXWebClientService> webClientServiceProvider,
            ObjectProvider<@NonNull JsonMapper> jsonMapperProvider,
            @Qualifier("mapleWebClientConnector") ReactorClientHttpConnector clientHttpConnector,
            Environment environment) {
        return buildWebClientBuilder(webClientServiceProvider, jsonMapperProvider, clientHttpConnector,
                environment, true);
    }

    /**
     * Direct (non load balanced) builder for FQDN, IP or port based addressing.
     *
     * @param webClientServiceProvider auth/trace SPI provider
     * @param jsonMapperProvider       JSON mapper provider
     * @param clientHttpConnector      connector
     * @param environment              environment
     * @return prototype scoped direct builder
     */
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnMissingBean(name = "webClientDirectBuilder")
    public WebClient.Builder webClientDirectBuilder(
            ObjectProvider<@NonNull GXWebClientService> webClientServiceProvider,
            ObjectProvider<@NonNull JsonMapper> jsonMapperProvider,
            @Qualifier("mapleWebClientConnector") ReactorClientHttpConnector clientHttpConnector,
            Environment environment) {
        return buildWebClientBuilder(webClientServiceProvider, jsonMapperProvider, clientHttpConnector,
                environment, false);
    }

    /**
     * Creates the shared WebClient from the load balanced builder.
     *
     * @param webClientBuilder load balanced builder
     * @return WebClient bean
     */
    @Bean
    @ConditionalOnMissingBean
    public WebClient webClient(@Qualifier("webClientBuilder") WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }

    /**
     * Builds a plain (non load balanced) builder reusing the container connector.
     * <p>When no connector bean is available the self managed pool of
     * {@link GXWebClientResourceHolder} is reused instead of creating a new pool on
     * every call. After {@link GXWebClientResourceHolder#dispose()} the holder
     * refuses to rebuild a pool.</p>
     *
     * @return builder without the load balancer guard
     */
    public WebClient.Builder webClientBuilder() {
        Environment environment = GXSpringContextUtils.getEnvironment();
        ReactorClientHttpConnector clientHttpConnector =
                GXSpringContextUtils.getBean("mapleWebClientConnector", ReactorClientHttpConnector.class);
        if (ObjectUtil.isNull(clientHttpConnector)) {
            ConnectionProvider connectionProvider = resourceHolder.getOrCreate(
                    () -> createConnectionProvider(environment));
            clientHttpConnector = mapleWebClientConnector(connectionProvider, environment);
        }
        return webClientBuilder(contextServiceProvider(), contextJsonMapperProvider(), clientHttpConnector, environment);
    }

    /**
     * Compatibility overload: plain (non load balanced) builder with the original
     * four parameter signature.
     *
     * @param webClientServiceProvider auth/trace SPI provider
     * @param jsonMapperProvider       JSON mapper provider
     * @param clientHttpConnector      connector
     * @param environment              environment
     * @return builder without the load balancer guard
     */
    public WebClient.Builder webClientBuilder(
            ObjectProvider<@NonNull GXWebClientService> webClientServiceProvider,
            ObjectProvider<@NonNull JsonMapper> jsonMapperProvider,
            ReactorClientHttpConnector clientHttpConnector,
            Environment environment) {
        return buildWebClientBuilder(webClientServiceProvider, jsonMapperProvider, clientHttpConnector,
                environment, false);
    }

    /**
     * Releases the self managed connection pool.
     */
    @Override
    public void destroy() {
        resourceHolder.dispose();
    }


    private WebClient.Builder buildWebClientBuilder(
            ObjectProvider<@NonNull GXWebClientService> webClientServiceProvider,
            ObjectProvider<@NonNull JsonMapper> jsonMapperProvider,
            ReactorClientHttpConnector clientHttpConnector,
            Environment environment,
            boolean lbMode) {
        GXWebClientProperties properties = GXWebClientProperties.from(environment);
        if (!properties.getInvalidLbGuardAllowHosts().isEmpty()) {
            LOGGER.warn("Invalid lb-guard-allow-hosts entries ignored: count={}",
                    properties.getInvalidLbGuardAllowHosts().size());
        }
        Set<HttpMethod> retryMethods = properties.toRetryMethods();
        // Resolved once per builder: no Environment or container lookup happens on the request path.
        GXWebClientService webClientService = resolveService(webClientServiceProvider);
        String applicationName = resolveApplicationName(environment);
        Map<String, String> sensitiveHeaders = sensitiveHeaders(webClientService);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(properties.getMemoryBufferSizeMb() * 1024 * 1024))
                .build();

        ExchangeFilterFunction requestFilter = (clientRequest, next) -> Mono.deferContextual(contextView -> {
            LOGGER.debug("HTTP request: {} {}", clientRequest.method(), clientRequest.url());

            ClientRequest.Builder requestBuilder = ClientRequest.from(clientRequest);

            if (CharSequenceUtil.isNotBlank(applicationName)) {
                requestBuilder.header(SOURCE_HEADER, applicationName);
            }
            requestBuilder.header(HttpHeaders.USER_AGENT, properties.userAgent());

            GXWebClientContextSnapshot snapshot = snapshotOf(clientRequest,
                    contextView.getOrDefault(GXWebClientContextSnapshot.ATTRIBUTE_KEY, null));
            String traceId = snapshot != null && CharSequenceUtil.isNotBlank(snapshot.getTraceId())
                    ? snapshot.getTraceId()
                    : (webClientService == null ? null : blankToNull(webClientService.getTraceId()));
            String platform = snapshot != null && CharSequenceUtil.isNotBlank(snapshot.getPlatform())
                    ? snapshot.getPlatform()
                    : (webClientService == null ? null : blankToNull(webClientService.getPlatform()));

            if (CharSequenceUtil.isBlank(traceId) && CharSequenceUtil.isBlank(platform)
                    && CharSequenceUtil.isBlank(snapshot == null ? null : snapshot.getAuthToken())) {
                warnDroppedContext();
            }
            if (CharSequenceUtil.isNotBlank(traceId)) {
                requestBuilder.header(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            }
            if (CharSequenceUtil.isNotBlank(platform)) {
                requestBuilder.header(GXTokenConstant.PLATFORM, platform);
            }

            String authToken = snapshot == null ? null : blankToNull(snapshot.getAuthToken());
            if (CharSequenceUtil.isBlank(authToken) && webClientService != null) {
                try {
                    authToken = blankToNull(webClientService.generateHttpAuthToken());
                } catch (Exception e) {
                    LOGGER.warn("Generate WebClient auth token failed: exceptionType={}",
                            e.getClass().getName());
                    return Mono.error(new GXBusinessException("Generate WebClient auth token failed", e));
                }
            }
            if (CharSequenceUtil.isNotBlank(authToken)) {
                requestBuilder.header(GXCommonConstant.X_AUTH_TOKEN, authToken);
            }

            ClientRequest builtRequest = requestBuilder.build();
            logHeaders("Request", builtRequest.headers(), sensitiveHeaders);
            return next.exchange(builtRequest);
        });

        ExchangeFilterFunction retryFilter = (clientRequest, next) -> {
            AttemptMetrics metrics = new AttemptMetrics();
            Mono<ClientResponse> attempt = Mono.defer(() -> {
                ClientRequest stampedRequest = ClientRequest.from(clientRequest)
                        .header(START_TIME_HEADER, String.valueOf(System.currentTimeMillis()))
                        .build();
                long startNanos = System.nanoTime();
                return next.exchange(stampedRequest)
                        .doOnError(throwable -> recordAttempt(metrics, startNanos))
                        .doOnSuccess(clientResponse -> recordAttempt(metrics, startNanos));
            });

            return attempt
                    .retryWhen(Retry.fixedDelay(properties.getRetryMaxAttempts(),
                                    Duration.ofMillis(properties.getRetryDelayMillis()))
                            .filter(throwable -> retryMethods.contains(clientRequest.method())
                                    && isRetryableException(throwable))
                            .doBeforeRetry(retrySignal -> {
                                LOGGER.debug("Retry HTTP request: attempt={}, exceptionType={}",
                                        retrySignal.totalRetries() + 1,
                                        retrySignal.failure().getClass().getName());
                                metrics.retryDelayMillis.addAndGet(properties.getRetryDelayMillis());
                            })
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> retrySignal.failure()))
                    .flatMap(clientResponse -> {
                        logResponse(clientResponse, metrics, properties, sensitiveHeaders);
                        notifyResponse(webClientService, clientRequest, clientResponse, metrics,
                                sensitiveHeaders);
                        return Mono.just(clientResponse);
                    })
                    .doOnError(throwable -> {
                        LOGGER.error("HTTP request failed: durationMs={}, attempts={}, exceptionType={}",
                                metrics.netMillis.get(), metrics.attempts.get(),
                                throwable.getClass().getName());
                        notifyException(webClientService, clientRequest, throwable);
                    });
        };

        ExchangeFilterFunction errorResponseHandleFilter = (clientRequest, next) -> next.exchange(clientRequest)
                .flatMap(clientResponse -> {
                    HttpStatusCode httpStatusCode = clientResponse.statusCode();
                    if (httpStatusCode.is4xxClientError()) {
                        return handleErrorResponse(clientResponse, httpStatusCode, true);
                    }
                    if (httpStatusCode.is5xxServerError()) {
                        return handleErrorResponse(clientResponse, httpStatusCode, false);
                    }
                    return Mono.just(clientResponse);
                });

        JsonMapper jsonMapper = jsonMapperProvider.getIfAvailable();
        WebClient.Builder builder = WebClient.builder()
                .exchangeStrategies(strategies)
                .filter(requestFilter)
                .filter(retryFilter)
                .filter(errorResponseHandleFilter);
        if (lbMode && properties.isLbGuardEnabled()) {
            builder.filter(lbGuardFilter(properties));
        }
        return builder
                .clientConnector(clientHttpConnector)
                .codecs(configurer -> {
                    if (ObjectUtil.isNotNull(jsonMapper)) {
                        configurer.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
                        configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
                    }
                });
    }

    private static void recordAttempt(AttemptMetrics metrics, long startNanos) {
        metrics.netMillis.addAndGet(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        metrics.attempts.incrementAndGet();
    }

    private ExchangeFilterFunction lbGuardFilter(GXWebClientProperties properties) {
        return (clientRequest, next) -> {
            String host = hostOf(clientRequest.url());
            if (isAllowedHost(host, properties)) {
                return next.exchange(clientRequest);
            }
            if (isDirectHost(host, clientRequest.url())) {
                LOGGER.error("Load-balanced client rejects direct host: host={}", host);
                String message = "负载均衡客户端仅支持服务名寻址（host=" + host
                        + "）；直连请使用 webClientDirectBuilder；带点服务名请登记 lb-guard-allow-hosts";
                return Mono.error(new GXBusinessException(message, 400));
            }
            return next.exchange(clientRequest);
        };
    }

    private Mono<@NonNull ClientResponse> handleErrorResponse(ClientResponse clientResponse,
                                                              HttpStatusCode httpStatusCode,
                                                              boolean clientError) {
        return clientResponse.bodyToMono(String.class)
                .onErrorResume(throwable -> {
                    LOGGER.debug("Read error body failed: exceptionType={}", throwable.getClass().getName());
                    return Mono.just("");
                })
                .defaultIfEmpty("")
                .flatMap(body -> {
                    GXWebClientErrorResDto errorBody = parseErrorBody(body);
                    int code = resolveErrorCode(errorBody, httpStatusCode);
                    String detail = resolveErrorDetail(errorBody, httpStatusCode);
                    String prefix = clientError ? "客户端请求错误(4xx): " : "服务器处理错误(5xx): ";
                    if (clientError) {
                        LOGGER.error("HTTP 4xx error detail: statusCode={}", httpStatusCode.value());
                    } else {
                        LOGGER.error("HTTP 5xx error detail: statusCode={}, responseLength={}",
                                httpStatusCode.value(), body.length());
                    }
                    return Mono.error(new GXBusinessException(prefix + detail, code));
                });
    }

    private GXWebClientErrorResDto parseErrorBody(String body) {
        if (CharSequenceUtil.isBlank(body)) {
            return new GXWebClientErrorResDto();
        }
        try {
            return JSONUtil.toBean(body, GXWebClientErrorResDto.class);
        } catch (Exception e) {
            LOGGER.debug("Parse error body failed: exceptionType={}", e.getClass().getName());
            return new GXWebClientErrorResDto();
        }
    }

    private int resolveErrorCode(GXWebClientErrorResDto errorBody, HttpStatusCode httpStatusCode) {
        Integer bodyCode = errorBody.getCode();
        if (bodyCode != null && bodyCode > 0) {
            return bodyCode;
        }
        Integer bodyStatus = errorBody.getStatus();
        if (bodyStatus != null && bodyStatus > 0) {
            return bodyStatus;
        }
        return httpStatusCode.value();
    }

    private String resolveErrorDetail(GXWebClientErrorResDto errorBody, HttpStatusCode httpStatusCode) {
        if (CharSequenceUtil.isNotBlank(errorBody.getMsg())) {
            return errorBody.getMsg();
        }
        if (CharSequenceUtil.isNotBlank(errorBody.getMessage())) {
            return errorBody.getMessage();
        }
        return getHttpStatusDescription(httpStatusCode);
    }

    private void logResponse(ClientResponse clientResponse, AttemptMetrics metrics,
                             GXWebClientProperties properties, Map<String, String> sensitiveHeaders) {
        HttpStatusCode statusCode = clientResponse.statusCode();
        logHeaders("Response", clientResponse.headers().asHttpHeaders(), sensitiveHeaders);
        long duration = metrics.netMillis.get();
        long retryDelay = metrics.retryDelayMillis.get();
        int attempts = metrics.attempts.get();

        if (duration > SLOW_THRESHOLD_MILLIS) {
            LOGGER.warn("HTTP response: statusCode={}, durationMs={}, attempts={}, retryDelayMs={} (slow)",
                    statusCode.value(), duration, attempts, retryDelay);
        } else if (duration > DELAYED_THRESHOLD_MILLIS) {
            LOGGER.info("HTTP response: statusCode={}, durationMs={}, attempts={}, retryDelayMs={} (delayed)",
                    statusCode.value(), duration, attempts, retryDelay);
        } else {
            LOGGER.debug("HTTP response: statusCode={}, durationMs={}, attempts={}, retryDelayMs={}",
                    statusCode.value(), duration, attempts, retryDelay);
        }

        List<String> contentTypeHeaders = clientResponse.headers().header(HttpHeaders.CONTENT_TYPE);
        if (!contentTypeHeaders.isEmpty()) {
            String contentType = contentTypeHeaders.getFirst();
            if (contentType != null
                    && (contentType.contains("text/html") || contentType.contains("application/javascript"))) {
                LOGGER.debug("Potential script response content type: {}", contentType);
            }
        }

        List<String> contentLengthHeaders = clientResponse.headers().header(HttpHeaders.CONTENT_LENGTH);
        String rawContentLength = contentLengthHeaders.isEmpty() ? null : contentLengthHeaders.getFirst();
        if (rawContentLength != null) {
            try {
                long contentLength = Long.parseLong(rawContentLength);
                long limit = (long) properties.getMemoryBufferSizeMb() * 1024 * 1024;
                if (contentLength > limit) {
                    LOGGER.warn("Response body size exceeds configured limit: {}MB > {}MB",
                            contentLength / (1024 * 1024), properties.getMemoryBufferSizeMb());
                }
            } catch (NumberFormatException e) {
                LOGGER.trace("Parse Content-Length failed: {}", rawContentLength);
            }
        }
    }

    private void notifyResponse(@Nullable GXWebClientService webClientService,
                                ClientRequest clientRequest,
                                ClientResponse clientResponse,
                                AttemptMetrics metrics,
                                Map<String, String> sensitiveHeaders) {
        if (webClientService == null) {
            return;
        }
        try {
            long duration = metrics.netMillis.get();
            webClientService.logRequestResponse(
                    clientRequest.method(),
                    clientRequest.url().toString(),
                    maskedHeaders(clientRequest.headers(), sensitiveHeaders),
                    null,
                    clientResponse.statusCode(),
                    maskedHeaders(clientResponse.headers().asHttpHeaders(), sensitiveHeaders),
                    null,
                    duration);
            webClientService.logRequestResponse(clientRequest.method(), clientRequest.url().toString(),
                    clientResponse.statusCode(), duration, traceIdOf(clientRequest));
        } catch (Exception e) {
            LOGGER.debug("SPI logRequestResponse failed: exceptionType={}", e.getClass().getName());
        }
    }

    private void notifyException(@Nullable GXWebClientService webClientService,
                                 ClientRequest clientRequest,
                                 Throwable throwable) {
        if (webClientService == null) {
            return;
        }
        try {
            webClientService.logRequestException(clientRequest.method(), clientRequest.url().toString(),
                    throwable, traceIdOf(clientRequest));
        } catch (Exception e) {
            LOGGER.debug("SPI logRequestException failed: exceptionType={}", e.getClass().getName());
        }
    }

    private void warnDroppedContext() {
        long now = System.currentTimeMillis();
        long last = lastContextWarningAtMillis.get();
        if (now - last < contextWarningIntervalMillis) {
            return;
        }
        if (lastContextWarningAtMillis.compareAndSet(last, now)) {
            LOGGER.warn("HTTP request dropped context: keys=traceId,platform");
        }
    }

    private static ConnectionProvider createConnectionProvider(Environment environment) {
        GXWebClientProperties properties = GXWebClientProperties.from(environment);
        return ConnectionProvider.builder("maple-leaf-webclient-connection-pool")
                .maxConnections(properties.getMaxConnections())
                .pendingAcquireTimeout(Duration.ofMillis(properties.getAcquireTimeoutMillis()))
                .maxIdleTime(Duration.ofMillis(properties.getIdleTimeoutMillis()))
                .maxLifeTime(Duration.ofMillis(properties.getMaxLifeTimeMillis()))
                .pendingAcquireMaxCount(properties.getPendingAcquireMaxCount())
                .evictInBackground(Duration.ofSeconds(properties.getEvictInBackgroundSeconds()))
                .build();
    }

    private static @Nullable GXWebClientService resolveService(
            ObjectProvider<@NonNull GXWebClientService> provider) {
        try {
            return provider.getIfAvailable();
        } catch (NoUniqueBeanDefinitionException e) {
            LOGGER.warn("Multiple GXWebClientService beans found: count={}, using the first one",
                    e.getNumberOfBeansFound());
            return provider.orderedStream().findFirst().orElse(null);
        }
    }

    private static ObjectProvider<@NonNull GXWebClientService> contextServiceProvider() {
        return new ObjectProvider<>() {
            @Override
            public GXWebClientService getIfAvailable() {
                Map<String, GXWebClientService> beans = GXSpringContextUtils.getBeans(GXWebClientService.class);
                if (beans.isEmpty()) {
                    return null;
                }
                if (beans.size() > 1) {
                    LOGGER.warn("Multiple GXWebClientService beans found: count={}, using the first one",
                            beans.size());
                }
                return beans.values().iterator().next();
            }
        };
    }

    private static ObjectProvider<@NonNull JsonMapper> contextJsonMapperProvider() {
        return new ObjectProvider<>() {
            @Override
            public JsonMapper getIfAvailable() {
                return GXSpringContextUtils.getBean(JsonMapper.class);
            }
        };
    }

    private static @Nullable GXWebClientContextSnapshot snapshotOf(ClientRequest clientRequest,
                                                                   @Nullable Object contextValue) {
        Object attribute = clientRequest.attribute(GXWebClientContextSnapshot.ATTRIBUTE_KEY);
        if (attribute instanceof GXWebClientContextSnapshot snapshot) {
            return snapshot;
        }
        return contextValue instanceof GXWebClientContextSnapshot snapshot ? snapshot : null;
    }

    private static String traceIdOf(ClientRequest clientRequest) {
        String traceId = clientRequest.headers().getFirst(GXTraceIdContextUtils.TRACE_ID_KEY);
        return traceId == null ? "" : traceId;
    }

    private static @Nullable String hostOf(URI uri) {
        return uri.getHost();
    }

    private static boolean isAllowedHost(@Nullable String host, GXWebClientProperties properties) {
        if (CharSequenceUtil.isBlank(host)) {
            return false;
        }
        return properties.getLbGuardAllowHosts().contains(host.toLowerCase(Locale.ROOT));
    }

    private static boolean isDirectHost(@Nullable String host, URI uri) {
        if (CharSequenceUtil.isBlank(host)) {
            return true;
        }
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        if (host.contains(".") || host.contains(":")) {
            return true;
        }
        return uri.getPort() != -1;
    }

    private static Map<String, String> sensitiveHeaders(@Nullable GXWebClientService webClientService) {
        Map<String, String> headers = new HashMap<>(BUILT_IN_SENSITIVE_HEADERS);
        if (webClientService != null) {
            try {
                webClientService.getSensitiveHeaderFields().forEach(
                        (name, value) -> headers.put(name.toLowerCase(Locale.ROOT), value));
            } catch (Exception e) {
                LOGGER.debug("Read sensitive header fields failed: exceptionType={}", e.getClass().getName());
            }
        }
        return Map.copyOf(headers);
    }

    private static @Nullable String resolveApplicationName(Environment environment) {
        String value = environment.getProperty(APPLICATION_NAME_KEY);
        return CharSequenceUtil.isBlank(value) ? null : value;
    }

    private static HttpHeaders maskedHeaders(HttpHeaders source, Map<String, String> sensitiveHeaders) {
        HttpHeaders masked = new HttpHeaders();
        source.forEach((name, values) -> {
            String replacement = sensitiveHeaders.get(name.toLowerCase(Locale.ROOT));
            masked.put(name, replacement == null ? new ArrayList<>(values) : List.of(replacement));
        });
        return masked;
    }

    private static void logHeaders(String direction, HttpHeaders headers, Map<String, String> sensitiveHeaders) {
        if (!LOGGER.isTraceEnabled()) {
            return;
        }
        maskedHeaders(headers, sensitiveHeaders).forEach((name, values) ->
                LOGGER.trace("{} header: {}={}", direction, name, String.join(", ", values)));
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

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof GXBusinessException businessException) {
            return shouldRetry(HttpStatusCode.valueOf(businessException.getCode()));
        }
        return throwable instanceof WebClientRequestException;
    }

    private boolean shouldRetry(HttpStatusCode statusCode) {
        return statusCode.value() == 408
                || statusCode.value() == 429
                || statusCode.value() == 500
                || statusCode.value() == 502
                || statusCode.value() == 503
                || statusCode.value() == 504;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return CharSequenceUtil.isBlank(value) ? null : value;
    }

    /**
     * Per request attempt accumulator shared across filter stages.
     */
    static final class AttemptMetrics {
        private final AtomicInteger attempts = new AtomicInteger();

        private final AtomicLong netMillis = new AtomicLong();

        private final AtomicLong retryDelayMillis = new AtomicLong();
    }
}
