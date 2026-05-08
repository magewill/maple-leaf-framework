package cn.maple.mongodb.datasource.properties.nacos;

import cn.maple.mongodb.datasource.properties.GXMongoDataSourceProperties;
import cn.maple.mongodb.datasource.properties.GXMongoDynamicDataSourceProperties;
import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Component
@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
//@ConfigurationProperties(prefix = "mongodb")
//@ConfigurationProperties(prefix = "spring.data.mongodb")  可以直接使用MongoTemplate对象  不需要在GXMongoConfig中进行配置
@NacosConfigurationProperties(groupId = "${nacos.config.group:DEFAULT_GROUP}",
        prefix = "mongodb",
        dataId = "mongodb.yml",
        properties = @NacosProperties(
                serverAddr = "${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:}}",
                namespace = "${spring.cloud.nacos.config.namespace:${nacos.config.namespace:}}",
                username = "${spring.cloud.nacos.username:${nacos.config.username:}}",
                password = "${spring.cloud.nacos.password:${nacos.config.password:}}"),
        autoRefreshed = true,
        type = ConfigType.YAML)
public class GXNacosMongoDynamicDataSourceProperties extends GXMongoDynamicDataSourceProperties {
    private Map<String, GXMongoDataSourceProperties> datasource = new LinkedHashMap<>();

    public GXNacosMongoDynamicDataSourceProperties() {
        log.info("Use Nacos MongoDB datasource configuration");
    }

    @Override
    public Map<String, GXMongoDataSourceProperties> getDatasource() {
        return datasource;
    }
}
