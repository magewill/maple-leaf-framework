package cn.maple.core.framework.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.event.dto.GXExceptionNotifyEventDto;

public class GXExceptionNotifyEvent extends GXBaseEvent<GXExceptionNotifyEventDto> {
    public GXExceptionNotifyEvent(GXExceptionNotifyEventDto source) {
        super(source);
    }

    public GXExceptionNotifyEvent(GXExceptionNotifyEventDto source, String eventType) {
        super(source, eventType);
    }

    public GXExceptionNotifyEvent(GXExceptionNotifyEventDto source, String eventType, String eventName) {
        super(source, eventType, eventName);
    }

    public GXExceptionNotifyEvent(GXExceptionNotifyEventDto source, String eventType, Dict param) {
        super(source, eventType, param);
    }

    public GXExceptionNotifyEvent(GXExceptionNotifyEventDto source, String eventType, Dict param, String eventName) {
        super(source, eventType, param, eventName);
    }
}
