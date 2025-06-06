package cn.maple.feign.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.feign.service.GXFeignService;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign请求拦截器
 * <p>
 * 该拦截器为所有通过Feign客户端发出的请求添加必要的HTTP头信息，包括API密钥和认证令牌。
 * 拦截器会自动从当前请求上下文中获取认证信息，实现微服务间的身份传递，确保调用链中的安全性。
 * </p>
 *
 * <p>
 * 功能特性：
 * 1. 自动为所有Feign请求添加API密钥，用于服务间的基础认证
 * 2. 支持从当前请求上下文中获取并传递认证令牌，实现用户身份在微服务间的传递
 * 3. 支持传递平台标识，便于服务端根据不同平台来源进行差异化处理
 * 4. 可通过配置文件自定义API密钥，避免硬编码
 * 5. 支持传递追踪ID(traceId)，便于分布式追踪和日志关联
 * 6. 线程安全设计，适用于高并发环境
 * </p>
 *
 * <p>
 * 使用示例：
 * 1. 确保在Spring配置类中注册了该拦截器：
 * ```java
 *
 * @author maple
 * @Bean public GXFeignRequestInterceptor feignRequestInterceptor() {
 * return new GXFeignRequestInterceptor();
 * }
 * ```
 * <p>
 * 2. 在Feign客户端接口上使用：
 * ```java
 * @FeignClient(name = "service-name", configuration = YourFeignConfig.class)
 * public interface YourServiceClient {
 * // 方法定义
 * }
 * ```
 * </p>
 */
@Slf4j
public class GXFeignRequestInterceptor implements RequestInterceptor {
    /**
     * 拦截并处理Feign请求
     * <p>
     * 该方法会在每个Feign请求发送前被调用，用于添加必要的HTTP头信息。
     * 主要添加以下头信息：
     * 1. X-API-KEY: 用于服务间的基础认证
     * 2. TOKEN_NAME: 用户认证令牌，从当前请求上下文中获取或生成
     * 3. PLATFORM: 用于标识请求来源的平台
     * 4. X-AUTH-TOKEN: 用于分布式追踪的traceId
     * 5. 其他配置的敏感头信息（可选）
     * </p>
     *
     * @param requestTemplate Feign请求模板，用于修改请求信息
     */
    @Override
    public void apply(RequestTemplate requestTemplate) {
        GXFeignService feignService = GXSpringContextUtils.getBean(GXFeignService.class);
        if (feignService == null) {
            log.warn("Failed to get FeignService instance, skipping header propagation");
            return;
        }

        // 获取并传递认证令牌
        String token = feignService.generateHttpAuthToken();
        if (StrUtil.isNotBlank(token)) {
            requestTemplate.header(GXCommonConstant.X_AUTH_TOKEN, token);
            log.debug("Propagated authentication token to Feign request");
        }

        // 获取并传递平台标识
        String platform = feignService.getPlatform();
        if (StrUtil.isNotBlank(platform)) {
            requestTemplate.header(GXTokenConstant.PLATFORM, platform);
            log.debug("Propagated platform identifier [{}] to Feign request", platform);
        }

        // 获取并传递traceId
        String traceId = feignService.getTraceId();
        if (StrUtil.isNotBlank(traceId)) {
            requestTemplate.header(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            log.debug("Propagated trace ID [{}] to Feign request", traceId);
        }
    }
}
