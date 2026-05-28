package cn.maple.core.framework.web.advice;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.service.GXRequestBodyAdviceService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.IOException;
import java.lang.reflect.Type;

@Slf4j
@RestControllerAdvice
public class GXRequestBodyAdvice extends RequestBodyAdviceAdapter {
    private static final String JSON_REQUEST_BODY_ATTRIBUTE = "JSON_REQUEST_BODY";

    private static final GXRequestBodyAdviceService DEFAULT_SERVICE = new GXRequestBodyAdviceService() {
    };

    private final ObjectProvider<@NonNull GXRequestBodyAdviceService> requestBodyAdviceServiceProvider;
    private final boolean captureAllRequestBodies;

    @Autowired
    public GXRequestBodyAdvice(ObjectProvider<@NonNull GXRequestBodyAdviceService> requestBodyAdviceServiceProvider) {
        this(requestBodyAdviceServiceProvider,
                GXCommonUtils.getEnvironmentValue("maple.framework.web.advice.capture-all-request-bodies", boolean.class, false));
    }

    GXRequestBodyAdvice(ObjectProvider<@NonNull GXRequestBodyAdviceService> requestBodyAdviceServiceProvider, boolean captureAllRequestBodies) {
        this.requestBodyAdviceServiceProvider = requestBodyAdviceServiceProvider;
        this.captureAllRequestBodies = captureAllRequestBodies;
    }

    @Override
    public boolean supports(@NonNull MethodParameter methodParameter, @NonNull Type targetType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        if (isCaptureAllRequestBodies()) {
            return true;
        }
        return getRequestBodyAdviceService().supports(methodParameter, targetType, converterType);
    }

    @NotNull
    @Override
    public Object afterBodyRead(@NonNull Object body, @NonNull HttpInputMessage inputMessage, @NonNull MethodParameter parameter, @NonNull Type targetType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        storeJsonRequestBodyIfAbsent(body, inputMessage);
        return getRequestBodyAdviceService().afterBodyRead(body, inputMessage, parameter, targetType, converterType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(@NonNull HttpInputMessage inputMessage, @NonNull MethodParameter parameter, @NonNull Type targetType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        return getRequestBodyAdviceService().beforeBodyRead(inputMessage, parameter, targetType, converterType);
    }

    @Override
    public Object handleEmptyBody(Object body, @NonNull HttpInputMessage inputMessage, @NonNull MethodParameter parameter, @NonNull Type targetType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return getRequestBodyAdviceService().handleEmptyBody(body, inputMessage, parameter, targetType, converterType);
    }

    private GXRequestBodyAdviceService getRequestBodyAdviceService() {
        GXRequestBodyAdviceService requestBodyAdviceService = requestBodyAdviceServiceProvider.getIfUnique();
        return ObjectUtil.defaultIfNull(requestBodyAdviceService, DEFAULT_SERVICE);
    }

    private boolean isCaptureAllRequestBodies() {
        return captureAllRequestBodies;
    }

    private void storeJsonRequestBodyIfAbsent(Object body, HttpInputMessage inputMessage) {
        HttpServletRequest request = getHttpServletRequest(inputMessage);
        if (request == null) {
            log.debug("No HTTP request context, skip storing JSON request body");
            return;
        }
        String jsonBody = JSONUtil.toJsonStr(body);
        setJsonRequestBodyIfAbsent(request, jsonBody);
        HttpServletRequest contextRequest = GXCurrentRequestContextUtils.getHttpServletRequest();
        if (contextRequest != null && contextRequest != request) {
            setJsonRequestBodyIfAbsent(contextRequest, jsonBody);
        }
    }

    private void setJsonRequestBodyIfAbsent(HttpServletRequest request, String jsonBody) {
        Object jsonRequestBody = request.getAttribute(JSON_REQUEST_BODY_ATTRIBUTE);
        if (ObjectUtil.isEmpty(jsonRequestBody)) {
            request.setAttribute(JSON_REQUEST_BODY_ATTRIBUTE, jsonBody);
        }
    }

    private HttpServletRequest getHttpServletRequest(HttpInputMessage inputMessage) {
        if (inputMessage instanceof ServletServerHttpRequest servletServerHttpRequest) {
            return servletServerHttpRequest.getServletRequest();
        }
        return GXCurrentRequestContextUtils.getHttpServletRequest();
    }
}
