package cn.maple.core.framework.web.advice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.framework.service.GXResponseBodyAdviceService;
import cn.maple.core.framework.util.GXAuthCodeUtils;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.web.server.Cookie;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

/**
 * 响应体处理增强类，用于拦截和处理HTTP响应体
 * <p>
 * 该类通过Spring的ResponseBodyAdvice机制，在响应体被写入之前提供拦截点，
 * 允许对响应体进行处理和修改。主要功能包括：
 * <ul>
 *   <li>响应体的处理和转换</li>
 *   <li>添加Cookie到HTTP响应头</li>
 *   <li>委托给GXResponseBodyAdviceService进行实际业务处理</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 该类实现了ResponseBodyAdvice接口，通过重写其方法来实现响应拦截和处理。
 * 通过@RestControllerAdvice注解，使其对所有RestController的响应生效。
 * </p>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>统一响应格式处理：将不同格式的返回值统一转换为标准响应格式</li>
 *   <li>响应数据加密：对敏感数据进行加密处理</li>
 *   <li>添加通用响应头：如跨域头、安全头等</li>
 *   <li>响应数据脱敏：对手机号、身份证等敏感信息进行脱敏</li>
 *   <li>统一异常处理：配合全局异常处理器使用</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 1. 默认使用方式 - 无需额外配置，框架会自动注册并使用默认实现
 * 
 * // 2. 自定义响应处理服务 - 实现GXResponseBodyAdviceService接口
 * @Service
 * public class CustomResponseBodyAdviceService implements GXResponseBodyAdviceService {
 *     @Override
 *     public boolean supports(MethodParameter returnType, Class converterType) {
 *         // 自定义判断逻辑，决定是否需要处理该响应
 *         return true; // 处理所有响应
 *     }
 * 
 *     @Override
 *     public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
 *                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
 *                                  ServerHttpRequest request, ServerHttpResponse response) {
 *         // 如果已经是标准响应格式，则直接返回
 *         if (body instanceof GXResultUtils) {
 *             return body;
 *         }
 *         
 *         // 对于String类型特殊处理，避免类型转换异常
 *         if (body instanceof String) {
 *             return JSONUtil.toJsonStr(GXResultUtils.success(body));
 *         }
 *         
 *         // 其他类型统一包装为标准响应格式
 *         return GXResultUtils.success(body);
 *     }
 * 
 *     @Override
 *     public List<String> buildCookies() {
 *         // 自定义Cookie构建逻辑
 *         ResponseCookie cookie = ResponseCookie.from("token", "your-token-value")
 *                 .maxAge(3600) // 1小时过期
 *                 .httpOnly(true)
 *                 .secure(true)
 *                 .path("/")
 *                 .sameSite(Cookie.SameSite.STRICT.attributeValue())
 *                 .build();
 *         return CollUtil.newArrayList(cookie.toString());
 *     }
 * }
 * </pre>
 * 
 * <p>安全性考虑：</p>
 * <ul>
 *   <li>Cookie安全：默认设置httpOnly=true防止XSS攻击，使用SameSite=LAX防止CSRF攻击</li>
 *   <li>数据脱敏：敏感数据在返回前应进行适当脱敏处理</li>
 *   <li>错误信息：生产环境应避免返回详细的错误堆栈信息</li>
 * </ul>
 *
 * @author maple
 * @see GXResponseBodyAdviceService 实际业务处理服务接口
 * @see ResponseBodyAdvice Spring响应体处理接口
 * @see RestControllerAdvice Spring REST控制器增强注解
 */
