package cn.maple.core.framework.util;

import cn.maple.core.framework.event.GXBaseEvent;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GXEventPublisherUtilsTest {
    private MockedStatic<GXSpringContextUtils> springContextUtils;

    @BeforeEach
    void setUp() {
        clearRegisterCache();
    }

    @AfterEach
    void tearDown() {
        if (springContextUtils != null) {
            springContextUtils.close();
        }
        clearRegisterCache();
    }

    @Test
    void syncAndAsyncPublishUseIndependentRegistrationCache() {
        EventBus syncEventBus = new EventBus();
        AsyncEventBus asyncEventBus = new AsyncEventBus(Runnable::run);
        TestListener listener = new TestListener();
        springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class);
        springContextUtils.when(() -> GXSpringContextUtils.getBean("eventBus", EventBus.class)).thenReturn(syncEventBus);
        springContextUtils.when(() -> GXSpringContextUtils.getBean("asyncEventBus", AsyncEventBus.class)).thenReturn(asyncEventBus);

        GXEventPublisherUtils.publishGuavaAsyncEvent(new GXBaseEvent<>("async"), listener);
        GXEventPublisherUtils.publishGuavaSyncEvent(new GXBaseEvent<>("sync"), listener);

        assertEquals(2, listener.getCount());
    }

    @SuppressWarnings("unchecked")
    private void clearRegisterCache() {
        ConcurrentHashMap<String, Boolean> cache = (ConcurrentHashMap<String, Boolean>) ReflectionTestUtils.getField(
                GXEventPublisherUtils.class,
                "EVENT_BUS_REGISTER_CACHE"
        );
        if (cache != null) {
            cache.clear();
        }
    }

    static class TestListener {
        private final AtomicInteger count = new AtomicInteger();

        @Subscribe
        public void onEvent(GXBaseEvent<String> event) {
            count.incrementAndGet();
        }

        int getCount() {
            return count.get();
        }
    }
}
