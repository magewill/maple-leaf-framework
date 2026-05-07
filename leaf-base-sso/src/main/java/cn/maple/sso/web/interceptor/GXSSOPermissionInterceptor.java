package cn.maple.sso.web.interceptor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.annotation.GXIgnoreLoginIntercept;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.web.interceptor.GXBaseSSOPermissionInterceptor;
import cn.maple.sso.annotation.GXPermissionAnnotation;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.enums.GXAction;
import cn.maple.sso.oauth.GXSSOAuthorization;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.utils.GXHttpUtil;
import cn.maple.sso.utils.GXSSOHelperUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

@Component
@Slf4j
public class GXSSOPermissionInterceptor extends GXBaseSSOPermissionInterceptor {
    private volatile String illegalUrl;

    private volatile boolean nothingAnnotationPass = true;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof HandlerMethod handlerMethod) {
            if (GXHandlerMethodAnnotationUtils.hasMergedAnnotation(handlerMethod, GXIgnoreLoginIntercept.class)) {
                return true;
            }

            GXPermissionAnnotation permission = GXHandlerMethodAnnotationUtils.findMergedAnnotation(handlerMethod, GXPermissionAnnotation.class);
            if (!requiresTokenForPermission(permission)) {
                return true;
            }

            Dict tokenDict = GXSSOHelperUtil.attrToken(request);
            if (CollUtil.isEmpty(tokenDict)) {
                tokenDict = GXSSOHelperUtil.getSSOToken(request);
                if (CollUtil.isEmpty(tokenDict)) {
                    return unauthorizedAccess(request, response);
                }
                request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, tokenDict);
            }

            if (isVerification(request, permission, tokenDict)) {
                return true;
            }

            return unauthorizedAccess(request, response);
        }

        return true;
    }

    private boolean requiresTokenForPermission(GXPermissionAnnotation permission) {
        if (permission == null) {
            return !this.isNothingAnnotationPass();
        }
        return permission.action() != GXAction.Skip;
    }

    protected boolean isVerification(HttpServletRequest request, GXPermissionAnnotation permission, Dict token) {
        if (GXSSOProperties.getInstance().isPermissionUri()) {
            String uri = request.getRequestURI();
            if (uri == null || this.getAuthorization().isPermitted(token, uri)) {
                return true;
            }
        }

        if (permission == null) {
            return this.isNothingAnnotationPass();
        }
        if (permission.action() == GXAction.Skip) {
            return true;
        }
        return CharSequenceUtil.isNotBlank(permission.value()) && this.getAuthorization().isPermitted(token, permission.value());
    }

    protected boolean unauthorizedAccess(HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.debug("Request is forbidden: {}", request.getRequestURI());

        if (GXHttpUtil.isAjax(request)) {
            GXHttpUtil.ajaxStatus(response, 403, "Forbidden");
        } else {
            if (this.getIllegalUrl() == null || "".equals(this.getIllegalUrl())) {
                response.sendError(403, "Forbidden");
            } else {
                response.sendRedirect(this.getIllegalUrl());
            }
        }

        return false;
    }

    protected GXSSOAuthorization getAuthorization() {
        GXSSOAuthorization authorization = GXSpringContextUtils.getBean(GXSSOAuthorization.class);
        if (authorization == null) {
            throw new GXBusinessException("GXSSOAuthorization implementation not found");
        }
        return authorization;
    }

    public String getIllegalUrl() {
        return illegalUrl;
    }

    public void setIllegalUrl(String illegalUrl) {
        this.illegalUrl = illegalUrl;
    }

    public boolean isNothingAnnotationPass() {
        return nothingAnnotationPass;
    }

    public void setNothingAnnotationPass(boolean nothingAnnotationPass) {
        this.nothingAnnotationPass = nothingAnnotationPass;
    }
}
