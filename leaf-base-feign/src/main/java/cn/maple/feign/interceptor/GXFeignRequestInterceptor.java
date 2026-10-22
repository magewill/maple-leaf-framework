package cn.maple.feign.interceptor;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.feign.annotation.GXFeignHeader;
import cn.maple.feign.service.GXFeignService;
import feign.MethodMetadata;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feign request interceptor.
 *
 * <p>Propagates the framework auth token, platform identifier, trace id, headers
 * declared by {@link GXFeignHeader} and a small set of common headers. Header writes
 * are idempotent: existing values are removed before the resolved value is added.</p>
 */
@Slf4j
public class GXFeignRequestInterceptor implements RequestInterceptor {
    static final String AUTH_TOKEN_ENABLED_KEY = "maple.framework.feign.auth-token.enabled";

    private static final String APPLICATION_NAME_KEY = "spring.application.name";

    private static final String AUTH_TOKEN_MISSING_KEY = "maple.framework.web.feign.token";

    private static final String USER_AGENT_HEADER = "User-Agent";

    private static final String USER_AGENT_PREFIX = "Maple-Leaf-Feign/1.0 (";

    private static final String UNKNOWN_METHOD_KEY = "unknown";

    private static final Set<String> BLOCKED_PROPAGATION_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            GXCommonConstant.X_AUTH_TOKEN.toLowerCase(Locale.ROOT),
            "x-hmac-signature",
            "x-api-key",
            "host",
            "content-length",
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade"
    );

    private static final String OS_NAME = System.getProperty("os.name", "Unknown");

    private static final String DEFAULT_USER_AGENT = USER_AGENT_PREFIX + OS_NAME + ")";

    /**
     * Caches annotation lookups per interface method. The key set is bounded by the
     * number of Feign client methods declared by the application.
     */
    private static final ConcurrentHashMap<Method, Optional<GXFeignHeader>> HEADER_ANNOTATION_CACHE =
            new ConcurrentHashMap<>();

    private final ObjectProvider<GXFeignService> feignServiceProvider;

    private volatile String applicationName;

    public GXFeignRequestInterceptor() {
        this(null);
    }

    public GXFeignRequestInterceptor(ObjectProvider<GXFeignService> feignServiceProvider) {
        this.feignServiceProvider = feignServiceProvider;
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {
        GXFeignService feignService = resolveFeignService();
        propagateAnnotatedHeaders(requestTemplate);
        propagateServiceHeaders(requestTemplate, feignService);
        propagateTraceId(requestTemplate, feignService);
        appendCommonHeaders(requestTemplate);
    }

    private GXFeignService resolveFeignService() {
        if (feignServiceProvider != null) {
            return feignServiceProvider.getIfAvailable();
        }
        return GXSpringContextUtils.getBean(GXFeignService.class);
    }

    private void propagateServiceHeaders(RequestTemplate requestTemplate, GXFeignService feignService) {
        if (feignService == null) {
            log.warn("Failed to get FeignService instance, skipping authentication header propagation");
            return;
        }

        if (isAuthTokenEnabled()) {
            String token = feignService.generateHttpAuthToken();
            if (StrUtil.isNotBlank(token)) {
                replaceHeader(requestTemplate, GXCommonConstant.X_AUTH_TOKEN, token);
                log.debug("Propagated authentication token to Feign request");
            }
        } else {
            log.warn("Feign auth token propagation disabled, skip token generation: configKey={}, requiredKey={}, method={}",
                    AUTH_TOKEN_ENABLED_KEY, AUTH_TOKEN_MISSING_KEY, resolveMethodKey(requestTemplate));
        }

        String platform = feignService.getPlatform();
        if (StrUtil.isNotBlank(platform)) {
            replaceHeader(requestTemplate, GXTokenConstant.PLATFORM, platform);
            log.debug("Propagated platform identifier to Feign request");
        }
    }

    private boolean isAuthTokenEnabled() {
        String configured = GXCommonUtils.getEnvironmentValue(AUTH_TOKEN_ENABLED_KEY, String.class);
        return CharSequenceUtil.isBlank(configured) || Boolean.parseBoolean(configured.trim());
    }

    private void propagateTraceId(RequestTemplate requestTemplate, GXFeignService feignService) {
        String traceId = feignService == null ? null : feignService.getTraceId();
        if (StrUtil.isBlank(traceId)) {
            traceId = GXTraceIdContextUtils.getTraceId();
        }
        if (StrUtil.isNotBlank(traceId)) {
            replaceHeader(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            log.debug("Propagated trace ID to Feign request");
            return;
        }

        String previousTraceId = GXTraceIdContextUtils.getNullableTraceId();
        String generatedTraceId = GXTraceIdContextUtils.generateTraceId();
        GXTraceIdContextUtils.putTraceId(generatedTraceId);
        try {
            replaceHeader(requestTemplate, GXTraceIdContextUtils.TRACE_ID_KEY, generatedTraceId);
            log.debug("Generated and propagated trace ID to Feign request");
        } finally {
            GXTraceIdContextUtils.restoreTraceId(previousTraceId);
        }
    }

    private void appendCommonHeaders(RequestTemplate requestTemplate) {
        String currentApplicationName = resolveApplicationName();
        if (CharSequenceUtil.isNotBlank(currentApplicationName)) {
            replaceHeader(requestTemplate, "X-Request-Source", currentApplicationName);
        }

        replaceHeader(requestTemplate, "X-Request-Start-Time", String.valueOf(System.currentTimeMillis()));

        if (!requestTemplate.headers().containsKey(USER_AGENT_HEADER)) {
            replaceHeader(requestTemplate, USER_AGENT_HEADER, DEFAULT_USER_AGENT);
        }
    }

    private String resolveApplicationName() {
        String cached = applicationName;
        if (cached != null) {
            return cached;
        }
        String resolved = GXCommonUtils.getEnvironmentValue(APPLICATION_NAME_KEY, String.class);
        if (resolved == null) {
            return null;
        }
        applicationName = resolved;
        return resolved;
    }

    private void propagateAnnotatedHeaders(RequestTemplate requestTemplate) {
        MethodMetadata methodMetadata = requestTemplate.methodMetadata();
        Method method = methodMetadata == null ? null : methodMetadata.method();
        if (method == null) {
            return;
        }

        GXFeignHeader feignHeader = HEADER_ANNOTATION_CACHE
                .computeIfAbsent(method, key -> Optional.ofNullable(AnnotationUtils.findAnnotation(key, GXFeignHeader.class)))
                .orElse(null);
        if (feignHeader == null || feignHeader.names().length == 0) {
            return;
        }

        HttpServletRequest request = GXCurrentRequestContextUtils.getHttpServletRequest();
        if (request == null) {
            log.debug("No servlet request context, skip annotated header propagation: method={}", method.getName());
            return;
        }

        for (String headerName : feignHeader.names()) {
            if (CharSequenceUtil.isBlank(headerName)) {
                continue;
            }
            if (isBlockedPropagationHeader(headerName)) {
                log.debug("Skipped blocked annotated header propagation");
                continue;
            }
            List<String> headerValues = Collections.list(request.getHeaders(headerName));
            if (headerValues.isEmpty()) {
                continue;
            }
            requestTemplate.removeHeader(headerName);
            requestTemplate.header(headerName, headerValues);
            log.debug("Propagated annotated header to Feign request");
        }
    }

    private boolean isBlockedPropagationHeader(String headerName) {
        return BLOCKED_PROPAGATION_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
    }

    private String resolveMethodKey(RequestTemplate requestTemplate) {
        MethodMetadata methodMetadata = requestTemplate.methodMetadata();
        if (methodMetadata == null || methodMetadata.method() == null) {
            return UNKNOWN_METHOD_KEY;
        }
        Method method = methodMetadata.method();
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    private void replaceHeader(RequestTemplate requestTemplate, String headerName, String headerValue) {
        requestTemplate.removeHeader(headerName);
        requestTemplate.header(headerName, headerValue);
    }
}
