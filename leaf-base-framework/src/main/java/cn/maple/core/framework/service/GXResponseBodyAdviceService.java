package cn.maple.core.framework.service;

import cn.hutool.core.collection.CollUtil;
import cn.maple.core.framework.util.GXResultUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.util.List;

/**
 * 响应体处理服务接口
 * <p>
 * 该接口用于在Spring MVC框架中对响应体进行处理。
 * 主要功能包括：
 * 1. 在响应体被写入前进行修改或增强
 * 2. 构建响应Cookie
 * </p>
 * <p>
 * 实现该接口可以对响应数据进行统一处理，如添加通用字段、格式转换、数据脱敏等，
 * 提高接口响应的一致性和安全性。
 * </p>
 */
public interface GXResponseBodyAdviceService {
    /**
     * 判断是否应用此组件
     * <p>
     * 该方法用于确定是否应该对当前响应应用此组件。
     * 默认实现检查返回类型是否为GXResultUtils的子类或实现类。
     * </p>
     *
     * @param returnType    控制器方法的返回类型
     * @param converterType 选择的转换器类型
     * @return 如果应该调用{@link #beforeBodyWrite}方法则返回true，否则返回false
     */
    default boolean supports(MethodParameter returnType, Class<?> converterType) {
        return returnType.getParameterType().isAssignableFrom(GXResultUtils.class);
    }

    /**
     * 在响应体被写入前处理
     * <p>
     * 该方法在HttpMessageConverter被选择后、其write方法被调用前被调用，
     * 可以用于修改或增强响应体。例如，可以添加通用字段、进行数据脱敏、
     * 格式转换等操作。
     * </p>
     * <p>
     * 实现类可以根据需要修改响应体，也可以返回一个全新的对象作为响应体。
     * </p>
     *
     * @param body                  要写入的响应体
     * @param returnType            控制器方法的返回类型
     * @param selectedContentType   通过内容协商选择的内容类型
     * @param selectedConverterType 选择的用于写入响应的转换器类型
     * @param request               当前请求
     * @param response              当前响应
     * @return 传入的响应体或修改后的实例（可能是新实例）
     */
    default Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        return body;
    }

    /**
     * 构建响应Cookie
     * <p>
     * 该方法用于构建需要在响应中设置的Cookie。
     * 实现类可以根据业务需求生成不同的Cookie，如用户认证信息、偏好设置等。
     * </p>
     * <p>
     * 默认实现返回一个空列表，表示不设置任何Cookie。
     * </p>
     *
     * @return Cookie字符串列表，每个字符串表示一个Cookie
     */
    default List<String> buildCookies() {
        return CollUtil.newArrayList();
    }
}
