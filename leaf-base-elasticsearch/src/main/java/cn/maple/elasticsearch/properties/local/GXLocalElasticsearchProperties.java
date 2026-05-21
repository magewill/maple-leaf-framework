package cn.maple.elasticsearch.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.elasticsearch.properties.GXElasticsearchProperties;
import cn.maple.elasticsearch.properties.GXElasticsearchSourceProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Local Elasticsearch configuration source. */
@Data
@Slf4j
@SuppressWarnings("all")
@EqualsAndHashCode(callSuper = true)
@Component
@ConditionalOnExpression("'${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:}}'.isBlank()")
@PropertySource(value = {"classpath:/${spring.profiles.active:default}/elasticsearch.yml"}, factory = GXYamlPropertySourceFactory.class, ignoreResourceNotFound = false)
@ConfigurationProperties(prefix = "elasticsearch")
public class GXLocalElasticsearchProperties extends GXElasticsearchSourceProperties {
    private Map<String, GXElasticsearchProperties> datasource = new LinkedHashMap<>();

    public GXLocalElasticsearchProperties() {
        log.info("Using local Elasticsearch configuration");
    }

    @Override
    public Map<String, GXElasticsearchProperties> getDatasource() {
        return datasource;
    }
}
