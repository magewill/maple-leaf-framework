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
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Feign request interceptor.
 */
@Slf4j
public class GXFeignRequestInterceptor implements RequestInterceptor {
    private final ObjectProvider<GXFeignService> feignServiceProvider;

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

        String token = feignService.generateHttpAuthToken();
        if (StrUtil.isNotBlank(token)) {
            requestTemplate.removeHeader(GXCommonConstant.X_AUTH_TOKEN);
            requestTemplate.header(GXCommonConstant.X_AUTH_TOKEN, token);
            log.debug("Propagated authentication token to Feign request");
        }

        String platform = feignService.getPlatform();
        if (StrUtil.isNotBlank(platform)) {
            requestTemplate.removeHeader(GXTokenConstant.PLATFORM);
            requestTemplate.header(GXTokenConstant.PLATFORM, platform);
            log.debug("Propagated platform identifier to Feign request");
        }
    }

    private void propagateTraceId(RequestTemplate requestTemplate, GXFeignService feignService) {
        String traceId = feignService == null ? null : feignService.getTraceId();
        if (StrUtil.isBlank(traceId)) {
            traceId = GXTraceIdContextUtils.getTraceId();
        }
        if (StrUtil.isBlank(traceId)) {
            traceId = GXTraceIdContextUtils.generateTraceId();
        }
        if (StrUtil.isNotBlank(traceId)) {
            requestTemplate.removeHeader(GXTraceIdContextUtils.TRACE_ID_KEY);
            requestTemplate.header(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            log.debug("Propagated trace ID to Feign request");
        }
    }

    private void appendCommonHeaders(RequestTemplate requestTemplate) {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        if (CharSequenceUtil.isNotBlank(appName)) {
            replaceHeader(requestTemplate, "X-Request-Source", appName);
        }

        replaceHeader(requestTemplate, "X-Request-Start-Time", String.valueOf(System.currentTimeMillis()));
        replaceHeader(requestTemplate, "X-Content-Type-Options", "nosniff");
        replaceHeader(requestTemplate, "X-Frame-Options", "DENY");
        replaceHeader(requestTemplate, "X-XSS-Protection", "1; mode=block");
        replaceHeader(requestTemplate, "Cache-Control", "no-cache, no-store, must-revalidate");
        replaceHeader(requestTemplate, "Pragma", "no-cache");
        replaceHeader(requestTemplate, "Expires", "0");

        String userAgent = String.format("Maple-Leaf-Feign/1.0 (%s)", System.getProperty("os.name", "Unknown"));
        replaceHeader(requestTemplate, "User-Agent", userAgent);
    }

    private void propagateAnnotatedHeaders(RequestTemplate requestTemplate) {
        Method method = requestTemplate.methodMetadata() == null ? null : requestTemplate.methodMetadata().method();
        if (method == null) {
            return;
        }

        GXFeignHeader feignHeader = AnnotationUtils.findAnnotation(method, GXFeignHeader.class);
        if (feignHeader == null || feignHeader.names().length == 0) {
            return;
        }

        HttpServletRequest request = GXCurrentRequestContextUtils.getHttpServletRequest();
        if (request == null) {
            return;
        }

        for (String headerName : feignHeader.names()) {
            if (CharSequenceUtil.isBlank(headerName)) {
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

    private void replaceHeader(RequestTemplate requestTemplate, String headerName, String headerValue) {
        requestTemplate.removeHeader(headerName);
        requestTemplate.header(headerName, headerValue);
    }
}
