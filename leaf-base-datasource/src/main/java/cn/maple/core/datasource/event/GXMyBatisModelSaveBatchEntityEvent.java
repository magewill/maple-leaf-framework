package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

/**
 * 批量保存数据事件
 * <p>
 * 该事件在多条实体数据被批量保存到数据库后触发，可用于执行后续的批量处理逻辑，
 * 例如批量缓存更新、批量日志记录、批量关联数据处理等。
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
