package cn.maple.retry.context;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXRetryContextTest {

    @Test
    void getAttributes_ReturnsStableUnmodifiableSnapshot() {
        GXRetryContext context = new GXRetryContext(0, null);
        context.setAttribute("first", "one");

        Map<String, Object> snapshot = context.getAttributes();
        context.setAttribute("second", "two");

        assertEquals(1, snapshot.size());
        assertEquals("one", snapshot.get("first"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("third", "three"));
    }

    @Test
    void attributeNames_ReturnsSnapshot() {
        GXRetryContext context = new GXRetryContext(0, null);
        context.setAttribute("first", "one");

        String[] names = context.attributeNames();
        context.setAttribute("second", "two");

        assertEquals(1, names.length);
        assertEquals("first", names[0]);
    }

    @Test
    void attributes_AllowNullNameAndNullValue() {
        GXRetryContext context = new GXRetryContext(0, null);

        context.setAttribute(null, null);

        assertNull(context.getAttribute(null));
        assertEquals(1, context.attributeNames().length);
        assertNull(context.attributeNames()[0]);
        assertNull(context.getAttributes().get(null));
    }

    @Test
    void attributeNames_PreserveInsertionOrder() {
        GXRetryContext context = new GXRetryContext(0, null);

        context.setAttribute("first", "one");
        context.setAttribute("second", "two");
        context.setAttribute("first", "updated");

        String[] names = context.attributeNames();
        assertEquals("first", names[0]);
        assertEquals("second", names[1]);
        assertEquals("updated", context.getAttributes().get("first"));
    }
}
