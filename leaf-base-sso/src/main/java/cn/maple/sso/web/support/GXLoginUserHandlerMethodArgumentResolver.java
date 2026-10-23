package cn.maple.sso.web.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.exception.GXBusinessException;

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

    /**
     * Resolve the current login user for a {@code @GXLoginUserAnnotation} parameter.
     *
     * <p>Failure semantics are single valued: a missing token, an unresolvable user id and a
     * missing user record all raise {@link GXBusinessException}. Returning {@code null} would
     * hand a {@code null} to a non nullable controller parameter (FR-017).</p>
     *
     * @param parameter controller parameter carrying the annotation
     * @param container model and view container
     * @param request current request
     * @param factory data binder factory
     * @return the resolved DTO instance, never {@code null}
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest request, WebDataBinderFactory factory) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            throw new GXBusinessException("SSO token is absent, cannot resolve @GXLoginUserAnnotation parameter.");
        }

        GXUUserService userService = GXSSOHelperUtil.bean(GXUUserService.class);
        if (userService == null) {
            throw new GXBusinessException("GXUUserService implementation not found.");
        }

        Dict user = userService.getUserByUserId(userId);
        if (CollUtil.isEmpty(user)) {
            throw new GXBusinessException("SSO user not found, userId=" + userId);
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
