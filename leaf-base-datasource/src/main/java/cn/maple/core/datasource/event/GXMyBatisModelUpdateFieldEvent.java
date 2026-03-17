package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

/**
 * MyBatis模型字段更新事件
 * <p>
 * 该事件在MyBatis模型的特定字段被更新时触发，用于实现业务逻辑的解耦和扩展。
 * 继承自GXBaseEvent基础事件类，支持泛型，可以携带任何继承自Dict的数据模型。
 * </p>
 *
 * @param <M> 模型类型参数，必须继承自Dict类
 * @author britton
 * @since 1.0.0
 */
public class GXMyBatisModelUpdateFieldEvent<M extends Dict> extends GXBaseEvent<M> {
    public GXMyBatisModelUpdateFieldEvent(M source) {
        super(source);
    }

    public GXMyBatisModelUpdateFieldEvent(M source, String eventType) {
        super(source, eventType);
    }

    public GXMyBatisModelUpdateFieldEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXMyBatisModelUpdateFieldEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