@Log4j2
@RestControllerAdvice
public class GXResponseBodyAdvice implements ResponseBodyAdvice<Object> {
    /**
     * 判断是否应该应用此拦截器
     * 首先检查是否存在GXResponseBodyAdviceService，如果存在则委托给它判断
     * 如果不存在则默认判断返回类型是否为GXResultUtils类型
     *
     * @param returnType    返回类型
     * @param converterType 选择的转换器类型
     * @return {@code true} 如果应该调用 {@link #beforeBodyWrite}；
     * {@code false} 否则
     */
    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        GXResponseBodyAdviceService responseBodyAdviceService = GXSpringContextUtils.getBean(GXResponseBodyAdviceService.class);
        if (ObjectUtil.isNotNull(responseBodyAdviceService)) {
            return responseBodyAdviceService.supports(returnType, converterType);
        }
        return returnType.getParameterType().isAssignableFrom(GXResultUtils.class);
    }

    /**
     * 在选择{@code HttpMessageConverter}之后且在调用其write方法之前调用
     * 主要用于处理响应体，添加Cookie，并委托给GXResponseBodyAdviceService进行处理
     *
     * @param body                  要写入的响应体
     * @param returnType            控制器方法的返回类型
     * @param selectedContentType   通过内容协商选择的内容类型
     * @param selectedConverterType 选择用于写入响应的转换器类型
     * @param request               当前请求
     * @param response              当前响应
     * @return 传入的响应体或修改后的（可能是新的）实例
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        log.debug("响应拦截成功!");
        boolean allowCredentials = GXCommonUtils.getEnvironmentValue("cors.allow.credentials", boolean.class, false);
        if (allowCredentials) {
            List<String> cookies = buildCookies();
            cookies.forEach(cookie -> response.getHeaders().add(HttpHeaders.SET_COOKIE, cookie));
        }
        GXResponseBodyAdviceService responseBodyAdviceService = GXSpringContextUtils.getBean(GXResponseBodyAdviceService.class);
        if (ObjectUtil.isNotNull(responseBodyAdviceService)) {
            return responseBodyAdviceService.beforeBodyWrite(body, returnType, selectedContentType, selectedConverterType, request, response);
        }
        return body;
    }

    /**
     * 构建响应Cookie
     * 首先检查是否存在GXResponseBodyAdviceService，如果存在则委托给它构建Cookie
     * 如果不存在则使用默认的Cookie构建方法
     *
     * @return Cookie字符串列表
     */
    private List<String> buildCookies() {
        GXResponseBodyAdviceService responseBodyAdviceService = GXSpringContextUtils.getBean(GXResponseBodyAdviceService.class);
        if (ObjectUtil.isNotNull(responseBodyAdviceService)) {
            return responseBodyAdviceService.buildCookies();
        }
        return defaultBuildCookies();
    }

    /**
     * 默认的Cookie构建方法
     * 从环境配置中获取Cookie相关参数，并构建包含认证码的ResponseCookie
     * 主要用于在没有自定义GXResponseBodyAdviceService实现时提供基本的Cookie功能
     *
     * @return Cookie字符串列表
     */
    private List<String> defaultBuildCookies() {
        boolean isSecure = GXCommonUtils.getEnvironmentValue("cors.cookie.secure", boolean.class, false);
        String cookieSecret = GXCommonUtils.getEnvironmentValue("cors.cookie.secret", String.class, "CF3417E3CF6B0F28");
        Integer effectiveDuration = GXCommonUtils.getEnvironmentValue("cors.cookie.duration", Integer.class, 300);
        String cookieVirusID = GXCommonUtils.getEnvironmentValue("cors.cookie.virusId", String.class);
        if (CharSequenceUtil.isNotBlank(cookieVirusID)) {
            String authCode = GXAuthCodeUtils.authCodeEncode(cookieVirusID, cookieSecret, effectiveDuration);
            ResponseCookie cookie = ResponseCookie.from("UserData", authCode)
                    .maxAge(-1)// 浏览器关闭，则删除 Cookie
                    .secure(isSecure)       // 可以在HTTP或者HTTPS协议中传输
                    .httpOnly(true)         // javascript不能读写
                    //.domain(null)		    // 提交cookie的域
                    //.path(null)		    // 提交cookie的path
                    .sameSite(Cookie.SameSite.LAX.attributeValue())// 设置 SameSite 为 LAX
                    .build();
            return CollUtil.newArrayList(cookie.toString());
        }
        return CollUtil.newArrayList();
    }
}