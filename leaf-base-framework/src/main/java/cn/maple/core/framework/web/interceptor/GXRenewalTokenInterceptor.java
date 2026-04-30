package cn.maple.core.framework.web.interceptor;

import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.service.GXRenewalTokenService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GXRenewalTokenInterceptor extends GXAuthorizationInterceptor {
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
