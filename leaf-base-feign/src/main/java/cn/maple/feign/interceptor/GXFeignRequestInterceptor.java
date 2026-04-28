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
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Feign request interceptor.
 */
@Slf4j
public class GXFeignRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate requestTemplate) {
        GXFeignService feignService = GXSpringContextUtils.getBean(GXFeignService.class);
        propagateAnnotatedHeaders(requestTemplate);
        propagateServiceHeaders(requestTemplate, feignService);
        propagateTraceId(requestTemplate, feignService);
        appendCommonHeaders(requestTemplate);
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
            log.debug("Propagated platform identifier [{}] to Feign request", platform);
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
            log.debug("Propagated trace ID [{}] to Feign request", traceId);
        }
    }

    private void appendCommonHeaders(RequestTemplate requestTemplate) {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        if (CharSequenceUtil.isNotBlank(appName)) {
            requestTemplate.header("X-Request-Source", appName);
        }

        requestTemplate.header("X-Request-Start-Time", String.valueOf(System.currentTimeMillis()));
        requestTemplate.header("X-Content-Type-Options", "nosniff");
        requestTemplate.header("X-Frame-Options", "DENY");
        requestTemplate.header("X-XSS-Protection", "1; mode=block");
        requestTemplate.header("Cache-Control", "no-cache, no-store, must-revalidate");
        requestTemplate.header("Pragma", "no-cache");
        requestTemplate.header("Expires", "0");

        String userAgent = String.format("Maple-Leaf-Feign/1.0 (%s)", System.getProperty("os.name", "Unknown"));
        requestTemplate.header("User-Agent", userAgent);
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
            requestTemplate.header(headerName, headerValues);
            log.debug("Propagated annotated header [{}] to Feign request", headerName);
        }
    }
}
