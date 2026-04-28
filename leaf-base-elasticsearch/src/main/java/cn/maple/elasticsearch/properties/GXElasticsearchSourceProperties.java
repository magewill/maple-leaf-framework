package cn.maple.elasticsearch.properties;

import java.util.Map;

public abstract class GXElasticsearchSourceProperties {
    public abstract Map<String, GXElasticsearchProperties> getDatasource();
}
