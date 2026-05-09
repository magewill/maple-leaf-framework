package cn.maple.core.framework.web.interceptor;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.service.GXRenewalTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GXRenewalTokenInterceptor extends GXAuthorizationInterceptor {
    private static final String RENEWAL_TOKEN_HEADER = "Renewal-Token";

    private final ObjectProvider<GXRenewalTokenService> renewalTokenServiceProvider;

    public GXRenewalTokenInterceptor(ObjectProvider<GXRenewalTokenService> renewalTokenServiceProvider) {
        this.renewalTokenServiceProvider = renewalTokenServiceProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!super.preHandle(request, response, handler)) {
            return false;
        }
        GXRenewalTokenService renewalTokenService = renewalTokenServiceProvider.getIfUnique();
        if (ObjectUtil.isNotNull(renewalTokenService)) {
            boolean shouldRenew = renewalTokenService.renewalToken();
            if (shouldRenew) {
                String renewalToken = renewalTokenService.renewalTokenHeaderValue(Dict.create());
                response.setHeader(RENEWAL_TOKEN_HEADER,
                        CharSequenceUtil.isBlank(renewalToken) ? GXRenewalTokenService.RENEWAL_TOKEN_MARKER : renewalToken);
            }
        }
        return true;
    }
}
