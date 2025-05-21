package cn.maple.sso.web.interceptor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.annotation.GXIgnoreLoginIntercept;
import cn.maple.core.framework.annotation.GXWebClientAuthToken;
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
import java.util.concurrent.atomic.AtomicReference;

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
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在Spring配置中注册拦截器
 * // 自动通过@Component注解注册
 *
 * // 2. 配置URL白名单
 * // 在application.yml中配置
 * maple:
 *   sso:
 *     url-white-lists:
 *       - /api/public/**
 *       - /api/login
 *       - /api/register
 *
 * // 3. 在需要跳过登录验证的方法上添加注解
 * @GXIgnoreLoginIntercept
 * @GetMapping("/public-data")
 * public Result getPublicData() {
 *     // 处理逻辑
 * }
 *
 * // 4. 自定义未登录处理逻辑
 * @Bean
 * public GXSSOHandler customSsoHandler() {
 *     return new CustomSSOHandler();
 * }
 * </pre>
 * </p>
 *
 * @author britton
 * @since 2021-09-16
 */
@Component
@Slf4j
public class GXSSOAuthorizationInterceptor extends GXAuthorizationInterceptor {
    /**
     * SSO 处理器
     * <p>
     * 用于处理未登录情况下的请求响应
     * 可以通过setHandler方法自定义处理器
     * </p>
     * <p>
     * 使用AtomicReference确保线程安全，避免在高并发场景下可能出现的问题
     * </p>
     */
    private final AtomicReference<GXSSOHandler> handlerRef = new AtomicReference<>();

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
     * @param request  HTTP请求对象，不能为null
     * @param response HTTP响应对象，不能为null
     * @param handler  处理器对象，不能为null
     * @return 如果验证通过返回true，否则返回false
     * @throws Exception 如果处理过程中发生错误
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 快速检查是否需要跳过验证
        if (shouldSkipAuth(request, handler)) {
            return true;
        }

        // 2. 应用自定义拦截规则
        if (applyCustomInterceptorRules(request, response)) {
            return true;
        }

        // 3. 获取并验证Token
        Dict ssoToken = GXSSOHelperUtil.getSSOToken(request);

