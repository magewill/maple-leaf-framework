package cn.maple.core.framework.service;

import cn.hutool.core.collection.CollUtil;
import cn.maple.core.framework.util.GXResultUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.util.List;

public interface GXResponseBodyAdviceService {
    default boolean supports(MethodParameter returnType, Class<?> converterType) {
        return returnType.getParameterType().isAssignableFrom(GXResultUtils.class);
    }

    default Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        return body;
    }

    default List<String> buildCookies() {
        return CollUtil.newArrayList();
    }
}
