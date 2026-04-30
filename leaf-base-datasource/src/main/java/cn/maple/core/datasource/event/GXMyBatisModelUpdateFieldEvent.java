package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

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
