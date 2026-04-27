package cn.maple.debezium.properties;

import java.util.Map;

@SuppressWarnings("all")
public abstract class GXDebeziumProperties {
    public abstract Map<String, String> getConfig();
}
