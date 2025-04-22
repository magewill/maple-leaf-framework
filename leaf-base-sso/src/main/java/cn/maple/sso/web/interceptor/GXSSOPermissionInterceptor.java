package cn.maple.sso.web.interceptor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.web.interceptor.GXBaseSSOPermissionInterceptor;
import cn.maple.sso.annotation.GXPermissionAnnotation;
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

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 权限拦截器（必须在 sso 拦截器之后执行）
 * <p>
 * 该拦截器负责对已登录用户的权限进行验证，主要功能包括：
 * 1. 基于URL的权限验证
 * 2. 基于注解的权限验证
 * 3. 处理无权限访问的情况
 * </p>
 * <p>
 * 安全说明：
 * - 支持多种权限验证策略
 * - 提供灵活的权限控制配置
 * - 对无权限访问进行统一处理
 * - 支持AJAX和普通HTTP请求的差异化处理
 * </p>
 *
 * @author britton britton@126.com
 * @since 2021-09-16
 */
@Component
@Slf4j
public class GXSSOPermissionInterceptor extends GXBaseSSOPermissionInterceptor {
    /**
     * 非法请求重定向URL
     * <p>
     * 当用户无权限访问某资源时，如果该值不为空，则重定向到此URL
     * 如果为空，则返回403错误码
     * </p>
     */
    private String illegalUrl;

    /**
     * 无注解情况下的权限控制策略
     * <p>
     * 当为true时，没有权限注解的方法默认放行
     * 当为false时，没有权限注解的方法默认拦截
     * </p>
     */
    private boolean nothingAnnotationPass = true;

    /**
     * 用户权限验证
     * <p>
     * 拦截器的主要入口方法，在Controller处理之前调用
     * 主要流程：
     * 1. 检查处理器是否为HandlerMethod类型
     * 2. 获取当前请求的Token
     * 3. 验证用户是否有权限访问
     * 4. 对无权限访问进行处理
     * </p>
     * <p>
     * 安全说明：
     * - 只对HandlerMethod类型的处理器进行权限验证
     * - 如果Token为空，默认放行（因为已经过登录拦截器验证）
     * - 通过isVerification方法进行具体的权限验证
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
        if (handler instanceof HandlerMethod) {
            // 获取当前请求的Token
            Dict tokenDict = GXSSOHelperUtil.attrToken(request);
            if (CollUtil.isEmpty(tokenDict)) {
                return true; // Token为空，已经过登录拦截器验证，此处放行
            }

            // 权限验证
            if (isVerification(request, handler, tokenDict)) {
                return true; // 权限验证通过
            }

            // 无权限访问处理
            return unauthorizedAccess(request, response);
        }

        return true; // 非HandlerMethod类型的处理器默认放行
    }

    /**
     * 判断权限是否合法
     * <p>
     * 支持两种权限验证方式：
     * 1. 基于请求URL的权限验证
     * 2. 基于方法注解的权限验证
     * </p>
     * <p>
     * 安全说明：
     * - URL权限验证依赖于GXSSOAuthorization接口的实现
     * - 注解权限验证支持Skip操作和具体权限值验证
     * - 无注解情况下的处理由nothingAnnotationPass属性控制
     * </p>
     *
     * @param request 请求对象
     * @param handler 处理器对象
     * @param token   用户Token
     * @return 如果权限验证通过返回true，否则返回false
     */
    protected boolean isVerification(HttpServletRequest request, Object handler, Dict token) {
        // URL 权限认证
        if (GXSSOProperties.getInstance().isPermissionUri()) {
            String uri = request.getRequestURI();
            if (uri == null || this.getAuthorization().isPermitted(token, uri)) {
                return true; // URL权限验证通过
            }
        }

        // 注解权限认证
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        GXPermissionAnnotation pm = method.getAnnotation(GXPermissionAnnotation.class);

        if (pm != null) {
            // 有注解的情况
            if (pm.action() == GXAction.Skip) {
                return true; // 注解指定跳过权限验证
            } else {
                // 验证具体权限值
                return !"".equals(pm.value()) && this.getAuthorization().isPermitted(token, pm.value());
            }
        } else {
            // 无注解情况下的处理
            return this.isNothingAnnotationPass();
        }
        // 非法访问
        // todo
    }

    /**
     * 无权限访问处理
     * <p>
     * 根据请求类型（AJAX或普通HTTP）提供不同的响应：
     * 1. AJAX请求返回403状态码和JSON错误信息
     * 2. 普通HTTP请求根据illegalUrl配置决定是返回403错误还是重定向
     * </p>
     * <p>
     * 安全说明：
     * - 对AJAX请求和普通HTTP请求进行差异化处理
     * - 提供统一的错误响应格式
     * - 支持配置重定向URL
     * </p>
     *
     * @param request  请求对象
     * @param response 响应对象
     * @return 始终返回false，表示拦截请求
     * @throws Exception 如果处理过程中发生错误
     */
    protected boolean unauthorizedAccess(HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.debug("请求无权限访问: {}", request.getRequestURI());

        if (GXHttpUtil.isAjax(request)) {
            // AJAX 请求返回403状态码和JSON错误信息
            GXHttpUtil.ajaxStatus(response, 403, "无权限访问该资源");
        } else {
            // 普通 HTTP 请求
            if (this.getIllegalUrl() == null || "".equals(this.getIllegalUrl())) {
                // 无重定向URL，直接返回403错误
                response.sendError(403, "无权限访问");
            } else {
                // 重定向到指定URL
                response.sendRedirect(this.getIllegalUrl());
            }
        }

        return false; // 拦截请求
    }

    /**
     * 获取权限验证实现类
     * <p>
     * 从Spring容器中获取GXSSOAuthorization接口的实现类
     * </p>
     * <p>
     * 安全说明：
     * - 确保权限验证实现类存在
     * - 提供明确的错误信息
     * </p>
     *
     * @return GXSSOAuthorization接口实现类
     * @throws GXBusinessException 当未找到实现类时抛出异常
     */
    public GXSSOAuthorization getAuthorization() {
        GXSSOAuthorization authorization = GXSpringContextUtils.getBean(GXSSOAuthorization.class);
        if (Objects.isNull(authorization)) {
            throw new GXBusinessException("请实现GXSSOAuthorization接口,并将其放入Spring容器中");
        }
        return authorization;
    }

    /**
     * 获取非法请求重定向URL
     *
     * @return 重定向URL
     */
    public String getIllegalUrl() {
        return illegalUrl;
    }

    /**
     * 设置非法请求重定向URL
     *
     * @param illegalUrl 重定向URL
     */
    public void setIllegalUrl(String illegalUrl) {
        this.illegalUrl = illegalUrl;
    }

    /**
     * 获取无注解情况下的权限控制策略
     *
     * @return 当为true时放行，为false时拦截
     */
    public boolean isNothingAnnotationPass() {
        return nothingAnnotationPass;
    }

    /**
     * 设置无注解情况下的权限控制策略
     *
     * @param nothingAnnotationPass 当为true时放行，为false时拦截
     */
    public void setNothingAnnotationPass(boolean nothingAnnotationPass) {
        this.nothingAnnotationPass = nothingAnnotationPass;
    }
}
