package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

/**
 * 数据已经更新数据库事件
 * <p>
 * 该事件在实体数据被成功更新到数据库后触发，可用于执行后续的业务逻辑，
 * 例如缓存更新、日志记录、关联数据处理等。
 * </p>
 * <p>
 * 线程安全说明：
 * 该事件处理应当是线程安全的，不应修改共享状态，避免并发问题。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建事件监听器
 * @Component
 * public class UserUpdateListener implements ApplicationListener<GXMyBatisModelUpdateEntityEvent<Dict>> {
 *     @Autowired
 *     private RedisTemplate<String, Object> redisTemplate;
 *     
 *     @Override
 *     public void onApplicationEvent(GXMyBatisModelUpdateEntityEvent<Dict> event) {
 *         Dict userData = event.getSource();
 *         // 在用户更新后进行缓存刷新
 *         Long userId = userData.getLong("id");
 *         redisTemplate.delete("user:" + userId);
 *         
 *         // 可以通过事件参数获取上下文信息
 *         Dict params = event.getParam();
 *         String operationType = params.getStr("operationType");
 *         log.info("用户数据已更新，操作类型: {}", operationType);
 *     }
 * }
 * 
 * // 2. 发布事件
 * Dict userData = Dict.create().set("id", 1L).set("username", "新用户名");
 * Dict params = Dict.create().set("operationType", "update");
 * applicationContext.publishEvent(new GXMyBatisModelUpdateEntityEvent<>(userData, "UPDATE", params));
 * </pre>
 * </p>
 * 
 * @param <M> 数据字典类型，继承自Dict
 * @author britton chen <britton@126.com>
 */
public class GXMyBatisModelUpdateEntityEvent<M extends Dict> extends GXBaseEvent<M> {
    public GXMyBatisModelUpdateEntityEvent(M source) {
        super(source);
    }

    public GXMyBatisModelUpdateEntityEvent(M source, String eventType) {
        super(source, eventType);
    }

    public GXMyBatisModelUpdateEntityEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXMyBatisModelUpdateEntityEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
