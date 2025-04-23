package cn.maple.core.framework.web.interceptor;

import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.service.GXRenewalTokenService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Token自动续期拦截器
 * <p>
 * 该拦截器用于实现Token的无感刷新机制，当用户Token即将过期时，
 * 系统会自动为其续期，避免用户因Token过期而需要重新登录。
 * 该功能需要前端配合，当前端收到响应头中包含Renewal-Token=renew时，
 * 应当从响应中获取新的Token并更新本地存储。
 * </p>
 * 
 * @author 塵子曦
 * @see GXRenewalTokenService 实际执行Token续期的服务接口
 * @see GXAuthorizationInterceptor 基础授权拦截器
 */
@Component
@Slf4j
public class GXRenewalTokenInterceptor extends GXAuthorizationInterceptor {
    /**
     * 请求预处理方法，在Controller处理请求前执行
     * <p>
     * 该方法通过Spring上下文获取GXRenewalTokenService的实现类，
     * 并调用其renewalToken方法判断当前Token是否需要续期。
     * 如果需要续期，则在响应头中添加Renewal-Token=renew标识，
     * 通知前端需要更新Token。
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理请求的方法对象
     * @return 是否继续执行后续拦截器和Controller，true表示继续，false表示中断
     * @throws Exception 处理过程中可能抛出的异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        GXRenewalTokenService renewalTokenService = GXSpringContextUtils.getBean(GXRenewalTokenService.class);
        if (ObjectUtil.isNotNull(renewalTokenService)) {
            boolean b = renewalTokenService.renewalToken();
            if (b) {
                response.setHeader("Renewal-Token", "renew");
            }
        }
        return super.preHandle(request, response, handler);
    }
}