        // 4. 处理Token验证结果
        if (CollUtil.isEmpty(ssoToken)) {
            return handleEmptyToken(request, response);
        } else {
            // Token不为空，将Token存入请求属性中以减少二次解密
            request.setAttribute(GXSSOConstant.SSO_TOKEN_ATTR, ssoToken);
            return true; // 验证通过，放行请求
        }
    }

    /**
     * 检查是否应该跳过身份验证
     * <p>
     * 以下情况将跳过身份验证：
     * 1. OPTIONS请求（预检请求）
     * 2. 非HandlerMethod类型的处理器
     * 3. URL在白名单中
     * 4. 方法上有@GXIgnoreLoginIntercept注解
     * </p>
     *
     * @param request HTTP请求对象
     * @param handler 处理器对象
     * @return 如果应该跳过验证返回true，否则返回false
     */
    private boolean shouldSkipAuth(HttpServletRequest request, Object handler) {
        // OPTIONS请求或非HandlerMethod类型的处理器直接放行
        if (request.getMethod().equalsIgnoreCase("OPTIONS") || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 检查URL是否在白名单中
        String requestURI = request.getRequestURI();
        if (CollUtil.contains(urlWhiteListsConfigProperties.getWhiteLists(), requestURI)) {
            log.debug("URL在白名单中，跳过登录验证: {}", requestURI);
            return true;
        }

        // 检查方法是否有@GXIgnoreLoginIntercept注解
        GXIgnoreLoginIntercept ignoreLoginIntercept = handlerMethod.getMethodAnnotation(GXIgnoreLoginIntercept.class);
        if (Objects.nonNull(ignoreLoginIntercept)) {
            log.debug("方法有@GXIgnoreLoginIntercept注解，跳过登录验证: {}", handlerMethod.getMethod().getName());
            return true;
        }

        // 检查方法是否有@GXWebClientAuthToken注解
        GXWebClientAuthToken webClientAuthToken = handlerMethod.getMethodAnnotation(GXWebClientAuthToken.class);
        if (Objects.nonNull(webClientAuthToken)) {
            log.debug("方法有@GXWebClientAuthToken注解，跳过登录验证: {}", handlerMethod.getMethod().getName());
            return true;
        }

        return false;
    }

    /**
     * 应用自定义拦截规则
     * <p>
     * 如果存在GXAuthorizationInterceptorService的实现类，则调用其interceptor方法
     * 进行自定义拦截规则验证
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @return 如果自定义规则允许放行返回true，否则返回false
     */
    private boolean applyCustomInterceptorRules(HttpServletRequest request, HttpServletResponse response) {
        GXAuthorizationInterceptorService authorizationInterceptorService =
                GXSpringContextUtils.getBean(GXAuthorizationInterceptorService.class);
        if (Objects.nonNull(authorizationInterceptorService)) {
            boolean interceptor = authorizationInterceptorService.interceptor(request, response);
            if (interceptor) {
                log.debug("自定义拦截规则允许放行请求: {}", request.getRequestURI());
                return true;
            }
        }
        return false;
    }

    /**
     * 处理Token为空的情况
     * <p>
     * 根据请求类型（AJAX或普通HTTP请求）采用不同的处理策略
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @return 始终返回false，表示拦截请求
     * @throws Exception 如果处理过程中发生错误
     */
    private boolean handleEmptyToken(HttpServletRequest request, HttpServletResponse response) throws Exception {
        // 获取SSO缓存实例（如果存在）
        GXSSOCache ssoCache = GXSpringContextUtils.getBean(GXSSOCache.class);

        if (GXHttpUtil.isAjax(request)) {
            // 处理AJAX请求的未登录情况
            getHandler().preTokenIsNullAjax(request, response);

            // 清理缓存中的Token数据
            if (Objects.nonNull(ssoCache)) {
                ssoCache.delete(Dict.create());
            }
        } else {
            // 处理普通HTTP请求的未登录情况
            if (getHandler().preTokenIsNull(request, response)) {
                log.debug("用户未登录，请求URL: {}", request.getRequestURL());

                // 清理缓存中的Token数据
                if (Objects.nonNull(ssoCache)) {
                    ssoCache.delete(Dict.create());
                }

                // 清理登录状态并重定向到登录页面
                GXSSOHelperUtil.clearRedirectLogin(request, response);
            }
        }

        return false; // 拦截请求
    }

    /**
     * 获取SSO处理器
     * <p>
     * 如果未设置自定义处理器，则返回默认处理器
     * 使用AtomicReference确保线程安全的懒加载初始化
     * </p>
     *
     * @return SSO处理器，不会返回null
     */
    public GXSSOHandler getHandler() {
        GXSSOHandler handler = handlerRef.get();
        if (handler == null) {
            // 使用默认处理器作为初始值
            GXSSOHandler defaultHandler = GXSSODefaultHandler.getInstance();
            // 原子操作设置处理器，确保线程安全
            if (!handlerRef.compareAndSet(null, defaultHandler)) {
                // 如果其他线程已经设置了处理器，使用已设置的值
                handler = handlerRef.get();
            } else {
                handler = defaultHandler;
            }
        }
        return handler;
    }

    /**
     * 设置SSO处理器
     * <p>
     * 允许自定义未登录情况的处理逻辑
     * 使用AtomicReference确保线程安全的更新
     * </p>
     *
     * @param handler 自定义SSO处理器，不能为null
     * @throws NullPointerException 如果handler为null
     */
    public void setHandler(GXSSOHandler handler) {
        Objects.requireNonNull(handler, "SSO处理器不能为null");
        handlerRef.set(handler);
    }
}
