package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

/**
 * 数据已经插入数据库事件
 * <p>
 * 该事件在实体数据被成功插入到数据库后触发，可用于执行后续的业务逻辑，
 * 例如缓存更新、日志记录、关联数据处理、消息通知等。
 * </p>
 *
 * @param <M> 数据字典类型，继承自Dict
 * @author britton chen <britton@126.com>
 */
public class GXMyBatisModelSaveEntityEvent<M extends Dict> extends GXBaseEvent<M> {
    public GXMyBatisModelSaveEntityEvent(M source) {
        super(source);
    }

    public GXMyBatisModelSaveEntityEvent(M source, String eventType) {
        super(source, eventType);
    }

    public GXMyBatisModelSaveEntityEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXMyBatisModelSaveEntityEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
