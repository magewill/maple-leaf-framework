package cn.maple.core.framework.service;

import cn.hutool.core.lang.Dict;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 远程验证服务接口
 * <p>
 * 该接口用于调用远程服务进行数据验证，通常与Jakarta Validation框架集成，
 * 用于实现自定义验证注解的验证逻辑。可以将验证逻辑委托给远程服务或其他系统组件执行，
 * 适用于需要访问数据库或其他外部资源进行验证的场景。
 * </p>
 */
public interface GXCallRemoteValidateService {
    /**
     * 调用远程服务验证指定值是否有效
     * <p>
     * 该方法将验证请求委托给远程服务或其他系统组件执行，并返回验证结果。
     * 验证上下文对象可用于自定义验证失败消息。参数字典可包含验证所需的额外信息。
     * </p>
     *
     * @param value                      需要验证的值，可以是任意类型
     * @param constraintValidatorContext 验证约束上下文，用于自定义验证失败消息
     * @param param                      额外参数字典，包含验证所需的配置信息
     * @return 如果验证通过返回true，否则返回false
     * @throws UnsupportedOperationException 当验证操作不被支持时抛出
     */
    boolean callRemoteValidateService(Object value, ConstraintValidatorContext constraintValidatorContext, Dict param) throws UnsupportedOperationException;
}
