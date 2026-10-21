package cn.maple.core.datasource.it;

import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.datasource.properties.GXDynamicDataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local-only dynamic datasource properties shared by the integration test contexts.
 *
 * <p>The production local bean is disabled when Nacos classes are on the classpath, which is the
 * case for this module's test scope.</p>
 */
@ConfigurationProperties(prefix = "dynamic")
public class TestDynamicDataSourceProperties extends GXDynamicDataSourceProperties {
    private final Map<String, GXDataSourceProperties> datasource = new LinkedHashMap<>();

    @Override
    public Map<String, GXDataSourceProperties> getDatasource() {
        return datasource;
    }
}
