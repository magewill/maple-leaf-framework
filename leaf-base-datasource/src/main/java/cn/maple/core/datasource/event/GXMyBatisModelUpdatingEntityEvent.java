package cn.maple.core.datasource.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.model.GXBaseModel;

public class GXMyBatisModelUpdatingEntityEvent<M extends GXBaseModel> extends GXBaseEvent<M> {
    public GXMyBatisModelUpdatingEntityEvent(M source) {
        super(source);
    }

    public GXMyBatisModelUpdatingEntityEvent(M source, String eventType) {
        super(source, eventType);
    }

    public GXMyBatisModelUpdatingEntityEvent(M source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXMyBatisModelUpdatingEntityEvent(M source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
