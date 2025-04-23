package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

/**
 * 批量保存数据事件
 * <p>
 * 该事件在多条实体数据被批量保存到数据库后触发，可用于执行后续的批量处理逻辑，
 * 例如批量缓存更新、批量日志记录、批量关联数据处理等。
 * </p>
 * <p>
 * 线程安全说明：
 * 该事件处理应当是线程安全的，不应修改共享状态，避免并发问题。
 * 由于涉及批量操作，应特别注意资源使用和性能优化。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建事件监听器
 * @Component
 * public class UserBatchSaveListener implements ApplicationListener<GXMyBatisModelSaveBatchEntityEvent<Dict>> {
 *     @Autowired
 *     private RedisTemplate<String, Object> redisTemplate;
 *     
 *     @Override
 *     public void onApplicationEvent(GXMyBatisModelSaveBatchEntityEvent<Dict> event) {
 *         Dict batchData = event.getSource();
 *         // 批量数据可能包含在一个列表字段中
 *         List<Dict> userList = (List<Dict>) batchData.get("dataList");
 *         
 *         // 批量处理，例如更新缓存
 *         List<String> cacheKeys = new ArrayList<>();
 *         for (Dict user : userList) {
 *             Long userId = user.getLong("id");
 *             cacheKeys.add("user:" + userId);
 *         }
 *         
 *         // 批量删除缓存
 *         if (!cacheKeys.isEmpty()) {
 *             redisTemplate.delete(cacheKeys);
 *         }
 *         
 *         // 可以通过事件参数获取上下文信息
 *         Dict params = event.getParam();
 *         String operationType = params.getStr("operationType");
 *         log.info("批量保存用户数据完成，共{}条记录，操作类型: {}", userList.size(), operationType);
 *     }
 * }
 * 
 * // 2. 发布事件
 * List<Dict> userList = new ArrayList<>();
 * userList.add(Dict.create().set("id", 1L).set("username", "用户1"));
 * userList.add(Dict.create().set("id", 2L).set("username", "用户2"));
 * 
 * Dict batchData = Dict.create().set("dataList", userList);
 * Dict params = Dict.create().set("operationType", "batchImport");
 * applicationContext.publishEvent(new GXMyBatisModelSaveBatchEntityEvent<>(batchData, "BATCH_CREATE", params));
 * </pre>
 * </p>
 * 
 * @param <M> 数据字典类型，继承自Dict
 * @author britton chen <britton@126.com>
 */
public class GXMyBatisModelSaveBatchEntityEvent<M extends Dict> extends GXBaseEvent<M> {
    public GXMyBatisModelSaveBatchEntityEvent(M source) {
        super(source);
    }

    public GXMyBatisModelSaveBatchEntityEvent(M source, String eventType) {
        super(source, eventType);
    }

    public GXMyBatisModelSaveBatchEntityEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXMyBatisModelSaveBatchEntityEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
