package cn.maple.sso.web.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.web.support.GXCustomerHandlerMethodArgumentResolver;
import cn.maple.sso.annotation.GXLoginUserAnnotation;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.dto.GXUserInfoDto;
import cn.maple.sso.service.GXUUserService;
import cn.maple.sso.utils.GXSSOHelperUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@ConditionalOnBean(value = {GXUUserService.class})
public class GXLoginUserHandlerMethodArgumentResolver implements GXCustomerHandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(GXLoginUserAnnotation.class) &&
                GXUserInfoDto.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest request, WebDataBinderFactory factory) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return null;
        }

        GXUUserService userService = GXSpringContextUtils.getBean(GXUUserService.class);
        if (userService == null) {
            return null;
        }

        Dict user = userService.getUserByUserId(userId);
        if (CollUtil.isEmpty(user)) {
            return null;
        }
        return JSONUtil.toBean(JSONUtil.toJsonStr(user), parameter.getParameterType());
    }

    private Long resolveUserId(NativeWebRequest request) {
        Object userId = request.getAttribute(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, RequestAttributes.SCOPE_REQUEST);
        if (userId == null) {
            Object ssoToken = request.getAttribute(GXSSOConstant.SSO_TOKEN_ATTR, RequestAttributes.SCOPE_REQUEST);
            if (ssoToken instanceof Dict tokenData) {
                userId = tokenData.getObj(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
            }
        }
        if (userId != null) {
            return Convert.toLong(userId);
        }

        return resolveUserIdFromCurrentToken(request);
    }

    private Long resolveUserIdFromCurrentToken(NativeWebRequest request) {
        if (request.getNativeRequest(jakarta.servlet.http.HttpServletRequest.class) instanceof jakarta.servlet.http.HttpServletRequest servletRequest) {
            Dict token = GXSSOHelperUtil.getSSOToken(servletRequest);
            if (CollUtil.isNotEmpty(token)) {
                request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, token, RequestAttributes.SCOPE_REQUEST);
                return Convert.toLong(token.getObj(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME));
            }
        }
        return null;
    }
}
