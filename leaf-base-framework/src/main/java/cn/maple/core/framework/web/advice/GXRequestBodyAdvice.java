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
 * 
 * <p>
 * 该类继承自RequestBodyAdviceAdapter，只需要重写必要的方法，简化了开发。
 * 通过@RestControllerAdvice注解，使其对所有RestController生效。
 * </p>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>请求参数解密：对加密的请求体进行解密处理</li>
 *   <li>请求数据转换：将特定格式的请求数据转换为系统内部格式</li>
 *   <li>请求日志记录：记录原始请求体，用于审计和调试</li>
 *   <li>请求参数校验：在控制器方法执行前进行自定义参数校验</li>
 *   <li>防重复提交：结合请求体内容进行幂等性检查</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 1. 默认使用方式 - 无需额外配置，框架会自动注册并使用默认实现
 * // 默认实现会将原始JSON请求体保存到请求属性中，键名为"JSON_REQUEST_BODY"
 * 
 * // 2. 自定义请求处理服务 - 实现GXRequestBodyAdviceService接口
 * @Service
 * public class CustomRequestBodyAdviceService implements GXRequestBodyAdviceService {
 *     @Override
 *     public boolean supports(MethodParameter methodParameter, Type targetType, 
 *                           Class<? extends HttpMessageConverter<?>> converterType) {
 *         // 自定义判断逻辑，决定是否需要处理该请求
 *         return true; // 处理所有请求
 *     }
 *     
 *     @Override
 *     public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, 
 *                               Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
 *         // 请求体读取并转换为对象后的处理
 *         log.info("接收到请求体: {}", JSONUtil.toJsonStr(body));
 *         
 *         // 可以在这里进行请求体的修改、增强或验证
 *         if (body instanceof LoginRequest) {
 *             LoginRequest loginRequest = (LoginRequest) body;
 *             // 对密码字段进行解密
 *             loginRequest.setPassword(decryptPassword(loginRequest.getPassword()));
 *             return loginRequest;
 *         }
 *         
 *         return body; // 返回原始或修改后的请求体
 *     }
 *     
 *     @Override
 *     public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, 
 *                                         Type targetType, Class<? extends HttpMessageConverter<?>> converterType) 
 *                                         throws IOException {
 *         // 在请求体被读取之前的处理
 *         // 可以在这里对原始请求进行预处理，如解密等
 *         return new CustomHttpInputMessage(inputMessage); // 返回原始或包装后的请求
 *     }
 *     
 *     @Override
 *     public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, 
 *                                 Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
 *         // 处理空请求体的情况
 *         log.warn("接收到空请求体");
 *         return body; // 可以返回默认值或保持为null
 *     }
 *     
 *     // 自定义方法 - 密码解密
 *     private String decryptPassword(String encryptedPassword) {
 *         // 实现密码解密逻辑
 *         return "decrypted-" + encryptedPassword;
 *     }
 * }
 * 
 * // 自定义HttpInputMessage实现，用于请求体预处理
 * class CustomHttpInputMessage implements HttpInputMessage {
 *     private final HttpInputMessage inputMessage;
 *     
 *     public CustomHttpInputMessage(HttpInputMessage inputMessage) {
 *         this.inputMessage = inputMessage;
 *     }
 *     
 *     @Override
 *     public InputStream getBody() throws IOException {
 *         // 可以在这里对请求体进行预处理，如解密
 *         return inputMessage.getBody();
 *     }
 *     
 *     @Override
 *     public HttpHeaders getHeaders() {
 *         return inputMessage.getHeaders();
 *     }
 * }
 * </pre>
 * 
 * <p>安全性考虑：</p>
 * <ul>
 *   <li>敏感数据处理：对请求中的敏感信息（如密码）应进行适当处理，避免明文记录</li>
 *   <li>输入验证：对请求数据进行严格验证，防止注入攻击</li>
 *   <li>异常处理：请求处理过程中的异常应妥善处理，避免敏感信息泄露</li>
 *   <li>性能影响：复杂的请求处理逻辑可能影响性能，应进行适当优化</li>
 * </ul>
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