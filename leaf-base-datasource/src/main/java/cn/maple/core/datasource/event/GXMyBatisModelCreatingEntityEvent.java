package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.model.GXBaseModel;

public class GXMyBatisModelCreatingEntityEvent<M extends GXBaseModel> extends GXBaseEvent<M> {
    public GXMyBatisModelCreatingEntityEvent(M source) {
        super(source);
    }

    public GXMyBatisModelCreatingEntityEvent(M source, String eventType) {
        super(source, eventType);
    }

    public GXMyBatisModelCreatingEntityEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXMyBatisModelCreatingEntityEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
