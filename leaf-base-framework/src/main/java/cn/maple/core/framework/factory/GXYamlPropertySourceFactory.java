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