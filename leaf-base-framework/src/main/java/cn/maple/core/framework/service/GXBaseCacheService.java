package cn.maple.core.framework.service;

import java.util.concurrent.TimeUnit;

/**
 * 基础缓存服务接口
 * <p>
 * 该接口提供了一套统一的缓存操作API，支持多种缓存实现（如Redis、Memcached、本地缓存等）。
 * 采用桶（Bucket）的概念进行缓存隔离，不同业务模块可以使用不同的桶来存储数据，避免键名冲突。
 * </p>
 *
 * <p>线程安全说明：</p>
 * <p>该接口的实现类应当保证线程安全，因为缓存服务通常在多线程环境下使用。</p>
 *
 * <p>性能考虑：</p>
 * <p>1. 缓存操作应当高效，尤其是读操作</p>
 * <p>2. 实现类应当考虑连接池管理，避免频繁创建和销毁连接</p>
 * <p>3. 对于高频访问的数据，可以考虑使用多级缓存策略</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * @Service
 * public class UserService {
 *     @Autowired
 *     private GXBaseCacheService cacheService;
 *
 *     private static final String USER_CACHE_BUCKET = "user";
 *
 *     public UserEntity getUserById(Long userId) {
 *         // 先尝试从缓存获取
 *         String cacheKey = "user:" + userId;
 *         Object cachedUser = cacheService.getCache(USER_CACHE_BUCKET, cacheKey);
 *         if (cachedUser != null) {
 *             return (UserEntity) cachedUser;
 *         }
 *
 *         // 缓存未命中，从数据库查询
 *         UserEntity user = userMapper.selectById(userId);
 *         if (user != null) {
 *             // 设置缓存，有效期30分钟
 *             cacheService.setCache(USER_CACHE_BUCKET, cacheKey, user, 30, TimeUnit.MINUTES);
 *         }
 *         return user;
 *     }
 *
 *     public void updateUser(UserEntity user) {
 *         // 更新数据库
 *         userMapper.updateById(user);
 *         // 更新缓存
 *         String cacheKey = "user:" + user.getId();
 *         cacheService.setCache(USER_CACHE_BUCKET, cacheKey, user, 30, TimeUnit.MINUTES);
 *     }
 *
 *     public void deleteUser(Long userId) {
 *         // 删除数据库记录
 *         userMapper.deleteById(userId);
 *         // 删除缓存
 *         String cacheKey = "user:" + userId;
 *         cacheService.deleteCache(USER_CACHE_BUCKET, cacheKey);
 *     }
 * }
 * </pre>
 *
 * @author britton <britton@126.com>
 */
public interface GXBaseCacheService {
    /**
     * 设置缓存（对象类型，有过期时间）
     * <p>
     * 将对象类型的值存入指定桶的缓存中，并设置过期时间。
     * 适用于需要缓存Java对象且需要自动过期的场景。
     * </p>
     * <p>
     * 性能考虑：
     * 1. 对象会被序列化后存储，可能影响性能
     * 2. 大对象应考虑压缩或拆分存储
     * </p>
     *
     * @param bucketName 数据桶的名字，用于隔离不同业务的缓存，通常来源于CacheConstant常量类
     * @param key        缓存的键名，应当具有业务含义且确保在桶内唯一
     * @param value      要缓存的对象值，可以是任意可序列化的对象
     * @param expired    过期时间的数值，与timeUnit配合使用
     * @param timeUnit   时间单位，如TimeUnit.SECONDS、TimeUnit.MINUTES等
     * @return 缓存操作的结果，通常是操作是否成功的标识
     */
    Object setCache(String bucketName, String key, Object value, int expired, TimeUnit timeUnit);

