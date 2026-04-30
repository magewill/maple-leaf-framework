package cn.maple.core.framework.config;

import cn.maple.core.framework.util.GXCommonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
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

//@Configuration
//@ComponentScan({"cn.maple"})
@AutoConfiguration(
        after = JacksonAutoConfiguration.class,
        before = ValidationAutoConfiguration.class
)
@ImportAutoConfiguration
public class GXFrameworkConfig {
    @Value("${maple.framework.validator.fail-fast:true}")
    private boolean failFast;

    @Bean
    @ConditionalOnClass(name = "tools.jackson.datatype.guava.GuavaModule")
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> {
            builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            builder.addModule(new GuavaModule());

            SimpleModule module = new SimpleModule();
            module.setSerializerModifier(new GXValueSerializerModifier());
            builder.addModule(module);
        };
    }

    @Bean
    @ConditionalOnMissingBean(LocalValidatorFactoryBean.class)
    public LocalValidatorFactoryBean localValidatorFactoryBean() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.getValidationPropertyMap().put("hibernate.validator.fail_fast", String.valueOf(failFast));
        return bean;
    }

    public static class GXValueSerializerModifier extends ValueSerializerModifier {
        private static final ValueSerializer<Object> NULL_STRING_SERIALIZER = new NullStringJsonSerializer();
        private static final ValueSerializer<Object> NULL_ARRAY_COLLECTION_SERIALIZER = new NullArrayOrCollectionJsonSerializer();
        private static final ValueSerializer<Object> NULL_MAP_SERIALIZER = new NullMapJsonSerializer();
        private static final ValueSerializer<Object> NULL_NUMBER_SERIALIZER = new NullNumberJsonSerializer();
        private static final ValueSerializer<Object> NULL_BOOLEAN_SERIALIZER = new NullBooleanJsonSerializer();
        private static final boolean NULL_NUMBER_CONVERT_ZERO = GXCommonUtils.getEnvironmentValue("maple.framework.jackson.null_number_convertor_zero", Boolean.class, false);
        private static final boolean NULL_BOOLEAN_CONVERT_FALSE = GXCommonUtils.getEnvironmentValue("maple.framework.jackson.null_number_convertor_false", Boolean.class, false);

        @Override
        public List<BeanPropertyWriter> changeProperties(
                SerializationConfig config,
                BeanDescription.Supplier beanDesc,
                List<BeanPropertyWriter> beanProperties) {
            for (BeanPropertyWriter writer : beanProperties) {
                Class<?> clazz = writer.getType().getRawClass();
                if (CharSequence.class.isAssignableFrom(clazz)) {
                    writer.assignNullSerializer(NULL_STRING_SERIALIZER);
                } else if (Collection.class.isAssignableFrom(clazz) || clazz.isArray()) {
                    writer.assignNullSerializer(NULL_ARRAY_COLLECTION_SERIALIZER);
                } else if (Map.class.isAssignableFrom(clazz)) {
                    writer.assignNullSerializer(NULL_MAP_SERIALIZER);
                } else if (Number.class.isAssignableFrom(clazz) && NULL_NUMBER_CONVERT_ZERO) {
                    writer.assignNullSerializer(NULL_NUMBER_SERIALIZER);
                } else if (Boolean.class.isAssignableFrom(clazz) && NULL_BOOLEAN_CONVERT_FALSE) {
                    writer.assignNullSerializer(NULL_BOOLEAN_SERIALIZER);
                }
            }
            return beanProperties;
        }
    }

    public static class NullStringJsonSerializer extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeString("");
        }
    }

    public static class NullNumberJsonSerializer extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt)
                throws JacksonException {
            gen.writeNumber(0);
        }
    }

    public static class NullBooleanJsonSerializer extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt)
                throws JacksonException {
            gen.writeBoolean(false);
        }
    }

    public static class NullArrayOrCollectionJsonSerializer extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartArray();
            gen.writeEndArray();
        }
    }

    public static class NullMapJsonSerializer extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeStartObject();
            gen.writeEndObject();
        }
    }
}
