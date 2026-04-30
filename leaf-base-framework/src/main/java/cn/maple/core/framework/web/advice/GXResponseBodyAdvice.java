package cn.maple.core.framework.web.advice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.service.GXResponseBodyAdviceService;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.web.server.Cookie;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

@Log4j2
@RestControllerAdvice
public class GXResponseBodyAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        GXResponseBodyAdviceService responseBodyAdviceService = GXSpringContextUtils.getBean(GXResponseBodyAdviceService.class);
        if (ObjectUtil.isNotNull(responseBodyAdviceService)) {
            return responseBodyAdviceService.supports(returnType, converterType);
        }
        return returnType.getParameterType().isAssignableFrom(GXResultUtils.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        log.debug("响应拦截成功!");
        boolean allowCredentials = GXCommonUtils.getEnvironmentValue("cors.allow.credentials", boolean.class, false);
        if (allowCredentials) {
            List<String> cookies = buildCookies();
            cookies.forEach(cookie -> response.getHeaders().add(HttpHeaders.SET_COOKIE, cookie));
        }
        GXResponseBodyAdviceService responseBodyAdviceService = GXSpringContextUtils.getBean(GXResponseBodyAdviceService.class);
        if (ObjectUtil.isNotNull(responseBodyAdviceService)) {
            return responseBodyAdviceService.beforeBodyWrite(body, returnType, selectedContentType, selectedConverterType, request, response);
        }
        return body;
    }

    private List<String> buildCookies() {
        GXResponseBodyAdviceService responseBodyAdviceService = GXSpringContextUtils.getBean(GXResponseBodyAdviceService.class);
        if (ObjectUtil.isNotNull(responseBodyAdviceService)) {
            return responseBodyAdviceService.buildCookies();
        }
        return defaultBuildCookies();
    }

    private List<String> defaultBuildCookies() {
        boolean isSecure = GXCommonUtils.getEnvironmentValue("cors.cookie.secure", boolean.class, false);
        String cookieSecret = GXCommonUtils.getEnvironmentValue("cors.cookie.secret", String.class, "CF3417E3CF6B0F28");
        Integer effectiveDuration = GXCommonUtils.getEnvironmentValue("cors.cookie.duration", Integer.class, 300);
        String cookieVirusID = GXCommonUtils.getEnvironmentValue("cors.cookie.virusId", String.class);
        if (CharSequenceUtil.isNotBlank(cookieVirusID)) {
            String authCode = GXAuthCodeUtils.authCodeEncode(cookieVirusID, cookieSecret, effectiveDuration);
            ResponseCookie cookie = ResponseCookie.from("UserData", authCode)
                    .maxAge(-1)
                    .secure(isSecure)
                    .httpOnly(true)
                    //.domain(null)
                    //.path(null)
                    .sameSite(Cookie.SameSite.LAX.attributeValue())
                    .build();
            return CollUtil.newArrayList(cookie.toString());
        }
        return CollUtil.newArrayList();
    }
}