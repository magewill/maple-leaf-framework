package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.model.GXBaseModel;

/**
 * 更新数据之前的事件
 * <p>
 * 该事件在实体数据被更新到数据库之前触发，允许在数据更新前进行额外的处理，
 * 例如数据校验、字段修改或业务规则验证等。
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
 * public class UserUpdatingListener implements ApplicationListener<GXMyBatisModelUpdatingEntityEvent<UserModel>> {
 *     @Override
 *     public void onApplicationEvent(GXMyBatisModelUpdatingEntityEvent<UserModel> event) {
 *         UserModel user = event.getSource();
 *         // 在用户更新前进行额外处理
 *         if (user.getUpdateTime() == null) {
 *             user.setUpdateTime(new Date());
 *         }
 *         // 可以通过事件参数获取上下文信息
 *         Dict params = event.getParam();
 *         String operationType = params.getStr("operationType");
 *     }
 * }
 * 
 * // 2. 发布事件
 * UserModel user = userService.getById(1L);
 * user.setUsername("新用户名");
 * Dict params = Dict.create().set("operationType", "update");
 * applicationContext.publishEvent(new GXMyBatisModelUpdatingEntityEvent<>(user, "UPDATE", params));
 * </pre>
 * </p>
 * 
 * @param <M> 实体模型类型，必须继承自GXBaseModel
 * @author britton chen <britton@126.com>
 */
public class GXMyBatisModelUpdatingEntityEvent<M extends GXBaseModel> extends GXBaseEvent<M> {
    public GXMyBatisModelUpdatingEntityEvent(M source) {
        super(source);
    }

    public GXMyBatisModelUpdatingEntityEvent(M source, String eventType) {
        super(source, eventType);
    }

    public GXMyBatisModelUpdatingEntityEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXMyBatisModelUpdatingEntityEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
