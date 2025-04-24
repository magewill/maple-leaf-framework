package cn.maple.core.framework.factory;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXLoggerUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * <p>
 * YAML配置文件解析工厂类
 * </p>
 * 
 * <p>
 * 该工厂类扩展了Spring的DefaultPropertySourceFactory，提供对YAML格式配置文件的解析支持。
 * 主要用于在Spring应用中加载自定义的YAML配置文件，支持通过@PropertySource注解引入YAML配置。
 * </p>
 * 
 * <p>
 * 功能特点：
 * 1. 支持YAML格式配置文件的加载和解析
 * 2. 优雅处理配置文件不存在的情况，提供友好的日志提示
 * 3. 与Spring的配置体系无缝集成
 * 4. 支持多文档YAML文件（返回第一个文档的配置）
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 在配置类上使用@PropertySource注解引入YAML配置文件
 * @Configuration
 * @PropertySource(value = "classpath:custom-config.yml", factory = GXYamlPropertySourceFactory.class)
 * public class AppConfig {
 *     @Value("${custom.property}")
 *     private String customProperty;
 *     
 *     // ...
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 注意事项：
 * - 确保YAML文件格式正确，否则可能导致解析失败
 * - 对于不存在的文件会记录警告日志，但不会中断应用启动
 * - 多文档YAML文件只会加载第一个文档的配置
 * </p>
 * 
 * @author zj chen <britton@126.com>
 */
@Slf4j
public class GXYamlPropertySourceFactory extends DefaultPropertySourceFactory {
    @Override
    @NonNull
    public PropertySource<?> createPropertySource(@Nullable String name, @NonNull EncodedResource resource) throws IOException {
        if (!resource.getResource().exists()) {
            GXLoggerUtils.logInfo(log, CharSequenceUtil.format("{}文件不存在,请创建该文件!", resource.getResource().getFilename()));
            return super.createPropertySource(name, resource);
        }
        List<PropertySource<?>> propertySourceList = new YamlPropertySourceLoader().load(resource.getResource().getFilename(), resource.getResource());
        if (!propertySourceList.isEmpty()) {
            return propertySourceList.iterator().next();
        }
        return super.createPropertySource(name, resource);
    }
}