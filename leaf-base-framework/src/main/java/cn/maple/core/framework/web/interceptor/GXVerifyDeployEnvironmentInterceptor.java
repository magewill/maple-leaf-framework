package cn.maple.core.framework.web.interceptor;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.SystemPropsUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.util.GXCommonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GXVerifyDeployEnvironmentInterceptor extends GXAuthorizationInterceptor {
    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws IOException {
        return verifyDeployEnvironmentConsistency(request, response);
    }

    private boolean verifyDeployEnvironmentConsistency(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean verifyDeployEnvironmentValue = GXCommonUtils.getEnvironmentValue("verify.deploy.environment", boolean.class, false);
        if (!verifyDeployEnvironmentValue) {
            return true;
        }
        String requestEnvValue = request.getHeader(GXCommonConstant.DEPLOY_REQUEST_ENV_HEADER_NAME);
        String deployEnvValue = SystemPropsUtil.get(GXCommonConstant.DEPLOY_ENV_HEADER_NAME);
        String currentActiveProfile = GXCommonUtils.getActiveProfile();
        if (!CharSequenceUtil.equals(requestEnvValue, deployEnvValue) || !CharSequenceUtil.equals(deployEnvValue, currentActiveProfile)) {
            Dict data = Dict.create().set("code", HttpStatus.HTTP_FORBIDDEN).set("msg", "请求环境不一致!").set("data", null);
            JSONConfig jsonConfig = new JSONConfig();
            jsonConfig.setIgnoreNullValue(false);
            response.setContentType("application/json;charset=UTF-8");
            response.addIntHeader("Allow", HttpStatus.HTTP_FORBIDDEN);
            response.getWriter().write(JSONUtil.toJsonStr(data, jsonConfig));
            return false;
        }
        return true;
    }
}
