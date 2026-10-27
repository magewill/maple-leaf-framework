package cn.maple.canal.config;

import cn.maple.canal.constant.GXCanalConstant;
import cn.maple.canal.properties.GXCanalConfigSourceValidator;
import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.canal.properties.local.GXLocalCanalProperties;
import cn.maple.canal.properties.nacos.GXNacosCanalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates exactly one {@link GXCanalProperties} bean.
 *
 * <p>{@code canal.config-source} accepts {@code local} and {@code nacos}. When it is absent the
 * historical behaviour is preserved: the Nacos variant is used only if the Nacos configuration
 * annotation is on the class path, otherwise the local variant is used. Both variants bind through
 * standard Spring Boot configuration binding, so external configuration files and
 * {@code spring.config.import} based Nacos imports take effect.</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class GXCanalPropertiesConfig {
    private static final String NACOS_PROPERTIES_CLASS =
            "com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties";

    public GXCanalPropertiesConfig() {
        log.info(GXCanalConstant.ASSEMBLY_RESTART_HINT);
    }

    /**
     * Explicit local source.
     *
     * @return local properties bean
     */
    @Bean
    @ConditionalOnProperty(prefix = "canal", name = "config-source", havingValue = "local")
    public GXLocalCanalProperties localCanalProperties() {
        log.info("Canal config source resolved: LOCAL (explicit)");
        return new GXLocalCanalProperties();
    }

    /**
     * Default local source used when no Nacos support is present.
     *
     * @return local properties bean
     */
    @Bean
    @ConditionalOnProperty(prefix = "canal", name = "config-source", havingValue = "auto", matchIfMissing = true)
    @ConditionalOnMissingClass(NACOS_PROPERTIES_CLASS)
    public GXLocalCanalProperties autoLocalCanalProperties() {
        log.info("Canal config source resolved: LOCAL (class path detection)");
        return new GXLocalCanalProperties();
    }

    /**
     * Explicit Nacos source.
     *
     * @return Nacos properties bean
     */
    @Bean
    @ConditionalOnProperty(prefix = "canal", name = "config-source", havingValue = "nacos")
    @ConditionalOnClass(name = NACOS_PROPERTIES_CLASS)
    public GXNacosCanalProperties nacosCanalProperties() {
        log.info("Canal config source resolved: NACOS (explicit)");
        return new GXNacosCanalProperties();
    }

    /**
     * Default Nacos source used when Nacos support is present.
     *
     * @return Nacos properties bean
     */
    @Bean
    @ConditionalOnProperty(prefix = "canal", name = "config-source", havingValue = "auto", matchIfMissing = true)
    @ConditionalOnClass(name = NACOS_PROPERTIES_CLASS)
    public GXNacosCanalProperties autoNacosCanalProperties() {
        log.info("Canal config source resolved: NACOS (class path detection)");
        return new GXNacosCanalProperties();
    }

    /**
     * Fails fast when {@code nacos} is requested but Nacos support is missing, instead of silently
     * serving defaults.
     *
     * @return never returns; bean creation fails with an actionable message
     */
    @Bean
    @ConditionalOnProperty(prefix = "canal", name = "config-source", havingValue = "nacos")
    @ConditionalOnMissingClass(NACOS_PROPERTIES_CLASS)
    public GXCanalConfigSourceValidator nacosConfigSourceValidator() {
        return new GXCanalConfigSourceValidator("nacos");
    }
}
