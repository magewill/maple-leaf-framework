package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

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
