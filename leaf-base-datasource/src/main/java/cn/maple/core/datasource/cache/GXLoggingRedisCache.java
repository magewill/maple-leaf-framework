package cn.maple.core.datasource.cache;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.decorators.LoggingCache;

@Slf4j
public class GXLoggingRedisCache extends LoggingCache {
    public GXLoggingRedisCache(String id) {
        super(new GXMybatisPlusRedissonCache(id));
        log.debug("Initialized MyBatis logging Redis cache: id={}", id);
    }
}
