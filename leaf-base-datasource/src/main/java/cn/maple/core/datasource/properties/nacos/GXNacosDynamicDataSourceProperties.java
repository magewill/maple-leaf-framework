package cn.maple.core.datasource.properties.nacos;

import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.datasource.properties.GXDynamicDataSourceProperties;
import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Slf4j
@Component
@EqualsAndHashCode(callSuper = true)
@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@NacosConfigurationProperties(dataId = "datasource.yml",
        groupId = "${spring.cloud.nacos.config.group:${nacos.config.group:DEFAULT_GROUP}}",
        properties = @NacosProperties(
                serverAddr = "${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:}}",
                namespace = "${spring.cloud.nacos.config.namespace:${nacos.config.namespace:}}",
                username = "${spring.cloud.nacos.username:${nacos.config.username:}}",
                password = "${spring.cloud.nacos.password:${nacos.config.password:}}"))
@ConfigurationProperties(prefix = "dynamic")
public class GXNacosDynamicDataSourceProperties extends GXDynamicDataSourceProperties {
    private Map<String, GXDataSourceProperties> datasource = new LinkedHashMap<>();

    public GXNacosDynamicDataSourceProperties() {
        log.info("MySQL 数据源配置使用 Nacos 配置");
    }

    @Override
    public Map<String, GXDataSourceProperties> getDatasource() {
        return datasource;
    }
}
