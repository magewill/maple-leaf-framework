package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;

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
