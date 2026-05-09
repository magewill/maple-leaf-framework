package cn.maple.redisson.properties;

import java.util.Map;

public abstract class GXRedissonMQProperties {
    public abstract Map<String, GXRedissonConnectProperties> getConfig();
}
