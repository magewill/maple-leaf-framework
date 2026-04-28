package cn.maple.mongodb.datasource.properties;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("all")
public abstract class GXMongoDynamicDataSourceProperties {
    public Map<String, GXMongoDataSourceProperties> getDatasource() {
        return new HashMap<>();
    }

    public abstract void setDatasource(Map<String, GXMongoDataSourceProperties> datasource);
}
