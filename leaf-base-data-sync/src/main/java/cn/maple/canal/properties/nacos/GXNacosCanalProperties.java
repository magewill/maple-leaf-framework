package cn.maple.canal.properties.nacos;

import cn.maple.canal.properties.GXCanalProperties;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Component
@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})
@NacosConfigurationProperties(groupId = "${nacos.config.group:DEFAULT_GROUP}", prefix = "canal", dataId = "canal.yml", autoRefreshed = true, type = ConfigType.YAML)
public class GXNacosCanalProperties extends GXCanalProperties {
    public GXNacosCanalProperties() {
        log.info("Canal config source: NACOS");
    }
}
