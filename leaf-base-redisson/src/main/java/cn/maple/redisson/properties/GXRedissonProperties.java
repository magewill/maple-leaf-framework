package cn.maple.redisson.properties;

import java.util.Map;

public abstract class GXRedissonProperties {
    public abstract Map<String, GXRedissonConnectProperties> getConfig();
}
