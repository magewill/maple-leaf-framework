package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.lang.GXDict;

/**
 * MyBatis模型物理删除事件
 * <p>
 * 该事件在MyBatis模型被物理删除时触发，用于实现业务逻辑的解耦和扩展。
 * 物理删除指的是从数据库中永久移除记录，与软删除（标记删除）不同。
 * 继承自GXBaseEvent基础事件类，支持泛型，可以携带任何继承自GXDict的数据模型。
 * </p>
 *
 * @param <M> 模型类型参数，必须继承自GXDict类
 * @author britton
 * @since 1.0.0
 */
public class GXMyBatisModelDeleteEvent<M extends GXDict> extends GXBaseEvent<M> {
    public GXMyBatisModelDeleteEvent(M source) {
        super(source);
    }

    public GXMyBatisModelDeleteEvent(M source, String eventType) {
        super(source, eventType);
    }

    public GXMyBatisModelDeleteEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXMyBatisModelDeleteEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
