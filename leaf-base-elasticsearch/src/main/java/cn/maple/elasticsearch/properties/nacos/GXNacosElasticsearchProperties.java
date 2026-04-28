package cn.maple.elasticsearch.properties.nacos;

import cn.maple.elasticsearch.properties.GXElasticsearchProperties;
import cn.maple.elasticsearch.properties.GXElasticsearchSourceProperties;
import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 每个应用自定义的环境配置文件
 */
@Data
@Slf4j
@Component
@Primary
@SuppressWarnings("all")
@EqualsAndHashCode(callSuper = true)
@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@ConditionalOnExpression("!'${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:}}'.isBlank()")
@NacosConfigurationProperties(dataId = "elasticsearch.yml",
        groupId = "${spring.cloud.nacos.config.group:${nacos.config.group:}}",
        properties = @NacosProperties(
                serverAddr = "${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:}}",
                namespace = "${spring.cloud.nacos.config.namespace:${nacos.config.namespace:}}",
                username = "${spring.cloud.nacos.username:${nacos.config.username:}}",
                password = "${spring.cloud.nacos.password:${nacos.config.password:}}"))
@ConfigurationProperties(prefix = "elasticsearch")
public class GXNacosElasticsearchProperties extends GXElasticsearchSourceProperties {
    private Map<String, GXElasticsearchProperties> datasource = new LinkedHashMap<>();

    public GXNacosElasticsearchProperties() {
        log.info("Elasticsearch数据源的配置使用的是NACOS配置");
    }

    @Override
    public Map<String, GXElasticsearchProperties> getDatasource() {
        return datasource;
    }
}
