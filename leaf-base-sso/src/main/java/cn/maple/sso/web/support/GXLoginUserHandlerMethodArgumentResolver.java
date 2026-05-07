package cn.maple.sso.web.support;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.web.support.GXCustomerHandlerMethodArgumentResolver;
import cn.maple.sso.annotation.GXLoginUserAnnotation;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.dto.GXUserInfoDto;
import cn.maple.sso.service.GXUUserService;
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
        /*return parameter.getParameterType().getSuperclass().isAssignableFrom(GXUUserEntity.class)
                && parameter.hasParameterAnnotation(GXLoginUserAnnotation.class);*/
        return parameter.hasParameterAnnotation(GXLoginUserAnnotation.class) &&
                GXUserInfoDto.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest request, WebDataBinderFactory factory) throws Exception {
        Object object = request.getAttribute(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME, RequestAttributes.SCOPE_REQUEST);

        if (object == null) {
            Object ssoToken = request.getAttribute(GXSSOConstant.SSO_TOKEN_ATTR, RequestAttributes.SCOPE_REQUEST);
            if (ssoToken instanceof Dict tokenData) {
                object = tokenData.getObj(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
            }
        }

        if (object == null) {
            final String header = request.getHeader(GXTokenConstant.USER_TOKEN_NAME);
            if (null == header) {
                return null;
            }

            final Dict tokenData = JSONUtil.toBean(
                    GXAuthCodeUtils.authCodeDecode(header, GXTokenConstant.USER_TOKEN_SECRET_KEY),
                    Dict.class
            );

            object = tokenData.getObj(GXTokenConstant.TOKEN_USER_ID_FIELD_NAME);
            if (null == object) {
                return null;
            }
        }

        Long userId = Convert.toLong(object);
        if (userId == null) {
            return null;
        }

        GXUUserService userService = GXSpringContextUtils.getBean(GXUUserService.class);
        if (userService == null) {
            return null;
        }

        return userService.getUserByUserId(userId);
    }
}
