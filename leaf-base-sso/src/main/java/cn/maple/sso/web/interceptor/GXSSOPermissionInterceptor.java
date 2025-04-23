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
 * <p>
 * 权限拦截器（必须在 SSO 拦截器之后执行）
 * </p>
 * 
 * <p>
 * 该拦截器负责对已登录用户的权限进行验证，是SSO系统权限控制的核心组件。
 * 主要功能包括：
 * 1. 基于URL的权限验证 - 通过请求路径判断用户是否有权限访问
 * 2. 基于注解的权限验证 - 通过方法上的@GXPermissionAnnotation注解控制访问权限
 * 3. 处理无权限访问的情况 - 返回403错误或重定向到指定页面
 * 4. 支持灵活的权限控制策略 - 可配置无注解方法的默认处理行为
 * </p>
 * 
 * <p>
 * 工作流程：
 * 1. 拦截请求并获取当前用户的Token信息
 * 2. 根据配置决定使用URL验证还是注解验证方式
 * 3. 调用GXSSOAuthorization接口进行具体的权限验证
 * 4. 对验证失败的请求进行统一处理
 * </p>
 * 
 * <p>
 * 安全特性：
 * - 多层次权限验证 - 同时支持URL和注解两种验证方式，提供更精细的权限控制
 * - 差异化响应处理 - 区分AJAX请求和普通HTTP请求，提供更友好的用户体验
 * - 可配置的权限策略 - 通过nothingAnnotationPass属性控制无注解方法的默认行为
 * - 统一的错误处理 - 对无权限访问提供一致的响应，增强系统安全性
 * - 与SSO系统无缝集成 - 利用Token中的用户信息进行权限判断，简化开发
 * </p>
 * 
 * <p>
 * 使用建议：
 * - 对安全要求高的接口，建议同时使用URL验证和注解验证
 * - 对公共接口，可以使用@GXPermissionAnnotation(action=GXAction.Skip)跳过验证
 * - 生产环境建议设置nothingAnnotationPass为false，采用白名单策略
 * - 建议配置illegalUrl，为无权限访问提供友好的错误页面
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
     * 当用户无权限访问某资源时，系统的处理方式：
     * - 如果该值不为空，则重定向到此URL，通常是一个"无权限访问"的提示页面
     * - 如果为空，则直接返回403错误码和错误信息
     * </p>
     * <p>
     * 建议配置此项，提供更友好的用户体验，特别是对于非AJAX请求
     * </p>
     */
    private String illegalUrl;

    /**
     * 无注解情况下的权限控制策略
     * <p>
     * 控制对没有添加@GXPermissionAnnotation注解的方法的默认处理行为：
     * - true: 默认放行，适用于大部分方法都不需要权限控制的场景（黑名单模式）
     * - false: 默认拦截，适用于大部分方法都需要权限控制的场景（白名单模式）
     * </p>
     * <p>
     * 安全建议：
     * - 开发环境可设置为true，便于调试
     * - 生产环境建议设置为false，采用白名单策略，更安全
     * </p>
     */
    private boolean nothingAnnotationPass = true;

    /**
     * 用户权限验证
     * <p>
     * 拦截器的主要入口方法，在Controller处理之前调用，负责权限验证的核心逻辑。
     * 主要流程：
     * 1. 检查处理器是否为HandlerMethod类型（只对Controller方法进行验证）
     * 2. 获取当前请求的Token，包含用户身份和权限信息
     * 3. 调用isVerification方法验证用户是否有权限访问
     * 4. 对无权限访问调用unauthorizedAccess方法进行处理
     * </p>
     * <p>
     * 特殊处理：
     * - 非HandlerMethod类型的处理器（如静态资源）默认放行
     * - Token为空的情况默认放行（因为已经过登录拦截器验证）
     * </p>
     * <p>
     * 安全说明：
     * - 与登录拦截器配合使用，确保已登录用户的权限控制
     * - 提供统一的权限验证入口，便于审计和监控
     * - 对验证失败的请求进行安全的处理，防止信息泄露
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象，通常是Controller中的方法
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
     * 根据系统配置和注解信息，选择合适的权限验证策略。
     * 支持两种权限验证方式：
     * 1. 基于请求URL的权限验证 - 通过GXSSOAuthorization接口验证用户是否有权限访问当前URL
     * 2. 基于方法注解的权限验证 - 检查方法上的@GXPermissionAnnotation注解并验证权限
     * </p>
     * <p>
     * 注解验证的处理逻辑：
     * - 如果注解的action为Skip，直接放行
     * - 如果注解指定了权限值，调用GXSSOAuthorization接口验证用户是否拥有该权限
     * - 如果方法没有注解，根据nothingAnnotationPass属性决定是否放行
     * </p>
     * <p>
     * 安全说明：
     * - URL权限验证依赖于GXSSOAuthorization接口的实现，支持自定义权限规则
     * - 注解权限验证支持细粒度的方法级权限控制
     * - 通过配置nothingAnnotationPass属性，可以实现黑名单或白名单模式
     * </p>
     *
     * @param request 请求对象
     * @param handler 处理器对象
     * @param token   用户Token，包含用户身份和权限信息
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
     * 当用户权限验证失败时，根据请求类型提供不同的响应处理：
     * 1. AJAX请求 - 返回403状态码和JSON格式的错误信息，便于前端处理
     * 2. 普通HTTP请求 - 根据illegalUrl配置决定是返回403错误还是重定向到指定页面
     * </p>
     * <p>
     * 实现细节：
     * - 使用GXHttpUtil.isAjax方法判断是否为AJAX请求
     * - 对AJAX请求返回JSON格式的错误信息
     * - 对普通HTTP请求，如果配置了illegalUrl则重定向，否则返回403错误
     * </p>
     * <p>
     * 安全说明：
     * - 对不同类型的请求提供差异化处理，提升用户体验
     * - 统一的错误响应格式，便于前端处理
     * - 支持配置重定向URL，可以提供更友好的错误页面
     * - 记录无权限访问日志，便于安全审计和问题排查
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
     * 从Spring容器中获取GXSSOAuthorization接口的实现类，用于执行具体的权限验证逻辑。
     * 如果容器中没有找到实现类，则抛出异常。
     * </p>
     * <p>
     * 实现说明：
     * - 使用GXSpringContextUtils工具类从Spring容器获取Bean
     * - 如果找不到实现类，抛出明确的异常信息
     * - 支持自定义GXSSOAuthorization实现，增强系统灵活性
     * </p>
     *
     * @return GXSSOAuthorization接口的实现类
     * @throws GXBusinessException 如果找不到GXSSOAuthorization的实现类
     */
    protected GXSSOAuthorization getAuthorization() {
        GXSSOAuthorization authorization = GXSpringContextUtils.getBean(GXSSOAuthorization.class);
        if (authorization == null) {
            throw new GXBusinessException("未找到GXSSOAuthorization接口的实现类");
        }
        return authorization;
    }

    /**
     * 获取非法请求重定向URL
     * <p>
     * 返回配置的无权限访问重定向URL，用于unauthorizedAccess方法中的重定向处理。
     * </p>
     *
     * @return 非法请求重定向URL
     */
    public String getIllegalUrl() {
        return illegalUrl;
    }

    /**
     * 设置非法请求重定向URL
     * <p>
     * 配置当用户无权限访问时的重定向URL，通常指向一个"无权限访问"的提示页面。
     * </p>
     * <p>
     * 使用建议：
     * - 建议在生产环境中配置此项，提供更友好的用户体验
     * - URL应当是一个静态页面或不需要权限验证的控制器方法
     * </p>
     *
     * @param illegalUrl 非法请求重定向URL
     */
    public void setIllegalUrl(String illegalUrl) {
        this.illegalUrl = illegalUrl;
    }

    /**
     * 获取无注解情况下的权限控制策略
     * <p>
     * 返回当前配置的nothingAnnotationPass值，用于isVerification方法中的权限判断。
     * </p>
     *
     * @return 无注解情况下是否放行
     */
    public boolean isNothingAnnotationPass() {
        return nothingAnnotationPass;
    }

    /**
     * 设置无注解情况下的权限控制策略
     * <p>
     * 配置对没有添加@GXPermissionAnnotation注解的方法的默认处理行为：
     * - true: 默认放行，适用于大部分方法都不需要权限控制的场景（黑名单模式）
     * - false: 默认拦截，适用于大部分方法都需要权限控制的场景（白名单模式）
     * </p>
     * <p>
     * 安全建议：
     * - 开发环境可设置为true，便于调试
     * - 生产环境建议设置为false，采用白名单策略，更安全
     * </p>
     *
     * @param nothingAnnotationPass 无注解情况下是否放行
     */
    public void setNothingAnnotationPass(boolean nothingAnnotationPass) {
        this.nothingAnnotationPass = nothingAnnotationPass;
    }
}
