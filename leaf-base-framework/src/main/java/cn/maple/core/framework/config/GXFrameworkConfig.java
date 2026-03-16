package cn.maple.core.framework.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.IOException;
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
     * 配置自定义的ObjectMapper，用于处理JSON序列化
     * <p>
     * 通过 @Bean 声明并构建 ObjectMapper 提供给 Spring 容器使用，
     * 以兼容旧版本（缺少 Customizer 机制）或者 IDE 严格的 Autowired 检查机制，
     * 同时保留并采用高性能的 BeanSerializerModifier 空值处理策略。
     *
     * @return 配置好的ObjectMapper实例
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 1. 关闭空 Bean 序列化报错
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // 2. 关闭未知属性反序列化报错
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // 3. 注册 Guava 类型支持
        objectMapper.registerModule(new GuavaModule());

        // 4. 使用 BeanSerializerModifier 在初始化时绑定不同的 Null 处理策略，
        // 避免每次序列化时通过反射导致极大的性能消耗和父类字段丢失的问题。
        objectMapper.setSerializerFactory(
                objectMapper.getSerializerFactory().withSerializerModifier(new GxBeanSerializerModifier())
        );

        return objectMapper;
    }

    /**
     * 配置Bean验证工厂，支持配置驱动的快速失败模式
     *
     * @return 配置好的LocalValidatorFactoryBean实例
     */
    @Bean
    public LocalValidatorFactoryBean localValidatorFactoryBean() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.getValidationPropertyMap().put("hibernate.validator.fail_fast", failFast);
        return bean;
    }

    /**
     * 自定义 Bean 序列化修饰器，针对不同类型注册相应的 Null 序列化器
     */
    public static class GxBeanSerializerModifier extends BeanSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
            for (BeanPropertyWriter writer : beanProperties) {
                // 判断字段的实际类型
                Class<?> clazz = writer.getType().getRawClass();
                if (CharSequence.class.isAssignableFrom(clazz)) {
                    writer.assignNullSerializer(new NullStringJsonSerializer());
                } else if (Collection.class.isAssignableFrom(clazz)) {
                    writer.assignNullSerializer(new NullCollectionJsonSerializer());
                } else if (Map.class.isAssignableFrom(clazz)) {
                    writer.assignNullSerializer(new NullMapJsonSerializer());
                }
            }
            return beanProperties;
        }
    }

    /**
     * 字符串处理：null 转为 ""
     */
    public static class NullStringJsonSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString("");
        }
    }

    /**
     * 列表处理：由于原逻辑设定为返回 null，在此保留（也可按需改为返回 []）
     */
    public static class NullCollectionJsonSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeNull();
        }
    }

    /**
     * Map与Bean处理：null 转为 {}
     */
    public static class NullMapJsonSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeEndObject();
        }
    }
}
