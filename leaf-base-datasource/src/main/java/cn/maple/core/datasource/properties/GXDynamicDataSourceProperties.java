package cn.maple.core.datasource.properties;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("all")
public abstract class GXDynamicDataSourceProperties {
    public Map<String, GXDataSourceProperties> getDatasource() {
        return new HashMap<>();
    }
}
