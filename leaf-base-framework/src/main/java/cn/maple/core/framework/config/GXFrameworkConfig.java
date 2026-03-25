package cn.maple.core.framework.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.*;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.datatype.guava.GuavaModule;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 框架核心配置类
 * <p>
 * 该配置类提供了以下功能：
 * 1. 自定义JSON序列化处理，特别是对null值的处理策略
 * 2. Bean验证配置，支持快速失败模式
 * 3. 组件自动扫描配置
 *
 * @author britton chen <britton@126.com>
 */
@Configuration
@ComponentScan({"cn.maple"})
@Slf4j
public class GXFrameworkConfig {
    @Value("${maple.framework.validator.fail-fast:true}")
    private String failFast;

    /**
     * 通过 JsonMapperBuilderCustomizer 注入自定义的 ValueSerializerModifier
     * 这是 Spring Boot 4 + Jackson 3 的标准扩展方式
     */
    @Bean
    public JsonMapperBuilderCustomizer nullHandlingCustomizer() {
        return builder -> {
            // 1. 关闭空 Bean 序列化报错
            builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            // 2. 关闭未知属性反序列化报错
            builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            // 3. 注册 Guava 类型支持（Jackson 3 版本）
            builder.addModule(new GuavaModule());
            SimpleModule module = new SimpleModule();
            module.setSerializerModifier(new GxValueSerializerModifier());
            builder.addModule(module);
        };
    }

    @Bean
    public LocalValidatorFactoryBean localValidatorFactoryBean() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.getValidationPropertyMap().put("hibernate.validator.fail_fast", failFast);
        return bean;
    }

    /**
     * Jackson 3 中 BeanSerializerModifier → ValueSerializerModifier
     */
    public static class GxValueSerializerModifier extends ValueSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(
                SerializationConfig config,
                BeanDescription.Supplier beanDesc,
                List<BeanPropertyWriter> beanProperties) {
            for (BeanPropertyWriter writer : beanProperties) {
                Class<?> clazz = writer.getType().getRawClass();
                if (CharSequence.class.isAssignableFrom(clazz)) {
                    writer.assignNullSerializer(new NullStringJsonSerializer());
                } else if (Collection.class.isAssignableFrom(clazz)) {
                    writer.assignNullSerializer(new NullCollectionJsonSerializer());
                } else if (Map.class.isAssignableFrom(clazz)) {
                    writer.assignNullSerializer(new NullMapJsonSerializer());
                } else if (clazz.isArray()) {
                    writer.assignNullSerializer(new NullArrayJsonSerializer());

                }
            }
            return beanProperties;
        }
    }

    // ★ Jackson 3: JsonSerializer → ValueSerializer，IOException 变为 unchecked
    public static class NullStringJsonSerializer extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, tools.jackson.core.JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeString("");
        }
    }

    public static class NullCollectionJsonSerializer extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, tools.jackson.core.JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartArray();
            gen.writeEndArray();
        }
    }

    public static class NullMapJsonSerializer extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, tools.jackson.core.JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartObject();
            gen.writeEndObject();
        }
    }

    /**
     * 原生数组 (String[]/int[] 等)：null → []
     */
    public static class NullArrayJsonSerializer extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartArray();
            gen.writeEndArray();
        }
    }
}
