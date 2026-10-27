package cn.maple.canal.properties.local;

import cn.maple.canal.properties.GXCanalProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Canal properties sourced from the application environment (local configuration files).
 *
 * <p>The bean is created by {@code GXCanalPropertiesConfig}; this class no longer takes part in
 * component scanning so that the configuration source becomes an explicit, testable decision.</p>
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "canal")
public class GXLocalCanalProperties extends GXCanalProperties {
    public GXLocalCanalProperties() {
        log.info("Canal config source: LOCAL");
    }
}
