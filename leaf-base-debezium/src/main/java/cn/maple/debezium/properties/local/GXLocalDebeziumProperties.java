package cn.maple.debezium.properties.local;

import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import cn.maple.debezium.properties.GXDebeziumProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Slf4j
@Component
@EqualsAndHashCode(callSuper = true)
@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@PropertySource(value = {"classpath:/${spring.profiles.active}/debezium.yml"}, factory = GXYamlPropertySourceFactory.class, encoding = "utf-8", ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "debezium")
public class GXLocalDebeziumProperties extends GXDebeziumProperties {
    private Map<String, String> config = new LinkedHashMap<>();

    public GXLocalDebeziumProperties() {
        log.info("Debezium config source: local");
    }

    @Override
    public Map<String, String> getConfig() {
        return config == null ? Collections.emptyMap() : config;
    }
}
