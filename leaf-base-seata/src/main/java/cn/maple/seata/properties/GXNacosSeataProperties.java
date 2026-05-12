package cn.maple.seata.properties;

import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

/**
 * Loads Seata configuration from Nacos when Spring Cloud Alibaba Nacos Config is available.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {
        "com.alibaba.cloud.nacos.NacosConfigManager",
        "com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"
})
@NacosConfigurationProperties(
        dataId = "seata.yml",
        groupId = "${spring.cloud.nacos.config.group:${nacos.config.group:}}",
        type = ConfigType.YAML,
        properties = @NacosProperties(
                serverAddr = "${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:}}",
                namespace = "${spring.cloud.nacos.config.namespace:${nacos.config.namespace:}}",
                username = "${spring.cloud.nacos.username:${nacos.config.username:}}",
                password = "${spring.cloud.nacos.password:${nacos.config.password:}}"),
        autoRefreshed = true
)
//@ConfigurationProperties(prefix = "")
public class GXNacosSeataProperties {
}