    /**
     * 设置缓存（对象类型，永久有效）
     * <p>
     * 将对象类型的值存入指定桶的缓存中，不设置过期时间（永久有效或使用默认过期策略）。
     * 适用于需要长期缓存且不会频繁变化的数据。
     * </p>
     * <p>
     * 注意事项：
     * 1. 永久缓存可能导致内存占用持续增长，应谨慎使用
     * 2. 建议为重要数据设置合理的过期时间，避免数据不一致
     * </p>
     *
     * @param bucketName 数据桶的名字，用于隔离不同业务的缓存
     * @param key        缓存的键名，应当具有业务含义且确保在桶内唯一
     * @param value      要缓存的对象值，可以是任意可序列化的对象
     * @return 缓存操作的结果，通常是操作是否成功的标识
     */
    Object setCache(String bucketName, String key, Object value);

    /**
     * 设置缓存（字符串类型，有过期时间）
     * <p>
     * 将字符串类型的值存入指定桶的缓存中，并设置过期时间。
     * 适用于需要缓存简单字符串数据且需要自动过期的场景。
     * </p>
     * <p>
     * 性能考虑：
     * 字符串类型通常比对象类型的存取更高效，因为不需要序列化/反序列化
     * </p>
     *
     * @param bucketName 数据桶的名字，用于隔离不同业务的缓存
     * @param key        缓存的键名，应当具有业务含义且确保在桶内唯一
     * @param value      要缓存的字符串值
     * @param expired    过期时间的数值，与timeUnit配合使用
     * @param timeUnit   时间单位，如TimeUnit.SECONDS、TimeUnit.MINUTES等
     * @return 缓存操作的结果，通常是操作是否成功的标识
     */
    Object setCache(String bucketName, String key, String value, int expired, TimeUnit timeUnit);

    /**
     * 设置缓存（字符串类型，永久有效）
     * <p>
     * 将字符串类型的值存入指定桶的缓存中，不设置过期时间（永久有效或使用默认过期策略）。
     * 适用于需要长期缓存的简单字符串数据。
     * </p>
     * <p>
     * 使用场景：
     * 1. 系统配置信息
     * 2. 静态数据
     * 3. 基础字典数据
     * </p>
     *
     * @param bucketName 数据桶的名字，用于隔离不同业务的缓存
     * @param key        缓存的键名，应当具有业务含义且确保在桶内唯一
     * @param value      要缓存的字符串值
     * @return 缓存操作的结果，通常是操作是否成功的标识
     */
    Object setCache(String bucketName, String key, String value);

    /**
     * 获取缓存数据
     * <p>
     * 根据桶名和键名获取缓存中存储的数据。
     * 如果数据不存在或已过期，则返回null。
     * </p>
     * <p>
     * 性能考虑：
     * 1. 缓存读取通常是高频操作，应确保实现高效
     * 2. 对于对象类型，需要进行反序列化，可能影响性能
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取用户缓存
     * Object userCache = cacheService.getCache("user", "user:10001");
     * if (userCache != null) {
     *     UserEntity user = (UserEntity) userCache;
     *     // 使用缓存数据...
     * } else {
     *     // 缓存未命中，从数据库加载...
     * }
     * </pre>
     * </p>
     *
     * @param bucketName 数据桶的名字，用于隔离不同业务的缓存
     * @param key        缓存的键名
     * @return 缓存的值，如果不存在则返回null
     */
    Object getCache(String bucketName, String key);

    /**
     * 删除指定的缓存数据
     * <p>
     * 根据桶名和键名删除缓存中的数据。
     * 如果数据不存在，通常不会报错，但返回值可能不同。
     * </p>
     * <p>
     * 使用场景：
     * 1. 数据更新后清除旧缓存
     * 2. 数据删除后清除相关缓存
     * 3. 手动使缓存失效
     * </p>
     * <p>
     * 注意事项：
     * 在分布式环境中，应确保所有节点的缓存一致性
     * </p>
     *
     * @param bucketName 数据桶的名字，用于隔离不同业务的缓存
     * @param key        缓存的键名
     * @return 被删除的缓存值，某些实现可能返回操作结果而非值
     */
    Object deleteCache(String bucketName, String key);

