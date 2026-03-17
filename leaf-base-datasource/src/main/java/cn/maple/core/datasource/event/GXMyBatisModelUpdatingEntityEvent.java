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
