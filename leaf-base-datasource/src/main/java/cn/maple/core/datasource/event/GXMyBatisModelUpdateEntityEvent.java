package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

/**
 * 数据已经更新数据库事件
 * <p>
 * 该事件在实体数据被成功更新到数据库后触发，可用于执行后续的业务逻辑，
 * 例如缓存更新、日志记录、关联数据处理等。
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
