package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

/**
 * MyBatis模型软删除事件
 * <p>
 * 该事件在MyBatis模型被软删除时触发，用于实现业务逻辑的解耦和扩展。
 * 软删除通常指的是将数据标记为已删除状态，而非物理删除数据库记录。
 * 继承自GXBaseEvent基础事件类，支持泛型，可以携带任何继承自Dict的数据模型。
 * </p>
 *
 * @param <M> 模型类型参数，必须继承自Dict类
 * @author britton
 * @since 1.0.0
 */
public class GXMyBatisModelDeleteSoftEvent<M extends Dict> extends GXBaseEvent<M> {
    public GXMyBatisModelDeleteSoftEvent(M source) {
        super(source);
    }

    public GXMyBatisModelDeleteSoftEvent(M source, String eventType) {
        super(source, eventType);
    }

    public GXMyBatisModelDeleteSoftEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXMyBatisModelDeleteSoftEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
