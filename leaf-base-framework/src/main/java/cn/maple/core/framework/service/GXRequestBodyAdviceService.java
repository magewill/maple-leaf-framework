package cn.maple.core.framework.service;

import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.TypeUtil;
import cn.maple.core.framework.dto.protocol.req.GXBaseReqProtocol;
import cn.maple.core.framework.util.GXCommonUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * 请求体处理服务接口
 * <p>
 * 该接口用于在Spring MVC框架中对请求体进行预处理和后处理。
 * 主要功能包括：
 * 1. 在请求体被读取和转换前进行预处理
 * 2. 在请求体被转换为对象后进行验证和修复
 * 3. 处理空请求体的情况
 * </p>
 * <p>
 * 实现该接口可以对请求数据进行统一的验证、转换和修复，提高接口的安全性和稳定性。
 * </p>
 */
public interface GXRequestBodyAdviceService {
    /**
     * 进行参数验证之前对数据进行修复的方法名字
     * <p>
     * 该常量定义了在验证请求数据前需要调用的修复方法名。
     * 请求对象可以实现此方法来预处理数据，如格式转换、默认值设置等。
     * </p>
     */
    String BEFORE_REPAIR_METHOD = "beforeRepair";

    /**
     * 进行参数验证的方法名字
     * <p>
     * 该常量定义了用于验证请求数据的方法名。
     * 请求对象可以实现此方法来执行自定义验证逻辑，补充标准验证框架的不足。
     * </p>
     */
    String VERIFY_METHOD = "verify";

    /**
     * 进行参数验证之后对数据进行修复的方法名字
     * <p>
     * 该常量定义了在验证请求数据后需要调用的修复方法名。
     * 请求对象可以实现此方法来对验证后的数据进行最终处理，如数据规范化、关联数据处理等。
     * </p>
     */
    String AFTER_REPAIR_METHOD = "afterRepair";

    /**
     * 判断是否应用此拦截器
     * <p>
     * 该方法首先被调用，用于确定是否应该对当前请求应用此拦截器。
     * 默认实现检查目标类型是否为GXBaseReqProtocol的子类或实现类。
     * </p>
     *
     * @param methodParameter 方法参数信息
     * @param targetType      目标类型，不一定与方法参数类型相同，例如对于{@code HttpEntity<String>}
     * @param converterType   选择的转换器类型
     * @return 如果应该调用此拦截器则返回true，否则返回false
     */
    default boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ClassUtil.isAssignable(GXBaseReqProtocol.class, TypeUtil.getClass(targetType));
    }

    /**
     * 在请求体被转换为对象后处理
     * <p>
     * 该方法在请求体被转换为对象后被调用，用于对转换后的对象进行处理。
     * 默认实现会按顺序调用对象的三个方法：
     * 1. beforeRepair - 预处理数据
     * 2. verify - 执行自定义验证
     * 3. afterRepair - 最终处理数据
     * </p>
     * <p>
     * 这种处理流程确保了请求数据在进入业务逻辑前已经过充分验证和规范化。
     * </p>
     *
     * @param body          转换后的对象，在第一个advice被调用前设置
     * @param inputMessage  请求信息
     * @param parameter     目标方法参数
     * @param targetType    目标类型，不一定与方法参数类型相同
     * @param converterType 用于反序列化请求体的转换器
     * @return 处理后的对象，可以是原对象或新实例
     */
    default Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        try {
            // 对请求数据进行验证之前的修复处理
            GXCommonUtils.reflectCallObjectMethod(body, BEFORE_REPAIR_METHOD);
            // 调用目标bean对象的验证方法对数据进行验证(自定义验证)
            GXCommonUtils.reflectCallObjectMethod(body, VERIFY_METHOD);
            // 调用目标bean对象的修复方法对数据进行最后的修复
            GXCommonUtils.reflectCallObjectMethod(body, AFTER_REPAIR_METHOD);
        } catch (Exception e) {
            // 记录异常但不中断流程，确保请求能够继续处理
            // 具体异常处理可由实现类覆盖此方法进行自定义
        }
        return body;
    }

    /**
     * 在请求体被读取和转换前处理
     * <p>
     * 该方法在请求体被读取和转换前被调用，可以用于修改原始请求数据。
     * 例如，可以在此处解密加密的请求数据、添加或修改请求头等。
     * </p>
     *
     * @param inputMessage  请求信息
     * @param parameter     目标方法参数
     * @param targetType    目标类型，不一定与方法参数类型相同
     * @param converterType 用于反序列化请求体的转换器
     * @return 处理后的请求信息，不能为null
     * @throws IOException 如果读取请求体时发生I/O错误
     */
    default HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        return inputMessage;
    }

    /**
     * 处理空请求体
     * <p>
     * 该方法在请求体为空时被调用，可以用于提供默认值或执行特殊处理。
     * 如果返回null且参数是必需的，则可能会抛出HttpMessageNotReadableException异常。
     * </p>
     *
     * @param body          通常在第一个advice被调用前设置为null
     * @param inputMessage  请求信息
     * @param parameter     方法参数
     * @param targetType    目标类型，不一定与方法参数类型相同
     * @param converterType 选择的转换器类型
     * @return 要使用的值，如果返回null且参数是必需的，则可能会抛出异常
     */
    default Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
