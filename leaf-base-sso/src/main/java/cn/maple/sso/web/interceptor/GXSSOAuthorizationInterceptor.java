package cn.maple.sso.web.interceptor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.Objects;

/**
 * 前端用户登录验证拦截器
 * <p>
 * 该拦截器负责验证用户是否已登录，主要功能包括：
 * 1. 白名单URL直接放行
 * 2. 带有@GXIgnoreLoginIntercept注解的方法直接放行
 * 3. 支持自定义拦截规则
 * 4. 处理未登录用户的请求（AJAX和普通HTTP请求）
 * </p>
 * <p>
 * 安全说明：
 * - 支持URL白名单配置
 * - 支持方法级别的登录豁免
 * - 对未登录用户提供友好的响应
 * - 支持Token的缓存管理
 * </p>
 */
@Component
@Slf4j
@SuppressWarnings("all")
public class GXSSOAuthorizationInterceptor extends GXAuthorizationInterceptor {
    /**
     * SSO 处理器
     * <p>
     * 用于处理未登录情况下的请求响应
     * 可以通过setHandler方法自定义处理器
     * </p>
     */
    private GXSSOHandler handler;

    /**
     * URL白名单配置属性
     * <p>
     * 包含不需要登录验证的URL列表
     * </p>
     */
    @Resource
    private GXUrlWhiteListsConfigProperties urlWhiteListsConfigProperties;

    /**
     * 登录验证拦截处理
     * <p>
     * 拦截器的主要入口方法，在Controller处理之前调用
     * 主要流程：
     * 1. 检查是否需要跳过登录验证（OPTIONS请求、非HandlerMethod、白名单URL、带注解的方法）
     * 2. 应用自定义拦截规则
     * 3. 获取并验证Token
     * 4. 处理未登录情况
     * </p>
     * <p>
     * 安全说明：
     * - 多层验证确保安全性
     * - 对未登录用户提供友好的响应
     * - 支持AJAX和普通HTTP请求的差异化处理
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @return 如果验证通过返回true，否则返回false
     * @throws Exception 如果处理过程中发生错误
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        GXIgnoreLoginIntercept ignoreLoginIntercept;
        if (request.getMethod().equalsIgnoreCase("OPTIONS") || !(handler instanceof HandlerMethod)) {
            return true; // OPTIONS请求或非HandlerMethod类型的处理器直接放行
        }

        // 检查URL是否在白名单中
        String requestURI = request.getRequestURI();
        if (CollUtil.contains(urlWhiteListsConfigProperties.getWhiteLists(), requestURI)) {
            // 白名单URL直接放行
            return true;
        }

        // 检查方法是否有@GXIgnoreLoginIntercept注解
        ignoreLoginIntercept = ((HandlerMethod) handler).getMethodAnnotation(GXIgnoreLoginIntercept.class);
        if (Objects.nonNull(ignoreLoginIntercept)) {
            // 有@GXIgnoreLoginIntercept注解的方法直接放行
            return true;
        }

        // 应用自定义拦截规则
        GXAuthorizationInterceptorService authorizationInterceptorService = GXSpringContextUtils.getBean(GXAuthorizationInterceptorService.class);
        if (Objects.nonNull(authorizationInterceptorService)) {
            boolean interceptor = authorizationInterceptorService.interceptor(request, response);
            if (interceptor) {
                return interceptor; // 自定义拦截规则允许放行
            }
        }

        // 获取Token
        Dict ssoToken = GXSSOHelperUtil.getSSOToken(request);

        // 判断Token是否为空
        if (CollUtil.isEmpty(ssoToken)) {
            if (GXHttpUtil.isAjax(request)) {
                // 处理AJAX请求的未登录情况
                getHandler().preTokenIsNullAjax(request, response);

                // 清理缓存中的Token数据
                GXSSOCache ssoCache = GXSpringContextUtils.getBean(GXSSOCache.class);
                if (Objects.nonNull(ssoCache)) {
                    ssoCache.delete(Dict.create());
                }
                return false; // 拦截请求
            } else {
                // 处理普通HTTP请求的未登录情况
                if (getHandler().preTokenIsNull(request, response)) {
                    log.debug("用户未登录，请求URL: " + request.getRequestURL());

                    // 清理缓存中的Token数据
                    GXSSOCache ssoCache = GXSpringContextUtils.getBean(GXSSOCache.class);
                    if (Objects.nonNull(ssoCache)) {
                        ssoCache.delete(Dict.create());
                    }

                    // 清理登录状态并重定向到登录页面
                    GXSSOHelperUtil.clearRedirectLogin(request, response);
                }
                return false; // 拦截请求
            }
        } else {
            // Token不为空，将Token存入请求属性中以减少二次解密
            request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, ssoToken);
        }

        return true; // 验证通过，放行请求
    }

    /**
     * 获取SSO处理器
     * <p>
     * 如果未设置自定义处理器，则返回默认处理器
     * </p>
     *
     * @return SSO处理器
     */
    public GXSSOHandler getHandler() {
        if (handler == null) {
            return GXSSODefaultHandler.getInstance();
        }
        return handler;
    }

    /**
     * 设置SSO处理器
     * <p>
     * 允许自定义未登录情况的处理逻辑
     * </p>
     *
     * @param handler 自定义SSO处理器
     */
    public void setHandler(GXSSOHandler handler) {
        this.handler = handler;
    }
}
