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

/**
 * 部署环境一致性验证拦截器
 * <p>
 * 该拦截器用于确保请求的环境与系统部署环境一致，防止跨环境调用导致的数据混乱或安全问题。
 * 工作原理是比较请求头中的环境标识、系统属性中的部署环境标识以及当前激活的Spring配置文件，
 * 三者必须完全一致才允许请求继续处理，否则返回403禁止访问错误。
 * </p>
 * <p>
 * 该功能可通过配置项"verify.deploy.environment"控制是否启用，默认为不启用。
 * 当启用时，系统会严格校验环境一致性，适用于多环境部署（如开发、测试、预发布、生产）的场景，
 * 有效防止开发环境的请求误发送到生产环境等情况。
 * </p>
 * 
 * @author 塵子曦
 * @see GXAuthorizationInterceptor 基础授权拦截器
 * @see GXCommonConstant#DEPLOY_REQUEST_ENV_HEADER_NAME 请求环境头名称常量
 * @see GXCommonConstant#DEPLOY_ENV_HEADER_NAME 部署环境系统属性名称常量
 */
@Component
public class GXVerifyDeployEnvironmentInterceptor extends GXAuthorizationInterceptor {
    /**
     * 请求预处理方法，在Controller处理请求前执行
     * <p>
     * 该方法调用verifyDeployEnvironmentConsistency方法验证请求环境与部署环境是否一致。
     * 如果环境不一致且验证功能已启用，则拒绝请求并返回403错误。
     * </p>
     *
     * @param request  HTTP请求对象，不能为null
     * @param response HTTP响应对象，不能为null
     * @param handler  处理请求的方法对象，不能为null
     * @return 是否继续执行后续拦截器和Controller，true表示继续，false表示中断
     * @throws IOException 响应写入过程中可能抛出的IO异常
     */
    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws IOException {
        return verifyDeployEnvironmentConsistency(request, response);
    }

    /**
     * 验证部署环境一致性
     * <p>
     * 该方法首先检查是否启用了环境验证功能，如未启用则直接放行。
     * 当启用验证时，会比较以下三个值：
     * 1. 请求头中的环境标识（通过DEPLOY_REQUEST_ENV_HEADER_NAME指定的请求头获取）
     * 2. 系统属性中的部署环境标识（通过DEPLOY_ENV_HEADER_NAME指定的系统属性获取）
     * 3. 当前激活的Spring配置文件
     * </p>
     * <p>
     * 三者必须完全一致才返回true允许请求继续，否则返回false并向客户端发送403错误响应。
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @return 环境是否一致，true表示一致或验证未启用，false表示不一致
     * @throws IOException 响应写入过程中可能抛出的IO异常
     */
    private boolean verifyDeployEnvironmentConsistency(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 是否需要验证部署环境 默认为不验证
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
