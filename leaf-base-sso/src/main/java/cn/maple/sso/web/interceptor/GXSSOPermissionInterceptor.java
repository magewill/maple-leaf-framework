package cn.maple.sso.web.interceptor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.annotation.GXIgnoreLoginIntercept;
import cn.maple.core.framework.exception.GXBusinessException;

import cn.maple.core.framework.web.interceptor.GXBaseSSOPermissionInterceptor;
import cn.maple.sso.annotation.GXPermissionAnnotation;
import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.enums.GXAction;
import cn.maple.sso.metrics.GXSSOMetrics;
import cn.maple.sso.oauth.GXSSOAuthorization;
import cn.maple.sso.properties.GXSSOProperties;
import cn.maple.sso.utils.GXHttpUtil;
import cn.maple.sso.utils.GXSSOHelperUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Permission interceptor.
 *
 * <p>The decision chain is explicit and single pass:</p>
 * <ol>
 *   <li>{@link GXIgnoreLoginIntercept} - allow</li>
 *   <li>{@code @GXPermissionAnnotation(action = Skip)} - allow</li>
 *   <li>URI mode enabled - decide by {@code isPermitted(token, uri)} and stop</li>
 *   <li>annotation with a non blank value - decide by {@code isPermitted(token, value)}</li>
 *   <li>no annotation - decided by {@code nothingAnnotationPass}</li>
 * </ol>
 */
@Component
@Slf4j
@Order(GXSSOConstant.PERMISSION_ORDER)
public class GXSSOPermissionInterceptor extends GXBaseSSOPermissionInterceptor {
    private static final int FORBIDDEN_STATUS = 403;

    /**
     * Explicit overrides; {@code null} means "read from {@code sso.config.*}" (see
     * {@link GXSSOProperties#getIllegalUrl()} / {@link GXSSOProperties#isNothingAnnotationPass()}).
     */
    private volatile @Nullable String illegalUrlOverride;

    private volatile @Nullable Boolean nothingAnnotationPassOverride;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (GXHandlerMethodAnnotationUtils.hasIgnoreAnnotation(handlerMethod)) {
            return true;
        }

        GXHandlerMethodAnnotationUtils.PermissionInfo permission = GXHandlerMethodAnnotationUtils.permissionInfo(handlerMethod);
        if (permission.present() && permission.action() == GXAction.Skip) {
            return true;
        }

        boolean uriMode = GXSSOProperties.getInstance().isPermissionUri();
        boolean annotationMode = CharSequenceUtil.isNotBlank(permission.value());
        if (!uriMode && !annotationMode && this.isNothingAnnotationPass()) {
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

    /**
     * Single decision point for URI mode and annotation mode.
     *
     * @param request current request
     * @param permission annotation found on the handler, may be {@code null}
     * @param token validated token
     * @return {@code true} when the request is permitted
     */
    protected boolean isVerification(HttpServletRequest request, GXHandlerMethodAnnotationUtils.PermissionInfo permission,
                                     Dict token) {
        if (GXSSOProperties.getInstance().isPermissionUri()) {
            String uri = request.getRequestURI();
            if (CharSequenceUtil.isBlank(uri)) {
                log.debug("event=sso.permission.denied reason=blank-uri");
                return false;
            }
            return this.getAuthorization().isPermitted(token, uri);
        }

        if (!permission.present() || permission.action() == GXAction.Skip) {
            return this.isNothingAnnotationPass();
        }
        if (CharSequenceUtil.isBlank(permission.value())) {
            return this.isNothingAnnotationPass();
        }
        return this.getAuthorization().isPermitted(token, permission.value());
    }

    protected boolean unauthorizedAccess(HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.debug("event=sso.permission.denied uri={}", request.getRequestURI());
        GXSSOMetrics.getInstance().incrementDenied();

        if (GXHttpUtil.isAjax(request)) {
            GXHttpUtil.ajaxStatus(response, FORBIDDEN_STATUS, "Forbidden");
        } else {
            String redirectUrl = this.getIllegalUrl();
            if (CharSequenceUtil.isBlank(redirectUrl)) {
                response.sendError(FORBIDDEN_STATUS, "Forbidden");
            } else {
                response.sendRedirect(redirectUrl);
            }
        }
        return false;
    }

    protected GXSSOAuthorization getAuthorization() {
        GXSSOAuthorization authorization = GXSSOHelperUtil.bean(GXSSOAuthorization.class);
        if (authorization == null) {
            throw new GXBusinessException("GXSSOAuthorization implementation not found");
        }
        return authorization;
    }

    /**
     * Illegal-url override, falling back to {@code sso.config.illegal-url}.
     *
     * @return redirect target, may be {@code null}
     */
    public @Nullable String getIllegalUrl() {
        String explicit = illegalUrlOverride;
        return explicit != null ? explicit : GXSSOProperties.getInstance().getIllegalUrl();
    }

    public void setIllegalUrl(String illegalUrl) {
        this.illegalUrlOverride = illegalUrl;
    }

    /**
     * Whether handlers without a permission annotation pass, falling back to
     * {@code sso.config.nothing-annotation-pass}.
     *
     * @return {@code true} when no-annotation handlers are allowed
     */
    public boolean isNothingAnnotationPass() {
        Boolean explicit = nothingAnnotationPassOverride;
        return explicit != null ? explicit : GXSSOProperties.getInstance().isNothingAnnotationPass();
    }

    public void setNothingAnnotationPass(boolean nothingAnnotationPass) {
        this.nothingAnnotationPassOverride = nothingAnnotationPass;
    }
}
