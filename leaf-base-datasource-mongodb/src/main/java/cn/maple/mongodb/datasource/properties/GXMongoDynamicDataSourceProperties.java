package cn.maple.mongodb.datasource.properties;

import java.util.Collections;
import java.util.Map;

public abstract class GXMongoDynamicDataSourceProperties {
    public Map<String, GXMongoDataSourceProperties> getDatasource() {
        return Collections.emptyMap();
    }

    public abstract void setDatasource(Map<String, GXMongoDataSourceProperties> datasource);
}
