package cn.maple.core.framework.audit;

import cn.maple.core.framework.event.GXBaseEvent;
import cn.maple.core.framework.event.processor.GuavaSyncEventBusBeanPostProcessor;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CURRENT BEHAVIOR - flip when fixed.
 *
 * <p>Characterizes the "snapshot on publish" contract of {@code GXBaseEvent} and the
 * identity based subscription registry of {@code GXEventPublisherUtils}.</p>
 */
class GXEventContractAuditTest {
    @Test
    void customPayloadIsNotSnapshotted() {
        MutablePayload payload = new MutablePayload("before");
        GXBaseEvent<MutablePayload> event = new GXBaseEvent<>(payload, "audit", "audit-event");

        payload.value = "after";

        assertSame(payload, event.getSource());
        assertEquals("after", event.getSource().value);
    }

    @Test
    void mutableArrayKeyIsReplacedBySnapshotCopy() {
        Map<Object, Object> source = new HashMap<>();
        byte[] key = new byte[]{1, 2, 3};
        source.put(key, "value");
        GXBaseEvent<Map<Object, Object>> event = new GXBaseEvent<>(source, "audit", "audit-keys");

        Map<Object, Object> snapshot = event.getSource();

        assertEquals(1, snapshot.size());
        assertFalse(snapshot.containsKey(key));
        assertEquals("value", snapshot.values().iterator().next());
    }

    @Test
    void registerCacheKeyUsesIdentityHashCode() throws Exception {
        EventBus bus = new EventBus("audit-register-bus");
        Object first = new Object();
        Object second = new Object();

        String firstKey = registerCacheKey(bus, first);

        assertEquals(firstKey, registerCacheKey(bus, first));
        assertNotEquals(firstKey, registerCacheKey(bus, second));
        assertTrue(firstKey.endsWith(":java.lang.Object"));
    }

    @Test
    void snapshotReturnsSameReferenceForUnsupportedTypes() {
        StringBuilder payload = new StringBuilder("audit");
        GXBaseEvent<StringBuilder> event = new GXBaseEvent<>(payload, "audit", "audit-stringbuilder");

        assertSame(payload, event.getSource());
    }

    @Test
    void jdkProxySubscriberIsNotRegistered() throws Exception {
        GuavaSyncEventBusBeanPostProcessor postProcessor = new GuavaSyncEventBusBeanPostProcessor();
        Field eventBusField = GuavaSyncEventBusBeanPostProcessor.class.getDeclaredField("eventBus");
        eventBusField.setAccessible(true);
        eventBusField.set(postProcessor, new EventBus("audit-proxy-bus"));
        ProxySubscriber proxy = (ProxySubscriber) Proxy.newProxyInstance(
                ProxySubscriber.class.getClassLoader(),
                new Class<?>[]{ProxySubscriber.class},
                (proxyInstance, method, args) -> null);

        postProcessor.postProcessAfterInitialization(proxy, "auditProxySubscriber");

        assertFalse(postProcessor.isBeanRegistered("auditProxySubscriber"));
    }

    @Test
    void sameBeanNameSecondInstanceIsSkipped() throws Exception {
        GuavaSyncEventBusBeanPostProcessor postProcessor = new GuavaSyncEventBusBeanPostProcessor();
        Field eventBusField = GuavaSyncEventBusBeanPostProcessor.class.getDeclaredField("eventBus");
        eventBusField.setAccessible(true);
        eventBusField.set(postProcessor, new EventBus("audit-instance-bus"));

        postProcessor.postProcessAfterInitialization(new AuditListener(), "sameBeanName");
        postProcessor.postProcessAfterInitialization(new AuditListener(), "sameBeanName");

        assertEquals(1, postProcessor.getRegisteredBeanCount());
    }

    private String registerCacheKey(EventBus bus, Object listener) throws Exception {
        Method method = GXEventPublisherUtils.class.getDeclaredMethod(
                "buildRegisterCacheKey", EventBus.class, Object.class);
        method.setAccessible(true);
        return (String) method.invoke(null, bus, listener);
    }

    static class MutablePayload {
        private String value;

        MutablePayload(String value) {
            this.value = value;
        }
    }

    interface ProxySubscriber {
        @Subscribe
        void onAuditEvent(String event);
    }

    static class AuditListener {
        @Subscribe
        public void onAuditEvent(String event) {
            // no-op: registration accounting only
        }
    }
}
