package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

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
