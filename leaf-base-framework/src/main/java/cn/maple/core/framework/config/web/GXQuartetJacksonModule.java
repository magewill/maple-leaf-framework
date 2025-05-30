package cn.maple.core.framework.config.web;

import cn.maple.core.framework.exception.GXBusinessException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.javatuples.Quartet;

import java.io.IOException;
import java.util.Map;

/**
 * Jackson模块，用于序列化和反序列化javatuples库中的Quartet类。
 * <p>
 * 该模块提供了自定义的序列化器和反序列化器，使Quartet对象能够与JSON格式进行无缝转换。
 * Quartet是一个包含4个元素的不可变元组，在复杂数据结构传输时非常有用。
 * </p>
 *
 * <p><b>序列化格式：</b> Quartet对象将被序列化为包含value0到value3字段的JSON对象</p>
 *
 * <p><b>线程安全性：</b> 该模块是无状态的，可以安全地在多线程环境中使用</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 注册模块到ObjectMapper
 * ObjectMapper mapper = new ObjectMapper();
 * mapper.registerModule(GXQuartetJacksonModule.createModule());
 *
 * // 序列化示例
 * Quartet&lt;String, Integer, Boolean, List&lt;String&gt;&gt; quartet =
 *     Quartet.with("example", 123, true, Arrays.asList("a", "b", "c"));
 * String json = mapper.writeValueAsString(quartet);
 * // 输出: {"value0":"example","value1":123,"value2":true,"value3":["a","b","c"]}
 *
 * // 反序列化示例
 * String jsonStr = "{\"value0\":\"example\",\"value1\":123,\"value2\":true,\"value3\":[\"a\",\"b\",\"c\"]}";
 * Quartet quartet = mapper.readValue(jsonStr, Quartet.class);
 * </pre>
 */
public class GXQuartetJacksonModule {
    /**
     * 创建并配置用于处理Quartet类的Jackson SimpleModule
     *
     * @return 配置好的SimpleModule实例，包含Quartet的序列化器和反序列化器
     */
    @SuppressWarnings({"rawtypes"})
    public static SimpleModule createModule() {
        // 创建一个具有描述性名称的SimpleModule实例
        SimpleModule module = new SimpleModule("GXQuartetModule");

        // 注册Quartet类的序列化器
        module.addSerializer(Quartet.class, new JsonSerializer<>() {
            /**
             * 将Quartet对象序列化为JSON
             * <p>
             * 序列化格式: {"value0":obj1, "value1":obj2, "value2":obj3, "value3":obj4}
             * </p>
             *
             * @param value Quartet对象，包含4个可能是任何类型的值
             * @param gen JSON生成器
             * @param serializers 序列化提供者，用于处理嵌套对象的序列化
             * @throws IOException 如果序列化过程中发生I/O错误
             */
            @Override
            public void serialize(Quartet value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                // 处理null值情况
                if (value == null) {
                    gen.writeNull();
                    return;
                }

                try {
                    // 开始写入JSON对象
                    gen.writeStartObject();

                    // 序列化Quartet的四个值，使用统一的字段命名模式
                    // 使用defaultSerializeValue确保每个值都能根据其实际类型正确序列化
                    gen.writeFieldName("value0");
                    serializers.defaultSerializeValue(value.getValue0(), gen);

                    gen.writeFieldName("value1");
                    serializers.defaultSerializeValue(value.getValue1(), gen);

                    gen.writeFieldName("value2");
                    serializers.defaultSerializeValue(value.getValue2(), gen);

                    gen.writeFieldName("value3");
                    serializers.defaultSerializeValue(value.getValue3(), gen);

                    // 结束JSON对象
                    gen.writeEndObject();
                } catch (Exception e) {
                    // 转换为IOException并添加上下文信息，便于调试
                    throw new IOException("Error serializing Quartet: " + e.getMessage(), e);
                }
            }
        });

        // 注册Quartet类的反序列化器
        module.addDeserializer(Quartet.class, new JsonDeserializer<>() {
            /**
             * 将JSON对象反序列化为Quartet实例
             * <p>
             * 期望的JSON格式: {"value0":obj1, "value1":obj2, "value2":obj3, "value3":obj4}
             * </p>
             *
             * @param p JSON解析器
             * @param ctxt 反序列化上下文
             * @return 反序列化后的Quartet实例
             * @throws IOException 如果反序列化过程中发生I/O错误
             */
            @Override
            public Quartet deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                try {
                    // 验证JSON格式，确保我们从一个对象开始
                    if (p.currentToken() != JsonToken.START_OBJECT) {
                        throw new GXBusinessException("Expected START_OBJECT token for Quartet, but found: " + p.currentToken());
                    }

                    // 将整个JSON对象读入Map，这样可以灵活处理字段顺序和缺失字段
                    Map<String, Object> objectMap = p.readValueAs(new TypeReference<Map<String, Object>>() {
                    });

                    // 从map中提取值，如果某个字段不存在则为null
                    Object value0 = objectMap.getOrDefault("value0", null);
                    Object value1 = objectMap.getOrDefault("value1", null);
                    Object value2 = objectMap.getOrDefault("value2", null);
                    Object value3 = objectMap.getOrDefault("value3", null);

                    // 使用Quartet.with()工厂方法创建不可变的Quartet实例
                    return Quartet.with(value0, value1, value2, value3);
                } catch (GXBusinessException e) {
                    // 业务异常直接抛出
                    throw e;
                } catch (Exception e) {
                    // 其他异常转换为IOException并添加上下文信息
                    throw new IOException("Error deserializing Quartet: " + e.getMessage(), e);
                }
            }
        });

        // 返回配置好的模块
        return module;
    }
}