package cn.maple.core.framework.service;

import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.TypeUtil;
import cn.maple.core.framework.dto.protocol.req.GXBaseReqProtocol;
import cn.maple.core.framework.util.GXCommonUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;

import java.io.IOException;
import java.lang.reflect.Type;

public interface GXRequestBodyAdviceService {
    String BEFORE_REPAIR_METHOD = "beforeRepair";

    String VERIFY_METHOD = "verify";

    String AFTER_REPAIR_METHOD = "afterRepair";

    default boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ClassUtil.isAssignable(GXBaseReqProtocol.class, TypeUtil.getClass(targetType));
    }

    default Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        try {
            GXCommonUtils.reflectCallObjectMethod(body, BEFORE_REPAIR_METHOD);
            GXCommonUtils.reflectCallObjectMethod(body, VERIFY_METHOD);
            GXCommonUtils.reflectCallObjectMethod(body, AFTER_REPAIR_METHOD);
        } catch (Exception ignored) {
        }
        return body;
    }

    default HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        return inputMessage;
    }

    default Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
