package cn.maple.core.framework.event;

import cn.hutool.core.lang.Dict;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

@Getter
public class GXBaseEvent<T> extends ApplicationEvent implements ResolvableTypeProvider {
    private final Dict param;

    private final String eventName;

    private final String eventType;

    public GXBaseEvent(T source) {
        this(source, "", Dict.create(), "");
    }

    public GXBaseEvent(T source, String eventType) {
        this(source, eventType, Dict.create(), "");
    }

    public GXBaseEvent(T source, String eventType, String eventName) {
        this(source, eventType, Dict.create(), eventName);
    }

    public GXBaseEvent(T source, String eventType, Dict param) {
        this(source, eventType, param, "");
    }

    public GXBaseEvent(T source, String eventType, Dict param, String eventName) {
        super(source);
        this.param = copyParam(param);
        this.eventName = eventName;
        this.eventType = eventType;
    }

    public Object getEventName() {
        return eventName;
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClass(getClass());
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getSource() {
        return (T) super.getSource();
    }

    private static Dict copyParam(Dict param) {
        Dict copiedParam = Dict.create();
        if (param != null) {
            copiedParam.putAll(param);
        }
        return copiedParam;
    }
}
