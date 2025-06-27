package cn.maple.debezium.services;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.redisson.services.GXRedissonCacheService;

/**
 * Debezium服务接口
 * <p>
 * 该接口定义了处理Debezium捕获的数据库变更事件的方法，以及在分布式环境下
 * 确保只有一个服务实例处理CDC(变更数据捕获)事件的锁定机制。
 * </p>
 *
 * <p>使用示例:</p>
 * <pre>
 *
 * &#64;Service
 * public class MyDebeziumServiceImpl implements GXDebeziumService {
 *     &#64;Override
 *     public void processCaptureDataChange(Dict data) {
 *         // 获取操作类型 (c:新增, u:更新, d:删除)
 *         String op = data.getStr("op");
 *         // 获取变更前的数据
 *         Dict before = Convert.convert(Dict.class, data.getObj("before"));
 *         // 获取变更后的数据
 *         Dict after = Convert.convert(Dict.class, data.getObj("after"));
 *
 *         // 根据操作类型处理数据
 *         switch (op) {
 *             case "c" -> handleInsert(after);
 *             case "u" -> handleUpdate(before, after);
 *             case "d" -> handleDelete(before);
 *             default -> log.warn("未知的操作类型: {}", op);
 *         }
 *     }
 *
 *     private void handleInsert(Dict data) {
 *         // 处理插入操作
 *     }
 *
 *     private void handleUpdate(Dict before, Dict after) {
 *         // 处理更新操作
 *     }
 *
 *     private void handleDelete(Dict data) {
 *         // 处理删除操作
 *     }
 * }
 * </pre>
 */
public interface GXDebeziumService {
    /**
     * Redis缓存桶名称
     */
    String BUCKET_NAME = "maple-framework-debezium-engine";

    /**
     * Redis锁名称格式
     */
    String LOCK_NAME_FORMAT = "initial-engine-lock:{}";

    /**
     * 自定义业务处理
     * <p>
     * 该方法用于处理Debezium捕获的数据库变更事件。当数据库中的记录发生变化时，
     * Debezium会捕获这些变更并调用此方法进行处理。
     * </p>
     * <p>
     * 数据结构说明:
     * <ul>
     *   <li>op: 操作类型 (c:新增, u:更新, d:删除)</li>
     *   <li>before: 变更前的数据 (删除和更新操作会包含此字段)</li>
     *   <li>after: 变更后的数据 (新增和更新操作会包含此字段)</li>
     *   <li>source: 事件源信息，包含数据库、表名等元数据</li>
     *   <li>ts_ms: 事件发生的时间戳</li>
     * </ul>
     * </p>
     *
     * @param data 数据库变化的数据，包含变更类型、变更前后的数据等信息
     */
    void processCaptureDataChange(Dict data);

    /**
     * 初始化Debezium引擎上锁
     * <p>
     * 在分布式环境下（同一个服务部署了多个实例的情况下），只需要有一个服务实例处理CDC事件即可。
     * 该方法通过分布式锁确保在服务启动时，只有获得锁的实例才能初始化并启动Debezium引擎。
     * </p>
     * <p>
     * 锁的命名规则为: debezium-initial-engine-lock:{应用名}
     * </p>
     *
     * @param lockKey 锁的键名
     */
    default void initialEngineLock(String lockKey) {
        GXRedissonCacheService redissonCacheService = GXSpringContextUtils.getBean(GXRedissonCacheService.class);
        assert redissonCacheService != null;
        redissonCacheService.setCache(BUCKET_NAME, lockKey, "locked");
    }

    /**
     * 初始化Debezium引擎解锁
     * <p>
     * 该方法用于释放由{@link #initialEngineLock(String)}方法获取的分布式锁。
     * 应当在Debezium引擎初始化完成后调用此方法，以释放锁资源。
     * </p>
     * <p>
     * 注意: 该方法应当在finally块中调用，以确保在初始化过程中发生异常时也能正确释放锁。
     * </p>
     *
     * @param lockKey 锁的键名
     * @see #initialEngineLock(String)
     */
    default void initialEngineUnLock(String lockKey) {
        GXRedissonCacheService redissonCacheService = GXSpringContextUtils.getBean(GXRedissonCacheService.class);
        assert redissonCacheService != null;
        redissonCacheService.deleteCache(BUCKET_NAME, lockKey);
    }

    /**
     * 检查是否已有其他实例初始化了引擎
     * <p>
     * 该方法用于检查是否已有其他服务实例初始化了Debezium引擎。
     * 通过检查分布式锁状态，避免多个服务实例重复初始化引擎。
     * </p>
     *
     * @param lockKey 锁的键名
     * @return 如果已有其他实例初始化了引擎，则返回true；否则返回false
     */
    default boolean isEngineInitialized(String lockKey) {
        GXRedissonCacheService redissonCacheService = GXSpringContextUtils.getBean(GXRedissonCacheService.class);
        assert redissonCacheService != null;
        return redissonCacheService.exists(BUCKET_NAME, lockKey);
    }
}
