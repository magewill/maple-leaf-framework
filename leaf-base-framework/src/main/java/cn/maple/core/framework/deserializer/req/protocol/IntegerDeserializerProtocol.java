package cn.maple.core.framework.deserializer.req.protocol;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Integer类型反序列化处理器
 * <p>
 * 该类用于处理JSON反序列化过程中Integer类型的特殊情况，主要解决以下问题：
 * 1. 处理非数字格式字符串到Integer的转换
 * 2. 优雅处理"Invalid date"等无法转换为Integer的值
 * 3. 确保在反序列化失败时返回null而不是抛出异常
 * </p>
 * <p>
 * 线程安全说明：
 * 该类不包含任何状态变量，所有操作都基于方法参数，因此是线程安全的。
 * Jackson框架在反序列化时会为每个线程创建独立的反序列化器实例。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * public class MyDto {
 *     @JsonDeserialize(using = IntegerDeserializerProtocol.class)
 *     private Integer value;
 *     // getters and setters
 * }
 * </pre>
 * </p>
 *
 * @author britton chen
 * @since 1.0.0
 */
@Slf4j
public class IntegerDeserializerProtocol extends StdDeserializer<Integer> {
    public IntegerDeserializerProtocol() {
        super(Integer.class);
    }

    /**
     * 反序列化方法，将JSON值转换为Integer对象
     * <p>
     * 该方法实现了以下逻辑：
     * 1. 首先尝试直接获取整数值
     * 2. 如果失败，尝试将字符串解析为整数
     * 3. 如果解析失败，记录日志并返回null
     * </p>
     * <p>
     * 内存安全：该方法不创建大量临时对象，避免内存泄漏
     * 异常安全：所有异常都被捕获并妥善处理，不会导致应用崩溃
     * </p>
     *
     * @param p  JSON解析器，提供要反序列化的值
     * @param ct 反序列化上下文
     * @return 解析后的Integer对象，解析失败时返回null
     * @throws IOException 如果发生I/O错误
     */
    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ct) throws JacksonException {
        if (p == null) {
            return null;
        }

        try {
            // 如果是整数，直接返回  
            return p.getIntValue();
        } catch (Exception e) {
            // 如果是字符串，尝试处理  
            String text = p.getText();
            if (text == null || text.isEmpty()) {
                return null;
            }

            try {
                // 用于可解析为数字的字符串
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                log.debug("Cannot deserialize Integer from value: '{}'", text);
                // 返回默认值或其他逻辑，比如 null  
                return null;
            }
        }
    }
}