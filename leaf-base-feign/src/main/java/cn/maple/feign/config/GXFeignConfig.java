package cn.maple.feign.config;

import cn.maple.feign.codec.GXFeignCustomErrorDecoder;
import cn.maple.feign.interceptor.GXFeignRequestInterceptor;
import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign客户端全局配置类
 * <p>
 * 该配置类为Feign客户端提供全局设置，包括日志级别、错误解码器和请求拦截器。
 * 通过Spring的自动配置机制，这些Bean会被自动注册并应用于所有Feign客户端。
 * </p>
 *
 * <p>
 * 功能特性：
 * 1. 配置Feign客户端的日志级别为FULL，便于调试和问题排查
 * 2. 提供自定义错误解码器，针对特定HTTP状态码进行特殊处理
 * 3. 添加请求拦截器，为所有出站请求添加认证信息和其他必要的HTTP头
 * </p>
 *
 * <p>
 * 使用说明：
 * - 该配置类会被自动应用于所有使用@FeignClient注解的接口
 * - 可以在@FeignClient注解中通过configuration属性覆盖特定客户端的配置
 * - 日志级别设置为FULL时，确保将对应包的日志级别设置为DEBUG才能看到详细日志
 * </p>
 *
 * @author maple
 * @see GXFeignCustomErrorDecoder 自定义Feign错误解码器
 * @see GXFeignRequestInterceptor 自定义Feign请求拦截器
 */
@Configuration
public class GXFeignConfig {
    /**
     * 配置Feign客户端的日志级别
     * <p>
     * 日志级别说明：
     * - NONE：不记录任何日志（默认）
     * - BASIC：仅记录请求方法、URL、响应状态码及执行时间
     * - HEADERS：记录BASIC级别的基础上，记录请求和响应的头信息
     * - FULL：记录请求和响应的头信息、正文和元数据，最详细的日志级别
     * </p>
     * <p>
     * 注意：要使日志配置生效，还需要在日志配置文件中将对应的Feign客户端接口包设置为DEBUG级别
     * </p>
     *
     * @return Logger.Level 日志级别枚举
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * 配置自定义的Feign错误解码器
     * <p>
     * 错误解码器负责将HTTP错误响应转换为Java异常，便于统一的异常处理。
     * {@link GXFeignCustomErrorDecoder}实现了对404状态码的特殊处理，将其转换为业务异常。
     * </p>
     *
     * @return ErrorDecoder 自定义的错误解码器实例
     * @see GXFeignCustomErrorDecoder
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new GXFeignCustomErrorDecoder();
    }

    /**
     * 配置自定义的Feign请求拦截器
     * <p>
     * 请求拦截器可以在请求发送前修改请求，如添加认证信息、跟踪ID等通用头信息。
     * {@link GXFeignRequestInterceptor}实现了为所有请求添加API密钥的功能。
     * </p>
     *
     * @return RequestInterceptor 自定义的请求拦截器实例
     * @see GXFeignRequestInterceptor
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new GXFeignRequestInterceptor();
    }
}