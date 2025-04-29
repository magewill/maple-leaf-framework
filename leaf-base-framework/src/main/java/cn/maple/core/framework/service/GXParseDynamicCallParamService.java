package cn.maple.core.framework.service;

import java.util.Map;

/**
 * 动态调用参数解析服务接口
 * <p>
 * 该接口用于解析动态调用方法时的参数，支持从JSON字符串中解析出方法所需的参数值。
 * 在微服务架构或动态API调用场景中，经常需要将前端或其他服务传递的JSON格式参数
 * 转换为目标方法所需的实际参数类型。
 * </p>
 */
public interface GXParseDynamicCallParamService {
    /**
     * 获取动态调用方法的参数实际值
     * <p>
     * 从JSON字符串中解析出方法调用所需的参数值。实现类应当能够处理以下情况：
     * 1. 基本数据类型及其包装类的转换
     * 2. 复杂对象的反序列化
     * 3. 集合类型的处理
     * 4. 空值的安全处理
     * </p>
     *
     * @param jsonStr JSON格式的参数字符串
     * @return 解析后的参数对象，可能是单个对象、数组或集合
     * @throws IllegalArgumentException 当JSON字符串格式不正确或无法解析时抛出
     */
    Object getDynamicCallMethodParamValue(String jsonStr);

    /**
     * 获取动态调用方法的多个参数值
     * <p>
     * 从JSON字符串中解析出多个参数值，并按照指定的参数名映射返回。
     * 此方法适用于需要多个命名参数的场景。
     * </p>
     *
     * @param jsonStr JSON格式的参数字符串
     * @return 参数名到参数值的映射
     * @throws IllegalArgumentException 当JSON字符串格式不正确或无法解析时抛出
     */
    default Map<String, Object> getDynamicCallMethodParamMap(String jsonStr) {
        throw new UnsupportedOperationException("请在实现类中提供此方法的具体实现");
    }

    /**
     * 获取指定类型的参数值
     * <p>
     * 从JSON字符串中解析出特定类型的参数值，提供类型安全的参数获取方式。
     * </p>
     *
     * @param jsonStr JSON格式的参数字符串
     * @param clazz   参数的目标类型
     * @param <T>     参数类型
     * @return 转换为指定类型的参数值
     * @throws IllegalArgumentException 当JSON字符串格式不正确或无法转换为指定类型时抛出
     */
    default <T> T getDynamicCallMethodParamValue(String jsonStr, Class<T> clazz) {
        throw new UnsupportedOperationException("请在实现类中提供此方法的具体实现");
    }
}
