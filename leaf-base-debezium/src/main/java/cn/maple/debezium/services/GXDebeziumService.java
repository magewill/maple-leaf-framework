package cn.maple.debezium.services;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.redisson.util.GXRedissonUtils;

import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;

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
 * @Service
 * public class MyDebeziumServiceImpl implements GXDebeziumService {
 *     @Override
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
     * 锁的命名规则为: debezium-initial-engine:{应用名}:{实例key}
     * 锁的默认持有时间为10秒，足够完成Debezium引擎的初始化。
     * </p>
     * <p>
     * 注意: 该方法应当在try块中使用，并在finally块中调用{@link #initialEngineUnLock(String)}方法释放锁，
     * 以确保在初始化过程中发生异常时也能正确释放锁。
     * </p>
     *
     * @param key 实例的key，用于区分不同的Debezium实例
     * @see #initialEngineUnLock(String)
     */
    default void initialEngineLock(String key) {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        String lockName = CharSequenceUtil.format("debezium-initial-engine:{}:{}", appName, key);
        GXRedissonUtils.getLock(lockName).lock(10, TimeUnit.SECONDS);
    }

    /**
     * 初始化Debezium引擎解锁
     * <p>
     * 该方法用于释放由{@link #initialEngineLock(String)}方法获取的分布式锁。
     * 应当在Debezium引擎初始化完成后调用此方法，以释放锁资源，允许其他操作获取该锁。
     * </p>
     * <p>
     * 注意: 该方法应当在finally块中调用，以确保在初始化过程中发生异常时也能正确释放锁。
     * 如果在未持有锁的情况下调用此方法，可能会抛出IllegalMonitorStateException异常。
     * </p>
     *
     * @param key 实例的key，与获取锁时使用的key相同
     * @see #initialEngineLock(String)
     */
    default void initialEngineUnLock(String key) {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        String lockName = CharSequenceUtil.format("debezium-initial-engine:{}:{}", appName, key);
        GXRedissonUtils.getLock(lockName).unlock();
    }
}
