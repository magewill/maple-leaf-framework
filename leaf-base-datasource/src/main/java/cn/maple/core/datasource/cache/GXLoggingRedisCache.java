package cn.maple.core.datasource.cache;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.decorators.LoggingCache;

/**
 * MyBatis Redis缓存日志装饰器
 * <p>
 * 该类是对GXMybatisPlusRedissonCache的装饰，为Redis缓存操作添加日志记录功能。
 * 通过继承MyBatis的LoggingCache类，可以自动记录缓存的命中率和访问统计信息，
 * 有助于监控和优化缓存性能。
 * </p>
 *
 * @author 塵渊  britton@126.com
 */
@Slf4j
public class GXLoggingRedisCache extends LoggingCache {
    /**
     * 构造函数，二级缓存必须提供id的构造函数
     *
     * @param id 缓存的唯一标识符，通常是Mapper的完全限定名
     */
    public GXLoggingRedisCache(String id) {
        super(new GXMybatisPlusRedissonCache(id));
        log.debug("初始化MyBatis Redis日志缓存: {}", id);
    }
}
