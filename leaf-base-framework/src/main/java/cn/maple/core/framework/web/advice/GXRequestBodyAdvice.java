package cn.maple.core.framework.web.advice;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.service.GXRequestBodyAdviceService;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * 请求体处理增强类，用于拦截和处理HTTP请求体
 * <p>
 * 该类通过Spring的RequestBodyAdvice机制，在请求体被读取和转换过程中提供拦截点，
 * 允许对请求体进行预处理和后处理。主要功能包括：
 * <ul>
 *   <li>请求体的验证和转换</li>
 *   <li>将原始JSON请求体保存到请求属性中，便于后续处理</li>
 *   <li>委托给GXRequestBodyAdviceService进行实际业务处理</li>
 * </ul>
 * </p>
 * <p>
 * 该类继承自RequestBodyAdviceAdapter，只需要重写必要的方法，简化了开发。
 * 通过@RestControllerAdvice注解，使其对所有RestController生效。
 * </p>
 *
 * @author maple
 * @see GXRequestBodyAdviceService 实际业务处理服务接口
 * @see RequestBodyAdviceAdapter Spring请求体处理适配器
 * @see RestControllerAdvice Spring REST控制器增强注解
 */
@Slf4j
@RestControllerAdvice
public class GXRequestBodyAdvice extends RequestBodyAdviceAdapter {
    /**
     * 判断是否应该应用此拦截器
     * 首先检查是否存在GXRequestBodyAdviceService，如果存在则委托给它判断
     * 如果不存在则默认返回true，表示拦截所有请求
     * 该方法在异常情况下默认返回true，确保安全性
     *
     * @param methodParameter 方法参数，包含目标方法的相关信息
     * @param targetType      目标类型，不一定与方法参数类型相同，例如对于HttpEntity<String>
     * @param converterType   选择的转换器类型，用于反序列化请求体
     * @return {@code true} 如果应该调用此拦截器；{@code false} 否则
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        GXRequestBodyAdviceService requestBodyAdviceService = GXSpringContextUtils.getBean(GXRequestBodyAdviceService.class);
        if (ObjectUtil.isNull(requestBodyAdviceService)) {
            return true;
        }
        return requestBodyAdviceService.supports(methodParameter, targetType, converterType);
    }

    /**
     * 在请求体被读取并转换为对象后调用
     * 将原始JSON请求体保存到请求属性中，并委托给GXRequestBodyAdviceService进行处理
     *
     * @param body          转换后的请求体对象
     * @param inputMessage  原始HTTP请求
     * @param parameter     目标方法参数
     * @param targetType    目标类型
     * @param converterType 用于反序列化的转换器
     * @return 处理后的请求体对象
     */
    @NotNull
    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        GXRequestBodyAdviceService requestBodyAdviceService = GXSpringContextUtils.getBean(GXRequestBodyAdviceService.class);
        Object jsonRequestBody = Objects.requireNonNull(GXCurrentRequestContextUtils.getHttpServletRequest()).getAttribute("JSON_REQUEST_BODY");
        if (ObjectUtil.isEmpty(jsonRequestBody)) {
            Objects.requireNonNull(GXCurrentRequestContextUtils.getHttpServletRequest()).setAttribute("JSON_REQUEST_BODY", JSONUtil.toJsonStr(body));
        }
        if (ObjectUtil.isNull(requestBodyAdviceService)) {
            return super.afterBodyRead(body, inputMessage, parameter, targetType, converterType);
        }
        return requestBodyAdviceService.afterBodyRead(body, inputMessage, parameter, targetType, converterType);
    }

    /**
     * 在请求体被读取和转换之前调用
     * 委托给GXRequestBodyAdviceService进行预处理，如果服务不存在则使用默认处理
     *
     * @param inputMessage  HTTP请求
     * @param parameter     目标方法参数
     * @param targetType    目标类型
     * @param converterType 用于反序列化的转换器
     * @return 处理后的HTTP请求（不能为null）
     * @throws IOException 如果读取请求体时发生I/O异常
     */
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        GXRequestBodyAdviceService requestBodyAdviceService = GXSpringContextUtils.getBean(GXRequestBodyAdviceService.class);
        if (ObjectUtil.isNull(requestBodyAdviceService)) {
            return super.beforeBodyRead(inputMessage, parameter, targetType, converterType);
        }
        return requestBodyAdviceService.beforeBodyRead(inputMessage, parameter, targetType, converterType);
    }

    /**
     * 当请求体为空时调用
     * 委托给GXRequestBodyAdviceService处理空请求体，如果服务不存在则使用默认处理
     *
     * @param body          通常在第一个advice调用前设置为null
     * @param inputMessage  HTTP请求
     * @param parameter     方法参数
     * @param targetType    目标类型
     * @param converterType 选择的转换器类型
     * @return 要使用的值，如果返回null且参数是必需的，可能会引发HttpMessageNotReadableException
     */
    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        GXRequestBodyAdviceService requestBodyAdviceService = GXSpringContextUtils.getBean(GXRequestBodyAdviceService.class);
        if (ObjectUtil.isNull(requestBodyAdviceService)) {
            return super.handleEmptyBody(body, inputMessage, parameter, targetType, converterType);
        }
        return requestBodyAdviceService.handleEmptyBody(body, inputMessage, parameter, targetType, converterType);
    }
}