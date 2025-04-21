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
 * <p>工作原理：</p>
 * <p>1. 装饰器模式：包装GXMybatisPlusRedissonCache，增强其功能</p>
 * <p>2. 透明代理：对所有缓存操作进行日志记录，但不改变原有功能</p>
 * <p>3. 性能监控：记录缓存命中次数、请求次数，计算命中率</p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 在Mapper接口上配置缓存
 * @CacheNamespace(implementation = GXLoggingRedisCache.class, eviction = LruCache.class, flushInterval = 300000)
 * public interface UserMapper extends BaseMapper<UserEntity> {
 *     // 接口方法...
 * }
 * </pre>
 * 
 * <p>注意事项：</p>
 * <p>1. 确保已正确配置Redisson客户端</p>
 * <p>2. 缓存的对象必须实现Serializable接口</p>
 * <p>3. 合理设置flushInterval以避免缓存过期问题</p>
 * <p>4. 在高并发环境下，缓存日志可能会很多，建议适当配置日志级别</p>
 * 
 * @author 塵渊  britton@126.com
 */
@Slf4j
public class GXLoggingRedisCache extends LoggingCache {
    /**
     * 构造函数，二级缓存必须提供id的构造函数
     * <p>
     * 创建一个带日志记录功能的Redis缓存实例，使用GXMybatisPlusRedissonCache作为实际的缓存实现
     * </p>
     *
     * @param id 缓存的唯一标识符，通常是Mapper的完全限定名
     */
    public GXLoggingRedisCache(String id) {
        super(new GXMybatisPlusRedissonCache(id));
        log.debug("初始化MyBatis Redis日志缓存: {}", id);
    }
}