    /**
     * 获取缓存剩余有效时长
     * <p>
     * 查询指定缓存键的剩余有效时间。
     * 对于永久有效的键，不同实现可能返回-1或特定的大值。
     * </p>
     * <p>
     * 使用场景：
     * 1. 判断缓存是否即将过期
     * 2. 根据剩余时间决定是否刷新缓存
     * 3. 监控缓存状态
     * </p>
     *
     * @param bucketName 缓存桶名字
     * @param keyName    缓存键名
     * @return 剩余有效时长（通常以秒为单位），如果键不存在则可能返回0或-1
     */
    Long getCacheRemainTimeToLive(String bucketName, String keyName);

    /**
     * 更新缓存有效时长
     * <p>
     * 根据当前剩余时间和阈值判断是否需要更新缓存的过期时间。
     * 如果剩余时间小于阈值，则将过期时间重置为指定值。
     * </p>
     * <p>
     * 使用场景：
     * 1. 实现滑动过期策略，活跃使用的缓存自动延长过期时间
     * 2. 会话续期，如用户登录状态维持
     * 3. 分布式锁的续期
     * </p>
     * <p>
     * 实现说明：
     * 1. 先获取剩余有效时间
     * 2. 如果小于阈值，则更新过期时间
     * 3. 返回更新操作的结果
     * </p>
     *
     * @param bucketName       缓存桶名字
     * @param keyName          缓存键名
     * @param expired          新的过期时间，单位：秒
     * @param refreshThreshold 刷新阈值，如果剩余时间小于该值则刷新过期时间
     * @return 是否成功更新过期时间
     */
    boolean updateCacheExpiredTime(String bucketName, String keyName, Integer expired, Integer refreshThreshold);

    /**
     * 清空指定桶中的所有数据
     * <p>
     * 删除指定缓存桶中的所有键值对。
     * 这是一个批量操作，可能影响性能，应谨慎使用。
     * </p>
     * <p>
     * 使用场景：
     * 1. 系统重置或初始化
     * 2. 全量数据更新后刷新缓存
     * 3. 缓存紧急清理
     * </p>
     * <p>
     * 注意事项：
     * 1. 这是一个危险操作，会删除整个桶的数据，应谨慎使用
     * 2. 在生产环境中应设置权限控制
     * 3. 可能影响系统性能，建议在低峰期执行
     * </p>
     *
     * @param bucketName 缓存桶名字
     */
    void clear(String bucketName);

    /**
     * 查询桶中键的数量
     * <p>
     * 获取指定缓存桶中键值对的总数。
     * 可用于监控缓存使用情况和容量规划。
     * </p>
     * <p>
     * 使用场景：
     * 1. 缓存监控
     * 2. 容量规划
     * 3. 缓存使用统计
     * </p>
     * <p>
     * 性能考虑：
     * 对于大型缓存，此操作可能较为耗时，应避免频繁调用
     * </p>
     *
     * @param bucketName 缓存桶名字
     * @return 桶中键的数量
     */
    Integer size(String bucketName);

    /**
     * 检查指定的键是否存在
     * <p>
     * 判断指定的缓存桶中是否包含指定的键。
     * 通常比获取值更高效，适用于只需要判断存在性的场景。
     * </p>
     * <p>
     * 使用场景：
     * 1. 缓存命中率检查
     * 2. 避免重复操作
     * 3. 数据存在性验证
     * </p>
     * <p>
     * 与getCache的区别：
     * - exists只检查键是否存在，不返回值
     * - getCache返回键对应的值，如果不存在则返回null
     * - 通常exists性能更好，特别是对于大对象
     * </p>
     *
     * @param bucketName 桶名字
     * @param key        指定的键名
     * @return 如果键存在则返回true，否则返回false
     */
    boolean exists(String bucketName, String key);
}
