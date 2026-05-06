package cn.maple.core.framework.factory;

import cn.maple.core.framework.util.GXLoggerUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;

import java.io.IOException;
import java.util.List;

@Slf4j
public class GXYamlPropertySourceFactory extends DefaultPropertySourceFactory {
    @Override
    @NonNull
    public PropertySource<?> createPropertySource(@Nullable String name, @NonNull EncodedResource resource) throws IOException {
        if (!resource.getResource().exists()) {
            GXLoggerUtils.logInfo(log, "Property source file does not exist: " + resource.getResource().getFilename());
            return super.createPropertySource(name, resource);
        }
        String sourceName = name == null || name.isBlank() ? resource.getResource().getFilename() : name;
        assert sourceName != null;
        List<PropertySource<?>> propertySourceList = new YamlPropertySourceLoader().load(sourceName, resource.getResource());
        if (!propertySourceList.isEmpty()) {
            return propertySourceList.getFirst();
        }
        return super.createPropertySource(name, resource);
    }
}
