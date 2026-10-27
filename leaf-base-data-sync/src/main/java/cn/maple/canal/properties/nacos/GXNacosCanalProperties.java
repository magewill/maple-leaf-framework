package cn.maple.canal.properties.nacos;

import cn.maple.canal.properties.GXCanalProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Canal properties whose values are expected to arrive from a Nacos configuration source.
 *
 * <p>Binding itself always goes through {@link ConfigurationProperties}; whether Nacos supplies the
 * values depends on the application configuration import. The bean is created by
 * {@code GXCanalPropertiesConfig}.</p>
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "canal")
public class GXNacosCanalProperties extends GXCanalProperties {
    public GXNacosCanalProperties() {
        log.info("Canal config source: NACOS");
    }
}
