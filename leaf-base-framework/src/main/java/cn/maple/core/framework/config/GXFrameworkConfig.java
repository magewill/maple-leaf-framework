package cn.maple.core.framework.config;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

@Component
@ComponentScan({"cn.maple"})
public class GXFrameworkConfig {
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        final ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        final SerializerProvider serializerProvider = objectMapper.getSerializerProvider();
        serializerProvider.setNullValueSerializer(new JsonSerializer<>() {
            @Override
            public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                String fieldName = jsonGenerator.getOutputContext().getCurrentName();
                if (CharSequenceUtil.isNotEmpty(fieldName)) {
                    try {
                        // 反射获取字段类型
                        Field field = jsonGenerator.currentValue().getClass().getDeclaredField(fieldName);
                        if (CharSequence.class.isAssignableFrom(field.getType())) {
                            // 字符串型空值""
                            jsonGenerator.writeString("");
                            return;
                        } else if (Collection.class.isAssignableFrom(field.getType())) {
                            // 列表型空值返回[]
                            // jsonGenerator.writeStartArray();
                            // jsonGenerator.writeEndArray();
                            // 返回一个null值
                            jsonGenerator.writeNull();
                            return;
                        } else if (Map.class.isAssignableFrom(field.getType())) {
                            // map型空值或者bean对象返回"{}"
                            jsonGenerator.writeStartObject();
                            jsonGenerator.writeEndObject();
                            return;
                        }
                    } catch (NoSuchFieldException noSuchFieldException) {
                        //LOG.debug(noSuchFieldException.getMessage());
                    }
                    //jsonGenerator.writeString("");
                    jsonGenerator.writeNull();
                }
            }
        });
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    @Bean
    public LocalValidatorFactoryBean localValidatorFactoryBean() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.getValidationPropertyMap().put("hibernate.validator.fail_fast", "true");
        return bean;
    }
}
