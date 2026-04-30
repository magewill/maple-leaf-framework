package cn.maple.core.framework.event.center;

import cn.maple.core.framework.util.GXCommonUtils;
import com.google.common.eventbus.EventBus;

@SuppressWarnings("unused")
public class SyncEventBusCenter {
    private static final String APPLICATION_NAME = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);

    private static final Integer CPU_CORE_NUMBER = Runtime.getRuntime().availableProcessors();

    private static final EventBus SYNC_EVENT_BUS = new EventBus("sync-" + APPLICATION_NAME);

    private SyncEventBusCenter() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static EventBus getInstance() {
        return SYNC_EVENT_BUS;
    }

    public static void register(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event subscriber cannot be null");
        }
        SYNC_EVENT_BUS.register(obj);
    }

    public static void unregister(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event subscriber cannot be null");
        }
        SYNC_EVENT_BUS.unregister(obj);
    }

    public static void post(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event cannot be null");
        }
        SYNC_EVENT_BUS.post(obj);
    }
}
