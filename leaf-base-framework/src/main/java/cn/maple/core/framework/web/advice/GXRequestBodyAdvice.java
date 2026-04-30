package cn.maple.core.framework.web.advice;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.service.GXRequestBodyAdviceService;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GXRequestBodyAdvice extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        GXRequestBodyAdviceService requestBodyAdviceService = GXSpringContextUtils.getBean(GXRequestBodyAdviceService.class);
        if (ObjectUtil.isNull(requestBodyAdviceService)) {
            return true;
        }
        return requestBodyAdviceService.supports(methodParameter, targetType, converterType);
    }

    @NotNull
    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        GXRequestBodyAdviceService requestBodyAdviceService = GXSpringContextUtils.getBean(GXRequestBodyAdviceService.class);
        Object jsonRequestBody = Objects.requireNonNull(GXCurrentRequestContextUtils.getHttpServletRequest()).getAttribute("JSON_REQUEST_BODY");
        if (ObjectUtil.isEmpty(jsonRequestBody)) {
            Objects.requireNonNull(GXCurrentRequestContextUtils.getHttpServletRequest()).setAttribute("JSON_REQUEST_BODY", JSONUtil.toJsonStr(body));
        }
        if (ObjectUtil.isNull(requestBodyAdviceService)) {
            return super.afterBodyRead(body, inputMessage, parameter, targetType, converterType);
        }
        return requestBodyAdviceService.afterBodyRead(body, inputMessage, parameter, targetType, converterType);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        GXRequestBodyAdviceService requestBodyAdviceService = GXSpringContextUtils.getBean(GXRequestBodyAdviceService.class);
        if (ObjectUtil.isNull(requestBodyAdviceService)) {
            return super.beforeBodyRead(inputMessage, parameter, targetType, converterType);
        }
        return requestBodyAdviceService.beforeBodyRead(inputMessage, parameter, targetType, converterType);
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        GXRequestBodyAdviceService requestBodyAdviceService = GXSpringContextUtils.getBean(GXRequestBodyAdviceService.class);
        if (ObjectUtil.isNull(requestBodyAdviceService)) {
            return super.handleEmptyBody(body, inputMessage, parameter, targetType, converterType);
        }
        return requestBodyAdviceService.handleEmptyBody(body, inputMessage, parameter, targetType, converterType);
    }
}