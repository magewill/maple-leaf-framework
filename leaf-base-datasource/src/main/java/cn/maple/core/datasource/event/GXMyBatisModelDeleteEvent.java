package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

public class GXMyBatisModelDeleteEvent<M extends Dict> extends GXBaseEvent<M> {
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
