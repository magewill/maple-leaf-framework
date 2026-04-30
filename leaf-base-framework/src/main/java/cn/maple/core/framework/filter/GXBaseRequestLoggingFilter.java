package cn.maple.core.framework.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import java.io.IOException;
import java.util.Optional;

public class GXBaseRequestLoggingFilter extends AbstractRequestLoggingFilter {
    @Override
    protected void beforeRequest(HttpServletRequest request, @NotNull String message) {
    }

    @Override
    protected void afterRequest(@NotNull HttpServletRequest request, @NotNull String message) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String originalTraceId = GXTraceIdContextUtils.getNullableTraceId();
        String traceId = resolveTraceId(request, originalTraceId);
        GXTraceIdContextUtils.putTraceId(traceId);
        request.setAttribute(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
        response.setHeader(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);

        String headerName = "X-Request-Start-Time";
        String requestStartTimeHeader = request.getHeader(headerName);

        if (CharSequenceUtil.isNotBlank(requestStartTimeHeader)) {
            response.setHeader(headerName, requestStartTimeHeader);
        }

        try {
            super.doFilterInternal(request, response, filterChain);
        } finally {
            GXTraceIdContextUtils.restoreTraceId(originalTraceId);
        }
    }

    private String resolveTraceId(HttpServletRequest request, String originalTraceId) {
        String traceId = Optional.ofNullable(request.getHeader(GXTraceIdContextUtils.TRACE_ID_KEY))
                .filter(CharSequenceUtil::isNotBlank)
                .orElse(originalTraceId);
        if (CharSequenceUtil.isBlank(traceId)) {
            return GXTraceIdContextUtils.generateTraceId();
        }
        return traceId;
    }
}
