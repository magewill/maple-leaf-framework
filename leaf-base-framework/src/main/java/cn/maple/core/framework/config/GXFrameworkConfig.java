package cn.maple.core.framework.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.config.web.GXHashBasedTableJacksonModule;
import cn.maple.core.framework.config.web.GXQuartetJacksonModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import lombok.extern.slf4j.Slf4j;
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

/**
 * 框架核心配置类
 * <p>
 * 该配置类提供了以下功能：
 * 1. 自定义JSON序列化处理，特别是对null值的处理策略
 * 2. Bean验证配置，支持快速失败模式
 * 3. 组件自动扫描配置
 * <p>
 * 线程安全说明：
 * - ObjectMapper配置为无状态Bean，可安全地在多线程环境中使用
 * - 自定义的JsonSerializer实现是无状态的，每次调用都会创建新的序列化上下文
 * <p>
 * 使用示例：
 * <pre>
 * // 在Spring Boot应用中自动装配
 * @Autowired
 * private ObjectMapper objectMapper;
 *
 * // 使用配置的ObjectMapper进行序列化
 * String json = objectMapper.writeValueAsString(someObject);
 * </pre>
 *
 * @author britton chen <britton@126.com>
 */
@Component
@ComponentScan({"cn.maple"})
@Slf4j
public class GXFrameworkConfig {
    /**
     * 配置自定义的ObjectMapper，用于处理JSON序列化
     * <p>
     * 该Bean提供了以下特性：
     * 1. 自定义null值处理策略，根据字段类型返回不同的默认值
     * 2. 忽略未知属性，提高反序列化的健壮性
     * 3. 允许序列化空Bean对象
     * <p>
     * 线程安全说明：
     * - ObjectMapper实例是线程安全的，可以在多线程环境中共享使用
     * - 自定义序列化器在每次序列化调用时都会创建新的上下文，不存在状态共享问题
     *
     * @param builder Jackson2ObjectMapperBuilder实例，由Spring自动注入
     * @return 配置好的ObjectMapper实例
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        final ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        final SerializerProvider serializerProvider = objectMapper.getSerializerProvider();
        // 1. 首先，我们创建了一个匿名内部类，实现了JsonSerializer接口。
        // 2. 然后，我们重写了serialize()方法，该方法在序列化时被调用。
        // 3. 在serialize()方法中，我们首先获取了当前字段的名称，
        //    然后使用jsonGenerator.currentValue()方法获取当前值。  
        // 4. 接着，我们判断了当前值是否为null。如果是null，
        //    我们使用jsonGenerator.writeNull()方法将null值写入JSON。
        // 5. 如果当前值不为null，我们使用field.getType()方法获取字段的类型。
        // 6. 然后，我们使用if-else语句判断字段类型。
        // 7. 如果字段类型是CharSequence（如String），
        //    我们使用jsonGenerator.writeString("")方法将空字符串写入JSON。 
        // 8. 如果字段类型是Collection（如List），
        //    我们使用jsonGenerator.writeStartArray()和jsonGenerator.writeEndArray()方法将空数组写入JSON。
        // 9. 如果字段类型是Map（如HashMap），
        //    我们使用jsonGenerator.writeStartObject()和jsonGenerator.writeEndObject()方法将空对象写入JSON。    
        // 10. 如果字段类型是其他类型，我们使用jsonGenerator.writeNull()方法将null值写入JSON。
        // 11. 最后，我们返回配置好的ObjectMapper实例。
        // 这样，我们就完成了对ObjectMapper的配置，
        // 并实现了对null值的自定义处理策略。
        // 注：如果字段类型是其他类型，我们使用jsonGenerator.writeNull()方法将null值写入JSON。
        serializerProvider.setNullValueSerializer(new JsonSerializer<>() {
            @Override
            public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                String fieldName = jsonGenerator.getOutputContext().getCurrentName();
                if (CharSequenceUtil.isNotEmpty(fieldName)) {
                    try {
                        // 安全地获取当前值，避免NPE
                        Object currentValue = jsonGenerator.currentValue();
                        if (currentValue == null) {
                            jsonGenerator.writeNull();
                            return;
                        }

                        // 反射获取字段类型
                        Field field = currentValue.getClass().getDeclaredField(fieldName);
                        if (CharSequence.class.isAssignableFrom(field.getType())) {
                            // 字符串型空值""
                            jsonGenerator.writeString("");
                            return;
                        } else if (Collection.class.isAssignableFrom(field.getType())) {
                            // 列表型空值返回[]
                            // jsonGenerator.writeStartArray();
                            // jsonGenerator.writeEndArray();
                            // 列表型空值返回null
                            // 注：也可以返回空数组，取决于业务需求
                            jsonGenerator.writeNull();
                            return;
                        } else if (Map.class.isAssignableFrom(field.getType())) {
                            // map型空值或者bean对象返回"{}"
                            jsonGenerator.writeStartObject();
                            jsonGenerator.writeEndObject();
                            return;
                        }
                    } catch (NoSuchFieldException noSuchFieldException) {
                        // 字段不存在时记录日志，但不影响序列化过程
                        log.debug("字段 '{}' 不存在: {}", fieldName, noSuchFieldException.getMessage());
                    } catch (SecurityException | IllegalArgumentException e) {
                        // 处理其他反射相关异常
                        log.debug("反射获取字段类型失败: {}", e.getMessage());
                    }
                    // 默认返回null
                    jsonGenerator.writeNull();
                }
            }
        });
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 添加自定义模块 处理guava的HashBasedTable数据类型
        objectMapper.registerModule(GXHashBasedTableJacksonModule.createModule());
        // 添加自定义模块 处理quartet数据类型
        objectMapper.registerModule(GXQuartetJacksonModule.createModule());
        return objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    /**
     * 配置Bean验证工厂，支持快速失败模式
     * <p>
     * 在快速失败模式下，验证过程会在发现第一个验证错误后立即停止，
     * 而不是收集所有的验证错误。这对于提高验证性能很有帮助。
     * <p>
     * 线程安全说明：
     * - LocalValidatorFactoryBean是线程安全的，可以在多线程环境中共享使用
     * - 验证过程在每次调用时都会创建新的上下文，不存在状态共享问题
     *
     * @return 配置好的LocalValidatorFactoryBean实例
     */
    @Bean
    public LocalValidatorFactoryBean localValidatorFactoryBean() {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        // 1. 首先，我们创建了一个LocalValidatorFactoryBean实例。
        // 2. 然后，我们使用getValidationPropertyMap()方法获取一个Map对象，
        //    该Map对象用于存储验证器的属性。
        // 3. 接着，我们使用put()方法将"hibernate.validator.fail_fast"属性设置为"true"。
        // 4. 最后，我们返回配置好的LocalValidatorFactoryBean实例。
        // 这样，我们就完成了对验证器的配置，将快速失败模式设置为"true"。
        bean.getValidationPropertyMap().put("hibernate.validator.fail_fast", "true");
        return bean;
    }
}
