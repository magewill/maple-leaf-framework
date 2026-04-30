package cn.maple.core.datasource.properties.local;

import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.datasource.properties.GXDynamicDataSourceProperties;
import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Slf4j
@Component
@EqualsAndHashCode(callSuper = true)
@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@PropertySource(value = {"classpath:/${spring.profiles.active:dev}/datasource.yml"}, factory = GXYamlPropertySourceFactory.class, encoding = "utf-8", ignoreResourceNotFound = false)
@ConfigurationProperties(prefix = "dynamic")
public class GXLocalDynamicDataSourceProperties extends GXDynamicDataSourceProperties {
    private Map<String, GXDataSourceProperties> datasource = new LinkedHashMap<>();

    public GXLocalDynamicDataSourceProperties() {
        log.info("MySQL 数据源配置使用本地配置");
    }

    @Override
    public Map<String, GXDataSourceProperties> getDatasource() {
        return datasource;
    }
}
