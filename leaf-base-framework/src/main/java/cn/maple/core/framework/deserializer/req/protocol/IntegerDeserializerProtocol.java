package cn.maple.core.framework.deserializer.req.protocol;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

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

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ct) throws JacksonException {
        if (p == null) {
            return null;
        }

        try {
            return p.getIntValue();
        } catch (Exception e) {
            String text = p.getString();
            if (text == null || text.isEmpty()) {
                return null;
            }

            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                log.debug("Cannot deserialize Integer from value: '{}'", text);
                return null;
            }
        }
    }
}