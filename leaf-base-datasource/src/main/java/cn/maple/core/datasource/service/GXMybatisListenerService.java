package cn.maple.core.datasource.service;

import cn.hutool.core.lang.Dict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MyBatis操作监听服务接口
 * <p>
 * 该接口定义了一系列数据库操作的监听方法，用于在数据库操作前后执行自定义逻辑，
 * 例如日志记录、缓存更新、数据校验、关联数据处理等。
 * </p>
 * <p>
 * 线程安全说明：
 * 该接口的实现类应确保在多线程环境下是安全的，不应修改共享状态，避免并发问题。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 实现监听服务
 * @Service
 * public class UserServiceListener implements GXMybatisListenerService<UserEntity> {
 *     @Autowired
 *     private RedisTemplate<String, Object> redisTemplate;
 *     
 *     @Override
 *     public void saveEntityListener(UserEntity data) {
 *         // 在用户保存后更新缓存
 *         redisTemplate.opsForValue().set("user:" + data.getId(), data);
 *         LOG.info("用户数据已保存并更新缓存: {}", data.getUsername());
 *     }
 *     
 *     @Override
 *     public void updateEntityListener(UserEntity data, Dict keyValuePairs, Dict keyOperatorPairs) {
 *         // 在用户更新后清除缓存
 *         Long userId = data.getId();
 *         redisTemplate.delete("user:" + userId);
 *         LOG.info("用户数据已更新并清除缓存: {}", userId);
 *         
 *         // 可以根据更新条件执行特定逻辑
 *         if (keyValuePairs.containsKey("status")) {
 *             Integer newStatus = (Integer) keyValuePairs.get("status");
 *             if (newStatus == 1) {
 *                 LOG.info("用户状态已激活: {}", userId);
 *             }
 *         }
 *     }
 *     
 *     @Override
 *     public void updateFieldListener(Dict updateFieldData, Dict conditionFieldData) {
 *         // 处理字段更新
 *         if (updateFieldData.containsKey("email")) {
 *             String email = (String) updateFieldData.get("email");
 *             LOG.info("用户邮箱已更新: {}", email);
 *         }
 *         
 *         // 根据条件获取受影响的用户ID
 *         if (conditionFieldData.containsKey("id")) {
 *             Long userId = (Long) conditionFieldData.get("id");
 *             redisTemplate.delete("user:" + userId);
 *         }
 *     }
 *     
 *     @Override
 *     public void deleteSoftListener(Dict data) {
 *         // 软删除后的处理
 *         if (data.containsKey("id")) {
 *             Long userId = (Long) data.get("id");
 *             redisTemplate.delete("user:" + userId);
 *             LOG.info("用户已软删除: {}", userId);
 *         }
 *     }
 *     
 *     @Override
 *     public void saveBatchListener(Dict data) {
 *         // 批量保存后的处理
 *         if (data.containsKey("dataList")) {
 *             List<UserEntity> userList = (List<UserEntity>) data.get("dataList");
 *             LOG.info("批量保存用户数据完成，共{}条记录", userList.size());
 *             
 *             // 批量更新缓存
 *             for (UserEntity user : userList) {
 *                 redisTemplate.opsForValue().set("user:" + user.getId(), user);
 *             }
 *         }
 *     }
 * }
 * </pre>
 * </p>
 * 
 * @param <T> 实体类型
 * @author britton chen <britton@126.com>
 */
public interface GXMybatisListenerService<T> {
    Logger LOG = LoggerFactory.getLogger(GXMybatisListenerService.class);

    /**
     * 保存实体的监听器
     * <p>
     * 该方法在实体数据被保存到数据库后调用，可用于执行后续的业务逻辑，
     * 例如更新缓存、发送通知、记录操作日志等。
     * </p>
     * <p>
     * 默认实现仅记录日志，实现类应根据实际业务需求重写此方法。
     * </p>
     *
     * @param data 已保存的实体数据
     */
    default void saveEntityListener(T data) {
        LOG.info("请自定义实现saveEntityListener监听逻辑");
    }

    /**
     * 更新实体的监听器
     * <p>
     * 该方法在实体数据被更新后调用，可用于执行后续的业务逻辑，
     * 例如更新缓存、发送通知、记录操作日志等。
     * </p>
     * <p>
     * 参数说明：
     * - data: 更新后的实体数据
     * - keyValuePairs: 本次更新的字段名和值的映射
     * - keyOperatorPairs: 每个字段的操作符，如"=", ">", "<"等
     * </p>
     * <p>
     * 默认实现仅记录日志，实现类应根据实际业务需求重写此方法。
     * </p>
     *
     * @param data             更新之后的实体数据
     * @param keyValuePairs    本次更新的条件，键为字段名，值为字段值
     * @param keyOperatorPairs 每个字段的操作，键为字段名，值为操作符
     */
    default void updateEntityListener(T data, Dict keyValuePairs, Dict keyOperatorPairs) {
        LOG.info("请自定义实现updateEntityListener监听逻辑");
    }

    /**
     * 更新字段的监听器
     * <p>
     * 该方法在通过字段更新方式更新数据后调用，可用于执行后续的业务逻辑，
     * 例如更新缓存、发送通知、记录操作日志等。
     * </p>
     * <p>
     * 参数说明：
     * - updateFieldData: 包含更新字段名和值的映射
     * - conditionFieldData: 包含更新条件字段名和值的映射
     * </p>
     * <p>
     * 默认实现仅记录日志，实现类应根据实际业务需求重写此方法。
     * </p>
     *
     * @param updateFieldData    更新的字段信息，键为字段名，值为字段值
     * @param conditionFieldData 更新条件，键为字段名，值为字段值
     */
    default void updateFieldListener(Dict updateFieldData, Dict conditionFieldData) {
        LOG.info("请自定义实现updateFieldListener监听逻辑");
    }

    /**
     * 软删除监听器
     * <p>
     * 该方法在执行软删除操作后调用，可用于执行后续的业务逻辑，
     * 例如更新缓存、发送通知、记录操作日志等。
     * </p>
     * <p>
     * 软删除通常是将数据标记为已删除状态，而不是物理删除数据。
     * </p>
     * <p>
     * 默认实现仅记录日志，实现类应根据实际业务需求重写此方法。
     * </p>
     *
     * @param data 删除的条件，包含字段名和值的映射
     */
    default void deleteSoftListener(Dict data) {
        LOG.info("请自定义实现deleteSoftListener监听逻辑");
    }

    /**
     * 批量新增与修改监听器
     * <p>
     * 该方法在执行批量保存或更新操作后调用，可用于执行后续的批量处理逻辑，
     * 例如批量更新缓存、批量发送通知、记录操作日志等。
     * </p>
     * <p>
     * 由于涉及批量操作，应特别注意资源使用和性能优化。
     * </p>
     * <p>
     * 默认实现仅记录日志，实现类应根据实际业务需求重写此方法。
     * </p>
     *
     * @param data 新增与修改的数据，通常包含一个数据列表
     */
    default void saveBatchListener(Dict data) {
        LOG.info("请自定义实现saveBatchListener监听逻辑");
    }
}
