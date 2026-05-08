package cn.maple.mongodb.datasource.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.mongodb.datasource.properties.GXMongoDataSourceProperties;
import cn.maple.mongodb.datasource.properties.GXMongoDynamicDataSourceProperties;
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
@PropertySource(value = {"classpath:/${spring.profiles.active}/mongodb.yml"}, factory = GXYamlPropertySourceFactory.class, ignoreResourceNotFound = false)
@ConfigurationProperties(prefix = "mongodb")
//@ConfigurationProperties(prefix = "spring.data.mongodb") 可以直接使用MongoTemplate对象  不需要在GXMongoConfig中进行配置
public class GXLocalMongoDynamicDataSourceProperties extends GXMongoDynamicDataSourceProperties /*MongoProperties*/ {
    private Map<String, GXMongoDataSourceProperties> datasource = new LinkedHashMap<>();

    public GXLocalMongoDynamicDataSourceProperties() {
        log.info("Use local MongoDB datasource configuration");
    }

    @Override
    public Map<String, GXMongoDataSourceProperties> getDatasource() {
        return datasource;
    }
}
