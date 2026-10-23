package cn.maple.sso.web.interceptor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.annotation.GXIgnoreLoginIntercept;

import cn.maple.core.framework.web.interceptor.GXAuthorizationInterceptor;

import cn.maple.sso.constant.GXSSOConstant;
import cn.maple.sso.properties.GXUrlWhiteListsConfigProperties;
import cn.maple.sso.service.GXAuthorizationInterceptorService;
import cn.maple.sso.utils.GXHttpUtil;
import cn.maple.sso.utils.GXSSOHelperUtil;
import cn.maple.sso.web.handler.GXSSODefaultHandler;
import cn.maple.sso.web.handler.GXSSOHandler;
import jakarta.annotation.Resource;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.util.UrlPathHelper;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Login interceptor.
 *
 * <p>An anonymous request is answered without any side effect: no cache delete, no plugin
 * callback and no cookie cleanup.</p>
 */
@Component
@Slf4j
@Order(GXSSOConstant.AUTHORIZATION_ORDER)
public class GXSSOAuthorizationInterceptor extends GXAuthorizationInterceptor {
    private final AtomicReference<GXSSOHandler> handlerRef = new AtomicReference<>();

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Resource
    private GXUrlWhiteListsConfigProperties urlWhiteListsConfigProperties;

    /**
     * Test seam for the whitelist configuration.
     *
     * @param urlWhiteListsConfigProperties whitelist configuration, may be {@code null}
     */
    void setUrlWhiteListsConfigProperties(GXUrlWhiteListsConfigProperties urlWhiteListsConfigProperties) {
        this.urlWhiteListsConfigProperties = urlWhiteListsConfigProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (shouldSkipAuth(request, handler)) {
            return true;
        }

        if (applyCustomInterceptorRules(request, response)) {
            return true;
        }

        Dict ssoToken = GXSSOHelperUtil.getSSOToken(request);
        if (CollUtil.isEmpty(ssoToken)) {
            return handleEmptyToken(request, response);
        }

        request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, ssoToken);
        return true;
    }

    private boolean shouldSkipAuth(HttpServletRequest request, Object handler) {
        if (isErrorDispatch(request)) {
            log.debug("Skip SSO login check for error dispatch: {}", request.getRequestURI());
            return true;
        }

        if (request.getMethod().equalsIgnoreCase("OPTIONS") || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (isWhiteListUrl(request)) {
            log.debug("Skip SSO login check for whitelist URL: {}", request.getRequestURI());
            return true;
        }

        if (GXHandlerMethodAnnotationUtils.hasIgnoreAnnotation(handlerMethod)) {
            log.debug("Skip SSO login check for GXIgnoreLoginIntercept: {}", handlerMethod.getMethod().getName());
            return true;
        }

        if (GXHandlerMethodAnnotationUtils.hasHttpInvokerTokenAnnotation(handlerMethod)) {
            log.debug("Skip SSO login check for GXHttpInvokerAuthToken: {}", handlerMethod.getMethod().getName());
            return true;
        }

        return false;
    }

    private boolean isErrorDispatch(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ERROR
                || request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) != null;
    }

    private boolean isWhiteListUrl(HttpServletRequest request) {
        if (urlWhiteListsConfigProperties == null || CollUtil.isEmpty(urlWhiteListsConfigProperties.getWhiteLists())) {
            return false;
        }

        String pathWithinApplication = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        String requestUri = request.getRequestURI();

        return urlWhiteListsConfigProperties.getWhiteLists().stream()
                .filter(pattern -> CharSequenceUtil.isNotBlank(pattern))
                .anyMatch(pattern -> pathMatcher.match(pattern, pathWithinApplication)
                        || pathMatcher.match(pattern, requestUri));
    }

    private boolean applyCustomInterceptorRules(HttpServletRequest request, HttpServletResponse response) {
        GXAuthorizationInterceptorService authorizationInterceptorService =
                GXSSOHelperUtil.bean(GXAuthorizationInterceptorService.class);
        if (Objects.nonNull(authorizationInterceptorService)) {
            boolean interceptor = authorizationInterceptorService.interceptor(request, response);
            if (interceptor) {
                log.debug("Custom SSO interceptor allows request: {}", request.getRequestURI());
                return true;
            }
        }
        return false;
    }

    private boolean handleEmptyToken(HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.debug("event=sso.token.absent uri={}", request.getRequestURI());

        if (GXHttpUtil.isAjax(request)) {
            getHandler().preTokenIsNullAjax(request, response);
        } else if (getHandler().preTokenIsNull(request, response)) {
            log.debug("User is not logged in, request URL: {}", request.getRequestURL());
            GXSSOHelperUtil.clearRedirectLogin(request, response);
        }

        return false;
    }

    public GXSSOHandler getHandler() {
        GXSSOHandler handler = handlerRef.get();
        if (handler == null) {
            GXSSOHandler defaultHandler = GXSSODefaultHandler.getInstance();
            if (!handlerRef.compareAndSet(null, defaultHandler)) {
                handler = handlerRef.get();
            } else {
                handler = defaultHandler;
            }
        }
        return handler;
    }

    public void setHandler(GXSSOHandler handler) {
        Objects.requireNonNull(handler, "SSO handler must not be null");
        handlerRef.set(handler);
    }
}
