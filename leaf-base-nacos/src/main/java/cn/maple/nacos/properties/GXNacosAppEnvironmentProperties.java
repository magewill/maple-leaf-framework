package cn.maple.nacos.properties;

import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Loads application environment configuration from Nacos.
 */
@Component
@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@NacosConfigurationProperties(
        dataId = "app-environment.yml",
        groupId = "${spring.cloud.nacos.config.group:${nacos.config.group:DEFAULT_GROUP}}",
        properties = @NacosProperties(
                serverAddr = "${spring.cloud.nacos.config.server-addr:${nacos.config.server-addr:127.0.0.1:8848}}",
                namespace = "${spring.cloud.nacos.config.namespace:${nacos.config.namespace:}}",
                username = "${spring.cloud.nacos.username:${nacos.config.username:}}",
                password = "${spring.cloud.nacos.password:${nacos.config.password:}}"),
        autoRefreshed = true)
// prefix = "" 表示从 YAML 根路径绑定，配置直接进入 Spring Environment
// ignoreUnknownFields = true（默认值）：YAML 中存在未声明字段时不抛异常，与空类体配合
// ignoreInvalidFields = false（默认值）：类型不匹配时仍快速失败，保留错误可见性
// @ConfigurationProperties(prefix = "")
public class GXNacosAppEnvironmentProperties {
    /*
     * This class is intentionally empty. It declares where app-environment.yml
     * is loaded from; typed settings should be declared by dedicated
     * @ConfigurationProperties classes in the module that owns those settings.
     */
}
