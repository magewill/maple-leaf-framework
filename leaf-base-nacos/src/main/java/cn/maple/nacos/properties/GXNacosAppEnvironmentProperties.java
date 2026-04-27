package cn.maple.nacos.properties;

import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 每个应用自定义的环境配置文件
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
@ConfigurationProperties(prefix = "")
public class GXNacosAppEnvironmentProperties {
    /*
     * 此类体有意保持为空。
     *
     * 设计原则：配置的"发现"（从 Nacos 加载进 Environment）与配置的"消费"（绑定到类型安全 Bean）
     * 分离到不同的类中，遵循单一职责原则。
     *
     * 如需绑定特定配置项，请在对应业务模块中声明专属的 @ConfigurationProperties 类，例如：
     *
     *   @Component
     *   @ConfigurationProperties(prefix = "app.feature")
     *   public class AppFeatureProperties {
     *       private boolean enableNewPayment;
     *       // getter / setter ...
     *   }
     */
}
