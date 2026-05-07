package cn.maple.sso.web.interceptor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.annotation.GXHttpInvokerAuthToken;
import cn.maple.core.framework.annotation.GXIgnoreLoginIntercept;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.web.interceptor.GXAuthorizationInterceptor;
import cn.maple.sso.cache.GXSSOCache;
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
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class GXSSOAuthorizationInterceptor extends GXAuthorizationInterceptor {
    private final AtomicReference<GXSSOHandler> handlerRef = new AtomicReference<>();

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Resource
    private GXUrlWhiteListsConfigProperties urlWhiteListsConfigProperties;

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

        String requestURI = request.getRequestURI();
        if (isWhiteListUrl(requestURI)) {
            log.debug("Skip SSO login check for whitelist URL: {}", requestURI);
            return true;
        }

        if (GXHandlerMethodAnnotationUtils.hasMergedAnnotation(handlerMethod, GXIgnoreLoginIntercept.class)) {
            log.debug("Skip SSO login check for GXIgnoreLoginIntercept: {}", handlerMethod.getMethod().getName());
            return true;
        }

        if (GXHandlerMethodAnnotationUtils.hasMergedAnnotation(handlerMethod, GXHttpInvokerAuthToken.class)) {
            log.debug("Skip SSO login check for GXHttpInvokerAuthToken: {}", handlerMethod.getMethod().getName());
            return true;
        }

        return false;
    }

    private boolean isErrorDispatch(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ERROR
                || request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) != null;
    }

    private boolean isWhiteListUrl(String requestURI) {
        if (urlWhiteListsConfigProperties == null || CollUtil.isEmpty(urlWhiteListsConfigProperties.getWhiteLists())) {
            return false;
        }
        return urlWhiteListsConfigProperties.getWhiteLists().stream()
                .filter(Objects::nonNull)
                .anyMatch(pattern -> pathMatcher.match(pattern, requestURI));
    }

    private boolean applyCustomInterceptorRules(HttpServletRequest request, HttpServletResponse response) {
        GXAuthorizationInterceptorService authorizationInterceptorService =
                GXSpringContextUtils.getBean(GXAuthorizationInterceptorService.class);
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
        GXSSOCache ssoCache = GXSpringContextUtils.getBean(GXSSOCache.class);

        if (GXHttpUtil.isAjax(request)) {
            getHandler().preTokenIsNullAjax(request, response);

            if (Objects.nonNull(ssoCache)) {
                ssoCache.delete(Dict.create());
            }
        } else {
            if (getHandler().preTokenIsNull(request, response)) {
                log.debug("User is not logged in, request URL: {}", request.getRequestURL());

                if (Objects.nonNull(ssoCache)) {
                    ssoCache.delete(Dict.create());
                }

                GXSSOHelperUtil.clearRedirectLogin(request, response);
            }
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
